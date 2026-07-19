package pinyinsearchsupport.ui;

import arc.scene.Element;
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

public final class SchematicsAdapter{
    private static volatile Field searchFieldField;
    private static volatile Field searchTextField;
    private static volatile Field rebuildPaneField;
    private static volatile Field firstSchematicField;
    private static volatile boolean unavailable;

    private SchematicsAdapter(){}

    public static boolean isSchematicsSearch(TextField field){
        Target target = find(field);
        return target != null;
    }

    public static boolean filter(TextField field, String query, MatchEngine.MatchOptions opts){
        return filter(field, query, opts, ScopeTree.capture(field));
    }

    static boolean filter(TextField field, String query, MatchEngine.MatchOptions opts, ScopeTree.Context context){
        Target target = find(field);
        if(target == null || query == null || query.isEmpty() || context == null || !context.isActive(field)) return false;

        String previousSearch = null;
        Seq<Card> candidates;
        ResultScope scope;
        try{
            previousSearch = (String)searchTextField.get(target.dialog);
            searchTextField.set(target.dialog, "");
            target.rebuild.run();

            ScopeTree located = ScopeTree.locate(field, context);
            scope = located == null ? null : new ResultScope(located);
            if(scope == null || !scope.isValid()){
                Log.warn("[PinyinSearchSupport] schematics results table not found");
                return false;
            }

            candidates = visibleCards(scope.table);
            applyFilter(scope, candidates, query, opts, target);
        }catch(Throwable t){
            Log.warn("[PinyinSearchSupport] schematics rebuild adapter failed: @", t.getMessage());
            return false;
        }finally{
            try{
                searchTextField.set(target.dialog, previousSearch == null ? "" : previousSearch);
            }catch(Throwable ignored){}
        }
        return true;
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
            Log.warn("[PinyinSearchSupport] schematics adapter lookup failed: @", t.getMessage());
            unavailable = true;
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
        for(int i = 0; i < cells.size; i++){
            Cell<?> cell = cells.get(i);
            Element actor = cell.get();
            Schematic schematic = schematicOf(actor);
            if(schematic != null){
                out.add(new Card(schematic, actor, CellSnapshot.capture(cell), cell.isEndRow()));
            }
        }
        return out;
    }

    private static Schematic schematicOf(Element root){
        if(root == null) return null;
        if("SchematicImage".equals(root.getClass().getSimpleName())){
            try{
                Field field = root.getClass().getDeclaredField("schematic");
                field.setAccessible(true);
                Object value = field.get(root);
                if(value instanceof Schematic) return (Schematic)value;
            }catch(Throwable ignored){}
        }
        if(root instanceof arc.scene.Group){
            Seq<Element> children = ((arc.scene.Group)root).getChildren();
            for(int i = 0; i < children.size; i++){
                Schematic schematic = schematicOf(children.get(i));
                if(schematic != null) return schematic;
            }
        }
        return null;
    }

    private static void applyFilter(ResultScope scope, Seq<Card> candidates, String query,
                                    MatchEngine.MatchOptions opts, Target target) throws IllegalAccessException{
        Table table = scope.table;
        float scrollY = scope.pane.getScrollY();

        int available = candidates.size;
        boolean[] endRows = new boolean[available];
        for(int i = 0; i < available; i++){
            endRows[i] = candidates.get(i).endRow;
        }

        int columns = detectColumns(endRows, available);
        table.clearChildren();

        int matches = 0;
        int displayed = 0;
        Schematic first = null;
        int col = 0;
        for(int i = 0; i < candidates.size; i++){
            Card card = candidates.get(i);
            Schematic schematic = card.schematic;
            if(schematic == null || !MatchEngine.accepts(schematic.name(), query, opts)) continue;
            if(first == null) first = schematic;
            matches++;

            Cell<?> cell = table.add(card.actor);
            if(card.snapshot != null) card.snapshot.applyTo(cell);
            cell.colspan(1);
            col++;
            displayed++;
            if(col % columns == 0){
                table.row();
                col = 0;
            }
        }
        if(col > 0) table.row();

        if(matches == 0){
            table.add("@none.found").padLeft(54f).padTop(10f);
        }

        firstSchematicField.set(target.dialog, first);
        table.invalidateHierarchy();
        try{
            scope.pane.layout();
        }catch(Throwable t){
            Log.warn("[PinyinSearchSupport] schematics pane.layout() threw: @", t.getMessage());
        }
        scope.pane.setScrollYForce(Math.max(0f, Math.min(scrollY, scope.pane.getMaxY())));
        scope.pane.updateVisualScroll();
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

    private static boolean ensure(){
        if(unavailable) return false;
        if(searchFieldField != null && searchTextField != null && rebuildPaneField != null
            && firstSchematicField != null) return true;
        synchronized(SchematicsAdapter.class){
            if(searchFieldField != null && searchTextField != null && rebuildPaneField != null
                && firstSchematicField != null) return true;
            try{
                Class<?> type = Vars.ui.schematics.getClass();
                searchFieldField = field(type, "searchField");
                searchTextField = field(type, "search");
                rebuildPaneField = field(type, "rebuildPane");
                firstSchematicField = field(type, "firstSchematic");
                return true;
            }catch(Throwable t){
                Log.warn("[PinyinSearchSupport] cannot access SchematicsDialog internals: @", t.getMessage());
                unavailable = true;
                return false;
            }
        }
    }

    private static Field field(Class<?> type, String name) throws NoSuchFieldException{
        Field f = type.getDeclaredField(name);
        f.setAccessible(true);
        return f;
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
