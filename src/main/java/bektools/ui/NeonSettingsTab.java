package bektools.ui;

import arc.Core;
import arc.scene.style.Drawable;
import mindustry.gen.Icon;

/** Top-level category tabs for Neon's unified settings workbench. */
public enum NeonSettingsTab{
    OVERVIEW("bektools.ui.tab.overview", "Overview", Icon.settings),
    BUILD_LOGISTICS("bektools.ui.tab.build", "Build & Logistics", Icon.hammer),
    COMBAT_HUD("bektools.ui.tab.combat", "Combat & Vision", Icon.map),
    LOGIC_EDITOR("bektools.ui.tab.logic", "Logic & Editor", Icon.logic),
    TOOLS_CORE("bektools.ui.tab.tools", "Tools & Core", Icon.wrench);

    public final String bundleKey;
    public final String fallbackTitle;
    private final Drawable icon;

    NeonSettingsTab(String bundleKey, String fallbackTitle, Drawable icon){
        this.bundleKey = bundleKey;
        this.fallbackTitle = fallbackTitle;
        this.icon = icon;
    }

    public String title(){
        return Core.bundle == null ? fallbackTitle : Core.bundle.get(bundleKey, fallbackTitle);
    }

    public Drawable icon(){
        return icon;
    }
}
