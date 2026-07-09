package betterpolyai.features;

import mindustry.ai.types.BuilderAI;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

final class MindustryXBuilderAiProbe {

    private static final String overlayUiClassName = "mindustryX.features.ui.OverlayUI";
    private static final String auxiliaryToolsClassName = "mindustryX.features.ui.AuxiliaryTools";

    private boolean resolved;
    private boolean available;

    private Class<?> overlayUiClass;
    private Class<?> auxiliaryToolsClass;
    private Field overlayWindowsField;
    private Field selectAiField;

    boolean isBuilderAiSelected() {
        if (!resolve()) return false;

        try {
            Object selected = readSelectedAi();
            return selected instanceof BuilderAI;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean resolve() {
        if (resolved) return available;
        resolved = true;

        try {
            overlayUiClass = Class.forName(overlayUiClassName);
            auxiliaryToolsClass = Class.forName(auxiliaryToolsClassName);
            overlayWindowsField = findField(overlayUiClass, "windows");
            selectAiField = findField(auxiliaryToolsClass, "selectAI");
            available = selectAiField != null;
        } catch (Throwable ignored) {
            available = false;
        }

        return available;
    }

    private Object readSelectedAi() throws IllegalAccessException {
        if (selectAiField == null) return null;

        if (Modifier.isStatic(selectAiField.getModifiers())) {
            Object selected = selectAiField.get(null);
            if (selected != null) return selected;
        }

        if (overlayWindowsField == null) return null;

        Object overlayOwner = Modifier.isStatic(overlayWindowsField.getModifiers()) ? null : findSingletonInstance(overlayUiClass);
        if (!Modifier.isStatic(overlayWindowsField.getModifiers()) && overlayOwner == null) return null;

        Object windows = overlayWindowsField.get(overlayOwner);
        if (windows instanceof Iterable) {
            for (Object window : (Iterable<?>) windows) {
                if (window != null && auxiliaryToolsClass.isInstance(window)) {
                    Object selected = selectAiField.get(window);
                    if (selected != null) return selected;
                }
            }
        } else if (windows instanceof Object[]) {
            for (Object window : (Object[]) windows) {
                if (window != null && auxiliaryToolsClass.isInstance(window)) {
                    Object selected = selectAiField.get(window);
                    if (selected != null) return selected;
                }
            }
        }

        return null;
    }

    private static Field findField(Class<?> type, String name) {
        Class<?> current = type;
        while (current != null) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private static Object findSingletonInstance(Class<?> type) throws IllegalAccessException {
        if (type == null) return null;

        Class<?> current = type;
        while (current != null) {
            for (Field field : current.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers())) continue;
                if (!type.isAssignableFrom(field.getType())) continue;
                field.setAccessible(true);
                Object instance = field.get(null);
                if (instance != null) return instance;
            }
            current = current.getSuperclass();
        }

        return null;
    }
}
