package modupdater.features;

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
    }

    private static void check(boolean condition, String name){
        if(!condition) throw new AssertionError(name);
    }
}
