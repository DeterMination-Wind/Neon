package updatescheme.features;

import arc.Core;
import arc.Events;
import arc.util.Interval;
import arc.util.Log;
import mdtxcompat.OverlayUiBridge;
import mdtxcompat.SchematicShareBridge;
import mindustry.Vars;
import mindustry.game.EventType;
import mindustry.ui.dialogs.SchematicsDialog;
import mindustry.ui.dialogs.SettingsMenuDialog;

public final class UpdateSchemeFeature {

    static final String keyEnabled = "us-enabled";
    static final String keyAutoCheck = "us-auto-check";
    static final String keyIntervalMin = "us-interval-min";
    static final String keyChatDetect = "us-chat-detect";
    static final String keyChatAutoSub = "us-chat-autosub";

    static final Interval interval = new Interval(6);
    static final int idSettings = 0;
    static final int idAutoTick = 1;
    static final int idHookTick = 2;
    static final float settingsRefreshTime = 0.5f;
    static final float autoTickTime = 1.0f;
    static final float hookTickTime = 0.5f;

    static boolean inited;
    static boolean enabled;
    static boolean autoCheck;
    static int intervalMin;
    static boolean chatDetect;
    static boolean chatAutoSub;
    static long nextAutoCheckAtMs;
    static long hookDeadlineAtMs;
    static SchematicsDialog originalSchematicsDialog;
    static OverlayUiBridge overlayUi = OverlayUiBridge.UNSUPPORTED;
    static SchematicShareBridge shareBridge = SchematicShareBridge.UNSUPPORTED;

    private UpdateSchemeFeature() {
    }

    public static void init() {
        if (inited) return;
        inited = true;

        Core.settings.defaults(keyEnabled, true);
        Core.settings.defaults(keyAutoCheck, true);
        Core.settings.defaults(keyIntervalMin, 10);
        Core.settings.defaults(keyChatDetect, true);
        Core.settings.defaults(keyChatAutoSub, false);
        Core.settings.defaults(UpdateSchemeUtil.keyRegistryRepo, "");
        Core.settings.defaults(UpdateSchemeUtil.keyRegistryToken, "");
        refreshSettings();
        scheduleNextAutoCheck();

        Events.on(EventType.ClientLoadEvent.class, e -> {
            refreshSettings();
            scheduleNextAutoCheck();
            UpdateSchemeOverlay.init();
            hookDeadlineAtMs = System.currentTimeMillis() + 12_000L;
            tryInstallSchematicExportHooks();
        });

        Events.on(EventType.PlayerChatEvent.class, e -> {
            if (!enabled || !chatDetect) return;
            UpdateSchemeUI.onChatMessage(e.player, e.message);
        });

        Events.run(EventType.Trigger.update, () -> {
            if (interval.check(idSettings, settingsRefreshTime)) refreshSettings();
            if (interval.check(idAutoTick, autoTickTime)) tickAutoCheck();
            if (hookDeadlineAtMs > 0L && System.currentTimeMillis() < hookDeadlineAtMs && interval.check(idHookTick, hookTickTime)) {
                tryInstallSchematicExportHooks();
            }
            redirectOriginalSchematicsDialogIfShown();
            UpdateSchemeOverlay.syncVisibility();
            UpdateSchemeFetcher.tickQueue();
        });
    }

    public static void configureCompat(OverlayUiBridge overlay, SchematicShareBridge share) {
        overlayUi = overlay == null ? OverlayUiBridge.UNSUPPORTED : overlay;
        shareBridge = share == null ? SchematicShareBridge.UNSUPPORTED : share;
    }

    public static void buildSettings(SettingsMenuDialog.SettingsTable table) {
        table.checkPref(keyEnabled, true);
        table.checkPref(keyAutoCheck, true);
        table.sliderPref(keyIntervalMin, 10, 1, 120, 1, v -> Integer.toString((int) v));
        table.checkPref(keyChatDetect, true);
        table.checkPref(keyChatAutoSub, false);

        table.pref(new SettingsMenuDialog.SettingsTable.Setting("us-open-manager") {
            @Override
            public void add(SettingsMenuDialog.SettingsTable t) {
                t.button(title, UpdateSchemeUI::openManager)
                    .growX()
                    .height(52f)
                    .minWidth(420f)
                    .margin(16f)
                    .pad(8f)
                    .row();
            }
        });

        table.pref(new SettingsMenuDialog.SettingsTable.Setting("us-check-now") {
            @Override
            public void add(SettingsMenuDialog.SettingsTable t) {
                t.button(title, () -> UpdateSchemeFetcher.enqueueAllChecks(true))
                    .growX()
                    .height(52f)
                    .minWidth(420f)
                    .margin(16f)
                    .pad(8f)
                    .row();
            }
        });
    }

    static void refreshSettings() {
        enabled = Core.settings.getBool(keyEnabled, true);
        autoCheck = Core.settings.getBool(keyAutoCheck, true);
        intervalMin = Math.max(1, Core.settings.getInt(keyIntervalMin, 10));
        chatDetect = Core.settings.getBool(keyChatDetect, true);
        chatAutoSub = Core.settings.getBool(keyChatAutoSub, false);
    }

    static void scheduleNextAutoCheck() {
        nextAutoCheckAtMs = System.currentTimeMillis() + intervalMin * 60_000L;
    }

    static void tickAutoCheck() {
        if (!enabled || !autoCheck) return;
        long now = System.currentTimeMillis();
        if (nextAutoCheckAtMs <= 0L) scheduleNextAutoCheck();
        if (now < nextAutoCheckAtMs) return;
        if (!Vars.headless) UpdateSchemeFetcher.enqueueAllChecks(false);
        nextAutoCheckAtMs = now + intervalMin * 60_000L;
    }

    private static void tryInstallSchematicExportHooks() {
        try {
            if (Vars.ui == null || Vars.headless) return;
            if (Vars.ui.schematics instanceof UpdateSchemeSchematicsDialog) return;

            if (originalSchematicsDialog == null && Vars.ui.schematics instanceof SchematicsDialog) {
                originalSchematicsDialog = Vars.ui.schematics;
                Log.info("UpdateScheme: captured original schematics dialog instance=@.", originalSchematicsDialog.getClass().getName());
            }

            if (Vars.ui.schematics != null) {
                try {
                    Vars.ui.schematics.hide();
                } catch (Throwable ignored) {
                }
            }
            Vars.ui.schematics = new UpdateSchemeSchematicsDialog();
            Log.info("UpdateScheme: installed schematics dialog hook instance=@.", Vars.ui.schematics.getClass().getName());
        } catch (Throwable ignored) {
        }
    }

    private static void redirectOriginalSchematicsDialogIfShown() {
        try {
            if (Vars.ui == null || Vars.headless) return;
            if (!(Vars.ui.schematics instanceof UpdateSchemeSchematicsDialog)) return;
            if (originalSchematicsDialog == null) return;

            if (originalSchematicsDialog.isShown()) {
                originalSchematicsDialog.hide();
                Core.app.post(() -> {
                    try {
                        Vars.ui.schematics.show();
                    } catch (Throwable ignored) {
                    }
                });
            }
        } catch (Throwable ignored) {
        }
    }
}
