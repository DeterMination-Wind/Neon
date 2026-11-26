package bektools.ui;

import arc.Core;
import arc.func.Cons;
import arc.scene.event.Touchable;
import arc.scene.ui.Button;
import arc.scene.ui.Image;
import arc.scene.ui.ImageButton;
import arc.scene.ui.Label;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.TextButton;
import arc.scene.ui.TextField;
import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import arc.util.Align;
import arc.util.Scaling;
import mindustry.gen.Icon;
import mindustry.gen.Tex;
import mindustry.ui.Styles;

import java.util.Locale;

/**
 * Left navigation rail (or full-width master list in narrow mode) with flat underline search bar,
 * gold-framed square filter chips, and translucent flat-futuristic sub-mod tiles.
 */
public class NeonSubmodSidebar extends Table{
    public enum FilterMode{
        ALL("bektools.ui.filter.all", "All"),
        ACTIVE("bektools.ui.filter.active", "On"),
        DISABLED("bektools.ui.filter.disabled", "Off"),
        FAILED("bektools.ui.filter.failed", "Error");

        final String key;
        final String fallback;

        FilterMode(String key, String fallback){
            this.key = key;
            this.fallback = fallback;
        }

        String label(){
            return Core.bundle == null ? fallback : Core.bundle.get(key, fallback);
        }
    }

    private final Seq<NeonSettingsModule> allModules;
    private final NeonSettingsContext context;
    private final Cons<NeonSettingsModule> onSelect;
    private final Cons<String> onSearchChanged;

    private final Table filterBar;
    private final Table listTable;
    private final ScrollPane scrollPane;
    private TextField searchField;
    private Image searchIcon;
    private ImageButton clearSearchBtn;

    private NeonSettingsTab activeTab = NeonSettingsTab.BUILD_LOGISTICS;
    private String selectedModuleId;
    private String searchQuery = "";
    private FilterMode filterMode = FilterMode.ALL;
    private boolean narrowMode;
    private float savedScrollY;

    public NeonSubmodSidebar(
        Seq<NeonSettingsModule> allModules,
        NeonSettingsContext context,
        Cons<NeonSettingsModule> onSelect,
        Cons<String> onSearchChanged
    ){
        super(VscodeSettingsStyle.sidebarBackground());
        this.allModules = allModules;
        this.context = context;
        this.onSelect = onSelect;
        this.onSearchChanged = onSearchChanged;

        top().left().margin(8f);

        // Reference 1 search bar: crisp white/gold zoom icon + flat underline input field
        table(bar -> {
            bar.left().margin(2f, 2f, 4f, 2f);
            searchIcon = new Image(Icon.zoom);
            searchIcon.setScaling(Scaling.fit);
            searchIcon.setColor(VscodeSettingsStyle.primaryTextColor());
            bar.add(searchIcon).size(18f).padRight(6f).bottom().padBottom(5f);

            searchField = bar.field(searchQuery, VscodeSettingsStyle.flatSearchFieldStyle(), text -> {
                searchQuery = text == null ? "" : text;
                syncSearchDecorations();
                if(onSearchChanged != null){
                    onSearchChanged.get(searchQuery);
                }
                rebuildList();
            }).growX().minWidth(0f).height(32f).get();
            searchField.setMessageText(Core.bundle.get("bektools.ui.search.hint", "Search:"));

            clearSearchBtn = bar.button(Icon.cancelSmall, VscodeSettingsStyle.flatIconButtonStyle(), () -> {
                if(!searchQuery.isEmpty()){
                    searchQuery = "";
                    searchField.setText("");
                    syncSearchDecorations();
                    if(onSearchChanged != null){
                        onSearchChanged.get("");
                    }
                    rebuildList();
                }
            }).size(26f).padLeft(4f).bottom().padBottom(2f).get();
            syncSearchDecorations();
        }).growX().minWidth(0f).padBottom(8f).row();

        // Square gold-bordered filter chips row
        filterBar = new Table();
        filterBar.left();
        add(filterBar).growX().minWidth(0f).padBottom(8f).row();
        rebuildFilterBar();

        // Scrollable sub-mod list
        listTable = new Table();
        listTable.top().left();
        scrollPane = new ScrollPane(listTable, Styles.noBarPane);
        scrollPane.setFadeScrollBars(false);
        scrollPane.setScrollingDisabled(true, false);
        scrollPane.setOverscroll(false, false);
        scrollPane.update(() -> savedScrollY = scrollPane.getScrollY());
        add(scrollPane).grow().minHeight(0f).row();
    }

    private void syncSearchDecorations(){
        boolean hasText = searchQuery != null && !searchQuery.trim().isEmpty();
        if(searchIcon != null){
            searchIcon.setColor(hasText ? VscodeSettingsStyle.accentColor() : VscodeSettingsStyle.primaryTextColor());
        }
        if(clearSearchBtn != null){
            clearSearchBtn.visible = hasText;
        }
    }

    public void configure(NeonSettingsTab tab, String selectedId, String query, boolean narrow){
        this.activeTab = tab;
        this.selectedModuleId = selectedId;
        this.narrowMode = narrow;
        String safeQuery = query == null ? "" : query;
        if(!safeQuery.equals(searchQuery)){
            searchQuery = safeQuery;
            if(searchField != null && !safeQuery.equals(searchField.getText())){
                searchField.setText(safeQuery);
            }
            syncSearchDecorations();
        }
        rebuildFilterBar();
        rebuildList();
    }

    public void setSelectedModuleId(String moduleId){
        this.selectedModuleId = moduleId;
        rebuildList();
    }

    public void refreshStates(){
        rebuildFilterBar();
        rebuildList();
    }

    public Seq<NeonSettingsModule> visibleModules(){
        Seq<NeonSettingsModule> result = new Seq<>();
        String q = searchQuery == null ? "" : searchQuery.trim().toLowerCase(Locale.ROOT);
        boolean globalSearch = !q.isEmpty();

        for(int i = 0; i < allModules.size; i++){
            NeonSettingsModule module = allModules.get(i);
            if(!globalSearch && module.tab != activeTab){
                continue;
            }
            if(globalSearch && !matchesQuery(module, q)){
                continue;
            }
            ModuleRuntimeState state = module.runtimeState(context);
            if(!matchesFilter(state)){
                continue;
            }
            result.add(module);
        }
        return result;
    }

    private boolean matchesQuery(NeonSettingsModule module, String queryLower){
        if(module.moduleId != null && module.moduleId.toLowerCase(Locale.ROOT).contains(queryLower)) return true;
        if(module.title != null && module.title.toLowerCase(Locale.ROOT).contains(queryLower)) return true;
        if(module.tab != null && module.tab.title().toLowerCase(Locale.ROOT).contains(queryLower)) return true;
        return false;
    }

    private boolean matchesFilter(ModuleRuntimeState state){
        switch(filterMode){
            case ACTIVE:
                return state == ModuleRuntimeState.ACTIVE || state == ModuleRuntimeState.PENDING_ENABLE;
            case DISABLED:
                return state == ModuleRuntimeState.DISABLED || state == ModuleRuntimeState.PENDING_DISABLE;
            case FAILED:
                return state == ModuleRuntimeState.FAILED;
            case ALL:
            default:
                return true;
        }
    }

    private void rebuildFilterBar(){
        filterBar.clearChildren();
        boolean hasFailures = hasAnyFailures();

        FilterMode[] modes = FilterMode.values();
        for(int i = 0; i < modes.length; i++){
            FilterMode mode = modes[i];
            if(mode == FilterMode.FAILED && !hasFailures && filterMode != FilterMode.FAILED){
                continue;
            }
            boolean active = mode == filterMode;
            TextButton chip = new TextButton(mode.label(), VscodeSettingsStyle.chipButtonStyle(active));
            chip.clicked(() -> {
                filterMode = mode;
                rebuildFilterBar();
                rebuildList();
            });
            filterBar.add(chip).growX().minWidth(0f).height(28f).padRight(4f);
        }
    }

    private boolean hasAnyFailures(){
        for(int i = 0; i < allModules.size; i++){
            if(context.isModuleFailed(allModules.get(i).moduleId)) return true;
        }
        return false;
    }

    private void rebuildList(){
        float prevScroll = savedScrollY;
        listTable.clearChildren();

        String q = searchQuery == null ? "" : searchQuery.trim();
        boolean globalSearch = !q.isEmpty();
        Seq<NeonSettingsModule> shown = visibleModules();

        if(globalSearch){
            listTable.add(Core.bundle.format("bektools.ui.search.results", shown.size))
                .color(VscodeSettingsStyle.accentColor())
                .left()
                .padLeft(4f)
                .padBottom(6f)
                .growX()
                .minWidth(0f)
                .wrap()
                .row();
        }

        if(shown.isEmpty()){
            listTable.add(Core.bundle.get("bektools.ui.search.empty", "No matching sub-mods."))
                .color(VscodeSettingsStyle.mutedColor())
                .pad(14f)
                .center()
                .growX()
                .minWidth(0f)
                .wrap()
                .row();
            return;
        }

        for(int i = 0; i < shown.size; i++){
            NeonSettingsModule module = shown.get(i);
            addModuleRow(module, globalSearch);
        }

        Core.app.post(() -> {
            if(scrollPane != null){
                scrollPane.layout();
                scrollPane.setScrollYForce(prevScroll);
            }
        });
    }

    private void addModuleRow(NeonSettingsModule module, boolean showTabHint){
        boolean selected = !narrowMode && module.moduleId.equals(selectedModuleId);
        ModuleRuntimeState state = module.runtimeState(context);

        Button rowButton = new Button(VscodeSettingsStyle.sidebarItemStyle(selected));
        rowButton.touchable = Touchable.enabled;
        rowButton.left().margin(7f, 8f, 7f, 8f);
        rowButton.clicked(() -> {
            if(onSelect != null){
                onSelect.get(module);
            }
        });

        // Left vertical geometric status/selection bar
        Image accentBar = new Image(Tex.whiteui);
        accentBar.touchable = Touchable.disabled;
        accentBar.setColor(selected ? VscodeSettingsStyle.accentColor() : state.color());
        rowButton.add(accentBar).width(selected ? 3f : 2f).height(22f).padRight(8f);

        // Module icon
        if(module.icon != null){
            Image iconImg = new Image(module.icon);
            iconImg.touchable = Touchable.disabled;
            iconImg.setScaling(Scaling.fit);
            iconImg.setColor(selected
                ? VscodeSettingsStyle.accentColor()
                : (state == ModuleRuntimeState.DISABLED ? VscodeSettingsStyle.mutedColor() : VscodeSettingsStyle.primaryTextColor()));
            rowButton.add(iconImg).size(18f).padRight(8f);
        }

        // Title + optional tab or restart/failed hint
        Table textCol = new Table();
        textCol.touchable = Touchable.disabled;
        textCol.left();
        Label titleLabel = new Label(module.title);
        titleLabel.setWrap(true);
        titleLabel.setAlignment(Align.left);
        titleLabel.setColor(selected
            ? VscodeSettingsStyle.accentColor()
            : (state == ModuleRuntimeState.DISABLED ? VscodeSettingsStyle.mutedColor() : VscodeSettingsStyle.primaryTextColor()));
        textCol.add(titleLabel).left().growX().minWidth(0f).row();

        if(showTabHint || state == ModuleRuntimeState.FAILED || state.needsRestart()){
            String sub = state == ModuleRuntimeState.FAILED || state.needsRestart()
                ? state.badgeText()
                : module.tab.title();
            Label subLabel = new Label(sub);
            subLabel.setFontScale(0.82f);
            subLabel.setColor(state == ModuleRuntimeState.FAILED || state.needsRestart()
                ? state.color()
                : VscodeSettingsStyle.mutedColor());
            textCol.add(subLabel).left().growX().minWidth(0f).padTop(2f).row();
        }
        rowButton.add(textCol).left().growX().minWidth(0f);

        // Crisp square status pixel + narrow chevron
        Image dot = new Image(Tex.whiteui);
        dot.touchable = Touchable.disabled;
        dot.setColor(state.color());
        rowButton.add(dot).size(6f).padLeft(6f);

        if(narrowMode){
            Image arrow = new Image(Icon.rightOpen);
            arrow.touchable = Touchable.disabled;
            arrow.setColor(VscodeSettingsStyle.accentColor());
            rowButton.add(arrow).size(14f).padLeft(6f);
        }

        listTable.add(rowButton).growX().minWidth(0f).padBottom(4f).row();
    }
}
