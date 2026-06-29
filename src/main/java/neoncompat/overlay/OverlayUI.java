package neoncompat.overlay;

import arc.Core;
import arc.Graphics.Cursor.SystemCursor;
import arc.func.Boolp;
import arc.func.Cons;
import arc.func.Prov;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.input.KeyCode;
import arc.math.Mathf;
import arc.math.geom.Rect;
import arc.math.geom.Vec2;
import arc.scene.Element;
import arc.scene.Group;
import arc.scene.event.InputEvent;
import arc.scene.event.InputListener;
import arc.scene.event.Touchable;
import arc.scene.ui.ImageButton;
import arc.scene.ui.Label;
import arc.scene.ui.Slider;
import arc.scene.ui.TextButton;
import arc.scene.ui.layout.Cell;
import arc.scene.ui.layout.Scl;
import arc.scene.ui.layout.Stack;
import arc.scene.ui.layout.Table;
import arc.scene.ui.layout.WidgetGroup;
import arc.struct.Seq;
import arc.util.Align;
import arc.util.Log;
import arc.util.Strings;
import arc.util.Tmp;
import arc.util.serialization.Json;
import mindustry.Vars;
import mindustry.gen.Icon;
import mindustry.gen.Tex;
import mindustry.graphics.Pal;
import mindustry.ui.Styles;

import java.util.ArrayList;
import java.util.List;

public class OverlayUI {
    public static final OverlayUI INSTANCE = new OverlayUI();
    private static final Json json = new Json();

    private enum WindowState {
        Stable,
        Dragging,
        EndDrag
    }

    public static class WindowData {
        public boolean enabled = false;
        public boolean pinned = false;
        @Deprecated
        public Rect rect = null;
        public Vec2 center = null;
        public Vec2 size = null;
        public AdsorptionSystem.Constraint constraintX = null;
        public AdsorptionSystem.Constraint constraintY = null;
        public float scale = 1f;

        public WindowData() {
        }

        public WindowData(boolean enabled, boolean pinned, Rect rect, Vec2 center, Vec2 size,
                          AdsorptionSystem.Constraint constraintX, AdsorptionSystem.Constraint constraintY,
                          float scale) {
            this.enabled = enabled;
            this.pinned = pinned;
            this.rect = rect;
            this.center = center;
            this.size = size;
            this.constraintX = constraintX;
            this.constraintY = constraintY;
            this.scale = scale;
        }

        public WindowData copy() {
            return copy(enabled, pinned, rect, center, size, constraintX, constraintY, scale);
        }

        public WindowData copy(boolean enabled, boolean pinned, Rect rect, Vec2 center, Vec2 size,
                               AdsorptionSystem.Constraint constraintX, AdsorptionSystem.Constraint constraintY,
                               float scale) {
            return new WindowData(
                enabled,
                pinned,
                rect == null ? null : new Rect(rect),
                center == null ? null : new Vec2(center),
                size == null ? null : new Vec2(size),
                constraintX == null ? null : new AdsorptionSystem.Constraint(constraintX.axis, constraintX.target, constraintX.type),
                constraintY == null ? null : new AdsorptionSystem.Constraint(constraintY.axis, constraintY.target, constraintY.type),
                scale
            );
        }

        public boolean getEnabled() {
            return enabled;
        }

        public boolean getPinned() {
            return pinned;
        }

        public Rect getRect() {
            return rect;
        }

        public Vec2 getCenter() {
            return center;
        }

        public Vec2 getSize() {
            return size;
        }

        public AdsorptionSystem.Constraint getConstraintX() {
            return constraintX;
        }

        public AdsorptionSystem.Constraint getConstraintY() {
            return constraintY;
        }

        public float getScale() {
            return scale;
        }

        public boolean component1() {
            return enabled;
        }

        public boolean component2() {
            return pinned;
        }

        public Rect component3() {
            return rect;
        }

        public Vec2 component4() {
            return center;
        }

        public Vec2 component5() {
            return size;
        }

        public AdsorptionSystem.Constraint component6() {
            return constraintX;
        }

        public AdsorptionSystem.Constraint component7() {
            return constraintY;
        }

        public float component8() {
            return scale;
        }

        public static WindowData copy$default(WindowData self, boolean enabled, boolean pinned, Rect rect,
                                              Vec2 center, Vec2 size, AdsorptionSystem.Constraint constraintX,
                                              AdsorptionSystem.Constraint constraintY, float scale, int mask,
                                              Object marker) {
            return self.copy(
                (mask & 1) != 0 ? self.enabled : enabled,
                (mask & 2) != 0 ? self.pinned : pinned,
                (mask & 4) != 0 ? self.rect : rect,
                (mask & 8) != 0 ? self.center : center,
                (mask & 16) != 0 ? self.size : size,
                (mask & 32) != 0 ? self.constraintX : constraintX,
                (mask & 64) != 0 ? self.constraintY : constraintY,
                (mask & 128) != 0 ? self.scale : scale
            );
        }
    }

    public static class WindowSetting {
        private final String name;
        private WindowData value;
        private boolean changed;

        public WindowSetting(String name) {
            this.name = name;
            this.value = load();
        }

        public String getName() {
            return name;
        }

        public String getTitle() {
            String fallback = name.startsWith("overlayUI.") ? name.substring("overlayUI.".length()) : name;
            String key = "settingV2." + name + ".name";
            return Core.bundle == null ? fallback : Core.bundle.get(key, fallback);
        }

        public WindowData getValue() {
            return value;
        }

        public void set(WindowData next) {
            value = next == null ? new WindowData() : next.copy();
            changed = true;
            save(value);
        }

        public boolean changed() {
            boolean out = changed;
            changed = false;
            return out;
        }

        public boolean getEnabled() {
            return value.enabled;
        }

        public void setEnabled(boolean enabled) {
            if (value.enabled == enabled) return;
            WindowData next = value.copy();
            next.enabled = enabled;
            set(next);
        }

        public boolean getPinned() {
            return value.pinned;
        }

        public void setPinned(boolean pinned) {
            if (value.pinned == pinned) return;
            WindowData next = value.copy();
            next.pinned = pinned;
            set(next);
        }

        private WindowData load() {
            if (Core.settings == null || !Core.settings.has(name)) {
                return new WindowData();
            }

            try {
                String raw = Core.settings.getString(name, null);
                if (raw == null || raw.isEmpty()) return new WindowData();
                WindowData data = json.fromJson(WindowData.class, raw);
                return data == null ? new WindowData() : data;
            } catch (Throwable t) {
                Log.err("[Neon Embedded Overlay] failed to load window setting: @", name);
                Log.err(t);
                return new WindowData();
            }
        }

        private void save(WindowData data) {
            if (Core.settings == null) return;
            try {
                Core.settings.put(name, json.toJson(data));
            } catch (Throwable t) {
                Log.err("[Neon Embedded Overlay] failed to save window setting: @", name);
                Log.err(t);
            }
        }

        public Table buildUI() {
            Table table = new Table();
            table.add(getTitle()).width(148f).padRight(8f).ellipsis(true).left();

            Label pos = new Label("", Styles.outlineLabel);
            pos.update(() -> {
                WindowData d = value;
                if (d.center == null) {
                    pos.setText("[grey][UNUSED]");
                    return;
                }

                StringBuilder sb = new StringBuilder();
                sb.append('[')
                    .append(Math.round(d.center.x))
                    .append(',')
                    .append(Math.round(d.center.y))
                    .append(']');
                if (d.size != null) {
                    sb.append('[')
                        .append(Math.round(d.size.x))
                        .append('x')
                        .append(Math.round(d.size.y))
                        .append(']');
                }
                pos.setText(sb.toString());
            });
            table.add(pos).expandX().left();

            ImageButton.ImageButtonStyle toggle = new ImageButton.ImageButtonStyle(Styles.clearNonei);
            toggle.imageUpColor = Color.white;
            toggle.imageCheckedColor = Pal.accent;
            toggle.imageDisabledColor = Color.darkGray;

            ImageButton eye = table.button(Icon.eyeSmall, toggle, Vars.iconSmall, () -> setEnabled(!getEnabled())).tooltip(i("toggle")).padRight(4f).get();
            eye.update(() -> eye.setChecked(getEnabled()));

            ImageButton lock = table.button(Icon.lockOpenSmall, toggle, Vars.iconSmall, () -> setPinned(!getPinned())).tooltip(i("lock")).padRight(4f).get();
            ImageButton.ImageButtonStyle lockStyle = new ImageButton.ImageButtonStyle(lock.getStyle());
            lockStyle.imageUp = Icon.lockOpenSmall;
            lockStyle.imageChecked = Icon.lockSmall;
            lock.setStyle(lockStyle);
            lock.update(() -> {
                boolean pinned = getPinned();
                lock.setChecked(pinned);
                lock.getImage().setDrawable(pinned ? Icon.lockSmall : Icon.lockOpenSmall);
            });

            ImageButton scale = table.button(Icon.resizeSmall, toggle, Vars.iconSmall, () -> {
                OverlayUI.INSTANCE.showFloatSettingsPanel(panel -> {
                    Label label = new Label("", Styles.outlineLabel);
                    label.update(() -> label.setText(i("scale") + ": " + Strings.fixed(value.scale, 1) + "x"));
                    panel.add(label).center().row();

                    Slider slider = new Slider(0.2f, 3f, 0.1f, false);
                    slider.setValue(value.scale);
                    slider.changed(() -> {
                        WindowData next = value.copy();
                        next.scale = slider.getValue();
                        set(next);
                    });
                    slider.update(() -> slider.setValue(value.scale));
                    panel.add(slider).width(220f).row();

                    ImageButton reset = panel.button(Icon.undo, Styles.clearNonei, () -> {
                        WindowData next = value.copy();
                        next.scale = 1f;
                        set(next);
                    }).padTop(4f).get();
                    reset.update(() -> reset.setDisabled(Mathf.equal(value.scale, 1f)));
                    panel.row();
                });
            }).tooltip(i("scale")).padRight(4f).get();
            scale.update(() -> scale.setChecked(!Mathf.equal(value.scale, 1f)));

            table.row();
            if (value.constraintX != null) {
                table.add();
                table.add("X: " + value.constraintX.type.name() + " to [" + value.constraintX.target + "]").colspan(Math.max(table.getColumns() - 1, 1)).left().row();
            }
            if (value.constraintY != null) {
                table.add();
                table.add("Y: " + value.constraintY.type.name() + " to [" + value.constraintY.target + "]").colspan(Math.max(table.getColumns() - 1, 1)).left().row();
            }
            return table;
        }
    }

    public static class Window extends Table {
        public final Table table;
        public final WindowSetting data;
        public boolean autoHeight = false;
        public boolean resizable = false;
        public Prov<Boolean> availability = () -> true;
        public final List<Object> settings = new ArrayList<>();

        private WindowState state = WindowState.Stable;
        private final AdsorptionSystem.Element adsorption;

        public Window(String name, Table table) {
            this.table = table;
            this.data = new WindowSetting("overlayUI." + name);
            this.adsorption = new AdsorptionSystem.Element(name);
            this.name = name;
            settings.add(data);
        }

        public Table getTable() {
            return table;
        }

        public WindowSetting getData() {
            return data;
        }

        public List<Object> getSettings() {
            return settings;
        }

        public void setAutoHeight(boolean autoHeight) {
            this.autoHeight = autoHeight;
        }

        public void setResizable(boolean resizable) {
            this.resizable = resizable;
        }

        public void setAvailability(Prov<Boolean> availability) {
            this.availability = availability == null ? () -> true : availability;
        }

        public boolean getAutoHeight() {
            return autoHeight;
        }

        public boolean getResizable() {
            return resizable;
        }

        public Prov<Boolean> getAvailability() {
            return availability;
        }

        public void updateVisibility() {
            visible = data.getEnabled() && availability.get() && (OverlayUI.INSTANCE.open || data.getPinned());
            if (!visible) adsorption.remove();
        }

        @Override
        public void act(float delta) {
            updateVisibility();
            super.act(delta);
            if (!visible || !data.getEnabled()) return;

            updateData();
            if (data.changed()) {
                rebuild();
                data.changed();
            }

            if (state == WindowState.Stable) {
                if (!resizable) {
                    setSize(getPrefWidth(), getPrefHeight());
                } else if (autoHeight && getPrefHeight() != height) {
                    height = getPrefHeight();
                }
            }

            applyScale();

            width = Math.min(width, Core.scene.getWidth());
            height = Math.min(height, Core.scene.getHeight());

            if (state == WindowState.Stable && data.getValue().center != null) {
                Vec2 center = data.getValue().center;
                setPosition(center.x, center.y, Align.center);
            }

            keepInStage();

            adsorption.reset(x, y, width, height);
            if (state == WindowState.Stable) {
                adsorption.applyConstraint(data.getValue().constraintX);
                adsorption.applyConstraint(data.getValue().constraintY);
            } else {
                AdsorptionSystem.Constraints c = adsorption.findBestConstraints();
                if (c.constraintX != null) {
                    adsorption.applyConstraint(c.constraintX);
                    OverlayUI.INSTANCE.constraintDrawTask.add(c.constraintX);
                }
                if (c.constraintY != null) {
                    adsorption.applyConstraint(c.constraintY);
                    OverlayUI.INSTANCE.constraintDrawTask.add(c.constraintY);
                }
            }
            setPosition(adsorption.rect.x, adsorption.rect.y);

            if (state == WindowState.EndDrag) {
                state = WindowState.Stable;
                WindowData next = data.getValue().copy();
                next.center = new Vec2(getX(Align.center), getY(Align.center));
                AdsorptionSystem.Constraints c = adsorption.findBestConstraints();
                next.constraintX = c.constraintX;
                next.constraintY = c.constraintY;
                data.set(next);
            }

            unapplyScale();
        }

        private void updateData() {
            WindowData v = data.getValue();
            if (v.rect != null) {
                WindowData next = v.copy();
                next.center = v.rect.getCenter(new Vec2());
                next.size = v.rect.getSize(new Vec2());
                next.rect = null;
                data.set(next);
                v = data.getValue();
            }

            if (v.center == null && parent != null) {
                WindowData next = v.copy();
                next.center = new Vec2(parent.getWidth() / 2f, parent.getHeight() / 2f);
                data.set(next);
                v = data.getValue();
            }

            if (!resizable && v.size != null) {
                WindowData next = v.copy();
                next.size = null;
                data.set(next);
            }
        }

        public void rebuild() {
            clear();

            if (OverlayUI.INSTANCE.open) {
                background(Tex.pane);
                touchable = Touchable.enabled;
                if (resizable) addListener(new ResizeListener());

                Table header = new Table();
                Label title = new Label("", Styles.outlineLabel);
                title.setEllipsis(true);
                title.setWrap(false);
                title.update(() -> title.setText(data.getTitle()));
                header.add(title).minWidth(0f).growX().left();

                header.touchable = Touchable.enabled;
                header.addListener(new DragListener());

                header.defaults().size(Vars.iconMed).pad(2f);
                header.button(Icon.settingsSmall, Styles.cleari, () -> OverlayUI.INSTANCE.showFloatSettingsPanel(panel -> {
                    panel.defaults().minWidth(120f).pad(4f);
                    for (Object setting : settings) {
                        if (setting instanceof WindowSetting) {
                            panel.add(((WindowSetting)setting).buildUI()).growX().padBottom(4f).row();
                        }
                    }
                }));

                ImageButton lock = header.button(Icon.lockOpenSmall, Styles.cleari, () -> data.setPinned(!data.getPinned())).get();
                ImageButton.ImageButtonStyle lockStyle = new ImageButton.ImageButtonStyle(lock.getStyle());
                lockStyle.imageUp = Icon.lockOpenSmall;
                lockStyle.imageChecked = Icon.lockSmall;
                lock.setStyle(lockStyle);
                lock.update(() -> {
                    boolean pinned = data.getPinned();
                    lock.setChecked(pinned);
                    lock.getImage().setDrawable(pinned ? Icon.lockSmall : Icon.lockOpenSmall);
                });

                header.button(Icon.cancelSmall, Styles.cleari, () -> data.setEnabled(false));

                add(header).fillX().row();

                Cell<Table> cell = add(table);
                if (data.getValue().size != null) {
                    Vec2 size = data.getValue().size;
                    cell.maxSize(size.x / Scl.scl(), size.y / Scl.scl());
                }
                pack();

                cell.grow().maxSize(Float.NEGATIVE_INFINITY);
                layout();

                addChild(new Element() {
                    @Override
                    public void act(float delta) {
                        touchable = Touchable.disabled;
                        setBounds(table.x, table.y, table.getWidth(), table.getHeight());
                    }

                    @Override
                    public void draw() {
                        Draw.color();
                        Lines.rect(x, y, width, height);
                    }
                });

            } else {
                background((arc.scene.style.Drawable)null);
                touchable = Touchable.childrenOnly;
                add(table).grow();
                if (data.getValue().size != null) {
                    Vec2 size = data.getValue().size;
                    setSize(size.x, size.y);
                }
            }
        }

        public void dragResize(int side, Vec2 delta) {
            if (Align.isCenterHorizontal(side)) delta.x = 0f;
            if (Align.isCenterVertical(side)) delta.y = 0f;

            if (Align.isLeft(side)) delta.x = -delta.x;
            if (Align.isBottom(side)) delta.y = -delta.y;

            if (width + delta.x < getMinWidth()) delta.x = getMinWidth() - width;
            if (getMaxWidth() > 0f && width + delta.x > getMaxWidth()) delta.x = getMaxWidth() - width;
            if (height + delta.y < getMinHeight()) delta.y = getMinHeight() - height;
            if (getMaxHeight() > 0f && height + delta.y > getMaxHeight()) delta.y = getMaxHeight() - height;

            if (Align.isLeft(side)) x -= delta.x;
            if (Align.isBottom(side)) y -= delta.y;
            setSize(width + delta.x, height + delta.y);
        }

        public void endResize() {
            if (parent == null) return;
            pack();

            WindowData next = data.getValue().copy();
            next.size = new Vec2(table.getWidth(), table.getHeight());
            data.set(next);
        }

        private void applyScale() {
            float scale = data.getValue().scale;
            x = (x + width / 2f) - (width * scale) / 2f;
            y = (y + height / 2f) - (height * scale) / 2f;
            width *= scale;
            height *= scale;
            setScale(1f);
        }

        private void unapplyScale() {
            float scale = data.getValue().scale;
            x = (x + width / 2f) - (width / scale) / 2f;
            y = (y + height / 2f) - (height / scale) / 2f;
            width /= scale;
            height /= scale;

            transform = scale != 1f;
            setScale(scale);
            setOrigin(width / 2f, height / 2f);
        }

        private class DragListener extends InputListener {
            private final Vec2 offset = new Vec2();

            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, KeyCode button) {
                if (Core.app.isMobile() && pointer != 0) return false;
                offset.set(event.stageX, event.stageY).sub(Window.this.x, Window.this.y);
                state = WindowState.Dragging;
                toFront();
                return true;
            }

            @Override
            public void touchDragged(InputEvent event, float x, float y, int pointer) {
                if (Core.app.isMobile() && pointer != 0) return;
                setPosition(event.stageX - offset.x, event.stageY - offset.y);
                applyScale();
                keepInStage();
                unapplyScale();
            }

            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, KeyCode button) {
                if (Core.app.isMobile() && pointer != 0) return;
                state = WindowState.EndDrag;
            }
        }

        private class ResizeListener extends InputListener {
            private final Vec2 last = new Vec2();
            private int resizeSide;

            @Override
            public boolean mouseMoved(InputEvent event, float x, float y) {
                if (event.targetActor != Window.this) {
                    resizeSide = 0;
                } else if (x < table.getX(Align.left)) {
                    resizeSide = Align.left;
                } else if (x > table.getX(Align.right)) {
                    resizeSide = Align.right;
                } else if (y < table.getY(Align.bottom)) {
                    resizeSide = Align.bottom;
                } else if (y > table.getY(Align.top)) {
                    resizeSide = Align.top;
                } else {
                    resizeSide = 0;
                }

                if (Align.isLeft(resizeSide) || Align.isRight(resizeSide)) {
                    Core.graphics.cursor(SystemCursor.horizontalResize);
                } else if (Align.isTop(resizeSide) || Align.isBottom(resizeSide)) {
                    Core.graphics.cursor(SystemCursor.verticalResize);
                } else {
                    Core.graphics.restoreCursor();
                    return false;
                }

                return true;
            }

            @Override
            public void exit(InputEvent event, float x, float y, int pointer, Element toActor) {
                if (Core.app.isMobile() && pointer != 0) return;
                Core.graphics.restoreCursor();
            }

            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, KeyCode button) {
                if (Core.app.isMobile() && pointer != 0) return false;
                mouseMoved(event, x, y);
                if (event.targetActor != Window.this || resizeSide == 0) return false;
                last.set(event.stageX, event.stageY);
                toFront();
                return true;
            }

            @Override
            public void touchDragged(InputEvent event, float x, float y, int pointer) {
                if (Core.app.isMobile() && pointer != 0) return;
                Vec2 delta = Tmp.v1.set(event.stageX, event.stageY).sub(last);
                last.set(event.stageX, event.stageY);
                dragResize(resizeSide, delta);
            }

            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, KeyCode button) {
                if (Core.app.isMobile() && pointer != 0) return;
                endResize();
            }
        }

        private class FixedResizeListener extends InputListener {
            private final int align;
            private final Vec2 last = new Vec2();

            private FixedResizeListener(int align) {
                this.align = align;
            }

            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, KeyCode button) {
                if (Core.app.isMobile() && pointer != 0) return false;
                if (event.targetActor != event.listenerActor) return false;
                last.set(event.stageX, event.stageY);
                toFront();
                return true;
            }

            @Override
            public void touchDragged(InputEvent event, float x, float y, int pointer) {
                if (Core.app.isMobile() && pointer != 0) return;
                Vec2 delta = Tmp.v1.set(event.stageX, event.stageY).sub(last);
                last.set(event.stageX, event.stageY);
                dragResize(align, delta);
            }

            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, KeyCode button) {
                if (Core.app.isMobile() && pointer != 0) return;
                endResize();
            }
        }
    }

    public static class PreferAnyWidth extends Element {
        @Override
        public float getMinWidth() {
            return 0f;
        }

        @Override
        public float getPrefWidth() {
            return width;
        }
    }

    private static class BoolSetting {
        private final String key;
        private final boolean def;

        private BoolSetting(String key, boolean def) {
            this.key = key;
            this.def = def;
        }

        public boolean get() {
            return Core.settings == null ? def : Core.settings.getBool(key, def);
        }

        public void set(boolean value) {
            if (Core.settings != null) Core.settings.put(key, value);
        }
    }

    private final BoolSetting showOverlayButton = new BoolSetting("gameUI.overlayButton", true);
    private final WidgetGroup group = new WidgetGroup();
    final Seq<AdsorptionSystem.Constraint> constraintDrawTask = new Seq<>();

    private Table toggleTable;
    private boolean open;
    private boolean initialized;
    private boolean warnedOverlayButtonHidden;

    private OverlayUI() {
        buildGroup();
        Log.info("[Neon Embedded Overlay] OverlayUI constructed.");
    }

    public boolean getOpen() {
        return open;
    }

    public List<Window> getWindows() {
        List<Window> windows = new ArrayList<>();
        for (Element child : group.getChildren()) {
            if (child instanceof Window) windows.add((Window)child);
        }
        return windows;
    }

    public Window registerWindow(String name, Table table) {
        Window window = new Window(name, table);
        group.addChild(window);
        window.rebuild();
        Log.info("[Neon Embedded Overlay] OverlayUI registered window '" + name + "'. windows=" + getWindows().size());
        debugLogState("after-register-" + name);
        return window;
    }

    public void init() {
        if (Core.scene == null) {
            Log.info("[Neon Embedded Overlay] OverlayUI init skipped: scene is null.");
            debugLogState("init-no-scene");
            return;
        }
        if (initialized && isAttached()) {
            Log.info("[Neon Embedded Overlay] OverlayUI init skipped: already initialized.");
            debugLogState("init-skipped");
            return;
        }

        Log.info("[Neon Embedded Overlay] OverlayUI init add group to scene. initialized=" + initialized
            + ", attached=" + isAttached());
        if (!isAttached()) {
            Core.scene.add(group);
        }
        group.toFront();
        initialized = true;
        debugLogState("init");
    }

    public boolean isInitialized() {
        return initialized;
    }

    public boolean isAttached() {
        return group.parent != null && group.getScene() != null;
    }

    public void toggle() {
        open = !open;
        for (Window window : getWindows()) {
            window.updateVisibility();
            if (window.visible) window.rebuild();
        }
        Log.info("[Neon Embedded Overlay] OverlayUI toggled. open=" + open + ", windows=" + getWindows().size());
        debugLogState("toggle");
    }

    public void showFloatSettingsPanel(Cons<Table> builder) {
        Vec2 mouse = Core.input.mouse().cpy();
        Table panel = new Table(Tex.pane);
        builder.get(panel);
        panel.button("@close", panel::remove).fillX().row();
        panel.touchable = Touchable.enabled;
        panel.addListener(new InputListener() {
            @Override
            public void exit(InputEvent event, float x, float y, int pointer, Element toActor) {
                if (panel.hit(x, y, false) == null) {
                    panel.remove();
                }
            }
        });

        Core.scene.add(panel);
        panel.pack();
        panel.update(() -> {
            if (panel.getWidth() > Core.scene.getWidth() * 0.8f) panel.setWidth(Core.scene.getWidth() * 0.8f);
            if (panel.getHeight() > Core.scene.getHeight() * 0.8f) panel.setHeight(Core.scene.getHeight() * 0.8f);
            panel.setPosition(mouse.x, mouse.y, Align.center);
            panel.keepInStage();
        });
    }

    public static boolean isVisible(Element element) {
        if (element == null || element.getScene() == null) return false;
        Element current = element;
        while (current != null) {
            if (!current.visible) return false;
            current = current.parent;
        }
        return true;
    }

    private void buildGroup() {
        group.name = "overlayUI";
        group.setFillParent(true);
        group.touchable = Touchable.childrenOnly;
        group.setZIndex(99);
        group.visibility = (Boolp)(() -> Vars.state.isMenu() || Vars.ui.hudfrag.shown);

        Table bg = new Table(Styles.black6);
        bg.name = "overlayUI-bg";
        bg.touchable = Touchable.enabled;
        bg.visibility = (Boolp)(() -> open);
        bg.bottom();
        bg.defaults().size(Vars.iconLarge).width(Vars.iconLarge * 1.5f).pad(4f);
        bg.button(Icon.add, Styles.cleari, this::showAddPanel);
        bg.button(Icon.exit, Styles.cleari, this::toggle);
        bg.setFillParent(true);
        group.addChild(bg);

        Table tips = new Table();
        tips.name = "overlayUI-tips";
        tips.touchable = Touchable.disabled;
        tips.visibility = (Boolp)(() -> open);
        tips.left().top();
        tips.add(Core.bundle == null ? "OverlayUI" : Core.bundle.get("overlayUI.tips", "OverlayUI"))
            .pad(8f).left();
        tips.setFillParent(true);
        group.addChild(tips);

        toggleTable = new Table();
        toggleTable.left();
        toggleTable.name = "toggle";
        Table toggleButtonBox = new Table(Styles.black6);
        toggleButtonBox.margin(2f);
        toggleButtonBox.button(Icon.settings, Styles.clearNonei, this::toggle).size(Vars.iconMed);
        toggleTable.add().growY().row();
        toggleTable.add(toggleButtonBox).pad(4f).left().center().row();
        toggleTable.add().growY();
        toggleTable.visibility = (Boolp)(showOverlayButton::get);
        toggleTable.setFillParent(true);
        group.addChild(toggleTable);

        Element drawConstraint = new Element() {
            @Override
            public void draw() {
                Draw.color(Color.red);
                Lines.stroke(4f * Scl.scl());
                for (AdsorptionSystem.Constraint c : constraintDrawTask) {
                    if (c == null) continue;
                    AdsorptionSystem.Element target = c.getTargetPoint();
                    if (target == null) continue;
                    Lines.rect(target.rect);
                }

                Draw.color(Color.yellow);
                Lines.stroke(2f * Scl.scl());
                for (AdsorptionSystem.Constraint c : constraintDrawTask) {
                    if (c == null) continue;
                    AdsorptionSystem.Element target = c.getTargetPoint();
                    if (target == null) continue;

                    float tar = target.computeAnchor(c.axis, c.type.targetAnchor);
                    if (c.axis == AdsorptionSystem.Axis.X) {
                        Lines.dashLine(tar, 0f, tar, height, 64);
                    } else {
                        Lines.dashLine(0f, tar, width, tar, 64);
                    }
                }
                Draw.reset();
                constraintDrawTask.clear();
            }
        };
        drawConstraint.name = "draw-Constraint";
        drawConstraint.touchable = Touchable.disabled;
        drawConstraint.setFillParent(true);
        drawConstraint.update(drawConstraint::toFront);
        group.addChild(drawConstraint);

        initDynamicAdsorption();
        group.update(AdsorptionSystem.INSTANCE::update);
    }

    public void debugLogState(String phase) {
        boolean overlayButton = showOverlayButton.get();
        if (!overlayButton && !warnedOverlayButtonHidden) {
            warnedOverlayButtonHidden = true;
            Log.warn("[Neon Embedded Overlay] gameUI.overlayButton=false; the left OverlayUI gear button is hidden by settings.");
        }

        boolean scene = Core.scene != null;
        boolean groupParent = group.parent != null;
        boolean groupScene = group.getScene() != null;
        boolean groupVisible = group.visible;
        boolean togglePresent = toggleTable != null;
        boolean toggleVisible = togglePresent && toggleTable.visible;
        boolean toggleScene = togglePresent && toggleTable.getScene() != null;
        String toggleBounds = togglePresent
            ? toggleTable.x + "," + toggleTable.y + " " + toggleTable.getWidth() + "x" + toggleTable.getHeight()
            : "missing";
        boolean hudGroup = Vars.ui != null && Vars.ui.hudGroup != null;
        boolean hudfrag = Vars.ui != null && Vars.ui.hudfrag != null;
        boolean hudShown = hudfrag && Vars.ui.hudfrag.shown;
        boolean menu = Vars.state != null && Vars.state.isMenu();
        String sceneSize = scene ? Core.scene.getWidth() + "x" + Core.scene.getHeight() : "none";

        Log.info("[Neon Embedded Overlay] OverlayUI state [" + phase + "]: initialized=" + initialized
            + ", open=" + open
            + ", scene=" + scene
            + ", sceneSize=" + sceneSize
            + ", groupParent=" + groupParent
            + ", groupScene=" + groupScene
            + ", groupVisible=" + groupVisible
            + ", groupChildren=" + group.getChildren().size
            + ", overlayButtonSetting=" + overlayButton
            + ", togglePresent=" + togglePresent
            + ", toggleVisible=" + toggleVisible
            + ", toggleScene=" + toggleScene
            + ", toggleBounds=" + toggleBounds
            + ", hudGroup=" + hudGroup
            + ", hudfrag=" + hudfrag
            + ", hudShown=" + hudShown
            + ", menu=" + menu
            + ", windows=" + getWindows().size());
    }

    private void initDynamicAdsorption() {
        Element minimap = Vars.ui.hudGroup.find("minimap");
        Group minimapParent = minimap == null ? null : minimap.parent;
        if (minimapParent != null) {
            AdsorptionSystem.INSTANCE.addDynamic("minimapFrag", elem -> {
                if (isVisible(minimapParent)) {
                    Rect r = rectForElements(minimapParent.getChildren());
                    if (r != null) elem.reset(r.x, r.y, r.width, r.height);
                }
            });
        } else {
            Log.warn("[Neon Embedded Overlay] cannot find 'minimap' for adsorption");
        }

        Stack statusStack = Vars.ui.hudGroup.find("waves/editor");
        if (statusStack != null) {
            AdsorptionSystem.INSTANCE.addDynamic("statusFrag", elem -> {
                Element visible = null;
                for (Element child : statusStack.getChildren()) {
                    if (child.visible) {
                        visible = child;
                        break;
                    }
                }

                if (visible instanceof Table && isVisible(visible)) {
                    Rect r = rectForElements(((Table)visible).getChildren());
                    if (r != null) elem.reset(r.x, r.y, r.width, r.height);
                }
            });
        } else {
            Log.warn("[Neon Embedded Overlay] cannot init 'statusFrag' for adsorption");
        }
    }

    private void showAddPanel() {
        showFloatSettingsPanel(panel -> {
            panel.add(i("Add Panel")).color(Color.gold).align(Align.center).row();

            Table list = new Table();
            list.defaults().minWidth(120f).fillX().pad(4f);
            Seq<Window> notAvailable = new Seq<>();

            for (Window window : getWindows()) {
                if (!window.availability.get()) {
                    notAvailable.add(window);
                    continue;
                }

                TextButton button = new TextButton(window.data.getTitle());
                button.getLabel().setWrap(false);
                button.update(() -> button.setDisabled(window.data.getEnabled()));
                button.changed(() -> {
                    if (button.isChecked()) {
                        window.data.setEnabled(true);
                        button.setChecked(false);
                    }
                });
                list.add(button).row();
            }

            if (notAvailable.any()) {
                list.add(i("Unavailable panels:")).align(Align.center).row();
                for (Window window : notAvailable) {
                    TextButton disabled = new TextButton(window.data.getTitle());
                    disabled.getLabel().setWrap(false);
                    disabled.setDisabled(true);
                    list.add(disabled).row();
                }
            }

            panel.pane(Styles.smallPane, list).grow().row();
        });
    }

    private Rect rectForElements(Seq<Element> elements) {
        if (elements == null || elements.isEmpty()) return null;

        Element first = elements.first();
        Rect r = new Rect(first.x, first.y, first.getWidth(), first.getHeight());
        for (int i = 1; i < elements.size; i++) {
            Element e = elements.get(i);
            r.merge(Tmp.r2.set(e.x, e.y, e.getWidth(), e.getHeight()));
        }
        return r;
    }

    private static String i(String zh) {
        return Core.bundle == null ? zh : Core.bundle.get(zh, zh);
    }
}
