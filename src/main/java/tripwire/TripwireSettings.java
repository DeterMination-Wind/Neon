package tripwire;

import arc.Core;
import arc.graphics.Color;
import arc.math.Mathf;
import mindustry.ui.dialogs.SettingsMenuDialog;

public final class TripwireSettings {
    public static final String detectInterval = "tripwire-detect-interval";
    public static final String chatAlert = "tripwire-chat-alert";
    public static final String chatBatchDelay = "tripwire-chat-batch-delay";
    public static final String toastAlert = "tripwire-toast-alert";
    public static final String showFences = "tripwire-show-fences";
    public static final String showMinimap = "tripwire-show-minimap";
    public static final String lineWidth = "tripwire-line-width";
    public static final String iconSize = "tripwire-icon-size";
    public static final String overrideColor = "tripwire-override-color";
    public static final String colorR = "tripwire-color-r";
    public static final String colorG = "tripwire-color-g";
    public static final String colorB = "tripwire-color-b";
    private static final int[] chatBatchDelayMillis = {300, 450, 500, 600, 750, 900, 1000};
    private static final int defaultChatBatchDelayIndex = 2;

    private TripwireSettings() {
    }

    public static void buildSettings(SettingsMenuDialog.SettingsTable table) {
        section(table, "tripwire-section-detection");
        migrateDetectionInterval();
        table.sliderPref(detectInterval, 150, 100, 200, 10, i -> i + "ms");
        table.checkPref(chatAlert, true);
        table.sliderPref(chatBatchDelay, defaultChatBatchDelayIndex, 0, chatBatchDelayMillis.length - 1, 1, TripwireSettings::formatChatBatchDelay);
        table.checkPref(toastAlert, true);

        section(table, "tripwire-section-display");
        table.checkPref(showFences, true);
        table.checkPref(showMinimap, true);
        table.sliderPref(lineWidth, 2, 1, 8, 1, i -> i + "px");
        table.sliderPref(iconSize, 24, 8, 48, 1, i -> i + "px");

        section(table, "tripwire-section-color");
        table.checkPref(overrideColor, false);
        table.sliderPref(colorR, 255, 0, 255, 1, String::valueOf);
        table.sliderPref(colorG, 220, 0, 255, 1, String::valueOf);
        table.sliderPref(colorB, 64, 0, 255, 1, String::valueOf);
    }

    private static void section(SettingsMenuDialog.SettingsTable table, String key) {
        SettingsMenuDialog.SettingsTable.Setting setting = new SettingsMenuDialog.SettingsTable.Setting(key) {
            @Override
            public void add(SettingsMenuDialog.SettingsTable table) {
                table.add(title).color(Color.gray).left().padTop(12f).padBottom(4f).row();
                table.image().color(Color.gray).height(3f).growX().padBottom(4f).row();
            }
        };
        setting.name = null;
        table.pref(setting);
    }

    public static int detectionMillis() {
        int value = Core.settings.getInt(detectInterval, 150);
        if (value < 100) return 150;
        return Mathf.clamp(value, 100, 200);
    }

    private static void migrateDetectionInterval() {
        int value = Core.settings.getInt(detectInterval, 150);
        if (value < 100) Core.settings.put(detectInterval, 150);
    }

    public static boolean chatAlert() {
        return Core.settings.getBool(chatAlert, true);
    }

    public static float chatBatchDelayTicks() {
        return chatBatchDelayMillis[chatBatchDelayIndex()] / 1000f * 60f;
    }

    private static int chatBatchDelayIndex() {
        return Mathf.clamp(Core.settings.getInt(chatBatchDelay, defaultChatBatchDelayIndex), 0, chatBatchDelayMillis.length - 1);
    }

    private static String formatChatBatchDelay(int index) {
        int millis = chatBatchDelayMillis[Mathf.clamp(index, 0, chatBatchDelayMillis.length - 1)];
        if (millis % 1000 == 0) return millis / 1000 + "s";
        if (millis % 100 == 0) return "0." + millis / 100 + "s";
        return "0." + millis / 10 + "s";
    }

    public static boolean toastAlert() {
        return Core.settings.getBool(toastAlert, true);
    }

    public static boolean showFences() {
        return Core.settings.getBool(showFences, true);
    }

    public static boolean showMinimap() {
        return Core.settings.getBool(showMinimap, true);
    }

    public static float lineWidth() {
        return Core.settings.getInt(lineWidth, 2);
    }

    public static float iconSize() {
        return Core.settings.getInt(iconSize, 24);
    }

    public static Color configuredColor(Color fallback) {
        if (!Core.settings.getBool(overrideColor, false)) return fallback;
        return new Color(
            Core.settings.getInt(colorR, 255) / 255f,
            Core.settings.getInt(colorG, 220) / 255f,
            Core.settings.getInt(colorB, 64) / 255f,
            fallback.a
        );
    }
}
