package pinyinsearchsupport.match;

import arc.struct.ObjectMap;
import arc.util.Strings;
import net.sourceforge.pinyin4j.PinyinHelper;
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType;
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat;
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType;
import net.sourceforge.pinyin4j.format.HanyuPinyinVCharType;

public final class PinyinIndex{
    private static final HanyuPinyinOutputFormat format = new HanyuPinyinOutputFormat();
    private static final ObjectMap<String, Profile> cache = new ObjectMap<>();

    static{
        format.setCaseType(HanyuPinyinCaseType.LOWERCASE);
        format.setToneType(HanyuPinyinToneType.WITHOUT_TONE);
        format.setVCharType(HanyuPinyinVCharType.WITH_V);
    }

    private PinyinIndex(){}

    public static Profile of(String raw){
        if(raw == null || raw.isEmpty()) return Profile.EMPTY;
        String key = Strings.stripColors(raw);
        Profile p = cache.get(key);
        if(p != null) return p;
        p = build(key);
        cache.put(key, p);
        return p;
    }

    public static boolean isCjk(char c){
        return (c >= 0x4E00 && c <= 0x9FFF) || (c >= 0x3400 && c <= 0x4DBF);
    }

    private static Profile build(String text){
        String[][] tokens = new String[text.length()][];
        StringBuilder primaryBuf = new StringBuilder(text.length() * 2);
        StringBuilder initialsBuf = new StringBuilder(text.length());
        StringBuilder lowerBuf = new StringBuilder(text.length());

        for(int i = 0; i < text.length(); i++){
            char c = text.charAt(i);
            lowerBuf.append(Character.toLowerCase(c));
            if(isCjk(c)){
                String[] arr;
                try{
                    arr = PinyinHelper.toHanyuPinyinStringArray(c, format);
                }catch(Throwable t){
                    arr = null;
                }
                if(arr != null && arr.length > 0){
                    String[] uniq = dedupe(arr);
                    tokens[i] = uniq;
                    primaryBuf.append(uniq[0]);
                    initialsBuf.append(uniq[0].charAt(0));
                }else{
                    tokens[i] = new String[]{String.valueOf(c)};
                    primaryBuf.append(c);
                    initialsBuf.append(c);
                }
            }else if(Character.isLetterOrDigit(c)){
                String s = String.valueOf(Character.toLowerCase(c));
                tokens[i] = new String[]{s};
                primaryBuf.append(s);
                initialsBuf.append(s);
            }else{
                tokens[i] = new String[0];
            }
        }

        return new Profile(text, lowerBuf.toString(), primaryBuf.toString(), initialsBuf.toString(), tokens);
    }

    private static String[] dedupe(String[] arr){
        java.util.LinkedHashSet<String> seen = new java.util.LinkedHashSet<>();
        for(String s : arr){
            if(s == null || s.isEmpty()) continue;
            String clean = s.replaceAll("[0-9]+$", "");
            if(!clean.isEmpty()) seen.add(clean);
        }
        if(seen.isEmpty()) return new String[]{arr[0]};
        return seen.toArray(new String[0]);
    }

    public static final class Profile{
        public static final Profile EMPTY = new Profile("", "", "", "", new String[0][]);

        public final String raw;
        public final String lower;
        public final String pinyinFull;
        public final String initials;
        public final String[][] tokens;

        Profile(String raw, String lower, String pinyinFull, String initials, String[][] tokens){
            this.raw = raw;
            this.lower = lower;
            this.pinyinFull = pinyinFull;
            this.initials = initials;
            this.tokens = tokens;
        }
    }
}
