package modupdater.features;

import arc.Core;
import arc.files.Fi;
import arc.func.Cons;
import arc.func.Cons2;
import arc.struct.Seq;
import arc.util.Http;
import arc.util.Strings;
import mindustry.Vars;
import mindustry.mod.Mods;

import java.io.InputStream;
import java.io.OutputStream;

public final class BatchUpdateInstaller{

    public static final class UpdateTarget{
        public final Mods.LoadedMod mod;
        public final String repo;
        public final GithubReleaseClient.ReleaseInfo release;
        public final GithubReleaseClient.AssetInfo asset;
        public final String downloadUrl;

        public UpdateTarget(Mods.LoadedMod mod, String repo, GithubReleaseClient.ReleaseInfo release, GithubReleaseClient.AssetInfo asset, String downloadUrl){
            this.mod = mod;
            this.repo = repo == null ? "" : repo;
            this.release = release;
            this.asset = asset;
            this.downloadUrl = downloadUrl == null ? "" : downloadUrl;
        }
    }

    public static final class Result{
        public final int total;
        public int success;
        public int failed;
        public int skipped;
        public final Seq<String> failedMods = new Seq<String>();

        public Result(int total){
            this.total = total;
        }
    }

    private BatchUpdateInstaller(){
    }

    public static void installAll(Seq<UpdateTarget> targets, Cons2<Integer, Integer> onProgress, Cons<Result> onDone){
        Result result = new Result(targets == null ? 0 : targets.size);
        if(targets == null || targets.isEmpty()){
            onDone.get(result);
            return;
        }

        Fi dir = Vars.tmpDirectory.child("modupdater-update");
        dir.mkdirs();
        processNext(targets, 0, dir, result, onProgress, onDone);
    }

    private static void processNext(Seq<UpdateTarget> targets, int index, Fi dir, Result result, Cons2<Integer, Integer> onProgress, Cons<Result> onDone){
        if(index >= targets.size){
            Core.app.post(() -> onDone.get(result));
            return;
        }

        UpdateTarget t = targets.get(index);
        if(t == null || t.mod == null || t.release == null || t.asset == null || t.downloadUrl.trim().isEmpty()){
            result.skipped++;
            onProgress.get(index + 1, targets.size);
            processNext(targets, index + 1, dir, result, onProgress, onDone);
            return;
        }

        String fileName = sanitizeFileName(t.asset.name.isEmpty() ? t.mod.name + ".zip" : t.asset.name);
        Fi file = dir.child(fileName);

        Http.get(t.downloadUrl)
        .timeout(30000)
        .header("User-Agent", "Mindustry")
        .error(e -> {
            result.failed++;
            result.failedMods.add(displayName(t.mod) + " - " + e.getMessage());
            onProgress.get(index + 1, targets.size);
            processNext(targets, index + 1, dir, result, onProgress, onDone);
        })
        .submit(res -> {
            try{
                int buffer = 1024 * 1024;
                try(InputStream in = res.getResultAsStream(); OutputStream out = file.write(false, buffer)){
                    byte[] buf = new byte[buffer];
                    int r;
                    while((r = in.read(buf)) != -1){
                        out.write(buf, 0, r);
                    }
                }
            }catch(Throwable ioErr){
                try{ file.delete(); }catch(Throwable ignored){}
                result.failed++;
                result.failedMods.add(displayName(t.mod) + " - " + ioErr.getMessage());
                onProgress.get(index + 1, targets.size);
                processNext(targets, index + 1, dir, result, onProgress, onDone);
                return;
            }

            Core.app.post(() -> {
                try{
                    Vars.mods.importMod(file);
                    Mods.LoadedMod imported = Vars.mods.getMod(t.mod.name);
                    if(imported != null && !t.repo.isEmpty()){
                        imported.setRepo(t.repo);
                    }
                    result.success++;
                }catch(Throwable installErr){
                    result.failed++;
                    result.failedMods.add(displayName(t.mod) + " - " + installErr.getMessage());
                }finally{
                    try{ file.delete(); }catch(Throwable ignored){}
                    onProgress.get(index + 1, targets.size);
                    processNext(targets, index + 1, dir, result, onProgress, onDone);
                }
            });
        });
    }

    private static String displayName(Mods.LoadedMod mod){
        if(mod == null || mod.meta == null || mod.meta.displayName == null || mod.meta.displayName.isEmpty()){
            return mod == null ? "unknown" : mod.name;
        }
        return mod.meta.displayName;
    }

    private static String sanitizeFileName(String name){
        String n = Strings.stripColors(name == null ? "" : name).trim();
        if(n.isEmpty()) return "mod.zip";
        return n.replaceAll("[\\\\/:*?\"<>|]", "_");
    }
}
