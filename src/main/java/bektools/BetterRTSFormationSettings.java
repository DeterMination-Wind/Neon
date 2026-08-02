package bektools;

import bektools.ui.RbmStyle;
import mindustry.gen.Icon;
import mindustry.ui.dialogs.SettingsMenuDialog;

public final class BetterRTSFormationSettings {
    private BetterRTSFormationSettings() {
    }

    public static void configure() {
        betterrtsformation.BetterRTSFormationMod.configureBundledSettingsRenderer(
            BetterRTSFormationSettings::build
        );
    }

    private static void build(SettingsMenuDialog.SettingsTable table, String enabledKey, String outsideCommandModeKey) {
        table.pref(new RbmStyle.IconCheckSetting(enabledKey, true, Icon.units, null));
        table.pref(new RbmStyle.IconCheckSetting(outsideCommandModeKey, false, Icon.commandRally, null));
    }
}
