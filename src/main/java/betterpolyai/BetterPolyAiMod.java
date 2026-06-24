package betterpolyai;

import arc.Events;
import betterpolyai.features.PolyAiFeature;
import mindustry.game.EventType;
import mindustry.gen.Icon;
import mindustry.mod.Mod;
import mindustry.ui.dialogs.SettingsMenuDialog;

import static mindustry.Vars.ui;

public class BetterPolyAiMod extends Mod {
    public static boolean bekBundled = false;

    private static boolean settingsAdded;

    public void bekBuildSettings(SettingsMenuDialog.SettingsTable table) {
        PolyAiFeature.buildSettings(table);
    }

    @Override
    public void init() {
        PolyAiFeature.init();

        Events.on(EventType.ClientLoadEvent.class, e -> {
            if (settingsAdded) return;
            settingsAdded = true;

            if (!bekBundled) GithubUpdateCheck.applyDefaults();

            if (!bekBundled && ui != null && ui.settings != null) {
                ui.settings.addCategory("@settings.betterpolyai", Icon.units, this::bekBuildSettings);
            }
            if (!bekBundled) GithubUpdateCheck.checkOnce();
        });
    }
}
