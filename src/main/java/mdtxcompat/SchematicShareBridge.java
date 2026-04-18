package mdtxcompat;

import mindustry.game.Schematic;

public interface SchematicShareBridge {
    SchematicShareBridge UNSUPPORTED = new NoopSchematicShareBridge();

    boolean isSupported();

    void shareToChat(Schematic schematic);

    void shareToClipboard(Schematic schematic);
}
