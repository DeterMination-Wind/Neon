package tripwire;

import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Mathf;
import arc.math.Mat;
import arc.math.geom.Rect;
import arc.math.geom.Vec2;
import arc.scene.Element;
import arc.scene.event.Touchable;
import arc.scene.ui.layout.Table;
import arc.util.Interval;
import arc.util.Tmp;
import mindustry.game.EventType;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.type.UnitType;

import static mindustry.Vars.renderer;
import static mindustry.Vars.state;
import static mindustry.Vars.tilesize;
import static mindustry.Vars.ui;
import static mindustry.Vars.world;

public final class TripwireRenderer {
    private static final String overlayName = "tripwire-minimap-overlay";
    private static final float minimapBaseSize = 16f;
    private static final Interval interval = new Interval(3);
    private static final Rect viewRect = new Rect();
    private static final Mat transform = new Mat();
    private static final Mat oldTransform = new Mat();

    private TripwireRenderer() {
    }

    public static void init() {
        Events.run(EventType.Trigger.draw, TripwireRenderer::drawWorld);
        Events.run(EventType.Trigger.update, () -> {
            if (interval.check(0, 1f)) ensureOverlayAttached();
        });
        Events.on(EventType.ClientLoadEvent.class, e -> ensureOverlayAttached());
    }

    private static void drawWorld() {
        if (!TripwireSettings.showFences() || state == null || !state.isGame()) return;
        Draw.z(156f);
        for (TripwireFence fence : TripwireData.fences) drawFence(fence, true, 1f);
        drawCreatingPreview();
        drawDeleteRect();
        Draw.reset();
    }

    private static void drawCreatingPreview() {
        if (!TripwireInput.isCreating()) return;
        Draw.color(Pal.accent);
        Lines.stroke(Math.max(1f, TripwireSettings.lineWidth()));
        forEachSegment(TripwireInput.creatingPoints(), (a, b) -> Lines.line(a.x, a.y, b.x, b.y));
        if (!TripwireInput.creatingPoints().isEmpty()) {
            Vec2 last = TripwireInput.creatingPoints().peek();
            Lines.line(last.x, last.y, Core.input.mouseWorldX(), Core.input.mouseWorldY());
        }
    }

    private static void drawDeleteRect() {
        if (!TripwireInput.isDeleting()) return;
        Rect r = TripwireInput.deleteRect();
        Draw.color(Pal.remove, 0.22f);
        Fill.rect(r.x + r.width / 2f, r.y + r.height / 2f, r.width, r.height);
        Draw.color(Pal.remove);
        Lines.stroke(1.5f);
        Lines.rect(r.x, r.y, r.width, r.height);
    }

    private static void drawFence(TripwireFence fence, boolean units, float scale) {
        if (fence.points.size < 2) return;
        Color color = fence.isConfigured() ? TripwireSettings.configuredColor(fence.team.color) : Pal.sap;
        Draw.color(color);
        Lines.stroke(Math.max(0.6f * scale, TripwireSettings.lineWidth() * scale));
        forEachSegment(fence.points, (a, b) -> {
            Draw.color(color);
            Lines.line(a.x, a.y, b.x, b.y);
            drawHatches(a, b, fence.isRightSide, scale);
        });
        if (units) {
            forEachSegment(fence.points, (a, b) -> drawUnitIcons(fence, a, b, scale));
        }
        Draw.reset();
    }

    private static void drawHatches(Vec2 a, Vec2 b, boolean rightSide, float scale) {
        float dx = b.x - a.x, dy = b.y - a.y;
        float len = Mathf.len(dx, dy);
        if (len <= 0.001f) return;
        dx /= len;
        dy /= len;
        float nx = rightSide ? -dy : dy;
        float ny = rightSide ? dx : -dx;
        float spacing = 18f * scale;
        float hatch = 8f * scale;
        int count = Math.max(1, (int)(len / spacing));
        for (int i = 0; i <= count; i++) {
            float t = (i + 0.35f) / (count + 1f);
            float x = Mathf.lerp(a.x, b.x, t);
            float y = Mathf.lerp(a.y, b.y, t);
            Lines.line(x, y, x + (nx - dx * 0.35f) * hatch, y + (ny - dy * 0.35f) * hatch);
        }
    }

    private static void drawUnitIcons(TripwireFence fence, Vec2 a, Vec2 b, float scale) {
        if (fence.selectedUnits.isEmpty()) return;
        float mx = (a.x + b.x) / 2f;
        float my = (a.y + b.y) / 2f;
        float dx = b.x - a.x, dy = b.y - a.y;
        float len = Mathf.len(dx, dy);
        if (len <= 0.001f) return;
        dx /= len;
        dy /= len;

        int count = fence.selectedUnits.size;
        float size = Math.max(4f, TripwireSettings.iconSize() * scale);
        float step = size * 0.82f;
        int index = 0;
        for (UnitType type : fence.selectedUnits) {
            float offset = (index - (count - 1) / 2f) * step;
            float x = mx + dx * offset;
            float y = my + dy * offset;
            Draw.color(Color.black, 0.82f);
            Fill.rect(x, y, size, size);
            Draw.color(Pal.accent);
            Lines.stroke(Math.max(0.75f, 1.25f * scale));
            Lines.rect(x - size / 2f, y - size / 2f, size, size);
            Draw.color();
            Draw.rect(type.uiIcon, x, y, size * 0.72f, size * 0.72f);
            index++;
        }
    }

    private static void ensureOverlayAttached() {
        if (ui == null || ui.hudGroup == null || !TripwireSettings.showMinimap()) return;
        Element minimap = ui.hudGroup.find("minimap");
        if (!(minimap instanceof Table)) return;
        Table table = (Table)minimap;
        if (table.find(overlayName) != null || table.getChildren().isEmpty()) return;
        HudMinimapOverlay overlay = new HudMinimapOverlay(table.getChildren().get(0));
        overlay.name = overlayName;
        overlay.touchable = Touchable.disabled;
        table.addChild(overlay);
        overlay.toFront();
    }

    private static Rect computeViewRect() {
        float zoom = renderer.minimap.getZoom();
        float sz = minimapBaseSize * zoom;
        float dx = Core.camera.position.x / tilesize;
        float dy = Core.camera.position.y / tilesize;
        dx = Mathf.clamp(dx, sz, world.width() - sz);
        dy = Mathf.clamp(dy, sz, world.height() - sz);
        viewRect.set((dx - sz) * tilesize, (dy - sz) * tilesize, sz * 2f * tilesize, sz * 2f * tilesize);
        return viewRect;
    }

    private static void forEachSegment(Iterable<Vec2> points, SegmentConsumer consumer) {
        Vec2 previous = null;
        for (Vec2 point : points) {
            if (previous != null) consumer.accept(previous, point);
            previous = point;
        }
    }

    private interface SegmentConsumer {
        void accept(Vec2 a, Vec2 b);
    }

    private static class HudMinimapOverlay extends Element {
        private final Element base;

        HudMinimapOverlay(Element base) {
            this.base = base;
        }

        @Override
        public void act(float delta) {
            if (base != null) setBounds(base.x, base.y, base.getWidth(), base.getHeight());
            super.act(delta);
        }

        @Override
        public void draw() {
            if (!TripwireSettings.showMinimap()) return;
            if (ui == null || ui.hudfrag == null || !ui.hudfrag.shown) return;
            if (ui.minimapfrag != null && ui.minimapfrag.shown()) return;
            if (renderer == null || renderer.minimap == null || renderer.minimap.getRegion() == null) return;
            if (world == null || state == null || !state.isGame() || world.isGenerating()) return;
            if (!clipBegin()) return;

            Rect r = computeViewRect();
            float scaleX = width / r.width;
            float scaleY = height / r.height;
            float invScale = 1f / Math.max(0.0001f, Math.min(scaleX, scaleY));

            oldTransform.set(Draw.trans());
            transform.set(oldTransform);
            transform.translate(x, y);
            transform.scl(Tmp.v1.set(scaleX, scaleY));
            transform.translate(-r.x, -r.y);
            transform.translate(tilesize / 2f, tilesize / 2f);
            Draw.trans(transform);

            for (TripwireFence fence : TripwireData.fences) drawFence(fence, false, invScale);

            Draw.trans(oldTransform);
            Draw.reset();
            clipEnd();
        }
    }
}
