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
import arc.util.Scaling;
import mdtxcompat.LegacyMindustryXGuard;
import mindustry.gen.Tex;
import mindustry.ui.Styles;

import java.util.EnumMap;
import java.util.Map;

/**
 * Unified Neon settings workbench: top category tabs + left sub-mod sidebar + right detail pane
 * on wide viewports, gracefully degrading to a Master-Detail stack on narrow / Android viewports.
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

        // Top tab bar wrapped in a horizontal-only ScrollPane so tabs never clip on narrow screens.
        tabBarRow = new Table(VscodeSettingsStyle.tabBarBackground());
        tabBarRow.left().margin(4f, 6f, 0f, 6f);
        tabScrollPane = new ScrollPane(tabBarRow, Styles.noBarPane);
        tabScrollPane.setFadeScrollBars(true);
        tabScrollPane.setScrollingDisabled(false, true);
        tabScrollPane.setOverscroll(false, false);

        add(tabScrollPane).growX().minWidth(0f).height(46f).row();
        image(VscodeSettingsStyle.dividerDrawable()).height(2f).growX().row();

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

    private void rebuildTabBar(){
        tabBarRow.clearChildren();
        for(NeonSettingsTab tab : NeonSettingsTab.values()){
            boolean active = tab == activeTab;
            Button btn = new Button(VscodeSettingsStyle.tabButtonStyle(active));
            btn.touchable = Touchable.enabled;
            btn.margin(6f, 12f, 4f, 12f);
            btn.clicked(() -> selectTab(tab));

            Table inner = new Table();
            inner.touchable = Touchable.disabled;
            inner.center();
            if(tab.icon() != null){
                Image ic = new Image(tab.icon());
                ic.setScaling(Scaling.fit);
                ic.setColor(active ? VscodeSettingsStyle.accentColor() : VscodeSettingsStyle.mutedColor());
                inner.add(ic).size(16f).padRight(6f);
            }
            Label label = new Label(tab.title());
            label.setColor(active ? VscodeSettingsStyle.accentColor() : VscodeSettingsStyle.mutedColor());
            inner.add(label).row();

            btn.add(inner).row();
            Image underline = new Image(Tex.whiteui);
            underline.touchable = Touchable.disabled;
            underline.setColor(active ? VscodeSettingsStyle.accentColor() : arc.graphics.Color.clear);
            btn.add(underline).height(2f).growX().padTop(4f).row();

            tabBarRow.add(btn).height(42f).padRight(4f);
        }
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
            workspaceHost.image(VscodeSettingsStyle.dividerDrawable()).width(2f).growY();
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
