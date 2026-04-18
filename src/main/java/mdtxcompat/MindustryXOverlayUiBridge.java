package mdtxcompat;

import arc.func.Prov;
import arc.scene.Element;
import arc.scene.ui.layout.Table;
import mindustryX.features.ui.OverlayUI;
import mindustryX.features.ui.OverlayUI.Window;

public class MindustryXOverlayUiBridge implements OverlayUiBridge {
    @Override
    public boolean isSupported() {
        return true;
    }

    @Override
    public OverlayWindowHandle registerWindow(String name, Table table, Prov<Boolean> availability) {
        Window window = OverlayUI.INSTANCE.registerWindow(name, table);
        if (availability != null) {
            window.setAvailability(availability);
        }
        return new WindowHandle(window);
    }

    @Override
    public void closeEditorIfOpen() {
        if (OverlayUI.INSTANCE.getOpen()) {
            OverlayUI.INSTANCE.toggle();
        }
    }

    private static class WindowHandle implements OverlayWindowHandle {
        private final Window window;

        private WindowHandle(Window window) {
            this.window = window;
        }

        @Override
        public void configure(boolean autoHeight, boolean resizable) {
            window.setAutoHeight(autoHeight);
            window.setResizable(resizable);
        }

        @Override
        public void setEnabledAndPinned(boolean enabled, boolean pinned) {
            window.getData().setEnabled(enabled);
            window.getData().setPinned(pinned);
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
