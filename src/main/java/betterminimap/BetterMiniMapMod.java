package betterminimap;

import arc.Events;
import betterminimap.features.BetterMiniMapFeature;
import mindustry.game.EventType;
import mindustry.gen.Icon;
import mindustry.mod.Mod;
import mindustry.ui.dialogs.SettingsMenuDialog;

import static mindustry.Vars.ui;

public class BetterMiniMapMod extends Mod {
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

            if (!bekBundled) GithubUpdateCheck.applyDefaults();

            if (!bekBundled && ui != null && ui.settings != null) {
                ui.settings.addCategory("@settings.betterminimap", Icon.map, BetterMiniMapMod::bekBuildSettings);
            }
            if (!bekBundled) GithubUpdateCheck.checkOnce();
        });
    }
}
