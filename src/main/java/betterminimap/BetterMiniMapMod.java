package betterminimap;

import arc.Events;
import betterminimap.features.BetterMiniMapFeature;
import mindustry.game.EventType;
import mindustry.gen.Icon;
import mindustry.mod.Mod;

import static mindustry.Vars.ui;

public class BetterMiniMapMod extends Mod {

    private static boolean settingsAdded;

    @Override
    public void init() {
        BetterMiniMapFeature.init();

        Events.on(EventType.ClientLoadEvent.class, e -> {
            if (settingsAdded) return;
            settingsAdded = true;

            GithubUpdateCheck.applyDefaults();

            ui.settings.addCategory("@settings.betterminimap", Icon.map, BetterMiniMapFeature::buildSettings);
            GithubUpdateCheck.checkOnce();
        });
    }
}
