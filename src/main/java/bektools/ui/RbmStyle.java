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
import arc.scene.ui.ScrollPane;
import arc.scene.ui.Slider;
import arc.scene.ui.TextField;
import arc.scene.ui.layout.Table;
import arc.util.Scaling;
import mindustry.gen.Tex;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.SettingsMenuDialog;

import java.util.Locale;

public final class RbmStyle{
    private static final float defaultButtonHeight = 42f;
    private static final float dialogEdgePadding = 56f;
    private static final float settingsRowInset = 72f;
    private static final float dialogGap = 8f;
    private static final float dialogMinRightPaneWidth = 440f;
    private static final float settingMargin = 10f;
    private static final float settingTopPad = 6f;
    private static final float settingIconSize = 20f;

    private RbmStyle(){
    }

    public static float prefWidth(){
        return Math.min(Core.graphics.getWidth() - dialogEdgePadding, 980f);
    }

    public static float rowWidth(){
        return Math.max(320f, Math.min(prefWidth() - settingsRowInset, dialogContentWidth()));
    }

    public static float buttonHeight(){
        return defaultButtonHeight;
    }

    public static float dialogContentWidth(){
        return Math.max(420f, Math.min(Core.graphics.getWidth() - dialogEdgePadding, 960f));
    }

    public static TwoPaneLayout twoPaneLayout(float desiredLeftWidth){
        float total = dialogContentWidth();
        float left = Mathf.clamp(desiredLeftWidth, 220f, 300f);
        float right = total - left - dialogGap;
        if(right < dialogMinRightPaneWidth){
            left = Math.max(220f, total - dialogGap - dialogMinRightPaneWidth);
            right = total - left - dialogGap;
        }
        return new TwoPaneLayout(total, left, Math.max(240f, right), dialogGap);
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
        return "7f8cff";
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

    public static class HeaderSetting extends SettingsMenuDialog.SettingsTable.Setting{
        private final Drawable icon;

        public HeaderSetting(String title, Drawable icon){
            super("bektools-header");
            this.title = title;
            this.icon = icon;
        }

        @Override
        public void add(SettingsMenuDialog.SettingsTable table){
            float width = rowWidth();
            table.row();
            table.table(t -> {
                t.center();
                t.table(inner -> {
                    inner.background(VscodeSettingsStyle.headerBackground());
                    inner.margin(8f);
                    inner.center();
                    if(icon != null){
                        Image ic = inner.image(icon).size(20f).padRight(8f).get();
                        ic.setScaling(Scaling.fit);
                        ic.update(() -> ic.setColor(VscodeSettingsStyle.accentColor()));
                    }
                    inner.add(title).color(VscodeSettingsStyle.accentColor()).center().growX().minWidth(0f).wrap();
                }).growX();
            }).width(width).padTop(12f).padBottom(6f).center();
            table.row();
            table.image(Tex.whiteui).color(VscodeSettingsStyle.accentColor()).height(2f).width(width).padBottom(10f).center();
            table.row();
        }
    }

    public static class SubHeaderSetting extends SettingsMenuDialog.SettingsTable.Setting{
        private final String titleKeyOrText;

        public SubHeaderSetting(String titleKeyOrText){
            super("bektools-subheader");
            this.titleKeyOrText = titleKeyOrText;
        }

        @Override
        public void add(SettingsMenuDialog.SettingsTable table){
            table.row();
            table.add(titleKeyOrText.startsWith("@") ? Core.bundle.get(titleKeyOrText.substring(1)) : titleKeyOrText)
                .color(VscodeSettingsStyle.mutedColor())
                .padTop(6f)
                .padBottom(4f)
                .left()
                .growX()
                .minWidth(0f)
                .wrap();
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

        @Override
        public void add(SettingsMenuDialog.SettingsTable table){
            CheckBox box = new CheckBox(title);
            box.getLabel().setWrap(true);
            box.getLabelCell().growX().minWidth(0f);
            box.update(() -> box.setChecked(Core.settings.getBool(name, def)));
            box.changed(() -> {
                Core.settings.put(name, box.isChecked());
                if(changed != null) changed.get(box.isChecked());
            });

            table.table(VscodeSettingsStyle.cardBackground(), t -> {
                t.left().margin(settingMargin);
                if(icon != null) t.image(icon).size(settingIconSize).padRight(8f);
                t.add(box).left().growX().minWidth(0f);
            }).width(rowWidth()).left().padTop(settingTopPad);

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
            Slider slider = new Slider(min, max, step, false);
            slider.setValue(Core.settings.getInt(name, def));

            Label value = new Label("", Styles.outlineLabel);
            Table content = new Table();
            content.left();
            if(icon != null) content.image(icon).size(settingIconSize).padRight(8f);
            content.add(title, Styles.outlineLabel).left().growX().minWidth(0f).wrap();
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

            Table root = table.table(VscodeSettingsStyle.cardBackground(), t -> {
                t.left().margin(6f);
                t.stack(slider, content).growX().height(buttonHeight());
            }).width(rowWidth()).left().padTop(settingTopPad).get();
            addDesc(root);
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
            table.table(VscodeSettingsStyle.cardBackground(), t -> {
                t.left().margin(settingMargin);
                if(icon != null) t.image(icon).size(settingIconSize).padRight(8f);
                t.add(title).left().growX().minWidth(0f).wrap();
                TextField field = t.field(Core.settings.getString(name, def), text -> {
                    Core.settings.put(name, text);
                    if(changed != null) changed.get(text);
                }).growX().minWidth(140f).get();
                field.setMessageText(def);
                fieldRef[0] = field;
            }).width(rowWidth()).left().padTop(settingTopPad);

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

            table.table(VscodeSettingsStyle.cardBackground(), t -> {
                t.left().margin(settingMargin);
                if(icon != null) t.image(icon).size(settingIconSize).padRight(8f);
                t.add(title).left().growX().minWidth(0f).wrap();
                TextField field = t.field(String.valueOf(clamped), text -> {
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
                }).growX().minWidth(140f).get();
                field.setMessageText(String.valueOf(def));
                field.setFilter((f, c) -> Character.isDigit(c) || (c == '-' && f.getCursorPosition() == 0 && !f.getText().contains("-")));
                fieldRef[0] = field;
            }).width(rowWidth()).left().padTop(settingTopPad);

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
            table.table(VscodeSettingsStyle.cardBackground(), row -> {
                row.left().margin(6f);
                if(icon != null) row.image(icon).size(settingIconSize).padRight(8f);
                row.button(title, Styles.flatt, action).growX().height(buttonHeight()).padLeft(icon == null ? 8f : 0f);
            }).width(rowWidth()).left().padTop(settingTopPad);
            table.row();
        }
    }

    public static ScrollPane verticalPane(Table content){
        ScrollPane pane = new ScrollPane(content);
        pane.setFadeScrollBars(false);
        pane.setScrollingDisabled(true, false);
        pane.setOverscroll(false, false);
        return pane;
    }

    public static class IndentSetting extends SettingsMenuDialog.SettingsTable.Setting{
        private final float indent;

        public IndentSetting(float indent){
            super("bektools-indent");
            this.indent = indent;
        }

        @Override
        public void add(SettingsMenuDialog.SettingsTable table){
            table.defaults().padLeft(indent);
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

    public static class TwoPaneLayout{
        public final float totalWidth;
        public final float leftWidth;
        public final float rightWidth;
        public final float gap;

        public TwoPaneLayout(float totalWidth, float leftWidth, float rightWidth, float gap){
            this.totalWidth = totalWidth;
            this.leftWidth = leftWidth;
            this.rightWidth = rightWidth;
            this.gap = gap;
        }
    }
}
