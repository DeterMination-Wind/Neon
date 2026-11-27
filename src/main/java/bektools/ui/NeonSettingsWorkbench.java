package bektools.ui;

import arc.Core;
import arc.math.Mathf;
import arc.scene.event.Touchable;
import arc.scene.ui.Button;
import arc.scene.ui.Image;
import arc.scene.ui.Label;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.layout.Scl;
import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import arc.util.Align;
import arc.util.Scaling;
import mindustry.gen.Icon;
import mindustry.ui.Styles;

import java.util.EnumMap;
import java.util.Map;

/**
 * Unified Neon settings workbench in translucent black-and-gold flat futuristic style:
 * Mindustry gold-line header with integrated return button + square gold-framed toolbar tiles
 * with vertical gold group dividers, paired with a responsive left sub-mod rail and right detail pane.
 */
public class NeonSettingsWorkbench extends Table{
    private static final float wideBreakpointDp = 720f;
    private static final float minWorkbenchWidthDp = 280f;
    private static final float minWorkbenchHeightDp = 320f;
    private static final float inlineWideSideInsetDp = 48f;
    private static final float inlineNarrowSideInsetDp = 16f;
    private static final float inlineHeightInsetDp = 272f;
    private static final float headerBalanceBreakpointDp = 420f;
    private static final float headerBackSlotWidthDp = 88f;

    private enum NarrowPage{
        LIST,
        DETAIL
    }

    private final Seq<NeonSettingsModule> modules;
    private final Seq<NeonSettingsModule> polledModules;
    private final NeonSettingsContext context;
    private final Runnable onExitCategory;
    private final Map<NeonSettingsTab, String> lastSelectedByTab = new EnumMap<>(NeonSettingsTab.class);

    private final Table headerStrip;
    private final Label headerTitleLabel;
    private final Table tabBarRow;
    private final ScrollPane tabScrollPane;
    private final Table workspaceHost;

    private final NeonOverviewPane overviewPane;
    private final NeonSubmodSidebar sidebar;
    private final NeonSubmodDetailPane detailPane;

    private NeonSettingsTab activeTab = NeonSettingsTab.OVERVIEW;
    private String selectedModuleId;
    private String searchQuery = "";
    private NarrowPage narrowPage = NarrowPage.LIST;

    private boolean fullScreenMode;
    private Runnable overlaySyncHook;

    private boolean wideMode = true;
    private float currentWidthDp;
    private float currentHeightDp;
    private float lastSafeTopDp = -1f;
    private float lastSafeLeftDp = -1f;
    private float lastSafeBottomDp = -1f;
    private float lastSafeRightDp = -1f;
    private boolean headerBalancedWithSpacer;
    private boolean layoutPending;
    private final boolean[] cachedSwitchStates;

    public NeonSettingsWorkbench(Seq<NeonSettingsModule> modules, NeonSettingsContext context){
        this(modules, context, null);
    }

    public NeonSettingsWorkbench(Seq<NeonSettingsModule> modules, NeonSettingsContext context, Runnable onExitCategory){
        super(VscodeSettingsStyle.panelBackground());
        this.modules = modules;
        this.context = context;
        this.onExitCategory = onExitCategory;
        this.polledModules = modules.select(m -> m.enableKey != null);
        this.cachedSwitchStates = new boolean[polledModules.size];
        captureSwitchSnapshot();

        // Seed initial per-tab selections to the first module in each domain tab.
        for(int i = 0; i < modules.size; i++){
            NeonSettingsModule module = modules.get(i);
            if(!lastSelectedByTab.containsKey(module.tab)){
                lastSelectedByTab.put(module.tab, module.moduleId);
            }
        }

        top().left();

        // Reference 2 motif: centered warm-gold title over a full-width crisp 2dp gold horizontal line,
        // paired with a flat-futuristic left return tile when taking over SettingsMenuDialog full-screen.
        headerTitleLabel = new Label("");
        headerTitleLabel.setColor(VscodeSettingsStyle.accentColor());
        headerTitleLabel.setAlignment(Align.center);
        headerTitleLabel.setEllipsis(true);

        headerStrip = new Table(VscodeSettingsStyle.titleRuleBackground());
        add(headerStrip).growX().minWidth(0f).row();

        // Reference 1 motif: dark translucent square-edged tiles with 2dp gold active frame & vertical gold group bars.
        tabBarRow = new Table();
        tabBarRow.left().margin(6f, 8f, 6f, 8f);
        tabScrollPane = new ScrollPane(tabBarRow, Styles.noBarPane);
        tabScrollPane.setFadeScrollBars(true);
        tabScrollPane.setScrollingDisabled(false, true);
        tabScrollPane.setOverscroll(false, false);

        table(VscodeSettingsStyle.tabBarBackground(), barHost -> {
            barHost.left();
            barHost.add(tabScrollPane).growX().minWidth(0f).height(50f);
        }).growX().minWidth(0f).row();

        workspaceHost = new Table();
        workspaceHost.top().left();
        add(workspaceHost).grow().minWidth(0f).minHeight(0f).row();

        overviewPane = new NeonOverviewPane(modules, context, this::jumpToModule, this::onModuleStateMutated);
        sidebar = new NeonSubmodSidebar(modules, context, this::onSidebarSelect, this::onSearchChanged);
        detailPane = new NeonSubmodDetailPane(context, this::onNarrowBack, this::onModuleStateMutated);

        measureViewport(true);
        rebuildHeaderStrip();
        rebuildTabBar();
        rebuildWorkspace(true);
    }

    void setOverlaySyncHook(Runnable hook){
        this.overlaySyncHook = hook;
    }

    public boolean isFullScreenMode(){
        return fullScreenMode;
    }

    public void setFullScreenMode(boolean fullScreen){
        if(this.fullScreenMode == fullScreen){
            syncSafeAreaMargins();
            return;
        }
        this.fullScreenMode = fullScreen;
        setBackground(fullScreen ? VscodeSettingsStyle.fullScreenPanelBackground() : VscodeSettingsStyle.panelBackground());
        syncSafeAreaMargins();
        measureViewport(true);
        rebuildHeaderStrip();
        rebuildTabBar();
        rebuildWorkspace(false);
        invalidateHierarchy();
    }

    void onFullScreenMounted(){
        syncSafeAreaMargins();
        measureViewport(true);
        rebuildHeaderStrip();
        rebuildTabBar();
        rebuildWorkspace(false);
        invalidateHierarchy();
        validate();
        focusPrimaryScrollPane();
    }

    public void focusPrimaryScrollPane(){
        if(activeTab == NeonSettingsTab.OVERVIEW){
            overviewPane.requestPrimaryScroll();
        }else if(wideMode || narrowPage == NarrowPage.DETAIL){
            detailPane.requestPrimaryScroll();
        }else{
            sidebar.requestPrimaryScroll();
        }
    }

    public float computeWorkbenchWidthDp(){
        if(fullScreenMode){
            float scale = uiScale();
            float rawPx = (parent != null && parent.getWidth() > 0f) ? parent.getWidth() : rawScreenWidthPx();
            float safePx = Math.max(0f, rawPx - sceneHorizontalMarginPx());
            return Math.max(minWorkbenchWidthDp, safePx / scale);
        }
        float logicalW = logicalScreenWidth();
        float sideInset = logicalW >= wideBreakpointDp ? inlineWideSideInsetDp : inlineNarrowSideInsetDp;
        return Math.max(minWorkbenchWidthDp, logicalW - sideInset);
    }

    public float computeWorkbenchHeightDp(){
        if(fullScreenMode){
            float scale = uiScale();
            float rawPx = (parent != null && parent.getHeight() > 0f) ? parent.getHeight() : rawScreenHeightPx();
            float safePx = Math.max(0f, rawPx - sceneVerticalMarginPx());
            return Math.max(minWorkbenchHeightDp, safePx / scale);
        }
        float logicalH = logicalScreenHeight();
        return Math.max(minWorkbenchHeightDp, logicalH - inlineHeightInsetDp);
    }

    @Override
    public void act(float delta){
        super.act(delta);
        if(overlaySyncHook != null){
            overlaySyncHook.run();
        }
        if(fillParent && (x != 0f || y != 0f)){
            setPosition(0f, 0f);
        }
        syncSafeAreaMargins();
        pollExternalSwitchChanges();
        checkResponsiveResize();
    }

    private void syncSafeAreaMargins(){
        float topDp = 0f;
        float leftDp = 0f;
        float bottomDp = 0f;
        float rightDp = 0f;
        if(fullScreenMode && Core.scene != null){
            float scale = uiScale();
            topDp = Math.max(0f, Core.scene.marginTop / scale);
            leftDp = Math.max(0f, Core.scene.marginLeft / scale);
            bottomDp = Math.max(0f, Core.scene.marginBottom / scale);
            rightDp = Math.max(0f, Core.scene.marginRight / scale);
        }
        if(Math.abs(topDp - lastSafeTopDp) > 0.5f
            || Math.abs(leftDp - lastSafeLeftDp) > 0.5f
            || Math.abs(bottomDp - lastSafeBottomDp) > 0.5f
            || Math.abs(rightDp - lastSafeRightDp) > 0.5f){
            lastSafeTopDp = topDp;
            lastSafeLeftDp = leftDp;
            lastSafeBottomDp = bottomDp;
            lastSafeRightDp = rightDp;
            margin(topDp, leftDp, bottomDp, rightDp);
            invalidateHierarchy();
        }
    }

    private void measureViewport(boolean force){
        float nextW = computeWorkbenchWidthDp();
        float nextH = computeWorkbenchHeightDp();
        boolean nextWide = nextW >= wideBreakpointDp;
        if(force || Math.abs(nextW - currentWidthDp) >= 2f || Math.abs(nextH - currentHeightDp) >= 2f || nextWide != wideMode){
            currentWidthDp = nextW;
            currentHeightDp = nextH;
            wideMode = nextWide;
        }
    }

    private void checkResponsiveResize(){
        float nextW = computeWorkbenchWidthDp();
        float nextH = computeWorkbenchHeightDp();
        boolean nextWide = nextW >= wideBreakpointDp;
        boolean nextBalanced = shouldBalanceHeaderSpacer(nextW);
        boolean significantWidth = Math.abs(nextW - currentWidthDp) >= 2f;
        boolean significantHeight = Math.abs(nextH - currentHeightDp) >= 2f;
        if(nextWide != wideMode || nextBalanced != headerBalancedWithSpacer || significantWidth || significantHeight){
            currentWidthDp = nextW;
            currentHeightDp = nextH;
            wideMode = nextWide;
            if(!layoutPending){
                layoutPending = true;
                Runnable applyResize = () -> {
                    layoutPending = false;
                    rebuildHeaderStrip();
                    rebuildTabBar();
                    rebuildWorkspace(false);
                    invalidateHierarchy();
                };
                if(Core.app != null){
                    Core.app.post(applyResize);
                }else{
                    applyResize.run();
                }
            }
        }
    }

    private boolean shouldBalanceHeaderSpacer(float widthDp){
        return onExitCategory != null && widthDp >= headerBalanceBreakpointDp;
    }

    private void rebuildHeaderStrip(){
        updateHeaderBannerText();
        headerStrip.clearChildren();
        headerStrip.center().margin(6f, 10f, 8f, 10f);

        boolean showBack = onExitCategory != null;
        headerBalancedWithSpacer = shouldBalanceHeaderSpacer(currentWidthDp);

        if(showBack){
            Button backBtn = new Button(VscodeSettingsStyle.toolbarTileStyle(false));
            backBtn.name = "neon-settings-back";
            backBtn.touchable = Touchable.enabled;
            backBtn.margin(5f, 10f, 5f, 12f);
            backBtn.clicked(onExitCategory);

            Table inner = new Table();
            inner.touchable = Touchable.disabled;
            inner.center();
            Image backIcon = new Image(Icon.left);
            backIcon.setScaling(Scaling.fit);
            backIcon.setColor(VscodeSettingsStyle.accentColor());
            inner.add(backIcon).size(16f).padRight(5f);

            String backText = Core.bundle == null ? "Back" : Core.bundle.get("back", "Back");
            Label backLabel = new Label(backText);
            backLabel.setColor(VscodeSettingsStyle.primaryTextColor());
            inner.add(backLabel);

            backBtn.add(inner);
            headerStrip.add(backBtn).height(34f).minWidth(headerBackSlotWidthDp).left().padRight(8f);
        }

        headerTitleLabel.setAlignment(showBack && !headerBalancedWithSpacer ? Align.left : Align.center);
        headerStrip.add(headerTitleLabel).growX().minWidth(0f);

        if(showBack && headerBalancedWithSpacer){
            headerStrip.add().width(headerBackSlotWidthDp).height(34f).padLeft(8f);
        }
    }

    private void captureSwitchSnapshot(){
        for(int i = 0; i < polledModules.size; i++){
            cachedSwitchStates[i] = polledModules.get(i).isSwitchOn();
        }
    }

    private void pollExternalSwitchChanges(){
        boolean changed = false;
        for(int i = 0; i < polledModules.size; i++){
            boolean current = polledModules.get(i).isSwitchOn();
            if(current != cachedSwitchStates[i]){
                cachedSwitchStates[i] = current;
                changed = true;
            }
        }
        if(changed){
            refreshReactiveViews();
        }
    }

    private void onModuleStateMutated(){
        captureSwitchSnapshot();
        refreshReactiveViews();
    }

    private void refreshReactiveViews(){
        if(activeTab == NeonSettingsTab.OVERVIEW){
            overviewPane.rebuild(!wideMode);
        }else{
            sidebar.refreshStates();
            detailPane.refreshHeaderOnly();
        }
    }

    private void updateHeaderBannerText(){
        String catName = Core.bundle == null ? "Neon" : Core.bundle.get("bektools.category", "Neon");
        headerTitleLabel.setText(catName + "  ·  " + activeTab.title());
    }

    private void rebuildTabBar(){
        updateHeaderBannerText();
        tabBarRow.clearChildren();

        // Group 1: Overview hub tile (left of first gold vertical separator bar)
        addToolbarTabTile(NeonSettingsTab.OVERVIEW);

        addGoldGroupSeparator();

        // Group 2: Four functional domain category tiles
        NeonSettingsTab[] domainTabs = {
            NeonSettingsTab.BUILD_LOGISTICS,
            NeonSettingsTab.COMBAT_HUD,
            NeonSettingsTab.LOGIC_EDITOR,
            NeonSettingsTab.TOOLS_CORE
        };
        for(int i = 0; i < domainTabs.length; i++){
            addToolbarTabTile(domainTabs[i]);
        }

        addGoldGroupSeparator();

        // Group 3: Right-hand quick action tile (Feedback), echoing the trailing text tile in Reference 1
        Button feedbackBtn = new Button(VscodeSettingsStyle.toolbarTileStyle(false));
        feedbackBtn.touchable = Touchable.enabled;
        feedbackBtn.margin(6f, 10f, 6f, 10f);
        feedbackBtn.clicked(context::openFeedback);

        Table fbInner = new Table();
        fbInner.touchable = Touchable.disabled;
        fbInner.center();
        Image fbIcon = new Image(Icon.chat);
        fbIcon.setScaling(Scaling.fit);
        fbIcon.setColor(VscodeSettingsStyle.primaryTextColor());
        fbInner.add(fbIcon).size(16f).padRight(wideMode ? 6f : 0f);
        if(wideMode){
            Label fbLabel = new Label(Core.bundle == null ? "Feedback" : Core.bundle.get("bektools.feedback", "Feedback"));
            fbLabel.setColor(VscodeSettingsStyle.primaryTextColor());
            fbInner.add(fbLabel);
        }
        feedbackBtn.add(fbInner);
        tabBarRow.add(feedbackBtn).height(38f);
    }

    private void addToolbarTabTile(NeonSettingsTab tab){
        boolean active = tab == activeTab;
        Button btn = new Button(VscodeSettingsStyle.toolbarTileStyle(active));
        btn.touchable = Touchable.enabled;
        btn.margin(6f, 11f, 6f, 11f);
        btn.clicked(() -> selectTab(tab));

        Table inner = new Table();
        inner.touchable = Touchable.disabled;
        inner.center();
        if(tab.icon() != null){
            Image ic = new Image(tab.icon());
            ic.setScaling(Scaling.fit);
            ic.setColor(active ? VscodeSettingsStyle.accentColor() : VscodeSettingsStyle.primaryTextColor());
            inner.add(ic).size(17f).padRight(6f);
        }
        Label label = new Label(tab.title());
        label.setColor(active ? VscodeSettingsStyle.accentColor() : VscodeSettingsStyle.primaryTextColor());
        inner.add(label);

        btn.add(inner);
        tabBarRow.add(btn).height(38f).padRight(5f);
    }

    private void addGoldGroupSeparator(){
        tabBarRow.image(VscodeSettingsStyle.goldRuleDrawable())
            .width(2f)
            .height(28f)
            .padLeft(3f)
            .padRight(8f);
    }

    private void selectTab(NeonSettingsTab tab){
        if(tab == null) return;
        if(tab == activeTab && (wideMode || narrowPage == NarrowPage.LIST)){
            return;
        }
        activeTab = tab;
        narrowPage = NarrowPage.LIST;
        if(activeTab != NeonSettingsTab.OVERVIEW){
            selectedModuleId = resolveSelectionForTab(activeTab);
        }
        rebuildTabBar();
        rebuildWorkspace(true);
        focusPrimaryScrollPane();
    }

    private void jumpToModule(NeonSettingsModule module){
        if(module == null) return;
        activeTab = module.tab;
        selectedModuleId = module.moduleId;
        lastSelectedByTab.put(module.tab, module.moduleId);
        narrowPage = NarrowPage.DETAIL;
        rebuildTabBar();
        rebuildWorkspace(true);
        focusPrimaryScrollPane();
    }

    private void onSidebarSelect(NeonSettingsModule module){
        if(module == null) return;
        selectedModuleId = module.moduleId;
        lastSelectedByTab.put(module.tab, module.moduleId);
        if(!wideMode){
            narrowPage = NarrowPage.DETAIL;
            rebuildWorkspace(true);
        }else{
            sidebar.setSelectedModuleId(selectedModuleId);
            detailPane.showModule(module, false, rightPaneWidthDp(), true);
        }
        focusPrimaryScrollPane();
    }

    private void onSearchChanged(String query){
        searchQuery = query == null ? "" : query;
        if(wideMode && activeTab != NeonSettingsTab.OVERVIEW){
            Seq<NeonSettingsModule> visible = sidebar.visibleModules();
            if(!visible.isEmpty() && !containsModule(visible, selectedModuleId)){
                NeonSettingsModule first = visible.first();
                selectedModuleId = first.moduleId;
                sidebar.setSelectedModuleId(selectedModuleId);
                detailPane.showModule(first, false, rightPaneWidthDp(), true);
            }
        }
    }

    private void onNarrowBack(){
        if(narrowPage != NarrowPage.LIST){
            narrowPage = NarrowPage.LIST;
            rebuildWorkspace(false);
            focusPrimaryScrollPane();
        }
    }

    private void rebuildWorkspace(boolean resetDetailScroll){
        workspaceHost.clearChildren();

        if(activeTab == NeonSettingsTab.OVERVIEW){
            overviewPane.rebuild(!wideMode);
            workspaceHost.add(overviewPane).grow().minWidth(0f).minHeight(0f);
            return;
        }

        NeonSettingsModule selected = findModuleById(selectedModuleId);
        if(selected == null || (searchQuery.trim().isEmpty() && selected.tab != activeTab)){
            selectedModuleId = resolveSelectionForTab(activeTab);
            selected = findModuleById(selectedModuleId);
        }

        if(wideMode){
            float sideW = sidebarWidthDp();
            float rightW = rightPaneWidthDp();
            sidebar.configure(activeTab, selectedModuleId, searchQuery, false);
            detailPane.showModule(selected, false, rightW, resetDetailScroll);

            workspaceHost.add(sidebar).width(sideW).growY().minHeight(0f);
            workspaceHost.image(VscodeSettingsStyle.dividerDrawable()).width(1.5f).growY();
            workspaceHost.add(detailPane).grow().minWidth(0f).minHeight(0f);
        }else if(narrowPage == NarrowPage.DETAIL && selected != null){
            detailPane.showModule(selected, true, Math.max(260f, currentWidthDp), resetDetailScroll);
            workspaceHost.add(detailPane).grow().minWidth(0f).minHeight(0f);
        }else{
            sidebar.configure(activeTab, selectedModuleId, searchQuery, true);
            workspaceHost.add(sidebar).grow().minWidth(0f).minHeight(0f);
        }
    }

    private float sidebarWidthDp(){
        return Mathf.clamp(currentWidthDp * 0.25f, 240f, 320f);
    }

    private float rightPaneWidthDp(){
        return Math.max(320f, currentWidthDp - sidebarWidthDp() - 2f);
    }

    private String resolveSelectionForTab(NeonSettingsTab tab){
        String remembered = lastSelectedByTab.get(tab);
        if(remembered != null && findModuleById(remembered) != null){
            return remembered;
        }
        for(int i = 0; i < modules.size; i++){
            NeonSettingsModule module = modules.get(i);
            if(module.tab == tab){
                lastSelectedByTab.put(tab, module.moduleId);
                return module.moduleId;
            }
        }
        return modules.isEmpty() ? null : modules.first().moduleId;
    }

    private NeonSettingsModule findModuleById(String id){
        if(id == null) return null;
        for(int i = 0; i < modules.size; i++){
            NeonSettingsModule module = modules.get(i);
            if(id.equals(module.moduleId)) return module;
        }
        return null;
    }

    private static boolean containsModule(Seq<NeonSettingsModule> list, String id){
        if(id == null) return false;
        for(int i = 0; i < list.size; i++){
            if(id.equals(list.get(i).moduleId)) return true;
        }
        return false;
    }

    private static float uiScale(){
        float scale = Scl.scl(1f);
        return scale > 0f ? scale : 1f;
    }

    private static float rawScreenWidthPx(){
        return Core.graphics == null ? 1024f * uiScale() : Core.graphics.getWidth();
    }

    private static float rawScreenHeightPx(){
        return Core.graphics == null ? 720f * uiScale() : Core.graphics.getHeight();
    }

    private static float sceneHorizontalMarginPx(){
        return Core.scene == null ? 0f : Math.max(0f, Core.scene.marginLeft) + Math.max(0f, Core.scene.marginRight);
    }

    private static float sceneVerticalMarginPx(){
        return Core.scene == null ? 0f : Math.max(0f, Core.scene.marginTop) + Math.max(0f, Core.scene.marginBottom);
    }

    private static float logicalScreenWidth(){
        return rawScreenWidthPx() / uiScale();
    }

    private static float logicalScreenHeight(){
        return rawScreenHeightPx() / uiScale();
    }
}
