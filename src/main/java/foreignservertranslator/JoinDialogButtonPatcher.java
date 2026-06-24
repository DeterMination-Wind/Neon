package foreignservertranslator;

import arc.Events;
import arc.scene.Element;
import arc.scene.ui.Button;
import arc.scene.ui.ImageButton;
import arc.scene.ui.layout.Table;
import arc.struct.ObjectSet;
import arc.struct.Seq;
import arc.util.Log;
import mindustry.Vars;
import mindustry.game.EventType.Trigger;
import mindustry.gen.Icon;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.JoinDialog;

import java.lang.reflect.Field;

final class JoinDialogButtonPatcher{
    private static final ObjectSet<Table> patchedHeaders = new ObjectSet<>();
    private static final ObjectSet<Button> patchedCards = new ObjectSet<>();
    private static Field remoteField;
    private static Field serversField;
    private static boolean installed;

    private JoinDialogButtonPatcher(){
    }

    static void install(){
        if(installed) return;
        installed = true;
        try{
            remoteField = JoinDialog.class.getDeclaredField("remote");
            serversField = JoinDialog.class.getDeclaredField("servers");
            remoteField.setAccessible(true);
            serversField.setAccessible(true);
            Events.run(Trigger.update, JoinDialogButtonPatcher::patchVisibleDialog);
        }catch(Throwable error){
            Log.err("ForeignServerTranslator cannot access JoinDialog fields.", error);
        }
    }

    @SuppressWarnings("unchecked")
    private static void patchVisibleDialog(){
        if(remoteField == null || Vars.ui == null || Vars.ui.join == null || !Vars.ui.join.isShown()) return;
        try{
            Table remote = (Table)remoteField.get(Vars.ui.join);
            Seq<JoinDialog.Server> servers = (Seq<JoinDialog.Server>)serversField.get(Vars.ui.join);
            int serverIndex = 0;
            for(Element element : remote.getChildren()){
                if(!(element instanceof Button) || serverIndex >= servers.size) continue;
                Button card = (Button)element;
                JoinDialog.Server server = servers.get(serverIndex++);
                String key = TranslatorFeature.serverKey(server.ip, server.port);
                patchCard(card, key);
            }
        }catch(Throwable error){
            Log.err("ForeignServerTranslator failed to patch remote server cards.", error);
            remoteField = null;
        }
    }

    private static void patchCard(Button card, String key){
        if(!patchedCards.contains(card)){
            patchedCards.add(card);
            card.clicked(() -> {
                if(!card.childrenPressed()){
                    TranslatorFeature.selectServer(key);
                }
            });
        }

        if(card.getChildren().isEmpty() || !(card.getChildren().first() instanceof Table)) return;
        Table header = (Table)card.getChildren().first();
        if(patchedHeaders.contains(header)) return;
        patchedHeaders.add(header);

        ImageButton toggle = header.button(Icon.eyeOffSmall, Styles.emptyi, () -> TranslatorFeature.toggleForeign(key))
            .margin(3f).pad(2f).padTop(6f).top().right().tooltip("@fst.server.foreign").get();
        toggle.update(() -> toggle.getStyle().imageUp = TranslatorFeature.isForeign(key) ? Icon.eyeSmall : Icon.eyeOffSmall);
    }
}
