package hidewhatprocessorsshow;


import mindustry.ui.dialogs.SettingsMenuDialog;
import mindustry.mod.Mod;

public class HideWhatProcessorsShowMod extends Mod {
    /** When true, this mod is running as a bundled component inside Neon. */
    public static boolean bekBundled = false;


    public HideWhatProcessorsShowMod() {
        ProcessorVisualController.registerKeybinds();
    }

    @Override
    public void init() {
        ProcessorVisualController.init();
    }

    /** This bundled module has no configurable settings. */
    public void bekBuildSettings(SettingsMenuDialog.SettingsTable table){
    }
}
