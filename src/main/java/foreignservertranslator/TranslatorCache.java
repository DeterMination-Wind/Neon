package foreignservertranslator;

import arc.Core;
import arc.struct.ObjectSet;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public final class TranslatorCache{
    private static final String translationIndexKey = "fst.translationCache.keys";
    private static final String translationValuePrefix = "fst.translationCache.value.";
    private static final String serverLanguageIndexKey = "fst.serverLanguageCache.keys";
    private static final String serverLanguageValuePrefix = "fst.serverLanguageCache.value.";

    private static ObjectSet<String> translationKeys;
    private static ObjectSet<String> serverLanguageKeys;

    private TranslatorCache(){
    }

    public static void init(){
        translationKeys = Core.settings.getJson(translationIndexKey, ObjectSet.class, String.class, ObjectSet::new);
        serverLanguageKeys = Core.settings.getJson(serverLanguageIndexKey, ObjectSet.class, String.class, ObjectSet::new);
    }

    public static String getTranslation(String server, String sourceLanguage, String targetLanguage, String text){
        String key = translationKey(server, sourceLanguage, targetLanguage, text);
        return Core.settings.getString(translationValuePrefix + key, "");
    }

    public static void putTranslation(String server, String sourceLanguage, String targetLanguage, String text, String translated){
        if(translated == null || translated.trim().isEmpty()) return;

        ensureLoaded();
        String key = translationKey(server, sourceLanguage, targetLanguage, text);
        translationKeys.add(key);
        Core.settings.put(translationValuePrefix + key, translated);
        Core.settings.putJson(translationIndexKey, String.class, translationKeys);
    }

    public static String getServerLanguage(String server){
        if(server == null || server.isEmpty()) return "";
        String key = serverLanguageKey(server);
        return Core.settings.getString(serverLanguageValuePrefix + key, "");
    }

    public static void putServerLanguage(String server, String language){
        if(server == null || server.isEmpty() || language == null || language.trim().isEmpty()) return;

        ensureLoaded();
        String key = serverLanguageKey(server);
        serverLanguageKeys.add(key);
        Core.settings.put(serverLanguageValuePrefix + key, language.trim());
        Core.settings.putJson(serverLanguageIndexKey, String.class, serverLanguageKeys);
    }

    public static void clearTranslations(){
        ensureLoaded();
        for(String key : translationKeys){
            Core.settings.remove(translationValuePrefix + key);
        }
        translationKeys.clear();
        Core.settings.putJson(translationIndexKey, String.class, translationKeys);
    }

    public static void clearServerLanguages(){
        ensureLoaded();
        for(String key : serverLanguageKeys){
            Core.settings.remove(serverLanguageValuePrefix + key);
        }
        serverLanguageKeys.clear();
        Core.settings.putJson(serverLanguageIndexKey, String.class, serverLanguageKeys);
    }

    public static int translationCount(){
        ensureLoaded();
        return translationKeys.size;
    }

    public static int serverLanguageCount(){
        ensureLoaded();
        return serverLanguageKeys.size;
    }

    private static void ensureLoaded(){
        if(translationKeys == null || serverLanguageKeys == null){
            init();
        }
    }

    private static String translationKey(String server, String sourceLanguage, String targetLanguage, String text){
        return sha256(clean(server) + "\n" + clean(sourceLanguage) + "\n" + clean(targetLanguage) + "\n" + clean(text));
    }

    private static String serverLanguageKey(String server){
        return sha256(clean(server));
    }

    private static String clean(String value){
        return value == null ? "" : value.trim();
    }

    private static String sha256(String value){
        try{
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(bytes.length * 2);
            for(byte b : bytes){
                result.append(Character.forDigit((b >> 4) & 0xf, 16));
                result.append(Character.forDigit(b & 0xf, 16));
            }
            return result.toString();
        }catch(Throwable error){
            throw new RuntimeException("SHA-256 is not available.", error);
        }
    }
}
