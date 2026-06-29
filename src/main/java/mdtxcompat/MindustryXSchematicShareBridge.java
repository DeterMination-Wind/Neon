package mdtxcompat;

import arc.util.Log;
import mindustry.game.Schematic;

import java.lang.reflect.Method;

public class MindustryXSchematicShareBridge implements SchematicShareBridge {
    private boolean available = true;
    private boolean resolved;
    private Method shareSchematicMethod;
    private Method shareClipboardMethod;

    @Override
    public boolean isSupported() {
        return resolveSymbols();
    }

    @Override
    public void shareToChat(Schematic schematic) {
        if (!resolveSymbols()) return;
        try {
            shareSchematicMethod.invoke(null, schematic);
        } catch (Throwable t) {
            available = false;
            resolved = false;
            shareSchematicMethod = null;
            shareClipboardMethod = null;
            Log.err("MindustryX schematic share failed; disabling integration.", t);
        }
    }

    @Override
    public void shareToClipboard(Schematic schematic) {
        if (!resolveSymbols()) return;
        try {
            shareClipboardMethod.invoke(null, schematic);
        } catch (Throwable t) {
            available = false;
            resolved = false;
            shareSchematicMethod = null;
            shareClipboardMethod = null;
            Log.err("MindustryX schematic clipboard share failed; disabling integration.", t);
        }
    }

    private boolean resolveSymbols() {
        if (!available) return false;
        if (resolved) return true;
        try {
            Class<?> shareFeature = LegacyMindustryXGuard.loadMindustryXClass("mindustryX.features.ShareFeature");
            shareSchematicMethod = shareFeature.getMethod("shareSchematic", Schematic.class);
            shareClipboardMethod = shareFeature.getMethod("shareSchematicClipboard", Schematic.class);
            resolved = true;
            return true;
        } catch (ClassNotFoundException notFound) {
            available = false;
            return false;
        } catch (Throwable t) {
            available = false;
            shareSchematicMethod = null;
            shareClipboardMethod = null;
            Log.err("MindustryX schematic share symbols resolve failed; disabling integration.", t);
            return false;
        }
    }
}
