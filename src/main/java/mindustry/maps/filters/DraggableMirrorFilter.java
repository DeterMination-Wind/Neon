package mindustry.maps.filters;

import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Mathf;
import arc.math.geom.Rect;
import arc.math.geom.Vec2;
import arc.scene.ui.Image;
import arc.scene.ui.layout.Scl;
import arc.util.Log;
import arc.util.Tmp;
import mindustry.graphics.Pal;
import mindustry.world.Tile;

import java.lang.reflect.Method;

import static mindustry.Vars.editor;

public class DraggableMirrorFilter extends MirrorFilter {
    private static final Vec2 dir = new Vec2();
    private static final Vec2 lineA = new Vec2();
    private static final Vec2 lineB = new Vec2();
    private static final Vec2 point = new Vec2();

    private static Method tileMethod;
    private static boolean tileMethodResolved;
    private static boolean tileMethodFailed;

    public float axisX = 0.5f;
    public float axisY = 0.5f;

    public static DraggableMirrorFilter fromMirror(MirrorFilter mirror) {
        DraggableMirrorFilter filter = new DraggableMirrorFilter();
        filter.seed = mirror.seed;
        filter.angle = mirror.angle;
        filter.rotate = mirror.rotate;
        return filter;
    }

    @Override
    public String simpleName() {
        return "mirror";
    }

    @Override
    public void apply(GenerateInput in) {
        try {
            float pivotX = axisCoord(axisX, in.width);
            float pivotY = axisCoord(axisY, in.height);

            dir.trnsExact(angle - 90f, 1f);
            lineA.set(pivotX + dir.x, pivotY + dir.y);
            lineB.set(pivotX - dir.x, pivotY - dir.y);
            point.set(in.x, in.y);

            if (!left(lineA, lineB, point)) {
                mirror(in.width, in.height, point, lineA.x, lineA.y, lineB.x, lineB.y);
                Tile tile = tileSafe(in, point.x, point.y);
                if (tile == null) return;

                in.floor = tile.floor();
                if (!tile.block().synthetic()) {
                    in.block = tile.block();
                }
                in.overlay = tile.overlay();
                in.packedData = tile.getPackedData();
            }
        } catch (Throwable t) {
            if (!tileMethodFailed) {
                tileMethodFailed = true;
                Log.err("[BetterMapEditor] Mirror filter apply failed; keeping generator alive.", t);
            }
        }
    }

    @Override
    public void draw(Image image) {
        Rect drawRect = Tmp.r1;
        computePreviewRect(image, drawRect);
        if (drawRect.width <= 0f || drawRect.height <= 0f) return;

        float localPivotX = drawRect.x + axisXNorm() * drawRect.width;
        float localPivotY = drawRect.y + axisYNorm() * drawRect.height;

        Tmp.v1.trns(angle - 90f, 1f);
        clipHalfLine(Tmp.v1,
            drawRect.x - localPivotX,
            drawRect.y - localPivotY,
            drawRect.x + drawRect.width - localPivotX,
            drawRect.y + drawRect.height - localPivotY);

        Tmp.v2.set(Tmp.v1).scl(-1f);
        clipHalfLine(Tmp.v2,
            drawRect.x - localPivotX,
            drawRect.y - localPivotY,
            drawRect.x + drawRect.width - localPivotX,
            drawRect.y + drawRect.height - localPivotY);

        float px = image.x + localPivotX;
        float py = image.y + localPivotY;

        Tmp.v1.add(px, py);
        Tmp.v2.add(px, py);

        Lines.stroke(Scl.scl(3f), Pal.accent);
        Lines.line(Tmp.v1.x, Tmp.v1.y, Tmp.v2.x, Tmp.v2.y);

        Draw.color(Pal.accent);
        Fill.circle(px, py, Scl.scl(4f));
        Draw.color(Pal.darkestGray);
        Fill.circle(px, py, Scl.scl(1.8f));
        Draw.reset();
    }

    public void setAxisNormalized(float x, float y) {
        axisX = sanitizeNorm(x);
        axisY = sanitizeNorm(y);
    }

    public float axisXNorm() {
        return sanitizeNorm(axisX);
    }

    public float axisYNorm() {
        return sanitizeNorm(axisY);
    }

    public static void computePreviewRect(Image image, Rect out) {
        if (out == null) return;

        if (image == null || editor == null || editor.width() <= 0 || editor.height() <= 0) {
            out.set(0f, 0f, 0f, 0f);
            return;
        }

        float w = image.getWidth();
        float h = image.getHeight();

        if (w <= 0f || h <= 0f) {
            out.set(0f, 0f, 0f, 0f);
            return;
        }

        float mapW = editor.width();
        float mapH = editor.height();
        float scale = Math.min(w / mapW, h / mapH);

        float drawW = mapW * scale;
        float drawH = mapH * scale;
        out.set((w - drawW) * 0.5f, (h - drawH) * 0.5f, drawW, drawH);
    }

    private float axisCoord(float normalized, int size) {
        if (size <= 1) return 0f;
        return sanitizeNorm(normalized) * (size - 1f);
    }

    private static float sanitizeNorm(float value) {
        if (!Float.isFinite(value)) return 0.5f;
        return Mathf.clamp(value, 0f, 1f);
    }

    private static Tile tileSafe(GenerateInput in, float x, float y) {
        Method method = resolveTileMethod();
        if (method == null) return null;

        try {
            Class<?>[] params = method.getParameterTypes();
            Object rawTile;

            if (params[0] == int.class && params[1] == int.class) {
                rawTile = method.invoke(in, (int) x, (int) y);
            } else {
                rawTile = method.invoke(in, x, y);
            }

            return rawTile instanceof Tile ? (Tile) rawTile : null;
        } catch (Throwable t) {
            if (!tileMethodFailed) {
                tileMethodFailed = true;
                Log.err("[BetterMapEditor] Failed to read tile from GenerateInput via reflection.", t);
            }
            return null;
        }
    }

    private static Method resolveTileMethod() {
        if (tileMethodResolved) return tileMethod;

        synchronized (DraggableMirrorFilter.class) {
            if (tileMethodResolved) return tileMethod;

            Method resolved = null;
            Method[] methods = GenerateInput.class.getDeclaredMethods();
            for (int i = 0; i < methods.length; i++) {
                Method method = methods[i];
                if (!method.getName().equals("tile") || method.getParameterCount() != 2) continue;

                Class<?>[] params = method.getParameterTypes();
                boolean xSupported = params[0] == float.class || params[0] == int.class;
                boolean ySupported = params[1] == float.class || params[1] == int.class;
                if (!xSupported || !ySupported) continue;

                method.setAccessible(true);
                resolved = method;
                break;
            }

            if (resolved == null && !tileMethodFailed) {
                tileMethodFailed = true;
                Log.err("[BetterMapEditor] Could not resolve GenerateInput.tile(...) method.");
            }

            tileMethod = resolved;
            tileMethodResolved = true;
            return tileMethod;
        }
    }

    @Override
    public void mirror(int width, int height, Vec2 p, float x0, float y0, float x1, float y1) {
        if ((width != height && angle % 90 != 0) || rotate) {
            p.x = width - p.x - 1;
            p.y = height - p.y - 1;
        } else {
            float dx = x1 - x0;
            float dy = y1 - y0;

            float a = (dx * dx - dy * dy) / (dx * dx + dy * dy);
            float b = 2f * dx * dy / (dx * dx + dy * dy);

            p.set(a * (p.x - x0) + b * (p.y - y0) + x0, b * (p.x - x0) - a * (p.y - y0) + y0);
        }
    }

    @Override
    public boolean left(Vec2 a, Vec2 b, Vec2 c) {
        return (b.x - a.x) * (c.y - a.y) > (b.y - a.y) * (c.x - a.x);
    }

    @Override
    public void clipHalfLine(Vec2 v, float xmin, float ymin, float xmax, float ymax) {
        v.scl(1f / Math.max(Math.abs(v.x < 0f ? v.x / xmin : v.x / xmax), Math.abs(v.y < 0f ? v.y / ymin : v.y / ymax)));
    }

}
