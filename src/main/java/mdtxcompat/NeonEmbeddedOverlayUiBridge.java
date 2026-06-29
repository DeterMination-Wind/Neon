package mdtxcompat;

import arc.func.Prov;
import arc.scene.Element;
import arc.scene.ui.layout.Table;
import arc.util.Log;
import neoncompat.overlay.NeonOverlayBootstrap;
import neoncompat.overlay.OverlayUI;

final class NeonEmbeddedOverlayUiBridge implements OverlayUiBridge {
    private static final OverlayWindowHandle NO_WINDOW = new OverlayWindowHandle() {
        @Override
        public void configure(boolean autoHeight, boolean resizable) {
        }

        @Override
        public void setEnabledAndPinned(boolean enabled, boolean pinned) {
        }

        @Override
        public Boolean getEnabled() {
            return null;
        }

        @Override
        public Element asElement() {
            return null;
        }
    };

    private boolean available = true;
    private boolean initAttempted;
    private boolean resolvedLogged;

    @Override
    public boolean isSupported() {
        return available && !LegacyMindustryXGuard.isMindustryXRuntime();
    }

    @Override
    public OverlayWindowHandle registerWindow(String name, Table table, Prov<Boolean> availability) {
        if (!ensureReady()) return NO_WINDOW;
        try {
            OverlayUI.Window window = OverlayUI.INSTANCE.registerWindow(name, table);
            if (availability != null) {
                window.setAvailability(availability);
            }
            Log.info("Neon OverlayUI integration: registered embedded window '" + name + "'.");
            return new WindowHandle(window);
        } catch (Throwable t) {
            disable("Neon OverlayUI integration: embedded registerWindow failed for '" + name + "'; disabling integration.", t);
            return NO_WINDOW;
        }
    }

    @Override
    public void closeEditorIfOpen() {
        if (!ensureReady()) return;
        try {
            if (OverlayUI.INSTANCE.getOpen()) {
                Log.info("Neon OverlayUI integration: closing embedded OverlayUI editor.");
                OverlayUI.INSTANCE.toggle();
            }
        } catch (Throwable t) {
            disable("Neon OverlayUI integration: embedded toggle failed; disabling integration.", t);
        }
    }

    private boolean ensureReady() {
        if (!available) return false;
        if (LegacyMindustryXGuard.isMindustryXRuntime()) return false;

        try {
            if (!initAttempted) {
                initAttempted = true;
                NeonOverlayBootstrap.ensureInitialized();
            }
            if (!resolvedLogged) {
                resolvedLogged = true;
                Log.info("Neon OverlayUI integration: using embedded Neon overlay bridge.");
            }
            return true;
        } catch (Throwable t) {
            disable("Neon OverlayUI integration: embedded bridge init failed; disabling integration.", t);
            return false;
        }
    }

    private void disable(String message, Throwable t) {
        if (!available) return;
        available = false;
        Log.err(message, t);
    }

    private static final class WindowHandle implements OverlayWindowHandle {
        private final OverlayUI.Window window;

        private WindowHandle(OverlayUI.Window window) {
            this.window = window;
        }

        @Override
        public void configure(boolean autoHeight, boolean resizable) {
            window.setAutoHeight(autoHeight);
            window.setResizable(resizable);
        }

        @Override
        public void setEnabledAndPinned(boolean enabled, boolean pinned) {
            OverlayUI.WindowSetting data = window.getData();
            data.setEnabled(enabled);
            data.setPinned(pinned);
        }

        @Override
        public Boolean getEnabled() {
            return window.getData().getEnabled();
        }

        @Override
        public Element asElement() {
            return window;
        }
    }
}
