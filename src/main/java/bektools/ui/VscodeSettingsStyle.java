package bektools.ui;

import arc.graphics.Color;
import arc.scene.style.Drawable;
import mindustry.gen.Tex;

public final class VscodeSettingsStyle{
    private static final Color bgPanel = Color.valueOf("111821");
    private static final Color bgHeader = Color.valueOf("172231");
    private static final Color bgCard = Color.valueOf("223246");
    private static final Color bgCardAlt = Color.valueOf("2b3d54");
    private static final Color accent = Color.valueOf("4ea8ff");
    private static final Color muted = Color.valueOf("a4b4c6");

    private VscodeSettingsStyle(){
    }

    public static Color accentColor(){
        return accent;
    }

    public static Color mutedColor(){
        return muted;
    }

    public static Drawable panelBackground(){
        return tint(bgPanel);
    }

    public static Drawable headerBackground(){
        return tint(bgHeader);
    }

    public static Drawable cardBackground(){
        return tint(bgCard);
    }

    public static Drawable cardAltBackground(){
        return tint(bgCardAlt);
    }

    private static Drawable tint(Color color){
        Drawable base = Tex.whiteui == null ? Tex.pane : Tex.whiteui;
        if(base instanceof arc.scene.style.TextureRegionDrawable){
            return ((arc.scene.style.TextureRegionDrawable)base).tint(color);
        }
        return base;
    }
}
