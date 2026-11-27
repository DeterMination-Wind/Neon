package bektools.ui;

import arc.Core;
import arc.func.Boolc;
import arc.func.Cons;
import arc.func.Intc;
import arc.scene.Element;
import arc.scene.ui.layout.Cell;
import arc.scene.ui.layout.Table;
import mindustry.ui.dialogs.SettingsMenuDialog;

/**
 * Right-pane host {@link SettingsMenuDialog.SettingsTable} for a single bundled sub-mod.
 *
 * <p>Key responsibilities:</p>
 * <ul>
 *   <li>Neutralizes MindustryX's {@code SettingsTable.act()} auto-rebuild trap via
 *       {@link #build()} (intentionally without {@code @Override}).</li>
 *   <li>Renders {@link #pref} items inline without clearing children in {@link #finishBuild()},
 *       so direct {@code table.add(...)} / {@code table.table(...)} rows (e.g. StealthPath,
 *       ForeignServerTranslator, BetterLogisticsSpeed) are never wiped.</li>
 *   <li>Upgrades standard vanilla {@code checkPref}/{@code sliderPref}/{@code textPref} calls
 *       into unified {@link RbmStyle} dark cards.</li>
 *   <li>Hoists the module's master switch ({@code hoistedEnableKey}) into the right-pane header
 *       while preserving any sub-mod {@code changed} callback.</li>
 *   <li>Buffers {@code *-header} settings and drops empty trailing/consecutive headers
 *       (e.g. bundled Update sections whose items are hidden).</li>
 *   <li>Reflows long single-line setting descriptions so tooltips fit the viewport.</li>
 * </ul>
 */
public class SubmodSettingsHostTable extends SettingsMenuDialog.SettingsTable{
    private static final int maxTooltipLineChars = 54;

    private final String hoistedEnableKey;
    private final Runnable rebuildAction;
    private boolean suppressRebuild = true;
    private boolean building;
    private boolean flushingHeader;
    private boolean renderingSetting;
    private SettingsMenuDialog.SettingsTable.Setting pendingHeader;
    private Cons<Boolean> hoistedEnableCallback;
    private int renderedSettingCount;

    public SubmodSettingsHostTable(String hoistedEnableKey, Runnable rebuildAction){
        super();
        this.hoistedEnableKey = hoistedEnableKey;
        this.rebuildAction = rebuildAction;
        left().top();
        defaults().left();
    }

    // MindustryX-only: its SettingsTable.act() auto-rebuilds whenever list.size
    // changes (lastSize != list.size); rendering sub-mod settings grows our list
    // from 0 to N, which would otherwise replay content, collapse nested widgets,
    // and append a stray vanilla reset button one frame later. Returning this
    // short-circuits that. No @Override on purpose - vanilla Mindustry has no
    // build() to override, and MindustryX dispatches to this at runtime.
    public Table build(){
        return this;
    }

    public void beginBuild(){
        building = true;
        suppressRebuild = true;
        pendingHeader = null;
        hoistedEnableCallback = null;
        renderedSettingCount = 0;
    }

    public void finishBuild(){
        // Discard any trailing section header that had zero items after it
        // (e.g. PGMM/SPDB "Update" headers whose toggles are hidden when bundled).
        pendingHeader = null;
        building = false;
        suppressRebuild = false;
    }

    public Cons<Boolean> getHoistedEnableCallback(){
        return hoistedEnableCallback;
    }

    public boolean hasVisibleContent(){
        return renderedSettingCount > 0 || getChildren().size > 0;
    }

    @Override
    public void rebuild(){
        if(suppressRebuild || building) return;
        if(rebuildAction != null){
            rebuildAction.run();
        }
    }

    @Override
    public <T extends Element> Cell<T> add(T actor){
        // If a sub-mod appends a direct widget right after a section header (for example
        // StealthPath's addThreatModeRow/addTargetRow), flush the buffered header first
        // so visual ordering matches the sub-mod's build method.
        if(building && !flushingHeader && !renderingSetting && pendingHeader != null && actor != null){
            flushPendingHeader();
        }
        return super.add(actor);
    }

    @Override
    public void pref(SettingsMenuDialog.SettingsTable.Setting setting){
        if(setting == null) return;

        if(hoistedEnableKey != null && hoistedEnableKey.equals(setting.name)){
            if(setting instanceof RbmStyle.IconCheckSetting){
                Cons<Boolean> cb = ((RbmStyle.IconCheckSetting)setting).changedCallback();
                if(cb != null){
                    hoistedEnableCallback = cb;
                }
            }
            return;
        }

        if(building && isSectionHeaderSetting(setting)){
            // Buffer until at least one non-header setting or direct widget follows.
            pendingHeader = setting;
            return;
        }

        if(building && pendingHeader != null){
            flushPendingHeader();
        }

        renderSettingInline(setting);
    }

    @Override
    public void checkPref(String name, boolean def){
        checkPref(name, def, null);
    }

    @Override
    public void checkPref(String name, boolean def, Boolc changed){
        Core.settings.defaults(name, def);
        if(hoistedEnableKey != null && hoistedEnableKey.equals(name)){
            if(changed != null){
                hoistedEnableCallback = changed::get;
            }
            return;
        }
        pref(new RbmStyle.IconCheckSetting(name, def, null, changed == null ? null : changed::get));
    }

    @Override
    public SettingsMenuDialog.SettingsTable.SliderSetting sliderPref(
        String name, int def, int min, int max, int step,
        SettingsMenuDialog.StringProcessor s, Intc changed
    ){
        Core.settings.defaults(name, def);
        SettingsMenuDialog.SettingsTable.SliderSetting res = new SettingsMenuDialog.SettingsTable.SliderSetting(name, def, min, max, step, s, changed){
            private final RbmStyle.IconSliderSetting delegate =
                new RbmStyle.IconSliderSetting(name, def, min, max, step, null, s, changed);

            @Override
            public void add(SettingsMenuDialog.SettingsTable table){
                delegate.title = this.title;
                delegate.description = this.description;
                delegate.add(table);
            }
        };
        pref(res);
        return res;
    }

    @Override
    public void textPref(String name, String def){
        textPref(name, def, null);
    }

    @Override
    public void textPref(String name, String def, Cons<String> changed){
        Core.settings.defaults(name, def);
        pref(new RbmStyle.IconTextSetting(name, def, null, changed));
    }

    private void flushPendingHeader(){
        SettingsMenuDialog.SettingsTable.Setting header = pendingHeader;
        pendingHeader = null;
        if(header == null) return;
        flushingHeader = true;
        try{
            renderSettingInline(header);
        }finally{
            flushingHeader = false;
        }
    }

    private void renderSettingInline(SettingsMenuDialog.SettingsTable.Setting setting){
        wrapLongDescription(setting);
        list.add(setting);
        boolean prevRendering = renderingSetting;
        renderingSetting = true;
        try{
            setting.add(this);
            // Guarantee row termination so sub-mod custom Settings that omit table.row()
            // (e.g. ModUpdateCenter.ButtonSetting) never pack horizontally on the same line.
            row();
            // Restore safe left alignment defaults after each setting in case a sub-mod
            // mutated table.defaults() (e.g. PatchViewerMod).
            defaults().left().pad(0f);
        }finally{
            renderingSetting = prevRendering;
        }
        if(!(setting instanceof RbmStyle.SpacerSetting)){
            renderedSettingCount++;
        }
    }

    private static boolean isSectionHeaderSetting(SettingsMenuDialog.SettingsTable.Setting setting){
        if(setting == null || setting.name == null) return false;
        return setting.name.endsWith("-header");
    }

    private static void wrapLongDescription(SettingsMenuDialog.SettingsTable.Setting setting){
        String desc = setting.description;
        if(desc == null || desc.length() <= maxTooltipLineChars || desc.indexOf('\n') >= 0){
            return;
        }
        setting.description = wrapText(desc, maxTooltipLineChars);
    }

    private static String wrapText(String text, int maxUnits){
        StringBuilder out = new StringBuilder(text.length() + 16);
        int lineUnits = 0;
        int lastBreakPos = -1;
        int i = 0;
        while(i < text.length()){
            char c = text.charAt(i);
            // Skip Mindustry color tags like [accent] or [#ff0000] when counting visual width.
            if(c == '['){
                int close = text.indexOf(']', i + 1);
                if(close > i && close - i <= 16){
                    out.append(text, i, close + 1);
                    i = close + 1;
                    continue;
                }
            }
            int charUnits = isWideChar(c) ? 2 : 1;
            out.append(c);
            lineUnits += charUnits;
            if(c == ' ' || c == '，' || c == '。' || c == '；' || c == '、' || c == ',' || c == ';'){
                lastBreakPos = out.length();
            }
            if(lineUnits >= maxUnits){
                if(lastBreakPos > 0 && out.length() - lastBreakPos < 18){
                    out.insert(lastBreakPos, '\n');
                    lineUnits = out.length() - lastBreakPos - 1;
                }else{
                    out.append('\n');
                    lineUnits = 0;
                }
                lastBreakPos = -1;
            }
            i++;
        }
        return out.toString().trim();
    }

    private static boolean isWideChar(char c){
        return c >= 0x2E80;
    }
}
