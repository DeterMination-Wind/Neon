package modupdater.features;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class VersionUtil{
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

    public static int compareVersions(String a, String b){
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
