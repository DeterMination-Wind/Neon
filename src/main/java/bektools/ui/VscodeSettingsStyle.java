package bektools.ui;

import arc.graphics.Color;
import arc.scene.style.Drawable;
import arc.scene.style.TextureRegionDrawable;
import arc.scene.ui.Button;
import arc.scene.ui.TextButton;
import arc.struct.ObjectMap;
import mindustry.gen.Tex;
import mindustry.ui.Styles;

/** VSCode-like dark palette, tinted drawables, and button styles for Neon's unified settings workbench and overlay chrome. */
public final class VscodeSettingsStyle{
    private static final Color bgPanel = Color.valueOf("111821");
    private static final Color bgTabBar = Color.valueOf("0d141d");
    private static final Color bgSidebar = Color.valueOf("131c28");
    private static final Color bgHeader = Color.valueOf("172231");
    private static final Color bgCard = Color.valueOf("223246");
    private static final Color bgCardAlt = Color.valueOf("2b3d54");
    private static final Color bgSelected = Color.valueOf("1e3550");
    private static final Color bgSelectedHover = Color.valueOf("264366");
    private static final Color bgDivider = Color.valueOf("27394f");
    private static final Color accent = Color.valueOf("4ea8ff");
    private static final Color muted = Color.valueOf("a4b4c6");
    private static final Color textPrimary = Color.valueOf("edf4fc");

    private static final Color statusActive = Color.valueOf("4cd964");
    private static final Color statusPending = Color.valueOf("ffd166");
    private static final Color statusDisabled = Color.valueOf("7c8ea3");
    private static final Color statusFailed = Color.valueOf("ff6363");

    private static final Color bannerActive = Color.valueOf("152a22");
    private static final Color bannerPending = Color.valueOf("2f2815");
    private static final Color bannerDisabled = Color.valueOf("1a2430");
    private static final Color bannerFailed = Color.valueOf("33191d");

    private static Drawable cachedBase;
    private static final ObjectMap<Color, Drawable> tintedCache = new ObjectMap<>();

    private VscodeSettingsStyle(){
    }

    public static Color accentColor(){
        return accent;
    }

    public static Color mutedColor(){
        return muted;
    }

    public static Color primaryTextColor(){
        return textPrimary;
    }

    public static Color dividerColor(){
        return bgDivider;
    }

    public static Color statusActiveColor(){
        return statusActive;
    }

    public static Color statusPendingColor(){
        return statusPending;
    }

    public static Color statusDisabledColor(){
        return statusDisabled;
    }

    public static Color statusFailedColor(){
        return statusFailed;
    }

    public static Drawable panelBackground(){
        return tint(bgPanel);
    }

    public static Drawable tabBarBackground(){
        return tint(bgTabBar);
    }

    public static Drawable sidebarBackground(){
        return tint(bgSidebar);
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

    public static Drawable selectedBackground(){
        return tint(bgSelected);
    }

    public static Drawable dividerDrawable(){
        return tint(bgDivider);
    }

    public static Drawable bannerBackground(ModuleRuntimeState state){
        if(state == null) return tint(bannerDisabled);
        switch(state){
            case ACTIVE:
                return tint(bannerActive);
            case PENDING_DISABLE:
            case PENDING_ENABLE:
                return tint(bannerPending);
            case FAILED:
                return tint(bannerFailed);
            case DISABLED:
            default:
                return tint(bannerDisabled);
        }
    }

    public static Button.ButtonStyle sidebarItemStyle(boolean selected){
        Button.ButtonStyle style = new Button.ButtonStyle(
            selected ? selectedBackground() : sidebarBackground(),
            selected ? tint(bgSelectedHover) : cardAltBackground(),
            selected ? selectedBackground() : sidebarBackground()
        );
        style.over = selected ? tint(bgSelectedHover) : headerBackground();
        return style;
    }

    public static Button.ButtonStyle tabButtonStyle(boolean active){
        Button.ButtonStyle style = new Button.ButtonStyle(
            active ? selectedBackground() : tabBarBackground(),
            active ? tint(bgSelectedHover) : cardBackground(),
            active ? selectedBackground() : tabBarBackground()
        );
        style.over = active ? tint(bgSelectedHover) : headerBackground();
        return style;
    }

    public static TextButton.TextButtonStyle chipButtonStyle(boolean active){
        TextButton.TextButtonStyle style = new TextButton.TextButtonStyle();
        style.font = Styles.flatt.font;
        style.up = active ? selectedBackground() : headerBackground();
        style.over = active ? tint(bgSelectedHover) : cardBackground();
        style.down = cardAltBackground();
        style.checked = selectedBackground();
        style.fontColor = active ? accent : muted;
        style.overFontColor = textPrimary;
        style.downFontColor = textPrimary;
        return style;
    }

    public static Drawable tint(Color color){
        Drawable base = Tex.whiteui == null ? Tex.pane : Tex.whiteui;
        if(!(base instanceof TextureRegionDrawable) || color == null){
            return base;
        }
        if(base != cachedBase){
            cachedBase = base;
            tintedCache.clear();
        }
        Drawable cached = tintedCache.get(color);
        if(cached != null) return cached;
        Drawable created = ((TextureRegionDrawable)base).tint(color);
        tintedCache.put(color, created);
        return created;
    }
}
