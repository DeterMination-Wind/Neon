package updatescheme;

import mindustry.gen.Icon;
import mindustry.mod.Mod;
import mdtxcompat.LegacyMindustryXGuard;
import mdtxcompat.OverlayUiBridge;
import mdtxcompat.SchematicShareBridge;
import updatescheme.features.UpdateSchemeFeature;

import static mindustry.Vars.ui;

public class UpdateSchemeMod extends Mod {

    public static boolean bekBundled = false;

    public UpdateSchemeMod() {
        this(vanillaOverlayUi(), SchematicShareBridge.UNSUPPORTED);
    }

    protected UpdateSchemeMod(OverlayUiBridge overlayUi, SchematicShareBridge schematicShare) {
        UpdateSchemeFeature.configureCompat(overlayUi, schematicShare);
        UpdateSchemeFeature.init();
    }

    private static OverlayUiBridge vanillaOverlayUi() {
        LegacyMindustryXGuard.rejectLegacyMindustryX("UpdateScheme");
        return OverlayUiBridge.UNSUPPORTED;
    }

    public void bekBuildSettings(mindustry.ui.dialogs.SettingsMenuDialog.SettingsTable table) {
        UpdateSchemeFeature.buildSettings(table);
    }

    @Override
    public void init() {
        if (bekBundled) return;
        if (ui != null) {
            ui.settings.addCategory("@settings.updatescheme", Icon.download, this::bekBuildSettings);
        }
    }
}
