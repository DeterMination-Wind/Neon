package bektools;

import arc.Core;
import arc.util.Http;
import arc.util.Log;
import arc.util.Strings;
import arc.util.serialization.Jval;
import mindustry.Vars;
import mindustry.mod.Mods;
import mindustry.gen.Icon;
import mindustry.ui.dialogs.BaseDialog;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Checks this mod's version against the version declared in the GitHub repository.
 * Runs asynchronously and should be triggered once per game startup.
 */
final class GithubUpdateCheck{
    private static final String owner = "DeterMination-Wind";
    private static final String repo = "Neon";
    private static final String modName = "bek-tools";

    private static final String keyIgnoreVersion = "bek-update-ignore-version";

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

        String ignore = Core.settings.getString(keyIgnoreVersion, "");
        String releasesUrl = "https://github.com/" + owner + "/" + repo + "/releases/latest";

        // Prefer GitHub Releases (matches "latest release" users should install).
        String apiUrl = "https://api.github.com/repos/" + owner + "/" + repo + "/releases/latest";

        Http.get(apiUrl)
            .header("User-Agent", "Mindustry")
            .error(e -> fetchFromMainModJson(mod, current, ignore, releasesUrl))
            .submit(res -> {
                try{
                    Jval json = Jval.read(res.getResultAsString());
                    String tag = Strings.stripColors(json.getString("tag_name", ""));
                    String htmlUrl = Strings.stripColors(json.getString("html_url", releasesUrl));
                    String latest = normalizeVersion(tag);
                    if(latest == null || latest.isEmpty()) return;
                    onLatestResolved(mod, current, latest, ignore, htmlUrl);
                }catch(Throwable t){
                    fetchFromMainModJson(mod, current, ignore, releasesUrl);
                }
            });
    }

    private static void fetchFromMainModJson(Mods.LoadedMod mod, String current, String ignore, String releasesUrl){
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
                    onLatestResolved(mod, current, latest, ignore, releasesUrl);
                }catch(Throwable t){
                    // Bad JSON/format/etc. -> skip.
                }
            });
    }

    private static void onLatestResolved(Mods.LoadedMod mod, String current, String latest, String ignore, String openUrl){
        if(compareVersions(latest, current) <= 0) return;
        if(ignore != null && !ignore.isEmpty() && compareVersions(latest, ignore) <= 0) return;

        Log.info("[{0}] Update available: {1} -> {2}", mod.meta.displayName, current, latest);

        Core.app.post(() -> showUpdateDialog(mod, current, latest, openUrl));
    }

    private static void showUpdateDialog(Mods.LoadedMod mod, String current, String latest, String openUrl){
        if(Vars.ui == null) return;

        // Toast for quick visibility (like MindustryX/MI2U), plus a clickable dialog.
        Vars.ui.showInfoToast(mod.meta.displayName + ": " + current + " -> " + latest, 8f);

        BaseDialog dialog = new BaseDialog("@bektools.update.title");
        dialog.cont.pane(p -> {
            p.left().defaults().left();
            p.add(Core.bundle.format("bektools.update.text", mod.meta.displayName, current, latest)).wrap().growX();
        }).width(520f).row();

        dialog.buttons.defaults().size(180f, 54f);
        dialog.buttons.button("@bektools.update.open", Icon.link, () -> Core.app.openURI(openUrl));
        dialog.buttons.button("@bektools.update.ignore", Icon.cancel, () -> {
            Core.settings.put(keyIgnoreVersion, latest);
            dialog.hide();
        });
        dialog.buttons.button("@bektools.update.later", Icon.ok, dialog::hide);

        dialog.show();
    }

    private static String normalizeVersion(String raw){
        if(raw == null) return "";
        String v = raw.trim();
        if(v.startsWith("v") || v.startsWith("V")){
            v = v.substring(1).trim();
        }
        return v;
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
