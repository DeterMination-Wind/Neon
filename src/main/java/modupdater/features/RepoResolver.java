package modupdater.features;

import arc.Core;
import arc.func.Cons;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import arc.util.Http;
import arc.util.Strings;
import arc.util.serialization.Jval;
import mindustry.mod.Mods;

import java.util.Locale;

public final class RepoResolver{
    private static final String modIndexUrl = "http://121.199.60.4/github/repos/Anuken/mindustry-mods/mods.json";
    private static final ObjectMap<String, String> builtinMap = new ObjectMap<>();
    private static final ObjectMap<String, String> modIndexMap = new ObjectMap<>();
    private static final Seq<Cons<Boolean>> modIndexWaiters = new Seq<>();
    private static boolean modIndexLoaded;
    private static boolean modIndexLoading;

    static{
        builtinMap.put("neon", "DeterMination-Wind/Neon");
        builtinMap.put("bek-tools", "DeterMination-Wind/Neon");
        builtinMap.put("betterminimap", "DeterMination-Wind/betterMiniMap");
        builtinMap.put("betterhotkey", "DeterMination-Wind/betterHotKey");
        builtinMap.put("powergrid-minimap", "DeterMination-Wind/Power-Grid-Minimap");
        builtinMap.put("stealth-path", "DeterMination-Wind/StealthPath");
        builtinMap.put("radial-build-menu", "DeterMination-Wind/Radial-Build-Menu-hud-");
        builtinMap.put("betterprojectoroverlay", "DeterMination-Wind/BetterProjectorOverlay");
        builtinMap.put("bettermapeditor", "DeterMination-Wind/BetterMapEditor");
        builtinMap.put("server-player-database", "DeterMination-Wind/ServerPlayerDataBase");
        builtinMap.put("updatescheme", "DeterMination-Wind/UpdateScheme");
    }

    private RepoResolver(){
    }

    public static ObjectMap<String, String> loadOverrides(String key){
        Seq<String> lines = Core.settings.getJson(key, Seq.class, String.class, Seq::new);
        ObjectMap<String, String> out = new ObjectMap<>();
        for(String line : lines){
            if(line == null) continue;
            int sep = line.indexOf('=');
            if(sep <= 0 || sep >= line.length() - 1) continue;
            String name = line.substring(0, sep).trim().toLowerCase(Locale.ROOT);
            String repo = sanitizeRepo(line.substring(sep + 1));
            if(!name.isEmpty() && !repo.isEmpty()){
                out.put(name, repo);
            }
        }
        return out;
    }

    public static void saveOverrides(String key, ObjectMap<String, String> map){
        Seq<String> lines = new Seq<>();
        for(ObjectMap.Entry<String, String> e : map){
            if(e == null || e.key == null || e.value == null) continue;
            String name = e.key.trim().toLowerCase(Locale.ROOT);
            String repo = sanitizeRepo(e.value);
            if(!name.isEmpty() && !repo.isEmpty()){
                lines.add(name + "=" + repo);
            }
        }
        lines.sort();
        Core.settings.putJson(key, String.class, lines);
    }

    public static String resolveRepo(Mods.LoadedMod mod, ObjectMap<String, String> overrides){
        if(mod == null) return "";
        String internalName = mod.name == null ? "" : mod.name.toLowerCase(Locale.ROOT);

        String override = overrides.get(internalName);
        String repo = sanitizeRepo(override);
        if(!repo.isEmpty()) return repo;

        repo = sanitizeRepo(mod.getRepo());
        if(!repo.isEmpty()) return repo;

        repo = sanitizeRepo(mod.meta == null ? null : mod.meta.repo);
        if(!repo.isEmpty()) return repo;

        repo = sanitizeRepo(builtinMap.get(internalName));
        return repo;
    }

    public static void loadModIndex(Cons<Boolean> done){
        if(modIndexLoaded){
            if(done != null) done.get(true);
            return;
        }

        if(done != null) modIndexWaiters.add(done);
        if(modIndexLoading) return;

        modIndexLoading = true;
        Http.get(modIndexUrl)
        .timeout(30000)
        .header("User-Agent", "Mindustry")
        .error(e -> finishModIndex(false))
        .submit(res -> {
            try{
                parseModIndex(Jval.read(res.getResultAsString()));
                finishModIndex(!modIndexMap.isEmpty());
            }catch(Throwable t){
                finishModIndex(false);
            }
        });
    }

    public static String resolveIndexedRepo(Mods.LoadedMod mod){
        if(!modIndexLoaded || mod == null) return "";

        String repo = lookupIndexedRepo(mod.name);
        if(!repo.isEmpty()) return repo;

        if(mod.meta != null){
            repo = lookupIndexedRepo(mod.meta.name);
            if(!repo.isEmpty()) return repo;

            repo = lookupIndexedRepo(mod.meta.displayName);
            if(!repo.isEmpty()) return repo;
        }

        return "";
    }

    public static String sanitizeRepo(String repo){
        String s = Strings.stripColors(repo == null ? "" : repo).trim();
        if(s.isEmpty()) return "";

        if(s.startsWith("https://github.com/")){
            s = s.substring("https://github.com/".length());
        }else if(s.startsWith("http://github.com/")){
            s = s.substring("http://github.com/".length());
        }else if(s.startsWith("github.com/")){
            s = s.substring("github.com/".length());
        }

        while(s.startsWith("/")){
            s = s.substring(1);
        }

        if(s.endsWith(".git")){
            s = s.substring(0, s.length() - 4);
        }
        if(s.endsWith("/")){
            s = s.substring(0, s.length() - 1);
        }

        String[] seg = s.split("/");
        if(seg.length < 2) return "";
        String owner = seg[0].trim();
        String name = seg[1].trim();
        if(owner.isEmpty() || name.isEmpty()) return "";
        if(owner.contains(" ") || name.contains(" ")) return "";
        return owner + "/" + name;
    }

    private static void finishModIndex(boolean success){
        modIndexLoading = false;
        modIndexLoaded = success && !modIndexMap.isEmpty();

        Seq<Cons<Boolean>> waiters = modIndexWaiters.copy();
        modIndexWaiters.clear();
        for(Cons<Boolean> waiter : waiters){
            if(waiter == null) continue;
            try{
                waiter.get(modIndexLoaded);
            }catch(Throwable ignored){
            }
        }
    }

    private static void parseModIndex(Jval json){
        modIndexMap.clear();
        if(json == null) return;

        if(json.isArray()){
            for(Jval item : json.asArray()){
                parseModIndexItem(item);
            }
        }else if(json.isObject()){
            Jval mods = json.get("mods");
            if(mods != null && mods.isArray()){
                for(Jval item : mods.asArray()){
                    parseModIndexItem(item);
                }
            }
        }
    }

    private static void parseModIndexItem(Jval item){
        if(item == null || item.isNull()) return;

        if(item.isString()){
            String repo = sanitizeRepo(item.asString());
            if(!repo.isEmpty()) addRepoNameKeys(repo);
            return;
        }

        if(!item.isObject()) return;

        String repo = sanitizeRepo(item.getString("repo", ""));
        if(repo.isEmpty()) repo = sanitizeRepo(item.getString("repository", ""));
        if(repo.isEmpty()) repo = sanitizeRepo(item.getString("github", ""));
        if(repo.isEmpty()) return;

        addIndexKey(item.getString("internalName", ""), repo);
        addIndexKey(item.getString("name", ""), repo);
        addIndexKey(item.getString("displayName", ""), repo);
        addRepoNameKeys(repo);
    }

    private static void addRepoNameKeys(String repo){
        String clean = sanitizeRepo(repo);
        if(clean.isEmpty()) return;
        int slash = clean.indexOf('/');
        if(slash >= 0 && slash < clean.length() - 1){
            addIndexKey(clean.substring(slash + 1), clean);
        }
    }

    private static void addIndexKey(String name, String repo){
        String key = lookupKey(name);
        String clean = sanitizeRepo(repo);
        if(key.isEmpty() || clean.isEmpty()) return;
        if(!modIndexMap.containsKey(key)){
            modIndexMap.put(key, clean);
        }
    }

    private static String lookupIndexedRepo(String name){
        String key = lookupKey(name);
        if(key.isEmpty()) return "";
        return sanitizeRepo(modIndexMap.get(key));
    }

    private static String lookupKey(String name){
        String s = Strings.stripColors(name == null ? "" : name).trim().toLowerCase(Locale.ROOT);
        if(s.isEmpty()) return "";

        StringBuilder out = new StringBuilder(s.length());
        for(int i = 0; i < s.length(); i++){
            char c = s.charAt(i);
            if((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')){
                out.append(c);
            }
        }
        return out.toString();
    }
}
