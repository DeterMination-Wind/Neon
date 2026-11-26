package bektools.ui;

import arc.Core;
import arc.func.Cons;
import arc.scene.style.Drawable;
import mindustry.ui.dialogs.SettingsMenuDialog;

/** Immutable descriptor for a bundled sub-mod or Neon-native feature in the settings workbench. */
public final class NeonSettingsModule{
    public final String moduleId;
    public final NeonSettingsTab tab;
    public final boolean available;
    public final String title;
    public final Drawable icon;
    public final String enableKey;
    public final boolean enableDefault;
    public final boolean neonSwitch;
    public final boolean hasOwnSettings;
    public final String emptyPlaceholderKey;
    public final Cons<SettingsMenuDialog.SettingsTable> builder;
    public final Cons<Boolean> onSwitchChanged;

    public NeonSettingsModule(
        String moduleId,
        NeonSettingsTab tab,
        boolean available,
        String title,
        Drawable icon,
        String enableKey,
        boolean enableDefault,
        boolean neonSwitch,
        boolean hasOwnSettings,
        String emptyPlaceholderKey,
        Cons<SettingsMenuDialog.SettingsTable> builder,
        Cons<Boolean> onSwitchChanged
    ){
        this.moduleId = moduleId;
        this.tab = tab;
        this.available = available;
        this.title = title;
        this.icon = icon;
        this.enableKey = enableKey;
        this.enableDefault = enableDefault;
        this.neonSwitch = neonSwitch;
        this.hasOwnSettings = hasOwnSettings;
        this.emptyPlaceholderKey = emptyPlaceholderKey;
        this.builder = builder;
        this.onSwitchChanged = onSwitchChanged;
    }

    public boolean isSwitchOn(){
        if(enableKey == null || Core.settings == null) return true;
        return Core.settings.getBool(enableKey, enableDefault);
    }

    public void setSwitchOn(boolean value){
        if(enableKey == null || Core.settings == null) return;
        Core.settings.put(enableKey, value);
        if(onSwitchChanged != null){
            onSwitchChanged.get(value);
        }
    }

    public ModuleRuntimeState runtimeState(NeonSettingsContext context){
        boolean failed = context != null && context.isModuleFailed(moduleId);
        return ModuleRuntimeState.resolve(available, failed, isSwitchOn());
    }

    public String emptyPlaceholderText(){
        if(emptyPlaceholderKey == null || emptyPlaceholderKey.isEmpty()){
            return Core.bundle == null
                ? "This module has no additional settings."
                : Core.bundle.get("bektools.ui.empty.default", "This module has no additional settings.");
        }
        String key = emptyPlaceholderKey.startsWith("@") ? emptyPlaceholderKey.substring(1) : emptyPlaceholderKey;
        return Core.bundle == null ? key : Core.bundle.get(key, key);
    }
}
