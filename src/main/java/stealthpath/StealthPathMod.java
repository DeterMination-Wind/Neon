package stealthpath;

import arc.Core;
import arc.Events;
import arc.func.Cons;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.input.KeyCode;
import arc.input.KeyBind;
import arc.math.Mathf;
import arc.scene.event.InputEvent;
import arc.scene.event.InputListener;
import arc.scene.style.Drawable;
import arc.scene.ui.CheckBox;
import arc.scene.ui.Dialog;
import arc.scene.ui.Label;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.Slider;
import arc.scene.ui.TextField;
import arc.scene.ui.TextButton;
import arc.scene.event.Touchable;
import arc.scene.ui.layout.Table;
import arc.struct.IntSeq;
import arc.struct.Seq;
import arc.math.geom.Vec2;
import arc.util.Align;
import arc.util.Strings;
import arc.util.Time;
import mindustry.game.EventType.*;
import mindustry.game.Team;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Icon;
import mindustry.gen.Tex;
import mindustry.gen.Unit;
import mindustry.gen.Building;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.ui.Fonts;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.ui.dialogs.SettingsMenuDialog;
import mindustry.world.Tile;
import mindustry.world.Block;
import mindustry.world.blocks.defense.ForceProjector;
import mindustry.world.blocks.defense.turrets.Turret;
import mindustry.world.blocks.power.PowerGenerator;
import mindustry.world.blocks.storage.CoreBlock;
import mindustry.world.meta.BuildVisibility;

import java.util.Arrays;
import java.util.Locale;
import java.util.PriorityQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static mindustry.Vars.content;
import static mindustry.Vars.control;
import static mindustry.Vars.mobile;
import static mindustry.Vars.player;
import static mindustry.Vars.renderer;
import static mindustry.Vars.state;
import static mindustry.Vars.tilesize;
import static mindustry.Vars.ui;
import static mindustry.Vars.world;

public class StealthPathMod extends mindustry.mod.Mod{
    /** When true, this mod is running as a bundled component inside BEK-Tools. */
    public static boolean bekBundled = false;


    private static final String keyEnabled = "sp-enabled";
    private static final String keyProMode = "sp-pro-mode";
    private static final String keyTargetMode = "sp-target-mode";
    private static final String keyTargetBlock = "sp-target-block";
    private static final String keyPathDuration = "sp-path-duration";
    private static final String keyPathWidth = "sp-path-width";
    private static final String keyPathAlpha = "sp-path-alpha";
    private static final String keyShowDamageText = "sp-show-damage-text";
    private static final String keyDamageTextScale = "sp-damage-text-scale";
    private static final String keyDamageLabelAtEnd = "sp-damage-label-at-end";
    private static final String keyDamageTextOffsetScale = "sp-damage-offset-scale";
    private static final String keyShowEndpoints = "sp-show-endpoints";
    private static final String keyStartDotScale = "sp-start-dot-scale";
    private static final String keyEndDotScale = "sp-end-dot-scale";
    private static final String keyPreviewRefresh = "sp-preview-refresh";
    private static final String keyThreatMode = "sp-threat-mode";
    private static final String keyGenPathColor = "sp-arrow-color";
    private static final String keyMousePathColor = "sp-mouse-path-color";
    private static final String keyGenClusterMaxPaths = "sp-gencluster-maxpaths";
    private static final String keyGenClusterStartFromCore = "sp-gencluster-start-core";
    private static final String keyGenClusterMinSize = "sp-gencluster-minsize";
    private static final String keyAutoSafeDamageThreshold = "sp-auto-safe-damage-threshold";
    private static final String keyAutoColorDead = "sp-auto-color-dead";
    private static final String keyAutoColorWarn = "sp-auto-color-warn";
    private static final String keyAutoColorSafe = "sp-auto-color-safe";
    private static final String keyAutoMoveEnabled = "sp-auto-move-enabled";
    private static final String keyAutoThreatPaddingMax = "sp-auto-threat-padding-max";
    private static final String keyShowToasts = "sp-show-toasts";
    private static final String keyAutoClusterSplitTiles = "sp-auto-cluster-split-tiles";
    private static final String keyFormationInflatePct = "sp-formation-inflate-pct";
    private static final String keySafeCorridorBiasPct = "sp-safe-corridor-bias-pct";
    private static final String keyComputeSafeDistance = "sp-compute-safe-distance";
    private static final String keyRtsMaxWaypoints = "sp-rts-max-waypoints";
    private static final String keyRtsUpdateInterval = "sp-rts-update-interval";
    private static final String keyRtsCommandSpacing = "sp-rts-command-spacing";
    private static final String keyAutoBatchEnabled = "sp-auto-batch-enabled";
    private static final String keyAutoBatchSizePct = "sp-auto-batch-size-pct";
    private static final String keyAutoBatchDelayPct = "sp-auto-batch-delay-pct";
    private static final String keyAutoSlowMultiplier = "sp-auto-slow-multiplier";
    private static final String keyPassableCacheEntries = "sp-passable-cache-entries";
    private static final String keyGenClusterLinkDistTiles = "sp-gencluster-linkdist-tiles";
    private static final String keyGenClusterNearTurretDistTiles = "sp-gencluster-near-turret-dist-tiles";
    private static final String keyGenClusterMinDrawTiles = "sp-gencluster-min-draw-tiles";
    private static final String keyGenClusterFallbackBacktrackTiles = "sp-gencluster-fallback-backtrack-tiles";

    private static final int targetModeCore = 0;
    private static final int targetModeNearest = 1;
    private static final int targetModeBlock = 2;
    private static final int targetModeGenCluster = 3;
    private static final int targetModeCoreMouse = 4;

    private static final int threatModeUnset = -1;
    private static final int threatModeGround = 0;
    private static final int threatModeAir = 1;
    private static final int threatModeBoth = 2;

    private static final String excludedGeneratorCombustion = "combustion-generator";
    private static final String excludedGeneratorTurbine = "turbine-condenser";
    private static final int defaultGenClusterLinkDistTiles = 8;
    private static final int defaultGenClusterNearTurretDistTiles = 12;
    private static final int defaultGenClusterMinDrawTiles = 12;
    private static final int defaultGenClusterFallbackBacktrackTiles = 16;

    private static boolean keybindsRegistered;
    private static KeyBind keybindTurrets;
    private static KeyBind keybindAll;
    private static KeyBind keybindModifier;
    private static KeyBind keybindCycleMode;
    private static KeyBind keybindThreatMode;
    private static KeyBind keybindAutoMouse;
    private static KeyBind keybindAutoAttack;
    private static KeyBind keybindAutoMove;

    private final Seq<RenderPath> drawPaths = new Seq<>();
    private float drawUntil = 0f;
    private final Color genPathColor = new Color(0.235f, 0.48f, 1f, 1f);
    private final Color mousePathColor = new Color(0.635f, 0.486f, 0.898f, 1f);
    private final Color autoDeadColor = new Color(1f, 0.23f, 0.19f, 1f);
    private final Color autoWarnColor = new Color(1f, 0.84f, 0.0f, 1f);
    private final Color autoSafeColor = new Color(0.2f, 0.78f, 0.35f, 1f);
    private float lastDamage = 0f;
    private boolean lastIncludeUnits = false;
    private final Seq<Building> tmpBuildings = new Seq<>();
    private final Seq<Unit> tmpUnits = new Seq<>();

    private int lastCycleBaseMode = targetModeCore;

    private boolean liveRefreshWasDown = false;
    private int liveLastMode = -1;
    private int liveLastThreatMode = Integer.MIN_VALUE;
    private int liveLastStartPacked = Integer.MIN_VALUE;
    private int liveLastMousePacked = Integer.MIN_VALUE;
    private boolean liveLastIncludeUnits = false;
    private float liveNextCompute = 0f;

    private static final int autoModeOff = 0;
    private static final int autoModeMouse = 1;
    private static final int autoModeAttack = 2;

    private int autoMode = autoModeOff;
    private int autoLastStartPacked = Integer.MIN_VALUE;
    private int autoLastGoalPacked = Integer.MIN_VALUE;
    private int autoLastThreatMode = Integer.MIN_VALUE;
    private float autoNextCompute = 0f;
    private int autoThreatExtraPaddingTiles = 0;
    private int autoMoveCommandId = 0;
    private final arc.struct.IntIntMap autoMoveCommandByUnit = new arc.struct.IntIntMap();
    private boolean autoMoveFollow = false;
    private int autoMoveFollowGoalX = -1;
    private int autoMoveFollowGoalY = -1;
    private int autoMoveFollowGoalPacked = -1;
    private final arc.struct.IntIntMap autoMoveFollowPathHash = new arc.struct.IntIntMap();
    private final arc.struct.IntFloatMap autoMoveFollowLastIssue = new arc.struct.IntFloatMap();
    private float rtsSendCursor = 0f;

    private float autoMoveMonitorUntil = 0f;
    private float autoMoveMonitorNextCheck = 0f;
    private float autoMoveMonitorMinHpGround = Float.NaN;
    private float autoMoveMonitorMinHpAir = Float.NaN;
    private float autoMoveMonitorPredictedMaxDmg = Float.NaN;

    private int attackTargetX = -1;
    private int attackTargetY = -1;
    private int attackTargetPacked = -1;
    private int bufferedTargetX = -1;
    private int bufferedTargetY = -1;
    private int bufferedTargetPacked = -1;

    private float autoNextNoUnitsToast = 0f;

    // Cached passability (expensive to recompute every frame).
    private final arc.struct.IntMap<boolean[]> passableCacheByKey = new arc.struct.IntMap<>();
    private final IntSeq passableCacheKeyOrder = new IntSeq();
    private int passableCacheRevision = 0;
    private int passableCacheUsedRevision = -1;

    // Scratch arrays for pathfinding (avoid allocating/filling map-sized arrays every search).
    private float[] pathBest;
    private int[] pathBestStamp;
    private int[] pathParent;
    private int[] pathClosedStamp;
    private int pathStamp = 1;
    private final PriorityQueue<Node> pathOpen = new PriorityQueue<>();

    private static final Pattern coordPattern = Pattern.compile("\\((-?\\d+)\\s*,\\s*(-?\\d+)\\)");

    public StealthPathMod(){
        Events.on(ClientLoadEvent.class, e -> {
            ensureDefaults();
            registerKeybinds();
            registerSettings();
            refreshGenPathColor();
            refreshMousePathColor();
            refreshAutoColors();
            registerTriggers();
            GithubUpdateCheck.checkOnce();
        });

        Events.on(ClientChatEvent.class, e -> onChatMessage(e.message));
        Events.on(PlayerChatEvent.class, e -> onChatMessage(e.message));
        Events.on(WorldLoadEvent.class, e -> {
            clearPaths();
            bufferedTargetPacked = -1;
            bufferedTargetX = -1;
            bufferedTargetY = -1;
            attackTargetPacked = -1;
            attackTargetX = -1;
            attackTargetY = -1;
            autoThreatExtraPaddingTiles = 0;
            autoMoveMonitorUntil = 0f;
            autoMoveCommandId++;
            autoMoveCommandByUnit.clear();
            autoMoveFollow = false;
            autoMoveFollowGoalPacked = -1;
            autoMoveFollowPathHash.clear();
            autoMoveFollowLastIssue.clear();
            rtsSendCursor = 0f;
            invalidatePassableCache();
        });

        Events.on(BlockBuildEndEvent.class, e -> invalidatePassableCache());
        Events.on(BlockDestroyEvent.class, e -> invalidatePassableCache());
    }

    private static float previewRefreshInterval(){
        return Math.max(1f, Core.settings.getInt(keyPreviewRefresh, 6));
    }

    private static int autoSlowMultiplier(){
        return clamp(Core.settings.getInt(keyAutoSlowMultiplier, 8), 1, 60);
    }

    private static int rtsMaxWaypoints(){
        return clamp(Core.settings.getInt(keyRtsMaxWaypoints, 12), 2, 80);
    }

    private static float rtsUpdateInterval(){
        return Math.max(1f, Core.settings.getInt(keyRtsUpdateInterval, 30));
    }

    private static float rtsCommandSpacing(){
        return Math.max(0f, Core.settings.getInt(keyRtsCommandSpacing, 2));
    }

    private static int autoClusterSplitTiles(){
        return clamp(Core.settings.getInt(keyAutoClusterSplitTiles, 5), 1, 40);
    }

    private static float formationInflate(){
        int pct = clamp(Core.settings.getInt(keyFormationInflatePct, 125), 100, 400);
        return pct / 100f;
    }

    private static float safeCorridorBiasFactor(){
        int pct = clamp(Core.settings.getInt(keySafeCorridorBiasPct, 35), 0, 400);
        return pct / 100f;
    }

    private static boolean computeSafeDistanceEnabled(){
        return Core.settings.getBool(keyComputeSafeDistance, true);
    }

    private static int passableCacheEntries(){
        return clamp(Core.settings.getInt(keyPassableCacheEntries, 8), 1, 64);
    }

    private static int genClusterLinkDistTiles(){
        return clamp(Core.settings.getInt(keyGenClusterLinkDistTiles, defaultGenClusterLinkDistTiles), 1, 40);
    }

    private static int genClusterNearTurretDistTiles(){
        return clamp(Core.settings.getInt(keyGenClusterNearTurretDistTiles, defaultGenClusterNearTurretDistTiles), 1, 80);
    }

    private static int genClusterMinDrawTiles(){
        return clamp(Core.settings.getInt(keyGenClusterMinDrawTiles, defaultGenClusterMinDrawTiles), 1, 200);
    }

    private static int genClusterFallbackBacktrackTiles(){
        return clamp(Core.settings.getInt(keyGenClusterFallbackBacktrackTiles, defaultGenClusterFallbackBacktrackTiles), 0, 200);
    }

    private void invalidatePassableCache(){
        passableCacheRevision++;
    }

    private void ensureDefaults(){
        Core.settings.defaults(keyEnabled, true);
        Core.settings.defaults(keyProMode, false);
        Core.settings.defaults(keyTargetMode, targetModeCore);
        Core.settings.defaults(keyTargetBlock, "");
        Core.settings.defaults(keyPathDuration, 10);
        Core.settings.defaults(keyPathWidth, 2);
        Core.settings.defaults(keyPathAlpha, 85);
        Core.settings.defaults(keyShowDamageText, true);
        Core.settings.defaults(keyDamageTextScale, 60);
        Core.settings.defaults(keyDamageLabelAtEnd, false);
        Core.settings.defaults(keyDamageTextOffsetScale, 100);
        Core.settings.defaults(keyShowEndpoints, true);
        Core.settings.defaults(keyStartDotScale, 220);
        Core.settings.defaults(keyEndDotScale, 260);
        Core.settings.defaults(keyPreviewRefresh, 6);
        Core.settings.defaults(keyThreatMode, threatModeUnset);
        Core.settings.defaults(keyGenPathColor, "3c7bff");
        Core.settings.defaults(keyMousePathColor, "a27ce5");
        Core.settings.defaults(keyGenClusterMaxPaths, 3);
        Core.settings.defaults(keyGenClusterStartFromCore, false);
        Core.settings.defaults(keyGenClusterMinSize, 2);
        Core.settings.defaults(keyAutoSafeDamageThreshold, 10);
        Core.settings.defaults(keyAutoColorDead, "ff3b30");
        Core.settings.defaults(keyAutoColorWarn, "ffd60a");
        Core.settings.defaults(keyAutoColorSafe, "34c759");
        Core.settings.defaults(keyAutoMoveEnabled, true);
        Core.settings.defaults(keyAutoThreatPaddingMax, 6);
        Core.settings.defaults(keyShowToasts, true);
        Core.settings.defaults(keyAutoClusterSplitTiles, 5);
        Core.settings.defaults(keyFormationInflatePct, 125);
        Core.settings.defaults(keySafeCorridorBiasPct, 35);
        Core.settings.defaults(keyComputeSafeDistance, true);
        Core.settings.defaults(keyRtsMaxWaypoints, 12);
        Core.settings.defaults(keyRtsUpdateInterval, 30);
        Core.settings.defaults(keyRtsCommandSpacing, 2);
        Core.settings.defaults(keyAutoBatchEnabled, true);
        Core.settings.defaults(keyAutoBatchSizePct, 100);
        Core.settings.defaults(keyAutoBatchDelayPct, 100);
        Core.settings.defaults(keyAutoSlowMultiplier, 8);
        Core.settings.defaults(keyPassableCacheEntries, 8);
        Core.settings.defaults(keyGenClusterLinkDistTiles, defaultGenClusterLinkDistTiles);
        Core.settings.defaults(keyGenClusterNearTurretDistTiles, defaultGenClusterNearTurretDistTiles);
        Core.settings.defaults(keyGenClusterMinDrawTiles, defaultGenClusterMinDrawTiles);
        Core.settings.defaults(keyGenClusterFallbackBacktrackTiles, defaultGenClusterFallbackBacktrackTiles);
    }

    private void registerKeybinds(){
        if(keybindsRegistered) return;
        keybindsRegistered = true;

        keybindTurrets = KeyBind.add("sp_path_turrets", KeyCode.x, "stealthpath");
        keybindAll = KeyBind.add("sp_path_all", KeyCode.y, "stealthpath");
        keybindModifier = KeyBind.add("sp_modifier", KeyCode.unset, "stealthpath");
        keybindCycleMode = KeyBind.add("sp_mode_cycle", KeyCode.k, "stealthpath");
        keybindThreatMode = KeyBind.add("sp_threat_cycle", KeyCode.l, "stealthpath");
        keybindAutoMouse = KeyBind.add("sp_auto_mouse", KeyCode.n, "stealthpath");
        keybindAutoAttack = KeyBind.add("sp_auto_attack", KeyCode.m, "stealthpath");
        keybindAutoMove = KeyBind.add("sp_auto_move", KeyCode.mouseRight, "stealthpath");
    }

    private void registerSettings(){
        if(ui == null || ui.settings == null) return;
        if(bekBundled) return;


        ui.settings.addCategory("@sp.category", Icon.map, this::bekBuildSettings);
    }
    /** Populates a {@link mindustry.ui.dialogs.SettingsMenuDialog.SettingsTable} with this mod's settings. */
    public void bekBuildSettings(SettingsMenuDialog.SettingsTable table){
            table.pref(new HeaderSetting("@sp.section.general", Icon.settings));
            table.pref(new IconCheckSetting(keyEnabled, true, Icon.ok, null));
            table.pref(new IconCheckSetting(keyShowToasts, true, Icon.chat, null));
            table.pref(new IconCheckSetting(keyProMode, false, Icon.settings, null));
            addAdvancedSettingsRow(table);
            table.pref(new IconSliderSetting(keyPathDuration, 10, 0, 60, 5, Icon.info, v -> v == 0 ? "inf" : v + "s", null));
            table.pref(new IconSliderSetting(keyPathWidth, 2, 1, 6, 1, Icon.resize, v -> String.valueOf(v), null));
            table.pref(new IconSliderSetting(keyPathAlpha, 85, 0, 100, 5, Icon.image, v -> v + "%", null));
            table.pref(new IconCheckSetting(keyShowEndpoints, true, Icon.image, null));
            table.pref(new IconSliderSetting(keyStartDotScale, 220, 0, 400, 10, Icon.right, v -> v + "%", null));
            table.pref(new IconSliderSetting(keyEndDotScale, 260, 0, 400, 10, Icon.left, v -> v + "%", null));
            table.pref(new IconCheckSetting(keyShowDamageText, true, Icon.info, null));
            table.pref(new IconSliderSetting(keyDamageTextScale, 60, 20, 140, 5, Icon.resize, v -> v + "%", null));
            table.pref(new IconCheckSetting(keyDamageLabelAtEnd, false, Icon.up, null));
            table.pref(new IconSliderSetting(keyDamageTextOffsetScale, 100, 0, 300, 10, Icon.move, v -> v + "%", null));
            table.pref(new IconSliderSetting(keyPreviewRefresh, 6, 1, 60, 1, Icon.refresh, v -> Strings.autoFixed(v / 60f, 2) + "s", null));

            table.pref(new HeaderSetting("@sp.section.colors", Icon.pencil));
            table.pref(new IconTextSetting(keyGenPathColor, "3c7bff", Icon.effect, v -> refreshGenPathColor()));
            table.pref(new IconTextSetting(keyMousePathColor, "a27ce5", Icon.zoom, v -> refreshMousePathColor()));

            table.pref(new HeaderSetting("@sp.section.auto", Icon.commandRally));
            table.pref(new IconSliderSetting(keyAutoSafeDamageThreshold, 10, 0, 200, 1, Icon.warning, v -> String.valueOf(v), null));
            table.pref(new IconTextSetting(keyAutoColorSafe, "34c759", Icon.ok, v -> refreshAutoColors()));
            table.pref(new IconTextSetting(keyAutoColorWarn, "ffd60a", Icon.warning, v -> refreshAutoColors()));
            table.pref(new IconTextSetting(keyAutoColorDead, "ff3b30", Icon.cancel, v -> refreshAutoColors()));
            table.pref(new IconCheckSetting(keyAutoMoveEnabled, true, Icon.commandRally, null));
            table.pref(new IconSliderSetting(keyAutoThreatPaddingMax, 6, 0, 20, 1, Icon.warning, v -> v + " tiles", null));

            table.pref(new HeaderSetting("@sp.section.gencluster", Icon.power));
            table.pref(new IconSliderSetting(keyGenClusterMaxPaths, 3, 1, 10, 1, Icon.list, v -> String.valueOf(v), null));
            table.pref(new IconSliderSetting(keyGenClusterMinSize, 2, 2, 10, 1, Icon.filter, v -> String.valueOf(v), null));
            table.pref(new IconCheckSetting(keyGenClusterStartFromCore, false, Icon.players, null));

            table.pref(new HeaderSetting("@sp.section.target", Icon.modeAttack));
            addThreatModeRow(table);
            addTargetRow(table);
        
    }


    private void addAdvancedSettingsRow(Table table){
        table.table(Tex.button, t -> {
            t.left().margin(10f);
            t.image(Icon.settings).size(20f).padRight(8f);
            t.add("@sp.setting.advanced.menu").left().width(170f);

            TextButton open = t.button("@sp.setting.advanced.open", Styles.flatt, this::showAdvancedSettingsDialog)
                .growX()
                .height(40f)
                .padLeft(8f)
                .get();

            open.update(() -> {
                boolean pro = Core.settings.getBool(keyProMode, false);
                open.setDisabled(!pro);
                open.setText(pro ? Core.bundle.get("sp.setting.advanced.open") : Core.bundle.get("sp.setting.advanced.locked"));
            });
        }).width(prefWidth()).left().padTop(6f);

        table.row();
    }

    private void showAdvancedSettingsDialog(){
        if(!Core.settings.getBool(keyProMode, false)){
            showToast("@sp.toast.pro-required", 2f);
            return;
        }

        BaseDialog dialog = new BaseDialog("@sp.advanced.title");
        dialog.addCloseButton();

        SettingsMenuDialog.SettingsTable table = new SettingsMenuDialog.SettingsTable();

        table.pref(new HeaderSetting("@sp.section.advanced.pathfinding", Icon.modeAttack));
        table.pref(new IconSliderSetting(keyAutoClusterSplitTiles, 5, 1, 30, 1, Icon.filter, v -> v + " tiles", null));
        table.pref(new IconSliderSetting(keyFormationInflatePct, 125, 100, 300, 5, Icon.resize, v -> v + "%", null));
        table.pref(new IconSliderSetting(keySafeCorridorBiasPct, 35, 0, 200, 5, Icon.info, v -> v + "%", null));
        table.pref(new IconCheckSetting(keyComputeSafeDistance, true, Icon.grid, null));

        table.pref(new HeaderSetting("@sp.section.advanced.automove", Icon.commandRally));
        table.pref(new IconSliderSetting(keyRtsMaxWaypoints, 12, 2, 60, 1, Icon.list, v -> String.valueOf(v), null));
        table.pref(new IconSliderSetting(keyRtsUpdateInterval, 30, 1, 240, 1, Icon.refresh, v -> Strings.autoFixed(v / 60f, 2) + "s", null));
        table.pref(new IconSliderSetting(keyRtsCommandSpacing, 2, 0, 10, 1, Icon.info, v -> Strings.autoFixed(v / 60f, 3) + "s", null));
        table.pref(new IconCheckSetting(keyAutoBatchEnabled, true, Icon.commandRally, null));
        table.pref(new IconSliderSetting(keyAutoBatchSizePct, 100, 50, 200, 10, Icon.resize, v -> v + "%", null));
        table.pref(new IconSliderSetting(keyAutoBatchDelayPct, 100, 0, 200, 10, Icon.refresh, v -> v + "%", null));
        table.pref(new IconSliderSetting(keyAutoSlowMultiplier, 8, 1, 30, 1, Icon.refresh, v -> v + "x", null));

        table.pref(new HeaderSetting("@sp.section.advanced.cache", Icon.info));
        table.pref(new IconSliderSetting(keyPassableCacheEntries, 8, 1, 24, 1, Icon.info, v -> String.valueOf(v), v -> invalidatePassableCache()));

        table.pref(new HeaderSetting("@sp.section.advanced.gencluster", Icon.power));
        table.pref(new IconSliderSetting(keyGenClusterLinkDistTiles, defaultGenClusterLinkDistTiles, 1, 20, 1, Icon.list, v -> v + " tiles", null));
        table.pref(new IconSliderSetting(keyGenClusterNearTurretDistTiles, defaultGenClusterNearTurretDistTiles, 1, 40, 1, Icon.warning, v -> v + " tiles", null));
        table.pref(new IconSliderSetting(keyGenClusterMinDrawTiles, defaultGenClusterMinDrawTiles, 1, 60, 1, Icon.info, v -> v + " tiles", null));
        table.pref(new IconSliderSetting(keyGenClusterFallbackBacktrackTiles, defaultGenClusterFallbackBacktrackTiles, 0, 60, 1, Icon.refresh, v -> v + " tiles", null));

        ScrollPane pane = new ScrollPane(table);
        pane.setScrollingDisabled(true, false);
        pane.setFadeScrollBars(false);

        dialog.cont.add(pane).grow().minHeight(420f);
        dialog.show();
    }

    private void addThreatModeRow(Table table){
        table.table(Tex.button, t -> {
            t.left().margin(10f);
            t.image(Icon.warning).size(20f).padRight(8f);
            t.add("@sp.setting.threat.mode").left().width(170f);

            TextButton modeButton = t.button("", Styles.flatt, this::cycleThreatMode)
                .growX()
                .height(40f)
                .padLeft(8f)
                .get();

            modeButton.update(() -> modeButton.setText(threatModeDisplay(Core.settings.getInt(keyThreatMode, threatModeGround))));
        }).growX().padTop(6f);

        table.row();
    }

    private static String threatModeDisplay(int mode){
        switch(mode){
            case threatModeAir:
                return Core.bundle.get("sp.setting.threat.mode.air");
            case threatModeBoth:
                return Core.bundle.get("sp.setting.threat.mode.both");
            case threatModeGround:
            default:
                return Core.bundle.get("sp.setting.threat.mode.ground");
        }
    }

    private void addTargetRow(Table table){
        table.table(Tex.button, t -> {
            t.left().margin(10f);
            t.image(Icon.commandAttack).size(20f).padRight(8f);
            t.add("@sp.setting.target.mode").left().width(170f);

            TextButton modeButton = t.button("", Styles.flatt, this::cycleTargetMode)
                .growX()
                .height(40f)
                .padLeft(8f)
                .get();

            modeButton.update(() -> modeButton.setText(targetModeDisplay(Core.settings.getInt(keyTargetMode, targetModeCore))));
        }).growX().padTop(6f);

        table.row();
        table.table(Tex.button, t -> {
            t.left().margin(10f);
            t.image(Icon.grid).size(20f).padRight(8f);
            t.add("@sp.setting.target.block").left().width(170f);

            TextButton selectButton = t.button("", Styles.flatt, () -> showBlockSelectDialog(block -> Core.settings.put(keyTargetBlock, block == null ? "" : block.name)))
                .growX()
                .height(40f)
                .padLeft(8f)
                .get();

            selectButton.update(() -> {
                Block block = selectedTargetBlock();
                boolean enabled = Core.settings.getInt(keyTargetMode, targetModeCore) == targetModeBlock;
                selectButton.setDisabled(!enabled);
                selectButton.setText(block == null ? Core.bundle.get("sp.setting.target.block.none") : block.localizedName);
            });
        }).growX().padTop(6f);
    }

    private void cycleTargetMode(){
        int cur = Core.settings.getInt(keyTargetMode, targetModeCore);
        int next = (cur + 1) % 5;
        Core.settings.put(keyTargetMode, next);
    }

    private String targetModeDisplay(int mode){
        switch(mode){
            case targetModeCore:
                return Core.bundle.get("sp.setting.target.mode.core");
            case targetModeNearest:
                return Core.bundle.get("sp.setting.target.mode.nearest");
            case targetModeBlock:
                return Core.bundle.get("sp.setting.target.mode.block");
            case targetModeGenCluster:
                return Core.bundle.get("sp.setting.target.mode.gencluster");
            case targetModeCoreMouse:
                return Core.bundle.get("sp.setting.target.mode.coremouse");
            default:
                return Core.bundle.get("sp.setting.target.mode.core");
        }
    }

    private void showBlockSelectDialog(Cons<Block> consumer){
        BaseDialog dialog = new BaseDialog("@sp.setting.target.block");
        dialog.addCloseButton();

        String[] searchText = {""};

        Table list = new Table();
        list.top().left();
        list.defaults().growX().height(54f).pad(2f);

        ScrollPane pane = new ScrollPane(list);
        pane.setFadeScrollBars(false);

        Runnable rebuild = () -> {
            list.clearChildren();

            list.button("@sp.setting.target.block.none", Styles.flatt, () -> {
                dialog.hide();
                consumer.get(null);
            }).row();

            String query = searchText[0] == null ? "" : searchText[0].trim().toLowerCase(Locale.ROOT);

            for(Block block : content.blocks()){
                if(block == null) continue;
                if(block.category == null) continue;
                if(!block.isVisible()) continue;
                if(block.buildVisibility == BuildVisibility.hidden) continue;
                if(!block.hasBuilding()) continue;

                if(!query.isEmpty()){
                    String name = block.name.toLowerCase(Locale.ROOT);
                    String localized = Strings.stripColors(block.localizedName).toLowerCase(Locale.ROOT);
                    if(!name.contains(query) && !localized.contains(query)){
                        continue;
                    }
                }

                list.button(b -> {
                    b.left();
                    b.image(block.uiIcon).size(32f).padRight(8f);
                    b.add(block.localizedName).left().growX().wrap();
                    b.add(block.name).color(Color.gray).padLeft(8f).right();
                }, Styles.flatt, () -> {
                    dialog.hide();
                    consumer.get(block);
                }).row();
            }
        };

        dialog.cont.table(t -> {
            t.left();
            t.image(Icon.zoom).padRight(8f);
            t.field("", text -> {
                searchText[0] = text;
                rebuild.run();
            }).growX().get().setMessageText("@players.search");
        }).growX().padBottom(6f);

        dialog.cont.row();
        dialog.cont.add(pane).grow().minHeight(320f);

        dialog.shown(rebuild);
        dialog.show();
    }

    private Block selectedTargetBlock(){
        String name = Core.settings.getString(keyTargetBlock, "");
        if(name == null || name.trim().isEmpty()) return null;
        return content.block(name);
    }

    private void registerTriggers(){
        Events.run(Trigger.update, this::update);
        Events.run(Trigger.draw, this::draw);
    }

    private void update(){
        if(mobile) return;
        if(!Core.settings.getBool(keyEnabled, true)) return;
        if(!state.isGame() || world == null || world.isGenerating() || player == null || player.unit() == null) return;

        if(Core.scene.hasKeyboard() || Core.scene.hasDialog()) return;

        Unit unit = player.unit();

        if(drawUntil > 0f && Time.time > drawUntil){
            clearPaths();
        }

        if(keybindTurrets == null || keybindAll == null || keybindModifier == null || keybindCycleMode == null || keybindThreatMode == null || keybindAutoMouse == null || keybindAutoAttack == null || keybindAutoMove == null){
            registerKeybinds();
        }

        if(Core.settings.getInt(keyThreatMode, threatModeUnset) == threatModeUnset){
            Core.settings.put(keyThreatMode, unit.isFlying() ? threatModeAir : threatModeGround);
        }

        boolean modifierRequired = keybindModifier != null && keybindModifier.value != null && keybindModifier.value.key != null && keybindModifier.value.key != KeyCode.unset;
        boolean modifierDown = !modifierRequired || Core.input.keyDown(keybindModifier);

        boolean cycleTap = modifierDown && keybindCycleMode != null && Core.input.keyTap(keybindCycleMode);
        boolean threatTap = modifierDown && keybindThreatMode != null && Core.input.keyTap(keybindThreatMode);
        boolean autoMouseTap = modifierDown && keybindAutoMouse != null && Core.input.keyTap(keybindAutoMouse);
        boolean autoAttackTap = modifierDown && keybindAutoAttack != null && Core.input.keyTap(keybindAutoAttack);

        boolean turretsTap = modifierDown && keybindTurrets != null && Core.input.keyTap(keybindTurrets);
        boolean allTap = modifierDown && keybindAll != null && Core.input.keyTap(keybindAll);

        boolean turretsDown = modifierDown && keybindTurrets != null && Core.input.keyDown(keybindTurrets);
        boolean allDown = modifierDown && keybindAll != null && Core.input.keyDown(keybindAll);
        boolean previewDown = turretsDown || allDown;
        boolean previewIncludeUnits = allDown;

        if(cycleTap){
            cycleDisplayMode();
        }

        if(threatTap){
            cycleThreatMode();
        }

        if(autoMouseTap){
            toggleAutoMode(autoModeMouse);
        }else if(autoAttackTap){
            toggleAutoMode(autoModeAttack);
        }

        if(previewDown){
            lastIncludeUnits = previewIncludeUnits;
            liveRefresh(unit, previewIncludeUnits);
        }else if(liveRefreshWasDown){
            // released: restore normal timeout behavior
            int seconds = Core.settings.getInt(keyPathDuration, 10);
            drawUntil = seconds <= 0 ? Float.POSITIVE_INFINITY : Time.time + seconds * 60f;
            liveRefreshWasDown = false;
            liveNextCompute = 0f;
        }

        if(turretsTap){
            lastIncludeUnits = false;
            computePath(false, true);
        }else if(allTap){
            lastIncludeUnits = true;
            computePath(true, true);
        }

        autoHandleAutoMoveKey();
        autoMonitorUnexpectedDamage();
        autoUpdate();
    }

    private void autoHandleAutoMoveKey(){
        if(autoMode == autoModeOff) return;
        if(!state.isGame() || world == null || player == null) return;
        if(!Core.settings.getBool(keyAutoMoveEnabled, true)) return;
        if(keybindAutoMove == null) return;
        if(!Core.input.keyTap(keybindAutoMove)) return;

        // Only act when the player has an RTS selection; otherwise, let the default right-click behavior work.
        if(control == null || control.input == null || control.input.selectedUnits == null || !control.input.selectedUnits.any()) return;

        Seq<ControlledCluster> clusters = computeControlledClusters();
        if(clusters == null || clusters.isEmpty()) return;

        int goalX, goalY;
        if(autoMoveFollow && autoMoveFollowGoalPacked != -1){
            goalX = autoMoveFollowGoalX;
            goalY = autoMoveFollowGoalY;
        }else if(autoMode == autoModeMouse){
            goalX = clamp(worldToTile(Core.input.mouseWorldX()), 0, world.width() - 1);
            goalY = clamp(worldToTile(Core.input.mouseWorldY()), 0, world.height() - 1);
        }else{
            if(bufferedTargetPacked == -1) return;
            goalX = bufferedTargetX;
            goalY = bufferedTargetY;
        }

        boolean movedAny = false;
        float minHpGround = Float.POSITIVE_INFINITY;
        float minHpAir = Float.POSITIVE_INFINITY;
        float predictedMax = 0f;
        int goalPacked = goalX + goalY * world.width();

        // Lock auto-move destination and enable follow-up rerouting.
        autoMoveFollow = false;
        autoMoveFollowGoalX = goalX;
        autoMoveFollowGoalY = goalY;
        autoMoveFollowGoalPacked = goalPacked;
        autoMoveFollowPathHash.clear();
        autoMoveFollowLastIssue.clear();
        autoMoveCommandByUnit.clear();

        for(int i = 0; i < clusters.size; i++){
            ControlledCluster cluster = clusters.get(i);
            if(cluster == null) continue;

            float passClearance = cluster.maxHitRadiusWorld;
            float threatClearance = cluster.threatClearanceWorld + autoThreatExtraPaddingTiles * tilesize;
            ThreatMap map = buildThreatMap(cluster.moveUnit, true, cluster.moveFlying, cluster.threatsAir, cluster.threatsGround, passClearance, threatClearance);
            ShiftedPath best = findBestShiftedClusterPath(cluster, map, goalX, goalY);
            if(best == null || best.path == null || best.path.isEmpty()) continue;

            issueRtsMoveAlongPath(cluster, best.path, map.width);
            movedAny = true;
            autoMoveFollowPathHash.put(cluster.key, hashWaypointPath(best.path, map.width));
            autoMoveFollowLastIssue.put(cluster.key, Time.time);

            if(cluster.hasGround && Float.isFinite(cluster.minHpGround)){
                minHpGround = Math.min(minHpGround, cluster.minHpGround);
            }
            if(cluster.hasAir && Float.isFinite(cluster.minHpAir)){
                minHpAir = Math.min(minHpAir, cluster.minHpAir);
            }
            if(Float.isFinite(best.maxDmg)){
                predictedMax = Math.max(predictedMax, best.maxDmg);
            }
        }

        if(!movedAny) return;

        autoMoveFollow = true;

        autoMoveMonitorUntil = Float.POSITIVE_INFINITY;
        autoMoveMonitorNextCheck = Time.time + previewRefreshInterval();
        autoMoveMonitorMinHpGround = minHpGround == Float.POSITIVE_INFINITY ? Float.NaN : minHpGround;
        autoMoveMonitorMinHpAir = minHpAir == Float.POSITIVE_INFINITY ? Float.NaN : minHpAir;
        autoMoveMonitorPredictedMaxDmg = predictedMax;
    }

    private void autoMonitorUnexpectedDamage(){
        if(autoMoveMonitorUntil <= 0f) return;
        if(!state.isGame() || world == null || player == null){
            autoMoveMonitorUntil = 0f;
            return;
        }

        // monitor indefinitely while enabled

        if(Time.time < autoMoveMonitorNextCheck) return;
        autoMoveMonitorNextCheck = Time.time + previewRefreshInterval();

        if(autoMode == autoModeOff) return;

        if(control == null || control.input == null || control.input.selectedUnits == null || !control.input.selectedUnits.any()){
            autoMoveMonitorUntil = 0f;
            return;
        }

        float curMinGround = Float.POSITIVE_INFINITY;
        float curMinAir = Float.POSITIVE_INFINITY;
        boolean hasGround = false;
        boolean hasAir = false;

        Unit playerUnit = player.unit();
        Unit first = null;
        int count = 0;

        for(int i = 0; i < control.input.selectedUnits.size; i++){
            Unit u = control.input.selectedUnits.get(i);
            if(u == null) continue;
            if(u.team != player.team()) continue;
            if(!u.isAdded() || u.dead()) continue;

            if(first == null) first = u;
            count++;

            float hp = u.health();
            if(u.isFlying()){
                hasAir = true;
                curMinAir = Math.min(curMinAir, hp);
            }else{
                hasGround = true;
                curMinGround = Math.min(curMinGround, hp);
            }
        }

        if(count <= 0 || (count == 1 && first == playerUnit)){
            autoMoveMonitorUntil = 0f;
            return;
        }

        if(!hasGround) curMinGround = Float.NaN;
        if(!hasAir) curMinAir = Float.NaN;

        boolean groundHit = hasGround
            && Float.isFinite(autoMoveMonitorMinHpGround)
            && Float.isFinite(curMinGround)
            && curMinGround < autoMoveMonitorMinHpGround - 0.01f;

        boolean airHit = hasAir
            && Float.isFinite(autoMoveMonitorMinHpAir)
            && Float.isFinite(curMinAir)
            && curMinAir < autoMoveMonitorMinHpAir - 0.01f;

        if(!groundHit && !airHit) return;

        // Update baseline for future checks.
        autoMoveMonitorMinHpGround = curMinGround;
        autoMoveMonitorMinHpAir = curMinAir;

        // If the mod predicted a safe (0 damage) path but units still took damage, increase the safety padding.
        if(!(Float.isFinite(autoMoveMonitorPredictedMaxDmg) && autoMoveMonitorPredictedMaxDmg <= 0.0001f)) return;

        int maxPadding = Math.max(0, Core.settings.getInt(keyAutoThreatPaddingMax, 6));
        autoThreatExtraPaddingTiles = Math.min(maxPadding, autoThreatExtraPaddingTiles + 1);
        autoNextCompute = 0f;

        int goalX, goalY;
        if(autoMode == autoModeMouse){
            goalX = clamp(worldToTile(Core.input.mouseWorldX()), 0, world.width() - 1);
            goalY = clamp(worldToTile(Core.input.mouseWorldY()), 0, world.height() - 1);
        }else{
            if(bufferedTargetPacked == -1) return;
            goalX = bufferedTargetX;
            goalY = bufferedTargetY;
        }

        float predictedMax = 0f;
        boolean issuedAny = false;

        Seq<ControlledCluster> clusters = computeControlledClusters();
        if(clusters == null || clusters.isEmpty()) return;

        for(int i = 0; i < clusters.size; i++){
            ControlledCluster cluster = clusters.get(i);
            if(cluster == null) continue;

            float passClearance = cluster.maxHitRadiusWorld;
            float threatClearance = cluster.threatClearanceWorld + autoThreatExtraPaddingTiles * tilesize;
            ThreatMap map = buildThreatMap(cluster.moveUnit, true, cluster.moveFlying, cluster.threatsAir, cluster.threatsGround, passClearance, threatClearance);
            ShiftedPath best = findBestShiftedClusterPath(cluster, map, goalX, goalY);
            if(best == null || best.path == null || best.path.isEmpty()) continue;

            issueRtsMoveAlongPath(cluster, best.path, map.width);
            issuedAny = true;
            if(autoMoveFollow){
                autoMoveFollowPathHash.put(cluster.key, hashWaypointPath(best.path, map.width));
                autoMoveFollowLastIssue.put(cluster.key, Time.time);
            }
            if(Float.isFinite(best.maxDmg)){
                predictedMax = Math.max(predictedMax, best.maxDmg);
            }
        }

        if(!issuedAny) return;

        autoMoveMonitorPredictedMaxDmg = predictedMax;
        autoMoveMonitorUntil = Float.POSITIVE_INFINITY;
    }

    private void toggleAutoMode(int requested){
        if(autoMode == requested){
            autoMode = autoModeOff;
            autoNextCompute = 0f;
            drawUntil = 0f;
            clearPaths();
            autoThreatExtraPaddingTiles = 0;
            autoMoveMonitorUntil = 0f;
            autoMoveCommandId++;
            autoMoveCommandByUnit.clear();
            autoMoveFollow = false;
            autoMoveFollowGoalPacked = -1;
            autoMoveFollowPathHash.clear();
            autoMoveFollowLastIssue.clear();
            rtsSendCursor = 0f;
            showToast(requested == autoModeMouse ? "@sp.toast.auto.mouse.off" : "@sp.toast.auto.attack.off", 2.5f);
        }else{
            autoMode = requested;
            autoNextCompute = 0f;
            autoLastStartPacked = Integer.MIN_VALUE;
            autoLastGoalPacked = Integer.MIN_VALUE;
            autoLastThreatMode = Integer.MIN_VALUE;
            drawUntil = Float.POSITIVE_INFINITY;
            autoThreatExtraPaddingTiles = 0;
            autoMoveMonitorUntil = 0f;
            autoMoveCommandId++;
            autoMoveCommandByUnit.clear();
            autoMoveFollow = false;
            autoMoveFollowGoalPacked = -1;
            autoMoveFollowPathHash.clear();
            autoMoveFollowLastIssue.clear();
            rtsSendCursor = 0f;

            if(autoMode == autoModeAttack && bufferedTargetPacked == -1){
                showToast("@sp.toast.auto.attack.wait", 3f);
            }else{
                showToast(autoMode == autoModeMouse ? "@sp.toast.auto.mouse.on" : "@sp.toast.auto.attack.on", 2.5f);
            }
        }
    }

    private void autoUpdate(){
        if(autoMode == autoModeOff) return;
        if(!state.isGame() || world == null || player == null) return;

        float baseInterval = previewRefreshInterval();
        float rtsInterval = Math.max(baseInterval, rtsUpdateInterval());
        if(Time.time < autoNextCompute) return;

        Seq<ControlledCluster> clusters = computeControlledClusters();
        if(clusters == null || clusters.isEmpty()){
            if(Time.time >= autoNextNoUnitsToast){
                showToast("@sp.toast.auto.no-units", 2.5f);
                autoNextNoUnitsToast = Time.time + 90f;
            }
            autoNextCompute = Time.time + baseInterval * 2f;
            return;
        }

        int goalX, goalY;
        if(autoMoveFollow && autoMoveFollowGoalPacked != -1){
            goalX = autoMoveFollowGoalX;
            goalY = autoMoveFollowGoalY;
        }else if(autoMode == autoModeMouse){
            goalX = clamp(worldToTile(Core.input.mouseWorldX()), 0, world.width() - 1);
            goalY = clamp(worldToTile(Core.input.mouseWorldY()), 0, world.height() - 1);
        }else{
            if(bufferedTargetPacked == -1){
                autoNextCompute = Time.time + baseInterval * 2f;
                return;
            }
            goalX = bufferedTargetX;
            goalY = bufferedTargetY;
        }

        int goalPacked = goalX + goalY * world.width();

        int startHash = 1;
        int threatHash = 1;
        for(int i = 0; i < clusters.size; i++){
            ControlledCluster c = clusters.get(i);
            if(c == null) continue;
            int sx = clamp(worldToTile(c.x), 0, world.width() - 1);
            int sy = clamp(worldToTile(c.y), 0, world.height() - 1);
            startHash = 31 * startHash + (sx + sy * world.width());
            threatHash = 31 * threatHash + c.threatMode;
        }

        startHash = 31 * startHash + clusters.size;
        threatHash = 31 * threatHash + clusters.size;

        boolean unchanged = startHash == autoLastStartPacked && goalPacked == autoLastGoalPacked && threatHash == autoLastThreatMode;

        clearPaths();
        float maxDmg = 0f;
        boolean anyPath = false;

        for(int i = 0; i < clusters.size; i++){
            ControlledCluster cluster = clusters.get(i);
            if(cluster == null) continue;

            float passClearance = cluster.maxHitRadiusWorld;
            float threatClearance = cluster.threatClearanceWorld + autoThreatExtraPaddingTiles * tilesize;
            ThreatMap map = buildThreatMap(cluster.moveUnit, true, cluster.moveFlying, cluster.threatsAir, cluster.threatsGround, passClearance, threatClearance);
            ShiftedPath sp = computeClusterToGoal(cluster, map, goalX, goalY, false);
            if(sp == null || sp.path == null || sp.path.isEmpty()) continue;
            anyPath = true;
            if(Float.isFinite(sp.maxDmg)){
                maxDmg = Math.max(maxDmg, sp.maxDmg);
            }

            // If auto-move was triggered, keep updating RTS waypoints when the computed path changes (e.g. turrets start firing).
            if(autoMoveFollow && Core.settings.getBool(keyAutoMoveEnabled, true)){
                int newHash = hashWaypointPath(sp.path, map.width);
                int prevHash = autoMoveFollowPathHash.get(cluster.key, Integer.MIN_VALUE);
                float lastIssued = autoMoveFollowLastIssue.get(cluster.key, -999999f);

                if(prevHash == Integer.MIN_VALUE || prevHash != newHash){
                    if(Time.time - lastIssued >= rtsInterval){
                        issueRtsMoveAlongPath(cluster, sp.path, map.width);
                        autoMoveFollowPathHash.put(cluster.key, newHash);
                        autoMoveFollowLastIssue.put(cluster.key, Time.time);
                    }
                }
            }
        }

        if(!anyPath){
            autoNextCompute = Time.time + baseInterval * 2f;
            return;
        }

        lastDamage = maxDmg;
        drawUntil = Float.POSITIVE_INFINITY;
        if(autoMoveFollow){
            autoMoveMonitorPredictedMaxDmg = maxDmg;
        }

        autoLastStartPacked = startHash;
        autoLastGoalPacked = goalPacked;
        autoLastThreatMode = threatHash;

        // If the goal/start didn't move, slow down updates to reduce CPU usage.
        float slowInterval = Math.min(240f, baseInterval * autoSlowMultiplier());
        autoNextCompute = Time.time + (autoMoveFollow ? baseInterval : (unchanged ? slowInterval : baseInterval));
    }

    private Seq<ControlledCluster> computeControlledClusters(){
        if(player == null) return null;
        Unit playerUnit = player.unit();
        if(playerUnit == null) return null;

        tmpUnits.clear();

        if(control != null && control.input != null && control.input.selectedUnits != null && control.input.selectedUnits.any()){
            for(int i = 0; i < control.input.selectedUnits.size; i++){
                Unit u = control.input.selectedUnits.get(i);
                if(u == null) continue;
                if(u.team != player.team()) continue;
                if(!u.isAdded() || u.dead()) continue;
                tmpUnits.add(u);
            }
        }

        // Auto modes require a real controlled group (not just the player alone).
        if(tmpUnits.isEmpty()) return null;
        if(tmpUnits.size == 1 && tmpUnits.first() == playerUnit) return null;

        float link = autoClusterSplitTiles() * tilesize;
        float link2 = link * link;

        boolean[] used = new boolean[tmpUnits.size];
        IntSeq queue = new IntSeq();

        Seq<ControlledCluster> out = new Seq<>();

        for(int i = 0; i < tmpUnits.size; i++){
            if(used[i]) continue;

            Seq<Unit> members = new Seq<>();
            queue.clear();

            used[i] = true;
            queue.add(i);
            members.add(tmpUnits.get(i));

            // Flood-fill by proximity: connect units within 5 tiles into the same cluster.
            for(int q = 0; q < queue.size; q++){
                Unit a = tmpUnits.get(queue.items[q]);
                if(a == null) continue;

                for(int j = 0; j < tmpUnits.size; j++){
                    if(used[j]) continue;

                    Unit b = tmpUnits.get(j);
                    if(b == null) continue;

                    float dx = b.x - a.x;
                    float dy = b.y - a.y;
                    float d2 = dx * dx + dy * dy;
                    if(d2 <= link2){
                        used[j] = true;
                        queue.add(j);
                        members.add(b);
                    }
                }
            }

            if(members.size == 1 && members.first() == playerUnit){
                // Ignore the player's own unit if it ends up being an isolated single-unit "cluster".
                continue;
            }

            ControlledCluster cluster = buildControlledCluster(members);
            if(cluster != null){
                out.add(cluster);
            }
        }

        if(out.isEmpty()) return null;

        // Prefer drawing/moving larger clusters first.
        out.sort((a, b) -> Integer.compare(b.units.size, a.units.size));
        return out;
    }

    private static ControlledCluster buildControlledCluster(Seq<Unit> members){
        if(members == null || members.isEmpty()) return null;

        int key = computeClusterKey(members);

        float sx = 0f, sy = 0f;
        int flying = 0;

        boolean hasGround = false;
        boolean hasAir = false;

        float minSpeedGround = Float.POSITIVE_INFINITY;
        float minHpGround = Float.POSITIVE_INFINITY;
        float minSpeedAir = Float.POSITIVE_INFINITY;
        float minHpAir = Float.POSITIVE_INFINITY;
        float maxHitRadiusWorld = 0f;
        float sumSqGround = 0f;
        float sumSqAir = 0f;

        Unit first = members.first();
        float leftX = first.x, leftY = first.y;
        float rightX = first.x, rightY = first.y;
        float topX = first.x, topY = first.y;
        float bottomX = first.x, bottomY = first.y;

        for(int i = 0; i < members.size; i++){
            Unit u = members.get(i);
            if(u == null) continue;

            sx += u.x;
            sy += u.y;

            if(u.x < leftX){
                leftX = u.x;
                leftY = u.y;
            }
            if(u.x > rightX){
                rightX = u.x;
                rightY = u.y;
            }
            if(u.y > topY){
                topX = u.x;
                topY = u.y;
            }
            if(u.y < bottomY){
                bottomX = u.x;
                bottomY = u.y;
            }

            float hitRadius = u.hitSize / 2f;
            maxHitRadiusWorld = Math.max(maxHitRadiusWorld, hitRadius);

            if(u.isFlying()){
                flying++;
                hasAir = true;
                sumSqAir += hitRadius * hitRadius;
                minSpeedAir = Math.min(minSpeedAir, u.speed());
                minHpAir = Math.min(minHpAir, u.health());
            }else{
                hasGround = true;
                sumSqGround += hitRadius * hitRadius;
                minSpeedGround = Math.min(minSpeedGround, u.speed());
                minHpGround = Math.min(minHpGround, u.health());
            }
        }

        float cx = sx / Math.max(1, members.size);
        float cy = sy / Math.max(1, members.size);

        float formationGround = hasGround ? (float)Math.sqrt(Math.max(0f, sumSqGround)) : 0f;
        float formationAir = hasAir ? (float)Math.sqrt(Math.max(0f, sumSqAir)) : 0f;
        float formation = Math.max(formationGround, formationAir);

        // Approximate formation collision "radius" from total collision area.
        // This helps keep every unit in the group away from turret boundaries, not just the center point.
        float threatClearanceWorld = Math.max(maxHitRadiusWorld, formation * formationInflate());
        if(!Float.isFinite(threatClearanceWorld)) threatClearanceWorld = maxHitRadiusWorld;

        int threatMode;
        boolean moveFlying;
        boolean threatsAir, threatsGround;

        if(flying == members.size){
            threatMode = threatModeAir;
            moveFlying = true;
            threatsAir = true;
            threatsGround = false;
        }else if(flying == 0){
            threatMode = threatModeGround;
            moveFlying = false;
            threatsAir = false;
            threatsGround = true;
        }else{
            threatMode = threatModeBoth;
            // Mixed: keep paths compatible with ground units.
            moveFlying = false;
            threatsAir = true;
            threatsGround = true;
        }

        Unit moveUnit = null;
        for(int i = 0; i < members.size; i++){
            Unit u = members.get(i);
            if(u == null) continue;
            if(moveUnit == null){
                moveUnit = u;
            }else if(!moveFlying && moveUnit.isFlying() && !u.isFlying()){
                // Prefer a ground unit for ground movement constraints.
                moveUnit = u;
            }
        }
        if(moveUnit == null) return null;

        float moveSpeed;
        if(moveFlying){
            moveSpeed = hasAir ? minSpeedAir : moveUnit.speed();
        }else{
            moveSpeed = hasGround ? minSpeedGround : moveUnit.speed();
        }

        if(!Float.isFinite(minSpeedGround)) minSpeedGround = Float.NaN;
        if(!Float.isFinite(minHpGround)) minHpGround = Float.NaN;
        if(!Float.isFinite(minSpeedAir)) minSpeedAir = Float.NaN;
        if(!Float.isFinite(minHpAir)) minHpAir = Float.NaN;

        return new ControlledCluster(members, key, cx, cy, moveUnit, moveSpeed, threatMode, moveFlying, threatsAir, threatsGround,
            hasGround, minSpeedGround, minHpGround,
            hasAir, minSpeedAir, minHpAir,
            leftX, leftY, rightX, rightY, topX, topY, bottomX, bottomY,
            maxHitRadiusWorld, threatClearanceWorld);
    }

    private static int computeClusterKey(Seq<Unit> members){
        if(members == null || members.isEmpty()) return 0;

        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        long sum = 0L;
        int xor = 0;

        for(int i = 0; i < members.size; i++){
            Unit u = members.get(i);
            if(u == null) continue;
            int id = u.id;
            if(id < min) min = id;
            if(id > max) max = id;
            sum += id;
            xor ^= id;
        }

        int h = members.size;
        h = 31 * h + (min == Integer.MAX_VALUE ? 0 : min);
        h = 31 * h + (max == Integer.MIN_VALUE ? 0 : max);
        h = 31 * h + (int)(sum ^ (sum >>> 32));
        h = 31 * h + xor;
        return h;
    }

    private boolean includeUnitsFromLast(){
        return lastIncludeUnits;
    }

    private void cycleDisplayMode(){
        int current = Core.settings.getInt(keyTargetMode, targetModeCore);
        int next;

        if(current == targetModeGenCluster){
            next = targetModeCoreMouse;
        }else if(current == targetModeCoreMouse){
            next = lastCycleBaseMode;
            if(next == targetModeGenCluster || next == targetModeCoreMouse){
                next = targetModeCore;
            }
        }else{
            lastCycleBaseMode = current;
            next = targetModeGenCluster;
        }

        Core.settings.put(keyTargetMode, next);
        showToast(targetModeDisplay(next), 2f);
    }

    private void liveRefresh(Unit unit, boolean includeUnits){
        // throttle heavy work: only recompute on meaningful changes or at a low rate when units are included.
        int mode = Core.settings.getInt(keyTargetMode, targetModeCore);
        int threatMode = Core.settings.getInt(keyThreatMode, threatModeGround);

        float interval = Math.max(1f, Core.settings.getInt(keyPreviewRefresh, 6));

        int startX = worldToTile(unit.x);
        int startY = worldToTile(unit.y);
        int startPacked = startX + startY * world.width();

        int mouseX = clamp(worldToTile(Core.input.mouseWorldX()), 0, world.width() - 1);
        int mouseY = clamp(worldToTile(Core.input.mouseWorldY()), 0, world.height() - 1);
        int mousePacked = mouseX + mouseY * world.width();

        boolean changed = !liveRefreshWasDown
            || mode != liveLastMode
            || threatMode != liveLastThreatMode
            || includeUnits != liveLastIncludeUnits
            || startPacked != liveLastStartPacked
            || (mode == targetModeCoreMouse && mousePacked != liveLastMousePacked);

        if(includeUnits && Time.time >= liveNextCompute){
            changed = true;
        }

        if(!changed) return;
        if(Time.time < liveNextCompute) return;

        computePath(includeUnits, false);
        drawUntil = Float.POSITIVE_INFINITY;

        liveRefreshWasDown = true;
        liveLastMode = mode;
        liveLastThreatMode = threatMode;
        liveLastIncludeUnits = includeUnits;
        liveLastStartPacked = startPacked;
        liveLastMousePacked = mousePacked;

        liveNextCompute = Time.time + interval;
    }

    private void cycleThreatMode(){
        int cur = Core.settings.getInt(keyThreatMode, threatModeGround);
        int next;
        if(cur == threatModeGround){
            next = threatModeAir;
        }else if(cur == threatModeAir){
            next = threatModeBoth;
        }else{
            next = threatModeGround;
        }

        Core.settings.put(keyThreatMode, next);
        showToast(threatModeToast(next), 2f);
    }

    private static String threatModeToast(int mode){
        switch(mode){
            case threatModeAir:
                return "@sp.toast.threatmode.air";
            case threatModeBoth:
                return "@sp.toast.threatmode.both";
            case threatModeGround:
            default:
                return "@sp.toast.threatmode.ground";
        }
    }

    private void draw(){
        if(!Core.settings.getBool(keyEnabled, true)) return;
        if(!state.isGame() || drawPaths.isEmpty()) return;

        Draw.draw(Layer.overlayUI + 0.01f, () -> {
            float baseWidth = Core.settings.getInt(keyPathWidth, 2);
            float stroke = baseWidth / Math.max(0.0001f, renderer.getDisplayScale());
            float alpha = Mathf.clamp(Core.settings.getInt(keyPathAlpha, 85) / 100f);

            Lines.stroke(stroke);

            float prevFontScale = Fonts.outline.getScaleX();
            float textScale = (Mathf.clamp(Core.settings.getInt(keyDamageTextScale, 60) / 100f) * 0.6f) / Math.max(0.0001f, renderer.getDisplayScale());
            Fonts.outline.getData().setScale(textScale);

            boolean showDamageText = Core.settings.getBool(keyShowDamageText, true);
            boolean labelAtEnd = Core.settings.getBool(keyDamageLabelAtEnd, false);
            float offsetScale = Mathf.clamp(Core.settings.getInt(keyDamageTextOffsetScale, 100) / 100f, 0f, 5f);
            boolean showEndpoints = Core.settings.getBool(keyShowEndpoints, true);
            float startDotScale = Mathf.clamp(Core.settings.getInt(keyStartDotScale, 220) / 100f, 0f, 10f);
            float endDotScale = Mathf.clamp(Core.settings.getInt(keyEndDotScale, 260) / 100f, 0f, 10f);

            for(int p = 0; p < drawPaths.size; p++){
                RenderPath path = drawPaths.get(p);
                if(path == null || path.points == null || path.points.isEmpty()) continue;

                Draw.color(path.color, alpha);

                Seq<Pos> pts = path.points;
                for(int i = 0; i < pts.size - 1; i++){
                    Pos a = pts.get(i);
                    Pos b = pts.get(i + 1);
                    if(a == null || b == null) continue;
                    Lines.line(a.x, a.y, b.x, b.y, false);
                }

                Pos start = pts.first();
                Pos end = pts.peek();
                if(showEndpoints){
                    if(start != null) Fill.circle(start.x, start.y, stroke * startDotScale);
                    if(end != null) Fill.circle(end.x, end.y, stroke * endDotScale);
                }

                if(showDamageText && path.damageText != null){
                    int labelIndex = labelAtEnd ? (pts.size - 1) : Math.min(pts.size - 1, Math.max(0, pts.size / 2));
                    Pos labelPos = pts.get(labelIndex);
                    if(labelPos != null){
                        float offset = Math.max(stroke * 10f, tilesize * 0.45f) * offsetScale;
                        Draw.color(path.color, alpha);
                        Fonts.outline.draw(path.damageText, labelPos.x, labelPos.y + offset, Align.center);
                    }
                }
            }

            Fonts.outline.getData().setScale(prevFontScale);
            Draw.reset();
        });
    }

    private void clearPaths(){
        drawPaths.clear();
        drawUntil = 0f;
        lastDamage = 0f;
    }

    private void computePath(boolean includeUnits, boolean showToasts){
        clearPaths();

        if(!state.isGame() || world == null || player == null || player.unit() == null){
            if(showToasts) showToast("@sp.toast.no-path", 2.5f);
            return;
        }

        Unit unit = player.unit();

        int threatMode = Core.settings.getInt(keyThreatMode, threatModeGround);
        boolean moveFlying = threatMode == threatModeAir;
        boolean threatsAir = threatMode == threatModeAir || threatMode == threatModeBoth;
        boolean threatsGround = threatMode == threatModeGround || threatMode == threatModeBoth;

        int mode = Core.settings.getInt(keyTargetMode, targetModeCore);
        if(mode == targetModeGenCluster){
            float r = unit.hitSize / 2f;
            ThreatMap map = buildThreatMap(unit, includeUnits, moveFlying, threatsAir, threatsGround, r, r);
            computeGenClusterPaths(unit, map, includeUnits, moveFlying, threatsAir, threatsGround, showToasts);
            return;
        }
        if(mode == targetModeCoreMouse){
            float r = unit.hitSize / 2f;
            ThreatMap map = buildThreatMap(unit, includeUnits, moveFlying, threatsAir, threatsGround, r, r);
            computePlayerToMousePath(unit, map, moveFlying, showToasts);
            return;
        }

        float r = unit.hitSize / 2f;
        ThreatMap map = buildThreatMap(unit, includeUnits, moveFlying, threatsAir, threatsGround, r, r);

        int startX = worldToTile(unit.x);
        int startY = worldToTile(unit.y);
        if(!inBounds(map, startX, startY)){
            if(showToasts) showToast("@sp.toast.no-path", 2.5f);
            return;
        }

        if(!map.passable[startX + startY * map.width]){
            int startIdx = findNearestPassable(map, startX, startY, 8);
            if(startIdx == -1){
                if(showToasts) showToast("@sp.toast.no-path", 2.5f);
                return;
            }
            startX = startIdx % map.width;
            startY = startIdx / map.width;
        }

        Building target = findTarget(mode);
        if(target == null){
            if(showToasts) showToast("@sp.toast.no-target", 2.5f);
            return;
        }

        IntSeq goals = buildGoalTiles(map, unit, target, moveFlying);
        if(goals.isEmpty()){
            if(showToasts) showToast("@sp.toast.no-path", 2.5f);
            return;
        }

        boolean[] goalMask = new boolean[map.size];
        for(int i = 0; i < goals.size; i++){
            goalMask[goals.items[i]] = true;
        }

        float speed = unit.speed();
        PathResult safe = findPath(map, startX, startY, goals, goalMask, true, speed);
        PathResult result = safe != null ? safe : findPath(map, startX, startY, goals, goalMask, false, speed);

        if(result == null){
            if(showToasts) showToast("@sp.toast.no-path", 2.5f);
            return;
        }

        IntSeq compact = compactPath(result.path, map.width);
        float dmg = estimateDamage(map, result.path, unit);
        drawPaths.add(new RenderPath(toWorldPoints(compact, map.width, unit, target), safe != null ? Pal.heal : Pal.remove, dmg));
        lastDamage = dmg;

        int seconds = Core.settings.getInt(keyPathDuration, 10);
        drawUntil = seconds <= 0 ? Float.POSITIVE_INFINITY : Time.time + seconds * 60f;

        if(showToasts) showToast(Core.bundle.format("sp.toast.path", result.path.size, Strings.autoFixed(lastDamage, 1)), 3f);
    }

    private Seq<Building> anchorBuildings(){
        tmpBuildings.clear();
        if(world == null) return tmpBuildings;

        for(int y = 0; y < world.height(); y++){
            for(int x = 0; x < world.width(); x++){
                Tile tile = world.tile(x, y);
                if(tile == null) continue;
                Building b = tile.build;
                if(b == null) continue;
                if(b.tileX() != x || b.tileY() != y) continue;
                tmpBuildings.add(b);
            }
        }

        return tmpBuildings;
    }

    private Building findTarget(int mode){
        Block wanted = selectedTargetBlock();
        if(mode == targetModeBlock && wanted == null){
            // If no block is selected (or the saved block name is invalid), fall back to nearest enemy building.
            mode = targetModeNearest;
        }

        float px = player.unit().x;
        float py = player.unit().y;

        Seq<Building> builds = anchorBuildings();

        Building best = null;
        float bestDst2 = Float.POSITIVE_INFINITY;
        Building bestDerelict = null;
        float bestDerelictDst2 = Float.POSITIVE_INFINITY;

        for(int i = 0; i < builds.size; i++){
            Building b = builds.get(i);
            if(b == null) continue;
            if(b.team == player.team()) continue;

            boolean isDerelict = b.team == Team.derelict;

            if(mode == targetModeCore){
                if(!(b.block instanceof CoreBlock)) continue;
            }else if(mode == targetModeBlock){
                if(b.block != wanted) continue;
            }

            float dst2 = Mathf.dst2(px, py, b.x, b.y);
            if(isDerelict){
                if(dst2 < bestDerelictDst2){
                    bestDerelictDst2 = dst2;
                    bestDerelict = b;
                }
            }else{
                if(dst2 < bestDst2){
                    bestDst2 = dst2;
                    best = b;
                }
            }
        }

        if(best != null) return best;
        if(mode == targetModeNearest) return bestDerelict;

        // fallback: any enemy building
        bestDerelict = null;
        bestDerelictDst2 = Float.POSITIVE_INFINITY;
        for(int i = 0; i < builds.size; i++){
            Building b = builds.get(i);
            if(b == null) continue;
            if(b.team == player.team()) continue;

            boolean isDerelict = b.team == Team.derelict;

            float dst2 = Mathf.dst2(px, py, b.x, b.y);
            if(isDerelict){
                if(dst2 < bestDerelictDst2){
                    bestDerelictDst2 = dst2;
                    bestDerelict = b;
                }
            }else{
                if(dst2 < bestDst2){
                    bestDst2 = dst2;
                    best = b;
                }
            }
        }

        return best != null ? best : bestDerelict;
    }

    private void computeGenClusterPaths(Unit unit, ThreatMap map, boolean includeUnits, boolean moveFlying, boolean threatsAir, boolean threatsGround, boolean showToasts){
        int minSize = Math.max(2, Core.settings.getInt(keyGenClusterMinSize, 2));
        int maxPaths = Math.max(1, Core.settings.getInt(keyGenClusterMaxPaths, 3));
        boolean startFromPlayer = Core.settings.getBool(keyGenClusterStartFromCore, false);

        float startWorldX = unit.x;
        float startWorldY = unit.y;

        int startX = worldToTile(startWorldX);
        int startY = worldToTile(startWorldY);
        if(!inBounds(map, startX, startY)){
            if(showToasts) showToast("@sp.toast.no-path", 2.5f);
            return;
        }

        if(!map.passable[startX + startY * map.width]){
            int startIdx = findNearestPassable(map, startX, startY, 8);
            if(startIdx == -1){
                if(showToasts) showToast("@sp.toast.no-path", 2.5f);
                return;
            }
            startX = startIdx % map.width;
            startY = startIdx / map.width;
        }

        Seq<Seq<Building>> clusters = findEnemyGeneratorClusters(minSize);
        if(clusters.isEmpty()){
            if(showToasts) showToast("@sp.toast.no-target", 2.5f);
            return;
        }

        Seq<Building> turrets = collectEnemyTurretBuildings(unit, threatsAir, threatsGround);

        Seq<ClusterPath> candidates = new Seq<>();
        float speed = unit.speed();

        for(int i = 0; i < clusters.size; i++){
            Seq<Building> cluster = clusters.get(i);
            if(cluster == null || cluster.size < minSize) continue;

            Building target = pickClusterRepresentative(cluster);
            if(target == null) continue;

            IntSeq goals = buildGoalTiles(map, unit, target, moveFlying);
            if(goals.isEmpty()) continue;

            boolean[] goalMask = new boolean[map.size];
            for(int g = 0; g < goals.size; g++){
                goalMask[goals.items[g]] = true;
            }

            PathResult safe = findPath(map, startX, startY, goals, goalMask, true, speed);
            PathResult result = safe != null ? safe : findPath(map, startX, startY, goals, goalMask, false, speed);
            if(result == null) continue;

            float damage = estimateDamage(map, result.path, unit);
            candidates.add(new ClusterPath(target, result.path, safe != null, damage));
        }

        if(candidates.isEmpty()){
            if(showToasts) showToast("@sp.toast.no-path", 2.5f);
            return;
        }

        candidates.sort((a, b) -> {
            if(Math.abs(a.damage - b.damage) > 0.0001f){
                return a.damage < b.damage ? -1 : 1;
            }
            return Integer.compare(a.path.size, b.path.size);
        });

        int take = Math.min(maxPaths, candidates.size);
        float bestDamage = Float.POSITIVE_INFINITY;

        for(int i = 0; i < take; i++){
            ClusterPath cp = candidates.get(i);
            if(cp == null || cp.path == null || cp.path.isEmpty()) continue;

            IntSeq segment;
            if(startFromPlayer){
                segment = cp.path;
            }else{
                int startIndex = findPathStartIndexNearTurrets(cp.path, map.width, turrets);
                startIndex = Math.max(0, Math.min(cp.path.size - 1, startIndex));
                segment = new IntSeq();
                for(int s = startIndex; s < cp.path.size; s++){
                    segment.add(cp.path.items[s]);
                }
            }

            float dmg = estimateDamage(map, segment, unit);
            if(dmg < bestDamage) bestDamage = dmg;

            IntSeq compact = compactPath(segment, map.width);
            Seq<Pos> points = startFromPlayer
                ? toWorldPointsFromTilesWithStart(compact, map.width, startWorldX, startWorldY, cp.target)
                : toWorldPointsFromTiles(compact, map.width, cp.target);

            drawPaths.add(new RenderPath(points, genPathColor, dmg));
        }

        lastDamage = Float.isFinite(bestDamage) ? bestDamage : 0f;

        int seconds = Core.settings.getInt(keyPathDuration, 10);
        drawUntil = seconds <= 0 ? Float.POSITIVE_INFINITY : Time.time + seconds * 60f;

        if(showToasts) showToast(Core.bundle.format("sp.toast.gencluster.multi", take, Strings.autoFixed(lastDamage, 1)), 3f);
    }

    private void computePlayerToMousePath(Unit unit, ThreatMap map, boolean moveFlying, boolean showToasts){
        int startX = worldToTile(unit.x);
        int startY = worldToTile(unit.y);
        if(!inBounds(map, startX, startY)){
            if(showToasts) showToast("@sp.toast.no-path", 2.5f);
            return;
        }

        if(!map.passable[startX + startY * map.width]){
            int startIdx = findNearestPassable(map, startX, startY, 8);
            if(startIdx == -1){
                if(showToasts) showToast("@sp.toast.no-path", 2.5f);
                return;
            }
            startX = startIdx % map.width;
            startY = startIdx / map.width;
        }

        int goalX = clamp(worldToTile(Core.input.mouseWorldX()), 0, map.width - 1);
        int goalY = clamp(worldToTile(Core.input.mouseWorldY()), 0, map.height - 1);
        int goalIdx = findNearestPassable(map, goalX, goalY, 6);
        if(goalIdx == -1){
            if(showToasts) showToast("@sp.toast.no-path", 2.5f);
            return;
        }

        IntSeq goals = new IntSeq();
        goals.add(goalIdx);

        boolean[] goalMask = new boolean[map.size];
        goalMask[goalIdx] = true;

        float speed = unit.speed();
        PathResult safe = findPath(map, startX, startY, goals, goalMask, true, speed);
        PathResult result = safe != null ? safe : findPath(map, startX, startY, goals, goalMask, false, speed);

        if(result == null){
            if(showToasts) showToast("@sp.toast.no-path", 2.5f);
            return;
        }

        IntSeq compact = compactPath(result.path, map.width);
        float dmg = estimateDamage(map, result.path, unit);
        drawPaths.add(new RenderPath(toWorldPointsFromTilesWithStart(compact, map.width, unit.x, unit.y, null), mousePathColor, dmg));

        lastDamage = dmg;

        int seconds = Core.settings.getInt(keyPathDuration, 10);
        drawUntil = seconds <= 0 ? Float.POSITIVE_INFINITY : Time.time + seconds * 60f;

        if(showToasts) showToast(Core.bundle.format("sp.toast.path", result.path.size, Strings.autoFixed(lastDamage, 1)), 3f);
    }

    private ShiftedPath computeClusterToGoal(ControlledCluster cluster, ThreatMap map, int goalX, int goalY, boolean showToasts){
        if(showToasts) clearPaths();

        ShiftedPath sp = findBestShiftedClusterPath(cluster, map, goalX, goalY);
        if(sp == null || sp.path == null || sp.path.isEmpty()){
            if(showToasts) showToast("@sp.toast.no-path", 2.5f);
            return null;
        }

        IntSeq compact = compactPath(sp.path, map.width);

        Color c = autoPathColor(cluster, sp.dmgGround, sp.dmgAir, sp.maxDmg);
        drawPaths.add(new RenderPath(toWorldPointsFromTilesWithStart(compact, map.width, cluster.x + sp.dx, cluster.y + sp.dy, null), c, sp.maxDmg));

        if(showToasts){
            lastDamage = sp.maxDmg;
            int seconds = Core.settings.getInt(keyPathDuration, 10);
            drawUntil = seconds <= 0 ? Float.POSITIVE_INFINITY : Time.time + seconds * 60f;
            showToast(Core.bundle.format("sp.toast.path", sp.path.size, Strings.autoFixed(lastDamage, 1)), 3f);
        }

        return sp;
    }

    private ShiftedPath findBestShiftedClusterPath(ControlledCluster cluster, ThreatMap map, int goalX, int goalY){
        if(cluster == null || map == null) return null;

        goalX = clamp(goalX, 0, map.width - 1);
        goalY = clamp(goalY, 0, map.height - 1);
        int goalIdx = findNearestPassable(map, goalX, goalY, 6);
        if(goalIdx == -1) return null;

        IntSeq goals = new IntSeq();
        goals.add(goalIdx);
        boolean[] goalMask = new boolean[map.size];
        goalMask[goalIdx] = true;

        int startX = clamp(worldToTile(cluster.x), 0, map.width - 1);
        int startY = clamp(worldToTile(cluster.y), 0, map.height - 1);
        if(!inBounds(map, startX, startY)) return null;
        if(!map.passable[startX + startY * map.width]){
            int startIdx = findNearestPassable(map, startX, startY, 10);
            if(startIdx == -1) return null;
            startX = startIdx % map.width;
            startY = startIdx / map.width;
        }

        float speed = Math.max(0.0001f, cluster.speed);
        PathResult safe = findPath(map, startX, startY, goals, goalMask, true, speed);
        PathResult base = safe != null ? safe : findPath(map, startX, startY, goals, goalMask, false, speed);
        if(base == null || base.path == null || base.path.isEmpty()) return null;

        // Plan from center first, then try small offsets to keep the whole formation's collision volume safe.
        float offset = Mathf.clamp(cluster.maxHitRadiusWorld, tilesize * 0.5f, tilesize * 3f);
        float[] dxs = new float[]{0f, -offset, offset, 0f, 0f};
        float[] dys = new float[]{0f, 0f, 0f, offset, -offset};

        ShiftedPath best = null;

        for(int i = 0; i < dxs.length; i++){
            float dx = dxs[i];
            float dy = dys[i];

            IntSeq shifted = shiftTilePath(map, base.path, dx, dy);
            if(shifted == null || shifted.isEmpty()) continue;

            float dmgGround = cluster.hasGround ? estimateDamage(map, shifted, cluster.minSpeedGround) : Float.NaN;
            float dmgAir = cluster.hasAir ? estimateDamage(map, shifted, cluster.minSpeedAir) : Float.NaN;

            float maxDmg = 0f;
            if(cluster.hasGround && Float.isFinite(dmgGround)) maxDmg = Math.max(maxDmg, dmgGround);
            if(cluster.hasAir && Float.isFinite(dmgAir)) maxDmg = Math.max(maxDmg, dmgAir);

            short minSafeDist = 0;
            if(map.safeDist != null){
                short min = Short.MAX_VALUE;
                for(int s = 0; s < shifted.size; s++){
                    int tidx = shifted.items[s];
                    short d = map.safeDist[tidx];
                    if(d < min) min = d;
                }
                minSafeDist = min == Short.MAX_VALUE ? 0 : min;
            }

            if(best == null
                || maxDmg + 0.0001f < best.maxDmg
                || (Math.abs(maxDmg - best.maxDmg) <= 0.0001f && (minSafeDist > best.minSafeDist || (minSafeDist == best.minSafeDist && shifted.size < best.path.size)))){
                best = new ShiftedPath(shifted, dx, dy, dmgGround, dmgAir, maxDmg, minSafeDist);
            }
        }

        return best;
    }

    private static IntSeq shiftTilePath(ThreatMap map, IntSeq base, float dx, float dy){
        if(map == null || base == null || base.isEmpty()) return null;

        IntSeq out = new IntSeq(base.size);
        int last = Integer.MIN_VALUE;

        for(int i = 0; i < base.size; i++){
            int idx = base.items[i];
            int tx = idx % map.width;
            int ty = idx / map.width;

            float wx = tileToWorld(tx) + tilesize / 2f + dx;
            float wy = tileToWorld(ty) + tilesize / 2f + dy;

            int ntx = clamp(worldToTile(wx), 0, map.width - 1);
            int nty = clamp(worldToTile(wy), 0, map.height - 1);
            int nidx = ntx + nty * map.width;

            if(!map.passable[nidx]) return null;

            if(nidx != last){
                out.add(nidx);
                last = nidx;
            }
        }

        return out;
    }

    private void issueRtsMoveAlongPath(ControlledCluster cluster, IntSeq tilePath, int width){
        if(tilePath == null || tilePath.isEmpty()) return;
        if(player == null || player.unit() == null) return;
        if(control == null || control.input == null || control.input.selectedUnits == null || !control.input.selectedUnits.any()) return;
        if(cluster == null || cluster.units == null || cluster.units.isEmpty()) return;

        Seq<Unit> units = new Seq<>();
        for(int i = 0; i < cluster.units.size; i++){
            Unit u = cluster.units.get(i);
            if(u == null) continue;
            if(u.team != player.team()) continue;
            if(!u.isAdded() || u.dead()) continue;
            units.add(u);
        }
        if(units.isEmpty()) return;

        IntSeq compact = compactPath(tilePath, width);
        if(compact.isEmpty()) compact = tilePath;

        int maxWaypoints = rtsMaxWaypoints();
        int step = Math.max(1, compact.size / maxWaypoints);

        Seq<Vec2> waypoints = new Seq<>();
        for(int i = 0; i < compact.size; i += step){
            int idx = compact.items[i];
            int tx = idx % width;
            int ty = idx / width;
            waypoints.add(new Vec2(tileToWorld(tx) + tilesize / 2f, tileToWorld(ty) + tilesize / 2f));
        }

        // Do not include a waypoint at the path's start; this can cause queued RTS paths to stall.
        if(waypoints.size > 1){
            waypoints.remove(0);
        }

        int lastIdx = compact.items[compact.size - 1];
        int lastTx = lastIdx % width;
        int lastTy = lastIdx / width;
        Vec2 end = new Vec2(tileToWorld(lastTx) + tilesize / 2f, tileToWorld(lastTy) + tilesize / 2f);
        if(waypoints.isEmpty() || waypoints.peek().dst2(end) > 0.1f){
            waypoints.add(end);
        }

        if(waypoints.isEmpty()) return;

        int commandId = ++autoMoveCommandId;
        for(int i = 0; i < units.size; i++){
            autoMoveCommandByUnit.put(units.get(i).id, commandId);
        }

        // If the selected group is very large, stagger movement in a few batches.
        // This helps keep the formation narrower so units don't drift into turret ranges while following the same path.
        int count = units.size;
        float formationTiles = cluster != null && Float.isFinite(cluster.threatClearanceWorld) ? (cluster.threatClearanceWorld / tilesize) : 0f;

        int maxPerBatch;
        float delayBetween;

        boolean batching = Core.settings.getBool(keyAutoBatchEnabled, true);
        if(!batching){
            maxPerBatch = count;
            delayBetween = 0f;
        }else{
            if(count >= 24 || formationTiles >= 6f){
                maxPerBatch = 8;
                delayBetween = 45f;
            }else if(count >= 16 || formationTiles >= 5f){
                maxPerBatch = 10;
                delayBetween = 35f;
            }else if(count >= 12 || formationTiles >= 4f){
                maxPerBatch = 12;
                delayBetween = 25f;
            }else{
                maxPerBatch = count;
                delayBetween = 0f;
            }

            float sizeScale = clamp(Core.settings.getInt(keyAutoBatchSizePct, 100), 10, 500) / 100f;
            float delayScale = clamp(Core.settings.getInt(keyAutoBatchDelayPct, 100), 0, 500) / 100f;

            maxPerBatch = Math.max(1, Math.round(maxPerBatch * sizeScale));
            delayBetween *= delayScale;

            if(maxPerBatch >= count){
                maxPerBatch = count;
                delayBetween = 0f;
            }
        }

        int batches = Math.max(1, (count + maxPerBatch - 1) / maxPerBatch);

        // Move bigger units first to reduce blocking in tight corridors.
        if(batches > 1){
            units.sort((a, b) -> Float.compare(b.hitSize, a.hitSize));
        }

        for(int b = 0; b < batches; b++){
            int from = b * maxPerBatch;
            int to = Math.min(count, from + maxPerBatch);
            if(from >= to) break;

            int[] ids = new int[to - from];
            for(int i = from; i < to; i++){
                ids[i - from] = units.get(i).id;
            }

            float delay = b * delayBetween;
            int[] batchIds = ids;

            // Send the first waypoint quickly, then drip-feed queued waypoints to avoid triggering server DoS protections.
            scheduleRtsCommand(delay, commandId, batchIds, waypoints.first(), false);

            if(waypoints.size > 1){
                Time.run(0f, () -> {
                    for(int i = 1; i < waypoints.size; i++){
                        scheduleRtsCommand(delay, commandId, batchIds, waypoints.get(i), true);
                    }
                });
            }
        }
    }

    private void scheduleRtsCommand(float baseDelayFrames, int commandId, int[] unitIds, Vec2 waypoint, boolean queue){
        float spacing = rtsCommandSpacing();
        float now = Time.time;
        float earliest = now + Math.max(0f, baseDelayFrames);
        float scheduled = Math.max(earliest, rtsSendCursor);
        rtsSendCursor = scheduled + spacing;

        float delay = scheduled - now;
        if(delay <= 0.001f){
            runRtsCommand(commandId, unitIds, waypoint, queue);
        }else{
            Time.run(delay, () -> runRtsCommand(commandId, unitIds, waypoint, queue));
        }
    }

    private void runRtsCommand(int commandId, int[] unitIds, Vec2 waypoint, boolean queue){
        if(!state.isGame() || player == null) return;
        if(unitIds == null || unitIds.length == 0) return;
        for(int i = 0; i < unitIds.length; i++){
            if(autoMoveCommandByUnit.get(unitIds[i], -1) != commandId) return;
        }
        Call.commandUnits(player, unitIds, null, null, waypoint, queue, true);
    }

    private static int findNearestPassable(ThreatMap map, int x, int y, int radius){
        if(inBounds(map, x, y)){
            int idx = x + y * map.width;
            if(map.passable[idx]) return idx;
        }

        int best = -1;
        float bestDst2 = Float.POSITIVE_INFINITY;

        for(int dy = -radius; dy <= radius; dy++){
            for(int dx = -radius; dx <= radius; dx++){
                int nx = x + dx;
                int ny = y + dy;
                if(!inBounds(map, nx, ny)) continue;
                int nidx = nx + ny * map.width;
                if(!map.passable[nidx]) continue;

                float d2 = dx * dx + dy * dy;
                if(d2 < bestDst2){
                    bestDst2 = d2;
                    best = nidx;
                }
            }
        }

        return best;
    }

    private Seq<Seq<Building>> findEnemyGeneratorClusters(int minSize){
        Seq<Building> gens = new Seq<>();
        Seq<Building> derelictGens = new Seq<>();

        Seq<Building> builds = anchorBuildings();
        for(int i = 0; i < builds.size; i++){
            Building b = builds.get(i);
            if(b == null) continue;
            if(b.team == player.team()) continue;
            if(!(b.block instanceof PowerGenerator)) continue;
            if(isExcludedGenerator(b.block)) continue;

            if(b.team == Team.derelict){
                derelictGens.add(b);
            }else{
                gens.add(b);
            }
        }

        Seq<Seq<Building>> clusters = new Seq<>();
        if(gens.isEmpty()){
            gens = derelictGens;
        }
        if(gens.isEmpty()) return clusters;

        boolean[] visited = new boolean[gens.size];
        float maxDst2 = tilesize * genClusterLinkDistTiles();
        maxDst2 *= maxDst2;

        for(int i = 0; i < gens.size; i++){
            if(visited[i]) continue;
            visited[i] = true;

            Seq<Building> cluster = new Seq<>();
            cluster.add(gens.get(i));

            boolean changed = true;
            while(changed){
                changed = false;
                for(int a = 0; a < cluster.size; a++){
                    Building ba = cluster.get(a);
                    for(int j = 0; j < gens.size; j++){
                        if(visited[j]) continue;
                        Building bb = gens.get(j);
                        if(bb == null) continue;
                        if(Mathf.dst2(ba.x, ba.y, bb.x, bb.y) <= maxDst2){
                            visited[j] = true;
                            cluster.add(bb);
                            changed = true;
                        }
                    }
                }
            }

            if(cluster.size >= minSize){
                clusters.add(cluster);
            }
        }

        return clusters;
    }

    private static boolean isExcludedGenerator(Block block){
        if(block == null || block.name == null) return false;
        return excludedGeneratorCombustion.equals(block.name) || excludedGeneratorTurbine.equals(block.name);
    }

    private static Building pickClusterRepresentative(Seq<Building> cluster){
        if(cluster == null || cluster.isEmpty()) return null;

        float sx = 0f, sy = 0f;
        int count = 0;
        for(int i = 0; i < cluster.size; i++){
            Building b = cluster.get(i);
            if(b == null) continue;
            sx += b.x;
            sy += b.y;
            count++;
        }
        if(count <= 0) return null;

        float cx = sx / count;
        float cy = sy / count;

        Building best = null;
        float bestDst2 = Float.POSITIVE_INFINITY;
        for(int i = 0; i < cluster.size; i++){
            Building b = cluster.get(i);
            if(b == null) continue;
            float dst2 = Mathf.dst2(cx, cy, b.x, b.y);
            if(dst2 < bestDst2){
                bestDst2 = dst2;
                best = b;
            }
        }

        return best;
    }

    private Seq<Building> collectEnemyTurretBuildings(Unit playerUnit, boolean threatsAir, boolean threatsGround){
        Seq<Building> out = new Seq<>();
        Seq<Building> derelictOut = new Seq<>();

        Seq<Building> builds = anchorBuildings();
        for(int i = 0; i < builds.size; i++){
            Building b = builds.get(i);
            if(b == null) continue;
            if(b.team == player.team()) continue;
            if(!(b.block instanceof Turret)) continue;
            if(!(b instanceof Turret.TurretBuild)) continue;

            Turret turret = (Turret)b.block;
            Turret.TurretBuild tb = (Turret.TurretBuild)b;

            if(turret.targetHealing) continue;
            if(!((threatsAir && turret.targetAir) || (threatsGround && turret.targetGround))) continue;

            if(tb.estimateDps() <= 0.0001f) continue;

            if(b.team == Team.derelict){
                derelictOut.add(b);
            }else{
                out.add(b);
            }
        }

        return out.isEmpty() ? derelictOut : out;
    }

    private static int findPathStartIndexNearTurrets(IntSeq path, int width, Seq<Building> turrets){
        if(path == null || path.isEmpty()) return 0;
        if(turrets == null || turrets.isEmpty()) return 0;

        float near = tilesize * genClusterNearTurretDistTiles();
        float near2 = near * near;

        int earliestNear = -1;
        float bestDst2 = Float.POSITIVE_INFINITY;
        int closestIndex = Math.max(0, path.size - 1);

        for(int i = 0; i < path.size; i++){
            int tile = path.items[i];
            float wx = tileToWorld(tile % width);
            float wy = tileToWorld(tile / width);

            float minDst2 = Float.POSITIVE_INFINITY;
            for(int t = 0; t < turrets.size; t++){
                Building turret = turrets.get(t);
                if(turret == null) continue;
                float dst2 = Mathf.dst2(wx, wy, turret.x, turret.y);
                if(dst2 < minDst2){
                    minDst2 = dst2;
                }
            }

            if(minDst2 < bestDst2){
                bestDst2 = minDst2;
                closestIndex = i;
            }

            if(earliestNear == -1 && minDst2 <= near2){
                earliestNear = i;
            }
        }

        int start = earliestNear != -1 ? earliestNear : Math.max(0, closestIndex - genClusterFallbackBacktrackTiles());

        int minLen = Math.min(genClusterMinDrawTiles(), path.size);
        if(path.size - start < minLen){
            start = Math.max(0, path.size - minLen);
        }

        return start;
    }

    private void refreshGenPathColor(){
        String value = Core.settings.getString(keyGenPathColor, "3c7bff");
        if(!tryParseHexColor(value, genPathColor)){
            genPathColor.set(0.235f, 0.48f, 1f, 1f);
        }
    }

    private void refreshMousePathColor(){
        String value = Core.settings.getString(keyMousePathColor, "a27ce5");
        if(!tryParseHexColor(value, mousePathColor)){
            mousePathColor.set(0.635f, 0.486f, 0.898f, 1f);
        }
    }

    private static boolean tryParseHexColor(String text, Color out){
        if(text == null) return false;
        String value = text.trim();
        if(value.startsWith("#")){
            value = value.substring(1);
        }

        int len = value.length();
        if(len != 6 && len != 8) return false;

        try{
            long parsed = Long.parseLong(value, 16);
            if(len == 6){
                float r = ((parsed >> 16) & 0xff) / 255f;
                float g = ((parsed >> 8) & 0xff) / 255f;
                float b = (parsed & 0xff) / 255f;
                out.set(r, g, b, 1f);
            }else{
                float r = ((parsed >> 24) & 0xff) / 255f;
                float g = ((parsed >> 16) & 0xff) / 255f;
                float b = ((parsed >> 8) & 0xff) / 255f;
                float a = (parsed & 0xff) / 255f;
                out.set(r, g, b, a);
            }
            return true;
        }catch(Throwable ignored){
            return false;
        }
    }

    private static Seq<Pos> toWorldPoints(IntSeq tilePath, int width, Unit unit, Building target){
        Seq<Pos> out = new Seq<>();
        out.add(new Pos(unit.x, unit.y));

        for(int i = 0; i < tilePath.size; i++){
            int idx = tilePath.items[i];
            int tx = idx % width;
            int ty = idx / width;
            out.add(new Pos(tileToWorld(tx), tileToWorld(ty)));
        }

        out.add(new Pos(target.x, target.y));
        return out;
    }

    private static Seq<Pos> toWorldPointsFromTiles(IntSeq tilePath, int width, Building target){
        Seq<Pos> out = new Seq<>();
        if(tilePath == null || tilePath.isEmpty()) return out;

        for(int i = 0; i < tilePath.size; i++){
            int idx = tilePath.items[i];
            int tx = idx % width;
            int ty = idx / width;
            out.add(new Pos(tileToWorld(tx), tileToWorld(ty)));
        }

        if(target != null){
            out.add(new Pos(target.x, target.y));
        }

        return out;
    }

    private static Seq<Pos> toWorldPointsFromTilesWithStart(IntSeq tilePath, int width, float startWorldX, float startWorldY, Building target){
        Seq<Pos> out = new Seq<>();
        out.add(new Pos(startWorldX, startWorldY));

        if(tilePath != null && !tilePath.isEmpty()){
            for(int i = 0; i < tilePath.size; i++){
                int idx = tilePath.items[i];
                int tx = idx % width;
                int ty = idx / width;
                out.add(new Pos(tileToWorld(tx), tileToWorld(ty)));
            }
        }

        if(target != null){
            out.add(new Pos(target.x, target.y));
        }

        return out;
    }

    private static float tileToWorld(int tile){
        return tile * tilesize;
    }

    private static int worldToTile(float world){
        return Math.round(world / tilesize);
    }

    private static boolean inBounds(ThreatMap map, int x, int y){
        return x >= 0 && y >= 0 && x < map.width && y < map.height;
    }

    private ThreatMap buildThreatMap(Unit unit, boolean includeUnits, boolean moveFlying, boolean threatsAir, boolean threatsGround, float passClearanceWorld, float threatClearanceWorld){
        ThreatMap map = new ThreatMap(world.width(), world.height());
        map.safeBias = Mathf.clamp(threatClearanceWorld / tilesize, 0f, 10f);

        fillPassable(map, unit, moveFlying, passClearanceWorld);
        applyShieldNoGoZones(map, passClearanceWorld);

        Seq<Threat> threats = collectThreats(unit, includeUnits, threatsAir, threatsGround);
        if(threats.isEmpty()){
            return map;
        }

        float threatInflate = Math.max(0f, threatClearanceWorld);

        for(int i = 0; i < threats.size; i++){
            Threat t = threats.get(i);
            if(t.dps <= 0.0001f || t.range <= 0.0001f) continue;

            float r = t.range + threatInflate;
            float r2 = r * r;
            float mr = Math.max(0f, t.minRange);
            float mr2 = mr * mr;

            int minX = clamp((int)Math.ceil((t.x - r) / tilesize), 0, map.width - 1);
            int maxX = clamp((int)Math.floor((t.x + r) / tilesize), 0, map.width - 1);
            int minY = clamp((int)Math.ceil((t.y - r) / tilesize), 0, map.height - 1);
            int maxY = clamp((int)Math.floor((t.y + r) / tilesize), 0, map.height - 1);

            for(int ty = minY; ty <= maxY; ty++){
                float wy = tileToWorld(ty);
                for(int tx = minX; tx <= maxX; tx++){
                    float wx = tileToWorld(tx);
                    float dx = wx - t.x;
                    float dy = wy - t.y;
                    float d2 = dx * dx + dy * dy;
                    if(d2 <= r2 && d2 >= mr2){
                        map.risk[tx + ty * map.width] += t.dps;
                    }
                }
            }
        }

        if(computeSafeDistanceEnabled()){
            computeSafeDistance(map);
        }else{
            map.safeDist = null;
        }
        return map;
    }

    private static void computeSafeDistance(ThreatMap map){
        if(map == null || map.risk == null) return;

        short[] dist = new short[map.size];
        Arrays.fill(dist, (short)-1);

        int[] queue = new int[map.size];
        int head = 0, tail = 0;

        for(int i = 0; i < map.size; i++){
            if(map.risk[i] > 0.0001f){
                dist[i] = 0;
                queue[tail++] = i;
            }
        }

        if(tail == 0){
            map.safeDist = null;
            return;
        }

        while(head < tail){
            int idx = queue[head++];
            short d = dist[idx];
            int nd = d == Short.MAX_VALUE ? Short.MAX_VALUE : (d + 1);

            int x = idx % map.width;
            int y = idx / map.width;

            // 4-neighbor expansion is enough for a "center of corridor" bias.
            if(x > 0){
                int n = idx - 1;
                if(dist[n] == (short)-1){
                    dist[n] = (short)nd;
                    queue[tail++] = n;
                }
            }
            if(x < map.width - 1){
                int n = idx + 1;
                if(dist[n] == (short)-1){
                    dist[n] = (short)nd;
                    queue[tail++] = n;
                }
            }
            if(y > 0){
                int n = idx - map.width;
                if(dist[n] == (short)-1){
                    dist[n] = (short)nd;
                    queue[tail++] = n;
                }
            }
            if(y < map.height - 1){
                int n = idx + map.width;
                if(dist[n] == (short)-1){
                    dist[n] = (short)nd;
                    queue[tail++] = n;
                }
            }
        }

        map.safeDist = dist;
    }

    private void applyShieldNoGoZones(ThreatMap map, float clearanceWorld){
        if(map == null || map.passable == null) return;
        if(!state.isGame() || world == null || player == null) return;

        float inflate = Math.max(0f, clearanceWorld);

        Seq<Building> builds = anchorBuildings();
        for(int i = 0; i < builds.size; i++){
            Building b = builds.get(i);
            if(b == null) continue;
            if(b.team == player.team()) continue;
            if(!(b.block instanceof ForceProjector)) continue;
            if(!(b instanceof ForceProjector.ForceBuild)) continue;
            ForceProjector.ForceBuild fb = (ForceProjector.ForceBuild)b;

            float r = fb.realRadius() + inflate;
            if(r <= 0.001f) continue;

            float r2 = r * r;

            int minX = clamp((int)Math.ceil((b.x - r) / tilesize), 0, map.width - 1);
            int maxX = clamp((int)Math.floor((b.x + r) / tilesize), 0, map.width - 1);
            int minY = clamp((int)Math.ceil((b.y - r) / tilesize), 0, map.height - 1);
            int maxY = clamp((int)Math.floor((b.y + r) / tilesize), 0, map.height - 1);

            for(int ty = minY; ty <= maxY; ty++){
                float wy = tileToWorld(ty);
                for(int tx = minX; tx <= maxX; tx++){
                    float wx = tileToWorld(tx);
                    float dx = wx - b.x;
                    float dy = wy - b.y;
                    float d2 = dx * dx + dy * dy;
                    if(d2 <= r2){
                        map.passable[tx + ty * map.width] = false;
                    }
                }
            }
        }
    }

    private void fillPassable(ThreatMap map, Unit unit, boolean treatFlying, float clearanceWorld){
        if(map == null) return;
        if(treatFlying){
            Arrays.fill(map.passable, true);
            return;
        }

        if(world == null || unit == null || unit.type == null){
            Arrays.fill(map.passable, false);
            return;
        }

        if(passableCacheUsedRevision != passableCacheRevision){
            passableCacheByKey.clear();
            passableCacheKeyOrder.clear();
            passableCacheUsedRevision = passableCacheRevision;
        }

        int clearanceTiles = clearanceTiles(clearanceWorld);

        int key = unit.type.id;
        key = key * 31 + (unit.type.allowLegStep ? 1 : 0);
        key = key * 31 + (unit.type.naval ? 1 : 0);
        key = key * 31 + clearanceTiles;
        key = key * 31 + map.width;
        key = key * 31 + map.height;

        boolean[] cached = passableCacheByKey.get(key);
        if(cached != null && cached.length == map.size){
            System.arraycopy(cached, 0, map.passable, 0, map.size);
            return;
        }

        boolean[] computed = new boolean[map.size];

        for(int y = 0; y < map.height; y++){
            for(int x = 0; x < map.width; x++){
                int idx = x + y * map.width;
                boolean p = passableFor(unit, world.tile(x, y), false);
                if(p && clearanceTiles > 0){
                    for(int dy = -clearanceTiles; dy <= clearanceTiles && p; dy++){
                        for(int dx = -clearanceTiles; dx <= clearanceTiles; dx++){
                            int nx = x + dx;
                            int ny = y + dy;
                            if(nx < 0 || ny < 0 || nx >= map.width || ny >= map.height){
                                p = false;
                                break;
                            }
                            if(!passableFor(unit, world.tile(nx, ny), false)){
                                p = false;
                                break;
                            }
                        }
                    }
                }
                computed[idx] = p;
            }
        }

        passableCacheByKey.put(key, computed);
        passableCacheKeyOrder.add(key);

        int maxEntries = passableCacheEntries();
        while(passableCacheKeyOrder.size > maxEntries){
            int dropKey = passableCacheKeyOrder.removeIndex(0);
            passableCacheByKey.remove(dropKey);
        }

        System.arraycopy(computed, 0, map.passable, 0, map.size);
    }

    private static int clearanceTiles(float clearanceWorld){
        if(!Float.isFinite(clearanceWorld) || clearanceWorld <= 0.001f) return 0;
        return Math.max(0, Mathf.ceil(clearanceWorld / tilesize));
    }

    private static boolean passableFor(Unit unit, Tile tile, boolean treatFlying){
        if(tile == null) return false;
        if(treatFlying) return true;

        if(unit.type.allowLegStep){
            return !tile.legSolid();
        }

        if(unit.type.naval){
            return !tile.solid() && tile.floor().isLiquid;
        }

        return !tile.solid();
    }

    private Seq<Threat> collectThreats(Unit playerUnit, boolean includeUnits, boolean threatsAir, boolean threatsGround){
        Seq<Threat> out = new Seq<>();
        Seq<Threat> derelictOut = new Seq<>();

        Seq<Building> builds = anchorBuildings();
        for(int i = 0; i < builds.size; i++){
            Building b = builds.get(i);
            if(b == null) continue;
            if(b.team == player.team()) continue;
            if(!(b.block instanceof Turret)) continue;
            if(!(b instanceof Turret.TurretBuild)) continue;

            Turret turret = (Turret)b.block;
            Turret.TurretBuild tb = (Turret.TurretBuild)b;

            if(turret.targetHealing) continue;
            if(!((threatsAir && turret.targetAir) || (threatsGround && turret.targetGround))) continue;

            float dps = tb.estimateDps();
            if(dps <= 0.0001f) continue;

            Threat t = new Threat(b.x, b.y, tb.range(), tb.minRange(), dps);
            if(b.team == Team.derelict){
                derelictOut.add(t);
            }else{
                out.add(t);
            }
        }

        if(includeUnits){
            for(int i = 0; i < Groups.unit.size(); i++){
                Unit u = Groups.unit.index(i);
                if(u == null) continue;
                if(u.team == player.team()) continue;
                if(!u.isAdded() || u.dead()) continue;
                if(!((threatsAir && u.type.targetAir) || (threatsGround && u.type.targetGround))) continue;

                float range = u.range();
                if(range <= 0.0001f) continue;

                float dps = u.type.estimateDps();
                if(dps <= 0.0001f) continue;

                Threat t = new Threat(u.x, u.y, range, 0f, dps);
                if(u.team == Team.derelict){
                    derelictOut.add(t);
                }else{
                    out.add(t);
                }
            }
        }

        if(out.isEmpty()){
            out.addAll(derelictOut);
        }

        return out;
    }

    private static IntSeq buildGoalTiles(ThreatMap map, Unit unit, Building target, boolean moveFlying){
        IntSeq out = new IntSeq();
        if(target == null) return out;

        if(moveFlying){
            int tx = worldToTile(target.x);
            int ty = worldToTile(target.y);
            if(inBounds(map, tx, ty)){
                out.add(tx + ty * map.width);
            }
            return out;
        }

        int bx = target.tileX();
        int by = target.tileY();
        int size = Math.max(1, target.block.size);

        // Ring around building footprint.
        for(int x = bx - 1; x <= bx + size; x++){
            addGoalIfPassable(map, out, x, by - 1);
            addGoalIfPassable(map, out, x, by + size);
        }
        for(int y = by; y <= by + size - 1; y++){
            addGoalIfPassable(map, out, bx - 1, y);
            addGoalIfPassable(map, out, bx + size, y);
        }

        return out;
    }

    private static void addGoalIfPassable(ThreatMap map, IntSeq out, int x, int y){
        if(!inBounds(map, x, y)) return;
        int idx = x + y * map.width;
        if(!map.passable[idx]) return;
        out.add(idx);
    }

    private static int clamp(int value, int min, int max){
        return Math.max(min, Math.min(max, value));
    }

    private void ensurePathScratch(int size){
        if(pathBest == null || pathBest.length != size){
            pathBest = new float[size];
            pathBestStamp = new int[size];
            pathParent = new int[size];
            pathClosedStamp = new int[size];
        }
    }

    private int nextPathStamp(){
        pathStamp++;
        if(pathStamp == Integer.MAX_VALUE){
            if(pathBestStamp != null) Arrays.fill(pathBestStamp, 0);
            if(pathClosedStamp != null) Arrays.fill(pathClosedStamp, 0);
            pathStamp = 1;
        }
        return pathStamp;
    }

    private PathResult findPath(ThreatMap map, int startX, int startY, IntSeq goals, boolean[] goalMask, boolean safeOnly, float speed){
        int startIdx = startX + startY * map.width;
        if(!map.passable[startIdx]) return null;
        if(safeOnly && map.risk[startIdx] > 0.0001f) return null;

        ensurePathScratch(map.size);
        int stamp = nextPathStamp();

        pathOpen.clear();

        pathBestStamp[startIdx] = stamp;
        pathBest[startIdx] = 0f;
        pathParent[startIdx] = -1;
        pathOpen.add(new Node(startIdx, heuristic(map, startX, startY, goals, safeOnly), 0f));

        while(!pathOpen.isEmpty()){
            Node cur = pathOpen.poll();
            int idx = cur.idx;

            if(pathClosedStamp[idx] == stamp) continue;
            if(pathBestStamp[idx] != stamp || cur.g != pathBest[idx]) continue;
            pathClosedStamp[idx] = stamp;

            if(goalMask[idx]){
                return new PathResult(reconstruct(pathParent, idx));
            }

            int x = idx % map.width;
            int y = idx / map.width;

            for(int dy = -1; dy <= 1; dy++){
                for(int dx = -1; dx <= 1; dx++){
                    if(dx == 0 && dy == 0) continue;
                    int nx = x + dx;
                    int ny = y + dy;
                    if(nx < 0 || ny < 0 || nx >= map.width || ny >= map.height) continue;

                    int nidx = nx + ny * map.width;
                    if(pathClosedStamp[nidx] == stamp) continue;
                    if(!map.passable[nidx]) continue;
                    if(safeOnly && map.risk[nidx] > 0.0001f) continue;

                    // No cutting corners through blocked tiles.
                    if(dx != 0 && dy != 0){
                        int aidx = (x + dx) + y * map.width;
                        int bidx = x + (y + dy) * map.width;
                        if(!map.passable[aidx] || !map.passable[bidx]) continue;
                        if(safeOnly && (map.risk[aidx] > 0.0001f || map.risk[bidx] > 0.0001f)) continue;
                    }

                    float step = (dx == 0 || dy == 0) ? 1f : Mathf.sqrt2;

                    float ng;
                    if(safeOnly){
                        ng = pathBest[idx] + step;

                        // Prefer the center of safe corridors (farther from risk zones) when multiple safe paths exist.
                        if(map.safeDist != null && map.safeBias > 0.0001f){
                            int sd = map.safeDist[nidx];
                            // Larger safeDist => smaller penalty; bias scales with formation size.
                            float centerBias = map.safeBias * safeCorridorBiasFactor();
                            ng += centerBias / (Math.max(0f, sd) + 1f);
                        }
                    }else{
                        float distWorld = tilesize * step;
                        float avgRisk = (map.risk[idx] + map.risk[nidx]) * 0.5f;
                        float v = Math.max(0.0001f, speed);

                        // damage = DPS * seconds; seconds = dist / (speed * 60).
                        float dmg = avgRisk * (distWorld / (v * 60f));
                        float tie = step * 0.001f;
                        ng = pathBest[idx] + dmg + tie;
                    }

                    float prev = (pathBestStamp[nidx] == stamp) ? pathBest[nidx] : Float.POSITIVE_INFINITY;
                    if(ng >= prev) continue;

                    pathBestStamp[nidx] = stamp;
                    pathBest[nidx] = ng;
                    pathParent[nidx] = idx;

                    float h = heuristic(map, nx, ny, goals, safeOnly);
                    pathOpen.add(new Node(nidx, ng + h, ng));
                }
            }
        }

        return null;
    }

    private static float heuristic(ThreatMap map, int x, int y, IntSeq goals, boolean safeOnly){
        float best = Float.POSITIVE_INFINITY;
        for(int i = 0; i < goals.size; i++){
            int idx = goals.items[i];
            int gx = idx % map.width;
            int gy = idx / map.width;
            float dst = Mathf.dst(x, y, gx, gy);
            if(dst < best) best = dst;
        }

        if(!Float.isFinite(best)) return 0f;
        return safeOnly ? best : best * 0.001f;
    }

    private static IntSeq reconstruct(int[] parent, int endIdx){
        IntSeq out = new IntSeq();
        int cur = endIdx;
        while(cur != -1){
            out.add(cur);
            cur = parent[cur];
        }
        out.reverse();
        return out;
    }

    private static IntSeq compactPath(IntSeq path, int width){
        if(path == null || path.size <= 2) return path;

        IntSeq out = new IntSeq();
        out.add(path.items[0]);

        int prev = path.items[0];
        int prevDx = 0, prevDy = 0;

        for(int i = 1; i < path.size; i++){
            int cur = path.items[i];
            int px = prev % width;
            int py = prev / width;
            int cx = cur % width;
            int cy = cur / width;

            int dx = Integer.compare(cx, px);
            int dy = Integer.compare(cy, py);

            if(i == 1){
                prevDx = dx;
                prevDy = dy;
            }else if(dx != prevDx || dy != prevDy){
                out.add(prev);
                prevDx = dx;
                prevDy = dy;
            }

            prev = cur;
        }

        out.add(path.peek());
        return out;
    }

    private static int hashWaypointPath(IntSeq tilePath, int width){
        if(tilePath == null || tilePath.isEmpty()) return 0;

        IntSeq compact = compactPath(tilePath, width);
        if(compact == null || compact.isEmpty()) compact = tilePath;

        int maxWaypoints = rtsMaxWaypoints();
        int step = Math.max(1, compact.size / maxWaypoints);

        int h = 1;
        for(int i = 0; i < compact.size; i += step){
            h = 31 * h + compact.items[i];
        }

        int last = compact.items[compact.size - 1];
        h = 31 * h + last;
        h = 31 * h + compact.size;
        return h;
    }

    private static float estimateDamage(ThreatMap map, IntSeq tilePath, Unit unit){
        if(tilePath == null || tilePath.size <= 1) return 0f;

        float speed = Math.max(0.0001f, unit.speed());
        return estimateDamage(map, tilePath, speed);
    }

    private static float estimateDamage(ThreatMap map, IntSeq tilePath, float speed){
        if(tilePath == null || tilePath.size <= 1) return 0f;

        speed = Math.max(0.0001f, speed);
        float dmg = 0f;

        for(int i = 0; i < tilePath.size - 1; i++){
            int a = tilePath.items[i];
            int b = tilePath.items[i + 1];

            int ax = a % map.width;
            int ay = a / map.width;
            int bx = b % map.width;
            int by = b / map.width;

            int dx = Math.abs(bx - ax);
            int dy = Math.abs(by - ay);
            float step = (dx + dy == 1) ? 1f : Mathf.sqrt2;

            float distWorld = tilesize * step;
            float avgRisk = (map.risk[a] + map.risk[b]) * 0.5f;
            dmg += avgRisk * (distWorld / (speed * 60f));
        }

        return dmg;
    }

    private void showToast(String keyOrText, float seconds){
        if(ui == null) return;
        if(!Core.settings.getBool(keyShowToasts, true)) return;
        String text = keyOrText.startsWith("@") ? Core.bundle.get(keyOrText.substring(1)) : keyOrText;
        ui.showInfoToast(text, seconds);
    }

    private static class HeaderSetting extends SettingsMenuDialog.SettingsTable.Setting{
        private final String titleKeyOrText;
        private final arc.scene.style.Drawable icon;

        public HeaderSetting(String titleKeyOrText, arc.scene.style.Drawable icon){
            super("sp-header");
            this.titleKeyOrText = titleKeyOrText;
            this.icon = icon;
        }

        @Override
        public void add(SettingsMenuDialog.SettingsTable table){
            table.row();
            table.table(t -> {
                t.left();
                if(icon != null){
                    t.image(icon).size(18f).padRight(6f);
                }
                t.add(titleKeyOrText.startsWith("@") ? Core.bundle.get(titleKeyOrText.substring(1)) : titleKeyOrText)
                    .color(Color.gray)
                    .left()
                    .growX()
                    .wrap();
            }).padTop(12f).padBottom(4f).left().growX();
            table.row();
        }
    }

    private static float prefWidth(){
        return Math.min(Core.graphics.getWidth() / 1.2f, 560f);
    }

    private static class IconCheckSetting extends SettingsMenuDialog.SettingsTable.Setting{
        private final boolean def;
        private final Drawable icon;
        private final Cons<Boolean> changed;

        public IconCheckSetting(String name, boolean def, Drawable icon, Cons<Boolean> changed){
            super(name);
            this.def = def;
            this.icon = icon;
            this.changed = changed;
        }

        @Override
        public void add(SettingsMenuDialog.SettingsTable table){
            CheckBox box = new CheckBox(title);
            box.getLabel().setWrap(true);
            box.getLabelCell().growX().minWidth(0f);

            box.update(() -> box.setChecked(Core.settings.getBool(name, def)));

            box.changed(() -> {
                Core.settings.put(name, box.isChecked());
                if(changed != null) changed.get(box.isChecked());
            });

            table.table(Tex.button, t -> {
                t.left().margin(10f);
                if(icon != null) t.image(icon).size(20f).padRight(8f);
                t.add(box).left().growX().minWidth(0f);
            }).width(prefWidth()).left().padTop(6f);

            addDesc(box);
            table.row();
        }
    }

    private static class IconSliderSetting extends SettingsMenuDialog.SettingsTable.Setting{
        private final int def, min, max, step;
        private final Drawable icon;
        private final SettingsMenuDialog.StringProcessor sp;
        private final arc.func.Intc changed;

        public IconSliderSetting(String name, int def, int min, int max, int step, Drawable icon, SettingsMenuDialog.StringProcessor sp, arc.func.Intc changed){
            super(name);
            this.def = def;
            this.min = min;
            this.max = max;
            this.step = step;
            this.icon = icon;
            this.sp = sp;
            this.changed = changed;
        }

        @Override
        public void add(SettingsMenuDialog.SettingsTable table){
            Slider slider = new Slider(min, max, step, false);
            slider.setValue(Core.settings.getInt(name, def));

            Label value = new Label("", Styles.outlineLabel);
            Table content = new Table();
            content.left();
            if(icon != null) content.image(icon).size(20f).padRight(8f);
            content.add(title, Styles.outlineLabel).left().growX().minWidth(0f).wrap();
            content.add(value).padLeft(10f).right();
            content.margin(3f, 16f, 3f, 16f);
            content.touchable = Touchable.disabled;

            slider.changed(() -> {
                int v = (int)slider.getValue();
                Core.settings.put(name, v);
                value.setText(sp == null ? String.valueOf(v) : sp.get(v));
                if(changed != null) changed.get(v);
            });

            slider.change();

            addDesc(table.stack(slider, content).width(prefWidth()).left().padTop(6f).get());
            table.row();
        }
    }

    private static class IconTextSetting extends SettingsMenuDialog.SettingsTable.Setting{
        private final String def;
        private final Drawable icon;
        private final Cons<String> changed;

        public IconTextSetting(String name, String def, Drawable icon, Cons<String> changed){
            super(name);
            this.def = def;
            this.icon = icon;
            this.changed = changed;
        }

        @Override
        public void add(SettingsMenuDialog.SettingsTable table){
            final TextField[] fieldRef = {null};
            table.table(Tex.button, t -> {
                t.left().margin(10f);
                if(icon != null) t.image(icon).size(20f).padRight(8f);

                t.add(title).left().growX().minWidth(0f).wrap();

                TextField field = t.field(Core.settings.getString(name, def), text -> {
                    Core.settings.put(name, text);
                    if(changed != null) changed.get(text);
                }).growX().minWidth(140f).get();

                field.setMessageText(def);
                fieldRef[0] = field;
            }).width(prefWidth()).left().padTop(6f);

            if(fieldRef[0] != null) addDesc(fieldRef[0]);
            table.row();
        }
    }

    private void onChatMessage(String message){
        if(message == null) return;
        String text = message.trim();
        if(text.isEmpty()) return;

        if(world == null) return;

        Matcher m = coordPattern.matcher(text);
        if(!m.find()) return;

        try{
            int x = Integer.parseInt(m.group(1));
            int y = Integer.parseInt(m.group(2));

            x = clamp(x, 0, world.width() - 1);
            y = clamp(y, 0, world.height() - 1);

            bufferedTargetX = x;
            bufferedTargetY = y;
            bufferedTargetPacked = x + y * world.width();

            // Preserve old <Attack> fields for compatibility, but buffer drives M mode now.
            attackTargetX = x;
            attackTargetY = y;
            attackTargetPacked = bufferedTargetPacked;

            if(autoMode == autoModeAttack){
                autoNextCompute = 0f;
            }
        }catch(Throwable ignored){
            // ignore
        }
    }

    private void refreshAutoColors(){
        String safe = Core.settings.getString(keyAutoColorSafe, "34c759");
        String warn = Core.settings.getString(keyAutoColorWarn, "ffd60a");
        String dead = Core.settings.getString(keyAutoColorDead, "ff3b30");

        if(!tryParseHexColor(safe, autoSafeColor)){
            autoSafeColor.set(0.2f, 0.78f, 0.35f, 1f);
        }
        if(!tryParseHexColor(warn, autoWarnColor)){
            autoWarnColor.set(1f, 0.84f, 0.0f, 1f);
        }
        if(!tryParseHexColor(dead, autoDeadColor)){
            autoDeadColor.set(1f, 0.23f, 0.19f, 1f);
        }
    }

    private Color autoPathColor(ControlledCluster cluster, float dmgGround, float dmgAir, float maxDmg){
        float safeThresh = Math.max(0f, Core.settings.getInt(keyAutoSafeDamageThreshold, 10));
        if(maxDmg < safeThresh){
            return autoSafeColor;
        }

        boolean groundDead = cluster.hasGround && Float.isFinite(dmgGround) && Float.isFinite(cluster.minHpGround) && dmgGround >= cluster.minHpGround;
        boolean airDead = cluster.hasAir && Float.isFinite(dmgAir) && Float.isFinite(cluster.minHpAir) && dmgAir >= cluster.minHpAir;

        boolean anySurvive = (cluster.hasGround && !groundDead) || (cluster.hasAir && !airDead);
        return anySurvive ? autoWarnColor : autoDeadColor;
    }

    private static class Pos{
        final float x, y;

        Pos(float x, float y){
            this.x = x;
            this.y = y;
        }
    }

    private static class RenderPath{
        final Seq<Pos> points;
        final Color color;
        final String damageText;

        RenderPath(Seq<Pos> points, Color color, float damage){
            this.points = points == null ? new Seq<Pos>() : points;
            this.color = color == null ? Color.white : color;
            this.damageText = Float.isFinite(damage) ? Strings.autoFixed(Math.max(0f, damage), 1) : null;
        }
    }

    private static class Threat{
        final float x, y;
        final float range;
        final float minRange;
        final float dps;

        Threat(float x, float y, float range, float minRange, float dps){
            this.x = x;
            this.y = y;
            this.range = Math.max(0f, range);
            this.minRange = Math.max(0f, minRange);
            this.dps = Math.max(0f, dps);
        }
    }

    private static class ThreatMap{
        final int width, height, size;
        final boolean[] passable;
        final float[] risk;
        short[] safeDist;
        float safeBias;

        ThreatMap(int width, int height){
            this.width = width;
            this.height = height;
            this.size = width * height;
            this.passable = new boolean[size];
            this.risk = new float[size];
        }
    }

    private static class PathResult{
        final IntSeq path;

        PathResult(IntSeq path){
            this.path = path;
        }
    }

    private static class Node implements Comparable<Node>{
        final int idx;
        final float f;
        final float g;

        Node(int idx, float f, float g){
            this.idx = idx;
            this.f = f;
            this.g = g;
        }

        @Override
        public int compareTo(Node other){
            return Float.compare(this.f, other.f);
        }
    }

    private static class ClusterPath{
        final Building target;
        final IntSeq path;
        final boolean safe;
        final float damage;

        ClusterPath(Building target, IntSeq path, boolean safe, float damage){
            this.target = target;
            this.path = path;
            this.safe = safe;
            this.damage = damage;
        }
    }

    private static class ShiftedPath{
        final IntSeq path;
        final float dx, dy;
        final float dmgGround, dmgAir;
        final float maxDmg;
        final short minSafeDist;

        ShiftedPath(IntSeq path, float dx, float dy, float dmgGround, float dmgAir, float maxDmg, short minSafeDist){
            this.path = path;
            this.dx = dx;
            this.dy = dy;
            this.dmgGround = dmgGround;
            this.dmgAir = dmgAir;
            this.maxDmg = maxDmg;
            this.minSafeDist = minSafeDist;
        }
    }

    private static class ControlledCluster{
        final Seq<Unit> units;
        final int key;
        final float x, y;
        final Unit moveUnit;
        final float speed;
        final int threatMode;
        final boolean moveFlying;
        final boolean threatsAir, threatsGround;
        final boolean hasGround;
        final float minSpeedGround;
        final float minHpGround;
        final boolean hasAir;
        final float minSpeedAir;
        final float minHpAir;

        final float leftX, leftY;
        final float rightX, rightY;
        final float topX, topY;
        final float bottomX, bottomY;

        final float maxHitRadiusWorld;
        final float threatClearanceWorld;

        ControlledCluster(Seq<Unit> units, int key, float x, float y, Unit moveUnit, float speed, int threatMode, boolean moveFlying, boolean threatsAir, boolean threatsGround,
                          boolean hasGround, float minSpeedGround, float minHpGround,
                          boolean hasAir, float minSpeedAir, float minHpAir,
                          float leftX, float leftY, float rightX, float rightY, float topX, float topY, float bottomX, float bottomY,
                          float maxHitRadiusWorld, float threatClearanceWorld){
            this.units = units == null ? new Seq<>() : units;
            this.key = key;
            this.x = x;
            this.y = y;
            this.moveUnit = moveUnit;
            this.speed = speed;
            this.threatMode = threatMode;
            this.moveFlying = moveFlying;
            this.threatsAir = threatsAir;
            this.threatsGround = threatsGround;
            this.hasGround = hasGround;
            this.minSpeedGround = minSpeedGround;
            this.minHpGround = minHpGround;
            this.hasAir = hasAir;
            this.minSpeedAir = minSpeedAir;
            this.minHpAir = minHpAir;

            this.leftX = leftX;
            this.leftY = leftY;
            this.rightX = rightX;
            this.rightY = rightY;
            this.topX = topX;
            this.topY = topY;
            this.bottomX = bottomX;
            this.bottomY = bottomY;
            this.maxHitRadiusWorld = maxHitRadiusWorld;
            this.threatClearanceWorld = threatClearanceWorld;
        }
    }
}
