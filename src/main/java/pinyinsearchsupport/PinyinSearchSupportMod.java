package pinyinsearchsupport;

import arc.Events;
import arc.util.Timer;
import mindustry.game.EventType.ClientLoadEvent;
import mindustry.gen.Icon;
import mindustry.mod.Mod;
import mindustry.ui.dialogs.SettingsMenuDialog;
import pinyinsearchsupport.ui.FieldDispatcher;

import static mindustry.Vars.headless;
import static mindustry.Vars.ui;

public class PinyinSearchSupportMod extends Mod{
    /** When true, this mod is running as a bundled component inside Neon. */
    public static boolean bekBundled = false;


    public static final String keyEnabled   = "pss-enabled";
    public static final String keyFuzzy     = "pss-fuzzy";
    public static final String keyInitials  = "pss-initials";
    public static final String keyHeteronym = "pss-heteronym";
    public static final String keyDelayMs   = "pss-delay-ms";

    public static final int defaultDelayMs = 120;

    private final FieldDispatcher dispatcher = new FieldDispatcher();

    public PinyinSearchSupportMod(){
        Events.on(ClientLoadEvent.class, e -> {
            if(headless) return;

            GithubUpdateCheck.applyDefaults();
            registerSettings();

            dispatcher.scan();
            Timer.schedule(dispatcher::scan, 0.25f, 0.5f);

            GithubUpdateCheck.checkOnce();
        });
    }

    private void registerSettings(){
        if(ui == null || ui.settings == null) return;
        if(bekBundled) return;

        if(!bekBundled) ui.settings.addCategory("@pss.category", Icon.zoom, this::bekBuildSettings);
    }
    /** Populates a {@link mindustry.ui.dialogs.SettingsMenuDialog.SettingsTable} with this mod's settings. */
    public void bekBuildSettings(SettingsMenuDialog.SettingsTable table){
            SettingsMenuDialog.SettingsTable st = (SettingsMenuDialog.SettingsTable)table;
            st.checkPref(keyEnabled, true);
            st.checkPref(keyFuzzy, true);
            st.checkPref(keyInitials, true);
            st.checkPref(keyHeteronym, true);
            st.sliderPref(keyDelayMs, defaultDelayMs, 0, 1500, 10, value -> value + " ms");
            st.checkPref(GithubUpdateCheck.enabledKey(), true);
            st.checkPref(GithubUpdateCheck.showDialogKey(), true);
        
    }

}
