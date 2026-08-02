package betterrtsformation;

import arc.Events;
import mindustry.game.EventType;
import mindustry.mod.Mod;
import mindustry.ui.dialogs.SettingsMenuDialog;

public class BetterRTSFormationMod extends Mod {
    public static boolean bekBundled = false;

    @FunctionalInterface
    public interface BundledSettingsRenderer {
        void build(SettingsMenuDialog.SettingsTable table, String enabledKey, String outsideCommandModeKey);
    }

    public static void configureBundledSettingsRenderer(BundledSettingsRenderer renderer) {
        BetterRTSFormationFeature.configureBundledSettingsRenderer(renderer);
    }

    public void bekBuildSettings(SettingsMenuDialog.SettingsTable table) {
        BetterRTSFormationFeature.buildBundledSettings(table);
    }

    @Override
    public void init() {
        BetterRTSFormationFeature.init();

        Events.on(EventType.ClientLoadEvent.class, BetterRTSFormationFeature::loadClient);
    }
}
