package mdtxcompat;

import arc.func.Prov;
import arc.scene.Element;
import arc.scene.ui.layout.Table;

final class NoopOverlayUiBridge implements OverlayUiBridge {
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

    @Override
    public boolean isSupported() {
        return false;
    }

    @Override
    public OverlayWindowHandle registerWindow(String name, Table table, Prov<Boolean> availability) {
        return NO_WINDOW;
    }

    @Override
    public void closeEditorIfOpen() {
    }
}
