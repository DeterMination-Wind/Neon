package bektools;

import arc.Core;
import arc.Events;
import arc.util.CommandHandler;
import arc.util.Log;
import bektools.profiler.NeonProfilerFeature;
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
import hiddenmessage.HiddenMessageMod;
import mindustry.game.EventType.ClientLoadEvent;
import mindustry.gen.Icon;
import mindustry.mod.Mod;
import mindustry.ui.dialogs.SettingsMenuDialog;
import modupdater.ModUpdaterMod;
import bektools.ui.RbmStyle;
import patchviewer.PatchViewerMod;
import powergridminimap.PowerGridMinimapMod;
import radialbuildmenu.RadialBuildMenuMod;
import serverplayerdatabase.ServerPlayerDataBaseMod;
import stealthpath.StealthPathMod;
import updatescheme.UpdateSchemeMod;
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
    private final HiddenMessageMod hiddenMessage;
    private final UpdateSchemeMod updateScheme;
    private final WhoUsesThisBuildingMod whoUsesThisBuilding;
    private final PatchViewerMod patchViewer;
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
            BetterHotKeyMod::new,
            UpdateSchemeMod::new
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
        ModSupplier<UpdateSchemeMod> updateSchemeSupplier
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
        HiddenMessageMod.bekBundled = true;
        UpdateSchemeMod.bekBundled = true;
        WhoUsesThisBuildingMod.bekBundled = true;
        PatchViewerMod.bekBundled = true;

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
        hiddenMessage = new HiddenMessageMod();
        hiddenMessage.init();
        updateScheme = updateSchemeSupplier.get();
        updateScheme.init();
        whoUsesThisBuilding = new WhoUsesThisBuildingMod();
        whoUsesThisBuilding.init();
        patchViewer = new PatchViewerMod();
        patchViewer.init();
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
            addGroup(table, Core.bundle.get("bektools.section.us", "UpdateScheme"), Icon.download, updateScheme::bekBuildSettings);
            addGroup(table, Core.bundle.get("bektools.section.wutb", "Who Uses This Building"), Icon.logicSmall, whoUsesThisBuilding::bekBuildSettings);
            addGroup(table, Core.bundle.get("bektools.section.pv", "PatchViewer"), Icon.list, patchViewer::bekBuildSettings);
            addGroup(table, Core.bundle.get("bektools.section.hm", "Hidden Message"), Icon.chat, st -> {
                st.pref(new RbmStyle.SubHeaderSetting("@bektools.section.hm.none"));
            });
            addGroup(table, Core.bundle.get("bektools.section.profiler", "Performance Profiler"), Icon.chartBar, NeonProfilerFeature::buildSettings);
            addGroup(table, Core.bundle.get("bektools.section.update", "Update"), Icon.refresh, st -> {
                st.checkPref(GithubUpdateCheck.enabledKey(), true);
                st.checkPref(GithubUpdateCheck.showDialogKey(), true);
            });
        });
    }

    private static void addGroup(SettingsMenuDialog.SettingsTable table, String title, arc.scene.style.Drawable icon, arc.func.Cons<SettingsMenuDialog.SettingsTable> builder){
        table.pref(new RbmStyle.HeaderSetting(title, icon));
        table.pref(new GroupSetting(24f, builder));
        table.pref(new RbmStyle.SpacerSetting(4f));
    }

    private static class GroupSetting extends SettingsMenuDialog.SettingsTable.Setting{
        private final float indent;
        private final arc.func.Cons<SettingsMenuDialog.SettingsTable> builder;

        public GroupSetting(float indent, arc.func.Cons<SettingsMenuDialog.SettingsTable> builder){
            super("bektools-group");
            this.indent = indent;
            this.builder = builder;
        }

        @Override
        public void add(SettingsMenuDialog.SettingsTable table){
            NestedSettingsTable nested = new NestedSettingsTable(indent);
            builder.get(nested);
            nested.finishBuild();

            table.row();
            table.table(wrap -> {
                wrap.center();
                wrap.add(nested).width(RbmStyle.prefWidth());
            }).growX().center();
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
