package tripwire;

import arc.Events;
import mdtxcompat.OverlayUiBridge;
import mindustry.game.EventType;
import mindustry.gen.Icon;
import mindustry.mod.Mod;
import mindustry.ui.dialogs.SettingsMenuDialog;

import static mindustry.Vars.ui;

public class TripwireMod extends Mod {
    public static boolean bekBundled = false;
    private final TripwireOverlayUi overlayUi;

    public void bekBuildSettings(SettingsMenuDialog.SettingsTable table) {
        TripwireSettings.buildSettings(table);
    }

    public TripwireMod() {
        // Never hard-disable the overlay for bundled runtimes: Neon passes the bridge it
        // manages through the parameterized constructor, and a standalone jar has to work
        // both on a real MindustryX and on a plain vanilla client.
        this(OverlayUiBridge.autoDetect());
    }

    public TripwireMod(OverlayUiBridge overlayUi) {
        this.overlayUi = new TripwireOverlayUi(overlayUi);
        TripwireData.init();
        TripwireInput.init();
        TripwireRenderer.init();
        TripwireDetector.init();

        Events.on(EventType.ClientLoadEvent.class, e -> {
            this.overlayUi.init();
            if (ui != null && ui.settings != null) {
                if (!bekBundled) {
                    ui.settings.addCategory("@settings.tripwire", Icon.map, this::bekBuildSettings);
                }
            }
        });
    }
}
