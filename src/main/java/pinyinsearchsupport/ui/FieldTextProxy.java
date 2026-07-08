package pinyinsearchsupport.ui;

import arc.scene.ui.TextField;
import arc.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.WeakHashMap;

public final class FieldTextProxy{
    private static volatile Field textField;
    private static volatile Field cursorField;
    private static volatile Field selectionStartField;
    private static volatile Field hasSelectionField;
    private static volatile Method updateDisplayTextMethod;
    private static final Map<TextField, State> states = new WeakHashMap<TextField, State>();
    private static volatile boolean unavailable;

    private FieldTextProxy(){}

    public static String swap(TextField field, String to){
        if(!ensure()) return null;
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

            int len = next.length();
            nextCursor = clamp(nextCursor, len);
            nextSelectionStart = clamp(nextSelectionStart, len);
            if(!nextHasSelection || nextCursor == nextSelectionStart){
                nextHasSelection = false;
                nextSelectionStart = nextCursor;
            }

            textField.set(field, next);
            cursorField.setInt(field, nextCursor);
            selectionStartField.setInt(field, nextSelectionStart);
            hasSelectionField.setBoolean(field, nextHasSelection);
            updateDisplayTextMethod.invoke(field);

            if(restoring){
                synchronized(states){
                    states.remove(field);
                }
            }
            return prev;
        }catch(Throwable t){
            Log.warn("[PinyinSearchSupport] FieldTextProxy.swap failed: @", t.getMessage());
            unavailable = true;
            return null;
        }
    }

    private static boolean ensure(){
        if(unavailable) return false;
        if(textField != null && cursorField != null && selectionStartField != null
            && hasSelectionField != null && updateDisplayTextMethod != null) return true;
        synchronized(FieldTextProxy.class){
            if(textField != null && cursorField != null && selectionStartField != null
                && hasSelectionField != null && updateDisplayTextMethod != null) return true;
            try{
                textField = field("text");
                cursorField = field("cursor");
                selectionStartField = field("selectionStart");
                hasSelectionField = field("hasSelection");

                Method update = TextField.class.getDeclaredMethod("updateDisplayText");
                update.setAccessible(true);
                updateDisplayTextMethod = update;
                return true;
            }catch(Throwable t){
                Log.warn("[PinyinSearchSupport] cannot access TextField internals via reflection: @", t.getMessage());
                unavailable = true;
                return false;
            }
        }
    }

    private static Field field(String name) throws NoSuchFieldException{
        Field f = TextField.class.getDeclaredField(name);
        f.setAccessible(true);
        return f;
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
