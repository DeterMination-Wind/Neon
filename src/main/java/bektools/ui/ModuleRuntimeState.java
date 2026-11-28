package bektools.ui;

import arc.Core;
import arc.graphics.Color;

/** Five-state runtime lifecycle model separating persisted master-switch state from session load status and init failures. */
public enum ModuleRuntimeState{
    ACTIVE("bektools.ui.status.active", "Enabled"),
    PENDING_DISABLE("bektools.ui.status.pending_disable", "Off (Restart)"),
    DISABLED("bektools.ui.status.disabled", "Disabled"),
    PENDING_ENABLE("bektools.ui.status.pending_enable", "On (Restart)"),
    FAILED("bektools.ui.status.failed", "Failed");

    public final String badgeKey;
    public final String badgeFallback;

    ModuleRuntimeState(String badgeKey, String badgeFallback){
        this.badgeKey = badgeKey;
        this.badgeFallback = badgeFallback;
    }

    public static ModuleRuntimeState resolve(boolean available, boolean failed, boolean switchOn){
        if(failed) return FAILED;
        if(available){
            return switchOn ? ACTIVE : PENDING_DISABLE;
        }
        return switchOn ? PENDING_ENABLE : DISABLED;
    }

    public String badgeText(){
        return Core.bundle == null ? badgeFallback : Core.bundle.get(badgeKey, badgeFallback);
    }

    public Color color(){
        switch(this){
            case ACTIVE:
                return VscodeSettingsStyle.statusActiveColor();
            case PENDING_DISABLE:
            case PENDING_ENABLE:
                return VscodeSettingsStyle.statusPendingColor();
            case FAILED:
                return VscodeSettingsStyle.statusFailedColor();
            case DISABLED:
            default:
                return VscodeSettingsStyle.statusDisabledColor();
        }
    }

    public boolean needsRestart(){
        return this == PENDING_DISABLE || this == PENDING_ENABLE;
    }
}
