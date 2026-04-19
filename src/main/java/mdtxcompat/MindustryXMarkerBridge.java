package mdtxcompat;

import arc.math.geom.Vec2;
import arc.util.Log;

import java.lang.reflect.Method;

public class MindustryXMarkerBridge implements MarkerBridge {
    private boolean available = true;
    private boolean resolved;
    private Method markMethod;

    @Override
    public boolean isSupported() {
        return resolveSymbols();
    }

    @Override
    public void mark(String text, int tileX, int tileY) {
        if (!resolveSymbols()) return;
        try {
            markMethod.invoke(null, text, new Vec2(tileX, tileY));
        } catch (Throwable t) {
            available = false;
            resolved = false;
            markMethod = null;
            Log.err("MindustryX marker call failed; disabling integration.", t);
        }
    }

    private boolean resolveSymbols() {
        if (!available) return false;
        if (resolved) return true;
        try {
            ClassLoader loader = MindustryXMarkerBridge.class.getClassLoader();
            Class<?> markerType = Class.forName("mindustryX.features.MarkerType", false, loader);
            markMethod = markerType.getMethod("newMarkFromChat", String.class, Vec2.class);
            resolved = true;
            return true;
        } catch (ClassNotFoundException notFound) {
            available = false;
            return false;
        } catch (Throwable t) {
            available = false;
            markMethod = null;
            Log.err("MindustryX marker symbols resolve failed; disabling integration.", t);
            return false;
        }
    }
}
