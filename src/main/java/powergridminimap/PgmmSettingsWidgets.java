package powergridminimap;

import arc.Core;
import arc.func.Cons;
import arc.math.Mathf;
import arc.scene.event.Touchable;
import arc.scene.style.Drawable;
import arc.scene.ui.CheckBox;
import arc.scene.ui.Label;
import arc.scene.ui.Slider;
import arc.scene.ui.TextField;
import arc.scene.ui.layout.Table;
import bektools.ui.VscodeSettingsStyle;
import mindustry.gen.Tex;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.SettingsMenuDialog;

/**
 * MindustryX-style Settings UI widgets for PGMM.
 *
 * These widgets are designed to visually match MindustryX's settings aesthetics:
 * - Consistent background (`Tex.button`)
 * - Left icon + wrapped title
 * - Slider value displayed inline (Stack overlay)
 * - Description tooltip via {@link SettingsMenuDialog.SettingsTable.Setting#addDesc}
 */
final class PgmmSettingsWidgets{
    private PgmmSettingsWidgets(){
    }

    static float prefWidth(){
        // Similar to MindustryX/StealthPath: keep settings rows readable on both desktop and mobile.
        return Math.min(Core.graphics.getWidth() / 1.15f, 520f);
    }

    static final class HeaderSetting extends SettingsMenuDialog.SettingsTable.Setting{
        private final String titleKeyOrText;
        private final Drawable icon;

        HeaderSetting(String titleKeyOrText, Drawable icon){
            super("pgmm-header");
            this.titleKeyOrText = titleKeyOrText;
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
            String text = titleKeyOrText.startsWith("@")
                ? Core.bundle.get(titleKeyOrText.substring(1))
                : titleKeyOrText;
            t.add(text).color(VscodeSettingsStyle.accentColor()).left().growX().wrap();
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

    static final class IconSliderSetting extends SettingsMenuDialog.SettingsTable.Setting{
        private final int def, min, max, step;
        private final Drawable icon;
        private final SettingsMenuDialog.StringProcessor sp;
        private final arc.func.Intc changed;

        IconSliderSetting(String name, int def, int min, int max, int step, Drawable icon, SettingsMenuDialog.StringProcessor sp, arc.func.Intc changed){
            super(name);
            this.def = def;
            this.min = min;
            this.max = max;
            this.step = step;
            this.icon = icon;
            this.sp = sp;
            this.changed = changed;
        }

        @Override
        public void add(SettingsMenuDialog.SettingsTable table){
            Slider slider = new Slider(min, max, step, false);
            slider.setValue(Core.settings.getInt(name, def));

            Label value = new Label("", Styles.outlineLabel);
            Table content = new Table();
            content.left();
            if(icon != null) content.image(icon).size(20f).padRight(8f);
            content.add(title, Styles.outlineLabel).left().growX().minWidth(0f).wrap();
            content.add(value).padLeft(10f).right();
            content.margin(3f, 16f, 3f, 16f);
            content.touchable = Touchable.disabled;

            slider.changed(() -> {
                int v = (int)slider.getValue();
                Core.settings.put(name, v);
                value.setText(sp == null ? String.valueOf(v) : sp.get(v));
                if(changed != null) changed.get(v);
            });
            slider.change();

            table.table(VscodeSettingsStyle.cardBackground(), row -> {
                row.left().margin(4f);
                row.stack(slider, content).width(prefWidth() - 8f).left();
            }).width(prefWidth()).left().padTop(6f);
            addDesc(slider);
            table.row();
        }
    }

    static final class IconTextSetting extends SettingsMenuDialog.SettingsTable.Setting{
        private final String def;
        private final Drawable icon;
        private final Cons<String> changed;

        IconTextSetting(String name, String def, Drawable icon, Cons<String> changed){
            super(name);
            this.def = def;
            this.icon = icon;
            this.changed = changed;
        }

        @Override
        public void add(SettingsMenuDialog.SettingsTable table){
            final TextField[] fieldRef = {null};
            table.table(VscodeSettingsStyle.cardBackground(), t -> {
                t.left().margin(10f);
                if(icon != null) t.image(icon).size(20f).padRight(8f);

                t.add(title).left().growX().minWidth(0f).wrap();

                TextField field = t.field(Core.settings.getString(name, def), text -> {
                    Core.settings.put(name, text);
                    if(changed != null) changed.get(text);
                }).growX().minWidth(140f).get();

                field.setMessageText(def);
                fieldRef[0] = field;
            }).width(prefWidth()).left().padTop(6f);

            if(fieldRef[0] != null) addDesc(fieldRef[0]);
            table.row();
        }
    }

    static final class IconIntFieldSetting extends SettingsMenuDialog.SettingsTable.Setting{
        private final int def;
        private final int min;
        private final int max;
        private final Drawable icon;
        private final arc.func.Intc changed;

        IconIntFieldSetting(String name, int def, int min, int max, Drawable icon, arc.func.Intc changed){
            super(name);
            this.def = def;
            this.min = min;
            this.max = max;
            this.icon = icon;
            this.changed = changed;
        }

        @Override
        public void add(SettingsMenuDialog.SettingsTable table){
            final TextField[] fieldRef = {null};
            final boolean[] updatingText = {false};

            int stored = Core.settings.getInt(name, def);
            int clamped = Mathf.clamp(stored, min, max);
            if(clamped != stored){
                Core.settings.put(name, clamped);
            }

            table.table(VscodeSettingsStyle.cardBackground(), t -> {
                t.left().margin(10f);
                if(icon != null) t.image(icon).size(20f).padRight(8f);

                t.add(title).left().growX().minWidth(0f).wrap();

                TextField field = t.field(String.valueOf(clamped), text -> {
                    if(updatingText[0]) return;
                    int parsed;
                    try{
                        parsed = Integer.parseInt(text.trim());
                    }catch(Throwable ignored){
                        return;
                    }

                    int value = Mathf.clamp(parsed, min, max);
                    Core.settings.put(name, value);
                    if(changed != null) changed.get(value);

                    String normalized = String.valueOf(value);
                    if(!normalized.equals(fieldRef[0].getText())){
                        updatingText[0] = true;
                        fieldRef[0].setText(normalized);
                        fieldRef[0].setCursorPosition(normalized.length());
                        updatingText[0] = false;
                    }
                }).growX().minWidth(140f).get();

                field.setMessageText(String.valueOf(def));
                field.setFilter((f, c) -> Character.isDigit(c) || (c == '-' && f.getCursorPosition() == 0 && !f.getText().contains("-")));
                fieldRef[0] = field;
            }).width(prefWidth()).left().padTop(6f);

            if(fieldRef[0] != null) addDesc(fieldRef[0]);
            table.row();
        }
    }
}
