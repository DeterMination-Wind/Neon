package foreignservertranslator;

import arc.Events;
import arc.util.Log;
import mindustry.Vars;
import mindustry.core.UI;
import mindustry.game.EventType.ClientLoadEvent;
import mindustry.gen.Icon;
import mindustry.mod.Mod;
import mindustry.ui.dialogs.SettingsMenuDialog;
import mindustry.ui.fragments.TranslatorChatFragment;

public class ForeignServerTranslatorMod extends Mod{
    public static boolean bekBundled = false;

    private boolean installed;

    public void bekBuildSettings(SettingsMenuDialog.SettingsTable table){
        TranslatorSettings.build(table);
    }

    @Override
    public void init(){
        TranslatorFeature.init();
        MarkerTranslator.init();
        TokenStats.init();
        LanguageCatalog.init();
        Events.on(ClientLoadEvent.class, event -> installUi());
        installUi();
    }

    private void installUi(){
        if(installed || Vars.headless || Vars.ui == null || Vars.ui.chatfrag == null || Vars.ui.settings == null) return;

        try{
            UI original = Vars.ui;
            TranslatorChatFragment replacement = new TranslatorChatFragment();
            replacement.installOver(original.chatfrag);
            original.chatfrag = replacement;
            Vars.ui = TranslatorUI.wrap(original);
            TranslatorFeature.installJoinDialogButtons();
            WorldTextTranslator.install();
            MarkerTranslator.install();
            if(!bekBundled) Vars.ui.settings.addCategory("@fst.settings", Icon.chat, this::bekBuildSettings);
            installed = true;
        }catch(Throwable error){
            Log.err("ForeignServerTranslator failed to install UI hooks.", error);
        }
    }
}
