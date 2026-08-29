package overlaycompat;

import arc.Core;
import arc.Events;
import arc.input.KeyBind;
import arc.input.KeyCode;
import arc.util.Log;
import arc.util.Time;
import mindustry.Vars;
import mindustry.game.EventType.ClientLoadEvent;
import mindustry.game.EventType.Trigger;
import mindustry.input.Binding;
import mindustry.mod.Mod;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class OverlayCompatBridgeMod extends Mod {
    private static final String OVERLAY_UI_CLASS = "mindustryX.features.ui.OverlayUI";
    private static boolean installed;

    private enum OverlayState {
        UNRESOLVED,
        DEFERRED,
        BINDING,
        READY,
        RETRYABLE_FAILURE,
        UNAVAILABLE_CLASS
    }

    private KeyBind overlayBind;
    private OverlayState overlayState = OverlayState.UNRESOLVED;
    private OverlayState lastLoggedState;
    private Class<?> overlayClass;
    private Field instanceField;
    private Method initMethod;
    private Method isAttachedMethod;
    private Method toggleMethod;
    private Method debugLogStateMethod;
    private Object overlayInstance;
    private Throwable lastFailure;
    private long nextRetryAt;

    public OverlayCompatBridgeMod() {
        if (installed) return;
        installed = true;
        Log.info("[OverlayCompatBridge] mod constructed. headless=" + Vars.headless);
        logStateTransition(OverlayState.UNRESOLVED, "mod constructed");
        Events.on(ClientLoadEvent.class, event -> {
            Log.info("[OverlayCompatBridge] ClientLoadEvent. " + runtimeState());
            scheduleInit("client-load");
        });

        Events.run(Trigger.update, this::update);
        scheduleInit("bootstrap");
    }

    /**
     * When a real MindustryX runtime is present it owns mindustryX.features.ui.OverlayUI;
     * this bridge must stay dormant instead of binding its own (bundled) copy.
     * Mirrors the marker checks of Neon's LegacyMindustryXGuard.
     */
    private static boolean isMindustryXRuntime() {
        if ("1".equals(System.getProperty("mdtx.loader"))) return true;
        if (System.getProperty("MDTX-loaded") != null) return true;
        if (Vars.mods != null) {
            for (String name : new String[]{"mindustryx", "mdtx"}) {
                try {
                    if (Vars.mods.locateMod(name) != null) return true;
                } catch (Throwable ignored) {
                }
            }
        }
        return false;
    }

    private void ensureKeybind() {
        if (overlayBind != null) return;
        overlayBind = KeyBind.add("overlayUI", KeyCode.z, "mindustryX");
        Log.info("[OverlayCompatBridge] keybind registered: overlayUI default=Z category=mindustryX");
    }

    private void update() {
        if (Vars.headless || Core.scene == null) return;
        if (isMindustryXRuntime()) return;
        ensureKeybind();
        if (!isOverlayAttached()) {
            initializeOverlay("update");
        }

        if (Core.scene.hasField()) {
            return;
        }
        if (overlayBind == null || !Core.input.keyTap(overlayBind)) return;

        if (Vars.control != null && Vars.control.input != null
            && Core.input.keyTap(Binding.schematicFlipX)
            && !Vars.control.input.selectPlans.isEmpty()) {
            return;
        }

        if (!Core.input.ctrl()) {
            if (!isOverlayAttached()) {
                initializeOverlay("toggle");
            }
            if (overlayState == OverlayState.READY && overlayInstance != null) {
                invoke(toggleMethod, overlayInstance);
            }
        }
    }

    private void scheduleInit(String source) {
        ensureKeybind();
        Time.runTask(1f, () -> initializeOverlay(source));
    }

    private void initializeOverlay(String source) {
        if (Vars.headless) return;
        if (isMindustryXRuntime()) {
            // Reached only once the runtime is safe (scene/HUD ready), i.e. after every
            // mod is registered, so a single permanent dormancy decision is sound here.
            setState(OverlayState.UNAVAILABLE_CLASS, "MindustryX runtime present; bridge stays dormant");
            return;
        }
        if (!shouldRetryNow()) return;
        if (!resolveOverlayMetadata()) return;
        if (!isSafeToBindNow()) {
            setState(OverlayState.DEFERRED, source + " waiting for safe runtime: " + runtimeState());
            return;
        }
        if (overlayState == OverlayState.READY && isOverlayAttached()) return;
        if (overlayState == OverlayState.BINDING) return;

        try {
            setState(OverlayState.BINDING, source + " starting lazy bind");
            overlayInstance = instanceField.get(null);
            if (overlayInstance == null) {
                failRetryable(source + " instance field returned null", null);
                return;
            }
            if (!Boolean.TRUE.equals(invoke(isAttachedMethod, overlayInstance))) {
                Log.info("[OverlayCompatBridge] initializing OverlayUI from " + source + ". " + runtimeState());
                invoke(initMethod, overlayInstance);
            }
            if (!Boolean.TRUE.equals(invoke(isAttachedMethod, overlayInstance))) {
                failRetryable(source + " attached state remained false after init", null);
                return;
            }
            nextRetryAt = 0L;
            lastFailure = null;
            setState(OverlayState.READY, source + " bound real OverlayUI");
            if (debugLogStateMethod != null) {
                invoke(debugLogStateMethod, overlayInstance, "after-init-" + source);
            }
            Log.info("[OverlayCompatBridge] OverlayUI initialized from " + source + ".");
        } catch (Throwable t) {
            overlayInstance = null;
            failRetryable(source + " init failed", unwrapInvocationFailure(t));
            Log.err("[OverlayCompatBridge] OverlayUI init failed from " + source + ".", unwrapInvocationFailure(t));
        }
    }

    private boolean resolveOverlayMetadata() {
        if (overlayClass != null) return true;
        try {
            ClassLoader loader = OverlayCompatBridgeMod.class.getClassLoader();
            overlayClass = Class.forName(OVERLAY_UI_CLASS, false, loader);
            instanceField = overlayClass.getField("INSTANCE");
            initMethod = overlayClass.getMethod("init");
            isAttachedMethod = overlayClass.getMethod("isAttached");
            toggleMethod = overlayClass.getMethod("toggle");
            debugLogStateMethod = overlayClass.getMethod("debugLogState", String.class);
            Log.info("[OverlayCompatBridge] Resolved OverlayUI metadata via loader " + overlayClass.getClassLoader() + ".");
            return true;
        } catch (ClassNotFoundException e) {
            overlayState = OverlayState.UNAVAILABLE_CLASS;
            logStateTransition(OverlayState.UNAVAILABLE_CLASS, "OverlayUI class not found");
            return false;
        } catch (Throwable t) {
            failRetryable("resolveOverlayMetadata failed", unwrapInvocationFailure(t));
            Log.err("[OverlayCompatBridge] failed to resolve OverlayUI metadata.", unwrapInvocationFailure(t));
            return false;
        }
    }

    private boolean isSafeToBindNow() {
        return !Vars.headless
            && Core.graphics != null
            && Core.scene != null
            && Vars.ui != null
            && Vars.ui.hudGroup != null
            && Vars.ui.hudfrag != null
            && Vars.state != null;
    }

    private boolean isOverlayAttached() {
        if (overlayState != OverlayState.READY || overlayInstance == null || isAttachedMethod == null) return false;
        Object attached = invoke(isAttachedMethod, overlayInstance);
        return Boolean.TRUE.equals(attached);
    }

    private boolean shouldRetryNow() {
        if (overlayState == OverlayState.UNAVAILABLE_CLASS) return false;
        if (overlayState == OverlayState.UNRESOLVED || overlayState == OverlayState.DEFERRED) return true;
        return System.currentTimeMillis() >= nextRetryAt;
    }

    private void failRetryable(String reason, Throwable failure) {
        lastFailure = failure;
        nextRetryAt = System.currentTimeMillis() + 250L;
        setState(OverlayState.RETRYABLE_FAILURE, reason);
    }

    private void setState(OverlayState next, String reason) {
        overlayState = next;
        logStateTransition(next, reason);
    }

    private void logStateTransition(OverlayState next, String reason) {
        if (lastLoggedState == next) return;
        lastLoggedState = next;
        Log.info("[OverlayCompatBridge] state=" + next
            + ", reason=" + reason
            + ", retryAt=" + nextRetryAt
            + ", runtime={" + runtimeState() + "}");
        if (lastFailure != null && next == OverlayState.RETRYABLE_FAILURE) {
            Log.err("[OverlayCompatBridge] last failure", lastFailure);
        }
    }

    private static Object invoke(Method method, Object target, Object... args) {
        try {
            return method.invoke(target, args);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Cannot access OverlayUI method: " + method.getName(), e);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) throw (RuntimeException)cause;
            if (cause instanceof Error) throw (Error)cause;
            throw new IllegalStateException("OverlayUI method failed: " + method.getName(), cause);
        }
    }

    private static Throwable unwrapInvocationFailure(Throwable failure) {
        if (failure instanceof InvocationTargetException && ((InvocationTargetException)failure).getCause() != null) {
            return ((InvocationTargetException)failure).getCause();
        }
        return failure;
    }

    private static String runtimeState() {
        boolean scene = Core.scene != null;
        boolean settings = Core.settings != null;
        boolean ui = Vars.ui != null;
        boolean hudGroup = ui && Vars.ui.hudGroup != null;
        boolean hudfrag = ui && Vars.ui.hudfrag != null;
        boolean hudShown = hudfrag && Vars.ui.hudfrag.shown;
        boolean menu = Vars.state != null && Vars.state.isMenu();
        String size = Core.graphics == null ? "unknown" : Core.graphics.getWidth() + "x" + Core.graphics.getHeight();
        return "scene=" + scene
            + ", settings=" + settings
            + ", ui=" + ui
            + ", hudGroup=" + hudGroup
            + ", hudfrag=" + hudfrag
            + ", hudShown=" + hudShown
            + ", menu=" + menu
            + ", graphics=" + size;
    }
}
