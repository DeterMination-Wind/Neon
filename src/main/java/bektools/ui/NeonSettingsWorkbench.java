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
import mdtxcompat.LegacyMindustryXGuard;
import mindustry.gen.Icon;
import mindustry.ui.Styles;

import java.util.EnumMap;
import java.util.Map;

/**
 * Unified Neon settings workbench in translucent black-and-gold flat futuristic style:
 * Mindustry gold-line header + square gold-framed toolbar tiles with vertical gold group dividers,
 * paired with a responsive left sub-mod rail and right detail pane.
 */
public class NeonSettingsWorkbench extends Table{
    private static final float wideBreakpointDp = 720f;
    private static final float minWorkbenchWidthDp = 300f;
    private static final float maxWorkbenchWidthDp = 1140f;
    private static final float minWorkbenchHeightDp = 360f;
    private static final float maxWorkbenchHeightDp = 820f;

    private enum NarrowPage{
        LIST,
        DETAIL
    }

    private final Seq<NeonSettingsModule> modules;
    private final Seq<NeonSettingsModule> polledModules;
    private final NeonSettingsContext context;
    private final Map<NeonSettingsTab, String> lastSelectedByTab = new EnumMap<>(NeonSettingsTab.class);

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

    private boolean wideMode = true;
    private float currentWidthDp;
    private float currentHeightDp;
    private boolean layoutPending;
    private final boolean[] cachedSwitchStates;

    public NeonSettingsWorkbench(Seq<NeonSettingsModule> modules, NeonSettingsContext context){
        super(VscodeSettingsStyle.panelBackground());
        this.modules = modules;
        this.context = context;
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

        // Reference 2 motif: centered warm-gold title over a full-width crisp 2dp gold horizontal line.
        headerTitleLabel = new Label("");
        headerTitleLabel.setColor(VscodeSettingsStyle.accentColor());
        headerTitleLabel.setAlignment(Align.center);
        table(VscodeSettingsStyle.titleRuleBackground(), titleStrip -> {
            titleStrip.center().margin(6f, 12f, 8f, 12f);
            titleStrip.add(headerTitleLabel).center().growX().minWidth(0f);
        }).growX().minWidth(0f).row();

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
        add(workspaceHost).grow().minHeight(0f).row();

        overviewPane = new NeonOverviewPane(modules, context, this::jumpToModule, this::onModuleStateMutated);
        sidebar = new NeonSubmodSidebar(modules, context, this::onSidebarSelect, this::onSearchChanged);
        detailPane = new NeonSubmodDetailPane(context, this::onNarrowBack, this::onModuleStateMutated);

        measureViewport(true);
        rebuildTabBar();
        rebuildWorkspace(true);
    }

    public float computeWorkbenchWidthDp(){
        float logicalW = logicalScreenWidth();
        boolean mdtx = LegacyMindustryXGuard.isMindustryXRuntime();
        // MindustryX renders a persistent category rail on wide screens (~260-280dp).
        float reservedChrome = mdtx ? (logicalW >= 960f ? 304f : 72f) : 64f;
        return Mathf.clamp(logicalW - reservedChrome, minWorkbenchWidthDp, maxWorkbenchWidthDp);
    }

    public float computeWorkbenchHeightDp(){
        float logicalH = logicalScreenHeight();
        return Mathf.clamp(logicalH - 176f, minWorkbenchHeightDp, maxWorkbenchHeightDp);
    }

    @Override
    public void act(float delta){
        super.act(delta);
        pollExternalSwitchChanges();
        checkResponsiveResize();
    }

    private void measureViewport(boolean force){
        float nextW = computeWorkbenchWidthDp();
        float nextH = computeWorkbenchHeightDp();
        boolean nextWide = nextW >= wideBreakpointDp;
        if(force || Math.abs(nextW - currentWidthDp) >= 16f || Math.abs(nextH - currentHeightDp) >= 16f || nextWide != wideMode){
            currentWidthDp = nextW;
            currentHeightDp = nextH;
            wideMode = nextWide;
        }
    }

    private void checkResponsiveResize(){
        float nextW = computeWorkbenchWidthDp();
        float nextH = computeWorkbenchHeightDp();
        boolean nextWide = nextW >= wideBreakpointDp;
        boolean significantWidth = Math.abs(nextW - currentWidthDp) >= 24f;
        boolean significantHeight = Math.abs(nextH - currentHeightDp) >= 24f;
        if(nextWide != wideMode || significantWidth || significantHeight){
            currentWidthDp = nextW;
            currentHeightDp = nextH;
            wideMode = nextWide;
            if(!layoutPending){
                layoutPending = true;
                Core.app.post(() -> {
                    layoutPending = false;
                    rebuildTabBar();
                    rebuildWorkspace(false);
                    invalidateHierarchy();
                });
            }
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
            Label fbLabel = new Label(Core.bundle.get("bektools.feedback", "Feedback"));
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
    }

    private void jumpToModule(NeonSettingsModule module){
        if(module == null) return;
        activeTab = module.tab;
        selectedModuleId = module.moduleId;
        lastSelectedByTab.put(module.tab, module.moduleId);
        narrowPage = NarrowPage.DETAIL;
        rebuildTabBar();
        rebuildWorkspace(true);
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
        }
    }

    private void rebuildWorkspace(boolean resetDetailScroll){
        workspaceHost.clearChildren();

        if(activeTab == NeonSettingsTab.OVERVIEW){
            overviewPane.rebuild(!wideMode);
            workspaceHost.add(overviewPane).grow().minHeight(0f);
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
            workspaceHost.add(detailPane).width(rightW).grow().minHeight(0f);
        }else if(narrowPage == NarrowPage.DETAIL && selected != null){
            detailPane.showModule(selected, true, currentWidthDp - 4f, resetDetailScroll);
            workspaceHost.add(detailPane).grow().minHeight(0f);
        }else{
            sidebar.configure(activeTab, selectedModuleId, searchQuery, true);
            workspaceHost.add(sidebar).grow().minHeight(0f);
        }
    }

    private float sidebarWidthDp(){
        return Mathf.clamp(currentWidthDp * 0.28f, 236f, 286f);
    }

    private float rightPaneWidthDp(){
        return Math.max(360f, currentWidthDp - sidebarWidthDp() - 4f);
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

    private static float logicalScreenWidth(){
        if(Core.graphics == null) return 1024f;
        float scale = Scl.scl(1f);
        return scale > 0f ? Core.graphics.getWidth() / scale : Core.graphics.getWidth();
    }

    private static float logicalScreenHeight(){
        if(Core.graphics == null) return 720f;
        float scale = Scl.scl(1f);
        return scale > 0f ? Core.graphics.getHeight() / scale : Core.graphics.getHeight();
    }
}
