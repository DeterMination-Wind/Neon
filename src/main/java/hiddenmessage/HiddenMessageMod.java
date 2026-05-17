package hiddenmessage;

import arc.Core;
import arc.Events;
import arc.util.Log;
import bektools.profiler.NeonProfiler;
import mindustry.Vars;
import mindustry.game.EventType;
import mindustry.mod.Mod;

import java.lang.reflect.Method;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

public class HiddenMessageMod extends Mod {
    public static boolean bekBundled = false;

    private static final String PACKET_HELLO = "hm.hello";
    private static final String PACKET_CHALLENGE = "hm.challenge";
    private static final String PACKET_PROVE = "hm.prove";
    private static final String PACKET_OK = "hm.ok";
    private static final String PACKET_ERROR = "hm.error";
    private static final String PACKET_DELIVER = "hm.deliver";

    private static final int PROTOCOL_VERSION = 1;
    private static final String MOD_VERSION = "0.1.0";
    private static final String SHARED_SECRET = "hm-v1-default-secret";
    private static final long HELLO_INTERVAL_MS = 25_000L;
    private static final long RETRY_INTERVAL_MS = 6_000L;

    private static final Random random = new Random();
    private static final char[] nonceChars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789".toCharArray();

    private static boolean hooksInstalled = false;
    private static boolean sendMethodSearched = false;
    private static Method serverPacketReliableMethod = null;

    private long lastHelloAt = 0L;
    private String clientNonce = "";
    private boolean verified = false;

    public HiddenMessageMod() {
        Events.on(EventType.ClientLoadEvent.class, e -> installHooks());
    }

    @Override
    public void init() {
        installHooks();
    }

    private void installHooks() {
        if (hooksInstalled || Vars.headless || Vars.netClient == null) return;
        hooksInstalled = true;

        Vars.netClient.addPacketHandler(PACKET_CHALLENGE, this::handleChallenge);
        Vars.netClient.addPacketHandler(PACKET_OK, this::handleVerifyOk);
        Vars.netClient.addPacketHandler(PACKET_ERROR, this::handleError);
        Vars.netClient.addPacketHandler(PACKET_DELIVER, this::handleDeliver);

        Events.on(EventType.WorldLoadEvent.class, e -> {
            resetState();
            sendHello();
        });
        Events.on(EventType.ResetEvent.class, e -> resetState());

        Events.run(EventType.Trigger.update, () -> {
            try(NeonProfiler.Scope ignored = NeonProfiler.timeRoot("HM", "Update", "update", NeonProfiler.threadMain)){
            if (Vars.headless || !Vars.net.client() || Vars.player == null) return;
            long now = System.currentTimeMillis();
            long interval = verified ? HELLO_INTERVAL_MS : RETRY_INTERVAL_MS;
            if (now - lastHelloAt >= interval) {
                sendHello();
            }
            }
        });
    }

    private void resetState() {
        verified = false;
        clientNonce = "";
        lastHelloAt = 0L;
    }

    private void sendHello() {
        if (Vars.player == null || !Vars.net.client()) return;
        clientNonce = randomNonce(24);
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("pv", String.valueOf(PROTOCOL_VERSION));
        payload.put("mv", MOD_VERSION);
        payload.put("cn", clientNonce);

        if (sendPacketToServer(PACKET_HELLO, encodePayload(payload))) {
            lastHelloAt = System.currentTimeMillis();
        }
    }

    private void handleChallenge(String raw) {
        Map<String, String> payload = decodePayload(raw);
        int pv = parseInt(payload.get("pv"), -1);
        String serverNonce = payload.get("sn");
        if (pv != PROTOCOL_VERSION || serverNonce == null || serverNonce.isEmpty()) {
            return;
        }
        if (Vars.player == null || Vars.player.uuid() == null || Vars.player.uuid().isEmpty()) {
            return;
        }
        if (clientNonce.isEmpty()) {
            sendHello();
            return;
        }

        String signature = expectedSignature(
            Vars.player.uuid(),
            PROTOCOL_VERSION,
            MOD_VERSION,
            clientNonce,
            serverNonce
        );
        Map<String, String> prove = new LinkedHashMap<>();
        prove.put("pv", String.valueOf(PROTOCOL_VERSION));
        prove.put("mv", MOD_VERSION);
        prove.put("sn", serverNonce);
        prove.put("sig", signature);
        sendPacketToServer(PACKET_PROVE, encodePayload(prove));
    }

    private void handleVerifyOk(String raw) {
        verified = true;
    }

    private void handleError(String raw) {
        verified = false;
        Map<String, String> payload = decodePayload(raw);
        String code = payload.get("code");
        if (code != null && !code.isEmpty()) {
            Log.warn("hiddenMessage handshake error: " + code);
        }
    }

    private void handleDeliver(String raw) {
        Map<String, String> payload = decodePayload(raw);
        String shortId = payload.getOrDefault("sid", "???");
        String senderName = payload.getOrDefault("name", "unknown");
        String message = payload.getOrDefault("msg", "");
        if (message.isEmpty()) return;

        String text = "[accent][HM增强][] 来自 [gray]" + shortId + "[] " + senderName + "[white]: " + message;
        Core.app.post(() -> {
            if (Vars.ui != null) {
                Vars.ui.showInfoToast(text, 4f);
            }
        });
    }

    private static String randomNonce(int len) {
        StringBuilder out = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            out.append(nonceChars[random.nextInt(nonceChars.length)]);
        }
        return out.toString();
    }

    private static String expectedSignature(String uuid, int protocol, String modVersion, String clientNonce, String serverNonce) {
        String base = SHARED_SECRET + "|" + uuid + "|" + protocol + "|" + modVersion + "|" + clientNonce + "|" + serverNonce;
        return sha256Hex(base);
    }

    private static String sha256Hex(String text) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                int v = b & 0xff;
                out.append(Character.forDigit((v >>> 4) & 0x0f, 16));
                out.append(Character.forDigit(v & 0x0f, 16));
            }
            return out.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private static int parseInt(String value, int fallback) {
        if (value == null) return fallback;
        try {
            return Integer.parseInt(value);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static String encodePayload(Map<String, String> map) {
        StringBuilder out = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> e : map.entrySet()) {
            if (!first) out.append('&');
            first = false;
            out.append(e.getKey()).append('=').append(urlEncode(e.getValue()));
        }
        return out.toString();
    }

    private static Map<String, String> decodePayload(String raw) {
        Map<String, String> out = new LinkedHashMap<>();
        if (raw == null || raw.isEmpty()) return out;
        String[] parts = raw.split("&");
        for (String part : parts) {
            int eq = part.indexOf('=');
            if (eq <= 0 || eq == part.length() - 1) continue;
            String key = part.substring(0, eq);
            String value = part.substring(eq + 1);
            out.put(key, urlDecode(value));
        }
        return out;
    }

    private static String urlEncode(String s) {
        try {
            return URLEncoder.encode(s, StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            return s;
        }
    }

    private static String urlDecode(String s) {
        try {
            return URLDecoder.decode(s, StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            return s;
        }
    }

    private static boolean sendPacketToServer(String type, String payload) {
        Method method = resolveServerPacketMethod();
        if (method == null) return false;

        try {
            if (method.getParameterTypes().length == 2) {
                method.invoke(null, type, payload);
                return true;
            }
            if (method.getParameterTypes().length == 3) {
                Class<?> firstType = method.getParameterTypes()[0];
                Object firstArg = null;
                if (Vars.player != null && firstType.isInstance(Vars.player)) {
                    firstArg = Vars.player;
                } else if (Vars.player != null && Vars.player.con != null && firstType.isInstance(Vars.player.con)) {
                    firstArg = Vars.player.con;
                }
                if (firstArg == null) return false;
                method.invoke(null, firstArg, type, payload);
                return true;
            }
        } catch (Exception e) {
            Log.err("hiddenMessage send packet failed", e);
        }
        return false;
    }

    private static Method resolveServerPacketMethod() {
        if (sendMethodSearched) return serverPacketReliableMethod;
        sendMethodSearched = true;

        try {
            for (Method m : Class.forName("mindustry.gen.Call").getMethods()) {
                if (!m.getName().equals("serverPacketReliable")) continue;
                Class<?>[] p = m.getParameterTypes();
                if (p.length == 2 && p[0] == String.class && p[1] == String.class) {
                    m.setAccessible(true);
                    serverPacketReliableMethod = m;
                    break;
                }
                if (p.length == 3 && p[1] == String.class && p[2] == String.class) {
                    m.setAccessible(true);
                    serverPacketReliableMethod = m;
                    break;
                }
            }
        } catch (Exception e) {
            Log.err("hiddenMessage cannot resolve Call.serverPacketReliable", e);
        }
        return serverPacketReliableMethod;
    }
}
