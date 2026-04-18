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
    private Object overlayInstance;
    private Method registerWindowMethod;
    private Method getOpenMethod;
    private Method toggleMethod;

    public MindustryXOverlayUiBridge() {
        try {
            Class<?> overlayClass = Class.forName("mindustryX.features.ui.OverlayUI");
            Field instanceField = overlayClass.getField("INSTANCE");
            overlayInstance = instanceField.get(null);
            registerWindowMethod = overlayClass.getMethod("registerWindow", String.class, Table.class);
            getOpenMethod = overlayClass.getMethod("getOpen");
            toggleMethod = overlayClass.getMethod("toggle");
        } catch (Throwable t) {
            available = false;
            overlayInstance = null;
            registerWindowMethod = null;
            getOpenMethod = null;
            toggleMethod = null;
        }
    }

    @Override
    public boolean isSupported() {
        return available;
    }

    @Override
    public OverlayWindowHandle registerWindow(String name, Table table, Prov<Boolean> availability) {
        if (!available) return NO_WINDOW;
        try {
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
        if (!available) return;
        try {
            Object open = getOpenMethod.invoke(overlayInstance);
            if (Boolean.TRUE.equals(open)) {
                toggleMethod.invoke(overlayInstance);
            }
        } catch (Throwable t) {
            disable("MindustryX OverlayUI toggle failed; disabling integration.", t);
        }
    }

    private void disable(String message, Throwable t) {
        if (!available) return;
        available = false;
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
