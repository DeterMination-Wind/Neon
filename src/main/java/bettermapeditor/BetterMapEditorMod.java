package bettermapeditor;

import arc.Events;
import bettermapeditor.features.DraggableMirrorAxisFeature;
import mindustry.game.EventType;
import mindustry.gen.Icon;
import mindustry.mod.Mod;

import static mindustry.Vars.ui;

public class BetterMapEditorMod extends Mod {
    private static boolean settingsAdded;

    @Override
    public void init() {
        DraggableMirrorAxisFeature.init();

        Events.on(EventType.ClientLoadEvent.class, e -> {
            GithubUpdateCheck.applyDefaults();
            GithubUpdateCheck.checkOnce();

            if (settingsAdded || ui == null || ui.settings == null) return;
            settingsAdded = true;

            ui.settings.addCategory("@settings.bettermapeditor", Icon.map, table -> {
                table.checkPref(GithubUpdateCheck.enabledKey(), true);
                table.checkPref(GithubUpdateCheck.showDialogKey(), true);
            });
        });
    }
}
