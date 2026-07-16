package random;

import arc.Core;
import mindustry.ui.dialogs.SettingsMenuDialog;

/** Settings for the client-only Random presentation layer. */
public final class RandomSettings{
    public static final String TEXT = "random-text";
    public static final String TEXTURE = "random-texture";
    public static final String CLIENT_SEED = "random-client-seed";

    private RandomSettings(){
    }

    public static void registerDefaults(){
        if(Core.settings == null) return;
        Core.settings.defaults(TEXT, false, TEXTURE, false, CLIENT_SEED, "");
    }

    public static void build(SettingsMenuDialog.SettingsTable table){
        table.checkPref(TEXT, false);
        table.checkPref(TEXTURE, false);
    }

    public static boolean textEnabled(){
        return Core.settings != null && Core.settings.getBool(TEXT, false);
    }

    public static boolean textureEnabled(){
        return Core.settings != null && Core.settings.getBool(TEXTURE, false);
    }
}
