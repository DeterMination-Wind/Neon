package bektools;

import arc.Core;
import arc.Events;
import arc.func.Cons;
import arc.scene.event.Touchable;
import arc.scene.ui.Button;
import arc.scene.ui.Image;
import arc.scene.ui.Label;
import arc.scene.ui.layout.Collapser;
import arc.scene.ui.layout.Table;
import arc.scene.style.Drawable;
import arc.struct.Seq;
import arc.util.CommandHandler;
import arc.util.Log;
import arc.util.Scaling;
import bektools.profiler.NeonProfilerFeature;
import bektools.ui.VscodeSettingsStyle;
import betterrtsformation.BetterRTSFormationMod;
import mdtxcompat.LegacyMindustryXGuard;
import mdtxcompat.MarkerBridge;
import mdtxcompat.OverlayUiBridge;
import advancedreplace.AdvancedReplaceMod;
import autopruner.AutoPrunerMod;
import bettermapeditor.BetterMapEditorMod;
import betterhotkey.BetterHotKeyMod;
import betterminimap.BetterMiniMapMod;
import betterlogisticsspeed.BetterLogisticsSpeedMod;
import betterpolyai.BetterPolyAiMod;
import betterprojectoroverlay.BetterProjectorOverlayMod;
import betterterraingen.v2.BetterTerrainGenV2Mod;
import betterscreenshot.features.BetterScreenShotFeature;
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
import bektools.ui.RbmStyle;
import patchviewer.PatchViewerMod;
import pinyinsearchsupport.PinyinSearchSupportMod;
import powergridminimap.PowerGridMinimapMod;
import radialbuildmenu.RadialBuildMenuMod;
import random.RandomMod;
import serverplayerdatabase.ServerPlayerDataBaseMod;
import stealthpath.StealthPathMod;
import tripwire.TripwireMod;
import tripwire.TripwireInput;
import whousesthisbuilding.WhoUsesThisBuildingMod;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Locale;

import static mindustry.Vars.ui;

public class BekToolsMod extends Mod{
    private static final String moduleFailureMessage = "@bektools.module.failed";
    private static final String feedbackDiscordUrl = "https://discord.com/channels/391020510269669376/1467903894716940522";
    private static final String feedbackChineseUrl = "https://qm.qq.com/q/wkddSGW1J8";

    private static final String modulePgmm = "pgmm";
    private static final String moduleStealthPath = "sp";
    private static final String moduleCustomMarker = "cm";
    private static final String moduleScreenshot = "bss";
    private static final String moduleRadialBuildMenu = "rbm";
    private static final String moduleBetterRtsFormation = "brf";
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
    private static final String moduleProfiler = "profiler";
    private static final String moduleUsageReporter = "usage-reporter";

    private final Map<String, Throwable> moduleFailures = new LinkedHashMap<>();

    private final PowerGridMinimapMod pgmm;
    private final StealthPathMod stealthPath;
    private final RadialBuildMenuMod radialBuildMenu;
    private final BetterRTSFormationMod betterRtsFormation;
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
            BetterHotKeyMod::new
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
        ModSupplier<BetterHotKeyMod> betterHotKeySupplier
    ){
        DataImagePackerCompat.installHooks();

        markBundled(modulePgmm, () -> PowerGridMinimapMod.bekBundled = true);
        markBundled(moduleStealthPath, () -> StealthPathMod.bekBundled = true);
        markBundled(moduleRadialBuildMenu, () -> RadialBuildMenuMod.bekBundled = true);
        markBundled(moduleBetterRtsFormation, () -> {
            BetterRTSFormationMod.bekBundled = true;
            BetterRTSFormationSettings.configure();
        });
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

        pgmm = initializeModule(modulePgmm, pgmmSupplier);
        stealthPath = initializeModule(moduleStealthPath, stealthPathSupplier);
        radialBuildMenu = initializeModule(moduleRadialBuildMenu, radialBuildMenuSupplier);
        betterRtsFormation = initializeModule(moduleBetterRtsFormation, () -> {
            BetterRTSFormationMod mod = new BetterRTSFormationMod();
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
        tripwire = initializeModule(moduleTripwire, TripwireMod::new);
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
        return OverlayUiBridge.autoDetect();
    }

    @FunctionalInterface
    protected interface ModSupplier<T>{
        T get();
    }

    private void markBundled(String moduleId, Runnable action){
        if(isModuleFailed(moduleId)) return;
        try{
            action.run();
        }catch(Throwable t){
            recordModuleFailure(moduleId, t);
        }
    }

    private <T> T initializeModule(String moduleId, ModSupplier<T> initializer){
        if(isModuleFailed(moduleId)) return null;
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
        if(isModuleFailed(moduleId)) return;
        try{
            initializer.run();
        }catch(Throwable t){
            recordModuleFailure(moduleId, t);
        }
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
        return states;
    }

    private void addSubmodState(Map<String, Boolean> states, String name, String moduleId, boolean available, boolean enabled){
        if(available && !isModuleFailed(moduleId)) states.put(name, enabled);
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

        ui.settings.addCategory("@bektools.category", Icon.settings, table -> {
            table.pref(new ModuleListSetting(buildModuleEntries(), this));
            table.pref(new FeedbackSetting());
        });
        settingsRegistered = true;
    }

    private Seq<ModuleEntry> buildModuleEntries(){
        // Master switches were verified against each module's settings builder:
        // pgmm/sp/cm/bss/rbm/brf/bpo/bls/bhk/mu/wutb/pv/pss default true;
        // ap/ctd default !bekBundled (false when bundled); bmm/bpa/profiler default false.
        Seq<ModuleEntry> entries = new Seq<>();
        entries.add(new ModuleEntry(modulePgmm, pgmm != null, Core.bundle.get("bektools.section.pgmm", "Power Grid Minimap"), Icon.power, "pgmm-enabled", true, st -> pgmm.bekBuildSettings(st)));
        entries.add(new ModuleEntry(moduleStealthPath, stealthPath != null, Core.bundle.get("bektools.section.sp", "Stealth Path"), Icon.map, "sp-enabled", true, st -> stealthPath.bekBuildSettings(st)));
        entries.add(new ModuleEntry(moduleCustomMarker, !isModuleFailed(moduleCustomMarker), Core.bundle.get("bektools.section.cm", "Custom Marker"), Icon.mapSmall, "cm-enabled", true, CustomMarkerFeature::buildSettings));
        entries.add(new ModuleEntry(moduleScreenshot, !isModuleFailed(moduleScreenshot), Core.bundle.get("bektools.section.bss", "Better ScreenShot (BSS core by Miner)"), Icon.map, "bss-enabled", true, BetterScreenShotFeature::buildSettings));
        entries.add(new ModuleEntry(moduleRadialBuildMenu, radialBuildMenu != null, Core.bundle.get("bektools.section.rbm", "Radial Build Menu"), Icon.list, "rbm-enabled", true, st -> radialBuildMenu.bekBuildSettings(st)));
        entries.add(new ModuleEntry(moduleBetterRtsFormation, betterRtsFormation != null, Core.bundle.get("bektools.section.brf", "Better RTS Formation"), Icon.commandRally, "brf-enabled", true, st -> betterRtsFormation.bekBuildSettings(st)));
        entries.add(new ModuleEntry(moduleBetterTerrainGen, betterTerrainGen != null, Core.bundle.get("bektools.section.btg", "Better Terrain Gen V2"), Icon.map, null, false, st -> {
            betterTerrainGen.bekBuildSettings(st);
            st.pref(new RbmStyle.SubHeaderSetting("@bektools.section.btg.none"));
        }));
        entries.add(new ModuleEntry(moduleAutoPruner, autoPruner != null, Core.bundle.get("bektools.section.ap", "Auto Pruner"), Icon.trash, "ap-enabled", !AutoPrunerMod.bekBundled, st -> autoPruner.bekBuildSettings(st)));
        entries.add(new ModuleEntry(moduleColorTheDucts, colorTheDucts != null, Core.bundle.get("bektools.section.ctd", "Color-the-ducts"), Icon.imageSmall, "ctd-enabled", !ColorTheDuctsMod.bekBundled, st -> colorTheDucts.bekBuildSettings(st)));
        entries.add(new ModuleEntry(moduleLogicSugar, logicSugar != null, Core.bundle.get("bektools.section.ls", "LogicSugar"), Icon.edit, null, false, st -> logicSugar.bekBuildSettings(st)));
        entries.add(new ModuleEntry(moduleBetterMiniMap, betterMiniMap != null, Core.bundle.get("bektools.section.bmm", "betterMiniMap"), Icon.map, "mmplus-enabled", false, BetterMiniMapMod::bekBuildSettings));
        entries.add(new ModuleEntry(moduleServerPlayerDatabase, serverPlayerDataBase != null, Core.bundle.get("bektools.section.spdb", "Server Player DataBase"), Icon.players, null, false, st -> serverPlayerDataBase.bekBuildSettings(st)));
        entries.add(new ModuleEntry(moduleBetterMapEditor, betterMapEditor != null, Core.bundle.get("bektools.section.bme", "Better Map Editor"), Icon.map, null, false, st -> {
            st.pref(new RbmStyle.SubHeaderSetting("@bektools.section.bme.none"));
        }));
        entries.add(new ModuleEntry(moduleBetterProjectorOverlay, betterProjectorOverlay != null, Core.bundle.get("bektools.section.bpo", "Better Projector Overlay"), Icon.power, "bpo-enabled", true, BetterProjectorOverlayMod::bekBuildSettings));
        entries.add(new ModuleEntry(moduleBetterLogisticsSpeed, betterLogisticsSpeed != null, Core.bundle.get("bektools.section.bls", "Better Logistics Speed"), Icon.rightOpen, "bls-enabled", true, BetterLogisticsSpeedMod::bekBuildSettings));
        entries.add(new ModuleEntry(moduleBetterHotKey, betterHotKey != null, Core.bundle.get("bektools.section.bhk", "Better HotKey"), Icon.settingsSmall, "bhk-enabled", true, st -> betterHotKey.bekBuildSettings(st)));
        entries.add(new ModuleEntry(moduleModUpdater, modUpdater != null, Core.bundle.get("bektools.section.mu", "Mod Updater"), Icon.refresh, "mu-enabled", true, ModUpdaterMod::bekBuildSettings));
        entries.add(new ModuleEntry(moduleWhoUsesThisBuilding, whoUsesThisBuilding != null, Core.bundle.get("bektools.section.wutb", "Who Uses This Building"), Icon.logicSmall, "wutb-enabled", true, st -> whoUsesThisBuilding.bekBuildSettings(st)));
        entries.add(new ModuleEntry(modulePatchViewer, patchViewer != null, Core.bundle.get("bektools.section.pv", "PatchViewer"), Icon.list, "patchviewer-enabled", true, st -> patchViewer.bekBuildSettings(st)));
        entries.add(new ModuleEntry(modulePinyinSearchSupport, pinyinSearchSupport != null, Core.bundle.get("bektools.section.pss", "Pinyin Search Support"), Icon.zoom, "pss-enabled", true, st -> pinyinSearchSupport.bekBuildSettings(st)));
        entries.add(new ModuleEntry(moduleForeignServerTranslator, foreignServerTranslator != null, Core.bundle.get("bektools.section.fst", "Foreign Server Translator"), Icon.chat, null, false, st -> foreignServerTranslator.bekBuildSettings(st)));
        entries.add(new ModuleEntry(moduleTripwire, tripwire != null, Core.bundle.get("bektools.section.tw", "Tripwire"), Icon.map, null, false, st -> tripwire.bekBuildSettings(st)));
        entries.add(new ModuleEntry(moduleBetterPolyAi, betterPolyAi != null, Core.bundle.get("bektools.section.bpa", "Better PolyAI"), Icon.units, "bpa-enabled", false, st -> betterPolyAi.bekBuildSettings(st)));
        entries.add(new ModuleEntry(moduleAdvancedReplace, advancedReplace != null, Core.bundle.get("bektools.section.ar", "Advanced Replace"), Icon.map, null, false, st -> advancedReplace.bekBuildSettings(st)));
        entries.add(new ModuleEntry(moduleLockAttack, lockAttack != null, Core.bundle.get("bektools.section.la", "Lock Attack"), Icon.lock, null, false, st -> lockAttack.bekBuildSettings(st)));
        entries.add(new ModuleEntry(moduleProfiler, !isModuleFailed(moduleProfiler), Core.bundle.get("bektools.section.profiler", "Performance Profiler"), Icon.chartBar, "neon-profiler-enabled", false, NeonProfilerFeature::buildSettings));
        return entries;
    }

    private static void openFeedback(){
        String locale = Core.settings.getString("locale", "default");
        if(locale == null || locale.isEmpty() || "default".equalsIgnoreCase(locale)){
            locale = Locale.getDefault().toString();
        }
        Core.app.openURI(locale.toLowerCase(Locale.ROOT).startsWith("zh") ? feedbackChineseUrl : feedbackDiscordUrl);
    }

    private static class FeedbackSetting extends SettingsMenuDialog.SettingsTable.Setting{
        private Button feedbackButton;

        FeedbackSetting(){
            super(null);
        }

        @Override
        public void add(SettingsMenuDialog.SettingsTable table){
            Core.app.post(() -> {
                if(feedbackButton != null && feedbackButton.parent == table) return;
                table.row();
                feedbackButton = table.button("@bektools.feedback", BekToolsMod::openFeedback)
                    .margin(14f).width(240f).pad(6f).get();
            });
        }
    }

    private static class ModuleEntry{
        final String moduleId;
        final boolean available;
        final String title;
        final Drawable icon;
        final String enableKey; // null when the module has no master switch
        final boolean enableDefault;
        final Cons<SettingsMenuDialog.SettingsTable> builder;

        ModuleEntry(String moduleId, boolean available, String title, Drawable icon, String enableKey, boolean enableDefault, Cons<SettingsMenuDialog.SettingsTable> builder){
            this.moduleId = moduleId;
            this.available = available;
            this.title = title;
            this.icon = icon;
            this.enableKey = enableKey;
            this.enableDefault = enableDefault;
            this.builder = builder;
        }
    }

    private static class ModuleListSetting extends SettingsMenuDialog.SettingsTable.Setting{
        private final Seq<ModuleEntry> entries;
        private final Seq<ModuleEntry> polledEntries;
        private final BekToolsMod mod;

        ModuleListSetting(Seq<ModuleEntry> entries, BekToolsMod mod){
            super("bektools-module-list");
            this.entries = entries;
            this.polledEntries = entries.select(entry -> entry.enableKey != null);
            this.mod = mod;
        }

        @Override
        public void add(SettingsMenuDialog.SettingsTable table){
            Seq<ModuleEntry> disabled = new Seq<>();
            for(ModuleEntry entry : entries){
                if(!entry.available || mod.isModuleFailed(entry.moduleId)){
                    // Failed modules stay in the main list as failure placeholders.
                    addModuleGroup(table, entry);
                }else if(entry.enableKey == null || Core.settings.getBool(entry.enableKey, entry.enableDefault)){
                    addModuleGroup(table, entry);
                }else{
                    disabled.add(entry);
                }
            }

            if(!disabled.isEmpty()){
                // Collapsed section collecting every sub-mod whose master switch is off.
                addGroup(table, Core.bundle.format("bektools.disabled.section", disabled.size), Icon.eyeOffSmall, nested -> {
                    for(ModuleEntry entry : disabled){
                        addModuleGroupPref(nested, entry);
                    }
                });
            }

            // Anchor the category table's width. Everything is laid out with growX()
            // and the wrapped header labels report a pref width of 0, so a fully
            // collapsed list would otherwise shrink the dialog to near-zero width -
            // and then jump to full width the moment a group with fixed-width rows is
            // expanded (the "sudden redraw"). This invisible fixed-width row keeps
            // the dialog at the full settings width in every expansion state.
            table.row();
            table.add().width(RbmStyle.prefWidth());
            table.row();

            attachSwitchPoller(table);
        }

        private void addModuleGroup(SettingsMenuDialog.SettingsTable table, ModuleEntry entry){
            if(!entry.available || mod.isModuleFailed(entry.moduleId)){
                addGroup(table, entry.title, entry.icon, mod::addFailurePlaceholder);
                return;
            }

            addGroup(table, entry.title, entry.icon, nested -> {
                if(mod.isModuleFailed(entry.moduleId)){
                    mod.addFailurePlaceholder(nested);
                    return;
                }
                try{
                    entry.builder.get(nested);
                }catch(Throwable t){
                    mod.recordModuleFailure(entry.moduleId, t);
                    mod.addFailurePlaceholder(nested);
                }
            });
        }

        private void addModuleGroupPref(SettingsMenuDialog.SettingsTable nested, ModuleEntry entry){
            // Register through pref(): the section body is a NestedSettingsTable whose
            // finishBuild() clears children and replays only pref'd settings, so direct
            // add() rendering would be wiped. Its suppressRebuild flag keeps pref() from
            // re-triggering a rebuild while the body is being mounted.
            if(!entry.available || mod.isModuleFailed(entry.moduleId)){
                nested.pref(new CollapsibleGroupSetting(entry.title, entry.icon, 24f, mod::addFailurePlaceholder));
                nested.pref(new RbmStyle.SpacerSetting(4f));
                return;
            }

            nested.pref(new CollapsibleGroupSetting(entry.title, entry.icon, 24f, inner -> {
                if(mod.isModuleFailed(entry.moduleId)){
                    mod.addFailurePlaceholder(inner);
                    return;
                }
                try{
                    entry.builder.get(inner);
                }catch(Throwable t){
                    mod.recordModuleFailure(entry.moduleId, t);
                    mod.addFailurePlaceholder(inner);
                }
            }));
            nested.pref(new RbmStyle.SpacerSetting(4f));
        }

        private void redrawSettings(SettingsMenuDialog.SettingsTable table){
            // MindustryX turns SettingsTable.rebuild() into a no-op while list.size is
            // unchanged (build() short-circuits on lastSize), so re-render the category
            // table by hand: clear, re-add every group (this re-hooks the poller via
            // Element.update replacement), then re-add the feedback button and the
            // vanilla reset button - the equivalent of a vanilla rebuild().
            table.clearChildren();
            add(table);
            new FeedbackSetting().add(table);
            table.button(Core.bundle.get("settings.reset", "Reset to Defaults"), () -> {
                for(SettingsMenuDialog.SettingsTable.Setting setting : table.getSettings()){
                    if(setting.name == null || setting.title == null) continue;
                    Core.settings.remove(setting.name);
                }
                redrawSettings(table);
            }).margin(14f).width(240f).pad(6f);
        }

        private void attachSwitchPoller(SettingsMenuDialog.SettingsTable table){
            if(polledEntries.isEmpty()) return;
            boolean[] cached = new boolean[polledEntries.size];
            for(int i = 0; i < polledEntries.size; i++){
                ModuleEntry entry = polledEntries.get(i);
                cached[i] = Core.settings.getBool(entry.enableKey, entry.enableDefault);
            }
            boolean[] posted = {false};
            // One poller on the category table itself, which stays in the act chain while
            // the dialog is open. Element.update replaces the previous runnable, so every
            // rebuild refreshes the cache instead of stacking listeners.
            table.update(() -> {
                for(int i = 0; i < polledEntries.size; i++){
                    ModuleEntry entry = polledEntries.get(i);
                    boolean current = Core.settings.getBool(entry.enableKey, entry.enableDefault);
                    if(current != cached[i]){
                        cached[i] = current;
                        if(!posted[0]){
                            posted[0] = true;
                            Core.app.post(() -> {
                                posted[0] = false;
                                try{
                                    redrawSettings(table);
                                }catch(Throwable t){
                                    Log.err("Neon: failed to rebuild settings after a module master switch changed.", t);
                                }
                            });
                        }
                    }
                }
            });
        }
    }

    private void addFailurePlaceholder(SettingsMenuDialog.SettingsTable table){
        table.pref(new RbmStyle.SubHeaderSetting(moduleFailureMessage));
    }

    private static void addGroup(SettingsMenuDialog.SettingsTable table, String title, Drawable icon, Cons<SettingsMenuDialog.SettingsTable> builder){
        // Render directly instead of pref(): this runs inside a rebuild, so pref()
        // would re-trigger SettingsTable.rebuild() and recurse.
        new CollapsibleGroupSetting(title, icon, 24f, builder).add(table);
        new RbmStyle.SpacerSetting(4f).add(table);
    }

    private static class CollapsibleGroupSetting extends SettingsMenuDialog.SettingsTable.Setting{
        private final String title;
        private final Drawable icon;
        private final float indent;
        private final Cons<SettingsMenuDialog.SettingsTable> builder;
        private boolean expanded;

        public CollapsibleGroupSetting(String title, Drawable icon, float indent, Cons<SettingsMenuDialog.SettingsTable> builder){
            super("bektools-group");
            this.title = title;
            this.icon = icon;
            this.indent = indent;
            this.builder = builder;
        }

        @Override
        public void add(SettingsMenuDialog.SettingsTable table){
            // A rebuild always re-creates the collapsed header, so keep the field in sync with it.
            expanded = false;
            table.row();
            table.table(wrap -> {
                wrap.center();
                Table body = new Table();
                body.center();
                Collapser collapser = new Collapser(body, true);
                collapser.setDuration(0.12f);
                final boolean[] built = {false};
                final boolean[] rebuilding = {false};
                final Runnable[] mountBody = new Runnable[1];
                final Label[] arrow = new Label[1];

                mountBody[0] = () -> {
                    if(rebuilding[0]) return;
                    rebuilding[0] = true;
                    try{
                        NestedSettingsTable nested = new NestedSettingsTable(indent, () -> {
                            if(rebuilding[0]) return;
                            mountBody[0].run();
                        });
                        builder.get(nested);
                        nested.finishBuild();
                        body.clearChildren();
                        body.add(nested).growX().center();
                        built[0] = true;
                    }finally{
                        rebuilding[0] = false;
                    }
                };

                Runnable toggle = () -> {
                    expanded = !expanded;
                    arrow[0].setText(expanded ? "v" : ">");
                    if(expanded && !built[0]) mountBody[0].run();
                    collapser.setCollapsed(!expanded, true);
                };

                Button.ButtonStyle headerStyle = new Button.ButtonStyle(
                    VscodeSettingsStyle.headerBackground(),
                    VscodeSettingsStyle.cardAltBackground(),
                    VscodeSettingsStyle.headerBackground()
                );
                headerStyle.over = VscodeSettingsStyle.cardBackground();
                Button header = new Button(headerStyle);
                header.touchable = Touchable.enabled;
                header.margin(8f);
                header.left();
                header.clicked(toggle);
                if(icon != null){
                    Image ic = header.image(icon).size(20f).padRight(8f).get();
                    ic.touchable = Touchable.disabled;
                    ic.setScaling(Scaling.fit);
                    ic.update(() -> ic.setColor(VscodeSettingsStyle.accentColor()));
                }
                Label titleLabel = header.add(title).color(VscodeSettingsStyle.accentColor()).left().growX().minWidth(0f).wrap().get();
                titleLabel.touchable = Touchable.disabled;
                arrow[0] = new Label(">");
                arrow[0].touchable = Touchable.disabled;
                arrow[0].setColor(VscodeSettingsStyle.accentColor());
                header.add(arrow[0]).width(20f).padLeft(8f).right();

                wrap.add(header).growX();
                wrap.row();
                wrap.image(mindustry.gen.Tex.whiteui).color(VscodeSettingsStyle.accentColor()).height(2f).growX().padBottom(8f);
                wrap.row();
                wrap.add(collapser).growX().center();
            }).growX().padTop(12f).padBottom(2f).center();
            table.row();
        }
    }

    private static class NestedSettingsTable extends SettingsMenuDialog.SettingsTable{
        private final Runnable rebuildAction;
        private boolean suppressRebuild = true;

        public NestedSettingsTable(float indent, Runnable rebuildAction){
            super();
            this.rebuildAction = rebuildAction;
            left();
            defaults().left();
            defaults().padLeft(indent);
        }

        // MindustryX-only: its SettingsTable.act() auto-rebuilds whenever list.size
        // changes (lastSize != list.size); finishBuild() grows our list from 0 to N,
        // which would otherwise replay the content, collapse every child group and
        // append a stray vanilla reset button one frame after expanding. Returning
        // this short-circuits that. No @Override on purpose - vanilla v159 has no
        // build() to override, and MindustryX dispatches to this at runtime.
        public Table build(){
            return this;
        }

        @Override
        public void rebuild(){
            if(suppressRebuild) return;
            rebuildAction.run();
        }

        public void finishBuild(){
            suppressRebuild = false;
            clearChildren();
            for(SettingsMenuDialog.SettingsTable.Setting setting : list){
                setting.add(this);
            }
        }
    }
}
