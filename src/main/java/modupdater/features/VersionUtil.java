package modupdater.features;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class VersionUtil{
    private static final Pattern stablePattern = Pattern.compile("^N(\\d+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern betaPattern = Pattern.compile("^B(\\d+)\\.(\\d+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern numericPattern = Pattern.compile("^\\d+$");
    private static final Pattern semverPattern = Pattern.compile("^(\\d+)\\.(\\d+)\\.(\\d+)(?:[-+].*)?$");
    private static final Pattern numberPattern = Pattern.compile("\\d+");

    private VersionUtil(){
    }

    public static String normalizeVersion(String raw){
        if(raw == null) return "";
        String v = raw.trim();
        if(v.startsWith("v") || v.startsWith("V")){
            v = v.substring(1).trim();
        }
        return v;
    }

    /**
     * Prefer a canonical Neon release name when old releases keep their legacy v* tag.
     */
    public static String normalizeReleaseVersion(String tag, String name){
        String normalizedName = normalizeVersion(name);
        if(isNeonReleaseName(normalizedName)) return normalizedName;
        return normalizeVersion(tag);
    }

    public static boolean isNeonReleaseName(String raw){
        String value = normalizeVersion(raw);
        return stablePattern.matcher(value).matches() || betaPattern.matcher(value).matches();
    }

    /**
     * Converts both release labels and legacy descriptor versions to the six-digit
     * Mindustry version code: N11 -> 110000 and B11.20 -> 110020.
     */
    public static int versionCode(String raw){
        String value = normalizeVersion(raw);

        Matcher stable = stablePattern.matcher(value);
        if(stable.matches()) return code(stable.group(1), 0);

        Matcher beta = betaPattern.matcher(value);
        if(beta.matches()){
            try{
                int build = Integer.parseInt(beta.group(2));
                if(build <= 0) return -1;
                return code(beta.group(1), build);
            }catch(Throwable ignored){
                return -1;
            }
        }

        if(numericPattern.matcher(value).matches()){
            try{
                return Integer.parseInt(value);
            }catch(Throwable ignored){
                return -1;
            }
        }

        Matcher semver = semverPattern.matcher(value);
        if(semver.matches()){
            try{
                int major = Integer.parseInt(semver.group(1));
                int minor = Integer.parseInt(semver.group(2));
                int patch = Integer.parseInt(semver.group(3));
                return major * 10000 + minor * 100 + patch;
            }catch(Throwable ignored){
                return -1;
            }
        }

        return -1;
    }

    private static int code(String majorText, int build){
        try{
            int major = Integer.parseInt(majorText);
            if(major < 0 || build < 0 || build > 9999) return -1;
            return major * 10000 + build;
        }catch(Throwable ignored){
            return -1;
        }
    }

    public static int compareVersions(String a, String b){
        int codeA = versionCode(a);
        int codeB = versionCode(b);
        if(codeA >= 0 && codeB >= 0) return Integer.compare(codeA, codeB);

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

    public static int[] parseVersionParts(String v){
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
        for(int i = 0; i < parts.size(); i++){
            out[i] = parts.get(i);
        }
        return out;
    }
}
