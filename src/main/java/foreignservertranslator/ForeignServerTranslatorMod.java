package foreignservertranslator;

import arc.Events;
import arc.util.Log;
import mindustry.Vars;
import mindustry.core.UI;
import mindustry.game.EventType.ClientLoadEvent;
import mindustry.gen.Icon;
import mindustry.mod.Mod;
import mindustry.ui.dialogs.SettingsMenuDialog;
import mindustry.ui.fragments.ChatFragment;
import mindustry.ui.fragments.TranslatorChatFragment;

public class ForeignServerTranslatorMod extends Mod{
    private static final String AGZAM_CHAT_FRAGMENT = "agzam4.uiOverride.CustomChatFragmentHandle";
    public static boolean bekBundled = false;

    private boolean chatHookInstalled;
    private boolean uiWrapped;
    private boolean joinButtonsInstalled;
    private boolean markerInstalled;
    private boolean settingsInstalled;
    private String skippedChatHookClass;

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
        if(Vars.headless || Vars.ui == null || Vars.ui.chatfrag == null || Vars.ui.settings == null) return;

        UI original = Vars.ui;
        installChatHook(original);
        installUiWrapper(original);
        installJoinDialogButtons();
        installMarkerTranslator();
        installSettingsCategory();
    }

    private void installChatHook(UI original){
        if(chatHookInstalled) return;

        if(original.chatfrag instanceof TranslatorChatFragment){
            chatHookInstalled = true;
            return;
        }

        String incompatibleClass = incompatibleChatFragmentClass(original.chatfrag);
        if(incompatibleClass != null){
            logChatHookSkip(incompatibleClass);
            return;
        }

        try{
            TranslatorChatFragment replacement = new TranslatorChatFragment();
            replacement.installOver(original.chatfrag);
            original.chatfrag = replacement;
            chatHookInstalled = true;
        }catch(Throwable error){
            Log.err("ForeignServerTranslator failed to install chat hook.", error);
        }
    }

    private void installUiWrapper(UI original){
        if(uiWrapped) return;

        try{
            Vars.ui = TranslatorUI.wrap(original);
            uiWrapped = true;
        }catch(Throwable error){
            Log.err("ForeignServerTranslator failed to wrap UI hooks.", error);
        }
    }

    private void installJoinDialogButtons(){
        if(joinButtonsInstalled) return;

        TranslatorFeature.installJoinDialogButtons();
        joinButtonsInstalled = true;
    }

    private void installMarkerTranslator(){
        if(markerInstalled) return;

        MarkerTranslator.install();
        markerInstalled = true;
    }

    private void installSettingsCategory(){
        if(settingsInstalled || bekBundled || Vars.ui == null || Vars.ui.settings == null) return;

        Vars.ui.settings.addCategory("@fst.settings", Icon.chat, this::bekBuildSettings);
        settingsInstalled = true;
    }

    private String incompatibleChatFragmentClass(ChatFragment fragment){
        if(fragment == null) return null;

        Class<?> type = fragment.getClass();
        if(type == ChatFragment.class || type == TranslatorChatFragment.class) return null;
        return type.getName();
    }

    private void logChatHookSkip(String className){
        if(className.equals(skippedChatHookClass)) return;
        skippedChatHookClass = className;

        if(AGZAM_CHAT_FRAGMENT.equals(className)){
            Log.info("[Info] ForeignServerTranslator kept agzam custom chat active; outgoing chat translation hook is disabled in compatibility mode.");
        }else{
            Log.info("[Info] ForeignServerTranslator left custom chat fragment in place for compatibility: @", className);
        }
    }
}
