package mdtxcompat;

import arc.func.Prov;
import arc.scene.Element;
import arc.scene.ui.layout.Table;
import arc.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class MindustryXOverlayUiBridge implements OverlayUiBridge {
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
            return new WindowHandle(window);
        } catch (Throwable t) {
            disable("MindustryX OverlayUI registerWindow failed; disabling integration.", t);
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
                toggleMethod.invoke(overlayInstance);
            }
        } catch (Throwable t) {
            disable("MindustryX OverlayUI toggle failed; disabling integration.", t);
        }
    }

    private boolean resolveSymbols() {
        if (!available) return false;
        if (resolved) return true;

        try {
            ClassLoader loader = MindustryXOverlayUiBridge.class.getClassLoader();
            Class<?> overlayClass = Class.forName("mindustryX.features.ui.OverlayUI", false, loader);
            instanceField = overlayClass.getField("INSTANCE");
            registerWindowMethod = overlayClass.getMethod("registerWindow", String.class, Table.class);
            getOpenMethod = overlayClass.getMethod("getOpen");
            toggleMethod = overlayClass.getMethod("toggle");
            resolved = true;
            return true;
        } catch (ClassNotFoundException notFound) {
            available = false;
            return false;
        } catch (Throwable t) {
            disable("MindustryX OverlayUI symbols resolve failed; disabling integration.", t);
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
