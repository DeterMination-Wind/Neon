package pinyinsearchsupport.match;

import arc.util.Log;
import arc.util.Strings;
import net.sourceforge.pinyin4j.PinyinHelper;
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType;
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat;
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType;
import net.sourceforge.pinyin4j.format.HanyuPinyinVCharType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;

public final class PinyinIndex{
    static final int MAX_CACHE_ENTRIES = 2048;
    static final int MAX_INDEX_CODE_POINTS = 512;
    private static final int MAX_PINYIN4J_ERRORS = 8;

    private static final HanyuPinyinOutputFormat format = new HanyuPinyinOutputFormat();
    private static final Map<String, Profile> cache = new LinkedHashMap<String, Profile>(256, 0.75f, true){
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Profile> eldest){
            return size() > MAX_CACHE_ENTRIES;
        }
    };

    private static volatile boolean pinyin4jUnavailable;
    private static volatile int pinyin4jErrorCount;

    static{
        format.setCaseType(HanyuPinyinCaseType.LOWERCASE);
        format.setToneType(HanyuPinyinToneType.WITHOUT_TONE);
        format.setVCharType(HanyuPinyinVCharType.WITH_V);
    }

    private PinyinIndex(){}

    public static Profile of(String raw){
        if(raw == null || raw.isEmpty()) return Profile.EMPTY;

        String stripped;
        try{
            stripped = Strings.stripColors(raw);
        }catch(Throwable t){
            stripped = raw;
        }
        if(stripped == null || stripped.isEmpty()) return Profile.EMPTY;

        String key = truncateCodePoints(stripped, MAX_INDEX_CODE_POINTS);
        if(key.isEmpty()) return Profile.EMPTY;

        synchronized(cache){
            Profile cached = cache.get(key);
            if(cached != null) return cached;
        }

        Profile built = build(key);
        synchronized(cache){
            Profile existing = cache.get(key);
            if(existing != null) return existing;
            cache.put(key, built);
        }
        return built;
    }

    static int cacheSize(){
        synchronized(cache){
            return cache.size();
        }
    }

    static void clearCache(){
        synchronized(cache){
            cache.clear();
        }
    }

    public static boolean isCjk(char c){
        return isCjk((int)c);
    }

    public static boolean isCjk(int cp){
        return (cp >= 0x4E00 && cp <= 0x9FFF)
            || (cp >= 0x3400 && cp <= 0x4DBF)
            || (cp >= 0xF900 && cp <= 0xFAFF)
            || (cp >= 0x20000 && cp <= 0x2A6DF);
    }

    private static String truncateCodePoints(String text, int maxCodePoints){
        if(text == null || text.isEmpty() || maxCodePoints <= 0) return "";
        int len = text.length();
        if(len <= maxCodePoints) return text;
        int offset = 0;
        int count = 0;
        while(offset < len && count < maxCodePoints){
            int cp = text.codePointAt(offset);
            offset += Character.charCount(cp);
            count++;
        }
        return offset >= len ? text : text.substring(0, offset);
    }

    private static Profile build(String text){
        int len = text.length();
        ArrayList<String[]> tokenList = new ArrayList<String[]>(Math.min(len, MAX_INDEX_CODE_POINTS));
        StringBuilder primaryBuf = new StringBuilder(Math.min(len * 2, MAX_INDEX_CODE_POINTS * 4));
        StringBuilder initialsBuf = new StringBuilder(Math.min(len, MAX_INDEX_CODE_POINTS));
        StringBuilder lowerBuf = new StringBuilder(len);

        int i = 0;
        while(i < len){
            int cp = text.codePointAt(i);
            int charCount = Character.charCount(cp);
            i += charCount;

            if(Character.isISOControl(cp)){
                tokenList.add(new String[0]);
                continue;
            }

            if(charCount == 1){
                char c = (char)cp;
                // Skip unpaired surrogates safely.
                if(Character.isSurrogate(c)){
                    tokenList.add(new String[0]);
                    continue;
                }
                char lowerChar = Character.toLowerCase(c);
                lowerBuf.append(lowerChar);

                if(isCjk(cp)){
                    String[] uniq = lookupPinyin(c);
                    if(uniq != null && uniq.length > 0){
                        tokenList.add(uniq);
                        primaryBuf.append(uniq[0]);
                        initialsBuf.append(uniq[0].charAt(0));
                    }else{
                        String s = String.valueOf(c);
                        tokenList.add(new String[]{s});
                        primaryBuf.append(c);
                        initialsBuf.append(c);
                    }
                }else if(Character.isLetterOrDigit(c)){
                    String s = String.valueOf(lowerChar);
                    tokenList.add(new String[]{s});
                    primaryBuf.append(lowerChar);
                    initialsBuf.append(lowerChar);
                }else{
                    tokenList.add(new String[0]);
                }
            }else{
                int lowerCp = Character.toLowerCase(cp);
                lowerBuf.appendCodePoint(lowerCp);
                if(Character.isLetterOrDigit(cp) || isCjk(cp)){
                    String s = new String(Character.toChars(lowerCp));
                    tokenList.add(new String[]{s});
                    primaryBuf.append(s);
                    initialsBuf.append(s);
                }else{
                    tokenList.add(new String[0]);
                }
            }
        }

        String[][] tokens = tokenList.toArray(new String[0][]);
        return new Profile(text, lowerBuf.toString(), primaryBuf.toString(), initialsBuf.toString(), tokens);
    }

    private static String[] lookupPinyin(char c){
        if(pinyin4jUnavailable) return null;
        try{
            String[] arr = PinyinHelper.toHanyuPinyinStringArray(c, format);
            if(arr == null || arr.length == 0) return null;
            return dedupe(arr);
        }catch(LinkageError err){
            pinyin4jUnavailable = true;
            Log.warn("[PinyinSearchSupport] pinyin4j linkage unavailable, degrading to raw matching: @", err.getMessage());
            return null;
        }catch(Throwable t){
            int count = ++pinyin4jErrorCount;
            if(count <= 3){
                Log.warn("[PinyinSearchSupport] pinyin4j lookup failed: @", t.getMessage());
            }
            if(count >= MAX_PINYIN4J_ERRORS){
                pinyin4jUnavailable = true;
            }
            return null;
        }
    }

    private static String[] dedupe(String[] arr){
        LinkedHashSet<String> seen = new LinkedHashSet<String>(arr.length);
        for(int i = 0; i < arr.length; i++){
            String s = arr[i];
            if(s == null || s.isEmpty()) continue;
            String clean = stripTrailingDigits(s);
            if(!clean.isEmpty()){
                seen.add(clean);
            }
        }
        if(seen.isEmpty()) return null;
        return seen.toArray(new String[0]);
    }

    private static String stripTrailingDigits(String s){
        int end = s.length();
        while(end > 0){
            char c = s.charAt(end - 1);
            if(c >= '0' && c <= '9'){
                end--;
            }else{
                break;
            }
        }
        return end == s.length() ? s : s.substring(0, end);
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
