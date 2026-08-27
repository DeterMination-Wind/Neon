package bektools;

import betterhotkey.BetterHotKeyModX;
import betterprojectoroverlay.BetterProjectorOverlayModX;
import mdtxcompat.MindustryXMarkerBridge;
import mdtxcompat.MindustryXOverlayUiBridge;
import powergridminimap.PowerGridMinimapModX;
import radialbuildmenu.RadialBuildMenuModX;
import serverplayerdatabase.ServerPlayerDataBaseModX;
import stealthpath.StealthPathModX;

/** Neon entry for MindustryX-injected runtimes: selects the X-specific overlay/marker bridges and ModX subclasses. */
public class BekToolsModX extends BekToolsMod{
    public BekToolsModX(){
        super(
            new MindustryXOverlayUiBridge(),
            new MindustryXMarkerBridge(),
            PowerGridMinimapModX::new,
            StealthPathModX::new,
            RadialBuildMenuModX::new,
            ServerPlayerDataBaseModX::new,
            BetterProjectorOverlayModX::new,
            BetterHotKeyModX::new
        );
    }
}
