package mindustry.ui.dialogs;

import arc.*;
import arc.graphics.*;
import arc.input.*;
import arc.math.*;
import arc.scene.event.*;
import arc.scene.style.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import mindustry.*;
import mindustry.content.*;
import mindustry.ctype.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.type.*;
import mindustry.ui.*;
import random.RandomTextResolver;

import java.util.Locale;

import static arc.Core.*;
import static mindustry.Vars.*;

/** Database dialog replacement that changes only the display text and search predicate. */
public class RandomDatabaseDialog extends DatabaseDialog{
    private final RandomTextResolver resolver;
    private final OrderedMap<String, OrderedMap<String, Seq<UnlockableContent>>> sortedContents = new OrderedMap<>();
    private final OrderedMap<String, Seq<UnlockableContent>> temporaryCategory = new OrderedMap<>();
    private Seq<UnlockableContent> allTabs;
    private UnlockableContent tab = Planets.sun;
    private TextField search;
    private final Table all = new Table();

    public RandomDatabaseDialog(RandomTextResolver resolver){
        super();
        this.resolver = resolver;
        shown(() -> {
            checkTabList();
            if(state.isCampaign() && allTabs.contains(state.getPlanet())){
                tab = state.getPlanet();
            }else if(state.isGame() && state.rules.planet != null && allTabs.contains(state.rules.planet)){
                tab = state.rules.planet;
            }
            rebuild();
        });
        rebuild();
    }

    @Override
    void rebuild(){
        checkTabList();
        sortContents();

        if(search == null){
            cont.clear();
            cont.top();
            cont.table(s -> {
                s.image(Icon.zoom).padRight(8);
                search = s.field(null, text -> rebuildResults()).growX().get();
                search.setMessageText("@players.search");
            }).fillX().padBottom(4f).row();
            cont.pane(all).scrollX(false);
        }

        rebuildResults();
    }

    private void rebuildResults(){
        if(search == null) return;

        all.clear();
        String query = search.getText().toLowerCase(Locale.ROOT);

        all.table(t -> {
            int index = 0;
            for(UnlockableContent content : allTabs){
                t.button(content == Planets.sun ? Icon.eyeSmall : content instanceof Planet planet
                    ? Icon.icons.get(planet.icon, Icon.commandRally) : new TextureRegionDrawable(content.uiIcon),
                    Styles.clearNoneTogglei, iconMed, () -> {
                        tab = content;
                        rebuildResults();
                    }).size(50f).checked(button -> tab == content).tooltip(content == Planets.sun ? "@all" : content.localizedName)
                    .with(button -> button.getStyle().imageUpColor = content instanceof Planet planet ? planet.iconColor : Color.white.cpy());
                if(++index % 10 == 0) t.row();
            }
        }).row();

        boolean hasResult = false;
        for(int i = 0; i < sortedContents.size; i++){
            String categoryName = sortedContents.orderedKeys().get(i);
            OrderedMap<String, Seq<UnlockableContent>> categoryContents = sortedContents.get(categoryName);
            temporaryCategory.clear();
            boolean categoryHasResult = false;

            for(int j = 0; j < categoryContents.size; j++){
                String tagName = categoryContents.orderedKeys().get(j);
                Seq<UnlockableContent> array = categoryContents.get(tagName).select(content ->
                    !content.isHidden() && !content.hideDatabase &&
                    (tab == Planets.sun || content.allDatabaseTabs || content.databaseTabs.contains(tab)) &&
                    resolver.matches(content, query)).as();
                if(array.isEmpty()) continue;

                hasResult = true;
                categoryHasResult = true;
                if(state.isGame()){
                    array.sort(Structs.comps(Structs.comparingBool(UnlockableContent::isBanned), Structs.comparingInt(content -> content.id)));
                }
                temporaryCategory.put(tagName, array);
            }

            if(temporaryCategory.isEmpty() || !categoryHasResult) continue;

            all.add(resolver.databaseCategory(categoryName)).growX().left().color(Pal.accent);
            all.row();
            all.image().pad(5).padLeft(0).padRight(0).height(3).color(Pal.accent).growX();
            all.row();

            all.table(sub -> {
                for(int j = 0; j < temporaryCategory.size; j++){
                    String tagName = temporaryCategory.orderedKeys().get(j);
                    Seq<UnlockableContent> array = temporaryCategory.get(tagName);
                    if(array == null || array.isEmpty()) continue;

                    if(!"default".equals(tagName)){
                        sub.table(tag -> {
                            tag.add(resolver.databaseTag(tagName)).left().color(Pal.gray);
                            tag.image().growX().pad(5).height(3).color(Pal.gray);
                        }).pad(4, 8, 4, 8).growX();
                        sub.row();
                    }

                    sub.table(list -> {
                        list.left();
                        int cols = (int)Mathf.clamp((graphics.getWidth() - Scl.scl(30)) / Scl.scl(32 + 12), 1, 22);
                        int count = 0;
                        for(UnlockableContent unlock : array){
                            Image image = unlocked(unlock)
                                ? new Image(new TextureRegionDrawable(unlock.uiIcon), mobile ? Color.white : Color.lightGray).setScaling(Scaling.fit)
                                : new Image(Icon.lock, Pal.gray);

                            if(state.isGame() && unlock.isBanned()){
                                list.stack(image, new Image(Icon.cancel){{
                                    setColor(Color.scarlet);
                                    touchable = Touchable.disabled;
                                }}).size(8 * 4).pad(3);
                            }else if(state.isGame() && state.data.isPatched(unlock)){
                                list.stack(image, new Table(){{
                                    right().bottom().touchable = Touchable.disabled;
                                    image(Icon.fileSmall).size(12f).color(Tmp.c1.set(Color.white).a(0.5f));
                                }}).size(8 * 4).pad(3);
                            }else{
                                list.add(image).size(8 * 4).pad(3);
                            }

                            ClickListener listener = new ClickListener();
                            image.addListener(listener);
                            if(!mobile && unlocked(unlock)){
                                image.addListener(new HandCursorListener());
                                image.update(() -> image.color.lerp(!listener.isOver() ? Color.lightGray : Color.white, Mathf.clamp(0.4f * Time.delta)));
                            }

                            if(unlocked(unlock)){
                                image.clicked(() -> {
                                    if(input.keyDown(KeyCode.shiftLeft) && Fonts.getUnicode(unlock.name) != 0){
                                        app.setClipboardText((char)Fonts.getUnicode(unlock.name) + "");
                                        ui.showInfoFade("@copied");
                                    }else{
                                        ui.content.show(unlock);
                                    }
                                });
                                image.addListener(new Tooltip(t -> t.background(Tex.button).add(resolver.name(unlock)
                                    + (settings.getBool("console") ? "\n[gray]" + unlock.name : ""))));
                            }

                            if((++count) % cols == 0) list.row();
                        }
                        for(int k = 0; k < cols - count; k++){
                            Image image = new Image();
                            image.setColor(Color.clear);
                            list.add(image).size(8 * 4).pad(3);
                        }
                    });
                    sub.row();
                }
            }).growX().left().padBottom(10);
            all.row();
        }

        if(!hasResult) all.add("@none.found");
    }

    void checkTabList(){
        if(allTabs != null) return;
        Seq<Content>[] contentMap = Vars.content.getContentMap();
        ObjectSet<UnlockableContent> tabs = new ObjectSet<>();
        for(Seq<Content> contents : contentMap){
            for(Content content : contents){
                if(content instanceof UnlockableContent unlock) tabs.addAll(unlock.databaseTabs);
            }
        }
        allTabs = tabs.toSeq().sort();
        allTabs.insert(0, Planets.sun);
    }

    void sortContents(){
        sortedContents.clear();
        Seq<Content>[] contentMap = Vars.content.getContentMap();
        for(Seq<Content> contents : contentMap){
            for(Content content : contents){
                if(content instanceof UnlockableContent unlock){
                    String category = unlock.databaseCategory == null ? unlock.getContentType().name() : unlock.databaseCategory;
                    String tag = unlock.databaseTag == null ? "default" : unlock.databaseTag;
                    OrderedMap<String, Seq<UnlockableContent>> categoryContents = sortedContents.get(category, new OrderedMap<>());
                    Seq<UnlockableContent> tagged = categoryContents.get(tag, new Seq<>());
                    tagged.add(unlock);
                    categoryContents.put(tag, tagged);
                    sortedContents.put(category, categoryContents);
                }
            }
        }
    }

    boolean unlocked(UnlockableContent content){
        return (!Vars.state.isCampaign() && !Vars.state.isMenu()) || content.unlocked();
    }
}
