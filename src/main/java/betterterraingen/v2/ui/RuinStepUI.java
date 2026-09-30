package betterterraingen.v2.ui;

import arc.Core;
import arc.func.Boolf;
import arc.func.Cons;
import arc.func.Prov;
import arc.input.KeyCode;
import arc.scene.ui.Button;
import mindustry.content.Blocks;
import mindustry.gen.Icon;
import mindustry.maps.filters.FilterOption;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.world.Block;

import static mindustry.Vars.*;

/** Shared block picker behavior for the ordered Ruin step editor. */
public final class RuinStepUI {
    private RuinStepUI() {
    }

    public static void showBlockPicker(String title, Block selected, Cons<Block> consumer,
                                       Boolf<Block> filter, Runnable changed) {
        BaseDialog dialog = new BaseDialog(title);
        dialog.cont.pane(table -> {
            int index = 0;
            for (Block block : content.blocks()) {
                if (block != selected && !filter.get(block)) continue;
                table.image(block == Blocks.air ? Icon.none.getRegion() : block.uiIcon)
                    .size(iconMed).pad(3f)
                    .tooltip(block == Blocks.air ? "@none" : block.localizedName)
                    .get().clicked(() -> {
                        consumer.get(block);
                        dialog.hide();
                        changed.run();
                    });
                if (++index % 10 == 0) table.row();
            }
            dialog.setFillParent(index > 100);
        }).scrollX(false);
        dialog.addCloseButton();
        dialog.show();
    }

    public static void bindBlockButton(Button button, Prov<Block> supplier, Cons<Block> consumer,
                                       Boolf<Block> filter) {
        button.clicked(KeyCode.mouseMiddle, () -> {
            Block block = supplier.get();
            if (block != null && block != Blocks.air) {
                Core.app.setClipboardText(block.name);
                ui.showInfoFade("@copied");
            }
        });

        button.clicked(KeyCode.mouseRight, () -> {
            Block block = content.block(Core.app.getClipboardText());
            if (block != null && filter.get(block)) consumer.get(block);
        });
    }
}
