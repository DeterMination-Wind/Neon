package mdtxcompat;

import arc.util.Log;
import mindustry.game.Schematic;

import java.lang.reflect.Method;

public class MindustryXSchematicShareBridge implements SchematicShareBridge {
    private boolean available = true;
    private Method shareSchematicMethod;
    private Method shareClipboardMethod;

    public MindustryXSchematicShareBridge() {
        try {
            Class<?> shareFeature = Class.forName("mindustryX.features.ShareFeature");
            shareSchematicMethod = shareFeature.getMethod("shareSchematic", Schematic.class);
            shareClipboardMethod = shareFeature.getMethod("shareSchematicClipboard", Schematic.class);
        } catch (Throwable t) {
            available = false;
            shareSchematicMethod = null;
            shareClipboardMethod = null;
        }
    }

    @Override
    public boolean isSupported() {
        return available;
    }

    @Override
    public void shareToChat(Schematic schematic) {
        if (!available) return;
        try {
            shareSchematicMethod.invoke(null, schematic);
        } catch (Throwable t) {
            available = false;
            Log.err("MindustryX schematic share failed; disabling integration.", t);
        }
    }

    @Override
    public void shareToClipboard(Schematic schematic) {
        if (!available) return;
        try {
            shareClipboardMethod.invoke(null, schematic);
        } catch (Throwable t) {
            available = false;
            Log.err("MindustryX schematic clipboard share failed; disabling integration.", t);
        }
    }
}
