package modupdater.features;

import arc.Core;
import arc.files.Fi;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.TextButton;
import arc.scene.ui.TextField;
import arc.scene.ui.layout.Table;
import arc.util.Align;
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
import mindustry.ui.dialogs.SettingsMenuDialog;

import java.io.InputStream;
import java.io.OutputStream;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;

/**
 * Neon's single-mod updater. The layout intentionally follows MindustryX's
 * AutoUpdate dialog: release selection first, download URL second, recent
 * updates last.
 */
public final class ModUpdateCenter{
    private static final String neonRepo = "DeterMination-Wind/Neon";
    private static final String neonName = "Neon";
    private static final float maxContentWidth = 500f;

    private static final String keyEnabled = "mu-enabled";
    private static final String keyShowDialog = "mu-show-dialog";
    private static final String keyUseMirror = "mu-use-mirror";
    private static final String keyIntervalHours = "mu-check-interval-hours";
    private static final String keyLastCheckAt = "mu-last-check-at";
    private static final String keyIgnoreOnce = "mu-ignore-once";
    private static final String keyIgnoreUntil = "mu-ignore-until";

    private static boolean startupChecked;
    private static boolean checking;
    private static boolean checkFailed;
    private static boolean manualDialogRequested;

    private static Mods.LoadedMod neonMod;
    private static String currentVersion = "";
    private static ReleaseState releaseState = new ReleaseState();
    private static BaseDialog activeDialog;

    private ModUpdateCenter(){
    }

    public static void init(){
        applyDefaults();
    }

    public static void applyDefaults(){
        Core.settings.defaults(keyEnabled, true);
        Core.settings.defaults(keyShowDialog, true);
        Core.settings.defaults(keyUseMirror, false);
        Core.settings.defaults(keyIntervalHours, 6);
        Core.settings.defaults(keyIgnoreOnce, "");
        Core.settings.defaults(keyIgnoreUntil, "");
    }

    public static void buildSettings(SettingsMenuDialog.SettingsTable table){
        applyDefaults();
        table.checkPref(keyEnabled, true);
        table.checkPref(keyShowDialog, true);
        table.checkPref(keyUseMirror, false);
        table.sliderPref(keyIntervalHours, 6, 1, 48, 1, value -> (int)value + "h");
        table.pref(new ButtonSetting("mu-open-center", () -> showCenter(true)));
    }

    public static void checkOnceAtStartup(){
        if(startupChecked) return;
        startupChecked = true;

        if(Vars.headless || Vars.mods == null) return;
        applyDefaults();
        if(!Core.settings.getBool(keyEnabled, true)) return;

        long now = System.currentTimeMillis();
        long last = Core.settings.getLong(keyLastCheckAt, 0L);
        long hours = Math.max(1, Core.settings.getInt(keyIntervalHours, 6));
        long interval = hours * 60L * 60L * 1000L;
        if(last > 0L && now - last < interval) return;
        Core.settings.put(keyLastCheckAt, now);

        runCheck(true);
    }

    /** Opens the MindustryX-style updater dialog. */
    public static void showCenter(boolean refresh){
        if(Vars.headless || Vars.mods == null) return;

        manualDialogRequested = true;
        if(refresh){
            releaseState = new ReleaseState();
            checkFailed = false;
        }

        if(refresh || releaseState.releases.isEmpty()){
            if(checking){
                showDialog(null);
            }else{
                runCheck(false);
            }
        }else{
            manualDialogRequested = false;
            showDialog(releaseState.latest);
        }
    }

    private static void runCheck(boolean startupPrompt){
        if(Vars.headless || Vars.mods == null || checking) return;

        neonMod = findNeonMod();
        if(neonMod == null || neonMod.meta == null){
            checkFailed = true;
            if(manualDialogRequested){
                manualDialogRequested = false;
                showDialog(null);
            }
            return;
        }

        currentVersion = readCurrentVersion(neonMod);
        checking = true;
        checkFailed = false;
        if(manualDialogRequested) showDialog(null);

        GithubReleaseClient.fetchReleases(neonRepo, releases -> Core.app.post(() -> {
            releaseState = new ReleaseState(releases);
            releaseState.latest = pickLatestForCurrentVersion(currentVersion, releaseState.releases);
            checking = false;
            checkFailed = releaseState.releases.isEmpty();
            finishCheck(startupPrompt);
        }), error -> Core.app.post(() -> {
            Log.warn("Failed to fetch Neon releases: " + error);
            releaseState = new ReleaseState();
            checking = false;
            checkFailed = true;
            finishCheck(startupPrompt);
        }));
    }

    private static void finishCheck(boolean startupPrompt){
        if(manualDialogRequested){
            manualDialogRequested = false;
            showDialog(releaseState.latest);
            return;
        }

        if(startupPrompt && shouldShowStartupDialog()){
            showDialog(releaseState.latest);
        }
    }

    private static boolean shouldShowStartupDialog(){
        return Core.settings.getBool(keyShowDialog, true) && newVersion() != null;
    }

    private static GithubReleaseClient.ReleaseInfo newVersion(){
        if(releaseState.latest == null || currentVersion.isEmpty()) return null;
        if(VersionUtil.compareVersions(releaseState.latest.version, currentVersion) <= 0) return null;

        String ignored = Strings.stripColors(Core.settings.getString(keyIgnoreOnce, ""));
        if(ignored != null && !ignored.isEmpty() && VersionUtil.compareVersions(releaseState.latest.version, ignored) == 0){
            return null;
        }

        String ignoreUntil = Strings.stripColors(Core.settings.getString(keyIgnoreUntil, ""));
        if(ignoreUntil != null && !ignoreUntil.isEmpty()){
            try{
                if(Instant.parse(ignoreUntil).isAfter(Instant.now())) return null;
            }catch(Throwable ignoredError){
                // A malformed setting should not disable update checks forever.
            }
        }
        return releaseState.latest;
    }

    private static void showDialog(GithubReleaseClient.ReleaseInfo requestedVersion){
        if(Vars.ui == null) return;
        if(activeDialog != null && activeDialog.isShown()) activeDialog.hide();

        final BaseDialog dialog = new BaseDialog(Core.bundle.get("mu.dialog.title"));
        activeDialog = dialog;
        dialog.cont.margin(12f);
        float width = contentWidth();

        Table content = new Table();
        content.left().defaults().left();
        ScrollPane pane = new ScrollPane(content);
        pane.setFadeScrollBars(false);
        pane.setScrollingDisabledX(true);
        dialog.cont.add(pane).width(width).maxHeight(Core.graphics.getHeight() * 0.8f).growY().row();

        String current = currentVersion.isEmpty() ? Core.bundle.get("mu.version.unknown") : currentVersion;
        content.add(Core.bundle.format("mu.current.version", current)).labelAlign(Align.center).width(width).row();

        GithubReleaseClient.ReleaseInfo update = newVersion();
        if(update != null){
            content.add(Core.bundle.format("mu.new.version", update.version)).row();
        }

        if(checking && releaseState.releases.isEmpty()){
            content.add(Core.bundle.get("mu.checking")).row();
        }else if(releaseState.releases.isEmpty()){
            content.add(checkFailed ? Core.bundle.get("mu.check.failed") : Core.bundle.get("mu.latest")).row();
        }else{
            content.image().color(Pal.gray).fillX().height(2f).row();
            content.add(Core.bundle.get("mu.release.stable")).row();
            buildReleaseList(content, releaseState.releases, false, requestedVersion, dialog);

            content.image().color(Pal.gray).fillX().height(2f).row();
            content.add(Core.bundle.get("mu.release.prerelease")).row();
            buildReleaseList(content, releaseState.releases, true, requestedVersion, dialog);

            content.image().color(Pal.gray).fillX().height(2f).row();

            GithubReleaseClient.ReleaseInfo selected = requestedVersion == null ? releaseState.latest : requestedVersion;
            if(selected == null){
                content.add(Core.bundle.get("mu.latest")).row();
            }else{
                buildDownloadControls(content, dialog, selected, width);
            }
        }

        dialog.cont.row();
        dialog.cont.add(new RecentUpdatesTable(neonRepo))
            .height(Core.graphics.getHeight() * 0.3f)
            .width(width);
        dialog.addCloseButton();
        dialog.hidden(() -> {
            if(activeDialog == dialog) activeDialog = null;
            manualDialogRequested = false;
        });
        dialog.show();
    }

    private static void buildReleaseList(Table content, ArrayList<GithubReleaseClient.ReleaseInfo> releases,
                                         boolean preRelease, GithubReleaseClient.ReleaseInfo requestedVersion, BaseDialog parent){
        boolean found = false;
        GithubReleaseClient.ReleaseInfo selected = requestedVersion == null ? releaseState.latest : requestedVersion;

        for(GithubReleaseClient.ReleaseInfo release : releases){
            if(release == null || release.preRelease != preRelease) continue;
            found = true;

            final GithubReleaseClient.ReleaseInfo version = release;
            content.table(row -> {
                row.left().defaults().left();
                row.check(version.version, version == selected, checked -> {
                    if(!checked) return;
                    parent.hide();
                    showDialog(version);
                }).left().growX();

                if(!version.body.isEmpty()){
                    row.button(Icon.infoSmall, Styles.clearNonei, () -> showReleaseNotesDialog(version))
                        .size(32f)
                        .padRight(2f)
                        .tooltip(Core.bundle.get("mu.tooltip.release-notes"));
                }
                row.button(Icon.link, Styles.clearNonei, () -> openURI(version.htmlUrl))
                    .size(32f)
                    .tooltip(Core.bundle.get("mu.tooltip.release-page"));
            }).growX().left().row();
        }

        if(!found) content.add(Core.bundle.get("mu.release.none")).padBottom(4f).row();
    }

    private static void buildDownloadControls(Table content, BaseDialog parent, GithubReleaseClient.ReleaseInfo selected, float width){
        final GithubReleaseClient.AssetInfo asset = GithubReleaseClient.pickDefaultAsset(selected.assets);
        final boolean[] useMirror = {Core.settings.getBool(keyUseMirror, false)};
        final String[] url = {asset == null ? "" : downloadUrl(asset.url, useMirror[0])};
        final TextField[] field = {null};

        content.table(row -> {
            field[0] = row.field(url[0], value -> url[0] = value).minWidth(0f).growX().get();
            row.button(Icon.link, Styles.clearNonei, () -> openURI(url[0]))
                .width(50f)
                .tooltip(Core.bundle.get("mu.tooltip.open-url"));
        }).growX().fillX().row();

        content.check(Core.bundle.get("mu.mirror"), useMirror[0], checked -> {
            useMirror[0] = checked;
            Core.settings.put(keyUseMirror, checked);
            if(asset != null){
                url[0] = downloadUrl(asset.url, checked);
                field[0].setText(url[0]);
            }
        }).left().padTop(6f).row();

        if(asset == null){
            content.add(Core.bundle.get("mu.download.noasset")).wrap().width(width).padTop(8f).row();
        }

        content.button(Core.bundle.get("mu.action.download"), () -> {
            if(asset == null || url[0].trim().isEmpty()) return;
            parent.hide();
            startDownload(asset, url[0]);
        }).fillX().row();

        if(selected == newVersion()){
            content.table(actions -> {
                actions.button(Core.bundle.get("mu.action.ignore-once"), () -> {
                    Core.settings.put(keyIgnoreOnce, selected.version);
                    parent.hide();
                }).growX();
                actions.button(Core.bundle.get("mu.action.ignore-seven-days"), () -> {
                    Core.settings.put(keyIgnoreUntil, Instant.now().plus(7, ChronoUnit.DAYS).toString());
                    parent.hide();
                }).growX();
            }).fillX().row();
        }
    }

    private static void showReleaseNotesDialog(GithubReleaseClient.ReleaseInfo release){
        BaseDialog dialog = new BaseDialog(Core.bundle.get("mu.dialog.title") + " - " + release.version);
        dialog.cont.margin(12f);
        float width = contentWidth();
        if(release.body.isEmpty()){
            dialog.cont.add(Core.bundle.get("mu.release.notes.empty")).wrap().width(width).row();
        }else{
            dialog.cont.pane(p -> p.add(release.body).wrap().growX().left())
                .height(Math.min(Core.graphics.getHeight() * 0.6f, 420f))
                .width(width)
                .row();
        }
        dialog.addCloseButton();
        dialog.show();
    }

    private static void startDownload(GithubReleaseClient.AssetInfo asset, String url){
        if(Vars.ui == null) return;

        Fi directory = Vars.tmpDirectory.child("modupdater-update");
        directory.mkdirs();
        String fileName = sanitizeFileName(asset.name.isEmpty() ? "Neon-update.zip" : asset.name);
        for(Fi file : directory.list()){
            if(!file.name().equals(fileName)) file.delete();
        }

        Fi file = directory.child(fileName);
        final float[] progress = {0f};
        final float[] lengthMb = {0f};
        final boolean[] canceled = {false};

        BaseDialog progressDialog = new BaseDialog(Core.bundle.get("mu.update.progress.title"));
        progressDialog.cont.add(new Bar(() -> {
            if(lengthMb[0] <= 0f) return Core.bundle.get("mu.update.progress.unknown");
            return Strings.autoFixed(progress[0] * lengthMb[0], 2) + "/" + Strings.autoFixed(lengthMb[0], 2) + " MB";
        }, () -> Pal.accent, () -> progress[0])).width(400f).height(70f);
        progressDialog.buttons.button("@cancel", Icon.cancel, () -> {
            canceled[0] = true;
            progressDialog.hide();
        }).size(210f, 64f);
        progressDialog.setFillParent(false);
        progressDialog.show();

        // resolve the mirror host to an IPv4 right before the request; a no-op for non-mirror hosts (see PlayMirrorResolver)
        Http.get(PlayMirrorResolver.resolveHost(url))
            .timeout(30000)
            .header("User-Agent", "Mindustry")
            .error(error -> {
                progressDialog.hide();
                if(Vars.ui != null) Vars.ui.showException(error);
            })
            .submit(response -> {
                long total = response.getContentLength();
                lengthMb[0] = total > 0 ? total / 1024f / 1024f : 0f;

                if(total > 0 && file.exists() && file.length() == total){
                    progressDialog.hide();
                    Core.app.post(() -> installAndRestart(file));
                    return;
                }

                int buffer = 1024 * 1024;
                long read = 0L;
                try(InputStream input = response.getResultAsStream(); OutputStream output = file.write(false, buffer)){
                    byte[] bytes = new byte[buffer];
                    int count;
                    while((count = input.read(bytes)) != -1){
                        if(canceled[0]) break;
                        output.write(bytes, 0, count);
                        read += count;
                        if(total > 0) progress[0] = Math.min(1f, read / (float)total);
                    }
                }catch(Throwable error){
                    file.delete();
                    progressDialog.hide();
                    Core.app.post(() -> {
                        if(Vars.ui != null) Vars.ui.showException(error);
                    });
                    return;
                }

                if(canceled[0]){
                    file.delete();
                    return;
                }

                progress[0] = 1f;
                progressDialog.hide();
                Core.app.post(() -> installAndRestart(file));
            });
    }

    private static void installAndRestart(Fi file){
        try{
            Vars.mods.importMod(file);
            file.delete();
            if(OS.isAndroid || Vars.mobile){
                Vars.ui.showInfoToast(Core.bundle.get("mu.update.installed"), 5f);
                Vars.ui.mods.show();
                return;
            }

            Vars.ui.showInfoToast(Core.bundle.get("mu.update.restarting"), 4f);
            restartApp();
        }catch(Throwable error){
            if(Vars.ui != null) Vars.ui.showException(error);
        }
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
        }catch(Throwable error){
            Log.err("Failed to restart Mindustry.", error);
        }
        Core.app.exit();
    }

    private static Mods.LoadedMod findNeonMod(){
        Mods.LoadedMod mod = Vars.mods.getMod(neonName);
        if(mod != null) return mod;
        mod = Vars.mods.getMod("Neon-dev");
        if(mod != null) return mod;

        for(Mods.LoadedMod candidate : Vars.mods.list()){
            if(candidate != null && candidate.meta != null && neonRepo.equalsIgnoreCase(candidate.meta.repo)){
                return candidate;
            }
        }
        return null;
    }

    private static String readCurrentVersion(Mods.LoadedMod mod){
        if(mod == null || mod.meta == null) return "";
        String value = Strings.stripColors(mod.meta.version == null ? "" : mod.meta.version);
        return VersionUtil.normalizeVersion(value);
    }

    private static GithubReleaseClient.ReleaseInfo pickLatestForCurrentVersion(String current, ArrayList<GithubReleaseClient.ReleaseInfo> releases){
        if(releases == null || releases.isEmpty()) return null;

        String normalized = VersionUtil.normalizeVersion(current);
        int code = VersionUtil.versionCode(normalized);
        boolean preview = normalized.regionMatches(true, 0, "B", 0, 1) || (code >= 0 && code % 10000 != 0);
        GithubReleaseClient.ReleaseInfo best = null;
        for(GithubReleaseClient.ReleaseInfo release : releases){
            if(release == null || release.version.isEmpty()) continue;
            if(release.preRelease != preview) continue;
            if(best == null || VersionUtil.compareVersions(release.version, best.version) > 0){
                best = release;
            }
        }
        return best == null ? GithubReleaseClient.pickLatestRelease(releases) : best;
    }

    private static String downloadUrl(String original, boolean useMirror){
        return GithubReleaseClient.buildDownloadUrl(original, useMirror);
    }

    private static float contentWidth(){
        if(Core.scene == null || Core.scene.getWidth() <= 0f) return maxContentWidth;
        return Math.min(maxContentWidth, Core.scene.getWidth() * 0.84f);
    }

    private static void openURI(String url){
        if(url != null && !url.trim().isEmpty()) Core.app.openURI(url.trim());
    }

    private static String sanitizeFileName(String name){
        String value = Strings.stripColors(name == null ? "" : name).trim();
        if(value.isEmpty()) return "Neon-update.zip";
        return value.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private static final class ReleaseState{
        final ArrayList<GithubReleaseClient.ReleaseInfo> releases;
        GithubReleaseClient.ReleaseInfo latest;

        ReleaseState(){
            this(new ArrayList<GithubReleaseClient.ReleaseInfo>());
        }

        ReleaseState(ArrayList<GithubReleaseClient.ReleaseInfo> releases){
            this.releases = releases == null ? new ArrayList<GithubReleaseClient.ReleaseInfo>() : releases;
        }
    }

    private static final class CommitInfo{
        final String message;
        final String author;
        final String date;
        final String url;

        CommitInfo(String message, String author, String date, String url){
            this.message = message == null ? "" : message;
            this.author = author == null || author.isEmpty() ? "???" : author;
            this.date = date == null ? "" : date;
            this.url = url == null ? "" : url;
        }
    }

    /** Lightweight version of MindustryX's CommitsTable, kept inside the updater. */
    private static final class RecentUpdatesTable extends Table{
        private static final ArrayList<CommitInfo> cached = new ArrayList<CommitInfo>();
        private static boolean requested;
        private static boolean loading;
        private static String error = "";

        private final String repo;
        private final Table rows = new Table();

        RecentUpdatesTable(String repo){
            this.repo = repo;
            defaults().left();
            table(top -> {
                top.defaults().left();
                top.add(this.repo).style(Styles.outlineLabel).pad(4f);
                top.add(Core.bundle.get("mu.recent.title")).color(Pal.lightishGray);
            }).padBottom(16f).padTop(8f).growX();
            row();
            pane(Styles.noBarPane, table -> table.add(rows).minHeight(200f).grow()).grow();

            if(!requested) request();
            rebuild();
        }

        private void request(){
            requested = true;
            loading = true;
            Http.get("https://api.github.com/repos/" + repo + "/commits?per_page=20")
                .timeout(30000)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "Mindustry")
                .error(failure -> Core.app.post(() -> {
                    loading = false;
                    error = failure == null ? "" : failure.toString();
                    rebuild();
                }))
                .submit(response -> {
                    ArrayList<CommitInfo> parsed = parseCommits(response.getResultAsString());
                    Core.app.post(() -> {
                        loading = false;
                        cached.clear();
                        cached.addAll(parsed);
                        rebuild();
                    });
                });
        }

        private void rebuild(){
            rows.clearChildren();
            if(loading){
                rows.add(Core.bundle.get("mu.recent.loading")).style(Styles.outlineLabel).expand().center();
                return;
            }
            if(!error.isEmpty()){
                rows.add(Core.bundle.get("mu.recent.failed")).style(Styles.outlineLabel).expand().center();
                return;
            }
            if(cached.isEmpty()){
                rows.add(Core.bundle.get("mu.recent.empty")).style(Styles.outlineLabel).expand().center();
                return;
            }

            rows.image().color(Pal.accent).width(1.5f).growY();
            Table right = rows.table().growX().get();
            String lastDate = "";
            for(CommitInfo commit : cached){
                String date = commit.date.length() >= 10 ? commit.date.substring(0, 10) : commit.date;
                if(!date.equals(lastDate)){
                    right.table(split -> {
                        split.image().color(Pal.accent).width(8f).height(1.5f);
                        split.add(date).color(Pal.accent).padLeft(8f).padRight(8f);
                        split.image().color(Pal.accent).height(1.5f).padRight(8f).growX();
                    }).padTop(lastDate.isEmpty() ? 0f : 16f).padBottom(8f).growX();
                    right.row();
                    lastDate = date;
                }

                final CommitInfo info = commit;
                right.table(commitRow -> {
                    String[] lines = info.message.split("\\n");
                    String firstLine = lines.length == 0 ? "" : lines[0] + (lines.length > 1 ? "..." : "");
                    commitRow.table(left -> {
                        left.defaults().left();
                        left.add(firstLine).style(Styles.outlineLabel).minWidth(0f).wrap().growX().left();
                        left.row();
                        left.add(info.author).style(Styles.outlineLabel).color(Pal.lightishGray);
                    }).growX();
                    commitRow.add().growX();
                    commitRow.button(Icon.link, Styles.cleari, () -> openURI(info.url))
                        .size(38f)
                        .tooltip(Core.bundle.get("mu.tooltip.commit"));
                }).padLeft(16f).growX();
                right.row();
            }
        }

        private static ArrayList<CommitInfo> parseCommits(String raw){
            ArrayList<CommitInfo> result = new ArrayList<CommitInfo>();
            try{
                Jval root = Jval.read(raw);
                if(root == null || !root.isArray()) return result;
                for(Jval item : root.asArray()){
                    if(item == null || !item.isObject()) continue;
                    Jval commit = item.get("commit");
                    if(commit == null) continue;
                    Jval author = commit.get("author");
                    String message = commit.getString("message", "");
                    String authorName = author == null ? "" : author.getString("name", "");
                    String date = author == null ? "" : author.getString("date", "");
                    String url = item.getString("html_url", "");
                    if(!message.isEmpty()) result.add(new CommitInfo(message, authorName, date, url));
                }
            }catch(Throwable ignored){
            }
            return result;
        }
    }

    private static final class ButtonSetting extends SettingsMenuDialog.SettingsTable.Setting{
        private final Runnable action;

        ButtonSetting(String name, Runnable action){
            super(name);
            this.action = action;
        }

        @Override
        public void add(SettingsMenuDialog.SettingsTable table){
            TextButton button = table.button(title, action).growX().margin(14f).pad(6f).center().get();
            button.getLabel().setWrap(true);
        }
    }
}
