package stealthpath;

import arc.Core;
import arc.Events;
import arc.func.Cons;
import arc.func.Prov;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.input.KeyCode;
import arc.input.KeyBind;
import arc.math.Mathf;
import arc.scene.Element;
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
import arc.util.Log;
import arc.util.Strings;
import arc.util.Time;
import bektools.ui.VscodeSettingsStyle;
import mindustry.game.EventType.*;
import mindustry.game.Team;
import mindustry.ai.UnitCommand;
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
import mindustry.world.blocks.environment.Floor;
import mindustry.world.blocks.defense.ForceProjector;
import mindustry.world.blocks.defense.turrets.Turret;
import mindustry.world.blocks.power.PowerGenerator;
import mindustry.world.blocks.storage.CoreBlock;
import mindustry.world.meta.BuildVisibility;

import java.util.Arrays;
import java.util.Locale;
import java.util.PriorityQueue;
import java.lang.reflect.Method;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static stealthpath.StealthPathMathUtil.*;
import static stealthpath.StealthPathPathUtil.*;
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
    /** When true, this mod is running as a bundled component inside Neon. */
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
    private static final String keyDebugLogs = "sp-debug-logs";
    private static final String keyAutoClusterSplitTiles = "sp-auto-cluster-split-tiles";
    private static final String keyFormationInflatePct = "sp-formation-inflate-pct";
    private static final String keySafeCorridorBiasPct = "sp-safe-corridor-bias-pct";
    private static final String keyComputeSafeDistance = "sp-compute-safe-distance";
    private static final String keyRtsMaxWaypoints = "sp-rts-max-waypoints";
    private static final String keyRtsUpdateInterval = "sp-rts-update-interval";
    private static final String keyRtsCommandSpacing = "sp-rts-command-spacing";
    private static final String keyRtsWaypointReachTiles = "sp-rts-waypoint-reach-tiles";
    private static final String keyAutoBatchEnabled = "sp-auto-batch-enabled";
    private static final String keyAutoBatchSizePct = "sp-auto-batch-size-pct";
    private static final String keyAutoBatchDelayPct = "sp-auto-batch-delay-pct";
    private static final String keyAutoSlowMultiplier = "sp-auto-slow-multiplier";
    private static final String keyPassableCacheEntries = "sp-passable-cache-entries";
    private static final String keyGenClusterLinkDistTiles = "sp-gencluster-linkdist-tiles";
    private static final String keyGenClusterNearTurretDistTiles = "sp-gencluster-near-turret-dist-tiles";
    private static final String keyGenClusterMinDrawTiles = "sp-gencluster-min-draw-tiles";
    private static final String keyGenClusterFallbackBacktrackTiles = "sp-gencluster-fallback-backtrack-tiles";
    private static final String keyAlwaysPlanNearestPath = "sp-always-plan-nearest-path";
    private static final String keyPathComputeSplitTicks = "sp-path-compute-split-ticks";
    private static final String keyPathfinder = "sp-pathfinder";
    private static final String keyCoreTargetCount = "sp-core-target-count";
    private static final String keyPathUseSlowestUnit = "sp-path-use-slowest-unit";
    private static final String keyPathUseFloorStatusSlow = "sp-path-use-floor-status-slow";
    private static final String keyPathAllowSurvivableLiquidCross = "sp-path-allow-survivable-liquid-cross";
    private static final String keyDrownReserveDeciSeconds = "sp-drown-reserve-deciseconds";
    private static final String keyGoalCandidateRadiusTiles = "sp-goal-candidate-radius-tiles";

    private enum PathMode{
        safeOnly, minDamage, nearest
    }

    private static final float safeRiskEps = 1e-6f;

    private static final int pathfinderAStar = 0;
    private static final int pathfinderDfs = 1;

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
    private AutoComputeJob autoComputeJob;
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

    // Optional MindustryX OverlayUI integration (reflection; no hard dependency).
    private final MindustryXOverlayUI xOverlayUi = new MindustryXOverlayUI();
    private Object xModeWindow;
    private Object xDamageWindow;
    private Object xControlsWindow;
    private Table overlayModeContent;
    private Table overlayDamageContent;
    private Table overlayControlsContent;
    private float overlayNextAttachAttempt = 0f;

    private Label overlayModeValue;
    private Label overlayThreatValue;
    private Label overlayDamageValue;

    // Cached passability (expensive to recompute every frame).
    private final arc.struct.IntMap<boolean[]> passableCacheByKey = new arc.struct.IntMap<>();
    private final IntSeq passableCacheKeyOrder = new IntSeq();
    private int passableCacheRevision = 0;
    private int passableCacheUsedRevision = -1;

    // Reused ThreatMap + safe-distance scratch buffers to reduce allocations during auto mode.
    private ThreatMap threatMapScratch;
    private short[] safeDistScratch;
    private int[] safeDistQueueScratch;

    // Reused threat lists for building ThreatMap (sequential use only).
    private final Seq<Threat> tmpThreats = new Seq<>();
    private final Seq<Threat> tmpDerelictThreats = new Seq<>();

    // Scratch arrays for pathfinding (avoid allocating/filling map-sized arrays every search).
    private float[] pathBest;
    private int[] pathBestStamp;
    private int[] pathParent;
    private int[] pathClosedStamp;
    private int pathStamp = 1;
    private final PriorityQueue<Node> pathOpen = new PriorityQueue<>();
    private final IntSeq pathStack = new IntSeq();

    private static final Pattern coordPattern = Pattern.compile("\\((-?\\d+)\\s*,\\s*(-?\\d+)\\)");

    private static final class PlanningStart{
        final Unit unit;
        final Seq<Unit> pathUnits;
        final float worldX, worldY;
        final float passClearanceWorld;
        final float threatClearanceWorld;
        final float speed;

        PlanningStart(Unit unit, Seq<Unit> pathUnits, float worldX, float worldY, float passClearanceWorld, float threatClearanceWorld, float speed){
            this.unit = unit;
            this.pathUnits = pathUnits == null ? new Seq<>() : pathUnits;
            this.worldX = worldX;
            this.worldY = worldY;
            this.passClearanceWorld = passClearanceWorld;
            this.threatClearanceWorld = threatClearanceWorld;
            this.speed = speed;
        }
    }

    public StealthPathMod(){
        Events.on(ClientLoadEvent.class, e -> {
            ensureDefaults();
            registerKeybinds();
            registerSettings();
            refreshGenPathColor();
            refreshMousePathColor();
            refreshAutoColors();
            registerTriggers();
            if(!bekBundled) GithubUpdateCheck.checkOnce();
            // Try to attach OverlayUI windows (safe in vanilla; will fall back to HUD).
            Time.runTask(1f, this::ensureOverlayWindowsAttached);
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

    // Used by StealthPathPathUtil.hashWaypointPath() after extraction.
    static int rtsMaxWaypoints(){
        return clamp(Core.settings.getInt(keyRtsMaxWaypoints, 12), 2, 80);
    }

    private static float rtsUpdateInterval(){
        return Math.max(1f, Core.settings.getInt(keyRtsUpdateInterval, 30));
    }

    private static float rtsCommandSpacing(){
        return Math.max(0f, Core.settings.getInt(keyRtsCommandSpacing, 2));
    }

    private static float rtsWaypointReachTiles(){
        return clamp(Core.settings.getInt(keyRtsWaypointReachTiles, 2), 0, 12);
    }

    private static float drownReserveSeconds(){
        int deci = clamp(Core.settings.getInt(keyDrownReserveDeciSeconds, 15), 0, 30);
        return deci / 10f;
    }

    private static float drownReserveTicks(){
        return drownReserveSeconds() * 60f;
    }

    private static int goalCandidateRadiusTiles(){
        return clamp(Core.settings.getInt(keyGoalCandidateRadiusTiles, 24), 6, 64);
    }

    private static int autoClusterSplitTiles(){
        return clamp(Core.settings.getInt(keyAutoClusterSplitTiles, 5), 1, 40);
    }

    private static boolean alwaysPlanNearestPath(){
        return Core.settings.getBool(keyAlwaysPlanNearestPath, false);
    }

    private static int pathComputeSplitTicks(){
        return clamp(Core.settings.getInt(keyPathComputeSplitTicks, 1), 1, 120);
    }

    private static int pathfinderMode(){
        return clamp(Core.settings.getInt(keyPathfinder, pathfinderAStar), pathfinderAStar, pathfinderDfs);
    }

    private static int coreTargetCount(){
        return clamp(Core.settings.getInt(keyCoreTargetCount, 1), 1, 12);
    }

    private static boolean useSlowestUnitForPathCost(){
        return Core.settings.getBool(keyPathUseSlowestUnit, true);
    }

    private static boolean includeFloorStatusSlowdown(){
        return Core.settings.getBool(keyPathUseFloorStatusSlow, true);
    }

    private static boolean allowSurvivableLiquidCross(){
        return Core.settings.getBool(keyPathAllowSurvivableLiquidCross, true);
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

    private static boolean debugLogsEnabled(){
        return Core.settings.getBool(keyDebugLogs, true);
    }

    private static long elapsedMillis(long startedNano){
        return (System.nanoTime() - startedNano) / 1_000_000L;
    }

    private static String targetModeName(int mode){
        switch(mode){
            case targetModeNearest:
                return logText("sp.log.mode.nearest");
            case targetModeBlock:
                return logText("sp.log.mode.block");
            case targetModeGenCluster:
                return logText("sp.log.mode.gencluster");
            case targetModeCoreMouse:
                return logText("sp.log.mode.coremouse");
            case targetModeCore:
            default:
                return logText("sp.log.mode.core");
        }
    }

    private static String autoModeName(int mode){
        return mode == autoModeMouse ? logText("sp.log.automode.mouse") : logText("sp.log.automode.attack");
    }

    private static String pathModeName(PathMode mode){
        if(mode == PathMode.safeOnly) return logText("sp.log.pathmode.safe");
        if(mode == PathMode.minDamage) return logText("sp.log.pathmode.mindmg");
        if(mode == PathMode.nearest) return logText("sp.log.pathmode.nearest");
        return String.valueOf(mode);
    }

    private static String logText(String key){
        if(Core.bundle != null && Core.bundle.has(key)) return Core.bundle.get(key);
        return key;
    }

    private static String logFormat(String key, Object... args){
        if(Core.bundle != null && Core.bundle.has(key)) return Core.bundle.format(key, args);
        return key + " " + Arrays.toString(args);
    }

    private static String logCategoryColor(String category){
        if("PLAN".equals(category)) return "7aa2f7";
        if("AUTO".equals(category)) return "7ec699";
        if("RTS".equals(category)) return "f2cc60";
        if("DROWN".equals(category)) return "ff7b72";
        return "a9b1d6";
    }

    private static void debugLog(String category, String text){
        if(!debugLogsEnabled()) return;
        String tag = "[#7dcfff][StealthPath][] [#" + logCategoryColor(category) + "][" + category + "][]";
        Log.info(tag + " " + text);
    }

    private static void logPlan(String text){
        debugLog("PLAN", text);
    }

    private static void logAuto(String text){
        debugLog("AUTO", text);
    }

    private static void logRts(String text){
        debugLog("RTS", text);
    }

    private static void logDrown(String text){
        debugLog("DROWN", text);
    }

    private void invalidatePassableCache(){
        passableCacheRevision++;
    }

    private void ensureDefaults(){
        GithubUpdateCheck.applyDefaults();
        Core.settings.defaults(keyEnabled, false);
        Core.settings.defaults(keyProMode, false);
        Core.settings.defaults(keyAlwaysPlanNearestPath, false);
        Core.settings.defaults(keyPathfinder, pathfinderAStar);
        Core.settings.defaults(keyCoreTargetCount, 1);
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
        Core.settings.defaults(keyDebugLogs, true);
        Core.settings.defaults(keyAutoClusterSplitTiles, 5);
        Core.settings.defaults(keyFormationInflatePct, 125);
        Core.settings.defaults(keySafeCorridorBiasPct, 35);
        Core.settings.defaults(keyComputeSafeDistance, true);
        Core.settings.defaults(keyRtsMaxWaypoints, 12);
        Core.settings.defaults(keyRtsUpdateInterval, 30);
        Core.settings.defaults(keyRtsCommandSpacing, 2);
        Core.settings.defaults(keyRtsWaypointReachTiles, 2);
        Core.settings.defaults(keyAutoBatchEnabled, true);
        Core.settings.defaults(keyAutoBatchSizePct, 100);
        Core.settings.defaults(keyAutoBatchDelayPct, 100);
        Core.settings.defaults(keyAutoSlowMultiplier, 8);
        Core.settings.defaults(keyPathComputeSplitTicks, 1);
        Core.settings.defaults(keyPassableCacheEntries, 8);
        Core.settings.defaults(keyGenClusterLinkDistTiles, defaultGenClusterLinkDistTiles);
        Core.settings.defaults(keyGenClusterNearTurretDistTiles, defaultGenClusterNearTurretDistTiles);
        Core.settings.defaults(keyGenClusterMinDrawTiles, defaultGenClusterMinDrawTiles);
        Core.settings.defaults(keyGenClusterFallbackBacktrackTiles, defaultGenClusterFallbackBacktrackTiles);
        Core.settings.defaults(keyPathUseSlowestUnit, true);
        Core.settings.defaults(keyPathUseFloorStatusSlow, true);
        Core.settings.defaults(keyPathAllowSurvivableLiquidCross, true);
        Core.settings.defaults(keyDrownReserveDeciSeconds, 15);
        Core.settings.defaults(keyGoalCandidateRadiusTiles, 24);
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
            table.pref(new HeaderSetting("@sp.section.general", null));
            table.pref(new IconCheckSetting(keyEnabled, false, null, null));
            table.pref(new HeaderSetting("@sp.notice.partial-bug", null));
            table.pref(new IconCheckSetting(keyShowToasts, true, null, null));
            table.pref(new IconCheckSetting(keyDebugLogs, true, null, null));
            table.pref(new IconCheckSetting(keyProMode, false, null, null));
            table.pref(new IconCheckSetting(keyAlwaysPlanNearestPath, false, null, null));
            table.pref(new IconCheckSetting(keyPathUseSlowestUnit, true, null, null));
            table.pref(new IconCheckSetting(keyPathUseFloorStatusSlow, true, null, null));
            table.pref(new IconCheckSetting(keyPathAllowSurvivableLiquidCross, true, null, null));
            table.pref(new IconSliderSetting(keyPathDuration, 10, 0, 60, 5, null, v -> v == 0 ? "inf" : v + "s", null));
            table.pref(new IconSliderSetting(keyPathWidth, 2, 1, 6, 1, null, v -> String.valueOf(v), null));
            table.pref(new IconSliderSetting(keyPathAlpha, 85, 0, 100, 5, null, v -> v + "%", null));
            table.pref(new IconCheckSetting(keyShowEndpoints, true, null, null));
            table.pref(new IconSliderSetting(keyStartDotScale, 220, 0, 400, 10, null, v -> v + "%", null));
            table.pref(new IconSliderSetting(keyEndDotScale, 260, 0, 400, 10, null, v -> v + "%", null));
            table.pref(new IconCheckSetting(keyShowDamageText, true, null, null));
            table.pref(new IconSliderSetting(keyDamageTextScale, 60, 20, 140, 5, null, v -> v + "%", null));
            table.pref(new IconCheckSetting(keyDamageLabelAtEnd, false, null, null));
            table.pref(new IconSliderSetting(keyDamageTextOffsetScale, 100, 0, 300, 10, null, v -> v + "%", null));
            table.pref(new IconSliderSetting(keyPreviewRefresh, 6, 1, 60, 1, null, v -> Strings.autoFixed(v / 60f, 2) + "s", null));

            table.pref(new HeaderSetting("@sp.section.colors", null));
            table.pref(new IconTextSetting(keyGenPathColor, "3c7bff", null, v -> refreshGenPathColor()));
            table.pref(new IconTextSetting(keyMousePathColor, "a27ce5", null, v -> refreshMousePathColor()));

            table.pref(new HeaderSetting("@sp.section.auto", null));
            table.pref(new IconSliderSetting(keyAutoSafeDamageThreshold, 10, 0, 200, 1, null, v -> String.valueOf(v), null));
            table.pref(new IconTextSetting(keyAutoColorSafe, "34c759", null, v -> refreshAutoColors()));
            table.pref(new IconTextSetting(keyAutoColorWarn, "ffd60a", null, v -> refreshAutoColors()));
            table.pref(new IconTextSetting(keyAutoColorDead, "ff3b30", null, v -> refreshAutoColors()));
            table.pref(new IconCheckSetting(keyAutoMoveEnabled, true, null, null));
            table.pref(new IconSliderSetting(keyRtsUpdateInterval, 30, 1, 240, 1, null, v -> Strings.autoFixed(v / 60f, 2) + "s", null));
            table.pref(new IconSliderSetting(keyRtsCommandSpacing, 2, 0, 10, 1, null, v -> Strings.autoFixed(v / 60f, 3) + "s", null));
            table.pref(new IconSliderSetting(keyRtsWaypointReachTiles, 2, 0, 8, 1, null, v -> v + " tiles", null));
            table.pref(new IconSliderSetting(keyPathComputeSplitTicks, 1, 1, 60, 1, null, v -> v + " tick", null));
            table.pref(new IconSliderSetting(keyAutoThreatPaddingMax, 6, 0, 20, 1, null, v -> v + " tiles", null));

            table.pref(new HeaderSetting("@sp.section.gencluster", null));
            table.pref(new IconSliderSetting(keyGenClusterMaxPaths, 3, 1, 10, 1, null, v -> String.valueOf(v), null));
            table.pref(new IconSliderSetting(keyGenClusterMinSize, 2, 2, 10, 1, null, v -> String.valueOf(v), null));
            table.pref(new IconCheckSetting(keyGenClusterStartFromCore, false, null, null));

            table.pref(new HeaderSetting("@sp.section.target", null));
            addThreatModeRow(table);
            addTargetRow(table);
            table.pref(new IconSliderSetting(keyCoreTargetCount, 1, 1, 12, 1, null, v -> String.valueOf(v), null));

            if(!bekBundled){
                table.pref(new HeaderSetting("@sp.section.update", null));
                table.pref(new IconCheckSetting(GithubUpdateCheck.enabledKey(), true, null, null));
                table.pref(new IconCheckSetting(GithubUpdateCheck.showDialogKey(), true, null, null));
            }

            // Inline advanced settings (MindustryX-like: one screen; Pro Mode expands a collapsible section).
            table.pref(new HeaderSetting("@sp.setting.advanced.menu", null));
            table.pref(new AdvancedSectionSetting());
        
    }


    private final class AdvancedSectionSetting extends SettingsMenuDialog.SettingsTable.Setting{
        AdvancedSectionSetting(){
            super("sp-advanced-section");
        }

        @Override
        public void add(SettingsMenuDialog.SettingsTable table){
            Table holder = new Table();
            holder.left();

            final boolean[] lastPro = {Core.settings.getBool(keyProMode, false)};
            final Runnable[] rebuild = new Runnable[1];
            rebuild[0] = () -> {
                holder.clearChildren();

                if(Core.settings.getBool(keyProMode, false)){
                    SettingsMenuDialog.SettingsTable advanced = new SettingsMenuDialog.SettingsTable();
                    advanced.left();
                    buildAdvancedSettings(advanced);
                    advanced.rebuild();
                    holder.add(advanced).growX().row();
                }else{
                    Table locked = new Table();
                    locked.left().margin(10f);
                    locked.add("@sp.toast.pro-required").left().growX().wrap();
                    locked.button("@sp.setting.advanced.locked", Styles.flatt, () -> Core.settings.put(keyProMode, true))
                        .height(40f)
                        .padLeft(8f);
                    holder.add(locked).growX().padTop(6f).row();
                }
            };

            rebuild[0].run();
            holder.update(() -> {
                boolean nowPro = Core.settings.getBool(keyProMode, false);
                if(nowPro != lastPro[0]){
                    lastPro[0] = nowPro;
                    rebuild[0].run();
                }
            });

            table.add(holder).growX().row();
        }
    }

    private void buildAdvancedSettings(SettingsMenuDialog.SettingsTable table){
        table.pref(new HeaderSetting("@sp.section.advanced.pathfinding", null));
        addPathfinderRow(table);
        table.pref(new IconSliderSetting(keyAutoClusterSplitTiles, 5, 1, 30, 1, null, v -> v + " tiles", null));
        table.pref(new IconSliderSetting(keyFormationInflatePct, 125, 100, 300, 5, null, v -> v + "%", null));
        table.pref(new IconSliderSetting(keySafeCorridorBiasPct, 35, 0, 200, 5, null, v -> v + "%", null));
        table.pref(new IconCheckSetting(keyComputeSafeDistance, true, null, null));
        table.pref(new IconSliderSetting(keyDrownReserveDeciSeconds, 15, 0, 30, 1, null, v -> Strings.autoFixed(v / 10f, 1) + "s", null));
        table.pref(new IconSliderSetting(keyGoalCandidateRadiusTiles, 24, 6, 64, 1, null, v -> v + " tiles", null));

        table.pref(new HeaderSetting("@sp.section.advanced.automove", null));
        table.pref(new IconSliderSetting(keyRtsMaxWaypoints, 12, 2, 60, 1, null, v -> String.valueOf(v), null));
        table.pref(new IconCheckSetting(keyAutoBatchEnabled, true, null, null));
        table.pref(new IconSliderSetting(keyAutoBatchSizePct, 100, 50, 200, 10, null, v -> v + "%", null));
        table.pref(new IconSliderSetting(keyAutoBatchDelayPct, 100, 0, 200, 10, null, v -> v + "%", null));
        table.pref(new IconSliderSetting(keyAutoSlowMultiplier, 8, 1, 30, 1, null, v -> v + "x", null));

        table.pref(new HeaderSetting("@sp.section.advanced.cache", null));
        table.pref(new IconSliderSetting(keyPassableCacheEntries, 8, 1, 24, 1, null, v -> String.valueOf(v), v -> invalidatePassableCache()));

        table.pref(new HeaderSetting("@sp.section.advanced.gencluster", null));
        table.pref(new IconSliderSetting(keyGenClusterLinkDistTiles, defaultGenClusterLinkDistTiles, 1, 20, 1, null, v -> v + " tiles", null));
        table.pref(new IconSliderSetting(keyGenClusterNearTurretDistTiles, defaultGenClusterNearTurretDistTiles, 1, 40, 1, null, v -> v + " tiles", null));
        table.pref(new IconSliderSetting(keyGenClusterMinDrawTiles, defaultGenClusterMinDrawTiles, 1, 60, 1, null, v -> v + " tiles", null));
        table.pref(new IconSliderSetting(keyGenClusterFallbackBacktrackTiles, defaultGenClusterFallbackBacktrackTiles, 0, 60, 1, null, v -> v + " tiles", null));
    }

    private void addThreatModeRow(Table table){
        table.table(VscodeSettingsStyle.cardBackground(), t -> {
            t.left().margin(10f);
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
        table.table(VscodeSettingsStyle.cardBackground(), t -> {
            t.left().margin(10f);
            t.add("@sp.setting.target.mode").left().width(170f);

            TextButton modeButton = t.button("", Styles.flatt, this::cycleTargetMode)
                .growX()
                .height(40f)
                .padLeft(8f)
                .get();

            modeButton.update(() -> modeButton.setText(targetModeDisplay(Core.settings.getInt(keyTargetMode, targetModeCore))));
        }).growX().padTop(6f);

        table.row();
        table.table(VscodeSettingsStyle.cardBackground(), t -> {
            t.left().margin(10f);
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

    private void addPathfinderRow(Table table){
        table.table(VscodeSettingsStyle.cardBackground(), t -> {
            t.left().margin(10f);
            t.add("@sp.setting.pathfinder").left().width(170f);

            TextButton modeButton = t.button("", Styles.flatt, this::cyclePathfinder)
                .growX()
                .height(40f)
                .padLeft(8f)
                .get();

            modeButton.update(() -> modeButton.setText(pathfinderDisplay(pathfinderMode())));
        }).growX().padTop(6f);

        table.row();
    }

    private void cyclePathfinder(){
        int cur = pathfinderMode();
        int next = cur == pathfinderAStar ? pathfinderDfs : pathfinderAStar;
        Core.settings.put(keyPathfinder, next);
    }

    private static String pathfinderDisplay(int mode){
        switch(mode){
            case pathfinderDfs:
                return Core.bundle.get("sp.setting.pathfinder.dfs");
            case pathfinderAStar:
            default:
                return Core.bundle.get("sp.setting.pathfinder.astar");
        }
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

        ensureOverlayWindowsAttached();

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
        if(autoMode == autoModeMouse){
            goalX = clamp(worldToTile(Core.input.mouseWorldX()), 0, world.width() - 1);
            goalY = clamp(worldToTile(Core.input.mouseWorldY()), 0, world.height() - 1);
        }else{
            if(bufferedTargetPacked != -1){
                goalX = bufferedTargetX;
                goalY = bufferedTargetY;
            }else if(autoMoveFollow && autoMoveFollowGoalPacked != -1){
                goalX = autoMoveFollowGoalX;
                goalY = autoMoveFollowGoalY;
            }else{
                return;
            }
        }

        boolean movedAny = false;
        boolean issuedAny = false;
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

        logRts(logFormat("sp.log.rts.retarget", goalX, goalY, clusters.size));

        for(int i = 0; i < clusters.size; i++){
            ControlledCluster cluster = clusters.get(i);
            if(cluster == null) continue;

            float passClearance = cluster.maxHitRadiusWorld;
            float threatClearance = cluster.threatClearanceWorld + autoThreatExtraPaddingTiles * tilesize;
            ThreatMap map = buildThreatMap(cluster.moveUnit, cluster.units, true, cluster.moveFlying, cluster.threatsAir, cluster.threatsGround, passClearance, threatClearance);
            ShiftedPath best = findBestShiftedClusterPath(cluster, map, goalX, goalY);
            if(best == null || best.path == null || best.path.isEmpty()) continue;

            int issuedHash = issueRtsMoveAlongPath(cluster, best.path, map.width);
            if(issuedHash == Integer.MIN_VALUE) continue;

            movedAny = true;
            autoMoveFollowPathHash.put(cluster.key, issuedHash);
            autoMoveFollowLastIssue.put(cluster.key, Time.time);
            if(issuedHash != 0){
                issuedAny = true;
            }

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

        if(!movedAny){
            boolean fallbackIssued = issueDirectRtsFallback(clusters, goalX, goalY);
            if(fallbackIssued){
                autoMoveMonitorUntil = 0f;
                autoMoveFollow = false;
                logRts(logFormat("sp.log.rts.fallback", goalX, goalY));
            }
            return;
        }

        if(!issuedAny){
            autoMoveMonitorUntil = 0f;
            autoMoveFollow = false;
            logRts(logFormat("sp.log.rts.arrived", goalX, goalY));
            return;
        }

        autoMoveFollow = true;

        autoMoveMonitorUntil = Float.POSITIVE_INFINITY;
        autoMoveMonitorNextCheck = Time.time + previewRefreshInterval();
        autoMoveMonitorMinHpGround = minHpGround == Float.POSITIVE_INFINITY ? Float.NaN : minHpGround;
        autoMoveMonitorMinHpAir = minHpAir == Float.POSITIVE_INFINITY ? Float.NaN : minHpAir;
        autoMoveMonitorPredictedMaxDmg = predictedMax;
    }

    private boolean issueDirectRtsFallback(Seq<ControlledCluster> clusters, int goalX, int goalY){
        if(clusters == null || clusters.isEmpty()) return false;
        if(player == null) return false;

        Seq<Unit> units = new Seq<>();
        for(int i = 0; i < clusters.size; i++){
            ControlledCluster cluster = clusters.get(i);
            if(cluster == null || cluster.units == null || cluster.units.isEmpty()) continue;
            for(int j = 0; j < cluster.units.size; j++){
                Unit u = cluster.units.get(j);
                if(u == null) continue;
                if(u.team != player.team()) continue;
                if(!u.isAdded() || u.dead()) continue;
                units.add(u);
            }
        }

        if(units.isEmpty()) return false;

        int[] unitIds = new int[units.size];
        int commandId = ++autoMoveCommandId;
        for(int i = 0; i < units.size; i++){
            Unit u = units.get(i);
            unitIds[i] = u.id;
            autoMoveCommandByUnit.put(u.id, commandId);
        }

        Vec2 waypoint = new Vec2(tileToWorld(goalX) + tilesize / 2f, tileToWorld(goalY) + tilesize / 2f);
        if(UnitCommand.boostCommand != null){
            Call.setUnitCommand(player, unitIds, UnitCommand.boostCommand);
        }
        Call.commandUnits(player, unitIds, null, null, waypoint, false, true);
        return true;
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
            ThreatMap map = buildThreatMap(cluster.moveUnit, cluster.units, true, cluster.moveFlying, cluster.threatsAir, cluster.threatsGround, passClearance, threatClearance);
            ShiftedPath best = findBestShiftedClusterPath(cluster, map, goalX, goalY);
            if(best == null || best.path == null || best.path.isEmpty()) continue;

            int issuedHash = issueRtsMoveAlongPath(cluster, best.path, map.width);
            if(issuedHash == Integer.MIN_VALUE) continue;

            issuedAny = true;
            if(autoMoveFollow){
                autoMoveFollowPathHash.put(cluster.key, issuedHash);
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
            autoComputeJob = null;
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
            autoComputeJob = null;
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

        if(autoComputeJob != null){
            autoComputeJob.step();
            if(autoComputeJob.done){
                finishAutoComputeJob(autoComputeJob, baseInterval);
                autoComputeJob = null;
            }
            return;
        }

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
        boolean issueFollowCommands = autoMoveFollow;
        if(autoMode == autoModeMouse){
            goalX = clamp(worldToTile(Core.input.mouseWorldX()), 0, world.width() - 1);
            goalY = clamp(worldToTile(Core.input.mouseWorldY()), 0, world.height() - 1);

            if(autoMoveFollow && autoMoveFollowGoalPacked != -1){
                int currentPacked = goalX + goalY * world.width();
                issueFollowCommands = currentPacked == autoMoveFollowGoalPacked;
            }
        }else{
            if(bufferedTargetPacked != -1){
                goalX = bufferedTargetX;
                goalY = bufferedTargetY;
            }else if(autoMoveFollow && autoMoveFollowGoalPacked != -1){
                goalX = autoMoveFollowGoalX;
                goalY = autoMoveFollowGoalY;
            }else{
                autoNextCompute = Time.time + baseInterval * 2f;
                return;
            }
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

        logAuto(logFormat("sp.log.auto.begin",
            autoModeName(autoMode),
            clusters.size,
            goalX,
            goalY,
            unchanged,
            issueFollowCommands));

        autoComputeJob = new AutoComputeJob(clusters, goalX, goalY, goalPacked, startHash, threatHash, rtsInterval, unchanged, issueFollowCommands, pathComputeSplitTicks());
        autoComputeJob.step();
        if(autoComputeJob.done){
            finishAutoComputeJob(autoComputeJob, baseInterval);
            autoComputeJob = null;
        }
    }

    private void finishAutoComputeJob(AutoComputeJob job, float baseInterval){
        if(job == null) return;
        if(!job.anyPath){
            logAuto(logFormat("sp.log.auto.done.empty", elapsedMillis(job.startedNano)));
            autoNextCompute = Time.time + baseInterval * 2f;
            return;
        }

        clearPaths();
        drawPaths.addAll(job.paths);
        lastDamage = job.maxDmg;
        drawUntil = Float.POSITIVE_INFINITY;
        if(autoMoveFollow && job.issueFollowCommands){
            autoMoveMonitorPredictedMaxDmg = job.maxDmg;
        }

        autoLastStartPacked = job.startHash;
        autoLastGoalPacked = job.goalPacked;
        autoLastThreatMode = job.threatHash;

        float slowInterval = Math.min(240f, baseInterval * autoSlowMultiplier());
        autoNextCompute = Time.time + (autoMoveFollow ? baseInterval : (job.unchanged ? slowInterval : baseInterval));

        logAuto(logFormat("sp.log.auto.done.ok",
            job.clusters.size,
            job.paths.size,
            Strings.autoFixed(job.maxDmg, 2),
            elapsedMillis(job.startedNano),
            job.issueFollowCommands));
    }

    private final class AutoComputeJob{
        final Seq<ControlledCluster> clusters;
        final int goalX, goalY;
        final int goalPacked;
        final int startHash;
        final int threatHash;
        final float rtsInterval;
        final boolean unchanged;
        final boolean issueFollowCommands;
        final int clustersPerTick;
        final long startedNano;

        final Seq<RenderPath> paths = new Seq<>();
        boolean anyPath = false;
        float maxDmg = 0f;
        int index = 0;
        boolean done = false;

        AutoComputeJob(Seq<ControlledCluster> clusters, int goalX, int goalY, int goalPacked, int startHash, int threatHash, float rtsInterval, boolean unchanged, boolean issueFollowCommands, int splitTicks){
            this.clusters = new Seq<>(clusters.size);
            this.clusters.addAll(clusters);
            this.goalX = goalX;
            this.goalY = goalY;
            this.goalPacked = goalPacked;
            this.startHash = startHash;
            this.threatHash = threatHash;
            this.rtsInterval = rtsInterval;
            this.unchanged = unchanged;
            this.issueFollowCommands = issueFollowCommands;
            this.startedNano = System.nanoTime();
            int ticks = Math.max(1, splitTicks);
            this.clustersPerTick = Math.max(1, (clusters.size + ticks - 1) / ticks);
        }

        void step(){
            if(done) return;
            int end = Math.min(clusters.size, index + clustersPerTick);
            for(; index < end; index++){
                ControlledCluster cluster = clusters.get(index);
                if(cluster == null) continue;

                float passClearance = cluster.maxHitRadiusWorld;
                float threatClearance = cluster.threatClearanceWorld + autoThreatExtraPaddingTiles * tilesize;
                long clusterStart = System.nanoTime();
                ThreatMap map = buildThreatMap(cluster.moveUnit, cluster.units, true, cluster.moveFlying, cluster.threatsAir, cluster.threatsGround, passClearance, threatClearance);
                ShiftedPath sp = findBestShiftedClusterPath(cluster, map, goalX, goalY);
                if(sp == null || sp.path == null || sp.path.isEmpty()){
                    logAuto(logFormat("sp.log.auto.cluster.nopath", cluster.key, elapsedMillis(clusterStart)));
                    continue;
                }

                anyPath = true;
                if(Float.isFinite(sp.maxDmg)){
                    maxDmg = Math.max(maxDmg, sp.maxDmg);
                }

                IntSeq compact = compactPath(sp.path, map.width);
                Color c = autoPathColor(cluster, sp.dmgGround, sp.dmgAir, sp.maxDmg);
                paths.add(new RenderPath(toWorldPointsFromTilesWithStart(compact, map.width, cluster.x + sp.dx, cluster.y + sp.dy, null), c, sp.maxDmg));

                logAuto(logFormat("sp.log.auto.cluster.ok", cluster.key, sp.path.size, Strings.autoFixed(sp.maxDmg, 2), elapsedMillis(clusterStart)));

                if(issueFollowCommands && Core.settings.getBool(keyAutoMoveEnabled, true)){
                    int newHash = rtsWaypointHash(cluster, sp.path, map.width);
                    int prevHash = autoMoveFollowPathHash.get(cluster.key, Integer.MIN_VALUE);
                    float lastIssued = autoMoveFollowLastIssue.get(cluster.key, -999999f);

                    if(prevHash == Integer.MIN_VALUE || prevHash != newHash){
                        if(Time.time - lastIssued >= rtsInterval){
                            int issuedHash = issueRtsMoveAlongPath(cluster, sp.path, map.width);
                            if(issuedHash != Integer.MIN_VALUE){
                                autoMoveFollowPathHash.put(cluster.key, issuedHash);
                                autoMoveFollowLastIssue.put(cluster.key, Time.time);
                            }
                        }
                    }
                }
            }

            if(index >= clusters.size){
                done = true;
            }
        }
    }

    private Seq<ControlledCluster> computeControlledClusters(){
        if(player == null) return null;
        Unit playerUnit = player.unit();
        if(playerUnit == null || !playerUnit.isAdded() || playerUnit.dead()) return null;

        tmpUnits.clear();

        boolean hadSelection = control != null && control.input != null && control.input.selectedUnits != null && control.input.selectedUnits.any();
        if(hadSelection){
            for(int i = 0; i < control.input.selectedUnits.size; i++){
                Unit u = control.input.selectedUnits.get(i);
                if(u == null) continue;
                if(u.team != player.team()) continue;
                if(!u.isAdded() || u.dead()) continue;
                tmpUnits.add(u);
            }
        }

        // If nothing is selected, fall back to the player's unit so auto mode can still preview/move.
        if(tmpUnits.isEmpty()){
            tmpUnits.add(playerUnit);
        }

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
                // Ignore the player's own unit if it ends up being an isolated single-unit "cluster"
                // while some other units are selected/controlled.
                if(hadSelection && tmpUnits.size > 1) continue;
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

    private PlanningStart computeSelectedOrPlayerStart(){
        if(!state.isGame() || player == null) return null;
        Unit playerUnit = player.unit();

        if(control == null || control.input == null || control.input.selectedUnits == null || !control.input.selectedUnits.any()){
            if(playerUnit == null) return null;
            float r = playerUnit.hitSize / 2f;
            return new PlanningStart(playerUnit, singletonUnitSeq(playerUnit), playerUnit.x, playerUnit.y, r, r, playerUnit.speed());
        }

        tmpUnits.clear();
        for(int i = 0; i < control.input.selectedUnits.size; i++){
            Unit u = control.input.selectedUnits.get(i);
            if(u == null) continue;
            if(u.team != player.team()) continue;
            if(!u.isAdded() || u.dead()) continue;
            tmpUnits.add(u);
        }

        if(tmpUnits.isEmpty()){
            if(playerUnit == null) return null;
            float r = playerUnit.hitSize / 2f;
            return new PlanningStart(playerUnit, singletonUnitSeq(playerUnit), playerUnit.x, playerUnit.y, r, r, playerUnit.speed());
        }

        ControlledCluster cluster = buildControlledCluster(tmpUnits);
        if(cluster == null || cluster.moveUnit == null){
            Unit fallback = playerUnit != null ? playerUnit : tmpUnits.first();
            if(fallback == null) return null;
            float r = fallback.hitSize / 2f;
            return new PlanningStart(fallback, singletonUnitSeq(fallback), fallback.x, fallback.y, r, r, fallback.speed());
        }

        Seq<Unit> pathUnits = copyUnitSeq(cluster.units);
        float planSpeed = useSlowestUnitForPathCost()
            ? slowestUnitSpeed(pathUnits, cluster.moveUnit)
            : Math.max(0.0001f, cluster.speed);
        return new PlanningStart(cluster.moveUnit, pathUnits, cluster.x, cluster.y, cluster.maxHitRadiusWorld, cluster.threatClearanceWorld, planSpeed);
    }

    private static Seq<Unit> singletonUnitSeq(Unit unit){
        Seq<Unit> out = new Seq<>();
        if(unit != null) out.add(unit);
        return out;
    }

    private static Seq<Unit> copyUnitSeq(Seq<Unit> units){
        Seq<Unit> out = new Seq<>();
        if(units == null || units.isEmpty()) return out;
        for(int i = 0; i < units.size; i++){
            Unit u = units.get(i);
            if(u != null) out.add(u);
        }
        return out;
    }

    private static Unit slowestUnitRef(Seq<Unit> units, Unit fallbackUnit){
        Unit best = null;
        float bestSpeed = Float.POSITIVE_INFINITY;

        if(units != null && units.any()){
            for(int i = 0; i < units.size; i++){
                Unit u = units.get(i);
                if(u == null || !u.isAdded() || u.dead()) continue;
                float s = Math.max(0.0001f, u.speed());
                if(s < bestSpeed){
                    bestSpeed = s;
                    best = u;
                }
            }
        }

        if(best != null) return best;
        if(fallbackUnit != null) return fallbackUnit;
        return null;
    }

    private static Unit slowestUnitRefByFlight(Seq<Unit> units, Unit fallbackUnit, boolean wantFlying){
        Unit best = null;
        float bestSpeed = Float.POSITIVE_INFINITY;

        if(units != null && units.any()){
            for(int i = 0; i < units.size; i++){
                Unit u = units.get(i);
                if(u == null || !u.isAdded() || u.dead()) continue;
                if(u.isFlying() != wantFlying) continue;
                float s = Math.max(0.0001f, u.speed());
                if(s < bestSpeed){
                    bestSpeed = s;
                    best = u;
                }
            }
        }

        if(best != null) return best;
        if(fallbackUnit != null && fallbackUnit.isFlying() == wantFlying) return fallbackUnit;
        return null;
    }

    private static float slowestUnitSpeed(Seq<Unit> units, Unit fallbackUnit){
        Unit slowest = slowestUnitRef(units, fallbackUnit);
        if(slowest != null) return Math.max(0.0001f, slowest.speed());
        return 0.0001f;
    }

    private void computePath(boolean includeUnits, boolean showToasts){
        long planStarted = System.nanoTime();
        clearPaths();

        int mode = Core.settings.getInt(keyTargetMode, targetModeCore);
        int threatMode = Core.settings.getInt(keyThreatMode, threatModeGround);
        logPlan(logFormat("sp.log.plan.begin", targetModeName(mode), includeUnits, threatMode, showToasts));

        if(!state.isGame() || world == null || player == null){
            logPlan(logFormat("sp.log.plan.abort.notgame", elapsedMillis(planStarted)));
            if(showToasts) showToast("@sp.toast.no-path", 2.5f);
            return;
        }

        // Player->mouse mode should always start from the player's current unit.
        if(mode == targetModeCoreMouse){
            Unit unit = player.unit();
            if(unit == null){
                logPlan(logFormat("sp.log.plan.abort.noplayerunit", targetModeName(mode), elapsedMillis(planStarted)));
                if(showToasts) showToast("@sp.toast.no-path", 2.5f);
                return;
            }
            boolean moveFlying = threatMode == threatModeAir;
            boolean threatsAir = threatMode == threatModeAir || threatMode == threatModeBoth;
            boolean threatsGround = threatMode == threatModeGround || threatMode == threatModeBoth;

            float r = unit.hitSize / 2f;
            ThreatMap map = buildThreatMap(unit, singletonUnitSeq(unit), includeUnits, moveFlying, threatsAir, threatsGround, r, r);
            computePlayerToMousePath(unit, map, moveFlying, showToasts);
            logPlan(logFormat("sp.log.plan.finish", targetModeName(mode), elapsedMillis(planStarted), drawPaths.size, Strings.autoFixed(lastDamage, 2)));
            return;
        }

        PlanningStart start = computeSelectedOrPlayerStart();
        if(start == null || start.unit == null){
            logPlan(logFormat("sp.log.plan.abort.nostart", elapsedMillis(planStarted)));
            if(showToasts) showToast("@sp.toast.no-path", 2.5f);
            return;
        }

        Unit unit = start.unit;

        boolean moveFlying = threatMode == threatModeAir;
        boolean threatsAir = threatMode == threatModeAir || threatMode == threatModeBoth;
        boolean threatsGround = threatMode == threatModeGround || threatMode == threatModeBoth;

        if(mode == targetModeGenCluster){
            ThreatMap map = buildThreatMap(unit, start.pathUnits, includeUnits, moveFlying, threatsAir, threatsGround, start.passClearanceWorld, start.threatClearanceWorld);
            computeGenClusterPaths(unit, start.pathUnits, start, map, includeUnits, moveFlying, threatsAir, threatsGround, showToasts);
            logPlan(logFormat("sp.log.plan.finish", targetModeName(mode), elapsedMillis(planStarted), drawPaths.size, Strings.autoFixed(lastDamage, 2)));
            return;
        }

        ThreatMap map = buildThreatMap(unit, start.pathUnits, includeUnits, moveFlying, threatsAir, threatsGround, start.passClearanceWorld, start.threatClearanceWorld);

        int startX = worldToTile(start.worldX);
        int startY = worldToTile(start.worldY);
        if(!inBounds(map, startX, startY)){
            logPlan(logFormat("sp.log.plan.abort.startoob", startX, startY, elapsedMillis(planStarted)));
            if(showToasts) showToast("@sp.toast.no-path", 2.5f);
            return;
        }

        if(!map.passable[startX + startY * map.width]){
            int startIdx = findNearestPassable(map, startX, startY, 8);
            if(startIdx == -1){
                logPlan(logFormat("sp.log.plan.abort.startblocked", elapsedMillis(planStarted)));
                if(showToasts) showToast("@sp.toast.no-path", 2.5f);
                return;
            }
            startX = startIdx % map.width;
            startY = startIdx / map.width;
        }

        Seq<Building> targets;
        if(mode == targetModeCore){
            targets = findNearestEnemyCores(start.worldX, start.worldY, coreTargetCount());
        }else{
            Building t = findTarget(mode, start.worldX, start.worldY);
            targets = new Seq<>();
            if(t != null) targets.add(t);
        }

        if(targets == null || targets.isEmpty()){
            logPlan(logFormat("sp.log.plan.abort.notarget", elapsedMillis(planStarted)));
            if(showToasts) showToast("@sp.toast.no-target", 2.5f);
            return;
        }

        float speed = Math.max(0.0001f, start.speed);
        float bestDmg = Float.POSITIVE_INFINITY;
        int paths = 0;
        int firstPathTiles = 0;

        for(int ti = 0; ti < targets.size; ti++){
            Building target = targets.get(ti);
            if(target == null) continue;

            IntSeq goals = buildGoalTiles(map, unit, target, moveFlying);
            if(goals.isEmpty()) continue;
            boolean[] goalMask = buildGoalMask(map, goals);

            PathResult safe = null;
            PathResult result;
            if(alwaysPlanNearestPath()){
                result = findPath(map, startX, startY, goals, goalMask, PathMode.nearest, unit, start.pathUnits, speed);
            }else{
                safe = findPath(map, startX, startY, goals, goalMask, PathMode.safeOnly, unit, start.pathUnits, speed);
                result = safe != null ? safe : findPath(map, startX, startY, goals, goalMask, PathMode.minDamage, unit, start.pathUnits, speed);
            }

            if(result == null || result.path == null || result.path.isEmpty()) continue;

            IntSeq compact = compactPath(result.path, map.width);
            float dmg = estimateDamageForUnits(map, result.path, start.pathUnits, unit, speed);
            bestDmg = Math.min(bestDmg, dmg);

            Seq<Pos> points = toWorldPointsFromTilesWithStart(compact, map.width, start.worldX, start.worldY, target);
            drawPaths.add(new RenderPath(points, safe != null ? Pal.heal : Pal.remove, dmg));
            if(paths == 0){
                firstPathTiles = result.path.size;
            }
            paths++;
        }

        if(paths == 0){
            logPlan(logFormat("sp.log.plan.abort.nopath", targets.size, elapsedMillis(planStarted)));
            if(showToasts) showToast("@sp.toast.no-path", 2.5f);
            return;
        }

        lastDamage = Float.isFinite(bestDmg) ? bestDmg : 0f;

        int seconds = Core.settings.getInt(keyPathDuration, 10);
        drawUntil = seconds <= 0 ? Float.POSITIVE_INFINITY : Time.time + seconds * 60f;

        if(showToasts){
            if(paths == 1){
                showToast(Core.bundle.format("sp.toast.path", firstPathTiles, Strings.autoFixed(lastDamage, 1)), 3f);
            }else{
                showToast(Core.bundle.format("sp.toast.core.multi", paths, Strings.autoFixed(lastDamage, 1)), 3f);
            }
        }

        logPlan(logFormat("sp.log.plan.done", targetModeName(mode), paths, firstPathTiles, Strings.autoFixed(lastDamage, 2), elapsedMillis(planStarted)));
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

    private Building findTarget(int mode, float px, float py){
        Block wanted = selectedTargetBlock();
        if(mode == targetModeBlock && wanted == null){
            // If no block is selected (or the saved block name is invalid), fall back to nearest enemy building.
            mode = targetModeNearest;
        }

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

    private Seq<Building> findNearestEnemyCores(float px, float py, int k){
        Seq<Building> builds = anchorBuildings();
        Seq<Building> cores = new Seq<>();
        Seq<Building> derelict = new Seq<>();

        for(int i = 0; i < builds.size; i++){
            Building b = builds.get(i);
            if(b == null) continue;
            if(b.team == player.team()) continue;
            if(!(b.block instanceof CoreBlock)) continue;
            if(b.team == Team.derelict){
                derelict.add(b);
            }else{
                cores.add(b);
            }
        }

        Seq<Building> src = cores.isEmpty() ? derelict : cores;
        if(src.isEmpty()) return new Seq<>();

        src.sort((a, b) -> Float.compare(Mathf.dst2(px, py, a.x, a.y), Mathf.dst2(px, py, b.x, b.y)));

        Seq<Building> out = new Seq<>();
        int take = Math.min(k, src.size);
        for(int i = 0; i < take; i++){
            out.add(src.get(i));
        }
        return out;
    }

    private void computeGenClusterPaths(Unit unit, Seq<Unit> pathUnits, PlanningStart start, ThreatMap map, boolean includeUnits, boolean moveFlying, boolean threatsAir, boolean threatsGround, boolean showToasts){
        int minSize = Math.max(2, Core.settings.getInt(keyGenClusterMinSize, 2));
        int maxPaths = Math.max(1, Core.settings.getInt(keyGenClusterMaxPaths, 3));
        boolean startFromPlayer = Core.settings.getBool(keyGenClusterStartFromCore, false);

        float startWorldX = start == null ? unit.x : start.worldX;
        float startWorldY = start == null ? unit.y : start.worldY;

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
        float speed = start == null ? unit.speed() : start.speed;
        speed = Math.max(0.0001f, speed);

        for(int i = 0; i < clusters.size; i++){
            Seq<Building> cluster = clusters.get(i);
            if(cluster == null || cluster.size < minSize) continue;

            Building target = pickClusterRepresentative(cluster);
            if(target == null) continue;

            IntSeq goals = buildGoalTiles(map, unit, target, moveFlying);
            if(goals.isEmpty()) continue;

            boolean[] goalMask = buildGoalMask(map, goals);

            PathResult safe = null;
            PathResult result;
            if(alwaysPlanNearestPath()){
                result = findPath(map, startX, startY, goals, goalMask, PathMode.nearest, unit, pathUnits, speed);
            }else{
                safe = findPath(map, startX, startY, goals, goalMask, PathMode.safeOnly, unit, pathUnits, speed);
                result = safe != null ? safe : findPath(map, startX, startY, goals, goalMask, PathMode.minDamage, unit, pathUnits, speed);
            }
            if(result == null) continue;

            float damage = estimateDamageForUnits(map, result.path, pathUnits, unit, speed);
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

            float dmg = estimateDamageForUnits(map, segment, pathUnits, unit, speed);
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

        int goalRadius = goalCandidateRadiusTiles();
        IntSeq goalsAll = buildNearestGoalCandidates(map, goalX, goalY, goalRadius, false);
        if(goalsAll.isEmpty()){
            if(showToasts) showToast("@sp.toast.no-path", 2.5f);
            return;
        }

        IntSeq goalsSafe = buildNearestGoalCandidates(map, goalX, goalY, goalRadius, true);
        boolean[] goalMaskAll = buildGoalMask(map, goalsAll);

        float speed = unit.speed();
        Seq<Unit> pathUnits = singletonUnitSeq(unit);
        PathResult result;
        if(alwaysPlanNearestPath()){
            result = findPath(map, startX, startY, goalsAll, goalMaskAll, PathMode.nearest, unit, pathUnits, speed);
        }else{
            PathResult safe = !goalsSafe.isEmpty()
                ? findPath(map, startX, startY, goalsSafe, buildGoalMask(map, goalsSafe), PathMode.safeOnly, unit, pathUnits, speed)
                : null;
            result = safe != null ? safe : findPath(map, startX, startY, goalsAll, goalMaskAll, PathMode.minDamage, unit, pathUnits, speed);
        }

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

        int goalRadius = goalCandidateRadiusTiles();
        IntSeq goalsAll = buildNearestGoalCandidates(map, goalX, goalY, goalRadius, false);
        if(goalsAll.isEmpty()) return null;
        IntSeq goalsSafe = buildNearestGoalCandidates(map, goalX, goalY, goalRadius, true);
        boolean[] goalMaskAll = buildGoalMask(map, goalsAll);
        boolean[] goalMaskSafe = goalsSafe.isEmpty() ? null : buildGoalMask(map, goalsSafe);

        int startX = clamp(worldToTile(cluster.x), 0, map.width - 1);
        int startY = clamp(worldToTile(cluster.y), 0, map.height - 1);
        if(!inBounds(map, startX, startY)) return null;
        if(!map.passable[startX + startY * map.width]){
            int startIdx = findNearestPassable(map, startX, startY, 10);
            if(startIdx == -1) return null;
            startX = startIdx % map.width;
            startY = startIdx / map.width;
        }

        float speed = useSlowestUnitForPathCost()
            ? slowestUnitSpeed(cluster.units, cluster.moveUnit)
            : Math.max(0.0001f, cluster.speed);

        boolean nearestPlan = alwaysPlanNearestPath();
        PathResult base = findClusterPathFromStart(
            cluster,
            map,
            startX,
            startY,
            goalsAll,
            goalMaskAll,
            goalsSafe,
            goalMaskSafe,
            speed,
            nearestPlan
        );
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

            IntSeq candidatePath = shifted;
            if(pathWouldDrownForUnits(map, shifted, cluster.moveUnit, cluster.units, speed)){
                int offsetStartX = clamp(worldToTile(cluster.x + dx), 0, map.width - 1);
                int offsetStartY = clamp(worldToTile(cluster.y + dy), 0, map.height - 1);

                if(!map.passable[offsetStartX + offsetStartY * map.width]){
                    int offsetStartIdx = findNearestPassable(map, offsetStartX, offsetStartY, 10);
                    if(offsetStartIdx == -1) continue;
                    offsetStartX = offsetStartIdx % map.width;
                    offsetStartY = offsetStartIdx / map.width;
                }

                PathResult rerouted = findClusterPathFromStart(
                    cluster,
                    map,
                    offsetStartX,
                    offsetStartY,
                    goalsAll,
                    goalMaskAll,
                    goalsSafe,
                    goalMaskSafe,
                    speed,
                    nearestPlan
                );
                if(rerouted == null || rerouted.path == null || rerouted.path.isEmpty()) continue;
                if(pathWouldDrownForUnits(map, rerouted.path, cluster.moveUnit, cluster.units, speed)) continue;

                logDrown(logFormat("sp.log.drown.replan", cluster.key, Strings.autoFixed(dx, 1), Strings.autoFixed(dy, 1), rerouted.path.size));
                candidatePath = rerouted.path;
            }

            float dmgGround = cluster.hasGround ? estimateDamageForUnitsByFlight(map, candidatePath, cluster.units, cluster.moveUnit, false) : Float.NaN;
            float dmgAir = cluster.hasAir ? estimateDamageForUnitsByFlight(map, candidatePath, cluster.units, cluster.moveUnit, true) : Float.NaN;

            float maxDmg = 0f;
            if(cluster.hasGround && Float.isFinite(dmgGround)) maxDmg = Math.max(maxDmg, dmgGround);
            if(cluster.hasAir && Float.isFinite(dmgAir)) maxDmg = Math.max(maxDmg, dmgAir);

            short minSafeDist = 0;
            if(map.safeDist != null){
                short min = Short.MAX_VALUE;
                for(int s = 0; s < candidatePath.size; s++){
                    int tidx = candidatePath.items[s];
                    short d = map.safeDist[tidx];
                    if(d < min) min = d;
                }
                minSafeDist = min == Short.MAX_VALUE ? 0 : min;
            }

            if(nearestPlan){
                if(best == null
                    || candidatePath.size < best.path.size
                    || (candidatePath.size == best.path.size && (minSafeDist > best.minSafeDist || (minSafeDist == best.minSafeDist && maxDmg + 0.0001f < best.maxDmg)))){
                    best = new ShiftedPath(candidatePath, dx, dy, dmgGround, dmgAir, maxDmg, minSafeDist);
                }
            }else{
                if(best == null
                    || maxDmg + 0.0001f < best.maxDmg
                    || (Math.abs(maxDmg - best.maxDmg) <= 0.0001f && (minSafeDist > best.minSafeDist || (minSafeDist == best.minSafeDist && candidatePath.size < best.path.size)))){
                    best = new ShiftedPath(candidatePath, dx, dy, dmgGround, dmgAir, maxDmg, minSafeDist);
                }
            }
        }

        return best;
    }

    private PathResult findClusterPathFromStart(
        ControlledCluster cluster,
        ThreatMap map,
        int startX,
        int startY,
        IntSeq goalsAll,
        boolean[] goalMaskAll,
        IntSeq goalsSafe,
        boolean[] goalMaskSafe,
        float speed,
        boolean nearestPlan
    ){
        if(cluster == null || map == null) return null;
        if(!inBounds(map, startX, startY)) return null;

        if(nearestPlan){
            return findPath(map, startX, startY, goalsAll, goalMaskAll, PathMode.nearest, cluster.moveUnit, cluster.units, speed);
        }

        PathResult safe = goalsSafe != null && !goalsSafe.isEmpty() && goalMaskSafe != null
            ? findPath(map, startX, startY, goalsSafe, goalMaskSafe, PathMode.safeOnly, cluster.moveUnit, cluster.units, speed)
            : null;
        if(safe != null && safe.path != null && !safe.path.isEmpty()) return safe;

        return findPath(map, startX, startY, goalsAll, goalMaskAll, PathMode.minDamage, cluster.moveUnit, cluster.units, speed);
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

    private int issueRtsMoveAlongPath(ControlledCluster cluster, IntSeq tilePath, int width){
        long issuedStarted = System.nanoTime();
        if(tilePath == null || tilePath.isEmpty()) return Integer.MIN_VALUE;
        if(player == null || player.unit() == null) return Integer.MIN_VALUE;
        if(control == null || control.input == null || control.input.selectedUnits == null || !control.input.selectedUnits.any()) return Integer.MIN_VALUE;
        if(cluster == null || cluster.units == null || cluster.units.isEmpty()) return Integer.MIN_VALUE;

        Seq<Unit> units = new Seq<>();
        for(int i = 0; i < cluster.units.size; i++){
            Unit u = cluster.units.get(i);
            if(u == null) continue;
            if(u.team != player.team()) continue;
            if(!u.isAdded() || u.dead()) continue;
            units.add(u);
        }
        if(units.isEmpty()) return Integer.MIN_VALUE;

        IntSeq routedTiles = prepareRtsWaypointTiles(cluster, tilePath, width);
        if(routedTiles == null) return Integer.MIN_VALUE;
        if(routedTiles.isEmpty()){
            logRts(logFormat("sp.log.rts.issue.arrived", cluster.key, units.size, elapsedMillis(issuedStarted)));
            return 0;
        }
        int routedHash = hashWaypointPath(routedTiles, width);

        int maxWaypoints = rtsMaxWaypoints();
        int step = Math.max(1, routedTiles.size / maxWaypoints);

        Seq<Vec2> waypoints = new Seq<>();
        for(int i = 0; i < routedTiles.size; i += step){
            int idx = routedTiles.items[i];
            int tx = idx % width;
            int ty = idx / width;
            waypoints.add(new Vec2(tileToWorld(tx) + tilesize / 2f, tileToWorld(ty) + tilesize / 2f));
        }

        // Do not include a waypoint at the path's start; this can cause queued RTS paths to stall.
        if(waypoints.size > 1){
            waypoints.remove(0);
        }

        int lastIdx = routedTiles.items[routedTiles.size - 1];
        int lastTx = lastIdx % width;
        int lastTy = lastIdx / width;
        Vec2 end = new Vec2(tileToWorld(lastTx) + tilesize / 2f, tileToWorld(lastTy) + tilesize / 2f);
        if(waypoints.isEmpty() || waypoints.peek().dst2(end) > 0.1f){
            waypoints.add(end);
        }

        if(waypoints.isEmpty()) return Integer.MIN_VALUE;

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

        logRts(logFormat("sp.log.rts.issue",
            cluster.key,
            units.size,
            tilePath.size,
            routedTiles.size,
            waypoints.size,
            batches,
            elapsedMillis(issuedStarted)));

        return routedHash;
    }

    private int rtsWaypointHash(ControlledCluster cluster, IntSeq tilePath, int width){
        IntSeq routedTiles = prepareRtsWaypointTiles(cluster, tilePath, width);
        if(routedTiles == null) return Integer.MIN_VALUE;
        if(routedTiles.isEmpty()) return 0;
        return hashWaypointPath(routedTiles, width);
    }

    private IntSeq prepareRtsWaypointTiles(ControlledCluster cluster, IntSeq tilePath, int width){
        IntSeq compact = compactPath(tilePath, width);
        if(compact == null || compact.isEmpty()) compact = tilePath;
        if(compact == null || compact.isEmpty()) return compact;

        if(cluster == null || compact.size <= 1) return compact;

        float reachTiles = rtsWaypointReachTiles();
        if(reachTiles <= 0.0001f) return compact;

        float reachWorld = reachTiles * tilesize;
        float reachWorld2 = reachWorld * reachWorld;

        int start = 0;
        int last = compact.size - 1;
        while(start < last){
            int idx = compact.items[start];
            int tx = idx % width;
            int ty = idx / width;

            float wx = tileToWorld(tx) + tilesize / 2f;
            float wy = tileToWorld(ty) + tilesize / 2f;
            if(Mathf.dst2(cluster.x, cluster.y, wx, wy) > reachWorld2 + 0.0001f) break;

            if(segmentEntersDeepWater(compact.items[start], compact.items[start + 1], width)) break;
            start++;
        }

        if(start == last){
            int idx = compact.items[last];
            int tx = idx % width;
            int ty = idx / width;
            float wx = tileToWorld(tx) + tilesize / 2f;
            float wy = tileToWorld(ty) + tilesize / 2f;
            if(Mathf.dst2(cluster.x, cluster.y, wx, wy) <= reachWorld2 + 0.0001f){
                if(compact.size <= 1 || !segmentEntersDeepWater(compact.items[last - 1], compact.items[last], width)){
                    start = compact.size;
                }
            }
        }

        if(start <= 0) return compact;
        if(start >= compact.size) return new IntSeq();

        IntSeq trimmed = new IntSeq(compact.size - start);
        for(int i = start; i < compact.size; i++){
            trimmed.add(compact.items[i]);
        }
        return trimmed;
    }

    private static boolean segmentEntersDeepWater(int fromIdx, int toIdx, int width){
        if(world == null || width <= 0) return false;

        int fromX = fromIdx % width;
        int fromY = fromIdx / width;
        int toX = toIdx % width;
        int toY = toIdx / width;

        int steps = Math.max(Math.abs(toX - fromX), Math.abs(toY - fromY));
        if(steps <= 0) return false;

        boolean wasDeep = isDeepWaterTile(fromX, fromY);
        for(int i = 1; i <= steps; i++){
            float t = i / (float)steps;
            int x = clamp(Math.round(Mathf.lerp(fromX, toX, t)), 0, world.width() - 1);
            int y = clamp(Math.round(Mathf.lerp(fromY, toY, t)), 0, world.height() - 1);
            boolean deep = isDeepWaterTile(x, y);
            if(!wasDeep && deep) return true;
            wasDeep = deep;
        }

        return false;
    }

    private static boolean isDeepWaterTile(int x, int y){
        if(world == null) return false;
        Tile tile = world.tile(x, y);
        if(tile == null) return false;
        Floor floor = tile.floor();
        return floor != null && floor.isLiquid && floor.drownTime > 0f;
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

        if(!queue && UnitCommand.boostCommand != null){
            Call.setUnitCommand(player, unitIds, UnitCommand.boostCommand);
        }

        Call.commandUnits(player, unitIds, null, null, waypoint, queue, true);
        logRts(logFormat("sp.log.rts.send",
            commandId,
            unitIds.length,
            queue,
            Strings.autoFixed(waypoint.x, 1),
            Strings.autoFixed(waypoint.y, 1)));
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

    private static IntSeq buildNearestGoalCandidates(ThreatMap map, int x, int y, int radius, boolean safeOnly){
        IntSeq out = new IntSeq();
        if(map == null) return out;

        radius = Math.max(1, radius);
        int radiusSq = radius * radius;
        IntSeq[] buckets = new IntSeq[radiusSq + 1];

        for(int dy = -radius; dy <= radius; dy++){
            for(int dx = -radius; dx <= radius; dx++){
                int d2 = dx * dx + dy * dy;
                if(d2 > radiusSq) continue;

                int nx = x + dx;
                int ny = y + dy;
                if(!inBounds(map, nx, ny)) continue;

                int nidx = nx + ny * map.width;
                if(!map.passable[nidx]) continue;
                if(safeOnly && !isZeroDamageTile(map, nidx)) continue;

                IntSeq bucket = buckets[d2];
                if(bucket == null){
                    bucket = new IntSeq();
                    buckets[d2] = bucket;
                }
                bucket.add(nidx);
            }
        }

        int limit = 256;
        for(int d2 = 0; d2 < buckets.length && out.size < limit; d2++){
            IntSeq bucket = buckets[d2];
            if(bucket == null || bucket.isEmpty()) continue;
            for(int i = 0; i < bucket.size && out.size < limit; i++){
                out.add(bucket.items[i]);
            }
        }

        return out;
    }

    private static boolean isZeroDamageTile(ThreatMap map, int idx){
        if(map == null || idx < 0 || idx >= map.size) return false;
        float threat = map.risk == null ? 0f : map.risk[idx];
        float floor = map.floorRisk == null ? 0f : map.floorRisk[idx];
        return threat <= safeRiskEps && floor <= safeRiskEps;
    }

    private static boolean[] buildGoalMask(ThreatMap map, IntSeq goals){
        boolean[] mask = new boolean[map.size];
        for(int i = 0; i < goals.size; i++){
            int idx = goals.items[i];
            if(idx >= 0 && idx < map.size){
                mask[idx] = true;
            }
        }
        return mask;
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
        if(!StealthPathMathUtil.tryParseHexColor(value, genPathColor)){
            genPathColor.set(0.235f, 0.48f, 1f, 1f);
        }
    }

    private void refreshMousePathColor(){
        String value = Core.settings.getString(keyMousePathColor, "a27ce5");
        if(!StealthPathMathUtil.tryParseHexColor(value, mousePathColor)){
            mousePathColor.set(0.635f, 0.486f, 0.898f, 1f);
        }
    }

    // Path conversions extracted into `StealthPathPathUtil`.

    // Generic helpers extracted into `StealthPathMathUtil` (no behavior changes).

    private ThreatMap buildThreatMap(Unit unit, Seq<Unit> pathUnits, boolean includeUnits, boolean moveFlying, boolean threatsAir, boolean threatsGround, float passClearanceWorld, float threatClearanceWorld){
        ThreatMap map = obtainThreatMapScratch();
        map.safeBias = Mathf.clamp(threatClearanceWorld / tilesize, 0f, 10f);

        fillPassable(map, unit, pathUnits, moveFlying, passClearanceWorld);
        fillFloorHazards(map, unit, moveFlying);
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

    private ThreatMap obtainThreatMapScratch(){
        int w = world.width();
        int h = world.height();
        if(threatMapScratch == null || threatMapScratch.width != w || threatMapScratch.height != h){
            threatMapScratch = new ThreatMap(w, h);
            safeDistScratch = null;
            safeDistQueueScratch = null;
        }else{
            Arrays.fill(threatMapScratch.risk, 0f);
            Arrays.fill(threatMapScratch.floorRisk, 0f);
            Arrays.fill(threatMapScratch.drownRate, 0f);
        }
        threatMapScratch.safeDist = null;
        return threatMapScratch;
    }

    private void computeSafeDistance(ThreatMap map){
        if(map == null || map.risk == null) return;

        if(safeDistScratch == null || safeDistScratch.length < map.size){
            safeDistScratch = new short[map.size];
        }
        short[] dist = safeDistScratch;
        Arrays.fill(dist, (short)-1);

        if(safeDistQueueScratch == null || safeDistQueueScratch.length < map.size){
            safeDistQueueScratch = new int[map.size];
        }
        int[] queue = safeDistQueueScratch;
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

            float r = 0f;

            // Campaign/editor base shields that repel/kill units inside their radius.
            // See Mindustry core: mindustry.world.blocks.defense.BaseShield
            if(b.block != null && b.block.name != null){
                String name = b.block.name;
                float base = name.equals("shield-projector") ? 200f : (name.equals("large-shield-projector") ? 400f : 0f);
                if(base > 0.001f){
                    float eff = Mathf.clamp(b.efficiency, 0f, 1f);
                    r = base * eff + inflate;
                }
            }

            // Standard force projectors (kept for compatibility).
            if(r <= 0.001f && b.block instanceof ForceProjector && b instanceof ForceProjector.ForceBuild){
                ForceProjector.ForceBuild fb = (ForceProjector.ForceBuild)b;
                r = fb.realRadius() + inflate;
            }

            if(r <= 0.001f) continue;

            float r2 = r * r;

            // Expand bounds slightly; distance is tested using tile centers.
            int minX = clamp((int)Math.floor((b.x - r) / tilesize) - 1, 0, map.width - 1);
            int maxX = clamp((int)Math.ceil((b.x + r) / tilesize) + 1, 0, map.width - 1);
            int minY = clamp((int)Math.floor((b.y - r) / tilesize) - 1, 0, map.height - 1);
            int maxY = clamp((int)Math.ceil((b.y + r) / tilesize) + 1, 0, map.height - 1);

            for(int ty = minY; ty <= maxY; ty++){
                float wy = tileToWorld(ty) + tilesize / 2f;
                for(int tx = minX; tx <= maxX; tx++){
                    float wx = tileToWorld(tx) + tilesize / 2f;
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

    private void fillPassable(ThreatMap map, Unit unit, Seq<Unit> pathUnits, boolean treatFlying, float clearanceWorld){
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
        boolean avoidDrownLiquid = hasDrownableUnit(unit, pathUnits) && !allowSurvivableLiquidCross();

        int key = unit.type.id;
        key = key * 31 + (unit.type.allowLegStep ? 1 : 0);
        key = key * 31 + (unit.type.naval ? 1 : 0);
        key = key * 31 + (unit.type.canDrown ? 1 : 0);
        key = key * 31 + (avoidDrownLiquid ? 1 : 0);
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
                boolean p = passableFor(unit, world.tile(x, y), false, avoidDrownLiquid);
                if(p && clearanceTiles > 0){
                    for(int dy = -clearanceTiles; dy <= clearanceTiles && p; dy++){
                        for(int dx = -clearanceTiles; dx <= clearanceTiles; dx++){
                            int nx = x + dx;
                            int ny = y + dy;
                            if(nx < 0 || ny < 0 || nx >= map.width || ny >= map.height){
                                p = false;
                                break;
                            }
                            if(!passableFor(unit, world.tile(nx, ny), false, avoidDrownLiquid)){
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

    private void fillFloorHazards(ThreatMap map, Unit unit, boolean treatFlying){
        if(map == null || map.floorRisk == null || map.drownRate == null) return;
        if(world == null || unit == null || unit.type == null) return;
        if(treatFlying) return;

        boolean applyFloorDamage = !unit.type.hovering;
        boolean canDrown = unit.type.canDrown;
        float drownSizeFactor = Math.max(0.0001f, unit.hitSize / 8f * Math.max(0.0001f, unit.type.drownTimeMultiplier));
        float reserveTicks = drownReserveTicks();

        for(int y = 0; y < map.height; y++){
            for(int x = 0; x < map.width; x++){
                Tile tile = world.tile(x, y);
                if(tile == null) continue;

                Floor floor = tile.floor();
                if(floor == null) continue;

                int idx = x + y * map.width;

                if(applyFloorDamage){
                    float perTick = Math.max(0f, floor.damageTaken);
                    if(floor.status != null && floor.statusDuration > 0.0001f && (unit == null || !unit.isImmune(floor.status))){
                        perTick += Math.max(0f, floor.status.damage);
                    }
                    map.floorRisk[idx] = perTick * 60f;
                }

                if(canDrown && floor.isLiquid && floor.drownTime > 0f){
                    float denom = drownSizeFactor * floor.drownTime;
                    float safeDenom = denom - reserveTicks;
                    if(safeDenom > 0.0001f){
                        map.drownRate[idx] = 1f / safeDenom;
                    }else if(denom > 0.0001f){
                        map.drownRate[idx] = Float.POSITIVE_INFINITY;
                    }
                }
            }
        }
    }

    private static boolean passableFor(Unit unit, Tile tile, boolean treatFlying, boolean avoidDrownLiquid){
        if(tile == null) return false;
        if(treatFlying) return true;

        Floor floor = tile.floor();
        if(floor == null || floor.isAir()) return false;

        if(avoidDrownLiquid && floor.isLiquid && floor.drownTime > 0f) return false;

        if(unit.type.allowLegStep){
            return !tile.legSolid();
        }

        if(unit.type.naval){
            return !tile.solid() && tile.floor().isLiquid;
        }

        return !tile.solid();
    }

    private Seq<Threat> collectThreats(Unit playerUnit, boolean includeUnits, boolean threatsAir, boolean threatsGround){
        Seq<Threat> out = tmpThreats;
        Seq<Threat> derelictOut = tmpDerelictThreats;
        out.clear();
        derelictOut.clear();

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
            int tx = StealthPathMathUtil.worldToTile(target.x);
            int ty = StealthPathMathUtil.worldToTile(target.y);
            if(StealthPathMathUtil.inBounds(map, tx, ty)){
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
        if(!StealthPathMathUtil.inBounds(map, x, y)) return;
        int idx = x + y * map.width;
        if(!map.passable[idx]) return;
        out.add(idx);
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

    private PathResult findPath(ThreatMap map, int startX, int startY, IntSeq goals, boolean[] goalMask, PathMode mode, Unit unit, Seq<Unit> pathUnits, float speed){
        long searchStarted = System.nanoTime();
        Unit costUnit = useSlowestUnitForPathCost() ? slowestUnitRef(pathUnits, unit) : unit;
        float costSpeed = useSlowestUnitForPathCost() ? slowestUnitSpeed(pathUnits, unit) : Math.max(0.0001f, speed);
        int retries = 0;
        int blockedTilesTotal = 0;

        PathResult result = runPathSearch(map, startX, startY, goals, goalMask, mode, costUnit, costSpeed);
        if(result != null && result.path != null && !result.path.isEmpty() && hasDrownableUnit(unit, pathUnits) && pathWouldDrownForUnits(map, result.path, unit, pathUnits, costSpeed)){
            IntSeq restored = new IntSeq();
            int startIdx = startX + startY * map.width;

            // Retry a few times, each time blocking the drowning-critical liquid strip(s).
            for(int attempt = 0; attempt < 4; attempt++){
                IntSeq blocked = collectDrownBlockingTilesForUnits(map, result.path, unit, pathUnits, costSpeed);
                retries++;
                if(blocked.isEmpty()){
                    result = null;
                    break;
                }

                int added = 0;
                for(int i = 0; i < blocked.size; i++){
                    int idx = blocked.items[i];
                    if(idx < 0 || idx >= map.size) continue;
                    if(idx == startIdx) continue;
                    if(!map.passable[idx]) continue;
                    map.passable[idx] = false;
                    restored.add(idx);
                    added++;
                }
                blockedTilesTotal += added;

                if(added == 0){
                    result = null;
                    break;
                }

                result = runPathSearch(map, startX, startY, goals, goalMask, mode, costUnit, costSpeed);
                if(result == null || result.path == null || result.path.isEmpty()) break;
                if(!pathWouldDrownForUnits(map, result.path, unit, pathUnits, costSpeed)) break;
            }

            for(int i = 0; i < restored.size; i++){
                map.passable[restored.items[i]] = true;
            }

            if(result == null || result.path == null || result.path.isEmpty()){
                result = null;
            }else if(pathWouldDrownForUnits(map, result.path, unit, pathUnits, costSpeed)){
                result = null;
            }
        }

        int pathTiles = (result == null || result.path == null) ? 0 : result.path.size;
        logPlan(logFormat("sp.log.plan.search",
            pathModeName(mode),
            startX,
            startY,
            goals == null ? 0 : goals.size,
            Strings.autoFixed(costSpeed, 2),
            retries,
            blockedTilesTotal,
            pathTiles,
            (result != null && result.path != null && !result.path.isEmpty()),
            elapsedMillis(searchStarted)));

        if(retries > 0 || blockedTilesTotal > 0){
            logDrown(logFormat("sp.log.drown.check",
                pathModeName(mode),
                retries,
                blockedTilesTotal,
                pathTiles,
                (result != null && result.path != null && !result.path.isEmpty())));
        }

        return result;
    }

    private PathResult runPathSearch(ThreatMap map, int startX, int startY, IntSeq goals, boolean[] goalMask, PathMode mode, Unit unit, float speed){
        return pathfinderMode() == pathfinderDfs
            ? findPathDfs(map, startX, startY, goals, goalMask, mode, unit, speed)
            : findPathAStar(map, startX, startY, goals, goalMask, mode, unit, speed);
    }

    private static float segmentDistanceWorld(ThreatMap map, int a, int b){
        int ax = a % map.width;
        int ay = a / map.width;
        int bx = b % map.width;
        int by = b / map.width;

        int dx = Math.abs(bx - ax);
        int dy = Math.abs(by - ay);
        float step = (dx + dy == 1) ? 1f : Mathf.sqrt2;
        return tilesize * step;
    }

    private static boolean isDrownCandidate(Unit unit){
        return unit != null
            && unit.type != null
            && unit.type.canDrown
            && !unit.type.hovering
            && !unit.type.flying
            && !unit.type.naval;
    }

    private static boolean hasDrownableUnit(Unit fallback, Seq<Unit> units){
        if(units != null && units.any()){
            for(int i = 0; i < units.size; i++){
                if(isDrownCandidate(units.get(i))) return true;
            }
        }
        return isDrownCandidate(fallback);
    }

    private static Floor floorAt(ThreatMap map, int idx){
        if(map == null || idx < 0 || idx >= map.size || world == null) return null;
        int x = idx % map.width;
        int y = idx / map.width;
        Tile tile = world.tile(x, y);
        return tile == null ? null : tile.floor();
    }

    private static float floorSpeedMultiplierForUnit(Unit unit, Floor floor){
        if(unit == null || unit.type == null || floor == null) return 1f;
        float sm = Math.max(0.0001f, floor.speedMultiplier);
        return (float)Math.pow(sm, unit.type.floorMultiplier);
    }

    private static float floorStatusSpeedMultiplierForUnit(Unit unit, Floor floor){
        if(unit == null || floor == null || floor.status == null) return 1f;
        if(!includeFloorStatusSlowdown()) return 1f;
        if(unit.isImmune(floor.status)) return 1f;
        return Math.max(0.0001f, floor.status.speedMultiplier);
    }

    private static float floorTravelSpeedMultiplierForUnit(Unit unit, Floor floor){
        return floorSpeedMultiplierForUnit(unit, floor) * floorStatusSpeedMultiplierForUnit(unit, floor);
    }

    private static float nominalSpeedForUnit(Unit unit, float fallbackSpeed){
        float base = Math.max(0.0001f, fallbackSpeed);
        if(unit == null || unit.type == null) return base;

        float typeSpeed = Math.max(0.0001f, unit.type.speed);
        // Keep transient effects from the current state, but avoid binding path speed to current floor tile.
        float runtime = Math.max(0.0001f, unit.speed());
        return Math.max(typeSpeed, runtime);
    }

    private static float segmentSpeedForUnit(ThreatMap map, int a, int b, Unit unit, float fallbackSpeed){
        float base = nominalSpeedForUnit(unit, fallbackSpeed);
        if(unit == null || unit.type == null) return base;
        if(unit.type.flying || unit.type.hovering) return base;

        Floor floorA = floorAt(map, a);
        Floor floorB = floorAt(map, b);
        float segMulA = floorTravelSpeedMultiplierForUnit(unit, floorA);
        float segMulB = floorTravelSpeedMultiplierForUnit(unit, floorB);
        float segMul = (segMulA + segMulB) * 0.5f;

        return Math.max(0.0001f, base * segMul);
    }

    private static float segmentTicksForUnit(ThreatMap map, int a, int b, Unit unit, float fallbackSpeed){
        float speed = segmentSpeedForUnit(map, a, b, unit, fallbackSpeed);
        return segmentDistanceWorld(map, a, b) / Math.max(0.0001f, speed);
    }

    private static float drownRateAt(ThreatMap map, int idx, Unit unit){
        if(!isDrownCandidate(unit)) return 0f;

        Floor floor = floorAt(map, idx);
        if(floor == null || !floor.isLiquid || floor.drownTime <= 0f) return 0f;

        float unitFactor = Math.max(0.0001f, unit.hitSize / 8f * Math.max(0.0001f, unit.type.drownTimeMultiplier));
        float denom = floor.drownTime * unitFactor;
        if(denom <= 0.0001f) return 0f;

        float safeDenom = denom - drownReserveTicks();
        if(safeDenom <= 0.0001f) return Float.POSITIVE_INFINITY;
        return 1f / safeDenom;
    }

    private static float avgDrownRateForUnit(ThreatMap map, int a, int b, Unit unit){
        return (drownRateAt(map, a, unit) + drownRateAt(map, b, unit)) * 0.5f;
    }

    private static boolean pathWouldDrown(ThreatMap map, IntSeq tilePath, Unit unit, float fallbackSpeed){
        if(map == null || tilePath == null || tilePath.size <= 1) return false;
        if(!isDrownCandidate(unit)) return false;

        float progress = 0f;
        for(int i = 0; i < tilePath.size - 1; i++){
            int a = tilePath.items[i];
            int b = tilePath.items[i + 1];
            float ticks = segmentTicksForUnit(map, a, b, unit, fallbackSpeed);
            float rate = avgDrownRateForUnit(map, a, b, unit);

            if(rate > 0.000001f){
                progress += rate * ticks;
                if(progress >= 0.999f) return true;
                progress = Math.min(progress, 1f);
            }else{
                progress = Math.max(0f, progress - ticks / 50f);
            }
        }

        return false;
    }

    private static boolean pathWouldDrownForUnits(ThreatMap map, IntSeq tilePath, Unit fallback, Seq<Unit> units, float fallbackSpeed){
        if(map == null || tilePath == null || tilePath.size <= 1) return false;

        boolean checkedAny = false;
        if(units != null && units.any()){
            for(int i = 0; i < units.size; i++){
                Unit u = units.get(i);
                if(!isDrownCandidate(u)) continue;
                checkedAny = true;
                float speed = nominalSpeedForUnit(u, fallbackSpeed);
                if(pathWouldDrown(map, tilePath, u, speed)) return true;
            }
        }

        if(checkedAny) return false;
        return pathWouldDrown(map, tilePath, fallback, fallbackSpeed);
    }

    private static IntSeq collectDrownBlockingTiles(ThreatMap map, IntSeq tilePath, Unit unit, float fallbackSpeed){
        IntSeq blocked = new IntSeq();
        if(map == null || tilePath == null || tilePath.size <= 1) return blocked;
        if(!isDrownCandidate(unit)) return blocked;

        boolean[] marked = new boolean[map.size];
        boolean[] inRun = new boolean[map.size];
        IntSeq run = new IntSeq();
        boolean runFatal = false;
        float progress = 0f;

        for(int i = 0; i < tilePath.size - 1; i++){
            int a = tilePath.items[i];
            int b = tilePath.items[i + 1];
            float ticks = segmentTicksForUnit(map, a, b, unit, fallbackSpeed);
            float rate = avgDrownRateForUnit(map, a, b, unit);

            if(rate > 0.000001f){
                if(a >= 0 && a < map.size && !inRun[a]){
                    inRun[a] = true;
                    run.add(a);
                }
                if(b >= 0 && b < map.size && !inRun[b]){
                    inRun[b] = true;
                    run.add(b);
                }

                progress += rate * ticks;
                if(progress >= 0.999f){
                    runFatal = true;
                    progress = 1f;
                }
            }else{
                if(runFatal){
                    for(int r = 0; r < run.size; r++){
                        int idx = run.items[r];
                        if(idx < 0 || idx >= map.size || marked[idx]) continue;
                        marked[idx] = true;
                        blocked.add(idx);
                    }
                }

                for(int r = 0; r < run.size; r++){
                    int idx = run.items[r];
                    if(idx >= 0 && idx < map.size) inRun[idx] = false;
                }
                run.clear();
                runFatal = false;
                progress = Math.max(0f, progress - ticks / 50f);
            }
        }

        if(runFatal){
            for(int r = 0; r < run.size; r++){
                int idx = run.items[r];
                if(idx < 0 || idx >= map.size || marked[idx]) continue;
                marked[idx] = true;
                blocked.add(idx);
            }
        }

        return blocked;
    }

    private static IntSeq collectDrownBlockingTilesForUnits(ThreatMap map, IntSeq tilePath, Unit fallback, Seq<Unit> units, float fallbackSpeed){
        IntSeq out = new IntSeq();
        if(map == null || tilePath == null || tilePath.size <= 1) return out;

        boolean checkedAny = false;
        if(units != null && units.any()){
            for(int i = 0; i < units.size; i++){
                Unit u = units.get(i);
                if(!isDrownCandidate(u)) continue;
                checkedAny = true;
                float speed = nominalSpeedForUnit(u, fallbackSpeed);
                if(!pathWouldDrown(map, tilePath, u, speed)) continue;

                IntSeq blocked = collectDrownBlockingTiles(map, tilePath, u, speed);
                mergeUniqueInts(out, blocked);
            }
        }

        if(!checkedAny && pathWouldDrown(map, tilePath, fallback, fallbackSpeed)){
            mergeUniqueInts(out, collectDrownBlockingTiles(map, tilePath, fallback, fallbackSpeed));
        }

        return out;
    }

    private static void mergeUniqueInts(IntSeq dst, IntSeq src){
        if(dst == null || src == null || src.isEmpty()) return;
        for(int i = 0; i < src.size; i++){
            pushUnique(dst, src.items[i]);
        }
    }

    private static void pushUnique(IntSeq seq, int value){
        for(int i = 0; i < seq.size; i++){
            if(seq.items[i] == value) return;
        }
        seq.add(value);
    }

    private PathResult findPathAStar(ThreatMap map, int startX, int startY, IntSeq goals, boolean[] goalMask, PathMode mode, Unit unit, float speed){
        int startIdx = startX + startY * map.width;
        if(!map.passable[startIdx]) return null;
        boolean safeOnly = mode == PathMode.safeOnly;
        boolean nearest = mode == PathMode.nearest;
        if(safeOnly && !isZeroDamageTile(map, startIdx)) return null;

        ensurePathScratch(map.size);
        int stamp = nextPathStamp();

        pathOpen.clear();

        pathBestStamp[startIdx] = stamp;
        pathBest[startIdx] = 0f;
        pathParent[startIdx] = -1;
        pathOpen.add(new Node(startIdx, heuristic(map, startX, startY, goals, mode), 0f));

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
                    if(safeOnly && !isZeroDamageTile(map, nidx)) continue;

                    // No cutting corners through blocked tiles.
                    if(dx != 0 && dy != 0){
                        int aidx = (x + dx) + y * map.width;
                        int bidx = x + (y + dy) * map.width;
                        if(!map.passable[aidx] || !map.passable[bidx]) continue;
                        if(safeOnly && (!isZeroDamageTile(map, aidx) || !isZeroDamageTile(map, bidx))) continue;
                    }

                    float step = (dx == 0 || dy == 0) ? 1f : Mathf.sqrt2;

                    float ng;
                    if(safeOnly || nearest){
                        float tie = step * 0.0001f;
                        ng = pathBest[idx] + step + tie;

                        // Prefer the center of safe corridors (farther from risk zones) when multiple safe paths exist.
                        if(safeOnly && map.safeDist != null && map.safeBias > 0.0001f){
                            int sd = map.safeDist[nidx];
                            // Larger safeDist => smaller penalty; bias scales with formation size.
                            float centerBias = map.safeBias * safeCorridorBiasFactor();
                            ng += centerBias / (Math.max(0f, sd) + 1f);
                        }
                    }else{
                        float distWorld = tilesize * step;
                        float dmg = edgeDamage(map, idx, nidx, distWorld, unit, speed, true);
                        float tie = step * 0.001f;
                        ng = pathBest[idx] + dmg + tie;
                    }

                    float prev = (pathBestStamp[nidx] == stamp) ? pathBest[nidx] : Float.POSITIVE_INFINITY;
                    if(ng >= prev) continue;

                    pathBestStamp[nidx] = stamp;
                    pathBest[nidx] = ng;
                    pathParent[nidx] = idx;

                    float h = heuristic(map, nx, ny, goals, mode);
                    pathOpen.add(new Node(nidx, ng + h, ng));
                }
            }
        }

        return null;
    }

    private PathResult findPathDfs(ThreatMap map, int startX, int startY, IntSeq goals, boolean[] goalMask, PathMode mode, Unit unit, float speed){
        int startIdx = startX + startY * map.width;
        if(!map.passable[startIdx]) return null;

        boolean safeOnly = mode == PathMode.safeOnly;
        boolean nearest = mode == PathMode.nearest;
        if(safeOnly && !isZeroDamageTile(map, startIdx)) return null;

        ensurePathScratch(map.size);
        int stamp = nextPathStamp();

        pathStack.clear();
        pathStack.add(startIdx);

        pathBestStamp[startIdx] = stamp;
        pathBest[startIdx] = 0f;
        pathParent[startIdx] = -1;

        float bestGoal = Float.POSITIVE_INFINITY;
        int bestGoalIdx = -1;

        while(pathStack.size > 0){
            int idx = pathStack.pop();
            if(pathBestStamp[idx] != stamp) continue;

            float g = pathBest[idx];
            if(g >= bestGoal) continue;

            if(goalMask[idx]){
                bestGoal = g;
                bestGoalIdx = idx;
                continue;
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
                    if(!map.passable[nidx]) continue;
                    if(safeOnly && !isZeroDamageTile(map, nidx)) continue;

                    // No cutting corners through blocked tiles.
                    if(dx != 0 && dy != 0){
                        int aidx = (x + dx) + y * map.width;
                        int bidx = x + (y + dy) * map.width;
                        if(!map.passable[aidx] || !map.passable[bidx]) continue;
                        if(safeOnly && (!isZeroDamageTile(map, aidx) || !isZeroDamageTile(map, bidx))) continue;
                    }

                    float step = (dx == 0 || dy == 0) ? 1f : Mathf.sqrt2;

                    float ng;
                    if(safeOnly || nearest){
                        float tie = step * 0.0001f;
                        ng = g + step + tie;

                        if(safeOnly && map.safeDist != null && map.safeBias > 0.0001f){
                            int sd = map.safeDist[nidx];
                            float centerBias = map.safeBias * safeCorridorBiasFactor();
                            ng += centerBias / (Math.max(0f, sd) + 1f);
                        }
                    }else{
                        float distWorld = tilesize * step;
                        float dmg = edgeDamage(map, idx, nidx, distWorld, unit, speed, true);
                        float tie = step * 0.001f;
                        ng = g + dmg + tie;
                    }

                    if(ng >= bestGoal) continue;

                    float prev = (pathBestStamp[nidx] == stamp) ? pathBest[nidx] : Float.POSITIVE_INFINITY;
                    if(ng >= prev) continue;

                    pathBestStamp[nidx] = stamp;
                    pathBest[nidx] = ng;
                    pathParent[nidx] = idx;
                    pathStack.add(nidx);
                }
            }
        }

        if(bestGoalIdx == -1) return null;
        return new PathResult(reconstruct(pathParent, bestGoalIdx));
    }

    private static float heuristic(ThreatMap map, int x, int y, IntSeq goals, PathMode mode){
        float best = Float.POSITIVE_INFINITY;
        for(int i = 0; i < goals.size; i++){
            int idx = goals.items[i];
            int gx = idx % map.width;
            int gy = idx / map.width;
            float dst = Mathf.dst(x, y, gx, gy);
            if(dst < best) best = dst;
        }

        if(!Float.isFinite(best)) return 0f;
        return mode == PathMode.minDamage ? best * 0.001f : best;
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

    // Path compaction/hash extracted into `StealthPathPathUtil`.

    private static float edgeDamage(ThreatMap map, int a, int b, float distWorld, float speed, boolean includeThreat){
        if(map == null || distWorld <= 0.0001f) return 0f;

        float v = Math.max(0.0001f, speed);
        float seconds = distWorld / (v * 60f);

        float dps = 0f;
        if(includeThreat && map.risk != null){
            dps += (map.risk[a] + map.risk[b]) * 0.5f;
        }
        if(map.floorRisk != null){
            dps += (map.floorRisk[a] + map.floorRisk[b]) * 0.5f;
        }

        return dps * seconds;
    }

    private static float floorDamagePerTickForUnit(Floor floor, Unit unit){
        if(floor == null) return 0f;
        if(unit != null && unit.type != null && unit.type.hovering) return 0f;

        float perTick = Math.max(0f, floor.damageTaken);
        if(floor.status != null && floor.statusDuration > 0.0001f && (unit == null || !unit.isImmune(floor.status))){
            perTick += Math.max(0f, floor.status.damage);
        }
        return perTick;
    }

    private static float edgeDamage(ThreatMap map, int a, int b, float distWorld, Unit unit, float fallbackSpeed, boolean includeThreat){
        if(map == null || distWorld <= 0.0001f) return 0f;

        float segSpeed = segmentSpeedForUnit(map, a, b, unit, fallbackSpeed);
        float seconds = distWorld / (Math.max(0.0001f, segSpeed) * 60f);

        float dps = 0f;
        if(includeThreat && map.risk != null){
            dps += (map.risk[a] + map.risk[b]) * 0.5f;
        }

        Floor floorA = floorAt(map, a);
        Floor floorB = floorAt(map, b);
        float floorPerTick = (floorDamagePerTickForUnit(floorA, unit) + floorDamagePerTickForUnit(floorB, unit)) * 0.5f;
        dps += floorPerTick * 60f;

        return dps * seconds;
    }

    private static float estimateDamage(ThreatMap map, IntSeq tilePath, Unit unit){
        if(tilePath == null || tilePath.size <= 1) return 0f;
        if(unit == null) return 0f;

        float fallbackSpeed = Math.max(0.0001f, unit.speed());
        float dmg = 0f;

        for(int i = 0; i < tilePath.size - 1; i++){
            int a = tilePath.items[i];
            int b = tilePath.items[i + 1];
            float distWorld = segmentDistanceWorld(map, a, b);
            dmg += edgeDamage(map, a, b, distWorld, unit, fallbackSpeed, true);
        }

        return dmg;
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
            dmg += edgeDamage(map, a, b, distWorld, speed, true);
        }

        return dmg;
    }

    private static float estimateDamageForUnits(ThreatMap map, IntSeq tilePath, Seq<Unit> units, Unit fallbackUnit, float fallbackSpeed){
        if(tilePath == null || tilePath.size <= 1) return 0f;

        if(useSlowestUnitForPathCost()){
            Unit slowest = slowestUnitRef(units, fallbackUnit);
            if(slowest != null) return estimateDamage(map, tilePath, slowest);
            return estimateDamage(map, tilePath, fallbackSpeed);
        }

        float max = 0f;
        boolean hasAny = false;
        if(units != null && units.any()){
            for(int i = 0; i < units.size; i++){
                Unit u = units.get(i);
                if(u == null || !u.isAdded() || u.dead()) continue;
                hasAny = true;
                max = Math.max(max, estimateDamage(map, tilePath, u));
            }
        }
        if(hasAny) return max;
        if(fallbackUnit != null) return estimateDamage(map, tilePath, fallbackUnit);
        return estimateDamage(map, tilePath, fallbackSpeed);
    }

    private static float estimateDamageForUnitsByFlight(ThreatMap map, IntSeq tilePath, Seq<Unit> units, Unit fallbackUnit, boolean wantFlying){
        if(tilePath == null || tilePath.size <= 1) return Float.NaN;

        if(useSlowestUnitForPathCost()){
            Unit slowest = slowestUnitRefByFlight(units, fallbackUnit, wantFlying);
            if(slowest == null) return Float.NaN;
            return estimateDamage(map, tilePath, slowest);
        }

        float max = Float.NaN;
        if(units != null && units.any()){
            for(int i = 0; i < units.size; i++){
                Unit u = units.get(i);
                if(u == null || !u.isAdded() || u.dead()) continue;
                if(u.isFlying() != wantFlying) continue;
                float d = estimateDamage(map, tilePath, u);
                max = Float.isNaN(max) ? d : Math.max(max, d);
            }
        }

        if(!Float.isNaN(max)) return max;
        if(fallbackUnit != null && fallbackUnit.isFlying() == wantFlying) return estimateDamage(map, tilePath, fallbackUnit);
        return Float.NaN;
    }

    private void showToast(String keyOrText, float seconds){
        if(ui == null) return;
        if(!Core.settings.getBool(keyShowToasts, true)) return;
        String text = keyOrText.startsWith("@") ? Core.bundle.get(keyOrText.substring(1)) : keyOrText;
        ui.showInfoToast(text, seconds);
    }

    private void ensureOverlayWindowsAttached(){
        // Avoid spamming reflection attempts every frame.
        if(Time.time < overlayNextAttachAttempt) return;
        overlayNextAttachAttempt = Time.time + 60f;

        if(ui == null || ui.hudGroup == null) return;

        if(overlayModeContent == null || overlayDamageContent == null || overlayControlsContent == null){
            buildOverlayWindows();
        }

        boolean enabled = Core.settings.getBool(keyEnabled, true);

        if(xOverlayUi.isInstalled()){
            try{
                // When hosted by OverlayUI, do not manage position/size ourselves.
                if(xModeWindow == null){
                    try{ overlayModeContent.remove(); }catch(Throwable ignored){}
                    xModeWindow = xOverlayUi.registerWindow(
                        "偷袭小道-模式",
                        overlayModeContent,
                        () -> state != null && state.isGame() && Core.settings.getBool(keyEnabled, true)
                    );
                    xOverlayUi.tryConfigureWindow(xModeWindow, false, true);
                    if(enabled) xOverlayUi.setEnabledAndPinned(xModeWindow, true, false);
                }
                if(xDamageWindow == null){
                    try{ overlayDamageContent.remove(); }catch(Throwable ignored){}
                    xDamageWindow = xOverlayUi.registerWindow(
                        "偷袭小道-伤害",
                        overlayDamageContent,
                        () -> state != null && state.isGame() && Core.settings.getBool(keyEnabled, true)
                    );
                    xOverlayUi.tryConfigureWindow(xDamageWindow, false, true);
                    if(enabled) xOverlayUi.setEnabledAndPinned(xDamageWindow, true, false);
                }
                if(xControlsWindow == null){
                    try{ overlayControlsContent.remove(); }catch(Throwable ignored){}
                    xControlsWindow = xOverlayUi.registerWindow(
                        "偷袭小道-控制",
                        overlayControlsContent,
                        () -> state != null && state.isGame() && Core.settings.getBool(keyEnabled, true)
                    );
                    xOverlayUi.tryConfigureWindow(xControlsWindow, false, true);
                    if(enabled) xOverlayUi.setEnabledAndPinned(xControlsWindow, true, false);
                }
                return;
            }catch(Throwable ignored){
                xModeWindow = null;
                xDamageWindow = null;
                xControlsWindow = null;
            }
        }

        // Fallback: attach directly to HUD group (vanilla client, or OverlayUI unavailable).
        syncFallbackHud(overlayModeContent, "sp-ov-mode", 8f, -8f, enabled);
        syncFallbackHud(overlayDamageContent, "sp-ov-dmg", 8f, -84f, enabled);
        syncFallbackHud(overlayControlsContent, "sp-ov-ctl", 8f, -152f, enabled);
    }

    private void syncFallbackHud(Table content, String name, float x, float yFromTop, boolean visible){
        if(ui == null || ui.hudGroup == null) return;
        Element existing = ui.hudGroup.find(name);
        if(!visible){
            if(existing != null){
                try{ existing.remove(); }catch(Throwable ignored){}
            }
            return;
        }
        attachFallbackHud(content, name, x, yFromTop);
    }

    private void attachFallbackHud(Table content, String name, float x, float yFromTop){
        if(content == null || ui == null || ui.hudGroup == null) return;
        if(ui.hudGroup.find(name) != null) return;
        try{ content.remove(); }catch(Throwable ignored){}
        content.name = name;
        ui.hudGroup.addChild(content);
        content.toFront();
        content.update(() -> {
            // Anchor near top-left; yFromTop is negative pixels down from top.
            content.setPosition(x, Core.graphics.getHeight() + yFromTop, Align.topLeft);
        });
    }

    private void buildOverlayWindows(){
        // Neon overlay theme shared across bundled windows.
        Color bg = Color.valueOf("111821");
        Color border = Color.valueOf("233246");
        Color fg = Color.valueOf("d8e2ee");
        Color key = Color.valueOf("79c7ff");
        Color type = Color.valueOf("7dd9c4");
        Color value = Color.valueOf("b8e5ff");
        Color number = Color.valueOf("ffd58a");
        Color accent = Color.valueOf("4ea8ff");
        Color warn = Color.valueOf("ffb774");

        Drawable bgDraw = tintDrawable(Tex.whiteui == null ? Tex.pane : Tex.whiteui, bg);
        Drawable borderDraw = tintDrawable(Tex.whiteui == null ? Tex.pane : Tex.whiteui, border);

        arc.scene.ui.TextButton.TextButtonStyle btnStyle = new arc.scene.ui.TextButton.TextButtonStyle(Styles.flatt);
        btnStyle.up = borderDraw;
        btnStyle.over = tintDrawable(Tex.whiteui == null ? borderDraw : Tex.whiteui, Color.valueOf("2c3e56"));
        btnStyle.down = tintDrawable(Tex.whiteui == null ? borderDraw : Tex.whiteui, Color.valueOf("3f5f86"));
        btnStyle.fontColor = fg;

        // Window 1: mode + threat.
        overlayModeContent = new Table();
        overlayModeContent.background(bgDraw);
        overlayModeContent.margin(8f);
        overlayModeContent.touchable = Touchable.disabled;
        overlayModeContent.defaults().left().growX().minWidth(0f);

        overlayModeContent.table(t -> {
            t.background(borderDraw);
            t.margin(6f);
            t.add("SP").color(accent).padRight(6f);
            t.add("状态").color(key);
        }).growX().row();

        overlayModeContent.table(t -> {
            t.left().defaults().minWidth(0f);
            t.add("模式").color(key).padRight(8f);
            overlayModeValue = new Label("", Styles.outlineLabel);
            overlayModeValue.setWrap(true);
            overlayModeValue.setColor(value);
            overlayModeValue.update(() -> overlayModeValue.setText(overlayPathModeText()));
            t.add(overlayModeValue).left().growX();
        }).padTop(6f).growX().row();

        overlayModeContent.table(t -> {
            t.left().defaults().minWidth(0f);
            t.add("威胁").color(key).padRight(8f);
            overlayThreatValue = new Label("", Styles.outlineLabel);
            overlayThreatValue.setWrap(true);
            overlayThreatValue.setColor(type);
            overlayThreatValue.update(() -> overlayThreatValue.setText(threatModeDisplay(Core.settings.getInt(keyThreatMode, threatModeGround))));
            t.add(overlayThreatValue).left().growX();
        }).padTop(2f).growX().row();

        // Allow arbitrary resize in MindustryX OverlayUI (prevents "snap back" on resize end).
        overlayModeContent.add(new PreferAnySize()).grow().row();

        // Window 2: damage.
        overlayDamageContent = new Table();
        overlayDamageContent.background(bgDraw);
        overlayDamageContent.margin(8f);
        overlayDamageContent.touchable = Touchable.disabled;
        overlayDamageContent.defaults().left().growX().minWidth(0f);

        overlayDamageContent.table(t -> {
            t.background(borderDraw);
            t.margin(6f);
            t.add("SP").color(accent).padRight(6f);
            t.add("伤害").color(key);
        }).growX().row();

        overlayDamageContent.table(t -> {
            t.left().defaults().minWidth(0f);
            t.add("预计").color(key).padRight(8f);
            overlayDamageValue = new Label("", Styles.outlineLabel);
            overlayDamageValue.setColor(number);
            overlayDamageValue.update(() -> overlayDamageValue.setText(Strings.autoFixed(Math.max(0f, lastDamage), 2)));
            t.add(overlayDamageValue).left().growX();
        }).padTop(6f).growX().row();

        // Allow arbitrary resize in MindustryX OverlayUI (prevents "snap back" on resize end).
        overlayDamageContent.add(new PreferAnySize()).grow().row();

        // Window 3: controls.
        overlayControlsContent = new Table();
        overlayControlsContent.background(bgDraw);
        overlayControlsContent.margin(8f);
        overlayControlsContent.touchable = Touchable.childrenOnly;
        overlayControlsContent.defaults().left().growX().minWidth(0f);

        overlayControlsContent.table(t -> {
            t.background(borderDraw);
            t.margin(6f);
            t.add("SP").color(accent).padRight(6f);
            t.add("快捷控制").color(key);
        }).growX().row();

        Table buttonsHost = new Table();
        buttonsHost.left();
        overlayControlsContent.add(buttonsHost).growX().row();

        // Allow arbitrary resize in MindustryX OverlayUI (prevents "snap back" on resize end).
        overlayControlsContent.add(new PreferAnySize()).grow().row();

        arc.scene.ui.TextButton bx = new TextButton("X：仅炮塔", btnStyle);
        bx.getLabel().setColor(accent);
        bx.getLabel().setWrap(true);
        bx.getLabelCell().growX().minWidth(0f);
        bx.clicked(() -> {
            lastIncludeUnits = false;
            computePath(false, false);
        });
        bx.update(() -> bx.getLabel().setColor((autoMode == autoModeOff && !lastIncludeUnits) ? warn : accent));

        arc.scene.ui.TextButton by = new TextButton("Y：炮塔+单位", btnStyle);
        by.getLabel().setColor(accent);
        by.getLabel().setWrap(true);
        by.getLabelCell().growX().minWidth(0f);
        by.clicked(() -> {
            lastIncludeUnits = true;
            computePath(true, false);
        });
        by.update(() -> by.getLabel().setColor((autoMode == autoModeOff && lastIncludeUnits) ? warn : accent));

        arc.scene.ui.TextButton bn = new TextButton("N：自动→鼠标", btnStyle);
        bn.getLabel().setColor(fg);
        bn.getLabel().setWrap(true);
        bn.getLabelCell().growX().minWidth(0f);
        bn.clicked(() -> toggleAutoMode(autoModeMouse));
        bn.update(() -> bn.getLabel().setColor(autoMode == autoModeMouse ? warn : fg));

        arc.scene.ui.TextButton bm = new TextButton("M：自动→攻击", btnStyle);
        bm.getLabel().setColor(fg);
        bm.getLabel().setWrap(true);
        bm.getLabelCell().growX().minWidth(0f);
        bm.clicked(() -> toggleAutoMode(autoModeAttack));
        bm.update(() -> bm.getLabel().setColor(autoMode == autoModeAttack ? warn : fg));

        arc.scene.ui.TextButton bt = new TextButton("L：切换威胁模式", btnStyle);
        bt.getLabel().setColor(type);
        bt.getLabel().setWrap(true);
        bt.getLabelCell().growX().minWidth(0f);
        bt.clicked(this::cycleThreatMode);

        final float[] lastW = {Float.NaN};
        final float[] lastH = {Float.NaN};
        overlayControlsContent.update(() -> {
            float w = Math.max(0f, overlayControlsContent.getWidth());
            float h = Math.max(0f, overlayControlsContent.getHeight());
            if(Math.abs(w - lastW[0]) <= 2f && Math.abs(h - lastH[0]) <= 2f) return;
            lastW[0] = w;
            lastH[0] = h;

            // Responsive layout: vertical resize => stack; wide resize => multi-column grid.
            int cols = w >= 520f ? 3 : (w >= 340f ? 2 : 1);

            buttonsHost.clearChildren();
            buttonsHost.left().defaults().minWidth(0f).height(38f);

            if(cols <= 1){
                buttonsHost.defaults().growX();
                buttonsHost.add(bx).row();
                buttonsHost.add(by).padTop(6f).row();
                buttonsHost.add(bn).padTop(6f).row();
                buttonsHost.add(bm).padTop(6f).row();
                buttonsHost.add(bt).padTop(6f).row();
            }else if(cols == 2){
                buttonsHost.defaults().growX();
                buttonsHost.add(bx).padRight(6f);
                buttonsHost.add(by).row();
                buttonsHost.add(bn).padTop(6f).padRight(6f);
                buttonsHost.add(bm).padTop(6f).row();
                buttonsHost.add(bt).colspan(2).padTop(6f).row();
            }else{
                // 3 columns: put X/Y/Threat on first row; N/M on second row.
                buttonsHost.defaults().growX();
                buttonsHost.add(bx).padRight(6f);
                buttonsHost.add(by).padRight(6f);
                buttonsHost.add(bt).row();
                buttonsHost.add(bn).padTop(6f).padRight(6f).colspan(2);
                buttonsHost.add(bm).padTop(6f).row();
            }
        });
    }

    private static Drawable tintDrawable(Drawable base, Color tint){
        if(base == null || tint == null) return base;
        if(base instanceof arc.scene.style.TextureRegionDrawable){
            return ((arc.scene.style.TextureRegionDrawable)base).tint(tint);
        }
        return base;
    }

    private static class PreferAnySize extends Element{
        @Override
        public float getMinWidth(){
            return 0f;
        }

        @Override
        public float getPrefWidth(){
            return getWidth();
        }

        @Override
        public float getMinHeight(){
            return 0f;
        }

        @Override
        public float getPrefHeight(){
            return getHeight();
        }
    }

    private String overlayPathModeText(){
        if(autoMode == autoModeMouse) return "N / 自动 → 鼠标";
        if(autoMode == autoModeAttack) return "M / 自动 → 攻击";
        return lastIncludeUnits ? "Y / 路径（炮塔+单位）" : "X / 路径（仅炮塔）";
    }

    /** Optional integration with MindustryX OverlayUI. Uses reflection so vanilla builds won't crash. */
    private static class MindustryXOverlayUI{
        private boolean initialized = false;
        private boolean installed = false;
        private Object instance;
        private Method registerWindow;
        private Method setAvailability;
        private Method setResizable;
        private Method setAutoHeight;
        private Method getData;
        private Method setEnabled;
        private Method setPinned;
        private boolean accessorsInitialized = false;

        boolean isInstalled(){
            if(initialized) return installed;
            initialized = true;
            try{
                installed = mindustry.Vars.mods != null && mindustry.Vars.mods.locateMod("mindustryx") != null;
            }catch(Throwable ignored){
                installed = false;
            }
            if(!installed) return false;

            try{
                Class<?> c = Class.forName("mindustryX.features.ui.OverlayUI");
                instance = c.getField("INSTANCE").get(null);
                registerWindow = c.getMethod("registerWindow", String.class, Table.class);
            }catch(Throwable t){
                installed = false;
                return false;
            }
            return true;
        }

        Object registerWindow(String name, Table table, Prov<Boolean> availability){
            if(!isInstalled()) return null;
            try{
                Object window = registerWindow.invoke(instance, name, table);
                tryInitWindowAccessors(window);
                if(window != null && availability != null && setAvailability != null){
                    setAvailability.invoke(window, availability);
                }
                return window;
            }catch(Throwable t){
                return null;
            }
        }

        void setEnabledAndPinned(Object window, boolean enabled, boolean pinned){
            if(window == null) return;
            try{
                tryInitWindowAccessors(window);
                if(getData == null) return;
                Object data = getData.invoke(window);
                if(data == null) return;
                if(setEnabled != null) setEnabled.invoke(data, enabled);
                if(setPinned != null) setPinned.invoke(data, pinned);
            }catch(Throwable ignored){
            }
        }

        void tryConfigureWindow(Object window, boolean autoHeight, boolean resizable){
            if(window == null) return;
            try{
                tryInitWindowAccessors(window);
                if(setAutoHeight != null) setAutoHeight.invoke(window, autoHeight);
                if(setResizable != null) setResizable.invoke(window, resizable);
            }catch(Throwable ignored){
            }
        }

        private void tryInitWindowAccessors(Object window){
            if(window == null) return;
            if(accessorsInitialized && (getData != null || setAvailability != null || setResizable != null || setAutoHeight != null)) return;
            try{
                Class<?> wc = window.getClass();
                try{
                    setAvailability = wc.getMethod("setAvailability", Prov.class);
                }catch(Throwable ignored){
                    setAvailability = null;
                }
                try{
                    setResizable = wc.getMethod("setResizable", boolean.class);
                }catch(Throwable ignored){
                    setResizable = null;
                }
                try{
                    setAutoHeight = wc.getMethod("setAutoHeight", boolean.class);
                }catch(Throwable ignored){
                    setAutoHeight = null;
                }
                getData = wc.getMethod("getData");

                Object data = getData.invoke(window);
                if(data != null){
                    Class<?> dc = data.getClass();
                    try{
                        setEnabled = dc.getMethod("setEnabled", boolean.class);
                    }catch(Throwable ignored){
                        setEnabled = null;
                    }
                    try{
                        setPinned = dc.getMethod("setPinned", boolean.class);
                    }catch(Throwable ignored){
                        setPinned = null;
                    }
                }
                accessorsInitialized = true;
            }catch(Throwable ignored){
            }
        }
    }

    // Settings widgets extracted into `StealthPathSettingsWidgets` (same behavior; smaller main file).

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

    // Pathfinding / rendering data types extracted into `StealthPathPathTypes` (no behavior changes).
}
