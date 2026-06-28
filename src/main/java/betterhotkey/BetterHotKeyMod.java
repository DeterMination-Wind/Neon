package betterhotkey;

import arc.Events;
import betterhotkey.features.BetterHotKeyFeature;
import mdtxcompat.OverlayUiBridge;
import mdtxcompat.LegacyMindustryXGuard;
import mindustry.game.EventType;
import mindustry.gen.Icon;
import mindustry.mod.Mod;
import mindustry.ui.dialogs.SettingsMenuDialog;

import static mindustry.Vars.ui;

public class BetterHotKeyMod extends Mod {

    public static boolean bekBundled = false;

    private static boolean settingsAdded;

    public BetterHotKeyMod() {
        this(vanillaOverlayUi());
    }

    protected BetterHotKeyMod(OverlayUiBridge overlayUi) {
        BetterHotKeyFeature.configureOverlayUi(overlayUi);
    }

    private static OverlayUiBridge vanillaOverlayUi() {
        LegacyMindustryXGuard.rejectLegacyMindustryX("betterHotKey");
        return OverlayUiBridge.autoDetect();
    }

    public void bekBuildSettings(SettingsMenuDialog.SettingsTable table) {
        BetterHotKeyFeature.buildSettings(table);
    }

    @Override
    public void init() {
        BetterHotKeyFeature.init();

        Events.on(EventType.ClientLoadEvent.class, e -> {
            if (settingsAdded) return;
            settingsAdded = true;

            if (ui == null || ui.settings == null) return;
            if (!bekBundled) {
                ui.settings.addCategory("@settings.betterhotkey", Icon.settingsSmall, this::bekBuildSettings);
            }
        });
    }
}
