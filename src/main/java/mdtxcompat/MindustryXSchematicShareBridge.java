package mdtxcompat;

import mindustry.game.Schematic;
import mindustryX.features.ShareFeature;

public class MindustryXSchematicShareBridge implements SchematicShareBridge {
    @Override
    public boolean isSupported() {
        return true;
    }

    @Override
    public void shareToChat(Schematic schematic) {
        ShareFeature.shareSchematic(schematic);
    }

    @Override
    public void shareToClipboard(Schematic schematic) {
        ShareFeature.shareSchematicClipboard(schematic);
    }
}
