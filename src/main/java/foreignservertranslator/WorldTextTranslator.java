package foreignservertranslator;

import arc.Core;
import arc.Events;
import arc.func.Boolp;
import arc.func.Cons;
import arc.math.geom.Vec2;
import arc.struct.ObjectMap;
import arc.util.Log;
import mindustry.Vars;
import mindustry.game.EventType.Trigger;
import mindustry.gen.Building;
import mindustry.gen.WorldLabel;
import mindustry.world.blocks.logic.MessageBlock.MessageBuild;

public final class WorldTextTranslator{
    private static final ObjectMap<String, String> messageTextCache = new ObjectMap<>();
    private static final ObjectMap<String, Object> inFlight = new ObjectMap<>();
    private static final int messageOverlayId = Integer.MIN_VALUE + 4242;
    private static final int defaultLabelFlags = WorldLabel.flagBackground | WorldLabel.flagAutoscale | WorldLabel.flagOutline;
    private static boolean installed;

    private WorldTextTranslator(){
    }

    public static void install(){
        if(installed) return;
        installed = true;

        Events.run(Trigger.update, () -> {
            try{
                updateMessageBlockOverlay();
            }catch(Throwable error){
                Log.debug("ForeignServerTranslator world text update skipped: @", error.getMessage());
            }
        });
    }

    public static void resetSession(){
        messageTextCache.clear();
        inFlight.clear();
        clearMessageOverlay();
    }

    public static String translatedMessageText(String text){
        return translatedText(text, true, messageTextCache, () -> TranslatorFeature.shouldTranslateWorldText(text));
    }

    public static void translateMessageText(String text, Cons<String> done){
        if(text == null){
            done.get("");
            return;
        }
        String translated = translatedMessageText(text);
        if(translated != null && !translated.equals(text)){
            done.get(translated);
            return;
        }
        requestTranslation(text, messageTextCache, done);
    }

    private static String translatedText(String text, boolean request, ObjectMap<String, String> cache, Boolp shouldTranslate){
        if(text == null) return "";
        String source = sourceText(text);
        if(source.trim().isEmpty()) return source;
        if(!shouldTranslate.get()) return source;

        String key = cacheKey(source);
        String cached = cache.get(key);
        if(cached != null && !cached.isEmpty()) return cached;
        if(request) requestTranslation(text, cache, null);
        return source;
    }

    private static void updateMessageBlockOverlay(){
        if(Vars.ui == null || Vars.player == null || Vars.control == null || Vars.control.input == null || Vars.world == null){
            clearMessageOverlay();
            return;
        }
        if(Vars.control.input.block != null || Core.scene.hasMouse()){
            clearMessageOverlay();
            return;
        }

        Vec2 mouse = Core.input.mouseWorld(Vars.control.input.getMouseX(), Vars.control.input.getMouseY());
        Building build = Vars.world.buildWorld(mouse.x, mouse.y);
        if(!(build instanceof MessageBuild) || build.team != Vars.player.team()){
            clearMessageOverlay();
            return;
        }

        MessageBuild messageBuild = (MessageBuild)build;
        String text = messageBuild.message == null ? "" : messageBuild.message.toString();
        if(text.trim().isEmpty() || !TranslatorFeature.shouldTranslateWorldText(text)){
            clearMessageOverlay();
            return;
        }

        translateMessageText(text, translated -> showTranslatedOverlay(translated, messageOverlayId, 0.12f, build.x, build.y - Vars.tilesize / 2f - 6f));
    }

    private static void showTranslatedOverlay(String text, int id, float duration, float worldx, float worldy){
        if(Vars.ui instanceof TranslatorUI){
            ((TranslatorUI)Vars.ui).showLabelOnTop(text, id, duration, worldx, worldy + 8f);
        }else{
            Vars.ui.showLabel(text, id, duration, worldx, worldy + 8f, defaultLabelFlags);
        }
    }

    private static void clearMessageOverlay(){
        if(Vars.ui instanceof TranslatorUI){
            ((TranslatorUI)Vars.ui).showLabelOnTop(null, messageOverlayId, 0f, 0f, 0f);
        }else if(Vars.ui != null){
            Vars.ui.showLabel(null, messageOverlayId, 0f, 0f, 0f, defaultLabelFlags);
        }
    }

    private static void requestTranslation(String text, ObjectMap<String, String> cache){
        requestTranslation(text, cache, null);
    }

    private static void requestTranslation(String text, ObjectMap<String, String> cache, Cons<String> done){
        String key = cacheKey(sourceText(text));
        if(inFlight.containsKey(key)) return;
        inFlight.put(key, Boolean.TRUE);

        TranslatorFeature.translateWorldText(text, translated -> {
            inFlight.remove(key);
            cache.put(key, translated);
            if(done != null) done.get(translated);
        }, error -> {
            inFlight.remove(key);
            Log.warn("ForeignServerTranslator failed to translate world text: @", error.getMessage());
        });
    }

    private static String currentServerKey(){
        String server = TranslatorFeature.selectedServerKey();
        return server == null ? "" : server;
    }

    private static String sourceText(String text){
        if(text == null) return "";
        String source = TranslatorFeature.stripIncomingHint(text);
        return source == null ? "" : source;
    }

    private static String cacheKey(String text){
        return currentServerKey() + "|" + Core.settings.getString(TranslatorFeature.incomingLanguageKey, "zh-Hans") + "|" + text;
    }
}
