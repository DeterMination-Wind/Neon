package radialbuildmenu;

import arc.Core;
import arc.scene.ui.TextButton;
import arc.scene.ui.layout.Table;
import bektools.ui.RbmStyle;
import bektools.ui.VscodeSettingsStyle;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.SettingsMenuDialog;

/**
 * RBM settings UI helpers (extracted from the monolithic mod class).
 */
final class SubHeaderSetting extends RbmStyle.SubHeaderSetting{
    SubHeaderSetting(String titleKeyOrText){
        super(titleKeyOrText);
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
        final TextButton[] buttonRef = {null};
        table.table(VscodeSettingsStyle.cardBackground(), row -> {
            row.left().margin(6f);
            buttonRef[0] = row.button(title, Styles.flatt, mod::showAdvancedDialog)
                .growX()
                .height(RbmStyle.buttonHeight())
                .padLeft(8f)
                .get();
        }).width(RbmStyle.rowWidth()).left().padTop(6f);
        if(buttonRef[0] != null){
            buttonRef[0].update(() -> buttonRef[0].setDisabled(!Core.settings.getBool(RadialBuildMenuMod.keyProMode, false)));
            addDesc(buttonRef[0]);
        }
        table.row();
    }
}

final class WideSliderSetting extends RbmStyle.IconSliderSetting{
    WideSliderSetting(String name, int def, int min, int max, int step, SettingsMenuDialog.StringProcessor sp){
        super(name, def, min, max, step, null, sp, null);
    }
}

final class IconCheckSetting extends RbmStyle.IconCheckSetting{
    IconCheckSetting(String name, boolean def, arc.scene.style.Drawable icon){
        super(name, def, icon, null);
    }
}

final class IconSliderSetting extends RbmStyle.IconSliderSetting{
    IconSliderSetting(String name, int def, int min, int max, int step, arc.scene.style.Drawable icon, SettingsMenuDialog.StringProcessor sp){
        super(name, def, min, max, step, icon, sp, null);
    }
}
