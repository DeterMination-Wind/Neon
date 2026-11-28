package pinyinsearchsupport.ui;

import arc.scene.ui.TextField;
import arc.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.WeakHashMap;

public final class FieldTextProxy{
    private static final int MAX_RUNTIME_FAILURES = 6;

    private static volatile Field textField;
    private static volatile Field cursorField;
    private static volatile Field selectionStartField;
    private static volatile Field hasSelectionField;
    private static volatile Method updateDisplayTextMethod;
    private static final Map<TextField, State> states = new WeakHashMap<TextField, State>();
    private static volatile boolean unavailable;
    private static volatile int runtimeFailureCount;

    private FieldTextProxy(){}

    public static String swap(TextField field, String to){
        if(field == null || !ensure()) return null;
        try{
            String prev = (String)textField.get(field);
            int cursor = cursorField.getInt(field);
            int selectionStart = selectionStartField.getInt(field);
            boolean hasSelection = hasSelectionField.getBoolean(field);

            String next = to == null ? "" : to;
            State state;
            synchronized(states){
                state = states.get(field);
                if(state == null){
                    state = new State(prev, cursor, selectionStart, hasSelection);
                    states.put(field, state);
                }
            }

            boolean restoring = next.equals(state.text);
            int nextCursor = restoring ? state.cursor : cursor;
            int nextSelectionStart = restoring ? state.selectionStart : selectionStart;
            boolean nextHasSelection = restoring ? state.hasSelection : hasSelection;

            applyInternal(field, next, nextCursor, nextSelectionStart, nextHasSelection);

            if(restoring){
                synchronized(states){
                    states.remove(field);
                }
            }
            runtimeFailureCount = 0;
            return prev;
        }catch(Throwable t){
            synchronized(states){
                states.remove(field);
            }
            recordRuntimeFailure(t);
            return null;
        }
    }

    /**
     * Atomically replaces the field's internal text without firing ChangeEvents,
     * executes {@code action}, and guarantees restoration of the saved text, cursor,
     * and selection state in a {@code finally} block.
     *
     * @return true if the swap succeeded and {@code action} was executed; false otherwise.
     */
    static boolean withSwappedText(TextField field, String temporaryText, Runnable action){
        if(field == null || action == null || !ensure()) return false;

        State saved;
        try{
            String prev = (String)textField.get(field);
            int cursor = cursorField.getInt(field);
            int selectionStart = selectionStartField.getInt(field);
            boolean hasSelection = hasSelectionField.getBoolean(field);
            saved = new State(prev, cursor, selectionStart, hasSelection);
        }catch(Throwable t){
            recordRuntimeFailure(t);
            return false;
        }

        String temp = temporaryText == null ? "" : temporaryText;
        try{
            applyInternal(field, temp, 0, 0, false);
        }catch(Throwable t){
            // Best-effort restore if partial write occurred.
            try{
                applyInternal(field, saved.text, saved.cursor, saved.selectionStart, saved.hasSelection);
            }catch(Throwable ignored){}
            recordRuntimeFailure(t);
            return false;
        }

        try{
            action.run();
            runtimeFailureCount = 0;
            return true;
        }finally{
            try{
                applyInternal(field, saved.text, saved.cursor, saved.selectionStart, saved.hasSelection);
            }catch(Throwable t){
                recordRuntimeFailure(t);
            }
        }
    }

    private static void applyInternal(TextField field, String text, int cursor, int selectionStart, boolean hasSelection) throws Exception{
        String safeText = text == null ? "" : text;
        int len = safeText.length();
        int nextCursor = clamp(cursor, len);
        int nextSelectionStart = clamp(selectionStart, len);
        boolean nextHasSelection = hasSelection && nextCursor != nextSelectionStart;
        if(!nextHasSelection){
            nextSelectionStart = nextCursor;
        }

        textField.set(field, safeText);
        cursorField.setInt(field, nextCursor);
        selectionStartField.setInt(field, nextSelectionStart);
        hasSelectionField.setBoolean(field, nextHasSelection);
        updateDisplayTextMethod.invoke(field);
    }

    private static void recordRuntimeFailure(Throwable t){
        int count = ++runtimeFailureCount;
        if(count <= 3){
            Log.warn("[PinyinSearchSupport] FieldTextProxy operation failed: @", t.getMessage());
        }
        if(count >= MAX_RUNTIME_FAILURES){
            unavailable = true;
        }
    }

    private static boolean ensure(){
        if(unavailable) return false;
        if(textField != null && cursorField != null && selectionStartField != null
            && hasSelectionField != null && updateDisplayTextMethod != null) return true;
        synchronized(FieldTextProxy.class){
            if(unavailable) return false;
            if(textField != null && cursorField != null && selectionStartField != null
                && hasSelectionField != null && updateDisplayTextMethod != null) return true;
            try{
                textField = findField(TextField.class, "text");
                cursorField = findField(TextField.class, "cursor");
                selectionStartField = findField(TextField.class, "selectionStart");
                hasSelectionField = findField(TextField.class, "hasSelection");
                updateDisplayTextMethod = findMethod(TextField.class, "updateDisplayText");
                return true;
            }catch(Throwable t){
                Log.warn("[PinyinSearchSupport] cannot access TextField internals via reflection: @", t.getMessage());
                unavailable = true;
                return false;
            }
        }
    }

    private static Field findField(Class<?> type, String name) throws NoSuchFieldException{
        Class<?> current = type;
        while(current != null && current != Object.class){
            try{
                Field f = current.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            }catch(NoSuchFieldException ignored){
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(type.getName() + "." + name);
    }

    private static Method findMethod(Class<?> type, String name) throws NoSuchMethodException{
        Class<?> current = type;
        while(current != null && current != Object.class){
            try{
                Method m = current.getDeclaredMethod(name);
                m.setAccessible(true);
                return m;
            }catch(NoSuchMethodException ignored){
                current = current.getSuperclass();
            }
        }
        throw new NoSuchMethodException(type.getName() + "." + name + "()");
    }

    private static int clamp(int value, int max){
        if(value < 0) return 0;
        if(value > max) return max;
        return value;
    }

    private static final class State{
        final String text;
        final int cursor;
        final int selectionStart;
        final boolean hasSelection;

        State(String text, int cursor, int selectionStart, boolean hasSelection){
            this.text = text == null ? "" : text;
            this.cursor = cursor;
            this.selectionStart = selectionStart;
            this.hasSelection = hasSelection;
        }
    }
}
