package foreignservertranslator;

import arc.Core;
import arc.struct.Seq;
import arc.util.Http;
import arc.util.Log;
import arc.util.serialization.Jval;
import arc.util.serialization.Jval.JsonMap;

public final class LanguageCatalog{
    private static final String microsoftLanguages = "https://api.cognitive.microsofttranslator.com/languages?api-version=3.0&scope=translation";
    private static Seq<Language> languages = fallbackLanguages();
    private static boolean loading;
    private static boolean requested;
    private static String status = "fallback";

    private LanguageCatalog(){
    }

    public static void init(){
        if(requested) return;
        requested = true;
        refresh();
    }

    public static void refresh(){
        if(loading) return;
        loading = true;
        status = "loading";

        Http.get(microsoftLanguages)
            .header("Accept-Language", Core.bundle == null ? "en" : Core.bundle.getLocale().toLanguageTag())
            .timeout(10000)
            .error(error -> {
                loading = false;
                status = "fallback";
                Log.warn("ForeignServerTranslator failed to load Microsoft language list: @", error.getMessage());
            })
            .submit(response -> {
                try{
                    Seq<Language> parsed = parseMicrosoftLanguages(response.getResultAsString());
                    Core.app.post(() -> {
                        if(!parsed.isEmpty()){
                            languages = parsed;
                            status = "online";
                        }else{
                            status = "fallback";
                        }
                        loading = false;
                    });
                }catch(Throwable error){
                    loading = false;
                    status = "fallback";
                    Log.warn("ForeignServerTranslator failed to parse Microsoft language list.", error);
                }
            });
    }

    public static Seq<Language> languages(){
        return languages;
    }

    public static String status(){
        return status;
    }

    public static String display(String code){
        Language language = find(code);
        return language == null ? code : language.label();
    }

    public static Language find(String code){
        if(code == null) return null;
        for(Language language : languages){
            if(language.code.equalsIgnoreCase(code)) return language;
        }
        return null;
    }

    private static Seq<Language> parseMicrosoftLanguages(String value){
        Seq<Language> result = new Seq<>();
        JsonMap map = Jval.read(value).get("translation").asObject();
        for(int i = 0; i < map.size; i++){
            String code = map.keys[i];
            Jval data = map.values[i];
            String name = data.getString("name", code);
            String nativeName = data.getString("nativeName", name);
            result.add(new Language(code, name, nativeName));
        }
        result.sort((a, b) -> a.label().compareToIgnoreCase(b.label()));
        return result;
    }

    private static Seq<Language> fallbackLanguages(){
        Seq<Language> result = new Seq<>();
        add(result, "ar", "Arabic", "العربية");
        add(result, "bg", "Bulgarian", "Български");
        add(result, "ca", "Catalan", "Català");
        add(result, "cs", "Czech", "Čeština");
        add(result, "da", "Danish", "Dansk");
        add(result, "de", "German", "Deutsch");
        add(result, "el", "Greek", "Ελληνικά");
        add(result, "en", "English", "English");
        add(result, "es", "Spanish", "Español");
        add(result, "et", "Estonian", "Eesti");
        add(result, "fa", "Persian", "فارسی");
        add(result, "fi", "Finnish", "Suomi");
        add(result, "fr", "French", "Français");
        add(result, "he", "Hebrew", "עברית");
        add(result, "hi", "Hindi", "हिन्दी");
        add(result, "hr", "Croatian", "Hrvatski");
        add(result, "hu", "Hungarian", "Magyar");
        add(result, "id", "Indonesian", "Indonesia");
        add(result, "it", "Italian", "Italiano");
        add(result, "ja", "Japanese", "日本語");
        add(result, "ko", "Korean", "한국어");
        add(result, "lt", "Lithuanian", "Lietuvių");
        add(result, "lv", "Latvian", "Latviešu");
        add(result, "ms", "Malay", "Melayu");
        add(result, "nl", "Dutch", "Nederlands");
        add(result, "pl", "Polish", "Polski");
        add(result, "pt", "Portuguese", "Português");
        add(result, "ro", "Romanian", "Română");
        add(result, "ru", "Russian", "Русский");
        add(result, "sk", "Slovak", "Slovenčina");
        add(result, "sl", "Slovenian", "Slovenščina");
        add(result, "sr-Cyrl", "Serbian (Cyrillic)", "Српски");
        add(result, "sr-Latn", "Serbian (Latin)", "Srpski");
        add(result, "sv", "Swedish", "Svenska");
        add(result, "th", "Thai", "ไทย");
        add(result, "tr", "Turkish", "Türkçe");
        add(result, "uk", "Ukrainian", "Українська");
        add(result, "ur", "Urdu", "اردو");
        add(result, "vi", "Vietnamese", "Tiếng Việt");
        add(result, "zh-Hans", "Chinese Simplified", "简体中文");
        add(result, "zh-Hant", "Chinese Traditional", "繁體中文");
        return result;
    }

    private static void add(Seq<Language> result, String code, String name, String nativeName){
        result.add(new Language(code, name, nativeName));
    }

    public static final class Language{
        public final String code;
        public final String name;
        public final String nativeName;

        public Language(String code, String name, String nativeName){
            this.code = code;
            this.name = name;
            this.nativeName = nativeName;
        }

        public String label(){
            String localized = Core.bundle == null ? name : Core.bundle.get("fst.language." + code.replace('-', '_'), name);
            return nativeName == null || nativeName.equals(localized) ? localized + " (" + code + ")" : localized + " / " + nativeName + " (" + code + ")";
        }
    }
}
