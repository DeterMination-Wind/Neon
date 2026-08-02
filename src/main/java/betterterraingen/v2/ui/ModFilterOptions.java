package betterterraingen.v2.ui;

import arc.Core;
import arc.func.Boolc;
import arc.func.Boolf;
import arc.func.Boolp;
import arc.func.Cons;
import arc.func.Floatc;
import arc.func.Floatp;
import arc.func.Prov;
import arc.input.KeyCode;
import arc.scene.event.Touchable;
import arc.scene.style.TextureRegionDrawable;
import arc.scene.ui.Button;
import arc.scene.ui.CheckBox;
import arc.scene.ui.Slider;
import arc.scene.ui.layout.Table;
import arc.util.Align;
import arc.util.Strings;
import mindustry.content.Blocks;
import mindustry.gen.Icon;
import mindustry.maps.filters.FilterOption;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.world.Block;

import static mindustry.Vars.content;
import static mindustry.Vars.iconMed;
import static mindustry.Vars.iconSmall;
import static mindustry.Vars.ui;
import static mindustry.Vars.updateEditorOnChange;

/** Public filter controls that are safe to instantiate from a mod ClassLoader. */
public final class ModFilterOptions {
    private ModFilterOptions() {
    }

    public static final class SliderOption extends FilterOption {
        private final String name;
        private final Floatp getter;
        private final Floatc setter;
        private final float min;
        private final float max;
        private final float step;

        public SliderOption(String name, Floatp getter, Floatc setter, float min, float max, float step) {
            this.name = name;
            this.getter = getter;
            this.setter = setter;
            this.min = min;
            this.max = max;
            this.step = step;
        }

        @Override
        public void build(Table table) {
            Table label = new Table().marginLeft(11f).marginRight(11f);
            label.touchable = Touchable.disabled;
            label.add("@filter.option." + name).growX().wrap().style(Styles.outlineLabel);
            label.label(() -> Strings.autoFixed(getter.get(), 2))
                .style(Styles.outlineLabel)
                .right()
                .labelAlign(Align.right)
                .padLeft(6f);

            Slider slider = new Slider(min, max, step, false);
            slider.moved(setter);
            slider.setValue(getter.get());
            if (updateEditorOnChange) {
                slider.changed(changed);
            } else {
                slider.released(changed);
            }

            table.stack(slider, label).colspan(2).pad(3f).growX().row();
        }
    }

    public static final class BlockOption extends FilterOption {
        private final String name;
        private final Prov<Block> supplier;
        private final Cons<Block> consumer;
        private final Boolf<Block> filter;

        public BlockOption(String name, Prov<Block> supplier, Cons<Block> consumer, Boolf<Block> filter) {
            this.name = name;
            this.supplier = supplier;
            this.consumer = consumer;
            this.filter = filter;
        }

        @Override
        public void build(Table table) {
            Button button = table.button(buttonTable -> buttonTable.image(supplier.get().uiIcon)
                .update(image -> ((TextureRegionDrawable) image.getDrawable()).setRegion(
                    supplier.get() == Blocks.air ? Icon.none.getRegion() : supplier.get().uiIcon
                )).size(iconSmall), () -> {
                    BaseDialog dialog = new BaseDialog("@filter.option." + name);
                    dialog.cont.pane(blockTable -> {
                        int index = 0;
                        for (Block block : content.blocks()) {
                            if (!filter.get(block)) continue;

                            blockTable.image(block == Blocks.air ? Icon.none.getRegion() : block.uiIcon)
                                .size(iconMed)
                                .pad(3f)
                                .tooltip(block == Blocks.air ? "@none" : block.localizedName)
                                .get()
                                .clicked(() -> {
                                    consumer.get(block);
                                    dialog.hide();
                                    changed.run();
                                });
                            if (++index % 10 == 0) blockTable.row();
                        }
                        dialog.setFillParent(index > 100);
                    }).scrollX(false);
                    dialog.addCloseButton();
                    dialog.show();
                }).pad(4f).margin(12f).get();

            button.clicked(KeyCode.mouseMiddle, () -> {
                Core.app.setClipboardText(supplier.get().name);
                ui.showInfoFade("@copied");
            });

            button.clicked(KeyCode.mouseRight, () -> {
                Block clipboardBlock = content.block(Core.app.getClipboardText());
                if (clipboardBlock != null && filter.get(clipboardBlock)) {
                    consumer.get(clipboardBlock);
                    changed.run();
                }
            });

            table.add("@filter.option." + name);
        }
    }

    public static final class ToggleOption extends FilterOption {
        private final String name;
        private final Boolp getter;
        private final Boolc setter;

        public ToggleOption(String name, Boolp getter, Boolc setter) {
            this.name = name;
            this.getter = getter;
            this.setter = setter;
        }

        @Override
        public void build(Table table) {
            table.row();
            CheckBox check = table.check("@filter.option." + name, setter)
                .growX()
                .padBottom(5f)
                .padTop(5f)
                .center()
                .get();
            check.setChecked(getter.get());
            check.changed(changed);
        }
    }
}
