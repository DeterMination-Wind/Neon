package pinyinsearchsupport.ui;

import arc.Core;
import arc.func.Prov;
import arc.scene.Element;
import arc.scene.Group;
import arc.scene.event.ChangeListener;
import arc.scene.event.EventListener;
import arc.scene.ui.Image;
import arc.scene.ui.TextField;
import arc.struct.ObjectMap;
import arc.struct.ObjectSet;
import arc.struct.Seq;
import arc.util.Log;
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
    private final ObjectSet<String> bundleSearchKeys = new ObjectSet<>();
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
        }catch(Throwable ignored){}
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
        field.addListener(proxy);
    }

    private void onChange(TextField field){
        cancelDebounce(field);
        if(!Core.settings.getBool(PinyinSearchSupportMod.keyEnabled, true)){
            fireVanilla(field);
            return;
        }
        int delay = Math.max(0, Core.settings.getInt(PinyinSearchSupportMod.keyDelayMs, PinyinSearchSupportMod.defaultDelayMs));
        if(delay <= 0){
            runFilter(field);
            return;
        }
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
                        if(field.getScene() == null) return;
                        self.runFilter(field);
                    }
                });
            }
        }, delay / 1000f);
        debounces.put(field, t);
    }

    private void runFilter(TextField field){
        Seq<ChangeListener> list = vanillas.get(field);
        if(list == null || list.isEmpty()) return;

        String typed = field.getText();
        if(typed == null) typed = "";

        String prev = FieldTextProxy.swap(field, "");
        try{
            fireListeners(field, list);
        }finally{
            FieldTextProxy.swap(field, prev != null ? prev : typed);
        }

        if(typed.isEmpty()) return;

        ScopeTree scope = ScopeTree.locate(field);
        if(scope == null) return;

        MatchEngine.MatchOptions opts = new MatchEngine.MatchOptions(
            Core.settings.getBool(PinyinSearchSupportMod.keyFuzzy, true),
            Core.settings.getBool(PinyinSearchSupportMod.keyInitials, true),
            Core.settings.getBool(PinyinSearchSupportMod.keyHeteronym, true)
        );
        scope.postFilter(typed, opts);
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
