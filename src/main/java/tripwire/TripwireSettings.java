package tripwire;

import arc.Core;
import arc.graphics.Color;
import arc.math.Mathf;
import bektools.ui.RbmStyle;
import mindustry.gen.Icon;
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
    private static final int[] chatBatchDelayMillis = {
        100, 500, 1000, 1500, 2000, 2500, 3000, 3500, 4000, 4500,
        5000, 5500, 6000, 6500, 7000, 7500, 8000, 8500, 9000, 9500, 10000
    };
    private static final int defaultChatBatchDelayIndex = 1;

    private TripwireSettings() {
    }

    public static void buildSettings(SettingsMenuDialog.SettingsTable table) {
        if (!TripwireMod.bekBundled) {
            table.pref(new RbmStyle.HeaderSetting(Core.bundle.get("settings.tripwire", "Tripwire"), Icon.map));
        }
        table.pref(new RbmStyle.SubHeaderSetting("Detection"));
        migrateDetectionInterval();
        table.pref(new RbmStyle.IconSliderSetting(detectInterval, 150, 100, 200, 10, Icon.refreshSmall, i -> i + "ms", null));
        table.pref(new RbmStyle.IconCheckSetting(chatAlert, true, Icon.chatSmall, null));
        table.pref(new RbmStyle.IconSliderSetting(chatBatchDelay, defaultChatBatchDelayIndex, 0, chatBatchDelayMillis.length - 1, 1, Icon.refreshSmall, TripwireSettings::formatChatBatchDelay, null));
        table.pref(new RbmStyle.IconCheckSetting(toastAlert, true, Icon.infoSmall, null));

        table.pref(new RbmStyle.SubHeaderSetting("Display"));
        table.pref(new RbmStyle.IconCheckSetting(showFences, true, Icon.gridSmall, null));
        table.pref(new RbmStyle.IconCheckSetting(showMinimap, true, Icon.mapSmall, null));
        table.pref(new RbmStyle.IconSliderSetting(lineWidth, 2, 1, 8, 1, Icon.pencilSmall, i -> i + "px", null));
        table.pref(new RbmStyle.IconSliderSetting(iconSize, 24, 8, 48, 1, Icon.resizeSmall, i -> i + "px", null));

        table.pref(new RbmStyle.SubHeaderSetting("Color"));
        table.pref(new RbmStyle.IconCheckSetting(overrideColor, false, Icon.effectSmall, null));
        table.pref(new RbmStyle.IconSliderSetting(colorR, 255, 0, 255, 1, Icon.effectSmall, String::valueOf, null));
        table.pref(new RbmStyle.IconSliderSetting(colorG, 220, 0, 255, 1, Icon.effectSmall, String::valueOf, null));
        table.pref(new RbmStyle.IconSliderSetting(colorB, 64, 0, 255, 1, Icon.effectSmall, String::valueOf, null));
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
        return (millis / 1000) + "." + ((millis % 1000) / 100) + "s";
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
