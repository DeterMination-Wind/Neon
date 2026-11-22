package mdtxcompat;

import arc.func.Prov;
import arc.scene.Element;
import arc.scene.ui.layout.Table;
import arc.util.Log;
import neoncompat.overlay.NeonOverlayBootstrap;
import neoncompat.overlay.OverlayUI;

/** {@link OverlayUiBridge} backed by Neon's own embedded overlay, used when MindustryX is absent. */
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
            boolean hasStoredState = OverlaySettingsCompat.hasStoredWindowState(name);
            OverlayUI.Window window = OverlayUI.INSTANCE.registerWindow(name, table);
            if (availability != null) {
                window.setAvailability(availability);
            }
            WindowHandle handle = new WindowHandle(window);
            if (!hasStoredState) {
                handle.applyDefaultHiddenState();
            }
            handle.submitSizeSeed();
            Log.info("Neon OverlayUI integration: registered embedded window '" + name + "'.");
            return handle;
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

        private void applyDefaultHiddenState() {
            window.getData().setEnabled(false);
        }

        /** Packs the window once when its persisted state has no size; see {@link OverlayWindowSizeSeed}. */
        private void submitSizeSeed() {
            OverlayWindowSizeSeed.submit(this::seedSizeIfMissing);
        }

        private boolean seedSizeIfMissing() {
            try {
                // A restored size (or any other sizing path) already won; nothing to do.
                if (window.getWidth() > 0.5f && window.getHeight() > 0.5f) return true;
                // Wait until the window is actually shown and rebuilt; packing an invisible window
                // would lock in its registration-time skeleton. A detached one can never be packed,
                // and content without a usable pref size cannot improve by waiting.
                if (!window.visible) return false;
                if (window.getScene() == null) return true;
                if (!OverlayWindowSizeSeed.hasContent(window)) return false;
                if (!OverlayWindowSizeSeed.measurable(window)) return true;
                window.pack();
                return true;
            } catch (Throwable t) {
                Log.err("Neon OverlayUI integration: embedded size seed failed; leaving the window as-is.", t);
                return true;
            }
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
