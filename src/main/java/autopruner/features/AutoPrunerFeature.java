package autopruner.features;

import arc.Core;
import arc.Events;
import arc.input.KeyBind;
import arc.input.KeyCode;
import arc.scene.ui.TextButton;
import arc.struct.IntSeq;
import arc.struct.IntSet;
import arc.struct.LongMap;
import arc.struct.Seq;
import arc.util.Align;
import arc.util.Interval;
import arc.util.Time;
import autopruner.AutoPrunerMod;
import mindustry.game.EventType;
import mindustry.gen.Building;
import mindustry.gen.Groups;
import mindustry.ui.dialogs.SettingsMenuDialog;
import mindustry.world.blocks.power.PowerNode;
import mindustry.world.blocks.storage.CoreBlock;

import static mindustry.Vars.control;
import static mindustry.Vars.mobile;
import static mindustry.Vars.player;
import static mindustry.Vars.state;
import static mindustry.Vars.ui;
import static mindustry.Vars.world;

public class AutoPrunerFeature {

    private static final String keyEnabled = "ap-enabled";
    private static final String keyPruneNodeEnabled = "ap-prune-node-enabled";
    private static final String keyRecentRemoveEnabled = "ap-recent-remove-enabled";
    private static final String keyMinutes = "ap-minutes";
    private static final String keyTimeMode = "ap-time-mode";
    private static final String keyProtectCore = "ap-protect-core";
    private static final String keyToastEnabled = "ap-toast-enabled";

    private static final String modeStartWindow = "start-window";
    private static final String modeRecentWindow = "recent-window";

    private static final float settingsRefreshInterval = 0.25f;

    private static boolean inited;
    private static boolean keybindsRegistered;

    private static KeyBind pruneNodesKey;
    private static KeyBind removeByTimeKey;

    private static boolean enabled;
    private static boolean pruneNodeEnabled;
    private static boolean recentRemoveEnabled;
    private static int minutes;
    private static String timeMode = modeStartWindow;
    private static boolean protectCore;
    private static boolean toastEnabled;

    private static long worldStartMillis;
    private static final LongMap<Long> placedAtByPos = new LongMap<>();

    private static final Interval interval = new Interval(1);

    public static void init() {
        if (inited) return;
        inited = true;

        applyDefaults();
        refreshSettings();

        Events.on(EventType.ClientLoadEvent.class, e -> {
            registerKeybinds();
            refreshSettings();
        });

        Events.on(EventType.WorldLoadEvent.class, e -> {
            placedAtByPos.clear();
            worldStartMillis = Time.millis();
        });

        Events.on(EventType.BlockBuildEndEvent.class, AutoPrunerFeature::trackPlacement);
        Events.run(EventType.Trigger.update, AutoPrunerFeature::update);
    }

    public static void buildSettings(SettingsMenuDialog.SettingsTable table) {
        table.checkPref(keyEnabled, defaultEnabled());
        table.checkPref(keyPruneNodeEnabled, true);
        table.checkPref(keyRecentRemoveEnabled, true);
        table.sliderPref(keyMinutes, 5, 1, 120, 1, value -> value + Core.bundle.get("ap.minutes.short", "m"));

        table.pref(new SettingsMenuDialog.SettingsTable.Setting(keyTimeMode) {
            @Override
            public void add(SettingsMenuDialog.SettingsTable t) {
                TextButton b = t.button("", () -> {
                    toggleTimeMode();
                    refreshSettings();
                }).growX().margin(14f).pad(6f).center().get();
                b.getLabel().setAlignment(Align.center);
                b.getLabelCell().growX().align(Align.center);
                b.update(() -> b.setText(title + ": [accent]" + modeDisplayName(readTimeMode()) + "[]"));
                t.row();
            }
        });

        table.checkPref(keyProtectCore, true);
        table.checkPref(keyToastEnabled, true);

        refreshSettings();
    }

    private static void applyDefaults() {
        Core.settings.defaults(keyEnabled, defaultEnabled());
        Core.settings.defaults(keyPruneNodeEnabled, true);
        Core.settings.defaults(keyRecentRemoveEnabled, true);
        Core.settings.defaults(keyMinutes, 5);
        Core.settings.defaults(keyTimeMode, modeStartWindow);
        Core.settings.defaults(keyProtectCore, true);
        Core.settings.defaults(keyToastEnabled, true);
    }

    private static void registerKeybinds() {
        if (keybindsRegistered) return;
        keybindsRegistered = true;
        pruneNodesKey = KeyBind.add("ap-prune-nodes", KeyCode.j, "autopruner");
        removeByTimeKey = KeyBind.add("ap-remove-by-time", KeyCode.k, "autopruner");
    }

    private static void refreshSettings() {
        enabled = Core.settings.getBool(keyEnabled, defaultEnabled());
        pruneNodeEnabled = Core.settings.getBool(keyPruneNodeEnabled, true);
        recentRemoveEnabled = Core.settings.getBool(keyRecentRemoveEnabled, true);
        minutes = Math.max(1, Math.min(120, Core.settings.getInt(keyMinutes, 5)));
        timeMode = readTimeMode();
        protectCore = Core.settings.getBool(keyProtectCore, true);
        toastEnabled = Core.settings.getBool(keyToastEnabled, true);
    }

    private static void update() {
        if (interval.check(0, settingsRefreshInterval)) refreshSettings();

        if (!enabled || mobile || !isGameReady()) return;
        if (!player.isBuilder()) return;
        if (isInputBlocked()) return;

        if (pruneNodeEnabled && pruneNodesKey != null && Core.input.keyTap(pruneNodesKey)) {
            runPruneRedundantPowerNodes();
        }

        if (recentRemoveEnabled && removeByTimeKey != null && Core.input.keyTap(removeByTimeKey)) {
            runRemoveByTimeRule();
        }
    }

    private static boolean isGameReady() {
        return state != null && state.isGame() && world != null && !world.isGenerating() && player != null && control != null && control.input != null;
    }

    private static boolean isInputBlocked() {
        if (ui != null) {
            if (ui.chatfrag != null && ui.chatfrag.shown()) return true;
            if (ui.consolefrag != null && ui.consolefrag.shown()) return true;
        }
        return Core.scene != null && (Core.scene.hasDialog() || Core.scene.hasField() || Core.scene.hasKeyboard());
    }

    private static void runPruneRedundantPowerNodes() {
        if (!isGameReady()) return;

        Seq<Building> targets = collectRedundantPowerNodesSnapshot();
        if (targets.isEmpty()) {
            showToast(Core.bundle.get("ap.toast.prune.none"));
            return;
        }

        int removed = 0;
        int skippedProtected = 0;
        int skippedInvalid = 0;

        for (int i = 0; i < targets.size; i++) {
            Building build = targets.get(i);
            if (build == null || !build.isValid()) {
                skippedInvalid++;
                continue;
            }

            if (!canAutoBreak(build)) {
                if (isProtectedBuilding(build)) {
                    skippedProtected++;
                } else {
                    skippedInvalid++;
                }
                continue;
            }

            control.input.breakBlock(build.tileX(), build.tileY());
            removed++;
        }

        showToast(Core.bundle.format("ap.toast.prune.summary", removed, skippedProtected, skippedInvalid));
    }

    private static Seq<Building> collectRedundantPowerNodesSnapshot() {
        Seq<Building> out = new Seq<>();
        IntSet added = new IntSet();

        Groups.build.each(build -> {
            if (build == null || !build.isValid() || build.team != player.team()) return;
            if (!(build.block instanceof PowerNode)) return;
            if (build.power == null) return;

            IntSeq links = build.power.links;
            if (links == null || links.size != 1) return;

            Building other = world.build(links.first());
            if (other == null || !other.isValid() || other.power == null || other.power.links == null) return;
            if (other.power.links.size >= 2) return;

            if (added.add(build.pos())) {
                out.add(build);
            }
        });

        return out;
    }

    private static void runRemoveByTimeRule() {
        if (!isGameReady()) return;

        long now = Time.millis();
        Seq<Building> targets = collectTargetsByTimeMode(now);

        if (targets.isEmpty()) {
            showToast(Core.bundle.get("ap.toast.time.none"));
            return;
        }

        int removed = 0;
        int skippedProtected = 0;
        int skippedInvalid = 0;

        for (int i = 0; i < targets.size; i++) {
            Building build = targets.get(i);
            if (build == null || !build.isValid()) {
                skippedInvalid++;
                continue;
            }

            if (!canAutoBreak(build)) {
                if (isProtectedBuilding(build)) {
                    skippedProtected++;
                } else {
                    skippedInvalid++;
                }
                continue;
            }

            control.input.breakBlock(build.tileX(), build.tileY());
            removed++;
        }

        showToast(Core.bundle.format("ap.toast.time.summary", removed, skippedProtected, skippedInvalid, modeDisplayName(timeMode)));
    }

    private static Seq<Building> collectTargetsByTimeMode(long nowMillis) {
        Seq<Building> out = new Seq<>();
        IntSet added = new IntSet();

        Groups.build.each(build -> {
            if (build == null || !build.isValid() || build.team != player.team()) return;

            Long placedAt = placedAtByPos.get(build.pos());
            if (placedAt == null) return;
            if (!matchesTimeWindow(placedAt, nowMillis)) return;

            if (added.add(build.pos())) {
                out.add(build);
            }
        });

        return out;
    }

    private static void trackPlacement(EventType.BlockBuildEndEvent event) {
        if (event == null || event.tile == null) return;

        int tilePos = event.tile.pos();
        if (event.breaking) {
            placedAtByPos.remove(tilePos);
            return;
        }

        if (!isGameReady()) return;
        if (event.team != player.team()) return;

        long now = Time.millis();
        Building build = event.tile.build;
        if (build != null && build.isValid()) {
            placedAtByPos.put(build.pos(), now);
            if (build.pos() != tilePos) {
                placedAtByPos.remove(tilePos);
            }
        } else {
            placedAtByPos.put(tilePos, now);
        }
    }

    private static boolean canAutoBreak(Building build) {
        if (build == null || !build.isValid()) return false;
        if (build.team != player.team()) return false;
        if (control == null || control.input == null) return false;
        if (build.tile == null) return false;
        if (isProtectedBuilding(build)) return false;
        return control.input.validBreak(build.tileX(), build.tileY());
    }

    private static boolean isProtectedBuilding(Building build) {
        if (!protectCore || build == null || build.tile == null || build.block == null) return false;
        if (build.block instanceof CoreBlock) return true;
        if (build.block.privileged) return true;
        return !build.tile.breakable() || !build.block.canBreak(build.tile);
    }

    private static boolean matchesTimeWindow(long placedAtMillis, long nowMillis) {
        long window = Math.max(1L, minutes) * 60_000L;
        if (modeStartWindow.equals(timeMode)) {
            if (worldStartMillis <= 0L) return false;
            long end = worldStartMillis + window;
            return placedAtMillis >= worldStartMillis && placedAtMillis <= end;
        }
        return placedAtMillis >= nowMillis - window;
    }

    private static String readTimeMode() {
        String mode = Core.settings.getString(keyTimeMode, modeStartWindow);
        if (!modeStartWindow.equals(mode) && !modeRecentWindow.equals(mode)) {
            mode = modeStartWindow;
            Core.settings.put(keyTimeMode, mode);
        }
        return mode;
    }

    private static void toggleTimeMode() {
        String next = modeStartWindow.equals(readTimeMode()) ? modeRecentWindow : modeStartWindow;
        Core.settings.put(keyTimeMode, next);
    }

    private static String modeDisplayName(String mode) {
        if (modeRecentWindow.equals(mode)) return Core.bundle.get("ap.mode.recent-window");
        return Core.bundle.get("ap.mode.start-window");
    }

    private static boolean defaultEnabled() {
        return !AutoPrunerMod.bekBundled;
    }

    private static void showToast(String text) {
        if (!toastEnabled || ui == null || text == null || text.isEmpty()) return;
        ui.showInfoFade(text);
    }
}
