package mdtxcompat;

import arc.func.Prov;
import arc.scene.Element;
import arc.scene.ui.layout.Table;
import arc.util.Log;
import mindustry.Vars;
import mindustry.mod.Mods;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

public class MindustryXOverlayUiBridge implements OverlayUiBridge {
    private static final long missingRetryMillis = 5000L;
    private static final long bindRetryMillis = 250L;

    private enum State {
        UNRESOLVED,
        BINDING,
        READY,
        RETRYABLE_FAILURE,
        UNAVAILABLE_CLASS
    }

    private final Map<String, PendingWindow> pendingWindows = new LinkedHashMap<>();

    private boolean missingLogged;
    private boolean resolvedLogged;
    private boolean settingsMigrationComplete;
    private long lastMissingAt;
    private long nextRetryAt;
    private State state = State.UNRESOLVED;
    private Class<?> overlayClass;
    private Field instanceField;
    private Method registerWindowMethod;
    private Method getOpenMethod;
    private Method toggleMethod;
    private Method windowSetAvailabilityMethod;
    private Method windowSetAutoHeightMethod;
    private Method windowSetResizableMethod;
    private Method windowGetDataMethod;
    private Method dataSetEnabledMethod;
    private Method dataSetPinnedMethod;
    private Method dataGetEnabledMethod;
    private Object overlayInstance;

    @Override
    public boolean isSupported() {
        tryBind("isSupported");
        return state == State.READY;
    }

    @Override
    public OverlayWindowHandle registerWindow(String name, Table table, Prov<Boolean> availability) {
        PendingWindow pending = pendingWindows.get(name);
        if (pending == null) {
            pending = new PendingWindow(name, table, availability);
            pendingWindows.put(name, pending);
        } else {
            pending.table = table;
            pending.availability = availability;
        }
        tryBind("registerWindow:" + name);
        replayPendingWindowsIfReady("registerWindow:" + name);
        return pending.handle;
    }

    @Override
    public void closeEditorIfOpen() {
        tryBind("closeEditorIfOpen");
        if (overlayInstance == null) return;
        try {
            Object open = invoke(getOpenMethod, overlayInstance);
            if (Boolean.TRUE.equals(open)) {
                Log.info("Neon OverlayUI integration: closing OverlayUI editor.");
                invoke(toggleMethod, overlayInstance);
            }
        } catch (Throwable t) {
            markRetryableFailure();
            Log.err("Neon OverlayUI integration: toggle failed; keeping integration retryable.", unwrapReflectionFailure(t));
        }
    }

    private void tryBind(String reason) {
        if (state == State.READY || state == State.BINDING) return;
        if (!shouldRetryNow()) return;
        if (!resolveMetadata()) return;

        state = State.BINDING;
        try {
            Object instance = instanceField.get(null);
            if (instance == null) {
                markRetryableFailure();
                return;
            }
            overlayInstance = instance;
            replayPendingWindows();
            state = State.READY;
            nextRetryAt = 0L;
        } catch (Throwable t) {
            overlayInstance = null;
            markRetryableFailure();
            Log.err("Neon OverlayUI integration: bind failed from " + reason + "; will retry later.", unwrapReflectionFailure(t));
        }
    }

    private boolean resolveMetadata() {
        if (overlayClass != null) return true;

        long now = System.currentTimeMillis();
        if (lastMissingAt != 0L && now - lastMissingAt < missingRetryMillis) return false;

        try {
            Class<?> resolvedClass = LegacyMindustryXGuard.loadMindustryXClass("mindustryX.features.ui.OverlayUI");
            Field resolvedInstanceField = resolvedClass.getField("INSTANCE");
            Method resolvedRegisterWindowMethod = resolvedClass.getMethod("registerWindow", String.class, Table.class);
            Method resolvedGetOpenMethod = resolvedClass.getMethod("getOpen");
            Method resolvedToggleMethod = resolvedClass.getMethod("toggle");

            overlayClass = resolvedClass;
            instanceField = resolvedInstanceField;
            registerWindowMethod = resolvedRegisterWindowMethod;
            getOpenMethod = resolvedGetOpenMethod;
            toggleMethod = resolvedToggleMethod;
            state = State.UNRESOLVED;
            if (!resolvedLogged) {
                resolvedLogged = true;
                Log.info("Neon OverlayUI integration: resolved " + overlayClass.getName() + " with loader " + overlayClass.getClassLoader() + ".");
            }
            return true;
        } catch (ClassNotFoundException notFound) {
            lastMissingAt = now;
            state = State.UNAVAILABLE_CLASS;
            if (!missingLogged) {
                missingLogged = true;
                Log.info("Neon OverlayUI integration: mindustryX.features.ui.OverlayUI not found; install OverlayCompatBridge in this launcher mod directory or use MindustryX to enable overlay windows.");
                logLoadedModSnapshot();
            }
            return false;
        } catch (Throwable t) {
            markRetryableFailure();
            Log.err("Neon OverlayUI integration: symbols resolve failed; keeping integration retryable.", unwrapReflectionFailure(t));
            return false;
        }
    }

    private boolean shouldRetryNow() {
        if (state == State.UNAVAILABLE_CLASS) return true;
        if (state == State.UNRESOLVED) return true;
        return System.currentTimeMillis() >= nextRetryAt;
    }

    private void replayPendingWindows() {
        if (!settingsMigrationComplete) {
            settingsMigrationComplete = !LegacyMindustryXGuard.isMindustryXRuntime()
                || OverlaySettingsCompat.sanitizeNativeWindowSettings() >= 0;
        }
        for (PendingWindow pending : pendingWindows.values()) {
            if (pending.handle.window == null) {
                boolean hasStoredState = OverlaySettingsCompat.hasStoredWindowState(pending.name);
                Object window = invoke(registerWindowMethod, overlayInstance, pending.name, pending.table);
                if (window == null) {
                    throw new IllegalStateException("OverlayUI returned null window for " + pending.name);
                }
                pending.handle.bind(window);
                pending.handle.applyDefaultHiddenState(!hasStoredState);
                Log.info("Neon OverlayUI integration: registered window '" + pending.name + "' via " + window.getClass().getName() + ".");
            }
            pending.handle.applyPendingState();
        }
    }

    private void replayPendingWindowsIfReady(String reason) {
        if (state != State.READY || overlayInstance == null) return;
        try {
            replayPendingWindows();
        } catch (Throwable t) {
            markRetryableFailure();
            Log.err("Neon OverlayUI integration: pending window replay failed from " + reason + "; will retry later.", unwrapReflectionFailure(t));
        }
    }

    private void markRetryableFailure() {
        state = State.RETRYABLE_FAILURE;
        nextRetryAt = System.currentTimeMillis() + bindRetryMillis;
    }

    private Throwable unwrapReflectionFailure(Throwable failure) {
        if (failure instanceof InvocationTargetException && ((InvocationTargetException)failure).getCause() != null) {
            return ((InvocationTargetException)failure).getCause();
        }
        return failure;
    }

    private static void logLoadedModSnapshot() {
        try {
            if (Vars.mods == null) {
                Log.info("Neon OverlayUI integration: Vars.mods is null; cannot inspect loaded mods yet.");
                return;
            }

            Mods.LoadedMod bridge = Vars.mods.locateMod("overlay-compat-bridge");
            if (bridge == null) bridge = Vars.mods.locateMod("overlay-compat-bridge-dev");
            if (bridge == null) {
                Log.info("Neon OverlayUI integration: Vars.mods.locateMod('overlay-compat-bridge'/'overlay-compat-bridge-dev') = null.");
            } else {
                Log.info("Neon OverlayUI integration: located overlay bridge: " + describeMod(bridge));
            }

            StringBuilder related = new StringBuilder();
            StringBuilder enabledJava = new StringBuilder();
            int enabledJavaCount = 0;
            int total = 0;
            for (Mods.LoadedMod mod : Vars.mods.list()) {
                total++;
                String name = mod.name == null ? "" : mod.name;
                String metaName = mod.meta == null || mod.meta.name == null ? "" : mod.meta.name;
                String repo = mod.meta == null || mod.meta.repo == null ? "" : mod.meta.repo;
                String haystack = (name + " " + metaName + " " + repo).toLowerCase();
                if (haystack.contains("overlay") || haystack.contains("compat") || haystack.contains("bridge")) {
                    appendEntry(related, describeMod(mod));
                }
                if (mod.enabled() && mod.isJava()) {
                    enabledJavaCount++;
                    appendEntry(enabledJava, mod.name + "@" + (mod.meta == null ? "?" : mod.meta.version)
                        + "{loader=" + (mod.loader != null) + ", state=" + mod.state + "}");
                }
            }

            Log.info("Neon OverlayUI integration: loaded mods total=" + total + ", enabledJava=" + enabledJavaCount + ".");
            Log.info("Neon OverlayUI integration: overlay/compat/bridge-like mods: " + (related.length() == 0 ? "<none>" : related.toString()));
            Log.info("Neon OverlayUI integration: enabled Java mods: " + (enabledJava.length() == 0 ? "<none>" : enabledJava.toString()));
        } catch (Throwable t) {
            Log.err("Neon OverlayUI integration: failed to inspect loaded mods.", t);
        }
    }

    private static void appendEntry(StringBuilder builder, String value) {
        if (builder.length() > 0) builder.append("; ");
        builder.append(value);
    }

    private static String describeMod(Mods.LoadedMod mod) {
        String metaName = mod.meta == null ? "?" : mod.meta.name;
        String version = mod.meta == null ? "?" : mod.meta.version;
        String repo = mod.meta == null ? "?" : mod.meta.repo;
        return "name=" + mod.name
            + ", metaName=" + metaName
            + ", version=" + version
            + ", repo=" + repo
            + ", enabled=" + mod.enabled()
            + ", java=" + mod.isJava()
            + ", state=" + mod.state
            + ", loader=" + mod.loader
            + ", file=" + mod.file;
    }

    private Object invoke(Method method, Object target, Object... args) {
        try {
            return method.invoke(target, args);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Cannot access MindustryX OverlayUI method: " + method.getName(), e);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) throw (RuntimeException)cause;
            if (cause instanceof Error) throw (Error)cause;
            throw new IllegalStateException("MindustryX OverlayUI method failed: " + method.getName(), cause);
        }
    }

    private Method windowMethod(Object target, String name, Class<?>... parameters) {
        try {
            return target.getClass().getMethod(name, parameters);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("MindustryX OverlayUI method missing: " + name, e);
        }
    }

    private final class PendingWindow {
        private final String name;
        private Table table;
        private Prov<Boolean> availability;
        private final WindowHandle handle;

        private PendingWindow(String name, Table table, Prov<Boolean> availability) {
            this.name = name;
            this.table = table;
            this.availability = availability;
            this.handle = new WindowHandle(this);
        }
    }

    private final class WindowHandle implements OverlayWindowHandle {
        private final PendingWindow pending;
        private Object window;
        private Boolean autoHeight;
        private Boolean resizable;
        private Boolean enabled;
        private Boolean pinned;

        private WindowHandle(PendingWindow pending) {
            this.pending = pending;
        }

        private void bind(Object window) {
            this.window = window;
        }

        private void applyDefaultHiddenState(boolean noStoredState) {
            if (!noStoredState || window == null) return;

            ensureWindowMethods();
            Object data = invoke(windowGetDataMethod, window);
            ensureDataMethods(data);
            invoke(dataSetEnabledMethod, data, false);
        }

        private void ensureWindowMethods() {
            if (window == null) return;
            if (windowSetAvailabilityMethod == null) {
                windowSetAvailabilityMethod = windowMethod(window, "setAvailability", Prov.class);
                windowSetAutoHeightMethod = windowMethod(window, "setAutoHeight", boolean.class);
                windowSetResizableMethod = windowMethod(window, "setResizable", boolean.class);
                windowGetDataMethod = windowMethod(window, "getData");
            }
        }

        private void ensureDataMethods(Object data) {
            if (dataSetEnabledMethod == null) {
                dataSetEnabledMethod = windowMethod(data, "setEnabled", boolean.class);
                dataSetPinnedMethod = windowMethod(data, "setPinned", boolean.class);
                dataGetEnabledMethod = windowMethod(data, "getEnabled");
            }
        }

        private void applyPendingState() {
            if (window == null) return;
            ensureWindowMethods();
            if (pending.availability != null && windowSetAvailabilityMethod != null) {
                invoke(windowSetAvailabilityMethod, window, pending.availability);
            }
            if (autoHeight != null && windowSetAutoHeightMethod != null) {
                invoke(windowSetAutoHeightMethod, window, autoHeight);
            }
            if (resizable != null && windowSetResizableMethod != null) {
                invoke(windowSetResizableMethod, window, resizable);
            }
            if (enabled != null || pinned != null) {
                Object data = invoke(windowGetDataMethod, window);
                ensureDataMethods(data);
                if (enabled != null) invoke(dataSetEnabledMethod, data, enabled);
                if (pinned != null) invoke(dataSetPinnedMethod, data, pinned);
            }
        }

        private void applyPendingStateSafely(String action) {
            try {
                applyPendingState();
            } catch (Throwable t) {
                markRetryableFailure();
                Log.err("Neon OverlayUI integration: " + action + " failed for '" + pending.name
                    + "'; keeping integration retryable.", unwrapReflectionFailure(t));
            }
        }

        @Override
        public void configure(boolean autoHeight, boolean resizable) {
            this.autoHeight = autoHeight;
            this.resizable = resizable;
            tryBind("configure:" + pending.name);
            applyPendingStateSafely("configure");
        }

        @Override
        public void setEnabledAndPinned(boolean enabled, boolean pinned) {
            this.enabled = enabled;
            this.pinned = pinned;
            tryBind("setEnabledAndPinned:" + pending.name);
            applyPendingStateSafely("setEnabledAndPinned");
        }

        @Override
        public Boolean getEnabled() {
            tryBind("getEnabled:" + pending.name);
            if (window == null) return enabled;
            try {
                ensureWindowMethods();
                Object data = invoke(windowGetDataMethod, window);
                ensureDataMethods(data);
                Object value = invoke(dataGetEnabledMethod, data);
                return value instanceof Boolean ? (Boolean)value : null;
            } catch (Throwable t) {
                markRetryableFailure();
                Log.err("Neon OverlayUI integration: getEnabled failed for '" + pending.name
                    + "'; returning cached state.", unwrapReflectionFailure(t));
                return enabled;
            }
        }

        @Override
        public Element asElement() {
            return window instanceof Element ? (Element)window : null;
        }
    }
}
