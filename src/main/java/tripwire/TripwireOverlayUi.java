package tripwire;

import arc.func.Boolp;
import arc.graphics.Color;
import arc.scene.event.Touchable;
import arc.scene.ui.TextButton;
import arc.scene.ui.layout.Table;
import arc.util.Align;
import mdtxcompat.OverlayUiBridge;
import mindustry.graphics.Pal;
import mindustry.ui.Styles;

public final class TripwireOverlayUi {
    private static final String drawWindowName = "tripwire-draw";
    private static final String deleteWindowName = "tripwire-delete";
    private static final float minButtonWidth = 72f;
    private static final float minButtonHeight = 42f;
    private final OverlayUiBridge overlayUi;
    private boolean registered;

    public TripwireOverlayUi(OverlayUiBridge overlayUi) {
        this.overlayUi = overlayUi == null ? OverlayUiBridge.UNSUPPORTED : overlayUi;
    }

    public void init() {
        if (registered || !overlayUi.isSupported()) return;
        registered = true;

        OverlayUiBridge.OverlayWindowHandle draw = overlayUi.registerWindow(
            drawWindowName,
            buttonWindow("绘制", "绘制中", Pal.accent, TripwireInput::isDrawHoldActive, TripwireInput::pressDrawButton),
            TripwireInput::overlayAvailable
        );
        draw.configure(false, true);

        OverlayUiBridge.OverlayWindowHandle delete = overlayUi.registerWindow(
            deleteWindowName,
            buttonWindow("删除", "删除中", Pal.remove, TripwireInput::isDeleteHoldActive, TripwireInput::pressDeleteButton),
            TripwireInput::overlayAvailable
        );
        delete.configure(false, true);
    }

    private static Table buttonWindow(String normalText, String activeText, Color activeColor, Boolp active, Runnable action) {
        ResizableButtonTable table = new ResizableButtonTable();
        table.touchable = Touchable.childrenOnly;
        table.defaults().grow().minSize(minButtonWidth, minButtonHeight);

        TextButton button = table.button("", Styles.flatTogglet, action).grow().minSize(minButtonWidth, minButtonHeight).get();
        button.getLabel().setAlignment(Align.center);
        button.update(() -> {
            boolean activeNow = active.get();
            button.setText(activeNow ? activeText : normalText);
            button.setChecked(activeNow);
            button.getLabel().setColor(activeNow ? activeColor : Color.white);
            button.setDisabled(!TripwireInput.overlayAvailable());
        });
        return table;
    }

    private static class ResizableButtonTable extends Table {
        @Override
        public float getMinWidth() {
            return minButtonWidth;
        }

        @Override
        public float getPrefWidth() {
            return Math.max(minButtonWidth, getWidth());
        }

        @Override
        public float getMinHeight() {
            return minButtonHeight;
        }

        @Override
        public float getPrefHeight() {
            return Math.max(minButtonHeight, getHeight());
        }
    }
}
