package serverplayerdatabase;

import arc.Core;
import arc.Events;
import arc.files.Fi;
import arc.func.Prov;
import arc.math.geom.Vec2;
import arc.scene.Element;
import arc.scene.ui.TextField;
import arc.scene.ui.layout.Table;
import arc.struct.IntMap;
import arc.struct.IntSeq;
import arc.struct.ObjectMap;
import arc.struct.ObjectSet;
import arc.struct.OrderedSet;
import arc.struct.Seq;
import arc.util.CommandHandler;
import arc.util.Interval;
import arc.util.Log;
import arc.util.Strings;
import arc.util.Time;
import arc.util.serialization.Json;
import arc.util.serialization.JsonReader;
import arc.util.serialization.JsonValue;
import mindustry.Vars;
import mindustry.game.EventType.ClientLoadEvent;
import mindustry.game.EventType.ClientServerConnectEvent;
import mindustry.game.EventType.PlayerChatEvent;
import mindustry.game.EventType.ResetEvent;
import mindustry.game.EventType.Trigger;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Icon;
import mindustry.gen.Player;
import mindustry.mod.Mod;
import mindustry.net.Administration.TraceInfo;
import mindustry.net.Packets.AdminAction;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.ui.dialogs.SettingsMenuDialog;
import mindustry.ui.dialogs.TraceDialog;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.zip.CRC32;

public class ServerPlayerDataBaseMod extends Mod{
    /** When true, this mod is running as a bundled component inside Neon. */
    public static boolean bekBundled = false;

    private static final String keyCollect = "spdb-collect";
    private static final String keyRecordChat = "spdb-record-chat";
    private static final String keyAutoTrace = "spdb-auto-trace";
    private static final String keyShowAutoTraceDialog = "spdb-show-auto-trace-dialog";
    private static final String integrityAlgorithm = "sha256-spdb-v1";

    private static final int integrityValid = 0;
    private static final int integrityMissing = 1;
    private static final int integrityMismatch = 2;
    private static final int integrityUnsupported = 3;

    private static final float collectIntervalTicks = 120f;
    private static final float saveIntervalTicks = 300f;
    private static final long autoTraceCooldownMs = 2200L;
    private static final long autoTracePendingMs = 5000L;
    private static final Pattern shortUidPattern = Pattern.compile("[A-Za-z0-9]{3}");
    private static final Pattern uidColorPattern = Pattern.compile("\\[(?:gr(?:a|e)y)\\]\\s*([A-Za-z0-9]{3,16})", Pattern.CASE_INSENSITIVE);
    private static final Pattern uidTokenPattern = Pattern.compile("(?<![A-Za-z0-9])([A-Za-z0-9]{3,16})");
    private static final Pattern nameUidTailPattern = Pattern.compile("(?:\\||\\s)([A-Za-z0-9]{3,16})(?:[^A-Za-z0-9]*)$");

    private final Interval timer = new Interval(3);
    private final Json json = new Json();
    private final JsonReader jsonReader = new JsonReader();
    private final PlayerDatabase playerDb = new PlayerDatabase();
    private final ChatDatabase chatDb = new ChatDatabase();
    private final MindustryXOverlayUI overlayUI = new MindustryXOverlayUI();
    private final ObjectMap<String, String> ipGeoCache = new ObjectMap<>();
    private final ObjectSet<String> ipGeoPending = new ObjectSet<>();
    private final Seq<String> debugLines = new Seq<>();
    private final Vec2 overlayStagePos = new Vec2();

    private final IntMap<String> pidByPlayerId = new IntMap<>();
    private final IntMap<Long> nextTraceAt = new IntMap<>();
    private final IntMap<Long> autoTraceUntil = new IntMap<>();
    private final IntSeq staleTraceCooldownIds = new IntSeq();
    private final IntSeq staleTracePendingIds = new IntSeq();

    private Fi dataDir;
    private Fi playersFile;
    private Fi chatsDir;
    private Fi chatsIndexFile;
    private Fi legacyChatsFile;

    private boolean playersDirty;
    private boolean chatsDirty;
    private String currentServer = "offline";
    private int playersFileIntegrityState = integrityMissing;
    private final Seq<String> playersIntegrityIssues = new Seq<>();

    private TraceDialog originalTraceDialog;

    private Object overlayQueryWindow;
    private Object overlayDebugWindow;
    private OverlayQueryContent overlayQueryContent;
    private DebugContent debugContent;
    private float nextAttachAttempt;

    private BaseDialog fallbackQueryDialog;
    private BaseDialog fallbackDebugDialog;
    private QueryContent fallbackQueryContent;

    private java.lang.reflect.Field chatMessagesField;

    public ServerPlayerDataBaseMod(){
        Events.on(ClientLoadEvent.class, e -> {
            if(Vars.headless) return;

            Core.settings.defaults(keyCollect, true);
            Core.settings.defaults(keyRecordChat, false);
            Core.settings.defaults(keyAutoTrace, true);
            Core.settings.defaults(keyShowAutoTraceDialog, false);

            initStorage();
            loadLocalData();
            registerSettings();
            installTraceInterceptor();

            nextAttachAttempt = 0f;
            Time.runTask(10f, this::ensureOverlayAttached);
            Log.info("SPDB: loaded. Use /spdb to open local query window.");
        });

        Events.on(ClientServerConnectEvent.class, e -> {
            currentServer = normalizeServer(e.ip + ":" + e.port);
            pidByPlayerId.clear();
            nextTraceAt.clear();
            autoTraceUntil.clear();
            collectPlayers();
        });

        Events.on(ResetEvent.class, e -> {
            currentServer = "offline";
            pidByPlayerId.clear();
            nextTraceAt.clear();
            autoTraceUntil.clear();
            saveDirty(true);
        });

        Events.on(PlayerChatEvent.class, e -> {
            if(Vars.headless || e == null || e.player == null || e.message == null) return;

            String uidFromName = extractUidFromPlayerName(e.player.name);
            if(uidFromName != null) bindPlayerUid(e.player, uidFromName);

            ChatSnapshot snapshot = tryExtractChatSnapshotFromRecentChat(e.player, e.message);
            String chatUid = snapshot == null ? null : snapshot.uid;
            if(chatUid != null){
                bindPlayerUid(e.player, chatUid);
            }

            if(snapshot != null){
                appendDebugLine("uid=" + (chatUid == null ? "(none)" : chatUid) + " | sender=" + safeLine(snapshot.senderName, 36) + " | msg=" + safeLine(snapshot.message, 56));
            }else{
                appendDebugLine("uid=(none) | sender=" + safeLine(safeName(e.player.name), 36) + " | msg=" + safeLine(e.message, 56));
            }

            if(!Core.settings.getBool(keyRecordChat, false)) return;
            if(!Vars.state.isGame()) return;

            String pid = resolvePid(e.player);
            if(pid == null) return;

            String uid = playerDb.getBoundUid(pid);
            if(uid == null) uid = chatUid;
            if(uid == null){
                uid = deriveUidFromUuidLike(pid);
                if(uid != null && playerDb.bindUid(pid, uid)) playersDirty = true;
            }
            String sender = snapshot != null && snapshot.senderName != null ? snapshot.senderName : safeName(e.player.name);
            String message = snapshot != null && snapshot.message != null ? snapshot.message : safeMessage(e.message);

            if(chatDb.add(uid, sender, message, currentServer, Time.millis())){
                chatsDirty = true;
            }
        });

        Events.run(Trigger.update, this::update);
    }

    @Override
    public void registerClientCommands(CommandHandler handler){
        handler.<Player>register("spdb", "打开 ServerPlayerDataBase 查询窗口。", (args, player) -> Core.app.post(this::showStandaloneQueryDialog));
        handler.<Player>register("spdb-debug", "打开 ServerPlayerDataBase 调试窗口。", (args, player) -> Core.app.post(this::showStandaloneDebugDialog));
    }

    private void update(){
        if(Vars.headless) return;

        releaseOverlayFocusIfPointerOutside();

        if(Time.time >= nextAttachAttempt){
            nextAttachAttempt = Time.time + 120f;
            ensureOverlayAttached();
            installTraceInterceptor();
        }

        if(!Vars.net.active() || !Vars.state.isGame()) return;

        if(timer.get(0, collectIntervalTicks) && Core.settings.getBool(keyCollect, true)){
            collectPlayers();
        }

        if(timer.get(1, saveIntervalTicks)){
            saveDirty(false);
        }

        if(timer.get(2, 60f)){
            pruneTracePending();
        }
    }

    private void releaseOverlayFocusIfPointerOutside(){
        if(Core.scene == null || Core.input == null) return;
        if(isPointerOverOverlayContent()) return;

        boolean cleared = clearOverlayFocusIfOwned();
        if(!cleared){
            cleared = clearOverlayFocusFallback();
        }
        if(!cleared) return;

        // Match customMarker's approach: run delayed clear to handle late focus re-assignments.
        Core.app.post(() -> Core.app.post(() -> {
            if(isPointerOverOverlayContent()) return;
            if(!clearOverlayFocusIfOwned()) clearOverlayFocusFallback();
        }));
    }

    private boolean clearOverlayFocusIfOwned(){
        if(Core.scene == null) return false;

        Element scrollFocus = Core.scene.getScrollFocus();
        Element keyboardFocus = Core.scene.getKeyboardFocus();
        if(!ownsOverlayFocus(scrollFocus) && !ownsOverlayFocus(keyboardFocus)) return false;

        if(overlayQueryContent != null) Core.scene.unfocus(overlayQueryContent.root);
        if(debugContent != null) Core.scene.unfocus(debugContent.root);
        if(overlayQueryWindow instanceof Element) Core.scene.unfocus((Element)overlayQueryWindow);
        if(overlayDebugWindow instanceof Element) Core.scene.unfocus((Element)overlayDebugWindow);

        Core.scene.setScrollFocus(null);
        Core.scene.setKeyboardFocus(null);
        Core.scene.cancelTouchFocus();
        return true;
    }

    private boolean clearOverlayFocusFallback(){
        if(Core.scene == null) return false;
        if(!Vars.state.isGame()) return false;
        if(Core.scene.hasDialog() || Core.scene.hasField()) return false;

        Element scrollFocus = Core.scene.getScrollFocus();
        Element keyboardFocus = Core.scene.getKeyboardFocus();
        if(scrollFocus == null && keyboardFocus == null) return false;

        Core.scene.setScrollFocus(null);
        Core.scene.setKeyboardFocus(null);
        Core.scene.cancelTouchFocus();
        return true;
    }

    private boolean isPointerOverOverlayContent(){
        if(Core.scene == null || Core.input == null) return false;
        Core.scene.screenToStageCoordinates(overlayStagePos.set(Core.input.mouseX(), Core.input.mouseY()));
        Element hovered = Core.scene.hit(overlayStagePos.x, overlayStagePos.y, true);
        if(hovered == null) hovered = Core.scene.hit(overlayStagePos.x, overlayStagePos.y, false);
        return ownsOverlayElement(hovered);
    }

    private boolean ownsOverlayFocus(Element focus){
        if(focus == null) return false;
        if(ownsOverlayElement(focus)) return true;
        return hasMindustryXAncestor(focus);
    }

    private boolean ownsOverlayElement(Element element){
        if(element == null) return false;
        if(overlayQueryContent != null && (element == overlayQueryContent.root || element.isDescendantOf(overlayQueryContent.root))) return true;
        if(debugContent != null && (element == debugContent.root || element.isDescendantOf(debugContent.root))) return true;
        if(overlayQueryWindow instanceof Element){
            Element queryWindow = (Element)overlayQueryWindow;
            if(element == queryWindow || element.isDescendantOf(queryWindow)) return true;
        }
        if(overlayDebugWindow instanceof Element){
            Element debugWindow = (Element)overlayDebugWindow;
            if(element == debugWindow || element.isDescendantOf(debugWindow)) return true;
        }
        return hasMindustryXAncestor(element);
    }

    private boolean hasMindustryXAncestor(Element element){
        Element cur = element;
        int guard = 0;
        while(cur != null && guard++ < 32){
            String cls = cur.getClass().getName();
            if(cls != null && cls.startsWith("mindustryX.features.ui")) return true;
            cur = cur.parent;
        }
        return false;
    }

    private void collectPlayers(){
        long now = Time.millis();

        for(Player p : Groups.player){
            if(p == null) continue;
            String pid = resolvePid(p);
            if(pid == null) continue;

            String uidFromName = extractUidFromPlayerName(p.name);
            if(uidFromName != null) bindPlayerUid(p, uidFromName);

            String uid = playerDb.getBoundUid(pid);
            if(uid == null){
                uid = deriveUidFromUuidLike(pid);
                if(uid != null && playerDb.bindUid(pid, uid)) playersDirty = true;
            }

            if(playerDb.touch(pid, uid, p.name, currentServer, now)){
                playersDirty = true;
            }

            if(canAutoTraceIps()){
                requestTraceIfNeeded(p, now);
            }
        }
    }

    private boolean canAutoTraceIps(){
        return Core.settings.getBool(keyAutoTrace, true) && Vars.net.client() && Vars.player != null && Vars.player.admin;
    }

    private void requestTraceIfNeeded(Player target, long now){
        if(target == null) return;

        Long next = nextTraceAt.get(target.id);
        if(next != null && now < next) return;

        try{
            Call.adminRequest(target, AdminAction.trace, null);
            nextTraceAt.put(target.id, now + autoTraceCooldownMs);
            autoTraceUntil.put(target.id, now + autoTracePendingMs);
        }catch(Throwable ignored){
            //silently ignore; servers can reject trace requests.
        }
    }

    private void pruneTracePending(){
        long now = Time.millis();

        staleTraceCooldownIds.clear();
        for(IntMap.Entry<Long> entry : nextTraceAt.entries()){
            if(entry.value != null && entry.value < now - 30000L){
                staleTraceCooldownIds.add(entry.key);
            }
        }
        for(int i = 0; i < staleTraceCooldownIds.size; i++){
            nextTraceAt.remove(staleTraceCooldownIds.get(i));
        }

        staleTracePendingIds.clear();
        for(IntMap.Entry<Long> entry : autoTraceUntil.entries()){
            if(entry.value == null || entry.value < now){
                staleTracePendingIds.add(entry.key);
            }
        }
        for(int i = 0; i < staleTracePendingIds.size; i++){
            autoTraceUntil.remove(staleTracePendingIds.get(i));
        }
    }

    private void installTraceInterceptor(){
        if(Vars.ui == null || Vars.ui.traces == null) return;
        if(Vars.ui.traces instanceof TraceInterceptorDialog) return;

        originalTraceDialog = Vars.ui.traces;
        Vars.ui.traces = new TraceInterceptorDialog();
    }

    private class TraceInterceptorDialog extends TraceDialog{
        @Override
        public void show(Player player, TraceInfo info){
            handleTraceInfo(player, info);

            boolean isAuto = false;
            if(player != null){
                Long until = autoTraceUntil.get(player.id);
                isAuto = until != null && until >= Time.millis();
                autoTraceUntil.remove(player.id);
            }

            if(isAuto && !Core.settings.getBool(keyShowAutoTraceDialog, false)) return;

            if(originalTraceDialog != null){
                originalTraceDialog.show(player, info);
            }else{
                super.show(player, info);
            }
        }
    }

    private void handleTraceInfo(Player player, TraceInfo info){
        if(player == null || info == null) return;

        long now = Time.millis();
        String oldPid = pidByPlayerId.get(player.id);
        if(oldPid == null) oldPid = fallbackPid(player.id);

        String tracePid = normalizePid(info.uuid);
        String pid = choosePrimaryPid(oldPid, tracePid);
        if(pid == null) return;

        pidByPlayerId.put(player.id, pid);

        if(oldPid != null && !pid.equals(oldPid)){
            if(playerDb.mergeInto(oldPid, pid)){
                playersDirty = true;
            }
        }

        String boundUid = playerDb.getBoundUid(pid);
        if(boundUid == null){
            String uidFromUuid = deriveUidFromUuidLike(info.uuid);
            if(uidFromUuid != null && playerDb.bindUid(pid, uidFromUuid)){
                playersDirty = true;
                boundUid = uidFromUuid;
            }
        }
        boolean changed = false;
        changed |= playerDb.touch(pid, boundUid, player.name, currentServer, now);

        if(info.names != null){
            for(String name : info.names){
                changed |= playerDb.addName(pid, name);
            }
        }

        if(info.ip != null){
            changed |= playerDb.addIp(pid, info.ip);
        }

        if(info.ips != null){
            for(String ip : info.ips){
                changed |= playerDb.addIp(pid, ip);
            }
        }

        if(changed) playersDirty = true;
    }

    private void bindPlayerUid(Player player, String uid){
        if(player == null) return;
        uid = normalizeShortUid(uid);
        if(uid == null) return;

        String pid = resolvePid(player);
        if(pid == null) return;

        String oldUid = playerDb.getBoundUid(pid);
        if(playerDb.bindUid(pid, uid)) playersDirty = true;

        if(playerDb.touch(pid, uid, player.name, currentServer, Time.millis())){
            playersDirty = true;
        }

        if(oldUid != null && !oldUid.equals(uid) && chatDb.moveUid(oldUid, uid)) chatsDirty = true;
    }

    private String choosePrimaryPid(String oldPid, String tracePid){
        oldPid = normalizePid(oldPid);
        tracePid = normalizePid(tracePid);

        if(oldPid == null) return tracePid;
        if(tracePid == null) return oldPid;
        if(oldPid.equals(tracePid)) return oldPid;

        if(oldPid.startsWith("pid:")) return tracePid;
        if(tracePid.startsWith("pid:")) return oldPid;

        return oldPid;
    }

    private ChatSnapshot tryExtractChatSnapshotFromRecentChat(Player player, String message){
        if(player == null || Vars.ui == null || Vars.ui.chatfrag == null) return null;

        Seq<String> messages = getChatMessages();
        if(messages == null || messages.isEmpty()) return null;

        String plainName = safeName(player.name);
        String plainMsg = safeMessage(message);

        int checked = 0;
        for(String line : messages){
            if(line == null) continue;

            String stripped = Strings.stripColors(line);
            ChatSnapshot parsed = parseChatLine(line, stripped, plainName);
            if(parsed == null) continue;

            if(!plainMsg.isEmpty() && (parsed.message == null || !parsed.message.contains(plainMsg))) continue;
            if(parsed.uid != null) return parsed;

            if(++checked >= 20) break;
        }
        return null;
    }

    private ChatSnapshot parseChatLine(String rawLine, String strippedLine, String plainName){
        if(strippedLine == null) return null;

        int colon = strippedLine.indexOf(':');
        if(colon < 0) return null;

        String head = strippedLine.substring(0, colon).trim();
        String body = safeMessage(strippedLine.substring(colon + 1));
        if(body.isEmpty()) return null;

        String rawStripped = rawLine == null ? "" : Strings.stripColors(rawLine);
        if(!plainName.isEmpty() && !head.contains(plainName) && !rawStripped.contains(plainName)) return null;

        String uid = extractUidFromLine(rawLine, strippedLine, plainName);
        String sender = normalizeSenderHead(head, uid);
        if(sender.isEmpty()) sender = plainName;

        ChatSnapshot out = new ChatSnapshot();
        out.uid = uid;
        out.senderName = sender;
        out.message = body;
        return out;
    }

    @SuppressWarnings("unchecked")
    private Seq<String> getChatMessages(){
        if(Vars.ui == null || Vars.ui.chatfrag == null) return null;

        try{
            if(chatMessagesField == null){
                chatMessagesField = Vars.ui.chatfrag.getClass().getDeclaredField("messages");
                chatMessagesField.setAccessible(true);
            }
            Object obj = chatMessagesField.get(Vars.ui.chatfrag);
            if(obj instanceof Seq<?>){
                return (Seq<String>)obj;
            }
        }catch(Throwable ignored){
        }

        return null;
    }

    private String extractUidFromLine(String rawLine, String strippedLine, String plainName){
        String head = null;
        if(strippedLine != null){
            int colon = strippedLine.indexOf(':');
            head = colon >= 0 ? strippedLine.substring(0, colon) : strippedLine;

            String nearName = extractShortUidAfterName(head, plainName);
            if(nearName != null) return nearName;

            String tailUid = extractUidFromPlayerName(head);
            if(tailUid != null) return tailUid;
        }

        if(rawLine != null){
            int rawColon = rawLine.indexOf(':');
            String rawHead = rawColon >= 0 ? rawLine.substring(0, rawColon) : rawLine;
            java.util.regex.Matcher matcher = uidColorPattern.matcher(rawHead);
            while(matcher.find()){
                String parsed = extractShortUidToken(matcher.group(1));
                if(parsed != null) return parsed;
            }
        }

        if(head == null) return null;
        int pipe = head.lastIndexOf('|');
        String after = pipe >= 0 && pipe + 1 < head.length() ? head.substring(pipe + 1).trim() : head.trim();
        java.util.regex.Matcher matcher = uidTokenPattern.matcher(after);
        while(matcher.find()){
            String parsed = extractShortUidToken(matcher.group(1));
            if(parsed != null) return parsed;
        }
        return null;
    }

    private String extractShortUidAfterName(String head, String plainName){
        if(head == null || plainName == null || plainName.isEmpty()) return null;

        int nameIndex = head.indexOf(plainName);
        if(nameIndex < 0) return null;

        String tail = head.substring(nameIndex + plainName.length());
        java.util.regex.Matcher matcher = uidTokenPattern.matcher(tail);
        while(matcher.find()){
            String candidate = extractShortUidToken(matcher.group(1));
            if(candidate != null) return candidate;
        }
        return null;
    }

    private String normalizeSenderHead(String head, String uid){
        if(head == null) return "";
        String out = head.trim();
        while(out.startsWith("[")) out = out.substring(1).trim();
        while(out.endsWith("]")) out = out.substring(0, out.length() - 1).trim();

        if(uid != null && !uid.isEmpty()){
            String pattern = "(?i)(?:\\||\\s)+" + Pattern.quote(uid) + "(?:[Xx])?[^A-Za-z0-9]*$";
            out = out.replaceFirst(pattern, "").trim();
        }

        return out;
    }

    private static class ChatSnapshot{
        String uid;
        String senderName;
        String message;
    }

    private String extractShortUidToken(String token){
        if(token == null) return null;
        String clean = token.trim();
        if(clean.length() < 3) return null;

        StringBuilder prefix = new StringBuilder(8);
        for(int i = 0; i < clean.length(); i++){
            char c = clean.charAt(i);
            boolean alphaNum = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
            if(!alphaNum) break;
            prefix.append(c);
            if(prefix.length() >= 8) break;
        }

        if(prefix.length() < 3) return null;
        if(prefix.length() == 3) return normalizeShortUid(prefix.toString());
        if(prefix.length() >= 4 && (prefix.charAt(3) == 'X' || prefix.charAt(3) == 'x')){
            return normalizeShortUid(prefix.substring(0, 3));
        }

        return null;
    }

    private String extractUidFromPlayerName(String rawName){
        if(rawName == null) return null;
        String stripped = Strings.stripColors(rawName).trim();
        if(stripped.isEmpty()) return null;

        java.util.regex.Matcher matcher = nameUidTailPattern.matcher(stripped);
        if(matcher.find()){
            return extractShortUidToken(matcher.group(1));
        }
        return null;
    }

    private void appendDebugLine(String line){
        if(line == null || line.isEmpty()) return;
        String out = "[" + formatTime(Time.millis()) + "] " + line;
        debugLines.add(out);
        while(debugLines.size > 220){
            debugLines.remove(0);
        }
        if(debugContent != null) debugContent.markDirty();
    }

    private String resolvePid(Player p){
        if(p == null) return null;

        String known = pidByPlayerId.get(p.id);
        if(known != null) return known;

        String fallback = fallbackPid(p.id);
        pidByPlayerId.put(p.id, fallback);
        return fallback;
    }

    private String fallbackPid(int playerId){
        return "pid:" + playerId + "@" + normalizeServer(currentServer);
    }

    private void initStorage(){
        json.setIgnoreUnknownFields(true);
        dataDir = Vars.dataDirectory.child("ServerPlayerDataBase");
        dataDir.mkdirs();
        playersFile = dataDir.child("players.json");
        chatsDir = dataDir.child("chats");
        chatsDir.mkdirs();
        chatsIndexFile = chatsDir.child("chat_index.json");
        legacyChatsFile = dataDir.child("chats.json");
    }

    private void loadLocalData(){
        playersFileIntegrityState = integrityMissing;
        playersIntegrityIssues.clear();

        if(playersFile != null && playersFile.exists()){
            try{
                PlayerDbFile loaded = json.fromJson(PlayerDbFile.class, playersFile.readString("UTF-8"));
                if(loaded != null){
                    playersFileIntegrityState = verifyPlayerDbFileIntegrity(loaded);
                    if(playersFileIntegrityState == integrityMismatch){
                        playersIntegrityIssues.add("玩家库校验失败：players.json 可能已被修改。");
                    }else if(playersFileIntegrityState == integrityUnsupported){
                        playersIntegrityIssues.add("玩家库校验失败：players.json 使用了不支持的校验算法。");
                    }else if(playersFileIntegrityState == integrityMissing){
                        playersDirty = true;
                    }
                    playerDb.loadFrom(loaded);
                    if(playerDb.backfillUidFromPid() > 0) playersDirty = true;
                }
            }catch(Throwable t){
                Log.err("SPDB: failed to load players database.", t);
                playersIntegrityIssues.add("玩家库读取失败，无法完成完整性校验。");
            }
        }

        chatDb.loadStorage(chatsDir, chatsIndexFile, json);
        chatsDirty = chatsDirty || chatDb.hasPendingWrites();

        // Legacy migration: old single-file storage -> date sharding.
        if(legacyChatsFile != null && legacyChatsFile.exists()){
            try{
                ChatDbFile loaded = json.fromJson(ChatDbFile.class, legacyChatsFile.readString("UTF-8"));
                if(loaded != null){
                    int merged = chatDb.mergeFrom(loaded);
                    if(merged > 0){
                        chatsDirty = true;
                        saveDirty(true);
                    }
                }

                Fi migrated = dataDir.child("chats_legacy_migrated.json");
                try{
                    legacyChatsFile.copyTo(migrated);
                    legacyChatsFile.delete();
                }catch(Throwable ignored){
                }
            }catch(Throwable t){
                Log.err("SPDB: failed to migrate legacy chat database.", t);
            }
        }

        if(hasIntegrityIssues() && Vars.ui != null){
            Vars.ui.showInfoFade("SPDB: 检测到数据库完整性异常，请在查询窗口查看详情。");
        }
    }

    private void saveDirty(boolean force){
        if(dataDir == null) return;

        dataDir.mkdirs();

        if(force || playersDirty){
            try{
                PlayerDbFile out = playerDb.snapshot();
                signPlayerDbFile(out);
                playersFile.writeString(json.prettyPrint(out), false, "UTF-8");
                playersFileIntegrityState = integrityValid;
                playersDirty = false;
            }catch(Throwable t){
                Log.err("SPDB: failed to save players database.", t);
            }
        }

        if(force || chatsDirty){
            try{
                chatDb.flushToStorage(chatsDir, chatsIndexFile, json);
                chatsDirty = chatDb.hasPendingWrites();
            }catch(Throwable t){
                Log.err("SPDB: failed to save chat database.", t);
            }
        }
    }

    private void registerSettings(){
        if(Vars.ui == null || Vars.ui.settings == null) return;
        if(bekBundled) return;

        Core.settings.defaults(keyCollect, true);
        Core.settings.defaults(keyRecordChat, false);
        Core.settings.defaults(keyAutoTrace, true);
        Core.settings.defaults(keyShowAutoTraceDialog, false);

        Vars.ui.settings.addCategory("玩家数据库", Icon.zoom, this::bekBuildSettings);
    }

    /** Populates a {@link mindustry.ui.dialogs.SettingsMenuDialog.SettingsTable} with this mod's settings. */
    public void bekBuildSettings(SettingsMenuDialog.SettingsTable table){
        table.pref(new SpdbSettingsWidgets.HeaderSetting("数据采集", Icon.zoom));
        table.pref(new SpdbSettingsWidgets.IconCheckSetting(keyCollect, true, Icon.add, null));
        table.pref(new SpdbSettingsWidgets.IconCheckSetting(keyRecordChat, false, Icon.chat, null));

        table.pref(new SpdbSettingsWidgets.HeaderSetting("管理员增强", Icon.admin));
        table.pref(new SpdbSettingsWidgets.IconCheckSetting(keyAutoTrace, true, Icon.zoom, null));
        table.pref(new SpdbSettingsWidgets.IconCheckSetting(keyShowAutoTraceDialog, false, Icon.eyeSmall, null));

        table.pref(new SpdbSettingsWidgets.HeaderSetting("工具", Icon.wrench));
        table.pref(new SpdbSettingsWidgets.ActionButtonSetting("打开查询窗口", Icon.list, this::showStandaloneQueryDialog));
        table.pref(new SpdbSettingsWidgets.ActionButtonSetting("打开调试窗口", Icon.zoom, this::showStandaloneDebugDialog));
        table.pref(new SpdbSettingsWidgets.ActionButtonSetting("查找疑似小号（同IP）", Icon.players, this::showSameIpAltDialog));
        table.pref(new SpdbSettingsWidgets.ActionButtonSetting("立即保存数据库", Icon.save, () -> saveDirty(true)));
    }

    private void ensureOverlayAttached(){
        if(Vars.headless || Vars.ui == null || Vars.ui.hudGroup == null) return;
        if(!overlayUI.isInstalled()) return;

        if(overlayQueryContent == null){
            overlayQueryContent = new OverlayQueryContent();
        }
        if(overlayQueryWindow == null){
            overlayQueryWindow = overlayUI.registerWindow("玩家数据库-查询", overlayQueryContent.root, () -> Vars.state.isGame());
            overlayUI.tryConfigureWindow(overlayQueryWindow, false, true);
            overlayUI.setEnabledAndPinned(overlayQueryWindow, true, false);
        }

        if(debugContent == null){
            debugContent = new DebugContent();
        }
        if(overlayDebugWindow == null){
            overlayDebugWindow = overlayUI.registerWindow("玩家数据库-调试", debugContent.root, () -> Vars.state.isGame());
            overlayUI.tryConfigureWindow(overlayDebugWindow, false, true);
            overlayUI.setEnabledAndPinned(overlayDebugWindow, false, false);
        }
    }

    private boolean compactUi(){
        return uiWidth() < 980f || uiHeight() < 700f;
    }

    private float uiWidth(){
        return Core.graphics == null ? 1280f : Core.graphics.getWidth();
    }

    private float uiHeight(){
        return Core.graphics == null ? 720f : Core.graphics.getHeight();
    }

    private float fitDialogWidth(float desktopPref){
        return Math.max(320f, Math.min(desktopPref, uiWidth() - 48f));
    }

    private float fitDialogHeight(float desktopPref, float min){
        return Math.max(min, Math.min(desktopPref, uiHeight() - 108f));
    }

    private float fitPaneHeight(float desktopPref, float min){
        return Math.max(min, Math.min(desktopPref, uiHeight() * 0.36f));
    }

    private float queryDialogWidth(){
        float width = uiWidth();
        float margin = width < 900f ? 20f : 44f;
        float target = width * (width < 1200f ? 0.96f : 0.9f);
        return Math.max(360f, Math.min(target, width - margin));
    }

    private float queryDialogHeight(){
        float height = uiHeight();
        float margin = height < 700f ? 42f : 76f;
        float target = height * (height < 820f ? 0.92f : 0.88f);
        return Math.max(320f, Math.min(target, height - margin));
    }

    private void showStandaloneQueryDialog(){
        if(Vars.ui == null) return;

        if(fallbackQueryDialog == null){
            fallbackQueryDialog = new BaseDialog("玩家数据库 / 聊天记录");
            fallbackQueryDialog.addCloseButton();
            fallbackQueryContent = new QueryContent();
        }

        if(fallbackQueryContent == null){
            fallbackQueryContent = new QueryContent();
        }
        fallbackQueryContent.ensureResponsiveLayout();

        fallbackQueryDialog.cont.clear();
        fallbackQueryDialog.cont.add(fallbackQueryContent.root)
                .grow()
                .width(queryDialogWidth())
                .height(queryDialogHeight())
                .minWidth(0f)
                .minHeight(0f);

        fallbackQueryDialog.show();
    }

    private void showStandaloneDebugDialog(){
        if(Vars.ui == null) return;

        if(fallbackDebugDialog == null){
            fallbackDebugDialog = new BaseDialog("SPDB 调试面板");
            fallbackDebugDialog.addCloseButton();
            DebugContent content = new DebugContent();
            fallbackDebugDialog.cont.add(content.root)
                .grow()
                .width(fitDialogWidth(980f))
                .height(fitDialogHeight(700f, 320f))
                .minWidth(0f)
                .minHeight(0f);
        }

        fallbackDebugDialog.show();
    }

    private void showSameIpAltDialog(){
        if(Vars.ui == null) return;

        BaseDialog dialog = new BaseDialog("疑似小号（同IP账号）");
        dialog.addCloseButton();
        dialog.cont.defaults().left().pad(4f);

        Table result = new Table();
        result.left().top();

        dialog.cont.table(Styles.black3, top -> {
            top.left().defaults().left().pad(6f);
            top.add("一键查找数据库中同IP账号，按账号数降序列出。")
                .left()
                .growX()
                .wrap();
            top.button("刷新", Icon.refresh, Styles.defaultt, () -> refreshSameIpAltResult(result))
                .height(40f)
                .padLeft(8f);
        }).growX().row();

        dialog.cont.pane(result)
            .scrollX(false)
            .grow()
            .width(fitDialogWidth(1120f))
            .height(fitDialogHeight(710f, 320f))
            .minWidth(0f)
            .minHeight(0f)
            .row();

        refreshSameIpAltResult(result);
        dialog.show();
    }

    private void refreshSameIpAltResult(Table result){
        if(result == null) return;

        result.clear();
        result.left().top().defaults().left().pad(4f).growX();

        Seq<SameIpGroup> groups = playerDb.findSameIpGroups(2);
        if(groups.isEmpty()){
            result.add("当前没有发现同IP账号记录。").left().wrap().row();
            return;
        }

        int accountCount = 0;
        for(SameIpGroup group : groups){
            accountCount += group.players.size;
        }
        result.add("发现 " + groups.size + " 组疑似小号（同IP），涉及 " + accountCount + " 个账号。")
            .left()
            .wrap()
            .row();

        for(SameIpGroup group : groups){
            String geo = lookupIpGeoCached(group.ip);
            String ipTitle = "IP: " + group.ip + (geo == null || geo.isEmpty() ? "" : " (" + geo + ")");

            result.table(Styles.black3, card -> {
                card.left().top().defaults().left().pad(3f).growX();
                card.add(escapeMarkup(ipTitle)).left().wrap().row();
                card.add("账号数: " + group.players.size + " | 最近出现: " + formatTime(group.latestSeen)).left().wrap().row();

                for(int i = 0; i < group.players.size; i++){
                    PlayerRecord rec = group.players.get(i);
                    String line = (i + 1) + ". " + bestPlayerName(rec)
                        + " | UID: " + (rec.uid == null ? "(none)" : rec.uid)
                        + " | PID: " + rec.pid
                        + " | 最后出现: " + formatTime(rec.lastSeen);
                    card.add(escapeMarkup(line)).left().wrap().row();
                }
            }).growX().padTop(6f).row();
        }
    }

    private static String bestPlayerName(PlayerRecord rec){
        if(rec == null || rec.names == null || rec.names.isEmpty()) return "(unknown)";
        return safeName(rec.names.peek());
    }

    private void importPlayersFromFile(){
        Vars.platform.showFileChooser(true, "json", file -> {
            try{
                PlayerDbFile incoming = json.fromJson(PlayerDbFile.class, file.readString("UTF-8"));
                if(incoming == null || incoming.players == null){
                    Vars.ui.showErrorMessage("SPDB: 玩家库文件无效。");
                    return;
                }

                int state = verifyPlayerDbFileIntegrity(incoming);
                if(state == integrityMismatch){
                    Vars.ui.showErrorMessage("SPDB: 玩家库校验失败，文件疑似被修改。\n请重新导入原始导出文件。");
                    return;
                }
                if(state == integrityUnsupported){
                    Vars.ui.showErrorMessage("SPDB: 玩家库使用了不支持的校验算法，拒绝导入。");
                    return;
                }
                if(state == integrityMissing){
                    Vars.ui.showInfoFade("SPDB: 玩家库无校验元数据，按兼容模式导入。");
                }

                int merged = playerDb.mergeFrom(incoming);
                if(merged > 0){
                    playersDirty = true;
                    Vars.ui.showInfoFade("SPDB: 玩家库导入/合并条目: " + merged);
                }else{
                    Vars.ui.showInfoFade("SPDB: 没有可新增的玩家数据。");
                }
            }catch(Throwable t){
                Log.err("SPDB: failed to import players.", t);
                Vars.ui.showErrorMessage("SPDB: 玩家库导入失败。");
            }
        });
    }

    private void exportPlayersToFile(){
        Vars.platform.showFileChooser(false, "json", file -> {
            try{
                PlayerDbFile out = playerDb.snapshot();
                signPlayerDbFile(out);
                file.writeString(json.prettyPrint(out), false, "UTF-8");
                Vars.ui.showInfoFade("SPDB: 玩家库导出完成。");
            }catch(Throwable t){
                Log.err("SPDB: failed to export players.", t);
                Vars.ui.showErrorMessage("SPDB: 玩家库导出失败。");
            }
        });
    }

    private void importChatsFromFile(){
        Vars.platform.showFileChooser(true, "json", file -> {
            try{
                ChatDbFile incoming = json.fromJson(ChatDbFile.class, file.readString("UTF-8"));
                if(incoming == null || incoming.entries == null){
                    Vars.ui.showErrorMessage("SPDB: 聊天库文件无效。");
                    return;
                }

                int state = verifyChatDbFileIntegrity(incoming);
                if(state == integrityMismatch){
                    Vars.ui.showErrorMessage("SPDB: 聊天库校验失败，文件疑似被修改。\n请重新导入原始导出文件。");
                    return;
                }
                if(state == integrityUnsupported){
                    Vars.ui.showErrorMessage("SPDB: 聊天库使用了不支持的校验算法，拒绝导入。");
                    return;
                }
                if(state == integrityMissing){
                    Vars.ui.showInfoFade("SPDB: 聊天库无校验元数据，按兼容模式导入。");
                }

                int merged = chatDb.mergeFrom(incoming);
                if(merged > 0){
                    chatsDirty = true;
                    Vars.ui.showInfoFade("SPDB: 聊天库导入/合并条目: " + merged);
                }else{
                    Vars.ui.showInfoFade("SPDB: 没有可新增的聊天数据。");
                }
            }catch(Throwable t){
                Log.err("SPDB: failed to import chats.", t);
                Vars.ui.showErrorMessage("SPDB: 聊天库导入失败。");
            }
        });
    }

    private void exportChatsToFile(){
        Vars.platform.showFileChooser(false, "json", file -> {
            try{
                ChatDbFile out = chatDb.snapshot();
                signChatDbFile(out);
                file.writeString(json.prettyPrint(out), false, "UTF-8");
                Vars.ui.showInfoFade("SPDB: 聊天库导出完成。");
            }catch(Throwable t){
                Log.err("SPDB: failed to export chats.", t);
                Vars.ui.showErrorMessage("SPDB: 聊天库导出失败。");
            }
        });
    }

    private boolean hasIntegrityIssues(){
        return !playersIntegrityIssues.isEmpty() || chatDb.hasIntegrityIssues();
    }

    private void showIntegrityDialog(){
        if(Vars.ui == null) return;

        BaseDialog dialog = new BaseDialog("SPDB 数据完整性");
        dialog.addCloseButton();
        float width = fitDialogWidth(960f);
        float height = fitDialogHeight(660f, 320f);
        dialog.cont.pane(p -> {
            p.left().top().defaults().left().pad(4f).growX();

            p.add("玩家库文件: " + integrityStateText(playersFileIntegrityState)).left().row();
            p.add("聊天索引文件: " + integrityStateText(chatDb.indexIntegrityState())).left().row();
            p.add("聊天分片文件: 已校验 " + chatDb.shardsChecked() + " 个（通过 " + chatDb.shardsValid() + " / 缺少元数据 " + chatDb.shardsMissing() + " / 校验失败 " + chatDb.shardsMismatch() + " / 算法不支持 " + chatDb.shardsUnsupported() + "）").left().wrap().row();

            if(!playersIntegrityIssues.isEmpty() || chatDb.hasIntegrityIssues()){
                p.add("异常详情:").padTop(6f).left().row();
                for(String issue : playersIntegrityIssues){
                    p.add("- " + escapeMarkup(issue)).left().wrap().row();
                }
                for(String issue : chatDb.integrityIssues()){
                    p.add("- " + escapeMarkup(issue)).left().wrap().row();
                }
            }else{
                p.add("当前未发现完整性异常。\n如导入文件被改动，将在导入时直接拦截。", Styles.outlineLabel).left().padTop(6f).wrap().row();
            }
        }).grow().width(width).height(height).minWidth(0f).minHeight(0f);
        dialog.show();
    }

    private static String integrityStateText(int state){
        switch(state){
            case integrityValid:
                return "通过";
            case integrityMissing:
                return "缺少元数据";
            case integrityUnsupported:
                return "算法不支持";
            default:
                return "校验失败";
        }
    }

    private static int verifyPlayerDbFileIntegrity(PlayerDbFile file){
        if(file == null) return integrityMismatch;
        return integrityState(file.integrityAlgo, file.integritySha256, computePlayerDbSha256(file));
    }

    private static int verifyChatDbFileIntegrity(ChatDbFile file){
        if(file == null) return integrityMismatch;
        return integrityState(file.integrityAlgo, file.integritySha256, computeChatDbSha256(file));
    }

    private static int verifyChatDayFileIntegrity(ChatDayFile file){
        if(file == null) return integrityMismatch;
        return integrityState(file.integrityAlgo, file.integritySha256, computeChatDaySha256(file));
    }

    private static int verifyChatIndexFileIntegrity(ChatIndexFile file){
        if(file == null) return integrityMismatch;
        return integrityState(file.integrityAlgo, file.integritySha256, computeChatIndexSha256(file));
    }

    private static int integrityState(String algo, String sha, String expectedSha){
        if(algo == null || algo.trim().isEmpty() || sha == null || sha.trim().isEmpty()) return integrityMissing;
        if(!integrityAlgorithm.equals(algo.trim())) return integrityUnsupported;
        return expectedSha.equalsIgnoreCase(sha.trim()) ? integrityValid : integrityMismatch;
    }

    private static void signPlayerDbFile(PlayerDbFile file){
        if(file == null) return;
        file.schema = Math.max(file.schema, 2);
        file.integrityAlgo = integrityAlgorithm;
        file.integrityTime = Time.millis();
        file.integritySha256 = computePlayerDbSha256(file);
    }

    private static void signChatDbFile(ChatDbFile file){
        if(file == null) return;
        file.schema = Math.max(file.schema, 2);
        file.integrityAlgo = integrityAlgorithm;
        file.integrityTime = Time.millis();
        file.integritySha256 = computeChatDbSha256(file);
    }

    private static void signChatDayFile(ChatDayFile file){
        if(file == null) return;
        file.schema = Math.max(file.schema, 2);
        file.integrityAlgo = integrityAlgorithm;
        file.integrityTime = Time.millis();
        file.integritySha256 = computeChatDaySha256(file);
    }

    private static void signChatIndexFile(ChatIndexFile file){
        if(file == null) return;
        file.schema = Math.max(file.schema, 2);
        file.integrityAlgo = integrityAlgorithm;
        file.integrityTime = Time.millis();
        file.integritySha256 = computeChatIndexSha256(file);
    }

    private static String computePlayerDbSha256(PlayerDbFile file){
        StringBuilder sb = new StringBuilder(2048);
        sb.append("SPDB-PLAYERS-V1\n");
        sb.append(file.schema).append('\n');
        sb.append(file.integrityAlgo == null ? "" : file.integrityAlgo).append('\n');
        sb.append(file.integrityTime).append('\n');

        ArrayList<String> pids = new ArrayList<>();
        if(file.players != null){
            for(String pid : file.players.keys()){
                String norm = normalizePid(pid);
                if(norm != null) pids.add(norm);
            }
        }
        Collections.sort(pids);

        sb.append(pids.size()).append('\n');
        for(String pid : pids){
            PlayerRecord rec = file.players.get(pid);
            appendDigestToken(sb, pid);
            sb.append('\n');
            appendDigestToken(sb, rec == null ? null : normalizeShortUid(rec.uid));
            sb.append('\n');
            sb.append(rec == null ? 0L : rec.firstSeen).append('\n');
            sb.append(rec == null ? 0L : rec.lastSeen).append('\n');
            appendDigestSeq(sb, rec == null ? null : rec.names);
            appendDigestSeq(sb, rec == null ? null : rec.ips);
            appendDigestSeq(sb, rec == null ? null : rec.servers);
        }

        return sha256Hex(sb.toString());
    }

    private static String computeChatDbSha256(ChatDbFile file){
        StringBuilder sb = new StringBuilder(4096);
        sb.append("SPDB-CHAT-EXPORT-V1\n");
        sb.append(file.schema).append('\n');
        sb.append(file.integrityAlgo == null ? "" : file.integrityAlgo).append('\n');
        sb.append(file.integrityTime).append('\n');

        Seq<ChatEntry> sorted = sortedEntries(file.entries);
        sb.append(sorted.size).append('\n');
        for(ChatEntry entry : sorted){
            appendDigestEntry(sb, entry);
        }
        return sha256Hex(sb.toString());
    }

    private static String computeChatDaySha256(ChatDayFile file){
        StringBuilder sb = new StringBuilder(4096);
        sb.append("SPDB-CHAT-DAY-V1\n");
        sb.append(file.schema).append('\n');
        sb.append(file.integrityAlgo == null ? "" : file.integrityAlgo).append('\n');
        sb.append(file.integrityTime).append('\n');
        appendDigestToken(sb, file.date == null ? "" : file.date);
        sb.append('\n');

        Seq<ChatEntry> sorted = sortedEntries(file.entries);
        sb.append(sorted.size).append('\n');
        for(ChatEntry entry : sorted){
            appendDigestEntry(sb, entry);
        }
        return sha256Hex(sb.toString());
    }

    private static String computeChatIndexSha256(ChatIndexFile file){
        StringBuilder sb = new StringBuilder(2048);
        sb.append("SPDB-CHAT-INDEX-V1\n");
        sb.append(file.schema).append('\n');
        sb.append(file.integrityAlgo == null ? "" : file.integrityAlgo).append('\n');
        sb.append(file.integrityTime).append('\n');
        sb.append(file.totalEntries).append('\n');
        sb.append(file.updatedAt).append('\n');

        ArrayList<String> uids = new ArrayList<>();
        if(file.uidDates != null){
            for(String uid : file.uidDates.keys()){
                String norm = normalizeUid(uid);
                if(norm != null) uids.add(norm);
            }
        }
        Collections.sort(uids);

        sb.append(uids.size()).append('\n');
        for(String uid : uids){
            appendDigestToken(sb, uid);
            sb.append('\n');

            Object rawDates = file.uidDates.get(uid);
            ArrayList<String> sortedDates = new ArrayList<>();
            if(rawDates instanceof Seq<?>){
                for(Object item : (Seq<?>)rawDates){
                    String date = normalizeDigestDateToken(item);
                    if(ChatDatabase.isValidDateKey(date)) sortedDates.add(date);
                }
            }
            Collections.sort(sortedDates);

            sb.append(sortedDates.size()).append('\n');
            for(String date : sortedDates){
                appendDigestToken(sb, date);
                sb.append('\n');
            }
        }

        return sha256Hex(sb.toString());
    }

    private static Seq<ChatEntry> sortedEntries(Seq<ChatEntry> source){
        Seq<ChatEntry> sorted = new Seq<>();
        if(source != null){
            for(ChatEntry entry : source){
                if(entry == null) continue;
                sorted.add(entry);
            }
        }
        sorted.sort(chatEntryComparator);
        return sorted;
    }

    private static void appendDigestEntry(StringBuilder sb, ChatEntry entry){
        appendDigestToken(sb, normalizeUid(entry == null ? null : entry.uid));
        sb.append('\n');
        appendDigestToken(sb, safeName(entry == null ? null : entry.senderName));
        sb.append('\n');
        appendDigestToken(sb, entry == null ? null : entry.message);
        sb.append('\n');
        appendDigestToken(sb, normalizeServer(entry == null ? null : entry.server));
        sb.append('\n');
        sb.append(entry == null ? 0L : entry.time).append('\n');
    }

    private static void appendDigestSeq(StringBuilder sb, Seq<?> seq){
        if(seq == null){
            sb.append("-1\n");
            return;
        }
        sb.append(seq.size).append('\n');
        for(Object value : seq){
            appendDigestToken(sb, normalizeDigestScalar(value));
            sb.append('\n');
        }
    }

    private static String normalizeDigestDateToken(Object value){
        String text = normalizeDigestScalar(value);
        if(text == null) return null;
        text = text.trim();
        if(text.isEmpty()) return null;
        return text;
    }

    private static String normalizeDigestScalar(Object value){
        Object cur = value;
        int guard = 0;

        while(cur instanceof ObjectMap<?, ?> && guard < 8){
            @SuppressWarnings("rawtypes")
            ObjectMap map = (ObjectMap)cur;
            if(!map.containsKey("value")) break;

            if(!(map.size <= 2 || (map.containsKey("class") && map.containsKey("value")))){
                break;
            }

            cur = map.get("value");
            guard++;
        }

        return cur == null ? null : String.valueOf(cur);
    }

    private static void appendDigestToken(StringBuilder sb, String value){
        if(value == null){
            sb.append("-1:");
            return;
        }
        int byteLen = value.getBytes(StandardCharsets.UTF_8).length;
        sb.append(byteLen).append(':').append(value);
    }

    private static String sha256Hex(String text){
        try{
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest((text == null ? "" : text).getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(digest.length * 2);
            for(byte b : digest){
                int v = b & 0xff;
                if(v < 16) out.append('0');
                out.append(Integer.toHexString(v));
            }
            return out.toString();
        }catch(Throwable t){
            throw new RuntimeException("SPDB: SHA-256 unavailable", t);
        }
    }

    private static final Comparator<ChatEntry> chatEntryComparator = (a, b) -> {
        int byTime = Long.compare(a == null ? 0L : a.time, b == null ? 0L : b.time);
        if(byTime != 0) return byTime;
        int byUid = safeString(a == null ? null : a.uid).compareTo(safeString(b == null ? null : b.uid));
        if(byUid != 0) return byUid;
        int byServer = safeString(a == null ? null : a.server).compareTo(safeString(b == null ? null : b.server));
        if(byServer != 0) return byServer;
        int byName = safeString(a == null ? null : a.senderName).compareTo(safeString(b == null ? null : b.senderName));
        if(byName != 0) return byName;
        return safeString(a == null ? null : a.message).compareTo(safeString(b == null ? null : b.message));
    };

    private static String safeString(String value){
        return value == null ? "" : value;
    }

    private static String safeLine(String value, int maxChars){
        if(value == null) return "";
        String oneLine = value.replace('\n', ' ').replace('\r', ' ');
        if(maxChars <= 0 || oneLine.length() <= maxChars) return oneLine;
        return oneLine.substring(0, maxChars) + "...";
    }

    private class QueryContent{
        final Table root = new Table();
        final Table playerTab = new Table();
        final Table chatTab = new Table();
        final Table uidResult = new Table();
        final Table pidResult = new Table();
        final Table nameResult = new Table();
        final Table ipResult = new Table();
        final Table chatResult = new Table();
        final Table allPlayersResult = new Table();
        final Table allChatsResult = new Table();

        TextField uidField;
        TextField pidField;
        TextField nameField;
        TextField ipField;
        TextField chatUidField;
        TextField fillPidField;
        TextField fillUidField;
        TextField mergeFromUidField;
        TextField mergeToUidField;
        int activeTab;
        int allPlayersPage;
        int allChatsPage;
        boolean compactLayout;
        boolean mediumLayout;

        private static final int playersPageSize = 18;
        private static final int chatsPageSize = 22;

        QueryContent(){
            build();
        }

        void ensureResponsiveLayout(){
            boolean compact = compactUi();
            boolean medium = !compact && uiWidth() < 1360f;
            if(compact != compactLayout || medium != mediumLayout){
                build();
            }
        }

        private void build(){
            root.clear();
            root.top().left();
            root.defaults().left().pad(4f);
            activeTab = 0;
            compactLayout = compactUi();
            mediumLayout = !compactLayout && uiWidth() < 1360f;
            boolean compact = compactLayout;
            boolean medium = mediumLayout;

            root.table(Styles.black3, top -> {
                top.left().defaults().pad(6f).height(compact ? 40f : 44f).growX();
                if(compact){
                    top.button("导入玩家库", Icon.download, Styles.defaultt, ServerPlayerDataBaseMod.this::importPlayersFromFile).growX().row();
                    top.button("导出玩家库", Icon.upload, Styles.defaultt, ServerPlayerDataBaseMod.this::exportPlayersToFile).growX().row();
                    top.button("导入聊天库", Icon.download, Styles.defaultt, ServerPlayerDataBaseMod.this::importChatsFromFile).growX().row();
                    top.button("导出聊天库", Icon.upload, Styles.defaultt, ServerPlayerDataBaseMod.this::exportChatsToFile).growX().row();
                    top.button("完整性状态", Icon.list, Styles.defaultt, ServerPlayerDataBaseMod.this::showIntegrityDialog).growX();
                }else if(medium){
                    top.table(row -> {
                        row.left().defaults().height(42f).padRight(6f).growX();
                        row.button("导入玩家库", Icon.download, Styles.defaultt, ServerPlayerDataBaseMod.this::importPlayersFromFile).growX();
                        row.button("导出玩家库", Icon.upload, Styles.defaultt, ServerPlayerDataBaseMod.this::exportPlayersToFile).growX();
                        row.button("完整性状态", Icon.list, Styles.defaultt, ServerPlayerDataBaseMod.this::showIntegrityDialog).growX();
                    }).growX().row();
                    top.table(row -> {
                        row.left().defaults().height(42f).padRight(6f).growX();
                        row.button("导入聊天库", Icon.download, Styles.defaultt, ServerPlayerDataBaseMod.this::importChatsFromFile).growX();
                        row.button("导出聊天库", Icon.upload, Styles.defaultt, ServerPlayerDataBaseMod.this::exportChatsToFile).growX();
                    }).growX();
                }else{
                    top.button("导入玩家库", Icon.download, Styles.defaultt, ServerPlayerDataBaseMod.this::importPlayersFromFile);
                    top.button("导出玩家库", Icon.upload, Styles.defaultt, ServerPlayerDataBaseMod.this::exportPlayersToFile);
                    top.button("导入聊天库", Icon.download, Styles.defaultt, ServerPlayerDataBaseMod.this::importChatsFromFile);
                    top.button("导出聊天库", Icon.upload, Styles.defaultt, ServerPlayerDataBaseMod.this::exportChatsToFile);
                    top.button("完整性状态", Icon.list, Styles.defaultt, ServerPlayerDataBaseMod.this::showIntegrityDialog);
                }
            }).growX().padBottom(6f).row();

            root.table(Styles.black3, tabs -> {
                tabs.left().defaults().height(40f).pad(6f).growX();
                tabs.button("玩家库", Icon.players, Styles.togglet, () -> activeTab = 0).checked(b -> activeTab == 0).growX();
                tabs.button("聊天记录", Icon.chat, Styles.togglet, () -> activeTab = 1).checked(b -> activeTab == 1).growX();
            }).growX().padBottom(8f).row();

            playerTab.clear();
            playerTab.left().top().defaults().left().pad(4f).growX();
            allPlayersPage = 0;

            playerTab.table(Styles.black3, box -> {
                box.left().top().defaults().left().pad(4f).growX();
                box.add("UID 查询（3位）").left().row();
                if(compact){
                    box.table(line -> {
                        line.left().defaults().left().padRight(6f);
                        uidField = line.field("", text -> {}).growX().get();
                        uidField.setMessageText("输入三位 UID，例如 dNF");
                    }).growX().row();
                    box.table(line -> {
                        line.left().defaults().left().padRight(6f).growX();
                        line.button("查询 UID", this::refreshUidResult).height(38f).growX();
                        line.button("按UID合并", this::showMergeByUidPreview).height(38f).growX();
                    }).growX().row();
                }else{
                    box.table(line -> {
                        line.left().defaults().left().padRight(6f);
                        uidField = line.field("", text -> {}).growX().get();
                        uidField.setMessageText("输入三位 UID，例如 dNF");
                        line.button("查询 UID", this::refreshUidResult).height(38f);
                        line.button("按UID合并", this::showMergeByUidPreview).height(38f);
                    }).growX().row();
                }
                box.pane(uidResult).scrollX(false).maxHeight(fitPaneHeight(260f, 150f)).growX().row();
            }).growX().row();

            playerTab.table(Styles.black3, box -> {
                box.left().top().defaults().left().pad(4f).growX();
                box.add("UID 工具").left().row();

                box.table(line -> {
                    line.left().defaults().left().padRight(6f).growX();
                    line.button("一键合并全部重复UID", this::mergeAllSameUid).height(36f).growX();
                }).growX().row();

                if(compact){
                    box.table(line -> {
                        line.left().defaults().left().padRight(6f);
                        fillPidField = line.field("", text -> {}).growX().get();
                        fillPidField.setMessageText("输入 PID（仅填充 UID 为空的记录）");
                    }).growX().row();
                    box.table(line -> {
                        line.left().defaults().left().padRight(6f).growX();
                        fillUidField = line.field("", text -> {}).growX().get();
                        fillUidField.setMessageText("UID");
                        line.button("填充UID", this::fillMissingUidByPid).height(36f).growX();
                    }).growX().row();

                    box.table(line -> {
                        line.left().defaults().left().padRight(6f).growX();
                        mergeFromUidField = line.field("", text -> {}).growX().get();
                        mergeFromUidField.setMessageText("来源UID");
                        mergeToUidField = line.field("", text -> {}).growX().get();
                        mergeToUidField.setMessageText("目标UID");
                    }).growX().row();
                    box.table(line -> {
                        line.left().defaults().left().padRight(6f).growX();
                        line.button("合并两个UID", this::mergeTwoUids).height(36f).growX();
                    }).growX().row();
                }else{
                    box.table(line -> {
                        line.left().defaults().left().padRight(6f);
                        fillPidField = line.field("", text -> {}).growX().get();
                        fillPidField.setMessageText("输入 PID（仅填充 UID 为空的记录）");
                        fillUidField = line.field("", text -> {}).width(130f).get();
                        fillUidField.setMessageText("UID");
                        line.button("填充UID", this::fillMissingUidByPid).height(36f);
                    }).growX().row();

                    box.table(line -> {
                        line.left().defaults().left().padRight(6f);
                        mergeFromUidField = line.field("", text -> {}).width(130f).get();
                        mergeFromUidField.setMessageText("来源UID");
                        mergeToUidField = line.field("", text -> {}).width(130f).get();
                        mergeToUidField.setMessageText("目标UID");
                        line.button("合并两个UID", this::mergeTwoUids).height(36f);
                    }).growX().row();
                }
            }).growX().row();

            playerTab.table(Styles.black3, box -> {
                box.left().top().defaults().left().pad(4f).growX();
                box.add("PID 查询").padTop(4f).row();
                if(compact){
                    box.table(line -> {
                        line.left().defaults().left().padRight(6f);
                        pidField = line.field("", text -> {}).growX().get();
                        pidField.setMessageText("输入 PID（Trace/数据库中的 pid）");
                    }).growX().row();
                    box.table(line -> {
                        line.left().defaults().left().padRight(6f).growX();
                        line.button("查询 PID", this::refreshPidResult).height(38f).growX();
                    }).growX().row();
                }else{
                    box.table(line -> {
                        line.left().defaults().left().padRight(6f);
                        pidField = line.field("", text -> {}).growX().get();
                        pidField.setMessageText("输入 PID（Trace/数据库中的 pid）");
                        line.button("查询 PID", this::refreshPidResult).height(38f);
                    }).growX().row();
                }
                box.add(pidResult).growX().row();
            }).growX().row();

            playerTab.table(Styles.black3, box -> {
                box.left().top().defaults().left().pad(4f).growX();
                box.add("名字查询").padTop(4f).row();
                if(compact){
                    box.table(line -> {
                        line.left().defaults().left().padRight(6f);
                        nameField = line.field("", text -> {}).growX().get();
                        nameField.setMessageText("输入玩家名或片段（支持模糊）");
                    }).growX().row();
                    box.table(line -> {
                        line.left().defaults().left().padRight(6f).growX();
                        line.button("查询名字", this::refreshNameResult).height(38f).growX();
                    }).growX().row();
                }else{
                    box.table(line -> {
                        line.left().defaults().left().padRight(6f);
                        nameField = line.field("", text -> {}).growX().get();
                        nameField.setMessageText("输入玩家名或片段（支持模糊）");
                        line.button("查询名字", this::refreshNameResult).height(38f);
                    }).growX().row();
                }
                box.pane(nameResult).scrollX(false).maxHeight(fitPaneHeight(240f, 140f)).growX().row();
            }).growX().row();

            playerTab.table(Styles.black3, box -> {
                box.left().top().defaults().left().pad(4f).growX();
                box.add("IP 查询").padTop(4f).row();
                if(compact){
                    box.table(line -> {
                        line.left().defaults().left().padRight(6f);
                        ipField = line.field("", text -> {}).growX().get();
                        ipField.setMessageText("输入 IP");
                    }).growX().row();
                    box.table(line -> {
                        line.left().defaults().left().padRight(6f).growX();
                        line.button("查询 IP", this::refreshIpResult).height(38f).growX();
                    }).growX().row();
                }else{
                    box.table(line -> {
                        line.left().defaults().left().padRight(6f);
                        ipField = line.field("", text -> {}).growX().get();
                        ipField.setMessageText("输入 IP");
                        line.button("查询 IP", this::refreshIpResult).height(38f);
                    }).growX().row();
                }
                box.pane(ipResult).scrollX(false).maxHeight(fitPaneHeight(240f, 140f)).growX().row();
            }).growX().row();

            playerTab.add("全部玩家（按最后出现时间）").padTop(8f).row();
            playerTab.table(line -> {
                line.left().defaults().left().padRight(6f).growX();
                line.button("上一页", () -> {
                    if(allPlayersPage > 0) allPlayersPage--;
                    refreshAllPlayersResult();
                }).height(36f).growX();
                line.button("下一页", () -> {
                    allPlayersPage++;
                    refreshAllPlayersResult();
                }).height(36f).growX();
                if(compact){
                    line.row();
                    line.button("刷新", this::refreshAllPlayersResult).height(36f).growX().colspan(2);
                }else{
                    line.button("刷新", this::refreshAllPlayersResult).height(36f);
                }
            }).growX().row();
            playerTab.add(allPlayersResult).growX().growY().row();

            chatTab.clear();
            chatTab.left().top().defaults().left().pad(4f).growX();
            allChatsPage = 0;
            chatTab.add("聊天记录查询（按 UID）").row();
            if(compact){
                chatTab.table(line -> {
                    line.left().defaults().left().padRight(6f);
                    chatUidField = line.field("", text -> {}).growX().get();
                    chatUidField.setMessageText("输入 UID");
                }).growX().row();
                chatTab.table(line -> {
                    line.left().defaults().left().padRight(6f).growX();
                    line.button("查询聊天", this::refreshChatResult).height(38f).growX();
                }).growX().row();
            }else{
                chatTab.table(line -> {
                    line.left().defaults().left().padRight(6f);
                    chatUidField = line.field("", text -> {}).growX().get();
                    chatUidField.setMessageText("输入 UID");
                    line.button("查询聊天", this::refreshChatResult).height(38f);
                }).growX().row();
            }
            chatTab.pane(chatResult).scrollX(false).maxHeight(fitPaneHeight(260f, 140f)).growX().row();

            chatTab.add("全部聊天（按时间倒序）").padTop(8f).row();
            chatTab.table(line -> {
                line.left().defaults().left().padRight(6f).growX();
                line.button("上一页", () -> {
                    if(allChatsPage > 0) allChatsPage--;
                    refreshAllChatsResult();
                }).height(36f).growX();
                line.button("下一页", () -> {
                    allChatsPage++;
                    refreshAllChatsResult();
                }).height(36f).growX();
                if(compact){
                    line.row();
                    line.button("刷新", this::refreshAllChatsResult).height(36f).growX().colspan(2);
                }else{
                    line.button("刷新", this::refreshAllChatsResult).height(36f);
                }
            }).growX().row();
            chatTab.add(allChatsResult).growX().growY().row();

            root.stack(
                new Table(t -> t.pane(playerTab).scrollX(false).grow()),
                new Table(t -> t.pane(chatTab).scrollX(false).grow())
            ).update(stack -> {
                if(stack.getChildren().size >= 2){
                    stack.getChildren().get(0).visible = activeTab == 0;
                    stack.getChildren().get(1).visible = activeTab == 1;
                }
            }).grow().minWidth(0f).minHeight(0f).padBottom(compact ? 10f : 6f);
            root.row();
            root.add().height(compact ? 22f : 14f).growX();

            refreshUidResult();
            refreshPidResult();
            refreshNameResult();
            refreshIpResult();
            refreshChatResult();
            refreshAllPlayersResult();
            refreshAllChatsResult();
        }

        private void refreshUidResult(){
            uidResult.clear();
            uidResult.left().top();

            String uid = normalizeShortUid(uidField == null ? null : uidField.getText());
            if(uid == null){
                uidResult.add("请输入三位 UID 后查询。").left();
                return;
            }

            Seq<PlayerRecord> list = playerDb.findByUid(uid);
            if(list.isEmpty()){
                uidResult.add("未找到该 UID。").left();
                return;
            }

            uidResult.add("匹配到 " + list.size + " 个 PID").left().row();
            for(PlayerRecord record : list){
                uidResult.table(Styles.black3, box -> renderPlayerDetailCard(box, record)).growX().padTop(4f).row();
            }
        }

        private void showMergeByUidPreview(){
            String uid = normalizeShortUid(uidField == null ? null : uidField.getText());
            if(uid == null){
                Vars.ui.showInfoFade("SPDB: 请输入三位 UID。");
                return;
            }

            Seq<PlayerRecord> list = playerDb.findByUid(uid);
            if(list.size <= 1){
                Vars.ui.showInfoFade("SPDB: UID " + uid + " 无需合并。");
                return;
            }

            BaseDialog dialog = new BaseDialog("按UID合并预览");
            dialog.addCloseButton();
            dialog.cont.defaults().left().pad(4f);
            float previewWidth = fitDialogWidth(920f);
            float previewHeight = fitDialogHeight(520f, 260f);

            PlayerRecord target = list.first();
            dialog.cont.add("UID: " + uid + "，将合并 " + list.size + " 条 PID，保留最近活跃 PID: " + target.pid).left().wrap().row();
            dialog.cont.pane(p -> {
                p.left().top().defaults().left().pad(3f).growX();
                for(PlayerRecord rec : list){
                    boolean keep = rec == target;
                    String name = rec.names.isEmpty() ? "(unknown)" : rec.names.peek();
                    String line = (keep ? "[保留] " : "[并入] ") + rec.pid + " | " + name + " | 最后出现 " + formatTime(rec.lastSeen);
                    p.add(escapeMarkup(line)).left().wrap().row();
                }
            }).grow().width(previewWidth).height(previewHeight).minWidth(0f).minHeight(0f).row();

            dialog.buttons.defaults().height(44f).pad(4f);
            dialog.buttons.button("确认合并", () -> {
                dialog.hide();
                mergeByUid(uid);
            });
            dialog.buttons.button("取消", dialog::hide);
            dialog.show();
        }

        private void mergeByUid(String uid){
            uid = normalizeShortUid(uid);
            if(uid == null) return;

            int merged = playerDb.mergeByUid(uid);
            if(merged > 0){
                playersDirty = true;
                Vars.ui.showInfoFade("SPDB: UID " + uid + " 已合并 " + merged + " 条 PID 记录。");
            }else{
                Vars.ui.showInfoFade("SPDB: UID " + uid + " 无需合并。");
            }

            refreshUidResult();
            refreshAllPlayersResult();
            refreshNameResult();
            refreshIpResult();
        }

        private void mergeAllSameUid(){
            int merged = playerDb.mergeAllSameUid();
            if(merged > 0){
                playersDirty = true;
                Vars.ui.showInfoFade("SPDB: 已合并全部重复UID，共合并 " + merged + " 条 PID 记录。");
            }else{
                Vars.ui.showInfoFade("SPDB: 当前没有可一键合并的重复UID。");
            }

            refreshUidResult();
            refreshAllPlayersResult();
            refreshNameResult();
            refreshIpResult();
        }

        private void fillMissingUidByPid(){
            String pid = normalizePid(fillPidField == null ? null : fillPidField.getText());
            String uid = normalizeShortUid(fillUidField == null ? null : fillUidField.getText());
            if(pid == null || uid == null){
                Vars.ui.showInfoFade("SPDB: 请填写 PID 和三位 UID。");
                return;
            }

            PlayerRecord rec = playerDb.getByPid(pid);
            if(rec == null){
                Vars.ui.showInfoFade("SPDB: 未找到该 PID。");
                return;
            }
            if(rec.uid != null){
                Vars.ui.showInfoFade("SPDB: 该 PID 已有 UID（" + rec.uid + "），不会覆盖。");
                return;
            }

            if(playerDb.bindUid(pid, uid)){
                playersDirty = true;
                Vars.ui.showInfoFade("SPDB: 已填充 UID " + uid + " -> " + pid);
            }

            refreshPidResult();
            refreshUidResult();
            refreshAllPlayersResult();
            refreshNameResult();
        }

        private void mergeTwoUids(){
            String fromUid = normalizeShortUid(mergeFromUidField == null ? null : mergeFromUidField.getText());
            String toUid = normalizeShortUid(mergeToUidField == null ? null : mergeToUidField.getText());
            if(fromUid == null || toUid == null){
                Vars.ui.showInfoFade("SPDB: 请输入两个三位 UID。");
                return;
            }
            if(fromUid.equals(toUid)){
                Vars.ui.showInfoFade("SPDB: 来源UID和目标UID相同，无需合并。");
                return;
            }

            int rebound = playerDb.rebindUid(fromUid, toUid);
            int merged = playerDb.mergeByUid(toUid);
            if(chatDb.moveUid(fromUid, toUid)) chatsDirty = true;

            if(rebound > 0 || merged > 0){
                playersDirty = true;
                Vars.ui.showInfoFade("SPDB: UID " + fromUid + " 已并入 " + toUid + "（重绑 " + rebound + "，合并PID " + merged + "）。");
            }else{
                Vars.ui.showInfoFade("SPDB: 未找到可合并的 UID 记录。");
            }

            refreshUidResult();
            refreshPidResult();
            refreshAllPlayersResult();
            refreshNameResult();
            refreshIpResult();
        }

        private void refreshPidResult(){
            pidResult.clear();
            pidResult.left().top();

            String pid = normalizePid(pidField == null ? null : pidField.getText());
            if(pid == null){
                pidResult.add("请输入 PID 后查询。").left();
                return;
            }

            PlayerRecord record = playerDb.getByPid(pid);
            if(record == null){
                pidResult.add("未找到该 PID。").left();
                return;
            }

            pidResult.table(Styles.black3, box -> renderPlayerDetailCard(box, record)).growX().padTop(4f).row();
        }

        private void refreshNameResult(){
            nameResult.clear();
            nameResult.left().top();

            String keyword = normalizeNameKeyword(nameField == null ? null : nameField.getText());
            if(keyword == null){
                nameResult.add("请输入玩家名字后查询。").left();
                return;
            }

            Seq<PlayerRecord> players = playerDb.findByNameContains(keyword);
            if(players.isEmpty()){
                nameResult.add("未找到匹配名字的玩家。", Styles.outlineLabel).left();
                return;
            }

            nameResult.add("关键字 '" + escapeMarkup(keyword) + "' 匹配 " + players.size + " 条记录").left().wrap().row();
            if(compactUi()){
                for(PlayerRecord rec : players){
                    String bestName = rec.names.isEmpty() ? "(unknown)" : rec.names.peek();
                    String uidText = rec.uid == null ? "(none)" : rec.uid;
                    nameResult.table(Styles.black3, card -> {
                        card.left().top().defaults().left().pad(3f).growX();
                        card.add(escapeMarkup("玩家: " + bestName)).left().wrap().row();
                        card.add(escapeMarkup("PID: " + safeLine(rec.pid, 68))).left().wrap().row();
                        card.add(escapeMarkup("最后出现: " + formatTime(rec.lastSeen))).left().wrap().row();
                        card.table(line -> {
                            line.left().defaults().left().padRight(6f).growX();
                            line.button(uidText, Styles.defaultt, () -> {
                                if(uidField != null && rec.uid != null) uidField.setText(rec.uid);
                                if(chatUidField != null && rec.uid != null) chatUidField.setText(rec.uid);
                                if(rec.uid != null) refreshUidResult();
                            }).height(32f).growX();
                            line.button("定位", () -> {
                                if(pidField != null) pidField.setText(rec.pid);
                                refreshPidResult();
                            }).height(32f).growX();
                        }).growX().row();
                    }).growX().padTop(3f).row();
                }
                return;
            }

            nameResult.table(Styles.black3, head -> {
                head.left().defaults().pad(4f).left();
                head.add("Name").width(200f);
                head.add("UID").width(70f);
                head.add("PID").width(250f);
                head.add("最后出现").width(170f);
                head.add("操作").width(90f);
            }).growX().row();

            for(PlayerRecord rec : players){
                String bestName = rec.names.isEmpty() ? "(unknown)" : rec.names.peek();
                String uidText = rec.uid == null ? "(none)" : rec.uid;
                nameResult.table(row -> {
                    row.left().defaults().pad(3f).left();
                    row.add(escapeMarkup(safeLine(bestName, 26))).width(200f).left();
                    row.button(uidText, Styles.defaultt, () -> {
                        if(uidField != null && rec.uid != null) uidField.setText(rec.uid);
                        if(chatUidField != null && rec.uid != null) chatUidField.setText(rec.uid);
                        if(rec.uid != null) refreshUidResult();
                    }).width(70f).height(30f);
                    row.button(rec.pid, Styles.defaultt, () -> {
                        if(pidField != null) pidField.setText(rec.pid);
                        refreshPidResult();
                    }).width(250f).height(30f);
                    row.add(formatTime(rec.lastSeen)).width(170f).left();
                    row.button("定位", () -> {
                        if(pidField != null) pidField.setText(rec.pid);
                        refreshPidResult();
                    }).width(90f).height(30f);
                }).growX().left().row();
            }
        }

        private void renderPlayerDetailCard(Table box, PlayerRecord record){
            box.left().top().defaults().left().pad(3f).growX();
            addInteractiveFieldLine(box, "PID", record.pid, () -> {
                if(pidField != null) pidField.setText(record.pid);
                if(fillPidField != null) fillPidField.setText(record.pid);
                refreshPidResult();
            });
            addInteractiveFieldLine(box, "UID", record.uid == null ? "(none)" : record.uid, () -> {
                if(record.uid != null && uidField != null) uidField.setText(record.uid);
                if(record.uid != null && mergeFromUidField != null) mergeFromUidField.setText(record.uid);
                if(record.uid != null) refreshUidResult();
            });
            addFieldLine(box, "首次出现", formatTime(record.firstSeen));
            addFieldLine(box, "最后出现", formatTime(record.lastSeen));

            box.add("曾用名:").left().row();
            if(record.names.isEmpty()){
                box.add("(none)").left().row();
            }else{
                for(String name : record.names){
                    addFieldLine(box, "-", name);
                }
            }

            box.add("已知 IP:").left().row();
            if(record.ips.isEmpty()){
                box.add("(none)").left().row();
            }else{
                for(String ip : record.ips){
                    addIpLine(box, ip);
                }
            }

            box.add("出现过的服务器:").left().row();
            if(record.servers.isEmpty()){
                box.add("(none)").left().row();
            }else{
                for(String server : record.servers){
                    addFieldLine(box, "-", server);
                }
            }
        }

        private void refreshIpResult(){
            ipResult.clear();
            ipResult.left().top();

            String ip = normalizeIp(ipField == null ? null : ipField.getText());
            if(ip == null){
                ipResult.add("请输入 IP 后查询。").left();
                return;
            }

            Seq<PlayerRecord> players = playerDb.findByIp(ip);
            if(players.isEmpty()){
                ipResult.add("该 IP 没有匹配到玩家。").left();
                return;
            }

            if(compactUi()){
                for(PlayerRecord rec : players){
                    String bestName = rec.names.isEmpty() ? "(unknown)" : rec.names.peek();
                    String uidText = rec.uid == null ? "(none)" : rec.uid;
                    String geo = lookupIpGeoCached(ip);
                    ipResult.table(Styles.black3, card -> {
                        card.left().top().defaults().left().pad(3f).growX();
                        card.add(escapeMarkup("玩家: " + bestName)).left().wrap().row();
                        card.add(escapeMarkup("PID: " + safeLine(rec.pid, 68))).left().wrap().row();
                        card.add(escapeMarkup("IP 属地: " + (geo == null ? "(查询中...)" : geo))).left().wrap().row();
                        card.table(line -> {
                            line.left().defaults().left().padRight(6f).growX();
                            line.button(uidText, Styles.defaultt, () -> {
                                if(uidField != null && rec.uid != null) uidField.setText(rec.uid);
                                if(chatUidField != null && rec.uid != null) chatUidField.setText(rec.uid);
                                if(rec.uid != null) refreshUidResult();
                            }).height(32f).growX();
                            line.button("定位", () -> {
                                if(pidField != null) pidField.setText(rec.pid);
                                if(uidField != null && rec.uid != null) uidField.setText(rec.uid);
                                refreshPidResult();
                            }).height(32f).growX();
                        }).growX().row();
                    }).growX().padTop(3f).row();
                }
                return;
            }

            ipResult.table(Styles.black3, head -> {
                head.left().defaults().pad(4f).left();
                head.add("Name").width(180f);
                head.add("UID").width(70f);
                head.add("PID").width(250f);
                head.add("IP 属地").growX();
                head.add("操作").width(90f);
            }).growX().row();

            for(PlayerRecord rec : players){
                String bestName = rec.names.isEmpty() ? "(unknown)" : rec.names.peek();
                String uidText = rec.uid == null ? "(none)" : rec.uid;
                String geo = lookupIpGeoCached(ip);
                ipResult.table(row -> {
                    row.left().defaults().pad(3f).left();
                    row.add(escapeMarkup(bestName)).width(180f).left();
                    row.button(uidText, Styles.defaultt, () -> {
                        if(uidField != null && rec.uid != null) uidField.setText(rec.uid);
                        if(chatUidField != null && rec.uid != null) chatUidField.setText(rec.uid);
                        if(rec.uid != null) refreshUidResult();
                    }).width(70f).height(30f);
                    row.button(rec.pid, Styles.defaultt, () -> {
                        if(pidField != null) pidField.setText(rec.pid);
                        refreshPidResult();
                    }).width(250f).height(30f);
                    row.add(escapeMarkup(geo == null ? "(查询中...)" : geo)).growX().left();
                    row.button("定位", () -> {
                        if(pidField != null) pidField.setText(rec.pid);
                        if(uidField != null && rec.uid != null) uidField.setText(rec.uid);
                        refreshPidResult();
                    }).width(90f).height(30f);
                }).growX().left().row();
            }
        }

        private void refreshChatResult(){
            chatResult.clear();
            chatResult.left().top();

            String uid = normalizeShortUid(chatUidField == null ? null : chatUidField.getText());
            if(uid == null){
                chatResult.add("请输入三位 UID 后查询聊天记录。").left();
                return;
            }

            Seq<ChatEntry> list = chatDb.findByUid(uid);
            if(list.isEmpty()){
                chatResult.add("没有该 UID 的聊天记录。").left();
                return;
            }

            if(compactUi()){
                for(ChatEntry entry : list){
                    chatResult.table(Styles.black3, card -> {
                        card.left().top().defaults().left().pad(3f).growX();
                        card.add(escapeMarkup(formatTime(entry.time) + " | " + safeLine(entry.senderName, 26))).left().wrap().row();
                        card.add(escapeMarkup("内容: " + safeLine(entry.message, 130))).left().wrap().row();
                        card.table(line -> {
                            line.left().defaults().left().padRight(6f).growX();
                            line.button(entry.uid, Styles.defaultt, () -> {
                                if(uidField != null) uidField.setText(entry.uid);
                                if(chatUidField != null) chatUidField.setText(entry.uid);
                                refreshUidResult();
                            }).height(32f).growX();
                            line.button(Icon.copySmall, Styles.emptyi, () -> copy(entry.message)).size(32f);
                        }).growX().row();
                    }).growX().padTop(3f).row();
                }
                return;
            }

            chatResult.table(Styles.black3, head -> {
                head.left().defaults().pad(4f).left();
                head.add("时间").width(158f);
                head.add("UID").width(70f);
                head.add("玩家").width(160f);
                head.add("内容").growX();
                head.add("操作").width(90f);
            }).growX().row();

            for(ChatEntry entry : list){
                chatResult.table(row -> {
                    row.left().defaults().pad(3f).left();
                    row.add(formatTime(entry.time)).width(158f).left();
                    row.button(entry.uid, Styles.defaultt, () -> {
                        if(uidField != null) uidField.setText(entry.uid);
                        if(chatUidField != null) chatUidField.setText(entry.uid);
                        refreshUidResult();
                    }).width(70f).height(30f);
                    row.add(escapeMarkup(safeLine(entry.senderName, 22))).width(160f).left();
                    row.add(escapeMarkup(safeLine(entry.message, 90))).growX().left();
                    row.button(Icon.copySmall, Styles.emptyi, () -> copy(entry.message)).size(30f);
                }).growX().left().row();
            }
        }

        private void refreshAllPlayersResult(){
            allPlayersResult.clear();
            allPlayersResult.left().top();

            Seq<PlayerRecord> all = playerDb.allByLastSeen();
            int total = all.size;
            int pages = total == 0 ? 1 : (total + playersPageSize - 1) / playersPageSize;
            if(allPlayersPage >= pages) allPlayersPage = pages - 1;
            if(allPlayersPage < 0) allPlayersPage = 0;

            allPlayersResult.add("第 " + (allPlayersPage + 1) + " / " + pages + " 页，共 " + total + " 条").left().row();
            if(total == 0){
                allPlayersResult.add("暂无玩家记录。\n开启采集后会自动显示。", Styles.outlineLabel).left().wrap().row();
                return;
            }

            int from = allPlayersPage * playersPageSize;
            int to = Math.min(from + playersPageSize, total);

            if(compactUi()){
                for(int i = from; i < to; i++){
                    PlayerRecord rec = all.get(i);
                    String bestName = rec.names.isEmpty() ? "(unknown)" : rec.names.peek();
                    String uidText = rec.uid == null ? "(none)" : rec.uid;

                    allPlayersResult.table(Styles.black3, card -> {
                        card.left().top().defaults().left().pad(3f).growX();
                        card.add(escapeMarkup(bestName)).left().wrap().row();
                        card.add(escapeMarkup("最后出现: " + formatTime(rec.lastSeen))).left().wrap().row();
                        card.add(escapeMarkup("PID: " + safeLine(rec.pid, 68))).left().wrap().row();
                        card.table(line -> {
                            line.left().defaults().left().padRight(6f).growX();
                            line.button(uidText, Styles.defaultt, () -> {
                                if(uidField != null && rec.uid != null) uidField.setText(rec.uid);
                                if(chatUidField != null && rec.uid != null) chatUidField.setText(rec.uid);
                                if(rec.uid != null) refreshUidResult();
                            }).height(32f).growX();
                            line.button("聊天", () -> {
                                if(chatUidField != null && rec.uid != null) chatUidField.setText(rec.uid);
                                activeTab = 1;
                                refreshChatResult();
                            }).height(32f).growX();
                        }).growX().row();
                    }).growX().padTop(3f).row();
                }
                return;
            }

            allPlayersResult.table(Styles.black3, head -> {
                head.left().defaults().pad(4f).left();
                head.add("玩家").width(170f);
                head.add("UID").width(70f);
                head.add("PID").width(250f);
                head.add("最后出现").width(160f);
                head.add("操作").width(120f);
            }).growX().row();

            for(int i = from; i < to; i++){
                PlayerRecord rec = all.get(i);
                String bestName = rec.names.isEmpty() ? "(unknown)" : rec.names.peek();
                String uidText = rec.uid == null ? "(none)" : rec.uid;

                allPlayersResult.table(row -> {
                    row.left().defaults().pad(3f).left();
                    row.add(escapeMarkup(safeLine(bestName, 22))).width(170f).left();
                    row.button(uidText, Styles.defaultt, () -> {
                        if(uidField != null && rec.uid != null) uidField.setText(rec.uid);
                        if(chatUidField != null && rec.uid != null) chatUidField.setText(rec.uid);
                        if(rec.uid != null) refreshUidResult();
                    }).width(70f).height(30f);
                    row.button(rec.pid, Styles.defaultt, () -> {
                        if(pidField != null) pidField.setText(rec.pid);
                        refreshPidResult();
                    }).width(250f).height(30f);
                    row.add(formatTime(rec.lastSeen)).width(160f).left();
                    row.button("聊天", () -> {
                        if(chatUidField != null && rec.uid != null) chatUidField.setText(rec.uid);
                        activeTab = 1;
                        refreshChatResult();
                    }).width(120f).height(30f);
                }).growX().left().row();
            }
        }

        private void refreshAllChatsResult(){
            allChatsResult.clear();
            allChatsResult.left().top();

            int total = chatDb.totalEntries();
            int pages = total == 0 ? 1 : (total + chatsPageSize - 1) / chatsPageSize;
            if(allChatsPage >= pages) allChatsPage = pages - 1;
            if(allChatsPage < 0) allChatsPage = 0;

            allChatsResult.add("第 " + (allChatsPage + 1) + " / " + pages + " 页，共 " + total + " 条").left().row();
            if(total == 0){
                allChatsResult.add("暂无聊天记录。\n启用“存储聊天记录”后会自动显示。", Styles.outlineLabel).left().wrap().row();
                return;
            }

            Seq<ChatEntry> list = chatDb.findRecent(allChatsPage * chatsPageSize, chatsPageSize);
            if(compactUi()){
                for(ChatEntry entry : list){
                    allChatsResult.table(Styles.black3, card -> {
                        card.left().top().defaults().left().pad(3f).growX();
                        card.add(escapeMarkup(formatTime(entry.time) + " | " + safeLine(entry.senderName, 26))).left().wrap().row();
                        card.add(escapeMarkup("UID: " + entry.uid + " | 服: " + safeLine(entry.server, 34))).left().wrap().row();
                        card.add(escapeMarkup("内容: " + safeLine(entry.message, 130))).left().wrap().row();
                        card.table(line -> {
                            line.left().defaults().left().padRight(6f).growX();
                            line.button(entry.uid, Styles.defaultt, () -> {
                                if(uidField != null) uidField.setText(entry.uid);
                                if(chatUidField != null) chatUidField.setText(entry.uid);
                                activeTab = 0;
                                refreshUidResult();
                            }).height(32f).growX();
                            line.button(Icon.copySmall, Styles.emptyi, () -> copy(entry.message)).size(32f);
                        }).growX().row();
                    }).growX().padTop(3f).row();
                }
                return;
            }

            allChatsResult.table(Styles.black3, head -> {
                head.left().defaults().pad(4f).left();
                head.add("时间").width(158f);
                head.add("UID").width(70f);
                head.add("玩家").width(160f);
                head.add("服务器").width(180f);
                head.add("内容").growX();
                head.add("操作").width(90f);
            }).growX().row();

            for(ChatEntry entry : list){
                allChatsResult.table(row -> {
                    row.left().defaults().pad(3f).left();
                    row.add(formatTime(entry.time)).width(158f).left();
                    row.button(entry.uid, Styles.defaultt, () -> {
                        if(uidField != null) uidField.setText(entry.uid);
                        if(chatUidField != null) chatUidField.setText(entry.uid);
                        activeTab = 0;
                        refreshUidResult();
                    }).width(70f).height(30f);
                    row.add(escapeMarkup(safeLine(entry.senderName, 22))).width(160f).left();
                    row.add(escapeMarkup(safeLine(entry.server, 26))).width(180f).left();
                    row.add(escapeMarkup(safeLine(entry.message, 70))).growX().left();
                    row.button(Icon.copySmall, Styles.emptyi, () -> copy(entry.message)).size(30f);
                }).growX().left().row();
            }
        }

        private void addIpLine(Table table, String ip){
            String geo = lookupIpGeoCached(ip);
            String value = ip + (geo == null || geo.isEmpty() ? "" : "  (" + geo + ")");
            addFieldLine(table, "-", value);
        }

        private void addFieldLine(Table table, String key, String value){
            addInteractiveFieldLine(table, key, value, null);
        }

        private void addInteractiveFieldLine(Table table, String key, String value, Runnable action){
            boolean compact = compactUi();
            table.table(row -> {
                row.left().defaults().left().padRight(6f).padTop(1f).padBottom(1f);
                row.add(escapeMarkup((key == null ? "" : key) + ":")).width(compact ? 64f : 86f).left();

                if(action != null){
                    row.button(escapeMarkup(value == null ? "" : value), Styles.defaultt, action).left().growX().minWidth(0f).height(compact ? 32f : 28f);
                }else{
                    row.add(escapeMarkup(value == null ? "" : value)).left().growX().minWidth(0f).wrap();
                }

                row.button(Icon.copySmall, Styles.emptyi, () -> copy(value)).size(compact ? 30f : 28f);
            }).growX().left().row();
        }
    }

    private class OverlayQueryContent{
        final Table root = new Table();
        final Table result = new Table();
        TextField uidField;
        TextField nameField;
        TextField ipField;
        int lastMode;

        OverlayQueryContent(){
            build();
        }

        private void build(){
            root.clear();
            root.top().left().defaults().left().pad(4f);
            boolean compact = compactUi();

            root.table(Styles.black3, t -> {
                t.left().defaults().left().pad(4f);
                t.add("[accent]SPDB 轻量查询[]").left().growX().row();

                t.table(line -> {
                    line.left().defaults().left().padRight(6f).growX();
                    uidField = line.field("", text -> {}).growX().get();
                    uidField.setMessageText("按 UID 查询（3位）");
                    line.button("查UID", this::queryByUid).height(34f).growX();
                }).growX().row();

                if(compact){
                    t.table(line -> {
                        line.left().defaults().left().padRight(6f);
                        nameField = line.field("", text -> {}).growX().get();
                        nameField.setMessageText("按名字模糊查询");
                    }).growX().row();
                    t.table(line -> {
                        line.left().defaults().left().padRight(6f).growX();
                        line.button("查名字", this::queryByName).height(34f).growX();
                    }).growX().row();
                    t.table(line -> {
                        line.left().defaults().left().padRight(6f);
                        ipField = line.field("", text -> {}).growX().get();
                        ipField.setMessageText("按 IP 查询");
                    }).growX().row();
                    t.table(line -> {
                        line.left().defaults().left().padRight(6f).growX();
                        line.button("查IP", this::queryByIp).height(34f).growX();
                        line.button("清空", this::clearResult).height(34f).growX();
                    }).growX().row();
                }else{
                    t.table(line -> {
                        line.left().defaults().left().padRight(6f);
                        nameField = line.field("", text -> {}).growX().get();
                        nameField.setMessageText("按名字模糊查询");
                        line.button("查名字", this::queryByName).height(34f);
                    }).growX().row();
                    t.table(line -> {
                        line.left().defaults().left().padRight(6f);
                        ipField = line.field("", text -> {}).growX().get();
                        ipField.setMessageText("按 IP 查询");
                        line.button("查IP", this::queryByIp).height(34f);
                        line.button("清空", this::clearResult).height(34f);
                    }).growX().row();
                }
            }).growX().row();

            root.pane(result).scrollX(false).grow().maxHeight(fitPaneHeight(420f, 180f)).minWidth(0f).minHeight(0f).row();
            root.add(new PreferAnySize()).grow().row();
        }

        private void queryByUid(){
            lastMode = 1;
            result.clear();
            result.left().top();

            String uid = normalizeShortUid(uidField == null ? null : uidField.getText());
            if(uid == null){
                result.add("请输入三位 UID（例如 dNF）。", Styles.outlineLabel).left().row();
                return;
            }

            Seq<PlayerRecord> list = playerDb.findByUid(uid);
            if(list.isEmpty()){
                result.add("未找到该 UID。", Styles.outlineLabel).left().row();
                return;
            }

            result.add("UID " + uid + " 匹配 " + list.size + " 条记录").left().row();
            for(PlayerRecord rec : list){
                String name = rec.names.isEmpty() ? "(unknown)" : rec.names.peek();
                String geo = rec.ips.isEmpty() ? "" : lookupIpGeoCached(rec.ips.peek());
                String text = "PID: " + rec.pid + "\n" +
                    "UID: " + (rec.uid == null ? "(none)" : rec.uid) + "\n" +
                    "Name: " + name + "\n" +
                    "IP: " + (rec.ips.isEmpty() ? "(none)" : rec.ips.peek()) + (geo == null || geo.isEmpty() ? "" : " (" + geo + ")") + "\n" +
                    "Last: " + formatTime(rec.lastSeen);

                result.table(Styles.black3, row -> {
                    row.left().defaults().left().pad(4f);
                    row.add(escapeMarkup(text)).growX().left().wrap();
                }).growX().padTop(3f).row();
            }
        }

        private void queryByName(){
            lastMode = 3;
            result.clear();
            result.left().top();

            String keyword = normalizeNameKeyword(nameField == null ? null : nameField.getText());
            if(keyword == null){
                result.add("请输入玩家名字后查询。", Styles.outlineLabel).left().row();
                return;
            }

            Seq<PlayerRecord> list = playerDb.findByNameContains(keyword);
            if(list.isEmpty()){
                result.add("未找到匹配名字的玩家。", Styles.outlineLabel).left().row();
                return;
            }

            result.add("关键字 '" + escapeMarkup(keyword) + "' 匹配 " + list.size + " 条记录").left().wrap().row();
            for(PlayerRecord rec : list){
                String name = rec.names.isEmpty() ? "(unknown)" : rec.names.peek();
                String text = "Name: " + name + "\n" +
                    "UID: " + (rec.uid == null ? "(none)" : rec.uid) + "\n" +
                    "PID: " + rec.pid + "\n" +
                    "Last: " + formatTime(rec.lastSeen);

                result.table(Styles.black3, row -> {
                    row.left().defaults().left().pad(4f);
                    row.add(escapeMarkup(text)).growX().left().wrap();
                }).growX().padTop(3f).row();
            }
        }

        private void clearResult(){
            lastMode = 0;
            result.clear();
            if(uidField != null) uidField.setText("");
            if(nameField != null) nameField.setText("");
            if(ipField != null) ipField.setText("");
        }

        private void queryByIp(){
            lastMode = 2;
            result.clear();
            result.left().top();

            String ip = normalizeIp(ipField == null ? null : ipField.getText());
            if(ip == null){
                result.add("请输入 IP。", Styles.outlineLabel).left().row();
                return;
            }

            String geo = lookupIpGeoCached(ip);
            result.add("IP: " + ip + (geo == null ? "" : " (" + geo + ")")).left().wrap().row();

            Seq<PlayerRecord> list = playerDb.findByIp(ip);
            if(list.isEmpty()){
                result.add("该 IP 没有匹配到玩家。", Styles.outlineLabel).left().row();
                return;
            }

            for(PlayerRecord rec : list){
                String name = rec.names.isEmpty() ? "(unknown)" : rec.names.peek();
                String text = "Name: " + name + "\n" +
                    "UID: " + (rec.uid == null ? "(none)" : rec.uid) + "\n" +
                    "PID: " + rec.pid + "\n" +
                    "Last: " + formatTime(rec.lastSeen);

                result.table(Styles.black3, row -> {
                    row.left().defaults().left().pad(4f);
                    row.add(escapeMarkup(text)).growX().left().wrap();
                }).growX().padTop(3f).row();
            }
        }

        private void refreshLast(){
            if(lastMode == 1) queryByUid();
            else if(lastMode == 2) queryByIp();
            else if(lastMode == 3) queryByName();
        }
    }

    private class DebugContent{
        final Table root = new Table();
        final Table replayResult = new Table();
        final Table lines = new Table();
        TextField replayField;
        private boolean dirty = true;

        DebugContent(){
            build();
        }

        void markDirty(){
            dirty = true;
        }

        private void build(){
            root.clear();
            root.top().left();
            root.defaults().left().pad(4f);
            boolean compact = compactUi();

            root.table(Styles.black3, top -> {
                top.left().defaults().pad(6f).height(40f).growX();
                if(compact){
                    top.add("[accent]SPDB 调试面板[]  最近 UID 解析记录").left().growX().wrap().row();
                    top.button("刷新", this::refresh).height(36f).growX().row();
                    top.table(btns -> {
                        btns.left().defaults().padRight(6f).growX();
                        btns.button("清空", () -> {
                            debugLines.clear();
                            refresh();
                        }).height(36f).growX();
                        btns.button("复制", this::copyAll).height(36f).growX();
                    }).growX();
                }else{
                    top.add("[accent]SPDB 调试面板[]  最近 UID 解析记录").left().growX();
                    top.button("刷新", this::refresh).height(36f);
                    top.button("清空", () -> {
                        debugLines.clear();
                        refresh();
                    }).height(36f);
                    top.button("复制", this::copyAll).height(36f);
                }
            }).growX().row();

            root.table(Styles.black3, parser -> {
                parser.left().top().defaults().left().pad(4f).growX();
                parser.add("聊天行解析回放（粘贴原始聊天行，实时看 name/uid/message）").left().row();
                if(compact){
                    parser.table(line -> {
                        line.left().defaults().left().padRight(6f);
                        replayField = line.field("", text -> {}).growX().get();
                        replayField.setMessageText("粘贴一整行聊天文本，例: [coral]...[gray]dNFX...: 测试文本");
                    }).growX().row();
                    parser.table(line -> {
                        line.left().defaults().left().padRight(6f).growX();
                        line.button("解析", this::parseReplay).height(34f).growX();
                        line.button("清空", () -> {
                            replayField.setText("");
                            replayResult.clear();
                        }).height(34f).growX();
                    }).growX().row();
                }else{
                    parser.table(line -> {
                        line.left().defaults().left().padRight(6f);
                        replayField = line.field("", text -> {}).growX().get();
                        replayField.setMessageText("粘贴一整行聊天文本，例: [coral]...[gray]dNFX...: 测试文本");
                        line.button("解析", this::parseReplay).height(34f);
                        line.button("清空", () -> {
                            replayField.setText("");
                            replayResult.clear();
                        }).height(34f);
                    }).growX().row();
                }
                parser.add(replayResult).growX().row();
            }).growX().padTop(2f).row();

            root.pane(lines).scrollX(false).grow().maxHeight(fitPaneHeight(520f, 220f)).minWidth(0f).minHeight(0f).row();
            root.add(new PreferAnySize()).grow().row();

            root.update(() -> {
                if(dirty) refresh();
            });
        }

        private void refresh(){
            dirty = false;
            lines.clear();
            lines.left().top();

            if(debugLines.isEmpty()){
                lines.add("暂无调试记录。进行聊天后会自动出现解析日志。", Styles.outlineLabel).left().row();
                return;
            }

            for(String line : debugLines){
                lines.table(Styles.black3, row -> {
                    row.left().defaults().left().pad(4f);
                    row.add(escapeMarkup(line)).growX().left().wrap();
                }).growX().padTop(2f).row();
            }
        }

        private void parseReplay(){
            replayResult.clear();
            replayResult.left().top();

            String raw = replayField == null ? null : replayField.getText();
            if(raw == null || raw.trim().isEmpty()){
                replayResult.add("请先粘贴聊天行。", Styles.outlineLabel).left().row();
                return;
            }

            String stripped = Strings.stripColors(raw);
            ChatSnapshot snapshot = parseChatLine(raw, stripped, "");
            if(snapshot == null){
                replayResult.add("解析失败：未识别为有效聊天行（需包含 ':'）。", Styles.outlineLabel).left().row();
                addReplayField("去色文本", safeLine(stripped, 220));
                return;
            }

            addReplayField("玩家名称", snapshot.senderName == null ? "(none)" : snapshot.senderName);
            addReplayField("玩家UID", snapshot.uid == null ? "(none)" : snapshot.uid);
            addReplayField("聊天内容", snapshot.message == null ? "(none)" : snapshot.message);
            addReplayField("去色文本", safeLine(stripped, 220));

            appendDebugLine("replay uid=" + (snapshot.uid == null ? "(none)" : snapshot.uid) + " | name=" + safeLine(snapshot.senderName, 32) + " | msg=" + safeLine(snapshot.message, 48));
        }

        private void addReplayField(String key, String value){
            boolean compact = compactUi();
            replayResult.table(row -> {
                row.left().defaults().left().pad(2f);
                row.add(key + ":").width(compact ? 64f : 82f).left();
                row.add(escapeMarkup(value == null ? "" : value)).growX().left().wrap();
                row.button(Icon.copySmall, Styles.emptyi, () -> copy(value)).size(compact ? 30f : 26f);
            }).growX().row();
        }

        private void copyAll(){
            StringBuilder sb = new StringBuilder();
            for(String line : debugLines){
                sb.append(line).append('\n');
            }
            copy(sb.toString().trim());
        }
    }

    private static class PreferAnySize extends Element{
        @Override
        public float getMinWidth(){
            return 0f;
        }

        @Override
        public float getPrefWidth(){
            return getWidth();
        }

        @Override
        public float getMinHeight(){
            return 0f;
        }

        @Override
        public float getPrefHeight(){
            return getHeight();
        }
    }

    private void copy(String value){
        if(value == null) return;
        Core.app.setClipboardText(value);
        if(Vars.ui != null) Vars.ui.showInfoFade("已复制");
    }

    private static String safeName(String name){
        if(name == null) return "";
        String stripped = Strings.stripColors(name).trim();
        return stripped.isEmpty() ? name.trim() : stripped;
    }

    private static String safeMessage(String message){
        if(message == null) return "";
        return Strings.stripColors(message).trim();
    }

    private String lookupIpGeoCached(String ip){
        ip = normalizeIp(ip);
        if(ip == null) return null;

        String cached = ipGeoCache.get(ip);
        if(cached != null) return cached;

        if(ipGeoPending.add(ip)) requestIpGeoAsync(ip);
        return "查询中...";
    }

    private void requestIpGeoAsync(String ip){
        Thread thread = new Thread(() -> {
            String result = "查询失败";
            HttpURLConnection conn = null;

            try{
                String q = URLEncoder.encode(ip, "UTF-8");
                URL url = new URL("https://ip9.com.cn/get?ip=" + q);
                conn = (HttpURLConnection)url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(4500);
                conn.setReadTimeout(4500);

                String text;
                InputStream in = conn.getInputStream();
                try{
                    ByteArrayOutputStream out = new ByteArrayOutputStream(256);
                    byte[] buf = new byte[1024];
                    int len;
                    while((len = in.read(buf)) >= 0){
                        out.write(buf, 0, len);
                    }
                    text = new String(out.toByteArray(), StandardCharsets.UTF_8);
                }finally{
                    in.close();
                }
                JsonValue root = jsonReader.parse(text);
                if(root != null && root.getInt("ret", 0) == 200){
                    JsonValue data = root.get("data");
                    if(data != null){
                        String country = data.getString("country", "");
                        String prov = data.getString("prov", "");
                        String city = data.getString("city", "");
                        String area = data.getString("area", "");
                        String isp = data.getString("isp", "");

                        StringBuilder sb = new StringBuilder();
                        if(!country.isEmpty()) sb.append(country);
                        if(!prov.isEmpty() && !prov.equals(country)) sb.append(sb.length() == 0 ? "" : " ").append(prov);
                        if(!city.isEmpty() && !city.equals(prov)) sb.append(sb.length() == 0 ? "" : " ").append(city);
                        if(!area.isEmpty() && !area.equals(city)) sb.append(sb.length() == 0 ? "" : " ").append(area);
                        if(!isp.isEmpty()) sb.append(sb.length() == 0 ? "" : " | ").append(isp);

                        if(sb.length() > 0) result = sb.toString();
                    }
                }
            }catch(Throwable ignored){
            }finally{
                if(conn != null) conn.disconnect();
            }

            String finalResult = result;
            Core.app.post(() -> {
                ipGeoCache.put(ip, finalResult);
                ipGeoPending.remove(ip);
                if(overlayQueryContent != null) overlayQueryContent.refreshLast();
            });
        }, "spdb-ipgeo");
        thread.setDaemon(true);
        thread.start();
    }

    private static String deriveUidFromUuidLike(String input){
        if(input == null) return null;

        String compact = compactIdText(input);
        if(compact.isEmpty()) return null;

        byte[] decoded = decodeBase64Loose(compact);
        if(decoded == null) return null;

        byte[] uid16;
        if(decoded.length == 8){
            uid16 = computeUid16FromUuid8(decoded);
        }else if(decoded.length == 16){
            byte[] uuid8 = new byte[8];
            System.arraycopy(decoded, 0, uuid8, 0, 8);
            uid16 = computeUid16FromUuid8(uuid8);
        }else{
            return null;
        }

        if(uid16 == null) return null;
        String uid16Base64 = Base64.getEncoder().encodeToString(uid16);
        return normalizeShortUid(shortUidFromUid16(uid16Base64));
    }

    private static String shortUidFromUid16(String uid16){
        if(uid16 == null || uid16.isEmpty()) return null;
        try{
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            byte[] bs = uid16.getBytes(StandardCharsets.UTF_8);
            byte[] first = md5.digest(bs);

            byte[] mixed = new byte[first.length + bs.length];
            System.arraycopy(first, 0, mixed, 0, first.length);
            System.arraycopy(bs, 0, mixed, first.length, bs.length);

            md5.reset();
            byte[] second = md5.digest(mixed);
            String b64 = Base64.getEncoder().encodeToString(second);
            if(b64.length() < 3) return null;

            StringBuilder out = new StringBuilder(3);
            out.append(mapShortUidChar(b64.charAt(0)));
            out.append(mapShortUidChar(b64.charAt(1)));
            out.append(mapShortUidChar(b64.charAt(2)));
            return out.toString();
        }catch(Throwable ignored){
            return null;
        }
    }

    private static char mapShortUidChar(char c){
        if(c == 'k') return 'K';
        if(c == 'S') return 's';
        if(c == 'l') return 'L';
        if(c == '+') return 'A';
        if(c == '/') return 'B';
        return c;
    }

    private static byte[] computeUid16FromUuid8(byte[] uuid8){
        if(uuid8 == null || uuid8.length != 8) return null;

        byte[] out = new byte[16];
        System.arraycopy(uuid8, 0, out, 0, 8);

        CRC32 crc = new CRC32();
        crc.update(uuid8, 0, 8);
        long v = crc.getValue() & 0xffffffffL;

        out[8] = 0;
        out[9] = 0;
        out[10] = 0;
        out[11] = 0;
        out[12] = (byte)((v >>> 24) & 0xff);
        out[13] = (byte)((v >>> 16) & 0xff);
        out[14] = (byte)((v >>> 8) & 0xff);
        out[15] = (byte)(v & 0xff);
        return out;
    }

    private static byte[] decodeBase64Loose(String text){
        if(text == null || text.isEmpty()) return null;

        byte[] bytes = decodeBase64(text);
        if(bytes != null) return bytes;

        int mod = text.length() % 4;
        if(mod == 2) return decodeBase64(text + "==");
        if(mod == 3) return decodeBase64(text + "=");
        return null;
    }

    private static byte[] decodeBase64(String text){
        try{
            return Base64.getDecoder().decode(text);
        }catch(Throwable ignored){
            return null;
        }
    }

    private static String compactIdText(String text){
        if(text == null) return "";
        StringBuilder out = new StringBuilder(text.length());
        for(int i = 0; i < text.length(); i++){
            char c = text.charAt(i);
            if(!Character.isWhitespace(c)) out.append(c);
        }
        return out.toString();
    }

    private static String normalizeUid(String uid){
        if(uid == null) return null;
        String out = uid.trim();
        return out.isEmpty() ? null : out;
    }

    private static String normalizePid(String pid){
        return normalizeUid(pid);
    }

    private static String normalizeShortUid(String uid){
        String out = normalizeUid(uid);
        if(out == null) return null;
        return shortUidPattern.matcher(out).matches() ? out : null;
    }

    private static String normalizeIp(String ip){
        if(ip == null) return null;
        String out = ip.trim();
        return out.isEmpty() ? null : out;
    }

    private static String normalizeNameKeyword(String text){
        if(text == null) return null;
        String out = Strings.stripColors(text).trim();
        return out.isEmpty() ? null : out;
    }

    private static String normalizeServer(String server){
        if(server == null) return "unknown";
        String out = server.trim();
        if(out.isEmpty()) return "unknown";
        return out.replace(' ', '_');
    }

    private static String escapeMarkup(String text){
        return text == null ? "" : text.replace("[", "[[");
    }

    private static final class TimeFmt{
        private static final SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT);
        private static final SimpleDateFormat day = new SimpleDateFormat("yyyyMMdd", Locale.ROOT);
    }

    private static String formatTime(long millis){
        if(millis <= 0L) return "-";
        return TimeFmt.fmt.format(new Date(millis));
    }

    public static class PlayerRecord{
        public String pid;
        public String uid;
        public Seq<String> names = new Seq<>();
        public Seq<String> ips = new Seq<>();
        public Seq<String> servers = new Seq<>();
        public long firstSeen;
        public long lastSeen;

        public PlayerRecord copy(){
            PlayerRecord out = new PlayerRecord();
            out.pid = pid;
            out.uid = uid;
            out.names = names.copy();
            out.ips = ips.copy();
            out.servers = servers.copy();
            out.firstSeen = firstSeen;
            out.lastSeen = lastSeen;
            return out;
        }
    }

    private static class SameIpGroup{
        String ip;
        Seq<PlayerRecord> players = new Seq<>();
        long latestSeen;
    }

    public static class PlayerDbFile{
        public int schema = 2;
        public ObjectMap<String, PlayerRecord> players = new ObjectMap<>();
        public String integrityAlgo;
        public String integritySha256;
        public long integrityTime;
    }

    private static class PlayerDatabase{
        private final ObjectMap<String, PlayerRecord> players = new ObjectMap<>();
        private final ObjectMap<String, OrderedSet<String>> ipToPids = new ObjectMap<>();
        private final ObjectMap<String, OrderedSet<String>> uidToPids = new ObjectMap<>();

        PlayerRecord getByPid(String pid){
            pid = normalizePid(pid);
            return pid == null ? null : players.get(pid);
        }

        String getBoundUid(String pid){
            PlayerRecord rec = getByPid(pid);
            return rec == null ? null : normalizeShortUid(rec.uid);
        }

        void loadFrom(PlayerDbFile file){
            players.clear();
            ipToPids.clear();
            uidToPids.clear();
            if(file == null || file.players == null) return;

            for(ObjectMap.Entry<String, PlayerRecord> entry : file.players){
                if(entry.value == null) continue;

                PlayerRecord rec = entry.value.copy();

                String pid = normalizePid(rec.pid);
                if(pid == null) pid = normalizePid(entry.key);
                if(pid == null) continue;

                rec.pid = pid;
                rec.uid = normalizeShortUid(rec.uid);

                players.put(pid, rec);
            }
            rebuildIndexes();
        }

        PlayerDbFile snapshot(){
            PlayerDbFile out = new PlayerDbFile();
            for(ObjectMap.Entry<String, PlayerRecord> entry : players){
                out.players.put(entry.key, entry.value.copy());
            }
            return out;
        }

        int mergeFrom(PlayerDbFile incoming){
            if(incoming == null || incoming.players == null) return 0;
            int changed = 0;

            for(ObjectMap.Entry<String, PlayerRecord> entry : incoming.players){
                PlayerRecord rec = entry.value;
                if(rec == null) continue;

                String pid = normalizePid(rec.pid);
                if(pid == null) pid = normalizePid(entry.key);
                if(pid == null) continue;

                String uid = normalizeShortUid(rec.uid);

                boolean localChange = false;
                long first = rec.firstSeen;
                long last = rec.lastSeen;

                localChange |= touch(pid, uid, rec.names.size > 0 ? rec.names.peek() : null, rec.servers.size > 0 ? rec.servers.peek() : "import", last > 0 ? last : Time.millis());

                PlayerRecord to = players.get(pid);
                if(to == null) continue;
                if(first > 0 && (to.firstSeen == 0 || first < to.firstSeen)){
                    to.firstSeen = first;
                    localChange = true;
                }
                if(last > to.lastSeen){
                    to.lastSeen = last;
                    localChange = true;
                }

                if(uid != null && !uid.equals(to.uid)){
                    to.uid = uid;
                    localChange = true;
                }

                for(String name : rec.names){
                    localChange |= addName(pid, name);
                }
                for(String ip : rec.ips){
                    localChange |= addIp(pid, ip);
                }
                for(String server : rec.servers){
                    localChange |= addServer(pid, server);
                }

                if(localChange) changed++;
            }
            rebuildIndexes();
            return changed;
        }

        boolean mergeInto(String fromPid, String toPid){
            fromPid = normalizePid(fromPid);
            toPid = normalizePid(toPid);
            if(fromPid == null || toPid == null || fromPid.equals(toPid)) return false;

            PlayerRecord from = players.get(fromPid);
            if(from == null) return false;

            PlayerRecord to = players.get(toPid);
            if(to == null){
                to = new PlayerRecord();
                to.pid = toPid;
                players.put(toPid, to);
            }

            boolean changed = false;
            for(String name : from.names){
                changed |= addUnique(to.names, safeName(name));
            }
            for(String ip : from.ips){
                changed |= addUnique(to.ips, normalizeIp(ip));
            }
            for(String server : from.servers){
                changed |= addUnique(to.servers, normalizeServer(server));
            }

            if(to.firstSeen == 0 || (from.firstSeen > 0 && from.firstSeen < to.firstSeen)){
                to.firstSeen = from.firstSeen;
                changed = true;
            }
            if(from.lastSeen > to.lastSeen){
                to.lastSeen = from.lastSeen;
                changed = true;
            }

            if(to.uid == null && from.uid != null){
                to.uid = normalizeShortUid(from.uid);
                changed = true;
            }

            players.remove(fromPid);
            rebuildIndexes();
            return changed;
        }

        boolean touch(String pid, String uid, String name, String server, long seenAt){
            pid = normalizePid(pid);
            uid = normalizeShortUid(uid);
            if(pid == null) return false;

            boolean changed = false;
            PlayerRecord rec = players.get(pid);
            if(rec == null){
                rec = new PlayerRecord();
                rec.pid = pid;
                rec.uid = uid;
                rec.firstSeen = seenAt;
                rec.lastSeen = seenAt;
                players.put(pid, rec);
                changed = true;
            }

            if(rec.pid == null){
                rec.pid = pid;
                changed = true;
            }

            if(uid != null && !uid.equals(rec.uid)){
                rec.uid = uid;
                changed = true;
            }

            if(rec.firstSeen == 0 || (seenAt > 0 && seenAt < rec.firstSeen)){
                rec.firstSeen = seenAt;
                changed = true;
            }
            if(seenAt > rec.lastSeen){
                rec.lastSeen = seenAt;
                changed = true;
            }

            changed |= addUnique(rec.names, safeName(name));
            changed |= addUnique(rec.servers, normalizeServer(server));
            if(changed) rebuildIndexes();
            return changed;
        }

        boolean bindUid(String pid, String uid){
            pid = normalizePid(pid);
            uid = normalizeShortUid(uid);
            if(pid == null || uid == null) return false;

            PlayerRecord rec = ensure(pid);
            if(uid.equals(rec.uid)) return false;

            rec.uid = uid;
            rebuildIndexes();
            return true;
        }

        boolean addName(String pid, String name){
            pid = normalizePid(pid);
            if(pid == null) return false;
            PlayerRecord rec = ensure(pid);
            return addUnique(rec.names, safeName(name));
        }

        boolean addServer(String pid, String server){
            pid = normalizePid(pid);
            if(pid == null) return false;
            PlayerRecord rec = ensure(pid);
            return addUnique(rec.servers, normalizeServer(server));
        }

        boolean addIp(String pid, String ip){
            pid = normalizePid(pid);
            ip = normalizeIp(ip);
            if(pid == null || ip == null) return false;

            PlayerRecord rec = ensure(pid);
            if(!addUnique(rec.ips, ip)) return false;

            ipToPids.get(ip, OrderedSet::new).add(pid);
            return true;
        }

        Seq<PlayerRecord> findByUid(String uid){
            uid = normalizeShortUid(uid);
            Seq<PlayerRecord> out = new Seq<>();
            if(uid == null) return out;

            OrderedSet<String> pids = uidToPids.get(uid);
            if(pids == null) return out;

            for(String pid : pids){
                PlayerRecord rec = players.get(pid);
                if(rec != null) out.add(rec);
            }

            out.sort((a, b) -> Long.compare(b.lastSeen, a.lastSeen));
            return out;
        }

        Seq<PlayerRecord> findByNameContains(String keyword){
            keyword = normalizeNameKeyword(keyword);
            Seq<PlayerRecord> out = new Seq<>();
            if(keyword == null) return out;

            String needle = keyword.toLowerCase(Locale.ROOT);
            for(ObjectMap.Entry<String, PlayerRecord> entry : players){
                PlayerRecord rec = entry.value;
                if(rec == null || rec.names == null || rec.names.isEmpty()) continue;

                boolean matched = false;
                for(String name : rec.names){
                    String plain = safeName(name);
                    if(containsIgnoreCase(plain, needle) || containsIgnoreCase(name, needle)){
                        matched = true;
                        break;
                    }
                }

                if(matched) out.add(rec);
            }

            out.sort((a, b) -> Long.compare(b.lastSeen, a.lastSeen));
            return out;
        }

        int mergeByUid(String uid){
            uid = normalizeShortUid(uid);
            if(uid == null) return 0;

            OrderedSet<String> set = uidToPids.get(uid);
            if(set == null || set.size <= 1) return 0;

            Seq<String> pids = set.orderedItems().copy();
            if(pids.isEmpty()) return 0;

            String targetPid = pids.first();
            long bestLastSeen = Long.MIN_VALUE;
            for(String pid : pids){
                PlayerRecord rec = players.get(pid);
                long seen = rec == null ? Long.MIN_VALUE : rec.lastSeen;
                if(seen > bestLastSeen){
                    bestLastSeen = seen;
                    targetPid = pid;
                }
            }

            int merged = 0;
            for(String pid : pids){
                if(pid.equals(targetPid)) continue;
                if(mergeInto(pid, targetPid)) merged++;
            }

            bindUid(targetPid, uid);
            rebuildIndexes();
            return merged;
        }

        int mergeAllSameUid(){
            rebuildIndexes();
            Seq<String> allUids = new Seq<>();
            for(String uid : uidToPids.keys()){
                if(uid != null) allUids.add(uid);
            }

            int totalMerged = 0;
            for(String uid : allUids){
                totalMerged += mergeByUid(uid);
            }
            return totalMerged;
        }

        int rebindUid(String fromUid, String toUid){
            fromUid = normalizeShortUid(fromUid);
            toUid = normalizeShortUid(toUid);
            if(fromUid == null || toUid == null || fromUid.equals(toUid)) return 0;

            int changed = 0;
            for(ObjectMap.Entry<String, PlayerRecord> entry : players){
                PlayerRecord rec = entry.value;
                if(rec == null) continue;
                if(fromUid.equals(rec.uid)){
                    rec.uid = toUid;
                    changed++;
                }
            }

            if(changed > 0) rebuildIndexes();
            return changed;
        }

        int backfillUidFromPid(){
            int changed = 0;
            for(ObjectMap.Entry<String, PlayerRecord> entry : players){
                PlayerRecord rec = entry.value;
                if(rec == null || rec.uid != null) continue;

                String derived = deriveUidFromUuidLike(rec.pid);
                if(derived == null) continue;
                rec.uid = derived;
                changed++;
            }
            if(changed > 0) rebuildIndexes();
            return changed;
        }

        Seq<PlayerRecord> findByIp(String ip){
            ip = normalizeIp(ip);
            Seq<PlayerRecord> out = new Seq<>();
            if(ip == null) return out;

            OrderedSet<String> set = ipToPids.get(ip);
            if(set == null) return out;

            for(String pid : set){
                PlayerRecord rec = players.get(pid);
                if(rec != null) out.add(rec);
            }

            out.sort((a, b) -> Long.compare(b.lastSeen, a.lastSeen));
            return out;
        }

        Seq<SameIpGroup> findSameIpGroups(int minAccounts){
            int min = Math.max(2, minAccounts);
            Seq<SameIpGroup> out = new Seq<>();

            for(ObjectMap.Entry<String, OrderedSet<String>> entry : ipToPids){
                String ip = normalizeIp(entry.key);
                OrderedSet<String> pids = entry.value;
                if(ip == null || pids == null || pids.size < min) continue;

                SameIpGroup group = new SameIpGroup();
                group.ip = ip;

                for(String pid : pids){
                    PlayerRecord rec = players.get(pid);
                    if(rec != null) group.players.add(rec);
                }
                if(group.players.size < min) continue;

                group.players.sort((a, b) -> Long.compare(b.lastSeen, a.lastSeen));
                group.latestSeen = group.players.first().lastSeen;
                out.add(group);
            }

            out.sort((a, b) -> {
                int byCount = Integer.compare(b.players.size, a.players.size);
                if(byCount != 0) return byCount;
                int bySeen = Long.compare(b.latestSeen, a.latestSeen);
                if(bySeen != 0) return bySeen;
                return safeString(a.ip).compareTo(safeString(b.ip));
            });
            return out;
        }

        Seq<PlayerRecord> allByLastSeen(){
            Seq<PlayerRecord> out = new Seq<>();
            for(ObjectMap.Entry<String, PlayerRecord> entry : players){
                if(entry.value != null) out.add(entry.value);
            }
            out.sort((a, b) -> Long.compare(b.lastSeen, a.lastSeen));
            return out;
        }

        private PlayerRecord ensure(String pid){
            PlayerRecord rec = players.get(pid);
            if(rec == null){
                rec = new PlayerRecord();
                rec.pid = pid;
                rec.firstSeen = Time.millis();
                rec.lastSeen = rec.firstSeen;
                players.put(pid, rec);
            }
            return rec;
        }

        private void rebuildIndexes(){
            ipToPids.clear();
            uidToPids.clear();

            for(ObjectMap.Entry<String, PlayerRecord> entry : players){
                String pid = normalizePid(entry.key);
                PlayerRecord rec = entry.value;
                if(pid == null || rec == null) continue;

                rec.pid = pid;

                String uid = normalizeShortUid(rec.uid);
                rec.uid = uid;
                if(uid != null){
                    uidToPids.get(uid, OrderedSet::new).add(pid);
                }

                if(rec.ips == null) continue;
                for(String ip : rec.ips){
                    String norm = normalizeIp(ip);
                    if(norm == null) continue;
                    ipToPids.get(norm, OrderedSet::new).add(pid);
                }
            }
        }

        private static boolean addUnique(Seq<String> seq, String value){
            if(value == null || value.isEmpty()) return false;
            if(seq.contains(value, false)) return false;
            seq.add(value);
            return true;
        }

        private static boolean containsIgnoreCase(String source, String needleLower){
            if(source == null || needleLower == null || needleLower.isEmpty()) return false;
            return source.toLowerCase(Locale.ROOT).contains(needleLower);
        }
    }

    public static class ChatEntry{
        public String uid;
        public String senderName;
        public String message;
        public String server;
        public long time;

        public ChatEntry copy(){
            ChatEntry out = new ChatEntry();
            out.uid = uid;
            out.senderName = senderName;
            out.message = message;
            out.server = server;
            out.time = time;
            return out;
        }
    }

    public static class ChatDbFile{
        public int schema = 2;
        public Seq<ChatEntry> entries = new Seq<>();
        public String integrityAlgo;
        public String integritySha256;
        public long integrityTime;
    }

    public static class ChatDayFile{
        public int schema = 2;
        public String date;
        public Seq<ChatEntry> entries = new Seq<>();
        public String integrityAlgo;
        public String integritySha256;
        public long integrityTime;
    }

    public static class ChatIndexFile{
        public int schema = 2;
        public ObjectMap<String, Seq<String>> uidDates = new ObjectMap<>();
        public int totalEntries;
        public long updatedAt;
        public String integrityAlgo;
        public String integritySha256;
        public long integrityTime;
    }

    private static class ChatDatabase{
        private static final int maxCachedDays = 8;

        private final ObjectMap<String, Seq<ChatEntry>> dayEntries = new ObjectMap<>();
        private final ObjectMap<String, ObjectSet<String>> dayDedupe = new ObjectMap<>();
        private final Seq<String> dayCacheOrder = new Seq<>();
        private final ObjectSet<String> dirtyDates = new ObjectSet<>();
        private final ObjectMap<String, OrderedSet<String>> uidToDates = new ObjectMap<>();
        private final Seq<String> integrityIssues = new Seq<>();

        private Fi storageDir;
        private Fi indexFile;
        private Json json;
        private boolean indexDirty;
        private int indexIntegrityState = integrityMissing;
        private int shardsChecked;
        private int shardsValid;
        private int shardsMissing;
        private int shardsMismatch;
        private int shardsUnsupported;
        private int totalEntries;

        void loadStorage(Fi chatsDir, Fi chatsIndexFile, Json serializer){
            storageDir = chatsDir;
            indexFile = chatsIndexFile;
            json = serializer;

            dayEntries.clear();
            dayDedupe.clear();
            dayCacheOrder.clear();
            dirtyDates.clear();
            uidToDates.clear();
            integrityIssues.clear();
            indexDirty = false;
            indexIntegrityState = integrityMissing;
            shardsChecked = 0;
            shardsValid = 0;
            shardsMissing = 0;
            shardsMismatch = 0;
            shardsUnsupported = 0;
            totalEntries = 0;

            if(storageDir == null || json == null) return;
            storageDir.mkdirs();

            if(indexFile != null && indexFile.exists()){
                try{
                    ChatIndexFile idx = json.fromJson(ChatIndexFile.class, indexFile.readString("UTF-8"));
                    indexIntegrityState = verifyChatIndexFileIntegrity(idx);
                    if(indexIntegrityState == integrityMismatch){
                        addIntegrityIssue("聊天索引校验失败：chat_index.json 可能已被修改。");
                    }else if(indexIntegrityState == integrityUnsupported){
                        addIntegrityIssue("聊天索引校验失败：chat_index.json 使用了不支持的校验算法。");
                    }
                }catch(Throwable t){
                    indexIntegrityState = integrityMismatch;
                    addIntegrityIssue("聊天索引读取失败，无法完成完整性校验。");
                    Log.err("SPDB: failed to read chat index, rebuilding.", t);
                }
            }

            rebuildIndexFromFiles();
            indexDirty = true;
            if(indexIntegrityState == integrityMissing) indexDirty = true;
        }

        boolean hasPendingWrites(){
            return !dirtyDates.isEmpty() || indexDirty;
        }

        boolean hasIntegrityIssues(){
            return !integrityIssues.isEmpty() || shardsMismatch > 0 || shardsUnsupported > 0 || indexIntegrityState == integrityMismatch || indexIntegrityState == integrityUnsupported;
        }

        Seq<String> integrityIssues(){
            return integrityIssues.copy();
        }

        int indexIntegrityState(){
            return indexIntegrityState;
        }

        int shardsChecked(){
            return shardsChecked;
        }

        int shardsValid(){
            return shardsValid;
        }

        int shardsMissing(){
            return shardsMissing;
        }

        int shardsMismatch(){
            return shardsMismatch;
        }

        int shardsUnsupported(){
            return shardsUnsupported;
        }

        int totalEntries(){
            return totalEntries;
        }

        void flushToStorage(Fi chatsDir, Fi chatsIndexFile, Json serializer){
            if(chatsDir != null) storageDir = chatsDir;
            if(chatsIndexFile != null) indexFile = chatsIndexFile;
            if(serializer != null) json = serializer;
            if(storageDir == null || json == null) return;

            storageDir.mkdirs();

            if(!dirtyDates.isEmpty()){
                Seq<String> dates = dirtyDates.toSeq();
                dates.sort();
                for(String date : dates){
                    Seq<ChatEntry> entries = dayEntries.get(date);
                    if(entries == null) continue;

                    ChatDayFile out = new ChatDayFile();
                    out.date = date;
                    out.entries = entries;
                    signChatDayFile(out);
                    dayFile(date).writeString(json.prettyPrint(out), false, "UTF-8");
                }
                dirtyDates.clear();
            }

            if(indexDirty){
                ChatIndexFile out = new ChatIndexFile();
                for(ObjectMap.Entry<String, OrderedSet<String>> entry : uidToDates){
                    Seq<String> dates = entry.value == null ? new Seq<>() : entry.value.orderedItems().copy();
                    dates.sort();
                    out.uidDates.put(entry.key, dates);
                }

                out.totalEntries = totalEntries;
                out.updatedAt = Time.millis();
                signChatIndexFile(out);

                if(indexFile != null){
                    indexFile.writeString(json.prettyPrint(out), false, "UTF-8");
                }

                indexDirty = false;
                indexIntegrityState = integrityValid;
            }
        }

        ChatDbFile snapshot(){
            ChatDbFile out = new ChatDbFile();
            Seq<String> dates = collectAllDates();
            dates.sort();

            for(String date : dates){
                Seq<ChatEntry> entries = ensureDayLoaded(date);
                for(ChatEntry entry : entries){
                    out.entries.add(entry.copy());
                }
            }

            out.entries.sort((a, b) -> Long.compare(a.time, b.time));
            return out;
        }

        int mergeFrom(ChatDbFile incoming){
            if(incoming == null || incoming.entries == null) return 0;
            int merged = 0;
            for(ChatEntry entry : incoming.entries){
                if(entry == null) continue;
                if(add(entry.uid, entry.senderName, entry.message, entry.server, entry.time)){
                    merged++;
                }
            }
            return merged;
        }

        boolean moveUid(String oldUid, String newUid){
            oldUid = normalizeUid(oldUid);
            newUid = normalizeUid(newUid);
            if(oldUid == null || newUid == null || oldUid.equals(newUid)) return false;

            OrderedSet<String> datesSet = uidToDates.get(oldUid);
            if(datesSet == null || datesSet.size == 0) return false;

            boolean changed = false;
            Seq<String> dates = datesSet.orderedItems().copy();
            for(String date : dates){
                Seq<ChatEntry> entries = ensureDayLoaded(date);
                boolean dayChanged = false;
                for(ChatEntry entry : entries){
                    if(oldUid.equals(entry.uid)){
                        entry.uid = newUid;
                        dayChanged = true;
                    }
                }

                if(dayChanged){
                    Seq<ChatEntry> normalized = normalizeDayEntries(entries, date, false);
                    dayEntries.put(date, normalized);
                    dayDedupe.put(date, buildDedupe(normalized));
                    dirtyDates.add(date);
                    addUidDate(newUid, date);
                    changed = true;
                }
            }

            if(changed){
                uidToDates.remove(oldUid);
                indexDirty = true;
            }
            return changed;
        }

        boolean add(String uid, String senderName, String message, String server, long time){
            uid = normalizeUid(uid);
            if(uid == null || message == null) return false;

            ChatEntry entry = new ChatEntry();
            entry.uid = uid;
            entry.senderName = safeName(senderName);
            entry.message = message;
            entry.server = normalizeServer(server);
            entry.time = time > 0 ? time : Time.millis();

            String date = dateKey(entry.time);
            Seq<ChatEntry> entries = ensureDayLoaded(date);
            ObjectSet<String> dedupe = dayDedupe.get(date, ObjectSet::new);

            String key = dedupeKey(entry);
            if(!dedupe.add(key)) return false;

            entries.add(entry);
            dirtyDates.add(date);
            touchDay(date);
            addUidDate(uid, date);
            totalEntries++;
            return true;
        }

        Seq<ChatEntry> findByUid(String uid){
            uid = normalizeUid(uid);
            Seq<ChatEntry> out = new Seq<>();
            if(uid == null) return out;

            OrderedSet<String> datesSet = uidToDates.get(uid);
            if(datesSet == null || datesSet.size == 0) return out;

            Seq<String> dates = datesSet.orderedItems().copy();
            dates.sort((a, b) -> b.compareTo(a));

            for(String date : dates){
                Seq<ChatEntry> entries = ensureDayLoaded(date);
                for(ChatEntry entry : entries){
                    if(uid.equals(entry.uid)) out.add(entry);
                }
            }

            out.sort((a, b) -> Long.compare(b.time, a.time));
            return out;
        }

        Seq<ChatEntry> findRecent(int offset, int limit){
            Seq<ChatEntry> all = new Seq<>();
            if(limit <= 0) return all;

            Seq<String> dates = collectAllDates();
            for(String date : dates){
                Seq<ChatEntry> entries = ensureDayLoaded(date);
                for(ChatEntry entry : entries){
                    all.add(entry);
                }
            }

            all.sort((a, b) -> Long.compare(b.time, a.time));
            if(offset < 0) offset = 0;
            if(offset >= all.size) return new Seq<>();

            int to = Math.min(offset + limit, all.size);
            Seq<ChatEntry> out = new Seq<>();
            for(int i = offset; i < to; i++){
                out.add(all.get(i));
            }
            return out;
        }

        private Seq<ChatEntry> ensureDayLoaded(String date){
            if(!isValidDateKey(date)) return new Seq<>();

            Seq<ChatEntry> entries = dayEntries.get(date);
            if(entries != null){
                touchDay(date);
                return entries;
            }

            entries = new Seq<>();
            if(storageDir != null && json != null){
                Fi file = dayFile(date);
                if(file.exists()){
                    try{
                        ChatDayFile loaded = json.fromJson(ChatDayFile.class, file.readString("UTF-8"));
                        if(loaded != null && loaded.entries != null){
                            int state = verifyChatDayFileIntegrity(loaded);
                            if(state == integrityMissing){
                                dirtyDates.add(date);
                            }else if(state == integrityMismatch){
                                addIntegrityIssue("聊天分片校验失败：" + file.name() + " 可能已被修改。");
                            }else if(state == integrityUnsupported){
                                addIntegrityIssue("聊天分片校验失败：" + file.name() + " 使用了不支持的校验算法。");
                            }

                            entries = normalizeDayEntries(loaded.entries, date, true);
                        }
                    }catch(Throwable t){
                        addIntegrityIssue("聊天分片读取失败：" + file.name() + " 无法完成完整性校验。");
                        Log.err("SPDB: failed reading chat shard @.", file.path(), t);
                    }
                }
            }

            dayEntries.put(date, entries);
            dayDedupe.put(date, buildDedupe(entries));
            touchDay(date);
            return entries;
        }

        private Seq<ChatEntry> normalizeDayEntries(Seq<ChatEntry> source, String date, boolean updateIndex){
            Seq<ChatEntry> result = new Seq<>();
            ObjectSet<String> localDedupe = new ObjectSet<>();

            for(ChatEntry raw : source){
                if(raw == null) continue;
                String uid = normalizeUid(raw.uid);
                if(uid == null || raw.message == null) continue;

                ChatEntry entry = new ChatEntry();
                entry.uid = uid;
                entry.senderName = safeName(raw.senderName);
                entry.message = raw.message;
                entry.server = normalizeServer(raw.server);
                entry.time = raw.time > 0 ? raw.time : Time.millis();

                String key = dedupeKey(entry);
                if(!localDedupe.add(key)) continue;
                result.add(entry);

                if(updateIndex){
                    addUidDate(entry.uid, date);
                }
            }

            return result;
        }

        private ObjectSet<String> buildDedupe(Seq<ChatEntry> entries){
            ObjectSet<String> dedupe = new ObjectSet<>();
            for(ChatEntry entry : entries){
                dedupe.add(dedupeKey(entry));
            }
            return dedupe;
        }

        private void addUidDate(String uid, String date){
            OrderedSet<String> set = uidToDates.get(uid, OrderedSet::new);
            if(set.add(date)) indexDirty = true;
        }

        private void addIntegrityIssue(String message){
            if(message == null || message.isEmpty()) return;
            if(!integrityIssues.contains(message, false)) integrityIssues.add(message);
        }

        private void rebuildIndexFromFiles(){
            uidToDates.clear();
            totalEntries = 0;
            shardsChecked = 0;
            shardsValid = 0;
            shardsMissing = 0;
            shardsMismatch = 0;
            shardsUnsupported = 0;

            if(storageDir == null || json == null || !storageDir.exists()) return;

            Fi[] files = storageDir.list();
            for(Fi file : files){
                String date = dateFromFileName(file.name());
                if(date == null) continue;

                shardsChecked++;
                try{
                    ChatDayFile loaded = json.fromJson(ChatDayFile.class, file.readString("UTF-8"));
                    if(loaded == null || loaded.entries == null){
                        shardsMismatch++;
                        addIntegrityIssue("聊天分片读取失败：" + file.name() + " 文件格式无效。");
                        continue;
                    }

                    int state = verifyChatDayFileIntegrity(loaded);
                    if(state == integrityValid){
                        shardsValid++;
                    }else if(state == integrityMissing){
                        shardsMissing++;
                    }else if(state == integrityUnsupported){
                        shardsUnsupported++;
                        addIntegrityIssue("聊天分片校验失败：" + file.name() + " 使用了不支持的校验算法。");
                    }else{
                        shardsMismatch++;
                        addIntegrityIssue("聊天分片校验失败：" + file.name() + " 可能已被修改。");
                    }

                    Seq<ChatEntry> normalized = normalizeDayEntries(loaded.entries, date, true);
                    totalEntries += normalized.size;

                    if(state == integrityMissing){
                        dayEntries.put(date, normalized);
                        dayDedupe.put(date, buildDedupe(normalized));
                        dirtyDates.add(date);
                        touchDay(date);
                    }
                }catch(Throwable t){
                    shardsMismatch++;
                    addIntegrityIssue("聊天分片读取失败：" + file.name() + " 无法完成完整性校验。");
                    Log.err("SPDB: failed indexing chat shard @.", file.path(), t);
                }
            }
        }

        private Seq<String> collectAllDates(){
            OrderedSet<String> dates = new OrderedSet<>();

            for(String date : dayEntries.keys()){
                if(isValidDateKey(date)) dates.add(date);
            }

            if(storageDir != null && storageDir.exists()){
                Fi[] files = storageDir.list();
                for(Fi file : files){
                    String date = dateFromFileName(file.name());
                    if(date != null) dates.add(date);
                }
            }

            for(ObjectMap.Entry<String, OrderedSet<String>> entry : uidToDates){
                if(entry.value == null) continue;
                for(String date : entry.value){
                    if(isValidDateKey(date)) dates.add(date);
                }
            }

            return dates.orderedItems().copy();
        }

        private Fi dayFile(String date){
            return storageDir.child("chats_" + date + ".json");
        }

        private void touchDay(String date){
            dayCacheOrder.remove(date, false);
            dayCacheOrder.add(date);
            trimCache();
        }

        private void trimCache(){
            int guard = 0;
            while(dayCacheOrder.size > maxCachedDays && guard < dayCacheOrder.size * 2){
                String oldest = dayCacheOrder.remove(0);
                if(dirtyDates.contains(oldest)){
                    dayCacheOrder.add(oldest);
                    guard++;
                    continue;
                }
                dayEntries.remove(oldest);
                dayDedupe.remove(oldest);
                guard++;
            }
        }

        private static String dateFromFileName(String name){
            if(name == null) return null;
            if(!name.startsWith("chats_") || !name.endsWith(".json")) return null;
            String date = name.substring(6, name.length() - 5);
            return isValidDateKey(date) ? date : null;
        }

        private static boolean isValidDateKey(String date){
            if(date == null || date.length() != 8) return false;
            for(int i = 0; i < date.length(); i++){
                char c = date.charAt(i);
                if(c < '0' || c > '9') return false;
            }
            return true;
        }

        private static String dateKey(long millis){
            synchronized(TimeFmt.day){
                return TimeFmt.day.format(new Date(millis));
            }
        }

        private static String dedupeKey(ChatEntry entry){
            return entry.uid + "|" + entry.time + "|" + normalizeServer(entry.server) + "|" + entry.message;
        }
    }

    private static class MindustryXOverlayUI{
        private boolean initialized;
        private boolean installed;

        private Object instance;
        private Method registerWindow;
        private Method setAvailability;
        private Method getData;
        private Method setEnabled;
        private Method setPinned;
        private Method setResizable;
        private Method setAutoHeight;

        boolean isInstalled(){
            if(initialized) return installed;
            initialized = true;

            try{
                installed = Vars.mods != null && Vars.mods.locateMod("mindustryx") != null;
            }catch(Throwable ignored){
                installed = false;
            }
            if(!installed) return false;

            try{
                Class<?> cls = Class.forName("mindustryX.features.ui.OverlayUI");
                instance = cls.getField("INSTANCE").get(null);
                registerWindow = cls.getMethod("registerWindow", String.class, Table.class);
            }catch(Throwable t){
                installed = false;
                Log.err("SPDB: OverlayUI reflection init failed.", t);
            }

            return installed;
        }

        Object registerWindow(String name, Table table, Prov<Boolean> availability){
            if(!isInstalled()) return null;

            try{
                Object window = registerWindow.invoke(instance, name, table);
                tryInitWindowAccessors(window);
                if(window != null && availability != null && setAvailability != null){
                    setAvailability.invoke(window, availability);
                }
                return window;
            }catch(Throwable t){
                Log.err("SPDB: OverlayUI.registerWindow failed.", t);
                return null;
            }
        }

        void tryConfigureWindow(Object window, boolean autoHeight, boolean resizable){
            if(window == null) return;
            try{
                tryInitWindowAccessors(window);
                if(setAutoHeight != null) setAutoHeight.invoke(window, autoHeight);
                if(setResizable != null) setResizable.invoke(window, resizable);
            }catch(Throwable ignored){
            }
        }

        void setEnabledAndPinned(Object window, boolean enabled, boolean pinned){
            if(window == null) return;

            try{
                tryInitWindowAccessors(window);
                if(getData == null) return;

                Object data = getData.invoke(window);
                if(data == null) return;

                if(setEnabled != null) setEnabled.invoke(data, enabled);
                if(setPinned != null) setPinned.invoke(data, pinned);
            }catch(Throwable ignored){
            }
        }

        private void tryInitWindowAccessors(Object window){
            if(window == null) return;
            if(getData != null || setAvailability != null) return;

            try{
                Class<?> wc = window.getClass();

                try{
                    setAvailability = wc.getMethod("setAvailability", Prov.class);
                }catch(Throwable ignored){
                    setAvailability = null;
                }

                try{
                    setResizable = wc.getMethod("setResizable", boolean.class);
                }catch(Throwable ignored){
                    setResizable = null;
                }

                try{
                    setAutoHeight = wc.getMethod("setAutoHeight", boolean.class);
                }catch(Throwable ignored){
                    setAutoHeight = null;
                }

                getData = wc.getMethod("getData");

                Object data = getData.invoke(window);
                if(data != null){
                    Class<?> dc = data.getClass();

                    try{
                        setEnabled = dc.getMethod("setEnabled", boolean.class);
                    }catch(Throwable ignored){
                        setEnabled = null;
                    }

                    try{
                        setPinned = dc.getMethod("setPinned", boolean.class);
                    }catch(Throwable ignored){
                        setPinned = null;
                    }
                }
            }catch(Throwable ignored){
            }
        }
    }
}
