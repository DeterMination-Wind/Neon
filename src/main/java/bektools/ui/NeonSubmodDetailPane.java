package bektools.ui;

import arc.Core;
import arc.func.Cons;
import arc.scene.ui.CheckBox;
import arc.scene.ui.Image;
import arc.scene.ui.Label;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.layout.Table;
import arc.util.Align;
import arc.util.Scaling;
import mindustry.gen.Icon;
import mindustry.gen.Tex;
import mindustry.ui.Styles;

/**
 * Right-hand detail pane (or full-width DETAIL screen in narrow mode) hosting the selected
 * sub-mod's gold-rule flat futuristic header, square master switch, lifecycle banner, and settings body.
 */
public class NeonSubmodDetailPane extends Table{
    private final NeonSettingsContext context;
    private final Runnable onBackRequested;
    private final Runnable onStateMutated;

    private final Table backBar;
    private final Table headerHost;
    private final Table bodyHost;
    private final ScrollPane bodyScroll;

    private NeonSettingsModule currentModule;
    private boolean narrowMode;
    private float paneWidth = 640f;
    private float savedScrollY;
    private boolean rebuildingBody;
    private Cons<Boolean> hoistedEnableCallback;

    public NeonSubmodDetailPane(
        NeonSettingsContext context,
        Runnable onBackRequested,
        Runnable onStateMutated
    ){
        super(VscodeSettingsStyle.sidebarBackground());
        this.context = context;
        this.onBackRequested = onBackRequested;
        this.onStateMutated = onStateMutated;

        top().left().margin(10f);

        backBar = new Table();
        backBar.left();
        add(backBar).growX().minWidth(0f).row();

        headerHost = new Table();
        headerHost.top().left();
        add(headerHost).growX().minWidth(0f).padBottom(8f).row();

        bodyHost = new Table();
        bodyHost.top().left();
        bodyScroll = new ScrollPane(bodyHost, Styles.noBarPane);
        bodyScroll.setFadeScrollBars(false);
        bodyScroll.setScrollingDisabled(true, false);
        bodyScroll.setOverscroll(false, false);
        bodyScroll.update(() -> savedScrollY = bodyScroll.getScrollY());
        add(bodyScroll).grow().minHeight(0f).row();
    }

    public void showModule(NeonSettingsModule module, boolean narrow, float logicalPaneWidth, boolean resetScroll){
        boolean moduleChanged = currentModule == null || module == null || !currentModule.moduleId.equals(module.moduleId);
        this.currentModule = module;
        this.narrowMode = narrow;
        this.paneWidth = Math.max(260f, logicalPaneWidth);
        if(resetScroll || moduleChanged){
            savedScrollY = 0f;
        }
        rebuildBackBar();
        rebuildBody(resetScroll || moduleChanged);
        rebuildHeader();
    }

    public void refreshHeaderOnly(){
        rebuildHeader();
        if(currentModule != null && !currentModule.available){
            rebuildBody(false);
        }
    }

    public NeonSettingsModule getCurrentModule(){
        return currentModule;
    }

    void requestPrimaryScroll(){
        if(Core.scene != null && bodyScroll != null){
            bodyScroll.requestScroll();
        }
    }

    private void rebuildBackBar(){
        backBar.clearChildren();
        if(!narrowMode || currentModule == null) return;

        backBar.table(VscodeSettingsStyle.sectionHeaderBackground(), bar -> {
            bar.left().margin(6f, 8f, 8f, 8f);
            bar.button(Core.bundle.get("bektools.ui.nav.back", "Back"), Icon.left, VscodeSettingsStyle.flatButtonStyle(), () -> {
                if(onBackRequested != null) onBackRequested.run();
            }).height(34f).padRight(10f);
            bar.add(currentModule.tab.title() + "  /  " + currentModule.title)
                .color(VscodeSettingsStyle.accentColor())
                .left()
                .growX()
                .minWidth(0f)
                .wrap();
        }).growX().minWidth(0f).padBottom(8f).row();
    }

    private void rebuildHeader(){
        headerHost.clearChildren();
        if(currentModule == null) return;

        final NeonSettingsModule module = currentModule;
        ModuleRuntimeState state = module.runtimeState(context);

        headerHost.table(VscodeSettingsStyle.hudCardBackground(), card -> {
            card.top().left().margin(10f);

            // Row 1: Icon + Title + Metadata + Geometric Status Badge, underscored by gold rule
            card.table(VscodeSettingsStyle.titleRuleBackground(), topRow -> {
                topRow.left().margin(0f, 0f, 8f, 0f);
                if(module.icon != null){
                    Image ic = new Image(module.icon);
                    ic.setScaling(Scaling.fit);
                    ic.setColor(VscodeSettingsStyle.accentColor());
                    topRow.add(ic).size(24f).padRight(10f);
                }

                Table titles = new Table();
                titles.left();
                Label nameLabel = new Label(module.title);
                nameLabel.setColor(VscodeSettingsStyle.accentColor());
                nameLabel.setWrap(true);
                titles.add(nameLabel).left().growX().minWidth(0f).row();

                Label metaLabel = new Label(module.tab.title() + "  //  " + module.moduleId.toUpperCase(java.util.Locale.ROOT));
                metaLabel.setFontScale(0.82f);
                metaLabel.setColor(VscodeSettingsStyle.mutedColor());
                titles.add(metaLabel).left().growX().minWidth(0f).padTop(2f).row();

                topRow.add(titles).left().growX().minWidth(0f);

                // Flat futuristic rectangular status badge
                topRow.table(VscodeSettingsStyle.bannerBackground(state), badge -> {
                    badge.margin(4f, 9f, 4f, 9f);
                    Image dot = new Image(Tex.whiteui);
                    dot.setColor(state.color());
                    badge.add(dot).size(6f).padRight(6f);
                    Label statusLabel = new Label(state.badgeText());
                    statusLabel.setFontScale(0.85f);
                    statusLabel.setColor(state.color());
                    badge.add(statusLabel);
                }).right().padLeft(8f);
            }).growX().minWidth(0f).row();

            // Row 2: Unified flat-futuristic master switch
            if(module.enableKey != null && state != ModuleRuntimeState.FAILED){
                CheckBox masterSwitch = new CheckBox(
                    Core.bundle.get("bektools.module.switch", "Master switch: enable this module"),
                    VscodeSettingsStyle.flatCheckBoxStyle()
                );
                masterSwitch.getLabel().setWrap(true);
                masterSwitch.getLabelCell().padLeft(8f).growX().minWidth(0f);
                masterSwitch.setChecked(module.isSwitchOn());
                masterSwitch.update(() -> {
                    boolean stored = module.isSwitchOn();
                    if(masterSwitch.isChecked() != stored){
                        masterSwitch.setChecked(stored);
                    }
                });
                masterSwitch.changed(() -> {
                    boolean next = masterSwitch.isChecked();
                    module.setSwitchOn(next);
                    if(hoistedEnableCallback != null){
                        try{
                            hoistedEnableCallback.get(next);
                        }catch(Throwable ignored){
                        }
                    }
                    rebuildHeader();
                    if(!module.available){
                        rebuildBody(false);
                    }
                    if(onStateMutated != null){
                        onStateMutated.run();
                    }
                });

                card.table(VscodeSettingsStyle.cardBackground(), swRow -> {
                    swRow.left().margin(8f);
                    swRow.image(Icon.power).size(18f).color(state.color()).padRight(8f);
                    swRow.add(masterSwitch).left().growX().minWidth(0f);
                }).growX().minWidth(0f).padTop(8f).row();
            }

            // Row 3: Contextual lifecycle banner (only for non-ACTIVE states)
            if(state != ModuleRuntimeState.ACTIVE){
                card.table(VscodeSettingsStyle.bannerBackground(state), banner -> {
                    banner.left().margin(8f, 10f, 8f, 10f);
                    Image icon = new Image(state == ModuleRuntimeState.FAILED ? Icon.warningSmall : Icon.infoSmall);
                    icon.setColor(state.color());
                    banner.add(icon).size(18f).padRight(8f).top();
                    Label msg = new Label(bannerMessage(state));
                    msg.setColor(state.color());
                    msg.setWrap(true);
                    msg.setAlignment(Align.left);
                    banner.add(msg).left().growX().minWidth(0f);
                }).growX().minWidth(0f).padTop(8f).row();
            }
        }).growX().minWidth(0f).row();
    }

    private String bannerMessage(ModuleRuntimeState state){
        switch(state){
            case FAILED:
                return Core.bundle.get("bektools.module.failed", "This module failed to initialize. Check the log for details.");
            case DISABLED:
                return Core.bundle.get("bektools.module.disabled", "This module is switched off; it will not be loaded after the game restarts.");
            case PENDING_ENABLE:
                return Core.bundle.get("bektools.module.restart", "The master switch is on, but the module was not loaded in this session. Restart the game to load it.");
            case PENDING_DISABLE:
                return Core.bundle.get("bektools.ui.banner.pending_disable", "The master switch is off. This module stays loaded in this session and will stop loading after a restart.");
            case ACTIVE:
            default:
                return "";
        }
    }

    private void rebuildBody(boolean resetScroll){
        if(rebuildingBody) return;
        rebuildingBody = true;
        final float prevScroll = resetScroll ? 0f : savedScrollY;
        try{
            bodyHost.clearChildren();
            hoistedEnableCallback = null;

            if(currentModule == null){
                addInfoCard(Core.bundle.get("bektools.ui.empty.select", "Select a sub-mod from the list on the left."));
                return;
            }

            NeonSettingsModule module = currentModule;
            if(context.isModuleFailed(module.moduleId)){
                addFailureDiagnostics(module);
                return;
            }

            if(!module.available){
                addInfoCard(module.isSwitchOn()
                    ? Core.bundle.get("bektools.module.restart", "Restart the game to load this module and access its settings.")
                    : Core.bundle.get("bektools.module.disabled", "This module is switched off and was not loaded in this session."));
                return;
            }

            if(!module.hasOwnSettings){
                addInfoCard(module.emptyPlaceholderText());
                return;
            }

            // Match the exact horizontal content width of headerHost (paneWidth minus 10dp left + 10dp right margin).
            float innerWidth = Math.max(240f, paneWidth - 20f);
            SubmodSettingsHostTable host = new SubmodSettingsHostTable(module.enableKey, () -> {
                if(!rebuildingBody){
                    rebuildBody(false);
                }
            });

            host.beginBuild();
            try{
                RbmStyle.runWithRowWidth(innerWidth, () -> {
                    if(module.builder != null){
                        module.builder.get(host);
                    }
                });
            }catch(Throwable t){
                context.recordModuleFailure(module.moduleId, t);
                host.finishBuild();
                bodyHost.clearChildren();
                addFailureDiagnostics(module);
                if(onStateMutated != null){
                    onStateMutated.run();
                }
                return;
            }
            host.finishBuild();
            hoistedEnableCallback = host.getHoistedEnableCallback();

            if(!host.hasVisibleContent()){
                addInfoCard(module.emptyPlaceholderText());
            }else{
                SettingsVisualNormalizer.normalize(host, innerWidth);
                bodyHost.add(host).growX().fillX().minWidth(0f).top().left().row();
                bodyHost.add().height(12f).row();
            }
        }finally{
            rebuildingBody = false;
            Core.app.post(() -> {
                if(bodyScroll != null){
                    bodyScroll.layout();
                    bodyScroll.setScrollYForce(prevScroll);
                }
            });
        }
    }

    private void addInfoCard(String text){
        bodyHost.table(VscodeSettingsStyle.hudCardBackground(), card -> {
            card.left().margin(14f);
            card.image(Icon.infoSmall).size(18f).color(VscodeSettingsStyle.accentColor()).padRight(10f).top();
            Label label = new Label(text);
            label.setColor(VscodeSettingsStyle.mutedColor());
            label.setWrap(true);
            label.setAlignment(Align.left);
            card.add(label).left().growX().minWidth(0f);
        }).growX().minWidth(0f).padTop(4f).row();
    }

    private void addFailureDiagnostics(NeonSettingsModule module){
        Throwable failure = context.getModuleFailure(module.moduleId);
        bodyHost.table(VscodeSettingsStyle.bannerBackground(ModuleRuntimeState.FAILED), card -> {
            card.top().left().margin(14f);
            Label heading = new Label(Core.bundle.get("bektools.ui.failed.detail_title", "Initialization Error"));
            heading.setColor(VscodeSettingsStyle.statusFailedColor());
            card.add(heading).left().growX().minWidth(0f).padBottom(6f).row();

            String summary = failure == null
                ? Core.bundle.get("bektools.module.failed", "This module failed to initialize. Check the log for details.")
                : (failure.getClass().getSimpleName() + (failure.getMessage() == null ? "" : ": " + failure.getMessage()));
            Label detail = new Label(summary);
            detail.setColor(VscodeSettingsStyle.primaryTextColor());
            detail.setWrap(true);
            detail.setAlignment(Align.left);
            card.add(detail).left().growX().minWidth(0f).row();
        }).growX().minWidth(0f).padTop(4f).row();
    }
}
