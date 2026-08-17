package modupdater.features;

import arc.util.serialization.Jval;

public final class VersionUtilTest{
    private VersionUtilTest(){
    }

    public static void main(String[] args){
        check(VersionUtil.normalizeVersion(" v11.0.0 ").equals("11.0.0"), "legacy prefix removed");
        check(VersionUtil.normalizeVersion("B12.1").equals("B12.1"), "beta label retained");
        check(VersionUtil.compareVersions("B11.20", "N11") > 0, "beta after stable");
        check(VersionUtil.compareVersions("N12", "B11.9999") > 0, "next stable after beta");
        check(VersionUtil.compareVersions("v11", "N11") == 0, "legacy and label match");
        check(VersionUtil.compareVersions("N11", "v11.0.0") == 0, "new and legacy stable match");

        String githubUrl = "https://github.com/DeterMination-Wind/Neon/releases/download/v11.0.0/Neon-v11.0.0.zip";
        check(PlayMirrorResolver.resolveHost(githubUrl).equals(githubUrl), "non-mirror host untouched");
        check(PlayMirrorResolver.resolveHost("http://example.com/x").equals("http://example.com/x"), "other host untouched");
        check(PlayMirrorResolver.resolveHost(null) == null, "null URL falls back to null");
        check(PlayMirrorResolver.resolveHost("not a url").equals("not a url"), "malformed URL falls back as-is");

        Jval backup = Jval.read("[{\"tag_name\":\"v11.0.0\",\"name\":\"N11\",\"body\":\"notes\",\"assets\":[{\"name\":\"Neon-v11.0.0.zip\",\"browser_download_url\":\"" + githubUrl + "\"}]}]");
        java.util.ArrayList<GithubReleaseClient.ReleaseInfo> releases = GithubReleaseClient.parseReleasesList("DeterMination-Wind/Neon", backup);
        check(releases.size() == 1, "single release backup payload");
        check(releases.get(0).version.equals("11.0.0"), "backup release version");
        check(releases.get(0).body.equals("notes"), "backup release body");
        check(releases.get(0).assets.size() == 1, "backup release asset");
        check(GithubReleaseClient.pickDefaultAsset(releases.get(0).assets) != null, "default asset");

        Jval beta = Jval.read("[{\"tag_name\":\"B11.20\",\"name\":\"B11.20\",\"prerelease\":true}]");
        java.util.ArrayList<GithubReleaseClient.ReleaseInfo> betaReleases = GithubReleaseClient.parseReleasesList("DeterMination-Wind/Neon", beta);
        check(betaReleases.size() == 1 && betaReleases.get(0).preRelease, "beta release channel");
    }

    private static void check(boolean condition, String name){
        if(!condition) throw new AssertionError(name);
    }
}
