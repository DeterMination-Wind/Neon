package tripwire;

import arc.Core;
import arc.graphics.Color;
import arc.input.KeyCode;
import arc.scene.event.Touchable;
import arc.scene.ui.ImageButton;
import arc.scene.ui.TextButton;
import arc.scene.ui.TextField;
import arc.scene.ui.layout.Scl;
import arc.scene.ui.layout.Table;
import arc.struct.ObjectSet;
import arc.struct.Seq;
import mindustry.gen.Icon;
import mindustry.gen.Tex;
import mindustry.type.UnitType;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;

import java.util.Locale;

import static mindustry.Vars.content;

public final class TripwireConfig {
    private static final String groupGround = "ground";
    private static final String groupAir = "air";
    private static final String groupNaval = "naval";
    private static final String[] groupOrder = {groupGround, groupAir, groupNaval};
    private static final float unitButtonSize = 76f;
    private static final float unitIconSize = 60f;
    private static final float unitButtonPad = 5f;

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

        dialog.cont.pane(Styles.noBarPane, grid).grow().minHeight(420f).scrollX(false).row();
        dialog.cont.table(bottom -> {
            bottom.add("@tripwire.direction").padRight(8f);
            bottom.button("", Styles.flatToggleMenut, () -> fence.direction = fence.direction.next())
                .width(160f)
                .update(b -> {
                    TextButton button = (TextButton)b;
                    button.setText(Core.bundle.get(fence.direction.bundleKey));
                    button.setChecked(fence.direction != TripwireFence.DirectionMode.all);
                });
        }).growX().padTop(8f).row();
        dialog.buttons.button("@back", Icon.left, dialog::hide).size(180f, 64f);
        dialog.closeOnBack();

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
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        for (UnitType type : all) {
            String localized = type.localizedName == null ? "" : type.localizedName.toLowerCase(Locale.ROOT);
            String name = type.name == null ? "" : type.name.toLowerCase(Locale.ROOT);
            if (q.isEmpty() || localized.contains(q) || name.contains(q)) filtered.add(type);
        }
    }

    public static void buildMultiSelectTable(Table grid, Seq<UnitType> types, ObjectSet<UnitType> selected) {
        grid.clear();
        grid.top().left();
        grid.margin(8f);

        for (String group : groupOrder) {
            Seq<UnitType> row = types.select(type -> group.equals(groupKey(type))).as();
            if (!row.isEmpty()) addGroupRow(grid, row, selected);
        }

        Seq<UnitType> other = types.select(type -> !isKnownGroup(groupKey(type))).as();
        if (!other.isEmpty()) addGroupRow(grid, other, selected);
    }

    private static void addGroupRow(Table grid, Seq<UnitType> types, ObjectSet<UnitType> selected) {
        int col = columns();
        Table row = new Table();
        row.left();
        int i = 0;
        for (UnitType type : types) {
            ImageButton button = new ImageButton(type.uiIcon, Styles.clearNoneTogglei);
            button.resizeImage(unitIconSize);
            button.touchable = Touchable.enabled;
            button.clicked(() -> {
                if (selected.contains(type)) selected.remove(type);
                else selected.add(type);
            });
            button.update(() -> {
                boolean checked = selected.contains(type);
                button.setChecked(checked);
                button.getImage().setColor(checked ? Color.white : Color.gray);
            });
            button.addListener(new arc.scene.ui.Tooltip(t -> t.background(Tex.button).add(type.localizedName)));
            row.add(button).size(unitButtonSize).pad(unitButtonPad);
            if (++i % col == 0) row.row();
        }
        grid.add(row).growX().left().padBottom(10f).row();
    }

    private static int columns() {
        float available = Math.max(Scl.scl(480f), Core.scene.getWidth() - Scl.scl(120f));
        float stride = Scl.scl(unitButtonSize + unitButtonPad * 2f);
        return Math.max(1, (int)(available / stride));
    }

    private static String groupKey(UnitType type) {
        if (type == null) return groupGround;
        if (type.flying) return groupAir;
        if (type.naval) return groupNaval;
        return groupGround;
    }

    private static boolean isKnownGroup(String group) {
        for (String known : groupOrder) {
            if (known.equals(group)) return true;
        }
        return false;
    }
}
