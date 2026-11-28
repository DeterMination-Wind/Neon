package pinyinsearchsupport.ui;

import arc.scene.Element;
import arc.scene.ui.layout.Cell;
import arc.scene.ui.layout.Table;
import arc.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;

public final class CellSnapshot{
    private static final int MAX_RUNTIME_FAILURES = 8;

    private static volatile Field[] fields;
    private static volatile boolean unavailable;
    private static volatile int failureCount;

    private final Object[] values;
    private final boolean endRow;

    private CellSnapshot(Object[] values, boolean endRow){
        this.values = values;
        this.endRow = endRow;
    }

    public static CellSnapshot capture(Cell<?> cell){
        if(cell == null) return null;
        Field[] fs = ensure();
        if(fs == null) return null;
        Object[] vals = new Object[fs.length];
        try{
            for(int i = 0; i < fs.length; i++){
                vals[i] = fs[i].get(cell);
            }
            return new CellSnapshot(vals, cell.isEndRow());
        }catch(Throwable t){
            recordFailure("capture", t);
            return null;
        }
    }

    public boolean endRow(){
        return endRow;
    }

    public void applyTo(Cell<?> cell){
        if(cell == null || values == null) return;
        Field[] fs = ensure();
        if(fs == null || fs.length != values.length) return;
        try{
            for(int i = 0; i < fs.length; i++){
                fs[i].set(cell, values[i]);
            }
        }catch(Throwable t){
            recordFailure("applyTo", t);
        }
    }

    private static void recordFailure(String op, Throwable t){
        int count = ++failureCount;
        if(count <= 3){
            Log.warn("[PinyinSearchSupport] CellSnapshot." + op + " failed: @", t.getMessage());
        }
        if(count >= MAX_RUNTIME_FAILURES){
            unavailable = true;
        }
    }

    private static Field[] ensure(){
        if(unavailable) return null;
        Field[] cached = fields;
        if(cached != null) return cached;
        synchronized(CellSnapshot.class){
            if(unavailable) return null;
            if(fields != null) return fields;
            try{
                Field[] all = Cell.class.getDeclaredFields();
                ArrayList<Field> keep = new ArrayList<Field>(all.length);
                for(int i = 0; i < all.length; i++){
                    Field f = all[i];
                    int m = f.getModifiers();
                    if(Modifier.isStatic(m) || Modifier.isFinal(m) || Modifier.isTransient(m)) continue;

                    Class<?> type = f.getType();
                    if(Element.class.isAssignableFrom(type)
                        || Table.class.isAssignableFrom(type)
                        || Cell.class.isAssignableFrom(type)) continue;

                    String name = f.getName();
                    // Skip back-references and actor (re-set by Table.add).
                    if("element".equals(name) || "actor".equals(name)) continue;
                    if("tableLayout".equals(name) || "table".equals(name)) continue;
                    // Skip computed layout positions and internal table bookkeeping.
                    if("elementX".equals(name) || "elementY".equals(name)
                        || "elementWidth".equals(name) || "elementHeight".equals(name)) continue;
                    if("endRow".equals(name) || "column".equals(name) || "row".equals(name)) continue;
                    if("cellAboveIndex".equals(name)) continue;
                    if("computedPadTop".equals(name) || "computedPadLeft".equals(name)
                        || "computedPadBottom".equals(name) || "computedPadRight".equals(name)) continue;

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
