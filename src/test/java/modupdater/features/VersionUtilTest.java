package modupdater.features;

import arc.util.serialization.Jval;

public final class VersionUtilTest{
    private VersionUtilTest(){
    }

    public static void main(String[] args){
        check(VersionUtil.versionCode("N11") == 110000, "N11 code");
        check(VersionUtil.versionCode("B11.20") == 110020, "B11.20 code");
        check(VersionUtil.versionCode("B11.0") < 0, "zero beta build rejected");
        check(VersionUtil.versionCode("110020") == 110020, "numeric code");
        check(VersionUtil.versionCode("v11.0.0") == 110000, "legacy stable code");

        check(VersionUtil.compareVersions("B11.20", "N11") > 0, "beta after stable");
        check(VersionUtil.compareVersions("N12", "B11.9999") > 0, "next stable after beta");
        check(VersionUtil.compareVersions("110020", "B11.20") == 0, "numeric and label match");
        check(VersionUtil.compareVersions("N11", "v11.0.0") == 0, "new and legacy stable match");

        check(VersionUtil.normalizeReleaseVersion("v11.0.0", "N11").equals("N11"), "canonical release name");
        check(VersionUtil.normalizeReleaseVersion("v10.3.1", "v10.3.1").equals("10.3.1"), "legacy tag fallback");

        String githubUrl = "https://github.com/DeterMination-Wind/Neon/releases/download/v11.0.0/Neon-v11.0.0.zip";
        String mirrorUrl = "http://play.mindustry.men/github/mod-assets/DeterMination-Wind/Neon/v11.0.0/Neon-v11.0.0.zip";
        check(GithubReleaseClient.buildDownloadUrl(githubUrl, true).equals(mirrorUrl), "server mirror URL");
        check(GithubReleaseClient.buildDownloadUrl(mirrorUrl, false).equals(githubUrl), "restore GitHub URL");

        check(PlayMirrorResolver.resolveHost(githubUrl).equals(githubUrl), "non-mirror host untouched");
        check(PlayMirrorResolver.resolveHost("http://example.com/x").equals("http://example.com/x"), "other host untouched");
        check(PlayMirrorResolver.resolveHost(null) == null, "null URL falls back to null");
        check(PlayMirrorResolver.resolveHost("not a url").equals("not a url"), "malformed URL falls back as-is");

        Jval backup = Jval.read("{\"tag_name\":\"v11.0.0\",\"name\":\"N11\",\"body\":\"notes\",\"assets\":[{\"name\":\"Neon-v11.0.0.zip\",\"browser_download_url\":\"" + githubUrl + "\"}]}");
        java.util.ArrayList<GithubReleaseClient.ReleaseInfo> releases = GithubReleaseClient.parseReleasesPayload("DeterMination-Wind/Neon", backup);
        check(releases.size() == 1, "single release backup payload");
        check(releases.get(0).version.equals("N11"), "backup release version");
        check(releases.get(0).body.equals("notes"), "backup release body");
        check(releases.get(0).assets.size() == 1, "backup release asset");

        Jval beta = Jval.read("{\"tag_name\":\"B11.20\",\"name\":\"B11.20\"}");
        java.util.ArrayList<GithubReleaseClient.ReleaseInfo> betaReleases = GithubReleaseClient.parseReleasesPayload("DeterMination-Wind/Neon", beta);
        check(betaReleases.size() == 1 && betaReleases.get(0).preRelease, "beta release channel");
    }

    private static void check(boolean condition, String name){
        if(!condition) throw new AssertionError(name);
    }
}
