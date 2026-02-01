package bektools.ui;

import arc.Core;
import arc.graphics.Color;
import arc.scene.style.Drawable;
import arc.scene.ui.Image;
import arc.scene.ui.layout.Table;
import arc.util.Scaling;
import mindustry.gen.Tex;
import mindustry.graphics.Pal;
import mindustry.ui.dialogs.SettingsMenuDialog;

import java.util.Locale;

public final class RbmStyle{
    private RbmStyle(){
    }

    public static float prefWidth(){
        // Match RBM: wide enough that long texts don't get clipped.
        return Math.min(Core.graphics.getWidth() / 1.02f, 980f);
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

    // Same key as RBM (kept here so BEK sections match RBM's theme color).
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
                hudColorCache.set(Pal.accent);
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
            float width = prefWidth();
            table.row();
            table.table(Tex.button, t -> {
                t.left().margin(10f);

                Image stripe = t.image(Tex.whiteui).size(4f, 18f).padRight(10f).get();
                stripe.setScaling(Scaling.stretch);
                stripe.update(() -> stripe.setColor(readHudColor()));

                if(icon != null){
                    Image ic = t.image(icon).size(18f).padRight(8f).get();
                    ic.update(() -> ic.setColor(readHudColor()));
                }

                t.add(title).color(Color.lightGray).left().growX().minWidth(0f).wrap();
            }).width(width).padTop(10f).padBottom(4f).left();
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
                .color(Color.gray)
                .padTop(8f)
                .padBottom(2f)
                .left()
                .growX()
                .minWidth(0f)
                .wrap();
            table.row();
        }
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
}

