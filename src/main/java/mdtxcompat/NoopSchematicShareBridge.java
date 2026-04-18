package mdtxcompat;

import mindustry.game.Schematic;

final class NoopSchematicShareBridge implements SchematicShareBridge {
    @Override
    public boolean isSupported() {
        return false;
    }

    @Override
    public void shareToChat(Schematic schematic) {
    }

    @Override
    public void shareToClipboard(Schematic schematic) {
    }
}
