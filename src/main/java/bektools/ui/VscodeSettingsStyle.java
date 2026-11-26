package bektools.ui;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Font;
import arc.scene.style.BaseDrawable;
import arc.scene.style.Drawable;
import arc.scene.style.TextureRegionDrawable;
import arc.scene.ui.Button;
import arc.scene.ui.CheckBox;
import arc.scene.ui.ImageButton;
import arc.scene.ui.Slider;
import arc.scene.ui.TextButton;
import arc.scene.ui.TextField;
import arc.scene.ui.layout.Scl;
import arc.struct.ObjectMap;
import mindustry.gen.Tex;
import mindustry.ui.Fonts;
import mindustry.ui.Styles;

/**
 * Translucent obsidian-and-gold flat futuristic UI theme for Neon's unified settings workbench.
 *
 * <p>Visual specification:
 * <ul>
 *   <li>Semi-transparent neutral obsidian/charcoal surfaces so the Mindustry backdrop breathes through.</li>
 *   <li>Strictly zero blue hues anywhere in surfaces, borders, text, or status semantics.</li>
 *   <li>Warm Mindustry gold ({@code #f6d495}) crisp rectangular selection frames, group dividers, and section rules.</li>
 *   <li>Sharp geometric flat-futuristic controls (square checkboxes, HUD-framed bar sliders, underline search input, HUD corner ticks).</li>
 * </ul>
 * Kept under the historical {@code VscodeSettingsStyle} class name for binary compatibility across Neon features.
 */
public final class VscodeSettingsStyle{
    // Semi-transparent neutral obsidian & charcoal surfaces (R >= G >= B, zero blue cast)
    private static final Color bgPanel = new Color(0.035f, 0.035f, 0.035f, 0.56f);
    private static final Color bgTabBar = new Color(0.025f, 0.025f, 0.025f, 0.68f);
    private static final Color bgSidebar = new Color(0.045f, 0.045f, 0.045f, 0.52f);
    private static final Color bgHeader = new Color(0.075f, 0.075f, 0.072f, 0.66f);
    private static final Color bgTile = new Color(0.098f, 0.098f, 0.098f, 0.76f);      // #191919 translucent
    private static final Color bgCard = new Color(0.098f, 0.098f, 0.098f, 0.70f);      // #191919 translucent
    private static final Color bgCardAlt = new Color(0.155f, 0.148f, 0.132f, 0.82f);   // warm graphite hover/down
    private static final Color bgSelected = new Color(0.125f, 0.105f, 0.068f, 0.84f);  // dark gold-tinted active fill
    private static final Color bgSelectedHover = new Color(0.175f, 0.148f, 0.095f, 0.88f);
    private static final Color bgField = new Color(0.055f, 0.055f, 0.055f, 0.72f);
    private static final Color bgFieldFocus = new Color(0.105f, 0.092f, 0.065f, 0.82f);
    private static final Color bgSearchFocus = new Color(0.965f, 0.831f, 0.584f, 0.07f);

    // Borders, rules & typography (sampled from reference #f6d495 gold & #454543 neutral line)
    private static final Color accent = Color.valueOf("f6d495");
    private static final Color accentBright = Color.valueOf("ffe6b8");
    private static final Color accentSoft = new Color(0.965f, 0.831f, 0.584f, 0.34f);
    private static final Color accentSelection = new Color(0.965f, 0.831f, 0.584f, 0.28f);
    private static final Color accentCorner = new Color(0.965f, 0.831f, 0.584f, 0.42f);
    private static final Color bgDivider = new Color(0.271f, 0.271f, 0.263f, 0.88f);   // #454543
    private static final Color borderSubtle = new Color(0.235f, 0.231f, 0.220f, 0.78f);
    private static final Color borderHover = new Color(0.965f, 0.831f, 0.584f, 0.62f);
    private static final Color muted = Color.valueOf("9e9b93");
    private static final Color textPrimary = Color.valueOf("f5f4f0");
    private static final Color textDisabled = Color.valueOf("6e6c66");

    // Blue-free lifecycle status colors (warm gold, industrial amber, neutral graphite, vermilion)
    private static final Color statusActive = Color.valueOf("f6d495");
    private static final Color statusPending = Color.valueOf("ff9a47");
    private static final Color statusDisabled = Color.valueOf("75736e");
    private static final Color statusFailed = Color.valueOf("ff5c52");

    private static final Color bannerActive = new Color(0.155f, 0.128f, 0.068f, 0.66f);
    private static final Color bannerPending = new Color(0.175f, 0.105f, 0.045f, 0.68f);
    private static final Color bannerDisabled = new Color(0.088f, 0.088f, 0.086f, 0.62f);
    private static final Color bannerFailed = new Color(0.185f, 0.068f, 0.062f, 0.70f);

    private static final Drawable panelBgDrawable = new FlatFrameDrawable(bgPanel, borderSubtle, 1f, accentCorner, 7f);
    private static final Drawable tabBarBgDrawable = new FlatUnderlineDrawable(bgTabBar, bgDivider, 1f, false);
    private static final Drawable sidebarBgDrawable = new FlatFrameDrawable(bgSidebar, null, 0f, null, 0f);
    private static final Drawable headerBgDrawable = new FlatFrameDrawable(bgHeader, borderSubtle, 1f, accentCorner, 5f);
    private static final Drawable sectionHeaderBgDrawable = new FlatUnderlineDrawable(bgHeader, accent, 1.8f, false);
    private static final Drawable titleRuleBgDrawable = new FlatUnderlineDrawable(null, accent, 2f, false);
    private static final Drawable cardBgDrawable = new FlatFrameDrawable(bgCard, borderSubtle, 1f, null, 0f);
    private static final Drawable hudCardBgDrawable = new FlatFrameDrawable(bgCard, borderSubtle, 1f, accentCorner, 5f);
    private static final Drawable cardAltBgDrawable = new FlatFrameDrawable(bgCardAlt, borderHover, 1f, accent, 5f);
    private static final Drawable selectedBgDrawable = new FlatFrameDrawable(bgSelected, accent, 2f, null, 0f);
    private static final Drawable selectedHoverBgDrawable = new FlatFrameDrawable(bgSelectedHover, accentBright, 2f, null, 0f);
    private static final Drawable tileIdleBgDrawable = new FlatFrameDrawable(bgTile, borderSubtle, 1f, null, 0f);
    private static final Drawable tileHoverBgDrawable = new FlatFrameDrawable(bgCardAlt, borderHover, 1.4f, null, 0f);
    private static final Drawable sidebarIdleDrawable = new FlatFrameDrawable(new Color(0.08f, 0.08f, 0.08f, 0.54f), new Color(0.20f, 0.20f, 0.19f, 0.62f), 1f, null, 0f);
    private static final Drawable sidebarSelectedDrawable = new FlatFrameDrawable(bgSelected, accent, 1.6f, null, 0f);
    private static final Drawable sidebarSelectedHoverDrawable = new FlatFrameDrawable(bgSelectedHover, accentBright, 1.8f, null, 0f);

    private static final Drawable bannerActiveDrawable = new FlatBannerDrawable(bannerActive, statusActive, new Color(0.965f, 0.831f, 0.584f, 0.45f));
    private static final Drawable bannerPendingDrawable = new FlatBannerDrawable(bannerPending, statusPending, new Color(1f, 0.604f, 0.278f, 0.45f));
    private static final Drawable bannerDisabledDrawable = new FlatBannerDrawable(bannerDisabled, statusDisabled, borderSubtle);
    private static final Drawable bannerFailedDrawable = new FlatBannerDrawable(bannerFailed, statusFailed, new Color(1f, 0.361f, 0.322f, 0.48f));

    private static final Drawable dividerLineDrawable = new FlatSolidDrawable(bgDivider, 1f, 1f);
    private static final Drawable goldRuleLineDrawable = new FlatSolidDrawable(accent, 2f, 2f);
    private static final Drawable subtleGoldLineDrawable = new FlatSolidDrawable(accentSoft, 1f, 1f);

    private static final Drawable checkOffDrawable = new FlatCheckDrawable(false, false, false);
    private static final Drawable checkOverDrawable = new FlatCheckDrawable(false, true, false);
    private static final Drawable checkOnDrawable = new FlatCheckDrawable(true, false, false);
    private static final Drawable checkOnOverDrawable = new FlatCheckDrawable(true, true, false);
    private static final Drawable checkOffDisabledDrawable = new FlatCheckDrawable(false, false, true);
    private static final Drawable checkOnDisabledDrawable = new FlatCheckDrawable(true, false, true);

    private static final float sliderRowHeightDp = 40f;
    private static final Drawable sliderTrackDrawable = new FlatSliderTrackDrawable(bgCard, borderSubtle, accentCorner);
    private static final Drawable sliderFillDrawable = new FlatSliderFillDrawable(accentSoft, accent);
    private static final Drawable sliderKnobIdleDrawable = new FlatSliderKnobDrawable(accent);
    private static final Drawable sliderKnobActiveDrawable = new FlatSliderKnobDrawable(accentBright);
    private static final Drawable sliderKnobDisabledDrawable = new FlatSliderKnobDrawable(statusDisabled);

    private static final Drawable searchIdleDrawable = new FlatPaddedDrawable(
        new FlatUnderlineDrawable(null, bgDivider, 1.8f, false), 4f, 4f, 5f, 6f, 30f
    );
    private static final Drawable searchFocusDrawable = new FlatPaddedDrawable(
        new FlatUnderlineDrawable(bgSearchFocus, accent, 2f, false), 4f, 4f, 5f, 6f, 30f
    );
    private static final Drawable fieldIdleDrawable = new FlatPaddedDrawable(
        new FlatFrameDrawable(bgField, bgDivider, 1f, null, 0f), 8f, 8f, 6f, 6f, 32f
    );
    private static final Drawable fieldFocusDrawable = new FlatPaddedDrawable(
        new FlatFrameDrawable(bgFieldFocus, accent, 1.5f, null, 0f), 8f, 8f, 6f, 6f, 32f
    );
    private static final Drawable fieldDisabledDrawable = new FlatPaddedDrawable(
        new FlatFrameDrawable(bannerDisabled, borderSubtle, 1f, null, 0f), 8f, 8f, 6f, 6f, 32f
    );
    private static final Drawable cursorDrawable = new FlatSolidDrawable(accent, 2f, 16f);
    private static final Drawable selectionDrawable = new FlatSolidDrawable(accentSelection, 2f, 16f);

    private static Slider.SliderStyle sharedSliderStyle;
    private static CheckBox.CheckBoxStyle sharedCheckBoxStyle;
    private static CheckBox.CheckBoxStyle sharedHudCardCheckBoxStyle;
    private static TextField.TextFieldStyle sharedSearchFieldStyle;
    private static TextField.TextFieldStyle sharedFieldStyle;
    private static TextButton.TextButtonStyle sharedFlatButtonStyle;
    private static TextButton.TextButtonStyle sharedFlatToggleButtonStyle;
    private static TextButton.TextButtonStyle sharedHudCardButtonStyle;
    private static TextButton.TextButtonStyle sharedHudCardToggleButtonStyle;

    private static Drawable cachedBase;
    private static final ObjectMap<Color, Drawable> tintedCache = new ObjectMap<>();

    private VscodeSettingsStyle(){
    }

    public static Color accentColor(){
        return accent;
    }

    public static Color accentBrightColor(){
        return accentBright;
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
        return panelBgDrawable;
    }

    public static Drawable tabBarBackground(){
        return tabBarBgDrawable;
    }

    public static Drawable sidebarBackground(){
        return sidebarBgDrawable;
    }

    public static Drawable headerBackground(){
        return headerBgDrawable;
    }

    public static Drawable sectionHeaderBackground(){
        return sectionHeaderBgDrawable;
    }

    public static Drawable titleRuleBackground(){
        return titleRuleBgDrawable;
    }

    public static Drawable cardBackground(){
        return cardBgDrawable;
    }

    public static Drawable hudCardBackground(){
        return hudCardBgDrawable;
    }

    public static Drawable cardAltBackground(){
        return cardAltBgDrawable;
    }

    public static Drawable selectedBackground(){
        return selectedBgDrawable;
    }

    public static Drawable dividerDrawable(){
        return dividerLineDrawable;
    }

    public static Drawable goldRuleDrawable(){
        return goldRuleLineDrawable;
    }

    public static Drawable subtleGoldRuleDrawable(){
        return subtleGoldLineDrawable;
    }

    public static Drawable bannerBackground(ModuleRuntimeState state){
        if(state == null) return bannerDisabledDrawable;
        switch(state){
            case ACTIVE:
                return bannerActiveDrawable;
            case PENDING_DISABLE:
            case PENDING_ENABLE:
                return bannerPendingDrawable;
            case FAILED:
                return bannerFailedDrawable;
            case DISABLED:
            default:
                return bannerDisabledDrawable;
        }
    }

    public static Button.ButtonStyle sidebarItemStyle(boolean selected){
        Button.ButtonStyle style = new Button.ButtonStyle(
            selected ? sidebarSelectedDrawable : sidebarIdleDrawable,
            selected ? sidebarSelectedHoverDrawable : cardAltBgDrawable,
            selected ? sidebarSelectedDrawable : sidebarIdleDrawable
        );
        style.over = selected ? sidebarSelectedHoverDrawable : tileHoverBgDrawable;
        return style;
    }

    /** Square/rectangular flat tile style matching the black-and-gold toolbar reference. */
    public static Button.ButtonStyle toolbarTileStyle(boolean active){
        Button.ButtonStyle style = new Button.ButtonStyle(
            active ? selectedBgDrawable : tileIdleBgDrawable,
            active ? selectedHoverBgDrawable : cardAltBgDrawable,
            active ? selectedBgDrawable : tileIdleBgDrawable
        );
        style.over = active ? selectedHoverBgDrawable : tileHoverBgDrawable;
        return style;
    }

    public static Button.ButtonStyle tabButtonStyle(boolean active){
        return toolbarTileStyle(active);
    }

    public static TextButton.TextButtonStyle chipButtonStyle(boolean active){
        TextButton.TextButtonStyle style = new TextButton.TextButtonStyle();
        style.font = uiFont();
        style.up = active ? selectedBgDrawable : tileIdleBgDrawable;
        style.over = active ? selectedHoverBgDrawable : tileHoverBgDrawable;
        style.down = selectedHoverBgDrawable;
        style.checked = selectedBgDrawable;
        style.fontColor = active ? accent : muted;
        style.overFontColor = active ? accentBright : textPrimary;
        style.downFontColor = accentBright;
        style.disabledFontColor = textDisabled;
        return style;
    }

    public static TextButton.TextButtonStyle flatButtonStyle(){
        Font font = uiFont();
        if(sharedFlatButtonStyle != null && sharedFlatButtonStyle.font == font){
            return sharedFlatButtonStyle;
        }
        TextButton.TextButtonStyle style = new TextButton.TextButtonStyle();
        style.font = font;
        style.up = tileIdleBgDrawable;
        style.over = tileHoverBgDrawable;
        style.down = selectedBgDrawable;
        style.disabled = bannerDisabledDrawable;
        style.fontColor = textPrimary;
        style.overFontColor = accent;
        style.downFontColor = accentBright;
        style.disabledFontColor = textDisabled;
        sharedFlatButtonStyle = style;
        return style;
    }

    public static TextButton.TextButtonStyle flatToggleButtonStyle(){
        Font font = uiFont();
        if(sharedFlatToggleButtonStyle != null && sharedFlatToggleButtonStyle.font == font){
            return sharedFlatToggleButtonStyle;
        }
        TextButton.TextButtonStyle style = new TextButton.TextButtonStyle(flatButtonStyle());
        style.checked = selectedBgDrawable;
        style.checkedOver = selectedHoverBgDrawable;
        style.checkedFontColor = accent;
        style.checkedOverFontColor = accentBright;
        sharedFlatToggleButtonStyle = style;
        return style;
    }

    /** Full-row HUD card button style for standalone top-level button rows inside sub-mod settings. */
    public static TextButton.TextButtonStyle hudCardButtonStyle(){
        Font font = uiFont();
        if(sharedHudCardButtonStyle != null && sharedHudCardButtonStyle.font == font){
            return sharedHudCardButtonStyle;
        }
        TextButton.TextButtonStyle style = new TextButton.TextButtonStyle();
        style.font = font;
        style.up = hudCardBgDrawable;
        style.over = cardAltBgDrawable;
        style.down = selectedBgDrawable;
        style.disabled = bannerDisabledDrawable;
        style.fontColor = textPrimary;
        style.overFontColor = accent;
        style.downFontColor = accentBright;
        style.disabledFontColor = textDisabled;
        sharedHudCardButtonStyle = style;
        return style;
    }

    public static TextButton.TextButtonStyle hudCardToggleButtonStyle(){
        Font font = uiFont();
        if(sharedHudCardToggleButtonStyle != null && sharedHudCardToggleButtonStyle.font == font){
            return sharedHudCardToggleButtonStyle;
        }
        TextButton.TextButtonStyle style = new TextButton.TextButtonStyle(hudCardButtonStyle());
        style.checked = selectedBgDrawable;
        style.checkedOver = selectedHoverBgDrawable;
        style.checkedFontColor = accent;
        style.checkedOverFontColor = accentBright;
        sharedHudCardToggleButtonStyle = style;
        return style;
    }

    public static ImageButton.ImageButtonStyle flatIconButtonStyle(){
        ImageButton.ImageButtonStyle style = new ImageButton.ImageButtonStyle();
        style.up = null;
        style.over = tileHoverBgDrawable;
        style.down = selectedBgDrawable;
        style.disabled = null;
        style.imageUpColor = muted;
        style.imageOverColor = accent;
        style.imageDownColor = accentBright;
        style.imageDisabledColor = textDisabled;
        return style;
    }

    public static ImageButton.ImageButtonStyle flatTileIconButtonStyle(){
        ImageButton.ImageButtonStyle style = new ImageButton.ImageButtonStyle();
        style.up = tileIdleBgDrawable;
        style.over = tileHoverBgDrawable;
        style.down = selectedBgDrawable;
        style.checked = selectedBgDrawable;
        style.checkedOver = selectedHoverBgDrawable;
        style.imageUpColor = textPrimary;
        style.imageOverColor = accent;
        style.imageDownColor = accentBright;
        style.imageCheckedColor = accent;
        style.imageDisabledColor = textDisabled;
        return style;
    }

    public static CheckBox.CheckBoxStyle flatCheckBoxStyle(){
        Font font = uiFont();
        if(sharedCheckBoxStyle != null && sharedCheckBoxStyle.font == font){
            return sharedCheckBoxStyle;
        }
        CheckBox.CheckBoxStyle style = new CheckBox.CheckBoxStyle();
        style.font = font;
        style.fontColor = textPrimary;
        style.overFontColor = accentBright;
        style.downFontColor = accent;
        style.disabledFontColor = textDisabled;
        style.checkboxOff = checkOffDrawable;
        style.checkboxOver = checkOverDrawable;
        style.checkboxOn = checkOnDrawable;
        style.checkboxOnOver = checkOnOverDrawable;
        style.checkboxOffDisabled = checkOffDisabledDrawable;
        style.checkboxOnDisabled = checkOnDisabledDrawable;
        sharedCheckBoxStyle = style;
        return style;
    }

    /** Full-row HUD card checkbox style for bare top-level CheckBox rows (e.g. BetterHotKey). */
    public static CheckBox.CheckBoxStyle hudCardCheckBoxStyle(){
        Font font = uiFont();
        if(sharedHudCardCheckBoxStyle != null && sharedHudCardCheckBoxStyle.font == font){
            return sharedHudCardCheckBoxStyle;
        }
        CheckBox.CheckBoxStyle style = new CheckBox.CheckBoxStyle();
        style.font = font;
        style.up = hudCardBgDrawable;
        style.over = cardAltBgDrawable;
        style.down = cardAltBgDrawable;
        style.fontColor = textPrimary;
        style.overFontColor = accentBright;
        style.downFontColor = accent;
        style.disabledFontColor = textDisabled;
        style.checkboxOff = checkOffDrawable;
        style.checkboxOver = checkOverDrawable;
        style.checkboxOn = checkOnDrawable;
        style.checkboxOnOver = checkOnOverDrawable;
        style.checkboxOffDisabled = checkOffDisabledDrawable;
        style.checkboxOnDisabled = checkOnDisabledDrawable;
        sharedHudCardCheckBoxStyle = style;
        return style;
    }

    public static Slider.SliderStyle flatSliderStyle(){
        if(sharedSliderStyle != null){
            return sharedSliderStyle;
        }
        Slider.SliderStyle style = new Slider.SliderStyle();
        style.background = sliderTrackDrawable;
        style.disabledBackground = bannerDisabledDrawable;
        style.knobBefore = sliderFillDrawable;
        style.disabledKnobBefore = bannerDisabledDrawable;
        style.knob = sliderKnobIdleDrawable;
        style.knobOver = sliderKnobActiveDrawable;
        style.knobDown = sliderKnobActiveDrawable;
        style.disabledKnob = sliderKnobDisabledDrawable;
        sharedSliderStyle = style;
        return style;
    }

    /** Minimalist flat search field with bottom horizontal rule (idle #454543 -> focused #f6d495 gold). */
    public static TextField.TextFieldStyle flatSearchFieldStyle(){
        Font font = uiFont();
        if(sharedSearchFieldStyle != null && sharedSearchFieldStyle.font == font){
            return sharedSearchFieldStyle;
        }
        TextField.TextFieldStyle style = new TextField.TextFieldStyle();
        style.font = font;
        style.messageFont = font;
        style.fontColor = textPrimary;
        style.focusedFontColor = accentBright;
        style.disabledFontColor = textDisabled;
        style.messageFontColor = muted;
        style.background = searchIdleDrawable;
        style.focusedBackground = searchFocusDrawable;
        style.disabledBackground = searchIdleDrawable;
        style.invalidBackground = bannerFailedDrawable;
        style.cursor = cursorDrawable;
        style.selection = selectionDrawable;
        sharedSearchFieldStyle = style;
        return style;
    }

    public static TextField.TextFieldStyle flatFieldStyle(){
        Font font = uiFont();
        if(sharedFieldStyle != null && sharedFieldStyle.font == font){
            return sharedFieldStyle;
        }
        TextField.TextFieldStyle style = new TextField.TextFieldStyle();
        style.font = font;
        style.messageFont = font;
        style.fontColor = textPrimary;
        style.focusedFontColor = accentBright;
        style.disabledFontColor = textDisabled;
        style.messageFontColor = muted;
        style.background = fieldIdleDrawable;
        style.focusedBackground = fieldFocusDrawable;
        style.disabledBackground = fieldDisabledDrawable;
        style.invalidBackground = bannerFailedDrawable;
        style.cursor = cursorDrawable;
        style.selection = selectionDrawable;
        sharedFieldStyle = style;
        return style;
    }

    /** Returns true when a color carries a noticeable blue/cyan/indigo hue forbidden by the settings theme. */
    public static boolean isBlueHue(Color color){
        if(color == null || color.a <= 0.01f) return false;
        return color.b > color.r + 0.03f && color.b > color.g + 0.015f && color.b > 0.08f;
    }

    private static Font uiFont(){
        if(Styles.flatt != null && Styles.flatt.font != null){
            return Styles.flatt.font;
        }
        if(Fonts.def != null){
            return Fonts.def;
        }
        if(Core.scene != null){
            TextButton.TextButtonStyle fallback = Core.scene.getStyle(TextButton.TextButtonStyle.class);
            if(fallback != null && fallback.font != null){
                return fallback.font;
            }
        }
        return Fonts.outline;
    }

    public static Drawable tint(Color color){
        Drawable base = Tex.whiteui == null ? Tex.pane : Tex.whiteui;
        if(!(base instanceof TextureRegionDrawable) || color == null){
            return base != null ? base : new FlatSolidDrawable(color == null ? Color.clear : color, 1f, 1f);
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

    private static void drawRectColor(Color c, float parentAlpha, float x, float y, float w, float h){
        if(c == null || w <= 0f || h <= 0f) return;
        float alpha = c.a * parentAlpha;
        if(alpha <= 0.001f) return;
        Draw.color(c.r, c.g, c.b, alpha);
        Fill.crect(x, y, w, h);
    }

    private static void drawCrispBorder(Color c, float parentAlpha, float x, float y, float w, float h, float thicknessPx){
        if(c == null || thicknessPx <= 0f || w <= 0f || h <= 0f) return;
        float t = Math.min(thicknessPx, Math.min(w, h) * 0.5f);
        drawRectColor(c, parentAlpha, x, y, w, t);
        drawRectColor(c, parentAlpha, x, y + h - t, w, t);
        if(h > t * 2f){
            drawRectColor(c, parentAlpha, x, y + t, t, h - t * 2f);
            drawRectColor(c, parentAlpha, x + w - t, y + t, t, h - t * 2f);
        }
    }

    private static void drawCornerBrackets(Color c, float parentAlpha, float x, float y, float w, float h, float lenPx, float thickPx){
        if(c == null || lenPx <= 0f || thickPx <= 0f || w < lenPx * 2f || h < lenPx * 2f) return;
        // Bottom-left
        drawRectColor(c, parentAlpha, x, y, lenPx, thickPx);
        drawRectColor(c, parentAlpha, x, y + thickPx, thickPx, lenPx - thickPx);
        // Bottom-right
        drawRectColor(c, parentAlpha, x + w - lenPx, y, lenPx, thickPx);
        drawRectColor(c, parentAlpha, x + w - thickPx, y + thickPx, thickPx, lenPx - thickPx);
        // Top-left
        drawRectColor(c, parentAlpha, x, y + h - thickPx, lenPx, thickPx);
        drawRectColor(c, parentAlpha, x, y + h - lenPx, thickPx, lenPx - thickPx);
        // Top-right
        drawRectColor(c, parentAlpha, x + w - lenPx, y + h - thickPx, lenPx, thickPx);
        drawRectColor(c, parentAlpha, x + w - thickPx, y + h - lenPx, thickPx, lenPx - thickPx);
    }

    private static final class FlatSolidDrawable extends BaseDrawable{
        private final Color color;
        private final float minW;
        private final float minH;

        FlatSolidDrawable(Color color, float minW, float minH){
            this.color = color;
            this.minW = minW;
            this.minH = minH;
        }

        @Override
        public float getMinWidth(){
            return Scl.scl(minW);
        }

        @Override
        public float getMinHeight(){
            return Scl.scl(minH);
        }

        @Override
        public void draw(float x, float y, float width, float height){
            float parentAlpha = Draw.getColorAlpha();
            float prev = Draw.getColorPacked();
            drawRectColor(color, parentAlpha, x, y, width, height);
            Draw.color(prev);
        }
    }

    private static final class FlatFrameDrawable extends BaseDrawable{
        private final Color fill;
        private final Color border;
        private final float borderDp;
        private final Color cornerColor;
        private final float cornerLenDp;

        FlatFrameDrawable(Color fill, Color border, float borderDp, Color cornerColor, float cornerLenDp){
            this.fill = fill;
            this.border = border;
            this.borderDp = borderDp;
            this.cornerColor = cornerColor;
            this.cornerLenDp = cornerLenDp;
        }

        @Override
        public void draw(float x, float y, float width, float height){
            float parentAlpha = Draw.getColorAlpha();
            float prev = Draw.getColorPacked();
            drawRectColor(fill, parentAlpha, x, y, width, height);
            if(border != null && borderDp > 0f){
                drawCrispBorder(border, parentAlpha, x, y, width, height, Math.max(1f, Scl.scl(borderDp)));
            }
            if(cornerColor != null && cornerLenDp > 0f){
                drawCornerBrackets(cornerColor, parentAlpha, x, y, width, height, Scl.scl(cornerLenDp), Math.max(1f, Scl.scl(1.5f)));
            }
            Draw.color(prev);
        }
    }

    private static final class FlatUnderlineDrawable extends BaseDrawable{
        private final Color fill;
        private final Color line;
        private final float lineDp;
        private final boolean topEdge;

        FlatUnderlineDrawable(Color fill, Color line, float lineDp, boolean topEdge){
            this.fill = fill;
            this.line = line;
            this.lineDp = lineDp;
            this.topEdge = topEdge;
        }

        @Override
        public void draw(float x, float y, float width, float height){
            float parentAlpha = Draw.getColorAlpha();
            float prev = Draw.getColorPacked();
            drawRectColor(fill, parentAlpha, x, y, width, height);
            if(line != null && lineDp > 0f){
                float t = Math.min(height, Math.max(1f, Scl.scl(lineDp)));
                float ly = topEdge ? (y + height - t) : y;
                drawRectColor(line, parentAlpha, x, ly, width, t);
            }
            Draw.color(prev);
        }
    }

    private static final class FlatBannerDrawable extends BaseDrawable{
        private final Color fill;
        private final Color stripe;
        private final Color border;

        FlatBannerDrawable(Color fill, Color stripe, Color border){
            this.fill = fill;
            this.stripe = stripe;
            this.border = border;
        }

        @Override
        public void draw(float x, float y, float width, float height){
            float parentAlpha = Draw.getColorAlpha();
            float prev = Draw.getColorPacked();
            drawRectColor(fill, parentAlpha, x, y, width, height);
            if(border != null){
                drawCrispBorder(border, parentAlpha, x, y, width, height, Math.max(1f, Scl.scl(1f)));
            }
            if(stripe != null){
                float sw = Math.min(width, Math.max(2f, Scl.scl(3f)));
                drawRectColor(stripe, parentAlpha, x, y, sw, height);
            }
            Draw.color(prev);
        }
    }

    private static final class FlatPaddedDrawable extends BaseDrawable{
        private final Drawable delegate;
        private final float padL, padR, padT, padB, minH;

        FlatPaddedDrawable(Drawable delegate, float padL, float padR, float padT, float padB, float minH){
            this.delegate = delegate;
            this.padL = padL;
            this.padR = padR;
            this.padT = padT;
            this.padB = padB;
            this.minH = minH;
        }

        @Override
        public float getLeftWidth(){
            return Scl.scl(padL);
        }

        @Override
        public float getRightWidth(){
            return Scl.scl(padR);
        }

        @Override
        public float getTopHeight(){
            return Scl.scl(padT);
        }

        @Override
        public float getBottomHeight(){
            return Scl.scl(padB);
        }

        @Override
        public float getMinHeight(){
            return Scl.scl(minH);
        }

        @Override
        public void draw(float x, float y, float width, float height){
            if(delegate != null){
                delegate.draw(x, y, width, height);
            }
        }
    }

    private static final class FlatCheckDrawable extends BaseDrawable{
        private final boolean checked;
        private final boolean hover;
        private final boolean disabled;

        FlatCheckDrawable(boolean checked, boolean hover, boolean disabled){
            this.checked = checked;
            this.hover = hover;
            this.disabled = disabled;
        }

        @Override
        public float getMinWidth(){
            return Scl.scl(20f);
        }

        @Override
        public float getMinHeight(){
            return Scl.scl(20f);
        }

        @Override
        public float imageSize(){
            return Scl.scl(20f);
        }

        @Override
        public void draw(float x, float y, float width, float height){
            float parentAlpha = Draw.getColorAlpha();
            float prev = Draw.getColorPacked();
            Color boxFill = disabled ? bannerDisabled : (checked ? bgSelected : bgField);
            Color frame = disabled
                ? statusDisabled
                : (checked ? (hover ? accentBright : accent) : (hover ? accent : muted));
            float borderPx = Math.max(1f, Scl.scl(checked ? 1.8f : 1.4f));

            drawRectColor(boxFill, parentAlpha, x, y, width, height);
            drawCrispBorder(frame, parentAlpha, x, y, width, height, borderPx);

            if(checked){
                float inset = Math.max(borderPx + 2f, Math.min(width, height) * 0.27f);
                float coreW = Math.max(2f, width - inset * 2f);
                float coreH = Math.max(2f, height - inset * 2f);
                Color core = disabled ? statusDisabled : (hover ? accentBright : accent);
                drawRectColor(core, parentAlpha, x + (width - coreW) * 0.5f, y + (height - coreH) * 0.5f, coreW, coreH);
            }
            Draw.color(prev);
        }
    }

    private static final class FlatSliderTrackDrawable extends BaseDrawable{
        private final Color fill;
        private final Color border;
        private final Color corner;

        FlatSliderTrackDrawable(Color fill, Color border, Color corner){
            this.fill = fill;
            this.border = border;
            this.corner = corner;
        }

        @Override
        public float getMinHeight(){
            return Scl.scl(sliderRowHeightDp);
        }

        @Override
        public void draw(float x, float y, float width, float height){
            float parentAlpha = Draw.getColorAlpha();
            float prev = Draw.getColorPacked();
            drawRectColor(fill, parentAlpha, x, y, width, height);
            drawCrispBorder(border, parentAlpha, x, y, width, height, Math.max(1f, Scl.scl(1f)));
            if(corner != null){
                drawCornerBrackets(corner, parentAlpha, x, y, width, height, Scl.scl(5f), Math.max(1f, Scl.scl(1.5f)));
            }
            Draw.color(prev);
        }
    }

    private static final class FlatSliderFillDrawable extends BaseDrawable{
        private final Color fill;
        private final Color bottomAccent;

        FlatSliderFillDrawable(Color fill, Color bottomAccent){
            this.fill = fill;
            this.bottomAccent = bottomAccent;
        }

        @Override
        public float getMinHeight(){
            return Scl.scl(sliderRowHeightDp);
        }

        @Override
        public void draw(float x, float y, float width, float height){
            float parentAlpha = Draw.getColorAlpha();
            float prev = Draw.getColorPacked();
            drawRectColor(fill, parentAlpha, x, y, width, height);
            float lineH = Math.min(height, Math.max(1.5f, Scl.scl(2f)));
            drawRectColor(bottomAccent, parentAlpha, x, y, width, lineH);
            Draw.color(prev);
        }
    }

    private static final class FlatSliderKnobDrawable extends BaseDrawable{
        private final Color color;

        FlatSliderKnobDrawable(Color color){
            this.color = color;
        }

        @Override
        public float getMinWidth(){
            return Scl.scl(8f);
        }

        @Override
        public float getMinHeight(){
            return Scl.scl(sliderRowHeightDp);
        }

        @Override
        public void draw(float x, float y, float width, float height){
            float parentAlpha = Draw.getColorAlpha();
            float prev = Draw.getColorPacked();
            drawRectColor(color, parentAlpha, x, y, width, height);
            Draw.color(prev);
        }
    }
}
