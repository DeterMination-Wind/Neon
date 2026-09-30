package betterterraingen.v2.ui;

import arc.scene.style.TextureRegionDrawable;
import arc.scene.ui.Button;
import arc.scene.ui.Label;
import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import arc.util.Strings;
import betterterraingen.v2.filters.RuinGenerateFilter;
import betterterraingen.v2.filters.RuinStep;
import mindustry.content.Blocks;
import mindustry.gen.Icon;
import mindustry.maps.filters.FilterOption;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.world.Block;

import static mindustry.Vars.*;

/** Small ordered-step editor adapted from NH's ruin filter UI. */
public class RuinStepsDialog extends BaseDialog {
    private final RuinGenerateFilter filter;
    private final Runnable changed;
    private final Seq<RuinStep> editing = new Seq<>();
    private Table list;

    public RuinStepsDialog(RuinGenerateFilter filter, Runnable changed) {
        super("@btg.filter.steps.title");
        this.filter = filter;
        this.changed = changed;

        if (filter.steps != null) {
            for (RuinStep step : filter.steps) {
                if (step != null) editing.add(step.copy());
            }
        }

        cont.pane(table -> table.table(content -> {
            list = content;
            rebuildList();
        })).grow();

        buttons.defaults().size(200f, 54f);
        buttons.button("@add", Icon.add, () -> {
            editing.add(new RuinStep());
            rebuildList();
        });
        buttons.button("@back", Icon.left, this::hide);

        hidden(this::save);
    }

    private void save() {
        filter.steps = editing.toArray(RuinStep.class);
        changed.run();
    }

    private void rebuildList() {
        list.clear();
        list.defaults().pad(4f);

        if (editing.isEmpty()) {
            list.add("@btg.filter.steps.empty").wrap().width(280f).row();
            return;
        }

        for (int i = 0; i < editing.size; i++) {
            buildRow(list, editing.get(i), i);
            list.row();
        }
    }

    private void buildRow(Table table, RuinStep step, int index) {
        table.add("#" + (index + 1)).width(28f);

        table.field(String.valueOf((int)step.radius), text -> {
            if (Strings.canParsePositiveInt(text)) {
                step.radius = Integer.parseInt(text);
                changed.run();
            }
        }).width(55f).padRight(6f);

        table.add("@btg.filter.step.radius").padRight(8f);

        Label modeLabel = new Label("@btg.filter.step.mode." + modeName(step));
        modeLabel.setStyle(Styles.outlineLabel);
        table.button(button -> button.add(modeLabel).update(label ->
            label.setText("@btg.filter.step.mode." + modeName(step))), Styles.flatBordert, () -> {
            step.stepMode = step.stepMode == null ? RuinStep.StepMode.geometric : step.stepMode.next();
            changed.run();
        }).width(96f).padRight(8f);

        Button floorButton = table.button(button -> button.image(floorIcon(step))
            .update(image -> ((TextureRegionDrawable)image.getDrawable()).setRegion(floorIcon(step)))
            .size(iconSmall), () -> showFloorPicker(step)).size(48f).padRight(4f).get();
        bindFloorButton(floorButton, step);

        Button wallButton = table.button(button -> button.image(wallIcon(step))
            .update(image -> ((TextureRegionDrawable)image.getDrawable()).setRegion(wallIcon(step)))
            .size(iconSmall), () -> showWallPicker(step)).size(48f).padRight(4f).get();
        bindWallButton(wallButton, step);

        table.button(Icon.up, Styles.clearNoneTogglei, () -> {
            if (index <= 0) return;
            editing.swap(index, index - 1);
            rebuildList();
            changed.run();
        }).size(40f).disabled(button -> index <= 0);

        table.button(Icon.down, Styles.clearNoneTogglei, () -> {
            if (index >= editing.size - 1) return;
            editing.swap(index, index + 1);
            rebuildList();
            changed.run();
        }).size(40f).disabled(button -> index >= editing.size - 1);

        table.button(Icon.trash, Styles.clearNoneTogglei, () -> {
            editing.remove(index);
            rebuildList();
            changed.run();
        }).size(40f).padLeft(4f);
    }

    private void showFloorPicker(RuinStep step) {
        showBlockPicker("@filter.option.floor", floorDisplay(step), block -> {
            if (block == Blocks.removeWall) step.floor = Blocks.removeWall;
            else if (block == Blocks.air) step.floor = null;
            else step.floor = block;
            rebuildList();
            changed.run();
        }, block -> block == Blocks.removeWall || FilterOption.floorsOptional.get(block));
    }

    private void showWallPicker(RuinStep step) {
        showBlockPicker("@filter.option.wall", wallDisplay(step), block -> {
            if (block == Blocks.removeWall) {
                step.removeWall = true;
                step.wall = null;
            } else if (block == Blocks.air) {
                step.removeWall = false;
                step.wall = null;
            } else {
                step.removeWall = false;
                step.wall = block;
            }
            rebuildList();
            changed.run();
        }, block -> block == Blocks.removeWall || FilterOption.wallsOptional.get(block));
    }

    private void bindFloorButton(Button button, RuinStep step) {
        RuinStepUI.bindBlockButton(button, () -> floorDisplay(step), block -> {
            if (block == Blocks.removeWall) step.floor = Blocks.removeWall;
            else if (block == Blocks.air) step.floor = null;
            else step.floor = block;
            rebuildList();
            changed.run();
        }, block -> block == Blocks.removeWall || FilterOption.floorsOptional.get(block));
    }

    private void bindWallButton(Button button, RuinStep step) {
        RuinStepUI.bindBlockButton(button, () -> wallDisplay(step), block -> {
            if (block == Blocks.removeWall) {
                step.removeWall = true;
                step.wall = null;
            } else if (block == Blocks.air) {
                step.removeWall = false;
                step.wall = null;
            } else {
                step.removeWall = false;
                step.wall = block;
            }
            rebuildList();
            changed.run();
        }, block -> block == Blocks.removeWall || FilterOption.wallsOptional.get(block));
    }

    private void showBlockPicker(String title, Block selected, arc.func.Cons<Block> consumer,
                                 arc.func.Boolf<Block> filter) {
        RuinStepUI.showBlockPicker(title, selected, consumer, filter, changed);
    }

    private static String modeName(RuinStep step) {
        return (step.stepMode == null ? RuinStep.StepMode.chebyshev : step.stepMode).name();
    }

    private static Block floorDisplay(RuinStep step) {
        if (step.preservesFloor()) return Blocks.removeWall;
        return step.floor == null ? Blocks.air : step.floor;
    }

    private static Block wallDisplay(RuinStep step) {
        if (step.removesWall()) return Blocks.removeWall;
        return step.wall == null ? Blocks.air : step.wall;
    }

    private static arc.graphics.g2d.TextureRegion floorIcon(RuinStep step) {
        Block block = floorDisplay(step);
        return block == Blocks.air ? Icon.none.getRegion() : block.uiIcon;
    }

    private static arc.graphics.g2d.TextureRegion wallIcon(RuinStep step) {
        Block block = wallDisplay(step);
        return block == Blocks.air ? Icon.none.getRegion() : block.uiIcon;
    }
}
