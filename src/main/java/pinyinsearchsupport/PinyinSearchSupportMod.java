package pinyinsearchsupport;

import arc.Events;
import arc.util.Log;
import arc.util.Timer;
import mindustry.game.EventType.ClientLoadEvent;
import mindustry.gen.Icon;
import mindustry.mod.Mod;
import mindustry.ui.dialogs.SettingsMenuDialog;
import pinyinsearchsupport.ui.FieldDispatcher;

import java.util.concurrent.atomic.AtomicBoolean;

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
    private final AtomicBoolean clientLoaded = new AtomicBoolean(false);
    private Timer.Task scanTask;

    public PinyinSearchSupportMod(){
        Events.on(ClientLoadEvent.class, e -> {
            if(headless) return;
            if(!clientLoaded.compareAndSet(false, true)) return;

            try{
                if(!bekBundled) GithubUpdateCheck.applyDefaults();
                registerSettings();

                safeScan();
                scanTask = Timer.schedule(this::safeScan, 0.25f, 0.5f);

                if(!bekBundled) GithubUpdateCheck.checkOnce();
            }catch(Throwable t){
                Log.warn("[PinyinSearchSupport] ClientLoadEvent init failed: @", t.getMessage());
            }
        });
    }

    private void safeScan(){
        try{
            dispatcher.scan();
        }catch(Throwable t){
            Log.warn("[PinyinSearchSupport] scan tick failed: @", t.getMessage());
        }
    }

    private void registerSettings(){
        if(ui == null || ui.settings == null) return;
        if(bekBundled) return;

        ui.settings.addCategory("@pss.category", Icon.zoom, this::bekBuildSettings);
    }

    /** Populates a {@link mindustry.ui.dialogs.SettingsMenuDialog.SettingsTable} with this mod's settings. */
    public void bekBuildSettings(SettingsMenuDialog.SettingsTable table){
        if(table == null) return;
        try{
            table.checkPref(keyEnabled, true);
            table.checkPref(keyFuzzy, true);
            table.checkPref(keyInitials, true);
            table.checkPref(keyHeteronym, true);
            table.sliderPref(keyDelayMs, defaultDelayMs, 0, 1500, 10, value -> value + " ms");
            if(!bekBundled) table.checkPref(GithubUpdateCheck.enabledKey(), true);
            if(!bekBundled) table.checkPref(GithubUpdateCheck.showDialogKey(), true);
        }catch(Throwable t){
            Log.warn("[PinyinSearchSupport] bekBuildSettings failed: @", t.getMessage());
        }
    }
}
