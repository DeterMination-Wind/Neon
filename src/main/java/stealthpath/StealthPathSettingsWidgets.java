package stealthpath;

import bektools.ui.RbmStyle;
import mindustry.ui.dialogs.SettingsMenuDialog;

final class HeaderSetting extends RbmStyle.HeaderSetting{
    HeaderSetting(String titleKeyOrText, arc.scene.style.Drawable icon){
        super(titleKeyOrText.startsWith("@") ? arc.Core.bundle.get(titleKeyOrText.substring(1)) : titleKeyOrText, icon);
    }
}

final class IconCheckSetting extends RbmStyle.IconCheckSetting{
    IconCheckSetting(String name, boolean def, arc.scene.style.Drawable icon, arc.func.Cons<Boolean> changed){
        super(name, def, icon, changed);
    }
}

final class IconSliderSetting extends RbmStyle.IconSliderSetting{
    IconSliderSetting(String name, int def, int min, int max, int step, arc.scene.style.Drawable icon, SettingsMenuDialog.StringProcessor sp, arc.func.Intc changed){
        super(name, def, min, max, step, icon, sp, changed);
    }
}

final class IconTextSetting extends RbmStyle.IconTextSetting{
    IconTextSetting(String name, String def, arc.scene.style.Drawable icon, arc.func.Cons<String> changed){
        super(name, def, icon, changed);
    }
}
