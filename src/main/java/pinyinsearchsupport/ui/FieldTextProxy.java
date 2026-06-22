package pinyinsearchsupport.ui;

import arc.scene.ui.TextField;
import arc.util.Log;

import java.lang.reflect.Field;

public final class FieldTextProxy{
    private static volatile Field textField;
    private static volatile boolean unavailable;

    private FieldTextProxy(){}

    public static String swap(TextField field, String to){
        Field f = ensure();
        if(f == null) return null;
        try{
            String prev = (String)f.get(field);
            f.set(field, to == null ? "" : to);
            return prev;
        }catch(Throwable t){
            Log.warn("[PinyinSearchSupport] FieldTextProxy.swap failed: @", t.getMessage());
            unavailable = true;
            return null;
        }
    }

    private static Field ensure(){
        if(unavailable) return null;
        Field f = textField;
        if(f != null) return f;
        synchronized(FieldTextProxy.class){
            if(textField != null) return textField;
            try{
                Field t = TextField.class.getDeclaredField("text");
                t.setAccessible(true);
                textField = t;
                return t;
            }catch(Throwable t){
                Log.warn("[PinyinSearchSupport] cannot access TextField.text via reflection: @", t.getMessage());
                unavailable = true;
                return null;
            }
        }
    }
}
