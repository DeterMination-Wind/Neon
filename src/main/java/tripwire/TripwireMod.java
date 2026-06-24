package tripwire;

import arc.Events;
import mindustry.game.EventType;
import mindustry.gen.Icon;
import mindustry.mod.Mod;
import mindustry.ui.dialogs.SettingsMenuDialog;

import static mindustry.Vars.ui;

public class TripwireMod extends Mod {
    public static boolean bekBundled = false;

    public void bekBuildSettings(SettingsMenuDialog.SettingsTable table) {
        TripwireSettings.buildSettings(table);
    }

    public TripwireMod() {
        TripwireData.init();
        TripwireInput.init();
        TripwireRenderer.init();
        TripwireDetector.init();

        Events.on(EventType.ClientLoadEvent.class, e -> {
            if (ui != null && ui.settings != null) {
                if (!bekBundled) {
                    ui.settings.addCategory("@settings.tripwire", Icon.map, this::bekBuildSettings);
                }
            }
        });
    }
}
