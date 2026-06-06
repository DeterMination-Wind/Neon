package modupdater.features;

import arc.Core;
import arc.files.Fi;
import arc.graphics.Color;
import arc.scene.ui.CheckBox;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.TextButton;
import arc.scene.ui.TextField;
import arc.scene.ui.layout.Table;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import arc.util.Log;
import arc.util.OS;
import arc.util.Strings;
import mindustry.Vars;
import mindustry.gen.Icon;
import mindustry.mod.Mods;
import mindustry.ui.Bar;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.ui.dialogs.SettingsMenuDialog;

import java.util.ArrayList;
import java.util.Locale;

public final class ModUpdateCenter{
    private static final String selfModName = "modupdater";

    private static final String keyEnabled = "mu-enabled";
    private static final String keyShowDialog = "mu-show-dialog";
    private static final String keyUseMirror = "mu-use-mirror";
    private static final String keyIntervalHours = "mu-check-interval-hours";
    private static final String keyLastCheckAt = "mu-last-check-at";
    private static final String keyBlacklist = "mu-blacklist";
    private static final String keyRepoOverrides = "mu-repo-overrides";
    private static final String mirrorPrefix = "https://ghfile.geekertao.top/";

    private static boolean startupChecked;
    private static boolean checking;

    private static Seq<String> blacklistNames = new Seq<String>();
    private static ObjectMap<String, String> repoOverrides = new ObjectMap<String, String>();
    private static Seq<ModEntry> lastEntries = new Seq<ModEntry>();

    private static BaseDialog centerDialog;
    private static Table centerContent;

    public static final class ModEntry{
        public final Mods.LoadedMod mod;
        public final String internalName;
        public final String displayName;
        public final String currentVersion;
        public String repo = "";
        public boolean blacklisted;
        public boolean noRepo;
        public boolean expanded;
        public String error = "";
        public int compareLatest;
        public GithubReleaseClient.ReleaseInfo latest;
        public GithubReleaseClient.ReleaseInfo selected;
        public final ArrayList<GithubReleaseClient.ReleaseInfo> releases = new ArrayList<GithubReleaseClient.ReleaseInfo>();

        public ModEntry(Mods.LoadedMod mod){
            this.mod = mod;
            this.internalName = normalizeName(mod == null ? "" : mod.name);
            this.displayName = displayNameOf(mod);
            this.currentVersion = VersionUtil.normalizeVersion(Strings.stripColors(mod != null && mod.meta != null ? mod.meta.version : ""));
        }

        public boolean hasUpdate(){
            return latest != null && compareLatest > 0;
        }

        public GithubReleaseClient.ReleaseInfo selectedRelease(){
            return selected == null ? latest : selected;
        }
    }

    private ModUpdateCenter(){
    }

    public static void init(){
        applyDefaults();
        reloadLocalSettings();
    }

    public static void applyDefaults(){
        Core.settings.defaults(keyEnabled, true);
        Core.settings.defaults(keyShowDialog, true);
        Core.settings.defaults(keyUseMirror, true);
        Core.settings.defaults(keyIntervalHours, 6);
    }

    public static void buildSettings(SettingsMenuDialog.SettingsTable table){
        applyDefaults();
        table.checkPref(keyEnabled, true);
        table.checkPref(keyShowDialog, true);
        table.checkPref(keyUseMirror, true);
        table.sliderPref(keyIntervalHours, 6, 1, 48, 1, v -> (int)v + "h");

        table.pref(new ButtonSetting("mu-open-center", () -> showCenter(true)));
        table.pref(new ButtonSetting("mu-open-blacklist", ModUpdateCenter::showBlacklistDialog));
    }

    public static void checkOnceAtStartup(){
        if(startupChecked) return;
        startupChecked = true;

        if(Vars.headless || Vars.mods == null) return;
        applyDefaults();
        reloadLocalSettings();
        if(!Core.settings.getBool(keyEnabled, true)) return;

        long now = System.currentTimeMillis();
        long last = Core.settings.getLong(keyLastCheckAt, 0L);
        long hours = Math.max(1, Core.settings.getInt(keyIntervalHours, 6));
        long intervalMs = hours * 60L * 60L * 1000L;
        if(last > 0L && now - last < intervalMs) return;
        Core.settings.put(keyLastCheckAt, now);

        runCheck(true);
    }

    public static void showCenter(boolean refresh){
        ensureCenterDialog();
        if(refresh || lastEntries.isEmpty()){
            runCheck(false);
        }else{
            rebuildCenter();
        }
        centerDialog.show();
    }

    private static void ensureCenterDialog(){
        if(centerDialog != null) return;

        centerDialog = new BaseDialog(Core.bundle.get("mu.dialog.title"));
        centerDialog.cont.margin(12f);

        centerContent = new Table();
        centerContent.left().defaults().left();
        ScrollPane pane = new ScrollPane(centerContent);
        pane.setFadeScrollBars(false);
        centerDialog.cont.add(pane).width(760f).height(Math.min(720f, Core.graphics.getHeight() * 0.8f)).row();

        centerDialog.buttons.defaults().size(220f, 56f).pad(6f);
        centerDialog.buttons.button("@mu.action.refresh", Icon.refresh, () -> runCheck(false));
        centerDialog.buttons.button("@mu.action.update-all", Icon.download, ModUpdateCenter::updateAll);
        centerDialog.buttons.button("@mu.action.blacklist", Icon.cancel, ModUpdateCenter::showBlacklistDialog);
        centerDialog.addCloseButton();
    }

    private static void rebuildCenter(){
        if(centerContent == null) return;
        centerContent.clearChildren();

        if(checking){
            centerContent.add(Core.bundle.get("mu.checking")).color(Color.lightGray).padBottom(6f).row();
        }

        if(lastEntries.isEmpty()){
            centerContent.add(Core.bundle.get("mu.empty")).color(Color.gray).row();
            return;
        }

        Seq<ModEntry> updatable = selectEntries(e -> e.hasUpdate() && !e.blacklisted && !e.noRepo && e.error.isEmpty());
        Seq<ModEntry> upToDate = selectEntries(e -> !e.hasUpdate() && !e.blacklisted && !e.noRepo && e.error.isEmpty());
        Seq<ModEntry> blacklisted = selectEntries(e -> e.blacklisted);
        Seq<ModEntry> errored = selectEntries(e -> !e.error.isEmpty() && !e.blacklisted && !e.noRepo);

        boolean visible = false;
        visible |= addGroup("mu.group.updatable", updatable);
        visible |= addGroup("mu.group.uptodate", upToDate);
        visible |= addGroup("mu.group.blacklisted", blacklisted);
        visible |= addGroup("mu.group.error", errored);

        if(!visible){
            centerContent.add(Core.bundle.get("mu.empty")).color(Color.gray).row();
        }
    }

    private static boolean addGroup(String titleKey, Seq<ModEntry> entries){
        if(entries.isEmpty()) return false;

        centerContent.image().color(Color.darkGray).height(2f).growX().padTop(6f).padBottom(6f).row();
        centerContent.add(Core.bundle.get(titleKey) + " (" + entries.size + ")").color(Color.white).left().padBottom(4f).row();

        for(ModEntry e : entries){
            addEntryCard(e);
        }

        return true;
    }

    private static void addEntryCard(ModEntry e){
        centerContent.table(card -> {
            card.left().defaults().left();

            String fold = e.expanded ? "▼ " : "▶ ";
            String status = statusText(e);
            String title = fold + e.displayName + " [lightgray](" + e.internalName + ")[]";
            String version = e.currentVersion.isEmpty() ? "-" : e.currentVersion;
            if(e.latest != null && !e.latest.version.isEmpty()){
                version += " -> " + e.latest.version;
            }

            card.button(title + "\n[lightgray]" + version + "[]  " + status, Styles.flatt, () -> {
                e.expanded = !e.expanded;
                rebuildCenter();
            }).width(730f).left().row();

            if(!e.expanded) return;

            card.add("[lightgray]repo:[] " + (e.repo.isEmpty() ? Core.bundle.get("mu.repo.none") : e.repo)).wrap().width(730f).padTop(4f).row();

            if(e.noRepo){
                final String[] value = {e.repo};
                TextField field = card.field(e.repo, v -> value[0] = v).width(600f).left().padTop(4f).get();
                field.setMessageText(Core.bundle.get("mu.repo.input"));
                card.row();
                card.button("@mu.action.repo.save", Icon.save, () -> {
                    String repo = RepoResolver.sanitizeRepo(value[0]);
                    if(repo.isEmpty()){
                        removeRepoOverride(e.internalName);
                        Vars.ui.showInfoToast(Core.bundle.get("mu.repo.cleared"), 3f);
                    }else{
                        setRepoOverride(e.internalName, repo);
                        if(e.mod != null){
                            e.mod.setRepo(repo);
                        }
                        Vars.ui.showInfoToast(Core.bundle.get("mu.repo.saved"), 3f);
                    }
                    runCheck(false);
                }).size(220f, 46f).left().padTop(4f).row();
            }

            card.table(actions -> {
                actions.defaults().size(180f, 46f).pad(3f);

                actions.button("@mu.action.open", Icon.link, () -> {
                    String url = e.selectedRelease() != null && !e.selectedRelease().htmlUrl.isEmpty() ? e.selectedRelease().htmlUrl :
                        (e.repo.isEmpty() ? "" : "https://github.com/" + e.repo + "/releases");
                    if(!url.isEmpty()){
                        Core.app.openURI(url);
                    }
                });

                actions.button(e.blacklisted ? "@mu.action.unblacklist" : "@mu.action.blacklist-add", Icon.cancel, () -> {
                    toggleBlacklist(e.internalName, !e.blacklisted);
                    runCheck(false);
                });

                if(e.hasUpdate()){
                    actions.button("@mu.action.update-one", Icon.download, () -> updateOne(e));
                }
            }).left().row();

            if(!e.error.isEmpty()){
                card.add("[scarlet]" + e.error).wrap().width(730f).padTop(4f).row();
            }

            if(!e.releases.isEmpty()){
                addReleaseSection(card, e, false);
                addReleaseSection(card, e, true);
            }
        }).left().padBottom(6f).row();
    }

    private static void addReleaseSection(Table card, ModEntry e, boolean preRelease){
        ArrayList<GithubReleaseClient.ReleaseInfo> list = new ArrayList<GithubReleaseClient.ReleaseInfo>();
        for(GithubReleaseClient.ReleaseInfo r : e.releases){
            if(r.preRelease == preRelease){
                list.add(r);
            }
        }
        if(list.isEmpty()) return;

        card.add(preRelease ? Core.bundle.get("mu.release.prerelease") : Core.bundle.get("mu.release.stable"))
            .color(Color.lightGray).padTop(6f).row();

        for(final GithubReleaseClient.ReleaseInfo r : list){
            String marker = e.selected == r ? "[accent]*[] " : "";
            String label = marker + r.version + (r == e.latest ? " [" + Core.bundle.get("mu.release.latest") + "]" : "");

            card.table(row -> {
                row.left().defaults().left();
                row.button(label, Styles.togglet, () -> {
                    e.selected = r;
                    rebuildCenter();
                }).checked(e.selected == r).width(520f).left();

                row.button(Icon.link, () -> {
                    if(!r.htmlUrl.isEmpty()) Core.app.openURI(r.htmlUrl);
                }).size(40f).padLeft(4f);

                if(e.hasUpdate()){
                    GithubReleaseClient.AssetInfo asset = GithubReleaseClient.pickDefaultAsset(r.assets);
                    if(asset != null){
                        row.button(Icon.download, () -> updateOne(e)).size(40f).padLeft(4f);
                    }
                }
            }).left().row();
        }
    }

    private static String statusText(ModEntry e){
        if(e.blacklisted) return "[scarlet]" + Core.bundle.get("mu.status.blacklisted") + "[]";
        if(e.noRepo) return "[lightgray]" + Core.bundle.get("mu.status.norepo") + "[]";
        if(!e.error.isEmpty()) return "[scarlet]" + Core.bundle.get("mu.status.error") + "[]";
        if(e.hasUpdate()) return "[accent]" + Core.bundle.get("mu.status.update") + "[]";
        return "[green]" + Core.bundle.get("mu.status.latest") + "[]";
    }

    private static Seq<ModEntry> selectEntries(Boolf1<ModEntry> filter){
        Seq<ModEntry> out = new Seq<ModEntry>();
        for(ModEntry e : lastEntries){
            if(filter.get(e)) out.add(e);
        }
        return out;
    }

    private static void runCheck(boolean startupPrompt){
        if(Vars.headless || Vars.mods == null) return;
        if(checking) return;

        reloadLocalSettings();
        checking = true;
        rebuildCenter();

        Seq<ModEntry> fresh = collectEntries();
        if(hasMissingRepos(fresh)){
            RepoResolver.loadModIndex(loaded -> {
                if(loaded) resolveMissingReposFromIndex(fresh);
                continueCheck(fresh, startupPrompt);
            });
            return;
        }

        continueCheck(fresh, startupPrompt);
    }

    private static void continueCheck(Seq<ModEntry> fresh, boolean startupPrompt){
        Seq<ModEntry> needsFetch = new Seq<ModEntry>();
        for(ModEntry e : fresh){
            if(!e.blacklisted && !e.noRepo){
                needsFetch.add(e);
            }
        }

        if(needsFetch.isEmpty()){
            onCheckFinished(fresh, startupPrompt);
            return;
        }

        fetchEntry(needsFetch, 0, fresh, startupPrompt);
    }

    private static void fetchEntry(Seq<ModEntry> needsFetch, int index, Seq<ModEntry> fresh, boolean startupPrompt){
        if(index >= needsFetch.size){
            onCheckFinished(fresh, startupPrompt);
            return;
        }

        ModEntry entry = needsFetch.get(index);
        GithubReleaseClient.fetchReleases(entry.repo, releases -> {
            if(releases == null || releases.isEmpty()){
                fallbackRaw(entry, () -> fetchEntry(needsFetch, index + 1, fresh, startupPrompt));
                return;
            }

            entry.releases.clear();
            entry.releases.addAll(releases);
            entry.latest = GithubReleaseClient.pickLatestRelease(releases);
            entry.selected = entry.latest;
            if(entry.latest != null && !entry.latest.version.isEmpty() && !entry.currentVersion.isEmpty()){
                entry.compareLatest = VersionUtil.compareVersions(entry.latest.version, entry.currentVersion);
            }
            fetchEntry(needsFetch, index + 1, fresh, startupPrompt);
        }, err -> fallbackRaw(entry, () -> fetchEntry(needsFetch, index + 1, fresh, startupPrompt)));
    }

    private static void fallbackRaw(ModEntry entry, Runnable done){
        GithubReleaseClient.fetchLatestFromRaw(entry.repo, rel -> {
            entry.releases.clear();
            entry.releases.add(rel);
            entry.latest = rel;
            entry.selected = rel;
            if(!entry.currentVersion.isEmpty()){
                entry.compareLatest = VersionUtil.compareVersions(rel.version, entry.currentVersion);
            }
            done.run();
        }, err -> {
            entry.error = friendlyError(err);
            done.run();
        });
    }

    private static void onCheckFinished(Seq<ModEntry> fresh, boolean startupPrompt){
        fresh.sort((a, b) -> a.displayName.compareToIgnoreCase(b.displayName));
        lastEntries = fresh;
        checking = false;
        Core.app.post(() -> {
            rebuildCenter();

            if(startupPrompt && Core.settings.getBool(keyShowDialog, true) && hasUpdates(fresh)){
                showCenter(false);
            }
        });
    }

    private static Seq<ModEntry> collectEntries(){
        Seq<ModEntry> out = new Seq<ModEntry>();
        for(Mods.LoadedMod mod : Vars.mods.list()){
            if(mod == null || mod.meta == null) continue;
            if(!mod.enabled()) continue;
            if(selfModName.equalsIgnoreCase(mod.name)) continue;

            ModEntry e = new ModEntry(mod);
            e.blacklisted = blacklistNames.contains(e.internalName);
            e.repo = RepoResolver.resolveRepo(mod, repoOverrides);
            e.noRepo = e.repo.isEmpty();
            out.add(e);
        }
        return out;
    }

    private static boolean hasMissingRepos(Seq<ModEntry> entries){
        for(ModEntry e : entries){
            if(!e.blacklisted && e.noRepo){
                return true;
            }
        }
        return false;
    }

    private static void resolveMissingReposFromIndex(Seq<ModEntry> entries){
        for(ModEntry e : entries){
            if(e == null || e.blacklisted || !e.noRepo) continue;
            String repo = RepoResolver.resolveIndexedRepo(e.mod);
            if(repo.isEmpty()) continue;

            e.repo = repo;
            e.noRepo = false;
            if(e.mod != null){
                e.mod.setRepo(repo);
            }
        }
    }

    private static boolean hasUpdates(Seq<ModEntry> entries){
        for(ModEntry e : entries){
            if(e.hasUpdate() && !e.blacklisted && e.error.isEmpty() && !e.noRepo){
                return true;
            }
        }
        return false;
    }

    private static void updateOne(ModEntry entry){
        if(entry == null || !entry.hasUpdate()) return;
        Seq<ModEntry> one = new Seq<ModEntry>();
        one.add(entry);
        runBatchUpdate(one);
    }

    private static void updateAll(){
        Seq<ModEntry> all = new Seq<ModEntry>();
        for(ModEntry e : lastEntries){
            if(e.hasUpdate() && !e.blacklisted && !e.noRepo && e.error.isEmpty()){
                all.add(e);
            }
        }
        if(all.isEmpty()){
            Vars.ui.showInfoToast(Core.bundle.get("mu.toast.noupdate"), 3f);
            return;
        }
        runBatchUpdate(all);
    }

    private static void runBatchUpdate(Seq<ModEntry> entries){
        boolean useMirror = Core.settings.getBool(keyUseMirror, true);
        Seq<BatchUpdateInstaller.UpdateTarget> targets = new Seq<BatchUpdateInstaller.UpdateTarget>();
        for(ModEntry e : entries){
            GithubReleaseClient.ReleaseInfo rel = e.selectedRelease();
            if(rel == null) rel = e.latest;
            if(rel == null) continue;

            GithubReleaseClient.AssetInfo asset = GithubReleaseClient.pickDefaultAsset(rel.assets);
            if(asset == null) continue;
            String downloadUrl = buildDownloadUrl(asset.url, useMirror);
            targets.add(new BatchUpdateInstaller.UpdateTarget(e.mod, e.repo, rel, asset, downloadUrl));
        }

        if(targets.isEmpty()){
            Vars.ui.showInfoToast(Core.bundle.get("mu.toast.noasset"), 3f);
            return;
        }

        final int[] done = {0};
        final int total = targets.size;

        BaseDialog progress = new BaseDialog(Core.bundle.get("mu.update.batch.title"));
        progress.cont.margin(10f);
        progress.cont.add(new Bar(() -> Core.bundle.format("mu.update.batch.progress", done[0], total), () -> Color.valueOf("5ec7ff"), () -> total == 0 ? 0f : done[0] / (float)total))
            .width(520f).height(66f).row();
        progress.buttons.button("@cancel", Icon.cancel, progress::hide).size(180f, 56f);
        progress.show();

        BatchUpdateInstaller.installAll(targets, (i, t) -> Core.app.post(() -> done[0] = i), result -> Core.app.post(() -> {
            progress.hide();

            String summary = Core.bundle.format("mu.update.batch.summary", result.total, result.success, result.failed, result.skipped);
            Vars.ui.showInfoToast(summary, 6f);

            if(result.success > 0){
                restartApp();
            }else{
                runCheck(false);
            }
        }));
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

    private static void reloadLocalSettings(){
        blacklistNames = loadBlacklist();
        repoOverrides = RepoResolver.loadOverrides(keyRepoOverrides);
    }

    private static Seq<String> loadBlacklist(){
        Seq<String> raw = Core.settings.getJson(keyBlacklist, Seq.class, String.class, Seq::new);
        Seq<String> out = new Seq<String>();
        for(String s : raw){
            String n = normalizeName(s);
            if(!n.isEmpty() && !out.contains(n)){
                out.add(n);
            }
        }
        out.sort();
        return out;
    }

    private static void saveBlacklist(){
        Seq<String> sorted = blacklistNames.copy();
        sorted.sort();
        Core.settings.putJson(keyBlacklist, String.class, sorted);
    }

    private static void toggleBlacklist(String internalName, boolean add){
        String n = normalizeName(internalName);
        if(n.isEmpty()) return;
        if(add){
            if(!blacklistNames.contains(n)) blacklistNames.add(n);
        }else{
            blacklistNames.remove(n);
        }
        saveBlacklist();
    }

    private static void setRepoOverride(String internalName, String repo){
        String n = normalizeName(internalName);
        String r = RepoResolver.sanitizeRepo(repo);
        if(n.isEmpty() || r.isEmpty()) return;
        repoOverrides.put(n, r);
        RepoResolver.saveOverrides(keyRepoOverrides, repoOverrides);
    }

    private static void removeRepoOverride(String internalName){
        String n = normalizeName(internalName);
        if(n.isEmpty()) return;
        repoOverrides.remove(n);
        RepoResolver.saveOverrides(keyRepoOverrides, repoOverrides);
    }

    private static String normalizeName(String name){
        return Strings.stripColors(name == null ? "" : name).trim().toLowerCase(Locale.ROOT);
    }

    private static String displayNameOf(Mods.LoadedMod mod){
        if(mod == null || mod.meta == null) return "unknown";
        String n = Strings.stripColors(mod.meta.displayName == null ? mod.meta.name : mod.meta.displayName);
        if(n == null || n.isEmpty()) n = mod.name;
        return n == null || n.isEmpty() ? "unknown" : n;
    }

    private static String friendlyError(Throwable t){
        if(t == null) return Core.bundle.get("mu.error.unknown");
        String msg = t.getMessage();
        if(msg == null || msg.trim().isEmpty()) msg = t.getClass().getSimpleName();
        return msg;
    }

    private static String buildDownloadUrl(String original, boolean mirror){
        String url = original == null ? "" : original.trim();
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

    private static void showBlacklistDialog(){
        ensureCenterDialog();

        BaseDialog dialog = new BaseDialog(Core.bundle.get("mu.blacklist.title"));
        dialog.cont.margin(12f);
        final boolean[] changed = {false};

        Table list = new Table();
        list.left().defaults().left();
        Seq<ModEntry> entries = lastEntries.copy();
        entries.sort((a, b) -> a.displayName.compareToIgnoreCase(b.displayName));

        if(entries.isEmpty()){
            list.add(Core.bundle.get("mu.blacklist.empty")).color(Color.gray).row();
        }else{
            for(ModEntry e : entries){
                CheckBox box = list.check(e.displayName + " [lightgray](" + e.internalName + ")[]", blacklistNames.contains(e.internalName), v -> {
                    toggleBlacklist(e.internalName, v);
                    changed[0] = true;
                }).left().growX().get();
                box.getLabel().setWrap(true);
                list.row();
            }
        }

        ScrollPane pane = new ScrollPane(list);
        pane.setFadeScrollBars(false);
        dialog.cont.add(pane).width(700f).height(Math.min(640f, Core.graphics.getHeight() * 0.75f)).row();
        dialog.addCloseButton();
        dialog.hidden(() -> {
            if(changed[0]){
                runCheck(false);
            }
        });
        dialog.show();
    }

    private interface Boolf1<T>{
        boolean get(T value);
    }

    private static class ButtonSetting extends SettingsMenuDialog.SettingsTable.Setting{
        private final Runnable action;

        public ButtonSetting(String name, Runnable action){
            super(name);
            this.action = action;
        }

        @Override
        public void add(SettingsMenuDialog.SettingsTable table){
            TextButton b = table.button(title, action).growX().margin(14f).pad(6f).center().get();
            b.getLabel().setWrap(true);
        }
    }
}
