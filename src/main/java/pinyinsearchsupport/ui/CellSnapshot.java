package pinyinsearchsupport.ui;

import arc.scene.ui.layout.Cell;
import arc.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

public final class CellSnapshot{
    private static volatile Field[] fields;
    private static volatile boolean unavailable;
    private final Object[] values;
    private final boolean endRow;

    private CellSnapshot(Object[] values, boolean endRow){
        this.values = values;
        this.endRow = endRow;
    }

    public static CellSnapshot capture(Cell<?> cell){
        Field[] fs = ensure();
        if(fs == null) return null;
        Object[] vals = new Object[fs.length];
        try{
            for(int i = 0; i < fs.length; i++){
                vals[i] = fs[i].get(cell);
            }
        }catch(Throwable t){
            Log.warn("[PinyinSearchSupport] CellSnapshot.capture failed: @", t.getMessage());
            return null;
        }
        return new CellSnapshot(vals, cell.isEndRow());
    }

    public boolean endRow(){ return endRow; }

    public void applyTo(Cell<?> cell){
        Field[] fs = ensure();
        if(fs == null) return;
        try{
            for(int i = 0; i < fs.length; i++){
                fs[i].set(cell, values[i]);
            }
        }catch(Throwable t){
            Log.warn("[PinyinSearchSupport] CellSnapshot.applyTo failed: @", t.getMessage());
        }
    }

    private static Field[] ensure(){
        if(unavailable) return null;
        Field[] cached = fields;
        if(cached != null) return cached;
        synchronized(CellSnapshot.class){
            if(fields != null) return fields;
            try{
                Field[] all = Cell.class.getDeclaredFields();
                java.util.ArrayList<Field> keep = new java.util.ArrayList<>(all.length);
                for(Field f : all){
                    int m = f.getModifiers();
                    if(Modifier.isStatic(m) || Modifier.isFinal(m)) continue;
                    String name = f.getName();
                    // skip back-references and actor — actor is re-set by add()
                    if("element".equals(name) || "actor".equals(name)) continue;
                    if("tableLayout".equals(name) || "table".equals(name)) continue;
                    // skip computed layout positions / internal bookkeeping
                    if("elementX".equals(name) || "elementY".equals(name) ||
                       "elementWidth".equals(name) || "elementHeight".equals(name)) continue;
                    if("endRow".equals(name) || "column".equals(name) || "row".equals(name)) continue;
                    if("cellAboveIndex".equals(name)) continue;
                    if("computedPadTop".equals(name) || "computedPadLeft".equals(name) ||
                       "computedPadBottom".equals(name) || "computedPadRight".equals(name)) continue;
                    f.setAccessible(true);
                    keep.add(f);
                }
                fields = keep.toArray(new Field[0]);
                return fields;
            }catch(Throwable t){
                Log.warn("[PinyinSearchSupport] cannot snapshot Cell fields: @", t.getMessage());
                unavailable = true;
                return null;
            }
        }
    }
}
