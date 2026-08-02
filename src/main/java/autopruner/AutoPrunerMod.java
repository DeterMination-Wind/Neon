package autopruner;

import arc.Events;
import autopruner.features.AutoPrunerFeature;
import mindustry.game.EventType;
import mindustry.gen.Icon;
import mindustry.mod.Mod;
import mindustry.ui.dialogs.SettingsMenuDialog;

import static mindustry.Vars.ui;

public class AutoPrunerMod extends Mod {

    public static boolean bekBundled = false;

    private static boolean settingsAdded;

    @Override
    public void init() {
        AutoPrunerFeature.init();

        Events.on(EventType.ClientLoadEvent.class, e -> {
            if (bekBundled || settingsAdded) return;
            settingsAdded = true;
            ui.settings.addCategory("@settings.autopruner", Icon.trash, AutoPrunerFeature::buildSettings);
        });
    }

    public void bekBuildSettings(SettingsMenuDialog.SettingsTable table) {
        AutoPrunerFeature.buildSettings(table);
    }
}
