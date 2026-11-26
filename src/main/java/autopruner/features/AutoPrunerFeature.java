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
import mindustry.world.blocks.power.BeamNode;
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

    /**
     * 冗余电力节点判定：把电力网络当作无向图，只要删掉某个电力节点不会让所在连通分量分裂，就判为冗余；反复剥离到不动点。
     *
     * 顶点来自世界瓦片扫描（`world.tiles.eachTile` + 中心格），不依赖 Groups.build：Mindustry 自己的编辑器代码里就注明过 "Groups.build can be empty"，用瓦片才是权威来源。边 = Building.getPowerConnections（游戏建 PowerGraph 用的同一套关系：相邻通电建筑 + power.links 连线），再并上显式 power.links 兜底。
     *
     * 可拆的只有 PowerNode / BeamNode / LongPowerNode（含子类），并且必须能通过 validBreak（本队与 derelict 中立建筑可拆，敌对建筑不可拆）。电池、发电机、用电器、电力二极管、电力束塔等一律作为固定顶点保留。
     *
     * 结构性保证：
     * 1. 剩余节点的诱导子图与拆除前保持同样的连通关系，任何一段电网都不会被拆散；
     * 2. 最终保留的每个可拆节点都是当前网络的割点，单独再拆就会断开电网。
     */
    private static Seq<Building> collectRedundantPowerNodesSnapshot() {
        Seq<Building> out = new Seq<>();

        Seq<Building> builds = new Seq<>();
        LongMap<Integer> indices = new LongMap<>();

        world.tiles.eachTile(tile -> {
            if (tile == null || !tile.isCenter() || tile.build == null) return;
            Building build = tile.build;
            if (!build.isValid() || build.power == null) return;
            if (indices.containsKey(build.pos())) return;
            indices.put(build.pos(), builds.size);
            builds.add(build);
        });

        int count = builds.size;
        if (count == 0) return out;

        IntSeq[] adjacency = new IntSeq[count];
        for (int i = 0; i < count; i++) adjacency[i] = new IntSeq();

        Seq<Building> connections = new Seq<>();

        for (int i = 0; i < count; i++) {
            Building build = builds.get(i);

            // 1) 游戏自己的电网关系：相邻通电建筑 + 连线，含二极管方向与绝缘判定
            build.getPowerConnections(connections);
            for (int c = 0; c < connections.size; c++) {
                connectEdge(i, connections.get(c), indices, adjacency);
            }

            // 2) 显式连线兜底：对方即使因为队伍 / 模块原因没进顶点集，也不能丢边
            IntSeq links = build.power.links;
            if (links != null) {
                for (int l = 0; l < links.size; l++) {
                    connectEdge(i, world.build(links.get(l)), indices, adjacency);
                }
            }
        }

        boolean[] removable = new boolean[count];
        boolean anyCandidate = false;

        for (int i = 0; i < count; i++) {
            if (isPrunablePowerNode(builds.get(i))) {
                removable[i] = true;
                anyCandidate = true;
            }
        }
        if (!anyCandidate) return out;

        boolean[] removed = new boolean[count];
        int[] queue = new int[count];
        int[] visitStamp = new int[count];
        int stamp = 0;

        boolean changed = true;
        while (changed) {
            changed = false;
            for (int i = 0; i < count; i++) {
                if (removed[i] || !removable[i]) continue;

                int neighbor = -1;
                IntSeq neighbors = adjacency[i];
                for (int k = 0; k < neighbors.size; k++) {
                    int other = neighbors.get(k);
                    if (removed[other]) continue;
                    neighbor = other;
                    break;
                }

                int componentSize = reachableCount(i, -1, adjacency, removed, queue, visitStamp, ++stamp);
                if (neighbor >= 0 && reachableCount(neighbor, i, adjacency, removed, queue, visitStamp, ++stamp) != componentSize - 1) {
                    continue;
                }

                removed[i] = true;
                out.add(builds.get(i));
                changed = true;
            }
        }

        return out;
    }

    /** 把 from 与 other 之间的边加进无向邻接表，返回新增边数（0 或 1）。 */
    private static int connectEdge(int from, Building other, LongMap<Integer> indices, IntSeq[] adjacency) {
        if (other == null) return 0;
        Integer to = indices.get(other.pos());
        if (to == null || to == from) return 0;
        if (adjacency[from].contains(to)) return 0;
        addNeighbor(adjacency[from], to);
        addNeighbor(adjacency[to], from);
        return 1;
    }

    private static void addNeighbor(IntSeq neighbors, int index) {
        if (!neighbors.contains(index)) neighbors.add(index);
    }

    /**
     * 判断这个建筑是否允许作为拆除目标：必须是输电节点——PowerNode / BeamNode（含子类），且不是发电建筑。
     *
     * 发电豁免很重要：沙盒的 PowerSource（电力源）就是 PowerNode 的子类，只按类型判断会把它当冗余节点删掉。
     * 豁免条件用「只发电、不耗电」（outputsPower && !consumesPower），这样 E 星的 beam-node / beam-tower
     * 虽然带储能（outputsPower 与 consumesPower 都是 true）仍然会被当成可削节点。
     */
    private static boolean isPrunablePowerNode(Building build) {
        if (build == null || !build.isValid() || build.block == null) return false;
        if (!(build.block instanceof PowerNode) && !(build.block instanceof BeamNode)) return false;
        if (build.block.outputsPower && !build.block.consumesPower) return false;
        if (build.getPowerProduction() > 0f) return false;
        if (isProtectedBuilding(build)) return false;
        if (control == null || control.input == null) return false;
        return control.input.validBreak(build.tileX(), build.tileY());
    }

    /**
     * 从 start 出发统计可达顶点数，跳过 removed 与 excluded。
     * visitStamp 用时间戳复用数组，避免每次判定都重新分配 / 清空。
     */
    private static int reachableCount(int start, int excluded, IntSeq[] adjacency, boolean[] removed,
                                      int[] queue, int[] visitStamp, int stamp) {
        int head = 0;
        int tail = 0;
        int total = 0;

        queue[tail++] = start;
        visitStamp[start] = stamp;

        while (head < tail) {
            int index = queue[head++];
            total++;

            IntSeq neighbors = adjacency[index];
            for (int k = 0; k < neighbors.size; k++) {
                int other = neighbors.get(k);
                if (other == excluded || removed[other] || visitStamp[other] == stamp) continue;
                visitStamp[other] = stamp;
                queue[tail++] = other;
            }
        }

        return total;
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
        if (control == null || control.input == null) return false;
        if (build.tile == null) return false;
        if (isProtectedBuilding(build)) return false;
        // validBreak 已经包含队伍判定：本队建筑与 derelict 中立建筑都可通过
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
