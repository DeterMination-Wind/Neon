package foreignservertranslator;

import arc.Core;
import arc.func.Cons;
import arc.func.Intc;
import arc.input.KeyCode;
import arc.math.Interp;
import arc.math.geom.Vec2;
import arc.scene.Element;
import arc.scene.actions.Actions;
import arc.scene.event.Touchable;
import arc.scene.ui.Dialog;
import arc.scene.ui.TextField;
import arc.scene.ui.TextField.TextFieldFilter;
import arc.scene.ui.layout.Scl;
import arc.scene.ui.layout.Table;
import arc.struct.IntMap;
import arc.struct.ObjectMap;
import arc.util.Align;
import arc.util.Log;
import arc.util.Nullable;
import mindustry.Vars;
import mindustry.core.UI;
import mindustry.graphics.Pal;
import mindustry.ui.Styles;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

public class TranslatorUI extends UI{
    private final ObjectMap<String, Element> translatedPopups = new ObjectMap<>();
    private final IntMap<Table> translatedLabels = new IntMap<>();

    public static TranslatorUI wrap(UI original) throws IllegalAccessException{
        if(original instanceof TranslatorUI) return (TranslatorUI)original;

        TranslatorUI wrapper = new TranslatorUI();
        for(Field field : UI.class.getFields()){
            int modifiers = field.getModifiers();
            if(Modifier.isStatic(modifiers) || Modifier.isFinal(modifiers)) continue;
            field.set(wrapper, field.get(original));
        }
        return wrapper;
    }

    @Override
    public void showLabel(@Nullable String info, int id, float duration, float worldx, float worldy, int flags){
        if(info == null){
            super.showLabel(null, id, duration, worldx, worldy, flags);
            showLabelOnTop(null, id, 0f, 0f, 0f);
            return;
        }

        String source = TranslatorFeature.stripIncomingHint(info);
        if(!TranslatorFeature.shouldTranslateWorldText(info)){
            super.showLabel(source, id, duration, worldx, worldy, flags);
            showLabelOnTop(null, id, 0f, 0f, 0f);
            return;
        }

        super.showLabel(source, id, duration, worldx, worldy, flags);
        WorldTextTranslator.translateMessageText(info, translated -> showLabelOnTop(translated, id, duration, worldx, worldy + 8f));
    }

    public void showLabelOnTop(@Nullable String info, int id, float duration, float worldx, float worldy){
        if(info == null){
            Table old = translatedLabels.remove(id);
            if(old != null) old.remove();
            return;
        }

        Table table = new Table(Styles.black3).margin(4);
        if(id != -1){
            Table old = translatedLabels.put(id, table);
            if(old != null) old.remove();
        }
        table.touchable = Touchable.disabled;
        table.update(() -> {
            if(Vars.state.isMenu()){
                table.remove();
                if(id != -1) translatedLabels.remove(id);
                return;
            }
            Vec2 v = Core.camera.project(worldx, worldy);
            table.setPosition(v.x, v.y, Align.center);
            if(table.parent != null && table.parent.getChildren().peek() != table){
                table.toFront();
            }
        });
        table.actions(Actions.delay(duration), Actions.remove(), Actions.run(() -> { if(id != -1) translatedLabels.remove(id); }));
        table.add(info).style(Styles.outlineLabel);
        table.pack();
        table.act(0f);
        Core.scene.add(table);
        table.toFront();
        table.getChildren().first().act(0f);
    }

    @Override
    public void showText(String titleText, String text, int align){
        if(!TranslatorFeature.shouldTranslateWorldText(text)){
            super.showText(titleText, text, align);
            return;
        }
        Dialog dialog = new Dialog(titleText){{
            cont.row();
            cont.image().width(400f).pad(2).colspan(2).height(4f).color(Pal.accent);
            cont.row();
            cont.add(text).width(400f).wrap().get().setAlignment(align, align);
            cont.row();
            buttons.button("@ok", this::hide).size(110, 50).pad(4);
            closeOnBack();
        }};
        dialog.show();
        translatePair(titleText, text, (title, message) -> showTextClone(dialog, title, message, align));
    }

    @Override
    public void showInfo(String info){
        if(!TranslatorFeature.shouldTranslateWorldText(info)){
            super.showInfo(info);
            return;
        }
        Dialog dialog = new Dialog(""){{
            getCell(cont).growX();
            cont.margin(15).add(info).width(400f).wrap().get().setAlignment(Align.center, Align.center);
            buttons.button("@ok", this::hide).size(110, 50).pad(4);
            keyDown(KeyCode.enter, this::hide);
            closeOnBack();
        }};
        dialog.show();
        translateOne(info, translated -> showTextClone(dialog, "", translated, Align.center));
    }

    @Override
    public void showInfoText(String titleText, String text){
        if(!TranslatorFeature.shouldTranslateWorldText(text)){
            super.showInfoText(titleText, text);
            return;
        }
        Dialog dialog = new Dialog(titleText){{
            cont.margin(15).add(text).width(400f).wrap().left().get().setAlignment(Align.left, Align.left);
            buttons.button("@ok", this::hide).size(110, 50).pad(4);
            closeOnBack();
        }};
        dialog.show();
        translatePair(titleText, text, (title, message) -> showTextClone(dialog, title, message, Align.left));
    }

    @Override
    public void showTextInput(String titleText, String text, int textLength, String def, boolean numbers, Cons<String> confirmed, Runnable closed){
        showTextInput(titleText, text, textLength, def, numbers, false, confirmed, closed);
    }

    @Override
    public void showTextInput(String titleText, String text, int textLength, String def, boolean numbers, boolean allowEmpty, Cons<String> confirmed, Runnable closed){
        if(!TranslatorFeature.shouldTranslateWorldText(text)){
            super.showTextInput(titleText, text, textLength, def, numbers, allowEmpty, confirmed, closed);
            return;
        }
        if(Vars.mobile){
            super.showTextInput(titleText, text, textLength, def, numbers, allowEmpty, confirmed, closed);
            return;
        }

        Dialog dialog = new Dialog(titleText){{
            cont.margin(30).add(text).padRight(6f);
            TextFieldFilter filter = numbers ? TextFieldFilter.digitsOnly : (f, c) -> true;
            TextField field = cont.field(def, t -> {}).size(330f, 50f).get();
            field.setMaxLength(textLength);
            field.setFilter(filter);
            buttons.defaults().size(120, 54).pad(4);
            buttons.button("@cancel", () -> {
                closed.run();
                hide();
            });
            buttons.button("@ok", () -> {
                confirmed.get(field.getText());
                hide();
            }).disabled(b -> !allowEmpty && field.getText().isEmpty());

            keyDown(KeyCode.enter, () -> {
                String result = field.getText();
                if(allowEmpty || !result.isEmpty()){
                    confirmed.get(result);
                    hide();
                }
            });

            closeOnBack(closed);
            show();

            Core.scene.setKeyboardFocus(field);
            field.setCursorPosition(def.length());
        }};
        translatePair(titleText, text, (title, message) -> showInputClone(dialog, title, message, def));
    }

    @Override
    public void announce(String text){
        announce(text, 3f);
    }

    @Override
    public void announce(String text, float duration){
        super.announce(text, duration);
        translateOne(text, translated -> showTimedClone(translated, duration, Align.left, cloneLeftPad(), 0f, true));
    }

    @Override
    public void showInfoToast(String info, float duration){
        super.showInfoToast(info, duration);
        translateOne(info, translated -> showTimedClone(translated, duration, Align.topLeft, cloneLeftPad(), 10f, false));
    }

    @Override
    public void showInfoPopup(@Nullable String id, String info, float duration, int align, int top, int left, int bottom, int right){
        if(info == null){
            super.showInfoPopup(id, null, duration, align, top, left, bottom, right);
            return;
        }

        super.showInfoPopup(id, info, duration, align, top, left, bottom, right);
        translateOne(info, translated -> {
            String popupKey = id == null ? "popup" : id;
            Element old = translatedPopups.remove(popupKey);
            if(old != null) old.remove();
            Element clone = showPopupClone(translated, duration, top, bottom);
            if(clone != null) translatedPopups.put(popupKey, clone);
        });
    }

    @Override
    public void showMenu(String title, String message, String[][] options, Intc callback){
        if(!TranslatorFeature.shouldTranslateWorldText(message) && !TranslatorFeature.shouldTranslateWorldText(title)){
            super.showMenu(title, message, options, callback);
            return;
        }
        Dialog dialog = newMenuDialog(title, message, options, (option, myself) -> {
            callback.get(option);
            myself.hide();
        });
        dialog.closeOnBack(() -> callback.get(-1));
        dialog.show();
        translateMenu(dialog, title, message, options);
    }

    @Override
    public void showFollowUpMenu(int menuId, String title, String message, String[][] options, Intc callback){
        if(!TranslatorFeature.shouldTranslateWorldText(message) && !TranslatorFeature.shouldTranslateWorldText(title)){
            super.showFollowUpMenu(menuId, title, message, options, callback);
            return;
        }
        Dialog dialog = newMenuDialog(title, message, options, (option, myself) -> callback.get(option));
        dialog.closeOnBack(() -> {
            followUpMenus.remove(menuId);
            callback.get(-1);
        });

        Dialog oldDialog = followUpMenus.remove(menuId);
        if(oldDialog != null){
            dialog.show(Core.scene, null);
            oldDialog.hide(null);
        }else{
            dialog.show();
        }
        followUpMenus.put(menuId, dialog);
        translateMenu(dialog, title, message, options);
    }

    private void translateMenu(Dialog source, String title, String message, String[][] options){
        int optionCount = 0;
        if(options != null){
            for(String[] row : options){
                if(row != null) optionCount += row.length;
            }
        }

        PendingGroup group = new PendingGroup(2 + optionCount, () -> {});
        final String[] translatedTitle = {displayText(title)};
        final String[] translatedMessage = {displayText(message)};
        String[][] translatedOptions = copyOptions(options);

        group.finished = () -> showMenuClone(source, translatedTitle[0], translatedMessage[0], translatedOptions);
        translateOne(title, value -> {
            translatedTitle[0] = value;
            group.done();
        });
        translateOne(message, value -> {
            translatedMessage[0] = value;
            group.done();
        });
        if(options != null){
            for(int r = 0; r < options.length; r++){
                if(options[r] == null) continue;
                for(int c = 0; c < options[r].length; c++){
                    final int row = r, col = c;
                    translateOne(options[r][c], value -> {
                        translatedOptions[row][col] = value;
                        group.done();
                    });
                }
            }
        }
    }

    private String[][] copyOptions(String[][] options){
        if(options == null) return new String[0][0];

        String[][] result = new String[options.length][];
        for(int i = 0; i < options.length; i++){
            if(options[i] == null){
                result[i] = new String[0];
            }else{
                result[i] = new String[options[i].length];
                for(int j = 0; j < options[i].length; j++){
                    result[i][j] = displayText(options[i][j]);
                }
            }
        }
        return result;
    }

    private void showTextClone(Dialog source, String title, String message, int align){
        showLeftClone(source, title, table -> {
            table.add(message).width(cloneContentWidth()).wrap().get().setAlignment(align, align);
        });
    }

    private void showInputClone(Dialog source, String title, String message, String def){
        showLeftClone(source, title, table -> {
            table.add(message).width(cloneContentWidth()).wrap().left().row();
            table.table(Styles.black3, field -> field.margin(8f).add(def == null ? "" : def).left().growX()).width(cloneContentWidth()).padTop(8f).row();
            table.table(buttons -> {
                buttons.defaults().height(44f).pad(4f).growX();
                buttons.button(displayText("@cancel"), () -> {});
                buttons.button(displayText("@ok"), () -> {});
            }).width(cloneContentWidth()).padTop(8f);
        });
    }

    private void showMenuClone(Dialog source, String title, String message, String[][] options){
        showLeftClone(source, title, table -> {
            table.add(message).width(cloneContentWidth()).wrap().get().setAlignment(Align.center);
            table.row();

            for(String[] row : options){
                if(row == null || row.length == 0) continue;
                table.table(buttonRow -> {
                    buttonRow.defaults().height(46f).pad(4f).growX();
                    for(String option : row){
                        if(option == null) continue;
                        buttonRow.button(option, () -> {});
                    }
                }).width(cloneContentWidth()).row();
            }
        });
    }

    private Element showLeftClone(Dialog source, String title, Cons<Table> builder){
        if(source.parent == null || Core.scene == null) return null;

        Table overlay = new Table();
        overlay.touchable = Touchable.disabled;
        overlay.setFillParent(true);
        overlay.left();
        overlay.update(() -> {
            if(source.parent == null){
                overlay.remove();
            }
        });
        overlay.table(Styles.black3, box -> {
            box.margin(12f);
            String shownTitle = displayText(title);
            if(shownTitle != null && !shownTitle.isEmpty()){
                box.add(shownTitle).width(cloneContentWidth()).wrap().center().row();
                box.image().width(cloneContentWidth()).height(3f).pad(2f).color(Pal.accent).row();
            }
            builder.get(box);
        }).width(cloneOuterWidth()).padLeft(cloneLeftPad());
        Core.scene.add(overlay);
        return overlay;
    }

    private Element showTimedClone(String text, float duration, int align, float leftPad, float topPad, boolean centeredVertically){
        if(Core.scene == null || !TranslatorFeature.shouldTranslateServerText(text)) return null;

        Table overlay = new Table();
        overlay.touchable = Touchable.disabled;
        overlay.setFillParent(true);
        overlay.align(align);
        if(centeredVertically){
            overlay.left();
        }
        overlay.actions(Actions.delay(duration * 0.9f), Actions.fadeOut(duration * 0.1f, Interp.fade), Actions.remove());
        overlay.table(Styles.black3, box -> box.margin(6f).add(text).style(Styles.outlineLabel).width(cloneContentWidth()).wrap())
            .width(cloneOuterWidth()).padLeft(leftPad).padTop(topPad);
        Core.scene.add(overlay);
        return overlay;
    }

    private Element showPopupClone(String text, float duration, int top, int bottom){
        if(Core.scene == null || !TranslatorFeature.shouldTranslateServerText(text)) return null;

        Table overlay = new Table();
        overlay.touchable = Touchable.disabled;
        overlay.setFillParent(true);
        overlay.left();
        overlay.actions(Actions.delay(duration), Actions.remove());
        overlay.table(Styles.black3, box -> box.margin(4f).add(text).style(Styles.outlineLabel).width(cloneContentWidth()).wrap())
            .width(cloneOuterWidth()).pad(clampedPad(top), cloneLeftPad(), clampedPad(bottom), 0f);
        Core.scene.add(overlay);
        return overlay;
    }

    private void translatePair(String first, String second, PairCons done){
        final String[] translatedFirst = {displayText(first)};
        final String[] translatedSecond = {displayText(second)};
        PendingGroup group = new PendingGroup(2, () -> done.get(translatedFirst[0], translatedSecond[0]));
        translateOne(first, value -> {
            translatedFirst[0] = value;
            group.done();
        });
        translateOne(second, value -> {
            translatedSecond[0] = value;
            group.done();
        });
    }

    private void translateOne(String text, Cons<String> done){
        if(!TranslatorFeature.shouldTranslateWorldText(text)){
            done.get(displayText(text));
            return;
        }

        TranslatorFeature.translateWorldText(text, done, error -> {
            Log.warn("ForeignServerTranslator failed to translate server UI text: @", error.getMessage());
            done.get(displayText(text));
        });
    }

    private String displayText(String text){
        if(text == null) return "";
        String trimmed = text.trim();
        if(trimmed.matches("@[A-Za-z0-9_.\\-]+")){
            return Core.bundle.get(trimmed.substring(1), text);
        }
        return text;
    }

    private float cloneOuterWidth(){
        return Math.min(Core.graphics.getWidth() / Scl.scl() / 2.35f, 430f);
    }

    private float cloneContentWidth(){
        return Math.max(220f, cloneOuterWidth() - 36f);
    }

    private float cloneLeftPad(){
        return Math.max(8f, Math.min(24f, Core.graphics.getWidth() / Scl.scl() * 0.025f));
    }

    private float clampedPad(float value){
        return Math.max(0f, Math.min(120f, value));
    }

    private interface PairCons{
        void get(String first, String second);
    }

    private static final class PendingGroup{
        int remaining;
        Runnable finished;

        PendingGroup(int remaining, Runnable finished){
            this.remaining = remaining;
            this.finished = finished;
            if(remaining == 0) finished.run();
        }

        void done(){
            remaining--;
            if(remaining <= 0){
                finished.run();
            }
        }
    }
}
