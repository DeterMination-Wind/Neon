package random;

import arc.Core;
import arc.struct.ObjectMap;
import arc.util.I18NBundle;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Temporarily randomizes instructional bundle strings without touching normal menu text. */
public final class RandomInstructionTextController{
    private static final String KIND = "instruction";
    private static final List<String> PREFIXES = Arrays.asList("hint.", "gz.", "onset.", "lst.", "lenum.", "lglobal.");
    private static final Pattern FORMAT_ARGUMENT = Pattern.compile("\\{\\d+(?:\\s*,[^}]*)?}");

    private final RandomStateStore store;
    private final Map<String, String> replacedValues = new LinkedHashMap<>();
    private final Set<String> insertedKeys = new LinkedHashSet<>();
    private ObjectMap<String, String> appliedProperties;
    private boolean active;

    public RandomInstructionTextController(RandomStateStore store){
        this.store = store;
    }

    public void begin(String cacheKey, boolean enabled){
        reset();
        if(!enabled || Core.bundle == null) return;

        Map<String, String> values = collectValues(Core.bundle);
        List<String> ids = new ArrayList<>(values.keySet());
        if(ids.isEmpty()) return;

        Map<String, String> mapping = store.load(cacheKey, KIND);
        if(!RandomStateStore.mappingComplete(ids, mapping)){
            mapping = RandomPermutationController.mapping(ids,
                RandomPermutationController.seed(store.clientSeed() + "|" + cacheKey + "|" + KIND));
            store.save(cacheKey, KIND, mapping);
        }

        appliedProperties = Core.bundle.getProperties();
        for(String key : ids){
            if(appliedProperties.containsKey(key)){
                replacedValues.put(key, appliedProperties.get(key));
            }else{
                insertedKeys.add(key);
            }
            String sourceKey = mapping.get(key);
            String replacement = values.get(sourceKey);
            if(replacement != null) appliedProperties.put(key, replacement);
        }
        active = true;
    }

    public void reset(){
        if(appliedProperties != null){
            for(Map.Entry<String, String> entry : replacedValues.entrySet()){
                appliedProperties.put(entry.getKey(), entry.getValue());
            }
            for(String key : insertedKeys){
                appliedProperties.remove(key);
            }
        }
        replacedValues.clear();
        insertedKeys.clear();
        appliedProperties = null;
        active = false;
    }

    public boolean active(){
        return active;
    }

    private static Map<String, String> collectValues(I18NBundle bundle){
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        for(I18NBundle current = bundle; current != null; current = current.getParent()){
            for(String key : current.getKeys()) candidates.add(key);
        }

        ArrayList<String> keys = new ArrayList<>(candidates);
        keys.sort(String::compareTo);
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        for(String key : keys){
            String value = bundle.getOrNull(key);
            if(isInstructionText(key, value)) result.put(key, value);
        }
        return result;
    }

    static boolean isInstructionText(String key, String value){
        if(key == null || value == null || value.trim().isEmpty() || hasFormatArguments(value)) return false;
        for(String prefix : PREFIXES){
            if(key.startsWith(prefix)) return true;
        }
        return false;
    }

    static boolean hasFormatArguments(String value){
        return value != null && FORMAT_ARGUMENT.matcher(value).find();
    }
}
