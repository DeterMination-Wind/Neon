package tripwire;

import arc.Core;
import arc.graphics.Color;
import arc.input.KeyCode;
import arc.scene.ui.Image;
import arc.scene.ui.TextButton;
import arc.scene.ui.TextField;
import arc.scene.ui.layout.Scl;
import arc.scene.ui.layout.Table;
import arc.struct.ObjectSet;
import arc.struct.Seq;
import mindustry.gen.Icon;
import mindustry.graphics.Pal;
import mindustry.type.UnitType;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;

import static mindustry.Vars.content;

public final class TripwireConfig {
    private TripwireConfig() {
    }

    public static void show(TripwireFence fence) {
        BaseDialog dialog = new BaseDialog("Tripwire #" + fence.id);
        Table grid = new Table();
        Seq<UnitType> all = content.units().copy();
        Seq<UnitType> filtered = new Seq<>();
        ObjectSet<UnitType> selected = fence.selectedUnits;

        Runnable rebuild = () -> buildMultiSelectTable(grid, filtered, selected);

        dialog.cont.table(top -> {
            top.left();
            top.add("@tripwire.search").padRight(6f);
            TextField search = top.field("", text -> { }).growX().get();
            search.setMessageText(Core.bundle.get("tripwire.search.hint"));

            top.button("@tripwire.all", () -> {
                selected.clear();
                selected.addAll(all);
                rebuild.run();
            }).padLeft(6f);
            top.button("@tripwire.none", () -> {
                selected.clear();
                rebuild.run();
            }).padLeft(4f);
            top.button("@tripwire.invert", () -> {
                for (UnitType type : all) {
                    if (selected.contains(type)) selected.remove(type);
                    else selected.add(type);
                }
                rebuild.run();
            }).padLeft(4f);

            search.changed(() -> {
                filter(all, filtered, search.getText());
                rebuild.run();
            });
        }).growX().row();

        dialog.cont.pane(Styles.noBarPane, grid).grow().minHeight(320f).row();
        dialog.cont.table(bottom -> {
            bottom.add("@tripwire.direction").padRight(8f);
            bottom.button("", Styles.flatToggleMenut, () -> fence.isRightSide = !fence.isRightSide)
                .width(160f)
                .update(b -> {
                    TextButton button = (TextButton)b;
                    button.setText(fence.isRightSide ? Core.bundle.get("tripwire.right") : Core.bundle.get("tripwire.left"));
                    button.setChecked(fence.isRightSide);
                });
        }).growX().padTop(8f).row();
        dialog.buttons.button("@back", Icon.left, dialog::hide).size(180f, 64f);

        filter(all, filtered, "");
        rebuild.run();
        dialog.update(() -> {
            if (Core.input.keyTap(KeyCode.mouseLeft) && !dialog.cont.hasMouse() && !dialog.buttons.hasMouse()) {
                dialog.hide();
            }
        });
        dialog.show();
    }

    private static void filter(Seq<UnitType> all, Seq<UnitType> filtered, String query) {
        filtered.clear();
        String q = query == null ? "" : query.trim().toLowerCase();
        for (UnitType type : all) {
            String localized = type.localizedName == null ? "" : type.localizedName.toLowerCase();
            String name = type.name == null ? "" : type.name.toLowerCase();
            if (q.isEmpty() || localized.contains(q) || name.contains(q)) filtered.add(type);
        }
    }

    public static void buildMultiSelectTable(Table grid, Seq<UnitType> types, ObjectSet<UnitType> selected) {
        grid.clear();
        int col = Math.max(1, (int)(Core.scene.getWidth() / Scl.scl(170f)));
        int i = 0;
        for (UnitType type : types) {
            TextButton button = new TextButton(type.localizedName, Styles.flatToggleMenut);
            button.add(new Image(type.uiIcon)).size(32f).pad(8f);
            button.getCells().reverse();
            button.clicked(() -> {
                if (selected.contains(type)) selected.remove(type);
                else selected.add(type);
            });
            button.update(() -> {
                boolean checked = selected.contains(type);
                button.setChecked(checked);
                button.setColor(checked ? Pal.accent : Color.white);
            });
            grid.add(button).size(160f, 92f).pad(4f);
            if (++i % col == 0) grid.row();
        }
    }
}
