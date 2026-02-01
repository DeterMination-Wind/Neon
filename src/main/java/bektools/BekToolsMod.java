package bektools;

import arc.Core;
import arc.Events;
import arc.util.CommandHandler;
import mindustry.game.EventType.ClientLoadEvent;
import mindustry.gen.Icon;
import mindustry.mod.Mod;
import mindustry.ui.dialogs.SettingsMenuDialog;
import bektools.ui.RbmStyle;
import powergridminimap.PowerGridMinimapMod;
import radialbuildmenu.RadialBuildMenuMod;
import stealthpath.StealthPathMod;

import static mindustry.Vars.ui;

public class BekToolsMod extends Mod{
    private final PowerGridMinimapMod pgmm;
    private final StealthPathMod stealthPath;
    private final RadialBuildMenuMod radialBuildMenu;

    public BekToolsMod(){
        PowerGridMinimapMod.bekBundled = true;
        StealthPathMod.bekBundled = true;
        RadialBuildMenuMod.bekBundled = true;

        pgmm = new PowerGridMinimapMod();
        stealthPath = new StealthPathMod();
        radialBuildMenu = new RadialBuildMenuMod();

        Events.on(ClientLoadEvent.class, e -> registerSettings());
    }

    @Override
    public void registerClientCommands(CommandHandler handler){
        pgmm.registerClientCommands(handler);
        stealthPath.registerClientCommands(handler);
        radialBuildMenu.registerClientCommands(handler);
    }

    private void registerSettings(){
        if(ui == null || ui.settings == null) return;

        ui.settings.addCategory("@bektools.category", Icon.settings, table -> {
            addGroup(table, Core.bundle.get("bektools.section.pgmm", "Power Grid Minimap"), Icon.power, pgmm::bekBuildSettings);
            addGroup(table, Core.bundle.get("bektools.section.sp", "Stealth Path"), Icon.map, stealthPath::bekBuildSettings);
            addGroup(table, Core.bundle.get("bektools.section.rbm", "Radial Build Menu"), Icon.list, radialBuildMenu::bekBuildSettings);
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
