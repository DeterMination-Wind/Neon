package foreignservertranslator;

import arc.Core;
import arc.Events;
import arc.func.Cons;
import arc.struct.ObjectMap;
import arc.struct.ObjectSet;
import arc.struct.Seq;
import arc.util.Log;
import arc.util.Strings;
import arc.util.Time;
import mindustry.Vars;
import mindustry.game.EventType.ClientPreConnectEvent;
import mindustry.game.EventType.PlayerChatEvent;
import mindustry.gen.Player;
import mindustry.ui.fragments.TranslatorChatFragment;
import mindustry.ui.fragments.TranslatorChatFragment.TranslationSlot;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Properties;

public final class TranslatorFeature{
    public static final String openAiEnabledKey = "fst.openAiEnabled";
    public static final String baseUrlKey = "fst.baseUrl";
    public static final String apiKeyKey = "fst.apiKey";
    public static final String modelKey = "fst.model";
    public static final String incomingLanguageKey = "fst.incomingLanguage";
    public static final String outgoingLanguageKey = "fst.outgoingLanguage";
    public static final String contextMessagesKey = "fst.contextMessages";
    public static final String bundleHintsKey = "fst.bundleHints";
    public static final String fullBundleContextKey = "fst.fullBundleContext";
    private static final String serversKey = "fst.foreignServers";
    private static final int maxStoredContext = 40;
    private static final int maxBundleHints = 16;
    private static final long recentPlayerLineMillis = 15000L;
    private static final String languageOverridePrefixes = "'\u2019";

    private static final ObjectMap<String, String> languageAliases = new ObjectMap<>();
    private static final ObjectMap<String, GameBundle> bundleCache = new ObjectMap<>();
    private static final ObjectMap<String, String> fullBundleCache = new ObjectMap<>();
    private static final Seq<ChatEntry> chatHistory = new Seq<>();
    private static final Seq<RecentPlayerLine> recentPlayerLines = new Seq<>();
    private static final Seq<PendingServerTranslation> pendingServerTranslations = new Seq<>();
    private static final ObjectSet<String> detectingServers = new ObjectSet<>();
    private static ObjectSet<String> foreignServers;
    private static String selectedServerKey;
    private static boolean initialized;

    /** Returns the current server key for cache access (package-private). */
    static String selectedServerKey(){
        return selectedServerKey;
    }

    private TranslatorFeature(){
    }

    public static void init(){
        if(initialized) return;
        initialized = true;

        Core.settings.defaults(openAiEnabledKey, false);
        Core.settings.defaults(baseUrlKey, "");
        Core.settings.defaults(apiKeyKey, "");
        Core.settings.defaults(modelKey, "gpt-4o-mini");
        Core.settings.defaults(incomingLanguageKey, "zh-Hans");
        Core.settings.defaults(outgoingLanguageKey, "en");
        Core.settings.defaults(contextMessagesKey, 3);
        Core.settings.defaults(bundleHintsKey, false);
        Core.settings.defaults(fullBundleContextKey, false);
        Core.settings.defaults("fst.marker.openAiEnabled", false);
        Core.settings.defaults("fst.marker.baseUrl", "");
        Core.settings.defaults("fst.marker.apiKey", "");
        Core.settings.defaults("fst.marker.model", "gpt-4o-mini");
        TranslatorCache.init();

        alias("EN", "en", "US", "UK", "GB");
        alias("JA", "ja", "JP", "JR");
        alias("RU", "ru");
        alias("ZH", "zh-Hans", "CN");
        alias("TW", "zh-Hant", "HK");
        alias("KO", "ko", "KR");
        alias("DE", "de");
        alias("FR", "fr");
        alias("ES", "es");
        alias("PT", "pt", "BR");
        alias("IT", "it");
        alias("TR", "tr");
        alias("PL", "pl");
        alias("TH", "th");
        alias("VI", "vi");
        alias("ID", "id");
        alias("AR", "ar");

        foreignServers = Core.settings.getJson(serversKey, ObjectSet.class, String.class, ObjectSet::new);

        Events.on(ClientPreConnectEvent.class, event -> {
            if(event.host != null){
                selectedServerKey = serverKey(event.host.address, event.host.port);
                chatHistory.clear();
                recentPlayerLines.clear();
                pendingServerTranslations.clear();
                detectingServers.clear();
            }
        });

        Events.on(PlayerChatEvent.class, event -> onPlayerMessage(event.player, event.message));
    }

    public static void installJoinDialogButtons(){
        JoinDialogButtonPatcher.install();
    }

    public static void selectServer(String key){
        selectedServerKey = key;
    }

    public static boolean isCurrentServerForeign(){
        return selectedServerKey != null && isForeign(selectedServerKey);
    }

    public static boolean isForeign(String key){
        return foreignServers != null && foreignServers.contains(key);
    }

    public static void toggleForeign(String key){
        if(foreignServers == null) foreignServers = new ObjectSet<>();
        if(!foreignServers.remove(key)){
            foreignServers.add(key);
        }
        Core.settings.putJson(serversKey, String.class, foreignServers);
    }

    public static String serverKey(String ip, int port){
        return ip.toLowerCase(Locale.ROOT) + ":" + port;
    }

    public static OutgoingMessage parseOutgoing(String message){
        String prefix = "";
        String body = message;
        if(body.startsWith("/t ") || body.startsWith("/a ")){
            prefix = body.substring(0, 3);
            body = body.substring(3).trim();
        }else if(body.startsWith("/")){
            return new OutgoingMessage(message, "", null, false);
        }

        String target = Core.settings.getString(outgoingLanguageKey, "en").trim();
        String overrideToken = null;
        LanguageOverride override = parseLanguageOverride(body);
        if(override != null){
            target = override.language;
            body = override.body;
            overrideToken = override.token;
        }
        return new OutgoingMessage(prefix, body, target.isEmpty() ? "en" : target, true, overrideToken);
    }

    public static TranslationContext buildTranslationContext(String currentMessage){
        return buildTranslationContext(currentMessage, null);
    }

    public static TranslationContext buildTranslationContext(String currentMessage, String targetLanguage){
        Seq<ChatEntry> entries = contextSnapshot();
        String sourceLanguage = detectBundleLanguage(currentMessage);
        Seq<String> bundleHints = new Seq<>();
        String fullBundleContext = "";
        if(TranslationService.isOpenAiEnabled()){
            if(Core.settings.getBool(fullBundleContextKey, false)){
                fullBundleContext = buildFullBundleContext(sourceLanguage, targetLanguage);
            }else if(Core.settings.getBool(bundleHintsKey, false)){
                bundleHints = findBundleHints(currentMessage, entries, targetLanguage);
            }
        }
        return new TranslationContext(entries, bundleHints, null, null, normalizeLanguageCode(sourceLanguage), false, fullBundleContext);
    }

    public static TranslationContext buildOutgoingTranslationContext(String currentMessage, OutgoingMessage outgoing){
        TranslationContext context = buildTranslationContext(currentMessage, outgoing == null ? null : outgoing.targetLanguage);
        if(outgoing != null && outgoing.overrideToken != null){
            return context.withOutgoingOverride(outgoing.overrideToken, outgoing.targetLanguage);
        }
        return context;
    }

    public static boolean shouldTranslateServerText(String text){
        if(!isCurrentServerForeign() || text == null) return false;

        String normalized = normalizeForMatch(text);
        if(normalized.isEmpty() || normalized.startsWith("=>")) return false;
        return !isLocalBundleReference(text);
    }

    public static void translateServerChatLine(String renderedMessage){
        if(!shouldTranslateServerText(renderedMessage) || isRecentPlayerChatLine(renderedMessage)) return;

        TranslationSlot slot = reserveIncomingSlot("", renderedMessage);
        translateServerText(renderedMessage, translated -> {
            if(isCurrentServerForeign()){
                completeIncomingSlot(slot, "[lightgray]=> " + renderMarkup(translated) + "[]");
            }
        }, error -> {
            Log.warn("ForeignServerTranslator failed to translate server chat line: @", error.getMessage());
            completeIncomingSlot(slot, Core.bundle.get("fst.translate.server.failed", "[scarlet]Failed to translate server message.[]"));
        });
    }

    public static void translateServerText(String text, Cons<String> success, Cons<Throwable> failure){
        String target = Core.settings.getString(incomingLanguageKey, "zh-Hans").trim();
        translateServerText(text, target.isEmpty() ? "zh-Hans" : target, success, failure);
    }

    public static void translateServerText(String text, String targetLanguage, Cons<String> success, Cons<Throwable> failure){
        if(!shouldTranslateServerText(text)){
            success.get(text);
            return;
        }

        String server = selectedServerKey == null ? "" : selectedServerKey;
        String target = targetLanguage == null || targetLanguage.trim().isEmpty() ? "zh-Hans" : targetLanguage.trim();
        if(!TranslationService.isOpenAiEnabled()){
            translateServerTextWithSource(server, "auto", target, text, success, failure);
            return;
        }

        if(!TranslationService.hasOpenAiConfig()){
            failure.get(new IllegalStateException("OpenAI translation is enabled but Base URL or API key is missing."));
            return;
        }

        String sourceLanguage = TranslatorCache.getServerLanguage(server);
        if(sourceLanguage != null && !sourceLanguage.trim().isEmpty()){
            translateServerTextWithSource(server, sourceLanguage.trim(), target, text, success, failure);
            return;
        }

        pendingServerTranslations.add(new PendingServerTranslation(server, text, target, success, failure));
        startServerLanguageDetection(server, text);
    }

    public static void recordOwnMessage(String message){
        String name = Vars.player == null ? "You" : safeSpeaker(Vars.player.plainName());
        recordChat(name.isEmpty() ? "You" : name, message);
    }

    private static void translateServerTextWithSource(String server, String sourceLanguage, String targetLanguage, String text, Cons<String> success, Cons<Throwable> failure){
        String source = normalizeLanguageCode(sourceLanguage);
        if(source.isEmpty()) source = "auto";
        String target = targetLanguage == null || targetLanguage.trim().isEmpty() ? "zh-Hans" : targetLanguage.trim();
        final String cacheSource = source;

        String cached = TranslatorCache.getTranslation(server, cacheSource, target, text);
        if(cached != null && !cached.isEmpty()){
            success.get(cached);
            return;
        }

        TranslationContext context = buildServerTranslationContext(text, cacheSource, target);
        TranslationService.translate(text, target, context, translated -> {
            TranslatorCache.putTranslation(server, cacheSource, target, text, translated);
            success.get(translated);
        }, failure);
    }

    private static TranslationContext buildServerTranslationContext(String text, String sourceLanguage, String targetLanguage){
        Seq<String> bundleHints = new Seq<>();
        String fullBundleContext = "";
        if(TranslationService.isOpenAiEnabled()){
            if(Core.settings.getBool(fullBundleContextKey, false)){
                fullBundleContext = buildFullBundleContext(sourceLanguage, targetLanguage);
            }else if(Core.settings.getBool(bundleHintsKey, false)){
                bundleHints = findBundleHints(text, new Seq<>(), targetLanguage, sourceLanguage);
            }
        }
        return new TranslationContext(new Seq<>(), bundleHints, null, null, normalizeLanguageCode(sourceLanguage), true, fullBundleContext);
    }

    private static void startServerLanguageDetection(String server, String sample){
        if(server == null || server.isEmpty() || !detectingServers.add(server)) return;

        Log.info("[Info] ForeignServerTranslator detecting server language for @ from server UI/system text: @", server, oneLine(sample));
        TranslationService.detectLanguage(sample, detected -> {
            String language = normalizeLanguageCode(detected);
            detectingServers.remove(server);
            if(language.isEmpty()){
                failPendingServerTranslations(server, new IllegalStateException("OpenAI language detection returned an empty language code."));
                return;
            }

            TranslatorCache.putServerLanguage(server, language);
            Log.info("[Info] ForeignServerTranslator detected server language for @: @", server, language);
            drainPendingServerTranslations(server, language);
        }, error -> {
            detectingServers.remove(server);
            failPendingServerTranslations(server, error);
        });
    }

    private static void drainPendingServerTranslations(String server, String language){
        Seq<PendingServerTranslation> drained = new Seq<>();
        for(int i = pendingServerTranslations.size - 1; i >= 0; i--){
            PendingServerTranslation pending = pendingServerTranslations.get(i);
            if(pending.server.equals(server)){
                drained.add(pending);
                pendingServerTranslations.remove(i);
            }
        }

        for(PendingServerTranslation pending : drained){
            translateServerTextWithSource(server, language, pending.targetLanguage, pending.text, pending.success, pending.failure);
        }
    }

    private static void failPendingServerTranslations(String server, Throwable error){
        for(int i = pendingServerTranslations.size - 1; i >= 0; i--){
            PendingServerTranslation pending = pendingServerTranslations.get(i);
            if(pending.server.equals(server)){
                pendingServerTranslations.remove(i);
                pending.failure.get(error);
            }
        }
    }

    public static String renderMarkup(String value){
        if(value == null) return "";
        return value.replace('\n', ' ').replace('\r', ' ').trim();
    }

    public static String translateFailed(){
        return Core.bundle.get("fst.translate.failed", "[scarlet]Translation failed; message was not sent.[]");
    }

    private static void onPlayerMessage(Player sender, String message){
        markRecentPlayerLine(sender, message);
        if(!isCurrentServerForeign() || sender == null || sender == Vars.player || message == null || message.trim().isEmpty()) return;

        String target = Core.settings.getString(incomingLanguageKey, "zh-Hans").trim();
        TranslationContext context = buildTranslationContext(message, target);
        String speaker = safeSpeaker(sender.plainName());
        recordChat(speaker, message);
        TranslationSlot slot = reserveIncomingSlot(speaker, message);
        TranslationService.translate(message, target.isEmpty() ? "zh-Hans" : target, context, translated -> {
            if(isCurrentServerForeign() && Vars.ui != null && Vars.ui.chatfrag != null){
                completeIncomingSlot(slot, "[lightgray]=> " + renderMarkup(translated) + "[]");
            }
        }, error -> {
            Log.warn("ForeignServerTranslator failed to translate received chat: @", error.getMessage());
            completeIncomingSlot(slot, Core.bundle.get("fst.translate.receive.failed", "[scarlet]Failed to translate received chat.[]"));
        });
    }

    private static TranslationSlot reserveIncomingSlot(String speaker, String message){
        if(Vars.ui != null && Vars.ui.chatfrag instanceof TranslatorChatFragment){
            TranslatorChatFragment chat = (TranslatorChatFragment)Vars.ui.chatfrag;
            return chat.reserveTranslationSlot(speaker, message, Core.bundle.get("fst.translate.pending", "=>..."));
        }
        return null;
    }

    private static void completeIncomingSlot(TranslationSlot slot, String message){
        if(Vars.ui != null && Vars.ui.chatfrag instanceof TranslatorChatFragment && slot != null){
            TranslatorChatFragment chat = (TranslatorChatFragment)Vars.ui.chatfrag;
            chat.completeTranslationSlot(slot, message);
        }else if(slot == null && Vars.ui != null && Vars.ui.chatfrag != null){
            Vars.ui.chatfrag.addMessage(message);
        }
    }

    private static void markRecentPlayerLine(Player sender, String message){
        if(sender == null || message == null || message.trim().isEmpty()) return;
        recentPlayerLines.add(new RecentPlayerLine(safeSpeaker(sender.plainName()), message, Time.millis()));
        cleanupRecentPlayerLines();
    }

    private static boolean isRecentPlayerChatLine(String renderedMessage){
        cleanupRecentPlayerLines();
        String rendered = normalizeForMatch(renderedMessage);
        if(rendered.isEmpty()) return false;

        for(RecentPlayerLine line : recentPlayerLines){
            if(line.message.isEmpty() || !rendered.contains(line.message)) continue;
            if(line.speaker.isEmpty() || rendered.contains(line.speaker)) return true;
        }
        return false;
    }

    private static void cleanupRecentPlayerLines(){
        long now = Time.millis();
        for(int i = recentPlayerLines.size - 1; i >= 0; i--){
            if(now - recentPlayerLines.get(i).time > recentPlayerLineMillis){
                recentPlayerLines.remove(i);
            }
        }
        while(recentPlayerLines.size > 40){
            recentPlayerLines.remove(0);
        }
    }

    private static Seq<ChatEntry> contextSnapshot(){
        int count = Math.max(0, Core.settings.getInt(contextMessagesKey, 3));
        Seq<ChatEntry> result = new Seq<>();
        int start = Math.max(0, chatHistory.size - count);
        for(int i = start; i < chatHistory.size; i++){
            result.add(chatHistory.get(i));
        }
        return result;
    }

    private static void recordChat(String speaker, String message){
        if(message == null || message.trim().isEmpty()) return;
        chatHistory.add(new ChatEntry(safeSpeaker(speaker), oneLine(message)));
        while(chatHistory.size > maxStoredContext){
            chatHistory.remove(0);
        }
    }

    private static Seq<String> findBundleHints(String currentMessage, Seq<ChatEntry> context, String targetLanguage){
        return findBundleHints(currentMessage, context, targetLanguage, null);
    }

    private static Seq<String> findBundleHints(String currentMessage, Seq<ChatEntry> context, String targetLanguage, String sourceLanguageOverride){
        Seq<String> sourceTexts = new Seq<>();
        sourceTexts.add(currentMessage);
        for(ChatEntry entry : context){
            sourceTexts.add(entry.message);
        }

        Seq<String> result = new Seq<>();
        ObjectSet<String> seen = new ObjectSet<>();
        String sourceLanguage = sourceLanguageOverride == null || sourceLanguageOverride.trim().isEmpty() ? detectBundleLanguage(currentMessage, sourceTexts) : normalizeBundleLanguage(sourceLanguageOverride);
        String target = normalizeBundleLanguage(targetLanguage);
        GameBundle sourceBundle = bundleForLanguage(sourceLanguage);
        GameBundle targetBundle = bundleForLanguage(target);
        GameBundle baseBundle = bundleForLanguage("en");
        if(sourceBundle == null || targetBundle == null || baseBundle == null) return result;

        ObjectSet<String> keys = new ObjectSet<>();
        addBundleKeys(keys, sourceBundle);
        addBundleKeys(keys, targetBundle);
        addBundleKeys(keys, baseBundle);

        for(String key : keys){
            String sourceValue = sourceBundle.get(key, null);
            if(sourceValue == null) continue;

            String candidate = normalizedBundleValue(sourceValue);
            if(!isMatchableBundleValue(key, candidate)) continue;

            for(String source : sourceTexts){
                if(matchesBundleValue(normalizeForMatch(source), candidate) && seen.add(key)){
                    String targetValue = targetBundle.get(key, sourceValue);
                    result.add(key + " | " + sourceLanguage + ": " + oneLine(Strings.stripColors(sourceValue)) + " | " + target + ": " + oneLine(Strings.stripColors(targetValue)));
                    break;
                }
            }

            if(result.size >= maxBundleHints) break;
        }

        Log.info("[Info] ForeignServerTranslator bundle hint scan: source=@ target=@ matches=@ message=@", sourceLanguage, target, result.size, oneLine(currentMessage));
        return result;
    }

    private static String buildFullBundleContext(String sourceLanguage, String targetLanguage){
        String source = normalizeBundleLanguage(sourceLanguage);
        String target = normalizeBundleLanguage(targetLanguage);
        String cacheKey = source + "->" + target;
        if(fullBundleCache.containsKey(cacheKey)) return fullBundleCache.get(cacheKey);

        GameBundle sourceBundle = bundleForLanguage(source);
        GameBundle targetBundle = bundleForLanguage(target);
        GameBundle baseBundle = bundleForLanguage("en");
        if(sourceBundle == null || targetBundle == null || baseBundle == null) return "";

        ObjectSet<String> seen = new ObjectSet<>();
        Seq<String> keys = new Seq<>();
        collectBundleKeys(keys, seen, baseBundle);
        collectBundleKeys(keys, seen, sourceBundle);
        collectBundleKeys(keys, seen, targetBundle);
        keys.sort(String::compareTo);

        StringBuilder rows = new StringBuilder(keys.size * 64);
        for(String key : keys){
            String baseValue = baseBundle.get(key, "");
            String sourceValue = sourceBundle.get(key, baseValue);
            String targetValue = targetBundle.get(key, baseValue);
            if(oneLine(sourceValue).isEmpty() && oneLine(targetValue).isEmpty()) continue;
            rows.append(key)
                .append(" | ")
                .append(glossaryCell(sourceValue))
                .append(" | ")
                .append(glossaryCell(targetValue))
                .append('\n');
        }

        String value = rows.toString();
        fullBundleCache.put(cacheKey, value);
        Log.info("[Info] ForeignServerTranslator built full bundle context: source=@ target=@ rows=@", source, target, keys.size);
        return value;
    }

    private static void collectBundleKeys(Seq<String> keys, ObjectSet<String> seen, GameBundle bundle){
        if(bundle == null) return;
        for(String key : bundle.keys()){
            if(seen.add(key)) keys.add(key);
        }
    }

    private static String glossaryCell(String value){
        return oneLine(value).replace('|', '/');
    }

    private static void addBundleKeys(ObjectSet<String> keys, GameBundle bundle){
        if(bundle == null) return;
        for(String key : bundle.keys()){
            keys.add(key);
        }
    }

    private static String normalizedBundleValue(String value){
        String normalized = normalizeForMatch(value);
        int placeholder = normalized.indexOf('{');
        if(placeholder >= 0){
            normalized = normalized.substring(0, placeholder).trim();
        }
        return normalized;
    }

    private static boolean isMatchableBundleValue(String key, String candidate){
        if(candidate.isEmpty() || candidate.indexOf('{') >= 0) return false;
        if(isContentNameKey(key) && containsCjk(candidate)){
            return candidate.length() >= 2;
        }
        if(containsCjk(candidate)){
            return candidate.length() >= 4;
        }
        return candidate.length() >= 3;
    }

    private static boolean isContentNameKey(String key){
        return key.endsWith(".name") && (key.startsWith("block.") || key.startsWith("unit.") || key.startsWith("item.") || key.startsWith("liquid.") || key.startsWith("status."));
    }

    private static boolean matchesBundleValue(String source, String candidate){
        return !source.isEmpty() && !candidate.isEmpty() && source.contains(candidate);
    }

    private static String normalizeForMatch(String value){
        if(value == null) return "";
        return Strings.stripColors(value).replace('\n', ' ').replace('\r', ' ').replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
    }

    private static boolean isLocalBundleReference(String value){
        if(value == null) return true;
        String trimmed = value.trim();
        return trimmed.matches("@[A-Za-z0-9_.\\-]+");
    }

    private static boolean containsCjk(String value){
        for(int i = 0; i < value.length(); i++){
            char c = value.charAt(i);
            if(c >= '\u4e00' && c <= '\u9fff') return true;
        }
        return false;
    }

    private static String detectBundleLanguage(String primaryText, Seq<String> fallbackTexts){
        String primary = detectBundleLanguage(primaryText);
        return primary == null ? detectBundleLanguage(fallbackTexts) : primary;
    }

    private static String detectBundleLanguage(Seq<String> texts){
        for(String text : texts){
            String detected = detectBundleLanguage(text);
            if(detected != null) return detected;
        }
        return currentBundleLanguage();
    }

    private static String detectBundleLanguage(String text){
        int cjk = 0, kana = 0, hangul = 0, cyrillic = 0, thai = 0, arabic = 0, hebrew = 0, latin = 0;
        if(text == null) return null;
        for(int i = 0; i < text.length(); i++){
            char c = text.charAt(i);
            if(c >= '\u4e00' && c <= '\u9fff'){
                cjk++;
            }else if((c >= '\u3040' && c <= '\u30ff') || (c >= '\u31f0' && c <= '\u31ff')){
                kana++;
            }else if(c >= '\uac00' && c <= '\ud7af'){
                hangul++;
            }else if(c >= '\u0400' && c <= '\u04ff'){
                cyrillic++;
            }else if(c >= '\u0e00' && c <= '\u0e7f'){
                thai++;
            }else if(c >= '\u0600' && c <= '\u06ff'){
                arabic++;
            }else if(c >= '\u0590' && c <= '\u05ff'){
                hebrew++;
            }else if((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')){
                latin++;
            }
        }

        if(kana > 0) return "ja";
        if(hangul > 0) return "ko";
        if(cjk > 0) return "zh-Hans";
        if(cyrillic > 0) return "ru";
        if(thai > 0) return "th";
        if(arabic > 0) return "ar";
        if(hebrew > 0) return "he";
        return latin > 0 ? "en" : null;
    }

    private static String currentBundleLanguage(){
        if(Core.bundle != null && Core.bundle.getLocale() != null){
            Locale locale = Core.bundle.getLocale();
            if("zh".equals(locale.getLanguage())){
                return "TW".equalsIgnoreCase(locale.getCountry()) ? "zh-Hant" : "zh-Hans";
            }
            if(!locale.getLanguage().isEmpty()) return locale.getLanguage();
        }
        return "en";
    }

    public static String normalizeLanguageCode(String language){
        if(language == null || language.trim().isEmpty()) return "";
        return normalizeBundleLanguage(language);
    }

    private static String normalizeBundleLanguage(String language){
        if(language == null || language.trim().isEmpty()) return currentBundleLanguage();
        String normalized = language.trim()
            .replace("`", "")
            .replace("\"", "")
            .replace("'", "")
            .replace('_', '-');
        int newline = normalized.indexOf('\n');
        if(newline >= 0) normalized = normalized.substring(0, newline);
        normalized = normalized.trim();
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\b(zh[-](?:hans|hant|cn|tw|hk)|pt[-](?:br|pt)|sr[-](?:cyrl|latn)|[a-z]{2,3}(?:[-][a-z0-9]{2,8})?)\\b", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(normalized);
        String matched = null;
        while(matcher.find()){
            matched = matcher.group(1);
        }
        if(matched != null) normalized = matched;
        if(normalized.isEmpty()) return currentBundleLanguage();
        String lower = normalized.toLowerCase(Locale.ROOT);
        if(lower.equals("zh") || lower.equals("zh-hans") || lower.equals("zh-cn")) return "zh-Hans";
        if(lower.equals("zh-hant") || lower.equals("zh-tw") || lower.equals("zh-hk")) return "zh-Hant";
        if(lower.equals("pt-br")) return "pt-BR";
        if(lower.equals("pt-pt")) return "pt-PT";
        int dash = lower.indexOf('-');
        return dash < 0 ? lower : lower.substring(0, dash);
    }

    private static GameBundle bundleForLanguage(String language){
        String key = normalizeBundleLanguage(language);
        if(bundleCache.containsKey(key)) return bundleCache.get(key);

        GameBundle base = bundleCache.get("en");
        if(base == null){
            ObjectMap<String, String> baseValues = loadBundleProperties("bundle.properties");
            if(baseValues == null){
                Log.warn("ForeignServerTranslator failed to load embedded Mindustry base bundle.");
                return null;
            }
            base = new GameBundle("en", baseValues);
            bundleCache.put("en", base);
        }

        if("en".equals(key)) return base;

        ObjectMap<String, String> localized = loadBundleProperties(bundleFileName(key));
        if(localized == null){
            Log.info("[Info] ForeignServerTranslator embedded Mindustry bundle missing for @; using English fallback.", key);
            bundleCache.put(key, base);
            return base;
        }

        ObjectMap<String, String> merged = new ObjectMap<>();
        merged.putAll(base.values);
        merged.putAll(localized);
        GameBundle bundle = new GameBundle(key, merged);
        bundleCache.put(key, bundle);
        return bundle;
    }

    private static ObjectMap<String, String> loadBundleProperties(String fileName){
        try(InputStream stream = TranslatorFeature.class.getResourceAsStream("/fst-bundles/" + fileName)){
            if(stream == null) return null;

            Properties properties = new Properties();
            properties.load(new InputStreamReader(stream, StandardCharsets.UTF_8));

            ObjectMap<String, String> values = new ObjectMap<>(properties.size());
            for(String key : properties.stringPropertyNames()){
                values.put(key, properties.getProperty(key));
            }
            return values;
        }catch(Throwable error){
            Log.warn("ForeignServerTranslator failed to load embedded Mindustry bundle @: @", fileName, error.getMessage());
            return null;
        }
    }

    private static String bundleFileName(String language){
        if("en".equals(language)) return "bundle.properties";
        if("zh-Hans".equals(language)) return "bundle_zh_CN.properties";
        if("zh-Hant".equals(language)) return "bundle_zh_TW.properties";
        if("pt-BR".equals(language)) return "bundle_pt_BR.properties";
        if("pt-PT".equals(language) || "pt".equals(language)) return "bundle_pt_PT.properties";
        if("id".equals(language)) return "bundle_id_ID.properties";
        if("uk".equals(language)) return "bundle_uk_UA.properties";
        return "bundle_" + language + ".properties";
    }

    private static String safeSpeaker(String value){
        String speaker = oneLine(Strings.stripColors(value));
        return speaker.isEmpty() ? "Unknown" : speaker;
    }

    private static String oneLine(String value){
        if(value == null) return "";
        return value.replace('\n', ' ').replace('\r', ' ').replaceAll("\\s+", " ").trim();
    }

    private static void alias(String key, String language, String... aliases){
        languageAliases.put(key, language);
        for(String alias : aliases){
            languageAliases.put(alias, language);
        }
    }

    private static LanguageOverride parseLanguageOverride(String body){
        if(body == null || body.isEmpty() || !isLanguageOverridePrefix(body.charAt(0))) return null;

        int end = 1;
        while(end < body.length()){
            char c = body.charAt(end);
            if((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '-'){
                end++;
            }else{
                break;
            }
        }

        if(end <= 1 || end >= body.length()) return null;

        String language = languageAliases.get(body.substring(1, end).toUpperCase(Locale.ROOT));
        if(language == null) return null;

        String text = body.substring(end).trim();
        return text.isEmpty() ? null : new LanguageOverride(body.substring(1, end), language, text);
    }

    private static boolean isLanguageOverridePrefix(char value){
        return languageOverridePrefixes.indexOf(value) >= 0;
    }

    private static final class LanguageOverride{
        final String token;
        final String language;
        final String body;

        LanguageOverride(String token, String language, String body){
            this.token = token;
            this.language = language;
            this.body = body;
        }
    }

    public static final class OutgoingMessage{
        public final String prefix;
        public final String body;
        public final String targetLanguage;
        public final boolean translatable;
        public final String overrideToken;

        public OutgoingMessage(String prefix, String body, String targetLanguage, boolean translatable){
            this(prefix, body, targetLanguage, translatable, null);
        }

        public OutgoingMessage(String prefix, String body, String targetLanguage, boolean translatable, String overrideToken){
            this.prefix = prefix;
            this.body = body;
            this.targetLanguage = targetLanguage;
            this.translatable = translatable;
            this.overrideToken = overrideToken;
        }
    }

    private static final class GameBundle{
        final String language;
        final ObjectMap<String, String> values;

        GameBundle(String language, ObjectMap<String, String> values){
            this.language = language;
            this.values = values;
        }

        String get(String key, String def){
            return values.get(key, def);
        }

        Iterable<String> keys(){
            return values.keys();
        }
    }

    private static final class RecentPlayerLine{
        final String speaker;
        final String message;
        final long time;

        RecentPlayerLine(String speaker, String message, long time){
            this.speaker = normalizeForMatch(speaker);
            this.message = normalizeForMatch(message);
            this.time = time;
        }
    }

    private static final class PendingServerTranslation{
        final String server;
        final String text;
        final String targetLanguage;
        final Cons<String> success;
        final Cons<Throwable> failure;

        PendingServerTranslation(String server, String text, String targetLanguage, Cons<String> success, Cons<Throwable> failure){
            this.server = server == null ? "" : server;
            this.text = text;
            this.targetLanguage = targetLanguage;
            this.success = success;
            this.failure = failure;
        }
    }

    public static final class ChatEntry{
        public final String speaker;
        public final String message;

        public ChatEntry(String speaker, String message){
            this.speaker = speaker;
            this.message = message;
        }
    }

    public static final class TranslationContext{
        public final Seq<ChatEntry> messages;
        public final Seq<String> bundleHints;
        public final String outgoingOverrideToken;
        public final String outgoingTargetLanguage;
        public final String sourceLanguage;
        public final boolean serverText;
        public final String fullBundleContext;

        public TranslationContext(Seq<ChatEntry> messages, Seq<String> bundleHints){
            this(messages, bundleHints, null, null, "", false, "");
        }

        public TranslationContext(Seq<ChatEntry> messages, Seq<String> bundleHints, String outgoingOverrideToken, String outgoingTargetLanguage){
            this(messages, bundleHints, outgoingOverrideToken, outgoingTargetLanguage, "", false, "");
        }

        public TranslationContext(Seq<ChatEntry> messages, Seq<String> bundleHints, String outgoingOverrideToken, String outgoingTargetLanguage, String sourceLanguage, boolean serverText, String fullBundleContext){
            this.messages = messages;
            this.bundleHints = bundleHints;
            this.outgoingOverrideToken = outgoingOverrideToken;
            this.outgoingTargetLanguage = outgoingTargetLanguage;
            this.sourceLanguage = sourceLanguage == null ? "" : sourceLanguage;
            this.serverText = serverText;
            this.fullBundleContext = fullBundleContext == null ? "" : fullBundleContext;
        }

        public TranslationContext withOutgoingOverride(String token, String targetLanguage){
            return new TranslationContext(messages, bundleHints, token, targetLanguage, sourceLanguage, serverText, fullBundleContext);
        }
    }
}
