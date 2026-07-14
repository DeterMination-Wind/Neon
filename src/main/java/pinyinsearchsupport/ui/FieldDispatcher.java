package pinyinsearchsupport.ui;

import arc.Core;
import arc.func.Prov;
import arc.scene.Element;
import arc.scene.Group;
import arc.scene.event.ChangeListener;
import arc.scene.event.EventListener;
import arc.scene.ui.Image;
import arc.scene.ui.Label;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.TextField;
import arc.scene.ui.Tooltip;
import arc.scene.ui.layout.Table;
import arc.struct.ObjectMap;
import arc.struct.ObjectSet;
import arc.struct.Seq;
import arc.util.Log;
import arc.util.Strings;
import arc.util.Timer;
import arc.util.pooling.Pools;
import mindustry.Vars;
import mindustry.gen.Icon;
import pinyinsearchsupport.PinyinSearchSupportMod;
import pinyinsearchsupport.match.MatchEngine;

import java.util.Locale;

public final class FieldDispatcher{
    private final ObjectSet<TextField> patched = new ObjectSet<>();
    private final ObjectMap<TextField, Seq<ChangeListener>> vanillas = new ObjectMap<>();
    private final ObjectMap<TextField, Timer.Task> debounces = new ObjectMap<>();
    private final ObjectMap<TextField, ChangeListener> proxies = new ObjectMap<>();
    private final ObjectSet<String> bundleSearchKeys = new ObjectSet<>();
    private final ObjectSet<String> modsContextTokens = new ObjectSet<>();
    private final ObjectSet<String> modsExactTokens = new ObjectSet<>();
    private boolean keysCollected;

    public void scan(){
        if(Vars.headless || Core.scene == null || Core.scene.root == null) return;

        if(!keysCollected) collectBundleKeys();

        Seq<TextField> stale = new Seq<>();
        for(TextField f : patched){
            if(f == null || f.getScene() == null) stale.add(f);
        }
        for(TextField f : stale){
            cancelDebounce(f);
            proxies.remove(f);
            patched.remove(f);
            vanillas.remove(f);
        }

        Seq<TextField> all = new Seq<>();
        collect(Core.scene.root, all);
        for(TextField f : all){
            if(patched.contains(f)) continue;
            if(!isSearchField(f)) continue;
            attach(f);
            patched.add(f);
        }
    }

    private void collectBundleKeys(){
        keysCollected = true;
        if(Core.bundle == null) return;
        try{
            String[] hints = {
                "players.search", "editor.search", "save.search", "schematic.search", "search",
                "locales.searchname", "locales.searchvalue", "locales.searchlocale"
            };
            for(String k : hints){
                String v = Core.bundle.get(k, "");
                if(v != null && !v.isEmpty()) bundleSearchKeys.add(v);
            }
            String[] modsKeys = {
                "mods", "mods.browser", "mods.browser.sortdate", "mods.browser.sortstars",
                "mods.guide", "mods.openfolder", "mods.none", "mods.disabled",
                "mods.group.mod", "mods.group.internal", "mod.disabled"
            };
            for(String k : modsKeys){
                addModsContextToken("@" + k);
                String v = Core.bundle.get(k, "");
                addModsContextToken(v);
                if(!"mod.disabled".equals(k)){
                    addModsContextToken(Strings.stripColors(v));
                }
            }
            addModsExactToken("@mods");
            addModsExactToken(Core.bundle.get("mods", ""));
            addModsExactToken(Strings.stripColors(Core.bundle.get("mods", "")));
        }catch(Throwable ignored){}
    }

    private void addModsContextToken(String token){
        if(token == null) return;
        token = token.trim().toLowerCase(Locale.ROOT);
        if(token.length() < 4) return;
        if("mod".equals(token) || "mods".equals(token) || "disabled".equals(token)) return;
        modsContextTokens.add(token);
    }

    private void addModsExactToken(String token){
        if(token == null) return;
        token = token.trim().toLowerCase(Locale.ROOT);
        if(token.length() < 2) return;
        modsExactTokens.add(token);
    }

    private static void collect(Element root, Seq<TextField> out){
        if(root instanceof TextField) out.add((TextField)root);
        if(root instanceof Group){
            Seq<Element> ch = ((Group)root).getChildren();
            for(int i = 0; i < ch.size; i++) collect(ch.get(i), out);
        }
    }

    private boolean isSearchField(TextField f){
        if(f == null) return false;
        if(f.name != null && f.name.toLowerCase(Locale.ROOT).contains("search")) return true;
        String msg = f.getMessageText();
        if(msg != null && !msg.isEmpty() && bundleSearchKeys.contains(msg)) return true;
        if(hasZoomSibling(f)) return true;
        return false;
    }

    private static boolean hasZoomSibling(TextField f){
        if(f == null || f.parent == null) return false;
        Seq<Element> sib = f.parent.getChildren();
        for(int i = 0; i < sib.size; i++){
            Element e = sib.get(i);
            if(e instanceof Image && ((Image)e).getDrawable() == Icon.zoom) return true;
        }
        return false;
    }

    private void attach(TextField field){
        Seq<EventListener> listeners = field.getListeners();
        Seq<ChangeListener> existing = new Seq<>();
        for(int i = 0; i < listeners.size; i++){
            EventListener l = listeners.get(i);
            if(l == proxies.get(field)) continue;
            if(l instanceof ChangeListener) existing.add((ChangeListener)l);
        }
        if(existing.isEmpty()) return;

        for(ChangeListener cl : existing){
            field.removeListener(cl);
        }
        vanillas.put(field, existing);

        final FieldDispatcher self = this;
        ChangeListener proxy = new ChangeListener(){
            @Override
            public void changed(ChangeEvent event, Element actor){
                if(actor != field) return;
                self.onChange(field);
            }
        };
        proxies.put(field, proxy);
        field.addListener(proxy);
    }

    private void onChange(TextField field){
        cancelDebounce(field);
        if(!Core.settings.getBool(PinyinSearchSupportMod.keyEnabled, true)){
            fireVanilla(field);
            return;
        }
        final ScopeTree.Context context = ScopeTree.capture(field);
        int delay = Math.max(0, Core.settings.getInt(PinyinSearchSupportMod.keyDelayMs, PinyinSearchSupportMod.defaultDelayMs));
        if(delay <= 0){
            runFilter(field, context);
            return;
        }
        final String scheduledText = field.getText();
        final FieldDispatcher self = this;
        Timer.Task t = Timer.schedule(new Timer.Task(){
            @Override
            public void run(){
                final Timer.Task taskRef = this;
                Core.app.post(new Runnable(){
                    @Override
                    public void run(){
                        if(debounces.get(field) != taskRef) return;
                        debounces.remove(field);
                        String currentText = field.getText();
                        boolean sameText = scheduledText == null ? currentText == null : scheduledText.equals(currentText);
                        if(!sameText || context == null || !context.isActive(field)){
                            self.fireVanilla(field);
                            return;
                        }
                        self.runFilter(field, context);
                    }
                });
            }
        }, delay / 1000f);
        debounces.put(field, t);
    }

    private void runFilter(TextField field, ScopeTree.Context context){
        Seq<ChangeListener> list = vanillas.get(field);
        if(list == null || list.isEmpty()) return;

        String typed = field.getText();
        if(typed == null) typed = "";

        if(typed.isEmpty() || !shouldUsePinyinSearch(typed) || isModsSearchField(field)){
            fireListeners(field, list);
            return;
        }

        if(context == null || !context.isActive(field)){
            fireListeners(field, list);
            return;
        }

        MatchEngine.MatchOptions opts = new MatchEngine.MatchOptions(
            Core.settings.getBool(PinyinSearchSupportMod.keyFuzzy, true),
            Core.settings.getBool(PinyinSearchSupportMod.keyInitials, true),
            Core.settings.getBool(PinyinSearchSupportMod.keyHeteronym, true)
        );

        if(SectorListAdapter.isSectorSearch(field)){
            fireListeners(field, list);
            SectorListAdapter.filter(field, typed, opts);
            return;
        }

        if(SchematicsAdapter.isSchematicsSearch(field)){
            if(!SchematicsAdapter.filter(field, typed, opts, context)){
                fireListeners(field, list);
            }
            return;
        }

        String prev = FieldTextProxy.swap(field, "");
        if(field.getText() != null && !field.getText().isEmpty()){
            fireListeners(field, list);
            return;
        }
        try{
            fireListeners(field, list);
        }finally{
            FieldTextProxy.swap(field, prev != null ? prev : typed);
        }

        if(!context.isActive(field)){
            fireListeners(field, list);
            return;
        }

        ScopeTree scope = ScopeTree.locate(field, context);
        if(scope == null || !scope.isValid()){
            fireListeners(field, list);
            return;
        }

        try{
            scope.postFilter(typed, opts);
        }catch(Throwable t){
            Log.warn("[PinyinSearchSupport] post filter failed: @", t.getMessage());
            fireListeners(field, list);
        }
    }

    private static boolean shouldUsePinyinSearch(String query){
        if(query == null || query.isEmpty()) return false;
        for(int i = 0; i < query.length(); i++){
            char c = query.charAt(i);
            if(Character.isLetter(c) || pinyinsearchsupport.match.PinyinIndex.isCjk(c)) return true;
        }
        return false;
    }

    private boolean isModsSearchField(TextField field){
        if(field == null || field.getScene() == null) return false;
        if(!keysCollected) collectBundleKeys();

        Group root = locateContextRoot(field);
        if(root == null) return false;

        Group cursor = root;
        for(int depth = 0; depth < 4 && cursor != null; depth++){
            if(Core.scene != null && cursor == Core.scene.root) break;
            if(containsModsContext(cursor, new int[]{0})) return true;
            cursor = cursor.parent;
        }
        return false;
    }

    private static Group locateContextRoot(TextField field){
        Group cursor = field.parent;
        for(int depth = 0; depth < 12 && cursor != null; depth++){
            if(hasTableScrollPane(cursor)) return cursor;
            cursor = cursor.parent;
        }
        return null;
    }

    private static boolean hasTableScrollPane(Element root){
        if(root instanceof ScrollPane){
            return ((ScrollPane)root).getWidget() instanceof Table;
        }
        if(root instanceof Group){
            Seq<Element> ch = ((Group)root).getChildren();
            for(int i = 0; i < ch.size; i++){
                if(hasTableScrollPane(ch.get(i))) return true;
            }
        }
        return false;
    }

    private boolean containsModsContext(Element root, int[] visited){
        if(root == null || visited[0]++ > 1200) return false;

        if(root instanceof Label){
            CharSequence text = ((Label)root).getText();
            if(matchesModsContext(text == null ? null : text.toString())) return true;
        }

        Seq<EventListener> listeners = root.getListeners();
        for(int i = 0; i < listeners.size; i++){
            EventListener l = listeners.get(i);
            if(l instanceof Tooltip && containsModsContext(((Tooltip)l).getContainer(), visited)){
                return true;
            }
        }

        if(root instanceof Group){
            Seq<Element> ch = ((Group)root).getChildren();
            for(int i = 0; i < ch.size; i++){
                if(containsModsContext(ch.get(i), visited)) return true;
            }
        }
        return false;
    }

    private boolean matchesModsContext(String text){
        if(text == null || text.isEmpty()) return false;

        String lower = text.trim().toLowerCase(Locale.ROOT);
        String stripped = Strings.stripColors(lower).trim();
        if(modsExactTokens.contains(lower) || modsExactTokens.contains(stripped)) return true;

        if(lower.contains("@mods.") || lower.contains("@mod.disabled")) return true;
        for(String token : modsContextTokens){
            if(token != null && token.length() > 0 && (lower.contains(token) || stripped.contains(token))) return true;
        }
        return false;
    }

    private void fireVanilla(TextField field){
        Seq<ChangeListener> list = vanillas.get(field);
        if(list != null) fireListeners(field, list);
    }

    private void fireListeners(TextField field, Seq<ChangeListener> list){
        for(int i = 0; i < list.size; i++){
            try{
                ChangeListener.ChangeEvent ev = Pools.obtain(ChangeListener.ChangeEvent.class, new Prov<ChangeListener.ChangeEvent>(){
                    @Override
                    public ChangeListener.ChangeEvent get(){
                        return new ChangeListener.ChangeEvent();
                    }
                });
                ev.targetActor = field;
                list.get(i).changed(ev, field);
                Pools.free(ev);
            }catch(Throwable t){
                Log.warn("[PinyinSearchSupport] vanilla listener threw: @", t.getMessage());
            }
        }
    }

    private void cancelDebounce(TextField field){
        Timer.Task t = debounces.remove(field);
        if(t != null) t.cancel();
    }
}
