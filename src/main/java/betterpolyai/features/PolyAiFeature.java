package betterpolyai.features;

import arc.Core;
import arc.Events;
import arc.input.KeyBind;
import arc.input.KeyCode;
import arc.util.Interval;
import betterpolyai.BetterPolyAiMod;
import betterpolyai.GithubUpdateCheck;
import mindustry.game.EventType;
import mindustry.gen.Unit;
import mindustry.ui.dialogs.SettingsMenuDialog;

import static mindustry.Vars.player;
import static mindustry.Vars.state;
import static mindustry.Vars.ui;
import static mindustry.Vars.world;

public class PolyAiFeature {

    private static final String keyEnabled = "bpa-enabled";
    private static final String keyMutexWithX = "bpa-mutex-with-x-polyai";

    private static final Interval interval = new Interval(1);
    private static final int idSettings = 0;
    private static final float settingsRefreshTime = 0.25f;

    private static boolean inited;
    private static boolean keybindsRegistered;

    private static boolean enabled;
    private static boolean mutexWithXEnabled;

    private static KeyBind toggleKeybind;
    private static final PlayerPlanBuilderAI builderAI = new PlayerPlanBuilderAI();
    private static final MindustryXBuilderAiProbe xBuilderAiProbe = new MindustryXBuilderAiProbe();

    private static RuntimeState runtimeState = RuntimeState.normal;

    enum RuntimeState {
        normal,
        pausedByManualMove,
        yieldToX
    }

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
        table.checkPref(keyEnabled, false);
        table.checkPref(keyMutexWithX, true);
        if (!BetterPolyAiMod.bekBundled) {
            table.checkPref(GithubUpdateCheck.enabledKey(), true);
            table.checkPref(GithubUpdateCheck.showDialogKey(), true);
        }
        refreshSettings();
    }

    private static void applyDefaults() {
        Core.settings.defaults(keyEnabled, false);
        Core.settings.defaults(keyMutexWithX, true);
    }

    private static void registerKeybinds() {
        if (keybindsRegistered) return;
        keybindsRegistered = true;
        toggleKeybind = KeyBind.add("bpa-toggle", KeyCode.p, "betterpolyai");
    }

    private static void refreshSettings() {
        enabled = Core.settings.getBool(keyEnabled, false);
        mutexWithXEnabled = Core.settings.getBool(keyMutexWithX, true);
    }

    private static void setEnabled(boolean value) {
        enabled = value;
        Core.settings.put(keyEnabled, value);
        if (!value) {
            resetRuntimeState();
        }
        if (ui != null) {
            ui.showInfoFade(Core.bundle.get(value ? "bpa.toast.enabled" : "bpa.toast.disabled"));
        }
    }

    private static void update() {
        if (interval.check(idSettings, settingsRefreshTime)) refreshSettings();

        if (toggleKeybind != null && Core.scene != null && !Core.scene.hasField() && Core.input.keyTap(toggleKeybind)) {
            setEnabled(!enabled);
        }

        if (!enabled) {
            resetRuntimeState();
            return;
        }
        if (state == null || !state.isGame() || world == null || world.isGenerating()) {
            resetRuntimeState();
            return;
        }
        if (player == null || player.dead()) {
            resetRuntimeState();
            return;
        }

        Unit unit = player.unit();
        if (unit == null || !unit.isValid() || !unit.canBuild()) {
            resetRuntimeState();
            return;
        }

        builderAI.unit(unit);
        boolean yieldToX = mutexWithXEnabled && xBuilderAiProbe.isBuilderAiSelected();
        setYieldToX(yieldToX);

        if (runtimeState != RuntimeState.yieldToX) {
            builderAI.updateUnit();
        }
        player.boosting = unit.isShooting;
    }

    static boolean autonomousMovementAllowed() {
        return runtimeState == RuntimeState.normal;
    }

    static void onManualMovePauseChanged(boolean paused) {
        RuntimeState next = paused ? RuntimeState.pausedByManualMove : RuntimeState.normal;
        if (runtimeState == RuntimeState.yieldToX) return;
        if (runtimeState == next) return;

        runtimeState = next;
        showToast(paused ? "bpa.toast.manual-move-paused" : "bpa.toast.manual-move-resumed");
    }

    private static void setYieldToX(boolean value) {
        if (value) {
            if (runtimeState != RuntimeState.yieldToX) {
                runtimeState = RuntimeState.yieldToX;
                showToast("bpa.toast.x-mutex-yield");
            }
            return;
        }

        if (runtimeState == RuntimeState.yieldToX) {
            runtimeState = builderAI.isPausedByManualMove() ? RuntimeState.pausedByManualMove : RuntimeState.normal;
        }
    }

    private static void resetRuntimeState() {
        runtimeState = RuntimeState.normal;
        builderAI.resetAutonomyState();
    }

    private static void showToast(String key) {
        if (ui != null) {
            ui.showInfoFade(Core.bundle.get(key));
        }
    }
}
