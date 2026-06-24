package tripwire;

import arc.Events;
import arc.math.geom.Vec2;
import arc.struct.ObjectMap;
import arc.util.Time;
import mindustry.game.EventType;
import mindustry.gen.Groups;
import mindustry.gen.Unit;

import static mindustry.Vars.player;
import static mindustry.Vars.state;

public final class TripwireDetector {
    private static final ObjectMap<Unit, Vec2> lastPositions = new ObjectMap<>();
    private static float nextDetectTime;

    private TripwireDetector() {
    }

    public static void init() {
        Events.run(EventType.Trigger.update, TripwireDetector::update);
        Events.on(EventType.WorldLoadEvent.class, e -> clearCache());
    }

    public static void clearCache() {
        lastPositions.clear();
        nextDetectTime = 0f;
    }

    private static void update() {
        if (state == null || !state.isGame() || player == null) return;
        if (TripwireData.fences.isEmpty()) return;
        if (Time.time < nextDetectTime) return;
        nextDetectTime = Time.time + TripwireSettings.detectionMillis() / 1000f * 60f;

        for (int i = 0; i < Groups.unit.size(); i++) {
            Unit unit = Groups.unit.index(i);
            if (unit == null || !unit.isValid() || unit.type == null) continue;
            Vec2 last = lastPositions.get(unit);
            if (last != null && unit.team != player.team()) {
                checkUnit(unit, last.x, last.y);
            }
            if (last == null) lastPositions.put(unit, new Vec2(unit.x, unit.y));
            else last.set(unit.x, unit.y);
        }
    }

    private static void checkUnit(Unit unit, float lastX, float lastY) {
        for (TripwireFence fence : TripwireData.fences) {
            if (fence.team == unit.team || !fence.contains(unit.type)) continue;
            for (int i = 0; i < fence.points.size - 1; i++) {
                Vec2 a = fence.points.get(i);
                Vec2 b = fence.points.get(i + 1);
                float cross1 = TripwireFence.cross(a, b, lastX, lastY);
                float cross2 = TripwireFence.cross(a, b, unit.x, unit.y);
                if (!fence.crossedSelectedSide(cross1, cross2)) continue;
                if (!movementIntersectsSegment(lastX, lastY, unit.x, unit.y, a.x, a.y, b.x, b.y)) continue;
                TripwireAlert.crossed((a.x + b.x) / 2f, (a.y + b.y) / 2f, unit);
                return;
            }
        }
    }

    private static boolean movementIntersectsSegment(float ax, float ay, float bx, float by, float cx, float cy, float dx, float dy) {
        float c1 = cross(ax, ay, bx, by, cx, cy);
        float c2 = cross(ax, ay, bx, by, dx, dy);
        float c3 = cross(cx, cy, dx, dy, ax, ay);
        float c4 = cross(cx, cy, dx, dy, bx, by);
        return c1 * c2 <= 0f && c3 * c4 <= 0f;
    }

    private static float cross(float ax, float ay, float bx, float by, float px, float py) {
        return (bx - ax) * (py - ay) - (by - ay) * (px - ax);
    }
}
