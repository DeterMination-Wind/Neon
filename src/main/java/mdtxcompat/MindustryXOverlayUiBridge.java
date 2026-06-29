package mdtxcompat;

import arc.func.Prov;
import arc.scene.Element;
import arc.scene.ui.layout.Table;
import arc.util.Log;
import mindustry.Vars;
import mindustry.mod.Mods;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class MindustryXOverlayUiBridge implements OverlayUiBridge {
    private static final long missingRetryMillis = 5000L;

    private static final OverlayWindowHandle NO_WINDOW = new OverlayWindowHandle() {
        @Override
        public void configure(boolean autoHeight, boolean resizable) {
        }

        @Override
        public void setEnabledAndPinned(boolean enabled, boolean pinned) {
        }

        @Override
        public Boolean getEnabled() {
            return null;
        }

        @Override
        public Element asElement() {
            return null;
        }
    };

    private boolean available = true;
    private boolean resolved;
    private boolean missingLogged;
    private boolean resolvedLogged;
    private long lastMissingAt;
    private Field instanceField;
    private Method registerWindowMethod;
    private Method getOpenMethod;
    private Method toggleMethod;

    @Override
    public boolean isSupported() {
        return resolveSymbols();
    }

    @Override
    public OverlayWindowHandle registerWindow(String name, Table table, Prov<Boolean> availability) {
        if (!resolveSymbols()) return NO_WINDOW;
        try {
            Object overlayInstance = instanceField.get(null);
            if (overlayInstance == null) return NO_WINDOW;
            Object window = registerWindowMethod.invoke(overlayInstance, name, table);
            if (window == null) return NO_WINDOW;
            if (availability != null) {
                invokeIfPresent(window, "setAvailability", Prov.class, availability);
            }
            Log.info("Neon OverlayUI integration: registered window '" + name + "' via " + window.getClass().getName() + ".");
            return new WindowHandle(window);
        } catch (Throwable t) {
            disable("Neon OverlayUI integration: registerWindow failed for '" + name + "'; disabling integration.", t);
            return NO_WINDOW;
        }
    }

    @Override
    public void closeEditorIfOpen() {
        if (!resolveSymbols()) return;
        try {
            Object overlayInstance = instanceField.get(null);
            if (overlayInstance == null) return;
            Object open = getOpenMethod.invoke(overlayInstance);
            if (Boolean.TRUE.equals(open)) {
                Log.info("Neon OverlayUI integration: closing OverlayUI editor.");
                toggleMethod.invoke(overlayInstance);
            }
        } catch (Throwable t) {
            disable("Neon OverlayUI integration: toggle failed; disabling integration.", t);
        }
    }

    private boolean resolveSymbols() {
        if (!available) return false;
        if (resolved) return true;

        long now = System.currentTimeMillis();
        if (lastMissingAt != 0L && now - lastMissingAt < missingRetryMillis) return false;

        ClassLoader loader = MindustryXOverlayUiBridge.class.getClassLoader();
        try {
            Class<?> overlayClass = Class.forName("mindustryX.features.ui.OverlayUI", false, loader);
            instanceField = overlayClass.getField("INSTANCE");
            registerWindowMethod = overlayClass.getMethod("registerWindow", String.class, Table.class);
            getOpenMethod = overlayClass.getMethod("getOpen");
            toggleMethod = overlayClass.getMethod("toggle");
            resolved = true;
            if (!resolvedLogged) {
                resolvedLogged = true;
                Log.info("Neon OverlayUI integration: resolved " + overlayClass.getName() + " with loader " + overlayClass.getClassLoader() + ".");
            }
            return true;
        } catch (ClassNotFoundException notFound) {
            lastMissingAt = now;
            if (!missingLogged) {
                missingLogged = true;
                Log.info("Neon OverlayUI integration: mindustryX.features.ui.OverlayUI not found with loader " + loader + "; install OverlayCompatBridge in this launcher mod directory or use MindustryX to enable overlay windows.");
                logLoadedModSnapshot();
            }
            return false;
        } catch (Throwable t) {
            disable("Neon OverlayUI integration: symbols resolve failed; disabling integration.", t);
            return false;
        }
    }

    private void disable(String message, Throwable t) {
        if (!available) return;
        available = false;
        resolved = false;
        instanceField = null;
        registerWindowMethod = null;
        getOpenMethod = null;
        toggleMethod = null;
        Log.err(message, t);
    }

    private static void logLoadedModSnapshot() {
        try {
            if (Vars.mods == null) {
                Log.info("Neon OverlayUI integration: Vars.mods is null; cannot inspect loaded mods yet.");
                return;
            }

            Mods.LoadedMod bridge = Vars.mods.locateMod("overlay-compat-bridge");
            if (bridge == null) {
                Log.info("Neon OverlayUI integration: Vars.mods.locateMod('overlay-compat-bridge') = null.");
            } else {
                Log.info("Neon OverlayUI integration: located overlay-compat-bridge: " + describeMod(bridge));
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

    private static void invokeIfPresent(Object target, String methodName, Class<?> argType, Object arg) {
        try {
            Method m = target.getClass().getMethod(methodName, argType);
            m.invoke(target, arg);
        } catch (NoSuchMethodException ignored) {
        } catch (Throwable ignored) {
        }
    }

    private static Object invokeNoArg(Object target, String methodName) {
        if (target == null) return null;
        try {
            Method m = target.getClass().getMethod(methodName);
            return m.invoke(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void invokeBool(Object target, String methodName, boolean value) {
        if (target == null) return;
        try {
            Method m = target.getClass().getMethod(methodName, boolean.class);
            m.invoke(target, value);
        } catch (Throwable ignored) {
        }
    }

    private static class WindowHandle implements OverlayWindowHandle {
        private final Object window;

        private WindowHandle(Object window) {
            this.window = window;
        }

        @Override
        public void configure(boolean autoHeight, boolean resizable) {
            invokeBool(window, "setAutoHeight", autoHeight);
            invokeBool(window, "setResizable", resizable);
        }

        @Override
        public void setEnabledAndPinned(boolean enabled, boolean pinned) {
            Object data = invokeNoArg(window, "getData");
            invokeBool(data, "setEnabled", enabled);
            invokeBool(data, "setPinned", pinned);
        }

        @Override
        public Boolean getEnabled() {
            Object data = invokeNoArg(window, "getData");
            Object enabled = invokeNoArg(data, "getEnabled");
            return enabled instanceof Boolean ? (Boolean)enabled : null;
        }

        @Override
        public Element asElement() {
            return window instanceof Element ? (Element)window : null;
        }
    }
}
