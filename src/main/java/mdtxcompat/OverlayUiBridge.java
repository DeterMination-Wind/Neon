package mdtxcompat;

import arc.func.Prov;
import arc.scene.Element;
import arc.scene.ui.layout.Table;

/**
 * Registers floating overlay windows; {@link #AUTO_DETECT} locks onto MindustryX's
 * OverlayUI when present, otherwise Neon's embedded overlay.
 */
public interface OverlayUiBridge {
    OverlayUiBridge UNSUPPORTED = new NoopOverlayUiBridge();
    OverlayUiBridge AUTO_DETECT = new AutoDetectingOverlayUiBridge();

    static OverlayUiBridge autoDetect() {
        return AUTO_DETECT;
    }

    /**
     * Tells the auto-detecting bridge whether Neon's bundled OverlayUI copy (the {@code ocb}
     * sub-module) actually came up this session.
     *
     * <p>The copy carries MindustryX's FQCN and always sits in Neon's jar, so a successful
     * reflective probe is not proof that a real MindustryX is running, nor that the copy was
     * initialized (gear button, {@code Z} key, manager). The entry point sets this from the ocb
     * module's initialization result before the first probe locks the delegate; a real
     * MindustryX runtime still wins through its core marker classes.</p>
     */
    static void setBundledOverlayActive(boolean active){
        AutoDetectingOverlayUiBridge.setBundledOverlayActive(active);
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
