package serverplayerdatabase;

import arc.func.Cons;
import arc.scene.style.Drawable;
import bektools.ui.RbmStyle;

final class SpdbSettingsWidgets{
    private SpdbSettingsWidgets(){
    }

    static final class HeaderSetting extends RbmStyle.HeaderSetting{
        HeaderSetting(String header, Drawable icon){
            super(header, icon);
        }
    }

    static final class IconCheckSetting extends RbmStyle.IconCheckSetting{
        IconCheckSetting(String name, boolean def, Drawable icon, Cons<Boolean> changed){
            super(name, def, icon, changed);
        }
    }

    static final class ActionButtonSetting extends RbmStyle.ActionButtonSetting{
        ActionButtonSetting(String buttonText, Drawable icon, Runnable action){
            super("spdb-action", buttonText, icon, action);
        }
    }
}
