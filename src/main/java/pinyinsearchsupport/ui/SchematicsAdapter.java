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

public final class SchematicsAdapter{
    private static volatile Field searchFieldField;
    private static volatile Field searchTextField;
    private static volatile Field rebuildPaneField;
    private static volatile Field selectedTagsField;
    private static volatile Field firstSchematicField;
    private static volatile boolean unavailable;

    private SchematicsAdapter(){}

    public static boolean isSchematicsSearch(TextField field){
        Target target = find(field);
        return target != null;
    }

    public static boolean filter(TextField field, String query, MatchEngine.MatchOptions opts){
        Target target = find(field);
        if(target == null || query == null || query.isEmpty()) return false;

        String previousSearch = null;
        Seq<Schematic> candidates;
        ResultScope scope;
        try{
            previousSearch = (String)searchTextField.get(target.dialog);
            searchTextField.set(target.dialog, "");
            target.rebuild.run();

            candidates = candidates(target);
            scope = locateResults(field, candidates.size);
            if(scope == null || !scope.isValid()){
                Log.warn("[PinyinSearchSupport] schematics results table not found");
                return false;
            }

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

    @SuppressWarnings("unchecked")
    private static Seq<Schematic> candidates(Target target) throws IllegalAccessException{
        Seq<Schematic> out = new Seq<Schematic>();
        Seq<String> selected = (Seq<String>)selectedTagsField.get(target.dialog);
        boolean hasTags = selected != null && selected.any();

        Seq<Schematic> all = Vars.schematics.all();
        for(int i = 0; i < all.size; i++){
            Schematic schematic = all.get(i);
            if(schematic == null) continue;
            if(hasTags && (schematic.labels == null || !schematic.labels.containsAll(selected))) continue;
            out.add(schematic);
        }
        return out;
    }

    private static void applyFilter(ResultScope scope, Seq<Schematic> candidates, String query,
                                    MatchEngine.MatchOptions opts, Target target) throws IllegalAccessException{
        Table table = scope.table;
        float scrollY = scope.pane.getScrollY();

        Seq<Cell> cells = table.getCells();
        int available = Math.min(candidates.size, cells.size);
        Element[] actors = new Element[available];
        CellSnapshot[] snapshots = new CellSnapshot[available];
        boolean[] endRows = new boolean[available];
        for(int i = 0; i < available; i++){
            Cell<?> cell = cells.get(i);
            actors[i] = cell.get();
            snapshots[i] = CellSnapshot.capture(cell);
            endRows[i] = cell.isEndRow();
        }

        int columns = detectColumns(endRows, available);
        table.clearChildren();

        int matches = 0;
        int displayed = 0;
        Schematic first = null;
        int col = 0;
        for(int i = 0; i < candidates.size; i++){
            Schematic schematic = candidates.get(i);
            if(schematic == null || !MatchEngine.accepts(schematic.name(), query, opts)) continue;
            if(first == null) first = schematic;
            matches++;

            if(i >= available || actors[i] == null) continue;

            Cell<?> cell = table.add(actors[i]);
            if(snapshots[i] != null) snapshots[i].applyTo(cell);
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
        }else if(displayed != matches){
            Log.warn("[PinyinSearchSupport] schematics card count mismatch: matched @, displayed @", matches, displayed);
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

    private static ResultScope locateResults(TextField field, int expectedCards){
        Element cursor = field.parent;
        ResultScope best = null;
        int bestScore = Integer.MIN_VALUE;
        for(int depth = 0; depth < 16 && cursor != null; depth++){
            if(cursor instanceof Group){
                Seq<ScrollPane> panes = new Seq<ScrollPane>();
                collectPanes(cursor, panes);
                for(int i = 0; i < panes.size; i++){
                    ScrollPane pane = panes.get(i);
                    if(!(pane.getWidget() instanceof Table)) continue;
                    if(pane.getHeight() > 0f && pane.getHeight() < 80f) continue;

                    Table table = (Table)pane.getWidget();
                    int score = countDescendants(table);
                    int cellCount = table.getCells().size;
                    if(expectedCards > 0){
                        score += Math.max(0, 500 - Math.abs(cellCount - expectedCards) * 20);
                    }else if(cellCount <= 1){
                        score += 500;
                    }
                    if(pane.getHeight() >= 80f) score += 1000;

                    if(score > bestScore){
                        bestScore = score;
                        best = new ResultScope(pane, table);
                    }
                }
            }
            cursor = cursor.parent;
        }
        return best;
    }

    private static void collectPanes(Element root, Seq<ScrollPane> out){
        if(root instanceof ScrollPane) out.add((ScrollPane)root);
        if(root instanceof Group){
            Seq<Element> children = ((Group)root).getChildren();
            for(int i = 0; i < children.size; i++){
                collectPanes(children.get(i), out);
            }
        }
    }

    private static int countDescendants(Element root){
        if(root == null) return 0;
        if(!(root instanceof Group)) return 1;
        int count = 1;
        Seq<Element> children = ((Group)root).getChildren();
        for(int i = 0; i < children.size; i++){
            count += countDescendants(children.get(i));
        }
        return count;
    }

    private static boolean ensure(){
        if(unavailable) return false;
        if(searchFieldField != null && searchTextField != null && rebuildPaneField != null
            && selectedTagsField != null && firstSchematicField != null) return true;
        synchronized(SchematicsAdapter.class){
            if(searchFieldField != null && searchTextField != null && rebuildPaneField != null
                && selectedTagsField != null && firstSchematicField != null) return true;
            try{
                Class<?> type = Vars.ui.schematics.getClass();
                searchFieldField = field(type, "searchField");
                searchTextField = field(type, "search");
                rebuildPaneField = field(type, "rebuildPane");
                selectedTagsField = field(type, "selectedTags");
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

    private static final class ResultScope{
        final ScrollPane pane;
        final Table table;

        ResultScope(ScrollPane pane, Table table){
            this.pane = pane;
            this.table = table;
        }

        boolean isValid(){
            return pane != null && pane.getScene() != null
                && table != null && table.getScene() != null;
        }
    }
}
