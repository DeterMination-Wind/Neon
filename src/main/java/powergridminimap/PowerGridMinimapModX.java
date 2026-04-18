package powergridminimap;

import mdtxcompat.MindustryXMarkerBridge;
import mdtxcompat.MindustryXOverlayUiBridge;

public class PowerGridMinimapModX extends PowerGridMinimapMod {
    public PowerGridMinimapModX() {
        super(new MindustryXMarkerBridge(), new MindustryXOverlayUiBridge());
    }
}
