package bektools.ui;

/** Decoupled runtime context bridging BekToolsMod failure isolation and feedback actions into the bektools.ui package. */
public interface NeonSettingsContext{
    boolean isModuleFailed(String moduleId);

    Throwable getModuleFailure(String moduleId);

    void recordModuleFailure(String moduleId, Throwable failure);

    void openFeedback();
}
