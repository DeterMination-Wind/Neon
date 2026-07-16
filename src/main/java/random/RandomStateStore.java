package random;

import arc.Core;
import arc.files.Fi;
import arc.util.Log;
import arc.struct.Seq;
import mindustry.Vars;
import mindustry.ctype.Content;
import mindustry.ctype.ContentType;
import mindustry.ctype.MappableContent;
import mindustry.ctype.UnlockableContent;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import static mindustry.Vars.control;
import static mindustry.Vars.state;

/** Stores mappings outside native save files while keeping their cache key explicit. */
public final class RandomStateStore{
    public static final int ALGORITHM_VERSION = 1;
    private static final String TEXT_KIND = "text";
    private static final String TEXTURE_KIND = "texture";

    private final Fi mappingDirectory;
    private final String sessionId = UUID.randomUUID().toString();
    private int sessionWorld;
    private String activeIdentity;

    public RandomStateStore(){
        Fi data = Core.settings == null ? null : Core.settings.getDataDirectory();
        mappingDirectory = (data == null ? Core.files.local("random-mod") : data.child("random-mod")).child("mappings");
        mappingDirectory.mkdirs();
    }

    public String clientSeed(){
        if(Core.settings == null) return sessionId;
        String seed = Core.settings.getString(RandomSettings.CLIENT_SEED, "");
        if(seed == null || seed.isEmpty()){
            seed = UUID.randomUUID().toString();
            Core.settings.put(RandomSettings.CLIENT_SEED, seed);
        }
        return seed;
    }

    /** Selects the identity for the next world. Unsaved worlds use a session-only identity. */
    public String beginWorld(){
        String saved = savedIdentity();
        if(saved != null){
            activeIdentity = saved;
        }else{
            sessionWorld++;
            String map = state == null || state.map == null ? "unknown" : state.map.name();
            activeIdentity = "session:" + sessionId + ":" + sessionWorld + ":" + map;
        }
        return activeIdentity;
    }

    public String activeIdentity(){
        return activeIdentity;
    }

    public String savedIdentity(){
        try{
            if(control != null && control.saves != null){
                var current = control.saves.getCurrent();
                if(current != null && current.file != null) return "save:" + current.file.absolutePath();
            }
        }catch(Throwable ignored){
            // Some startup paths do not have the save manager initialized yet.
        }

        try{
            if(state != null && state.map != null && state.map.file != null && state.map.file.exists()){
                return "map:" + state.map.file.absolutePath();
            }
        }catch(Throwable ignored){
        }
        return null;
    }

    public String cacheKey(String identity, String contentSignature, boolean text, boolean texture){
        return "algorithm=" + ALGORITHM_VERSION + "|identity=" + identity + "|content=" + contentSignature
            + "|text=" + text + "|texture=" + texture;
    }

    public Map<String, String> load(String cacheKey, String kind){
        Properties properties = new Properties();
        Fi file = cacheFile(cacheKey, kind);
        if(!file.exists()) return new LinkedHashMap<>();

        try(Reader reader = file.reader("UTF-8")){
            properties.load(reader);
            if(!cacheKey.equals(properties.getProperty("cacheKey")) || !kind.equals(properties.getProperty("kind"))){
                return new LinkedHashMap<>();
            }

            LinkedHashMap<String, String> result = new LinkedHashMap<>();
            for(String property : properties.stringPropertyNames()){
                if(!property.startsWith("target.")) continue;
                String target = decode(property.substring("target.".length()));
                String source = decode(properties.getProperty(property, ""));
                if(target != null && source != null) result.put(target, source);
            }
            return result;
        }catch(Throwable error){
            Log.warn("Random mapping cache could not be read: @", error.toString());
            return new LinkedHashMap<>();
        }
    }

    public void save(String cacheKey, String kind, Map<String, String> mapping){
        if(mapping == null || mapping.isEmpty()) return;
        Properties properties = new Properties();
        properties.setProperty("cacheKey", cacheKey);
        properties.setProperty("kind", kind);
        properties.setProperty("algorithm", Integer.toString(ALGORITHM_VERSION));
        for(Map.Entry<String, String> entry : mapping.entrySet()){
            properties.setProperty("target." + encode(entry.getKey()), encode(entry.getValue()));
        }

        Fi file = cacheFile(cacheKey, kind);
        try(Writer writer = file.writer(false, "UTF-8")){
            properties.store(writer, "Random client mapping; do not edit");
        }catch(Throwable error){
            Log.warn("Random mapping cache could not be written: @", error.toString());
        }
    }

    public void migrate(String oldCacheKey, String newCacheKey, boolean text, boolean texture){
        if(oldCacheKey == null || newCacheKey == null || oldCacheKey.equals(newCacheKey)) return;
        if(text) copy(oldCacheKey, newCacheKey, TEXT_KIND);
        if(texture) copy(oldCacheKey, newCacheKey, TEXTURE_KIND);
    }

    private void copy(String oldKey, String newKey, String kind){
        Fi source = cacheFile(oldKey, kind);
        if(source.exists()){
            try{
                source.copyTo(cacheFile(newKey, kind));
            }catch(Throwable error){
                Log.warn("Random mapping cache could not be migrated: @", error.toString());
            }
        }
    }

    private Fi cacheFile(String cacheKey, String kind){
        return mappingDirectory.child(RandomPermutationController.digest(cacheKey + "|" + kind) + ".properties");
    }

    public static boolean mappingComplete(java.util.List<String> ids, Map<String, String> mapping){
        return RandomPermutationController.isPermutation(ids, mapping);
    }

    /** Computes a signature for the loaded content set and the visual dimensions used by this mod. */
    public static String contentSignature(){
        StringBuilder text = new StringBuilder();
        if(Vars.content == null) return RandomPermutationController.digest("no-content");
        for(ContentType type : ContentType.all){
            Seq<Content> contents = Vars.content.getBy(type);
            text.append(type.name()).append('[').append(contents.size).append(']');
            for(Content content : contents){
                text.append('|').append(content.id).append(':');
                if(content instanceof MappableContent mapped) text.append(mapped.name);
                text.append(':').append(content.removed);
                if(content instanceof UnlockableContent unlock){
                    text.append(':').append(unlock.isHidden()).append(':').append(unlock.hideDatabase)
                        .append(':').append(regionSignature(unlock.fullIcon)).append(':').append(regionSignature(unlock.uiIcon));
                }
            }
        }
        return RandomPermutationController.digest(text.toString());
    }

    private static String regionSignature(arc.graphics.g2d.TextureRegion region){
        return region == null ? "null" : region.width + "x" + region.height + "@" + region.scale;
    }

    private static String encode(String value){
        if(value == null) return "";
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for(byte b : bytes) out.append(String.format("%02x", b & 0xff));
        return out.toString();
    }

    private static String decode(String value){
        if(value == null || (value.length() & 1) != 0) return null;
        try{
            byte[] bytes = new byte[value.length() / 2];
            for(int i = 0; i < bytes.length; i++) bytes[i] = (byte)Integer.parseInt(value.substring(i * 2, i * 2 + 2), 16);
            return new String(bytes, StandardCharsets.UTF_8);
        }catch(Throwable ignored){
            return null;
        }
    }
}
