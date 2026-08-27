package mdtxcompat;

import mindustry.game.Schematic;

/** Shares a schematic to chat or clipboard; {@link #UNSUPPORTED} when the runtime has no schematic-share feature. */
public interface SchematicShareBridge {
    SchematicShareBridge UNSUPPORTED = new NoopSchematicShareBridge();

    boolean isSupported();

    void shareToChat(Schematic schematic);

    void shareToClipboard(Schematic schematic);
}
