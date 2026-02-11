package serverplayerdatabase;

import arc.Core;
import arc.func.Cons;
import arc.scene.style.Drawable;
import arc.scene.ui.CheckBox;
import arc.scene.ui.TextButton;
import bektools.ui.VscodeSettingsStyle;
import mindustry.gen.Tex;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.SettingsMenuDialog;

final class SpdbSettingsWidgets{
    private SpdbSettingsWidgets(){
    }

    static float prefWidth(){
        return Math.min(Core.graphics.getWidth() / 1.15f, 520f);
    }

    static final class HeaderSetting extends SettingsMenuDialog.SettingsTable.Setting{
        private final String header;
        private final Drawable icon;

        HeaderSetting(String header, Drawable icon){
            super("spdb-header");
            this.header = header;
            this.icon = icon;
        }

        @Override
        public void add(SettingsMenuDialog.SettingsTable table){
            table.row();
            table.table(VscodeSettingsStyle.headerBackground(), t -> {
                t.left().margin(8f);
                if(icon != null){
                    t.image(icon).size(18f).padRight(6f);
                }
                t.add(header).color(VscodeSettingsStyle.accentColor()).left().growX().wrap();
            }).padTop(10f).left().growX();
            table.row();
        }
    }

    static final class IconCheckSetting extends SettingsMenuDialog.SettingsTable.Setting{
        private final boolean def;
        private final Drawable icon;
        private final Cons<Boolean> changed;

        IconCheckSetting(String name, boolean def, Drawable icon, Cons<Boolean> changed){
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

            table.table(VscodeSettingsStyle.cardBackground(), t -> {
                t.left().margin(10f);
                if(icon != null) t.image(icon).size(20f).padRight(8f);
                t.add(box).left().growX().minWidth(0f);
            }).width(prefWidth()).left().padTop(6f);

            addDesc(box);
            table.row();
        }
    }

    static final class ActionButtonSetting extends SettingsMenuDialog.SettingsTable.Setting{
        private final String buttonText;
        private final Drawable icon;
        private final Runnable action;

        ActionButtonSetting(String buttonText, Drawable icon, Runnable action){
            super("spdb-action");
            this.buttonText = buttonText;
            this.icon = icon;
            this.action = action;
        }

        @Override
        public void add(SettingsMenuDialog.SettingsTable table){
            table.table(VscodeSettingsStyle.cardBackground(), t -> {
                t.left().margin(10f);

                TextButton button = new TextButton(buttonText, icon == null ? Styles.defaultt : Styles.defaultt);
                if(icon != null){
                    button.clearChildren();
                    button.left();
                    button.image(icon).size(18f).padRight(8f);
                    button.add(buttonText).left().wrap().growX();
                }
                button.clicked(action);
                t.add(button).growX().height(46f);
            }).width(prefWidth()).left().padTop(6f);

            table.row();
        }
    }
}
