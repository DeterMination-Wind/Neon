package pinyinsearchsupport.ui;

import arc.math.Mathf;
import arc.scene.Element;
import arc.scene.Group;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.TextField;
import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import arc.util.Log;
import arc.util.Structs;
import arc.util.Time;
import mindustry.Vars;
import mindustry.gen.Icon;
import mindustry.graphics.Pal;
import mindustry.type.Sector;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.PlanetDialog;
import pinyinsearchsupport.match.MatchEngine;

import static mindustry.Vars.iconSmall;

public final class SectorListAdapter{
    private static final int MAX_DEPTH = 32;
    private static final int MAX_NODES = 1024;
    private static final int MAX_CONSECUTIVE_FAILURES = 5;

    private static volatile int consecutiveFailures;
    private static volatile boolean circuitBroken;

    private SectorListAdapter(){}

    public static boolean isSectorSearch(TextField field){
        if(circuitBroken) return false;
        try{
            return find(field) != null;
        }catch(Throwable t){
            recordFailure("isSectorSearch", t);
            return false;
        }
    }

    public static void filter(TextField field, String query, MatchEngine.MatchOptions opts){
        tryFilter(field, query, opts);
    }

    static boolean tryFilter(TextField field, String query, MatchEngine.MatchOptions opts){
        if(circuitBroken) return false;
        try{
            Target target = find(field);
            if(target == null || query == null || query.isEmpty()) return false;
            if(target.dialog.state == null || target.dialog.state.planet == null || target.dialog.state.planet.sectors == null){
                return false;
            }

            float scrollY = target.pane.getScrollY();
            if(!Float.isFinite(scrollY) || scrollY < 0f) scrollY = 0f;

            Seq<Sector> all = target.dialog.state.planet.sectors.select(s -> s != null && s.hasBase());
            all.sort(Structs.comps(
                Structs.comparingBool(s -> !s.isAttacked()),
                (a, b) -> Long.compare(timePlayed(b), timePlayed(a))
            ));

            Seq<Sector> matched = new Seq<Sector>();
            for(int i = 0; i < all.size; i++){
                Sector sector = all.get(i);
                if(sector != null && sector.hasBase() && matches(sector, query, opts)){
                    matched.add(sector);
                }
            }

            target.results.clearChildren();
            for(int i = 0; i < matched.size; i++){
                addSector(target.dialog, target.results, matched.get(i));
            }

            if(matched.isEmpty()){
                target.results.add("@none.found").pad(10f);
            }

            target.results.invalidateHierarchy();
            try{
                target.pane.layout();
            }catch(Throwable ignored){}

            float maxY = target.pane.getMaxY();
            float clampedY = Float.isFinite(maxY) && maxY > 0f ? Math.min(scrollY, maxY) : 0f;
            target.pane.setScrollYForce(Math.max(0f, clampedY));
            target.pane.updateVisualScroll();

            consecutiveFailures = 0;
            return true;
        }catch(Throwable t){
            recordFailure("filter", t);
            return false;
        }
    }

    private static long timePlayed(Sector sector){
        if(sector == null || sector.save == null || sector.save.meta == null) return 0L;
        return sector.save.meta.timePlayed;
    }

    private static boolean matches(Sector sector, String query, MatchEngine.MatchOptions opts){
        String name = sector.name();
        if(name != null && MatchEngine.accepts(name, query, opts)) return true;
        return sector.preset != null && sector.preset.localizedName != null
            && MatchEngine.accepts(sector.preset.localizedName, query, opts);
    }

    private static void addSector(PlanetDialog dialog, Table results, Sector sector){
        results.button(t -> {
            t.marginRight(10f);
            t.left();
            t.defaults().growX();

            t.table(head -> {
                head.left().defaults();

                if(sector.isAttacked()){
                    head.image(Icon.warningSmall).update(i -> {
                        i.color.set(Pal.accent).lerp(Pal.remove, Mathf.absin(Time.globalTime, 9f, 1f));
                    }).padRight(4f);
                }else if(sector.preset != null && sector.preset.requireUnlock && sector.preset.uiIcon != null){
                    head.image(sector.preset.uiIcon).size(iconSmall).padRight(4f);
                }

                String icon = sector.iconChar() == null ? "" : sector.iconChar() + " ";
                String displayName = sector.name() == null ? "" : sector.name();
                head.add(icon + displayName).growX().wrap();
            }).growX().row();
        }, Styles.underlineb, () -> {
            try{
                dialog.lookAt(sector);
                dialog.selectSector(sector);
            }catch(Throwable t){
                Log.warn("[PinyinSearchSupport] sector select callback failed: @", t.getMessage());
            }
        }).margin(8f).marginLeft(13f).marginBottom(6f).marginTop(6f).padBottom(3f).padTop(3f).growX().checked(b -> dialog.selected == sector).row();
    }

    private static Target find(TextField field){
        if(field == null || field.getScene() == null || Vars.ui == null || Vars.ui.planet == null) return null;

        PlanetDialog dialog = Vars.ui.planet;
        if(dialog.notifs == null || !contains(dialog.notifs, field, 0, new int[]{0})) return null;

        ScrollPane pane = ancestorPane(field);
        if(pane == null || !(pane.getWidget() instanceof Table)) return null;

        Table root = (Table)pane.getWidget();
        Table results = null;
        Seq<Element> children = root.getChildren();
        if(children == null) return null;
        for(int i = 0; i < children.size; i++){
            Element child = children.get(i);
            if(child instanceof Table && !contains(child, field, 0, new int[]{0})){
                results = (Table)child;
                break;
            }
        }

        return results == null ? null : new Target(dialog, pane, results);
    }

    private static ScrollPane ancestorPane(Element element){
        Element cursor = element;
        int depth = 0;
        while(cursor != null && depth++ < MAX_DEPTH){
            if(cursor instanceof ScrollPane) return (ScrollPane)cursor;
            cursor = cursor.parent;
        }
        return null;
    }

    private static boolean contains(Element root, Element child, int depth, int[] visited){
        if(root == null || child == null || depth > MAX_DEPTH || visited[0]++ >= MAX_NODES) return false;
        if(root == child) return true;
        if(root instanceof Group){
            Seq<Element> children = ((Group)root).getChildren();
            if(children == null) return false;
            for(int i = 0; i < children.size; i++){
                if(contains(children.get(i), child, depth + 1, visited)) return true;
            }
        }
        return false;
    }

    private static void recordFailure(String op, Throwable t){
        int count = ++consecutiveFailures;
        if(count <= 3){
            Log.warn("[PinyinSearchSupport] SectorListAdapter." + op + " failed: @", t.getMessage());
        }
        if(count >= MAX_CONSECUTIVE_FAILURES){
            circuitBroken = true;
            Log.warn("[PinyinSearchSupport] SectorListAdapter circuit breaker tripped; falling back to vanilla sector search.");
        }
    }

    private static final class Target{
        final PlanetDialog dialog;
        final ScrollPane pane;
        final Table results;

        Target(PlanetDialog dialog, ScrollPane pane, Table results){
            this.dialog = dialog;
            this.pane = pane;
            this.results = results;
        }
    }
}
