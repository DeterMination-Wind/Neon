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
    public DirectionMode direction = DirectionMode.all;
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

    public static boolean isCoreUnit(UnitType type) {
        if (type == null || type.name == null) return false;
        switch (type.name) {
            case "alpha":
            case "beta":
            case "gamma":
            case "evoke":
            case "incite":
            case "emanate":
                return true;
            default:
                return false;
        }
    }

    public boolean crossedSelectedSide(float cross1, float cross2) {
        if (Math.abs(cross1) < 0.0001f && Math.abs(cross2) < 0.0001f) return false;
        switch (direction) {
            case outer:
                return cross1 < 0f && cross2 >= 0f;
            case inner:
                return cross1 > 0f && cross2 <= 0f;
            case all:
            default:
                return (cross1 < 0f && cross2 >= 0f) || (cross1 > 0f && cross2 <= 0f);
        }
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

    public enum DirectionMode {
        inner(0, "tripwire.inner"),
        outer(1, "tripwire.outer"),
        all(2, "tripwire.allDirections");

        public final int id;
        public final String bundleKey;

        DirectionMode(int id, String bundleKey) {
            this.id = id;
            this.bundleKey = bundleKey;
        }

        public DirectionMode next() {
            switch (this) {
                case inner:
                    return outer;
                case outer:
                    return all;
                case all:
                default:
                    return inner;
            }
        }

        public static DirectionMode byId(int id) {
            for (DirectionMode mode : values()) {
                if (mode.id == id) return mode;
            }
            return all;
        }

        public static DirectionMode fromLegacyRightSide(boolean rightSide) {
            return rightSide ? outer : inner;
        }
    }
}
