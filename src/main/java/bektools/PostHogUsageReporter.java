package bektools;

import arc.Core;
import arc.Events;
import arc.util.Log;
import arc.util.Strings;
import arc.util.Time;
import mindustry.Vars;
import mindustry.core.Version;
import mindustry.game.EventType;
import mindustry.mod.Mod;
import mindustry.mod.Mods;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

final class PostHogUsageReporter{
    private static final String logTag = "[Neon/PostHog]";
    private static final String apiKey = "phc_ry3vePCjmUFcy9oQ765tTDA2FagUXVrMcw3DWPsVCkn9";
    private static final String endpoint = "https://us.i.posthog.com/i/v0/e/";
    private static final String eventName = "mod_session_end";

    private static final String keyPendingMinutes = "bek-posthog-pending-minutes";
    private static final String keyStablePlayerId = "bek-posthog-stable-player-id";
    private static final int minFlushMinutes = 2;

    private final Class<? extends Mod> modType;

    private boolean installed;
    private boolean flushed;
    private double inGameSeconds;
    private Thread shutdownHook;
    private PlayerIdResolution cachedIdResolution;

    PostHogUsageReporter(Class<? extends Mod> modType){
        this.modType = modType;
    }

    void onClientLoad(){
        if(installed || Vars.headless) return;
        installed = true;

        Core.settings.defaults(keyPendingMinutes, 0);
        Core.settings.defaults(keyStablePlayerId, "");
        Log.info(logTag + " initialized. thresholdMinutes=" + minFlushMinutes + ", pendingMinutes=" + Math.max(0, Core.settings.getInt(keyPendingMinutes, 0)));
        Events.run(EventType.Trigger.update, this::onUpdate);
        Events.on(EventType.DisposeEvent.class, e -> flush("dispose-event"));
        installShutdownHook();

        cachedIdResolution = resolvePlayerId();
        Log.info(logTag + " player id pre-cached. source=" + cachedIdResolution.source + ", distinct_id=" + cachedIdResolution.playerId);
    }

    private void onUpdate(){
        if(flushed) return;
        if(Vars.state != null && Vars.state.isGame()){
            inGameSeconds += Time.delta / 60.0;
        }
    }

    private void flush(String source){
        if(flushed || Vars.headless) return;
        flushed = true;

        int sessionMinutes = Math.max(0, (int)(inGameSeconds / 60.0));
        int pendingMinutes = Math.max(0, Core.settings.getInt(keyPendingMinutes, 0));
        int nextPending = pendingMinutes + sessionMinutes;
        Log.info(logTag + " flush start. source=" + source + ", sessionMinutes=" + sessionMinutes + ", pendingBefore=" + pendingMinutes + ", pendingAfter=" + nextPending);

        // Persist first so short sessions and failed requests roll into next launch.
        persistPendingMinutes(nextPending);
        if(nextPending < minFlushMinutes){
            Log.info(logTag + " skip upload: pendingAfter=" + nextPending + " < threshold=" + minFlushMinutes);
            return;
        }

        ModMeta meta = resolveModMeta();
        String gameVersion = normalize(Version.buildString(), "unknown");
        PlayerIdResolution idResolution = cachedIdResolution;
        String distinctId = normalize(idResolution.playerId, "unknown");
        String playerName = normalize(resolvePlayerName(), "unknown");
        Log.info(logTag + " send prepared. distinct_id=" + distinctId + ", player_id=" + distinctId + ", player_id_source=" + idResolution.source + ", raw_player_uuid=" + idResolution.rawPlayerUuid + ", raw_platform_uuid=" + idResolution.rawPlatformUuid + ", cached_player_id=" + idResolution.cachedPlayerId + ", player_name=" + playerName + ", game_version=" + gameVersion + ", mod_version=" + meta.modVersion + ", usage_minutes=" + nextPending);

        if(sendUsage(distinctId, playerName, gameVersion, meta.modVersion, nextPending)){
            persistPendingMinutes(0);
            Log.info(logTag + " upload success. pending reset to 0.");
        }else{
            Log.warn(logTag + " upload failed. keep pendingMinutes=" + nextPending + " for next run.");
        }
    }

    private boolean sendUsage(String distinctId, String playerName, String gameVersion, String modVersion, int usageMinutes){
        HttpURLConnection conn = null;
        try{
            String json = "{"
            + "\"api_key\":\"" + escapeJson(apiKey) + "\","
            + "\"event\":\"" + eventName + "\","
            + "\"distinct_id\":\"" + escapeJson(distinctId) + "\","
            + "\"properties\":{"
            + "\"player_id\":\"" + escapeJson(distinctId) + "\","
            + "\"player_name\":\"" + escapeJson(playerName) + "\","
            + "\"game_version\":\"" + escapeJson(gameVersion) + "\","
            + "\"mod_version\":\"" + escapeJson(modVersion) + "\","
            + "\"usage_minutes\":" + usageMinutes
            + "}}";

            conn = (HttpURLConnection)new URL(endpoint).openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(2500);
            conn.setReadTimeout(2500);
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setDoOutput(true);

            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            conn.setFixedLengthStreamingMode(bytes.length);
            try(OutputStream out = conn.getOutputStream()){
                out.write(bytes);
            }

            int code = conn.getResponseCode();
            String body = readBody(code >= 200 && code < 400 ? conn.getInputStream() : conn.getErrorStream());
            boolean ok = code >= 200 && code < 300;
            if(ok){
                Log.info(logTag + " HTTP " + code + " response=" + snippet(body));
            }else{
                Log.warn(logTag + " HTTP " + code + " response=" + snippet(body));
            }
            return ok;
        }catch(Throwable t){
            Log.warn(logTag + " request exception: " + t);
            return false;
        }finally{
            if(conn != null) conn.disconnect();
        }
    }

    private void installShutdownHook(){
        if(shutdownHook != null) return;
        shutdownHook = new Thread(() -> {
            try{
                flush("shutdown-hook");
            }catch(Throwable t){
                Log.warn(logTag + " shutdown hook flush exception", t);
            }
        }, "neon-posthog-shutdown");
        shutdownHook.setDaemon(true);
        try{
            Runtime.getRuntime().addShutdownHook(shutdownHook);
        }catch(Throwable t){
            Log.warn(logTag + " failed to install shutdown hook", t);
        }
    }

    private ModMeta resolveModMeta(){
        String modName = "Neon";
        String modVersion = "unknown";
        if(Vars.mods != null){
            Mods.LoadedMod mod = Vars.mods.getMod(modType);
            if(mod != null && mod.meta != null){
                modName = normalize(mod.meta.name, modName);
                modVersion = normalize(mod.meta.version, modVersion);
            }
        }
        return new ModMeta(modName, modVersion);
    }

    private PlayerIdResolution resolvePlayerId(){
        String cachedPlayerId = normalizeRaw(Core.settings.getString(keyStablePlayerId, ""));
        if(isOnlineUuid(cachedPlayerId)){
            return new PlayerIdResolution(cachedPlayerId, "stable-cache", "", "", cachedPlayerId);
        }

        String rawPlayerUuid = "";
        if(Vars.player != null){
            String playerUuid = Vars.player.uuid();
            rawPlayerUuid = playerUuid == null ? "" : playerUuid.trim();
            if(isOnlineUuid(playerUuid)){
                persistStablePlayerId(rawPlayerUuid);
                return new PlayerIdResolution(rawPlayerUuid, "player.uuid->stable-cache", rawPlayerUuid, "", rawPlayerUuid);
            }
        }
        String platformUuid = Vars.platform == null ? "" : normalizeRaw(Vars.platform.getUUID());
        if(isOnlineUuid(platformUuid)){
            persistStablePlayerId(platformUuid);
            return new PlayerIdResolution(platformUuid, "platform.uuid->stable-cache", rawPlayerUuid, platformUuid, platformUuid);
        }
        return new PlayerIdResolution("", "none", rawPlayerUuid, platformUuid, cachedPlayerId);
    }

    private static String resolvePlayerName(){
        if(Vars.player == null || Vars.player.name == null) return "";
        return Vars.player.name;
    }

    private static boolean isOnlineUuid(String value){
        if(value == null) return false;
        String normalized = value.trim();
        if(normalized.isEmpty()) return false;
        return !"[LOCAL]".equalsIgnoreCase(normalized) && !"LOCAL".equalsIgnoreCase(normalized);
    }

    private static String normalizeRaw(String value){
        return value == null ? "" : value.trim();
    }

    private static String normalize(String value, String fallback){
        if(value == null) return fallback;
        String stripped = Strings.stripColors(value).trim();
        return stripped.isEmpty() ? fallback : stripped;
    }

    private static String escapeJson(String value){
        if(value == null) return "";
        StringBuilder out = new StringBuilder(value.length() + 16);
        for(int i = 0; i < value.length(); i++){
            char c = value.charAt(i);
            switch(c){
                case '\"': out.append("\\\""); break;
                case '\\': out.append("\\\\"); break;
                case '\b': out.append("\\b"); break;
                case '\f': out.append("\\f"); break;
                case '\n': out.append("\\n"); break;
                case '\r': out.append("\\r"); break;
                case '\t': out.append("\\t"); break;
                default:
                    if(c < 0x20){
                        out.append(String.format("\\u%04x", (int)c));
                    }else{
                        out.append(c);
                    }
            }
        }
        return out.toString();
    }

    private static String readBody(InputStream stream){
        if(stream == null) return "";
        try(InputStream in = stream; ByteArrayOutputStream out = new ByteArrayOutputStream(256)){
            byte[] buffer = new byte[1024];
            int len;
            while((len = in.read(buffer)) >= 0){
                out.write(buffer, 0, len);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }catch(Throwable ignored){
            return "";
        }
    }

    private static String snippet(String text){
        if(text == null) return "";
        String oneLine = text.replace('\n', ' ').replace('\r', ' ').trim();
        if(oneLine.length() <= 220) return oneLine;
        return oneLine.substring(0, 220) + "...";
    }

    private void persistPendingMinutes(int value){
        Core.settings.put(keyPendingMinutes, Math.max(0, value));
        try{
            Core.settings.forceSave();
        }catch(Throwable t){
            Log.warn(logTag + " forceSave failed", t);
        }
    }

    private void persistStablePlayerId(String value){
        if(!isOnlineUuid(value)) return;
        String normalized = normalizeRaw(value);
        if(normalized.equals(normalizeRaw(Core.settings.getString(keyStablePlayerId, "")))) return;
        Core.settings.put(keyStablePlayerId, normalized);
        try{
            Core.settings.forceSave();
        }catch(Throwable t){
            Log.warn(logTag + " stable player id forceSave failed", t);
        }
    }

    private static final class ModMeta{
        final String modName;
        final String modVersion;

        ModMeta(String modName, String modVersion){
            this.modName = modName;
            this.modVersion = modVersion;
        }
    }

    private static final class PlayerIdResolution{
        final String playerId;
        final String source;
        final String rawPlayerUuid;
        final String rawPlatformUuid;
        final String cachedPlayerId;

        PlayerIdResolution(String playerId, String source, String rawPlayerUuid, String rawPlatformUuid, String cachedPlayerId){
            this.playerId = playerId;
            this.source = source;
            this.rawPlayerUuid = rawPlayerUuid == null ? "" : rawPlayerUuid;
            this.rawPlatformUuid = rawPlatformUuid == null ? "" : rawPlatformUuid;
            this.cachedPlayerId = cachedPlayerId == null ? "" : cachedPlayerId;
        }
    }
}
