package betterlogisticsspeed;

import arc.Events;
import betterlogisticsspeed.features.LongWindowFlowFeature;
import mindustry.game.EventType;
import mindustry.gen.Icon;
import mindustry.mod.Mod;

import static mindustry.Vars.ui;

public class BetterLogisticsSpeedMod extends Mod {

    private static boolean settingsAdded;

    @Override
    public void init() {
        LongWindowFlowFeature.init();

        Events.on(EventType.ClientLoadEvent.class, e -> {
            if (settingsAdded) return;
            settingsAdded = true;
            ui.settings.addCategory("@settings.betterlogisticsspeed", Icon.settingsSmall, LongWindowFlowFeature::buildSettings);
        });
    }
}
