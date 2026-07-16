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
import arc.util.CommandHandler;
import arc.util.Log;
import arc.util.Scaling;
import bektools.profiler.NeonProfilerFeature;
import bektools.ui.VscodeSettingsStyle;
import mdtxcompat.LegacyMindustryXGuard;
import mdtxcompat.MarkerBridge;
import mdtxcompat.OverlayUiBridge;
import advancedreplace.AdvancedReplaceMod;
import bettermapeditor.BetterMapEditorMod;
import betterhotkey.BetterHotKeyMod;
import betterminimap.BetterMiniMapMod;
import betterlogisticsspeed.BetterLogisticsSpeedMod;
import betterpolyai.BetterPolyAiMod;
import betterprojectoroverlay.BetterProjectorOverlayMod;
import betterscreenshot.features.BetterScreenShotFeature;
import custommarker.features.CustomMarkerFeature;
import foreignservertranslator.ForeignServerTranslatorMod;
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
import whousesthisbuilding.WhoUsesThisBuildingMod;

import java.util.LinkedHashMap;
import java.util.Map;

import static mindustry.Vars.ui;

public class BekToolsMod extends Mod{
    private static final String moduleFailureMessage = "@bektools.module.failed";

    private static final String modulePgmm = "pgmm";
    private static final String moduleStealthPath = "sp";
    private static final String moduleCustomMarker = "cm";
    private static final String moduleScreenshot = "bss";
    private static final String moduleRadialBuildMenu = "rbm";
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
    private static final String moduleProfiler = "profiler";
    private static final String moduleUsageReporter = "usage-reporter";

    private final Map<String, Throwable> moduleFailures = new LinkedHashMap<>();

    private final PowerGridMinimapMod pgmm;
    private final StealthPathMod stealthPath;
    private final RadialBuildMenuMod radialBuildMenu;
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

        pgmm = initializeModule(modulePgmm, pgmmSupplier);
        stealthPath = initializeModule(moduleStealthPath, stealthPathSupplier);
        radialBuildMenu = initializeModule(moduleRadialBuildMenu, radialBuildMenuSupplier);
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
            BetterLogisticsSpeedMod.configureOverlayUi(overlayUi);
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
        postHogUsageReporter = initializeModule(moduleUsageReporter, () -> new PostHogUsageReporter(getClass()));

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
            addModuleGroup(table, modulePgmm, pgmm != null, Core.bundle.get("bektools.section.pgmm", "Power Grid Minimap"), Icon.power, st -> pgmm.bekBuildSettings(st));
            addModuleGroup(table, moduleStealthPath, stealthPath != null, Core.bundle.get("bektools.section.sp", "Stealth Path"), Icon.map, st -> stealthPath.bekBuildSettings(st));
            addModuleGroup(table, moduleCustomMarker, !isModuleFailed(moduleCustomMarker), Core.bundle.get("bektools.section.cm", "Custom Marker"), Icon.mapSmall, CustomMarkerFeature::buildSettings);
            addModuleGroup(table, moduleScreenshot, !isModuleFailed(moduleScreenshot), Core.bundle.get("bektools.section.bss", "Better ScreenShot (BSS core by Miner)"), Icon.map, BetterScreenShotFeature::buildSettings);
            addModuleGroup(table, moduleRadialBuildMenu, radialBuildMenu != null, Core.bundle.get("bektools.section.rbm", "Radial Build Menu"), Icon.list, st -> radialBuildMenu.bekBuildSettings(st));
            addModuleGroup(table, moduleBetterMiniMap, betterMiniMap != null, Core.bundle.get("bektools.section.bmm", "betterMiniMap"), Icon.map, BetterMiniMapMod::bekBuildSettings);
            addModuleGroup(table, moduleServerPlayerDatabase, serverPlayerDataBase != null, Core.bundle.get("bektools.section.spdb", "Server Player DataBase"), Icon.players, st -> serverPlayerDataBase.bekBuildSettings(st));
            addModuleGroup(table, moduleBetterMapEditor, betterMapEditor != null, Core.bundle.get("bektools.section.bme", "Better Map Editor"), Icon.map, st -> {
                st.pref(new RbmStyle.SubHeaderSetting("@bektools.section.bme.none"));
            });
            addModuleGroup(table, moduleBetterProjectorOverlay, betterProjectorOverlay != null, Core.bundle.get("bektools.section.bpo", "Better Projector Overlay"), Icon.power, BetterProjectorOverlayMod::bekBuildSettings);
            addModuleGroup(table, moduleBetterLogisticsSpeed, betterLogisticsSpeed != null, Core.bundle.get("bektools.section.bls", "Better Logistics Speed"), Icon.rightOpen, BetterLogisticsSpeedMod::bekBuildSettings);
            addModuleGroup(table, moduleBetterHotKey, betterHotKey != null, Core.bundle.get("bektools.section.bhk", "Better HotKey"), Icon.settingsSmall, st -> betterHotKey.bekBuildSettings(st));
            addModuleGroup(table, moduleModUpdater, modUpdater != null, Core.bundle.get("bektools.section.mu", "Mod Updater"), Icon.refresh, ModUpdaterMod::bekBuildSettings);
            addModuleGroup(table, moduleWhoUsesThisBuilding, whoUsesThisBuilding != null, Core.bundle.get("bektools.section.wutb", "Who Uses This Building"), Icon.logicSmall, st -> whoUsesThisBuilding.bekBuildSettings(st));
            addModuleGroup(table, modulePatchViewer, patchViewer != null, Core.bundle.get("bektools.section.pv", "PatchViewer"), Icon.list, st -> patchViewer.bekBuildSettings(st));
            addModuleGroup(table, modulePinyinSearchSupport, pinyinSearchSupport != null, Core.bundle.get("bektools.section.pss", "Pinyin Search Support"), Icon.zoom, st -> pinyinSearchSupport.bekBuildSettings(st));
            addModuleGroup(table, moduleForeignServerTranslator, foreignServerTranslator != null, Core.bundle.get("bektools.section.fst", "Foreign Server Translator"), Icon.chat, st -> foreignServerTranslator.bekBuildSettings(st));
            addModuleGroup(table, moduleTripwire, tripwire != null, Core.bundle.get("bektools.section.tw", "Tripwire"), Icon.map, st -> tripwire.bekBuildSettings(st));
            addModuleGroup(table, moduleBetterPolyAi, betterPolyAi != null, Core.bundle.get("bektools.section.bpa", "Better PolyAI"), Icon.units, st -> betterPolyAi.bekBuildSettings(st));
            addModuleGroup(table, moduleAdvancedReplace, advancedReplace != null, Core.bundle.get("bektools.section.ar", "Advanced Replace"), Icon.map, st -> advancedReplace.bekBuildSettings(st));
            addModuleGroup(table, moduleProfiler, !isModuleFailed(moduleProfiler), Core.bundle.get("bektools.section.profiler", "Performance Profiler"), Icon.chartBar, NeonProfilerFeature::buildSettings);
        });
        settingsRegistered = true;
    }

    private void addModuleGroup(SettingsMenuDialog.SettingsTable table, String moduleId, boolean available, String title, Drawable icon, Cons<SettingsMenuDialog.SettingsTable> builder){
        if(!available || isModuleFailed(moduleId)){
            addGroup(table, title, icon, this::addFailurePlaceholder);
            return;
        }

        addGroup(table, title, icon, nested -> {
            if(isModuleFailed(moduleId)){
                addFailurePlaceholder(nested);
                return;
            }
            try{
                builder.get(nested);
            }catch(Throwable t){
                recordModuleFailure(moduleId, t);
                addFailurePlaceholder(nested);
            }
        });
    }

    private void addFailurePlaceholder(SettingsMenuDialog.SettingsTable table){
        table.pref(new RbmStyle.SubHeaderSetting(moduleFailureMessage));
    }

    private static void addGroup(SettingsMenuDialog.SettingsTable table, String title, Drawable icon, Cons<SettingsMenuDialog.SettingsTable> builder){
        table.pref(new CollapsibleGroupSetting(title, icon, 24f, builder));
        table.pref(new RbmStyle.SpacerSetting(4f));
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
            float width = RbmStyle.prefWidth();
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
                        body.add(nested).width(width).growX().center();
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

                wrap.add(header).width(width).growX();
                wrap.row();
                wrap.image(mindustry.gen.Tex.whiteui).color(VscodeSettingsStyle.accentColor()).height(2f).width(width).padBottom(8f);
                wrap.row();
                wrap.add(collapser).width(width).center();
            }).width(width).padTop(12f).padBottom(2f).center();
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
