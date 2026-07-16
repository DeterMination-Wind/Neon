package mindustry.ui.dialogs;

import arc.*;
import arc.graphics.*;
import arc.scene.actions.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import mindustry.core.UI;
import mindustry.ctype.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.input.*;
import mindustry.ui.*;
import mindustry.world.meta.*;
import random.RandomTextResolver;

import static arc.Core.*;
import static mindustry.Vars.*;

/** Database content details with display-only randomized strings. */
public class RandomContentInfoDialog extends ContentInfoDialog{
    private final RandomTextResolver resolver;

    public RandomContentInfoDialog(RandomTextResolver resolver){
        super();
        this.resolver = resolver;
    }

    @Override
    public void show(UnlockableContent content){
        cont.clear();

        Table table = new Table();
        table.margin(10);

        content.checkStats();

        table.table(title1 -> {
            title1.image(content.uiIcon).size(iconXLarge).scaling(Scaling.fit);
            title1.add("[accent]" + resolver.name(content) + (settings.getBool("console") ? "\n[gray]" + content.name : "")).padLeft(5);
        });

        table.row();

        if(state.isGame() && state.data.isPatched(content)){
            table.table(t -> {
                t.image(Icon.info).color(Pal.lightishGray);
                t.add("@database.patched").color(Pal.lightishGray).padLeft(4f);
            }).pad(4f).left();
            table.row();
        }

        if(content.description != null){
            var any = content.stats.toMap().size > 0;

            if(any){
                table.add("@category.purpose").color(Pal.accent).fillX().padTop(10);
                table.row();
            }

            table.add("[lightgray]" + UI.formatIcons(resolver.description(content))).wrap().fillX()
                .padLeft(any ? 10 : 0).width(500f).padTop(any ? 0 : 10).left();
            table.row();

            if(!content.stats.useCategories && any){
                table.add("@category.general").fillX().color(Pal.accent);
                table.row();
            }
        }

        Stats stats = content.stats;
        for(StatCat cat : stats.toMap().keys()){
            OrderedMap<Stat, Seq<StatValue>> map = stats.toMap().get(cat);
            if(map.size == 0) continue;

            if(stats.useCategories){
                table.add(resolver.statCategory(cat)).color(Pal.accent).fillX();
                table.row();
            }

            for(Stat stat : map.keys()){
                table.table(inset -> {
                    inset.left();
                    stats.statInfo(inset.add("[lightgray]" + resolver.stat(stat) + ":[] ").left().top(), stat);
                    Seq<StatValue> values = map.get(stat);
                    for(StatValue value : values){
                        value.display(inset);
                        inset.add().size(10f);
                    }
                }).fillX().padLeft(10);
                table.row();
            }
        }

        if(content.details != null){
            table.add("[gray]" + (content.unlocked() || !content.hideDetails ? UI.formatIcons(resolver.details(content))
                : Iconc.lock + " " + Core.bundle.get("unlock.incampaign"))).pad(6).padTop(20).width(400f).wrap().fillX();
            table.row();
        }

        if(content.credit != null){
            table.row();
            table.add(Core.bundle.format("content.createdby", content.credit)).color(Color.gray).padTop(40f).row();
        }

        if(settings.getBool("console")){
            table.button("@viewfields", Icon.link, Styles.grayt, () -> {
                Class<?> contentClass = content.getClass();
                if(contentClass.isAnonymousClass()) contentClass = contentClass.getSuperclass();
                Core.app.openURI("https://mindustrygame.github.io/wiki/Modding%20Classes/" + contentClass.getSimpleName());
            }).margin(8f).pad(4f).padTop(16f).size(300f, 50f).row();
        }

        content.displayExtra(table);

        ScrollPane pane = new ScrollPane(table);
        table.marginRight(30f);
        cont.add(pane);

        if(isShown()){
            show(scene, Actions.fadeIn(0f));
        }else{
            show();
        }
    }
}
