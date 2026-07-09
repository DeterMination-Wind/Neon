package foreignservertranslator;

import arc.Core;
import arc.Events;
import arc.func.Cons;
import arc.struct.ObjectMap;
import arc.util.Log;
import mindustry.Vars;
import mindustry.game.EventType.Trigger;
import mindustry.game.MapObjectives.MapObjective;
import mindustry.game.MapObjectives.ObjectiveMarker;
import mindustry.game.MapObjectives.ShapeTextMarker;
import mindustry.game.MapObjectives.TextMarker;

public final class MarkerTranslator{
    private static final ObjectMap<String, Object> inFlightRequests = new ObjectMap<>();
    private static final ObjectMap<ObjectiveMarker, String> originalTexts = new ObjectMap<>();
    private static boolean installed;

    private MarkerTranslator(){
    }

    public static void init(){
    }

    public static void install(){
        if(installed) return;
        installed = true;

        Events.run(Trigger.draw, () -> {
            try{
                for(ObjectiveMarker marker : Vars.state.markers){
                    updateMarker(marker);
                }
            }catch(Throwable ignored){
                // markers may be removed during iteration
            }

            if(Vars.state.rules.objectives != null){
                try{
                    for(MapObjective objective : Vars.state.rules.objectives){
                        for(ObjectiveMarker marker : objective.markers){
                            updateMarker(marker);
                        }
                    }
                }catch(Throwable ignored){
                    // objectives may be modified during iteration
                }
            }
        });
    }

    public static boolean shouldTranslate(String text){
        if(!TranslatorFeature.shouldTranslateWorldText(text)) return false;
        String trimmed = TranslatorFeature.stripIncomingHint(text).trim();
        return !trimmed.isEmpty() && trimmed.length() >= 2;
    }

    public static void translateMarker(String text, String targetLanguage, Cons<String> success, Cons<Throwable> failure){
        String server = currentServerKey();
        String sourceText = TranslatorFeature.stripIncomingHint(text);
        String sourceLanguage = TranslatorFeature.worldTextSourceLanguage(text);
        String target = targetLanguage == null || targetLanguage.trim().isEmpty() ? "en" : targetLanguage.trim();
        String cacheSource = sourceLanguage.isEmpty() ? "auto" : sourceLanguage;

        String cached = TranslatorCache.getTranslation(server, cacheSource, target, sourceText);
        if(cached != null && !cached.isEmpty()){
            success.get(cached);
            return;
        }

        String requestKey = server + "|" + cacheSource + "|" + target + "|" + sourceText;
        if(inFlightRequests.containsKey(requestKey)) return;

        inFlightRequests.put(requestKey, Boolean.TRUE);
        MarkerTranslationService.translate(sourceText, target, translated -> {
            inFlightRequests.remove(requestKey);
            TranslatorCache.putTranslation(server, cacheSource, target, sourceText, translated);
            success.get(translated);
        }, error -> {
            inFlightRequests.remove(requestKey);
            Log.warn("ForeignServerTranslator marker translation failed: @", error.getMessage());
            failure.get(error);
        });
    }

    private static void updateMarker(ObjectiveMarker marker){
        if(marker instanceof TextMarker){
            updateTextMarker((TextMarker)marker);
        }else if(marker instanceof ShapeTextMarker){
            updateShapeTextMarker((ShapeTextMarker)marker);
        }
    }

    private static void updateTextMarker(TextMarker marker){
        updateMarkerText(marker, marker.text, marker::setText);
    }

    private static void updateShapeTextMarker(ShapeTextMarker marker){
        updateMarkerText(marker, marker.text, marker::setText);
    }

    private static void updateMarkerText(ObjectiveMarker marker, String currentText, MarkerTextSetter setter){
        String original = originalTexts.get(marker);
        if(original == null){
            original = currentText;
            originalTexts.put(marker, currentText);
        }else if(currentText != null && !currentText.equals(original) && !currentText.equals(TranslatorCache.getTranslation(currentServerKey(), "auto", currentTargetLanguage(), original))){
            original = currentText;
            originalTexts.put(marker, currentText);
        }

        if(!shouldTranslate(original)) return;

        String target = currentTargetLanguage();
        String sourceText = TranslatorFeature.stripIncomingHint(original);
        String sourceLanguage = TranslatorFeature.worldTextSourceLanguage(original);
        String cacheSource = sourceLanguage.isEmpty() ? "auto" : sourceLanguage;
        String cached = TranslatorCache.getTranslation(currentServerKey(), cacheSource, target, sourceText);
        if(cached != null && !cached.isEmpty()){
            if(!cached.equals(currentText)) setter.set(cached, false);
            return;
        }

        translateMarker(original, target, translated -> setter.set(translated, false), error -> {});
    }

    private static String currentServerKey(){
        String key = TranslatorFeature.selectedServerKey();
        return key == null ? "" : key;
    }

    private static String currentTargetLanguage(){
        String target = Core.settings.getString(TranslatorFeature.incomingLanguageKey, "zh-Hans").trim();
        return target.isEmpty() ? "zh-Hans" : target;
    }

    private interface MarkerTextSetter{
        void set(String text, boolean fetch);
    }
}
