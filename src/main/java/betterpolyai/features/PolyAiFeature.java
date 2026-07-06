package betterpolyai.features;

import arc.Core;
import arc.Events;
import arc.input.KeyBind;
import arc.input.KeyCode;
import arc.util.Interval;
import betterpolyai.BetterPolyAiMod;
import betterpolyai.GithubUpdateCheck;
import bektools.ui.RbmStyle;
import mindustry.game.EventType;
import mindustry.gen.Unit;
import mindustry.ui.dialogs.SettingsMenuDialog;

import static mindustry.Vars.player;
import static mindustry.Vars.state;
import static mindustry.Vars.ui;
import static mindustry.Vars.world;

public class PolyAiFeature {

    private static final String keyEnabled = "bpa-enabled";
    private static final String keyBuildGapTiles = "bpa-build-gap-tiles";
    private static final int defaultBuildGapTiles = 0;
    private static final int minBuildGapTiles = 0;
    private static final int maxBuildGapTiles = 30;

    private static final Interval interval = new Interval(1);
    private static final int idSettings = 0;
    private static final float settingsRefreshTime = 0.25f;

    private static boolean inited;
    private static boolean keybindsRegistered;

    private static boolean enabled;

    private static KeyBind toggleKeybind;
    private static final PlayerPlanBuilderAI builderAI = new PlayerPlanBuilderAI();

    public static void init() {
        if (inited) return;
        inited = true;

        applyDefaults();

        Events.on(EventType.ClientLoadEvent.class, e -> {
            registerKeybinds();
            refreshSettings();
        });

        Events.run(EventType.Trigger.update, PolyAiFeature::update);
    }

    public static void buildSettings(SettingsMenuDialog.SettingsTable table) {
        if (!BetterPolyAiMod.bekBundled) {
            table.pref(new RbmStyle.HeaderSetting(Core.bundle.get("settings.betterpolyai", "Better PolyAI"), mindustry.gen.Icon.units));
        }
        table.pref(new RbmStyle.IconCheckSetting(keyEnabled, false, mindustry.gen.Icon.eyeSmall, null));
        table.pref(new RbmStyle.IconTextSetting(keyBuildGapTiles, String.valueOf(defaultBuildGapTiles), mindustry.gen.Icon.gridSmall, null));
        if (!BetterPolyAiMod.bekBundled) {
            table.pref(new RbmStyle.SubHeaderSetting("Update"));
            table.pref(new RbmStyle.IconCheckSetting(GithubUpdateCheck.enabledKey(), true, mindustry.gen.Icon.refreshSmall, null));
            table.pref(new RbmStyle.IconCheckSetting(GithubUpdateCheck.showDialogKey(), true, mindustry.gen.Icon.infoSmall, null));
        }
        refreshSettings();
    }

    private static void applyDefaults() {
        Core.settings.defaults(keyEnabled, false);
        Core.settings.defaults(keyBuildGapTiles, String.valueOf(defaultBuildGapTiles));
    }

    private static void registerKeybinds() {
        if (keybindsRegistered) return;
        keybindsRegistered = true;
        toggleKeybind = KeyBind.add("bpa-toggle", KeyCode.p, "betterpolyai");
    }

    private static void refreshSettings() {
        enabled = Core.settings.getBool(keyEnabled, false);
        int gapTiles = parseGapTiles(Core.settings.getString(keyBuildGapTiles, String.valueOf(defaultBuildGapTiles)));
        PlayerPlanBuilderAI.setBuildGapTiles(gapTiles);
    }

    private static int parseGapTiles(String rawValue) {
        int parsed = defaultBuildGapTiles;
        if (rawValue != null) {
            try {
                parsed = Integer.parseInt(rawValue.trim());
            } catch (NumberFormatException ignored) {
                parsed = defaultBuildGapTiles;
            }
        }

        if (parsed < minBuildGapTiles) return minBuildGapTiles;
        if (parsed > maxBuildGapTiles) return maxBuildGapTiles;
        return parsed;
    }

    private static void setEnabled(boolean value) {
        enabled = value;
        Core.settings.put(keyEnabled, value);
        if (ui != null) {
            ui.showInfoFade(Core.bundle.get(value ? "bpa.toast.enabled" : "bpa.toast.disabled"));
        }
    }

    private static void update() {
        if (interval.check(idSettings, settingsRefreshTime)) refreshSettings();

        if (toggleKeybind != null && Core.scene != null && !Core.scene.hasField() && Core.input.keyTap(toggleKeybind)) {
            setEnabled(!enabled);
        }

        if (!enabled) return;
        if (state == null || !state.isGame() || world == null || world.isGenerating()) return;
        if (player == null || player.dead()) return;

        Unit unit = player.unit();
        if (unit == null || !unit.isValid() || !unit.canBuild()) return;

        builderAI.unit(unit);
        builderAI.updateUnit();
        player.boosting = unit.isShooting;
    }
}
