package betterminimap;

import arc.Events;
import betterminimap.features.BetterMiniMapFeature;
import mindustry.game.EventType;
import mindustry.gen.Icon;
import mindustry.mod.Mod;
import mindustry.ui.dialogs.SettingsMenuDialog;

import static mindustry.Vars.ui;

public class BetterMiniMapMod extends Mod {

    /** When true, this mod is running as a bundled component inside Neon. */
    public static boolean bekBundled = false;

    private static boolean settingsAdded;

    public static void bekBuildSettings(SettingsMenuDialog.SettingsTable table) {
        BetterMiniMapFeature.buildSettings(table);
    }

    @Override
    public void init() {
        BetterMiniMapFeature.init();

        Events.on(EventType.ClientLoadEvent.class, e -> {
            if (settingsAdded) return;
            settingsAdded = true;
            if (bekBundled) return;
            if (ui == null || ui.settings == null) return;

            ui.settings.addCategory("@settings.betterminimap", Icon.map, BetterMiniMapFeature::buildSettings);
        });
    }
}
