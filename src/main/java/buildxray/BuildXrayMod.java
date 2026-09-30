package buildxray;

import arc.Events;
import mindustry.game.EventType;
import mindustry.gen.Icon;
import mindustry.mod.Mod;
import mindustry.ui.dialogs.SettingsMenuDialog;

import static mindustry.Vars.ui;

/**
 * Entry point of the standalone mod ({@code mod.json} "main").
 *
 * Ported to Java from the JavaScript mod "build-xray" by minRi2 (Miner); the original
 * script and descriptor are preserved under {@code original/}. The feature itself lives
 * in {@link BuildXrayFeature}; this class only forwards the lifecycle and keeps the Neon
 * aggregation contract.
 */
public class BuildXrayMod extends Mod{
    /** Set by Neon before its bundled modules initialize: suppresses this mod's own settings category. */
    public static boolean bekBundled = false;

    private static boolean settingsAdded;

    /** Neon aggregate contract: renders this mod's settings inside Neon's unified settings page. */
    public static void bekBuildSettings(SettingsMenuDialog.SettingsTable table){
        BuildXrayFeature.buildSettings(table);
    }

    @Override
    public void init(){
        BuildXrayFeature.init();

        Events.on(EventType.ClientLoadEvent.class, e -> {
            if(settingsAdded) return;
            settingsAdded = true;
            // Bundled form: Neon registers the settings entry itself.
            if(!bekBundled && ui != null && ui.settings != null){
                ui.settings.addCategory("@category.build-xray.name", Icon.eyeSmall, BuildXrayMod::bekBuildSettings);
            }
        });
    }
}
