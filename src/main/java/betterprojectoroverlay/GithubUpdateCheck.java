package betterprojectoroverlay;

import arc.Core;
import arc.util.Http;
import arc.util.Strings;
import arc.util.serialization.Jval;
import mindustry.Vars;
import mindustry.gen.Icon;
import mindustry.mod.Mods;
import mindustry.ui.dialogs.BaseDialog;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class GithubUpdateCheck {
    private static final String owner = "DeterMination-Wind";
    private static final String repo = "BetterProjectorOverlay";
    private static final String modName = "betterprojectoroverlay";

    private static final String keyUpdateCheckEnabled = "bpo-updatecheck";
    private static final String keyUpdateCheckShowDialog = "bpo-updatecheck-dialog";
    private static final String keyUpdateCheckLastAt = "bpo-updatecheck-lastAt";
    private static final String keyUpdateCheckIgnoreVersion = "bpo-updatecheck-ignore";

    private static final long checkIntervalMs = 6L * 60L * 60L * 1000L;
    private static final Pattern numberPattern = Pattern.compile("\\d+");

    private static boolean checked;

    private GithubUpdateCheck() {
    }

    public static void applyDefaults() {
        Core.settings.defaults(keyUpdateCheckEnabled, true);
        Core.settings.defaults(keyUpdateCheckShowDialog, true);
    }

    public static String enabledKey() {
        return keyUpdateCheckEnabled;
    }

    public static String showDialogKey() {
        return keyUpdateCheckShowDialog;
    }

    public static void checkOnce() {
        if (checked) return;
        checked = true;

        if (Vars.headless || Vars.mods == null) return;

        applyDefaults();
        if (!Core.settings.getBool(keyUpdateCheckEnabled, true)) return;

        long now = System.currentTimeMillis();
        long last = Core.settings.getLong(keyUpdateCheckLastAt, 0L);
        if (last > 0L && now - last < checkIntervalMs) return;
        Core.settings.put(keyUpdateCheckLastAt, now);

        Mods.LoadedMod mod = Vars.mods.getMod(modName);
        if (mod == null || mod.meta == null) return;

        String current = normalizeVersion(Strings.stripColors(mod.meta.version));
        if (current.isEmpty()) return;

        String ignored = normalizeVersion(Strings.stripColors(Core.settings.getString(keyUpdateCheckIgnoreVersion, "")));

        String apiUrl = "https://api.github.com/repos/" + owner + "/" + repo + "/releases/latest";
        Http.get(apiUrl)
            .timeout(25000)
            .header("User-Agent", "Mindustry")
            .error(err -> checkFromRawModJson(mod, current, ignored))
            .submit(res -> {
                try {
                    Jval json = Jval.read(res.getResultAsString());
                    String latest = normalizeVersion(Strings.stripColors(json.getString("tag_name", "")));
                    if (latest.isEmpty()) latest = normalizeVersion(Strings.stripColors(json.getString("name", "")));
                    if (latest.isEmpty()) {
                        checkFromRawModJson(mod, current, ignored);
                        return;
                    }

                    if (!ignored.isEmpty() && compareVersions(latest, ignored) == 0) return;
                    if (compareVersions(latest, current) <= 0) return;

                    String htmlUrl = Strings.stripColors(json.getString("html_url", ""));
                    if (htmlUrl == null || htmlUrl.isEmpty()) {
                        htmlUrl = "https://github.com/" + owner + "/" + repo + "/releases/latest";
                    }
                    notifyUpdate(mod, current, latest, htmlUrl);
                } catch (Throwable ignoredErr) {
                    checkFromRawModJson(mod, current, ignored);
                }
            });
    }

    private static void checkFromRawModJson(Mods.LoadedMod mod, String current, String ignored) {
        String url = "https://raw.githubusercontent.com/" + owner + "/" + repo + "/main/mod.json";
        Http.get(url)
            .timeout(25000)
            .error(err -> {
            })
            .submit(res -> {
                try {
                    Jval json = Jval.read(res.getResultAsString());
                    String latest = normalizeVersion(Strings.stripColors(json.getString("version", "")));
                    if (latest.isEmpty()) return;
                    if (!ignored.isEmpty() && compareVersions(latest, ignored) == 0) return;
                    if (compareVersions(latest, current) <= 0) return;

                    String releasesUrl = "https://github.com/" + owner + "/" + repo + "/releases/latest";
                    notifyUpdate(mod, current, latest, releasesUrl);
                } catch (Throwable ignoredErr) {
                }
            });
    }

    private static void notifyUpdate(Mods.LoadedMod mod, String current, String latest, String releaseUrl) {
        Core.app.post(() -> {
            if (Vars.ui == null) return;

            if (!Core.settings.getBool(keyUpdateCheckShowDialog, true)) {
                Vars.ui.showInfoToast(mod.meta.displayName + ": " + current + " -> " + latest, 8f);
                return;
            }

            BaseDialog dialog = new BaseDialog(mod.meta.displayName + " Update");
            dialog.cont.margin(12f);
            dialog.cont.add("Current: " + current + "\nLatest: " + latest)
                .left()
                .wrap()
                .width(480f)
                .row();
            dialog.cont.add("Open GitHub releases to download/install the new version.")
                .left()
                .padTop(8f)
                .wrap()
                .width(480f)
                .row();

            dialog.buttons.defaults().size(200f, 54f).pad(6f);
            dialog.buttons.button("Open Releases", Icon.link, () -> {
                Core.app.openURI(releaseUrl);
                dialog.hide();
            });
            dialog.buttons.button("Ignore", Icon.cancel, () -> {
                Core.settings.put(keyUpdateCheckIgnoreVersion, latest);
                dialog.hide();
            });
            dialog.addCloseButton();
            dialog.show();
        });
    }

    private static String normalizeVersion(String raw) {
        if (raw == null) return "";
        String out = raw.trim();
        if (out.startsWith("v") || out.startsWith("V")) out = out.substring(1).trim();
        return out;
    }

    private static int compareVersions(String a, String b) {
        int[] pa = parseVersionParts(a);
        int[] pb = parseVersionParts(b);
        int max = Math.max(pa.length, pb.length);
        for (int i = 0; i < max; i++) {
            int ai = i < pa.length ? pa[i] : 0;
            int bi = i < pb.length ? pb[i] : 0;
            if (ai != bi) return Integer.compare(ai, bi);
        }
        return 0;
    }

    private static int[] parseVersionParts(String v) {
        if (v == null) return new int[0];
        Matcher m = numberPattern.matcher(v);
        ArrayList<Integer> parts = new ArrayList<>();
        while (m.find()) {
            try {
                parts.add(Integer.parseInt(m.group()));
            } catch (Throwable ignored) {
            }
        }
        int[] out = new int[parts.size()];
        for (int i = 0; i < parts.size(); i++) out[i] = parts.get(i);
        return out;
    }
}
