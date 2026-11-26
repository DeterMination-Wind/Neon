package bektools.ui;

import arc.scene.ui.layout.Cell;
import arc.struct.Seq;
import mindustry.ui.dialogs.SettingsMenuDialog;

/**
 * Single top-level {@link SettingsMenuDialog.SettingsTable.Setting} mounted into Neon's settings category.
 *
 * <p>Keeping the outer {@link SettingsMenuDialog.SettingsTable} at a constant {@code list.size == 1}
 * guarantees that MindustryX's {@code SettingsTable.act()} ({@code lastSize != list.size}) never
 * triggers spurious rebuilds, collapses, or duplicate reset buttons while the user switches tabs
 * or toggles sub-mods inside {@link NeonSettingsWorkbench}.</p>
 */
public class NeonSettingsHostSetting extends SettingsMenuDialog.SettingsTable.Setting{
    private final Seq<NeonSettingsModule> modules;
    private final NeonSettingsContext context;

    public NeonSettingsHostSetting(Seq<NeonSettingsModule> modules, NeonSettingsContext context){
        super(null);
        this.modules = modules;
        this.context = context;
    }

    @Override
    public void add(SettingsMenuDialog.SettingsTable table){
        NeonSettingsWorkbench workbench = new NeonSettingsWorkbench(modules, context);
        table.row();
        Cell<NeonSettingsWorkbench> hostCell = table.add(workbench)
            .width(workbench.computeWorkbenchWidthDp())
            .height(workbench.computeWorkbenchHeightDp())
            .center();
        table.row();

        // Keep outer cell dimensions synced when the desktop window is resized.
        final float[] lastApplied = {workbench.computeWorkbenchWidthDp(), workbench.computeWorkbenchHeightDp()};
        workbench.update(() -> {
            float targetW = workbench.computeWorkbenchWidthDp();
            float targetH = workbench.computeWorkbenchHeightDp();
            if(Math.abs(targetW - lastApplied[0]) >= 24f || Math.abs(targetH - lastApplied[1]) >= 24f){
                lastApplied[0] = targetW;
                lastApplied[1] = targetH;
                hostCell.width(targetW).height(targetH);
                table.invalidateHierarchy();
            }
        });
    }
}
