package bektools;

import arc.Core;
import arc.util.Http;
import arc.util.Log;
import arc.util.Strings;
import arc.util.serialization.Jval;
import mindustry.Vars;
import mindustry.mod.Mods;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Checks this mod's version against the version declared in the GitHub repository.
 * Runs asynchronously and should be triggered once per game startup.
 */
final class GithubUpdateCheck{
    private static final String owner = "DeterMination-Wind";
    private static final String repo = "BEK-Tools";
    private static final String modName = "bek-tools";

    private static final Pattern numberPattern = Pattern.compile("\\d+");
    private static boolean checked;

    private GithubUpdateCheck(){
    }

    static void checkOnce(){
        if(checked) return;
        checked = true;

        if(Vars.headless) return;
        if(Vars.mods == null) return;

        Mods.LoadedMod mod = Vars.mods.getMod(modName);
        if(mod == null || mod.meta == null) return;
        String current = Strings.stripColors(mod.meta.version);
        if(current == null || current.isEmpty()) return;

        String url = "https://raw.githubusercontent.com/" + owner + "/" + repo + "/main/src/main/resources/mod.json";

        Http.get(url)
        .error(e -> {
            // No internet/offline/etc. -> silently skip.
        })
        .submit(res -> {
            try{
                Jval json = Jval.read(res.getResultAsString());
                String latest = Strings.stripColors(json.getString("version", ""));
                if(latest == null || latest.isEmpty()) return;

                if(compareVersions(latest, current) > 0){
                    String releasesUrl = "https://github.com/" + owner + "/" + repo + "/releases/latest";
                    Log.info("[{0}] Update available: {1} -> {2}", mod.meta.displayName, current, latest);
                    Core.app.post(() -> {
                        if(Vars.ui != null){
                            Vars.ui.showInfoToast(mod.meta.displayName + ": " + current + " -> " + latest + " (GitHub)", 8f);
                        }
                        Log.info(releasesUrl);
                    });
                }
            }catch(Throwable t){
                // Bad JSON/format/etc. -> skip.
            }
        });
    }

    private static int compareVersions(String a, String b){
        int[] pa = parseVersionParts(a);
        int[] pb = parseVersionParts(b);
        int max = Math.max(pa.length, pb.length);
        for(int i = 0; i < max; i++){
            int ai = i < pa.length ? pa[i] : 0;
            int bi = i < pb.length ? pb[i] : 0;
            if(ai != bi) return Integer.compare(ai, bi);
        }
        return 0;
    }

    private static int[] parseVersionParts(String v){
        if(v == null) return new int[0];
        Matcher m = numberPattern.matcher(v);
        ArrayList<Integer> parts = new ArrayList<>();
        while(m.find()){
            try{
                parts.add(Integer.parseInt(m.group()));
            }catch(Throwable ignored){
            }
        }
        int[] out = new int[parts.size()];
        for(int i = 0; i < parts.size(); i++) out[i] = parts.get(i);
        return out;
    }
}

