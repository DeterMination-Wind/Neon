package tripwire;

import arc.math.Mathf;
import arc.math.geom.Rect;
import arc.math.geom.Vec2;
import arc.struct.ObjectSet;
import arc.struct.Seq;
import mindustry.game.Team;
import mindustry.type.UnitType;

public class TripwireFence {
    public final int id;
    public final Seq<Vec2> points = new Seq<>();
    public final ObjectSet<UnitType> selectedUnits = new ObjectSet<>();
    public boolean isRightSide;
    public Team team;

    public TripwireFence(int id, Team team) {
        this.id = id;
        this.team = team;
    }

    public boolean isConfigured() {
        return !selectedUnits.isEmpty();
    }

    public boolean contains(UnitType type) {
        return type != null && selectedUnits.contains(type);
    }

    public float distanceTo(float x, float y) {
        float best = Float.MAX_VALUE;
        for (int i = 0; i < points.size - 1; i++) {
            Vec2 a = points.get(i), b = points.get(i + 1);
            best = Math.min(best, distancePointSegment(x, y, a.x, a.y, b.x, b.y));
        }
        return best;
    }

    public boolean intersects(Rect rect) {
        for (int i = 0; i < points.size - 1; i++) {
            Vec2 a = points.get(i), b = points.get(i + 1);
            if (rect.contains(a.x, a.y) || rect.contains(b.x, b.y)) return true;
            if (segmentsIntersect(a.x, a.y, b.x, b.y, rect.x, rect.y, rect.x + rect.width, rect.y)) return true;
            if (segmentsIntersect(a.x, a.y, b.x, b.y, rect.x, rect.y, rect.x, rect.y + rect.height)) return true;
            if (segmentsIntersect(a.x, a.y, b.x, b.y, rect.x + rect.width, rect.y, rect.x + rect.width, rect.y + rect.height)) return true;
            if (segmentsIntersect(a.x, a.y, b.x, b.y, rect.x, rect.y + rect.height, rect.x + rect.width, rect.y + rect.height)) return true;
        }
        return false;
    }

    public static float cross(Vec2 a, Vec2 b, float px, float py) {
        return (b.x - a.x) * (py - a.y) - (b.y - a.y) * (px - a.x);
    }

    private static float distancePointSegment(float px, float py, float ax, float ay, float bx, float by) {
        float dx = bx - ax, dy = by - ay;
        float len2 = dx * dx + dy * dy;
        if (len2 <= 0.0001f) return Mathf.dst(px, py, ax, ay);
        float t = Mathf.clamp(((px - ax) * dx + (py - ay) * dy) / len2);
        return Mathf.dst(px, py, ax + dx * t, ay + dy * t);
    }

    private static boolean segmentsIntersect(float ax, float ay, float bx, float by, float cx, float cy, float dx, float dy) {
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
