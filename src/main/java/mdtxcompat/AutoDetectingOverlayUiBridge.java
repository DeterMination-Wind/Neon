package mdtxcompat;

import arc.func.Prov;
import arc.scene.Element;
import arc.scene.ui.layout.Table;

final class AutoDetectingOverlayUiBridge implements OverlayUiBridge {
    private final MindustryXOverlayUiBridge external = new MindustryXOverlayUiBridge();
    private final NeonEmbeddedOverlayUiBridge embedded = new NeonEmbeddedOverlayUiBridge();
    private OverlayUiBridge lockedDelegate;

    @Override
    public boolean isSupported() {
        OverlayUiBridge delegate = lockedDelegate;
        if (delegate != null) return delegate.isSupported();
        return probeDelegate().isSupported();
    }

    @Override
    public OverlayWindowHandle registerWindow(String name, Table table, Prov<Boolean> availability) {
        return lockDelegate().registerWindow(name, table, availability);
    }

    @Override
    public void closeEditorIfOpen() {
        lockDelegate().closeEditorIfOpen();
    }

    private OverlayUiBridge lockDelegate() {
        OverlayUiBridge current = lockedDelegate;
        if (current != null) return current;

        current = probeDelegate();
        lockedDelegate = current;
        return current;
    }

    private OverlayUiBridge probeDelegate() {
        if (LegacyMindustryXGuard.isMindustryXRuntime()) {
            return OverlayUiBridge.UNSUPPORTED;
        }

        if (external.isSupported()) {
            return external;
        }

        return embedded;
    }
}
