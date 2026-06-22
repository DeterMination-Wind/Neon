package bektools;

import arc.Core;
import arc.Events;
import arc.func.Cons;
import arc.scene.ui.Image;
import arc.scene.ui.Label;
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
import bettermapeditor.BetterMapEditorMod;
import betterhotkey.BetterHotKeyMod;
import betterminimap.BetterMiniMapMod;
import betterlogisticsspeed.BetterLogisticsSpeedMod;
import betterprojectoroverlay.BetterProjectorOverlayMod;
import betterscreenshot.features.BetterScreenShotFeature;
import custommarker.features.CustomMarkerFeature;
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
    private final PostHogUsageReporter postHogUsageReporter;

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
        postHogUsageReporter = new PostHogUsageReporter(getClass());
        CustomMarkerFeature.init();
        BetterScreenShotFeature.init();

        Events.on(ClientLoadEvent.class, e -> {
            postHogUsageReporter.onClientLoad();
            GithubUpdateCheck.applyDefaults();
            registerSettings();
            GithubUpdateCheck.checkOnce();
        });
    }

    private static OverlayUiBridge vanillaOverlayUi(){
        LegacyMindustryXGuard.rejectLegacyMindustryX("Neon");
        return OverlayUiBridge.UNSUPPORTED;
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
            addGroup(table, Core.bundle.get("bektools.section.profiler", "Performance Profiler"), Icon.chartBar, NeonProfilerFeature::buildSettings);
            addGroup(table, Core.bundle.get("bektools.section.update", "Update"), Icon.refresh, st -> {
                st.checkPref(GithubUpdateCheck.enabledKey(), true);
                st.checkPref(GithubUpdateCheck.showDialogKey(), true);
            });
        });
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
                final Label[] arrow = new Label[1];
                final Table[] body = new Table[1];
                Runnable refresh = () -> {
                    arrow[0].setText(expanded ? "v" : ">");
                    body[0].clearChildren();
                    if(!expanded) return;

                    NestedSettingsTable nested = new NestedSettingsTable(indent);
                    builder.get(nested);
                    nested.finishBuild();
                    body[0].add(nested).width(width).growX().center();
                };

                wrap.table(header -> {
                    header.background(VscodeSettingsStyle.headerBackground());
                    header.margin(8f);
                    header.left();
                    header.clicked(() -> {
                        expanded = !expanded;
                        refresh.run();
                    });
                    if(icon != null){
                        Image ic = header.image(icon).size(20f).padRight(8f).get();
                        ic.setScaling(Scaling.fit);
                        ic.update(() -> ic.setColor(VscodeSettingsStyle.accentColor()));
                    }
                    header.add(title).color(VscodeSettingsStyle.accentColor()).left().growX().minWidth(0f).wrap();
                    arrow[0] = new Label(">");
                    arrow[0].setColor(VscodeSettingsStyle.accentColor());
                    header.add(arrow[0]).width(20f).padLeft(8f).right();
                }).width(width).growX();
                wrap.row();
                wrap.image(mindustry.gen.Tex.whiteui).color(VscodeSettingsStyle.accentColor()).height(2f).width(width).padBottom(8f);
                wrap.row();
                body[0] = new Table();
                body[0].center();
                wrap.add(body[0]).width(width).center();
                refresh.run();
            }).width(width).padTop(12f).padBottom(2f).center();
            table.row();
        }
    }

    private static class NestedSettingsTable extends SettingsMenuDialog.SettingsTable{
        private boolean suppressRebuild = true;

        public NestedSettingsTable(float indent){
            super();
            left();
            defaults().left();
            defaults().padLeft(indent);
        }

        @Override
        public void rebuild(){
            if(suppressRebuild) return;

            clearChildren();
            for(Setting setting : list){
                setting.add(this);
            }
        }

        public void finishBuild(){
            suppressRebuild = false;
            rebuild();
        }
    }
}
