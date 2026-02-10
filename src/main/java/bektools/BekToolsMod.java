package bektools;

import arc.Core;
import arc.Events;
import arc.util.CommandHandler;
import bettermapeditor.BetterMapEditorMod;
import betterminimap.BetterMiniMapMod;
import betterprojectoroverlay.BetterProjectorOverlayMod;
import mindustry.game.EventType.ClientLoadEvent;
import mindustry.gen.Icon;
import mindustry.mod.Mod;
import mindustry.ui.dialogs.SettingsMenuDialog;
import bektools.ui.RbmStyle;
import powergridminimap.PowerGridMinimapMod;
import radialbuildmenu.RadialBuildMenuMod;
import serverplayerdatabase.ServerPlayerDataBaseMod;
import stealthpath.StealthPathMod;

import static mindustry.Vars.ui;

public class BekToolsMod extends Mod{
    private final PowerGridMinimapMod pgmm;
    private final StealthPathMod stealthPath;
    private final RadialBuildMenuMod radialBuildMenu;
    private final BetterMiniMapMod betterMiniMap;
    private final ServerPlayerDataBaseMod serverPlayerDataBase;
    private final BetterMapEditorMod betterMapEditor;
    private final BetterProjectorOverlayMod betterProjectorOverlay;

    public BekToolsMod(){
        PowerGridMinimapMod.bekBundled = true;
        StealthPathMod.bekBundled = true;
        RadialBuildMenuMod.bekBundled = true;
        BetterMiniMapMod.bekBundled = true;
        ServerPlayerDataBaseMod.bekBundled = true;
        BetterProjectorOverlayMod.bekBundled = true;

        pgmm = new PowerGridMinimapMod();
        stealthPath = new StealthPathMod();
        radialBuildMenu = new RadialBuildMenuMod();
        betterMiniMap = new BetterMiniMapMod();
        betterMiniMap.init();
        serverPlayerDataBase = new ServerPlayerDataBaseMod();
        betterMapEditor = new BetterMapEditorMod();
        betterMapEditor.init();
        betterProjectorOverlay = new BetterProjectorOverlayMod();
        betterProjectorOverlay.init();

        Events.on(ClientLoadEvent.class, e -> {
            GithubUpdateCheck.applyDefaults();
            registerSettings();
            GithubUpdateCheck.checkOnce();
        });
    }

    @Override
    public void registerClientCommands(CommandHandler handler){
        pgmm.registerClientCommands(handler);
        stealthPath.registerClientCommands(handler);
        radialBuildMenu.registerClientCommands(handler);
        serverPlayerDataBase.registerClientCommands(handler);
    }

    private void registerSettings(){
        if(ui == null || ui.settings == null) return;

        ui.settings.addCategory("@bektools.category", Icon.settings, table -> {
            addGroup(table, Core.bundle.get("bektools.section.pgmm", "Power Grid Minimap"), Icon.power, pgmm::bekBuildSettings);
            addGroup(table, Core.bundle.get("bektools.section.sp", "Stealth Path"), Icon.map, stealthPath::bekBuildSettings);
            addGroup(table, Core.bundle.get("bektools.section.rbm", "Radial Build Menu"), Icon.list, radialBuildMenu::bekBuildSettings);
            addGroup(table, Core.bundle.get("bektools.section.bmm", "betterMiniMap"), Icon.map, BetterMiniMapMod::bekBuildSettings);
            addGroup(table, Core.bundle.get("bektools.section.spdb", "Server Player DataBase"), Icon.players, serverPlayerDataBase::bekBuildSettings);
            addGroup(table, Core.bundle.get("bektools.section.bme", "Better Map Editor"), Icon.map, st -> {
                st.pref(new RbmStyle.SubHeaderSetting("@bektools.section.bme.none"));
            });
            addGroup(table, Core.bundle.get("bektools.section.bpo", "Better Projector Overlay"), Icon.power, BetterProjectorOverlayMod::bekBuildSettings);
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
            table.add(nested).left().width(RbmStyle.prefWidth());
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
