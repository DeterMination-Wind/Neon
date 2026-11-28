package pinyinsearchsupport.ui;

import arc.Core;
import arc.scene.Element;
import arc.scene.Group;
import arc.scene.Scene;
import arc.scene.ui.Button;
import arc.scene.ui.Dialog;
import arc.scene.ui.Image;
import arc.scene.ui.Label;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.TextField;
import arc.scene.ui.layout.Cell;
import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import arc.util.Log;
import mindustry.Vars;
import pinyinsearchsupport.match.MatchEngine;

public final class ScopeTree{
    public enum LayoutMode{ LIST, GRID, SECTIONED }

    private static final int MAX_TRAVERSAL_DEPTH = 32;
    private static final int MAX_TRAVERSAL_NODES = 2048;
    private static final int MAX_SECTION_DEPTH = 8;

    private final TextField field;
    private final Context context;
    private final Seq<SubScope> scopes;

    private ScopeTree(TextField field, Context context, Seq<SubScope> scopes){
        this.field = field;
        this.context = context;
        this.scopes = scopes;
    }

    public boolean isValid(){
        if(context == null || !context.isActive(field) || scopes == null || scopes.isEmpty()) return false;
        for(int i = 0; i < scopes.size; i++){
            if(!scopes.get(i).isValid()) return false;
        }
        return true;
    }

    static ScopeTree locate(TextField field, Context context){
        if(context == null || !context.isActive(field)) return null;

        Seq<ScrollPane> allPanes = new Seq<ScrollPane>();
        collectScrollPanes(context.root, context.root, 0, new int[]{0}, allPanes);

        Candidate best = null;
        boolean ambiguous = false;
        for(int i = 0; i < allPanes.size; i++){
            ScrollPane sp = allPanes.get(i);
            Candidate candidate = candidate(field, context, sp);
            if(candidate == null) continue;

            if(best == null || candidate.score.compareTo(best.score) < 0){
                best = candidate;
                ambiguous = false;
            }else if(candidate.score.compareTo(best.score) == 0){
                ambiguous = true;
            }
        }

        if(best == null || ambiguous) return null;

        Seq<SubScope> scopes = new Seq<SubScope>();
        scopes.add(new SubScope(field, context, best.pane, best.table, detectMode(best.table)));
        return new ScopeTree(field, context, scopes);
    }

    public void postFilter(String query, MatchEngine.MatchOptions opts){
        if(query == null || query.isEmpty() || scopes == null) return;
        for(int i = 0; i < scopes.size; i++){
            scopes.get(i).filter(query, opts);
        }
    }

    // ---- helpers ----

    static Context capture(TextField field){
        if(field == null || field.getScene() == null || field.parent == null) return null;

        Scene scene = field.getScene();
        Group originalParent = field.parent;
        Group boundary = globalBoundaryOf(field);
        Group cursor = originalParent;
        for(int depth = 0; depth < MAX_TRAVERSAL_DEPTH && cursor != null; depth++){
            if(cursor instanceof Dialog){
                return new Context(scene, cursor, (Dialog)cursor, originalParent, boundary);
            }
            if(cursor == scene.root || cursor == boundary) break;
            cursor = cursor.parent;
        }

        // Global UI groups are hard boundaries, not blanket exclusion zones.
        // A strict local descendant may own a search field and result pane,
        // but the boundary itself can never become the inferred scope.
        cursor = originalParent;
        for(int depth = 0; depth < MAX_TRAVERSAL_DEPTH && cursor != null && cursor != scene.root && cursor != boundary; depth++){
            Context context = new Context(scene, cursor, null, originalParent, boundary);
            if(hasQualifyingScrollPane(field, context)){
                return context;
            }
            cursor = cursor.parent;
        }
        return null;
    }

    ScrollPane primaryPane(){
        return scopes == null || scopes.isEmpty() ? null : scopes.first().pane;
    }

    Table primaryTable(){
        return scopes == null || scopes.isEmpty() ? null : scopes.first().table;
    }

    private static boolean hasQualifyingScrollPane(TextField field, Context context){
        Seq<ScrollPane> panes = new Seq<ScrollPane>();
        collectScrollPanes(context.root, context.root, 0, new int[]{0}, panes);
        for(int i = 0; i < panes.size; i++){
            if(candidate(field, context, panes.get(i)) != null) return true;
        }
        return false;
    }

    private static void collectScrollPanes(Element root, Element current, int depth, int[] visited, Seq<ScrollPane> out){
        if(current == null || depth > MAX_TRAVERSAL_DEPTH || visited[0]++ >= MAX_TRAVERSAL_NODES) return;
        if(current != root){
            if(current instanceof Dialog) return;
            if(current instanceof Group && isGlobalUiGroup((Group)current)) return;
        }
        if(current instanceof ScrollPane) out.add((ScrollPane)current);
        if(current instanceof Group){
            Seq<Element> ch = ((Group)current).getChildren();
            if(ch == null) return;
            for(int i = 0; i < ch.size; i++){
                if(visited[0] >= MAX_TRAVERSAL_NODES) break;
                collectScrollPanes(root, ch.get(i), depth + 1, visited, out);
            }
        }
    }

    private static Candidate candidate(TextField field, Context context, ScrollPane pane){
        if(field == null || context == null || pane == null || pane.getScene() != context.scene) return null;
        if(!context.owns(field) || !context.owns(pane)) return null;
        Element widget = pane.getWidget();
        if(!(widget instanceof Table)) return null;

        Table table = (Table)widget;
        Seq<Cell> cells = table.getCells();
        if(table.getScene() != context.scene || cells == null || cells.isEmpty()) return null;
        if(pane.getHeight() > 0f && pane.getHeight() < 80f) return null;
        if(field.isDescendantOf(pane) || field.isDescendantOf(table)) return null;
        if(!context.owns(table)) return null;
        if(!hasActor(table)) return null;

        StructuralScore score = StructuralScore.between(field, pane, context.root);
        return score == null ? null : new Candidate(pane, table, score);
    }

    private static boolean hasActor(Table table){
        Seq<Cell> cells = table.getCells();
        if(cells == null) return false;
        for(int i = 0; i < cells.size; i++){
            Cell<?> cell = cells.get(i);
            if(cell != null && cell.get() != null) return true;
        }
        return false;
    }

    private static boolean isGlobalUiGroup(Group group){
        return Vars.ui != null && (group == Vars.ui.menuGroup || group == Vars.ui.hudGroup);
    }

    private static Group globalBoundaryOf(Element element){
        Element cursor = element;
        int depth = 0;
        while(cursor != null && depth++ < MAX_TRAVERSAL_DEPTH * 2){
            if(cursor instanceof Group && isGlobalUiGroup((Group)cursor)) return (Group)cursor;
            cursor = cursor.parent;
        }
        return null;
    }

    private static boolean visibleThrough(Element element, Element root){
        Element cursor = element;
        int depth = 0;
        while(cursor != null && depth++ < MAX_TRAVERSAL_DEPTH * 2){
            if(!cursor.visible) return false;
            if(cursor == root) return true;
            cursor = cursor.parent;
        }
        return false;
    }

    private static LayoutMode detectMode(Table t){
        if(t == null) return LayoutMode.LIST;
        Seq<Cell> cells = t.getCells();
        if(cells == null || cells.isEmpty()) return LayoutMode.LIST;

        int actorCount = 0;
        int buttonCount = 0;
        int tableCount = 0;
        boolean hasMultiCellRow = false;
        for(int i = 0; i < cells.size; i++){
            Cell<?> cell = cells.get(i);
            if(cell == null) continue;
            Element actor = cell.get();
            if(actor != null){
                actorCount++;
                if(actor instanceof Button) buttonCount++;
                if(actor instanceof Table) tableCount++;
            }
            if(i < cells.size - 1 && !cell.isEndRow()) hasMultiCellRow = true;
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

    // ---- Context ----

    static final class Context{
        final Scene scene;
        final Group root;
        final Dialog dialog;
        final Group originalParent;
        final Group boundary;

        Context(Scene scene, Group root, Dialog dialog, Group originalParent, Group boundary){
            this.scene = scene;
            this.root = root;
            this.dialog = dialog;
            this.originalParent = originalParent;
            this.boundary = boundary;
        }

        boolean owns(Element element){
            if(element == null || scene == null || root == null) return false;
            if(element.getScene() != scene || root.getScene() != scene) return false;
            if(root == scene.root || isGlobalUiGroup(root) || !element.isDescendantOf(root)) return false;

            if(boundary != null){
                if(!isGlobalUiGroup(boundary) || boundary.getScene() != scene) return false;
                return root != boundary && root.isDescendantOf(boundary) && element.isDescendantOf(boundary);
            }

            return globalBoundaryOf(root) == null && globalBoundaryOf(element) == null;
        }

        boolean isActive(TextField field){
            if(field == null || scene == null || root == null || originalParent == null) return false;
            if(Core.scene != scene || field.parent != originalParent || !owns(field)) return false;
            if(!visibleThrough(field, root)) return false;

            Element keyboard = scene.getKeyboardFocus();
            Element scroll = scene.getScrollFocus();
            if(dialog != null){
                if(!dialog.isShown()) return false;
                return (keyboard != null && keyboard.isDescendantOf(dialog))
                    || (scroll != null && scroll.isDescendantOf(dialog));
            }

            return keyboard != null && keyboard.isDescendantOf(root);
        }
    }

    private static final class Candidate{
        final ScrollPane pane;
        final Table table;
        final StructuralScore score;

        Candidate(ScrollPane pane, Table table, StructuralScore score){
            this.pane = pane;
            this.table = table;
            this.score = score;
        }
    }

    private static final class StructuralScore implements Comparable<StructuralScore>{
        final int fieldDepth;
        final int totalDepth;
        final int directionPenalty;
        final int siblingGap;

        StructuralScore(int fieldDepth, int totalDepth, int directionPenalty, int siblingGap){
            this.fieldDepth = fieldDepth;
            this.totalDepth = totalDepth;
            this.directionPenalty = directionPenalty;
            this.siblingGap = siblingGap;
        }

        static StructuralScore between(Element field, Element pane, Group root){
            Element common = field;
            int fieldDepth = 0;
            while(common != null && !pane.isDescendantOf(common) && fieldDepth < MAX_TRAVERSAL_DEPTH * 2){
                common = common.parent;
                fieldDepth++;
            }
            if(common == null || !pane.isDescendantOf(common) || !common.isDescendantOf(root)) return null;

            int paneDepth = 0;
            Element cursor = pane;
            while(cursor != null && cursor != common && paneDepth < MAX_TRAVERSAL_DEPTH * 2){
                cursor = cursor.parent;
                paneDepth++;
            }
            if(cursor != common) return null;

            int directionPenalty = 0;
            int siblingGap = 0;
            if(common instanceof Group){
                Element fieldBranch = branchBelow(field, common);
                Element paneBranch = branchBelow(pane, common);
                if(fieldBranch != null && paneBranch != null && fieldBranch != paneBranch){
                    Seq<Element> children = ((Group)common).getChildren();
                    if(children != null){
                        int fieldIndex = children.indexOf(fieldBranch, true);
                        int paneIndex = children.indexOf(paneBranch, true);
                        if(fieldIndex >= 0 && paneIndex >= 0){
                            directionPenalty = paneIndex >= fieldIndex ? 0 : 1;
                            siblingGap = Math.abs(paneIndex - fieldIndex);
                        }
                    }
                }
            }

            return new StructuralScore(fieldDepth, fieldDepth + paneDepth, directionPenalty, siblingGap);
        }

        private static Element branchBelow(Element element, Element ancestor){
            Element cursor = element;
            Element branch = element;
            int depth = 0;
            while(cursor != null && cursor != ancestor && depth++ < MAX_TRAVERSAL_DEPTH * 2){
                branch = cursor;
                cursor = cursor.parent;
            }
            return cursor == ancestor ? branch : null;
        }

        @Override
        public int compareTo(StructuralScore other){
            int result = Integer.compare(fieldDepth, other.fieldDepth);
            if(result != 0) return result;
            result = Integer.compare(totalDepth, other.totalDepth);
            if(result != 0) return result;
            result = Integer.compare(directionPenalty, other.directionPenalty);
            if(result != 0) return result;
            return Integer.compare(siblingGap, other.siblingGap);
        }
    }

    private static final class SubScope{
        final TextField field;
        final Context context;
        final ScrollPane pane;
        final Table table;
        final LayoutMode mode;

        SubScope(TextField field, Context context, ScrollPane pane, Table table, LayoutMode mode){
            this.field = field;
            this.context = context;
            this.pane = pane;
            this.table = table;
            this.mode = mode;
        }

        boolean isValid(){
            return context != null && context.isActive(field)
                && pane != null && context.owns(pane)
                && table != null && context.owns(table)
                && !field.isDescendantOf(table);
        }

        void filter(String query, MatchEngine.MatchOptions opts){
            if(!isValid()) return;

            float scrollY = pane.getScrollY();
            if(!Float.isFinite(scrollY) || scrollY < 0f) scrollY = 0f;

            int matches = filterTable(table, mode, query, opts, 0);
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
            float clampedY = Float.isFinite(maxY) && maxY > 0f ? Math.min(scrollY, maxY) : 0f;
            pane.setScrollYForce(Math.max(0f, clampedY));
            pane.updateVisualScroll();
        }

        private int filterTable(Table target, LayoutMode layout, String query, MatchEngine.MatchOptions opts, int depth){
            if(target == null || target == context.root || field.isDescendantOf(target)) return 0;
            if(depth > MAX_SECTION_DEPTH) return 0;
            if(target == table && !context.owns(target)) return 0;

            Seq<Cell> cells = target.getCells();
            if(cells == null || cells.isEmpty()) return 0;

            int n = cells.size;
            Element[] actors = new Element[n];
            CellSnapshot[] snaps = new CellSnapshot[n];
            boolean[] endRows = new boolean[n];
            for(int i = 0; i < n; i++){
                Cell<?> c = cells.get(i);
                if(c == null) continue;
                actors[i] = c.get();
                snaps[i] = CellSnapshot.capture(c);
                endRows[i] = c.isEndRow();
            }

            if(layout == LayoutMode.SECTIONED){
                target.clearChildren();
                try{
                    return filterSectioned(target, actors, snaps, endRows, n, query, opts, depth);
                }catch(Throwable t){
                    restoreOriginal(target, actors, snaps, endRows, n);
                    throw t;
                }
            }else if(layout == LayoutMode.GRID){
                boolean[] matched = evaluateMatches(actors, n, query, opts);
                target.clearChildren();
                try{
                    return rebuildGrid(target, actors, snaps, endRows, matched, n);
                }catch(Throwable t){
                    restoreOriginal(target, actors, snaps, endRows, n);
                    throw t;
                }
            }else{
                boolean[] matched = evaluateMatches(actors, n, query, opts);
                target.clearChildren();
                try{
                    return rebuildList(target, actors, snaps, endRows, matched, n);
                }catch(Throwable t){
                    restoreOriginal(target, actors, snaps, endRows, n);
                    throw t;
                }
            }
        }

        private boolean[] evaluateMatches(Element[] actors, int n, String query, MatchEngine.MatchOptions opts){
            boolean[] matched = new boolean[n];
            for(int i = 0; i < n; i++){
                Element actor = actors[i];
                if(actor == null) continue;
                matched[i] = matchesActorSafe(actor, query, opts);
            }
            return matched;
        }

        private int rebuildList(Table target, Element[] actors, CellSnapshot[] snaps, boolean[] endRows,
                                boolean[] matched, int n){
            int matches = 0;
            for(int i = 0; i < n; i++){
                if(actors[i] == null || !matched[i]) continue;
                addOriginal(target, actors, snaps, endRows, i);
                matches++;
            }
            return matches;
        }

        private int rebuildGrid(Table target, Element[] actors, CellSnapshot[] snaps, boolean[] endRows,
                                boolean[] matched, int n){
            int cols = detectColumns(endRows, n);

            int matches = 0;
            int col = 0;
            for(int i = 0; i < n; i++){
                Element actor = actors[i];
                if(actor == null || !matched[i]) continue;
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
            if(col > 0) target.row();
            return matches;
        }

        private int filterSectioned(Table target, Element[] actors, CellSnapshot[] snaps, boolean[] endRows, int n,
                                    String query, MatchEngine.MatchOptions opts, int depth){
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

                    int childMatches = filterTable(section, detectMode(section), query, opts, depth + 1);
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
                }else if(actor instanceof Button && matchesActorSafe(actor, query, opts)){
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

        private void restoreOriginal(Table target, Element[] actors, CellSnapshot[] snaps, boolean[] endRows, int n){
            try{
                target.clearChildren();
                for(int i = 0; i < n; i++){
                    if(actors[i] == null) continue;
                    addOriginal(target, actors, snaps, endRows, i);
                }
                target.invalidateHierarchy();
            }catch(Throwable ignored){}
        }

        private Cell<?> addOriginal(Table target, Element[] actors, CellSnapshot[] snaps, boolean[] endRows, int index){
            Cell<?> cell = target.add(actors[index]);
            if(snaps[index] != null) snaps[index].applyTo(cell);
            if(endRows[index]) target.row();
            return cell;
        }

        private boolean matchesActorSafe(Element actor, String query, MatchEngine.MatchOptions opts){
            try{
                String text = SearchTextExtractor.extract(actor);
                return text != null && MatchEngine.accepts(text, query, opts);
            }catch(Throwable ignored){
                return false;
            }
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
            if(cells == null || cells.isEmpty()) return false;
            int actors = 0;
            int buttons = 0;
            for(int i = 0; i < cells.size; i++){
                Cell<?> c = cells.get(i);
                if(c == null) continue;
                Element actor = c.get();
                if(actor == null) continue;
                actors++;
                if(actor instanceof Button) buttons++;
            }
            return buttons > 0 && buttons * 2 >= Math.max(1, actors);
        }

        private boolean isHeaderTable(Table section){
            Seq<Cell> cells = section.getCells();
            if(cells == null || cells.isEmpty() || cells.size > 4) return false;

            boolean hasLabel = false;
            for(int i = 0; i < cells.size; i++){
                Cell<?> c = cells.get(i);
                if(c == null) continue;
                Element actor = c.get();
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
