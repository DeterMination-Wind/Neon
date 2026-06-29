package pinyinsearchsupport;

import arc.Events;
import arc.util.Log;
import arc.util.Timer;
import mindustry.game.EventType.ClientLoadEvent;
import mindustry.gen.Icon;
import mindustry.mod.Mod;
import mindustry.mod.Mods;
import mindustry.ui.dialogs.SettingsMenuDialog;
import pinyinsearchsupport.ui.FieldDispatcher;

import static mindustry.Vars.headless;
import static mindustry.Vars.ui;
import static mindustry.Vars.mods;

public class PinyinSearchSupportMod extends Mod{
    /** When true, this mod is running as a bundled component inside Neon. */
    public static boolean bekBundled = false;
    private static final String scannerOwnerProperty = "neon.pss.scanner-owner";
    private static final String duplicateLogProperty = "neon.pss.duplicate-log";


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

            String owner = scannerOwnerTag();
            String existing = claimScannerOwnership(owner);
            if(existing != null){
                logDuplicateScanner(owner, existing);
                return;
            }

            if(!bekBundled) GithubUpdateCheck.applyDefaults();
            registerSettings();

            dispatcher.scan();
            Timer.schedule(dispatcher::scan, 0.25f, 0.5f);

            if(!bekBundled) GithubUpdateCheck.checkOnce();
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
            if(!bekBundled) st.checkPref(GithubUpdateCheck.enabledKey(), true);
            if(!bekBundled) st.checkPref(GithubUpdateCheck.showDialogKey(), true);
        
    }

    private String scannerOwnerTag(){
        String source = bekBundled ? "Neon" : "PinyinSearchSupport";
        return source + "@" + Integer.toHexString(System.identityHashCode(getClass().getClassLoader()));
    }

    private static String claimScannerOwnership(String owner){
        synchronized(System.getProperties()){
            String existing = System.getProperty(scannerOwnerProperty);
            if(existing == null || existing.isEmpty()){
                System.setProperty(scannerOwnerProperty, owner);
                return null;
            }
            return existing;
        }
    }

    private void logDuplicateScanner(String owner, String existing){
        synchronized(System.getProperties()){
            String marker = existing + " -> " + owner;
            if(marker.equals(System.getProperty(duplicateLogProperty))) return;
            System.setProperty(duplicateLogProperty, marker);
        }

        Log.warn("[PinyinSearchSupport] duplicate scanner detected; active=@, skipped=@. Search patching is disabled for this instance.", existing, owner);
        Log.warn("[PinyinSearchSupport] remove old Neon/PinyinSearchSupport jars from the mods folder, then restart the game.");
        Log.warn("[PinyinSearchSupport] related loaded mods: @", relatedModsSnapshot());
    }

    private static String relatedModsSnapshot(){
        if(mods == null) return "<Vars.mods unavailable>";

        StringBuilder out = new StringBuilder();
        for(Mods.LoadedMod mod : mods.list()){
            if(mod == null || mod.meta == null) continue;

            String internal = mod.name == null ? "" : mod.name;
            String display = mod.meta.displayName == null ? "" : mod.meta.displayName;
            String repo = mod.meta.repo == null ? "" : mod.meta.repo;
            String main = mod.main == null ? mod.meta.main : mod.main.getClass().getName();
            String haystack = (internal + " " + display + " " + repo + " " + main).toLowerCase();
            if(!(haystack.contains("neon")
                || haystack.contains("bek-tools")
                || haystack.contains("pinyinsearchsupport")
                || haystack.contains("pinyin-search-support"))){
                continue;
            }

            if(out.length() > 0) out.append("; ");
            out.append(internal)
                .append("{display=").append(display)
                .append(", version=").append(mod.meta.version)
                .append(", repo=").append(repo)
                .append(", enabled=").append(mod.enabled())
                .append(", main=").append(main)
                .append("}");
        }
        return out.length() == 0 ? "<none>" : out.toString();
    }

}
