package pinyinsearchsupport.ui;

import arc.scene.Element;
import arc.scene.Group;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.TextField;
import arc.scene.ui.layout.Cell;
import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import arc.util.Log;
import mindustry.Vars;
import mindustry.game.Schematic;
import pinyinsearchsupport.match.MatchEngine;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.WeakHashMap;

public final class SchematicsAdapter{
    private static final int MAX_CARD_DEPTH = 12;
    private static final int MAX_CARD_NODES = 64;
    private static final int MAX_CONSECUTIVE_FAILURES = 5;

    private static volatile Field searchFieldField;
    private static volatile Field searchTextField;
    private static volatile Field rebuildPaneField;
    private static volatile Field firstSchematicField;
    private static volatile boolean unavailable;
    private static volatile int consecutiveFailures;

    private static final Map<Class<?>, Field> schematicFieldCache = new WeakHashMap<Class<?>, Field>();

    private SchematicsAdapter(){}

    public static boolean isSchematicsSearch(TextField field){
        if(unavailable) return false;
        try{
            return find(field) != null;
        }catch(Throwable t){
            recordRuntimeFailure("isSchematicsSearch", t);
            return false;
        }
    }

    static boolean filter(TextField field, String query, MatchEngine.MatchOptions opts, ScopeTree.Context context){
        if(unavailable) return false;
        Target target = find(field);
        if(target == null || query == null || query.isEmpty() || context == null || !context.isActive(field)) return false;

        String previousSearch = null;
        try{
            previousSearch = (String)searchTextField.get(target.dialog);
            searchTextField.set(target.dialog, "");
            target.rebuild.run();

            ScopeTree located = ScopeTree.locate(field, context);
            ResultScope scope = located == null ? null : new ResultScope(located);
            if(scope == null || !scope.isValid()){
                Log.warn("[PinyinSearchSupport] schematics results table not found");
                return false;
            }

            Seq<Card> candidates = visibleCards(scope.table);
            applyFilter(scope, candidates, query, opts, target);
            consecutiveFailures = 0;
            return true;
        }catch(Throwable t){
            recordRuntimeFailure("filter", t);
            return false;
        }finally{
            try{
                searchTextField.set(target.dialog, previousSearch == null ? "" : previousSearch);
            }catch(Throwable ignored){}
        }
    }

    private static Target find(TextField field){
        if(field == null || field.getScene() == null || Vars.ui == null || Vars.ui.schematics == null || Vars.schematics == null) return null;
        if(!ensure()) return null;
        try{
            Object dialog = Vars.ui.schematics;
            Object actual = searchFieldField.get(dialog);
            if(actual != field) return null;

            Object rebuild = rebuildPaneField.get(dialog);
            if(!(rebuild instanceof Runnable)) return null;
            return new Target(dialog, (Runnable)rebuild);
        }catch(Throwable t){
            recordRuntimeFailure("find", t);
            return null;
        }
    }

    static Seq<Schematic> visibleCandidates(Table table){
        Seq<Card> cards = visibleCards(table);
        Seq<Schematic> out = new Seq<Schematic>(cards.size);
        for(int i = 0; i < cards.size; i++){
            out.add(cards.get(i).schematic);
        }
        return out;
    }

    private static Seq<Card> visibleCards(Table table){
        Seq<Card> out = new Seq<Card>();
        if(table == null) return out;

        Seq<Cell> cells = table.getCells();
        if(cells == null) return out;
        for(int i = 0; i < cells.size; i++){
            Cell<?> cell = cells.get(i);
            if(cell == null) continue;
            Element actor = cell.get();
            Schematic schematic = schematicOf(actor, 0, new int[]{0});
            if(schematic != null){
                out.add(new Card(schematic, actor, CellSnapshot.capture(cell), cell.isEndRow()));
            }
        }
        return out;
    }

    private static Schematic schematicOf(Element root, int depth, int[] visited){
        if(root == null || depth > MAX_CARD_DEPTH || visited[0]++ >= MAX_CARD_NODES) return null;

        Class<?> cls = root.getClass();
        if("SchematicImage".equals(cls.getSimpleName())){
            Field f = resolveSchematicField(cls);
            if(f != null){
                try{
                    Object value = f.get(root);
                    if(value instanceof Schematic) return (Schematic)value;
                }catch(Throwable ignored){}
            }
        }

        if(root instanceof Group){
            Seq<Element> children = ((Group)root).getChildren();
            if(children == null) return null;
            for(int i = 0; i < children.size; i++){
                Schematic schematic = schematicOf(children.get(i), depth + 1, visited);
                if(schematic != null) return schematic;
            }
        }
        return null;
    }

    private static Field resolveSchematicField(Class<?> cls){
        synchronized(schematicFieldCache){
            if(schematicFieldCache.containsKey(cls)){
                return schematicFieldCache.get(cls);
            }
        }
        Field found = findFieldOptional(cls, "schematic");
        synchronized(schematicFieldCache){
            schematicFieldCache.put(cls, found);
        }
        return found;
    }

    private static void applyFilter(ResultScope scope, Seq<Card> candidates, String query,
                                    MatchEngine.MatchOptions opts, Target target){
        Table table = scope.table;
        float scrollY = scope.pane.getScrollY();
        if(!Float.isFinite(scrollY) || scrollY < 0f) scrollY = 0f;

        int available = candidates.size;
        boolean[] endRows = new boolean[available];
        Seq<Card> matched = new Seq<Card>();
        for(int i = 0; i < available; i++){
            Card card = candidates.get(i);
            endRows[i] = card.endRow;
            Schematic schematic = card.schematic;
            if(schematic != null && MatchEngine.accepts(schematic.name(), query, opts)){
                matched.add(card);
            }
        }

        int columns = detectColumns(endRows, available);
        table.clearChildren();

        Schematic first = null;
        try{
            int col = 0;
            for(int i = 0; i < matched.size; i++){
                Card card = matched.get(i);
                if(first == null) first = card.schematic;

                Cell<?> cell = table.add(card.actor);
                if(card.snapshot != null) card.snapshot.applyTo(cell);
                cell.colspan(1);
                col++;
                if(col % columns == 0){
                    table.row();
                    col = 0;
                }
            }
            if(col > 0) table.row();

            if(matched.isEmpty()){
                table.add("@none.found").padLeft(54f).padTop(10f);
            }
        }catch(Throwable t){
            // Restore original candidate cards so the dialog is never left blank on error.
            restoreCards(table, candidates);
            throw t;
        }

        if(firstSchematicField != null){
            try{
                firstSchematicField.set(target.dialog, first);
            }catch(Throwable ignored){}
        }

        table.invalidateHierarchy();
        try{
            scope.pane.layout();
        }catch(Throwable t){
            Log.warn("[PinyinSearchSupport] schematics pane.layout() threw: @", t.getMessage());
        }
        float maxY = scope.pane.getMaxY();
        float clampedY = Float.isFinite(maxY) && maxY > 0f ? Math.min(scrollY, maxY) : 0f;
        scope.pane.setScrollYForce(Math.max(0f, clampedY));
        scope.pane.updateVisualScroll();
    }

    private static void restoreCards(Table table, Seq<Card> candidates){
        try{
            table.clearChildren();
            for(int i = 0; i < candidates.size; i++){
                Card card = candidates.get(i);
                if(card == null || card.actor == null) continue;
                Cell<?> cell = table.add(card.actor);
                if(card.snapshot != null) card.snapshot.applyTo(cell);
                if(card.endRow) table.row();
            }
            table.invalidateHierarchy();
        }catch(Throwable ignored){}
    }

    private static int detectColumns(boolean[] endRows, int n){
        int cols = 1;
        int current = 0;
        for(int i = 0; i < n; i++){
            current++;
            if(endRows[i]){
                if(current > cols) cols = current;
                current = 0;
            }
        }
        if(current > cols) cols = current;
        return Math.max(1, cols);
    }

    private static void recordRuntimeFailure(String op, Throwable t){
        int count = ++consecutiveFailures;
        if(count <= 3){
            Log.warn("[PinyinSearchSupport] SchematicsAdapter." + op + " failed: @", t.getMessage());
        }
        if(count >= MAX_CONSECUTIVE_FAILURES){
            unavailable = true;
            Log.warn("[PinyinSearchSupport] SchematicsAdapter circuit breaker tripped; falling back to vanilla schematics search.");
        }
    }

    private static boolean ensure(){
        if(unavailable) return false;
        if(searchFieldField != null && searchTextField != null && rebuildPaneField != null) return true;
        synchronized(SchematicsAdapter.class){
            if(unavailable) return false;
            if(searchFieldField != null && searchTextField != null && rebuildPaneField != null) return true;
            try{
                Class<?> type = Vars.ui.schematics.getClass();
                searchFieldField = findFieldRequired(type, "searchField");
                searchTextField = findFieldRequired(type, "search");
                rebuildPaneField = findFieldRequired(type, "rebuildPane");
                firstSchematicField = findFieldOptional(type, "firstSchematic");
                return true;
            }catch(Throwable t){
                Log.warn("[PinyinSearchSupport] cannot access SchematicsDialog internals: @", t.getMessage());
                unavailable = true;
                return false;
            }
        }
    }

    private static Field findFieldRequired(Class<?> type, String name) throws NoSuchFieldException{
        Field f = findFieldOptional(type, name);
        if(f == null) throw new NoSuchFieldException(type.getName() + "." + name);
        return f;
    }

    private static Field findFieldOptional(Class<?> type, String name){
        Class<?> current = type;
        while(current != null && current != Object.class){
            try{
                Field f = current.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            }catch(NoSuchFieldException ignored){
                current = current.getSuperclass();
            }catch(Throwable t){
                return null;
            }
        }
        return null;
    }

    private static final class Target{
        final Object dialog;
        final Runnable rebuild;

        Target(Object dialog, Runnable rebuild){
            this.dialog = dialog;
            this.rebuild = rebuild;
        }
    }

    private static final class Card{
        final Schematic schematic;
        final Element actor;
        final CellSnapshot snapshot;
        final boolean endRow;

        Card(Schematic schematic, Element actor, CellSnapshot snapshot, boolean endRow){
            this.schematic = schematic;
            this.actor = actor;
            this.snapshot = snapshot;
            this.endRow = endRow;
        }
    }

    private static final class ResultScope{
        final ScopeTree owner;
        final ScrollPane pane;
        final Table table;

        ResultScope(ScopeTree owner){
            this.owner = owner;
            this.pane = owner.primaryPane();
            this.table = owner.primaryTable();
        }

        boolean isValid(){
            return owner != null && owner.isValid()
                && pane != null && pane.getScene() != null
                && table != null && table.getScene() != null;
        }
    }
}
