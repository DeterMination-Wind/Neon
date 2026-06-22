package bektools.profiler;

import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.scene.Element;
import arc.scene.event.Touchable;
import arc.scene.ui.Image;
import arc.scene.ui.Label;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.TextButton;
import arc.scene.ui.TextField;
import arc.scene.ui.layout.Collapser;
import arc.scene.ui.layout.Scl;
import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import arc.util.Align;
import arc.util.CommandHandler;
import arc.util.Log;
import arc.util.Strings;
import arc.util.Time;
import mdtxcompat.OverlayUiBridge;
import mindustry.Vars;
import mindustry.game.EventType;
import mindustry.gen.Icon;
import mindustry.gen.Tex;
import mindustry.mod.Scripts;
import mindustry.graphics.Pal;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.ui.dialogs.SettingsMenuDialog;
import mindustry.ui.fragments.ConsoleFragment;
import rhino.ScriptableObject;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class NeonProfilerFeature{
    private static final String categoryKey = "neon-profiler-category";
    private static final String keyEnabled = "neon-profiler-enabled";
    private static final String keyShowAll = "neon-profiler-show-all";
    private static final String overlayWindowName = "neon-profiler";
    private static final String fallbackHudName = "neon-profiler-hud";
    private static final NeonProfilerConsoleApi consoleApi = new NeonProfilerConsoleApi();

    private static OverlayUiBridge overlayUi = OverlayUiBridge.UNSUPPORTED;
    private static OverlayUiBridge.OverlayWindowHandle overlayWindow;

    private static boolean initialized;
    private static boolean consoleApiLogged;
    private static boolean consoleSlashBridgeLogged;
    private static boolean consoleSlashBridgeInstalled;
    private static float nextAttachAttempt;
    private static float nextConsoleApiAttempt;
    private static float nextConsoleBridgeAttempt;
    private static float nextUiRefresh;

    private static Table panel;
    private static Label headerLabel;
    private static Label memoryLabel;
    private static Table rowsTable;
    private static TextButton detailsButton;

    private static BaseDialog detailDialog;
    private static Label detailHeaderLabel;
    private static Label detailMemoryLabel;
    private static Table detailRowsTable;
    private static Label detailFooterLabel;
    private static TextButton detailToggleButton;
    private static ScrollPane detailScrollPane;
    private static final LinkedHashMap<String, Boolean> detailExpanded = new LinkedHashMap<>();
    private static float detailScrollY;

    private NeonProfilerFeature(){
    }

    public static void configureOverlayUi(OverlayUiBridge value){
        overlayUi = value == null ? OverlayUiBridge.UNSUPPORTED : value;
    }

    public static void init(){
        if(initialized) return;
        initialized = true;

        Events.on(EventType.ClientLoadEvent.class, e -> {
            Core.settings.defaults(keyEnabled, false);
            Core.settings.defaults(keyShowAll, false);
            NeonProfiler.setEnabled(false);
            NeonProfiler.setShowAll(Core.settings.getBool(keyShowAll, false));
            ensurePanelBuilt();
            nextAttachAttempt = 0f;
            nextConsoleApiAttempt = 0f;
            nextConsoleBridgeAttempt = 0f;
            installConsoleApi();
            installConsoleSlashBridge();
            Time.runTask(10f, NeonProfilerFeature::installConsoleApi);
            Time.runTask(10f, NeonProfilerFeature::installConsoleSlashBridge);
        });

        Events.on(EventType.ResetEvent.class, e -> NeonProfiler.reset());

        Events.run(EventType.Trigger.update, () -> {
            if(Vars.headless) return;
            NeonProfiler.setShowAll(Core.settings.getBool(keyShowAll, false));
            if(Time.time >= nextAttachAttempt){
                nextAttachAttempt = Time.time + 60f;
                ensureAttached();
            }
            if(Time.time >= nextConsoleApiAttempt){
                nextConsoleApiAttempt = Time.time + 300f;
                installConsoleApi();
            }
            if(!consoleSlashBridgeInstalled && Time.time >= nextConsoleBridgeAttempt){
                nextConsoleBridgeAttempt = Time.time + 300f;
                installConsoleSlashBridge();
            }
            if(Time.time >= nextUiRefresh){
                nextUiRefresh = Time.time + 60f;
                refreshPanel();
            }
        });
    }

    public static void registerClientCommands(CommandHandler handler){
        handler.<mindustry.gen.Player>register("neon-profiler", "[on/off/toggle/reset/copy/all]", "Control Neon profiler.", (args, player) -> {
            String mode = args.length == 0 ? "toggle" : args[0].toLowerCase();
            if(!applyMode(mode)) return;
            refreshPanel();
        });
    }

    private static boolean applyMode(String mode){
        String op = mode == null ? "toggle" : mode.toLowerCase();
        switch(op){
            case "on":
                setEnabled(true);
                return true;
            case "off":
                setEnabled(false);
                return true;
            case "toggle":
                setEnabled(!NeonProfiler.isEnabled());
                return true;
            case "reset":
                NeonProfiler.reset();
                return true;
            case "copy":
                Core.app.setClipboardText(NeonProfiler.copySummaryText());
                return true;
            case "all":
                boolean showAll = !NeonProfiler.isShowAll();
                NeonProfiler.setShowAll(showAll);
                Core.settings.put(keyShowAll, showAll);
                return true;
            default:
                return false;
        }
    }

    private static void installConsoleApi(){
        if(Vars.headless) return;
        if(Vars.mods == null) return;
        try{
            Scripts scripts = Vars.mods.getScripts();
            if(scripts == null || scripts.scope == null) return;
            ScriptableObject.putProperty(scripts.scope, "neonProfiler", rhino.Context.javaToJS(consoleApi, scripts.scope));
            if(!consoleApiLogged){
                consoleApiLogged = true;
                Log.info("NeonProfiler: F8 console API installed. Try: neonProfiler.help()");
            }
        }catch(Throwable t){
            Log.err("NeonProfiler: failed to install F8 console API.", t);
        }
    }

    private static void installConsoleSlashBridge(){
        if(Vars.headless || Vars.ui == null || Vars.ui.consolefrag == null) return;
        if(Vars.ui.consolefrag instanceof NeonConsoleFragment){
            consoleSlashBridgeInstalled = true;
            return;
        }
        if(Vars.ui.consolefrag.getClass() != ConsoleFragment.class) return;

        try{
            ConsoleFragment previous = Vars.ui.consolefrag;
            NeonConsoleFragment replacement = new NeonConsoleFragment();
            replacement.copyMessagesFrom(previous);
            previous.remove();
            Vars.ui.consolefrag = replacement;
            replacement.build(Vars.ui.hudGroup);
            consoleSlashBridgeInstalled = true;
            if(!consoleSlashBridgeLogged){
                consoleSlashBridgeLogged = true;
                Log.info("NeonProfiler: F8 slash bridge installed. Supports /neon-profiler ...");
            }
        }catch(Throwable t){
            Log.err("NeonProfiler: failed to install F8 slash bridge.", t);
        }
    }

    private static String runSlashConsoleCommand(String text){
        if(text == null) return "Usage: /neon-profiler [on/off/toggle/reset/copy/all/status/help]";

        String trimmed = text.trim();
        if(!trimmed.startsWith("/")) return null;

        String[] tokens = trimmed.substring(1).split("\\s+");
        if(tokens.length == 0 || tokens[0].isEmpty()) return null;
        if(!tokens[0].equalsIgnoreCase("neon-profiler")) return null;

        if(tokens.length <= 1){
            applyMode("toggle");
            refreshPanel();
            return consoleApi.status();
        }

        String mode = tokens[1].toLowerCase(Locale.ROOT);
        if(mode.equals("help")) return consoleApi.help();
        if(mode.equals("status")) return consoleApi.status();
        return consoleApi.run(mode);
    }

    private static class NeonConsoleFragment extends ConsoleFragment{
        private static final Field fieldChatField = reflectField("chatfield");
        private static final Field fieldMessages = reflectField("messages");
        private static final Field fieldHistory = reflectField("history");
        private static final Field fieldScrollPos = reflectField("scrollPos");

        @Override
        public void toggle(){
            if(!open()){
                super.toggle();
                return;
            }

            String message = currentInput();
            String result = runSlashConsoleCommand(message);
            if(result == null){
                super.toggle();
                return;
            }

            rememberHistory(message);
            addMessage("[lightgray]> " + escapeConsoleText(message == null ? "" : message));
            hide();
            setIntField(fieldScrollPos, this, 0);
            addMessage(escapeConsoleText(result));
        }

        void copyMessagesFrom(ConsoleFragment other){
            if(other == null) return;
            Seq<String> otherMessages = getSeqField(fieldMessages, other);
            Seq<String> thisMessages = getSeqField(fieldMessages, this);
            if(otherMessages == null || thisMessages == null) return;
            thisMessages.clear();
            thisMessages.addAll(otherMessages);
        }

        private String currentInput(){
            TextField field = getTextField(fieldChatField, this);
            return field == null ? "" : field.getText();
        }

        private void rememberHistory(String message){
            Seq<String> history = getSeqField(fieldHistory, this);
            if(history == null) return;
            if(history.size < 2 || !history.get(1).equals(message)){
                history.insert(1, message);
            }
        }

        private static Field reflectField(String name){
            try{
                Field field = ConsoleFragment.class.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            }catch(Throwable t){
                throw new RuntimeException(t);
            }
        }

        @SuppressWarnings("unchecked")
        private static Seq<String> getSeqField(Field field, Object target){
            try{
                return (Seq<String>)field.get(target);
            }catch(Throwable t){
                return null;
            }
        }

        private static TextField getTextField(Field field, Object target){
            try{
                return (TextField)field.get(target);
            }catch(Throwable t){
                return null;
            }
        }

        private static void setIntField(Field field, Object target, int value){
            try{
                field.setInt(target, value);
            }catch(Throwable ignored){
            }
        }
    }

    private static String escapeConsoleText(String text){
        return text == null ? "null" : text.replace("[", "[[");
    }

    public static class NeonProfilerConsoleApi{
        public String help(){
            return "NeonProfiler F8 API:\n"
                + "  neonProfiler.on()      - enable profiler\n"
                + "  neonProfiler.off()     - disable profiler and reset data\n"
                + "  neonProfiler.toggle()  - toggle profiler\n"
                + "  neonProfiler.reset()   - clear collected samples\n"
                + "  neonProfiler.copy()    - copy summary text to clipboard\n"
                + "  neonProfiler.all()     - toggle top/all rows\n"
                + "  neonProfiler.status()  - print current state\n"
                + "  neonProfiler.run(mode) - mode: on/off/toggle/reset/copy/all";
        }

        public String on(){
            setEnabled(true);
            return status();
        }

        public String off(){
            setEnabled(false);
            return status();
        }

        public String toggle(){
            applyMode("toggle");
            return status();
        }

        public String reset(){
            applyMode("reset");
            refreshPanel();
            return status();
        }

        public String copy(){
            applyMode("copy");
            refreshPanel();
            return "Copied Neon profiler summary to clipboard.";
        }

        public String all(){
            applyMode("all");
            refreshPanel();
            return status();
        }

        public String run(String mode){
            if(!applyMode(mode)){
                return "Unknown mode: " + mode + "\n" + help();
            }
            refreshPanel();
            return status();
        }

        public String status(){
            NeonProfiler.Snapshot snapshot = NeonProfiler.snapshot();
            return "Neon profiler: enabled=" + snapshot.enabled
                + ", showAll=" + snapshot.showAll
                + ", neonMsPerSec=" + Strings.autoFixed((float)snapshot.neonTotalMsPerSecond, 3)
                + ", selfMsPerSec=" + Strings.autoFixed((float)snapshot.selfTotalMsPerSecond, 3)
                + ", window=" + snapshot.windowSeconds + "s";
        }
    }

    public static void buildSettings(SettingsMenuDialog.SettingsTable table){
        table.pref(new SettingsMenuDialog.SettingsTable.Setting("neon-profiler-toggle") {
            @Override
            public void add(SettingsMenuDialog.SettingsTable t){
                TextButton button = t.button("", () -> {
                    boolean enable = !NeonProfiler.isEnabled();
                    setEnabled(enable);
                    if(enable){
                        openPanel();
                    }
                }).growX().margin(14f).pad(6f).get();
                button.update(() -> button.setText(
                    NeonProfiler.isEnabled()
                        ? Core.bundle.get("neon.profiler.action.off", "Disable")
                        : Core.bundle.get("neon.profiler.action.on", "Enable")
                ));
                t.row();
            }
        });
        table.pref(new SettingsMenuDialog.SettingsTable.Setting("neon-profiler-enabled") {
            @Override
            public void add(SettingsMenuDialog.SettingsTable t){
                t.check(Core.bundle.get("neon.profiler.action.enabled", "Enable profiler"), Core.settings.getBool(keyEnabled, false), checked -> setEnabled(checked)).left().row();
            }
        });
        table.pref(new SettingsMenuDialog.SettingsTable.Setting("neon-profiler-open") {
            @Override
            public void add(SettingsMenuDialog.SettingsTable t){
                t.button(Core.bundle.get("neon.profiler.action.open", "Open profiler"), NeonProfilerFeature::openPanel).growX().margin(14f).pad(6f).row();
            }
        });
        table.pref(new SettingsMenuDialog.SettingsTable.Setting("neon-profiler-reset") {
            @Override
            public void add(SettingsMenuDialog.SettingsTable t){
                t.button(Core.bundle.get("neon.profiler.action.reset", "Reset"), NeonProfiler::reset).growX().margin(14f).pad(6f).row();
            }
        });
        table.pref(new SettingsMenuDialog.SettingsTable.Setting("neon-profiler-copy") {
            @Override
            public void add(SettingsMenuDialog.SettingsTable t){
                t.button(Core.bundle.get("neon.profiler.action.copy", "Copy summary"), () -> Core.app.setClipboardText(NeonProfiler.copySummaryText())).growX().margin(14f).pad(6f).row();
            }
        });
        table.pref(new SettingsMenuDialog.SettingsTable.Setting("neon-profiler-all") {
            @Override
            public void add(SettingsMenuDialog.SettingsTable t){
                t.check(Core.bundle.get("neon.profiler.action.showall", "Show all rows"), Core.settings.getBool(keyShowAll, false), checked -> {
                    Core.settings.put(keyShowAll, checked);
                    NeonProfiler.setShowAll(checked);
                    refreshPanel();
                }).left().row();
            }
        });
    }

    public static void setEnabled(boolean enabled){
        NeonProfiler.setEnabled(enabled);
        Core.settings.put(keyEnabled, enabled);
        if(!enabled){
            NeonProfiler.reset();
        }
        refreshPanel();
    }

    public static void openPanel(){
        ensureDetailDialogBuilt();
        refreshPanel();
        if(detailDialog != null){
            detailDialog.show();
        }
    }

    private static void ensureAttached(){
        if(Vars.ui == null || Vars.ui.hudGroup == null) return;
        ensurePanelBuilt();
        if(overlayUi.isSupported()){
            if(overlayWindow == null){
                try{
                    if(panel.hasParent()) panel.remove();
                }catch(Throwable ignored){
                }
                overlayWindow = overlayUi.registerWindow(overlayWindowName, panel, () -> Vars.state != null && Vars.state.isGame() && NeonProfiler.isEnabled());
                if(overlayWindow != null){
                    overlayWindow.configure(true, false);
                }
            }
            return;
        }

        Element existing = Vars.ui.hudGroup.find(fallbackHudName);
        if(existing == panel) return;
        if(existing != null){
            existing.remove();
        }
        try{
            if(panel.hasParent()) panel.remove();
        }catch(Throwable ignored){
        }
        panel.name = fallbackHudName;
        Vars.ui.hudGroup.addChild(panel);
        panel.toFront();
        panel.touchable = Touchable.childrenOnly;
        panel.update(() -> {
            panel.visible = Vars.state != null && Vars.state.isGame() && NeonProfiler.isEnabled();
            panel.pack();
            panel.setPosition(Core.graphics.getWidth() - panel.getWidth() - 12f, Core.graphics.getHeight() - 12f, Align.topRight);
        });
    }

    private static void ensurePanelBuilt(){
        if(panel != null) return;

        panel = new Table(Styles.black6);
        panel.margin(6f);
        panel.touchable = Touchable.childrenOnly;
        panel.defaults().left().growX();

        headerLabel = new Label("", Styles.outlineLabel);
        headerLabel.setColor(Color.white);
        panel.add(headerLabel).row();

        memoryLabel = new Label("", Styles.outlineLabel);
        memoryLabel.setColor(Color.lightGray);
        panel.add(memoryLabel).padTop(2f).row();

        rowsTable = new Table(Tex.pane);
        rowsTable.margin(5f);
        panel.add(rowsTable).width(360f).padTop(4f).row();

        Table buttons = new Table();
        buttons.defaults().height(32f);
        detailsButton = buttons.button("", Styles.flatt, NeonProfilerFeature::openPanel).get();
        panel.add(buttons).padTop(6f).row();

        ensureDetailDialogBuilt();
        refreshPanel();
    }

    private static void ensureDetailDialogBuilt(){
        if(detailDialog != null) return;

        detailDialog = new BaseDialog(Core.bundle.get("neon.profiler.detail.title", "Neon Profiler Details"));
        detailDialog.cont.margin(12f);
        detailDialog.cont.defaults().grow().center();
        detailDialog.cont.table(wrapper -> {
            wrapper.center();
            wrapper.table(Styles.black6, content -> {
                content.margin(12f);
                content.defaults().left().growX();

                detailHeaderLabel = new Label("", Styles.outlineLabel);
                detailHeaderLabel.setColor(Color.white);
                content.add(detailHeaderLabel).row();

                detailMemoryLabel = new Label("", Styles.outlineLabel);
                detailMemoryLabel.setColor(Color.lightGray);
                content.add(detailMemoryLabel).padTop(2f).row();

                detailRowsTable = new Table(Tex.pane);
                detailRowsTable.margin(8f);
                detailScrollPane = new ScrollPane(detailRowsTable);
                detailScrollPane.setFadeScrollBars(false);
                detailScrollPane.setScrollingDisabled(true, false);
                detailScrollPane.update(() -> detailScrollY = detailScrollPane.getScrollY());
                content.add(detailScrollPane).width(detailPaneWidth()).height(detailPaneHeight()).padTop(8f).grow().row();

                detailFooterLabel = new Label("", Styles.outlineLabel);
                detailFooterLabel.setColor(Color.lightGray);
                content.add(detailFooterLabel).padTop(8f).row();
            }).width(detailPaneWidth() + Scl.scl(24f)).center();
        }).grow().center();

        detailDialog.buttons.defaults().size(220f, 64f).pad(6f);
        detailToggleButton = detailDialog.buttons.button("", Styles.flatt, () -> {
            setEnabled(!NeonProfiler.isEnabled());
            refreshPanel();
        }).get();
        detailDialog.buttons.button(Core.bundle.get("neon.profiler.action.reset", "Reset"), Styles.flatt, () -> {
            NeonProfiler.reset();
            refreshPanel();
        });
        detailDialog.buttons.button(Core.bundle.get("neon.profiler.action.copy", "Copy summary"), Styles.flatt, () -> Core.app.setClipboardText(NeonProfiler.copySummaryText()));
        detailDialog.buttons.button("@back", Icon.left, detailDialog::hide);
        detailDialog.closeOnBack();
        detailDialog.shown(NeonProfilerFeature::refreshPanel);
    }

    private static void refreshPanel(){
        if(panel == null) return;
        long started = Time.nanos();
        NeonProfiler.Snapshot snapshot = NeonProfiler.snapshot();

        headerLabel.setText(
            Core.bundle.get("neon.profiler.title", "Neon Profiler")
                + "  "
                + (snapshot.enabled ? Core.bundle.get("neon.profiler.state.on", "ON") : Core.bundle.get("neon.profiler.state.off", "OFF"))
                + "  "
                + snapshot.windowSeconds + "s"
                + "  Neon "
                + Strings.autoFixed((float)snapshot.neonTotalMsPerSecond, 3) + " ms/s"
        );

        memoryLabel.setText(
            Core.bundle.get("neon.profiler.memory", "Memory")
                + " "
                + Strings.autoFixed(snapshot.memUsedBytes / 1024f / 1024f, 1)
                + "/"
                + Strings.autoFixed(snapshot.memTotalBytes / 1024f / 1024f, 1)
                + " MiB"
        );

        detailsButton.setText(Core.bundle.get("neon.profiler.action.details", "Details"));

        rowsTable.clearChildren();
        rowsTable.defaults().left().padBottom(2f);
        rowsTable.add(Core.bundle.get("neon.profiler.col.name", "Name")).color(Color.white).padRight(12f).minWidth(170f).left();
        rowsTable.add(Core.bundle.get("neon.profiler.col.msps", "ms/s")).color(Color.white).padRight(12f).width(72f).right();
        rowsTable.add(Core.bundle.get("neon.profiler.col.pct", "%")).color(Color.white).width(48f).right();
        rowsTable.row();

        ArrayList<NeonProfiler.SnapshotRow> topRows = collectTopModuleRows(snapshot, 3);
        for(NeonProfiler.SnapshotRow row : topRows){
            rowsTable.add(displayModuleName(row.module)).color(Color.white).padRight(12f).minWidth(170f).left();
            rowsTable.add(Strings.autoFixed((float)row.msPerSecond, 3)).color(colorForMs(row.msPerSecond)).padRight(12f).width(72f).right();
            rowsTable.add(Strings.autoFixed((float)row.percentOfNeon, 1)).color(Color.lightGray).width(48f).right();
            rowsTable.row();
        }

        if(topRows.isEmpty()){
            rowsTable.add(Core.bundle.get("neon.profiler.none", "No profiler samples yet.")).color(Color.lightGray).colspan(3).left();
            rowsTable.row();
        }

        refreshDetailDialog(snapshot);

        NeonProfiler.addSelfUiRefresh(Time.timeSinceNanos(started));
    }

    private static void refreshDetailDialog(NeonProfiler.Snapshot snapshot){
        if(detailDialog == null || detailRowsTable == null) return;

        if(detailToggleButton != null){
            detailToggleButton.setText(
                snapshot.enabled
                    ? Core.bundle.get("neon.profiler.action.off", "Disable")
                    : Core.bundle.get("neon.profiler.action.on", "Enable")
            );
        }

        detailHeaderLabel.setText(
            Core.bundle.get("neon.profiler.detail.title", "Neon Profiler Details")
                + "  "
                + (snapshot.enabled ? Core.bundle.get("neon.profiler.state.on", "ON") : Core.bundle.get("neon.profiler.state.off", "OFF"))
                + "  Neon "
                + Strings.autoFixed((float)snapshot.neonTotalMsPerSecond, 3)
                + " ms/s"
        );
        detailMemoryLabel.setText(
            Core.bundle.get("neon.profiler.memory", "Memory")
                + " "
                + Strings.autoFixed(snapshot.memUsedBytes / 1024f / 1024f, 1)
                + "/"
                + Strings.autoFixed(snapshot.memTotalBytes / 1024f / 1024f, 1)
                + " MiB"
        );

        detailRowsTable.clearChildren();
        buildDetailTree(detailRowsTable, snapshot);
        if(detailScrollPane != null){
            detailScrollPane.layout();
            detailScrollPane.setScrollYForce(detailScrollY);
        }

        detailFooterLabel.setText(
            Core.bundle.get("neon.profiler.self", "Profiler self")
                + " "
                + Strings.autoFixed((float)snapshot.selfTotalMsPerSecond, 3)
                + " ms/s"
        );
    }

    private static ArrayList<NeonProfiler.SnapshotRow> collectTopModuleRows(NeonProfiler.Snapshot snapshot, int limit){
        ArrayList<NeonProfiler.SnapshotRow> rows = new ArrayList<>();
        if(snapshot == null || snapshot.rows == null) return rows;

        for(NeonProfiler.SnapshotRow row : snapshot.rows){
            if(!isModuleTotalRow(row)) continue;
            if("Profiler".equals(row.module) || "Neon".equals(row.module)) continue;
            rows.add(row);
            if(rows.size() >= limit) break;
        }
        return rows;
    }

    private static boolean isModuleTotalRow(NeonProfiler.SnapshotRow row){
        return row != null
            && row.rollup
            && "Module".equals(row.category)
            && "total".equals(row.operation);
    }

    private static String displayRowName(NeonProfiler.SnapshotRow row){
        String moduleName = displayModuleName(row.module);
        if(isModuleTotalRow(row)){
            return moduleName + " [" + row.threadGroup + "]";
        }
        if(row.rollup){
            return "  " + moduleName + " / " + row.category + " / " + row.operation + " [" + row.threadGroup + "]";
        }
        return "    " + moduleName + " / " + row.category + " / " + row.operation + " [" + row.threadGroup + "]";
    }

    private static String displayModuleName(String module){
        return Core.bundle.get(moduleBundleKey(module), module);
    }

    private static String moduleBundleKey(String module){
        if(module == null) return "";
        switch(module){
            case "PGMM": return "bektools.section.pgmm";
            case "SP": return "bektools.section.sp";
            case "RBM": return "bektools.section.rbm";
            case "BMM": return "bektools.section.bmm";
            case "SPDB": return "bektools.section.spdb";
            case "BME": return "bektools.section.bme";
            case "BPO": return "bektools.section.bpo";
            case "BLS": return "bektools.section.bls";
            case "BHK": return "bektools.section.bhk";
            case "MU": return "bektools.section.mu";
            case "HM": return "bektools.section.hm";
            case "US": return "bektools.section.us";
            case "WUTB": return "bektools.section.wutb";
            case "PV": return "bektools.section.pv";
            case "CM": return "bektools.section.cm";
            case "BSS": return "bektools.section.bss";
            case "Profiler": return "bektools.section.profiler";
            default: return "";
        }
    }

    private static void buildDetailTree(Table root, NeonProfiler.Snapshot snapshot){
        root.defaults().left().padBottom(2f);
        buildTreeHeader(root);

        LinkedHashMap<String, ModuleNode> modules = new LinkedHashMap<>();
        if(snapshot != null && snapshot.rows != null){
            for(NeonProfiler.SnapshotRow row : snapshot.rows){
                if(row == null || row.selfCost) continue;
                ModuleNode module = modules.get(row.module);
                if(module == null){
                    module = new ModuleNode(row.module);
                    modules.put(row.module, module);
                }
                module.add(row);
            }
        }

        for(ModuleNode module : modules.values()){
            root.add(createModuleSection(module)).growX().row();
        }
    }

    private static void buildTreeHeader(Table table){
        table.add(Core.bundle.get("neon.profiler.col.name", "Name")).color(Color.white).padRight(14f).minWidth(520f).left();
        table.add(Core.bundle.get("neon.profiler.col.msps", "ms/s")).color(Color.white).padRight(12f).width(96f).right();
        table.add(Core.bundle.get("neon.profiler.col.avg", "avg")).color(Color.white).padRight(12f).width(90f).right();
        table.add(Core.bundle.get("neon.profiler.col.max", "max")).color(Color.white).padRight(12f).width(90f).right();
        table.add(Core.bundle.get("neon.profiler.col.calls", "calls/s")).color(Color.white).padRight(12f).width(90f).right();
        table.add(Core.bundle.get("neon.profiler.col.pct", "%")).color(Color.white).width(70f).right();
        table.row();
    }

    private static Table createModuleSection(ModuleNode module){
        NeonProfiler.SnapshotRow moduleRow = module.total != null ? module.total : summarizeRows(module.module, module.module, module.collectAllRows(), true);
        String key = "module:" + module.module;
        boolean hasChildren = !module.categories.isEmpty();
        boolean[] expanded = {expandedState(key, true)};

        Table content = new Table();
        content.left().defaults().left().growX();
        content.add(buildCategoryTree(module)).growX().padLeft(20f);

        Collapser collapser = null;
        if(hasChildren){
            collapser = new Collapser(content, !expanded[0]);
            collapser.setDuration(0.12f);
        }

        Table wrap = new Table();
        wrap.left().defaults().left().growX();
        wrap.add(buildTreeHeaderRow(
            displayModuleName(module.module),
            moduleRow,
            true,
            expanded,
            collapser,
            key,
            hasChildren
        )).growX().row();
        if(collapser != null){
            wrap.add(collapser).colspan(6).growX().row();
        }
        return wrap;
    }

    private static Table buildCategoryTree(ModuleNode module){
        Table table = new Table();
        table.left().defaults().left().growX();

        for(CategoryNode category : module.categories.values()){
            String key = "category:" + module.module + ":" + category.name + ":" + category.operation;
            boolean hasChildren = !category.rows.isEmpty();
            boolean[] expanded = {expandedState(key, false)};
            Table detail = new Table();
            detail.left().defaults().left().growX();
            for(NeonProfiler.SnapshotRow row : category.rows){
                detail.add(buildLeafRow(row, "    " + row.operation + " [" + row.threadGroup + "]")).growX().row();
            }

            Collapser collapser = null;
            if(hasChildren){
                collapser = new Collapser(detail, !expanded[0]);
                collapser.setDuration(0.12f);
            }
            NeonProfiler.SnapshotRow categoryRow = category.total != null
                ? category.total
                : summarizeRows(category.name, category.operation, category.rows, false);

            String categoryName = "Module".equals(category.name) && "total".equals(category.operation)
                ? Core.bundle.get("neon.profiler.label.total", "Total")
                : category.name + " / " + category.operation;
            table.add(buildTreeHeaderRow("  " + categoryName, categoryRow, false, expanded, collapser, key, hasChildren)).growX().row();
            if(collapser != null){
                table.add(collapser).colspan(6).growX().row();
            }
        }
        return table;
    }

    private static Table buildTreeHeaderRow(String name, NeonProfiler.SnapshotRow row, boolean accent, boolean[] expanded, Collapser collapser, String stateKey, boolean hasChildren){
        Table line = new Table();
        line.left().defaults().padBottom(2f);

        Table nameCell = new Table();
        nameCell.left();
        if(hasChildren){
            TextButton toggle = new TextButton("", Styles.flatt);
            toggle.clearChildren();
            Image arrow = new Image(expanded[0] ? Icon.downOpen : Icon.rightOpen);
            toggle.add(arrow).size(16f);
            toggle.clicked(() -> {
                expanded[0] = !expanded[0];
                detailExpanded.put(stateKey, expanded[0]);
                arrow.setDrawable(expanded[0] ? Icon.downOpen : Icon.rightOpen);
                collapser.toggle();
            });
            nameCell.add(toggle).size(22f).padRight(6f);
        }else{
            nameCell.add().width(22f).padRight(6f);
        }
        nameCell.add(name).color(accent ? Pal.accent : Color.white).left().growX();

        line.add(nameCell).minWidth(520f).growX().left().padRight(14f);
        addMetricCell(line, Strings.autoFixed((float)row.msPerSecond, 3), 96f, colorForMs(row.msPerSecond));
        addMetricCell(line, Strings.autoFixed((float)row.avgMs, 3), 90f, Color.lightGray);
        addMetricCell(line, Strings.autoFixed((float)row.maxMs, 3), 90f, Color.lightGray);
        addMetricCell(line, Strings.autoFixed((float)row.callsPerSecond, 1), 90f, Color.lightGray);
        addMetricCell(line, row.rollup ? Strings.autoFixed((float)row.percentOfNeon, 1) : "-", 70f, Color.lightGray, false);
        return line;
    }

    private static Table buildLeafRow(NeonProfiler.SnapshotRow row, String name){
        Table line = new Table();
        line.left().defaults().padBottom(2f);
        line.add(name).color(Color.lightGray).minWidth(520f).growX().left().padRight(14f);
        addMetricCell(line, Strings.autoFixed((float)row.msPerSecond, 3), 96f, colorForMs(row.msPerSecond));
        addMetricCell(line, Strings.autoFixed((float)row.avgMs, 3), 90f, Color.lightGray);
        addMetricCell(line, Strings.autoFixed((float)row.maxMs, 3), 90f, Color.lightGray);
        addMetricCell(line, Strings.autoFixed((float)row.callsPerSecond, 1), 90f, Color.lightGray);
        addMetricCell(line, "-", 70f, Color.lightGray, false);
        return line;
    }

    private static Label textLabel(String text, Color color){
        Label label = new Label(text, Styles.outlineLabel);
        label.setColor(color);
        label.setAlignment(Align.right);
        label.setWrap(false);
        label.setEllipsis(false);
        return label;
    }

    private static void addMetricCell(Table table, String text, float width, Color color){
        addMetricCell(table, text, width, color, true);
    }

    private static void addMetricCell(Table table, String text, float width, Color color, boolean padRight){
        table.add(textLabel(text, color)).width(width).right();
        if(padRight){
            table.getCell(table.getChildren().peek()).padRight(12f);
        }
    }

    private static boolean expandedState(String key, boolean defaultValue){
        Boolean state = detailExpanded.get(key);
        if(state == null){
            detailExpanded.put(key, defaultValue);
            return defaultValue;
        }
        return state;
    }

    private static NeonProfiler.SnapshotRow summarizeRows(String category, String operation, Iterable<NeonProfiler.SnapshotRow> rows, boolean rollup){
        NeonProfiler.SnapshotRow out = new NeonProfiler.SnapshotRow();
        out.module = "";
        out.category = category;
        out.operation = operation;
        out.threadGroup = NeonProfiler.threadMain;
        out.rollup = rollup;

        int count = 0;
        for(NeonProfiler.SnapshotRow row : rows){
            if(row == null) continue;
            if(count == 0){
                out.threadGroup = row.threadGroup;
            }
            out.msPerSecond += row.msPerSecond;
            out.avgMs += row.avgMs;
            out.maxMs = Math.max(out.maxMs, row.maxMs);
            out.callsPerSecond += row.callsPerSecond;
            out.percentOfNeon += row.percentOfNeon;
            count++;
        }
        if(count > 0){
            out.avgMs /= count;
        }
        return out;
    }

    private static final class ModuleNode{
        final String module;
        NeonProfiler.SnapshotRow total;
        final LinkedHashMap<String, CategoryNode> categories = new LinkedHashMap<>();

        ModuleNode(String module){
            this.module = module;
        }

        void add(NeonProfiler.SnapshotRow row){
            if(isModuleTotalRow(row)){
                total = row;
                return;
            }
            String key = row.category + "\u0000" + row.operation;
            CategoryNode category = categories.get(key);
            if(category == null){
                category = new CategoryNode(row.category, row.operation);
                categories.put(key, category);
            }
            category.add(row);
        }

        ArrayList<NeonProfiler.SnapshotRow> collectAllRows(){
            ArrayList<NeonProfiler.SnapshotRow> all = new ArrayList<>();
            for(CategoryNode category : categories.values()){
                if(category.total != null) all.add(category.total);
                all.addAll(category.rows);
            }
            return all;
        }
    }

    private static final class CategoryNode{
        final String name;
        final String operation;
        final ArrayList<NeonProfiler.SnapshotRow> rows = new ArrayList<>();
        NeonProfiler.SnapshotRow total;

        CategoryNode(String name, String operation){
            this.name = name;
            this.operation = operation;
        }

        void add(NeonProfiler.SnapshotRow row){
            if(row.rollup){
                total = row;
            }else{
                rows.add(row);
            }
        }
    }

    private static float detailPaneWidth(){
        return Math.max(Scl.scl(1200f), Math.min(Core.graphics.getWidth() - Scl.scl(220f), Scl.scl(1700f)));
    }

    private static float detailPaneHeight(){
        return Math.max(Scl.scl(520f), Math.min(Core.graphics.getHeight() - Scl.scl(280f), Scl.scl(820f)));
    }

    private static Color colorForMs(double msPerSecond){
        double perFrame = msPerSecond / 60.0;
        if(perFrame >= 3.0) return Color.scarlet;
        if(perFrame >= 1.0) return Color.gold;
        return Color.white;
    }
}
