package mdtxcompat;

import arc.math.geom.Vec2;
import arc.util.Log;
import mindustryX.features.MarkerType;

public class MindustryXMarkerBridge implements MarkerBridge {
    private boolean available = true;

    @Override
    public boolean isSupported() {
        return available;
    }

    @Override
    public void mark(String text, int tileX, int tileY) {
        if (!available) return;
        try {
            MarkerType.newMarkFromChat(text, new Vec2(tileX, tileY));
        } catch (Throwable t) {
            available = false;
            Log.err("MindustryX marker call failed; disabling integration.", t);
        }
    }
}
