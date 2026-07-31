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
import mindustry.net.Host;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.JoinDialog;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.IdentityHashMap;

final class JoinDialogButtonPatcher{
    private static final ObjectSet<Table> patchedHeaders = new ObjectSet<>();
    private static final ObjectSet<Button> patchedCards = new ObjectSet<>();
    private static Field remoteField;
    private static Field localField;
    private static Field globalField;
    private static Field serversField;
    private static boolean installed;
    private static boolean errorLogged;

    private JoinDialogButtonPatcher(){
    }

    static void install(){
        if(installed) return;
        installed = true;
        try{
            remoteField = findField("remote");
            localField = findField("local");
            globalField = findField("global");
            serversField = findField("servers");
            if(remoteField == null && localField == null && globalField == null){
                throw new NoSuchFieldException("JoinDialog server list fields are unavailable");
            }
            Events.run(Trigger.update, JoinDialogButtonPatcher::patchVisibleDialog);
        }catch(Throwable error){
            logError("ForeignServerTranslator cannot access JoinDialog fields.", error);
        }
    }

    private static Field findField(String name) throws NoSuchFieldException{
        try{
            Field field = JoinDialog.class.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        }catch(NoSuchFieldException error){
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static void patchVisibleDialog(){
        if(Vars.ui == null || Vars.ui.join == null || !Vars.ui.join.isShown()) return;

        try{
            if(remoteField != null && serversField != null){
                Table remote = (Table)remoteField.get(Vars.ui.join);
                Seq<JoinDialog.Server> servers = (Seq<JoinDialog.Server>)serversField.get(Vars.ui.join);
                patchSavedServers(remote, servers);
            }
        }catch(Throwable error){
            logError("ForeignServerTranslator failed to patch remote server cards.", error);
        }

        try{
            patchDiscoveredTable(localField);
            patchDiscoveredTable(globalField);
        }catch(Throwable error){
            logError("ForeignServerTranslator failed to patch discovered server cards.", error);
        }
    }

    private static void patchSavedServers(Table remote, Seq<JoinDialog.Server> servers){
        if(remote == null || servers == null) return;

        int serverIndex = 0;
        for(Element element : remote.getChildren()){
            if(!(element instanceof Button) || serverIndex >= servers.size) continue;
            Button card = (Button)element;
            JoinDialog.Server server = servers.get(serverIndex++);
            patchCard(card, TranslatorFeature.serverKey(server.ip, server.port));
        }
    }

    private static void patchDiscoveredTable(Field field) throws IllegalAccessException{
        if(field == null) return;
        Object value = field.get(Vars.ui.join);
        if(value instanceof Table){
            patchDiscoveredElements((Table)value);
        }
    }

    private static void patchDiscoveredElements(Element element){
        if(element instanceof Button){
            Button card = (Button)element;
            if(hasServerHeader(card)){
                Host host = findHost(card);
                if(host != null){
                    patchCard(card, TranslatorFeature.serverKey(host.address, host.port));
                }
                return;
            }
        }

        if(element instanceof Table){
            for(Element child : ((Table)element).getChildren()){
                patchDiscoveredElements(child);
            }
        }
    }

    private static boolean hasServerHeader(Button card){
        return !card.getChildren().isEmpty() && card.getChildren().first() instanceof Table;
    }

    private static Host findHost(Button card){
        for(arc.scene.event.EventListener listener : card.getListeners()){
            Host host = findHost(listener, new IdentityHashMap<>(), 0);
            if(host != null) return host;
        }
        return null;
    }

    private static Host findHost(Object value, IdentityHashMap<Object, Boolean> visited, int depth){
        if(value == null || depth > 8 || visited.put(value, Boolean.TRUE) != null) return null;
        if(value instanceof Host) return (Host)value;
        if(value instanceof Element) return null;

        Class<?> type = value.getClass();
        if(type.isArray()){
            int length = Array.getLength(value);
            for(int i = 0; i < length; i++){
                Host host = findHost(Array.get(value, i), visited, depth + 1);
                if(host != null) return host;
            }
            return null;
        }
        if(!isCapturedObject(type)) return null;

        for(Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()){
            for(Field field : current.getDeclaredFields()){
                if(Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive()) continue;
                try{
                    field.setAccessible(true);
                    Host host = findHost(field.get(value), visited, depth + 1);
                    if(host != null) return host;
                }catch(Throwable ignored){
                    // Captured listener fields are implementation details of the game/Arc version.
                }
            }
        }
        return null;
    }

    private static boolean isCapturedObject(Class<?> type){
        String name = type.getName();
        return type.isSynthetic() || name.contains("$$Lambda$") || name.startsWith("arc.scene.Element$");
    }

    private static void logError(String message, Throwable error){
        if(!errorLogged){
            errorLogged = true;
            Log.err(message, error);
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
