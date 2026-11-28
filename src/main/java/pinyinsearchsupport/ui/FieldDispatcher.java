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
import arc.scene.ui.layout.Cell;
import arc.scene.ui.layout.Table;
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
import pinyinsearchsupport.match.PinyinIndex;

import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

public final class FieldDispatcher{
    private static final int MAX_SCENE_DEPTH = 48;
    private static final int MAX_SCENE_NODES = 8192;
    private static final int MAX_MODS_SCAN_NODES = 1200;
    private static final int MAX_MODS_SCAN_DEPTH = 24;
    private static final int MAX_FIELD_FAILURES = 3;
    private static final int MAX_SCAN_FAILURES = 5;
    private static final long SCAN_COOLDOWN_MS = 30_000L;
    private static final int MAX_CONSECUTIVE_REFRESHES = 4;
    private static final long REFRESH_WINDOW_MS = 2_000L;

    private static final Prov<ChangeListener.ChangeEvent> changeEventProv = new Prov<ChangeListener.ChangeEvent>(){
        @Override
        public ChangeListener.ChangeEvent get(){
            return new ChangeListener.ChangeEvent();
        }
    };

    private enum FieldState{
        IDLE,
        DEBOUNCING,
        DISPATCHING,
        BYPASSED
    }

    private final Map<TextField, FieldRecord> records = new WeakHashMap<TextField, FieldRecord>();
    private final Seq<TextField> scanScratch = new Seq<TextField>(32);
    private final Seq<TextField> refreshScratch = new Seq<TextField>(16);

    private final ObjectSet<String> bundleSearchKeys = new ObjectSet<String>();
    private final ObjectSet<String> modsContextTokens = new ObjectSet<String>();
    private final ObjectSet<String> modsExactTokens = new ObjectSet<String>();
    private boolean keysCollected;

    private int dispatchDepth;
    private int consecutiveScanFailures;
    private long scanCooldownUntilMs;

    public void scan(){
        if(Vars.headless || Core.scene == null || Core.scene.root == null) return;

        long now = System.currentTimeMillis();
        if(scanCooldownUntilMs > 0L && now < scanCooldownUntilMs) return;

        try{
            boolean enabled = getSettingBool(PinyinSearchSupportMod.keyEnabled, true);
            sweepStaleRecords(!enabled);
            if(!enabled) return;

            if(!keysCollected) collectBundleKeys();

            scanScratch.clear();
            collect(Core.scene.root, 0, new int[]{0}, scanScratch);
            for(int i = 0; i < scanScratch.size; i++){
                TextField f = scanScratch.get(i);
                if(f == null || records.containsKey(f)) continue;
                if(!isSearchField(f)) continue;
                try{
                    attach(f);
                }catch(Throwable t){
                    Log.warn("[PinyinSearchSupport] attach failed: @", t.getMessage());
                }
            }
            scanScratch.clear();

            refreshScratch.clear();
            for(Map.Entry<TextField, FieldRecord> entry : records.entrySet()){
                TextField f = entry.getKey();
                FieldRecord rec = entry.getValue();
                if(f != null && rec != null && rec.state == FieldState.IDLE){
                    refreshScratch.add(f);
                }
            }
            for(int i = 0; i < refreshScratch.size; i++){
                TextField f = refreshScratch.get(i);
                FieldRecord rec = records.get(f);
                if(rec == null || rec.state != FieldState.IDLE) continue;
                try{
                    refreshAfterVanillaRebuild(f, rec);
                }catch(Throwable t){
                    recordFieldFailure(f, rec, "refresh", t);
                }
            }
            refreshScratch.clear();

            consecutiveScanFailures = 0;
            scanCooldownUntilMs = 0L;
        }catch(Throwable t){
            scanScratch.clear();
            refreshScratch.clear();
            int failures = ++consecutiveScanFailures;
            if(failures <= 3){
                Log.warn("[PinyinSearchSupport] scan failed: @", t.getMessage());
            }
            if(failures >= MAX_SCAN_FAILURES){
                scanCooldownUntilMs = now + SCAN_COOLDOWN_MS;
                Log.warn("[PinyinSearchSupport] scan circuit breaker tripped; pausing scan for 30s.");
            }
        }
    }

    private void sweepStaleRecords(boolean disabledMode){
        Iterator<Map.Entry<TextField, FieldRecord>> it = records.entrySet().iterator();
        while(it.hasNext()){
            Map.Entry<TextField, FieldRecord> entry = it.next();
            TextField f = entry.getKey();
            FieldRecord rec = entry.getValue();
            if(f == null || rec == null || f.getScene() == null){
                if(rec != null){
                    rec.cancelDebounce();
                    rec.snapshot = null;
                }
                it.remove();
            }else if(disabledMode){
                rec.cancelDebounce();
                rec.snapshot = null;
                if(rec.state == FieldState.DEBOUNCING){
                    rec.state = FieldState.IDLE;
                }
            }
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
            for(int i = 0; i < hints.length; i++){
                String v = Core.bundle.get(hints[i], "");
                if(v != null && !v.isEmpty()) bundleSearchKeys.add(v);
            }
            String[] modsKeys = {
                "mods", "mods.browser", "mods.browser.sortdate", "mods.browser.sortstars",
                "mods.guide", "mods.openfolder", "mods.none", "mods.disabled",
                "mods.group.mod", "mods.group.internal", "mod.disabled"
            };
            for(int i = 0; i < modsKeys.length; i++){
                String k = modsKeys[i];
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

    private static void collect(Element root, int depth, int[] visited, Seq<TextField> out){
        if(root == null || depth > MAX_SCENE_DEPTH || visited[0]++ >= MAX_SCENE_NODES) return;
        if(root instanceof TextField) out.add((TextField)root);
        if(root instanceof Group){
            Seq<Element> ch = ((Group)root).getChildren();
            if(ch == null) return;
            for(int i = 0; i < ch.size; i++){
                if(visited[0] >= MAX_SCENE_NODES) break;
                collect(ch.get(i), depth + 1, visited, out);
            }
        }
    }

    private boolean isSearchField(TextField f){
        if(f == null) return false;
        if(f.name != null && f.name.toLowerCase(Locale.ROOT).contains("search")) return true;
        String msg = f.getMessageText();
        if(msg != null && !msg.isEmpty() && bundleSearchKeys.contains(msg)) return true;
        return hasZoomSibling(f);
    }

    private static boolean hasZoomSibling(TextField f){
        if(f == null || f.parent == null) return false;
        Seq<Element> sib = f.parent.getChildren();
        if(sib == null) return false;
        for(int i = 0; i < sib.size; i++){
            Element e = sib.get(i);
            if(e instanceof Image && ((Image)e).getDrawable() == Icon.zoom) return true;
        }
        return false;
    }

    boolean attach(TextField field){
        if(field == null || records.containsKey(field)) return false;

        Seq<EventListener> listeners = field.getListeners();
        if(listeners == null || listeners.isEmpty()) return false;

        Seq<ChangeListener> existing = new Seq<ChangeListener>();
        for(int i = 0; i < listeners.size; i++){
            EventListener l = listeners.get(i);
            if(l instanceof ChangeListener){
                existing.add((ChangeListener)l);
            }
        }
        if(existing.isEmpty()) return false;

        for(int i = 0; i < existing.size; i++){
            field.removeListener(existing.get(i));
        }

        final FieldDispatcher self = this;
        final FieldRecord record = new FieldRecord(existing);
        ChangeListener proxy = new ChangeListener(){
            @Override
            public void changed(ChangeEvent event, Element actor){
                if(actor != field) return;
                self.onChange(field, record);
            }
        };
        record.proxy = proxy;
        field.addListener(proxy);
        records.put(field, record);
        return true;
    }

    private void onChange(TextField field, FieldRecord record){
        if(field == null || record == null) return;
        // Strict reentrancy guard: never re-enter while dispatching or when bypassed.
        if(record.state == FieldState.DISPATCHING || dispatchDepth > 0) return;
        if(record.state == FieldState.BYPASSED){
            fireVanillaGuarded(field, record);
            return;
        }

        record.cancelDebounce();
        record.resetRefreshGuard();

        if(!getSettingBool(PinyinSearchSupportMod.keyEnabled, true)){
            record.snapshot = null;
            record.state = FieldState.IDLE;
            fireVanillaGuarded(field, record);
            return;
        }

        final ScopeTree.Context context;
        try{
            context = ScopeTree.capture(field);
        }catch(Throwable t){
            recordFieldFailure(field, record, "capture", t);
            fireVanillaGuarded(field, record);
            return;
        }

        int delay = Math.max(0, getSettingInt(PinyinSearchSupportMod.keyDelayMs, PinyinSearchSupportMod.defaultDelayMs));
        if(delay <= 0 || Core.app == null){
            runFilterSafe(field, record, context);
            return;
        }

        final long gen = ++record.generation;
        final String scheduledText = field.getText();
        record.state = FieldState.DEBOUNCING;

        final FieldDispatcher self = this;
        final WeakReference<TextField> fieldRef = new WeakReference<TextField>(field);
        final WeakReference<ScopeTree.Context> contextRef = new WeakReference<ScopeTree.Context>(context);
        Timer.Task t = Timer.schedule(new Timer.Task(){
            @Override
            public void run(){
                if(Core.app == null) return;
                Core.app.post(new Runnable(){
                    @Override
                    public void run(){
                        TextField target = fieldRef.get();
                        if(target == null) return;
                        FieldRecord current = records.get(target);
                        if(current != record || current.generation != gen) return;
                        current.debounceTask = null;

                        if(current.state == FieldState.BYPASSED || current.state == FieldState.DISPATCHING) return;
                        current.state = FieldState.IDLE;

                        if(target.getScene() == null){
                            current.snapshot = null;
                            return;
                        }

                        String currentText = target.getText();
                        boolean sameText = scheduledText == null ? currentText == null : scheduledText.equals(currentText);
                        ScopeTree.Context ctx = contextRef.get();
                        if(!sameText || ctx == null || !ctx.isActive(target)){
                            self.fireVanillaGuarded(target, current);
                            return;
                        }
                        self.runFilterSafe(target, current, ctx);
                    }
                });
            }
        }, delay / 1000f);
        record.debounceTask = t;
    }

    private void runFilterSafe(TextField field, FieldRecord record, ScopeTree.Context context){
        if(field == null || record == null) return;
        if(record.state == FieldState.DISPATCHING || dispatchDepth > 0) return;
        if(record.state == FieldState.BYPASSED){
            fireVanillaGuarded(field, record);
            return;
        }

        FieldState prevState = record.state;
        record.state = FieldState.DISPATCHING;
        dispatchDepth++;
        try{
            runFilterInternal(field, record, context);
        }catch(Throwable t){
            record.snapshot = null;
            recordFieldFailure(field, record, "runFilter", t);
            fireListeners(field, record.vanillas);
        }finally{
            dispatchDepth--;
            if(record.state == FieldState.DISPATCHING){
                record.state = prevState == FieldState.BYPASSED ? FieldState.BYPASSED : FieldState.IDLE;
            }
        }
    }

    private void runFilterInternal(TextField field, FieldRecord record, ScopeTree.Context context){
        Seq<ChangeListener> list = record.vanillas;
        if(list == null || list.isEmpty()) return;

        String typed = field.getText();
        if(typed == null) typed = "";

        boolean schematicSearch = SchematicsAdapter.isSchematicsSearch(field);
        if(!shouldDispatchSearch(typed, schematicSearch) || isModsSearchField(field, record)){
            record.snapshot = null;
            fireListeners(field, list);
            return;
        }

        if(context == null || !context.isActive(field)){
            record.snapshot = null;
            fireListeners(field, list);
            return;
        }

        MatchEngine.MatchOptions opts = new MatchEngine.MatchOptions(
            getSettingBool(PinyinSearchSupportMod.keyFuzzy, true),
            getSettingBool(PinyinSearchSupportMod.keyInitials, true),
            getSettingBool(PinyinSearchSupportMod.keyHeteronym, true)
        );

        if(SectorListAdapter.isSectorSearch(field)){
            fireListeners(field, list);
            if(SectorListAdapter.tryFilter(field, typed, opts)){
                rememberFilteredResults(field, record, context);
                record.consecutiveFailures = 0;
            }else{
                record.snapshot = null;
            }
            return;
        }

        if(schematicSearch){
            if(SchematicsAdapter.filter(field, typed, opts, context)){
                rememberFilteredResults(field, record, context);
                record.consecutiveFailures = 0;
            }else{
                record.snapshot = null;
                fireListeners(field, list);
            }
            return;
        }

        boolean swapped = FieldTextProxy.withSwappedText(field, "", new Runnable(){
            @Override
            public void run(){
                fireListeners(field, list);
            }
        });
        if(!swapped){
            record.snapshot = null;
            fireListeners(field, list);
            return;
        }

        if(!context.isActive(field)){
            record.snapshot = null;
            fireListeners(field, list);
            return;
        }

        ScopeTree scope = ScopeTree.locate(field, context);
        if(scope == null || !scope.isValid()){
            record.snapshot = null;
            fireListeners(field, list);
            return;
        }

        try{
            scope.postFilter(typed, opts);
            rememberFilteredResults(field, record, context);
            record.consecutiveFailures = 0;
        }catch(Throwable t){
            record.snapshot = null;
            recordFieldFailure(field, record, "postFilter", t);
            fireListeners(field, list);
        }
    }

    /**
     * Native dialogs often rebuild their result table without changing the text field:
     * database planet tabs, map filters, and schematic tags all do this. Detect that
     * replacement and replay the pinyin pass against a fresh empty native query.
     */
    private void refreshAfterVanillaRebuild(TextField field, FieldRecord record){
        if(field == null || record == null) return;
        if(record.state != FieldState.IDLE || dispatchDepth > 0) return;
        if(field.getScene() != Core.scene || !field.visible) return;

        String typed = field.getText();
        if(typed == null || typed.isEmpty()){
            record.snapshot = null;
            return;
        }

        boolean schematicSearch = SchematicsAdapter.isSchematicsSearch(field);
        if(!shouldDispatchSearch(typed, schematicSearch) || isModsSearchField(field, record)){
            record.snapshot = null;
            return;
        }

        ScopeTree.Context context = ScopeTree.capture(field);
        if(context == null || !context.isActive(field)){
            record.snapshot = null;
            return;
        }

        ScopeTree scope = ScopeTree.locate(field, context);
        if(scope == null || !scope.isValid()){
            record.snapshot = null;
            return;
        }

        ResultSnapshot snapshot = record.snapshot;
        if(snapshot == null || !snapshot.matches(context, scope)){
            long now = System.currentTimeMillis();
            if(record.isRefreshStorm(now)){
                return;
            }
            record.noteRefresh(now);
            runFilterSafe(field, record, context);
        }
    }

    private void rememberFilteredResults(TextField field, FieldRecord record, ScopeTree.Context context){
        ScopeTree scope = ScopeTree.locate(field, context);
        if(scope == null || !scope.isValid()){
            record.snapshot = null;
            return;
        }
        record.snapshot = new ResultSnapshot(context, scope);
    }

    private void recordFieldFailure(TextField field, FieldRecord record, String op, Throwable t){
        if(record == null) return;
        int failures = ++record.consecutiveFailures;
        if(failures <= MAX_FIELD_FAILURES){
            Log.warn("[PinyinSearchSupport] " + op + " failed: @", t == null ? "unknown" : t.getMessage());
        }
        if(failures >= MAX_FIELD_FAILURES){
            bypassAndRestore(field, record);
        }
    }

    private void bypassAndRestore(TextField field, FieldRecord record){
        if(record == null || record.state == FieldState.BYPASSED) return;
        record.cancelDebounce();
        record.snapshot = null;
        record.state = FieldState.BYPASSED;

        if(field != null){
            try{
                if(record.proxy != null){
                    field.removeListener(record.proxy);
                }
                if(record.vanillas != null){
                    for(int i = 0; i < record.vanillas.size; i++){
                        ChangeListener cl = record.vanillas.get(i);
                        if(cl != null){
                            field.addListener(cl);
                        }
                    }
                }
            }catch(Throwable ignored){}
        }
        Log.warn("[PinyinSearchSupport] field circuit breaker tripped; restored vanilla search listeners.");
    }

    boolean isBypassed(TextField field){
        FieldRecord rec = records.get(field);
        return rec != null && rec.state == FieldState.BYPASSED;
    }

    private static boolean shouldUsePinyinSearch(String query){
        if(query == null || query.isEmpty()) return false;
        int len = query.length();
        int i = 0;
        while(i < len){
            int cp = query.codePointAt(i);
            if(Character.isLetter(cp) || PinyinIndex.isCjk(cp)) return true;
            i += Character.charCount(cp);
        }
        return false;
    }

    static boolean shouldDispatchSearch(String query, boolean schematicSearch){
        return query != null && !query.isEmpty() && (schematicSearch || shouldUsePinyinSearch(query));
    }

    private boolean isModsSearchField(TextField field, FieldRecord record){
        if(field == null || field.getScene() == null) return false;
        if(!keysCollected) collectBundleKeys();

        Group root = locateContextRoot(field);
        if(root == null) return false;

        if(record != null && record.modsContextCached && record.modsContextRootRef != null
            && record.modsContextRootRef.get() == root){
            return record.isModsField;
        }

        boolean found = false;
        Group cursor = root;
        for(int depth = 0; depth < 4 && cursor != null; depth++){
            if(Core.scene != null && cursor == Core.scene.root) break;
            if(containsModsContext(cursor, 0, new int[]{0})){
                found = true;
                break;
            }
            cursor = cursor.parent;
        }

        if(record != null){
            record.modsContextCached = true;
            record.modsContextRootRef = new WeakReference<Group>(root);
            record.isModsField = found;
        }
        return found;
    }

    private static Group locateContextRoot(TextField field){
        Group cursor = field.parent;
        for(int depth = 0; depth < 12 && cursor != null; depth++){
            if(hasTableScrollPane(cursor, 0, new int[]{0})) return cursor;
            cursor = cursor.parent;
        }
        return null;
    }

    private static boolean hasTableScrollPane(Element root, int depth, int[] visited){
        if(root == null || depth > MAX_MODS_SCAN_DEPTH || visited[0]++ >= MAX_MODS_SCAN_NODES) return false;
        if(root instanceof ScrollPane){
            return ((ScrollPane)root).getWidget() instanceof Table;
        }
        if(root instanceof Group){
            Seq<Element> ch = ((Group)root).getChildren();
            if(ch == null) return false;
            for(int i = 0; i < ch.size; i++){
                if(hasTableScrollPane(ch.get(i), depth + 1, visited)) return true;
            }
        }
        return false;
    }

    private boolean containsModsContext(Element root, int depth, int[] visited){
        if(root == null || depth > MAX_MODS_SCAN_DEPTH || visited[0]++ >= MAX_MODS_SCAN_NODES) return false;

        if(root instanceof Label){
            CharSequence text = ((Label)root).getText();
            if(matchesModsContext(text == null ? null : text.toString())) return true;
        }

        Seq<EventListener> listeners = root.getListeners();
        if(listeners != null){
            for(int i = 0; i < listeners.size; i++){
                EventListener l = listeners.get(i);
                if(l instanceof Tooltip && containsModsContext(((Tooltip)l).getContainer(), depth + 1, visited)){
                    return true;
                }
            }
        }

        if(root instanceof Group){
            Seq<Element> ch = ((Group)root).getChildren();
            if(ch != null){
                for(int i = 0; i < ch.size; i++){
                    if(containsModsContext(ch.get(i), depth + 1, visited)) return true;
                }
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

    private void fireVanillaGuarded(TextField field, FieldRecord record){
        if(field == null || record == null || record.vanillas == null) return;
        if(dispatchDepth > 0 || record.state == FieldState.DISPATCHING) return;

        FieldState prev = record.state;
        if(prev != FieldState.BYPASSED){
            record.state = FieldState.DISPATCHING;
        }
        dispatchDepth++;
        try{
            fireListeners(field, record.vanillas);
        }finally{
            dispatchDepth--;
            if(record.state == FieldState.DISPATCHING){
                record.state = prev == FieldState.BYPASSED ? FieldState.BYPASSED : FieldState.IDLE;
            }
        }
    }

    private void fireListeners(TextField field, Seq<ChangeListener> list){
        if(field == null || list == null) return;
        for(int i = 0; i < list.size; i++){
            ChangeListener listener = list.get(i);
            if(listener == null) continue;
            ChangeListener.ChangeEvent ev = null;
            try{
                ev = Pools.obtain(ChangeListener.ChangeEvent.class, changeEventProv);
                ev.targetActor = field;
                listener.changed(ev, field);
            }catch(Throwable t){
                Log.warn("[PinyinSearchSupport] vanilla listener threw: @", t.getMessage());
            }finally{
                if(ev != null){
                    try{
                        Pools.free(ev);
                    }catch(Throwable ignored){}
                }
            }
        }
    }

    private static boolean getSettingBool(String key, boolean def){
        if(Core.settings == null) return def;
        try{
            return Core.settings.getBool(key, def);
        }catch(Throwable ignored){
            return def;
        }
    }

    private static int getSettingInt(String key, int def){
        if(Core.settings == null) return def;
        try{
            return Core.settings.getInt(key, def);
        }catch(Throwable ignored){
            return def;
        }
    }

    private static final class FieldRecord{
        final Seq<ChangeListener> vanillas;
        ChangeListener proxy;
        FieldState state = FieldState.IDLE;
        Timer.Task debounceTask;
        long generation;
        ResultSnapshot snapshot;
        int consecutiveFailures;

        boolean modsContextCached;
        WeakReference<Group> modsContextRootRef;
        boolean isModsField;

        int consecutiveRefreshes;
        long lastRefreshWindowStartMs;

        FieldRecord(Seq<ChangeListener> vanillas){
            this.vanillas = vanillas;
        }

        void cancelDebounce(){
            generation++;
            Timer.Task task = debounceTask;
            debounceTask = null;
            if(task != null){
                try{
                    task.cancel();
                }catch(Throwable ignored){}
            }
        }

        void resetRefreshGuard(){
            consecutiveRefreshes = 0;
            lastRefreshWindowStartMs = 0L;
        }

        boolean isRefreshStorm(long now){
            if(lastRefreshWindowStartMs > 0L && now - lastRefreshWindowStartMs < REFRESH_WINDOW_MS){
                return consecutiveRefreshes >= MAX_CONSECUTIVE_REFRESHES;
            }
            return false;
        }

        void noteRefresh(long now){
            if(lastRefreshWindowStartMs == 0L || now - lastRefreshWindowStartMs >= REFRESH_WINDOW_MS){
                lastRefreshWindowStartMs = now;
                consecutiveRefreshes = 1;
            }else{
                consecutiveRefreshes++;
            }
        }
    }

    /** Weakly-referenced snapshot of only the native result surface we own. */
    static final class ResultSnapshot{
        final WeakReference<Group> rootRef;
        final WeakReference<Group> originalParentRef;
        final WeakReference<ScrollPane> paneRef;
        final WeakReference<Table> tableRef;
        final Seq<WeakReference<Element>> actorRefs = new Seq<WeakReference<Element>>();

        ResultSnapshot(ScopeTree.Context context, ScopeTree scope){
            rootRef = new WeakReference<Group>(context.root);
            originalParentRef = new WeakReference<Group>(context.originalParent);
            paneRef = new WeakReference<ScrollPane>(scope.primaryPane());
            Table primary = scope.primaryTable();
            tableRef = new WeakReference<Table>(primary);
            captureActorRefs(primary, actorRefs);
        }

        boolean matches(ScopeTree.Context context, ScopeTree scope){
            if(context == null || scope == null) return false;
            Group root = rootRef.get();
            Group originalParent = originalParentRef.get();
            ScrollPane pane = paneRef.get();
            Table table = tableRef.get();
            if(root == null || root != context.root || originalParent == null || originalParent != context.originalParent){
                return false;
            }
            if(pane == null || pane != scope.primaryPane() || table == null || table != scope.primaryTable()){
                return false;
            }
            return sameActorRefs(table, actorRefs);
        }

        static boolean sameActors(Table table, Seq<Element> expected){
            if(table == null || expected == null) return false;
            Seq<Cell> cells = table.getCells();
            if(cells == null || cells.size != expected.size) return false;
            for(int i = 0; i < cells.size; i++){
                Cell<?> c = cells.get(i);
                if(c == null || c.get() != expected.get(i)) return false;
            }
            return true;
        }

        private static boolean sameActorRefs(Table table, Seq<WeakReference<Element>> expected){
            if(table == null || expected == null) return false;
            Seq<Cell> cells = table.getCells();
            if(cells == null || cells.size != expected.size) return false;
            for(int i = 0; i < cells.size; i++){
                Cell<?> c = cells.get(i);
                WeakReference<Element> ref = expected.get(i);
                Element expectedActor = ref == null ? null : ref.get();
                if(c == null || expectedActor == null || c.get() != expectedActor) return false;
            }
            return true;
        }

        private static void captureActorRefs(Table table, Seq<WeakReference<Element>> out){
            if(table == null || out == null) return;
            Seq<Cell> cells = table.getCells();
            if(cells == null) return;
            for(int i = 0; i < cells.size; i++){
                Cell<?> c = cells.get(i);
                out.add(new WeakReference<Element>(c == null ? null : c.get()));
            }
        }
    }
}
