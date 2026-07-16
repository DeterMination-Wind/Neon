package random;

import arc.Core;
import arc.struct.Seq;
import mindustry.Vars;
import mindustry.ctype.UnlockableContent;
import mindustry.world.meta.Stat;
import mindustry.world.meta.StatCat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Resolves randomized strings without modifying Mindustry content objects. */
public final class RandomTextResolver{
    private static final String NAME_KIND = "name";
    private static final String DESCRIPTION_KIND = "description";
    private static final String DETAILS_KIND = "details";

    private final RandomStateStore store;
    private final Map<String, TextEntry> entries = new LinkedHashMap<>();
    private final Map<String, TextEntry> contentNames = new LinkedHashMap<>();
    private final Map<String, TextEntry> contentDescriptions = new LinkedHashMap<>();
    private final Map<String, TextEntry> contentDetails = new LinkedHashMap<>();
    private final Map<String, TextEntry> databaseCategories = new LinkedHashMap<>();
    private final Map<String, TextEntry> databaseTags = new LinkedHashMap<>();
    private Map<String, String> nameMapping = new LinkedHashMap<>();
    private Map<String, String> descriptionMapping = new LinkedHashMap<>();
    private Map<String, String> detailsMapping = new LinkedHashMap<>();
    private boolean active;

    public RandomTextResolver(RandomStateStore store){
        this.store = store;
    }

    public void begin(String cacheKey, boolean enabled){
        reset();
        if(!enabled || Vars.content == null) return;

        entries.clear();
        contentNames.clear();
        contentDescriptions.clear();
        contentDetails.clear();
        databaseCategories.clear();
        databaseTags.clear();

        List<String> commonIds = new ArrayList<>();
        List<String> descriptionIds = new ArrayList<>();
        List<String> detailsIds = new ArrayList<>();

        addContentEntries(Vars.content.blocks(), commonIds, descriptionIds, detailsIds);
        addContentEntries(Vars.content.items(), commonIds, descriptionIds, detailsIds);
        addContentEntries(Vars.content.units(), commonIds, descriptionIds, detailsIds);
        addDatabaseEntries(commonIds);

        for(StatCat category : StatCat.all){
            TextEntry entry = entry("name:category:" + category.name, "category." + category.name, category.localized());
            if(entry != null){
                commonIds.add(entry.id);
            }
        }
        for(Stat stat : Stat.all){
            String bundleName = stat.name.toLowerCase(Locale.ROOT);
            TextEntry entry = entry("name:stat:" + stat.name, "stat." + bundleName, stat.localized());
            if(entry != null){
                commonIds.add(entry.id);
            }
        }

        nameMapping = loadOrGenerate(cacheKey, NAME_KIND, commonIds);
        descriptionMapping = loadOrGenerate(cacheKey, DESCRIPTION_KIND, descriptionIds);
        detailsMapping = loadOrGenerate(cacheKey, DETAILS_KIND, detailsIds);
        active = true;
    }

    private void addContentEntries(Seq<? extends UnlockableContent> contents, List<String> commonIds,
                                   List<String> descriptionIds, List<String> detailsIds){
        for(UnlockableContent content : contents){
            if(content == null || content.removed) continue;

            String prefix = content.getContentType().name() + ":" + content.name;
            String bundlePrefix = content.getContentType().name() + "." + content.name;
            TextEntry name = entry("name:content:" + prefix, bundlePrefix + ".name", content.localizedName);
            if(name != null){
                contentNames.put(contentKey(content), name);
                commonIds.add(name.id);
            }

            if(content.description != null && !content.description.trim().isEmpty()){
                TextEntry description = entry("description:content:" + prefix, bundlePrefix + ".description", content.description);
                if(description != null){
                    contentDescriptions.put(contentKey(content), description);
                    descriptionIds.add(description.id);
                }
            }

            if(content.details != null && !content.details.trim().isEmpty()){
                TextEntry details = entry("details:content:" + prefix, bundlePrefix + ".details", content.details);
                if(details != null){
                    contentDetails.put(contentKey(content), details);
                    detailsIds.add(details.id);
                }
            }
        }
    }

    private TextEntry entry(String id, String bundleKey, String fallback){
        if(fallback == null || sanitize(fallback).trim().isEmpty()) return null;
        TextEntry result = new TextEntry(id, bundleKey, fallback);
        entries.put(id, result);
        return result;
    }

    private Map<String, String> loadOrGenerate(String cacheKey, String kind, List<String> ids){
        if(ids.isEmpty()) return new LinkedHashMap<>();
        Map<String, String> loaded = store.load(cacheKey, kind);
        if(!RandomStateStore.mappingComplete(ids, loaded)){
            loaded = RandomPermutationController.mapping(ids,
                RandomPermutationController.seed(store.clientSeed() + "|" + cacheKey + "|" + kind));
            store.save(cacheKey, kind, loaded);
        }
        return loaded;
    }

    public void reset(){
        active = false;
        nameMapping = new LinkedHashMap<>();
        descriptionMapping = new LinkedHashMap<>();
        detailsMapping = new LinkedHashMap<>();
        entries.clear();
        contentNames.clear();
        contentDescriptions.clear();
        contentDetails.clear();
        databaseCategories.clear();
        databaseTags.clear();
    }

    public boolean active(){
        return active;
    }

    public String name(UnlockableContent content){
        if(content == null) return "";
        String original = originalName(content);
        if(!active) return original;
        return mapped(contentNames.get(contentKey(content)), nameMapping, original);
    }

    public String description(UnlockableContent content){
        if(content == null || content.description == null) return null;
        String original = originalDescription(content);
        if(!active) return content.displayDescription();
        return mapped(contentDescriptions.get(contentKey(content)), descriptionMapping, original);
    }

    public String details(UnlockableContent content){
        if(content == null || content.details == null) return null;
        String original = sanitize(content.details);
        if(!active) return original;
        return mapped(contentDetails.get(contentKey(content)), detailsMapping, original);
    }

    public String statCategory(StatCat category){
        if(category == null) return "";
        String original = localized("category." + category.name, category.localized());
        if(!active) return original;
        return mapped(entries.get("name:category:" + category.name), nameMapping, original);
    }

    public String stat(Stat stat){
        if(stat == null) return "";
        String key = stat.name.toLowerCase(Locale.ROOT);
        String original = localized("stat." + key, stat.localized());
        if(!active) return original;
        return mapped(entries.get("name:stat:" + stat.name), nameMapping, original);
    }

    public String databaseCategory(String category){
        String original = localized("database-category." + category, category);
        if(!active) return original;
        return mapped(databaseCategories.get(category), nameMapping, original);
    }

    public String databaseTag(String tag){
        String original = localized("database-tag." + tag, tag);
        if(!active) return original;
        return mapped(databaseTags.get(tag), nameMapping, original);
    }

    public boolean matches(UnlockableContent content, String query){
        if(content == null || query == null || query.isEmpty()) return true;
        String lower = query.toLowerCase(Locale.ROOT);
        return originalName(content).toLowerCase(Locale.ROOT).contains(lower)
            || name(content).toLowerCase(Locale.ROOT).contains(lower);
    }

    private String mapped(TextEntry target, Map<String, String> mapping, String fallback){
        if(target == null) return fallback;
        TextEntry source = entries.get(mapping.get(target.id));
        if(source == null) return fallback;
        String value = source.value();
        return value.isEmpty() ? fallback : value;
    }

    private String originalName(UnlockableContent content){
        return localized(content.getContentType().name() + "." + content.name + ".name", content.localizedName);
    }

    private String originalDescription(UnlockableContent content){
        return localized(content.getContentType().name() + "." + content.name + ".description", content.description);
    }

    private void addDatabaseEntries(List<String> commonIds){
        for(Seq<mindustry.ctype.Content> contents : Vars.content.getContentMap()){
            for(mindustry.ctype.Content raw : contents){
                if(!(raw instanceof UnlockableContent content)) continue;

                String category = content.databaseCategory == null || content.databaseCategory.isEmpty()
                    ? content.getContentType().name() : content.databaseCategory;
                String tag = content.databaseTag == null || content.databaseTag.isEmpty()
                    ? "default" : content.databaseTag;

                TextEntry categoryEntry = ensureEntry("name:database-category:" + category,
                    "database-category." + category, category);
                TextEntry tagEntry = ensureEntry("name:database-tag:" + tag,
                    "database-tag." + tag, tag);
                databaseCategories.put(category, categoryEntry);
                databaseTags.put(tag, tagEntry);
                addUnique(commonIds, categoryEntry == null ? null : categoryEntry.id);
                addUnique(commonIds, tagEntry == null ? null : tagEntry.id);
            }
        }
    }

    private TextEntry ensureEntry(String id, String bundleKey, String fallback){
        TextEntry existing = entries.get(id);
        return existing == null ? entry(id, bundleKey, fallback) : existing;
    }

    private static void addUnique(List<String> values, String value){
        if(value != null && !values.contains(value)) values.add(value);
    }

    private String localized(String key, String fallback){
        if(Core.bundle == null) return sanitize(fallback);
        return sanitize(Core.bundle.get(key, fallback == null ? "" : fallback));
    }

    private static String contentKey(UnlockableContent content){
        return content.getContentType().name() + ":" + content.name;
    }

    /** Removes non-printing control characters while retaining Mindustry formatting, icons and line breaks. */
    public static String sanitize(String value){
        if(value == null || value.isEmpty()) return "";
        StringBuilder out = new StringBuilder(value.length());
        for(int i = 0; i < value.length(); i++){
            char c = value.charAt(i);
            if(c == '\n' || c == '\r' || c == '\t' || !Character.isISOControl(c)) out.append(c);
        }
        return out.toString();
    }

    private final class TextEntry{
        final String id;
        final String bundleKey;
        final String fallback;

        TextEntry(String id, String bundleKey, String fallback){
            this.id = id;
            this.bundleKey = bundleKey;
            this.fallback = fallback;
        }

        String value(){
            if(Core.bundle == null) return sanitize(fallback);
            String value = Core.bundle.getOrNull(bundleKey);
            return sanitize(value == null ? fallback : value);
        }
    }
}
