package tripwire;

import arc.Core;
import arc.Events;
import arc.input.KeyBind;
import arc.input.KeyCode;
import arc.math.Mathf;
import arc.math.geom.Rect;
import arc.math.geom.Vec2;
import arc.scene.event.Touchable;
import arc.scene.ui.layout.Scl;
import arc.struct.Seq;
import arc.util.Align;
import mindustry.game.EventType;
import mindustry.ui.Styles;

import static mindustry.Vars.player;
import static mindustry.Vars.state;
import static mindustry.Vars.ui;

public final class TripwireInput {
    private static final KeyBind createKey = KeyBind.add("tripwire-create", KeyCode.num1, "tripwire");
    private static final KeyBind deleteKey = KeyBind.add("tripwire-delete", KeyCode.num2, "tripwire");
    private static final float leftHintCenterOffset = -150f;
    private static final Seq<Vec2> creatingPoints = new Seq<>();
    private static final Rect deleteRect = new Rect();
    private static String leftHintText = "";
    private static String bottomHintText = "";
    private static boolean creating;
    private static boolean keyboardDeleteDragging;
    private static boolean overlayDeleteMode;
    private static boolean overlayDeleteDragging;
    private static boolean deleteRectValid;
    private static boolean hintsBuilt;
    private static float deleteStartX;
    private static float deleteStartY;

    private TripwireInput() {
    }

    public static void init() {
        Events.run(EventType.Trigger.update, TripwireInput::update);
        Events.on(EventType.ClientLoadEvent.class, e -> buildHints());
    }

    public static void pressDrawButton() {
        if (!overlayAvailable() || deleteModeActive()) return;
        toggleCreate();
    }

    public static void pressDeleteButton() {
        if (!overlayAvailable() || creating || keyboardDeleteDragging) return;
        if (overlayDeleteMode) {
            finishOverlayDeleteMode();
        } else {
            overlayDeleteMode = true;
            overlayDeleteDragging = false;
            deleteRectValid = false;
        }
    }

    public static boolean overlayAvailable() {
        return state != null && state.isGame();
    }

    public static boolean isDrawHoldActive() {
        return creating;
    }

    public static boolean isDeleteHoldActive() {
        return overlayDeleteMode || keyboardDeleteDragging;
    }

    private static void update() {
        updateHints();

        if (!overlayAvailable()) {
            if (creating) cancelCreate();
            cancelDeleteModes();
            return;
        }
        if (Core.scene.hasKeyboard()) return;

        if (Core.input.keyTap(createKey) && !deleteModeActive()) toggleCreate();

        updateKeyboardDelete();
        updateOverlayDelete();

        if (creating && Core.input.keyTap(KeyCode.mouseLeft) && !mouseOverUi()) {
            creatingPoints.add(new Vec2(Core.input.mouseWorldX(), Core.input.mouseWorldY()));
            return;
        }

        if (!creating && !deleteModeActive() && Core.input.keyTap(KeyCode.mouseLeft) && !mouseOverUi()) {
            TripwireFence fence = TripwireData.nearest(Core.input.mouseWorldX(), Core.input.mouseWorldY(), 10f);
            if (fence != null) TripwireConfig.show(fence);
        }
    }

    private static void updateKeyboardDelete() {
        if (creating || overlayDeleteMode) return;

        if (Core.input.keyDown(deleteKey)) {
            if (!keyboardDeleteDragging && Core.input.keyTap(KeyCode.mouseLeft) && !mouseOverUi()) {
                beginDeleteDrag();
                keyboardDeleteDragging = true;
            }
        } else if (keyboardDeleteDragging) {
            finishKeyboardDelete();
        }

        if (!keyboardDeleteDragging) return;
        if (Core.input.keyDown(KeyCode.mouseLeft)) {
            updateDeleteRect();
        } else {
            finishKeyboardDelete();
        }
    }

    private static void updateOverlayDelete() {
        if (!overlayDeleteMode) return;

        if (!overlayDeleteDragging && Core.input.keyTap(KeyCode.mouseLeft) && !mouseOverUi()) {
            beginDeleteDrag();
            overlayDeleteDragging = true;
            return;
        }

        if (!overlayDeleteDragging) return;
        if (Core.input.keyDown(KeyCode.mouseLeft)) {
            updateDeleteRect();
        } else {
            overlayDeleteDragging = false;
        }
    }

    private static void toggleCreate() {
        if (!creating) {
            creating = true;
            creatingPoints.clear();
            return;
        }

        if (creatingPoints.size >= 2) {
            TripwireFence fence = TripwireData.create(player == null ? null : player.team());
            for (Vec2 point : creatingPoints) fence.points.add(new Vec2(point));
            TripwireData.add(fence);
        }
        creating = false;
        creatingPoints.clear();
    }

    private static void cancelCreate() {
        creating = false;
        creatingPoints.clear();
    }

    private static void beginDeleteDrag() {
        deleteStartX = Core.input.mouseWorldX();
        deleteStartY = Core.input.mouseWorldY();
        deleteRectValid = true;
        updateDeleteRect();
    }

    private static void finishKeyboardDelete() {
        if (deleteRectValid) {
            updateDeleteRect();
            deleteIntersectingFences();
        }
        keyboardDeleteDragging = false;
        deleteRectValid = false;
    }

    private static void finishOverlayDeleteMode() {
        if (overlayDeleteDragging) {
            updateDeleteRect();
            overlayDeleteDragging = false;
        }
        if (deleteRectValid) deleteIntersectingFences();
        overlayDeleteMode = false;
        deleteRectValid = false;
    }

    private static void cancelDeleteModes() {
        keyboardDeleteDragging = false;
        overlayDeleteMode = false;
        overlayDeleteDragging = false;
        deleteRectValid = false;
    }

    private static void deleteIntersectingFences() {
        TripwireData.fences.removeAll(fence -> fence.intersects(deleteRect));
    }

    private static boolean deleteModeActive() {
        return keyboardDeleteDragging || overlayDeleteMode;
    }

    static boolean isCreating() {
        return creating;
    }

    static Seq<Vec2> creatingPoints() {
        return creatingPoints;
    }

    static boolean isDeleting() {
        return keyboardDeleteDragging || (overlayDeleteMode && deleteRectValid);
    }

    static Rect deleteRect() {
        if (keyboardDeleteDragging || overlayDeleteDragging) updateDeleteRect();
        return deleteRect;
    }

    private static void updateDeleteRect() {
        float x2 = Core.input.mouseWorldX();
        float y2 = Core.input.mouseWorldY();
        deleteRect.set(Math.min(deleteStartX, x2), Math.min(deleteStartY, y2), Math.abs(x2 - deleteStartX), Math.abs(y2 - deleteStartY));
    }

    private static boolean mouseOverUi() {
        return Core.scene != null && Core.scene.hasMouse();
    }

    private static void buildHints() {
        if (hintsBuilt || ui == null || ui.hudGroup == null) return;
        hintsBuilt = true;

        ui.hudGroup.fill(left -> {
            left.touchable = Touchable.disabled;
            left.color.a = 0f;
            left.setTranslation(0f, Scl.scl(leftHintCenterOffset));
            left.visible(() -> fadeVisible(left, leftHintVisible()));
            left.left();
            left.marginLeft(12f);
            left.table(Styles.black6, table -> {
                table.touchable = Touchable.disabled;
                table.margin(10f);
                table.label(() -> leftHintText).style(Styles.outlineLabel).width(420f).left().labelAlign(Align.left).wrap();
            });
        });

        ui.hudGroup.fill(bottom -> {
            bottom.touchable = Touchable.disabled;
            bottom.color.a = 0f;
            bottom.visible(() -> fadeVisible(bottom, bottomHintVisible()));
            bottom.bottom();
            bottom.marginBottom(14f);
            bottom.table(Styles.black6, table -> {
                table.touchable = Touchable.disabled;
                table.margin(10f);
                table.label(() -> bottomHintText).style(Styles.outlineLabel).width(520f).center().labelAlign(Align.center).wrap();
            });
        });
    }

    private static void updateHints() {
        if (!hintsBuilt) buildHints();
        if (creating) {
            leftHintText = Core.bundle.get("tripwire.hint.create.click");
        } else if (Core.input.keyDown(deleteKey) || overlayDeleteMode) {
            leftHintText = Core.bundle.get("tripwire.hint.delete.drag");
        } else {
            leftHintText = "";
        }
        bottomHintText = creating ? Core.bundle.format("tripwire.hint.create.finish", createKeyName()) : "";
    }

    private static boolean leftHintVisible() {
        return ui != null && ui.hudfrag != null && ui.hudfrag.shown && (creating || Core.input.keyDown(deleteKey) || overlayDeleteMode);
    }

    private static boolean bottomHintVisible() {
        return ui != null && ui.hudfrag != null && ui.hudfrag.shown && creating;
    }

    private static boolean fadeVisible(arc.scene.ui.layout.Table table, boolean visible) {
        table.color.a = Mathf.lerpDelta(table.color.a, Mathf.num(visible), 0.15f);
        return table.color.a > 0.001f;
    }

    private static String createKeyName() {
        if (createKey.value == null) return "?";
        if (createKey.value.key != null) return createKey.value.key.toString();
        if (createKey.value.min != null && createKey.value.max != null) return createKey.value.min.toString() + "/" + createKey.value.max.toString();
        return "?";
    }
}
