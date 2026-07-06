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
import serverplayerdatabase.ServerPlayerDataBaseMod;
import stealthpath.StealthPathMod;
import tripwire.TripwireMod;
import whousesthisbuilding.WhoUsesThisBuildingMod;

import static mindustry.Vars.ui;

public class BekToolsMod extends Mod{
    private static final String spdbUnavailableMessage = "当前运行环境缺少 SPDB 所需的 Java/SQLite 组件，Neon 已跳过该模块以避免整体加载失败。";

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
        PowerGridMinimapMod.bekBundled = true;
        StealthPathMod.bekBundled = true;
        RadialBuildMenuMod.bekBundled = true;
        BetterMiniMapMod.bekBundled = true;
        BetterMapEditorMod.bekBundled = true;
        ServerPlayerDataBaseMod.bekBundled = true;
        BetterProjectorOverlayMod.bekBundled = true;
        BetterLogisticsSpeedMod.bekBundled = true;
        BetterHotKeyMod.bekBundled = true;
        ModUpdaterMod.bekBundled = true;
        WhoUsesThisBuildingMod.bekBundled = true;
        PatchViewerMod.bekBundled = true;
        PinyinSearchSupportMod.bekBundled = true;
        ForeignServerTranslatorMod.bekBundled = true;
        TripwireMod.bekBundled = true;
        BetterPolyAiMod.bekBundled = true;
        AdvancedReplaceMod.bekBundled = true;

        BetterScreenShotFeature.configureOverlayUi(overlayUi);
        CustomMarkerFeature.configureCompat(overlayUi, markerBridge);
        NeonProfilerFeature.configureOverlayUi(overlayUi);
        NeonProfilerFeature.init();

        pgmm = pgmmSupplier.get();
        stealthPath = stealthPathSupplier.get();
        radialBuildMenu = radialBuildMenuSupplier.get();
        betterMiniMap = new BetterMiniMapMod();
        betterMiniMap.init();
        ServerPlayerDataBaseMod spdb = null;
        try{
            spdb = serverPlayerDataBaseSupplier.get();
        }catch(Throwable t){
            Log.err("Neon: failed to initialize bundled ServerPlayerDataBase module; continuing without it.", t);
        }
        serverPlayerDataBase = spdb;
        betterMapEditor = new BetterMapEditorMod();
        betterMapEditor.init();
        betterProjectorOverlay = betterProjectorOverlaySupplier.get();
        betterProjectorOverlay.init();
        betterLogisticsSpeed = new BetterLogisticsSpeedMod();
        betterLogisticsSpeed.init();
        betterHotKey = betterHotKeySupplier.get();
        betterHotKey.init();
        modUpdater = new ModUpdaterMod();
        modUpdater.init();
        whoUsesThisBuilding = new WhoUsesThisBuildingMod();
        whoUsesThisBuilding.init();
        patchViewer = new PatchViewerMod();
        patchViewer.init();
        pinyinSearchSupport = new PinyinSearchSupportMod();
        pinyinSearchSupport.init();
        foreignServerTranslator = new ForeignServerTranslatorMod();
        foreignServerTranslator.init();
        tripwire = new TripwireMod();
        betterPolyAi = new BetterPolyAiMod();
        betterPolyAi.init();
        advancedReplace = new AdvancedReplaceMod();
        advancedReplace.init();
        postHogUsageReporter = new PostHogUsageReporter(getClass());
        CustomMarkerFeature.init();
        BetterScreenShotFeature.init();

        Events.on(ClientLoadEvent.class, e -> {
            try{
                postHogUsageReporter.onClientLoad();
            }catch(Throwable t){
                Log.err("Neon: failed to initialize usage reporter; continuing with settings registration.", t);
            }
            try{
                registerSettings();
            }catch(Throwable t){
                Log.err("Neon: failed to register unified settings.", t);
            }
        });
    }

    private static OverlayUiBridge vanillaOverlayUi(){
        return OverlayUiBridge.autoDetect();
    }

    @FunctionalInterface
    protected interface ModSupplier<T>{
        T get();
    }

    @Override
    public void registerClientCommands(CommandHandler handler){
        NeonProfilerFeature.registerClientCommands(handler);
        pgmm.registerClientCommands(handler);
        stealthPath.registerClientCommands(handler);
        radialBuildMenu.registerClientCommands(handler);
        if(serverPlayerDataBase != null){
            serverPlayerDataBase.registerClientCommands(handler);
        }
    }

    private void registerSettings(){
        if(ui == null || ui.settings == null) return;
        if(settingsRegistered) return;

        ui.settings.addCategory("@bektools.category", Icon.settings, table -> {
            addGroup(table, Core.bundle.get("bektools.section.pgmm", "Power Grid Minimap"), Icon.power, pgmm::bekBuildSettings);
            addGroup(table, Core.bundle.get("bektools.section.sp", "Stealth Path"), Icon.map, stealthPath::bekBuildSettings);
            addGroup(table, Core.bundle.get("bektools.section.cm", "Custom Marker"), Icon.mapSmall, CustomMarkerFeature::buildSettings);
            addGroup(table, Core.bundle.get("bektools.section.bss", "Better ScreenShot (BSS core by Miner)"), Icon.map, BetterScreenShotFeature::buildSettings);
            addGroup(table, Core.bundle.get("bektools.section.rbm", "Radial Build Menu"), Icon.list, radialBuildMenu::bekBuildSettings);
            addGroup(table, Core.bundle.get("bektools.section.bmm", "betterMiniMap"), Icon.map, BetterMiniMapMod::bekBuildSettings);
            if(serverPlayerDataBase != null){
                addGroup(table, Core.bundle.get("bektools.section.spdb", "Server Player DataBase"), Icon.players, serverPlayerDataBase::bekBuildSettings);
            }else{
                addGroup(table, Core.bundle.get("bektools.section.spdb", "Server Player DataBase"), Icon.players, st -> {
                    st.pref(new RbmStyle.SubHeaderSetting(spdbUnavailableMessage));
                });
            }
            addGroup(table, Core.bundle.get("bektools.section.bme", "Better Map Editor"), Icon.map, st -> {
                st.pref(new RbmStyle.SubHeaderSetting("@bektools.section.bme.none"));
            });
            addGroup(table, Core.bundle.get("bektools.section.bpo", "Better Projector Overlay"), Icon.power, BetterProjectorOverlayMod::bekBuildSettings);
            addGroup(table, Core.bundle.get("bektools.section.bls", "Better Logistics Speed"), Icon.rightOpen, BetterLogisticsSpeedMod::bekBuildSettings);
            addGroup(table, Core.bundle.get("bektools.section.bhk", "Better HotKey"), Icon.settingsSmall, betterHotKey::bekBuildSettings);
            addGroup(table, Core.bundle.get("bektools.section.mu", "Mod Updater"), Icon.refresh, ModUpdaterMod::bekBuildSettings);
            addGroup(table, Core.bundle.get("bektools.section.wutb", "Who Uses This Building"), Icon.logicSmall, whoUsesThisBuilding::bekBuildSettings);
            addGroup(table, Core.bundle.get("bektools.section.pv", "PatchViewer"), Icon.list, patchViewer::bekBuildSettings);
            addGroup(table, Core.bundle.get("bektools.section.pss", "Pinyin Search Support"), Icon.zoom, pinyinSearchSupport::bekBuildSettings);
            addGroup(table, Core.bundle.get("bektools.section.fst", "Foreign Server Translator"), Icon.chat, foreignServerTranslator::bekBuildSettings);
            addGroup(table, Core.bundle.get("bektools.section.tw", "Tripwire"), Icon.map, tripwire::bekBuildSettings);
            addGroup(table, Core.bundle.get("bektools.section.bpa", "Better PolyAI"), Icon.units, betterPolyAi::bekBuildSettings);
            addGroup(table, Core.bundle.get("bektools.section.ar", "Advanced Replace"), Icon.map, advancedReplace::bekBuildSettings);
            addGroup(table, Core.bundle.get("bektools.section.profiler", "Performance Profiler"), Icon.chartBar, NeonProfilerFeature::buildSettings);
        });
        settingsRegistered = true;
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
