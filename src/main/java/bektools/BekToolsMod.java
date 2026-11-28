package bektools;

import arc.Core;
import arc.Events;
import arc.func.Cons;
import arc.scene.style.Drawable;
import arc.struct.Seq;
import arc.util.CommandHandler;
import arc.util.Log;
import bektools.profiler.NeonProfilerFeature;
import bektools.ui.NeonSettingsContext;
import bektools.ui.NeonSettingsHostSetting;
import bektools.ui.NeonSettingsModule;
import bektools.ui.NeonSettingsTab;
import betterrtsformation.BetterRTSFormationMod;
import hidewhatprocessorsshow.HideWhatProcessorsShowMod;
import mdtxcompat.LegacyMindustryXGuard;
import mdtxcompat.MarkerBridge;
import mdtxcompat.OverlayUiBridge;
import overlaycompat.OverlayCompatBridgeMod;
import advancedreplace.AdvancedReplaceMod;
import autopruner.AutoPrunerMod;
import bettermapeditor.BetterMapEditorMod;
import betterhotkey.BetterHotKeyMod;
import betterminimap.BetterMiniMapMod;
import betterlogisticsspeed.BetterLogisticsSpeedMod;
import betterlogisticsspeed.features.LongWindowFlowFeature;
import betterpolyai.BetterPolyAiMod;
import betterprojectoroverlay.BetterProjectorOverlayMod;
import betterterraingen.v2.BetterTerrainGenV2Mod;
import betterscreenshot.features.BetterScreenShotFeature;
import buildxray.BuildXrayMod;
import colortheducts.ColorTheDuctsMod;
import custommarker.features.CustomMarkerFeature;
import foreignservertranslator.ForeignServerTranslatorMod;
import foreignservertranslator.TranslatorFeature;
import logicsugar.LogicSugarMod;
import lockattack.LockAttackMod;
import mindustry.game.EventType.ClientLoadEvent;
import mindustry.gen.Icon;
import mindustry.mod.Mod;
import mindustry.ui.dialogs.SettingsMenuDialog;
import modupdater.ModUpdaterMod;
import patchviewer.PatchViewerMod;
import pinyinsearchsupport.PinyinSearchSupportMod;
import powergridminimap.PowerGridMinimapMod;
import radialbuildmenu.RadialBuildMenuMod;
import random.RandomMod;
import serverplayerdatabase.ServerPlayerDataBaseMod;
import smartplacement.SmartPlacementMod;
import stealthpath.StealthPathMod;
import patrolcancel.PatrolCancelMod;
import tripwire.TripwireMod;
import tripwire.TripwireInput;
import whousesthisbuilding.WhoUsesThisBuildingMod;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Locale;
import java.util.Set;

import static mindustry.Vars.ui;

/**
 * Neon aggregate entry for vanilla Mindustry: initializes every bundled sub-mod in
 * isolation (one failing module never takes down the rest), registers the unified
 * Neon settings category on client load, and dispatches module-scoped client
 * commands. MindustryX-injected runtimes load {@link BekToolsModX} instead, which
 * supplies X-specific bridges and ModX subclasses through the protected constructor.
 */
public class BekToolsMod extends Mod{
    private static final String feedbackDiscordUrl = "https://discord.com/channels/391020510269669376/1467903894716940522";
    private static final String feedbackChineseUrl = "https://qm.qq.com/q/wkddSGW1J8";

    private static final String modulePgmm = "pgmm";
    private static final String moduleStealthPath = "sp";
    private static final String moduleCustomMarker = "cm";
    private static final String moduleScreenshot = "bss";
    private static final String moduleRadialBuildMenu = "rbm";
    private static final String moduleBetterRtsFormation = "brf";
    private static final String moduleSmartPlacement = "smp";
    private static final String moduleHideWhatProcessorsShow = "hwps";
    private static final String modulePatrolCancel = "pc";
    private static final String moduleBetterTerrainGen = "btg";
    private static final String moduleAutoPruner = "ap";
    private static final String moduleColorTheDucts = "ctd";
    private static final String moduleLogicSugar = "ls";
    private static final String moduleBetterMiniMap = "bmm";
    private static final String moduleServerPlayerDatabase = "spdb";
    private static final String moduleBetterMapEditor = "bme";
    private static final String moduleBetterProjectorOverlay = "bpo";
    private static final String moduleBetterLogisticsSpeed = "bls";
    private static final String moduleBetterHotKey = "bhk";
    private static final String moduleModUpdater = "mu";
    private static final String moduleWhoUsesThisBuilding = "wutb";
    private static final String modulePatchViewer = "pv";
    private static final String modulePinyinSearchSupport = "pss";
    private static final String moduleForeignServerTranslator = "fst";
    private static final String moduleTripwire = "tw";
    private static final String moduleBetterPolyAi = "bpa";
    private static final String moduleAdvancedReplace = "ar";
    private static final String moduleRandom = "random";
    private static final String moduleLockAttack = "la";
    private static final String moduleBuildXray = "bx";
    private static final String moduleOverlayCompatBridge = "ocb";
    private static final String moduleProfiler = "profiler";
    private static final String moduleUsageReporter = "usage-reporter";

    /**
     * Load-time master switches. A module whose key reads false at startup is never
     * constructed, so a switched-off sub-mod is completely absent until the game is
     * restarted with the switch on. {@code neonSwitch} marks modules that have no
     * switch of their own (or none at all): Neon renders that master-switch row in the
     * module group. Only the usage reporter is gate-free infrastructure and always loads;
     * {@code ocb} has a switch like every other sub-mod; while it is off, vanilla clients
     * get no overlay at all (see {@link #vanillaOverlayUi()}).
     */
    private static final class ModuleGate{
        final String key;
        final boolean def;
        final boolean neonSwitch;

        ModuleGate(String key, boolean def, boolean neonSwitch){
            this.key = key;
            this.def = def;
            this.neonSwitch = neonSwitch;
        }
    }

    private static final Map<String, ModuleGate> moduleGates = new LinkedHashMap<>();

    static{
        moduleGates.put(modulePgmm, new ModuleGate("pgmm-enabled", true, false));
        // Known defects: StealthPath is default-off in the aggregate build (docs/user/battlefield-awareness.md).
        moduleGates.put(moduleStealthPath, new ModuleGate("sp-enabled", false, false));
        moduleGates.put(moduleCustomMarker, new ModuleGate("cm-enabled", true, false));
        moduleGates.put(moduleScreenshot, new ModuleGate("bss-enabled", true, false));
        moduleGates.put(moduleRadialBuildMenu, new ModuleGate("rbm-enabled", true, false));
        moduleGates.put(moduleBetterRtsFormation, new ModuleGate("brf-enabled", true, false));
        moduleGates.put(moduleSmartPlacement, new ModuleGate("smp-enabled", true, true));
        moduleGates.put(moduleHideWhatProcessorsShow, new ModuleGate("hwps-enabled", true, true));
        moduleGates.put(modulePatrolCancel, new ModuleGate("pc-enabled", true, true));
        moduleGates.put(moduleBetterTerrainGen, new ModuleGate("btg-enabled", true, true));
        moduleGates.put(moduleAutoPruner, new ModuleGate("ap-enabled", false, false));
        moduleGates.put(moduleColorTheDucts, new ModuleGate("ctd-enabled", false, false));
        moduleGates.put(moduleLogicSugar, new ModuleGate("ls-enabled", true, true));
        moduleGates.put(moduleBetterMiniMap, new ModuleGate("mmplus-enabled", false, false));
        moduleGates.put(moduleServerPlayerDatabase, new ModuleGate("spdb-enabled", true, true));
        moduleGates.put(moduleBetterMapEditor, new ModuleGate("bme-enabled", true, true));
        moduleGates.put(moduleBetterProjectorOverlay, new ModuleGate("bpo-enabled", true, false));
        moduleGates.put(moduleBetterLogisticsSpeed, new ModuleGate("bls-enabled", true, false));
        moduleGates.put(moduleBetterHotKey, new ModuleGate("bhk-enabled", true, false));
        moduleGates.put(moduleModUpdater, new ModuleGate("mu-enabled", true, false));
        moduleGates.put(moduleWhoUsesThisBuilding, new ModuleGate("wutb-enabled", true, false));
        moduleGates.put(modulePatchViewer, new ModuleGate("patchviewer-enabled", true, false));
        moduleGates.put(modulePinyinSearchSupport, new ModuleGate("pss-enabled", true, false));
        moduleGates.put(moduleForeignServerTranslator, new ModuleGate("fst-enabled", true, true));
        moduleGates.put(moduleTripwire, new ModuleGate("tw-enabled", true, true));
        moduleGates.put(moduleBetterPolyAi, new ModuleGate("bpa-enabled", false, false));
        moduleGates.put(moduleAdvancedReplace, new ModuleGate("ar-enabled", true, true));
        moduleGates.put(moduleRandom, new ModuleGate("random-enabled", true, true));
        moduleGates.put(moduleLockAttack, new ModuleGate("lockattack-enabled", true, true));
        moduleGates.put(moduleBuildXray, new ModuleGate("bx-enabled", true, false));
        moduleGates.put(moduleOverlayCompatBridge, new ModuleGate("ocb-enabled", true, true));
        moduleGates.put(moduleProfiler, new ModuleGate("neon-profiler-enabled", false, false));
    }

    private static boolean moduleEnabled(String moduleId){
        ModuleGate gate = moduleGates.get(moduleId);
        // No gate = infrastructure that must always load. A missing settings backend
        // (unusual hosts/tests) falls back to the historical always-load behavior.
        if(gate == null || Core.settings == null) return true;
        return Core.settings.getBool(gate.key, gate.def);
    }

    private final Map<String, Throwable> moduleFailures = new LinkedHashMap<>();
    private final Set<String> gatedModules = new LinkedHashSet<>();

    private final PowerGridMinimapMod pgmm;
    private final StealthPathMod stealthPath;
    private final RadialBuildMenuMod radialBuildMenu;
    private final BetterRTSFormationMod betterRtsFormation;
    private final SmartPlacementMod smartPlacement;
    private final HideWhatProcessorsShowMod hideWhatProcessorsShow;
    private final PatrolCancelMod patrolCancel;
    private final BetterTerrainGenV2Mod betterTerrainGen;
    private final AutoPrunerMod autoPruner;
    private final ColorTheDuctsMod colorTheDucts;
    private final LogicSugarMod logicSugar;
    private final BetterMiniMapMod betterMiniMap;
    private final ServerPlayerDataBaseMod serverPlayerDataBase;
    private final BetterMapEditorMod betterMapEditor;
    private final BetterProjectorOverlayMod betterProjectorOverlay;
    private final BetterLogisticsSpeedMod betterLogisticsSpeed;
    private final BetterHotKeyMod betterHotKey;
    private final ModUpdaterMod modUpdater;
    private final WhoUsesThisBuildingMod whoUsesThisBuilding;
    private final PatchViewerMod patchViewer;
    private final PinyinSearchSupportMod pinyinSearchSupport;
    private final ForeignServerTranslatorMod foreignServerTranslator;
    private final TripwireMod tripwire;
    private final BetterPolyAiMod betterPolyAi;
    private final AdvancedReplaceMod advancedReplace;
    private final RandomMod random;
    private final LockAttackMod lockAttack;
    private final BuildXrayMod buildXray;
    private final OverlayCompatBridgeMod overlayCompatBridge;
    private final PostHogUsageReporter postHogUsageReporter;
    private boolean settingsRegistered;

    public BekToolsMod(){
        this(
            vanillaOverlayUi(),
            MarkerBridge.UNSUPPORTED,
            PowerGridMinimapMod::new,
            StealthPathMod::new,
            RadialBuildMenuMod::new,
            ServerPlayerDataBaseMod::new,
            BetterProjectorOverlayMod::new,
            BetterHotKeyMod::new,
            null
        );
    }

    protected BekToolsMod(
        OverlayUiBridge overlayUi,
        MarkerBridge markerBridge,
        ModSupplier<PowerGridMinimapMod> pgmmSupplier,
        ModSupplier<StealthPathMod> stealthPathSupplier,
        ModSupplier<RadialBuildMenuMod> radialBuildMenuSupplier,
        ModSupplier<ServerPlayerDataBaseMod> serverPlayerDataBaseSupplier,
        ModSupplier<BetterProjectorOverlayMod> betterProjectorOverlaySupplier,
        ModSupplier<BetterHotKeyMod> betterHotKeySupplier,
        ModSupplier<TripwireMod> tripwireSupplier
    ){
        Log.info("Neon: runtime = " + (LegacyMindustryXGuard.isMindustryXRuntime() ? "MindustryX" : "vanilla") + " (core marker mindustryX.VarsX).");
        DataImagePackerCompat.installHooks();

        markBundled(modulePgmm, () -> PowerGridMinimapMod.bekBundled = true);
        markBundled(moduleStealthPath, () -> StealthPathMod.bekBundled = true);
        markBundled(moduleRadialBuildMenu, () -> RadialBuildMenuMod.bekBundled = true);
        markBundled(moduleBetterRtsFormation, () -> {
            BetterRTSFormationMod.bekBundled = true;
            BetterRTSFormationSettings.configure();
        });
        markBundled(moduleSmartPlacement, () -> SmartPlacementMod.bekBundled = true);
        markBundled(moduleHideWhatProcessorsShow, () -> HideWhatProcessorsShowMod.bekBundled = true);
        markBundled(modulePatrolCancel, () -> PatrolCancelMod.bekBundled = true);
        markBundled(moduleBetterTerrainGen, () -> BetterTerrainGenV2Mod.bekBundled = true);
        markBundled(moduleAutoPruner, () -> AutoPrunerMod.bekBundled = true);
        markBundled(moduleColorTheDucts, () -> ColorTheDuctsMod.bekBundled = true);
        markBundled(moduleLogicSugar, () -> LogicSugarMod.bekBundled = true);
        markBundled(moduleBetterMiniMap, () -> BetterMiniMapMod.bekBundled = true);
        markBundled(moduleBetterMapEditor, () -> BetterMapEditorMod.bekBundled = true);
        markBundled(moduleServerPlayerDatabase, () -> ServerPlayerDataBaseMod.bekBundled = true);
        markBundled(moduleBetterProjectorOverlay, () -> BetterProjectorOverlayMod.bekBundled = true);
        markBundled(moduleBetterLogisticsSpeed, () -> BetterLogisticsSpeedMod.bekBundled = true);
        markBundled(moduleBetterHotKey, () -> BetterHotKeyMod.bekBundled = true);
        markBundled(moduleModUpdater, () -> ModUpdaterMod.bekBundled = true);
        markBundled(moduleWhoUsesThisBuilding, () -> WhoUsesThisBuildingMod.bekBundled = true);
        markBundled(modulePatchViewer, () -> PatchViewerMod.bekBundled = true);
        markBundled(modulePinyinSearchSupport, () -> PinyinSearchSupportMod.bekBundled = true);
        markBundled(moduleForeignServerTranslator, () -> ForeignServerTranslatorMod.bekBundled = true);
        markBundled(moduleTripwire, () -> TripwireMod.bekBundled = true);
        markBundled(moduleBetterPolyAi, () -> BetterPolyAiMod.bekBundled = true);
        markBundled(moduleAdvancedReplace, () -> AdvancedReplaceMod.bekBundled = true);
        markBundled(moduleRandom, () -> RandomMod.bekBundled = true);
        markBundled(moduleLockAttack, () -> LockAttackMod.bekBundled = true);
        markBundled(moduleBuildXray, () -> BuildXrayMod.bekBundled = true);

        // Bundled OverlayUI copy for vanilla clients; it carries MindustryX's FQCN and stays
        // dormant when a real MindustryX runtime is present. Switchable like the other sub-mods:
        // while the switch is off vanilla clients get no overlay at all (see vanillaOverlayUi()),
        // and a failed module falls back to the frozen embedded copy. Tell the bridge probe which
        // of those happened before its first use locks the delegate.
        overlayCompatBridge = initializeModule(moduleOverlayCompatBridge, OverlayCompatBridgeMod::new);
        OverlayUiBridge.setBundledOverlayActive(overlayCompatBridge != null);
        pgmm = initializeModule(modulePgmm, pgmmSupplier);
        stealthPath = initializeModule(moduleStealthPath, stealthPathSupplier);
        radialBuildMenu = initializeModule(moduleRadialBuildMenu, radialBuildMenuSupplier);
        betterRtsFormation = initializeModule(moduleBetterRtsFormation, () -> {
            BetterRTSFormationMod mod = new BetterRTSFormationMod();
            mod.init();
            return mod;
        });
        smartPlacement = initializeModule(moduleSmartPlacement, () -> {
            SmartPlacementMod mod = new SmartPlacementMod();
            mod.init();
            return mod;
        });
        hideWhatProcessorsShow = initializeModule(moduleHideWhatProcessorsShow, () -> {
            HideWhatProcessorsShowMod mod = new HideWhatProcessorsShowMod();
            mod.init();
            return mod;
        });
        patrolCancel = initializeModule(modulePatrolCancel, () -> {
            PatrolCancelMod mod = new PatrolCancelMod();
            mod.init();
            return mod;
        });
        betterTerrainGen = initializeModule(moduleBetterTerrainGen, () -> {
            BetterTerrainGenV2Mod mod = new BetterTerrainGenV2Mod();
            mod.init();
            return mod;
        });
        autoPruner = initializeModule(moduleAutoPruner, () -> {
            AutoPrunerMod mod = new AutoPrunerMod();
            mod.init();
            return mod;
        });
        colorTheDucts = initializeModule(moduleColorTheDucts, ColorTheDuctsMod::new);
        logicSugar = initializeModule(moduleLogicSugar, () -> {
            LogicSugarMod mod = new LogicSugarMod();
            mod.init();
            return mod;
        });
        betterMiniMap = initializeModule(moduleBetterMiniMap, () -> {
            BetterMiniMapMod mod = new BetterMiniMapMod();
            mod.init();
            return mod;
        });
        serverPlayerDataBase = initializeModule(moduleServerPlayerDatabase, serverPlayerDataBaseSupplier);
        betterMapEditor = initializeModule(moduleBetterMapEditor, () -> {
            BetterMapEditorMod mod = new BetterMapEditorMod();
            mod.init();
            return mod;
        });
        betterProjectorOverlay = initializeModule(moduleBetterProjectorOverlay, () -> {
            BetterProjectorOverlayMod mod = betterProjectorOverlaySupplier.get();
            mod.init();
            return mod;
        });
        betterLogisticsSpeed = initializeModule(moduleBetterLogisticsSpeed, () -> {
            LongWindowFlowFeature.configureOverlayUi(overlayUi);
            BetterLogisticsSpeedMod mod = new BetterLogisticsSpeedMod();
            mod.init();
            return mod;
        });
        betterHotKey = initializeModule(moduleBetterHotKey, () -> {
            BetterHotKeyMod mod = betterHotKeySupplier.get();
            mod.init();
            return mod;
        });
        modUpdater = initializeModule(moduleModUpdater, () -> {
            ModUpdaterMod mod = new ModUpdaterMod();
            mod.init();
            return mod;
        });
        whoUsesThisBuilding = initializeModule(moduleWhoUsesThisBuilding, () -> {
            WhoUsesThisBuildingMod mod = new WhoUsesThisBuildingMod();
            mod.init();
            return mod;
        });
        patchViewer = initializeModule(modulePatchViewer, () -> {
            PatchViewerMod mod = new PatchViewerMod();
            mod.init();
            return mod;
        });
        pinyinSearchSupport = initializeModule(modulePinyinSearchSupport, PinyinSearchSupportMod::new);
        foreignServerTranslator = initializeModule(moduleForeignServerTranslator, () -> {
            ForeignServerTranslatorMod mod = new ForeignServerTranslatorMod();
            mod.init();
            return mod;
        });
        tripwire = initializeModule(moduleTripwire, tripwireSupplier != null ? tripwireSupplier : () -> new TripwireMod(overlayUi));
        betterPolyAi = initializeModule(moduleBetterPolyAi, () -> {
            BetterPolyAiMod mod = new BetterPolyAiMod();
            mod.init();
            return mod;
        });
        advancedReplace = initializeModule(moduleAdvancedReplace, () -> {
            AdvancedReplaceMod mod = new AdvancedReplaceMod();
            mod.init();
            return mod;
        });
        random = initializeModule(moduleRandom, () -> {
            RandomMod mod = new RandomMod();
            mod.init();
            return mod;
        });
        lockAttack = initializeModule(moduleLockAttack, () -> {
            LockAttackMod mod = new LockAttackMod();
            mod.init();
            return mod;
        });
        buildXray = initializeModule(moduleBuildXray, () -> {
            BuildXrayMod mod = new BuildXrayMod();
            mod.init();
            return mod;
        });
        postHogUsageReporter = initializeModule(moduleUsageReporter, () -> new PostHogUsageReporter(getClass(), this::snapshotSubmodStates));

        // Global UI/event features are initialized only after every bundled module has
        // been isolated, so a later module failure cannot leave a profiler ghost window.
        initializeFeature(moduleCustomMarker, () -> {
            CustomMarkerFeature.configureCompat(overlayUi, markerBridge);
            CustomMarkerFeature.init();
        });
        initializeFeature(moduleScreenshot, () -> {
            BetterScreenShotFeature.configureOverlayUi(overlayUi);
            BetterScreenShotFeature.init();
        });
        initializeFeature(moduleProfiler, () -> {
            NeonProfilerFeature.configureOverlayUi(overlayUi);
            NeonProfilerFeature.init();
        });

        try{
            Events.on(ClientLoadEvent.class, e -> {
                if(postHogUsageReporter != null){
                    try{
                        postHogUsageReporter.onClientLoad();
                    }catch(Throwable t){
                        recordModuleFailure(moduleUsageReporter, t);
                    }
                }
                try{
                    registerSettings();
                }catch(Throwable t){
                    Log.err("Neon: failed to register unified settings.", t);
                }
            });
        }catch(Throwable t){
            Log.err("Neon: failed to register the client-load entry point; bundled modules remain isolated.", t);
        }
    }

    private static OverlayUiBridge vanillaOverlayUi(){
        // An injected MindustryX runtime must select mainX. If an older loader enters
        // the vanilla main class, keep the original upgrade/backtrack error instead of
        // swallowing it as an individual bundled-module failure.
        LegacyMindustryXGuard.rejectLegacyMindustryX("Neon");
        // With the ocb switch off, vanilla clients get no overlay at all: no gear button, no Z
        // key, and every registerWindow is a no-op. The frozen embedded copy is reserved for
        // "ocb failed to initialize", not for "ocb switched off".
        if(!moduleEnabled(moduleOverlayCompatBridge)){
            Log.info("Neon: overlay disabled by ocb-enabled=false; vanilla clients get no overlay this session.");
            return OverlayUiBridge.UNSUPPORTED;
        }
        return OverlayUiBridge.autoDetect();
    }

    @FunctionalInterface
    protected interface ModSupplier<T>{
        T get();
    }

    private void markBundled(String moduleId, Runnable action){
        if(isModuleGatedOut(moduleId) || isModuleFailed(moduleId)) return;
        try{
            action.run();
        }catch(Throwable t){
            recordModuleFailure(moduleId, t);
        }
    }

    private <T> T initializeModule(String moduleId, ModSupplier<T> initializer){
        if(isModuleGatedOut(moduleId) || isModuleFailed(moduleId)) return null;
        try{
            T module = initializer.get();
            if(module == null){
                throw new IllegalStateException("module initializer returned null");
            }
            return module;
        }catch(Throwable t){
            recordModuleFailure(moduleId, t);
            return null;
        }
    }

    private void initializeFeature(String moduleId, Runnable initializer){
        if(isModuleGatedOut(moduleId) || isModuleFailed(moduleId)) return;
        try{
            initializer.run();
        }catch(Throwable t){
            recordModuleFailure(moduleId, t);
        }
    }

    /**
     * Load-time gate. {@code true} when the module's master switch is off; the id is
     * remembered so the settings page and the telemetry snapshot can tell "switched
     * off" apart from "failed" and from "loaded".
     */
    private boolean isModuleGatedOut(String moduleId){
        if(moduleEnabled(moduleId)) return false;
        gatedModules.add(moduleId);
        return true;
    }

    private boolean isModuleGated(String moduleId){
        return gatedModules.contains(moduleId);
    }

    private void recordModuleFailure(String moduleId, Throwable failure){
        if(moduleFailures.containsKey(moduleId)) return;
        moduleFailures.put(moduleId, failure);
        Log.err("Neon: bundled module '" + moduleId + "' failed; continuing without it.", failure);
    }

    private boolean isModuleFailed(String moduleId){
        return moduleFailures.containsKey(moduleId);
    }

    private Map<String, Boolean> snapshotSubmodStates(){
        Map<String, Boolean> states = new LinkedHashMap<>();
        addSubmodState(states, "电网小地图", modulePgmm, pgmm != null, true);
        addSubmodState(states, "偷袭小道", moduleStealthPath, stealthPath != null, false);
        addSubmodState(states, "圆盘快捷建造", moduleRadialBuildMenu, radialBuildMenu != null, false);
        addSubmodState(states, "RTS 编队增强", moduleBetterRtsFormation, betterRtsFormation != null, true);
        addSubmodState(states, "更自然的地形生成 V2", moduleBetterTerrainGen, betterTerrainGen != null, betterTerrainGen != null && BetterTerrainGenV2Mod.hasBeenUsed());
        addSubmodState(states, "智能拆除", moduleAutoPruner, autoPruner != null, false);
        addSubmodState(states, "导管染色", moduleColorTheDucts, colorTheDucts != null, false);
        addSubmodState(states, "LogicSugar", moduleLogicSugar, logicSugar != null, false);
        addSubmodState(states, "增强小地图", moduleBetterMiniMap, betterMiniMap != null, true);
        addSubmodState(states, "物流速率增强", moduleBetterLogisticsSpeed, betterLogisticsSpeed != null, true);
        addSubmodState(states, "快捷键增强", moduleBetterHotKey, betterHotKey != null, false);
        addSubmodState(states, "模组更新器", moduleModUpdater, modUpdater != null, false);
        addSubmodState(states, "玩家数据库", moduleServerPlayerDatabase, serverPlayerDataBase != null, false);
        addSubmodState(states, "地图编辑增强", moduleBetterMapEditor, betterMapEditor != null, true);
        addSubmodState(states, "投影覆盖增强", moduleBetterProjectorOverlay, betterProjectorOverlay != null, true);
        addSubmodState(states, "谁在用这个建筑", moduleWhoUsesThisBuilding, whoUsesThisBuilding != null, false);
        addSubmodState(states, "补丁查看器", modulePatchViewer, patchViewer != null, true);
        addSubmodState(states, "拼音搜索支持", modulePinyinSearchSupport, pinyinSearchSupport != null, false);
        addSubmodState(states, "外语服务器翻译", moduleForeignServerTranslator, foreignServerTranslator != null, foreignServerTranslator != null && TranslatorFeature.hasMarkedForeignServer());
        addSubmodState(states, "地理围栏报警", moduleTripwire, tripwire != null, tripwire != null && TripwireInput.hasConfiguredControlKey());
        addSubmodState(states, "更好的 PolyAI", moduleBetterPolyAi, betterPolyAi != null, false);
        addSubmodState(states, "高级替换", moduleAdvancedReplace, advancedReplace != null, true);
        addSubmodState(states, "随机化", moduleRandom, random != null, false);
        addSubmodState(states, "锁定攻击", moduleLockAttack, lockAttack != null, false);
        addSubmodState(states, "建造透视", moduleBuildXray, buildXray != null, buildXray != null && Core.settings.getBool("bx-enabled", true));
        addSubmodState(states, "OverlayUI 兼容层", moduleOverlayCompatBridge, overlayCompatBridge != null, false);
        return states;
    }

    private void addSubmodState(Map<String, Boolean> states, String name, String moduleId, boolean available, boolean enabled){
        if(isModuleFailed(moduleId)) return;
        if(available){
            states.put(name, enabled);
        }else if(isModuleGated(moduleId)){
            // Loaded out by its master switch: report it as disabled instead of dropping it.
            states.put(name, false);
        }
    }

    private void registerModuleCommands(String moduleId, boolean available, Runnable registration){
        if(!available || isModuleFailed(moduleId)) return;
        try{
            registration.run();
        }catch(Throwable t){
            recordModuleFailure(moduleId, t);
        }
    }

    @Override
    public void registerClientCommands(CommandHandler handler){
        registerModuleCommands(moduleProfiler, !isModuleFailed(moduleProfiler), () -> NeonProfilerFeature.registerClientCommands(handler));
        registerModuleCommands(modulePgmm, pgmm != null, () -> pgmm.registerClientCommands(handler));
        registerModuleCommands(moduleStealthPath, stealthPath != null, () -> stealthPath.registerClientCommands(handler));
        registerModuleCommands(moduleRadialBuildMenu, radialBuildMenu != null, () -> radialBuildMenu.registerClientCommands(handler));
        registerModuleCommands(moduleServerPlayerDatabase, serverPlayerDataBase != null, () -> serverPlayerDataBase.registerClientCommands(handler));
    }

    private void registerSettings(){
        if(ui == null || ui.settings == null) return;
        if(settingsRegistered) return;

        NeonSettingsContext settingsContext = new NeonSettingsContext(){
            @Override
            public boolean isModuleFailed(String moduleId){
                return BekToolsMod.this.isModuleFailed(moduleId);
            }

            @Override
            public Throwable getModuleFailure(String moduleId){
                return moduleFailures.get(moduleId);
            }

            @Override
            public void recordModuleFailure(String moduleId, Throwable failure){
                BekToolsMod.this.recordModuleFailure(moduleId, failure);
            }

            @Override
            public void openFeedback(){
                BekToolsMod.openFeedback();
            }
        };

        // Keep outer SettingsTable list.size constant at 1 so MindustryX's SettingsTable.act()
        // never triggers spurious rebuilds or stray vanilla reset buttons.
        ui.settings.addCategory("@bektools.category", Icon.settings, table -> {
            table.pref(new NeonSettingsHostSetting(buildModuleEntries(), settingsContext));
        });
        settingsRegistered = true;
    }

    private NeonSettingsModule moduleEntry(
        String moduleId,
        NeonSettingsTab tab,
        boolean available,
        String titleKey,
        String titleFallback,
        Drawable icon,
        boolean hasOwnSettings,
        String emptyPlaceholderKey,
        Cons<SettingsMenuDialog.SettingsTable> builder,
        Cons<Boolean> onSwitchChanged
    ){
        ModuleGate gate = moduleGates.get(moduleId);
        return new NeonSettingsModule(
            moduleId,
            tab,
            available,
            Core.bundle.get(titleKey, titleFallback),
            icon,
            gate == null ? null : gate.key,
            gate != null && gate.def,
            gate != null && gate.neonSwitch,
            hasOwnSettings,
            emptyPlaceholderKey,
            builder,
            onSwitchChanged
        );
    }

    private Seq<NeonSettingsModule> buildModuleEntries(){
        Seq<NeonSettingsModule> entries = new Seq<>();

        // Build & Logistics (10)
        entries.add(moduleEntry(moduleRadialBuildMenu, NeonSettingsTab.BUILD_LOGISTICS, radialBuildMenu != null, "bektools.section.rbm", "Radial Build Menu", Icon.list, true, null, st -> radialBuildMenu.bekBuildSettings(st), null));
        entries.add(moduleEntry(moduleSmartPlacement, NeonSettingsTab.BUILD_LOGISTICS, smartPlacement != null, "bektools.section.smp", "Smart Placement", Icon.rightOpen, true, null, st -> {
            if(smartPlacement != null) smartPlacement.bekBuildSettings(st);
        }, null));
        entries.add(moduleEntry(moduleAutoPruner, NeonSettingsTab.BUILD_LOGISTICS, autoPruner != null, "bektools.section.ap", "Auto Pruner", Icon.trash, true, null, st -> autoPruner.bekBuildSettings(st), null));
        entries.add(moduleEntry(moduleColorTheDucts, NeonSettingsTab.BUILD_LOGISTICS, colorTheDucts != null, "bektools.section.ctd", "Color-the-ducts", Icon.imageSmall, true, null, st -> colorTheDucts.bekBuildSettings(st), null));
        entries.add(moduleEntry(moduleBetterProjectorOverlay, NeonSettingsTab.BUILD_LOGISTICS, betterProjectorOverlay != null, "bektools.section.bpo", "Better Projector Overlay", Icon.power, true, null, BetterProjectorOverlayMod::bekBuildSettings, null));
        entries.add(moduleEntry(moduleBetterLogisticsSpeed, NeonSettingsTab.BUILD_LOGISTICS, betterLogisticsSpeed != null, "bektools.section.bls", "Better Logistics Speed", Icon.rightOpen, true, null, BetterLogisticsSpeedMod::bekBuildSettings, null));
        entries.add(moduleEntry(moduleBetterPolyAi, NeonSettingsTab.BUILD_LOGISTICS, betterPolyAi != null, "bektools.section.bpa", "Better PolyAI", Icon.units, true, null, st -> betterPolyAi.bekBuildSettings(st), null));
        entries.add(moduleEntry(moduleAdvancedReplace, NeonSettingsTab.BUILD_LOGISTICS, advancedReplace != null, "bektools.section.ar", "Advanced Replace", Icon.map, true, null, st -> advancedReplace.bekBuildSettings(st), null));
        entries.add(moduleEntry(moduleBuildXray, NeonSettingsTab.BUILD_LOGISTICS, buildXray != null, "bektools.section.bx", "Build X-ray (by Miner)", Icon.eyeSmall, true, null, st -> buildXray.bekBuildSettings(st), null));
        entries.add(moduleEntry(moduleWhoUsesThisBuilding, NeonSettingsTab.BUILD_LOGISTICS, whoUsesThisBuilding != null, "bektools.section.wutb", "Who Uses This Building", Icon.logicSmall, true, null, st -> whoUsesThisBuilding.bekBuildSettings(st), null));

        // Combat & Vision (8)
        entries.add(moduleEntry(modulePgmm, NeonSettingsTab.COMBAT_HUD, pgmm != null, "bektools.section.pgmm", "Power Grid Minimap", Icon.power, true, null, st -> pgmm.bekBuildSettings(st), null));
        entries.add(moduleEntry(moduleStealthPath, NeonSettingsTab.COMBAT_HUD, stealthPath != null, "bektools.section.sp", "Stealth Path", Icon.map, true, null, st -> stealthPath.bekBuildSettings(st), null));
        entries.add(moduleEntry(moduleBetterMiniMap, NeonSettingsTab.COMBAT_HUD, betterMiniMap != null, "bektools.section.bmm", "betterMiniMap", Icon.map, true, null, BetterMiniMapMod::bekBuildSettings, null));
        entries.add(moduleEntry(moduleBetterRtsFormation, NeonSettingsTab.COMBAT_HUD, betterRtsFormation != null, "bektools.section.brf", "Better RTS Formation", Icon.commandRally, true, null, st -> betterRtsFormation.bekBuildSettings(st), null));
        entries.add(moduleEntry(modulePatrolCancel, NeonSettingsTab.COMBAT_HUD, patrolCancel != null, "bektools.section.pc", "Patrol Cancel", Icon.commandRally, false, "@bektools.section.pc.none", null, null));
        entries.add(moduleEntry(moduleLockAttack, NeonSettingsTab.COMBAT_HUD, lockAttack != null, "bektools.section.la", "Lock Attack", Icon.lock, true, null, st -> lockAttack.bekBuildSettings(st), null));
        entries.add(moduleEntry(moduleTripwire, NeonSettingsTab.COMBAT_HUD, tripwire != null, "bektools.section.tw", "Tripwire", Icon.map, true, null, st -> tripwire.bekBuildSettings(st), null));
        entries.add(moduleEntry(moduleCustomMarker, NeonSettingsTab.COMBAT_HUD, !isModuleFailed(moduleCustomMarker) && !isModuleGated(moduleCustomMarker), "bektools.section.cm", "Custom Marker", Icon.mapSmall, true, null, CustomMarkerFeature::buildSettings, null));

        // Logic & Editor (6)
        entries.add(moduleEntry(moduleLogicSugar, NeonSettingsTab.LOGIC_EDITOR, logicSugar != null, "bektools.section.ls", "LogicSugar", Icon.edit, true, null, st -> logicSugar.bekBuildSettings(st), null));
        entries.add(moduleEntry(moduleHideWhatProcessorsShow, NeonSettingsTab.LOGIC_EDITOR, hideWhatProcessorsShow != null, "bektools.section.hwps", "Hide What Processors Show", Icon.logic, false, "@bektools.section.hwps.none", null, null));
        entries.add(moduleEntry(moduleBetterTerrainGen, NeonSettingsTab.LOGIC_EDITOR, betterTerrainGen != null, "bektools.section.btg", "Better Terrain Gen V2", Icon.map, true, "@bektools.section.btg.none", st -> betterTerrainGen.bekBuildSettings(st), null));
        entries.add(moduleEntry(moduleBetterMapEditor, NeonSettingsTab.LOGIC_EDITOR, betterMapEditor != null, "bektools.section.bme", "Better Map Editor", Icon.map, false, "@bektools.section.bme.none", null, null));
        entries.add(moduleEntry(modulePatchViewer, NeonSettingsTab.LOGIC_EDITOR, patchViewer != null, "bektools.section.pv", "PatchViewer", Icon.list, true, null, st -> patchViewer.bekBuildSettings(st), null));
        entries.add(moduleEntry(moduleRandom, NeonSettingsTab.LOGIC_EDITOR, random != null, "bektools.section.random", "Random", Icon.effect, true, "@bektools.section.random.none", st -> random.bekBuildSettings(st), null));

        // Tools & Core (8)
        entries.add(moduleEntry(moduleBetterHotKey, NeonSettingsTab.TOOLS_CORE, betterHotKey != null, "bektools.section.bhk", "Better HotKey", Icon.settingsSmall, true, null, st -> betterHotKey.bekBuildSettings(st), null));
        entries.add(moduleEntry(modulePinyinSearchSupport, NeonSettingsTab.TOOLS_CORE, pinyinSearchSupport != null, "bektools.section.pss", "Pinyin Search Support", Icon.zoom, true, null, st -> pinyinSearchSupport.bekBuildSettings(st), null));
        entries.add(moduleEntry(moduleForeignServerTranslator, NeonSettingsTab.TOOLS_CORE, foreignServerTranslator != null, "bektools.section.fst", "Foreign Server Translator", Icon.chat, true, null, st -> foreignServerTranslator.bekBuildSettings(st), null));
        entries.add(moduleEntry(moduleServerPlayerDatabase, NeonSettingsTab.TOOLS_CORE, serverPlayerDataBase != null, "bektools.section.spdb", "Server Player DataBase", Icon.players, true, null, st -> serverPlayerDataBase.bekBuildSettings(st), null));
        entries.add(moduleEntry(moduleScreenshot, NeonSettingsTab.TOOLS_CORE, !isModuleFailed(moduleScreenshot) && !isModuleGated(moduleScreenshot), "bektools.section.bss", "Better ScreenShot (BSS core by Miner)", Icon.map, true, null, BetterScreenShotFeature::buildSettings, null));
        entries.add(moduleEntry(moduleModUpdater, NeonSettingsTab.TOOLS_CORE, modUpdater != null, "bektools.section.mu", "Mod Updater", Icon.refresh, true, null, ModUpdaterMod::bekBuildSettings, null));
        entries.add(moduleEntry(moduleOverlayCompatBridge, NeonSettingsTab.TOOLS_CORE, overlayCompatBridge != null, "bektools.section.ocb", "OverlayCompatBridge", Icon.layers, false, "@bektools.section.ocb.none", null, null));
        entries.add(moduleEntry(moduleProfiler, NeonSettingsTab.TOOLS_CORE, !isModuleFailed(moduleProfiler) && !isModuleGated(moduleProfiler), "bektools.section.profiler", "Performance Profiler", Icon.chartBar, true, null, NeonProfilerFeature::buildSettings, NeonProfilerFeature::setEnabled));

        return entries;
    }

    private static void openFeedback(){
        String locale = Core.settings.getString("locale", "default");
        if(locale == null || locale.isEmpty() || "default".equalsIgnoreCase(locale)){
            locale = Locale.getDefault().toString();
        }
        Core.app.openURI(locale.toLowerCase(Locale.ROOT).startsWith("zh") ? feedbackChineseUrl : feedbackDiscordUrl);
    }
}
