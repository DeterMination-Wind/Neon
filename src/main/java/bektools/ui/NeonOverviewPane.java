package bektools.ui;

import arc.Core;
import arc.func.Cons;
import arc.scene.ui.CheckBox;
import arc.scene.ui.Image;
import arc.scene.ui.Label;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.TextButton;
import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import arc.util.Align;
import arc.util.Scaling;
import mdtxcompat.LegacyMindustryXGuard;
import mindustry.gen.Icon;
import mindustry.gen.Tex;
import mindustry.ui.Styles;

/**
 * Overview tab pane presenting module counts, runtime environment badge, pending-restart notice,
 * failure diagnostics, quick module switchboard grouped by tab, and issue feedback entry.
 */
public class NeonOverviewPane extends Table{
    private final Seq<NeonSettingsModule> modules;
    private final NeonSettingsContext context;
    private final Cons<NeonSettingsModule> onOpenModule;
    private final Runnable onStateMutated;

    private final Table contentTable;
    private final ScrollPane scrollPane;
    private NeonSubmodSidebar.FilterMode filterMode = NeonSubmodSidebar.FilterMode.ALL;
    private boolean narrowMode;
    private float savedScrollY;

    public NeonOverviewPane(
        Seq<NeonSettingsModule> modules,
        NeonSettingsContext context,
        Cons<NeonSettingsModule> onOpenModule,
        Runnable onStateMutated
    ){
        super(VscodeSettingsStyle.panelBackground());
        this.modules = modules;
        this.context = context;
        this.onOpenModule = onOpenModule;
        this.onStateMutated = onStateMutated;

        top().left().margin(10f);

        contentTable = new Table();
        contentTable.top().left();
        scrollPane = new ScrollPane(contentTable, Styles.noBarPane);
        scrollPane.setFadeScrollBars(false);
        scrollPane.setScrollingDisabled(true, false);
        scrollPane.setOverscroll(false, false);
        scrollPane.update(() -> savedScrollY = scrollPane.getScrollY());
        add(scrollPane).grow().minHeight(0f).row();
    }

    public void rebuild(boolean narrow){
        this.narrowMode = narrow;
        float prevScroll = savedScrollY;
        contentTable.clearChildren();

        int activeCount = 0;
        int disabledCount = 0;
        int pendingCount = 0;
        int failedCount = 0;
        Seq<NeonSettingsModule> failedModules = new Seq<>();

        for(int i = 0; i < modules.size; i++){
            NeonSettingsModule module = modules.get(i);
            ModuleRuntimeState state = module.runtimeState(context);
            switch(state){
                case ACTIVE:
                    activeCount++;
                    break;
                case DISABLED:
                    disabledCount++;
                    break;
                case PENDING_ENABLE:
                case PENDING_DISABLE:
                    pendingCount++;
                    break;
                case FAILED:
                    failedCount++;
                    failedModules.add(module);
                    break;
            }
        }

        buildSummarySection(activeCount, disabledCount, pendingCount, failedCount);

        if(pendingCount > 0){
            buildPendingRestartBanner(pendingCount);
        }

        if(!failedModules.isEmpty()){
            buildFailureSection(failedModules);
        }

        buildQuickSwitchboard(failedCount > 0);
        buildFooterSection();

        Core.app.post(() -> {
            if(scrollPane != null){
                scrollPane.layout();
                scrollPane.setScrollYForce(prevScroll);
            }
        });
    }

    private void buildSummarySection(int activeCount, int disabledCount, int pendingCount, int failedCount){
        boolean mdtx = LegacyMindustryXGuard.isMindustryXRuntime();
        String runtimeText = mdtx
            ? Core.bundle.get("bektools.ui.runtime.mdtx", "MindustryX")
            : Core.bundle.get("bektools.ui.runtime.vanilla", "Vanilla Mindustry");

        contentTable.table(VscodeSettingsStyle.headerBackground(), header -> {
            header.top().left().margin(12f);

            header.table(topRow -> {
                topRow.left();
                topRow.image(Icon.settings).size(22f).color(VscodeSettingsStyle.accentColor()).padRight(8f);
                Label title = new Label(Core.bundle.get("bektools.ui.overview.title", "Neon Module Overview"));
                title.setColor(VscodeSettingsStyle.primaryTextColor());
                title.setWrap(true);
                topRow.add(title).left().growX().minWidth(0f);

                topRow.table(VscodeSettingsStyle.cardBackground(), badge -> {
                    badge.margin(4f, 8f, 4f, 8f);
                    Label rt = new Label(runtimeText);
                    rt.setFontScale(0.84f);
                    rt.setColor(VscodeSettingsStyle.accentColor());
                    badge.add(rt);
                }).right().padLeft(8f);
            }).growX().minWidth(0f).padBottom(10f).row();

            // Stat counters grid (2x2 in narrow mode, 4-column row in wide mode)
            header.table(stats -> {
                stats.left();
                addStatTile(stats, Core.bundle.get("bektools.ui.stat.active", "Enabled"), String.valueOf(activeCount), VscodeSettingsStyle.statusActiveColor());
                addStatTile(stats, Core.bundle.get("bektools.ui.stat.disabled", "Disabled"), String.valueOf(disabledCount), VscodeSettingsStyle.statusDisabledColor());
                if(narrowMode) stats.row();
                addStatTile(stats, Core.bundle.get("bektools.ui.stat.pending", "Pending Restart"), String.valueOf(pendingCount), VscodeSettingsStyle.statusPendingColor());
                addStatTile(stats, Core.bundle.get("bektools.ui.stat.failed", "Failed"), String.valueOf(failedCount),
                    failedCount > 0 ? VscodeSettingsStyle.statusFailedColor() : VscodeSettingsStyle.mutedColor());
            }).growX().minWidth(0f).row();
        }).growX().minWidth(0f).padBottom(8f).row();
    }

    private void addStatTile(Table parent, String label, String value, arc.graphics.Color color){
        parent.table(VscodeSettingsStyle.cardBackground(), tile -> {
            tile.left().margin(8f);
            Label val = new Label(value);
            val.setFontScale(1.2f);
            val.setColor(color);
            tile.add(val).left().row();

            Label cap = new Label(label);
            cap.setFontScale(0.84f);
            cap.setColor(VscodeSettingsStyle.mutedColor());
            cap.setWrap(true);
            tile.add(cap).left().growX().minWidth(0f).padTop(2f).row();
        }).growX().minWidth(0f).pad(3f);
    }

    private void buildPendingRestartBanner(int pendingCount){
        contentTable.table(VscodeSettingsStyle.bannerBackground(ModuleRuntimeState.PENDING_ENABLE), banner -> {
            banner.left().margin(10f);
            banner.image(Icon.refreshSmall).size(18f).color(VscodeSettingsStyle.statusPendingColor()).padRight(8f).top();
            Label msg = new Label(Core.bundle.format("bektools.ui.overview.pending_banner", pendingCount));
            msg.setColor(VscodeSettingsStyle.statusPendingColor());
            msg.setWrap(true);
            msg.setAlignment(Align.left);
            banner.add(msg).left().growX().minWidth(0f);
        }).growX().minWidth(0f).padBottom(8f).row();
    }

    private void buildFailureSection(Seq<NeonSettingsModule> failedModules){
        contentTable.table(VscodeSettingsStyle.bannerBackground(ModuleRuntimeState.FAILED), box -> {
            box.top().left().margin(10f);
            box.table(head -> {
                head.left();
                head.image(Icon.warningSmall).size(18f).color(VscodeSettingsStyle.statusFailedColor()).padRight(8f);
                Label title = new Label(Core.bundle.format("bektools.ui.overview.failed_header", failedModules.size));
                title.setColor(VscodeSettingsStyle.statusFailedColor());
                title.setWrap(true);
                head.add(title).left().growX().minWidth(0f);
            }).growX().minWidth(0f).padBottom(6f).row();

            for(int i = 0; i < failedModules.size; i++){
                NeonSettingsModule module = failedModules.get(i);
                Throwable err = context.getModuleFailure(module.moduleId);
                String reason = err == null ? "" : (err.getClass().getSimpleName() + (err.getMessage() == null ? "" : ": " + err.getMessage()));
                box.table(VscodeSettingsStyle.headerBackground(), row -> {
                    row.left().margin(8f);
                    Table leftCol = new Table();
                    leftCol.left();
                    leftCol.add(module.title).color(VscodeSettingsStyle.primaryTextColor()).left().growX().minWidth(0f).wrap().row();
                    if(!reason.isEmpty()){
                        Label r = new Label(reason);
                        r.setFontScale(0.82f);
                        r.setColor(VscodeSettingsStyle.statusFailedColor());
                        r.setWrap(true);
                        leftCol.add(r).left().growX().minWidth(0f).padTop(2f).row();
                    }
                    row.add(leftCol).left().growX().minWidth(0f);
                    row.button(Core.bundle.get("bektools.ui.overview.inspect", "Details"), Styles.flatt, () -> {
                        if(onOpenModule != null) onOpenModule.get(module);
                    }).height(34f).padLeft(8f);
                }).growX().minWidth(0f).padTop(4f).row();
            }
        }).growX().minWidth(0f).padBottom(8f).row();
    }

    private void buildQuickSwitchboard(boolean hasFailures){
        contentTable.table(VscodeSettingsStyle.headerBackground(), bar -> {
            bar.left().margin(8f);
            Label title = new Label(Core.bundle.get("bektools.ui.overview.switchboard", "Quick Module Switches"));
            title.setColor(VscodeSettingsStyle.accentColor());
            bar.add(title).left().growX().minWidth(0f).wrap();

            Table chips = new Table();
            chips.right();
            for(NeonSubmodSidebar.FilterMode mode : NeonSubmodSidebar.FilterMode.values()){
                if(mode == NeonSubmodSidebar.FilterMode.FAILED && !hasFailures && filterMode != mode){
                    continue;
                }
                TextButton chip = new TextButton(mode.label(), VscodeSettingsStyle.chipButtonStyle(filterMode == mode));
                chip.clicked(() -> {
                    filterMode = mode;
                    rebuild(narrowMode);
                });
                chips.add(chip).height(28f).padLeft(4f).minWidth(48f);
            }
            if(narrowMode){
                bar.row();
                bar.add(chips).left().padTop(6f);
            }else{
                bar.add(chips).right().padLeft(8f);
            }
        }).growX().minWidth(0f).padBottom(6f).row();

        NeonSettingsTab[] domainTabs = {
            NeonSettingsTab.BUILD_LOGISTICS,
            NeonSettingsTab.COMBAT_HUD,
            NeonSettingsTab.LOGIC_EDITOR,
            NeonSettingsTab.TOOLS_CORE
        };

        int totalRendered = 0;
        for(NeonSettingsTab tab : domainTabs){
            Seq<NeonSettingsModule> group = new Seq<>();
            for(int i = 0; i < modules.size; i++){
                NeonSettingsModule m = modules.get(i);
                if(m.tab != tab) continue;
                if(!matchesOverviewFilter(m.runtimeState(context))) continue;
                group.add(m);
            }
            if(group.isEmpty()) continue;

            totalRendered += group.size;
            contentTable.table(secHead -> {
                secHead.left();
                if(tab.icon() != null){
                    secHead.image(tab.icon()).size(16f).color(VscodeSettingsStyle.accentColor()).padRight(6f);
                }
                secHead.add(tab.title()).color(VscodeSettingsStyle.accentColor()).left();
            }).growX().minWidth(0f).padTop(6f).padBottom(4f).row();

            for(int i = 0; i < group.size; i++){
                addQuickModuleRow(group.get(i));
            }
        }

        if(totalRendered == 0){
            contentTable.add(Core.bundle.get("bektools.ui.search.empty", "No matching sub-mods."))
                .color(VscodeSettingsStyle.mutedColor())
                .pad(14f)
                .center()
                .growX()
                .row();
        }
    }

    private void addQuickModuleRow(NeonSettingsModule module){
        ModuleRuntimeState state = module.runtimeState(context);
        contentTable.table(VscodeSettingsStyle.cardBackground(), row -> {
            row.left().margin(8f);

            Image dot = new Image(Tex.whiteui);
            dot.setColor(state.color());
            row.add(dot).size(7f).padRight(8f);

            if(module.icon != null){
                Image ic = new Image(module.icon);
                ic.setScaling(Scaling.fit);
                ic.setColor(state == ModuleRuntimeState.DISABLED ? VscodeSettingsStyle.mutedColor() : VscodeSettingsStyle.primaryTextColor());
                row.add(ic).size(18f).padRight(8f);
            }

            Table info = new Table();
            info.left();
            Label title = new Label(module.title);
            title.setWrap(true);
            title.setColor(state == ModuleRuntimeState.DISABLED ? VscodeSettingsStyle.mutedColor() : VscodeSettingsStyle.primaryTextColor());
            info.add(title).left().growX().minWidth(0f).row();

            if(state != ModuleRuntimeState.ACTIVE){
                Label badge = new Label(state.badgeText());
                badge.setFontScale(0.82f);
                badge.setColor(state.color());
                info.add(badge).left().growX().minWidth(0f).padTop(1f).row();
            }
            row.add(info).left().growX().minWidth(0f).padRight(8f);

            if(module.enableKey != null && state != ModuleRuntimeState.FAILED){
                CheckBox toggle = new CheckBox("");
                toggle.setChecked(module.isSwitchOn());
                toggle.changed(() -> {
                    module.setSwitchOn(toggle.isChecked());
                    if(onStateMutated != null) onStateMutated.run();
                });
                row.add(toggle).padRight(6f);
            }

            row.button(Icon.rightOpen, Styles.emptyi, () -> {
                if(onOpenModule != null) onOpenModule.get(module);
            }).size(34f);
        }).growX().minWidth(0f).padBottom(4f).row();
    }

    private void buildFooterSection(){
        contentTable.image(VscodeSettingsStyle.dividerDrawable()).height(1f).growX().padTop(10f).padBottom(10f).row();
        contentTable.table(footer -> {
            footer.left();
            footer.button("@bektools.feedback", Icon.chat, Styles.flatt, context::openFeedback)
                .margin(10f)
                .height(42f)
                .minWidth(180f)
                .left();
        }).growX().minWidth(0f).padBottom(8f).row();
    }

    private boolean matchesOverviewFilter(ModuleRuntimeState state){
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
}
