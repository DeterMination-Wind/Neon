package lockattack;

import arc.Events;
import mindustry.game.EventType;
import mindustry.mod.Mod;
import mindustry.ui.dialogs.SettingsMenuDialog;

public class LockAttackMod extends Mod {
    public static boolean bekBundled = false;

    @FunctionalInterface
    public interface BundledSettingsRenderer {
        void build(SettingsMenuDialog.SettingsTable table);
    }

    public static void configureBundledSettingsRenderer(BundledSettingsRenderer renderer) {
        LockAttackFeature.configureBundledSettingsRenderer(renderer);
    }

    public void bekBuildSettings(SettingsMenuDialog.SettingsTable table) {
        LockAttackFeature.buildBundledSettings(table);
    }

    @Override
    public void init() {
        LockAttackFeature.init();
        Events.on(EventType.ClientLoadEvent.class, LockAttackFeature::loadClient);
    }
}
