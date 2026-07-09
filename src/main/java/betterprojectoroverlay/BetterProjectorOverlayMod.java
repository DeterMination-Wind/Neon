package betterprojectoroverlay;

import arc.Events;
import betterprojectoroverlay.features.BetterProjectorOverlayFeature;
import mdtxcompat.LegacyMindustryXGuard;
import mdtxcompat.MarkerBridge;
import mindustry.game.EventType;
import mindustry.gen.Icon;
import mindustry.mod.Mod;

import static mindustry.Vars.ui;

public class BetterProjectorOverlayMod extends Mod {
    public static boolean bekBundled = false;

    private static boolean settingsAdded;

    public BetterProjectorOverlayMod() {
        this(vanillaMarkers());
    }

    protected BetterProjectorOverlayMod(MarkerBridge markers) {
        BetterProjectorOverlayFeature.configureMarkers(markers);
    }

    private static MarkerBridge vanillaMarkers() {
        LegacyMindustryXGuard.rejectLegacyMindustryX("BetterProjectorOverlay");
        return MarkerBridge.UNSUPPORTED;
    }

    public static void bekBuildSettings(mindustry.ui.dialogs.SettingsMenuDialog.SettingsTable table) {
        BetterProjectorOverlayFeature.buildSettings(table);
    }

    @Override
    public void init() {
        BetterProjectorOverlayFeature.init();

        Events.on(EventType.ClientLoadEvent.class, e -> {
            if (settingsAdded) return;
            settingsAdded = true;

            if (!bekBundled) GithubUpdateCheck.applyDefaults();

            if (!bekBundled) {
                ui.settings.addCategory("@settings.bpo", Icon.map, BetterProjectorOverlayFeature::buildSettings);
            }
            if (!bekBundled) GithubUpdateCheck.checkOnce();
        });
    }
}
