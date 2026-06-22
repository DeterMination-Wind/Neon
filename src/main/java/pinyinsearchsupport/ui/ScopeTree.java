package pinyinsearchsupport.ui;

import arc.scene.Element;
import arc.scene.Group;
import arc.scene.ui.Button;
import arc.scene.ui.Image;
import arc.scene.ui.Label;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.TextField;
import arc.scene.ui.layout.Cell;
import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import arc.util.Log;
import pinyinsearchsupport.match.MatchEngine;

public final class ScopeTree{
    public enum LayoutMode{ LIST, GRID, SECTIONED }

    private final Seq<SubScope> scopes;

    private ScopeTree(Seq<SubScope> scopes){
        this.scopes = scopes;
    }

    public boolean isValid(){
        if(scopes == null || scopes.isEmpty()) return false;
        for(int i = 0; i < scopes.size; i++){
            if(!scopes.get(i).isValid()) return false;
        }
        return true;
    }

    public static ScopeTree locate(TextField field){
        if(field == null || field.getScene() == null) return null;

        // Walk up to find a dialog root that contains at least one ScrollPane with a Table widget
        Group dialogRoot = null;
        Group cursor = field.parent;
        for(int depth = 0; depth < 12 && cursor != null; depth++){
            if(hasQualifyingScrollPane(cursor)){
                dialogRoot = cursor;
                break;
            }
            cursor = cursor.parent;
        }
        if(dialogRoot == null) return null;

        // BFS collect all ScrollPanes under dialogRoot
        Seq<ScrollPane> allPanes = new Seq<>();
        collectScrollPanes(dialogRoot, allPanes);

        // Filter: widget must be Table with >= 2 cells; skip horizontal chip rows
        Seq<ScrollPane> candidates = new Seq<>();
        for(int i = 0; i < allPanes.size; i++){
            ScrollPane sp = allPanes.get(i);
            Element w = sp.getWidget();
            if(!(w instanceof Table)) continue;
            Table t = (Table)w;
            if(t.getCells().size < 2) continue;
            // Skip horizontal chip/filter rows without excluding real result
            // panes that happen to disable Y scrolling before layout.
            if(sp.getHeight() > 0f && sp.getHeight() < 80f) continue;
            candidates.add(sp);
        }

        if(candidates.isEmpty()) return null;

        // Sort by descendant count descending, take top 3
        candidates.sort((a, b) -> countDescendants(b.getWidget()) - countDescendants(a.getWidget()));
        int take = Math.min(3, candidates.size);

        Seq<SubScope> scopes = new Seq<>();
        for(int i = 0; i < take; i++){
            ScrollPane sp = candidates.get(i);
            Table t = (Table)sp.getWidget();
            LayoutMode mode = detectMode(t);
            scopes.add(new SubScope(sp, t, mode));
        }

        return new ScopeTree(scopes);
    }

    public void postFilter(String query, MatchEngine.MatchOptions opts){
        if(query == null || query.isEmpty()) return;
        SearchTextExtractor.invalidate();
        for(int i = 0; i < scopes.size; i++){
            scopes.get(i).filter(query, opts);
        }
    }

    // ---- helpers ----

    private static boolean hasQualifyingScrollPane(Group root){
        Seq<ScrollPane> panes = new Seq<>();
        collectScrollPanes(root, panes);
        for(int i = 0; i < panes.size; i++){
            ScrollPane sp = panes.get(i);
            Element w = sp.getWidget();
            if(w instanceof Table && ((Table)w).getCells().size >= 4) return true;
        }
        return false;
    }

    private static void collectScrollPanes(Element root, Seq<ScrollPane> out){
        if(root instanceof ScrollPane) out.add((ScrollPane)root);
        if(root instanceof Group){
            Seq<Element> ch = ((Group)root).getChildren();
            for(int i = 0; i < ch.size; i++) collectScrollPanes(ch.get(i), out);
        }
    }

    private static int countDescendants(Element root){
        if(root == null) return 0;
        if(!(root instanceof Group)) return 1;
        int count = 1;
        Seq<Element> ch = ((Group)root).getChildren();
        for(int i = 0; i < ch.size; i++) count += countDescendants(ch.get(i));
        return count;
    }

    private static LayoutMode detectMode(Table t){
        Seq<Cell> cells = t.getCells();
        if(cells.isEmpty()) return LayoutMode.LIST;

        int actorCount = 0;
        int buttonCount = 0;
        int tableCount = 0;
        boolean hasMultiCellRow = false;
        for(int i = 0; i < cells.size; i++){
            Element actor = cells.get(i).get();
            if(actor != null){
                actorCount++;
                if(actor instanceof Button) buttonCount++;
                if(actor instanceof Table) tableCount++;
            }
            if(i < cells.size - 1 && !cells.get(i).isEndRow()) hasMultiCellRow = true;
        }

        // Buttons are Tables in Arc. Treat button-heavy/card layouts as grids
        // before checking for section tables, or map/schematic cards get replayed
        // with their original row breaks and leave gaps after filtering.
        if(buttonCount > 0 && buttonCount * 2 >= Math.max(1, actorCount)) return LayoutMode.GRID;
        if(hasMultiCellRow) return LayoutMode.GRID;
        if(tableCount > 0 && !hasMultiCellRow) return LayoutMode.SECTIONED;
        if(tableCount > actorCount / 2) return LayoutMode.SECTIONED;
        return LayoutMode.LIST;
    }

    // ---- SubScope ----

    private static final class SubScope{
        final ScrollPane pane;
        final Table table;
        final LayoutMode mode;

        SubScope(ScrollPane pane, Table table, LayoutMode mode){
            this.pane = pane;
            this.table = table;
            this.mode = mode;
        }

        boolean isValid(){
            return pane != null && pane.getScene() != null
                && table != null && table.getScene() != null;
        }

        void filter(String query, MatchEngine.MatchOptions opts){
            if(!isValid()) return;

            float scrollY = pane.getScrollY();

            int matches = filterTable(table, mode, query, opts);
            if(matches == 0){
                table.add("@none.found").padLeft(54f).padTop(10f);
            }

            table.invalidateHierarchy();
            try{
                pane.layout();
            }catch(Throwable t){
                Log.warn("[PinyinSearchSupport] pane.layout() threw: @", t.getMessage());
            }
            float maxY = pane.getMaxY();
            pane.setScrollYForce(Math.max(0f, Math.min(scrollY, maxY)));
            pane.updateVisualScroll();
        }

        private int filterTable(Table target, LayoutMode layout, String query, MatchEngine.MatchOptions opts){
            Seq<Cell> cells = target.getCells();
            if(cells.isEmpty()) return 0;

            int n = cells.size;
            Element[] actors = new Element[n];
            CellSnapshot[] snaps = new CellSnapshot[n];
            boolean[] endRows = new boolean[n];
            for(int i = 0; i < n; i++){
                Cell<?> c = cells.get(i);
                actors[i] = c.get();
                snaps[i] = CellSnapshot.capture(c);
                endRows[i] = c.isEndRow();
            }

            target.clearChildren();

            if(layout == LayoutMode.SECTIONED){
                return filterSectioned(target, actors, snaps, endRows, n, query, opts);
            }else if(layout == LayoutMode.GRID){
                return filterGrid(target, actors, snaps, endRows, n, query, opts);
            }else{
                return filterList(target, actors, snaps, endRows, n, query, opts);
            }
        }

        private int filterList(Table target, Element[] actors, CellSnapshot[] snaps, boolean[] endRows, int n,
                               String query, MatchEngine.MatchOptions opts){
            int matches = 0;
            for(int i = 0; i < n; i++){
                Element actor = actors[i];
                if(actor == null) continue;
                if(matchesActor(actor, query, opts)){
                    addOriginal(target, actors, snaps, endRows, i);
                    matches++;
                }
            }
            return matches;
        }

        private int filterGrid(Table target, Element[] actors, CellSnapshot[] snaps, boolean[] endRows, int n,
                               String query, MatchEngine.MatchOptions opts){
            int cols = detectColumns(endRows, n);

            int matches = 0;
            int col = 0;
            for(int i = 0; i < n; i++){
                Element actor = actors[i];
                if(actor == null) continue;
                if(matchesActor(actor, query, opts)){
                    Cell<?> cell = target.add(actor);
                    if(snaps[i] != null) snaps[i].applyTo(cell);
                    cell.colspan(1);
                    col++;
                    if(col % cols == 0){
                        target.row();
                        col = 0;
                    }
                    matches++;
                }
            }
            if(col > 0) target.row();
            return matches;
        }

        private int filterSectioned(Table target, Element[] actors, CellSnapshot[] snaps, boolean[] endRows, int n,
                                    String query, MatchEngine.MatchOptions opts){
            int matches = 0;
            int[] pendingHeaders = new int[n];
            int pendingCount = 0;

            for(int i = 0; i < n; i++){
                Element actor = actors[i];
                if(actor == null) continue;

                if(actor instanceof Table && !(actor instanceof Button)){
                    Table section = (Table)actor;

                    if(isHeaderTable(section)){
                        pendingHeaders[pendingCount++] = i;
                        continue;
                    }

                    if(isControlTable(section) && pendingCount == 0 && matches == 0){
                        addOriginal(target, actors, snaps, endRows, i);
                        continue;
                    }

                    int childMatches = filterTable(section, detectMode(section), query, opts);
                    if(childMatches > 0){
                        for(int p = 0; p < pendingCount; p++){
                            addOriginal(target, actors, snaps, endRows, pendingHeaders[p]);
                        }
                        pendingCount = 0;
                        addOriginal(target, actors, snaps, endRows, i);
                        matches += childMatches;
                    }else{
                        pendingCount = 0;
                    }
                }else if(actor instanceof Button && matchesActor(actor, query, opts)){
                    for(int p = 0; p < pendingCount; p++){
                        addOriginal(target, actors, snaps, endRows, pendingHeaders[p]);
                    }
                    pendingCount = 0;
                    addOriginal(target, actors, snaps, endRows, i);
                    matches++;
                }else{
                    pendingHeaders[pendingCount++] = i;
                }
            }
            return matches;
        }

        private Cell<?> addOriginal(Table target, Element[] actors, CellSnapshot[] snaps, boolean[] endRows, int index){
            Cell<?> cell = target.add(actors[index]);
            if(snaps[index] != null) snaps[index].applyTo(cell);
            if(endRows[index]) target.row();
            return cell;
        }

        private boolean matchesActor(Element actor, String query, MatchEngine.MatchOptions opts){
            String text = SearchTextExtractor.extract(actor);
            return text != null && MatchEngine.accepts(text, query, opts);
        }

        private int detectColumns(boolean[] endRows, int n){
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

        private boolean isControlTable(Table section){
            Seq<Cell> cells = section.getCells();
            if(cells.isEmpty()) return false;
            int actors = 0;
            int buttons = 0;
            for(int i = 0; i < cells.size; i++){
                Element actor = cells.get(i).get();
                if(actor == null) continue;
                actors++;
                if(actor instanceof Button) buttons++;
            }
            return buttons > 0 && buttons * 2 >= Math.max(1, actors);
        }

        private boolean isHeaderTable(Table section){
            Seq<Cell> cells = section.getCells();
            if(cells.isEmpty() || cells.size > 4) return false;

            boolean hasLabel = false;
            for(int i = 0; i < cells.size; i++){
                Element actor = cells.get(i).get();
                if(actor == null) continue;
                if(actor instanceof Label){
                    hasLabel = true;
                }else if(actor instanceof Image){
                    // separator line
                }else{
                    return false;
                }
            }
            return hasLabel;
        }
    }
}
