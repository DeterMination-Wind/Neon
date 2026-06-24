package mindustry.ui.fragments;

import arc.Core;
import arc.Events;
import arc.Input.TextInput;
import arc.scene.Group;
import arc.scene.ui.TextField;
import arc.struct.Seq;
import arc.util.Log;
import arc.util.Strings;
import arc.util.Time;
import foreignservertranslator.TranslationService;
import foreignservertranslator.TranslatorFeature;
import foreignservertranslator.TranslatorFeature.OutgoingMessage;
import mindustry.Vars;
import mindustry.game.EventType.ClientChatEvent;
import mindustry.gen.Call;
import mindustry.core.UI;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;

import static mindustry.Vars.*;

public class TranslatorChatFragment extends ChatFragment{
    private static final int messagesShown = 10;

    private final Field shownField;
    private final Field fadeTimeField;
    private final Field scrollPosField;
    private final Seq<String> messages;
    private final TextField chatfield;
    private final Seq<String> history;
    private final Method originalSend;
    private final Seq<TranslationSlot> translationSlots = new Seq<>();
    private final Seq<TranslationSlot> pendingTranslationSlots = new Seq<>();
    private boolean active = true;

    @SuppressWarnings("unchecked")
    public TranslatorChatFragment() throws Exception{
        super();
        Class<?> base = ChatFragment.class;
        shownField = accessibleField(base, "shown");
        fadeTimeField = accessibleField(base, "fadetime");
        scrollPosField = accessibleField(base, "scrollPos");
        Field messagesField = accessibleField(base, "messages");
        Field chatField = accessibleField(base, "chatfield");
        Field historyField = accessibleField(base, "history");
        originalSend = base.getDeclaredMethod("sendMessage");
        originalSend.setAccessible(true);
        messages = (Seq<String>)messagesField.get(this);
        chatfield = (TextField)chatField.get(this);
        history = (Seq<String>)historyField.get(this);
    }

    public void installOver(ChatFragment oldFragment){
        if(Core.scene == null) return;
        Core.scene.root.addChild(this);
        Core.scene.root.swapActor(oldFragment, this);
        Core.scene.root.removeChild(oldFragment);
    }

    @Override
    public void addMessage(String message){
        super.addMessage(message);
        shiftTranslationSlots(0, 1);
        attachPendingTranslationSlots(message);
        if(TranslatorFeature.shouldTranslateServerText(message)){
            Time.runTask(1f, () -> TranslatorFeature.translateServerChatLine(message));
        }
    }

    @Override
    public void clearMessages(){
        super.clearMessages();
        translationSlots.clear();
        pendingTranslationSlots.clear();
    }

    public TranslationSlot reserveTranslationSlot(String speaker, String source, String pendingMessage){
        TranslationSlot slot = new TranslationSlot();
        slot.speaker = normalizeForMatch(speaker);
        slot.source = normalizeForMatch(source);
        slot.pendingMessage = pendingMessage;

        int sourceIndex = findSourceIndex(slot);
        if(sourceIndex >= 0){
            insertTranslationSlot(slot, sourceIndex);
        }else{
            pendingTranslationSlots.add(slot);
        }
        return slot;
    }

    public void completeTranslationSlot(TranslationSlot slot, String message){
        if(slot == null) return;
        if(slot.done && slot.reserved) return;

        slot.done = true;
        slot.completedMessage = message;

        if(!slot.reserved){
            int sourceIndex = findSourceIndex(slot);
            if(sourceIndex >= 0){
                pendingTranslationSlots.remove(slot);
                insertMessage(sourceIndex, message);
            }
            return;
        }

        translationSlots.remove(slot);
        int placeholderIndex = findPlaceholderIndex(slot);
        if(placeholderIndex >= 0){
            messages.set(placeholderIndex, message);
        }
    }

    private void attachPendingTranslationSlots(String message){
        if(message == null || pendingTranslationSlots.isEmpty()) return;

        for(int i = 0; i < pendingTranslationSlots.size; i++){
            TranslationSlot slot = pendingTranslationSlots.get(i);
            if(matchesSource(message, slot)){
                pendingTranslationSlots.remove(i);
                if(slot.done){
                    insertMessage(0, slot.completedMessage);
                }else{
                    insertTranslationSlot(slot, 0);
                }
                return;
            }
        }
    }

    private void insertTranslationSlot(TranslationSlot slot, int sourceIndex){
        slot.reserved = true;
        insertMessage(sourceIndex, slot.pendingMessage);
        slot.index = sourceIndex;
        translationSlots.add(slot);
    }

    private void insertMessage(int index, String message){
        if(message == null) return;

        int insertIndex = Math.max(0, Math.min(index, messages.size));
        messages.insert(insertIndex, message);
        shiftTranslationSlots(insertIndex, 1);

        try{
            float fadeTime = fadeTimeField.getFloat(this);
            fadeTime += 1f;
            fadeTimeField.setFloat(this, Math.min(fadeTime, messagesShown) + 1f);

            int scrollPos = scrollPosField.getInt(this);
            if(scrollPos > 0) scrollPosField.setInt(this, scrollPos + 1);
        }catch(Throwable error){
            disable(error);
        }
    }

    private int findSourceIndex(TranslationSlot slot){
        for(int i = 0; i < messages.size; i++){
            if(isTranslationLine(messages.get(i))) continue;
            if(matchesSource(messages.get(i), slot)) return i;
        }
        return -1;
    }

    private int findPlaceholderIndex(TranslationSlot slot){
        if(slot.index >= 0 && slot.index < messages.size && isPendingLine(messages.get(slot.index), slot)){
            return slot.index;
        }

        int sourceIndex = findSourceIndex(slot);
        if(sourceIndex > 0 && isPendingLine(messages.get(sourceIndex - 1), slot)){
            slot.index = sourceIndex - 1;
            return slot.index;
        }

        for(int i = 0; i < messages.size; i++){
            if(isPendingLine(messages.get(i), slot)){
                slot.index = i;
                return i;
            }
        }
        return -1;
    }

    private boolean matchesSource(String message, TranslationSlot slot){
        String normalized = normalizeForMatch(message);
        if(slot.source.isEmpty() || !normalized.contains(slot.source)) return false;
        return slot.speaker.isEmpty() || normalized.contains(slot.speaker);
    }

    private boolean isPendingLine(String message, TranslationSlot slot){
        return normalizeForMatch(message).equals(normalizeForMatch(slot.pendingMessage));
    }

    private boolean isTranslationLine(String message){
        return normalizeForMatch(message).startsWith("=>");
    }

    private static String normalizeForMatch(String value){
        if(value == null) return "";
        return Strings.stripColors(value).replace('\n', ' ').replace('\r', ' ').replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
    }

    private void shiftTranslationSlots(int startIndex, int amount){
        for(TranslationSlot slot : translationSlots){
            if(slot.index >= startIndex){
                slot.index += amount;
            }
        }
    }

    @Override
    public void toggle(){
        if(!active){
            super.toggle();
            return;
        }

        try{
            if(!shown()){
                Core.scene.setKeyboardFocus(chatfield);
                shownField.setBoolean(this, true);
                if(mobile){
                    TextInput input = new TextInput();
                    input.maxLength = maxTextLength;
                    input.text = chatfield.getText();
                    input.accepted = text -> {
                        chatfield.setText(text);
                        interceptSend();
                        hide();
                        Core.input.setOnscreenKeyboardVisible(false);
                    };
                    input.canceled = this::hide;
                    Core.input.getTextInput(input);
                }else{
                    chatfield.fireClick();
                }
            }else{
                Time.runTask(2f, () -> {
                    try{
                        Core.scene.setKeyboardFocus(null);
                        shownField.setBoolean(this, false);
                        scrollPosField.setInt(this, 0);
                        interceptSend();
                    }catch(Throwable error){
                        disable(error);
                    }
                });
            }
        }catch(Throwable error){
            disable(error);
            super.toggle();
        }
    }

    private void interceptSend(){
        String raw = chatfield.getText().trim();
        if(!TranslatorFeature.isCurrentServerForeign()){
            invokeOriginal();
            return;
        }

        OutgoingMessage outgoing = TranslatorFeature.parseOutgoing(raw);
        if(!outgoing.translatable || outgoing.body.isEmpty()){
            invokeOriginal();
            return;
        }

        clearChatInput();
        if(history.size < 2 || !history.get(1).equals(raw)){
            history.insert(1, raw);
        }

        String body = UI.formatIcons(outgoing.body);
        TranslatorFeature.TranslationContext context = TranslatorFeature.buildOutgoingTranslationContext(body, outgoing);
        TranslatorFeature.recordOwnMessage(body);
        TranslationService.translate(body, outgoing.targetLanguage, context, translated -> {
            String sent = outgoing.prefix + TranslatorFeature.renderMarkup(translated);
            Events.fire(new ClientChatEvent(sent));
            Call.sendChatMessage(sent);
        }, error -> addMessage(TranslatorFeature.translateFailed()));
    }

    private void invokeOriginal(){
        try{
            originalSend.invoke(this);
        }catch(Throwable error){
            disable(error);
        }
    }

    private void disable(Throwable error){
        active = false;
        Log.err("ForeignServerTranslator chat hook failed; returning to normal sending.", error);
    }

    private static Field accessibleField(Class<?> type, String name) throws Exception{
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    public static final class TranslationSlot{
        private int index = -1;
        private boolean reserved;
        private boolean done;
        private String speaker = "";
        private String source = "";
        private String pendingMessage = "";
        private String completedMessage = "";
    }
}
