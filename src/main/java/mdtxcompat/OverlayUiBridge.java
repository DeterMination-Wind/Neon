package mdtxcompat;

import arc.func.Prov;
import arc.scene.Element;
import arc.scene.ui.layout.Table;

public interface OverlayUiBridge {
    OverlayUiBridge UNSUPPORTED = new NoopOverlayUiBridge();
    OverlayUiBridge AUTO_DETECT = new AutoDetectingOverlayUiBridge();

    static OverlayUiBridge autoDetect() {
        return AUTO_DETECT;
    }

    boolean isSupported();

    OverlayWindowHandle registerWindow(String name, Table table, Prov<Boolean> availability);

    void closeEditorIfOpen();

    interface OverlayWindowHandle {
        void configure(boolean autoHeight, boolean resizable);

        void setEnabledAndPinned(boolean enabled, boolean pinned);

        Boolean getEnabled();

        Element asElement();
    }
}
