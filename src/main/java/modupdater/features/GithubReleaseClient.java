package modupdater.features;

import arc.func.Cons;
import arc.util.Http;
import arc.util.OS;
import arc.util.Strings;
import arc.util.serialization.Jval;

import java.util.ArrayList;
import java.util.Collections;

public final class GithubReleaseClient{
    private static final String neonRepo = "DeterMination-Wind/Neon";
    private static final String neonReleaseBackupUrl = "http://121.199.60.4/github/mod-assets/DeterMination-Wind/Neon/release-backup.json";
    private static final String githubDownloadPrefix = "https://github.com/";
    private static final String mirrorDownloadPrefix = "http://121.199.60.4/github/mod-assets/";

    public static final class AssetInfo{
        public final String name;
        public final String url;
        public final long sizeBytes;
        public final int downloadCount;

        public AssetInfo(String name, String url, long sizeBytes, int downloadCount){
            this.name = name == null ? "" : name;
            this.url = url == null ? "" : url;
            this.sizeBytes = sizeBytes;
            this.downloadCount = downloadCount;
        }
    }

    public static final class ReleaseInfo{
        public final String version;
        public final String tag;
        public final String name;
        public final String body;
        public final String htmlUrl;
        public final String publishedAt;
        public final boolean preRelease;
        public final ArrayList<AssetInfo> assets;
        public final String releaseId;

        public ReleaseInfo(String version, String tag, String name, String body, String htmlUrl, String publishedAt, boolean preRelease, String releaseId, ArrayList<AssetInfo> assets){
            this.version = version == null ? "" : version;
            this.tag = tag == null ? "" : tag;
            this.name = name == null ? "" : name;
            this.body = body == null ? "" : body;
            this.htmlUrl = htmlUrl == null ? "" : htmlUrl;
            this.publishedAt = publishedAt == null ? "" : publishedAt;
            this.preRelease = preRelease;
            this.releaseId = releaseId == null ? "" : releaseId;
            this.assets = assets == null ? new ArrayList<AssetInfo>() : assets;
        }
    }

    private GithubReleaseClient(){
    }

    public static void fetchReleases(String repo, Cons<ArrayList<ReleaseInfo>> onSuccess, Cons<Throwable> onError){
        String apiUrl = "https://api.github.com/repos/" + repo + "/releases?per_page=100";
        fetchReleasesUrl(repo, apiUrl, releases -> {
            if(isNeonRepo(repo) && releases.isEmpty()){
                fetchNeonBackup(repo, onSuccess, onError);
            }else{
                onSuccess.get(releases);
            }
        }, error -> {
            if(isNeonRepo(repo)){
                fetchNeonBackup(repo, onSuccess, onError);
            }else{
                onError.get(error);
            }
        });
    }

    private static void fetchReleasesUrl(String repo, String url, Cons<ArrayList<ReleaseInfo>> onSuccess, Cons<Throwable> onError){
        Http.get(url)
        .timeout(30000)
        .header("User-Agent", "Mindustry")
        .error(onError::get)
        .submit(res -> {
            try{
                Jval json = Jval.read(res.getResultAsString());
                onSuccess.get(parseReleasesPayload(repo, json));
            }catch(Throwable t){
                onError.get(t);
            }
        });
    }

    private static void fetchNeonBackup(String repo, Cons<ArrayList<ReleaseInfo>> onSuccess, Cons<Throwable> onError){
        fetchReleasesUrl(repo, neonReleaseBackupUrl, releases -> {
            if(releases.isEmpty()){
                onError.get(new RuntimeException("Neon release backup is empty"));
            }else{
                onSuccess.get(releases);
            }
        }, onError);
    }

    public static ArrayList<ReleaseInfo> parseReleasesPayload(String repo, Jval json){
        ArrayList<ReleaseInfo> out = new ArrayList<ReleaseInfo>();
        if(json == null) return out;

        String fallbackHtmlUrl = "https://github.com/" + repo + "/releases";
        if(json.isArray()){
            for(Jval r : json.asArray()){
                addRelease(out, r, fallbackHtmlUrl);
            }
        }else if(json.isObject()){
            Jval nested = json.get("releases");
            if(nested != null && nested.isArray()){
                for(Jval r : nested.asArray()){
                    addRelease(out, r, fallbackHtmlUrl);
                }
            }else{
                addRelease(out, json, fallbackHtmlUrl);
            }
        }

        Collections.sort(out, (a, b) -> {
            int c = VersionUtil.compareVersions(b.version, a.version);
            if(c != 0) return c;
            if(a.preRelease != b.preRelease) return a.preRelease ? 1 : -1;
            return a.tag.compareToIgnoreCase(b.tag);
        });

        return out;
    }

    private static void addRelease(ArrayList<ReleaseInfo> out, Jval json, String fallbackHtmlUrl){
        if(json == null || !json.isObject()) return;
        if(json.getBool("draft", false)) return;
        try{
            ReleaseInfo rel = parseRelease(json, fallbackHtmlUrl);
            if(rel != null && !rel.version.isEmpty()){
                out.add(rel);
            }
        }catch(Throwable ignored){
        }
    }

    public static ReleaseInfo pickLatestRelease(ArrayList<ReleaseInfo> releases){
        if(releases == null || releases.isEmpty()) return null;

        ReleaseInfo bestStable = null;
        ReleaseInfo bestAny = null;
        for(ReleaseInfo r : releases){
            if(r == null || r.version.isEmpty()) continue;

            if(bestAny == null || VersionUtil.compareVersions(r.version, bestAny.version) > 0){
                bestAny = r;
            }
            if(!r.preRelease && (bestStable == null || VersionUtil.compareVersions(r.version, bestStable.version) > 0)){
                bestStable = r;
            }
        }

        return bestStable != null ? bestStable : bestAny;
    }

    public static AssetInfo pickDefaultAsset(ArrayList<AssetInfo> assets){
        if(assets == null || assets.isEmpty()) return null;

        boolean android = OS.isAndroid;
        if(android){
            for(AssetInfo a : assets){
                if(endsWithIgnoreCase(a.name, ".jar")) return a;
            }
        }else{
            for(AssetInfo a : assets){
                if(endsWithIgnoreCase(a.name, ".zip")) return a;
            }
        }

        for(AssetInfo a : assets){
            if(endsWithIgnoreCase(a.name, ".jar")) return a;
        }
        return assets.get(0);
    }

    private static ReleaseInfo parseRelease(Jval json, String fallbackHtmlUrl){
        String tag = Strings.stripColors(json.getString("tag_name", ""));
        String htmlUrl = Strings.stripColors(json.getString("html_url", fallbackHtmlUrl));
        if(htmlUrl == null || htmlUrl.isEmpty()) htmlUrl = fallbackHtmlUrl;

        String releaseId = Strings.stripColors(json.getString("id", ""));
        String name = Strings.stripColors(json.getString("name", ""));
        String body = Strings.stripColors(json.getString("body", ""));
        String publishedAt = Strings.stripColors(json.getString("published_at", ""));
        boolean pre = json.getBool("prerelease", false);

        String version = VersionUtil.normalizeReleaseVersion(tag, name);
        if(version.isEmpty()) version = VersionUtil.normalizeVersion(json.getString("version", ""));
        if(VersionUtil.normalizeVersion(version).regionMatches(true, 0, "B", 0, 1)) pre = true;

        ArrayList<AssetInfo> assets = new ArrayList<AssetInfo>();
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
        return new ReleaseInfo(version, tag, name, body, htmlUrl, publishedAt, pre, releaseId, assets);
    }

    private static boolean endsWithIgnoreCase(String text, String suffix){
        if(text == null || suffix == null) return false;
        int offset = text.length() - suffix.length();
        return offset >= 0 && text.regionMatches(true, offset, suffix, 0, suffix.length());
    }

    public static String buildDownloadUrl(String original, boolean useMirror){
        String url = original == null ? "" : original.trim();
        if(url.isEmpty()) return url;

        if(useMirror){
            if(url.startsWith(mirrorDownloadPrefix)) return url;

            int marker = url.indexOf("/releases/download/");
            if(url.startsWith(githubDownloadPrefix) && marker > githubDownloadPrefix.length()){
                String repo = url.substring(githubDownloadPrefix.length(), marker);
                String asset = url.substring(marker + "/releases/download/".length());
                if(!repo.isEmpty() && !asset.isEmpty()){
                    return mirrorDownloadPrefix + repo + "/" + asset;
                }
            }
            return url;
        }

        if(url.startsWith(mirrorDownloadPrefix)){
            String path = url.substring(mirrorDownloadPrefix.length());
            String[] parts = path.split("/", 4);
            if(parts.length == 4){
                return githubDownloadPrefix + parts[0] + "/" + parts[1] + "/releases/download/" + parts[2] + "/" + parts[3];
            }
        }
        return url;
    }

    private static boolean isNeonRepo(String repo){
        return neonRepo.equalsIgnoreCase(repo == null ? "" : repo.trim());
    }
}
