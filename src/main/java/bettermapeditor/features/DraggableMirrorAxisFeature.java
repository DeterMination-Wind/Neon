package bettermapeditor.features;

import arc.Events;
import arc.func.Prov;
import arc.input.KeyCode;
import arc.math.geom.Rect;
import arc.math.geom.Vec2;
import arc.scene.Element;
import arc.scene.Group;
import arc.scene.event.InputEvent;
import arc.scene.event.InputListener;
import arc.scene.ui.Image;
import arc.scene.ui.layout.Scl;
import arc.struct.Seq;
import arc.struct.SnapshotSeq;
import arc.util.Log;
import arc.util.Time;
import mindustry.editor.MapGenerateDialog;
import mindustry.game.EventType;
import mindustry.maps.Maps;
import mindustry.maps.filters.DraggableMirrorFilter;
import mindustry.maps.filters.GenerateFilter;
import mindustry.maps.filters.MirrorFilter;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static mindustry.Vars.ui;

public class DraggableMirrorAxisFeature {
    private static boolean inited;
    private static boolean reflectionReady;

    private static Field filtersField;
    private static Method updateMethod;
    private static Method rebuildFiltersMethod;

    private static MapGenerateDialog attachedDialog;
    private static Image attachedPreview;
    private static AxisDragListener dragListener;

    public static void init() {
        if (inited) return;
        inited = true;

        patchFilterRegistry();

        Events.on(EventType.ClientLoadEvent.class, event -> {
            patchFilterRegistry();
            resolveReflection();
        });

        Events.run(EventType.Trigger.update, DraggableMirrorAxisFeature::update);
    }

    private static void patchFilterRegistry() {
        Prov<GenerateFilter>[] all = Maps.allFilterTypes;
        if (all == null || all.length == 0) return;

        for (int i = 0; i < all.length; i++) {
            GenerateFilter sample;
            try {
                sample = all[i].get();
            } catch (Throwable e) {
                continue;
            }

            if (sample != null && sample.getClass() == MirrorFilter.class) {
                all[i] = DraggableMirrorFilter::new;
            }
        }
    }

    private static void update() {
        if (ui == null || ui.editor == null) {
            clearAttached();
            return;
        }

        MapGenerateDialog dialog = ui.editor.getGenerateDialog();
        if (dialog == null || !dialog.isShown()) {
            clearAttached();
            return;
        }

        if (!reflectionReady) resolveReflection();
        if (!reflectionReady) return;

        if (dialog != attachedDialog) {
            clearAttached();
            attachedDialog = dialog;
        }

        Seq<GenerateFilter> filters = getFilters(dialog);
        if (filters == null) return;

        boolean replaced = replaceMirrorFilters(filters);
        if (replaced) {
            invoke(dialog, rebuildFiltersMethod);
            invoke(dialog, updateMethod);
        }

        Image preview = findPreviewImage(dialog.cont);
        if (preview == null) return;

        if (preview != attachedPreview) {
            detachListener();
            attachedPreview = preview;
            dragListener = new AxisDragListener(dialog, preview);
            attachedPreview.addListener(dragListener);
        }
    }

    private static boolean replaceMirrorFilters(Seq<GenerateFilter> filters) {
        boolean replaced = false;

        for (int i = 0; i < filters.size; i++) {
            GenerateFilter filter = filters.get(i);
            if (filter instanceof DraggableMirrorFilter) continue;

            if (filter instanceof MirrorFilter) {
                filters.set(i, DraggableMirrorFilter.fromMirror((MirrorFilter) filter));
                replaced = true;
            }
        }

        return replaced;
    }

    private static Seq<GenerateFilter> getFilters(MapGenerateDialog dialog) {
        try {
            @SuppressWarnings("unchecked")
            Seq<GenerateFilter> filters = (Seq<GenerateFilter>) filtersField.get(dialog);
            return filters;
        } catch (Throwable e) {
            return null;
        }
    }

    private static Seq<DraggableMirrorFilter> collectDraggableFilters(MapGenerateDialog dialog) {
        Seq<DraggableMirrorFilter> out = new Seq<>();
        Seq<GenerateFilter> filters = getFilters(dialog);
        if (filters == null) return out;

        for (int i = 0; i < filters.size; i++) {
            GenerateFilter filter = filters.get(i);
            if (filter instanceof DraggableMirrorFilter) {
                out.add((DraggableMirrorFilter) filter);
            }
        }
        return out;
    }

    private static void resolveReflection() {
        try {
            filtersField = MapGenerateDialog.class.getDeclaredField("filters");
            filtersField.setAccessible(true);

            updateMethod = MapGenerateDialog.class.getDeclaredMethod("update");
            updateMethod.setAccessible(true);

            rebuildFiltersMethod = MapGenerateDialog.class.getDeclaredMethod("rebuildFilters");
            rebuildFiltersMethod.setAccessible(true);

            reflectionReady = true;
        } catch (Throwable e) {
            reflectionReady = false;
            Log.err("[BetterMapEditor] Failed to bind MapGenerateDialog internals.", e);
        }
    }

    private static void invoke(MapGenerateDialog dialog, Method method) {
        if (dialog == null || method == null) return;
        try {
            method.invoke(dialog);
        } catch (Throwable ignored) {
        }
    }

    private static Image findPreviewImage(Element root) {
        if (root == null) return null;

        Image best = null;
        float bestScore = -1f;

        Seq<Element> queue = new Seq<>();
        queue.add(root);

        while (!queue.isEmpty()) {
            Element next = queue.pop();

            if (next instanceof Image) {
                Image image = (Image) next;
                if (image.getDrawable() != null) {
                    float score = image.getWidth() * image.getHeight()
                        + image.getDrawable().getMinWidth() * image.getDrawable().getMinHeight() * 4f;
                    if (score > bestScore) {
                        bestScore = score;
                        best = image;
                    }
                }
            }

            if (next instanceof Group) {
                SnapshotSeq<Element> children = ((Group) next).getChildren();
                for (int i = 0; i < children.size; i++) {
                    queue.add(children.get(i));
                }
            }
        }

        return best;
    }

    private static void clearAttached() {
        detachListener();
        attachedDialog = null;
    }

    private static void detachListener() {
        if (attachedPreview != null && dragListener != null) {
            attachedPreview.removeListener(dragListener);
        }
        dragListener = null;
        attachedPreview = null;
    }

    private static class AxisDragListener extends InputListener {
        private final MapGenerateDialog dialog;
        private final Image preview;
        private final Rect mapRect = new Rect();
        private final Vec2 axisDir = new Vec2();

        private DraggableMirrorFilter dragging;
        private long lastUpdateTime;

        AxisDragListener(MapGenerateDialog dialog, Image preview) {
            this.dialog = dialog;
            this.preview = preview;
        }

        @Override
        public boolean touchDown(InputEvent event, float x, float y, int pointer, KeyCode button) {
            if (button != KeyCode.mouseLeft) return false;

            Seq<DraggableMirrorFilter> mirrors = collectDraggableFilters(dialog);
            if (mirrors.isEmpty()) return false;

            DraggableMirrorFilter.computePreviewRect(preview, mapRect);
            if (mapRect.width <= 0f || mapRect.height <= 0f) return false;

            dragging = pickFilter(mirrors, x, y);
            if (dragging == null) return false;

            updateAxis(dragging, x, y);
            requestPreviewUpdate(true);
            return true;
        }

        @Override
        public void touchDragged(InputEvent event, float x, float y, int pointer) {
            if (dragging == null) return;
            updateAxis(dragging, x, y);
            requestPreviewUpdate(false);
        }

        @Override
        public void touchUp(InputEvent event, float x, float y, int pointer, KeyCode button) {
            if (dragging != null) {
                requestPreviewUpdate(true);
                dragging = null;
            }
        }

        private DraggableMirrorFilter pickFilter(Seq<DraggableMirrorFilter> mirrors, float x, float y) {
            DraggableMirrorFilter nearest = null;
            float nearestDst = Float.MAX_VALUE;

            for (int i = 0; i < mirrors.size; i++) {
                DraggableMirrorFilter filter = mirrors.get(i);
                float axisX = mapRect.x + filter.axisXNorm() * mapRect.width;
                float axisY = mapRect.y + filter.axisYNorm() * mapRect.height;

                axisDir.trnsExact(filter.angle - 90f, 1f);
                float distance = Math.abs((x - axisX) * axisDir.y - (y - axisY) * axisDir.x);

                if (distance < nearestDst) {
                    nearestDst = distance;
                    nearest = filter;
                }
            }

            float threshold = Scl.scl(16f);
            if (nearest != null && nearestDst <= threshold) return nearest;

            if (mirrors.size == 1 && inRectExpanded(x, y, mapRect, threshold)) {
                return mirrors.first();
            }

            return null;
        }

        private void updateAxis(DraggableMirrorFilter filter, float x, float y) {
            float nx = (x - mapRect.x) / mapRect.width;
            float ny = (y - mapRect.y) / mapRect.height;
            filter.setAxisNormalized(nx, ny);
        }

        private void requestPreviewUpdate(boolean force) {
            long now = Time.millis();
            if (!force && now - lastUpdateTime < 33L) return;

            invoke(dialog, updateMethod);
            lastUpdateTime = now;
        }

        private boolean inRectExpanded(float x, float y, Rect rect, float pad) {
            return x >= rect.x - pad && y >= rect.y - pad && x <= rect.x + rect.width + pad && y <= rect.y + rect.height + pad;
        }
    }
}
