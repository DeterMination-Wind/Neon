package bettermapeditor;

import arc.Events;
import bettermapeditor.features.DraggableMirrorAxisFeature;
import mindustry.game.EventType;
import mindustry.gen.Icon;
import mindustry.mod.Mod;
import mindustry.ui.dialogs.SettingsMenuDialog;

import static mindustry.Vars.ui;

public class BetterMapEditorMod extends Mod {
    public static boolean bekBundled = false;

    private static boolean settingsAdded;

    public static void bekBuildSettings(SettingsMenuDialog.SettingsTable table) {
    }

    @Override
    public void init() {
        DraggableMirrorAxisFeature.init();

        Events.on(EventType.ClientLoadEvent.class, e -> {
            if (!bekBundled) {
                GithubUpdateCheck.applyDefaults();
                GithubUpdateCheck.checkOnce();
            }

            if (settingsAdded || ui == null || ui.settings == null) return;
            settingsAdded = true;
            if (bekBundled) return;

            ui.settings.addCategory("@settings.bettermapeditor", Icon.map, table -> {
                table.checkPref(GithubUpdateCheck.enabledKey(), true);
                table.checkPref(GithubUpdateCheck.showDialogKey(), true);
            });
        });
    }
}
