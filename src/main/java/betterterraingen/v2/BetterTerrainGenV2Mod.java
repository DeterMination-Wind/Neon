package betterterraingen.v2;

import arc.Core;
import arc.func.Prov;
import arc.util.serialization.Json;
import betterterraingen.v2.filters.NaturalWaterFilter;
import mindustry.io.JsonIO;
import mindustry.maps.Maps;
import mindustry.maps.filters.GenerateFilter;
import mindustry.mod.Mod;
import mindustry.ui.dialogs.SettingsMenuDialog;

import java.util.Arrays;

public class BetterTerrainGenV2Mod extends Mod {
    public static boolean bekBundled = false;

    private static final String classTag = "NaturalWater";
    private static final String keyUsed = "btg-used";

    public BetterTerrainGenV2Mod() {
        registerFilter();
    }

    @Override
    public void init() {
        Core.settings.defaults(keyUsed, false);
        registerFilter();
    }

    public void bekBuildSettings(SettingsMenuDialog.SettingsTable table) {
        // Better Terrain Gen V2 has no user settings.
    }

    public static boolean hasBeenUsed() {
        return Core.settings != null && Core.settings.getBool(keyUsed, false);
    }

    public static synchronized void markUsed() {
        if (Core.settings == null || hasBeenUsed()) return;

        Core.settings.put(keyUsed, true);
        try {
            Core.settings.forceSave();
        } catch (Throwable ignored) {
            // The setting is still updated in memory and will be persisted by the next save.
        }
    }

    public static synchronized void registerFilter() {
        if (!containsNaturalWaterFilter()) {
            Prov<GenerateFilter>[] current = Maps.allFilterTypes;
            Prov<GenerateFilter>[] expanded = Arrays.copyOf(current, current.length + 1);
            expanded[current.length] = NaturalWaterFilter::new;
            Maps.allFilterTypes = expanded;
        }

        Json json = JsonIO.json;
        json.addClassTag(classTag, NaturalWaterFilter.class);
    }

    private static boolean containsNaturalWaterFilter() {
        for (Prov<GenerateFilter> provider : Maps.allFilterTypes) {
            try {
                if (provider.get() instanceof NaturalWaterFilter) return true;
            } catch (Throwable ignored) {
                // A provider from another mod must not prevent this filter from registering.
            }
        }
        return false;
    }
}
