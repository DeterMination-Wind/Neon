package tripwire;

import arc.Core;
import arc.Events;
import arc.input.KeyCode;
import arc.input.KeyBind;
import arc.math.geom.Rect;
import arc.math.geom.Vec2;
import arc.struct.Seq;
import mindustry.game.EventType;

import static mindustry.Vars.player;
import static mindustry.Vars.state;

public final class TripwireInput {
    private static final KeyBind createKey = KeyBind.add("tripwire-create", KeyCode.num1, "tripwire");
    private static final KeyBind deleteKey = KeyBind.add("tripwire-delete", KeyCode.num2, "tripwire");
    private static final Seq<Vec2> creatingPoints = new Seq<>();
    private static final Rect deleteRect = new Rect();
    private static boolean creating;
    private static boolean deleting;
    private static float deleteStartX;
    private static float deleteStartY;

    private TripwireInput() {
    }

    public static void init() {
        Events.run(EventType.Trigger.update, TripwireInput::update);
    }

    private static void update() {
        if (state == null || !state.isGame() || Core.scene.hasKeyboard()) return;

        if (Core.input.keyTap(createKey)) toggleCreate();

        if (Core.input.keyDown(deleteKey)) {
            if (!deleting && Core.input.keyTap(KeyCode.mouseLeft) && !Core.scene.hasMouse()) {
                deleting = true;
                deleteStartX = Core.input.mouseWorldX();
                deleteStartY = Core.input.mouseWorldY();
            }
        } else if (deleting) {
            finishDelete();
        }

        if (deleting && !Core.input.keyDown(KeyCode.mouseLeft)) finishDelete();

        if (creating && Core.input.keyTap(KeyCode.mouseLeft) && !Core.scene.hasMouse()) {
            creatingPoints.add(new Vec2(Core.input.mouseWorldX(), Core.input.mouseWorldY()));
            return;
        }

        if (!creating && !deleting && Core.input.keyTap(KeyCode.mouseLeft) && !Core.scene.hasMouse()) {
            TripwireFence fence = TripwireData.nearest(Core.input.mouseWorldX(), Core.input.mouseWorldY(), 10f);
            if (fence != null) TripwireConfig.show(fence);
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

    private static void finishDelete() {
        deleting = false;
        updateDeleteRect();
        TripwireData.fences.removeAll(fence -> fence.intersects(deleteRect));
    }

    static boolean isCreating() {
        return creating;
    }

    static Seq<Vec2> creatingPoints() {
        return creatingPoints;
    }

    static boolean isDeleting() {
        return deleting;
    }

    static Rect deleteRect() {
        updateDeleteRect();
        return deleteRect;
    }

    private static void updateDeleteRect() {
        float x2 = Core.input.mouseWorldX();
        float y2 = Core.input.mouseWorldY();
        deleteRect.set(Math.min(deleteStartX, x2), Math.min(deleteStartY, y2), Math.abs(x2 - deleteStartX), Math.abs(y2 - deleteStartY));
    }
}
