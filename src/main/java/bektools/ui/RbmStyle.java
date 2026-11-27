package bektools.ui;

import arc.Core;
import arc.func.Cons;
import arc.graphics.Color;
import arc.math.Mathf;
import arc.scene.event.Touchable;
import arc.scene.style.Drawable;
import arc.scene.ui.CheckBox;
import arc.scene.ui.Image;
import arc.scene.ui.Label;
import arc.scene.ui.Slider;
import arc.scene.ui.TextField;
import arc.scene.ui.layout.Scl;
import arc.scene.ui.layout.Stack;
import arc.scene.ui.layout.Table;
import arc.util.Scaling;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.SettingsMenuDialog;

import java.util.Locale;

/** Shared Neon flat-futuristic settings-row widgets (gold-line sub-headers, square checks, bar sliders, flat text rows, spacers). */
public final class RbmStyle{
    private static final float defaultButtonHeight = 40f;
    private static final float dialogEdgePadding = 56f;
    private static final float settingsRowInset = 72f;
    private static final float settingMargin = 9f;
    private static final float settingTopPad = 6f;
    private static final float settingIconSize = 19f;

    /** Optional container-scoped logical width override set by {@link NeonSubmodDetailPane} while building settings. */
    private static final ThreadLocal<Float> activeRowWidth = new ThreadLocal<>();

    private RbmStyle(){
    }

    public static void runWithRowWidth(float logicalWidth, Runnable action){
        Float previous = activeRowWidth.get();
        activeRowWidth.set(Math.max(240f, logicalWidth));
        try{
            action.run();
        }finally{
            if(previous == null){
                activeRowWidth.remove();
            }else{
                activeRowWidth.set(previous);
            }
        }
    }

    private static float logicalScreenWidth(){
        if(Core.graphics == null) return 1024f;
        float scale = Scl.scl(1f);
        return scale > 0f ? Core.graphics.getWidth() / scale : Core.graphics.getWidth();
    }

    public static float prefWidth(){
        return Math.max(300f, Math.min(logicalScreenWidth() - dialogEdgePadding, 980f));
    }

    public static float rowWidth(){
        Float scoped = activeRowWidth.get();
        if(scoped != null && scoped > 0f){
            return scoped;
        }
        return Math.max(260f, Math.min(prefWidth() - settingsRowInset, dialogContentWidth()));
    }

    /**
     * Inside NeonSubmodDetailPane, the host table already owns the exact pane width; returning
     * {@link Float#NEGATIVE_INFINITY} (Arc Cell's unset sentinel) lets rows grow flush to the host.
     */
    private static float cellMaxWidthCap(){
        return activeRowWidth.get() != null ? Float.NEGATIVE_INFINITY : rowWidth();
    }

    private static float inlineFieldWidth(){
        return Math.min(220f, Math.max(120f, rowWidth() * 0.32f));
    }

    public static float buttonHeight(){
        return defaultButtonHeight;
    }

    public static float dialogContentWidth(){
        return Math.max(300f, Math.min(logicalScreenWidth() - dialogEdgePadding, 960f));
    }

    private static String normalizeHex(String text){
        if(text == null) return defaultHudColorHex();
        String hex = text.trim();
        if(hex.startsWith("#")) hex = hex.substring(1);
        hex = hex.toLowerCase(Locale.ROOT);
        if(hex.length() > 6) hex = hex.substring(0, 6);
        while(hex.length() < 6) hex += "0";
        for(int i = 0; i < hex.length(); i++){
            char c = hex.charAt(i);
            boolean ok = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f');
            if(!ok) return defaultHudColorHex();
        }
        return hex;
    }

    private static final String keyHudColor = "rbm-hudcolor";

    private static String defaultHudColorHex(){
        return "f6d495";
    }

    private static final Color hudColorCache = new Color();
    private static String hudColorCacheRaw = null;
    private static String hudColorCacheHex = null;

    public static Color readHudColor(){
        String raw = Core.settings.getString(keyHudColor, defaultHudColorHex());
        if(raw == null) raw = defaultHudColorHex();
        if(raw.equals(hudColorCacheRaw)){
            return hudColorCache;
        }

        hudColorCacheRaw = raw;
        String hex = normalizeHex(raw);

        if(!hex.equals(hudColorCacheHex)){
            hudColorCacheHex = hex;
            try{
                int r = Integer.parseInt(hex.substring(0, 2), 16);
                int g = Integer.parseInt(hex.substring(2, 4), 16);
                int b = Integer.parseInt(hex.substring(4, 6), 16);
                hudColorCache.set(r / 255f, g / 255f, b / 255f, 1f);
            }catch(Throwable t){
                hudColorCache.set(VscodeSettingsStyle.accentColor());
            }
        }

        return hudColorCache;
    }

    public static class SubHeaderSetting extends SettingsMenuDialog.SettingsTable.Setting{
        private final String titleKeyOrText;

        public SubHeaderSetting(String titleKeyOrText){
            super("bektools-subheader");
            this.titleKeyOrText = titleKeyOrText;
        }

        @Override
        public void add(SettingsMenuDialog.SettingsTable table){
            String resolved = titleKeyOrText.startsWith("@")
                ? Core.bundle.get(titleKeyOrText.substring(1))
                : titleKeyOrText;
            table.row();
            table.table(VscodeSettingsStyle.sectionHeaderBackground(), head -> {
                head.left().margin(6f, 8f, 7f, 8f);
                Label lbl = new Label(resolved);
                lbl.setColor(VscodeSettingsStyle.accentColor());
                lbl.setWrap(true);
                head.add(lbl).left().growX().minWidth(0f);
            }).minWidth(0f).maxWidth(cellMaxWidthCap()).growX().fillX().left().padTop(10f).padBottom(2f).padLeft(0f).padRight(0f);
            table.row();
        }
    }

    public static class IconCheckSetting extends SettingsMenuDialog.SettingsTable.Setting{
        private final boolean def;
        private final Drawable icon;
        private final Cons<Boolean> changed;

        public IconCheckSetting(String name, boolean def, Drawable icon, Cons<Boolean> changed){
            this(name, null, def, icon, changed);
        }

        public IconCheckSetting(String name, String title, boolean def, Drawable icon, Cons<Boolean> changed){
            super(name);
            if(title != null) this.title = title;
            this.def = def;
            this.icon = icon;
            this.changed = changed;
        }

        Cons<Boolean> changedCallback(){
            return changed;
        }

        @Override
        public void add(SettingsMenuDialog.SettingsTable table){
            CheckBox box = new CheckBox(title, VscodeSettingsStyle.flatCheckBoxStyle());
            box.getLabel().setWrap(true);
            box.getLabelCell().padLeft(8f).growX().minWidth(0f);
            final Image[] iconRef = {null};
            box.update(() -> {
                boolean on = Core.settings.getBool(name, def);
                box.setChecked(on);
                if(iconRef[0] != null){
                    iconRef[0].setColor(on ? VscodeSettingsStyle.accentColor() : VscodeSettingsStyle.mutedColor());
                }
            });
            box.changed(() -> {
                Core.settings.put(name, box.isChecked());
                if(changed != null) changed.get(box.isChecked());
            });

            table.table(VscodeSettingsStyle.hudCardBackground(), t -> {
                t.left().margin(settingMargin);
                if(icon != null){
                    Image ic = new Image(icon);
                    ic.setScaling(Scaling.fit);
                    ic.setColor(Core.settings.getBool(name, def) ? VscodeSettingsStyle.accentColor() : VscodeSettingsStyle.mutedColor());
                    iconRef[0] = ic;
                    t.add(ic).size(settingIconSize).padRight(8f);
                }
                t.add(box).left().growX().minWidth(0f);
            }).minWidth(0f).maxWidth(cellMaxWidthCap()).growX().fillX().left().padTop(settingTopPad).padLeft(0f).padRight(0f);

            addDesc(box);
            table.row();
        }
    }

    public static class IconSliderSetting extends SettingsMenuDialog.SettingsTable.Setting{
        private final int def, min, max, step;
        private final Drawable icon;
        private final SettingsMenuDialog.StringProcessor sp;
        private final arc.func.Intc changed;

        public IconSliderSetting(String name, int def, int min, int max, int step, Drawable icon, SettingsMenuDialog.StringProcessor sp, arc.func.Intc changed){
            super(name);
            this.def = def;
            this.min = min;
            this.max = max;
            this.step = step;
            this.icon = icon;
            this.sp = sp;
            this.changed = changed;
        }

        @Override
        public void add(SettingsMenuDialog.SettingsTable table){
            Slider slider = new Slider(min, max, step, false, VscodeSettingsStyle.flatSliderStyle());
            slider.setValue(Core.settings.getInt(name, def));

            Label value = new Label("", Styles.outlineLabel);
            value.setColor(VscodeSettingsStyle.accentColor());

            Table content = new Table();
            content.left();
            if(icon != null){
                Image ic = new Image(icon);
                ic.setScaling(Scaling.fit);
                ic.setColor(VscodeSettingsStyle.accentColor());
                content.add(ic).size(settingIconSize).padRight(8f);
            }
            Label titleLbl = new Label(title, Styles.outlineLabel);
            titleLbl.setColor(VscodeSettingsStyle.primaryTextColor());
            titleLbl.setWrap(true);
            content.add(titleLbl).left().growX().minWidth(0f);
            content.add(value).padLeft(10f).right();
            content.margin(3f, 10f, 3f, 10f);
            content.touchable = Touchable.disabled;

            slider.changed(() -> {
                int v = (int)slider.getValue();
                Core.settings.put(name, v);
                value.setText(sp == null ? String.valueOf(v) : sp.get(v));
                if(changed != null) changed.get(v);
            });
            slider.change();

            Stack stack = table.stack(slider, content)
                .minWidth(0f)
                .maxWidth(cellMaxWidthCap())
                .growX()
                .fillX()
                .height(buttonHeight())
                .left()
                .padTop(settingTopPad)
                .padLeft(0f)
                .padRight(0f)
                .get();
            addDesc(stack);
            table.row();
        }
    }

    public static class IconTextSetting extends SettingsMenuDialog.SettingsTable.Setting{
        private final String def;
        private final Drawable icon;
        private final Cons<String> changed;

        public IconTextSetting(String name, String def, Drawable icon, Cons<String> changed){
            super(name);
            this.def = def;
            this.icon = icon;
            this.changed = changed;
        }

        @Override
        public void add(SettingsMenuDialog.SettingsTable table){
            final TextField[] fieldRef = {null};
            float fw = inlineFieldWidth();
            table.table(VscodeSettingsStyle.hudCardBackground(), t -> {
                t.left().margin(settingMargin);
                if(icon != null){
                    Image ic = new Image(icon);
                    ic.setScaling(Scaling.fit);
                    ic.setColor(VscodeSettingsStyle.accentColor());
                    t.add(ic).size(settingIconSize).padRight(8f);
                }
                Label lbl = new Label(title);
                lbl.setColor(VscodeSettingsStyle.primaryTextColor());
                lbl.setWrap(true);
                t.add(lbl).left().growX().minWidth(0f).padRight(10f);
                TextField field = t.field(Core.settings.getString(name, def), VscodeSettingsStyle.flatFieldStyle(), text -> {
                    Core.settings.put(name, text);
                    if(changed != null) changed.get(text);
                }).width(fw).height(34f).right().get();
                field.setMessageText(def);
                fieldRef[0] = field;
            }).minWidth(0f).maxWidth(cellMaxWidthCap()).growX().fillX().left().padTop(settingTopPad).padLeft(0f).padRight(0f);

            if(fieldRef[0] != null) addDesc(fieldRef[0]);
            table.row();
        }
    }

    public static class IconIntFieldSetting extends SettingsMenuDialog.SettingsTable.Setting{
        private final int def;
        private final int min;
        private final int max;
        private final Drawable icon;
        private final arc.func.Intc changed;

        public IconIntFieldSetting(String name, int def, int min, int max, Drawable icon, arc.func.Intc changed){
            super(name);
            this.def = def;
            this.min = min;
            this.max = max;
            this.icon = icon;
            this.changed = changed;
        }

        @Override
        public void add(SettingsMenuDialog.SettingsTable table){
            final TextField[] fieldRef = {null};
            final boolean[] updatingText = {false};
            int stored = Core.settings.getInt(name, def);
            int clamped = Mathf.clamp(stored, min, max);
            if(clamped != stored) Core.settings.put(name, clamped);
            float fw = inlineFieldWidth();

            table.table(VscodeSettingsStyle.hudCardBackground(), t -> {
                t.left().margin(settingMargin);
                if(icon != null){
                    Image ic = new Image(icon);
                    ic.setScaling(Scaling.fit);
                    ic.setColor(VscodeSettingsStyle.accentColor());
                    t.add(ic).size(settingIconSize).padRight(8f);
                }
                Label lbl = new Label(title);
                lbl.setColor(VscodeSettingsStyle.primaryTextColor());
                lbl.setWrap(true);
                t.add(lbl).left().growX().minWidth(0f).padRight(10f);
                TextField field = t.field(String.valueOf(clamped), VscodeSettingsStyle.flatFieldStyle(), text -> {
                    if(updatingText[0]) return;
                    int parsed;
                    try{
                        parsed = Integer.parseInt(text.trim());
                    }catch(Throwable ignored){
                        return;
                    }
                    int value = Mathf.clamp(parsed, min, max);
                    Core.settings.put(name, value);
                    if(changed != null) changed.get(value);
                    String normalized = String.valueOf(value);
                    if(!normalized.equals(fieldRef[0].getText())){
                        updatingText[0] = true;
                        fieldRef[0].setText(normalized);
                        fieldRef[0].setCursorPosition(normalized.length());
                        updatingText[0] = false;
                    }
                }).width(fw).height(34f).right().get();
                field.setMessageText(String.valueOf(def));
                field.setFilter((f, c) -> Character.isDigit(c) || (c == '-' && f.getCursorPosition() == 0 && !f.getText().contains("-")));
                fieldRef[0] = field;
            }).minWidth(0f).maxWidth(cellMaxWidthCap()).growX().fillX().left().padTop(settingTopPad).padLeft(0f).padRight(0f);

            if(fieldRef[0] != null) addDesc(fieldRef[0]);
            table.row();
        }
    }

    public static class ActionButtonSetting extends SettingsMenuDialog.SettingsTable.Setting{
        private final Drawable icon;
        private final Runnable action;

        public ActionButtonSetting(String name, Drawable icon, Runnable action){
            this(name, null, icon, action);
        }

        public ActionButtonSetting(String name, String title, Drawable icon, Runnable action){
            super(name);
            if(title != null) this.title = title;
            this.icon = icon;
            this.action = action;
        }

        @Override
        public void add(SettingsMenuDialog.SettingsTable table){
            table.table(VscodeSettingsStyle.hudCardBackground(), row -> {
                row.left().margin(6f);
                if(icon != null){
                    Image ic = new Image(icon);
                    ic.setScaling(Scaling.fit);
                    ic.setColor(VscodeSettingsStyle.accentColor());
                    row.add(ic).size(settingIconSize).padRight(8f).padLeft(4f);
                }
                row.button(title, VscodeSettingsStyle.flatButtonStyle(), action)
                    .growX()
                    .minWidth(0f)
                    .height(36f)
                    .padLeft(icon == null ? 4f : 0f);
            }).minWidth(0f).maxWidth(cellMaxWidthCap()).growX().fillX().left().padTop(settingTopPad).padLeft(0f).padRight(0f);
            table.row();
        }
    }

    public static class SpacerSetting extends SettingsMenuDialog.SettingsTable.Setting{
        private final float height;

        public SpacerSetting(float height){
            super("bektools-spacer");
            this.height = height;
        }

        @Override
        public void add(SettingsMenuDialog.SettingsTable table){
            table.row();
            table.add().height(height);
            table.row();
        }
    }
}
