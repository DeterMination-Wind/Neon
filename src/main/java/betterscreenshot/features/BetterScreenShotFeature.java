package betterscreenshot.features;

import arc.Core;
import arc.Events;
import arc.func.Prov;
import arc.graphics.Color;
import arc.input.KeyBind;
import arc.input.KeyCode;
import arc.math.Mathf;
import arc.math.geom.Rect;
import arc.scene.event.Touchable;
import arc.scene.ui.Label;
import arc.scene.ui.TextButton;
import arc.scene.ui.layout.Table;
import arc.util.Interval;
import arc.util.Strings;
import betterscreenshot.core.Screenshot;
import mindustry.game.EventType;
import mindustry.graphics.Pal;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.SettingsMenuDialog;

import java.lang.reflect.Method;

import static mindustry.Vars.state;
import static mindustry.Vars.ui;
import static mindustry.Vars.world;

public class BetterScreenShotFeature {

    private static final String keyEnabled = "bss-enabled";
    private static final String keyResolution = "bss-resolution";
    private static final String keyShowProgress = "bss-show-progress";

    private static final String overlayWindowName = "better-screenshot";
    private static final String panelName = "betterscreenshot-panel";

    private static final Interval interval = new Interval(3);
    private static final int idSettings = 0;
    private static final int idAttach = 1;
    private static final int idSize = 2;

    private static final float settingsRefreshTime = 0.25f;
    private static final float attachRefreshTime = 1f;
    private static final float sizeRefreshTime = 0.2f;

    private static final MindustryXOverlayUI xOverlayUi = new MindustryXOverlayUI();

    private static boolean inited;
    private static boolean keybindsRegistered;

    private static boolean enabled;
    private static boolean showProgress;
    private static int resolutionPercent;

    private static boolean capturing;
    private static int lastProgressPercent = -1;

    private static KeyBind keybindCapture;
    private static Table panel;
    private static Label sizeLabel;
    private static Label statusLabel;
    private static TextButton captureButton;

    private static Object xWindow;
    private static boolean hostedByOverlayUI;
    private static boolean lastOverlayEnabled;

    public static void init() {
        if (inited) return;
        inited = true;

        applyDefaults();

        Events.on(EventType.ClientLoadEvent.class, e -> {
            registerKeybinds();
            refreshSettings();
            ensurePanelBuilt();
            ensurePanelAttached();
            updateSizeHint();
            updateCaptureButton();
        });

        Events.on(EventType.WorldLoadEvent.class, e -> updateSizeHint());

        Events.run(EventType.Trigger.update, () -> {
            if (interval.check(idSettings, settingsRefreshTime)) refreshSettings();
            if (interval.check(idAttach, attachRefreshTime)) ensurePanelAttached();
            if (interval.check(idSize, sizeRefreshTime)) updateSizeHint();
            handleCaptureHotkey();
        });
    }

    public static void buildSettings(SettingsMenuDialog.SettingsTable table) {
        table.checkPref(keyEnabled, true);
        table.sliderPref(keyResolution, 100, 25, 400, 25, i -> i + "%");
        table.checkPref(keyShowProgress, true);
        refreshSettings();
        updateSizeHint();
    }

    private static void applyDefaults() {
        Core.settings.defaults(keyEnabled, true);
        Core.settings.defaults(keyResolution, 100);
        Core.settings.defaults(keyShowProgress, true);
    }

    private static void registerKeybinds() {
        if (keybindsRegistered) return;
        keybindsRegistered = true;
        keybindCapture = KeyBind.add("bss-capture", KeyCode.f8, "betterscreenshot");
    }

    private static void refreshSettings() {
        enabled = Core.settings.getBool(keyEnabled, true);
        resolutionPercent = Mathf.clamp(Core.settings.getInt(keyResolution, 100), 25, 400);
        showProgress = Core.settings.getBool(keyShowProgress, true);

        if (xWindow != null && lastOverlayEnabled != enabled) {
            xOverlayUi.setEnabledAndPinned(xWindow, enabled, enabled);
            lastOverlayEnabled = enabled;
        }

        updateCaptureButton();
    }

    private static void ensurePanelBuilt() {
        if (panel != null) return;

        panel = new Table(Styles.black6);
        panel.name = panelName;
        panel.touchable = Touchable.childrenOnly;
        panel.margin(8f);
        panel.left();
        panel.defaults().left().growX().padBottom(4f);

        Label title = new Label(Core.bundle.get("bss.window.title"));
        title.setColor(Pal.accent);
        panel.add(title).row();

        sizeLabel = new Label("");
        sizeLabel.setWrap(true);
        panel.add(sizeLabel).width(280f).row();

        statusLabel = new Label(Core.bundle.get("bss.status.ready"));
        statusLabel.setColor(Color.lightGray);
        statusLabel.setWrap(true);
        panel.add(statusLabel).width(280f).row();

        captureButton = new TextButton(Core.bundle.get("bss.button.capture"), Styles.defaultt);
        captureButton.clicked(() -> requestCapture("button"));
        panel.add(captureButton).height(40f).padTop(4f).row();

        panel.update(() -> {
            if (hostedByOverlayUI) return;
            if (panel.parent == null) return;

            boolean show = enabled && state != null && state.isGame() && ui != null && ui.hudfrag != null && ui.hudfrag.shown;
            panel.visible = show;
            if (!show) return;

            panel.pack();
            float margin = 12f;
            panel.setPosition(Core.scene.getWidth() - panel.getWidth() - margin, Core.scene.getHeight() - panel.getHeight() - margin);
        });
    }

    private static void ensurePanelAttached() {
        if (panel == null || ui == null || ui.hudGroup == null) return;

        if (xOverlayUi.isInstalled()) {
            if (xWindow == null) {
                xWindow = xOverlayUi.registerWindow(overlayWindowName, panel, () -> state != null && state.isGame());
                if (xWindow != null) {
                    xOverlayUi.tryConfigureWindow(xWindow, false, true);
                    lastOverlayEnabled = !enabled;
                }
            }

            if (xWindow != null) {
                hostedByOverlayUI = true;
                panel.touchable = Touchable.childrenOnly;
                if (lastOverlayEnabled != enabled) {
                    xOverlayUi.setEnabledAndPinned(xWindow, enabled, enabled);
                    lastOverlayEnabled = enabled;
                }
                return;
            }
        }

        hostedByOverlayUI = false;
        panel.touchable = Touchable.childrenOnly;

        if (panel.parent != ui.hudGroup) {
            panel.remove();
            ui.hudGroup.addChild(panel);
            panel.toFront();
        }
    }

    private static void updateSizeHint() {
        if (sizeLabel == null) return;

        if (state == null || !state.isGame() || world == null) {
            sizeLabel.setText(Core.bundle.get("bss.size.nogame"));
            return;
        }

        Screenshot.ShotEstimate estimate = Screenshot.estimateWorld(getWorldBounds(), getResolution());
        String rawMB = Strings.autoFixed(estimate.rawBytes / 1024f / 1024f, 1);
        sizeLabel.setText(Core.bundle.format("bss.size.value", estimate.width, estimate.height, rawMB, estimate.totalChunks));
    }

    private static void handleCaptureHotkey() {
        if (!enabled || capturing) return;
        if (state == null || !state.isGame() || world == null) return;
        if (keybindCapture == null) return;
        if (Core.input.keyTap(keybindCapture)) requestCapture("hotkey");
    }

    private static void requestCapture(String trigger) {
        if (!enabled) return;
        if (capturing) {
            if (ui != null) ui.showInfoFade(Core.bundle.get("bss.toast.busy"));
            return;
        }
        if (state == null || !state.isGame() || world == null) {
            if (ui != null) ui.showInfoFade(Core.bundle.get("bss.toast.nogame"));
            return;
        }

        Rect bounds = getWorldBounds();
        float resolution = getResolution();
        Screenshot.ShotEstimate estimate = Screenshot.estimateWorld(bounds, resolution);
        String rawMB = Strings.autoFixed(estimate.rawBytes / 1024f / 1024f, 1);

        capturing = true;
        lastProgressPercent = -1;
        updateCaptureButton();
        statusLabel.setText(Core.bundle.format("bss.status.start", estimate.width, estimate.height));

        if (ui != null) {
            ui.showInfoFade(Core.bundle.format("bss.toast.start", estimate.width, estimate.height, rawMB));
        }

        try {
            Screenshot.shotWorld(bounds, resolution, new Screenshot.ProgressListener() {
                @Override
                public void onChunk(int completedChunks, int totalChunks) {
                    if (!showProgress || totalChunks <= 0) return;
                    int pct = Mathf.clamp((int) (completedChunks * 100f / totalChunks), 0, 100);
                    if (pct == lastProgressPercent) return;
                    if (lastProgressPercent != -1 && pct - lastProgressPercent < 3 && pct < 100) return;

                    lastProgressPercent = pct;
                    Core.app.post(() -> {
                        if (statusLabel != null) {
                            statusLabel.setText(Core.bundle.format("bss.status.progress", pct, completedChunks, totalChunks));
                        }
                    });
                }

                @Override
                public void onWriteStart(arc.files.Fi file) {
                    Core.app.post(() -> {
                        if (statusLabel != null) {
                            statusLabel.setText(Core.bundle.format("bss.status.writing", file.name()));
                        }
                    });
                }

                @Override
                public void onDone(arc.files.Fi file, int width, int height, long elapsedMs) {
                    Core.app.post(() -> {
                        capturing = false;
                        updateCaptureButton();
                        if (statusLabel != null) {
                            statusLabel.setText(Core.bundle.format("bss.status.done", file.name(), Strings.autoFixed(elapsedMs / 1000f, 2)));
                        }
                    });
                }

                @Override
                public void onError(Throwable error) {
                    Core.app.post(() -> {
                        capturing = false;
                        updateCaptureButton();
                        if (statusLabel != null) {
                            statusLabel.setText(Core.bundle.get("bss.status.error"));
                        }
                    });
                }
            });
        } catch (Throwable t) {
            capturing = false;
            updateCaptureButton();
            if (statusLabel != null) statusLabel.setText(Core.bundle.get("bss.status.error"));
            if (ui != null) ui.showException(t);
        }
    }

    private static void updateCaptureButton() {
        if (captureButton == null) return;
        captureButton.setText(capturing ? Core.bundle.get("bss.button.capturing") : Core.bundle.get("bss.button.capture"));
        captureButton.setDisabled(capturing || !enabled);
    }

    private static float getResolution() {
        return Math.max(0.25f, resolutionPercent / 100f);
    }

    private static Rect getWorldBounds() {
        return new Rect(0f, 0f, world.unitWidth(), world.unitHeight());
    }

    private static class MindustryXOverlayUI {
        private boolean initialized;
        private boolean installed;

        private Object instance;
        private Method registerWindow;

        private Method setAvailability;
        private Method setResizable;
        private Method setAutoHeight;
        private Method getData;
        private Method setEnabled;
        private Method setPinned;

        private boolean accessorsInitialized;

        boolean isInstalled() {
            if (initialized) return installed;
            initialized = true;

            try {
                installed = mindustry.Vars.mods != null && mindustry.Vars.mods.locateMod("mindustryx") != null;
            } catch (Throwable ignored) {
                installed = false;
            }
            if (!installed) return false;

            try {
                Class<?> c = Class.forName("mindustryX.features.ui.OverlayUI");
                instance = c.getField("INSTANCE").get(null);
                registerWindow = c.getMethod("registerWindow", String.class, Table.class);
                return true;
            } catch (Throwable t) {
                installed = false;
                return false;
            }
        }

        Object registerWindow(String name, Table table, Prov<Boolean> availability) {
            if (!isInstalled()) return null;
            try {
                Object window = registerWindow.invoke(instance, name, table);
                tryInitWindowAccessors(window);
                if (window != null && availability != null && setAvailability != null) {
                    setAvailability.invoke(window, availability);
                }
                return window;
            } catch (Throwable ignored) {
                return null;
            }
        }

        void tryConfigureWindow(Object window, boolean autoHeight, boolean resizable) {
            if (window == null) return;
            try {
                tryInitWindowAccessors(window);
                if (setAutoHeight != null) setAutoHeight.invoke(window, autoHeight);
                if (setResizable != null) setResizable.invoke(window, resizable);
            } catch (Throwable ignored) {
            }
        }

        void setEnabledAndPinned(Object window, boolean enabled, boolean pinned) {
            if (window == null) return;
            try {
                tryInitWindowAccessors(window);
                if (getData == null) return;

                Object data = getData.invoke(window);
                if (data == null) return;

                if (setEnabled != null) setEnabled.invoke(data, enabled);
                if (setPinned != null) setPinned.invoke(data, pinned);
            } catch (Throwable ignored) {
            }
        }

        private void tryInitWindowAccessors(Object window) {
            if (window == null) return;
            if (accessorsInitialized) return;
            accessorsInitialized = true;

            try {
                Class<?> wc = window.getClass();

                try {
                    setAvailability = wc.getMethod("setAvailability", Prov.class);
                } catch (Throwable ignored) {
                    setAvailability = null;
                }

                try {
                    setResizable = wc.getMethod("setResizable", boolean.class);
                } catch (Throwable ignored) {
                    setResizable = null;
                }

                try {
                    setAutoHeight = wc.getMethod("setAutoHeight", boolean.class);
                } catch (Throwable ignored) {
                    setAutoHeight = null;
                }

                try {
                    getData = wc.getMethod("getData");
                } catch (Throwable ignored) {
                    getData = null;
                }

                if (getData != null) {
                    Object data = getData.invoke(window);
                    if (data != null) {
                        Class<?> dc = data.getClass();
                        try {
                            setEnabled = dc.getMethod("setEnabled", boolean.class);
                        } catch (Throwable ignored) {
                            setEnabled = null;
                        }
                        try {
                            setPinned = dc.getMethod("setPinned", boolean.class);
                        } catch (Throwable ignored) {
                            setPinned = null;
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
        }
    }
}
