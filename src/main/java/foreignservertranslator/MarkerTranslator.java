package foreignservertranslator;

import arc.Core;
import arc.Events;
import arc.func.Cons;
import arc.graphics.Color;
import arc.graphics.g2d.Font;
import arc.graphics.g2d.GlyphLayout;
import arc.scene.ui.layout.Scl;
import arc.util.Align;
import arc.struct.ObjectMap;
import arc.util.Log;
import arc.util.pooling.Pools;
import mindustry.Vars;
import mindustry.game.EventType.Trigger;
import mindustry.game.MapObjectives.MapObjective;
import mindustry.game.MapObjectives.ObjectiveMarker;
import mindustry.game.MapObjectives.ShapeTextMarker;
import mindustry.game.MapObjectives.TextMarker;
import mindustry.ui.Fonts;

public final class MarkerTranslator{
    private static final ObjectMap<String, Object> inFlightRequests = new ObjectMap<>();

    private MarkerTranslator(){
    }

    public static void init(){
    }

    /** Registers the render hook that draws translated text below TextMarker/ShapeTextMarker on the map. */
    public static void install(){
        Events.run(Trigger.draw, () -> {
            if(!TranslatorFeature.isCurrentServerForeign()) return;

            try{
                for(ObjectiveMarker marker : Vars.state.markers){
                    drawMarkerTranslation(marker);
                }
            }catch(Throwable ignored){
                // markers may be removed during iteration
            }

            if(Vars.state.rules.objectives != null){
                try{
                    for(MapObjective objective : Vars.state.rules.objectives){
                        for(ObjectiveMarker marker : objective.markers){
                            drawMarkerTranslation(marker);
                        }
                    }
                }catch(Throwable ignored){
                    // objectives may be modified during iteration
                }
            }
        });
    }

    /** Returns false for null, empty, whitespace-only, @-prefixed (bundle reference), or very short text. */
    public static boolean shouldTranslate(String text){
        if(text == null) return false;
        String trimmed = text.trim();
        if(trimmed.isEmpty()) return false;
        if(trimmed.startsWith("@")) return false;
        if(trimmed.length() < 2) return false;
        return true;
    }

    /** Translates marker text with caching and in-flight deduplication. */
    public static void translateMarker(String text, String targetLanguage, Cons<String> success, Cons<Throwable> failure){
        String server = currentServerKey();
        String target = targetLanguage == null || targetLanguage.trim().isEmpty() ? "en" : targetLanguage.trim();

        String cached = TranslatorCache.getTranslation(server, "auto", target, text);
        if(cached != null && !cached.isEmpty()){
            success.get(cached);
            return;
        }

        if(inFlightRequests.containsKey(text)) return;

        inFlightRequests.put(text, Boolean.TRUE);
        MarkerTranslationService.translate(text, target, translated -> {
            inFlightRequests.remove(text);
            TranslatorCache.putTranslation(server, "auto", target, text, translated);
            success.get(translated);
        }, error -> {
            inFlightRequests.remove(text);
            Log.warn("ForeignServerTranslator marker translation failed: @", error.getMessage());
            failure.get(error);
        });
    }

    // --- internal helpers ---

    private static void drawMarkerTranslation(ObjectiveMarker marker){
        if(marker instanceof TextMarker){
            drawTextMarkerTranslation((TextMarker)marker);
        }else if(marker instanceof ShapeTextMarker){
            drawShapeTextMarkerTranslation((ShapeTextMarker)marker);
        }
    }

    private static void drawTextMarkerTranslation(TextMarker marker){
        if(!shouldTranslate(marker.text)) return;

        String server = currentServerKey();
        String target = currentTargetLanguage();
        String cached = TranslatorCache.getTranslation(server, "auto", target, marker.text);
        if(cached != null && !cached.isEmpty()){
            if(!cached.equals(marker.text)){
                drawTranslatedText(cached, marker.pos.x, marker.pos.y, marker.fontSize);
            }
        }else{
            translateMarker(marker.text, target, translated -> {}, error -> {});
        }
    }

    private static void drawShapeTextMarkerTranslation(ShapeTextMarker marker){
        if(!shouldTranslate(marker.text)) return;

        String server = currentServerKey();
        String target = currentTargetLanguage();
        String cached = TranslatorCache.getTranslation(server, "auto", target, marker.text);
        if(cached != null && !cached.isEmpty()){
            if(!cached.equals(marker.text)){
                float textY = marker.pos.y + marker.radius + marker.textHeight;
                drawTranslatedText(cached, marker.pos.x, textY, marker.fontSize);
            }
        }else{
            translateMarker(marker.text, target, translated -> {}, error -> {});
        }
    }

    private static void drawTranslatedText(String text, float x, float y, float fontSize){
        Font font = Fonts.outline;
        GlyphLayout layout = Pools.obtain(GlyphLayout.class, GlyphLayout::new);

        boolean prevIntegers = font.usesIntegerPositions();
        font.setUseIntegerPositions(false);

        float prevScale = font.getData().scaleX;
        float scale = 0.25f / Scl.scl(1f) * fontSize;
        font.getData().setScale(scale);

        layout.setText(font, text);

        font.setColor(Color.lightGray);
        font.draw(text, x, y - layout.height * 1.5f, 0, text.length(), 0, Align.center, false);
        font.setColor(Color.white);

        font.getData().setScale(prevScale);
        font.setUseIntegerPositions(prevIntegers);
        Pools.free(layout);
    }

    private static String currentServerKey(){
        String key = TranslatorFeature.selectedServerKey();
        return key == null ? "" : key;
    }

    private static String currentTargetLanguage(){
        String target = Core.settings.getString(TranslatorFeature.incomingLanguageKey, "zh-Hans").trim();
        return target.isEmpty() ? "zh-Hans" : target;
    }
}
