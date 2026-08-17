package modupdater.features;

import arc.func.Cons;
import arc.util.Http;
import arc.util.OS;
import arc.util.Strings;
import arc.util.serialization.Jval;

import java.util.ArrayList;
import java.util.Collections;

public final class GithubReleaseClient{

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
        String apiUrl = "https://api.github.com/repos/" + repo + "/releases?per_page=30";
        Http.get(apiUrl)
        .timeout(30000)
        .header("User-Agent", "Mindustry")
        .error(onError::get)
        .submit(res -> {
            try{
                Jval json = Jval.read(res.getResultAsString());
                onSuccess.get(parseReleasesList(repo, json));
            }catch(Throwable t){
                onError.get(t);
            }
        });
    }

    public static void fetchLatestFromRaw(String repo, Cons<ReleaseInfo> onSuccess, Cons<Throwable> onError){
        fetchLatestFromRaw(repo, "main", onSuccess, e -> fetchLatestFromRaw(repo, "master", onSuccess, onError));
    }

    private static void fetchLatestFromRaw(String repo, String branch, Cons<ReleaseInfo> onSuccess, Cons<Throwable> onError){
        String url = "https://raw.githubusercontent.com/" + repo + "/" + branch + "/mod.json";
        Http.get(url)
        .timeout(30000)
        .header("User-Agent", "Mindustry")
        .error(onError::get)
        .submit(res -> {
            try{
                Jval json = Jval.read(res.getResultAsString());
                String latest = VersionUtil.normalizeVersion(Strings.stripColors(json.getString("version", "")));
                if(latest.isEmpty()){
                    onError.get(new RuntimeException("Version missing in raw mod.json"));
                    return;
                }

                String releasesUrl = "https://github.com/" + repo + "/releases/latest";
                ReleaseInfo rel = new ReleaseInfo(latest, "", "", "", releasesUrl, "", false, "", new ArrayList<AssetInfo>());
                onSuccess.get(rel);
            }catch(Throwable t){
                onError.get(t);
            }
        });
    }

    public static ArrayList<ReleaseInfo> parseReleasesList(String repo, Jval json){
        ArrayList<ReleaseInfo> out = new ArrayList<ReleaseInfo>();
        if(json == null || !json.isArray()) return out;

        String fallbackHtmlUrl = "https://github.com/" + repo + "/releases";
        for(Jval r : json.asArray()){
            if(r == null || !r.isObject()) continue;
            if(r.getBool("draft", false)) continue;
            try{
                ReleaseInfo rel = parseRelease(r, fallbackHtmlUrl);
                if(rel != null && !rel.version.isEmpty()){
                    out.add(rel);
                }
            }catch(Throwable ignored){
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

        String version = VersionUtil.normalizeVersion(tag);
        if(version.isEmpty()) version = VersionUtil.normalizeVersion(name);

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
}
