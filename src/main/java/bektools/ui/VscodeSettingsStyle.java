package bektools.ui;

import arc.graphics.Color;
import arc.scene.style.Drawable;
import mindustry.gen.Tex;

/** VSCode-like dark palette and tinted drawables for Neon's unified settings pages and overlay chrome. */
public final class VscodeSettingsStyle{
    private static final Color bgPanel = Color.valueOf("111821");
    private static final Color bgHeader = Color.valueOf("172231");
    private static final Color bgCard = Color.valueOf("223246");
    private static final Color bgCardAlt = Color.valueOf("2b3d54");
    private static final Color accent = Color.valueOf("4ea8ff");
    private static final Color muted = Color.valueOf("a4b4c6");

    private static Drawable cachedBase;
    private static Drawable cachedPanelBg;
    private static Drawable cachedHeaderBg;
    private static Drawable cachedCardBg;
    private static Drawable cachedCardAltBg;

    private VscodeSettingsStyle(){
    }

    public static Color accentColor(){
        return accent;
    }

    public static Color mutedColor(){
        return muted;
    }

    public static Drawable panelBackground(){
        ensureDrawablesCached();
        return cachedPanelBg;
    }

    public static Drawable headerBackground(){
        ensureDrawablesCached();
        return cachedHeaderBg;
    }

    public static Drawable cardBackground(){
        ensureDrawablesCached();
        return cachedCardBg;
    }

    public static Drawable cardAltBackground(){
        ensureDrawablesCached();
        return cachedCardAltBg;
    }

    private static void ensureDrawablesCached(){
        Drawable base = Tex.whiteui == null ? Tex.pane : Tex.whiteui;
        if(base == cachedBase && cachedPanelBg != null) return;
        cachedBase = base;
        cachedPanelBg = tint(base, bgPanel);
        cachedHeaderBg = tint(base, bgHeader);
        cachedCardBg = tint(base, bgCard);
        cachedCardAltBg = tint(base, bgCardAlt);
    }

    private static Drawable tint(Drawable base, Color color){
        if(base instanceof arc.scene.style.TextureRegionDrawable){
            return ((arc.scene.style.TextureRegionDrawable)base).tint(color);
        }
        return base;
    }
}
