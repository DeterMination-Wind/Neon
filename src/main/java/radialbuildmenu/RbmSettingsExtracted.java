package radialbuildmenu;

import arc.Core;
import arc.graphics.Color;
import arc.scene.event.Touchable;
import arc.scene.style.Drawable;
import arc.scene.ui.CheckBox;
import arc.scene.ui.Label;
import arc.scene.ui.Slider;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.TextButton;
import arc.scene.ui.layout.Table;
import arc.util.Scaling;
import mindustry.gen.Icon;
import mindustry.gen.Tex;
import mindustry.graphics.Pal;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.SettingsMenuDialog;

/**
 * RBM settings UI helpers (extracted from the monolithic mod class).
 *
 * RBM 设置界面相关的小组件。
 * 这些类原本是 {@link RadialBuildMenuMod} 的内部类，拆出来以减少主文件体积；
 * 行为保持一致，不改功能。
 */
final class SubHeaderSetting extends SettingsMenuDialog.SettingsTable.Setting{
    private final String titleKeyOrText;

    SubHeaderSetting(String titleKeyOrText){
        super("rbm-subheader");
        this.titleKeyOrText = titleKeyOrText;
    }

    @Override
    public void add(SettingsMenuDialog.SettingsTable table){
        String text = titleKeyOrText.startsWith("@")
            ? Core.bundle.get(titleKeyOrText.substring(1))
            : titleKeyOrText;
        table.row();
        table.add(text).color(Pal.accent).left().growX().minWidth(0f).wrap().padTop(10f).padBottom(2f);
        table.row();
    }
}

final class AdvancedButtonSetting extends SettingsMenuDialog.SettingsTable.Setting{
    private final RadialBuildMenuMod mod;

    AdvancedButtonSetting(RadialBuildMenuMod mod){
        super("rbm-advanced");
        this.mod = mod;
    }

    @Override
    public void add(SettingsMenuDialog.SettingsTable table){
        TextButton button = table.button(title, mod::showAdvancedDialog).growX().margin(14f).pad(6f).get();
        button.update(() -> button.setDisabled(!Core.settings.getBool(RadialBuildMenuMod.keyProMode, false)));

        addDesc(button);
        table.row();
    }
}

final class WideSliderSetting extends SettingsMenuDialog.SettingsTable.Setting{
    private final int def, min, max, step;
    private final SettingsMenuDialog.StringProcessor sp;

    WideSliderSetting(String name, int def, int min, int max, int step, SettingsMenuDialog.StringProcessor sp){
        super(name);
        this.def = def;
        this.min = min;
        this.max = max;
        this.step = step;
        this.sp = sp;
    }

    @Override
    public void add(SettingsMenuDialog.SettingsTable table){
        Slider slider = new Slider(min, max, step, false);
        slider.setValue(Core.settings.getInt(name, def));

        Label value = new Label("", Styles.outlineLabel);

        Table content = new Table();
        content.left();
        content.add(title, Styles.outlineLabel).left().growX().minWidth(0f).wrap();
        content.add(value).padLeft(10f).right();
        // Match MindustryX SliderPref overlay spacing.
        content.margin(3f, 16f, 3f, 16f);
        content.touchable = Touchable.disabled;

        slider.changed(() -> {
            Core.settings.put(name, (int)slider.getValue());
            value.setText(sp.get((int)slider.getValue()));
        });

        slider.change();

        // leave room for the vertical scrollbar on the right side
        addDesc(table.stack(slider, content).width(RadialBuildMenuMod.prefWidth() - 64f).left().padTop(4f).get());
        table.row();
    }
}

/**
 * MindustryX-style checkbox setting row (background, icon, wrapped title).
 *
 * Used by RBM to visually match MindustryX settings aesthetics without adding a hard dependency.
 */
final class IconCheckSetting extends SettingsMenuDialog.SettingsTable.Setting{
    private final boolean def;
    private final Drawable icon;

    IconCheckSetting(String name, boolean def, Drawable icon){
        super(name);
        this.def = def;
        this.icon = icon;
    }

    @Override
    public void add(SettingsMenuDialog.SettingsTable table){
        CheckBox box = new CheckBox(title);
        box.getLabel().setWrap(true);
        box.getLabelCell().growX().minWidth(0f);

        box.update(() -> box.setChecked(Core.settings.getBool(name, def)));
        box.changed(() -> Core.settings.put(name, box.isChecked()));

        table.table(Tex.button, t -> {
            t.left().margin(10f);
            if(icon != null) t.image(icon).size(20f).padRight(8f);
            t.add(box).left().growX().minWidth(0f);
        }).width(RadialBuildMenuMod.prefWidth()).left().padTop(6f);

        addDesc(box);
        table.row();
    }
}

/**
 * MindustryX-style slider setting row (Stack overlay showing title + value).
 */
final class IconSliderSetting extends SettingsMenuDialog.SettingsTable.Setting{
    private final int def, min, max, step;
    private final Drawable icon;
    private final SettingsMenuDialog.StringProcessor sp;

    IconSliderSetting(String name, int def, int min, int max, int step, Drawable icon, SettingsMenuDialog.StringProcessor sp){
        super(name);
        this.def = def;
        this.min = min;
        this.max = max;
        this.step = step;
        this.icon = icon;
        this.sp = sp;
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
        });
        slider.change();

        addDesc(table.stack(slider, content).width(RadialBuildMenuMod.prefWidth()).left().padTop(6f).get());
        table.row();
    }
}
