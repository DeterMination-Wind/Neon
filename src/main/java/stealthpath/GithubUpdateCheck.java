package stealthpath;

import arc.Core;
import arc.files.Fi;
import arc.scene.ui.ButtonGroup;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.TextButton;
import arc.scene.ui.layout.Table;
import arc.util.Http;
import arc.util.Log;
import arc.util.OS;
import arc.util.Strings;
import arc.util.serialization.Jval;
import mindustry.Vars;
import mindustry.gen.Icon;
import mindustry.graphics.Pal;
import mindustry.mod.Mods;
import mindustry.ui.Bar;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Checks this mod's version against GitHub Releases.
 * Shows a rich update dialog (release notes/assets) and can auto-download + restart.
 */
final class GithubUpdateCheck{
    private static final String owner = "DeterMination-Wind";
    private static final String repo = "StealthPath";
    private static final String modName = "stealth-path";
    private static final String desktopAssetName = "stealth-path.zip";
    private static final String androidAssetName = "stealth-path-android.jar";

    private static final String mirrorPrefix = "https://ghfile.geekertao.top/";

    private static final String keyIgnoreVersion = "sp-update-ignore-version";
    private static final String keyUpdateCheckLastAt = "sp-update-lastAt";
    private static final String keyUpdateCheckUseMirror = "sp-update-mirror";
    private static final long checkIntervalMs = 6L * 60L * 60L * 1000L; //6 hours

    private static final Pattern numberPattern = Pattern.compile("\\d+");
    private static boolean checked;

    private static final class AssetInfo{
        final String name;
        final String url;
        final long sizeBytes;
        final int downloadCount;

        AssetInfo(String name, String url, long sizeBytes, int downloadCount){
            this.name = name == null ? "" : name;
            this.url = url == null ? "" : url;
            this.sizeBytes = sizeBytes;
            this.downloadCount = downloadCount;
        }
    }

    private static final class ReleaseInfo{
        final String version;
        final String tag;
        final String name;
        final String body;
        final String htmlUrl;
        final String publishedAt;
        final boolean preRelease;
        final ArrayList<AssetInfo> assets;

        ReleaseInfo(String version, String tag, String name, String body, String htmlUrl, String publishedAt, boolean preRelease, ArrayList<AssetInfo> assets){
            this.version = version == null ? "" : version;
            this.tag = tag == null ? "" : tag;
            this.name = name == null ? "" : name;
            this.body = body == null ? "" : body;
            this.htmlUrl = htmlUrl == null ? "" : htmlUrl;
            this.publishedAt = publishedAt == null ? "" : publishedAt;
            this.preRelease = preRelease;
            this.assets = assets == null ? new ArrayList<>() : assets;
        }
    }

    private GithubUpdateCheck(){
    }

    static void checkOnce(){
        if(checked) return;
        checked = true;

        if(Vars.headless) return;
        if(Vars.mods == null) return;

        Core.settings.defaults(keyUpdateCheckUseMirror, false);

        long now = System.currentTimeMillis();
        long last = Core.settings.getLong(keyUpdateCheckLastAt, 0L);
        if(last > 0L && now - last < checkIntervalMs) return;
        Core.settings.put(keyUpdateCheckLastAt, now);

        Mods.LoadedMod mod = Vars.mods.getMod(modName);
        if(mod == null || mod.meta == null) return;
        String current = Strings.stripColors(mod.meta.version);
        if(current == null || current.isEmpty()) return;

        String ignore = Strings.stripColors(Core.settings.getString(keyIgnoreVersion, ""));
        String releasesUrl = "https://github.com/" + owner + "/" + repo + "/releases/latest";

        String apiUrl = "https://api.github.com/repos/" + owner + "/" + repo + "/releases?per_page=30";
        Http.get(apiUrl)
            .timeout(30000)
            .header("User-Agent", "Mindustry")
            .error(e -> fetchFromMainModJson(mod, current, ignore, releasesUrl))
            .submit(res -> {
                try{
                    Jval json = Jval.read(res.getResultAsString());
                    ArrayList<ReleaseInfo> releases = parseReleasesList(json);
                    if(releases.isEmpty()) return;
                    ReleaseInfo latest = pickLatestRelease(releases);
                    if(latest == null || latest.version.isEmpty()) return;
                    onLatestResolved(mod, current, latest, releases, ignore);
                }catch(Throwable t){
                    fetchFromMainModJson(mod, current, ignore, releasesUrl);
                }
            });
    }

    private static void fetchFromMainModJson(Mods.LoadedMod mod, String current, String ignore, String releasesUrl){
        String url = "https://raw.githubusercontent.com/" + owner + "/" + repo + "/main/src/main/resources/mod.json";
        Http.get(url)
            .timeout(30000)
            .error(e -> {
                //offline/etc. -> skip
            })
            .submit(res -> {
                try{
                    Jval json = Jval.read(res.getResultAsString());
                    String latest = Strings.stripColors(json.getString("version", ""));
                    if(latest == null || latest.isEmpty()) return;
                    ReleaseInfo rel = new ReleaseInfo(latest, "", "", "", releasesUrl, "", false, new ArrayList<>());
                    ArrayList<ReleaseInfo> releases = new ArrayList<>();
                    releases.add(rel);
                    onLatestResolved(mod, current, rel, releases, ignore);
                }catch(Throwable ignoredErr){
                    //bad json/etc.
                }
            });
    }

    private static void onLatestResolved(Mods.LoadedMod mod, String current, ReleaseInfo latest, ArrayList<ReleaseInfo> releases, String ignore){
        if(compareVersions(latest.version, current) <= 0) return;
        if(ignore != null && !ignore.isEmpty() && compareVersions(latest.version, ignore) <= 0) return;

        Log.info("[{0}] Update available: {1} -> {2}", mod.meta.displayName, current, latest.version);
        Core.app.post(() -> showUpdateDialog(mod, current, latest, releases, latest));
    }

    private static void showUpdateDialog(Mods.LoadedMod mod, String current, ReleaseInfo latest, ArrayList<ReleaseInfo> releases, ReleaseInfo selectedRelease){
        if(Vars.ui == null) return;

        Vars.ui.showInfoToast(mod.meta.displayName + ": " + current + " -> " + latest.version, 8f);

        BaseDialog dialog = new BaseDialog("@sp.update.title");
        dialog.cont.margin(12f);

        Table content = new Table();
        content.left().defaults().left();
        ScrollPane pane = new ScrollPane(content);
        pane.setFadeScrollBars(false);

        dialog.cont.add(pane).width(560f).maxHeight(Core.graphics.getHeight() * 0.8f).growY().row();

        content.add(Core.bundle.format("sp.update.text", mod.meta.displayName, current, latest.version)).wrap().width(520f).row();

        content.image().color(Pal.gray).fillX().height(2f).padTop(8f).padBottom(6f).row();
        content.add("正式版").padBottom(4f).row();
        buildReleaseList(content, mod, current, latest, releases, selectedRelease, false, dialog);

        content.image().color(Pal.gray).fillX().height(2f).padTop(8f).padBottom(6f).row();
        content.add("预览版").padBottom(4f).row();
        buildReleaseList(content, mod, current, latest, releases, selectedRelease, true, dialog);

        content.image().color(Pal.gray).fillX().height(2f).padTop(8f).padBottom(6f).row();

        ReleaseInfo rel = selectedRelease == null ? latest : selectedRelease;
        content.add("已选择版本： " + rel.version + (rel == latest ? "（最新）" : "")).wrap().width(520f).padBottom(2f).row();

        if(!rel.publishedAt.isEmpty()){
            content.add("发布时间： " + rel.publishedAt).wrap().width(520f).row();
        }

        if(!rel.name.isEmpty() && !rel.name.equals(rel.tag)){
            content.add("标题： " + rel.name).wrap().width(520f).row();
        }

        if(!rel.body.isEmpty()){
            content.add("更新内容：").padTop(8f).row();
            content.pane(p -> p.add(rel.body).wrap().growX().left())
                .height(Math.min(Core.graphics.getHeight() * 0.35f, 260f))
                .width(520f)
                .row();
        }

        final AssetInfo[] selected = {pickAssetForRelease(rel)};
        final boolean[] useMirror = {Core.settings.getBool(keyUpdateCheckUseMirror, false)};
        final String[] url = {selected[0] == null ? rel.htmlUrl : buildDownloadUrl(selected[0].url, useMirror[0])};
        final arc.scene.ui.TextField[] urlFieldRef = {null};

        if(!rel.assets.isEmpty()){
            content.add("下载文件：").padTop(8f).row();
            ButtonGroup<TextButton> group = new ButtonGroup<>();
            group.setMaxCheckCount(1);
            group.setMinCheckCount(1);
            group.setUncheckLast(true);

            for(AssetInfo a : rel.assets){
                String label = a.name;
                String size = formatBytes(a.sizeBytes);
                if(!size.isEmpty()) label += " (" + size + ")";
                if(a.downloadCount > 0) label += " · " + a.downloadCount + " downloads";

                TextButton b = content.button(label, Styles.togglet, () -> {
                    selected[0] = a;
                    url[0] = buildDownloadUrl(a.url, useMirror[0]);
                    if(urlFieldRef[0] != null) urlFieldRef[0].setText(url[0]);
                }).width(520f).left().get();
                content.row();
                group.add(b);
                if(selected[0] == a){
                    b.setChecked(true);
                }
            }
        }

        content.add("下载地址：").padTop(8f).row();
        arc.scene.ui.TextField urlField = content.field(url[0], v -> url[0] = v).width(520f).get();
        urlFieldRef[0] = urlField;
        content.row();

        content.check("使用镜像下载（ghfile.geekertao.top）", useMirror[0], v -> {
            useMirror[0] = v;
            Core.settings.put(keyUpdateCheckUseMirror, v);
            if(selected[0] != null){
                url[0] = buildDownloadUrl(selected[0].url, v);
                urlField.setText(url[0]);
            }else{
                url[0] = buildDownloadUrl(url[0], v);
                urlField.setText(url[0]);
            }
        }).left().padTop(6f).row();

        content.add("提示：下载完成后将自动安装并重启游戏以应用更新。建议先保存/回到主菜单。")
            .wrap().width(520f).padTop(8f).row();

        dialog.buttons.defaults().size(200f, 54f).pad(6f);
        dialog.buttons.button("@sp.update.open", Icon.link, () -> Core.app.openURI(rel.htmlUrl));
        dialog.buttons.button("下载并重启", Icon.download, () -> {
            if(selected[0] == null || url[0] == null || url[0].trim().isEmpty()){
                if(Vars.ui != null) Vars.ui.showErrorMessage(Core.bundle.get("sp.update.no-asset"));
                return;
            }
            dialog.hide();
            startDownloadAndInstall(mod, selected[0], url[0]);
        });
        dialog.buttons.button("@sp.update.ignore", Icon.cancel, () -> {
            Core.settings.put(keyIgnoreVersion, latest.version);
            dialog.hide();
        });
        dialog.buttons.button("@sp.update.later", Icon.ok, dialog::hide);

        dialog.show();
    }

    private static void buildReleaseList(Table content, Mods.LoadedMod mod, String current, ReleaseInfo latest, ArrayList<ReleaseInfo> releases, ReleaseInfo selectedRelease, boolean preRelease, BaseDialog parent){
        boolean any = false;
        for(ReleaseInfo r : releases){
            if(r == null) continue;
            if(r.preRelease != preRelease) continue;
            any = true;

            String label = r.version;
            if(r == latest) label += "（最新）";

            final ReleaseInfo rel = r;
            final String labelText = label;

            content.table(row -> {
                row.left().defaults().left();
                row.check(labelText, rel == selectedRelease, v -> {
                    parent.hide();
                    showUpdateDialog(mod, current, latest, releases, rel);
                }).growX().left();

                if(rel.body != null && !rel.body.isEmpty()){
                    row.button(Icon.infoSmall, () -> showReleaseNotesDialog(mod.meta.displayName, rel)).size(32f).padRight(4f);
                }
                row.button(Icon.link, () -> Core.app.openURI(rel.htmlUrl)).size(32f);
            }).width(520f).left().row();
        }

        if(!any){
            content.add("（无）").padBottom(4f).row();
        }
    }

    private static void showReleaseNotesDialog(String title, ReleaseInfo rel){
        BaseDialog d = new BaseDialog(title + " - " + rel.version);
        d.cont.margin(12f);
        if(rel.body == null || rel.body.isEmpty()){
            d.cont.add("（无发布说明）").wrap().width(520f).row();
        }else{
            d.cont.pane(p -> p.add(rel.body).wrap().growX().left())
                .height(Math.min(Core.graphics.getHeight() * 0.6f, 420f))
                .width(520f)
                .row();
        }
        d.addCloseButton();
        d.show();
    }

    private static void startDownloadAndInstall(Mods.LoadedMod mod, AssetInfo asset, String url){
        if(Vars.ui == null) return;
        if(asset == null || asset.name == null || asset.name.isEmpty()){
            Vars.ui.showErrorMessage(Core.bundle.get("sp.update.no-asset"));
            return;
        }
        if(url == null || url.trim().isEmpty()){
            Vars.ui.showErrorMessage(Core.bundle.get("sp.update.no-url"));
            return;
        }

        Fi dir = Vars.tmpDirectory.child("mod-update");
        dir.mkdirs();
        for(Fi f : dir.list()){
            if(!f.name().equals(asset.name)){
                try{ f.delete(); }catch(Throwable ignored){}
            }
        }

        Fi file = dir.child(asset.name);

        final float[] progress = {0f};
        final float[] lengthMb = {0f};
        final boolean[] canceled = {false};

        final float[] downloadedMb = {0f};
        final float[] speedMb = {0f};

        BaseDialog dialog = new BaseDialog("@sp.update.downloading.title");
        dialog.cont.add(new Bar(() -> {
            if(lengthMb[0] <= 0f){
                return Core.bundle.format(
                    "sp.update.downloading.unknown",
                    Strings.autoFixed(downloadedMb[0], 2),
                    Strings.autoFixed(speedMb[0], 2)
                );
            }
            return Core.bundle.format(
                "sp.update.downloading.progress",
                Strings.autoFixed(progress[0] * 100f, 1),
                Strings.autoFixed(downloadedMb[0], 2),
                Strings.autoFixed(lengthMb[0], 2),
                Strings.autoFixed(speedMb[0], 2)
            );
        }, () -> Pal.accent, () -> progress[0])).width(400f).height(70f);
        dialog.buttons.button("@cancel", Icon.cancel, () -> {
            canceled[0] = true;
            dialog.hide();
        }).size(210f, 64f);
        dialog.setFillParent(false);
        dialog.show();

        Http.get(url.trim())
            .timeout(30000)
            .header("User-Agent", "Mindustry")
            .error(e -> {
                dialog.hide();
                if(Vars.ui != null) Vars.ui.showException(e);
            })
            .submit(res -> {
                long total = res.getContentLength();
                lengthMb[0] = total > 0 ? (total / 1024f / 1024f) : 0f;

                if(total > 0 && file.exists() && file.length() == total){
                    dialog.hide();
                    Core.app.post(() -> installAndRestart(mod, asset, file));
                    return;
                }

                int buffer = 1024 * 1024;
                long read = 0L;
                long speedCheckTime = System.currentTimeMillis();
                long speedCheckBytes = 0L;
                try(InputStream in = res.getResultAsStream(); OutputStream out = file.write(false, buffer)){
                    byte[] buf = new byte[buffer];
                    int r;
                    while((r = in.read(buf)) != -1){
                        if(canceled[0]) break;
                        out.write(buf, 0, r);
                        read += r;
                        downloadedMb[0] = read / 1024f / 1024f;

                        long now = System.currentTimeMillis();
                        if(now - speedCheckTime >= 350L){
                            float dt = Math.max(0.001f, (now - speedCheckTime) / 1000f);
                            speedMb[0] = ((read - speedCheckBytes) / 1024f / 1024f) / dt;
                            speedCheckTime = now;
                            speedCheckBytes = read;
                        }

                        if(total > 0){
                            progress[0] = Math.min(1f, read / (float)total);
                        }
                    }
                }catch(Throwable t){
                    try{ file.delete(); }catch(Throwable ignored){}
                    dialog.hide();
                    Core.app.post(() -> {
                        if(Vars.ui != null) Vars.ui.showException(t);
                    });
                    return;
                }

                if(canceled[0]){
                    try{ file.delete(); }catch(Throwable ignored){}
                    return;
                }

                progress[0] = 1f;
                dialog.hide();
                Core.app.post(() -> installAndRestart(mod, asset, file));
            });
    }

    private static void installAndRestart(Mods.LoadedMod mod, AssetInfo asset, Fi file){
        try{
            Fi target = resolveInstallTarget(mod, asset);
            if(target == null) throw new RuntimeException("Invalid mod file target.");

            target.parent().mkdirs();
            Fi backup = target.sibling(target.name() + ".bak");

            if(backup.exists()){
                try{ backup.delete(); }catch(Throwable ignored){}
            }

            if(target.exists()){
                try{ target.copyTo(backup); }catch(Throwable ignored){}
                try{ target.delete(); }catch(Throwable ignored){}
            }

            file.copyTo(target);

            if(backup.exists()){
                try{ backup.delete(); }catch(Throwable ignored){}
            }

            try{ file.delete(); }catch(Throwable ignored){}
            Vars.ui.showInfoToast(mod.meta.displayName + ": " + Core.bundle.get("sp.update.installing"), 4f);
            restartApp();
        }catch(Throwable t){
            if(Vars.ui != null) Vars.ui.showException(t);
        }
    }

    private static Fi resolveInstallTarget(Mods.LoadedMod mod, AssetInfo asset){
        String fileName = sanitizeAssetName(asset == null ? null : asset.name);
        if(mod != null && mod.file != null && !mod.file.isDirectory()){
            String ext = mod.file.extension();
            if(ext != null && !ext.isEmpty()) return mod.file;
        }
        return Vars.modDirectory.child(fileName);
    }

    private static String sanitizeAssetName(String name){
        String n = name == null ? "" : name.trim();
        if(n.isEmpty()){
            return (OS.isAndroid || Vars.mobile) ? androidAssetName : desktopAssetName;
        }
        if(!n.endsWith(".zip") && !n.endsWith(".jar")){
            return (OS.isAndroid || Vars.mobile) ? androidAssetName : desktopAssetName;
        }
        return n;
    }

    private static void restartApp(){
        if(OS.isAndroid || Vars.mobile){
            Core.app.exit();
            return;
        }

        try{
            Fi jar = Fi.get(Vars.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath());
            String[] args = OS.isMac ?
                new String[]{Vars.javaPath, "-XstartOnFirstThread", "-jar", jar.absolutePath()} :
                new String[]{Vars.javaPath, "-jar", jar.absolutePath()};
            Runtime.getRuntime().exec(args);
        }catch(Throwable t){
            Log.err("Failed to restart Mindustry.", t);
        }

        Core.app.exit();
    }

    private static ArrayList<ReleaseInfo> parseReleasesList(Jval json){
        ArrayList<ReleaseInfo> out = new ArrayList<>();
        if(json == null || !json.isArray()) return out;

        String fallbackHtmlUrl = "https://github.com/" + owner + "/" + repo + "/releases";
        for(Jval r : json.asArray()){
            if(r == null || !r.isObject()) continue;
            if(r.getBool("draft", false)) continue;
            try{
                ReleaseInfo rel = parseRelease(r, fallbackHtmlUrl);
                if(rel != null && rel.version != null && !rel.version.isEmpty()){
                    out.add(rel);
                }
            }catch(Throwable ignored){
            }
        }

        Collections.sort(out, (a, b) -> {
            if(a == null && b == null) return 0;
            if(a == null) return 1;
            if(b == null) return -1;
            int c = compareVersions(b.version, a.version);
            if(c != 0) return c;
            if(a.preRelease != b.preRelease) return a.preRelease ? 1 : -1;
            return a.tag.compareToIgnoreCase(b.tag);
        });

        return out;
    }

    private static ReleaseInfo pickLatestRelease(ArrayList<ReleaseInfo> releases){
        if(releases == null || releases.isEmpty()) return null;

        ReleaseInfo bestStable = null;
        ReleaseInfo bestAny = null;
        for(ReleaseInfo r : releases){
            if(r == null) continue;
            if(r.version == null || r.version.isEmpty()) continue;

            if(bestAny == null || compareVersions(r.version, bestAny.version) > 0){
                bestAny = r;
            }

            if(!r.preRelease){
                if(bestStable == null || compareVersions(r.version, bestStable.version) > 0){
                    bestStable = r;
                }
            }
        }

        return bestStable != null ? bestStable : bestAny;
    }

    private static ReleaseInfo parseRelease(Jval json, String fallbackHtmlUrl){
        if(json == null) return new ReleaseInfo("", "", "", "", fallbackHtmlUrl, "", false, new ArrayList<>());

        String tag = Strings.stripColors(json.getString("tag_name", ""));
        String htmlUrl = Strings.stripColors(json.getString("html_url", fallbackHtmlUrl));
        if(htmlUrl == null || htmlUrl.isEmpty()) htmlUrl = fallbackHtmlUrl;

        String name = Strings.stripColors(json.getString("name", ""));
        String body = Strings.stripColors(json.getString("body", ""));
        String publishedAt = Strings.stripColors(json.getString("published_at", ""));
        boolean pre = json.getBool("prerelease", false);

        String version = normalizeVersion(tag);
        if(version.isEmpty()) version = normalizeVersion(name);

        ArrayList<AssetInfo> assets = new ArrayList<>();
        try{
            Jval arr = json.get("assets");
            if(arr != null && arr.isArray()){
                for(Jval a : arr.asArray()){
                    String aname = Strings.stripColors(a.getString("name", ""));
                    String durl = Strings.stripColors(a.getString("browser_download_url", ""));
                    long size = a.getLong("size", -1L);
                    int dl = a.getInt("download_count", 0);
                    if(aname == null) aname = "";
                    if(durl == null) durl = "";
                    if(!aname.isEmpty() && !durl.isEmpty()){
                        assets.add(new AssetInfo(aname, durl, size, dl));
                    }
                }
            }
        }catch(Throwable ignored){
        }

        Collections.sort(assets, (a, b) -> a.name.compareToIgnoreCase(b.name));
        return new ReleaseInfo(version, tag, name, body, htmlUrl, publishedAt, pre, assets);
    }

    private static String normalizeVersion(String raw){
        if(raw == null) return "";
        String v = raw.trim();
        if(v.startsWith("v") || v.startsWith("V")){
            v = v.substring(1).trim();
        }
        return v;
    }

    private static AssetInfo pickDefaultAsset(ArrayList<AssetInfo> assets){
        if(assets == null || assets.isEmpty()) return null;

        boolean android = OS.isAndroid;
        if(android){
            for(AssetInfo a : assets){
                if(a.name.toLowerCase().endsWith(".jar")) return a;
            }
        }else{
            for(AssetInfo a : assets){
                if(a.name.toLowerCase().endsWith(".zip")) return a;
            }
        }

        for(AssetInfo a : assets){
            if(a.name.toLowerCase().endsWith(".jar")) return a;
        }

        return assets.get(0);
    }

    private static AssetInfo pickAssetForRelease(ReleaseInfo rel){
        if(rel != null){
            AssetInfo picked = pickDefaultAsset(rel.assets);
            if(picked != null) return picked;
        }

        boolean android = OS.isAndroid || Vars.mobile;
        String name = android ? androidAssetName : desktopAssetName;
        String tag = rel == null ? "" : Strings.stripColors(rel.tag);

        String directUrl;
        if(tag == null || tag.trim().isEmpty()){
            directUrl = "https://github.com/" + owner + "/" + repo + "/releases/latest/download/" + name;
        }else{
            directUrl = "https://github.com/" + owner + "/" + repo + "/releases/download/" + tag.trim() + "/" + name;
        }

        return new AssetInfo(name, directUrl, -1L, 0);
    }

    private static String buildDownloadUrl(String original, boolean mirror){
        if(original == null) return "";
        String url = original.trim();
        if(url.isEmpty()) return url;
        if(mirror){
            if(url.startsWith(mirrorPrefix)) return url;
            return mirrorPrefix + url;
        }
        if(url.startsWith(mirrorPrefix)){
            return url.substring(mirrorPrefix.length());
        }
        return url;
    }

    private static String formatBytes(long bytes){
        if(bytes <= 0) return "";
        float mb = bytes / 1024f / 1024f;
        if(mb < 10f) return Strings.autoFixed(mb, 2) + "MB";
        if(mb < 100f) return Strings.autoFixed(mb, 1) + "MB";
        return (int)mb + "MB";
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
