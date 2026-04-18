package updatescheme;

import mdtxcompat.MindustryXOverlayUiBridge;
import mdtxcompat.MindustryXSchematicShareBridge;

public final class UpdateSchemeModX extends UpdateSchemeMod {
    public UpdateSchemeModX() {
        super(new MindustryXOverlayUiBridge(), new MindustryXSchematicShareBridge());
    }
}
