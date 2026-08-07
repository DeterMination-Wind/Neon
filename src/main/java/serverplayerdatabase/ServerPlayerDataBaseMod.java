package serverplayerdatabase;

import arc.Core;
import arc.Events;
import arc.files.Fi;
import arc.func.Cons;
import arc.func.Prov;
import arc.graphics.Color;
import arc.input.KeyCode;
import arc.math.geom.Vec2;
import arc.scene.Element;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.TextArea;
import arc.scene.ui.TextField;
import arc.scene.ui.layout.Table;
import arc.struct.IntMap;
import arc.struct.IntSeq;
import arc.struct.ObjectMap;
import arc.struct.ObjectSet;
import arc.struct.OrderedSet;
import arc.struct.Seq;
import arc.util.CommandHandler;
import arc.util.Http;
import arc.util.Interval;
import arc.util.Log;
import arc.util.Strings;
import arc.util.Time;
import arc.util.Threads;
import arc.util.io.Streams;
import arc.util.serialization.Json;
import arc.util.serialization.JsonReader;
import arc.util.serialization.JsonValue;
import mdtxcompat.LegacyMindustryXGuard;
import mdtxcompat.OverlayUiBridge;
import mindustry.Vars;
import mindustry.game.EventType.ClientLoadEvent;
import mindustry.game.EventType.ClientServerConnectEvent;
import mindustry.game.EventType.DisposeEvent;
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
import mindustry.ui.Bar;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.SettingsMenuDialog;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.ui.dialogs.TraceDialog;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;
import java.util.zip.CRC32;

public class ServerPlayerDataBaseMod extends Mod{
    /** When true, this mod is running as a bundled component inside Neon. */
    public static boolean bekBundled = false;

    private static final String overlayQueryWindowName = "玩家数据库 / DB Query";
    private static final String overlayDebugWindowName = "解析调试 / Parser Debug";


    private static final String keyCollect = "spdb-collect";
    private static final String keyRecordChat = "spdb-record-chat";
    private static final String keyAutoTrace = "spdb-auto-trace";
    private static final String keyShowAutoTraceDialog = "spdb-show-auto-trace-dialog";
    private static final String keySemanticSearchEnabled = "spdb-semantic-enabled";
    private static final String integrityAlgorithm = "sha256-spdb-v1";
    private static final String semanticSearchModelFileName = "bge-base-zh-v1.5-q8_0.gguf";
    private static final String semanticSearchModelUrl = "https://hf-mirror.com/CompendiumLabs/bge-base-zh-v1.5-gguf/resolve/main/" + semanticSearchModelFileName;
    private static final String semanticSearchRuntimeFileName = "llama-4.1.0.jar";
    private static final String semanticSearchRuntimeUrl = "https://maven.aliyun.com/repository/central/de/kherud/llama/4.1.0/" + semanticSearchRuntimeFileName;
    private static final long embeddingFlushIntervalMs = 5L * 60L * 1000L;
    private static final long semanticSearchShutdownTimeoutMs = 3000L;

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
    private final OverlayUiBridge overlayUI;
    private final Map<String, String> ipGeoCache = new LinkedHashMap<>(64, 0.75f, true){
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, String> eldest){
            return size() > 512;
        }
    };
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
    private Fi chatsDbFile;
    private Fi chatsDir;
    private Fi chatsIndexFile;
    private Fi legacyChatsFile;
    private Fi semanticSearchModelFile;
    private Fi semanticSearchRuntimeFile;
    private Fi semanticSearchNativeDir;

    private boolean playersDirty;
    private boolean chatsDirty;
    private String currentServer = "offline";
    private int playersFileIntegrityState = integrityMissing;
    private final Seq<String> playersIntegrityIssues = new Seq<>();
    private final Object semanticSearchLock = new Object();
    private EmbeddingEngine embeddingEngine;
    private EmbeddingIndex embeddingIndex;
    private EmbeddingEngine pendingEmbeddingEngine;
    private EmbeddingIndex pendingEmbeddingIndex;
    private long lastEmbeddingFlushAt;
    private int semanticSearchLifecycleToken;
    private volatile boolean semanticSearchResourcesStarted;
    private volatile boolean semanticSearchExitFallbackStarted;
    private volatile boolean semanticSearchDisposing;
    private volatile boolean semanticSearchDownloadInProgress;
    private volatile boolean semanticSearchInitializing;
    private volatile boolean semanticSearchPromptOpen;
    private volatile float semanticSearchDownloadProgress = -1f;
    private volatile float semanticSearchDownloadTotalMb;
    private volatile String semanticSearchStatusOverride;
    private volatile String semanticSearchPendingQuery;
    private volatile int semanticSearchUiRevision;
    private BaseDialog semanticSearchProgressDialog;

    private TraceDialog originalTraceDialog;

    private OverlayUiBridge.OverlayWindowHandle overlayQueryWindow;
    private OverlayUiBridge.OverlayWindowHandle overlayDebugWindow;
    private OverlayQueryContent overlayQueryContent;
    private DebugContent debugContent;
    private float nextAttachAttempt;
    private boolean overlayFocusClearQueued;

    private BaseDialog fallbackQueryDialog;
    private BaseDialog fallbackDebugDialog;
    private QueryContent fallbackQueryContent;

    private java.lang.reflect.Field chatMessagesField;

    public ServerPlayerDataBaseMod(){
        this(vanillaOverlayUi());
    }

    protected ServerPlayerDataBaseMod(OverlayUiBridge overlayUi){
        overlayUI = overlayUi;
        registerSemanticSearchShutdownHook();
        Events.on(ClientLoadEvent.class, e -> {
            if(Vars.headless) return;

            Core.settings.defaults(keyCollect, true);
            Core.settings.defaults(keyRecordChat, false);
            Core.settings.defaults(keyAutoTrace, true);
            Core.settings.defaults(keyShowAutoTraceDialog, false);
            Core.settings.defaults(keySemanticSearchEnabled, false);
            if(!bekBundled) GithubUpdateCheck.applyDefaults();

            initStorage();
            loadLocalData();
            initializeSemanticSearchFromSettings();
            registerSettings();
            if(!bekBundled) GithubUpdateCheck.checkOnce();
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

        Events.on(DisposeEvent.class, e -> {
            shutdownSemanticSearchResources(true);
            ipGeoExecutor.shutdownNow();
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

            int entryId = chatDb.add(uid, sender, message, currentServer, Time.millis());
            if(entryId > 0){
                chatsDirty = true;
                EmbeddingIndex index = embeddingIndex;
                if(index != null && !semanticSearchDisposing){
                    ChatEntry entry = new ChatEntry();
                    entry.uid = uid;
                    entry.senderName = sender;
                    entry.message = message;
                    entry.server = normalizeServer(currentServer);
                    entry.time = Time.millis();
                    index.addEntryAsync(entryId, entry);
                }
            }
        });

        Events.run(Trigger.update, this::update);
    }

    @Override
    public void registerClientCommands(CommandHandler handler){
        handler.<Player>register("spdb", bundle("spdb.command.query", "Open the ServerPlayerDataBase query window."), (args, player) -> Core.app.post(this::showStandaloneQueryDialog));
        handler.<Player>register("spdb-debug", bundle("spdb.command.debug", "Open the ServerPlayerDataBase debug window."), (args, player) -> Core.app.post(this::showStandaloneDebugDialog));
        handler.<Player>register("spdb-search", "<query...>", bundle("spdb.command.search", "Open semantic search and fill the query."), (args, player) -> {
            String query = args == null || args.length == 0 ? "" : String.join(" ", args).trim();
            Core.app.post(() -> showSemanticSearchDialog(query));
        });
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

        EmbeddingIndex index = embeddingIndex;
        if(index != null && !semanticSearchDisposing && Time.millis() - lastEmbeddingFlushAt >= embeddingFlushIntervalMs){
            lastEmbeddingFlushAt = Time.millis();
            index.flushIfDirty();
        }
    }

    private void releaseOverlayFocusIfPointerOutside(){
        if(Core.scene == null || Core.input == null) return;
        if(overlayQueryContent == null && debugContent == null
            && (overlayQueryWindow == null || overlayQueryWindow.asElement() == null)
            && (overlayDebugWindow == null || overlayDebugWindow.asElement() == null)) return;
        if(!hasOverlayOwnedFocus()) return;
        if(isUiPointerActive()) return;
        if(isPointerOverOverlayContent()) return;

        if(overlayFocusClearQueued) return;
        overlayFocusClearQueued = true;

        // Match customMarker's approach: run delayed clear to handle late focus re-assignments.
        Core.app.post(() -> Core.app.post(() -> {
            overlayFocusClearQueued = false;
            if(isUiPointerActive()) return;
            if(isPointerOverOverlayContent()) return;
            clearOverlayFocusIfOwned();
        }));
    }

    private boolean hasOverlayOwnedFocus(){
        if(Core.scene == null) return false;
        return ownsOverlayFocus(Core.scene.getScrollFocus()) || ownsOverlayFocus(Core.scene.getKeyboardFocus());
    }

    private boolean isUiPointerActive(){
        if(Core.input == null) return false;
        if(Core.input.isTouched()) return true;
        return Core.input.keyDown(KeyCode.mouseLeft)
            || Core.input.keyDown(KeyCode.mouseRight)
            || Core.input.keyDown(KeyCode.mouseMiddle);
    }

    private boolean clearOverlayFocusIfOwned(){
        if(Core.scene == null) return false;

        Element scrollFocus = Core.scene.getScrollFocus();
        Element keyboardFocus = Core.scene.getKeyboardFocus();
        if(!ownsOverlayFocus(scrollFocus) && !ownsOverlayFocus(keyboardFocus)) return false;

        if(overlayQueryContent != null) Core.scene.unfocus(overlayQueryContent.root);
        if(debugContent != null) Core.scene.unfocus(debugContent.root);
        if(overlayQueryWindow != null && overlayQueryWindow.asElement() != null) Core.scene.unfocus(overlayQueryWindow.asElement());
        if(overlayDebugWindow != null && overlayDebugWindow.asElement() != null) Core.scene.unfocus(overlayDebugWindow.asElement());

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
        return ownsOverlayElement(focus);
    }

    private boolean ownsOverlayElement(Element element){
        if(element == null) return false;
        if(overlayQueryContent != null && (element == overlayQueryContent.root || element.isDescendantOf(overlayQueryContent.root))) return true;
        if(debugContent != null && (element == debugContent.root || element.isDescendantOf(debugContent.root))) return true;
        if(overlayQueryWindow != null && overlayQueryWindow.asElement() != null){
            Element queryWindow = overlayQueryWindow.asElement();
            if(element == queryWindow || element.isDescendantOf(queryWindow)) return true;
        }
        if(overlayDebugWindow != null && overlayDebugWindow.asElement() != null){
            Element debugWindow = overlayDebugWindow.asElement();
            if(element == debugWindow || element.isDescendantOf(debugWindow)) return true;
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
        if(target.admin) return;

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
        chatsDbFile = dataDir.child("chats.sqlite");
        chatsDir = dataDir.child("chats");
        chatsDir.mkdirs();
        chatsIndexFile = chatsDir.child("chat_index.json");
        legacyChatsFile = dataDir.child("chats.json");
        semanticSearchModelFile = dataDir.child("models").child(semanticSearchModelFileName);
        semanticSearchRuntimeFile = dataDir.child("runtime").child(semanticSearchRuntimeFileName);
        semanticSearchNativeDir = dataDir.child("native").child("llama-4.1.0");
    }

    private boolean semanticSearchEnabled(){
        return Core.settings != null && Core.settings.getBool(keySemanticSearchEnabled, false);
    }

    private void setSemanticSearchEnabled(boolean enabled){
        if(Core.settings == null) return;
        Core.settings.put(keySemanticSearchEnabled, enabled);
    }

    private void initializeSemanticSearchFromSettings(){
        if(!semanticSearchEnabled()){
            semanticSearchStatusOverride = bundle("spdb.semantic.status.disabled", "Semantic search is disabled. Open its tab or run /spdb-search to enable it.");
            return;
        }

        startSemanticSearchIfLocalComponentsReady();
    }

    private void startSemanticSearchIfLocalComponentsReady(){
        if(!semanticSearchEnabled() || semanticSearchDisposing) return;
        if(isAndroidRuntime()){
            semanticSearchStatusOverride = bundle("spdb.semantic.status.android", "Semantic search is unavailable on Android.");
            bumpSemanticSearchUiRevision();
            return;
        }
        if(chatsDbFile == null || semanticSearchModelFile == null || semanticSearchRuntimeFile == null){
            semanticSearchStatusOverride = bundle("spdb.semantic.status.db-missing", "The chat database path is unavailable; semantic search is unavailable.");
            bumpSemanticSearchUiRevision();
            return;
        }

        semanticSearchStatusOverride = null;
        if(semanticSearchRuntimeFile.exists() && semanticSearchRuntimeFile.length() <= 0L){
            semanticSearchStatusOverride = bundle("spdb.semantic.status.runtime-empty", "The local semantic-search runtime file is empty; download it again.");
            bumpSemanticSearchUiRevision();
            return;
        }
        if(semanticSearchModelFile.exists() && semanticSearchModelFile.length() <= 0L){
            semanticSearchStatusOverride = bundle("spdb.semantic.status.model-empty", "The local semantic model file is empty; download it again.");
            bumpSemanticSearchUiRevision();
            return;
        }
        if(semanticSearchRuntimeFile.exists() && semanticSearchRuntimeFile.length() > 0L && semanticSearchModelFile.exists() && semanticSearchModelFile.length() > 0L){
            startSemanticSearchInitialization(false);
        }else{
            bumpSemanticSearchUiRevision();
        }
    }

    private void ensureSemanticSearchReady(String query){
        rememberSemanticSearchQuery(query);
        if(!semanticSearchEnabled()){
            setSemanticSearchEnabled(true);
        }

        if(semanticSearchDisposing) return;
        if(isAndroidRuntime()){
            semanticSearchStatusOverride = bundle("spdb.semantic.status.android", "Semantic search is unavailable on Android.");
            bumpSemanticSearchUiRevision();
            return;
        }
        if(chatsDbFile == null){
            semanticSearchStatusOverride = bundle("spdb.semantic.status.db-missing", "The chat database path is unavailable; semantic search is unavailable.");
            bumpSemanticSearchUiRevision();
            return;
        }
        if(semanticSearchReady() || semanticSearchDownloadInProgress || semanticSearchInitializing || semanticSearchPromptOpen) return;
        if(embeddingIndex != null) return;
        if(semanticSearchModelFile == null || semanticSearchRuntimeFile == null){
            semanticSearchStatusOverride = bundle("spdb.semantic.status.db-missing", "The chat database path is unavailable; semantic search is unavailable.");
            bumpSemanticSearchUiRevision();
            return;
        }
        if(semanticSearchRuntimeFile.exists() && semanticSearchRuntimeFile.length() <= 0L){
            cleanupSemanticSearchRuntimeFile();
            semanticSearchStatusOverride = bundle("spdb.semantic.status.runtime-empty", "The local semantic-search runtime file is empty; download it again.");
        }
        if(!semanticSearchRuntimeFile.exists() || semanticSearchRuntimeFile.length() <= 0L){
            requestSemanticSearchDownload(true);
            return;
        }
        if(semanticSearchModelFile.exists() && semanticSearchModelFile.length() <= 0L){
            cleanupSemanticSearchModelFile();
            semanticSearchStatusOverride = bundle("spdb.semantic.status.model-empty", "The local semantic model file is empty; download it again.");
        }
        if(semanticSearchModelFile.exists() && semanticSearchModelFile.length() > 0L){
            startSemanticSearchInitialization(false);
            return;
        }
        requestSemanticSearchDownload(false);
    }

    private boolean semanticSearchReady(){
        EmbeddingIndex index = embeddingIndex;
        return !semanticSearchDisposing && index != null && index.isAvailable() && index.isReady();
    }

    private Seq<EmbeddingIndex.SearchResult> searchSemantic(String query, int limit){
        EmbeddingIndex index = embeddingIndex;
        return semanticSearchDisposing || index == null ? new Seq<>() : index.search(query, limit);
    }

    private String semanticSearchStatus(){
        if(!semanticSearchEnabled()){
            return bundle("spdb.semantic.status.disabled", "Semantic search is disabled. Open its tab or run /spdb-search to enable it.");
        }
        if(semanticSearchDisposing){
            return bundle("spdb.semantic.status.releasing", "Releasing semantic-search resources...");
        }
        if(isAndroidRuntime()){
            return bundle("spdb.semantic.status.android", "Semantic search is unavailable on Android.");
        }
        if(chatsDbFile == null){
            return bundle("spdb.semantic.status.db-missing", "The chat database path is unavailable; semantic search is unavailable.");
        }
        if(semanticSearchDownloadInProgress){
            if(semanticSearchDownloadTotalMb > 0f && semanticSearchDownloadProgress >= 0f){
                float downloadedMb = semanticSearchDownloadProgress * semanticSearchDownloadTotalMb;
                return bundleFormat(
                    "spdb.semantic.progress.known",
                    "Downloaded @ / @ MB",
                    Strings.autoFixed(downloadedMb, 2),
                    Strings.autoFixed(semanticSearchDownloadTotalMb, 2)
                ) + " (" + Math.round(semanticSearchDownloadProgress * 100f) + "%)";
            }
            return bundle("spdb.semantic.progress.unknown", "Downloading; total size is not available yet.");
        }
        if(embeddingIndex != null){
            String indexStatus = embeddingIndex.status();
            if(indexStatus != null && !indexStatus.trim().isEmpty() && (!embeddingIndex.isInitialStatus() || semanticSearchStatusOverride == null)){
                return indexStatus;
            }
        }
        if(semanticSearchStatusOverride != null && !semanticSearchStatusOverride.trim().isEmpty()){
            return semanticSearchStatusOverride;
        }
        if(semanticSearchRuntimeFile != null && semanticSearchRuntimeFile.exists() && semanticSearchRuntimeFile.length() <= 0L){
            return bundle("spdb.semantic.status.runtime-empty", "The local semantic-search runtime file is empty; download it again.");
        }
        if(semanticSearchModelFile != null && semanticSearchModelFile.exists() && semanticSearchModelFile.length() <= 0L){
            return bundle("spdb.semantic.status.model-empty", "The local semantic model file is empty; download it again.");
        }
        if(semanticSearchRuntimeFile == null || !semanticSearchRuntimeFile.exists() || semanticSearchRuntimeFile.length() <= 0L){
            return bundle("spdb.semantic.status.runtime-missing", "The semantic-search runtime is not downloaded. Confirm the download when opening semantic search for the first time.");
        }
        if(semanticSearchModelFile != null && semanticSearchModelFile.exists() && semanticSearchModelFile.length() > 0L){
            return bundle("spdb.semantic.status.model-ready", "The local model is ready; waiting for initialization.");
        }
        return bundle("spdb.semantic.status.model-missing", "The model is not downloaded. Confirm the download when opening semantic search for the first time.");
    }

    private void requestSemanticSearchDownload(boolean runtime){
        Fi target = runtime ? semanticSearchRuntimeFile : semanticSearchModelFile;
        if(!semanticSearchEnabled() || semanticSearchDisposing || Vars.ui == null || target == null) return;

        semanticSearchPromptOpen = true;
        semanticSearchStatusOverride = runtime
            ? bundle("spdb.semantic.status.runtime-prompt-open", "Waiting for confirmation to download the semantic-search runtime.")
            : bundle("spdb.semantic.status.prompt-open", "Waiting for confirmation to download the semantic model.");
        bumpSemanticSearchUiRevision();

        String prompt = (runtime
            ? bundle("spdb.semantic.prompt.runtime-reason", "Semantic search requires a local runtime.")
            : bundle("spdb.semantic.prompt.reason", "Semantic search requires a local semantic model."))
            + "\n" + (runtime
            ? bundle("spdb.semantic.prompt.runtime-source", "Download source: Alibaba Maven Central mirror")
            : bundle("spdb.semantic.prompt.source", "Download source: hf-mirror"))
            + "\n" + bundle("spdb.semantic.prompt.target", "Save path:")
            + "\n" + target.absolutePath()
            + "\n\n" + bundle("spdb.semantic.prompt.cancel", "Semantic search will remain unavailable after cancellation; you can retry later.");

        Vars.ui.showCustomConfirm("@confirm", prompt, "@ok", "@cancel", () -> {
            if(semanticSearchDisposing || !semanticSearchEnabled()) return;
            semanticSearchPromptOpen = false;
            semanticSearchStatusOverride = runtime
                ? bundle("spdb.semantic.status.runtime-download-start", "Preparing to download the semantic-search runtime...")
                : bundle("spdb.semantic.status.download-start", "Preparing to download the semantic model...");
            bumpSemanticSearchUiRevision();
            startSemanticSearchFileDownload(runtime);
        }, () -> {
            if(semanticSearchDisposing || !semanticSearchEnabled()) return;
            semanticSearchPromptOpen = false;
            semanticSearchStatusOverride = bundle("spdb.semantic.status.cancelled", "Semantic model download cancelled; semantic search is unavailable.");
            bumpSemanticSearchUiRevision();
        });
    }

    private void startSemanticSearchFileDownload(boolean runtime){
        Fi target = runtime ? semanticSearchRuntimeFile : semanticSearchModelFile;
        if(!semanticSearchEnabled() || semanticSearchDisposing || target == null || semanticSearchDownloadInProgress) return;

        if(target.parent() != null) target.parent().mkdirs();
        semanticSearchDownloadInProgress = true;
        semanticSearchDownloadProgress = -1f;
        semanticSearchDownloadTotalMb = 0f;
        semanticSearchStatusOverride = runtime
            ? bundle("spdb.semantic.status.runtime-downloading", "Downloading the semantic-search runtime from the Alibaba Maven Central mirror...")
            : bundle("spdb.semantic.status.downloading", "Downloading the semantic model from hf-mirror...");
        bumpSemanticSearchUiRevision();
        Core.app.post(this::showSemanticSearchProgressDialog);

        Http.get(runtime ? semanticSearchRuntimeUrl : semanticSearchModelUrl)
            .timeout(60000)
            .header("User-Agent", "Mindustry")
            .error(err -> Core.app.post(() -> failSemanticSearchDownload(err, runtime)))
            .submit(res -> {
                long total = res.getContentLength();
                semanticSearchDownloadTotalMb = total > 0L ? total / 1024f / 1024f : 0f;

                try(InputStream in = res.getResultAsStream(); OutputStream out = target.write(false, 1024 * 1024)){
                    if(in == null) throw new IllegalStateException("Download stream was empty.");

                    byte[] buf = new byte[1024 * 1024];
                    long read = 0L;
                    int r;
                    while((r = in.read(buf)) != -1){
                        out.write(buf, 0, r);
                        read += r;
                        if(total > 0L){
                            semanticSearchDownloadProgress = Math.min(1f, read / (float)total);
                        }
                    }
                }catch(Throwable t){
                    Core.app.post(() -> failSemanticSearchDownload(t, runtime));
                    return;
                }

                Core.app.post(() -> {
                    if(semanticSearchDisposing || !semanticSearchEnabled()){
                        semanticSearchDownloadInProgress = false;
                        semanticSearchDownloadProgress = -1f;
                        semanticSearchDownloadTotalMb = 0f;
                        hideSemanticSearchProgressDialog();
                        return;
                    }
                    semanticSearchDownloadInProgress = false;
                    semanticSearchDownloadProgress = 1f;
                    hideSemanticSearchProgressDialog();
                    semanticSearchStatusOverride = runtime
                        ? bundle("spdb.semantic.status.runtime-download-complete", "Runtime download completed; checking the semantic model...")
                        : bundle("spdb.semantic.status.download-complete", "Model download completed; initializing...");
                    bumpSemanticSearchUiRevision();
                    if(runtime){
                        ensureSemanticSearchReady(semanticSearchPendingQuery);
                    }else{
                        startSemanticSearchInitialization(true);
                    }
                });
            });
    }

    private void failSemanticSearchDownload(Throwable t, boolean runtime){
        semanticSearchDownloadInProgress = false;
        semanticSearchDownloadProgress = -1f;
        semanticSearchDownloadTotalMb = 0f;
        hideSemanticSearchProgressDialog();
        if(semanticSearchDisposing || !semanticSearchEnabled()) return;
        if(runtime){
            cleanupSemanticSearchRuntimeFile();
        }else{
            cleanupSemanticSearchModelFile();
        }
        semanticSearchStatusOverride = bundleFormat(
            runtime ? "spdb.semantic.status.runtime-download-failed" : "spdb.semantic.status.download-failed",
            runtime ? "Semantic-search runtime download failed: @" : "Semantic model download failed: @",
            semanticSearchErrorMessage(t)
        );
        bumpSemanticSearchUiRevision();
        if(Vars.ui != null) Vars.ui.showException(semanticSearchStatusOverride, t);
    }

    private void startSemanticSearchInitialization(boolean deleteModelOnFailure){
        final int lifecycleToken;
        synchronized(semanticSearchLock){
            if(!semanticSearchEnabled() || semanticSearchDisposing || semanticSearchInitializing || embeddingIndex != null || semanticSearchRuntimeFile == null || !semanticSearchRuntimeFile.exists() || semanticSearchRuntimeFile.length() <= 0L || semanticSearchModelFile == null || !semanticSearchModelFile.exists() || semanticSearchModelFile.length() <= 0L){
                return;
            }

            semanticSearchInitializing = true;
            semanticSearchResourcesStarted = true;
            lifecycleToken = semanticSearchLifecycleToken;
        }
        semanticSearchStatusOverride = bundle("spdb.semantic.status.init-start", "Initializing the semantic-search model...");
        bumpSemanticSearchUiRevision();

        Threads.daemon("spdb-semantic-init", () -> {
            EmbeddingEngine newEngine = null;
            EmbeddingIndex newIndex = null;
            try{
                if(semanticSearchDisposing || !semanticSearchEnabled()) return;
                int threads = Math.max(1, Runtime.getRuntime().availableProcessors() / 2);
                newEngine = EmbeddingEngine.load(semanticSearchModelFile.file(), semanticSearchRuntimeFile.file(), semanticSearchNativeDir == null ? null : semanticSearchNativeDir.file(), threads);
                newIndex = new EmbeddingIndex(newEngine, chatDb);
                synchronized(semanticSearchLock){
                    if(semanticSearchDisposing || lifecycleToken != semanticSearchLifecycleToken || !semanticSearchEnabled()) return;
                    pendingEmbeddingEngine = newEngine;
                    pendingEmbeddingIndex = newIndex;
                }
                EmbeddingEngine readyEngine = newEngine;
                EmbeddingIndex readyIndex = newIndex;
                newEngine = null;
                newIndex = null;
                Core.app.post(() -> completeSemanticSearchInitialization(readyEngine, readyIndex, lifecycleToken));
            }catch(Throwable t){
                EmbeddingEngine failedEngine = newEngine;
                EmbeddingIndex failedIndex = newIndex;
                newEngine = null;
                newIndex = null;
                boolean stale;
                synchronized(semanticSearchLock){
                    stale = lifecycleToken != semanticSearchLifecycleToken || !semanticSearchEnabled();
                }
                if(semanticSearchDisposing || stale){
                    clearStaleSemanticSearchInitialization(failedEngine, failedIndex, lifecycleToken);
                    closeSemanticSearchPair(failedIndex, failedEngine);
                    return;
                }
                Core.app.post(() -> failSemanticSearchInitialization(t, failedEngine, failedIndex, deleteModelOnFailure, lifecycleToken));
            }finally{
                if(newIndex != null || newEngine != null){
                    synchronized(semanticSearchLock){
                        if(pendingEmbeddingEngine == newEngine) pendingEmbeddingEngine = null;
                        if(pendingEmbeddingIndex == newIndex) pendingEmbeddingIndex = null;
                        if(lifecycleToken == semanticSearchLifecycleToken && (semanticSearchDisposing || !semanticSearchEnabled())) semanticSearchInitializing = false;
                    }
                    closeSemanticSearchPair(newIndex, newEngine);
                }
            }
        });
    }

    private void completeSemanticSearchInitialization(EmbeddingEngine newEngine, EmbeddingIndex newIndex, int lifecycleToken){
        EmbeddingEngine oldEngine;
        EmbeddingIndex oldIndex;
        boolean accepted;
        synchronized(semanticSearchLock){
            if(lifecycleToken == semanticSearchLifecycleToken) semanticSearchInitializing = false;
            if(pendingEmbeddingEngine == newEngine) pendingEmbeddingEngine = null;
            if(pendingEmbeddingIndex == newIndex) pendingEmbeddingIndex = null;
            if(semanticSearchDisposing || lifecycleToken != semanticSearchLifecycleToken || !semanticSearchEnabled()){
                oldEngine = null;
                oldIndex = null;
                accepted = false;
            }else{
                oldEngine = embeddingEngine;
                oldIndex = embeddingIndex;
                embeddingEngine = newEngine;
                embeddingIndex = newIndex;
                lastEmbeddingFlushAt = Time.millis();
                accepted = true;
            }
        }

        if(!accepted){
            closeSemanticSearchPair(newIndex, newEngine);
            return;
        }

        closeSemanticSearchPair(oldIndex, oldEngine);
        semanticSearchStatusOverride = bundle("spdb.semantic.status.index-wait", "The model is loaded; building the search index...");
        newIndex.initialize();
        bumpSemanticSearchUiRevision();
    }

    private void failSemanticSearchInitialization(Throwable t, EmbeddingEngine failedEngine, EmbeddingIndex failedIndex, boolean deleteModelOnFailure, int lifecycleToken){
        boolean stale;
        synchronized(semanticSearchLock){
            stale = lifecycleToken != semanticSearchLifecycleToken || !semanticSearchEnabled();
            if(lifecycleToken == semanticSearchLifecycleToken) semanticSearchInitializing = false;
            if(pendingEmbeddingEngine == failedEngine) pendingEmbeddingEngine = null;
            if(pendingEmbeddingIndex == failedIndex) pendingEmbeddingIndex = null;
        }
        closeSemanticSearchPair(failedIndex, failedEngine);
        if(semanticSearchDisposing || stale) return;
        if(deleteModelOnFailure){
            cleanupSemanticSearchModelFile();
        }
        semanticSearchStatusOverride = bundleFormat("spdb.semantic.status.init-failed", "Semantic-search initialization failed: @", semanticSearchErrorMessage(t));
        bumpSemanticSearchUiRevision();
        Log.err("SPDB: semantic search initialization failed.", t);
        if(Vars.ui != null) Vars.ui.showException(semanticSearchStatusOverride, t);
    }

    private void clearStaleSemanticSearchInitialization(EmbeddingEngine failedEngine, EmbeddingIndex failedIndex, int lifecycleToken){
        synchronized(semanticSearchLock){
            if(lifecycleToken == semanticSearchLifecycleToken) semanticSearchInitializing = false;
            if(pendingEmbeddingEngine == failedEngine) pendingEmbeddingEngine = null;
            if(pendingEmbeddingIndex == failedIndex) pendingEmbeddingIndex = null;
        }
    }

    private void cleanupSemanticSearchModelFile(){
        if(semanticSearchModelFile == null) return;
        try{
            semanticSearchModelFile.delete();
        }catch(Throwable ignored){
        }
    }

    private void cleanupSemanticSearchRuntimeFile(){
        if(semanticSearchRuntimeFile == null) return;
        try{
            semanticSearchRuntimeFile.delete();
        }catch(Throwable ignored){
        }
    }

    private void registerSemanticSearchShutdownHook(){
        try{
            Runtime.getRuntime().addShutdownHook(new Thread(() -> shutdownSemanticSearchResources(false), "spdb-semantic-shutdown"));
        }catch(IllegalStateException ignored){
            shutdownSemanticSearchResources(false);
        }catch(SecurityException e){
            Log.err("SPDB: failed registering semantic search shutdown hook.", e);
        }
    }

    private void disableSemanticSearchResources(){
        releaseSemanticSearchResources(true, false);
    }

    private void shutdownSemanticSearchResources(boolean updateUi){
        releaseSemanticSearchResources(updateUi, true);
    }

    private void releaseSemanticSearchResources(boolean updateUi, boolean terminal){
        EmbeddingEngine activeEngine;
        EmbeddingIndex activeIndex;
        EmbeddingEngine pendingEngine;
        EmbeddingIndex pendingIndex;
        boolean hadSemanticResources;
        int releaseToken;
        synchronized(semanticSearchLock){
            semanticSearchLifecycleToken++;
            releaseToken = semanticSearchLifecycleToken;
            hadSemanticResources = semanticSearchResourcesStarted
                || semanticSearchInitializing
                || embeddingEngine != null
                || embeddingIndex != null
                || pendingEmbeddingEngine != null
                || pendingEmbeddingIndex != null;
            semanticSearchDisposing = true;
            semanticSearchInitializing = false;
            semanticSearchDownloadInProgress = false;
            semanticSearchPromptOpen = false;
            semanticSearchDownloadProgress = -1f;
            semanticSearchDownloadTotalMb = 0f;

            activeEngine = embeddingEngine;
            activeIndex = embeddingIndex;
            pendingEngine = pendingEmbeddingEngine;
            pendingIndex = pendingEmbeddingIndex;

            embeddingEngine = null;
            embeddingIndex = null;
            pendingEmbeddingEngine = null;
            pendingEmbeddingIndex = null;
        }

        if(terminal && hadSemanticResources){
            startSemanticSearchExitFallback(releaseToken);
        }

        if(updateUi){
            hideSemanticSearchProgressDialog();
            bumpSemanticSearchUiRevision();
        }

        long deadlineNanos = System.nanoTime() + semanticSearchShutdownTimeoutMs * 1000000L;
        closeSemanticSearchPair(pendingIndex, pendingEngine, deadlineNanos);
        if(activeIndex != pendingIndex || activeEngine != pendingEngine){
            closeSemanticSearchPair(activeIndex, activeEngine, deadlineNanos);
        }

        boolean shouldRestart = false;
        synchronized(semanticSearchLock){
            if(releaseToken == semanticSearchLifecycleToken){
                if(!terminal){
                    semanticSearchDisposing = false;
                    if(!semanticSearchEnabled()){
                        semanticSearchStatusOverride = bundle("spdb.semantic.status.disabled", "Semantic search is disabled. Open its tab or run /spdb-search to enable it.");
                    }else{
                        semanticSearchStatusOverride = null;
                        shouldRestart = true;
                    }
                }
                semanticSearchExitFallbackStarted = false;
            }
        }
        if(updateUi) bumpSemanticSearchUiRevision();
        if(shouldRestart){
            startSemanticSearchIfLocalComponentsReady();
        }
    }

    private void closeSemanticSearchPair(EmbeddingIndex index, EmbeddingEngine engine){
        closeSemanticSearchPair(index, engine, System.nanoTime() + semanticSearchShutdownTimeoutMs * 1000000L);
    }

    private void closeSemanticSearchPair(EmbeddingIndex index, EmbeddingEngine engine, long deadlineNanos){
        if(index != null){
            try{
                index.close(remainingSemanticSearchShutdownMillis(deadlineNanos));
            }catch(Throwable ignored){
            }
        }
        if(engine != null){
            try{
                engine.close();
            }catch(Throwable ignored){
            }
        }
    }

    private long remainingSemanticSearchShutdownMillis(long deadlineNanos){
        long remaining = (deadlineNanos - System.nanoTime()) / 1000000L;
        return Math.max(1L, remaining);
    }

    private void startSemanticSearchExitFallback(int releaseToken){
        synchronized(semanticSearchLock){
            if(semanticSearchExitFallbackStarted) return;
            semanticSearchExitFallbackStarted = true;
        }
        Thread fallback = new Thread(() -> {
            try{
                Thread.sleep(semanticSearchShutdownTimeoutMs);
            }catch(InterruptedException ignored){
                Thread.currentThread().interrupt();
                return;
            }

            boolean shouldExit;
            synchronized(semanticSearchLock){
                shouldExit = semanticSearchExitFallbackStarted
                    && releaseToken == semanticSearchLifecycleToken
                    && semanticSearchDisposing
                    && semanticSearchResourcesStarted;
            }
            if(shouldExit){
                Log.warn("SPDB: semantic search resources did not close within @ ms; forcing JVM exit.", semanticSearchShutdownTimeoutMs);
                System.exit(0);
            }
        }, "spdb-semantic-exit-fallback");
        fallback.setDaemon(true);
        fallback.start();
    }

    private void showSemanticSearchProgressDialog(){
        if(semanticSearchDisposing || Vars.ui == null) return;
        if(semanticSearchProgressDialog != null){
            semanticSearchProgressDialog.show();
            return;
        }

        BaseDialog dialog = new BaseDialog(bundle("spdb.semantic.progress.title", "Download semantic-search components"));
        dialog.cont.margin(10f);
        dialog.cont.add(bundle("spdb.semantic.progress.desc", "Downloading semantic-search components, please wait."))
            .width(460f)
            .left()
            .wrap()
            .row();
        dialog.cont.add(new Bar(this::semanticSearchStatus, () -> Color.valueOf("5ec7ff"), () ->
            semanticSearchDownloadProgress < 0f ? 0f : semanticSearchDownloadProgress
        )).width(460f).height(70f).padTop(8f);
        dialog.setFillParent(false);
        semanticSearchProgressDialog = dialog;
        dialog.show();
    }

    private void hideSemanticSearchProgressDialog(){
        if(semanticSearchProgressDialog == null) return;
        semanticSearchProgressDialog.hide();
        semanticSearchProgressDialog = null;
    }

    private void rememberSemanticSearchQuery(String query){
        if(query == null || query.trim().isEmpty()) return;
        semanticSearchPendingQuery = query.trim();
    }

    private String consumePendingSemanticSearchQueryIfReady(){
        if(!semanticSearchReady()) return null;
        String pending = semanticSearchPendingQuery;
        if(pending == null || pending.trim().isEmpty()) return null;
        semanticSearchPendingQuery = null;
        return pending;
    }

    private void bumpSemanticSearchUiRevision(){
        semanticSearchUiRevision++;
    }

    private String semanticSearchErrorMessage(Throwable t){
        String message = Strings.getFinalMessage(t);
        if(message == null || message.trim().isEmpty()){
            return t == null ? "unknown" : t.getClass().getSimpleName();
        }
        return message.trim();
    }

    private static String bundle(String key, String fallback){
        return Core.bundle == null ? fallback : Core.bundle.get(key, fallback);
    }

    private static String bundleFormat(String key, String fallback, Object... args){
        String template = Core.bundle != null && Core.bundle.has(key) ? Core.bundle.get(key) : fallback;
        return Strings.format(template, args);
    }

    private boolean isAndroidRuntime(){
        String runtime = System.getProperty("java.runtime.name", "");
        return runtime != null && runtime.toLowerCase(Locale.ROOT).contains("android");
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
                        playersIntegrityIssues.add(bundle("spdb.integrity.players-modified", "Player database verification failed: players.json may have been modified."));
                    }else if(playersFileIntegrityState == integrityUnsupported){
                        playersIntegrityIssues.add(bundle("spdb.integrity.players-unsupported", "Player database verification failed: players.json uses an unsupported verification algorithm."));
                    }else if(playersFileIntegrityState == integrityMissing){
                        playersDirty = true;
                    }
                    playerDb.loadFrom(loaded);
                    if(playerDb.backfillUidFromPid() > 0) playersDirty = true;
                }
            }catch(Throwable t){
                Log.err("SPDB: failed to load players database.", t);
                playersIntegrityIssues.add(bundle("spdb.integrity.players-read-failed", "Player database could not be read; integrity verification could not be completed."));
            }
        }

        chatDb.loadStorage(chatsDbFile, chatsDir, chatsIndexFile, legacyChatsFile, json);
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
            Vars.ui.showInfoFade(bundle("spdb.toast.integrity", "SPDB: Database integrity issues detected. See the query window for details."));
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
                chatDb.flushToStorage(chatsDbFile, chatsDir, chatsIndexFile, legacyChatsFile, json);
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
        Core.settings.defaults(keySemanticSearchEnabled, false);

        if(!bekBundled) Vars.ui.settings.addCategory(bundle("spdb.category", "Server Player Database"), Icon.zoom, this::bekBuildSettings);
    }
    /** Populates a {@link mindustry.ui.dialogs.SettingsMenuDialog.SettingsTable} with this mod's settings. */
    public void bekBuildSettings(SettingsMenuDialog.SettingsTable table){
            table.pref(new SpdbSettingsWidgets.HeaderSetting(bundle("spdb.settings.header.collection", "Data collection"), Icon.zoom));
            table.pref(new SpdbSettingsWidgets.IconCheckSetting(keyCollect, true, Icon.add, null));
            table.pref(new SpdbSettingsWidgets.IconCheckSetting(keyRecordChat, false, Icon.chat, null));

            table.pref(new SpdbSettingsWidgets.HeaderSetting(bundle("spdb.settings.header.admin", "Administrator tools"), Icon.admin));
            table.pref(new SpdbSettingsWidgets.IconCheckSetting(keyAutoTrace, true, Icon.zoom, null));
            table.pref(new SpdbSettingsWidgets.IconCheckSetting(keyShowAutoTraceDialog, false, Icon.eyeSmall, null));

            table.pref(new SpdbSettingsWidgets.HeaderSetting(bundle("spdb.settings.header.semantic", "Semantic search"), Icon.zoom));
            table.pref(new SpdbSettingsWidgets.IconCheckSetting(keySemanticSearchEnabled, false, Icon.zoom, enabled -> {
                if(enabled){
                    startSemanticSearchIfLocalComponentsReady();
                }else{
                    disableSemanticSearchResources();
                }
            }));

            table.pref(new SpdbSettingsWidgets.HeaderSetting(bundle("spdb.settings.header.tools", "Tools"), Icon.wrench));
            table.pref(new SpdbSettingsWidgets.ActionButtonSetting(bundle("spdb.action.open-query", "Open query window"), Icon.list, this::showStandaloneQueryDialog));
            table.pref(new SpdbSettingsWidgets.ActionButtonSetting(bundle("spdb.action.open-debug", "Open debug window"), Icon.zoom, this::showStandaloneDebugDialog));
            table.pref(new SpdbSettingsWidgets.ActionButtonSetting(bundle("spdb.action.find-same-ip", "Find possible alternate accounts (same IP)"), Icon.players, this::showSameIpAltDialog));
            table.pref(new SpdbSettingsWidgets.ActionButtonSetting(bundle("spdb.action.save", "Save database now"), Icon.save, () -> saveDirty(true)));

            table.pref(new SpdbSettingsWidgets.HeaderSetting(bundle("spdb.settings.header.update", "Updates"), Icon.refresh));
            if(!bekBundled) table.pref(new SpdbSettingsWidgets.IconCheckSetting(GithubUpdateCheck.enabledKey(), true, Icon.refresh, null));
            if(!bekBundled) table.pref(new SpdbSettingsWidgets.IconCheckSetting(GithubUpdateCheck.showDialogKey(), true, Icon.infoSmall, null));
        
    }


    private void ensureOverlayAttached(){
        if(Vars.headless || Vars.ui == null || Vars.ui.hudGroup == null) return;
        if(!overlayUI.isSupported()) return;

        if(overlayQueryContent == null){
            overlayQueryContent = new OverlayQueryContent();
        }
        if(overlayQueryWindow == null){
            overlayQueryWindow = overlayUI.registerWindow(overlayQueryWindowName, overlayQueryContent.root, () -> Vars.state.isGame());
            if(overlayQueryWindow != null) overlayQueryWindow.configure(false, true);
            if(overlayQueryWindow != null && !hasStoredOverlayWindowState(overlayQueryWindowName)){
                overlayQueryWindow.setEnabledAndPinned(true, false);
            }
        }

        if(debugContent == null){
            debugContent = new DebugContent();
        }
        if(overlayDebugWindow == null){
            overlayDebugWindow = overlayUI.registerWindow(overlayDebugWindowName, debugContent.root, () -> Vars.state.isGame());
            if(overlayDebugWindow != null) overlayDebugWindow.configure(false, true);
            if(overlayDebugWindow != null && !hasStoredOverlayWindowState(overlayDebugWindowName)){
                overlayDebugWindow.setEnabledAndPinned(false, false);
            }
        }
    }

    private boolean hasStoredOverlayWindowState(String windowName){
        return Core.settings != null && Core.settings.has("overlayUI." + windowName);
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
            fallbackQueryDialog = new BaseDialog(bundle("spdb.window.query.title", "Player database / Chat history"));
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

    private void showSemanticSearchDialog(String query){
        showStandaloneQueryDialog();
        if(fallbackQueryContent != null){
            fallbackQueryContent.openSemanticSearch(query);
        }
    }

    private void showStandaloneDebugDialog(){
        if(Vars.ui == null) return;

        if(fallbackDebugDialog == null){
            fallbackDebugDialog = new BaseDialog(bundle("spdb.window.debug.title", "SPDB parser debug"));
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

        BaseDialog dialog = new BaseDialog(bundle("spdb.query.same-ip.title", "Possible alternate accounts (same IP)"));
        dialog.addCloseButton();
        dialog.cont.defaults().left().pad(4f);

        Table result = new Table();
        result.left().top();

        TextField targetUidField = new TextField("");
        targetUidField.setMessageText(bundle("spdb.query.same-ip.target", "Target UID to keep"));

        TextArea sourceUidsArea = new TextArea("");
        sourceUidsArea.setMessageText(bundle("spdb.query.same-ip.sources", "Source UIDs (multiple)"));

        Table mergeOutput = new Table();
        mergeOutput.left().top();
        setSameIpMergeOutput(mergeOutput, bundle("spdb.query.same-ip.example", "Example: target dNF, sources abc def ghi\nAccepted: abc def ghi"));

        dialog.cont.table(Styles.black3, top -> {
            top.left().defaults().left().pad(6f);
            top.add(bundle("spdb.query.same-ip.description", "Find same-IP account groups in the database, ordered by account count."))
                .left()
                .growX()
                .wrap();
            top.button(bundle("spdb.action.refresh", "Refresh"), Icon.refresh, Styles.defaultt, () -> refreshSameIpAltResult(result))
                .height(40f)
                .padLeft(8f);
        }).growX().row();

        float bodyWidth = fitDialogWidth(1240f);
        float sideWidth = Math.max(260f, Math.min(380f, bodyWidth * 0.32f));

        dialog.cont.table(body -> {
            body.top().left().defaults().pad(4f);

            body.pane(result)
                .scrollX(false)
                .grow()
                .minWidth(0f)
                .minHeight(0f);

            body.table(Styles.black3, side -> {
                side.left().top().defaults().left().pad(4f).growX();
                side.add(bundle("spdb.query.same-ip.merge", "Merge all")).left().wrap().row();
                side.add(bundle("spdb.query.same-ip.target", "Target UID to keep")).left().row();
                side.add(targetUidField).height(38f).growX().row();
                side.add(bundle("spdb.query.same-ip.sources", "Source UIDs (multiple)")).left().row();
                side.pane(sourceUidsArea)
                    .scrollX(false)
                    .height(220f)
                    .growX()
                    .row();
                side.table(btns -> {
                    btns.left().defaults().left().padRight(6f).growX();
                    btns.button(bundle("spdb.query.same-ip.merge", "Merge all"), Icon.save, Styles.defaultt, () ->
                        mergeUidBatchFromSameIpDialog(targetUidField.getText(), sourceUidsArea.getText(), result, mergeOutput)
                    ).height(38f).growX();
                    btns.button(bundle("spdb.action.clear", "Clear"), Styles.defaultt, () -> {
                        targetUidField.setText("");
                        sourceUidsArea.setText("");
                        setSameIpMergeOutput(mergeOutput, "");
                    }).height(38f).growX();
                }).growX().row();
                side.pane(mergeOutput)
                    .scrollX(false)
                    .height(128f)
                    .growX()
                    .row();
            }).width(sideWidth).growY().top().minWidth(0f).minHeight(0f);
        })
            .grow()
            .width(bodyWidth)
            .height(fitDialogHeight(710f, 320f))
            .minWidth(0f)
            .minHeight(0f)
            .row();

        refreshSameIpAltResult(result);
        dialog.show();
    }

    private void mergeUidBatchFromSameIpDialog(String targetUidText, String sourceUidsText, Table result, Table output){
        String targetUid = normalizeShortUid(targetUidText);
        if(targetUid == null){
            setSameIpMergeOutput(output, bundle("spdb.query.same-ip.invalid-target", "Enter a three-character target UID (for example dNF)."));
            Vars.ui.showInfoFade(bundle("spdb.query.same-ip.invalid-target", "Enter a three-character target UID (for example dNF)."));
            return;
        }

        String[] tokens = splitUidBatchTokens(sourceUidsText);
        if(tokens.length == 0){
            setSameIpMergeOutput(output, bundle("spdb.query.same-ip.invalid-source", "Enter at least one source UID.\nSpaces, newlines and commas are accepted."));
            Vars.ui.showInfoFade(bundle("spdb.query.same-ip.invalid-source", "Enter at least one source UID."));
            return;
        }

        Seq<String> fromUids = new Seq<>();
        int invalid = 0;
        for(String token : tokens){
            String uid = normalizeShortUid(token);
            if(uid == null){
                invalid++;
                continue;
            }
            if(!fromUids.contains(uid, false)) fromUids.add(uid);
        }

        fromUids.remove(targetUid, false);
        if(fromUids.isEmpty()){
            setSameIpMergeOutput(output, bundle("spdb.query.same-ip.no-source", "No mergeable source UIDs.\nA source UID cannot equal the target UID."));
            Vars.ui.showInfoFade(bundle("spdb.query.same-ip.no-source", "No mergeable source UIDs."));
            return;
        }

        int rebound = 0;
        int mergedPid = 0;
        int movedChatUid = 0;

        for(String fromUid : fromUids){
            rebound += playerDb.rebindUid(fromUid, targetUid);
            mergedPid += playerDb.mergeByUid(targetUid);
            if(chatDb.moveUid(fromUid, targetUid)) movedChatUid++;
        }

        if(rebound > 0 || mergedPid > 0) playersDirty = true;
        if(movedChatUid > 0) chatsDirty = true;

        refreshSameIpAltResult(result);

        boolean changed = rebound > 0 || mergedPid > 0 || movedChatUid > 0;
        if(!changed){
            setSameIpMergeOutput(output, bundleFormat("spdb.query.same-ip.no-data", "No mergeable data found.\nConfirm that the UIDs exist in the local database.@", invalid > 0 ? "\nIgnored invalid UIDs: " + invalid : ""));
            Vars.ui.showInfoFade(bundle("spdb.query.same-ip.no-data", "No mergeable data found."));
            return;
        }

        String summary = bundleFormat("spdb.query.same-ip.summary-target",
            "Target UID: @\nSource UID count: @\nRebound records: @\nMerged PIDs: @\nMoved chat UIDs: @",
            targetUid,
            fromUids.size + (invalid > 0 ? " (ignored invalid " + invalid + ")" : ""),
            rebound,
            mergedPid,
            movedChatUid);
        setSameIpMergeOutput(output, summary);
        Vars.ui.showInfoFade(bundle("spdb.query.same-ip.done", "SPDB: batch UID merge completed."));
    }

    private static void setSameIpMergeOutput(Table output, String message){
        if(output == null) return;
        output.clear();
        output.left().top().defaults().left().pad(2f).growX();
        if(message == null || message.trim().isEmpty()) return;

        String[] lines = message.split("\\n");
        for(String line : lines){
            output.add(escapeMarkup(line)).left().wrap().row();
        }
    }

    private static String[] splitUidBatchTokens(String text){
        if(text == null) return new String[0];
        String trimmed = text.trim();
        if(trimmed.isEmpty()) return new String[0];
        return trimmed.split("[,，;；\\s]+");
    }

    private void refreshSameIpAltResult(Table result){
        if(result == null) return;

        result.clear();
        result.left().top().defaults().left().pad(4f).growX();

        Seq<SameIpGroup> groups = playerDb.findSameIpGroups(2);
        if(groups.isEmpty()){
            result.add(bundle("spdb.query.same-ip.empty", "No same-IP account groups found.")).left().wrap().row();
            return;
        }

        int accountCount = 0;
        for(SameIpGroup group : groups){
            accountCount += group.players.size;
        }
        result.add(bundleFormat("spdb.query.same-ip.summary", "Found @ possible alternate-account groups involving @ accounts.", groups.size, accountCount))
            .left()
            .wrap()
            .row();

        for(SameIpGroup group : groups){
            String geo = lookupIpGeoCached(group.ip);
            String ipTitle = "IP: " + group.ip + (geo == null || geo.isEmpty() ? "" : " (" + geo + ")");

            result.table(Styles.black3, card -> {
                card.left().top().defaults().left().pad(3f).growX();
                card.add(escapeMarkup(ipTitle)).left().wrap().row();
                card.add(bundleFormat("spdb.query.same-ip.accounts", "Accounts: @ | Latest seen: @", group.players.size, formatTime(group.latestSeen))).left().wrap().row();

                for(int i = 0; i < group.players.size; i++){
                    PlayerRecord rec = group.players.get(i);
                    String line = (i + 1) + ". " + bestPlayerName(rec)
                        + " | UID: " + (rec.uid == null ? bundle("spdb.query.field.none", "(none)") : rec.uid)
                        + " | PID: " + rec.pid
                        + " | " + bundle("spdb.query.field.last-seen", "Last seen") + ": " + formatTime(rec.lastSeen);
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
        showFileChooser(true, "json", file -> {
            try{
                PlayerDbFile incoming = json.fromJson(PlayerDbFile.class, file.readString("UTF-8"));
                if(incoming == null || incoming.players == null){
                    Vars.ui.showErrorMessage(bundle("spdb.toast.players.invalid", "SPDB: Invalid player database file."));
                    return;
                }

                int state = verifyPlayerDbFileIntegrity(incoming);
                if(state == integrityMismatch){
                    Vars.ui.showErrorMessage(bundle("spdb.toast.players.modified", "SPDB: Player database verification failed; the file may have been modified.\nImport the original export again."));
                    return;
                }
                if(state == integrityUnsupported){
                    Vars.ui.showErrorMessage(bundle("spdb.toast.players.unsupported", "SPDB: Unsupported player database verification algorithm; import rejected."));
                    return;
                }
                if(state == integrityMissing){
                    Vars.ui.showInfoFade(bundle("spdb.toast.players.legacy", "SPDB: Player database has no integrity metadata; importing in compatibility mode."));
                }

                int merged = playerDb.mergeFrom(incoming);
                if(merged > 0){
                    playersDirty = true;
                    Vars.ui.showInfoFade(bundleFormat("spdb.toast.players.imported", "SPDB: Imported or merged player records: @", merged));
                }else{
                    Vars.ui.showInfoFade(bundle("spdb.toast.players.none", "SPDB: No new player data found."));
                }
            }catch(Throwable t){
                Log.err("SPDB: failed to import players.", t);
                Vars.ui.showErrorMessage(bundle("spdb.toast.players.import-failed", "SPDB: Player database import failed."));
            }
        });
    }

    private static OverlayUiBridge vanillaOverlayUi(){
        LegacyMindustryXGuard.rejectLegacyMindustryX("ServerPlayerDataBase");
        return OverlayUiBridge.autoDetect();
    }

    /** Supports both the legacy Platform chooser and the v159 FileChooser builder. */
    private static void showFileChooser(boolean open, String extension, Cons<Fi> consumer){
        try{
            Class<?> chooser = Class.forName("mindustry.ui.FileChooser");
            Method factory = chooser.getMethod(open ? "open" : "save", String[].class);
            Object params = factory.invoke(null, (Object)new String[]{extension});
            Method submit = params.getClass().getMethod("submit", Cons.class);
            submit.invoke(params, consumer);
            return;
        }catch(ClassNotFoundException ignored){
            // Mindustry v154 and earlier expose the legacy Platform method instead.
        }catch(Throwable t){
            Log.warn("SPDB: v159 file chooser bridge failed: @", t.getMessage());
        }

        try{
            Method legacy = Vars.platform.getClass().getMethod("showFileChooser", boolean.class, String.class, Cons.class);
            legacy.invoke(Vars.platform, open, extension, consumer);
        }catch(Throwable t){
            Log.err("SPDB: file chooser is unavailable.", t);
        }
    }

    private void exportPlayersToFile(){
        showFileChooser(false, "json", file -> {
            try{
                PlayerDbFile out = playerDb.snapshot();
                signPlayerDbFile(out);
                file.writeString(json.prettyPrint(out), false, "UTF-8");
                Vars.ui.showInfoFade(bundle("spdb.toast.players.exported", "SPDB: Player database export completed."));
            }catch(Throwable t){
                Log.err("SPDB: failed to export players.", t);
                Vars.ui.showErrorMessage(bundle("spdb.toast.players.export-failed", "SPDB: Player database export failed."));
            }
        });
    }

    private void importChatsFromFile(){
        showFileChooser(true, "json", file -> {
            try{
                ChatDbFile incoming = json.fromJson(ChatDbFile.class, file.readString("UTF-8"));
                if(incoming == null || incoming.entries == null){
                    Vars.ui.showErrorMessage(bundle("spdb.toast.chats.invalid", "SPDB: Invalid chat database file."));
                    return;
                }

                int state = verifyChatDbFileIntegrity(incoming);
                if(state == integrityMismatch){
                    Vars.ui.showErrorMessage(bundle("spdb.toast.chats.modified", "SPDB: Chat database verification failed; the file may have been modified.\nImport the original export again."));
                    return;
                }
                if(state == integrityUnsupported){
                    Vars.ui.showErrorMessage(bundle("spdb.toast.chats.unsupported", "SPDB: Unsupported chat database verification algorithm; import rejected."));
                    return;
                }
                if(state == integrityMissing){
                    Vars.ui.showInfoFade(bundle("spdb.toast.chats.legacy", "SPDB: Chat database has no integrity metadata; importing in compatibility mode."));
                }

                int merged = chatDb.mergeFrom(incoming);
                if(merged > 0){
                    chatsDirty = true;
                    Vars.ui.showInfoFade(bundleFormat("spdb.toast.chats.imported", "SPDB: Imported or merged chat records: @", merged));
                }else{
                    Vars.ui.showInfoFade(bundle("spdb.toast.chats.none", "SPDB: No new chat data found."));
                }
            }catch(Throwable t){
                Log.err("SPDB: failed to import chats.", t);
                Vars.ui.showErrorMessage(bundle("spdb.toast.chats.import-failed", "SPDB: Chat database import failed."));
            }
        });
    }

    private void exportChatsToFile(){
        showFileChooser(false, "json", file -> {
            try{
                ChatDbFile out = chatDb.snapshot();
                signChatDbFile(out);
                file.writeString(json.prettyPrint(out), false, "UTF-8");
                Vars.ui.showInfoFade(bundle("spdb.toast.chats.exported", "SPDB: Chat database export completed."));
            }catch(Throwable t){
                Log.err("SPDB: failed to export chats.", t);
                Vars.ui.showErrorMessage(bundle("spdb.toast.chats.export-failed", "SPDB: Chat database export failed."));
            }
        });
    }

    private boolean hasIntegrityIssues(){
        return !playersIntegrityIssues.isEmpty() || chatDb.hasIntegrityIssues();
    }

    private void showIntegrityDialog(){
        if(Vars.ui == null) return;

        BaseDialog dialog = new BaseDialog(bundle("spdb.integrity.title", "SPDB integrity"));
        dialog.addCloseButton();
        float width = fitDialogWidth(960f);
        float height = fitDialogHeight(660f, 320f);
        dialog.cont.pane(p -> {
            p.left().top().defaults().left().pad(4f).growX();

            p.add(bundleFormat("spdb.integrity.players-file", "Player database file: @", integrityStateText(playersFileIntegrityState))).left().row();
            p.add(bundleFormat("spdb.integrity.chat-backend", "Chat storage backend: @", chatDb.storageBackendName())).left().row();
            if(chatDb.usesSqlite()){
                p.add(bundleFormat("spdb.integrity.chat-database", "Chat database file: @ | Total records: @", integrityStateText(chatDb.indexIntegrityState()), chatDb.totalEntries())).left().wrap().row();
            }else{
                p.add(bundleFormat("spdb.integrity.chat-index", "Chat index file: @", integrityStateText(chatDb.indexIntegrityState()))).left().row();
                p.add(bundleFormat("spdb.integrity.chat-shards", "Chat shards checked: @ (valid @ / missing metadata @ / mismatched @ / unsupported algorithm @)", chatDb.shardsChecked(), chatDb.shardsValid(), chatDb.shardsMissing(), chatDb.shardsMismatch(), chatDb.shardsUnsupported())).left().wrap().row();
            }

            if(!playersIntegrityIssues.isEmpty() || chatDb.hasIntegrityIssues()){
                p.add(bundle("spdb.integrity.details", "Details:")).padTop(6f).left().row();
                for(String issue : playersIntegrityIssues){
                    p.add("- " + escapeMarkup(issue)).left().wrap().row();
                }
                for(String issue : chatDb.integrityIssues()){
                    p.add("- " + escapeMarkup(issue)).left().wrap().row();
                }
            }else{
                p.add(bundle("spdb.integrity.clean", "No integrity issues found.\nModified import files will be rejected during import."), Styles.outlineLabel).left().padTop(6f).wrap().row();
            }
        }).grow().width(width).height(height).minWidth(0f).minHeight(0f);
        dialog.show();
    }

    private static String integrityStateText(int state){
        switch(state){
            case integrityValid:
                return bundle("spdb.integrity.valid", "Valid");
            case integrityMissing:
                return bundle("spdb.integrity.missing", "Missing metadata");
            case integrityUnsupported:
                return bundle("spdb.integrity.unsupported", "Unsupported algorithm");
            default:
                return bundle("spdb.integrity.failed", "Verification failed");
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
        final Table semanticTab = new Table();
        final Table uidResult = new Table();
        final Table pidResult = new Table();
        final Table nameResult = new Table();
        final Table ipResult = new Table();
        final Table chatResult = new Table();
        final Table allPlayersResult = new Table();
        final Table allChatsResult = new Table();
        SemanticSearchContent semanticContent;

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
                    top.button(bundle("spdb.action.import-players", "Import player database"), Icon.download, Styles.defaultt, ServerPlayerDataBaseMod.this::importPlayersFromFile).growX().row();
                    top.button(bundle("spdb.action.export-players", "Export player database"), Icon.upload, Styles.defaultt, ServerPlayerDataBaseMod.this::exportPlayersToFile).growX().row();
                    top.button(bundle("spdb.action.import-chats", "Import chat database"), Icon.download, Styles.defaultt, ServerPlayerDataBaseMod.this::importChatsFromFile).growX().row();
                    top.button(bundle("spdb.action.export-chats", "Export chat database"), Icon.upload, Styles.defaultt, ServerPlayerDataBaseMod.this::exportChatsToFile).growX().row();
                    top.button(bundle("spdb.action.integrity", "Integrity status"), Icon.list, Styles.defaultt, ServerPlayerDataBaseMod.this::showIntegrityDialog).growX();
                }else if(medium){
                    top.table(row -> {
                        row.left().defaults().height(42f).padRight(6f).growX();
                        row.button(bundle("spdb.action.import-players", "Import player database"), Icon.download, Styles.defaultt, ServerPlayerDataBaseMod.this::importPlayersFromFile).growX();
                        row.button(bundle("spdb.action.export-players", "Export player database"), Icon.upload, Styles.defaultt, ServerPlayerDataBaseMod.this::exportPlayersToFile).growX();
                        row.button(bundle("spdb.action.integrity", "Integrity status"), Icon.list, Styles.defaultt, ServerPlayerDataBaseMod.this::showIntegrityDialog).growX();
                    }).growX().row();
                    top.table(row -> {
                        row.left().defaults().height(42f).padRight(6f).growX();
                        row.button(bundle("spdb.action.import-chats", "Import chat database"), Icon.download, Styles.defaultt, ServerPlayerDataBaseMod.this::importChatsFromFile).growX();
                        row.button(bundle("spdb.action.export-chats", "Export chat database"), Icon.upload, Styles.defaultt, ServerPlayerDataBaseMod.this::exportChatsToFile).growX();
                    }).growX();
                }else{
                    top.button(bundle("spdb.action.import-players", "Import player database"), Icon.download, Styles.defaultt, ServerPlayerDataBaseMod.this::importPlayersFromFile);
                    top.button(bundle("spdb.action.export-players", "Export player database"), Icon.upload, Styles.defaultt, ServerPlayerDataBaseMod.this::exportPlayersToFile);
                    top.button(bundle("spdb.action.import-chats", "Import chat database"), Icon.download, Styles.defaultt, ServerPlayerDataBaseMod.this::importChatsFromFile);
                    top.button(bundle("spdb.action.export-chats", "Export chat database"), Icon.upload, Styles.defaultt, ServerPlayerDataBaseMod.this::exportChatsToFile);
                    top.button(bundle("spdb.action.integrity", "Integrity status"), Icon.list, Styles.defaultt, ServerPlayerDataBaseMod.this::showIntegrityDialog);
                }
            }).growX().padBottom(6f).row();

            root.table(Styles.black3, tabs -> {
                tabs.left().defaults().height(40f).pad(6f).growX();
                tabs.button(bundle("spdb.tab.players", "Players"), Icon.players, Styles.togglet, () -> activeTab = 0).checked(b -> activeTab == 0).growX();
                tabs.button(bundle("spdb.tab.chats", "Chat history"), Icon.chat, Styles.togglet, () -> activeTab = 1).checked(b -> activeTab == 1).growX();
                tabs.button(bundle("spdb.tab.semantic", "Semantic search"), Icon.zoom, Styles.togglet, () -> openSemanticSearch(null)).checked(b -> activeTab == 2).growX();
            }).growX().padBottom(8f).row();

            playerTab.clear();
            playerTab.left().top().defaults().left().pad(4f).growX();
            allPlayersPage = 0;

            playerTab.table(Styles.black3, box -> {
                box.left().top().defaults().left().pad(4f).growX();
                box.add(bundle("spdb.query.uid.title", "UID query (3 characters)")).left().row();
                if(compact){
                    box.table(line -> {
                        line.left().defaults().left().padRight(6f);
                        uidField = line.field("", text -> {}).growX().get();
                        uidField.setMessageText(bundle("spdb.query.uid.placeholder", "Enter a three-character UID, for example dNF"));
                    }).growX().row();
                    box.table(line -> {
                        line.left().defaults().left().padRight(6f).growX();
                        line.button(bundle("spdb.query.uid.search", "Query UID"), this::refreshUidResult).height(38f).growX();
                        line.button(bundle("spdb.query.uid.merge", "Merge by UID"), this::showMergeByUidPreview).height(38f).growX();
                    }).growX().row();
                }else{
                    box.table(line -> {
                        line.left().defaults().left().padRight(6f);
                        uidField = line.field("", text -> {}).growX().get();
                        uidField.setMessageText(bundle("spdb.query.uid.placeholder", "Enter a three-character UID, for example dNF"));
                        line.button(bundle("spdb.query.uid.search", "Query UID"), this::refreshUidResult).height(38f);
                        line.button(bundle("spdb.query.uid.merge", "Merge by UID"), this::showMergeByUidPreview).height(38f);
                    }).growX().row();
                }
                box.pane(uidResult).scrollX(false).maxHeight(fitPaneHeight(260f, 150f)).growX().row();
            }).growX().row();

            playerTab.table(Styles.black3, box -> {
                box.left().top().defaults().left().pad(4f).growX();
                box.add(bundle("spdb.query.uid.tools", "UID tools")).left().row();

                box.table(line -> {
                    line.left().defaults().left().padRight(6f).growX();
                    line.button(bundle("spdb.query.uid.merge-all", "Merge all duplicate UIDs"), this::mergeAllSameUid).height(36f).growX();
                }).growX().row();

                if(compact){
                    box.table(line -> {
                        line.left().defaults().left().padRight(6f);
                        fillPidField = line.field("", text -> {}).growX().get();
                        fillPidField.setMessageText(bundle("spdb.query.uid.fill-pid", "Enter a PID (only fills records without a UID)"));
                    }).growX().row();
                    box.table(line -> {
                        line.left().defaults().left().padRight(6f).growX();
                        fillUidField = line.field("", text -> {}).growX().get();
                        fillUidField.setMessageText(bundle("spdb.query.header.uid", "UID"));
                        line.button(bundle("spdb.query.uid.fill", "Fill UID"), this::fillMissingUidByPid).height(36f).growX();
                    }).growX().row();

                    box.table(line -> {
                        line.left().defaults().left().padRight(6f).growX();
                        mergeFromUidField = line.field("", text -> {}).growX().get();
                        mergeFromUidField.setMessageText(bundle("spdb.query.uid.source", "Source UID"));
                        mergeToUidField = line.field("", text -> {}).growX().get();
                        mergeToUidField.setMessageText(bundle("spdb.query.uid.target", "Target UID"));
                    }).growX().row();
                    box.table(line -> {
                        line.left().defaults().left().padRight(6f).growX();
                        line.button(bundle("spdb.query.uid.merge-two", "Merge two UIDs"), this::mergeTwoUids).height(36f).growX();
                    }).growX().row();
                }else{
                    box.table(line -> {
                        line.left().defaults().left().padRight(6f);
                        fillPidField = line.field("", text -> {}).growX().get();
                        fillPidField.setMessageText(bundle("spdb.query.uid.fill-pid", "Enter a PID (only fills records without a UID)"));
                        fillUidField = line.field("", text -> {}).width(130f).get();
                        fillUidField.setMessageText(bundle("spdb.query.header.uid", "UID"));
                        line.button(bundle("spdb.query.uid.fill", "Fill UID"), this::fillMissingUidByPid).height(36f);
                    }).growX().row();

                    box.table(line -> {
                        line.left().defaults().left().padRight(6f);
                        mergeFromUidField = line.field("", text -> {}).width(130f).get();
                        mergeFromUidField.setMessageText(bundle("spdb.query.uid.source", "Source UID"));
                        mergeToUidField = line.field("", text -> {}).width(130f).get();
                        mergeToUidField.setMessageText(bundle("spdb.query.uid.target", "Target UID"));
                        line.button(bundle("spdb.query.uid.merge-two", "Merge two UIDs"), this::mergeTwoUids).height(36f);
                    }).growX().row();
                }
            }).growX().row();

            playerTab.table(Styles.black3, box -> {
                box.left().top().defaults().left().pad(4f).growX();
                box.add(bundle("spdb.query.pid.title", "PID query")).padTop(4f).row();
                if(compact){
                    box.table(line -> {
                        line.left().defaults().left().padRight(6f);
                        pidField = line.field("", text -> {}).growX().get();
                        pidField.setMessageText(bundle("spdb.query.pid.placeholder", "Enter a PID from Trace or the database"));
                    }).growX().row();
                    box.table(line -> {
                        line.left().defaults().left().padRight(6f).growX();
                        line.button(bundle("spdb.query.pid.search", "Query PID"), this::refreshPidResult).height(38f).growX();
                    }).growX().row();
                }else{
                    box.table(line -> {
                        line.left().defaults().left().padRight(6f);
                        pidField = line.field("", text -> {}).growX().get();
                        pidField.setMessageText(bundle("spdb.query.pid.placeholder", "Enter a PID from Trace or the database"));
                        line.button(bundle("spdb.query.pid.search", "Query PID"), this::refreshPidResult).height(38f);
                    }).growX().row();
                }
                box.add(pidResult).growX().row();
            }).growX().row();

            playerTab.table(Styles.black3, box -> {
                box.left().top().defaults().left().pad(4f).growX();
                box.add(bundle("spdb.query.name.title", "Name query")).padTop(4f).row();
                if(compact){
                    box.table(line -> {
                        line.left().defaults().left().padRight(6f);
                        nameField = line.field("", text -> {}).growX().get();
                        nameField.setMessageText(bundle("spdb.query.name.placeholder", "Enter a player name or fragment (fuzzy)"));
                    }).growX().row();
                    box.table(line -> {
                        line.left().defaults().left().padRight(6f).growX();
                        line.button(bundle("spdb.query.name.search", "Query name"), this::refreshNameResult).height(38f).growX();
                    }).growX().row();
                }else{
                    box.table(line -> {
                        line.left().defaults().left().padRight(6f);
                        nameField = line.field("", text -> {}).growX().get();
                        nameField.setMessageText(bundle("spdb.query.name.placeholder", "Enter a player name or fragment (fuzzy)"));
                        line.button(bundle("spdb.query.name.search", "Query name"), this::refreshNameResult).height(38f);
                    }).growX().row();
                }
                box.pane(nameResult).scrollX(false).maxHeight(fitPaneHeight(240f, 140f)).growX().row();
            }).growX().row();

            playerTab.table(Styles.black3, box -> {
                box.left().top().defaults().left().pad(4f).growX();
                box.add(bundle("spdb.query.ip.title", "IP query")).padTop(4f).row();
                if(compact){
                    box.table(line -> {
                        line.left().defaults().left().padRight(6f);
                        ipField = line.field("", text -> {}).growX().get();
                        ipField.setMessageText(bundle("spdb.query.ip.placeholder", "Enter an IP address"));
                    }).growX().row();
                    box.table(line -> {
                        line.left().defaults().left().padRight(6f).growX();
                        line.button(bundle("spdb.query.ip.search", "Query IP"), this::refreshIpResult).height(38f).growX();
                    }).growX().row();
                }else{
                    box.table(line -> {
                        line.left().defaults().left().padRight(6f);
                        ipField = line.field("", text -> {}).growX().get();
                        ipField.setMessageText(bundle("spdb.query.ip.placeholder", "Enter an IP address"));
                        line.button(bundle("spdb.query.ip.search", "Query IP"), this::refreshIpResult).height(38f);
                    }).growX().row();
                }
                box.pane(ipResult).scrollX(false).maxHeight(fitPaneHeight(240f, 140f)).growX().row();
            }).growX().row();

            playerTab.add(bundle("spdb.query.players.title", "All players (latest seen first)")).padTop(8f).row();
            playerTab.table(line -> {
                line.left().defaults().left().padRight(6f).growX();
                line.button(bundle("spdb.action.previous", "Previous"), () -> {
                    if(allPlayersPage > 0) allPlayersPage--;
                    refreshAllPlayersResult();
                }).height(36f).growX();
                line.button(bundle("spdb.action.next", "Next"), () -> {
                    allPlayersPage++;
                    refreshAllPlayersResult();
                }).height(36f).growX();
                if(compact){
                    line.row();
                    line.button(bundle("spdb.action.refresh", "Refresh"), this::refreshAllPlayersResult).height(36f).growX().colspan(2);
                }else{
                    line.button(bundle("spdb.action.refresh", "Refresh"), this::refreshAllPlayersResult).height(36f);
                }
            }).growX().row();
            playerTab.add(allPlayersResult).growX().growY().row();

            chatTab.clear();
            chatTab.left().top().defaults().left().pad(4f).growX();
            allChatsPage = 0;
            chatTab.add(bundle("spdb.query.chats.title", "Chat query (by UID)")).row();
            if(compact){
                chatTab.table(line -> {
                    line.left().defaults().left().padRight(6f);
                    chatUidField = line.field("", text -> {}).growX().get();
                    chatUidField.setMessageText(bundle("spdb.query.chats.placeholder", "Enter a UID"));
                }).growX().row();
                chatTab.table(line -> {
                    line.left().defaults().left().padRight(6f).growX();
                    line.button(bundle("spdb.query.chats.search", "Query chat"), this::refreshChatResult).height(38f).growX();
                }).growX().row();
            }else{
                chatTab.table(line -> {
                    line.left().defaults().left().padRight(6f);
                    chatUidField = line.field("", text -> {}).growX().get();
                    chatUidField.setMessageText(bundle("spdb.query.chats.placeholder", "Enter a UID"));
                    line.button(bundle("spdb.query.chats.search", "Query chat"), this::refreshChatResult).height(38f);
                }).growX().row();
            }
            chatTab.pane(chatResult).scrollX(false).maxHeight(fitPaneHeight(260f, 140f)).growX().row();

            chatTab.add(bundle("spdb.query.all-chats.title", "All chat history (newest first)")).padTop(8f).row();
            chatTab.table(line -> {
                line.left().defaults().left().padRight(6f).growX();
                line.button(bundle("spdb.action.previous", "Previous"), () -> {
                    if(allChatsPage > 0) allChatsPage--;
                    refreshAllChatsResult();
                }).height(36f).growX();
                line.button(bundle("spdb.action.next", "Next"), () -> {
                    allChatsPage++;
                    refreshAllChatsResult();
                }).height(36f).growX();
                if(compact){
                    line.row();
                    line.button(bundle("spdb.action.refresh", "Refresh"), this::refreshAllChatsResult).height(36f).growX().colspan(2);
                }else{
                    line.button(bundle("spdb.action.refresh", "Refresh"), this::refreshAllChatsResult).height(36f);
                }
            }).growX().row();
            chatTab.add(allChatsResult).growX().growY().row();

            semanticTab.clear();
            semanticTab.left().top().defaults().left().pad(4f).growX();
            semanticContent = new SemanticSearchContent(new SemanticSearchContent.Host(){
                @Override
                public boolean compactUi(){
                    return ServerPlayerDataBaseMod.this.compactUi();
                }

                @Override
                public String formatTime(long millis){
                    return ServerPlayerDataBaseMod.formatTime(millis);
                }

                @Override
                public String escapeMarkup(String text){
                    return ServerPlayerDataBaseMod.escapeMarkup(text);
                }

                @Override
                public String safeLine(String text, int maxLen){
                    return ServerPlayerDataBaseMod.safeLine(text, maxLen);
                }

                @Override
                public void copy(String value){
                    ServerPlayerDataBaseMod.this.copy(value);
                }

                @Override
                public void openUid(String uid){
                    if(uidField != null) uidField.setText(uid);
                    if(chatUidField != null) chatUidField.setText(uid);
                    activeTab = 0;
                    refreshUidResult();
                }

                @Override
                public void showInfo(String message){
                    if(Vars.ui != null) Vars.ui.showInfoFade(message);
                }

                @Override
                public void ensureSemanticSearchReady(String query){
                    ServerPlayerDataBaseMod.this.ensureSemanticSearchReady(query);
                }

                @Override
                public boolean canRunSemanticSearch(){
                    return semanticSearchReady();
                }

                @Override
                public String semanticSearchStatus(){
                    return ServerPlayerDataBaseMod.this.semanticSearchStatus();
                }

                @Override
                public Seq<EmbeddingIndex.SearchResult> searchSemantic(String query, int limit){
                    return ServerPlayerDataBaseMod.this.searchSemantic(query, limit);
                }
            });
            semanticTab.add(semanticContent.root).grow().minWidth(0f).minHeight(0f).row();

            root.stack(
                new Table(t -> t.pane(playerTab).scrollX(false).grow()),
                new Table(t -> t.pane(chatTab).scrollX(false).grow()),
                new Table(t -> t.pane(semanticTab).scrollX(false).grow())
            ).update(stack -> {
                if(stack.getChildren().size >= 3){
                    stack.getChildren().get(0).visible = activeTab == 0;
                    stack.getChildren().get(1).visible = activeTab == 1;
                    stack.getChildren().get(2).visible = activeTab == 2;
                }
                if(semanticContent == null) return;
                semanticContent.tick();
                String pending = consumePendingSemanticSearchQueryIfReady();
                if(pending != null){
                    semanticContent.setQueryText(pending);
                    semanticContent.runSearch();
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
            if(semanticContent != null) semanticContent.refreshStatus();
        }

        private void refreshUidResult(){
            uidResult.clear();
            uidResult.left().top();

            String uid = normalizeShortUid(uidField == null ? null : uidField.getText());
            if(uid == null){
                uidResult.add(bundle("spdb.query.uid.required", "Enter a three-character UID before searching.")).left();
                return;
            }

            Seq<PlayerRecord> list = playerDb.findByUid(uid);
            if(list.isEmpty()){
                uidResult.add(bundle("spdb.query.uid.not-found", "UID not found.")).left();
                return;
            }

            uidResult.add(bundleFormat("spdb.query.uid.matches", "Found @ PID records.", list.size)).left().row();
            for(PlayerRecord record : list){
                uidResult.table(Styles.black3, box -> renderPlayerDetailCard(box, record)).growX().padTop(4f).row();
            }
        }

        private void showMergeByUidPreview(){
            String uid = normalizeShortUid(uidField == null ? null : uidField.getText());
            if(uid == null){
                Vars.ui.showInfoFade(bundle("spdb.toast.uid.required", "SPDB: Enter a three-character UID."));
                return;
            }

            Seq<PlayerRecord> list = playerDb.findByUid(uid);
            if(list.size <= 1){
                Vars.ui.showInfoFade(bundleFormat("spdb.toast.uid.no-merge", "SPDB: UID @ does not need merging.", uid));
                return;
            }

            BaseDialog dialog = new BaseDialog(bundle("spdb.query.merge.title", "UID merge preview"));
            dialog.addCloseButton();
            dialog.cont.defaults().left().pad(4f);
            float previewWidth = fitDialogWidth(920f);
            float previewHeight = fitDialogHeight(520f, 260f);

            PlayerRecord target = list.first();
            dialog.cont.add(bundleFormat("spdb.query.merge.preview", "UID @ will merge @ PID records; keeping the most recently active PID: @", uid, list.size, target.pid)).left().wrap().row();
            dialog.cont.pane(p -> {
                p.left().top().defaults().left().pad(3f).growX();
                for(PlayerRecord rec : list){
                    boolean keep = rec == target;
                    String name = rec.names.isEmpty() ? "(unknown)" : rec.names.peek();
                    String line = (keep ? bundle("spdb.query.merge.keep", "[Keep]") : bundle("spdb.query.merge.into", "[Merge]") + " ")
                        + rec.pid + " | " + name + " | " + bundle("spdb.query.merge.last-seen", "Last seen") + " " + formatTime(rec.lastSeen);
                    p.add(escapeMarkup(line)).left().wrap().row();
                }
            }).grow().width(previewWidth).height(previewHeight).minWidth(0f).minHeight(0f).row();

            dialog.buttons.defaults().height(44f).pad(4f);
            dialog.buttons.button(bundle("spdb.query.merge.confirm", "Confirm merge"), () -> {
                dialog.hide();
                mergeByUid(uid);
            });
            dialog.buttons.button(bundle("spdb.action.cancel", "Cancel"), dialog::hide);
            dialog.show();
        }

        private void mergeByUid(String uid){
            uid = normalizeShortUid(uid);
            if(uid == null) return;

            int merged = playerDb.mergeByUid(uid);
            if(merged > 0){
                playersDirty = true;
                Vars.ui.showInfoFade(bundleFormat("spdb.toast.uid.merged", "SPDB: UID @ merged @ PID records.", uid, merged));
            }else{
                Vars.ui.showInfoFade(bundleFormat("spdb.toast.uid.no-merge", "SPDB: UID @ does not need merging.", uid));
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
                Vars.ui.showInfoFade(bundleFormat("spdb.toast.uid.merge-all", "SPDB: Merged all duplicate UIDs, total PID records merged: @.", merged));
            }else{
                Vars.ui.showInfoFade(bundle("spdb.toast.uid.merge-none", "SPDB: No duplicate UIDs can be merged."));
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
                Vars.ui.showInfoFade(bundle("spdb.toast.pid-fields", "SPDB: Enter a PID and a three-character UID."));
                return;
            }

            PlayerRecord rec = playerDb.getByPid(pid);
            if(rec == null){
                Vars.ui.showInfoFade(bundle("spdb.toast.pid-not-found", "SPDB: PID not found."));
                return;
            }
            if(rec.uid != null){
                Vars.ui.showInfoFade(bundleFormat("spdb.toast.pid-has-uid", "SPDB: This PID already has UID @; it will not be overwritten.", rec.uid));
                return;
            }

            if(playerDb.bindUid(pid, uid)){
                playersDirty = true;
                Vars.ui.showInfoFade(bundleFormat("spdb.toast.uid-filled", "SPDB: Filled UID @ -> @", uid, pid));
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
                Vars.ui.showInfoFade(bundle("spdb.toast.uid-two-required", "SPDB: Enter two three-character UIDs."));
                return;
            }
            if(fromUid.equals(toUid)){
                Vars.ui.showInfoFade(bundle("spdb.toast.uid-same", "SPDB: Source and target UIDs are identical; no merge is needed."));
                return;
            }

            int rebound = playerDb.rebindUid(fromUid, toUid);
            int merged = playerDb.mergeByUid(toUid);
            if(chatDb.moveUid(fromUid, toUid)) chatsDirty = true;

            if(rebound > 0 || merged > 0){
                playersDirty = true;
                Vars.ui.showInfoFade(bundleFormat("spdb.toast.uid-rebound", "SPDB: UID @ merged into @ (rebound @, merged PIDs @).", fromUid, toUid, rebound, merged));
            }else{
                Vars.ui.showInfoFade(bundle("spdb.toast.uid-merge-not-found", "SPDB: No mergeable UID records found."));
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
                pidResult.add(bundle("spdb.query.pid.required", "Enter a PID before searching.")).left();
                return;
            }

            PlayerRecord record = playerDb.getByPid(pid);
            if(record == null){
                pidResult.add(bundle("spdb.query.pid.not-found", "PID not found.")).left();
                return;
            }

            pidResult.table(Styles.black3, box -> renderPlayerDetailCard(box, record)).growX().padTop(4f).row();
        }

        private void refreshNameResult(){
            nameResult.clear();
            nameResult.left().top();

            String keyword = normalizeNameKeyword(nameField == null ? null : nameField.getText());
            if(keyword == null){
                nameResult.add(bundle("spdb.query.name.required", "Enter a player name before searching.")).left();
                return;
            }

            Seq<PlayerRecord> players = playerDb.findByNameContains(keyword);
            if(players.isEmpty()){
                nameResult.add(bundle("spdb.query.name.not-found", "No players matched that name."), Styles.outlineLabel).left();
                return;
            }

            nameResult.add(bundleFormat("spdb.query.name.matches", "Keyword '@' matched @ records.", escapeMarkup(keyword), players.size)).left().wrap().row();
            if(compactUi()){
                for(PlayerRecord rec : players){
                    String bestName = rec.names.isEmpty() ? "(unknown)" : rec.names.peek();
                    String uidText = rec.uid == null ? "(none)" : rec.uid;
                    nameResult.table(Styles.black3, card -> {
                        card.left().top().defaults().left().pad(3f).growX();
                        card.add(escapeMarkup(bundle("spdb.query.field.player", "Player") + ": " + bestName)).left().wrap().row();
                        card.add(escapeMarkup("PID: " + safeLine(rec.pid, 68))).left().wrap().row();
                        card.add(escapeMarkup(bundle("spdb.query.field.last-seen", "Last seen") + ": " + formatTime(rec.lastSeen))).left().wrap().row();
                        card.table(line -> {
                            line.left().defaults().left().padRight(6f).growX();
                            line.button(uidText, Styles.defaultt, () -> {
                                if(uidField != null && rec.uid != null) uidField.setText(rec.uid);
                                if(chatUidField != null && rec.uid != null) chatUidField.setText(rec.uid);
                                if(rec.uid != null) refreshUidResult();
                            }).height(32f).growX();
                            line.button(bundle("spdb.action.locate", "Locate"), () -> {
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
                head.add(bundle("spdb.query.header.name", "Name")).growX().minWidth(140f);
                head.add(bundle("spdb.query.header.uid", "UID")).width(70f);
                head.add(bundle("spdb.query.header.pid", "PID")).growX().minWidth(180f);
                head.add(bundle("spdb.query.header.last-seen", "Last seen")).growX().minWidth(140f);
                head.add(bundle("spdb.query.header.action", "Actions")).width(90f);
            }).growX().row();

            for(PlayerRecord rec : players){
                String bestName = rec.names.isEmpty() ? "(unknown)" : rec.names.peek();
                String uidText = rec.uid == null ? "(none)" : rec.uid;
                nameResult.table(row -> {
                    row.left().defaults().pad(3f).left();
                    row.add(escapeMarkup(safeLine(bestName, 26))).growX().minWidth(140f).left();
                    row.button(uidText, Styles.defaultt, () -> {
                        if(uidField != null && rec.uid != null) uidField.setText(rec.uid);
                        if(chatUidField != null && rec.uid != null) chatUidField.setText(rec.uid);
                        if(rec.uid != null) refreshUidResult();
                    }).width(70f).height(30f);
                    row.button(rec.pid, Styles.defaultt, () -> {
                        if(pidField != null) pidField.setText(rec.pid);
                        refreshPidResult();
                    }).growX().minWidth(180f).height(30f);
                    row.add(formatTime(rec.lastSeen)).growX().minWidth(140f).left();
                    row.button(bundle("spdb.action.locate", "Locate"), () -> {
                        if(pidField != null) pidField.setText(rec.pid);
                        refreshPidResult();
                    }).width(90f).height(30f);
                }).growX().left().row();
            }
        }

        private void renderPlayerDetailCard(Table box, PlayerRecord record){
            box.left().top().defaults().left().pad(3f).growX();
            addInteractiveFieldLine(box, bundle("spdb.query.header.pid", "PID"), record.pid, () -> {
                if(pidField != null) pidField.setText(record.pid);
                if(fillPidField != null) fillPidField.setText(record.pid);
                refreshPidResult();
            });
            addInteractiveFieldLine(box, bundle("spdb.query.header.uid", "UID"), record.uid == null ? bundle("spdb.query.field.none", "(none)") : record.uid, () -> {
                if(record.uid != null && uidField != null) uidField.setText(record.uid);
                if(record.uid != null && mergeFromUidField != null) mergeFromUidField.setText(record.uid);
                if(record.uid != null) refreshUidResult();
            });
            addFieldLine(box, bundle("spdb.query.field.first-seen", "First seen"), formatTime(record.firstSeen));
            addFieldLine(box, bundle("spdb.query.field.last-seen", "Last seen"), formatTime(record.lastSeen));

            box.add(bundle("spdb.query.field.previous-names", "Previous names:")).left().row();
            if(record.names.isEmpty()){
                box.add(bundle("spdb.query.field.none", "(none)")).left().row();
            }else{
                for(String name : record.names){
                    addFieldLine(box, "-", name);
                }
            }

            box.add(bundle("spdb.query.field.known-ips", "Known IPs:")).left().row();
            if(record.ips.isEmpty()){
                box.add(bundle("spdb.query.field.none", "(none)")).left().row();
            }else{
                for(String ip : record.ips){
                    addIpLine(box, ip);
                }
            }

            box.add(bundle("spdb.query.field.servers", "Servers seen:")).left().row();
            if(record.servers.isEmpty()){
                box.add(bundle("spdb.query.field.none", "(none)")).left().row();
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
                ipResult.add(bundle("spdb.query.ip.required", "Enter an IP address before searching.")).left();
                return;
            }

            Seq<PlayerRecord> players = playerDb.findByIp(ip);
            if(players.isEmpty()){
                ipResult.add(bundle("spdb.query.ip.not-found", "No players matched that IP address.")).left();
                return;
            }

            if(compactUi()){
                for(PlayerRecord rec : players){
                    String bestName = rec.names.isEmpty() ? "(unknown)" : rec.names.peek();
                    String uidText = rec.uid == null ? "(none)" : rec.uid;
                    String geo = lookupIpGeoCached(ip);
                    ipResult.table(Styles.black3, card -> {
                        card.left().top().defaults().left().pad(3f).growX();
                        card.add(escapeMarkup(bundle("spdb.query.field.player", "Player") + ": " + bestName)).left().wrap().row();
                        card.add(escapeMarkup("PID: " + safeLine(rec.pid, 68))).left().wrap().row();
                        card.add(escapeMarkup(bundle("spdb.query.field.ip-location", "IP location") + ": " + (geo == null ? bundle("spdb.query.field.pending", "(loading...)") : geo))).left().wrap().row();
                        card.table(line -> {
                            line.left().defaults().left().padRight(6f).growX();
                            line.button(uidText, Styles.defaultt, () -> {
                                if(uidField != null && rec.uid != null) uidField.setText(rec.uid);
                                if(chatUidField != null && rec.uid != null) chatUidField.setText(rec.uid);
                                if(rec.uid != null) refreshUidResult();
                            }).height(32f).growX();
                            line.button(bundle("spdb.action.locate", "Locate"), () -> {
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
                head.add(bundle("spdb.query.header.name", "Name")).growX().minWidth(140f);
                head.add(bundle("spdb.query.header.uid", "UID")).width(70f);
                head.add(bundle("spdb.query.header.pid", "PID")).growX().minWidth(180f);
                head.add(bundle("spdb.query.field.ip-location", "IP location")).growX().minWidth(140f);
                head.add(bundle("spdb.query.header.action", "Actions")).width(90f);
            }).growX().row();

            for(PlayerRecord rec : players){
                String bestName = rec.names.isEmpty() ? "(unknown)" : rec.names.peek();
                String uidText = rec.uid == null ? "(none)" : rec.uid;
                String geo = lookupIpGeoCached(ip);
                ipResult.table(row -> {
                    row.left().defaults().pad(3f).left();
                    row.add(escapeMarkup(bestName)).growX().minWidth(140f).left();
                    row.button(uidText, Styles.defaultt, () -> {
                        if(uidField != null && rec.uid != null) uidField.setText(rec.uid);
                        if(chatUidField != null && rec.uid != null) chatUidField.setText(rec.uid);
                        if(rec.uid != null) refreshUidResult();
                    }).width(70f).height(30f);
                    row.button(rec.pid, Styles.defaultt, () -> {
                        if(pidField != null) pidField.setText(rec.pid);
                        refreshPidResult();
                    }).growX().minWidth(180f).height(30f);
                    row.add(escapeMarkup(geo == null ? bundle("spdb.query.field.pending", "(loading...)") : geo)).growX().minWidth(140f).left();
                    row.button(bundle("spdb.action.locate", "Locate"), () -> {
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
                chatResult.add(bundle("spdb.query.chat.required", "Enter a three-character UID before searching chat history.")).left();
                return;
            }

            Seq<ChatEntry> list = chatDb.findByUid(uid);
            if(list.isEmpty()){
                chatResult.add(bundle("spdb.query.chat.not-found", "No chat history exists for this UID.")).left();
                return;
            }

            if(compactUi()){
                for(ChatEntry entry : list){
                    chatResult.table(Styles.black3, card -> {
                        card.left().top().defaults().left().pad(3f).growX();
                        card.add(escapeMarkup(formatTime(entry.time) + " | " + safeLine(entry.senderName, 26))).left().wrap().row();
                        card.add(escapeMarkup(bundle("spdb.query.field.content", "Content:") + " " + safeLine(entry.message, 130))).left().wrap().row();
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
                head.add(bundle("spdb.query.header.time", "Time")).width(158f);
                head.add(bundle("spdb.query.header.uid", "UID")).width(70f);
                head.add(bundle("spdb.query.header.player", "Player")).growX().minWidth(140f);
                head.add(bundle("spdb.query.header.content", "Content")).growX().minWidth(180f);
                head.add(bundle("spdb.query.header.action", "Actions")).width(90f);
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
                    row.add(escapeMarkup(safeLine(entry.senderName, 22))).growX().minWidth(140f).left();
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

            allPlayersResult.add(bundleFormat("spdb.query.page", "Page @ / @, @ records", allPlayersPage + 1, pages, total)).left().row();
            if(total == 0){
                allPlayersResult.add(bundle("spdb.query.players.empty", "No player records yet.\nEnable collection to populate this list."), Styles.outlineLabel).left().wrap().row();
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
                        card.add(escapeMarkup(bundle("spdb.query.field.last-seen", "Last seen") + ": " + formatTime(rec.lastSeen))).left().wrap().row();
                        card.add(escapeMarkup("PID: " + safeLine(rec.pid, 68))).left().wrap().row();
                        card.table(line -> {
                            line.left().defaults().left().padRight(6f).growX();
                            line.button(uidText, Styles.defaultt, () -> {
                                if(uidField != null && rec.uid != null) uidField.setText(rec.uid);
                                if(chatUidField != null && rec.uid != null) chatUidField.setText(rec.uid);
                                if(rec.uid != null) refreshUidResult();
                            }).height(32f).growX();
                            line.button(bundle("spdb.action.chat", "Chat"), () -> {
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
                head.add(bundle("spdb.query.header.player", "Player")).growX().minWidth(140f);
                head.add(bundle("spdb.query.header.uid", "UID")).width(70f);
                head.add(bundle("spdb.query.header.pid", "PID")).growX().minWidth(180f);
                head.add(bundle("spdb.query.header.last-seen", "Last seen")).growX().minWidth(140f);
                head.add(bundle("spdb.query.header.action", "Actions")).width(120f);
            }).growX().row();

            for(int i = from; i < to; i++){
                PlayerRecord rec = all.get(i);
                String bestName = rec.names.isEmpty() ? "(unknown)" : rec.names.peek();
                String uidText = rec.uid == null ? "(none)" : rec.uid;

                allPlayersResult.table(row -> {
                    row.left().defaults().pad(3f).left();
                    row.add(escapeMarkup(safeLine(bestName, 22))).growX().minWidth(140f).left();
                    row.button(uidText, Styles.defaultt, () -> {
                        if(uidField != null && rec.uid != null) uidField.setText(rec.uid);
                        if(chatUidField != null && rec.uid != null) chatUidField.setText(rec.uid);
                        if(rec.uid != null) refreshUidResult();
                    }).width(70f).height(30f);
                    row.button(rec.pid, Styles.defaultt, () -> {
                        if(pidField != null) pidField.setText(rec.pid);
                        refreshPidResult();
                    }).growX().minWidth(180f).height(30f);
                    row.add(formatTime(rec.lastSeen)).growX().minWidth(140f).left();
                    row.button(bundle("spdb.action.chat", "Chat"), () -> {
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

            allChatsResult.add(bundleFormat("spdb.query.page", "Page @ / @, @ records", allChatsPage + 1, pages, total)).left().row();
            if(total == 0){
                allChatsResult.add(bundle("spdb.query.chats.empty", "No chat records yet.\nEnable chat storage to populate this list."), Styles.outlineLabel).left().wrap().row();
                return;
            }

            Seq<ChatEntry> list = chatDb.findRecent(allChatsPage * chatsPageSize, chatsPageSize);
            if(compactUi()){
                for(ChatEntry entry : list){
                    allChatsResult.table(Styles.black3, card -> {
                        card.left().top().defaults().left().pad(3f).growX();
                        card.add(escapeMarkup(formatTime(entry.time) + " | " + safeLine(entry.senderName, 26))).left().wrap().row();
                        card.add(escapeMarkup(bundle("spdb.query.header.uid", "UID") + ": " + entry.uid + " | " + bundle("spdb.query.header.server", "Server") + ": " + safeLine(entry.server, 34))).left().wrap().row();
                        card.add(escapeMarkup(bundle("spdb.query.field.content", "Content:") + " " + safeLine(entry.message, 130))).left().wrap().row();
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
                head.add(bundle("spdb.query.header.time", "Time")).width(158f);
                head.add(bundle("spdb.query.header.uid", "UID")).width(70f);
                head.add(bundle("spdb.query.header.player", "Player")).growX().minWidth(140f);
                head.add(bundle("spdb.query.header.server", "Server")).growX().minWidth(160f);
                head.add(bundle("spdb.query.header.content", "Content")).growX().minWidth(180f);
                head.add(bundle("spdb.query.header.action", "Actions")).width(90f);
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
                    row.add(escapeMarkup(safeLine(entry.senderName, 22))).growX().minWidth(140f).left();
                    row.add(escapeMarkup(safeLine(entry.server, 26))).growX().minWidth(160f).left();
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

        void openSemanticSearch(String query){
            activeTab = 2;
            if(semanticContent != null){
                if(query != null){
                    semanticContent.setQueryText(query);
                }
                ensureSemanticSearchReady(query);
                if(semanticSearchReady() && query != null && !query.trim().isEmpty()){
                    semanticContent.runSearch();
                }else{
                    semanticContent.refreshStatus();
                }
            }
        }
    }

    private class OverlayQueryContent{
        final Table root = new Table();
        final Table result = new Table();
        ScrollPane resultPane;
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
                t.add("[accent]" + bundle("spdb.query.overlay.title", "SPDB quick query") + "[]").left().growX().row();

                t.table(line -> {
                    line.left().defaults().left().padRight(6f).growX();
                    uidField = line.field("", text -> {}).growX().get();
                    uidField.setMessageText(bundle("spdb.query.overlay.uid.placeholder", "Query by UID (3 characters)"));
                    line.button(bundle("spdb.query.overlay.uid", "Query UID"), this::queryByUid).height(34f).growX();
                }).growX().row();

                if(compact){
                    t.table(line -> {
                        line.left().defaults().left().padRight(6f);
                        nameField = line.field("", text -> {}).growX().get();
                        nameField.setMessageText(bundle("spdb.query.overlay.name.placeholder", "Fuzzy query by name"));
                    }).growX().row();
                    t.table(line -> {
                        line.left().defaults().left().padRight(6f).growX();
                        line.button(bundle("spdb.query.overlay.name", "Query name"), this::queryByName).height(34f).growX();
                    }).growX().row();
                    t.table(line -> {
                        line.left().defaults().left().padRight(6f);
                        ipField = line.field("", text -> {}).growX().get();
                        ipField.setMessageText(bundle("spdb.query.overlay.ip.placeholder", "Query by IP"));
                    }).growX().row();
                    t.table(line -> {
                        line.left().defaults().left().padRight(6f).growX();
                        line.button(bundle("spdb.query.overlay.ip", "Query IP"), this::queryByIp).height(34f).growX();
                        line.button(bundle("spdb.action.clear", "Clear"), this::clearResult).height(34f).growX();
                    }).growX().row();
                }else{
                    t.table(line -> {
                        line.left().defaults().left().padRight(6f);
                        nameField = line.field("", text -> {}).growX().get();
                        nameField.setMessageText(bundle("spdb.query.overlay.name.placeholder", "Fuzzy query by name"));
                        line.button(bundle("spdb.query.overlay.name", "Query name"), this::queryByName).height(34f);
                    }).growX().row();
                    t.table(line -> {
                        line.left().defaults().left().padRight(6f);
                        ipField = line.field("", text -> {}).growX().get();
                        ipField.setMessageText(bundle("spdb.query.overlay.ip.placeholder", "Query by IP"));
                        line.button(bundle("spdb.query.overlay.ip", "Query IP"), this::queryByIp).height(34f);
                        line.button(bundle("spdb.action.clear", "Clear"), this::clearResult).height(34f);
                    }).growX().row();
                }
            }).growX().row();

            resultPane = root.pane(result).scrollX(false).grow().maxHeight(fitPaneHeight(420f, 180f)).minWidth(0f).minHeight(0f).get();
            root.row();
            root.add(new PreferAnySize()).grow().row();
        }

        private void queryByUid(){
            lastMode = 1;
            result.clear();
            result.left().top();
            try{
                String uid = normalizeShortUid(uidField == null ? null : uidField.getText());
                if(uid == null){
                    result.add(bundle("spdb.query.overlay.result.uid-required", "Enter a three-character UID (for example dNF)."), Styles.outlineLabel).left().row();
                    return;
                }

                Seq<PlayerRecord> list = playerDb.findByUid(uid);
                if(list.isEmpty()){
                    result.add(bundle("spdb.query.overlay.result.uid-empty", "UID not found."), Styles.outlineLabel).left().row();
                    return;
                }

                result.add(bundleFormat("spdb.query.overlay.result.uid", "UID @ matched @ records", uid, list.size)).left().row();
                for(PlayerRecord rec : list){
                    String name = rec.names.isEmpty() ? bundle("spdb.query.field.none", "(none)") : rec.names.peek();
                    String geo = rec.ips.isEmpty() ? "" : lookupIpGeoCached(rec.ips.peek());
                    String text = bundle("spdb.query.header.pid", "PID") + ": " + rec.pid + "\n" +
                        bundle("spdb.query.header.uid", "UID") + ": " + (rec.uid == null ? bundle("spdb.query.field.none", "(none)") : rec.uid) + "\n" +
                        bundle("spdb.query.header.name", "Name") + ": " + name + "\n" +
                        "IP: " + (rec.ips.isEmpty() ? bundle("spdb.query.field.none", "(none)") : rec.ips.peek()) + (geo == null || geo.isEmpty() ? "" : " (" + geo + ")") + "\n" +
                        bundle("spdb.query.overlay.result.last", "Last") + ": " + formatTime(rec.lastSeen);

                    result.table(Styles.black3, row -> {
                        row.left().defaults().left().pad(4f);
                        row.add(escapeMarkup(text)).growX().left().wrap();
                    }).growX().padTop(3f).row();
                }
            }finally{
                refreshLayout();
            }
        }

        private void queryByName(){
            lastMode = 3;
            result.clear();
            result.left().top();
            try{
                String keyword = normalizeNameKeyword(nameField == null ? null : nameField.getText());
                if(keyword == null){
                    result.add(bundle("spdb.query.overlay.result.name-required", "Enter a player name before searching."), Styles.outlineLabel).left().row();
                    return;
                }

                Seq<PlayerRecord> list = playerDb.findByNameContains(keyword);
                if(list.isEmpty()){
                    result.add(bundle("spdb.query.overlay.result.name-empty", "No players matched that name."), Styles.outlineLabel).left().row();
                    return;
                }

                result.add(bundleFormat("spdb.query.overlay.result.name", "Keyword '@' matched @ records", escapeMarkup(keyword), list.size)).left().wrap().row();
                for(PlayerRecord rec : list){
                    String name = rec.names.isEmpty() ? bundle("spdb.query.field.none", "(none)") : rec.names.peek();
                    String text = bundle("spdb.query.header.name", "Name") + ": " + name + "\n" +
                        bundle("spdb.query.header.uid", "UID") + ": " + (rec.uid == null ? bundle("spdb.query.field.none", "(none)") : rec.uid) + "\n" +
                        bundle("spdb.query.header.pid", "PID") + ": " + rec.pid + "\n" +
                        bundle("spdb.query.overlay.result.last", "Last") + ": " + formatTime(rec.lastSeen);

                    result.table(Styles.black3, row -> {
                        row.left().defaults().left().pad(4f);
                        row.add(escapeMarkup(text)).growX().left().wrap();
                    }).growX().padTop(3f).row();
                }
            }finally{
                refreshLayout();
            }
        }

        private void clearResult(){
            lastMode = 0;
            result.clear();
            if(uidField != null) uidField.setText("");
            if(nameField != null) nameField.setText("");
            if(ipField != null) ipField.setText("");
            refreshLayout();
        }

        private void queryByIp(){
            lastMode = 2;
            result.clear();
            result.left().top();
            try{
                String ip = normalizeIp(ipField == null ? null : ipField.getText());
                if(ip == null){
                    result.add(bundle("spdb.query.overlay.result.ip-required", "Enter an IP address."), Styles.outlineLabel).left().row();
                    return;
                }

                String geo = lookupIpGeoCached(ip);
                result.add("IP: " + ip + (geo == null ? "" : " (" + geo + ")")).left().wrap().row();

                Seq<PlayerRecord> list = playerDb.findByIp(ip);
                if(list.isEmpty()){
                    result.add(bundle("spdb.query.overlay.result.ip-empty", "No players matched that IP address."), Styles.outlineLabel).left().row();
                    return;
                }

                for(PlayerRecord rec : list){
                    String name = rec.names.isEmpty() ? bundle("spdb.query.field.none", "(none)") : rec.names.peek();
                    String text = bundle("spdb.query.header.name", "Name") + ": " + name + "\n" +
                        bundle("spdb.query.header.uid", "UID") + ": " + (rec.uid == null ? bundle("spdb.query.field.none", "(none)") : rec.uid) + "\n" +
                        bundle("spdb.query.header.pid", "PID") + ": " + rec.pid + "\n" +
                        bundle("spdb.query.overlay.result.last", "Last") + ": " + formatTime(rec.lastSeen);

                    result.table(Styles.black3, row -> {
                        row.left().defaults().left().pad(4f);
                        row.add(escapeMarkup(text)).growX().left().wrap();
                    }).growX().padTop(3f).row();
                }
            }finally{
                refreshLayout();
            }
        }

        private void refreshLayout(){
            result.invalidateHierarchy();
            if(resultPane != null) resultPane.invalidateHierarchy();
            root.invalidateHierarchy();
            try{
                result.layout();
                if(resultPane != null) resultPane.layout();
                root.layout();
                if(overlayQueryWindow != null && overlayQueryWindow.asElement() instanceof Table){
                    Table window = (Table)overlayQueryWindow.asElement();
                    window.invalidateHierarchy();
                    window.layout();
                }
            }catch(Throwable t){
                Log.warn("SPDB: quick query layout refresh failed: @", t.getMessage());
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
                    top.add("[accent]" + bundle("spdb.query.debug.title", "SPDB debug panel") + "[]  " + bundle("spdb.query.debug.recent", "Recent UID parsing records")).left().growX().wrap().row();
                    top.button(bundle("spdb.action.refresh", "Refresh"), this::refresh).height(36f).growX().row();
                    top.table(btns -> {
                        btns.left().defaults().padRight(6f).growX();
                        btns.button(bundle("spdb.action.clear", "Clear"), () -> {
                            debugLines.clear();
                            refresh();
                        }).height(36f).growX();
                        btns.button(bundle("spdb.action.copy", "Copy"), this::copyAll).height(36f).growX();
                    }).growX();
                }else{
                    top.add("[accent]" + bundle("spdb.query.debug.title", "SPDB debug panel") + "[]  " + bundle("spdb.query.debug.recent", "Recent UID parsing records")).left().growX();
                    top.button(bundle("spdb.action.refresh", "Refresh"), this::refresh).height(36f);
                    top.button(bundle("spdb.action.clear", "Clear"), () -> {
                        debugLines.clear();
                        refresh();
                    }).height(36f);
                    top.button(bundle("spdb.action.copy", "Copy"), this::copyAll).height(36f);
                }
            }).growX().row();

            root.table(Styles.black3, parser -> {
                parser.left().top().defaults().left().pad(4f).growX();
                parser.add(bundle("spdb.query.debug.replay", "Chat line parser replay (paste a raw chat line to inspect name/UID/message)")).left().row();
                if(compact){
                    parser.table(line -> {
                        line.left().defaults().left().padRight(6f);
                        replayField = line.field("", text -> {}).growX().get();
                        replayField.setMessageText(bundle("spdb.query.debug.replay.placeholder", "Paste one raw chat line, e.g. [coral]...[gray]dNFX...: sample text"));
                    }).growX().row();
                    parser.table(line -> {
                        line.left().defaults().left().padRight(6f).growX();
                        line.button(bundle("spdb.query.debug.parse", "Parse"), this::parseReplay).height(34f).growX();
                        line.button(bundle("spdb.action.clear", "Clear"), () -> {
                            replayField.setText("");
                            replayResult.clear();
                        }).height(34f).growX();
                    }).growX().row();
                }else{
                    parser.table(line -> {
                        line.left().defaults().left().padRight(6f);
                        replayField = line.field("", text -> {}).growX().get();
                        replayField.setMessageText(bundle("spdb.query.debug.replay.placeholder", "Paste one raw chat line, e.g. [coral]...[gray]dNFX...: sample text"));
                        line.button(bundle("spdb.query.debug.parse", "Parse"), this::parseReplay).height(34f);
                        line.button(bundle("spdb.action.clear", "Clear"), () -> {
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
                lines.add(bundle("spdb.query.debug.empty", "No debug records yet. Parser logs will appear after chatting."), Styles.outlineLabel).left().row();
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
                replayResult.add(bundle("spdb.query.debug.required", "Paste a chat line first."), Styles.outlineLabel).left().row();
                return;
            }

            String stripped = Strings.stripColors(raw);
            ChatSnapshot snapshot = parseChatLine(raw, stripped, "");
            if(snapshot == null){
                replayResult.add(bundle("spdb.query.debug.invalid", "Parse failed: this is not a valid chat line (it must contain ':')."), Styles.outlineLabel).left().row();
                addReplayField(bundle("spdb.query.debug.stripped", "Uncolored text"), safeLine(stripped, 220));
                return;
            }

            addReplayField(bundle("spdb.query.debug.name", "Player name"), snapshot.senderName == null ? bundle("spdb.query.field.none", "(none)") : snapshot.senderName);
            addReplayField(bundle("spdb.query.debug.uid", "Player UID"), snapshot.uid == null ? bundle("spdb.query.field.none", "(none)") : snapshot.uid);
            addReplayField(bundle("spdb.query.debug.message", "Chat content"), snapshot.message == null ? bundle("spdb.query.field.none", "(none)") : snapshot.message);
            addReplayField(bundle("spdb.query.debug.stripped", "Uncolored text"), safeLine(stripped, 220));

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
        if(Vars.ui != null) Vars.ui.showInfoFade(bundle("spdb.toast.copy", "Copied"));
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
        return bundle("spdb.query.field.pending", "(loading...)");
    }

    private final ExecutorService ipGeoExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "spdb-ipgeo");
        thread.setDaemon(true);
        return thread;
    });

    private void requestIpGeoAsync(String ip){
        ipGeoExecutor.execute(() -> {
            String result = bundle("spdb.query.field.failed", "(lookup failed)");
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
        });
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

    static class ChatDatabase{
        private static final int maxCachedDays = 8;
        private SqliteChatBackend sqlite;
        private boolean useSqlite;

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

        void loadStorage(Fi chatsDbFile, Fi chatsDir, Fi chatsIndexFile, Fi legacyChatFile, Json serializer){
            Seq<String> sqliteFallbackIssues = new Seq<>();
            sqlite = tryCreateSqliteBackend(sqliteFallbackIssues);
            if(sqlite != null && sqlite.load(chatsDbFile, chatsDir, chatsIndexFile, legacyChatFile, serializer)){
                useSqlite = true;
                return;
            }

            useSqlite = false;
            storageDir = chatsDir;
            indexFile = chatsIndexFile;
            json = serializer;

            dayEntries.clear();
            dayDedupe.clear();
            dayCacheOrder.clear();
            dirtyDates.clear();
            uidToDates.clear();
            integrityIssues.clear();
            for(String issue : sqliteFallbackIssues){
                if(issue != null && !issue.isEmpty() && !integrityIssues.contains(issue, false)){
                    integrityIssues.add(issue);
                }
            }
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
                        addIntegrityIssue(bundle("spdb.integrity.chat-index-modified", "Chat index verification failed: chat_index.json may have been modified."));
                    }else if(indexIntegrityState == integrityUnsupported){
                        addIntegrityIssue(bundle("spdb.integrity.chat-index-unsupported", "Chat index verification failed: chat_index.json uses an unsupported verification algorithm."));
                    }
                }catch(Throwable t){
                    indexIntegrityState = integrityMismatch;
                    addIntegrityIssue(bundle("spdb.integrity.chat-index-read-failed", "Chat index could not be read; integrity verification could not be completed."));
                    Log.err("SPDB: failed to read chat index, rebuilding.", t);
                }
            }

            rebuildIndexFromFiles();
            indexDirty = true;
            if(indexIntegrityState == integrityMissing) indexDirty = true;
        }

        private SqliteChatBackend tryCreateSqliteBackend(Seq<String> fallbackIssues){
            try{
                return new SqliteChatBackend();
            }catch(Throwable t){
                String message = bundle("spdb.integrity.sqlite-unavailable", "SQLite chat storage is unavailable because the runtime lacks SPDB's Java SQL components; falling back to JSON shards.");
                if(fallbackIssues != null && !fallbackIssues.contains(message, false)){
                    fallbackIssues.add(message);
                }
                Log.err("SPDB: sqlite backend unavailable, falling back to JSON shard storage.", t);
                return null;
            }
        }

        boolean usesSqlite(){
            return useSqlite;
        }

        String storageBackendName(){
            return useSqlite ? "SQLite" : bundle("spdb.integrity.json-shards", "JSON shards");
        }

        boolean hasPendingWrites(){
            if(useSqlite) return sqlite.hasPendingWrites();
            return !dirtyDates.isEmpty() || indexDirty;
        }

        boolean hasIntegrityIssues(){
            if(useSqlite) return sqlite.hasIntegrityIssues();
            return !integrityIssues.isEmpty() || shardsMismatch > 0 || shardsUnsupported > 0 || indexIntegrityState == integrityMismatch || indexIntegrityState == integrityUnsupported;
        }

        Seq<String> integrityIssues(){
            if(useSqlite) return sqlite.integrityIssues();
            return integrityIssues.copy();
        }

        int indexIntegrityState(){
            if(useSqlite) return sqlite.indexIntegrityState();
            return indexIntegrityState;
        }

        int shardsChecked(){
            if(useSqlite) return sqlite.shardsChecked();
            return shardsChecked;
        }

        int shardsValid(){
            if(useSqlite) return sqlite.shardsValid();
            return shardsValid;
        }

        int shardsMissing(){
            if(useSqlite) return sqlite.shardsMissing();
            return shardsMissing;
        }

        int shardsMismatch(){
            if(useSqlite) return sqlite.shardsMismatch();
            return shardsMismatch;
        }

        int shardsUnsupported(){
            if(useSqlite) return sqlite.shardsUnsupported();
            return shardsUnsupported;
        }

        int totalEntries(){
            if(useSqlite) return sqlite.totalEntries();
            return totalEntries;
        }

        void flushToStorage(Fi chatsDbFile, Fi chatsDir, Fi chatsIndexFile, Fi legacyChatFile, Json serializer){
            if(useSqlite){
                sqlite.flushToStorage(chatsDbFile, chatsDir, chatsIndexFile, legacyChatFile, serializer);
                return;
            }

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
            if(useSqlite) return sqlite.snapshot();

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
            if(useSqlite) return sqlite.mergeFrom(incoming);

            if(incoming == null || incoming.entries == null) return 0;
            int merged = 0;
            for(ChatEntry entry : incoming.entries){
                if(entry == null) continue;
                if(add(entry.uid, entry.senderName, entry.message, entry.server, entry.time) > 0){
                    merged++;
                }
            }
            return merged;
        }

        boolean moveUid(String oldUid, String newUid){
            if(useSqlite) return sqlite.moveUid(oldUid, newUid);

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

        int add(String uid, String senderName, String message, String server, long time){
            if(useSqlite) return sqlite.add(uid, senderName, message, server, time);

            uid = normalizeUid(uid);
            if(uid == null || message == null) return -1;

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
            if(!dedupe.add(key)) return -1;

            entries.add(entry);
            dirtyDates.add(date);
            touchDay(date);
            addUidDate(uid, date);
            totalEntries++;
            return -1;
        }

        Seq<ChatEntry> findByUid(String uid){
            if(useSqlite) return sqlite.findByUid(uid);

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
            if(useSqlite) return sqlite.findRecent(offset, limit);

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

        Connection openSqliteConnection() throws SQLException{
            if(!useSqlite || sqlite == null){
                throw new SQLException("SQLite backend is not active.");
            }
            return sqlite.openConnection();
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
                                addIntegrityIssue(bundleFormat("spdb.integrity.shard-modified", "Chat shard verification failed: @ may have been modified.", file.name()));
                            }else if(state == integrityUnsupported){
                                addIntegrityIssue(bundleFormat("spdb.integrity.shard-unsupported", "Chat shard verification failed: @ uses an unsupported verification algorithm.", file.name()));
                            }

                            entries = normalizeDayEntries(loaded.entries, date, true);
                        }
                    }catch(Throwable t){
                        addIntegrityIssue(bundleFormat("spdb.integrity.shard-read-failed", "Chat shard read failed: integrity verification could not be completed for @.", file.name()));
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
                        addIntegrityIssue(bundleFormat("spdb.integrity.shard-invalid", "Chat shard read failed: @ has an invalid file format.", file.name()));
                        continue;
                    }

                    int state = verifyChatDayFileIntegrity(loaded);
                    if(state == integrityValid){
                        shardsValid++;
                    }else if(state == integrityMissing){
                        shardsMissing++;
                    }else if(state == integrityUnsupported){
                        shardsUnsupported++;
                        addIntegrityIssue(bundleFormat("spdb.integrity.shard-unsupported", "Chat shard verification failed: @ uses an unsupported verification algorithm.", file.name()));
                    }else{
                        shardsMismatch++;
                        addIntegrityIssue(bundleFormat("spdb.integrity.shard-modified", "Chat shard verification failed: @ may have been modified.", file.name()));
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
                    addIntegrityIssue(bundleFormat("spdb.integrity.shard-read-failed", "Chat shard read failed: integrity verification could not be completed for @.", file.name()));
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

    private static class SqliteChatBackend{
        private Fi databaseFile;
        private final Seq<String> integrityIssues = new Seq<>();
        private int integrityState = integrityMissing;
        private int totalEntries;

        boolean load(Fi chatsDbFile, Fi chatsDir, Fi chatsIndexFile, Fi legacyChatFile, Json serializer){
            databaseFile = chatsDbFile;
            integrityIssues.clear();
            integrityState = integrityMissing;
            totalEntries = 0;

            if(databaseFile == null) return false;

            try{
                Class.forName("org.sqlite.JDBC");
                if(databaseFile.parent() != null) databaseFile.parent().mkdirs();

                try(Connection conn = openConnection()){
                    applyPragmas(conn);
                    ensureSchema(conn);
                }

                integrityState = integrityValid;
                totalEntries = countRows();

                if(totalEntries == 0 && chatsDir != null && chatsDir.exists() && serializer != null){
                    int migrated = migrateShardFiles(chatsDir, serializer);
                    if(migrated > 0){
                        totalEntries = countRows();
                        Log.info("SPDB: migrated @ chat entries from JSON shards into SQLite.", migrated);
                    }
                }

                return true;
            }catch(Throwable t){
                integrityState = integrityMismatch;
                addIntegrityIssue(bundle("spdb.integrity.sqlite-init-failed", "SQLite chat storage initialization failed; falling back to JSON shards."), t);
                return false;
            }
        }

        boolean hasPendingWrites(){
            return false;
        }

        boolean hasIntegrityIssues(){
            return !integrityIssues.isEmpty() || integrityState == integrityMismatch || integrityState == integrityUnsupported;
        }

        Seq<String> integrityIssues(){
            return integrityIssues.copy();
        }

        int indexIntegrityState(){
            return integrityState;
        }

        int shardsChecked(){
            return databaseFile != null && databaseFile.exists() ? 1 : 0;
        }

        int shardsValid(){
            return integrityState == integrityValid ? 1 : 0;
        }

        int shardsMissing(){
            return integrityState == integrityMissing ? 1 : 0;
        }

        int shardsMismatch(){
            return integrityState == integrityMismatch ? 1 : 0;
        }

        int shardsUnsupported(){
            return integrityState == integrityUnsupported ? 1 : 0;
        }

        int totalEntries(){
            return totalEntries;
        }

        void flushToStorage(Fi chatsDbFile, Fi chatsDir, Fi chatsIndexFile, Fi legacyChatFile, Json serializer){
        }

        ChatDbFile snapshot(){
            ChatDbFile out = new ChatDbFile();

            try(Connection conn = openConnection();
                PreparedStatement stmt = conn.prepareStatement("select uid, sender_name, message, server, time from chat_entries order by time asc, id asc");
                ResultSet rs = stmt.executeQuery()){
                while(rs.next()){
                    out.entries.add(readEntry(rs));
                }
            }catch(Throwable t){
                addIntegrityIssue(bundle("spdb.integrity.sqlite-export-failed", "SQLite chat database export failed."), t);
            }

            return out;
        }

        int mergeFrom(ChatDbFile incoming){
            if(incoming == null || incoming.entries == null || incoming.entries.isEmpty()) return 0;
            int merged = 0;

            try(Connection conn = openConnection()){
                applyPragmas(conn);
                conn.setAutoCommit(false);

                try(PreparedStatement stmt = conn.prepareStatement("insert or ignore into chat_entries(uid, sender_name, message, server, time, dedupe_key) values (?, ?, ?, ?, ?, ?)")){
                    for(ChatEntry raw : incoming.entries){
                        if(insertEntry(stmt, raw) > 0L) merged++;
                    }
                }

                conn.commit();
                totalEntries = countRows(conn);
            }catch(Throwable t){
                addIntegrityIssue(bundle("spdb.integrity.sqlite-batch-write-failed", "SQLite chat database batch write failed."), t);
                return 0;
            }

            return merged;
        }

        boolean moveUid(String oldUid, String newUid){
            oldUid = normalizeUid(oldUid);
            newUid = normalizeUid(newUid);
            if(oldUid == null || newUid == null || oldUid.equals(newUid)) return false;

            boolean changed = false;
            try(Connection conn = openConnection()){
                applyPragmas(conn);
                conn.setAutoCommit(false);

                try(PreparedStatement select = conn.prepareStatement("select id, sender_name, message, server, time from chat_entries where uid = ? order by time asc, id asc");
                    PreparedStatement update = conn.prepareStatement("update chat_entries set uid = ?, dedupe_key = ? where id = ?");
                    PreparedStatement delete = conn.prepareStatement("delete from chat_entries where id = ?")){
                    select.setString(1, oldUid);

                    try(ResultSet rs = select.executeQuery()){
                        while(rs.next()){
                            long id = rs.getLong(1);
                            String message = rs.getString(3);
                            long time = rs.getLong(5);
                            String server = normalizeServer(rs.getString(4));
                            String dedupe = newUid + "|" + time + "|" + server + "|" + message;

                            try{
                                update.setString(1, newUid);
                                update.setString(2, dedupe);
                                update.setLong(3, id);
                                changed |= update.executeUpdate() > 0;
                            }catch(SQLException conflict){
                                if(!isUniqueConflict(conflict)) throw conflict;
                                delete.setLong(1, id);
                                changed |= delete.executeUpdate() > 0;
                            }
                        }
                    }
                }

                conn.commit();
                if(changed) totalEntries = countRows(conn);
                return changed;
            }catch(Throwable t){
                addIntegrityIssue(bundle("spdb.integrity.sqlite-uid-merge-failed", "SQLite chat UID merge failed."), t);
                return false;
            }
        }

        int add(String uid, String senderName, String message, String server, long time){
            ChatEntry normalized = normalizeEntry(uid, senderName, message, server, time);
            if(normalized == null) return -1;

            try(Connection conn = openConnection()){
                Long existingId = findExistingEntryId(conn, normalized);
                if(existingId != null) return existingId.intValue();

                try(PreparedStatement stmt = conn.prepareStatement(
                    "insert or ignore into chat_entries(uid, sender_name, message, server, time, dedupe_key) values (?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS
                )){
                    long insertedId = insertEntry(stmt, normalized);
                    if(insertedId > 0L){
                        totalEntries++;
                        return (int)insertedId;
                    }
                }
                return -1;
            }catch(Throwable t){
                addIntegrityIssue(bundle("spdb.integrity.sqlite-write-failed", "SQLite chat record write failed."), t);
                return -1;
            }
        }

        Seq<ChatEntry> findByUid(String uid){
            uid = normalizeUid(uid);
            Seq<ChatEntry> out = new Seq<>();
            if(uid == null) return out;

            try(Connection conn = openConnection();
                PreparedStatement stmt = conn.prepareStatement("select uid, sender_name, message, server, time from chat_entries where uid = ? order by time desc, id desc")){
                stmt.setString(1, uid);
                try(ResultSet rs = stmt.executeQuery()){
                    while(rs.next()){
                        out.add(readEntry(rs));
                    }
                }
            }catch(Throwable t){
                addIntegrityIssue(bundle("spdb.integrity.sqlite-query-failed", "SQLite chat record query failed."), t);
            }

            return out;
        }

        Seq<ChatEntry> findRecent(int offset, int limit){
            Seq<ChatEntry> out = new Seq<>();
            if(limit <= 0) return out;
            if(offset < 0) offset = 0;

            try(Connection conn = openConnection();
                PreparedStatement stmt = conn.prepareStatement("select uid, sender_name, message, server, time from chat_entries order by time desc, id desc limit ? offset ?")){
                stmt.setInt(1, limit);
                stmt.setInt(2, offset);
                try(ResultSet rs = stmt.executeQuery()){
                    while(rs.next()){
                        out.add(readEntry(rs));
                    }
                }
            }catch(Throwable t){
                addIntegrityIssue(bundle("spdb.integrity.sqlite-page-query-failed", "SQLite chat page query failed."), t);
            }

            return out;
        }

        private int migrateShardFiles(Fi chatsDir, Json serializer){
            if(chatsDir == null || serializer == null || !chatsDir.exists()) return 0;

            Seq<Fi> files = new Seq<>();
            for(Fi file : chatsDir.list()){
                if(ChatDatabase.dateFromFileName(file.name()) != null) files.add(file);
            }
            files.sort((a, b) -> a.name().compareTo(b.name()));

            int migrated = 0;
            try(Connection conn = openConnection()){
                applyPragmas(conn);
                conn.setAutoCommit(false);

                try(PreparedStatement stmt = conn.prepareStatement("insert or ignore into chat_entries(uid, sender_name, message, server, time, dedupe_key) values (?, ?, ?, ?, ?, ?)")){
                    for(Fi file : files){
                        try{
                            ChatDayFile loaded = serializer.fromJson(ChatDayFile.class, file.readString("UTF-8"));
                            if(loaded == null || loaded.entries == null) continue;
                            for(ChatEntry raw : loaded.entries){
                                if(insertEntry(stmt, raw) > 0L) migrated++;
                            }
                        }catch(Throwable t){
                            addIntegrityIssue(bundleFormat("spdb.integrity.sqlite-migrate-file-failed", "SQLite migration of old chat shard failed: @", file.name()), t);
                        }
                    }
                }

                conn.commit();
            }catch(Throwable t){
                addIntegrityIssue(bundle("spdb.integrity.sqlite-migrate-failed", "SQLite migration of old chat shards failed."), t);
                return 0;
            }

            return migrated;
        }

        Connection openConnection() throws SQLException{
            return DriverManager.getConnection("jdbc:sqlite:" + databaseFile.file().getAbsolutePath());
        }

        private static void applyPragmas(Connection conn) throws SQLException{
            try(Statement stmt = conn.createStatement()){
                stmt.execute("pragma journal_mode = WAL");
                stmt.execute("pragma synchronous = NORMAL");
                stmt.execute("pragma temp_store = MEMORY");
            }
        }

        private static void ensureSchema(Connection conn) throws SQLException{
            try(Statement stmt = conn.createStatement()){
                stmt.execute("create table if not exists chat_entries (id integer primary key autoincrement, uid text not null, sender_name text, message text not null, server text not null, time integer not null, dedupe_key text not null unique)");
                stmt.execute("create index if not exists idx_chat_uid_time on chat_entries(uid, time desc)");
                stmt.execute("create index if not exists idx_chat_time on chat_entries(time desc)");
                stmt.execute("create table if not exists chat_vectors (entry_id integer primary key references chat_entries(id) on delete cascade, vector blob not null)");
            }
        }

        private int countRows(){
            try(Connection conn = openConnection()){
                return countRows(conn);
            }catch(Throwable t){
                addIntegrityIssue(bundle("spdb.integrity.sqlite-count-failed", "SQLite chat record count failed."), t);
                return totalEntries;
            }
        }

        private static int countRows(Connection conn) throws SQLException{
            try(Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery("select count(*) from chat_entries")){
                return rs.next() ? rs.getInt(1) : 0;
            }
        }

        private static ChatEntry readEntry(ResultSet rs) throws SQLException{
            ChatEntry entry = new ChatEntry();
            entry.uid = rs.getString(1);
            entry.senderName = rs.getString(2);
            entry.message = rs.getString(3);
            entry.server = rs.getString(4);
            entry.time = rs.getLong(5);
            return entry;
        }

        private static boolean isUniqueConflict(SQLException error){
            String message = error == null ? "" : error.getMessage();
            return message != null && message.toLowerCase(Locale.ROOT).contains("unique");
        }

        private long insertEntry(PreparedStatement stmt, ChatEntry raw) throws SQLException{
            ChatEntry entry = normalizeEntry(raw == null ? null : raw.uid, raw == null ? null : raw.senderName, raw == null ? null : raw.message, raw == null ? null : raw.server, raw == null ? 0L : raw.time);
            if(entry == null) return -1L;

            stmt.setString(1, entry.uid);
            stmt.setString(2, entry.senderName);
            stmt.setString(3, entry.message);
            stmt.setString(4, entry.server);
            stmt.setLong(5, entry.time);
            stmt.setString(6, ChatDatabase.dedupeKey(entry));
            if(stmt.executeUpdate() <= 0) return -1L;

            try(ResultSet generatedKeys = stmt.getGeneratedKeys()){
                if(generatedKeys.next()) return generatedKeys.getLong(1);
            }
            return -1L;
        }

        private Long findExistingEntryId(Connection conn, ChatEntry entry) throws SQLException{
            try(PreparedStatement stmt = conn.prepareStatement("select id from chat_entries where dedupe_key = ?")){
                stmt.setString(1, ChatDatabase.dedupeKey(entry));
                try(ResultSet rs = stmt.executeQuery()){
                    return rs.next() ? rs.getLong(1) : null;
                }
            }
        }

        private static ChatEntry normalizeEntry(String uid, String senderName, String message, String server, long time){
            uid = normalizeUid(uid);
            if(uid == null || message == null) return null;

            ChatEntry entry = new ChatEntry();
            entry.uid = uid;
            entry.senderName = safeName(senderName);
            entry.message = message;
            entry.server = normalizeServer(server);
            entry.time = time > 0 ? time : Time.millis();
            return entry;
        }

        private void addIntegrityIssue(String message, Throwable error){
            if(message != null && !message.isEmpty() && !integrityIssues.contains(message, false)) integrityIssues.add(message);
            if(error != null) Log.err("SPDB: @", message, error);
        }
    }

}
