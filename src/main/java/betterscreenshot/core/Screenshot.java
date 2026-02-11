package betterscreenshot.core;

import arc.Core;
import arc.files.Fi;
import arc.graphics.*;
import arc.graphics.Camera;
import arc.graphics.Pixmap;
import arc.graphics.PixmapIO;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.*;
import arc.graphics.gl.FrameBuffer;
import arc.graphics.gl.*;
import arc.math.Mathf;
import arc.math.geom.Rect;
import arc.scene.Element;
import arc.scene.Group;
import arc.scene.Scene;
import arc.struct.ObjectMap;
import arc.struct.ObjectMap.Entry;
import arc.util.Reflect;
import arc.util.*;
import arc.util.Threads;
import arc.util.Time;
import mindustry.Vars;

import static arc.Core.app;
import static arc.Core.bundle;
import static mindustry.Vars.ui;

public class Screenshot {

    public interface ProgressListener {
        default void onStart(int width, int height, int totalChunks) {
        }

        default void onChunk(int completedChunks, int totalChunks) {
        }

        default void onWriteStart(Fi file) {
        }

        default void onDone(Fi file, int width, int height, long elapsedMs) {
        }

        default void onError(Throwable error) {
        }
    }

    public static class ShotEstimate {
        public final int width;
        public final int height;
        public final int chunkWidth;
        public final int chunkHeight;
        public final int chunksX;
        public final int chunksY;
        public final int totalChunks;
        public final long rawBytes;

        ShotEstimate(int width, int height, int chunkWidth, int chunkHeight, int chunksX, int chunksY) {
            this.width = width;
            this.height = height;
            this.chunkWidth = chunkWidth;
            this.chunkHeight = chunkHeight;
            this.chunksX = chunksX;
            this.chunksY = chunksY;
            this.totalChunks = chunksX * chunksY;
            this.rawBytes = (long) width * height * 4L;
        }
    }

    public static Pixmap shotElement(Rect area, Element elem) {
        if (area == null) throw new IllegalArgumentException("area cannot be null");
        if (elem == null) throw new IllegalArgumentException("elem cannot be null");

        Group parent = elem.parent;
        int lastIndex = -1;

        if (parent != null) {
            lastIndex = parent.getChildren().indexOf(elem);
            parent.getChildren().remove(lastIndex);
            elem.parent = null;
        }

        Scene scene = Core.scene;
        Camera camera = scene.getCamera();

        float lastX = elem.x;
        float lastY = elem.y;
        float lastCamX = camera.position.x;
        float lastCamY = camera.position.y;
        float lastCamW = camera.width;
        float lastCamH = camera.height;
        int lastScreenW = Core.graphics.getWidth();
        int lastScreenH = Core.graphics.getHeight();

        ObjectMap<Group, Rect> unculled = new ObjectMap<>();
        scene.root.forEach(c -> {
            if (c instanceof Group) {
                Group g = (Group) c;
                if (g.getCullingArea() != null) {
                    unculled.put(g, g.getCullingArea());
                    g.setCullingArea(null);
                }
            }
        });

        int width = Math.max(1, Mathf.ceil(area.width));
        int height = Math.max(1, Mathf.ceil(area.height));
        int x = Mathf.floor(area.x);
        int y = Mathf.floor(area.y);

        int maxTexture = Math.max(1, Gl.maxTextureSize);
        int bufferWidth = Math.min(width, maxTexture);
        int bufferHeight = Math.min(height, maxTexture);
        int chunksX = Mathf.ceil((float) width / bufferWidth);
        int chunksY = Mathf.ceil((float) height / bufferHeight);

        FrameBuffer buffer = null;
        Pixmap result = new Pixmap(width, height);

        try {
            // 将目标元素临时挂到根场景，避免父级裁剪和布局干扰截图区域。
            scene.add(elem);
            scene.act();
            elem.setPosition(0f, 0f);

            scene.getViewport().update(bufferWidth, bufferHeight, false);
            buffer = new FrameBuffer(bufferWidth, bufferHeight);

            for (int ix = 0; ix < chunksX; ix++) {
                for (int iy = 0; iy < chunksY; iy++) {
                    int px = ix * bufferWidth;
                    int py = iy * bufferHeight;
                    int cw = Math.min(bufferWidth, width - px);
                    int ch = Math.min(bufferHeight, height - py);

                    camera.position.set(x + px + cw / 2f, y + py + ch / 2f);
                    camera.width = cw;
                    camera.height = ch;
                    camera.update();

                    // 大图按块渲染：每块都在同一个 FrameBuffer 中完成，减少对象创建。
                    buffer.begin();
                    Gl.viewport(0, 0, cw, ch);
                    Gl.clearColor(0f, 0f, 0f, 0f);
                    Gl.clear(Gl.colorBufferBit);
                    scene.draw();
                    Pixmap pixmap = ScreenUtils.getFrameBufferPixmap(0, 0, cw, ch, true);
                    buffer.end();

                    result.draw(pixmap, px, result.height - py - ch);
                    pixmap.dispose();
                }
            }
        } catch (Throwable t) {
            result.dispose();
            throw t;
        } finally {
            // 不管成功失败都必须恢复现场，否则会影响正常 UI 渲染。
            if (buffer != null) buffer.dispose();

            elem.setPosition(lastX, lastY);
            if (elem.parent != null) elem.remove();

            if (parent != null && lastIndex != -1) {
                parent.getChildren().insert(lastIndex, elem);
                elem.parent = parent;
                Reflect.invoke(Element.class, elem, "setScene", new Object[]{parent.getScene()}, Scene.class);
            }

            for (Entry<Group, Rect> entry : unculled) {
                entry.key.setCullingArea(entry.value);
            }

            camera.position.set(lastCamX, lastCamY);
            camera.width = lastCamW;
            camera.height = lastCamH;
            camera.update();
            scene.getViewport().update(lastScreenW, lastScreenH, true);
        }

        return result;
    }

    public static ShotEstimate estimateWorld(float resolution) {
        return estimateWorld(new Rect(0f, 0f, Vars.world.unitWidth(), Vars.world.unitHeight()), resolution);
    }

    public static ShotEstimate estimateWorld(Rect bounds, float resolution) {
        float safeResolution = Math.max(0.01f, resolution);
        int width = Math.max(1, Mathf.ceil(bounds.width * safeResolution));
        int height = Math.max(1, Mathf.ceil(bounds.height * safeResolution));

        int maxTexture = Math.max(1, Gl.maxTextureSize);
        int chunkWidth = Math.min(width, maxTexture);
        int chunkHeight = Math.min(height, maxTexture);
        int chunksX = Mathf.ceil(width / (float) chunkWidth);
        int chunksY = Mathf.ceil(height / (float) chunkHeight);

        return new ShotEstimate(width, height, chunkWidth, chunkHeight, chunksX, chunksY);
    }

    public static void shotWorld(float resolution) {
        shotWorld(new Rect(0f, 0f, Vars.world.unitWidth(), Vars.world.unitHeight()), resolution, null);
    }

    public static void shotWorld(Rect bounds, float resolution) {
        shotWorld(bounds, resolution, null);
    }

    public static void shotWorld(Rect bounds, float resolution, ProgressListener listener) {
        if (bounds == null) throw new IllegalArgumentException("bounds cannot be null");

        String fileBaseName = "screenshot-" + Time.millis();
        float safeResolution = Math.max(0.01f, resolution);
        ShotEstimate estimate = estimateWorld(bounds, safeResolution);
        long startMs = Time.millis();

        boolean oldDrawWeather = Vars.renderer.drawWeather;
        boolean oldDisableUI = Vars.disableUI;
        Camera lastCamera = Core.camera;

        FrameBuffer buffer = null;
        Pixmap result = new Pixmap(estimate.width, estimate.height);

        try {
            Vars.renderer.drawWeather = false;
            Vars.disableUI = true;
            Core.camera = new Camera();

            if (listener != null) listener.onStart(estimate.width, estimate.height, estimate.totalChunks);

            int completed = 0;
            int reportStep = Math.max(1, estimate.totalChunks / 20);

            // 复用同一个 FrameBuffer 分块渲染整张地图，规避纹理尺寸限制。
            buffer = new FrameBuffer(estimate.chunkWidth, estimate.chunkHeight);
            for (int ix = 0; ix < estimate.chunksX; ix++) {
                for (int iy = 0; iy < estimate.chunksY; iy++) {
                    int pixmapX = ix * estimate.chunkWidth;
                    int pixmapY = iy * estimate.chunkHeight;
                    int chunkWidth = Math.min(estimate.width - pixmapX, estimate.chunkWidth);
                    int chunkHeight = Math.min(estimate.height - pixmapY, estimate.chunkHeight);

                    Core.camera.width = chunkWidth / safeResolution;
                    Core.camera.height = chunkHeight / safeResolution;
                    Core.camera.position.set(
                        bounds.x + (pixmapX + chunkWidth / 2f) / safeResolution,
                        bounds.y + (pixmapY + chunkHeight / 2f) / safeResolution
                    );
                    Core.camera.update();

                    buffer.begin();
                    Gl.viewport(0, 0, chunkWidth, chunkHeight);
                    Gl.clearColor(0f, 0f, 0f, 0f);
                    Gl.clear(Gl.colorBufferBit);

                    Draw.proj(Core.camera);
                    Vars.renderer.draw();
                    Draw.flush();

                    Pixmap pixmap = ScreenUtils.getFrameBufferPixmap(0, 0, chunkWidth, chunkHeight, true);
                    buffer.end();

                    result.draw(pixmap, pixmapX, estimate.height - pixmapY - chunkHeight);
                    pixmap.dispose();

                    completed++;
                    if (listener != null && (completed == 1 || completed == estimate.totalChunks || completed % reportStep == 0)) {
                        listener.onChunk(completed, estimate.totalChunks);
                    }
                }
            }
        } catch (Throwable t) {
            result.dispose();
            if (listener != null) listener.onError(t);
            throw t;
        } finally {
            // 恢复全局渲染状态，避免影响后续游戏帧。
            if (buffer != null) buffer.dispose();
            Vars.renderer.drawWeather = oldDrawWeather;
            Vars.disableUI = oldDisableUI;
            Core.camera = lastCamera;
            if (lastCamera != null) Draw.proj(lastCamera);
        }

        Threads.thread(() -> {
            Fi file = Vars.screenshotDirectory.child(fileBaseName + ".png");
            if (listener != null) {
                app.post(() -> listener.onWriteStart(file));
            }

            try {
                PixmapIO.writePng(file, result);
                long elapsedMs = Time.timeSinceMillis(startMs);
                app.post(() -> {
                    result.dispose();
                    ui.showInfoFade(bundle.format("screenshot", file.toString()));
                    if (listener != null) listener.onDone(file, estimate.width, estimate.height, elapsedMs);
                });
            } catch (Throwable t) {
                app.post(() -> {
                    result.dispose();
                    if (listener != null) listener.onError(t);
                    ui.showException(t);
                });
            }
        });
    }
}
