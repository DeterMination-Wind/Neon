package mdtxcompat;

import arc.func.Prov;
import arc.scene.Element;
import arc.scene.ui.layout.Table;

/** Probes for MindustryX once, then locks the delegate: external X bridge if resolvable, embedded Neon overlay otherwise. */
final class AutoDetectingOverlayUiBridge implements OverlayUiBridge {
    private final MindustryXOverlayUiBridge external = new MindustryXOverlayUiBridge();
    private final NeonEmbeddedOverlayUiBridge embedded = new NeonEmbeddedOverlayUiBridge();
    /**
     * Whether Neon's bundled OverlayUI copy (the ocb sub-module) was initialized this session;
     * its class lives in Neon's jar either way, see {@link OverlayUiBridge#setBundledOverlayActive}.
     */
    private static boolean bundledOverlayActive = true;
    private OverlayUiBridge lockedDelegate;

    static void setBundledOverlayActive(boolean active){
        bundledOverlayActive = active;
    }

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
            return external;
        }

        // A resolvable OverlayUI class does not prove a real MindustryX runtime: the bundled copy
        // shares its FQCN and is always in Neon's jar. With the ocb master switch off that copy is
        // never initialized (no gear button / Z key / manager), so only a real MindustryX — its
        // marker classes are the reliable signal, the same rule ocb uses to go dormant — keeps the
        // external bridge; everything else falls back to the frozen embedded copy.
        if (!bundledOverlayActive && !LegacyMindustryXGuard.hasMindustryXRuntimeMarkers()) {
            return embedded;
        }

        if (external.isSupported()) {
            return external;
        }

        return embedded;
    }
}
