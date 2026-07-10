package mdtxcompat;

import arc.Core;
import arc.Settings;
import arc.util.Log;

import java.util.ArrayList;
import java.util.List;

public final class OverlaySettingsCompat {
    private static final String nativePrefix = "overlayUI.";
    private static final String embeddedPrefix = "neoncompat.overlayUI.";

    private OverlaySettingsCompat() {
    }

    public static String nativeKey(String windowName) {
        return nativePrefix + windowName;
    }

    public static String embeddedKey(String windowName) {
        return embeddedPrefix + windowName;
    }

    public static boolean hasStoredWindowState(String windowName) {
        Settings settings = Core.settings;
        return settings != null
            && (settings.has(nativeKey(windowName)) || settings.has(embeddedKey(windowName)));
    }

    public static boolean migrateLegacyEmbeddedWindow(String windowName) {
        Settings settings = Core.settings;
        if (settings == null) return false;

        String nativeKey = nativeKey(windowName);
        if (!settings.has(nativeKey)) return false;

        Object raw = settings.get(nativeKey, (Object)null);
        if (raw instanceof byte[]) return false;

        if (raw instanceof String) {
            String embeddedKey = embeddedKey(windowName);
            if (!settings.has(embeddedKey)) {
                settings.put(embeddedKey, raw);
            }
        }
        settings.remove(nativeKey);
        Log.info("Neon OverlayUI settings: migrated incompatible embedded state for '" + windowName + "'.");
        return true;
    }

    public static int sanitizeNativeWindowSettings() {
        Settings settings = Core.settings;
        if (settings == null) return -1;

        List<String> keys = new ArrayList<>();
        for (String key : settings.keys()) {
            if (key != null && key.startsWith(nativePrefix)) keys.add(key);
        }

        int removed = 0;
        int preserved = 0;
        for (String key : keys) {
            Object raw = settings.get(key, (Object)null);
            if (raw instanceof byte[]) continue;

            if (raw instanceof String) {
                String windowName = key.substring(nativePrefix.length());
                String embeddedKey = embeddedKey(windowName);
                if (!settings.has(embeddedKey)) {
                    settings.put(embeddedKey, raw);
                    preserved++;
                }
            }
            settings.remove(key);
            removed++;
        }

        if (removed > 0) {
            Log.info("Neon OverlayUI settings: repaired " + removed
                + " incompatible native setting(s); preserved " + preserved + " embedded JSON state(s).");
        }
        return removed;
    }
}
