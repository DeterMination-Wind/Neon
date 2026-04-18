package mdtxcompat;

import arc.math.geom.Vec2;
import arc.util.Log;

import java.lang.reflect.Method;

public class MindustryXMarkerBridge implements MarkerBridge {
    private boolean available = true;
    private Method markMethod;

    public MindustryXMarkerBridge() {
        try {
            Class<?> markerType = Class.forName("mindustryX.features.MarkerType");
            markMethod = markerType.getMethod("newMarkFromChat", String.class, Vec2.class);
        } catch (Throwable t) {
            available = false;
            markMethod = null;
        }
    }

    @Override
    public boolean isSupported() {
        return available;
    }

    @Override
    public void mark(String text, int tileX, int tileY) {
        if (!available) return;
        try {
            markMethod.invoke(null, text, new Vec2(tileX, tileY));
        } catch (Throwable t) {
            available = false;
            Log.err("MindustryX marker call failed; disabling integration.", t);
        }
    }
}
