package pinyinsearchsupport.ui;

import arc.math.Mathf;
import arc.scene.Element;
import arc.scene.Group;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.TextField;
import arc.scene.ui.layout.Table;
import arc.struct.Seq;
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
    private SectorListAdapter(){}

    public static boolean isSectorSearch(TextField field){
        return find(field) != null;
    }

    public static void filter(TextField field, String query, MatchEngine.MatchOptions opts){
        Target target = find(field);
        if(target == null || query == null || query.isEmpty()) return;

        float scrollY = target.pane.getScrollY();
        target.results.clearChildren();

        Seq<Sector> all = target.dialog.state.planet.sectors.select(Sector::hasBase);
        all.sort(Structs.comps(
            Structs.comparingBool(s -> !s.isAttacked()),
            Structs.comparingInt(s -> s.save == null ? 0 : -(int)s.save.meta.timePlayed)
        ));

        int added = 0;
        for(Sector sector : all){
            if(!sector.hasBase() || !matches(sector, query, opts)) continue;
            addSector(target.dialog, target.results, sector);
            added++;
        }

        if(added == 0){
            target.results.add("@none.found").pad(10f);
        }

        target.results.invalidateHierarchy();
        target.pane.setScrollYForce(scrollY);
        target.pane.updateVisualScroll();
    }

    private static boolean matches(Sector sector, String query, MatchEngine.MatchOptions opts){
        if(MatchEngine.accepts(sector.name(), query, opts)) return true;
        return sector.preset != null && MatchEngine.accepts(sector.preset.localizedName, query, opts);
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
                }else if(sector.preset != null && sector.preset.requireUnlock){
                    head.image(sector.preset.uiIcon).size(iconSmall).padRight(4f);
                }

                String icon = sector.iconChar() == null ? "" : sector.iconChar() + " ";
                head.add(icon + sector.name()).growX().wrap();
            }).growX().row();
        }, Styles.underlineb, () -> {
            dialog.lookAt(sector);
            dialog.selectSector(sector);
        }).margin(8f).marginLeft(13f).marginBottom(6f).marginTop(6f).padBottom(3f).padTop(3f).growX().checked(b -> dialog.selected == sector).row();
    }

    private static Target find(TextField field){
        if(field == null || field.getScene() == null || Vars.ui == null || Vars.ui.planet == null) return null;

        PlanetDialog dialog = Vars.ui.planet;
        if(dialog.notifs == null || !contains(dialog.notifs, field)) return null;

        ScrollPane pane = ancestorPane(field);
        if(pane == null || !(pane.getWidget() instanceof Table)) return null;

        Table root = (Table)pane.getWidget();
        Table results = null;
        Seq<Element> children = root.getChildren();
        for(int i = 0; i < children.size; i++){
            Element child = children.get(i);
            if(child instanceof Table && !contains(child, field)){
                results = (Table)child;
                break;
            }
        }

        return results == null ? null : new Target(dialog, pane, results);
    }

    private static ScrollPane ancestorPane(Element element){
        Element cursor = element;
        while(cursor != null){
            if(cursor instanceof ScrollPane) return (ScrollPane)cursor;
            cursor = cursor.parent;
        }
        return null;
    }

    private static boolean contains(Element root, Element child){
        if(root == child) return true;
        if(root instanceof Group){
            Seq<Element> children = ((Group)root).getChildren();
            for(int i = 0; i < children.size; i++){
                if(contains(children.get(i), child)) return true;
            }
        }
        return false;
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
