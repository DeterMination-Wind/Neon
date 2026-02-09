package powergridminimap;

import arc.Core;
import arc.Events;
import arc.func.Prov;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Font;
import arc.graphics.g2d.GlyphLayout;
import arc.graphics.g2d.Lines;
import arc.math.Mat;
import arc.math.Mathf;
import arc.math.geom.Point2;
import arc.math.geom.Rect;
import arc.scene.Element;
import arc.scene.event.ClickListener;
import arc.scene.event.InputEvent;
import arc.scene.style.TextureRegionDrawable;
import arc.scene.ui.Label;
import arc.scene.ui.TextButton;
import arc.scene.ui.layout.Table;
import arc.struct.ObjectSet;
import arc.struct.Seq;
import arc.util.Align;
import arc.util.CommandHandler;
import arc.util.Time;
import arc.util.Tmp;
import arc.util.pooling.Pools;
import arc.scene.event.Touchable;
import arc.scene.ui.layout.Scl;
import arc.util.Log;
import arc.graphics.Pixmap;
import arc.graphics.Texture;
import arc.graphics.g2d.TextureRegion;
import arc.math.geom.Vec2;
import arc.struct.IntIntMap;
import arc.struct.IntMap;
import arc.struct.IntQueue;
import arc.struct.IntSeq;
import arc.struct.IntSet;
import arc.struct.LongSeq;
import arc.util.Structs;
import arc.util.Strings;
import mindustry.content.Blocks;
import mindustry.core.UI;
import mindustry.game.EventType.ClientLoadEvent;
import mindustry.game.EventType.BlockBuildEndEvent;
import mindustry.game.EventType.BlockDestroyEvent;
import mindustry.game.EventType.BuildRotateEvent;
import mindustry.game.EventType.BuildTeamChangeEvent;
import mindustry.game.EventType.ConfigEvent;
import mindustry.game.EventType.WorldLoadEvent;
import mindustry.game.EventType.Trigger;
import mindustry.game.Team;
import mindustry.graphics.Drawf;
import mindustry.graphics.Pal;
import mindustry.gen.Building;
import mindustry.gen.Call;
import mindustry.gen.Icon;
import mindustry.gen.Player;
import mindustry.mod.Scripts;
import mindustry.ui.Fonts;
import mindustry.ui.dialogs.SettingsMenuDialog;
import mindustry.ui.Styles;
import mindustry.world.blocks.power.PowerNode;
import mindustry.world.blocks.power.PowerGraph;
import mindustry.mod.Mods;
import rhino.ScriptableObject;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;

import static mindustry.Vars.control;
import static mindustry.Vars.player;
import static mindustry.Vars.renderer;
import static mindustry.Vars.state;
import static mindustry.Vars.tilesize;
import static mindustry.Vars.ui;
import static mindustry.Vars.world;

public class PowerGridMinimapMod extends mindustry.mod.Mod{
    /** When true, this mod is running as a bundled component inside Neon. */
    public static boolean bekBundled = false;


    private static final String overlayName = "neon-pgmm-minimap-overlay";
    private static final String mi2OverlayName = "neon-pgmm-mi2-overlay";
    private static final String powerTableName = "neon-pgmm-power-table";

    private static final String keyEnabled = "pgmm-enabled";
    private static final String keyGridAlpha = "pgmm-gridalpha";
    private static final String keyMarkerScale = "pgmm-markerscale";
    private static final String keyMarkerColor = "pgmm-markercolor";
    private static final String keyHudMarkerFollowScale = "pgmm-hudmarkerscale";
    private static final String keyShowBalance = "pgmm-showbalance";
    private static final String keyDrawOnMi2Minimap = "pgmm-mi2minimap";
    private static final String keyClaimDistance = "pgmm-claimdistance";
    private static final String keySplitAlertEnabled = "pgmm-splitalert";
    private static final String keySplitAlertThreshold = "pgmm-splitalertthreshold";
    private static final String keySplitAlertNegativeThreshold = "pgmm-splitalert-negative-threshold";
    private static final String keySplitAlertWindowSeconds = "pgmm-splitwindow";
    private static final String keySplitAlertMultiplayerEnabled = "pgmm-splitalert-multiplayer";
    private static final String keySplitAlertMultiplayerIntervalSeconds = "pgmm-splitalert-multiplayer-interval";
    private static final String keyClusterMarkerDistance = "pgmm-clustermarkerdistance";
    private static final String keyReconnectStroke = "pgmm-markerstroke";
    private static final String keyReconnectColor = "pgmm-markerlinecolor";
    private static final String keyRescueEnabled = "pgmm-rescue";
    private static final String keyRescueAggressive = "pgmm-rescue-aggressive";
    private static final String keyRescueWindowSeconds = "pgmm-rescue-window";
    private static final String keyRescueClearWindowSeconds = "pgmm-rescue-clearwindow";
    private static final String keyRescueTopK = "pgmm-rescue-topk";
    private static final String keyRescueStroke = "pgmm-rescue-stroke";
    private static final String keyRescueColor = "pgmm-rescue-color";
    private static final String keyPowerTableEnabled = "pgmm-power-table";
    private static final String keyPowerTableThreshold = "pgmm-power-table-threshold";
    private static final String keyPowerTableBgAlpha = "pgmm-power-table-bgalpha";
    //debounce cache rebuilds after block changes (tenths of a second)
    private static final String keyUpdateWaitTenths = "pgmm-updatewait";
    //Ignore power grids whose functional area (producers/consumers/batteries) is below this threshold (tiles^2).
    private static final String keyIgnoreAreaTiles = "pgmm-ignore-area";

    private final PowerGridCache cache = new PowerGridCache();
    private final Color markerColor = new Color(Color.white);
    private final Color reconnectColor = new Color(Color.orange);
    private final Color rescueColor = new Color(Color.scarlet);

    private float nextAttachAttempt = 0f;
    private boolean shownAttachToast = false;

    private final FullMinimapAccess fullAccess = new FullMinimapAccess();
    private final Rect fullBounds = new Rect();
    private final Mat fullTransform = new Mat();
    private final Mat oldTransform = new Mat();

    private final SplitWatcher splitWatcher = new SplitWatcher();
    private final SplitAlert alert = new SplitAlert();
    private final RescueAdvisor rescueAdvisor = new RescueAdvisor();
    private final Vec2 tmpGridCenter = new Vec2();
    private final RescueAlert rescueAlert = new RescueAlert();
    private final PowerTableOverlay powerTable = new PowerTableOverlay();
    private final MindustryXMarkers xMarkers = new MindustryXMarkers();
    //MindustryX OverlayUI integration (optional): if MindustryX exists, register our power-table as a proper OverlayUI panel.
    //This is done via reflection so vanilla clients don't crash.
    private final MindustryXOverlayUI xOverlayUi = new MindustryXOverlayUI();
    private Object xPowerTableWindow = null;
    private boolean lastPowerTableEnabled = false;
    private float nextSplitAlertMultiplayerChatAt = 0f;
    private final Mi2MinimapIntegration mi2 = new Mi2MinimapIntegration();
    private final PgmmConsoleApi consoleApi = new PgmmConsoleApi(this);
    private boolean consoleApiLogged = false;

    public PowerGridMinimapMod(){
        Events.on(ClientLoadEvent.class, e -> {
            Core.settings.defaults(keyEnabled, true);
            Core.settings.defaults(keyGridAlpha, 40);
            Core.settings.defaults(keyMarkerScale, 100);
            Core.settings.defaults(keyMarkerColor, "ffffff");
            Core.settings.defaults(keyHudMarkerFollowScale, 100);
            Core.settings.defaults(keyShowBalance, true);
            Core.settings.defaults(keyDrawOnMi2Minimap, false);
            Core.settings.defaults(keyClaimDistance, 5);
            Core.settings.defaults(keySplitAlertEnabled, true);
            Core.settings.defaults(keySplitAlertThreshold, 10000);
            Core.settings.defaults(keySplitAlertNegativeThreshold, 0);
            Core.settings.defaults(keySplitAlertWindowSeconds, 4);
            Core.settings.defaults(keySplitAlertMultiplayerEnabled, false);
            Core.settings.defaults(keySplitAlertMultiplayerIntervalSeconds, 8);
            Core.settings.defaults(keyClusterMarkerDistance, 15);
            Core.settings.defaults(keyReconnectStroke, 2);
            Core.settings.defaults(keyReconnectColor, "ffa500");
            Core.settings.defaults(keyRescueEnabled, false);
            Core.settings.defaults(keyRescueAggressive, false);
            Core.settings.defaults(keyRescueWindowSeconds, 4);
            Core.settings.defaults(keyRescueClearWindowSeconds, 8);
            Core.settings.defaults(keyRescueTopK, 2);
            Core.settings.defaults(keyRescueStroke, 2);
            Core.settings.defaults(keyRescueColor, "ff3344");
            Core.settings.defaults(keyPowerTableEnabled, false);
            Core.settings.defaults(keyPowerTableThreshold, 10000);
            Core.settings.defaults(keyPowerTableBgAlpha, 70);
            Core.settings.defaults(keyUpdateWaitTenths, 10);
            Core.settings.defaults(keyIgnoreAreaTiles, 0);
            GithubUpdateCheck.applyDefaults();

            registerSettings();
            refreshMarkerColor();
            refreshReconnectColor();
            refreshRescueColor();
            xMarkers.tryInit();
            mi2.tryInit();
            installConsoleApi();
            Time.runTask(10f, this::installConsoleApi);
            Time.runTask(10f, this::ensureOverlayAttached);
            Time.runTask(10f, this::ensurePowerTableAttached);
            GithubUpdateCheck.checkOnce();
        });

        Events.on(WorldLoadEvent.class, e -> {
            cache.clear();
            rescueAlert.clear();
            rescueAdvisor.reset();
            nextSplitAlertMultiplayerChatAt = 0f;
            Time.runTask(10f, this::ensureOverlayAttached);
            Time.runTask(10f, this::ensurePowerTableAttached);
        });

        Events.on(BlockBuildEndEvent.class, e -> cache.invalidateAll());
        Events.on(BlockDestroyEvent.class, e -> cache.invalidateAll());
        Events.on(BuildRotateEvent.class, e -> cache.invalidateAll());
        Events.on(BuildTeamChangeEvent.class, e -> cache.invalidateAll());
        Events.on(ConfigEvent.class, e -> cache.invalidateAll());

        Events.run(Trigger.update, () -> {
            if(!Core.settings.getBool(keyEnabled, true)) return;

            //the HUD may be rebuilt; keep trying to attach.
            if(Time.time >= nextAttachAttempt){
                nextAttachAttempt = Time.time + 60f;
                ensureOverlayAttached();
                ensurePowerTableAttached();
                mi2.ensureAttached(cache, markerColor, alert, rescueAlert, rescueColor);
            }

            //MindustryX OverlayUI integration:
            //OverlayUI.Window visibility is controlled by its own persisted setting (`overlayUI.<name>`), not our PGMM setting.
            //To keep UX consistent ("enable power table" == it shows up), we mirror our boolean into OverlayUI's enabled.
            //Important: only sync on setting changes so players can hide the window from OverlayUI without us forcing it back on.
            if(xPowerTableWindow != null){
                boolean enabled = Core.settings.getBool(keyPowerTableEnabled, false);
                if(enabled != lastPowerTableEnabled){
                    lastPowerTableEnabled = enabled;
                    if(enabled){
                        xOverlayUi.setEnabledAndPinned(xPowerTableWindow, true, false);
                    }else{
                        xOverlayUi.setEnabledAndPinned(xPowerTableWindow, false, false);
                    }
                }
            }

            splitWatcher.update();
            rescueAdvisor.update();
        });

        //draw rescue hints directly in the main game view.
        Events.run(Trigger.draw, this::drawWorldRescueOverlay);

        //draw on top of the full-screen minimap (opened via M).
        Events.run(Trigger.uiDrawEnd, this::drawFullMinimapOverlay);
    }

    private void installConsoleApi(){
        if(mindustry.Vars.headless) return;
        if(mindustry.Vars.mods == null) return;
        try{
            Scripts scripts = mindustry.Vars.mods.getScripts();
            if(scripts == null || scripts.scope == null) return;
            ScriptableObject.putProperty(scripts.scope, "pgmm", rhino.Context.javaToJS(consoleApi, scripts.scope));
            if(!consoleApiLogged){
                consoleApiLogged = true;
                Log.info("PGMM: F8 console API installed. Try: pgmm.help()");
            }
        }catch(Throwable t){
            Log.err("PGMM: failed to install F8 console API.", t);
        }
    }

    @Override
    public void registerClientCommands(CommandHandler handler){
        handler.<Player>register("pgmm-restart", "Restart Power Grid Minimap (clear cache + reattach overlays).", (args, player) -> {
            restartMod();
            if(player != null) player.sendMessage("[accent]PGMM restarted.");
        });

        handler.<Player>register("pgmm-rescan", "Force immediate power-grid rescan + overlay rebuild.", (args, player) -> {
            rescanNow();
            if(player != null) player.sendMessage("[accent]PGMM rescan requested.");
        });

        handler.<Player>register("pgmm-mi2", "[on/off/refresh]", "MI2 minimap overlay control (refresh = re-detect + reattach).", (args, player) -> {
            String mode = args.length == 0 ? "refresh" : args[0].toLowerCase();

            switch(mode){
                case "on":
                    Core.settings.put(keyDrawOnMi2Minimap, true);
                    break;
                case "off":
                    Core.settings.put(keyDrawOnMi2Minimap, false);
                    break;
                case "refresh":
                    //leave setting as-is
                    break;
                default:
                    if(player != null) player.sendMessage("[scarlet]Usage: /pgmm-mi2 [on/off/refresh]");
                    return;
            }

            refreshMi2Overlay("refresh".equals(mode));
            if(player != null) player.sendMessage("[accent]PGMM MI2 overlay: " + mode + "[]");
        });
    }

    /** F8 console helper object: use {@code pgmm.help()} / {@code pgmm.restart()} / {@code pgmm.rescan()} / {@code pgmm.mi2Refresh()}. */
    public static class PgmmConsoleApi{
        private final PowerGridMinimapMod mod;

        PgmmConsoleApi(PowerGridMinimapMod mod){
            this.mod = mod;
        }

        public String help(){
            return "PGMM console API:\n" +
                "  pgmm.restart()     - restart PGMM (clear cache + reattach overlays)\n" +
                "  pgmm.rescan()      - rescan grids immediately (ignore update delay)\n" +
                "  pgmm.mi2Refresh()  - re-detect MI2 + reattach overlay (if enabled)\n" +
                "  pgmm.mi2On()       - enable MI2 overlay + refresh\n" +
                "  pgmm.mi2Off()      - disable MI2 overlay + detach";
        }

        public String restart(){
            mod.restartMod();
            return "PGMM restarted.";
        }

        public String rescan(){
            mod.rescanNow();
            return "PGMM rescan requested.";
        }

        public String mi2Refresh(){
            mod.refreshMi2Overlay(true);
            return "PGMM MI2 overlay refresh requested.";
        }

        public String mi2On(){
            Core.settings.put(keyDrawOnMi2Minimap, true);
            mod.refreshMi2Overlay(true);
            return "PGMM MI2 overlay enabled.";
        }

        public String mi2Off(){
            Core.settings.put(keyDrawOnMi2Minimap, false);
            mod.refreshMi2Overlay(false);
            return "PGMM MI2 overlay disabled.";
        }
    }

    /** "Soft restart" for debugging: clears caches/state and reattaches overlays. */
    public void restartMod(){
        Log.info("PGMM: restart requested.");

        try{
            refreshMarkerColor();
            refreshReconnectColor();
        }catch(Throwable ignored){
        }

        try{
            cache.clear();
            cache.forceRebuildNow();
        }catch(Throwable ignored){
        }

        try{
            alert.clear();
        }catch(Throwable ignored){
        }

        try{
            rescueAlert.clear();
        }catch(Throwable ignored){
        }

        try{
            splitWatcher.reset();
        }catch(Throwable ignored){
        }

        nextSplitAlertMultiplayerChatAt = 0f;

        try{
            rescueAdvisor.reset();
        }catch(Throwable ignored){
        }

        try{
            nextAttachAttempt = 0f;
            ensureOverlayAttached();
        }catch(Throwable ignored){
        }

        try{
            mi2.ensureAttached(cache, markerColor, alert, rescueAlert, rescueColor);
        }catch(Throwable ignored){
        }
    }

    /** Forces an immediate rescan/rebuild, bypassing the update delay. */
    public void rescanNow(){
        Log.info("PGMM: rescan requested.");
        cache.forceRebuildNow();
        cache.updateBasic();
        cache.updateFullOverlay();
    }

    /** Re-detects MI2 and reattaches the MI2 overlay (if enabled). */
    public void refreshMi2Overlay(boolean forceRedetect){
        Log.info("PGMM: MI2 overlay refresh requested (forceRedetect=@).", forceRedetect);
        if(forceRedetect){
            mi2.tryInit();
        }
        mi2.detachIfPresent();
        mi2.ensureAttached(cache, markerColor, alert, rescueAlert, rescueColor);
        //ensure something is available to draw immediately
        rescanNow();
    }

    private void registerSettings(){
        if(ui == null || ui.settings == null) return;
        if(bekBundled) return;


        String category = Core.bundle.get("pgmm.category", "Power Grid Minimap");
        ui.settings.addCategory(category, Icon.powerSmall, this::bekBuildSettings);
    }
    /** Populates a {@link mindustry.ui.dialogs.SettingsMenuDialog.SettingsTable} with this mod's settings. */
    public void bekBuildSettings(SettingsMenuDialog.SettingsTable table){
            // Match MindustryX settings style (icon + Tex.button rows + wrapped titles).
            table.pref(new PgmmSettingsWidgets.HeaderSetting(Core.bundle.get("pgmm.section.basic", "Basic"), Icon.settings));
            table.pref(new PgmmSettingsWidgets.IconCheckSetting(keyEnabled, true, Icon.eyeSmall, null));
            table.pref(new PgmmSettingsWidgets.IconSliderSetting(keyGridAlpha, 40, 0, 100, 5, Icon.imageSmall, v -> v + "%", null));
            table.pref(new PgmmSettingsWidgets.IconCheckSetting(keyShowBalance, true, Icon.infoSmall, null));
            table.pref(new PgmmSettingsWidgets.IconSliderSetting(keyMarkerScale, 100, 50, 300, 10, Icon.resizeSmall, v -> v + "%", null));
            table.pref(new PgmmSettingsWidgets.IconTextSetting(keyMarkerColor, "ffffff", Icon.effectSmall, v -> refreshMarkerColor()));
            table.pref(new PgmmSettingsWidgets.IconSliderSetting(keyHudMarkerFollowScale, 100, 0, 200, 10, Icon.moveSmall, v -> v + "%", null));

            table.pref(new PgmmSettingsWidgets.HeaderSetting(Core.bundle.get("pgmm.section.integration", "Integration"), Icon.linkSmall));
            table.pref(new PgmmSettingsWidgets.IconCheckSetting(keyDrawOnMi2Minimap, false, Icon.mapSmall, v -> {
                //If MI2 exists, attach/detach immediately; otherwise, no-op.
                mi2.ensureAttached(cache, markerColor, alert, rescueAlert, rescueColor);
            }));

            table.pref(new PgmmSettingsWidgets.HeaderSetting(Core.bundle.get("pgmm.section.alerts", "Alerts & Markers"), Icon.warningSmall));
            table.pref(new PgmmSettingsWidgets.IconSliderSetting(keyClaimDistance, 5, 1, 20, 1, Icon.gridSmall, String::valueOf, null));
            table.pref(new PgmmSettingsWidgets.IconCheckSetting(keySplitAlertEnabled, true, Icon.warningSmall, null));
            table.pref(new PgmmSettingsWidgets.IconSliderSetting(keySplitAlertThreshold, 10000, 1000, 50000, 500, Icon.warningSmall, v -> v + "/s", null));
            table.pref(new PgmmSettingsWidgets.IconIntFieldSetting(keySplitAlertNegativeThreshold, 0, -200000, 0, Icon.warningSmall, null));
            table.pref(new PgmmSettingsWidgets.IconSliderSetting(keySplitAlertWindowSeconds, 4, 1, 15, 1, Icon.refreshSmall, v -> v + "s", null));
            table.pref(new PgmmSettingsWidgets.IconCheckSetting(keySplitAlertMultiplayerEnabled, false, Icon.chatSmall, null));
            table.pref(new PgmmSettingsWidgets.IconSliderSetting(keySplitAlertMultiplayerIntervalSeconds, 8, 1, 60, 1, Icon.refreshSmall, v -> v + "s", null));
            table.pref(new PgmmSettingsWidgets.IconSliderSetting(keyClusterMarkerDistance, 15, 0, 60, 1, Icon.filterSmall, String::valueOf, null));
            table.pref(new PgmmSettingsWidgets.IconSliderSetting(keyReconnectStroke, 2, 1, 8, 1, Icon.pencilSmall, String::valueOf, null));
            table.pref(new PgmmSettingsWidgets.IconTextSetting(keyReconnectColor, "ffa500", Icon.effectSmall, v -> refreshReconnectColor()));

            table.pref(new PgmmSettingsWidgets.HeaderSetting(Core.bundle.get("pgmm.section.advanced", "Advanced"), Icon.settingsSmall));
            table.pref(new PgmmSettingsWidgets.IconSliderSetting(keyIgnoreAreaTiles, 0, 0, 500, 1, Icon.filterSmall, String::valueOf, null));

            table.pref(new PgmmSettingsWidgets.HeaderSetting("@setting.pgmm.rescue.section", Icon.powerSmall));
            table.pref(new PgmmSettingsWidgets.IconCheckSetting(keyRescueEnabled, false, Icon.warningSmall, null));
            table.pref(new PgmmSettingsWidgets.IconCheckSetting(keyRescueAggressive, false, Icon.settingsSmall, null));
            table.pref(new PgmmSettingsWidgets.IconSliderSetting(keyRescueWindowSeconds, 4, 1, 15, 1, Icon.refreshSmall, v -> v + "s", null));
            table.pref(new PgmmSettingsWidgets.IconSliderSetting(keyRescueClearWindowSeconds, 8, 0, 60, 1, Icon.refreshSmall, v -> v + "s", null));
            table.pref(new PgmmSettingsWidgets.IconSliderSetting(keyRescueTopK, 2, 1, 10, 1, Icon.listSmall, String::valueOf, null));
            table.pref(new PgmmSettingsWidgets.IconSliderSetting(keyRescueStroke, 2, 1, 8, 1, Icon.pencilSmall, String::valueOf, null));
            table.pref(new PgmmSettingsWidgets.IconTextSetting(keyRescueColor, "ff3344", Icon.effectSmall, v -> refreshRescueColor()));

            table.pref(new PgmmSettingsWidgets.HeaderSetting("@setting.pgmm.powertable.section", Icon.listSmall));
            table.pref(new PgmmSettingsWidgets.IconCheckSetting(keyPowerTableEnabled, false, Icon.listSmall, null));
            table.pref(new PgmmSettingsWidgets.IconSliderSetting(keyPowerTableThreshold, 10000, 0, 200000, 1000, Icon.powerSmall, v -> v + "/s", null));
            table.pref(new PgmmSettingsWidgets.IconSliderSetting(keyPowerTableBgAlpha, 70, 0, 100, 5, Icon.imageSmall, v -> v + "%", null));

            table.pref(new PgmmSettingsWidgets.HeaderSetting(Core.bundle.get("pgmm.section.performance", "Performance"), Icon.wrenchSmall));
            table.pref(new PgmmSettingsWidgets.IconSliderSetting(keyUpdateWaitTenths, 10, 0, 50, 1, Icon.refreshSmall, v -> Strings.autoFixed(v / 10f, 1) + "s", null));

            table.pref(new PgmmSettingsWidgets.HeaderSetting(Core.bundle.get("pgmm.section.update", "Update"), Icon.refreshSmall));
            table.pref(new PgmmSettingsWidgets.IconCheckSetting(GithubUpdateCheck.enabledKey(), true, Icon.refreshSmall, null));
            table.pref(new PgmmSettingsWidgets.IconCheckSetting(GithubUpdateCheck.showDialogKey(), true, Icon.infoSmall, null));
        
    }


    private void refreshMarkerColor(){
        Color out = markerColor;
        String value = Core.settings.getString(keyMarkerColor, "ffffff");
        if(!tryParseHexColor(value, out)){
            out.set(Color.white);
        }
    }

    private void refreshReconnectColor(){
        Color out = reconnectColor;
        String value = Core.settings.getString(keyReconnectColor, "ffa500");
        if(!tryParseHexColor(value, out)){
            out.set(Color.orange);
        }
    }

    private void refreshRescueColor(){
        Color out = rescueColor;
        String value = Core.settings.getString(keyRescueColor, "ff3344");
        if(!tryParseHexColor(value, out)){
            out.set(Color.scarlet);
        }
    }

    private void trySendSplitAlertMultiplayerChat(int tileX, int tileY){
        if(!Core.settings.getBool(keySplitAlertMultiplayerEnabled, false)) return;
        if(mindustry.Vars.net == null || !mindustry.Vars.net.active()) return;
        if(player == null || state == null || !state.isGame()) return;
        if(Time.time < nextSplitAlertMultiplayerChatAt) return;

        int intervalSeconds = Mathf.clamp(Core.settings.getInt(keySplitAlertMultiplayerIntervalSeconds, 8), 1, 60);
        nextSplitAlertMultiplayerChatAt = Time.time + intervalSeconds * 60f;

        Call.sendChatMessage("<PGMM><[red]断电建议连接点[]>(" + tileX + "," + tileY + ")");
    }

    private void ensureOverlayAttached(){
        if(ui == null || ui.hudGroup == null) return;

        Element minimap = ui.hudGroup.find("minimap");
        if(!(minimap instanceof Table)) return;

        Table table = (Table)minimap;
        if(table.find(overlayName) != null) return;

        if(table.getChildren().isEmpty()) return;
        Element base = table.getChildren().get(0);

        MinimapOverlay overlay = new MinimapOverlay(base, cache, markerColor, alert, rescueAlert, rescueColor);
        overlay.name = overlayName;
        overlay.touchable = Touchable.disabled;
        table.addChild(overlay);

        if(!shownAttachToast && ui.hudfrag != null){
            shownAttachToast = true;
            ui.hudfrag.showToast(Core.bundle.get("pgmm.toast.enabled"));
        }

        Log.info("PGMM: overlay attached.");
    }

    private void ensurePowerTableAttached(){
        if(ui == null || ui.hudGroup == null) return;

        //Prefer MindustryX OverlayUI if available, so the table becomes a proper Overlay panel (draggable/pinnable).
        //WayzerMapBrowser follows the same pattern: use OverlayUI when installed, otherwise fall back to normal HUD/Core.scene UI.
        if(xOverlayUi.isInstalled()){
            if(xPowerTableWindow == null){
                try{
                    //When hosted by OverlayUI, our table must not fight the Window for positioning/sizing.
                    powerTable.setHostedByOverlayUI(true);
                    xPowerTableWindow = xOverlayUi.registerWindow(powerTableName, powerTable, () -> state != null && state.isGame());
                    if(xPowerTableWindow != null){
                        xOverlayUi.tryConfigureWindow(xPowerTableWindow, false, true);
                        Log.info("PGMM: power table registered to MindustryX OverlayUI.");
                        return;
                    }
                }catch(Throwable t){
                    Log.err("PGMM: failed to register power table to MindustryX OverlayUI; falling back to HUD attachment.", t);
                    xPowerTableWindow = null;
                    powerTable.setHostedByOverlayUI(false);
                }
            }else{
                return;
            }
        }

        //Fallback: attach directly to HUD group (vanilla client, or OverlayUI unavailable).
        if(ui.hudGroup.find(powerTableName) != null) return;

        try{
            powerTable.remove();
        }catch(Throwable ignored){
        }

        powerTable.name = powerTableName;
        powerTable.setHostedByOverlayUI(false);
        ui.hudGroup.addChild(powerTable);
        powerTable.toFront();
    }

    private void focusGridCenter(GridInfo info){
        PowerGraph graph = info == null ? null : info.graph;
        if(graph == null) return;

        Core.app.post(() -> {
            if(!state.isGame() || world == null || world.isGenerating() || player == null) return;
            if(control == null || control.input == null) return;

            if(!computeGridCenter(info, tmpGridCenter)) return;

            float minx = tilesize / 2f;
            float miny = tilesize / 2f;
            float maxx = Math.max(minx, world.unitWidth() - tilesize / 2f);
            float maxy = Math.max(miny, world.unitHeight() - tilesize / 2f);
            float x = Mathf.clamp(tmpGridCenter.x, minx, maxx);
            float y = Mathf.clamp(tmpGridCenter.y, miny, maxy);

            control.input.panCamera(Tmp.v1.set(x, y));
        });
    }

    private boolean computeGridCenter(GridInfo info, Vec2 out){
        if(info == null) return false;
        if(info.hasCenter){
            out.set(info.centerX, info.centerY);
            return true;
        }

        PowerGraph graph = info.graph;
        if(graph == null || graph.all == null || graph.all.isEmpty()) return false;

        float sumx = 0f, sumy = 0f;
        int count = 0;
        Team team = info.team;
        Seq<mindustry.gen.Building> all = graph.all;
        for(int i = 0; i < all.size; i++){
            mindustry.gen.Building b = all.get(i);
            if(b == null || team != null && b.team != team) continue;
            sumx += b.x;
            sumy += b.y;
            count++;
        }
        if(count <= 0) return false;
        out.set(sumx / count, sumy / count);
        return true;
    }

    private void drawWorldRescueOverlay(){
        if(!Core.settings.getBool(keyEnabled, true)) return;
        if(!Core.settings.getBool(keyRescueEnabled, false)) return;
        if(world == null || !state.isGame() || world.isGenerating()) return;
        if(player == null) return;
        if(Core.camera == null) return;

        Rect viewRect = Core.camera.bounds(Tmp.r3);
        float invScale = viewRect.width / Math.max(1f, Core.graphics.getWidth());
        rescueAlert.drawWorldMarker(viewRect, invScale, rescueColor);
    }

    private void drawFullMinimapOverlay(){
        if(!Core.settings.getBool(keyEnabled, true)) return;
        if(ui == null || ui.minimapfrag == null || !ui.minimapfrag.shown()) return;
        if(renderer == null || renderer.minimap == null || renderer.minimap.getTexture() == null) return;
        if(world == null || !state.isGame() || world.isGenerating()) return;
        if(player == null) return;

        cache.updateBasic();

        // Full overlay texture rebuild is the most expensive part; skip it entirely when the
        // grid-color overlay is fully transparent.
        int gridAlphaInt = Core.settings.getInt(keyGridAlpha, 40);
        boolean drawGridOverlay = gridAlphaInt > 0;
        if(drawGridOverlay){
            cache.updateFullOverlay();
        }

        float w = Core.graphics.getWidth();
        float h = Core.graphics.getHeight();

        float ratio = (float)renderer.minimap.getTexture().height / (float)renderer.minimap.getTexture().width;

        float panx = fullAccess.getPanX(ui.minimapfrag);
        float pany = fullAccess.getPanY(ui.minimapfrag);
        float zoom = fullAccess.getZoom(ui.minimapfrag);
        float baseSize = fullAccess.getBaseSize(ui.minimapfrag, Scl.scl(5f));

        float size = baseSize * zoom * world.width();
        fullBounds.set(w/2f + panx*zoom - size/2f, h/2f + pany*zoom - size/2f * ratio, size, size * ratio);

        //overlay fill texture
        if(drawGridOverlay){
            Texture overlayTex = cache.getFullOverlayTexture();
            if(overlayTex != null){
                overlayTex.setFilter(Texture.TextureFilter.nearest);
                TextureRegion reg = Draw.wrap(overlayTex);
                Draw.color();
                Draw.rect(reg, w/2f + panx*zoom, h/2f + pany*zoom, size, size * ratio);
            }
        }

        //balance markers on top (world-coordinates transform)
        float scaleFactor = fullBounds.width / world.unitWidth();
        float invScale = 1f / scaleFactor;

        oldTransform.set(Draw.trans());
        fullTransform.idt();
        fullTransform.translate(fullBounds.x, fullBounds.y);
        fullTransform.scl(Tmp.v1.set(scaleFactor, fullBounds.height / world.unitHeight()));
        fullTransform.translate(tilesize / 2f, tilesize / 2f);
        Draw.trans(fullTransform);

        Rect viewRect = Tmp.r2.set(0f, 0f, world.unitWidth(), world.unitHeight());

        //fill marker rectangles with a light color (world-coordinates transform)
        if(drawGridOverlay && !cache.markerRects.isEmpty()){
            float alpha = Mathf.clamp(gridAlphaInt / 100f) * 0.12f;
            for(int i = 0; i < cache.markerRects.size; i++){
                MarkerRectInfo r = cache.markerRects.get(i);
                if(r.graph == null) continue;
                if(!viewRect.overlaps(r.worldRect)) continue;
                Color base = MinimapOverlay.colorForGraph(r.colorKey, Tmp.c1);
                Color light = Tmp.c2.set(base).lerp(Color.white, 0.75f);
                Draw.color(light.r, light.g, light.b, alpha);
                Fill.rect(r.worldRect.x + r.worldRect.width / 2f, r.worldRect.y + r.worldRect.height / 2f, r.worldRect.width, r.worldRect.height);
            }
            Draw.color();
        }

        drawBalanceMarkersOnFullMap(viewRect, invScale);

        //reconnect marker on top
        alert.drawWorldMarker(viewRect, invScale);
        rescueAlert.drawWorldMarker(viewRect, invScale, rescueColor);

        Draw.trans(oldTransform);
        Draw.reset();

        alert.drawScreenText();
        rescueAlert.drawScreenText();
    }

    private void drawBalanceMarkersOnFullMap(Rect viewRect, float invScale){
        if(!Core.settings.getBool(keyShowBalance, true)) return;

        float markerScale = Core.settings.getInt(keyMarkerScale, 100) / 100f;
        if(markerScale <= 0.001f) return;

        Font font = Fonts.outline;
        GlyphLayout layout = Pools.obtain(GlyphLayout.class, GlyphLayout::new);

        boolean ints = font.usesIntegerPositions();
        font.setUseIntegerPositions(false);

        float baseFontScale = (1f / 1.25f) / Math.max(0.0001f, Scl.scl(1f));
        font.getData().setScale(baseFontScale * invScale * markerScale);

        Color textColor = Tmp.c2.set(markerColor);

        for(int i = 0; i < cache.markers.size; i++){
            MarkerInfo info = cache.markers.get(i);
            PowerGraph graph = info.graph;
            if(graph == null) continue;
            if(!viewRect.contains(info.x, info.y)) continue;

            float balance = graph.getPowerBalance() * 60f;
            String text = (balance >= 0f ? "+" : "") + UI.formatAmount((long)balance);

            layout.setText(font, text);

            float margin = 3f * invScale * markerScale;

            Draw.color(0f, 0f, 0f, 0.35f);
            Fill.rect(info.x, info.y, layout.width + margin * 2f, layout.height + margin * 2f);
            Draw.color();

            font.setColor(textColor);
            font.draw(text, info.x, info.y + layout.height / 2f, 0, Align.center, false);
        }

        Draw.reset();
        font.getData().setScale(1f);
        font.setColor(Color.white);
        font.setUseIntegerPositions(ints);
        Pools.free(layout);
    }

    private static boolean tryParseHexColor(String text, Color out){
        if(text == null) return false;
        String value = text.trim();
        if(value.startsWith("#")){
            value = value.substring(1);
        }

        int len = value.length();
        if(len != 6 && len != 8) return false;

        try{
            long parsed = Long.parseLong(value, 16);
            if(len == 6){
                float r = ((parsed >> 16) & 0xff) / 255f;
                float g = ((parsed >> 8) & 0xff) / 255f;
                float b = (parsed & 0xff) / 255f;
                out.set(r, g, b, 1f);
            }else{
                float r = ((parsed >> 24) & 0xff) / 255f;
                float g = ((parsed >> 16) & 0xff) / 255f;
                float b = ((parsed >> 8) & 0xff) / 255f;
                float a = (parsed & 0xff) / 255f;
                out.set(r, g, b, a);
            }
            return true;
        }catch(Throwable ignored){
            return false;
        }
    }

    private static class MinimapOverlay extends Element{
        private static final float minimapBaseSize = 16f;

        private final Element base;
        private final PowerGridCache cache;
        private final Color markerColor;
        private final SplitAlert alert;
        private final RescueAlert rescueAlert;
        private final Color rescueColor;

        private final Rect viewRect = new Rect();
        private final Mat transform = new Mat();
        private final Mat oldTransform = new Mat();

        public MinimapOverlay(Element base, PowerGridCache cache, Color markerColor, SplitAlert alert, RescueAlert rescueAlert, Color rescueColor){
            this.base = base;
            this.cache = cache;
            this.markerColor = markerColor;
            this.alert = alert;
            this.rescueAlert = rescueAlert;
            this.rescueColor = rescueColor;
        }

        @Override
        public void act(float delta){
            if(base != null){
                setBounds(base.x, base.y, base.getWidth(), base.getHeight());
            }
            super.act(delta);
        }

        @Override
        public void draw(){
            if(!Core.settings.getBool(keyEnabled, true)) return;
            if(renderer == null || renderer.minimap == null || renderer.minimap.getRegion() == null) return;
            if(world == null || !state.isGame() || world.isGenerating()) return;

            cache.updateBasic();

            // Overlay texture rebuild is expensive; don't do it when the grid overlay is transparent.
            if(Core.settings.getInt(keyGridAlpha, 40) > 0){
                cache.updateFullOverlay();
            }

            if(!clipBegin()) return;

            float zoom = renderer.minimap.getZoom();
            float sz = minimapBaseSize * zoom;
            float dx = (Core.camera.position.x / tilesize);
            float dy = (Core.camera.position.y / tilesize);
            dx = Mathf.clamp(dx, sz, world.width() - sz);
            dy = Mathf.clamp(dy, sz, world.height() - sz);

            viewRect.set((dx - sz) * tilesize, (dy - sz) * tilesize, sz * 2f * tilesize, sz * 2f * tilesize);

            float scale = width / viewRect.width;
            float invScale = 1f / scale;

            oldTransform.set(Draw.trans());

            transform.set(oldTransform);
            transform.translate(x, y);
            transform.scl(Tmp.v1.set(scale, height / viewRect.height));
            transform.translate(-viewRect.x, -viewRect.y);
            transform.translate(tilesize / 2f, tilesize / 2f);
            Draw.trans(transform);

            drawGridColors(invScale);
            drawBalanceMarkers(invScale);
            alert.drawHudMinimapMarker(invScale, viewRect);
            rescueAlert.drawHudMinimapMarker(invScale, viewRect, rescueColor);

            Draw.trans(oldTransform);
            Draw.reset();

            clipEnd();
        }

        private void drawGridColors(float invScale){
            float alpha = Mathf.clamp(Core.settings.getInt(keyGridAlpha, 40) / 100f) * parentAlpha;
            if(alpha <= 0.001f) return;

            //draw the same full-map overlay texture on the HUD minimap
            Texture overlayTex = cache.getFullOverlayTexture();
            if(overlayTex != null){
                overlayTex.setFilter(Texture.TextureFilter.nearest);
                TextureRegion reg = Draw.wrap(overlayTex);
                Draw.color(1f, 1f, 1f, parentAlpha);
                Draw.rect(reg, world.width() * tilesize / 2f, world.height() * tilesize / 2f, world.width() * tilesize, world.height() * tilesize);
            }

            //fill marker rectangles with a light color to show the partitioned rectangles
            float rectAlpha = alpha * 0.18f;
            for(int ri = 0; ri < cache.markerRects.size; ri++){
                MarkerRectInfo r = cache.markerRects.get(ri);
                if(!viewRect.overlaps(r.worldRect)) continue;
                Color base = colorForGraph(r.colorKey, Tmp.c1);
                Color light = Tmp.c2.set(base).lerp(Color.white, 0.75f);
                Draw.color(light.r, light.g, light.b, rectAlpha);
                Fill.rect(r.worldRect.x + r.worldRect.width / 2f, r.worldRect.y + r.worldRect.height / 2f, r.worldRect.width, r.worldRect.height);
            }

            Draw.color();
        }

        private void drawBalanceMarkers(float invScale){
            if(player == null) return;
            if(!Core.settings.getBool(keyShowBalance, true)) return;

            float markerScale = Core.settings.getInt(keyMarkerScale, 100) / 100f;
            if(markerScale <= 0.001f) return;

            float follow = Mathf.clamp(Core.settings.getInt(keyHudMarkerFollowScale, 100) / 100f, 0f, 2f);
            float invScalePow = Mathf.pow(invScale, 1f - follow);

            Font font = Fonts.outline;
            GlyphLayout layout = Pools.obtain(GlyphLayout.class, GlyphLayout::new);

            boolean ints = font.usesIntegerPositions();
            font.setUseIntegerPositions(false);

            float baseFontScale = (1f / 1.25f) / Math.max(0.0001f, Scl.scl(1f));
            font.getData().setScale(baseFontScale * invScalePow * markerScale);

            Color textColor = Tmp.c2.set(markerColor);
            textColor.a *= parentAlpha;

            for(int i = 0; i < cache.markers.size; i++){
                MarkerInfo info = cache.markers.get(i);
                PowerGraph graph = info.graph;
                if(graph == null) continue;
                if(!viewRect.contains(info.x, info.y)) continue;

                float balance = graph.getPowerBalance() * 60f;
                String text = (balance >= 0f ? "+" : "") + UI.formatAmount((long)balance);

                layout.setText(font, text);

                float margin = 3f * invScalePow * markerScale;

                Draw.color(0f, 0f, 0f, 0.35f * parentAlpha);
                Fill.rect(info.x, info.y, layout.width + margin * 2f, layout.height + margin * 2f);
                Draw.color();

                font.setColor(textColor);
                font.draw(text, info.x, info.y + layout.height / 2f, 0, Align.center, false);
            }

            Draw.reset();
            font.getData().setScale(1f);
            font.setColor(Color.white);
            font.setUseIntegerPositions(ints);
            Pools.free(layout);
        }

        private static Color colorForGraph(int key, Color out){
            //Deterministic, high-contrast colors:
            //- stable across minimap opens (key is stable)
            //- avoids "almost same" hues by quantizing hue to 12 buckets (30° each)
            //- adds 3 saturation/value variants to support many grids without looking identical
            int h = key;
            h ^= (h >>> 16);
            h *= 0x7feb352d;
            h ^= (h >>> 15);
            h *= 0x846ca68b;
            h ^= (h >>> 16);

            int hueBuckets = 12;
            int variants = 3;
            int hueIndex = Math.floorMod(h, hueBuckets);
            int variant = Math.floorMod(h / hueBuckets, variants);

            float hue = hueIndex * (360f / hueBuckets);
            float sat = variant == 0 ? 0.78f : (variant == 1 ? 0.92f : 0.58f);
            float val = variant == 1 ? 0.88f : 1f;

            out.fromHsv(hue, sat, val);
            return out;
        }
    }

    /** Optional integration with MI2-Utilities-Java minimap window. Uses reflection so missing MI2 won't crash. */
    private static class Mi2MinimapIntegration{
        private boolean available = false;

        private ClassLoader mi2Loader;
        private java.lang.reflect.Field minimapField;
        private java.lang.reflect.Field rectField;
        private java.lang.reflect.Method setRectMethod;
        private float nextInitAttempt = 0f;

        void tryInit(){
            try{
                available = false;
                //MI2 is a mod -> loaded in a separate classloader; find its loader and load classes through it.
                Class<?> mm = null;
                if(mindustry.Vars.mods != null){
                    for(Mods.LoadedMod mod : mindustry.Vars.mods.list()){
                        if(mod == null || mod.loader == null) continue;
                        try{
                            mm = Class.forName("mi2u.ui.MinimapMindow", false, mod.loader);
                            mi2Loader = mod.loader;
                            break;
                        }catch(Throwable ignored){
                        }
                    }
                }
                if(mm == null){
                    throw new ClassNotFoundException("mi2u.ui.MinimapMindow");
                }
                minimapField = mm.getField("m"); // public static Minimap2 m
                Object minimap = minimapField.get(null);
                if(minimap == null) throw new IllegalStateException("MI2 minimap not initialized");

                Class<?> minimapType = minimap.getClass();
                rectField = minimapType.getField("rect"); // public Rect rect
                setRectMethod = minimapType.getMethod("setRect");

                available = true;
                Log.info("PGMM: MI2 minimap detected; overlay integration enabled.");
            }catch(Throwable ignored){
                available = false;
            }
        }

        boolean isAvailable(){
            return available;
        }

        void ensureAttached(PowerGridCache cache, Color markerColor, SplitAlert alert, RescueAlert rescueAlert, Color rescueColor){
            if(!Core.settings.getBool(keyDrawOnMi2Minimap, false)){
                detachIfPresent();
                return;
            }

            //MI2 can load later, and its minimap window can rebuild. Keep retrying initialization.
            if(!available){
                if(Time.time >= nextInitAttempt){
                    nextInitAttempt = Time.time + 60f * 2f;
                    tryInit();
                }
            }
            if(!available) return;
            if(Core.scene == null) return;

            try{
                Object minimapObj = minimapField == null ? null : minimapField.get(null);
                if(!(minimapObj instanceof Element)) return;
                Element minimap = (Element)minimapObj;
                if(minimap.getScene() == null) return;

                //MI2's mindow can rebuild/replace its internal tables frequently, causing children to be dropped.
                //We attach as a sibling of the minimap element and keep retrying periodically.
                Element existing = null;
                try{
                    existing = Core.scene.find(mi2OverlayName);
                }catch(Throwable ignored){
                }
                if(existing != null){
                    //If base minimap element changed, replace overlay; otherwise keep it.
                    if(existing instanceof Mi2Overlay){
                        Mi2Overlay mo = (Mi2Overlay)existing;
                        if(mo.base != minimap || mo.parent != minimap.parent){
                            mo.remove();
                            existing = null;
                        }
                    }else{
                        existing.remove();
                        existing = null;
                    }
                }
                if(existing != null) return;
                if(minimap.parent == null) return;

                Mi2Overlay overlay = new Mi2Overlay(minimap, cache, markerColor, alert, rescueAlert, rescueColor, rectField, setRectMethod);
                overlay.name = mi2OverlayName;
                //important: must have non-zero bounds BEFORE parent culling, otherwise draw() may never run.
                overlay.setBounds(minimap.x, minimap.y, Math.max(1f, minimap.getWidth()), Math.max(1f, minimap.getHeight()));
                overlay.update(overlay::syncBoundsToBase);
                overlay.touchable = Touchable.disabled;
                minimap.parent.addChild(overlay);
                overlay.toFront();
                Log.info("PGMM: MI2 overlay attached to minimap parent (@).", minimap.parent.getClass().getName());
            }catch(Throwable t){
                Log.err("PGMM: MI2 minimap attach failed; will retry.", t);
                //don't permanently disable; allow retry on next attempt
                nextInitAttempt = Time.time + 60f * 2f;
                available = false;
            }
        }

        private void detachIfPresent(){
            try{
                if(Core.scene == null) return;
                Element existing = null;
                try{
                    existing = Core.scene.find(mi2OverlayName);
                }catch(Throwable ignored){
                }
                if(existing != null) existing.remove();
            }catch(Throwable ignored){
            }
        }
    }

    private static class Mi2Overlay extends Element{
        private final Element base;
        private final PowerGridCache cache;
        private final Color markerColor;
        private final SplitAlert alert;
        private final RescueAlert rescueAlert;
        private final Color rescueColor;

        private final java.lang.reflect.Field rectField;
        private final java.lang.reflect.Method setRectMethod;

        private final Rect viewRect = new Rect();
        private boolean loggedDraw = false;
        private boolean loggedClipFail = false;

        Mi2Overlay(Element base, PowerGridCache cache, Color markerColor, SplitAlert alert, RescueAlert rescueAlert, Color rescueColor, java.lang.reflect.Field rectField, java.lang.reflect.Method setRectMethod){
            this.base = base;
            this.cache = cache;
            this.markerColor = markerColor;
            this.alert = alert;
            this.rescueAlert = rescueAlert;
            this.rescueColor = rescueColor;
            this.rectField = rectField;
            this.setRectMethod = setRectMethod;
        }

        void syncBoundsToBase(){
            if(base == null || parent == null) return;
            float bw = base.getWidth(), bh = base.getHeight();
            if(bw <= 0.001f || bh <= 0.001f){
                bw = Math.max(bw, base.getPrefWidth());
                bh = Math.max(bh, base.getPrefHeight());
            }
            if(bw <= 0.001f || bh <= 0.001f) return;
            setBounds(base.x, base.y, bw, bh);
        }

        @Override
        public void draw(){
            if(!Core.settings.getBool(keyEnabled, true)) return;
            if(!Core.settings.getBool(keyDrawOnMi2Minimap, false)) return;
            if(renderer == null || renderer.minimap == null || renderer.minimap.getRegion() == null) return;
            if(world == null || !state.isGame() || world.isGenerating()) return;
            if(base == null || base.getScene() == null) return;
            if(base.parent == null) return;

            syncBoundsToBase();
            if(width <= 0.001f || height <= 0.001f) return;

            if(!loggedDraw){
                loggedDraw = true;
                Log.info("PGMM: MI2 overlay draw() active (name=@, base=@).", name, base.getClass().getName());
            }

            cache.updateBasic();

            // Overlay texture rebuild is expensive; don't do it when the grid overlay is transparent.
            int gridAlphaInt = Core.settings.getInt(keyGridAlpha, 40);
            boolean drawGridOverlay = gridAlphaInt > 0;
            if(drawGridOverlay){
                cache.updateFullOverlay();
            }

            if(!clipBegin()){
                if(!loggedClipFail){
                    loggedClipFail = true;
                    Log.info("PGMM: MI2 overlay clipBegin failed (x=@ y=@ w=@ h=@, baseW=@ baseH=@).",
                        x, y, width, height, base.getWidth(), base.getHeight());
                }
                return;
            }

            Rect r = getMi2Rect();
            if(r == null){
                clipEnd();
                return;
            }
            viewRect.set(r);

            float scaleX = width / viewRect.width;
            float scaleY = height / viewRect.height;
            //MI2 maintains rect aspect ratio, so X/Y scales should match; use X as canonical.
            float scale = scaleX;
            float invScale = 1f / Math.max(0.000001f, scale);

            //Match MI2's draw style: draw a cropped region in UI-space, not a world-space transform.
            if(drawGridOverlay){
                Texture overlayTex = cache.getFullOverlayTexture();
                if(overlayTex != null){
                    overlayTex.setFilter(Texture.TextureFilter.nearest);
                    Draw.color(1f, 1f, 1f, parentAlpha);
                    float invTexWidth = 1f / overlayTex.width / tilesize;
                    float invTexHeight = 1f / overlayTex.height / tilesize;
                    float pixmapy = world.height() * tilesize - (viewRect.y + viewRect.height);
                    Tmp.tr1.set(overlayTex);
                    Tmp.tr1.set(
                        viewRect.x * invTexWidth,
                        pixmapy * invTexHeight,
                        (viewRect.x + viewRect.width) * invTexWidth,
                        (pixmapy + viewRect.height) * invTexHeight
                    );
                    Draw.rect(Tmp.tr1, x + width / 2f, y + height / 2f, width, height);
                }
            }

            float alpha = Mathf.clamp(gridAlphaInt / 100f) * parentAlpha;
            float rectAlpha = alpha * 0.18f;
            for(int ri = 0; ri < cache.markerRects.size; ri++){
                MarkerRectInfo mr = cache.markerRects.get(ri);
                if(!viewRect.overlaps(mr.worldRect)) continue;
                Color baseColor = MinimapOverlay.colorForGraph(mr.colorKey, Tmp.c1);
                Color light = Tmp.c2.set(baseColor).lerp(Color.white, 0.75f);
                Draw.color(light.r, light.g, light.b, rectAlpha);
                float cx = x + (mr.worldRect.x + mr.worldRect.width / 2f - viewRect.x) * scaleX;
                float cy = y + (mr.worldRect.y + mr.worldRect.height / 2f - viewRect.y) * scaleY;
                Fill.rect(cx, cy, mr.worldRect.width * scaleX, mr.worldRect.height * scaleY);
            }
            Draw.color();

            drawBalanceMarkersScreen(scale, invScale);
            alert.drawMinimapMarkerScreen(viewRect, x, y, scaleX, scaleY, parentAlpha);
            rescueAlert.drawMinimapMarkerScreen(viewRect, x, y, scaleX, scaleY, parentAlpha, rescueColor);
            Draw.reset();

            clipEnd();
        }

        private void drawBalanceMarkersScreen(float scale, float invScale){
            if(player == null) return;
            if(!Core.settings.getBool(keyShowBalance, true)) return;

            float markerScale = Core.settings.getInt(keyMarkerScale, 100) / 100f;
            if(markerScale <= 0.001f) return;

            float follow = Mathf.clamp(Core.settings.getInt(keyHudMarkerFollowScale, 100) / 100f, 0f, 2f);
            float invScalePow = Mathf.pow(invScale, 1f - follow);

            Font font = Fonts.outline;
            GlyphLayout layout = Pools.obtain(GlyphLayout.class, GlyphLayout::new);

            boolean ints = font.usesIntegerPositions();
            font.setUseIntegerPositions(false);

            //In the old (world-space) draw, scale was applied by Draw.trans(); here we fold it into the font scale.
            float baseFontScale = (1f / 1.25f) / Math.max(0.0001f, Scl.scl(1f));
            font.getData().setScale(baseFontScale * invScalePow * markerScale * scale);

            Color textColor = Tmp.c2.set(markerColor);
            textColor.a *= parentAlpha;

            for(int i = 0; i < cache.markers.size; i++){
                MarkerInfo info = cache.markers.get(i);
                PowerGraph graph = info.graph;
                if(graph == null) continue;
                if(!viewRect.contains(info.x, info.y)) continue;

                float balance = graph.getPowerBalance() * 60f;
                String text = (balance >= 0f ? "+" : "") + UI.formatAmount((long)balance);

                layout.setText(font, text);

                float sx = x + (info.x - viewRect.x) * (width / viewRect.width);
                float sy = y + (info.y - viewRect.y) * (height / viewRect.height);

                float margin = 3f * invScalePow * markerScale * scale;

                Draw.color(0f, 0f, 0f, 0.35f * parentAlpha);
                Fill.rect(sx, sy, layout.width + margin * 2f, layout.height + margin * 2f);
                Draw.color();

                font.setColor(textColor);
                font.draw(text, sx, sy + layout.height / 2f, 0, Align.center, false);
            }

            Draw.reset();
            font.getData().setScale(1f);
            font.setColor(Color.white);
            font.setUseIntegerPositions(ints);
            Pools.free(layout);
        }

        private Rect getMi2Rect(){
            try{
                if(setRectMethod != null){
                    setRectMethod.invoke(base);
                }
                Object r = rectField == null ? null : rectField.get(base);
                return r instanceof Rect ? (Rect)r : null;
            }catch(Throwable ignored){
                return null;
            }
        }

        private void drawBalanceMarkers(float invScale){
            if(player == null) return;
            if(!Core.settings.getBool(keyShowBalance, true)) return;

            float markerScale = Core.settings.getInt(keyMarkerScale, 100) / 100f;
            if(markerScale <= 0.001f) return;

            float follow = Mathf.clamp(Core.settings.getInt(keyHudMarkerFollowScale, 100) / 100f, 0f, 2f);
            float invScalePow = Mathf.pow(invScale, 1f - follow);

            Font font = Fonts.outline;
            GlyphLayout layout = Pools.obtain(GlyphLayout.class, GlyphLayout::new);

            boolean ints = font.usesIntegerPositions();
            font.setUseIntegerPositions(false);

            float baseFontScale = (1f / 1.25f) / Math.max(0.0001f, Scl.scl(1f));
            font.getData().setScale(baseFontScale * invScalePow * markerScale);

            Color textColor = Tmp.c2.set(markerColor);
            textColor.a *= parentAlpha;

            for(int i = 0; i < cache.markers.size; i++){
                MarkerInfo info = cache.markers.get(i);
                PowerGraph graph = info.graph;
                if(graph == null) continue;
                if(!viewRect.contains(info.x, info.y)) continue;

                float balance = graph.getPowerBalance() * 60f;
                String text = (balance >= 0f ? "+" : "") + UI.formatAmount((long)balance);

                layout.setText(font, text);

                float margin = 3f * invScalePow * markerScale;

                Draw.color(0f, 0f, 0f, 0.35f * parentAlpha);
                Fill.rect(info.x, info.y, layout.width + margin * 2f, layout.height + margin * 2f);
                Draw.color();

                font.setColor(textColor);
                font.draw(text, info.x, info.y + layout.height / 2f, 0, Align.center, false);
            }

            Draw.reset();
            font.getData().setScale(1f);
            font.setColor(Color.white);
            font.setUseIntegerPositions(ints);
            Pools.free(layout);
        }
    }

    // Extracted helper types live in `PgmmTypes` (no behavior changes).

    private static class PowerGridCache{
        private static float updateWaitTicks(){
            //Stored as tenths of seconds to allow fine-grained tuning with sliderPref.
            int tenths = Core.settings.getInt(keyUpdateWaitTenths, 10);
            tenths = Mathf.clamp(tenths, 0, 600);
            return tenths / 10f * 60f;
        }

        private static float fullUpdateMinTicks(){
            return Math.max(60f, updateWaitTicks());
        }

        private final ObjectSet<PowerGraph> graphs = new ObjectSet<>();
        private final Seq<GridInfo> grids = new Seq<>();
        private final Seq<MarkerInfo> markers = new Seq<>();
        private final Seq<MarkerRectInfo> markerRects = new Seq<>();

        //object pools to reduce allocations/GC
        private final Seq<MarkerInfo> markerPool = new Seq<>();
        private int markerPoolUsed = 0;
        private final Seq<MarkerRectInfo> rectPool = new Seq<>();
        private int rectPoolUsed = 0;
        private final Seq<ClusterInfo> clusterPool = new Seq<>();
        private int clusterPoolUsed = 0;

        //reused scratch objects for clustering
        private final IntSet tmpOccupied = new IntSet();
        private final IntSeq tmpOccupiedList = new IntSeq();
        private final IntSet tmpVisited = new IntSet();
        private final IntQueue tmpQueue = new IntQueue();
        private final Seq<ClusterInfo> tmpClusters = new Seq<>();

        //reused scratch objects for area-based filtering
        private final IntSet tmpAreaSeen = new IntSet();

        //reused scratch arrays for MST/partitioning (clusters are capped)
        private static final int maxMarkersPerGraph = 64;
        private final int[] mstParent = new int[maxMarkersPerGraph];
        private final float[] mstParentW = new float[maxMarkersPerGraph];
        private final boolean[] mstUsed = new boolean[maxMarkersPerGraph];
        private final float[] mstBest = new float[maxMarkersPerGraph];
        private final boolean[] mstCompVisited = new boolean[maxMarkersPerGraph];
        private final IntSeq[] mstAdj = new IntSeq[maxMarkersPerGraph];
        {
            for(int i = 0; i < mstAdj.length; i++){
                mstAdj[i] = new IntSeq();
            }
        }

        private float nextUpdateTime = 0f;
        private boolean basicDirty = true;
        private boolean fullDirty = true;

        private int lastIgnoreAreaTiles = Integer.MIN_VALUE;

        //fullscreen overlay cache
        private float nextFullUpdateTime = 0f;
        private Pixmap fullOverlayPixmap;
        private Texture fullOverlayTexture;
        private int lastClaimDistance = -1;
        private int lastGridAlpha = -1;
        private int lastWorldW = -1, lastWorldH = -1;

        //reused scratch arrays for full overlay rebuild
        private int lastTileCount = -1;
        private int[] tmpOwnerPower;
        private int[] tmpOwnerClaim;
        private int[] tmpOverlayOwner;
        private boolean[] tmpClaimedBuild;
        private boolean[] tmpVisitedTiles;
        private short[] tmpDist;
        private int[] tmpOwnerNear;
        private boolean[] tmpAssigned;
        private final Seq<mindustry.gen.Building> tmpNonPower = new Seq<>();
        private final IntSeq tmpTiles = new IntSeq();
        private final IntSeq tmpComp = new IntSeq();
        private final IntQueue tmpBfs = new IntQueue();
        private final int[] tmpNeighborGrids = new int[8];

        // Reused per-grid RGBA lookup tables for the full overlay rebuild.
        // These avoid allocating 3x int[gridCount] every time the overlay pixmap is regenerated.
        private int[] tmpBaseRgbaByGrid;
        private int[] tmpDarkRgbaByGrid;
        private int[] tmpLightRgbaByGrid;

        public void clear(){
            graphs.clear();
            grids.clear();
            markers.clear();
            markerRects.clear();
            nextUpdateTime = 0f;
            nextFullUpdateTime = 0f;
            basicDirty = true;
            fullDirty = true;
            lastIgnoreAreaTiles = Integer.MIN_VALUE;

            if(fullOverlayPixmap != null){
                fullOverlayPixmap.dispose();
                fullOverlayPixmap = null;
            }
            if(fullOverlayTexture != null){
                fullOverlayTexture.dispose();
                fullOverlayTexture = null;
            }
            lastClaimDistance = -1;
            lastGridAlpha = -1;
            lastWorldW = lastWorldH = -1;

            lastTileCount = -1;
            tmpOwnerPower = null;
            tmpOwnerClaim = null;
            tmpOverlayOwner = null;
            tmpClaimedBuild = null;
            tmpVisitedTiles = null;
            tmpDist = null;
            tmpOwnerNear = null;
            tmpAssigned = null;
        }

        public void invalidateAll(){
            basicDirty = true;
            fullDirty = true;
            float wait = updateWaitTicks();
            nextUpdateTime = Time.time + wait;
            nextFullUpdateTime = Time.time + wait;
        }

        /** Bypasses update delay and forces next updateBasic/updateFullOverlay to rebuild immediately. */
        public void forceRebuildNow(){
            basicDirty = true;
            fullDirty = true;
            nextUpdateTime = 0f;
            nextFullUpdateTime = 0f;
        }

        public void updateBasic(){
            if(!state.isGame() || world == null || world.isGenerating() || player == null){
                clear();
                return;
            }

            int ignoreAreaTiles = Mathf.clamp(Core.settings.getInt(keyIgnoreAreaTiles, 0), 0, 1000000);
            if(ignoreAreaTiles != lastIgnoreAreaTiles){
                lastIgnoreAreaTiles = ignoreAreaTiles;
                basicDirty = true;
                fullDirty = true;
                nextUpdateTime = 0f;
                nextFullUpdateTime = 0f;
            }

            if(Time.time < nextUpdateTime) return;
            if(!basicDirty){
                // If nothing invalidated the cache, avoid rescanning the whole map repeatedly.
                float wait = updateWaitTicks();
                nextUpdateTime = Time.time + wait * 6f;
                return;
            }

            float wait = updateWaitTicks();
            nextUpdateTime = Time.time + wait;
            basicDirty = false;

            graphs.clear();
            grids.clear();
            markers.clear();
            markerRects.clear();
            markerPoolUsed = 0;
            rectPoolUsed = 0;

            for(int i = 0; i < mindustry.gen.Groups.build.size(); i++){
                mindustry.gen.Building build = mindustry.gen.Groups.build.index(i);
                if(build == null || build.power == null) continue;
                if(build.power.graph == null || build.power.graph.all == null || build.power.graph.all.isEmpty()) continue;
                graphs.add(build.power.graph);
            }

            for(PowerGraph graph : graphs){
                if(graph == null || graph.all == null) continue;

                Team team = graphTeam(graph);
                if(team == null) continue;

                //Ignore "single-building grids" to reduce minimap noise; they are often stray nodes and not useful to render.
                int buildCount = 0;
                Seq<mindustry.gen.Building> all = graph.all;
                for(int bi = 0; bi < all.size; bi++){
                    mindustry.gen.Building b = all.get(bi);
                    if(b == null || b.team != team) continue;
                    buildCount++;
                    if(buildCount > 1) break;
                }
                if(buildCount <= 1) continue;

                if(ignoreAreaTiles > 0 && graphAreaTiles(graph, team) < ignoreAreaTiles) continue;

                GridInfo info = new GridInfo();
                info.graph = graph;
                info.team = team;
                grids.add(info);

                //For sparse laser-linked grids, render one balance marker per contiguous "chunk" of buildings.
                addClusterMarkers(info);
            }
        }

        private Team graphTeam(PowerGraph graph){
            if(graph == null || graph.all == null) return null;
            Seq<Building> all = graph.all;
            for(int i = 0; i < all.size; i++){
                Building b = all.get(i);
                if(b != null && b.team != null) return b.team;
            }
            return null;
        }

        private int graphAreaTiles(PowerGraph graph, Team team){
            if(graph == null) return 0;
            tmpAreaSeen.clear();
            int area = 0;
            area += addAreaTiles(graph.producers, team);
            area += addAreaTiles(graph.consumers, team);
            area += addAreaTiles(graph.batteries, team);
            return area;
        }

        private int addAreaTiles(Seq<Building> builds, Team team){
            if(builds == null || builds.isEmpty()) return 0;
            int sum = 0;
            for(int i = 0; i < builds.size; i++){
                Building b = builds.get(i);
                if(b == null || b.team != team || b.block == null) continue;
                int pos = b.pos();
                if(tmpAreaSeen.contains(pos)) continue;
                tmpAreaSeen.add(pos);
                int size = Math.max(1, b.block.size);
                sum += size * size;
            }
            return sum;
        }

        private void addClusterMarkers(GridInfo info){
            PowerGraph graph = info.graph;
            if(graph == null || graph.all == null || graph.all.isEmpty()) return;
            Team team = info.team;
            if(team == null) return;
            info.colorKey = graph.getID();
            info.hasCenter = false;

            //Collect occupied tiles (all linked tiles for each building) for this graph.
            tmpOccupied.clear();
            tmpOccupiedList.clear();

            Seq<mindustry.gen.Building> all = graph.all;
            for(int i = 0; i < all.size; i++){
                mindustry.gen.Building b = all.get(i);
                if(b == null || b.team != team || b.tile == null) continue;
                b.tile.getLinkedTiles(t -> {
                    int pos = t.pos();
                    if(!tmpOccupied.contains(pos)){
                        tmpOccupied.add(pos);
                        tmpOccupiedList.add(pos);
                    }
                });
            }

            if(tmpOccupiedList.isEmpty()) return;

            //stable color key: minimum occupied tile position (stable across minimap opens)
            int minPos = Integer.MAX_VALUE;
            for(int i = 0; i < tmpOccupiedList.size; i++){
                int p = tmpOccupiedList.get(i);
                if(p < minPos) minPos = p;
            }
            if(minPos != Integer.MAX_VALUE){
                info.colorKey = minPos;
            }

            //Flood-fill connected components on the tile grid, using 4-neighbor adjacency.
            //If clusters are close together (< threshold tiles), draw a single marker at the whole-graph center.
            //If clusters are far apart (> threshold tiles), draw one marker per cluster.
            tmpVisited.clear();
            tmpQueue.clear();
            tmpClusters.clear();
            clusterPoolUsed = 0;
            float totalSumX = 0f, totalSumY = 0f;
            int totalCount = 0;

            for(int i = 0; i < tmpOccupiedList.size; i++){
                int start = tmpOccupiedList.get(i);
                if(tmpVisited.contains(start)) continue;

                ClusterInfo cluster = clusterPoolUsed < clusterPool.size ? clusterPool.get(clusterPoolUsed) : new ClusterInfo();
                if(clusterPoolUsed >= clusterPool.size) clusterPool.add(cluster);
                clusterPoolUsed++;
                cluster.minx = Integer.MAX_VALUE;
                cluster.miny = Integer.MAX_VALUE;
                cluster.maxx = Integer.MIN_VALUE;
                cluster.maxy = Integer.MIN_VALUE;
                cluster.sumX = 0f;
                cluster.sumY = 0f;
                cluster.count = 0;

                tmpVisited.add(start);
                tmpQueue.addLast(start);

                while(tmpQueue.size > 0){
                    int cur = tmpQueue.removeFirst();
                    int x = Point2.x(cur);
                    int y = Point2.y(cur);

                    cluster.sumX += (x + 0.5f) * tilesize;
                    cluster.sumY += (y + 0.5f) * tilesize;
                    cluster.count++;

                    if(x < cluster.minx) cluster.minx = x;
                    if(y < cluster.miny) cluster.miny = y;
                    if(x > cluster.maxx) cluster.maxx = x;
                    if(y > cluster.maxy) cluster.maxy = y;

                    int n;
                    n = Point2.pack(x + 1, y);
                    if(x + 1 < world.width() && tmpOccupied.contains(n) && !tmpVisited.contains(n)){ tmpVisited.add(n); tmpQueue.addLast(n); }
                    n = Point2.pack(x - 1, y);
                    if(x - 1 >= 0 && tmpOccupied.contains(n) && !tmpVisited.contains(n)){ tmpVisited.add(n); tmpQueue.addLast(n); }
                    n = Point2.pack(x, y + 1);
                    if(y + 1 < world.height() && tmpOccupied.contains(n) && !tmpVisited.contains(n)){ tmpVisited.add(n); tmpQueue.addLast(n); }
                    n = Point2.pack(x, y - 1);
                    if(y - 1 >= 0 && tmpOccupied.contains(n) && !tmpVisited.contains(n)){ tmpVisited.add(n); tmpQueue.addLast(n); }
                }

                if(cluster.count <= 0) continue;

                totalSumX += cluster.sumX;
                totalSumY += cluster.sumY;
                totalCount += cluster.count;

                tmpClusters.add(cluster);
                if(tmpClusters.size >= maxMarkersPerGraph){
                    //Avoid UI spam/perf issues on extreme maps.
                    break;
                }
            }

            if(tmpClusters.isEmpty() || totalCount <= 0) return;

            info.centerX = totalSumX / totalCount;
            info.centerY = totalSumY / totalCount;
            info.hasCenter = true;

            int thresholdTiles = Core.settings.getInt(keyClusterMarkerDistance, 15);
            //Partition the graph into multiple rectangles using MST cut:
            //- build MST of clusters with bbox-gap as edge weight
            //- cut edges whose weight > thresholdTiles
            //- each connected component becomes a rectangle (bbox union)
            int n = tmpClusters.size;
            int[] parent = mstParent;
            float[] parentW = mstParentW;
            boolean[] used = mstUsed;
            float[] best = mstBest;
            Arrays.fill(parent, 0, n, -1);
            Arrays.fill(parentW, 0, n, 0f);
            Arrays.fill(used, 0, n, false);
            Arrays.fill(best, 0, n, Float.POSITIVE_INFINITY);
            best[0] = 0f;

            for(int iter = 0; iter < n; iter++){
                int v = -1;
                float vBest = Float.POSITIVE_INFINITY;
                for(int i = 0; i < n; i++){
                    if(used[i]) continue;
                    float d = best[i];
                    if(d < vBest){
                        vBest = d;
                        v = i;
                    }
                }
                if(v == -1) break;
                used[v] = true;
                if(iter != 0){
                    parentW[v] = vBest;
                }

                ClusterInfo a = tmpClusters.get(v);
                for(int u = 0; u < n; u++){
                    if(used[u]) continue;
                    ClusterInfo b = tmpClusters.get(u);
                    float gap = clusterGap(a, b);
                    if(gap < best[u]){
                        best[u] = gap;
                        parent[u] = v;
                    }
                }
            }

            //build adjacency from MST edges that pass the threshold (or all edges if threshold <= 0)
            IntSeq[] adj = mstAdj;
            for(int i = 0; i < n; i++){
                adj[i].clear();
            }
            for(int i = 1; i < n; i++){
                int p = parent[i];
                if(p < 0) continue;
                float w = parentW[i];
                if(thresholdTiles > 0 && w > thresholdTiles) continue;
                adj[i].add(p);
                adj[p].add(i);
            }

            boolean[] compVisited = mstCompVisited;
            Arrays.fill(compVisited, 0, n, false);
            int maxRectsPerGraph = 64;

            for(int si = 0; si < n; si++){
                if(compVisited[si]) continue;
                compVisited[si] = true;
                tmpQueue.clear();
                tmpQueue.addLast(si);

                int minx = Integer.MAX_VALUE, miny = Integer.MAX_VALUE, maxx = Integer.MIN_VALUE, maxy = Integer.MIN_VALUE;

                while(tmpQueue.size > 0){
                    int v = tmpQueue.removeFirst();
                    ClusterInfo c = tmpClusters.get(v);
                    if(c.minx < minx) minx = c.minx;
                    if(c.miny < miny) miny = c.miny;
                    if(c.maxx > maxx) maxx = c.maxx;
                    if(c.maxy > maxy) maxy = c.maxy;

                    IntSeq nei = adj[v];
                    for(int ni = 0; ni < nei.size; ni++){
                        int to = nei.get(ni);
                        if(compVisited[to]) continue;
                        compVisited[to] = true;
                        tmpQueue.addLast(to);
                    }
                }

                if(minx == Integer.MAX_VALUE || miny == Integer.MAX_VALUE) continue;

                float wx = minx * tilesize;
                float wy = miny * tilesize;
                float ww = (maxx - minx + 1) * tilesize;
                float wh = (maxy - miny + 1) * tilesize;

                MarkerRectInfo r = rectPoolUsed < rectPool.size ? rectPool.get(rectPoolUsed) : new MarkerRectInfo();
                if(rectPoolUsed >= rectPool.size) rectPool.add(r);
                rectPoolUsed++;
                r.graph = graph;
                r.colorKey = info.colorKey;
                r.worldRect.set(wx, wy, ww, wh);
                markerRects.add(r);

                MarkerInfo m = markerPoolUsed < markerPool.size ? markerPool.get(markerPoolUsed) : new MarkerInfo();
                if(markerPoolUsed >= markerPool.size) markerPool.add(m);
                markerPoolUsed++;
                m.graph = graph;
                m.x = wx + ww / 2f;
                m.y = wy + wh / 2f;
                markers.add(m);

                if(markerRects.size >= maxRectsPerGraph) break;
            }
        }

        private static class ClusterInfo{
            float sumX, sumY;
            int count;
            int minx, miny, maxx, maxy;
        }

        private static float clusterGap(ClusterInfo a, ClusterInfo b){
            int dx = 0;
            if(a.maxx < b.minx) dx = b.minx - a.maxx - 1;
            else if(b.maxx < a.minx) dx = a.minx - b.maxx - 1;

            int dy = 0;
            if(a.maxy < b.miny) dy = b.miny - a.maxy - 1;
            else if(b.maxy < a.miny) dy = a.miny - b.maxy - 1;

            return Mathf.dst(0f, 0f, dx, dy);
        }

        public void updateFullOverlay(){
            if(!state.isGame() || world == null || world.isGenerating() || player == null){
                clear();
                return;
            }

            int claimDistance = Core.settings.getInt(keyClaimDistance, 5);
            int gridAlpha = Core.settings.getInt(keyGridAlpha, 40);

            boolean sizeChanged = world.width() != lastWorldW || world.height() != lastWorldH;
            boolean settingsChanged = claimDistance != lastClaimDistance || gridAlpha != lastGridAlpha;

            if(sizeChanged || settingsChanged){
                fullDirty = true;
            }

            if(!fullDirty) return;
            if(fullDirty && Time.time < nextFullUpdateTime) return;
            nextFullUpdateTime = Time.time + fullUpdateMinTicks();
            fullDirty = false;

            lastClaimDistance = claimDistance;
            lastGridAlpha = gridAlpha;
            lastWorldW = world.width();
            lastWorldH = world.height();

            rebuildFullOverlay(claimDistance, gridAlpha);
        }

        public Texture getFullOverlayTexture(){
            return fullOverlayTexture;
        }

        private void ensureOverlayBuffers(int w, int h){
            if(fullOverlayPixmap != null && (fullOverlayPixmap.width != w || fullOverlayPixmap.height != h)){
                fullOverlayPixmap.dispose();
                fullOverlayPixmap = null;
            }
            if(fullOverlayTexture != null && (fullOverlayTexture.width != w || fullOverlayTexture.height != h)){
                fullOverlayTexture.dispose();
                fullOverlayTexture = null;
            }

            if(fullOverlayPixmap == null){
                fullOverlayPixmap = new Pixmap(w, h);
            }
            if(fullOverlayTexture == null){
                fullOverlayTexture = new Texture(fullOverlayPixmap);
            }
        }

        private void rebuildFullOverlay(int claimDistance, int gridAlphaInt){
            //basic graph list required
            updateBasic();

            int w = world.width();
            int h = world.height();
            ensureOverlayBuffers(w, h);

            //clear to transparent
            fullOverlayPixmap.fill(0);

            if(grids.isEmpty()) {
                fullOverlayTexture.draw(fullOverlayPixmap);
                return;
            }

            final int tileCount = w * h;
            ensureOverlayScratch(tileCount);
            int[] ownerPower = tmpOwnerPower;
            int[] overlayOwner = tmpOverlayOwner;
            int[] ownerClaim = tmpOwnerClaim;
            boolean[] claimedBuild = tmpClaimedBuild;
            Team[] gridTeams = new Team[grids.size];
            for(int gi = 0; gi < grids.size; gi++){
                gridTeams[gi] = grids.get(gi).team;
            }
            Arrays.fill(ownerPower, -1);
            Arrays.fill(overlayOwner, -1);

            // Step 1) Seed power-using building tiles.
            for(int gi = 0; gi < grids.size; gi++){
                final int gridIndex = gi;
                GridInfo gridInfo = grids.get(gi);
                Team team = gridTeams[gi];
                PowerGraph graph = gridInfo == null ? null : gridInfo.graph;
                if(team == null) continue;
                if(graph == null || graph.all == null) continue;
                Seq<mindustry.gen.Building> all = graph.all;
                for(int bi = 0; bi < all.size; bi++){
                    mindustry.gen.Building b = all.get(bi);
                    if(b == null || b.team != team) continue;
                    if(b.tile == null) continue;
                    b.tile.getLinkedTiles(t -> {
                        int idx = t.x + t.y * w;
                        if(idx >= 0 && idx < tileCount) ownerPower[idx] = gridIndex;
                    });
                }
            }

            System.arraycopy(ownerPower, 0, ownerClaim, 0, tileCount);
            for(int i = 0; i < tileCount; i++){
                claimedBuild[i] = ownerClaim[i] >= 0;
            }

            // Step 2) Claim "non-power" buildings that touch exactly one grid.
            // Collect non-power buildings first so we can assign them deterministically.
            Seq<mindustry.gen.Building> nonPower = tmpNonPower;
            nonPower.clear();
            for(int i = 0; i < mindustry.gen.Groups.build.size(); i++){
                mindustry.gen.Building b = mindustry.gen.Groups.build.index(i);
                if(b == null || b.team == null) continue;
                if(b.power != null) continue;
                if(b.tile == null) continue;
                nonPower.add(b);
            }

            boolean[] assigned = ensureAssigned(nonPower.size);
            IntSeq tmpTiles = this.tmpTiles;
            int[] neighborGrids = tmpNeighborGrids;

            for(int ni = 0; ni < nonPower.size; ni++){
                mindustry.gen.Building b = nonPower.get(ni);
                tmpTiles.clear();
                b.tile.getLinkedTiles(t -> tmpTiles.add(t.x + t.y * w));

                int ncount = 0;
                Arrays.fill(neighborGrids, -1);

                for(int ti = 0; ti < tmpTiles.size; ti++){
                    int idx = tmpTiles.get(ti);
                    int tx = idx % w;
                    int ty = idx / w;

                    //4-dir adjacency
                    Team team = b.team;
                    ncount = addNeighborGrid(ownerPower, gridTeams, team, w, h, tx + 1, ty, neighborGrids, ncount);
                    ncount = addNeighborGrid(ownerPower, gridTeams, team, w, h, tx - 1, ty, neighborGrids, ncount);
                    ncount = addNeighborGrid(ownerPower, gridTeams, team, w, h, tx, ty + 1, neighborGrids, ncount);
                    ncount = addNeighborGrid(ownerPower, gridTeams, team, w, h, tx, ty - 1, neighborGrids, ncount);
                    if(ncount > 1) break;
                }

                if(ncount == 1){
                    int g = neighborGrids[0];
                    //claim this building tiles into that grid
                    for(int ti = 0; ti < tmpTiles.size; ti++){
                        int idx = tmpTiles.get(ti);
                        ownerClaim[idx] = g;
                        claimedBuild[idx] = true;
                    }
                    assigned[ni] = true;
                }
            }

            // Step 3) Distance-BFS from power tiles: remaining non-power buildings within threshold join the nearest grid.
            if(claimDistance > 0){
                short[] dist = tmpDist;
                int[] ownerNear = tmpOwnerNear;
                Arrays.fill(dist, (short)-1);
                Arrays.fill(ownerNear, -1);
                IntQueue q = tmpQueue;
                q.clear();

                for(int i = 0; i < tileCount; i++){
                    int g = ownerPower[i];
                    if(g >= 0){
                        dist[i] = 0;
                        ownerNear[i] = g;
                        q.addLast(i);
                    }
                }

                while(q.size > 0){
                    int idx = q.removeFirst();
                    int d = dist[idx];
                    if(d >= claimDistance) continue;
                    int x = idx % w;
                    int y = idx / w;
                    int g = ownerNear[idx];

                    //4-dir spread
                    spread(dist, ownerNear, q, w, h, x + 1, y, d + 1, g);
                    spread(dist, ownerNear, q, w, h, x - 1, y, d + 1, g);
                    spread(dist, ownerNear, q, w, h, x, y + 1, d + 1, g);
                    spread(dist, ownerNear, q, w, h, x, y - 1, d + 1, g);
                }

                for(int ni = 0; ni < nonPower.size; ni++){
                    if(assigned[ni]) continue;
                    mindustry.gen.Building b = nonPower.get(ni);
                    int cx = b.tileX();
                    int cy = b.tileY();
                    if(cx < 0 || cy < 0 || cx >= w || cy >= h) continue;
                    int cidx = cx + cy * w;
                    int d = dist[cidx];
                    int g = ownerNear[cidx];
                    if(d >= 0 && d < claimDistance && g >= 0 && g < gridTeams.length && gridTeams[g] == b.team){
                        tmpTiles.clear();
                        b.tile.getLinkedTiles(t -> tmpTiles.add(t.x + t.y * w));
                        for(int ti = 0; ti < tmpTiles.size; ti++){
                            int idx = tmpTiles.get(ti);
                            ownerClaim[idx] = g;
                            claimedBuild[idx] = true;
                        }
                        assigned[ni] = true;
                    }
                }
            }

            // Step 4) Render per-grid bounding rectangles into the overlay pixmap.
            boolean[] visited = tmpVisitedTiles;
            Arrays.fill(visited, false);
            IntQueue bfs = tmpBfs;
            bfs.clear();
            IntSeq comp = tmpComp;
            comp.clear();

            float alpha = Mathf.clamp(gridAlphaInt / 100f);

            // Precompute colors per grid once, then scan tiles once.
            int gridCount = grids.size;
            tmpBaseRgbaByGrid = ensureIntArray(tmpBaseRgbaByGrid, gridCount);
            tmpDarkRgbaByGrid = ensureIntArray(tmpDarkRgbaByGrid, gridCount);
            tmpLightRgbaByGrid = ensureIntArray(tmpLightRgbaByGrid, gridCount);
            int[] baseRgbaByGrid = tmpBaseRgbaByGrid;
            int[] darkRgbaByGrid = tmpDarkRgbaByGrid;
            int[] lightRgbaByGrid = tmpLightRgbaByGrid;
            for(int gi = 0; gi < gridCount; gi++){
                GridInfo info = grids.get(gi);
                Color base = MinimapOverlay.colorForGraph(info.colorKey, Tmp.c1);
                Color dark = Tmp.c2.set(base).mul(0.55f);
                Color light = Tmp.c3.set(base).lerp(Color.white, 0.75f);
                baseRgbaByGrid[gi] = Tmp.c4.set(base.r, base.g, base.b, alpha).rgba();
                darkRgbaByGrid[gi] = Tmp.c4.set(dark.r, dark.g, dark.b, alpha).rgba();
                lightRgbaByGrid[gi] = Tmp.c4.set(light.r, light.g, light.b, alpha * 0.45f).rgba();
            }

            for(int idx = 0; idx < tileCount; idx++){
                int gi = ownerClaim[idx];
                if(gi < 0 || visited[idx]) continue;

                //BFS component gather + bbox (for this grid)
                comp.clear();
                bfs.clear();
                bfs.addLast(idx);
                visited[idx] = true;

                int minx = w, miny = h, maxx = -1, maxy = -1;

                while(bfs.size > 0){
                    int cur = bfs.removeFirst();
                    comp.add(cur);
                    int x = cur % w;
                    int y = cur / w;
                    if(x < minx) minx = x;
                    if(y < miny) miny = y;
                    if(x > maxx) maxx = x;
                    if(y > maxy) maxy = y;

                    //neighbors
                    int n;
                    if(x + 1 < w && !visited[n = cur + 1] && ownerClaim[n] == gi){ visited[n] = true; bfs.addLast(n); }
                    if(x - 1 >= 0 && !visited[n = cur - 1] && ownerClaim[n] == gi){ visited[n] = true; bfs.addLast(n); }
                    if(y + 1 < h && !visited[n = cur + w] && ownerClaim[n] == gi){ visited[n] = true; bfs.addLast(n); }
                    if(y - 1 >= 0 && !visited[n = cur - w] && ownerClaim[n] == gi){ visited[n] = true; bfs.addLast(n); }
                }

                if(comp.isEmpty()) continue;

                //expand bbox by 1 to allow outside fill
                int bx0 = Math.max(0, minx - 1);
                int by0 = Math.max(0, miny - 1);
                int bx1 = Math.min(w - 1, maxx + 1);
                int by1 = Math.min(h - 1, maxy + 1);

                //rectangular-biased render:
                //- fill the whole (expanded) bounding box area with light color
                //- draw a dark border around the rectangle (closed shape)
                //- draw claimed building tiles with base color; border tiles with dark color
                int baseRgba = baseRgbaByGrid[gi];
                int darkRgba = darkRgbaByGrid[gi];
                int lightRgba = lightRgbaByGrid[gi];

                for(int wy = by0; wy <= by1; wy++){
                    int yOff = wy * w;
                    boolean edgeY = wy == by0 || wy == by1;
                    for(int wx = bx0; wx <= bx1; wx++){
                        int widx = wx + yOff;

                        //don't overwrite other grids' claimed buildings
                        if(claimedBuild[widx] && ownerClaim[widx] != gi) continue;

                        boolean edge = edgeY || wx == bx0 || wx == bx1;
                        boolean isBuild = claimedBuild[widx] && ownerClaim[widx] == gi;

                        //avoid messy overlaps between rectangles: only paint empty tiles if unowned yet
                        if(!isBuild && overlayOwner[widx] != -1 && overlayOwner[widx] != gi) continue;

                        int rgba;
                        if(isBuild){
                            rgba = edge ? darkRgba : baseRgba;
                        }else{
                            rgba = edge ? darkRgba : lightRgba;
                        }

                        fullOverlayPixmap.set(wx, h - 1 - wy, rgba);
                        if(!isBuild){
                            overlayOwner[widx] = gi;
                        }
                    }
                }
            }

            fullOverlayTexture.draw(fullOverlayPixmap);
        }

        private void ensureOverlayScratch(int tileCount){
            if(tileCount == lastTileCount && tmpOwnerPower != null) return;
            lastTileCount = tileCount;
            tmpOwnerPower = new int[tileCount];
            tmpOwnerClaim = new int[tileCount];
            tmpOverlayOwner = new int[tileCount];
            tmpClaimedBuild = new boolean[tileCount];
            tmpVisitedTiles = new boolean[tileCount];
            tmpDist = new short[tileCount];
            tmpOwnerNear = new int[tileCount];
            tmpAssigned = new boolean[0];
        }

        private boolean[] ensureAssigned(int size){
            if(tmpAssigned == null || tmpAssigned.length < size){
                tmpAssigned = new boolean[size];
            }else{
                Arrays.fill(tmpAssigned, 0, size, false);
            }
            return tmpAssigned;
        }

        private static int[] ensureIntArray(int[] arr, int size){
            if(arr == null || arr.length < size){
                return new int[size];
            }
            return arr;
        }

        private static int addNeighborGrid(int[] ownerPower, Team[] gridTeams, Team buildTeam, int w, int h, int x, int y, int[] out, int count){
            if(x < 0 || y < 0 || x >= w || y >= h) return count;
            int g = ownerPower[x + y * w];
            if(g < 0) return count;
            if(g >= gridTeams.length || gridTeams[g] != buildTeam) return count;
            for(int i = 0; i < count; i++){
                if(out[i] == g) return count;
            }
            out[count] = g;
            return count + 1;
        }

        private static void spread(short[] dist, int[] owner, IntQueue q, int w, int h, int x, int y, int nd, int g){
            if(x < 0 || y < 0 || x >= w || y >= h) return;
            int idx = x + y * w;
            int cd = dist[idx];
            if(cd == -1){
                dist[idx] = (short)nd;
                owner[idx] = g;
                q.addLast(idx);
            }else if(cd == nd){
                //tie-break: keep deterministic smallest grid index
                int og = owner[idx];
                if(og > g){
                    owner[idx] = g;
                }
            }
        }

    }

    private static class ReconnectHint{
        int graphA, graphB;
        float worldX, worldY;
        float endAx, endAy, endBx, endBy;
        float expiresAt;
    }

    private static class RescueCutHint{
        int graphId;
        int aPos, bPos;
        int islandStartPos;
        float rescueNetPerTick;
        float[] hull;
        float minX, minY, maxX, maxY;
        float centerX, centerY;
        float expiresAt;
        int rank;
    }

    private static class ImpactDisableHint{
        int graphId;
        int pos;
        float x, y;
        float improvePerSecond;
        float expiresAt;
        int rank;
    }

    private class SplitAlert{
        private ReconnectHint hint;
        private float textExpiresAt = 0f;

        void clear(){
            hint = null;
            textExpiresAt = 0f;
        }

        void trigger(int graphA, int graphB, float midWorldX, float midWorldY, float ax, float ay, float bx, float by){
            ReconnectHint h = new ReconnectHint();
            h.graphA = graphA;
            h.graphB = graphB;
            h.worldX = midWorldX;
            h.worldY = midWorldY;
            h.endAx = ax;
            h.endAy = ay;
            h.endBx = bx;
            h.endBy = by;
            h.expiresAt = Time.time + 60f * 8f;
            hint = h;

            textExpiresAt = Time.time + 60f * 4f;
            if(ui != null && ui.hudfrag != null){
                ui.hudfrag.showToast("[scarlet]" + Core.bundle.get("pgmm.toast.disconnected") + "[]");
            }

            //MindustryX integration: add a map mark with tile coordinates (no-op on vanilla).
            int tileX = Mathf.clamp((int)(midWorldX / tilesize), 0, world.width() - 1);
            int tileY = Mathf.clamp((int)(midWorldY / tilesize), 0, world.height() - 1);
            trySendSplitAlertMultiplayerChat(tileX, tileY);
            xMarkers.markReconnect(tileX, tileY);
        }

        void drawHudMinimapMarker(float invScale, Rect viewRect){
            if(hint == null || Time.time > hint.expiresAt) return;
            if(!viewRect.contains(hint.worldX, hint.worldY)) return;

            float r = tilesize * 2f * invScale;
            Draw.z(Drawf.text() + 1f);
            Draw.color(reconnectColor);
            Lines.stroke(Scl.scl(Core.settings.getInt(keyReconnectStroke, 2)) * invScale);
            Lines.circle(hint.worldX, hint.worldY, r);
            Lines.line(hint.endAx, hint.endAy, hint.worldX, hint.worldY);
            Lines.line(hint.endBx, hint.endBy, hint.worldX, hint.worldY);
            Draw.reset();
        }

        void drawMinimapMarkerScreen(Rect viewRect, float uiX, float uiY, float scaleX, float scaleY, float alpha){
            if(hint == null || Time.time > hint.expiresAt) return;
            if(!viewRect.contains(hint.worldX, hint.worldY)) return;

            float sx = uiX + (hint.worldX - viewRect.x) * scaleX;
            float sy = uiY + (hint.worldY - viewRect.y) * scaleY;
            float ax = uiX + (hint.endAx - viewRect.x) * scaleX;
            float ay = uiY + (hint.endAy - viewRect.y) * scaleY;
            float bx = uiX + (hint.endBx - viewRect.x) * scaleX;
            float by = uiY + (hint.endBy - viewRect.y) * scaleY;

            float r = tilesize * 2f;
            Draw.z(Drawf.text() + 1f);
            Draw.color(reconnectColor.r, reconnectColor.g, reconnectColor.b, reconnectColor.a * alpha);
            Lines.stroke(Scl.scl(Core.settings.getInt(keyReconnectStroke, 2)));
            Lines.circle(sx, sy, r);
            Lines.line(ax, ay, sx, sy);
            Lines.line(bx, by, sx, sy);
            Draw.reset();
        }

        void drawWorldMarker(Rect viewRect, float invScale){
            if(hint == null || Time.time > hint.expiresAt) return;
            if(!viewRect.contains(hint.worldX, hint.worldY)) return;

            float r = tilesize * 2.2f * invScale;
            Draw.color(reconnectColor);
            Lines.stroke(Scl.scl(Core.settings.getInt(keyReconnectStroke, 2)) * invScale);
            Lines.circle(hint.worldX, hint.worldY, r);
            Lines.line(hint.endAx, hint.endAy, hint.worldX, hint.worldY);
            Lines.line(hint.endBx, hint.endBy, hint.worldX, hint.worldY);
            Draw.reset();
        }

        void drawScreenText(){
            if(Time.time > textExpiresAt) return;
            if(state == null || state.isMenu()) return;

            String text = Core.bundle.get("pgmm.alert.disconnected");

            Font font = Fonts.outline;
            GlyphLayout layout = Pools.obtain(GlyphLayout.class, GlyphLayout::new);
            boolean ints = font.usesIntegerPositions();
            font.setUseIntegerPositions(false);

            float scale = 0.9f / Scl.scl(1f);
            font.getData().setScale(scale);

            layout.setText(font, text);
            float cx = Core.graphics.getWidth() / 2f;
            float cy = Core.graphics.getHeight() / 2f + Scl.scl(120f);

            Draw.color(0f, 0f, 0f, 0.35f);
            Fill.rect(cx, cy, layout.width + Scl.scl(14f), layout.height + Scl.scl(10f));
            Draw.color();
            font.setColor(Color.orange);
            font.draw(text, cx, cy + layout.height / 2f, 0, Align.center, false);

            font.getData().setScale(1f);
            font.setColor(Color.white);
            font.setUseIntegerPositions(ints);
            Pools.free(layout);
        }
    }

    private class RescueAlert{
        private int graphId = -1;
        private final Seq<RescueCutHint> hints = new Seq<>(false, 4, RescueCutHint.class);
        private final Seq<ImpactDisableHint> impacts = new Seq<>(false, 4, ImpactDisableHint.class);
        private float textExpiresAt = 0f;

        void clear(){
            graphId = -1;
            hints.clear();
            impacts.clear();
            textExpiresAt = 0f;
        }

        void trigger(int graphId, Seq<RescueCutHint> newHints, Seq<ImpactDisableHint> newImpacts, boolean showToast){
            this.graphId = graphId;
            hints.clear();
            for(int i = 0; i < newHints.size; i++){
                RescueCutHint h = newHints.get(i);
                h.graphId = graphId;
                h.expiresAt = Time.time + 60f * 6f;
                h.rank = i + 1;
                hints.add(h);
            }
            impacts.clear();
            for(int i = 0; i < newImpacts.size; i++){
                ImpactDisableHint h = newImpacts.get(i);
                h.graphId = graphId;
                h.expiresAt = Time.time + 60f * 6f;
                h.rank = i + 1;
                impacts.add(h);
            }

            textExpiresAt = Time.time + 60f * 4f;
            if(showToast && ui != null && ui.hudfrag != null){
                String key = impacts.isEmpty() ? "pgmm.toast.rescue" : "pgmm.toast.rescueImpact";
                String fallback = impacts.isEmpty()
                    ? "Power deficit: rescue hints available"
                    : "Power deficit: suggested reactors are marked";
                ui.hudfrag.showToast("[scarlet]" + Core.bundle.get(key, fallback) + "[]");
            }

            //MindustryX integration: add a map mark (no-op on vanilla).
            if(showToast){
                int tileX = -1, tileY = -1;
                if(!impacts.isEmpty()){
                    ImpactDisableHint h = impacts.first();
                    tileX = Point2.x(h.pos);
                    tileY = Point2.y(h.pos);
                }else if(!hints.isEmpty()){
                    RescueCutHint h = hints.first();
                    tileX = Mathf.clamp((int)(h.centerX / tilesize), 0, world.width() - 1);
                    tileY = Mathf.clamp((int)(h.centerY / tilesize), 0, world.height() - 1);
                }
                if(tileX >= 0 && tileY >= 0){
                    xMarkers.markRescue(tileX, tileY);
                }
            }
        }

        int getGraphId(){
            return graphId;
        }

        void drawHudMinimapMarker(float invScale, Rect viewRect, Color color){
            float now = Time.time;
            if(hints.isEmpty() && impacts.isEmpty()) return;

            Draw.z(Drawf.text() + 1f);

            Font font = Fonts.outline;
            GlyphLayout layout = Pools.obtain(GlyphLayout.class, GlyphLayout::new);
            boolean ints = font.usesIntegerPositions();
            font.setUseIntegerPositions(false);
            float baseFontScale = (1f / 1.25f) / Math.max(0.0001f, Scl.scl(1f));
            font.getData().setScale(baseFontScale * invScale * 0.95f);

            for(int i = 0; i < hints.size; i++){
                RescueCutHint h = hints.get(i);
                if(now > h.expiresAt) continue;
                if(h.hull == null || h.hull.length < 6) continue;
                if(!rectOverlaps(viewRect, h.minX, h.minY, h.maxX - h.minX, h.maxY - h.minY)) continue;

                Draw.color(color);
                Lines.stroke(Scl.scl(Core.settings.getInt(keyRescueStroke, 2)) * invScale);
                drawPolyWorld(h.hull);

                float netPerSecond = h.rescueNetPerTick * 60f;
                String text = "#" + h.rank + " " + (netPerSecond >= 0f ? "+" : "") + UI.formatAmount((long)netPerSecond);
                layout.setText(font, text);
                Draw.color(0f, 0f, 0f, 0.35f);
                Fill.rect(h.centerX, h.centerY, layout.width + 6f * invScale, layout.height + 4f * invScale);
                Draw.color(color);
                font.draw(text, h.centerX, h.centerY + layout.height / 2f, 0, Align.center, false);
            }

            for(int i = 0; i < impacts.size; i++){
                ImpactDisableHint h = impacts.get(i);
                if(now > h.expiresAt) continue;
                if(!viewRect.contains(h.x, h.y)) continue;

                Building b = world.build(h.pos);
                float size = b != null ? (b.block.size * tilesize / 2f + 1f) : tilesize * 2f;

                Draw.color(color);
                Lines.stroke(Scl.scl(Core.settings.getInt(keyRescueStroke, 2)) * invScale);
                Lines.square(h.x, h.y, size * invScale, 45f);

                String text = "!" + h.rank;
                layout.setText(font, text);
                Draw.color(0f, 0f, 0f, 0.35f);
                Fill.rect(h.x, h.y, layout.width + 6f * invScale, layout.height + 4f * invScale);
                Draw.color(color);
                font.draw(text, h.x, h.y + layout.height / 2f, 0, Align.center, false);
            }

            Draw.reset();
            font.getData().setScale(1f);
            font.setColor(Color.white);
            font.setUseIntegerPositions(ints);
            Pools.free(layout);
        }

        void drawMinimapMarkerScreen(Rect viewRect, float uiX, float uiY, float scaleX, float scaleY, float alpha, Color color){
            float now = Time.time;
            if(hints.isEmpty() && impacts.isEmpty()) return;

            Draw.z(Drawf.text() + 1f);
            Draw.color(color.r, color.g, color.b, color.a * alpha);
            Lines.stroke(Scl.scl(Core.settings.getInt(keyRescueStroke, 2)));

            for(int i = 0; i < hints.size; i++){
                RescueCutHint h = hints.get(i);
                if(now > h.expiresAt) continue;
                if(h.hull == null || h.hull.length < 6) continue;
                if(!rectOverlaps(viewRect, h.minX, h.minY, h.maxX - h.minX, h.maxY - h.minY)) continue;
                drawPolyScreen(h.hull, viewRect, uiX, uiY, scaleX, scaleY);
            }

            float r = tilesize * 2f;
            for(int i = 0; i < impacts.size; i++){
                ImpactDisableHint h = impacts.get(i);
                if(now > h.expiresAt) continue;
                if(!viewRect.contains(h.x, h.y)) continue;

                float sx = uiX + (h.x - viewRect.x) * scaleX;
                float sy = uiY + (h.y - viewRect.y) * scaleY;
                Lines.square(sx, sy, r, 45f);
            }

            Draw.reset();
        }

        void drawWorldMarker(Rect viewRect, float invScale, Color color){
            float now = Time.time;
            if(hints.isEmpty() && impacts.isEmpty()) return;

            Draw.z(mindustry.graphics.Layer.overlayUI - 1f);

            Font font = Fonts.outline;
            GlyphLayout layout = Pools.obtain(GlyphLayout.class, GlyphLayout::new);
            boolean ints = font.usesIntegerPositions();
            font.setUseIntegerPositions(false);
            float baseFontScale = (1f / 1.25f) / Math.max(0.0001f, Scl.scl(1f));
            font.getData().setScale(baseFontScale * invScale);

            for(int i = 0; i < hints.size; i++){
                RescueCutHint h = hints.get(i);
                if(now > h.expiresAt) continue;
                if(h.hull == null || h.hull.length < 6) continue;
                if(!rectOverlaps(viewRect, h.minX, h.minY, h.maxX - h.minX, h.maxY - h.minY)) continue;
                Draw.color(color);
                Lines.stroke(Scl.scl(Core.settings.getInt(keyRescueStroke, 2)) * invScale);
                drawPolyWorld(h.hull);

                float netPerSecond = h.rescueNetPerTick * 60f;
                String text = "#" + h.rank + " " + (netPerSecond >= 0f ? "+" : "") + UI.formatAmount((long)netPerSecond);
                layout.setText(font, text);
                Draw.color(0f, 0f, 0f, 0.35f);
                Fill.rect(h.centerX, h.centerY, layout.width + 6f * invScale, layout.height + 4f * invScale);
                Draw.color(color);
                font.draw(text, h.centerX, h.centerY + layout.height / 2f, 0, Align.center, false);
            }

            for(int i = 0; i < impacts.size; i++){
                ImpactDisableHint h = impacts.get(i);
                if(now > h.expiresAt) continue;
                if(!viewRect.contains(h.x, h.y)) continue;

                Building b = world.build(h.pos);
                float size = b != null ? (b.block.size * tilesize / 2f + 1f) : tilesize * 2f;
                Draw.color(color);
                Lines.stroke(Scl.scl(Core.settings.getInt(keyRescueStroke, 2)) * invScale);
                Lines.square(h.x, h.y, size * invScale, 45f);

                String text = "!" + h.rank;
                layout.setText(font, text);
                Draw.color(0f, 0f, 0f, 0.35f);
                Fill.rect(h.x, h.y, layout.width + 6f * invScale, layout.height + 4f * invScale);
                Draw.color(color);
                font.draw(text, h.x, h.y + layout.height / 2f, 0, Align.center, false);
            }

            Draw.reset();

            font.getData().setScale(1f);
            font.setColor(Color.white);
            font.setUseIntegerPositions(ints);
            Pools.free(layout);
        }

        void drawScreenText(){
            if(Time.time > textExpiresAt) return;
            if(state == null || state.isMenu()) return;
            if(hints.isEmpty() && impacts.isEmpty()) return;

            String key = impacts.isEmpty() ? "pgmm.rescue.text" : "pgmm.rescue.textImpact";
            String fallback = impacts.isEmpty()
                ? "Power deficit: suggested positive islands are outlined."
                : "Power deficit: suggested Impact Reactors to disable are marked.";
            String text = Core.bundle.get(key, fallback);

            Font font = Fonts.outline;
            GlyphLayout layout = Pools.obtain(GlyphLayout.class, GlyphLayout::new);
            boolean ints = font.usesIntegerPositions();
            font.setUseIntegerPositions(false);

            float scale = 0.9f / Scl.scl(1f);
            font.getData().setScale(scale);

            layout.setText(font, text);
            float cx = Core.graphics.getWidth() / 2f;
            float cy = Core.graphics.getHeight() / 2f + Scl.scl(150f);

            Draw.color(0f, 0f, 0f, 0.35f);
            Fill.rect(cx, cy, layout.width + Scl.scl(14f), layout.height + Scl.scl(10f));
            Draw.color();
            font.setColor(Color.scarlet);
            font.draw(text, cx, cy + layout.height / 2f, 0, Align.center, false);

            font.getData().setScale(1f);
            font.setColor(Color.white);
            font.setUseIntegerPositions(ints);
            Pools.free(layout);
        }

        private boolean rectOverlaps(Rect view, float x, float y, float w, float h){
            return view.x < x + w && view.x + view.width > x && view.y < y + h && view.y + view.height > y;
        }

        private void drawPolyWorld(float[] hull){
            int n = hull.length / 2;
            for(int i = 0; i < n; i++){
                int j = (i + 1) % n;
                Lines.line(hull[i * 2], hull[i * 2 + 1], hull[j * 2], hull[j * 2 + 1]);
            }
        }

        private void drawPolyScreen(float[] hull, Rect viewRect, float uiX, float uiY, float scaleX, float scaleY){
            int n = hull.length / 2;
            for(int i = 0; i < n; i++){
                int j = (i + 1) % n;

                float sx0 = uiX + (hull[i * 2] - viewRect.x) * scaleX;
                float sy0 = uiY + (hull[i * 2 + 1] - viewRect.y) * scaleY;
                float sx1 = uiX + (hull[j * 2] - viewRect.x) * scaleX;
                float sy1 = uiY + (hull[j * 2 + 1] - viewRect.y) * scaleY;

                Lines.line(sx0, sy0, sx1, sy1);
            }
        }
    }

    private class PowerTableOverlay extends Table{
        private float nextRebuildAt = 0f;
        private Element minimapRef;
        private TextureRegionDrawable whiteDrawable;
        //true = this table is inside MindustryX OverlayUI.Window; false = attached directly to hudGroup and anchored near minimap.
        private boolean hostedByOverlayUI = false;

        private final Color cId = Color.valueOf("9cdcfe"); //VS Code: variable
        private final Color cKey = Color.valueOf("d4d4d4"); //VS Code: default text
        private final Color cPos = Color.valueOf("4ec9b0"); //VS Code: class/green
        private final Color cNeg = Color.valueOf("f44747"); //VS Code: red

        PowerTableOverlay(){
            touchable = Touchable.disabled;
            left().top();
            margin(6f);
        }

        void setHostedByOverlayUI(boolean hosted){
            hostedByOverlayUI = hosted;
            touchable = hostedByOverlayUI ? Touchable.childrenOnly : Touchable.disabled;
        }

        @Override
        public float getMinWidth(){
            return hostedByOverlayUI ? 0f : super.getMinWidth();
        }

        @Override
        public float getMinHeight(){
            return hostedByOverlayUI ? 0f : super.getMinHeight();
        }

        @Override
        public float getPrefWidth(){
            if(!hostedByOverlayUI) return super.getPrefWidth();
            float pref = super.getPrefWidth();
            float w = parent != null ? parent.getWidth() : width;
            return w > 0.001f ? Math.max(pref, w) : pref;
        }

        @Override
        public float getPrefHeight(){
            if(!hostedByOverlayUI) return super.getPrefHeight();
            float pref = super.getPrefHeight();
            float h = parent != null ? parent.getHeight() : height;
            return h > 0.001f ? Math.max(pref, h) : pref;
        }

        @Override
        public void act(float delta){
            super.act(delta);

            boolean enabled = Core.settings.getBool(keyEnabled, true) && Core.settings.getBool(keyPowerTableEnabled, false);
            enabled &= state != null && state.isGame() && world != null && !world.isGenerating() && player != null;
            if(!hostedByOverlayUI){
                visible = enabled;
            }
            if(!enabled) return;

            //rebuild periodically (avoid heavy UI churn)
            if(Time.time >= nextRebuildAt){
                nextRebuildAt = Time.time + 30f;
                rebuild();
            }

            //OverlayUI owns window positioning; don't anchor to minimap in that mode.
            if(hostedByOverlayUI) return;

            //track minimap position to anchor this table on the left side
            if(minimapRef == null || minimapRef.getScene() == null){
                try{
                    minimapRef = ui != null && ui.hudGroup != null ? ui.hudGroup.find("minimap") : null;
                }catch(Throwable ignored){
                    minimapRef = null;
                }
            }

            pack();

            float pad = Scl.scl(4f);
            float maxY = Core.graphics.getHeight() - pad;

            if(minimapRef != null){
                float x = minimapRef.x;
                float y = minimapRef.y + minimapRef.getHeight() + pad;
                if(y + height > maxY){
                    setPosition(x, maxY, Align.topLeft);
                }else{
                    setPosition(x, y, Align.bottomLeft);
                }
            }else{
                setPosition(pad, maxY, Align.topLeft);
            }
        }

        @Override
        public void draw(){
            if(!visible && !hostedByOverlayUI) return;
            float alpha = Mathf.clamp(Core.settings.getInt(keyPowerTableBgAlpha, 70) / 100f) * parentAlpha;
            if(alpha > 0.001f){
                Draw.color(0f, 0f, 0f, alpha);
                Fill.rect(x + width / 2f, y + height / 2f, width, height);
                Draw.color();
            }
            super.draw();
        }

        private void rebuild(){
            clearChildren();
            defaults().left();

            cache.updateBasic();

            int threshold = Core.settings.getInt(keyPowerTableThreshold, 10000);
            int k = Math.max(0, Core.settings.getInt(keyRescueClearWindowSeconds, 8));

            String title = Core.bundle.get("pgmm.powertable.title", "Power");
            add("[accent]" + title + "[]").left().row();
            add("in>=" + UI.formatAmount(threshold) + "/s  min(" + k + "s)").color(cKey).padTop(2f).row();

            if(cache.grids.isEmpty()){
                add(Core.bundle.get("pgmm.powertable.empty", "No power grids.")).color(cKey).padTop(4f).row();
                return;
            }

            if(whiteDrawable == null && Core.atlas != null){
                whiteDrawable = new TextureRegionDrawable(Core.atlas.white());
            }

            Team playerTeam = player == null ? null : player.team();

            int shown = 0;
            for(int i = 0; i < cache.grids.size; i++){
                GridInfo info = cache.grids.get(i);
                PowerGraph graph = info == null ? null : info.graph;
                if(info == null || playerTeam == null || info.team != playerTeam) continue;
                if(graph == null) continue;
                if(!graph.hasPowerBalanceSamples()) continue;

                int gid = graph.getID();
                float powerIn = graph.getLastScaledPowerIn() * 60f;
                if(powerIn < threshold) continue;

                float now = graph.getPowerBalance() * 60f;
                float min = rescueAdvisor.minBalance(gid);

                Color gridColor = MinimapOverlay.colorForGraph(gid, Tmp.c1);

                table(row -> {
                    row.left();
                    row.defaults().padRight(6f);

                    row.table(left -> {
                        left.setClip(true);
                        left.left();
                        left.defaults().left().padRight(6f);

                        left.image(whiteDrawable).size(6f).color(gridColor);

                        TextButton idButton = left.button("#" + gid, Styles.cleart, () -> {
                            //Only make this interactive when hosted by OverlayUI, so the HUD-anchored fallback stays click-through.
                            if(hostedByOverlayUI){
                                focusGridCenter(info);
                            }
                        }).padLeft(4f).padRight(8f).get();
                        idButton.getLabel().setColor(cId);
                        idButton.getLabel().setWrap(false);
                        idButton.getLabel().setEllipsis(true);

                        left.add("in").color(cKey);
                        left.add(UI.formatAmount((long)powerIn)).color(cKey);
                    }).growX().fillX().left();

                    row.table(right -> {
                        right.right();
                        right.defaults().right().padLeft(6f);

                        right.add("now").color(cKey);
                        Label nowLabel = right.add((now >= 0f ? "+" : "") + UI.formatAmount((long)now)).color(now >= 0f ? cPos : cNeg).right().get();
                        nowLabel.setAlignment(Align.right);
                        nowLabel.setWrap(false);
                        nowLabel.setEllipsis(true);

                        right.add("min").color(cKey);
                        Label minLabel = right.add((min >= 0f ? "+" : "") + UI.formatAmount((long)min)).color(min >= 0f ? cPos : cNeg).right().get();
                        minLabel.setAlignment(Align.right);
                        minLabel.setWrap(false);
                        minLabel.setEllipsis(true);
                    }).right();
                }).growX().fillX().padTop(2f).row();

                shown++;
                if(shown >= 12) break;
            }

            if(shown == 0){
                add(Core.bundle.get("pgmm.powertable.none", "No grids above threshold.")).color(cKey).padTop(4f).row();
            }
        }
    }

    /** Optional integration with MindustryX OverlayUI. Uses reflection so vanilla builds won't crash. */
    private static class MindustryXOverlayUI{
        private boolean initialized = false;
        private boolean installed = false;
        private Object instance;
        private Method registerWindow;
        private Method setAvailability;
        private Method getData;
        private Method setEnabled;
        private Method setPinned;
        private Method setResizable;
        private Method setAutoHeight;

        boolean isInstalled(){
            if(initialized) return installed;
            initialized = true;
            try{
                //Mod ID check; if the user doesn't have MindustryX, we must not touch its classes.
                installed = mindustry.Vars.mods != null && mindustry.Vars.mods.locateMod("mindustryx") != null;
            }catch(Throwable ignored){
                installed = false;
            }
            if(!installed) return false;

            try{
                //Kotlin `object OverlayUI` => Java sees `OverlayUI.INSTANCE`.
                Class<?> c = Class.forName("mindustryX.features.ui.OverlayUI");
                instance = c.getField("INSTANCE").get(null);
                registerWindow = c.getMethod("registerWindow", String.class, Table.class);
            }catch(Throwable t){
                installed = false;
                Log.err("PGMM: MindustryX detected but OverlayUI reflection init failed.", t);
                return false;
            }
            return true;
        }

        Object registerWindow(String name, Table table, Prov<Boolean> availability){
            if(!isInstalled()) return null;
            try{
                //Returns an OverlayUI.Window instance (we keep it as Object to avoid a hard dependency).
                Object window = registerWindow.invoke(instance, name, table);
                tryInitWindowAccessors(window);
                if(window != null && availability != null && setAvailability != null){
                    setAvailability.invoke(window, availability);
                }
                return window;
            }catch(Throwable t){
                Log.err("PGMM: OverlayUI.registerWindow failed.", t);
                return null;
            }
        }

        void tryConfigureWindow(Object window, boolean autoHeight, boolean resizable){
            if(window == null) return;
            try{
                tryInitWindowAccessors(window);
                if(setAutoHeight != null) setAutoHeight.invoke(window, autoHeight);
                if(setResizable != null) setResizable.invoke(window, resizable);
            }catch(Throwable ignored){
            }
        }

        void setEnabledAndPinned(Object window, boolean enabled, boolean pinned){
            if(window == null) return;
            try{
                tryInitWindowAccessors(window);
                if(getData == null) return;
                Object data = getData.invoke(window);
                if(data == null) return;
                //OverlayUI.Window has a SettingsV2-backed `data` object: toggling it persists window state.
                if(setEnabled != null) setEnabled.invoke(data, enabled);
                if(pinned && setPinned != null) setPinned.invoke(data, true);
            }catch(Throwable ignored){
            }
        }

        private void tryInitWindowAccessors(Object window){
            if(window == null) return;
            if(getData != null || setAvailability != null) return;
            try{
                Class<?> wc = window.getClass();
                try{
                    //Kotlin property `var availability` => Java setter `setAvailability(Prov)`.
                    setAvailability = wc.getMethod("setAvailability", Prov.class);
                }catch(Throwable ignored){
                    setAvailability = null;
                }
                try{
                    setResizable = wc.getMethod("setResizable", boolean.class);
                }catch(Throwable ignored){
                    setResizable = null;
                }
                try{
                    setAutoHeight = wc.getMethod("setAutoHeight", boolean.class);
                }catch(Throwable ignored){
                    setAutoHeight = null;
                }
                getData = wc.getMethod("getData");

                Object data = getData.invoke(window);
                if(data != null){
                    Class<?> dc = data.getClass();
                    try{
                        //Kotlin property `var enabled` => Java setter `setEnabled(boolean)`.
                        setEnabled = dc.getMethod("setEnabled", boolean.class);
                    }catch(Throwable ignored){
                        setEnabled = null;
                    }
                    try{
                        //Kotlin property `var pinned` => Java setter `setPinned(boolean)`.
                        setPinned = dc.getMethod("setPinned", boolean.class);
                    }catch(Throwable ignored){
                        setPinned = null;
                    }
                }
            }catch(Throwable ignored){
            }
        }
    }

    private class SplitWatcher{
        private static final float scanInterval = 30f;
        private static final float cooldown = 60f * 10f;

        private final IntIntMap lastGraphByBuildPos = new IntIntMap();
        //per-second (UI unit) power-in of last scan
        private final IntMap<Float> lastGraphPowerIn = new IntMap<>();
        private final IntMap<Float> lastSplitTime = new IntMap<>();
        private final IntMap<PendingSplit> pendingSplits = new IntMap<>();

        //reused temp structures to reduce GC pressure
        private final IntMap<IntSet> prevToNew = new IntMap<>();
        private final Seq<IntSet> prevToNewSetPool = new Seq<>();
        private int prevToNewSetPoolUsed = 0;
        private final IntMap<Float> currentPowerIn = new IntMap<>();
        private final IntMap<Float> currentBalance = new IntMap<>();
        private final IntSet currentIds = new IntSet();
        private final IntSet tmpResultIds = new IntSet();
        private final IntSeq tmpToRemove = new IntSeq();

        private float nextScan = 0f;

        void reset(){
            lastGraphByBuildPos.clear();
            lastGraphPowerIn.clear();
            lastSplitTime.clear();
            pendingSplits.clear();
            nextScan = 0f;
        }

        void update(){
            if(!Core.settings.getBool(keySplitAlertEnabled, true)){
                if(!pendingSplits.isEmpty()){
                    reset();
                }
                alert.clear();
                return;
            }
            if(Time.time < nextScan) return;
            nextScan = Time.time + scanInterval;

            if(!state.isGame() || world == null || world.isGenerating() || player == null){
                lastGraphByBuildPos.clear();
                lastGraphPowerIn.clear();
                lastSplitTime.clear();
                pendingSplits.clear();
                alert.clear();
                return;
            }

            int threshold = Core.settings.getInt(keySplitAlertThreshold, 10000);
            int configuredNegativeThreshold = Core.settings.getInt(keySplitAlertNegativeThreshold, 0);
            int negativeThreshold = Math.min(configuredNegativeThreshold, 0);
            if(configuredNegativeThreshold != negativeThreshold){
                Core.settings.put(keySplitAlertNegativeThreshold, negativeThreshold);
            }
            int windowSeconds = Core.settings.getInt(keySplitAlertWindowSeconds, 4);
            float windowFrames = Math.max(1f, windowSeconds) * 60f;

            //clear temp maps; reuse IntSet instances via pool
            prevToNew.clear();
            prevToNewSetPoolUsed = 0;
            currentPowerIn.clear();
            currentBalance.clear();
            currentIds.clear();

            //scan all player buildings with power modules
            for(int i = 0; i < mindustry.gen.Groups.build.size(); i++){
                mindustry.gen.Building b = mindustry.gen.Groups.build.index(i);
                if(b == null || b.team != player.team() || b.power == null || b.power.graph == null) continue;

                int pos = b.pos();
                int newId = b.power.graph.getID();
                int prevId = lastGraphByBuildPos.get(pos, Integer.MIN_VALUE);

                lastGraphByBuildPos.put(pos, newId);

                //build stats
                currentPowerIn.put(newId, b.power.graph.getLastScaledPowerIn() * 60f);
                currentBalance.put(newId, b.power.graph.getPowerBalance() * 60f);
                currentIds.add(newId);

                if(prevId != Integer.MIN_VALUE && prevId != newId){
                    IntSet set = prevToNew.get(prevId);
                    if(set == null){
                        set = prevToNewSetPoolUsed < prevToNewSetPool.size ? prevToNewSetPool.get(prevToNewSetPoolUsed) : new IntSet();
                        if(prevToNewSetPoolUsed >= prevToNewSetPool.size) prevToNewSetPool.add(set);
                        prevToNewSetPoolUsed++;
                        set.clear();
                        prevToNew.put(prevId, set);
                    }
                    set.add(newId);
                }
            }

            //register split events (including the case where one side keeps the old graph ID)
            for(IntMap.Entry<IntSet> e : prevToNew){
                int prevId = e.key;
                IntSet changedIds = e.value;

                //result set = changed graph IDs + (prevId if it still exists after the split)
                tmpResultIds.clear();
                if(changedIds != null) changedIds.each(tmpResultIds::add);
                if(currentIds.contains(prevId)){
                    tmpResultIds.add(prevId);
                }
                if(tmpResultIds.size < 2) continue;

                float prevPowerIn = lastGraphPowerIn.get(prevId, 0f);
                if(prevPowerIn < threshold) continue;

                float lastTime = lastSplitTime.get(prevId, -999999f);
                if(Time.time - lastTime < cooldown) continue;

                PendingSplit pending = pendingSplits.get(prevId);
                if(pending == null || Time.time > pending.expiresAt){
                    pending = new PendingSplit();
                    pending.prevId = prevId;
                    pending.createdAt = Time.time;
                    pending.expiresAt = Time.time + windowFrames;
                    pending.resultIds.clear();
                    pending.belowSince.clear();
                    tmpResultIds.each(pending.resultIds::add);
                    pendingSplits.put(prevId, pending);
                }else{
                    //keep original window; just widen the set of resulting IDs
                    tmpResultIds.each(pending.resultIds::add);
                }
            }

            //evaluate pending split windows; if any resulting grid goes negative within the window, fire alert
            tmpToRemove.clear();
            for(IntMap.Entry<PendingSplit> e : pendingSplits){
                PendingSplit pending = e.value;
                if(pending == null) continue;

                boolean expired = Time.time > pending.expiresAt + scanInterval;

                final int[] negativeId = {-1};
                final float[] mostNegative = {Float.POSITIVE_INFINITY};

                pending.resultIds.each(id -> {
                    if(!currentIds.contains(id)){
                        pending.belowSince.remove(id);
                        return;
                    }
                    float bal = currentBalance.get(id, 0f);
                    if(bal <= negativeThreshold){
                        float belowAt = pending.belowSince.get(id, -1f);
                        if(belowAt < 0f){
                            pending.belowSince.put(id, Time.time);
                            belowAt = Time.time;
                        }
                        if(Time.time - belowAt >= windowFrames && bal < mostNegative[0]){
                            mostNegative[0] = bal;
                            negativeId[0] = id;
                        }
                    }else{
                        pending.belowSince.remove(id);
                    }
                });

                if(negativeId[0] == -1){
                    if(expired){
                        tmpToRemove.add(pending.prevId);
                    }
                    continue;
                }

                //pick a "stable" other side to reconnect to: highest power-in per second
                final int[] bestOther = {-1};
                final float[] bestPower = {Float.NEGATIVE_INFINITY};
                pending.resultIds.each(id -> {
                    if(id == negativeId[0]) return;
                    float pin = currentPowerIn.get(id, 0f);
                    if(pin > bestPower[0]){
                        bestPower[0] = pin;
                        bestOther[0] = id;
                    }
                });
                if(bestOther[0] == -1) continue;

                int aId = negativeId[0];
                int bId = bestOther[0];
                if(currentPowerIn.get(aId, 0f) >= currentPowerIn.get(bId, 0f)) continue;

                ReconnectResult reconnect = findReconnectPoint(aId, bId);
                if(reconnect == null){
                    //fallback: try any other candidate
                    final ReconnectResult[] found = {null};
                    final int[] foundB = {bId};
                    pending.resultIds.each(id -> {
                        if(found[0] != null) return;
                        if(id == aId) return;
                        if(currentPowerIn.get(aId, 0f) >= currentPowerIn.get(id, 0f)) return;
                        ReconnectResult r = findReconnectPoint(aId, id);
                        if(r != null){
                            found[0] = r;
                            foundB[0] = id;
                        }
                    });
                    reconnect = found[0];
                    bId = foundB[0];
                }

                if(reconnect != null){
                    lastSplitTime.put(pending.prevId, Time.time);
                    alert.trigger(aId, bId, reconnect.midX, reconnect.midY, reconnect.ax, reconnect.ay, reconnect.bx, reconnect.by);
                    tmpToRemove.add(pending.prevId);
                }
            }
            for(int i = 0; i < tmpToRemove.size; i++){
                pendingSplits.remove(tmpToRemove.get(i));
            }

            //store last stats
            lastGraphPowerIn.clear();
            for(IntMap.Entry<Float> e : currentPowerIn){
                lastGraphPowerIn.put(e.key, e.value);
            }
        }

        private class PendingSplit{
            int prevId;
            float createdAt;
            float expiresAt;
            IntSet resultIds = new IntSet();
            IntMap<Float> belowSince = new IntMap<>();
        }

        private ReconnectResult findReconnectPoint(int graphA, int graphB){
            float range = ((PowerNode)Blocks.powerNodeLarge).laserRange * tilesize;

            //collect tiles for each graph (power tiles only; faster and reflects actual graph connectivity)
            IntSeq tilesA = new IntSeq();
            IntSet tilesB = new IntSet();

            int w = world.width();
            int h = world.height();

            for(int i = 0; i < mindustry.gen.Groups.build.size(); i++){
                mindustry.gen.Building b = mindustry.gen.Groups.build.index(i);
                if(b == null || b.team != player.team() || b.power == null || b.power.graph == null || b.tile == null) continue;
                int gid = b.power.graph.getID();
                if(gid != graphA && gid != graphB) continue;

                if(gid == graphA){
                    b.tile.getLinkedTiles(t -> tilesA.add(t.pos()));
                }else{
                    b.tile.getLinkedTiles(t -> tilesB.add(t.pos()));
                }
            }

            if(tilesA.isEmpty() || tilesB.isEmpty()) return null;

            //compute bbox to limit BFS
            int minx = w, miny = h, maxx = -1, maxy = -1;
            for(int i = 0; i < tilesA.size; i++){
                int pos = tilesA.get(i);
                int x = Point2.x(pos);
                int y = Point2.y(pos);
                if(x < minx) minx = x;
                if(y < miny) miny = y;
                if(x > maxx) maxx = x;
                if(y > maxy) maxy = y;
            }
            IntSeq tilesBSeq = new IntSeq();
            tilesB.each(tilesBSeq::add);
            for(int i = 0; i < tilesBSeq.size; i++){
                int pos = tilesBSeq.get(i);
                int x = Point2.x(pos);
                int y = Point2.y(pos);
                if(x < minx) minx = x;
                if(y < miny) miny = y;
                if(x > maxx) maxx = x;
                if(y > maxy) maxy = y;
            }

            int margin = (int)Math.ceil((range / tilesize) * 2f) + 2;
            int bx0 = Math.max(0, minx - margin);
            int by0 = Math.max(0, miny - margin);
            int bx1 = Math.min(w - 1, maxx + margin);
            int by1 = Math.min(h - 1, maxy + margin);
            int bw = bx1 - bx0 + 1;
            int bh = by1 - by0 + 1;
            int area = bw * bh;

            //visited + predecessor within bbox coordinates
            boolean[] visited = new boolean[area];
            int[] prev = new int[area];
            Arrays.fill(prev, -1);
            IntQueue q = new IntQueue();

            //seed queue with all A tiles
            for(int i = 0; i < tilesA.size; i++){
                int pos = tilesA.get(i);
                int x = Point2.x(pos);
                int y = Point2.y(pos);
                if(x < bx0 || y < by0 || x > bx1 || y > by1) continue;
                int lx = x - bx0, ly = y - by0;
                int li = lx + ly * bw;
                if(!visited[li]){
                    visited[li] = true;
                    q.addLast(li);
                }
            }

            int hitLocal = -1;
            int hitWorldPos = -1;

            while(q.size > 0){
                int li = q.removeFirst();
                int lx = li % bw;
                int ly = li / bw;
                int wx = bx0 + lx;
                int wy = by0 + ly;
                int wpos = Point2.pack(wx, wy);

                if(tilesB.contains(wpos)){
                    hitLocal = li;
                    hitWorldPos = wpos;
                    break;
                }

                //4-dir
                expandBfs(li, lx + 1, ly, bw, bh, visited, prev, q);
                expandBfs(li, lx - 1, ly, bw, bh, visited, prev, q);
                expandBfs(li, lx, ly + 1, bw, bh, visited, prev, q);
                expandBfs(li, lx, ly - 1, bw, bh, visited, prev, q);
            }

            if(hitLocal == -1 || hitWorldPos == -1) return null;

            //trace back to nearest A tile in bbox
            int cur = hitLocal;
            int last = cur;
            while(prev[cur] != -1){
                last = cur;
                cur = prev[cur];
            }

            //cur is some seed tile; translate to world
            int axTile = bx0 + (cur % bw);
            int ayTile = by0 + (cur / bw);
            int bxTile = Point2.x(hitWorldPos);
            int byTile = Point2.y(hitWorldPos);

            float ax = (axTile + 0.5f) * tilesize;
            float ay = (ayTile + 0.5f) * tilesize;
            float bx = (bxTile + 0.5f) * tilesize;
            float by = (byTile + 0.5f) * tilesize;

            float dist = Mathf.dst(ax, ay, bx, by);
            if(dist > range * 2f){
                //still return midpoint, but it may require multiple nodes
            }

            float midX = (ax + bx) / 2f;
            float midY = (ay + by) / 2f;

            //snap midpoint to nearest tile center
            int mx = Mathf.clamp((int)(midX / tilesize), 0, w - 1);
            int my = Mathf.clamp((int)(midY / tilesize), 0, h - 1);
            float snapX = (mx + 0.5f) * tilesize;
            float snapY = (my + 0.5f) * tilesize;

            ReconnectResult res = new ReconnectResult();
            res.ax = ax; res.ay = ay; res.bx = bx; res.by = by;
            res.midX = snapX; res.midY = snapY;
            return res;
        }

        private int expandBfs(int from, int nx, int ny, int bw, int bh, boolean[] visited, int[] prev, IntQueue q){
            if(nx < 0 || ny < 0 || nx >= bw || ny >= bh) return -1;
            int ni = nx + ny * bw;
            if(visited[ni]) return -1;
            visited[ni] = true;
            prev[ni] = from;
            q.addLast(ni);
            return -1;
        }
    }

    private class RescueAdvisor{
        private final IntMap<Float> negativeSince = new IntMap<>();
        private final IntMap<BalanceWindow> balanceWindows = new IntMap<>();
        private final IntMap<Float> minBalanceById = new IntMap<>();
        private final IntSet currentIds = new IntSet();
        private final IntSeq tmpToRemove = new IntSeq();
        private int historySamples = -1;
        private float nextScan = 0f;
        private int lastGraphId = -1;
        private float lastToast = 0f;

        //reused temp structures to reduce GC pressure
        private final Seq<Building> tmpConns = new Seq<>(false, 8, Building.class);
        private final IntSet tmpVisited = new IntSet();
        private final IntQueue tmpQueue = new IntQueue();
        private final Seq<RescueCutHint> tmpHints = new Seq<>(false, 8, RescueCutHint.class);
        private final Seq<RescueCutHint> tmpBest = new Seq<>(false, 8, RescueCutHint.class);
        private final Seq<ImpactDisableHint> tmpImpactHints = new Seq<>(false, 8, ImpactDisableHint.class);
        private final IntSeq tmpEdgeA = new IntSeq();
        private final IntSeq tmpEdgeB = new IntSeq();
        private final IntSet tmpHullTiles = new IntSet();
        private final IntSeq tmpHullTileList = new IntSeq();
        private final LongSeq tmpHullCorners = new LongSeq();
        private final LongSeq tmpHullUnique = new LongSeq();
        private final LongSeq tmpHull = new LongSeq();

        void reset(){
            negativeSince.clear();
            balanceWindows.clear();
            minBalanceById.clear();
            currentIds.clear();
            tmpToRemove.clear();
            historySamples = -1;
            nextScan = 0f;
            lastGraphId = -1;
            lastToast = 0f;
        }

        void update(){
            if(!Core.settings.getBool(keyRescueEnabled, false)){
                rescueAlert.clear();
                reset();
                return;
            }
            if(Time.time < nextScan) return;
            if(!state.isGame() || world == null || world.isGenerating() || player == null){
                rescueAlert.clear();
                reset();
                return;
            }

            float scanInterval = 10f;
            nextScan = Time.time + scanInterval;

            cache.updateBasic();
            if(cache.grids.isEmpty()){
                rescueAlert.clear();
                negativeSince.clear();
                balanceWindows.clear();
                minBalanceById.clear();
                return;
            }

            updateMinHistory(scanInterval);

            int activeId = rescueAlert.getGraphId();
            if(activeId != -1){
                //clear if graph disappeared or stayed positive for the whole clear window
                float minBal = minBalanceById.get(activeId, 0f);
                if(!currentIds.contains(activeId) || minBal > 0f){
                    rescueAlert.clear();
                    lastGraphId = -1;
                    negativeSince.remove(activeId);
                }
            }

            if(!Core.settings.getBool(keyRescueEnabled, false)){
                rescueAlert.clear();
                negativeSince.clear();
                lastGraphId = -1;
                return;
            }

            //track negative duration per graph, pick the worst sustained graph
            int worstId = activeId;
            float worstBalance = 0f;

            for(int i = 0; i < cache.grids.size; i++){
                GridInfo info = cache.grids.get(i);
                PowerGraph graph = info == null ? null : info.graph;
                if(info == null || info.team != player.team()) continue;
                if(graph == null) continue;
                if(!graph.hasPowerBalanceSamples()) continue;

                float balance = graph.getPowerBalance() * 60f; //UI units (/s)
                int gid = graph.getID();

                if(balance < -0.01f){
                    if(!negativeSince.containsKey(gid)){
                        negativeSince.put(gid, Time.time);
                    }
                    float since = negativeSince.get(gid, Time.time);
                    float window = Core.settings.getInt(keyRescueWindowSeconds, 4);
                    if(activeId != -1){
                        if(gid == activeId) worstBalance = balance;
                    }else if(Time.time - since >= window){
                        if(worstId == -1 || balance < worstBalance){
                            worstId = gid;
                            worstBalance = balance;
                        }
                    }
                }else{
                    negativeSince.remove(gid);
                    if(activeId != -1 && gid == activeId){
                        worstBalance = balance;
                    }
                }
            }

            if(worstId == -1){
                lastGraphId = -1;
                return;
            }

            PowerGraph worstGraph = null;
            for(int i = 0; i < cache.grids.size; i++){
                GridInfo info = cache.grids.get(i);
                if(info != null && info.team == player.team() && info.graph != null && info.graph.getID() == worstId){
                    worstGraph = info.graph;
                    break;
                }
            }
            if(worstGraph == null){
                return;
            }

            //If currently positive, keep existing hints until the clear-window says it's stable.
            if(rescueAlert.getGraphId() == worstId && worstGraph.getPowerBalance() * 60f > 0f){
                lastGraphId = worstId;
                return;
            }

            boolean aggressive = Core.settings.getBool(keyRescueAggressive, false);
            int topk = Mathf.clamp(Core.settings.getInt(keyRescueTopK, 2), 1, 10);

            tmpHints.clear();
            buildCutHints(worstGraph, aggressive, topk, tmpHints);

            tmpImpactHints.clear();
            float deficitPerSecond = Math.max(0f, -worstGraph.getPowerBalance() * 60f);
            buildImpactDisableHints(worstGraph, deficitPerSecond, tmpImpactHints);

            //If Impact-Reactor disables can fully recover the deficit, prefer that plan and avoid cluttering with cut suggestions.
            if(!tmpImpactHints.isEmpty()){
                tmpHints.clear();
            }

            if(tmpHints.isEmpty() && tmpImpactHints.isEmpty()){
                rescueAlert.clear();
                lastGraphId = worstId;
                return;
            }

            //only toast when graph changes or periodically, to avoid spam
            boolean shouldToast = lastGraphId != worstId || Time.time - lastToast > 60f * 2f;
            lastGraphId = worstId;
            if(shouldToast){
                lastToast = Time.time;
                rescueAlert.trigger(worstId, tmpHints, tmpImpactHints, true);
            }else{
                //refresh hints silently
                rescueAlert.trigger(worstId, tmpHints, tmpImpactHints, false);
            }
        }

        float minBalance(int graphId){
            return minBalanceById.get(graphId, 0f);
        }

        private void updateMinHistory(float scanInterval){
            currentIds.clear();
            minBalanceById.clear();

            int clearWindowSeconds = Math.max(0, Core.settings.getInt(keyRescueClearWindowSeconds, 8));
            int samples = Math.max(1, (int)Math.ceil(clearWindowSeconds * 60f / Math.max(1f, scanInterval)));
            if(samples != historySamples){
                historySamples = samples;
                balanceWindows.clear();
            }

            for(int i = 0; i < cache.grids.size; i++){
                GridInfo info = cache.grids.get(i);
                PowerGraph graph = info == null ? null : info.graph;
                if(graph == null) continue;
                int gid = graph.getID();
                currentIds.add(gid);

                float balance = graph.getPowerBalance() * 60f;
                BalanceWindow w = balanceWindows.get(gid);
                if(w == null){
                    w = new BalanceWindow(samples);
                    balanceWindows.put(gid, w);
                }else if(w.size != samples){
                    w = new BalanceWindow(samples);
                    balanceWindows.put(gid, w);
                }
                w.add(balance);
                minBalanceById.put(gid, w.min());
            }

            //prune history for disappeared graphs
            tmpToRemove.clear();
            for(IntMap.Entry<BalanceWindow> e : balanceWindows){
                if(!currentIds.contains(e.key)){
                    tmpToRemove.add(e.key);
                }
            }
            for(int i = 0; i < tmpToRemove.size; i++){
                int gid = tmpToRemove.get(i);
                balanceWindows.remove(gid);
                minBalanceById.remove(gid);
                negativeSince.remove(gid);
            }
        }

        private class BalanceWindow{
            final int size;
            final float[] values;
            int index = 0;
            int filled = 0;

            BalanceWindow(int size){
                this.size = Math.max(1, size);
                this.values = new float[this.size];
            }

            void add(float v){
                values[index] = v;
                index = (index + 1) % size;
                if(filled < size) filled++;
            }

            float min(){
                if(filled <= 0) return 0f;
                float m = Float.POSITIVE_INFINITY;
                for(int i = 0; i < filled; i++){
                    float v = values[i];
                    if(v < m) m = v;
                }
                return m == Float.POSITIVE_INFINITY ? 0f : m;
            }
        }

        private void buildCutHints(PowerGraph graph, boolean aggressive, int topk, Seq<RescueCutHint> out){
            if(graph == null || graph.all == null || graph.all.isEmpty()) return;

            //collect candidate link edges (PowerNode laser links); dedupe in a small list
            tmpEdgeA.clear();
            tmpEdgeB.clear();
            for(int i = 0; i < graph.all.size; i++){
                Building b = graph.all.get(i);
                if(b == null || b.team != player.team() || b.power == null) continue;
                if(!(b.block instanceof PowerNode)) continue;
                IntSeq links = b.power.links;
                for(int li = 0; li < links.size; li++){
                    int otherPos = links.get(li);
                    if(otherPos == b.pos()) continue;
                    int a = Math.min(b.pos(), otherPos);
                    int c = Math.max(b.pos(), otherPos);
                    boolean exists = false;
                    for(int ei = 0; ei < tmpEdgeA.size; ei++){
                        if(tmpEdgeA.get(ei) == a && tmpEdgeB.get(ei) == c){
                            exists = true;
                            break;
                        }
                    }
                    if(!exists){
                        tmpEdgeA.add(a);
                        tmpEdgeB.add(c);
                    }
                }
            }

            if(tmpEdgeA.isEmpty()) return;

            //compute graph totals once (steady-state only; batteries kept separate)
            float totalProduced = 0f;
            float totalNeeded = 0f;
            float totalStored = 0f;
            for(int i = 0; i < graph.all.size; i++){
                Building b = graph.all.get(i);
                if(b == null || b.team != player.team() || b.power == null) continue;
                totalProduced += b.getPowerProduction() * b.delta();
                if(b.shouldConsumePower && b.block != null && b.block.consPower != null){
                    totalNeeded += b.block.consPower.requestedPower(b) * b.delta();
                }
                if(b.enabled && b.block != null && b.block.consPower != null && b.block.consPower.buffered && b.block.consPower.capacity > 0f){
                    totalStored += b.power.status * b.block.consPower.capacity;
                }
            }

            tmpBest.clear();

            int maxEdges = aggressive ? 60 : 30;
            for(int ei = 0; ei < tmpEdgeA.size && ei < maxEdges; ei++){
                int aPos = tmpEdgeA.get(ei);
                int bPos = tmpEdgeB.get(ei);
                Building aBuild = world.build(aPos);
                Building bBuild = world.build(bPos);
                if(aBuild == null || bBuild == null) continue;
                if(aBuild.team != player.team() || bBuild.team != player.team()) continue;
                if(aBuild.power == null || bBuild.power == null) continue;
                if(aBuild.power.graph != graph || bBuild.power.graph != graph) continue;

                ComponentStats aSide = computeComponent(graph, aBuild, aBuild.pos(), bBuild.pos());
                if(aSide.count <= 0) continue;

                float bProduced = totalProduced - aSide.produced;
                float bNeeded = totalNeeded - aSide.needed;
                float netA = aSide.produced - aSide.needed;
                float netB = bProduced - bNeeded;

                float rescueNet = Math.max(netA, netB);
                if(rescueNet <= 0.01f) continue;

                RescueCutHint hint = new RescueCutHint();
                hint.aPos = aBuild.pos();
                hint.bPos = bBuild.pos();
                hint.islandStartPos = netA >= netB ? hint.aPos : hint.bPos;
                hint.rescueNetPerTick = rescueNet;

                insertTopK(tmpBest, hint, topk);
            }

            for(int i = 0; i < tmpBest.size; i++){
                RescueCutHint h = tmpBest.get(i);
                if(h == null) continue;
                if(!computeIslandHull(graph, h)) continue;
                out.add(h);
            }
        }

        private void buildImpactDisableHints(PowerGraph graph, float deficitPerSecond, Seq<ImpactDisableHint> out){
            out.clear();
            if(deficitPerSecond <= 0.01f) return;
            if(graph == null || graph.all == null || graph.all.isEmpty()) return;

            float totalImprove = 0f;

            //insert-sort candidates by improvement desc (few items expected)
            for(int i = 0; i < graph.all.size; i++){
                Building b = graph.all.get(i);
                if(b == null || b.team != player.team() || b.power == null) continue;
                if(b.block != Blocks.impactReactor) continue;

                float produced = b.getPowerProduction() * b.delta() * 60f;
                float needed = 0f;
                if(b.shouldConsumePower && b.block != null && b.block.consPower != null){
                    needed = b.block.consPower.requestedPower(b) * b.delta() * 60f;
                }

                float net = produced - needed;
                if(net >= 0f) continue; //don't suggest disabling net-positive reactors

                float improve = -net;
                totalImprove += improve;

                ImpactDisableHint h = new ImpactDisableHint();
                h.pos = b.pos();
                h.x = b.x;
                h.y = b.y;
                h.improvePerSecond = improve;

                int idx = 0;
                for(; idx < out.size; idx++){
                    if(improve > out.get(idx).improvePerSecond){
                        break;
                    }
                }
                out.insert(idx, h);
            }

            //Only show this plan if it can fully recover the deficit.
            if(totalImprove + 0.01f < deficitPerSecond){
                out.clear();
                return;
            }

            float acc = 0f;
            int keep = 0;
            for(int i = 0; i < out.size; i++){
                acc += out.get(i).improvePerSecond;
                keep++;
                if(acc >= deficitPerSecond) break;
            }
            //cap clutter; keep should still be enough for recovery in normal cases
            keep = Mathf.clamp(keep, 0, 30);
            out.truncate(keep);
        }

        private boolean computeIslandHull(PowerGraph graph, RescueCutHint hint){
            if(graph == null || hint == null) return false;
            int startPos = hint.islandStartPos;
            Building start = world.build(startPos);
            if(start == null || start.power == null || start.power.graph != graph) return false;

            tmpHullTiles.clear();
            tmpHullTileList.clear();

            tmpVisited.clear();
            tmpQueue.clear();
            tmpVisited.add(startPos);
            tmpQueue.addLast(startPos);

            int cutA = hint.aPos;
            int cutB = hint.bPos;

            while(tmpQueue.size > 0){
                int pos = tmpQueue.removeFirst();
                Building cur = world.build(pos);
                if(cur == null || cur.power == null || cur.power.graph != graph) continue;
                if(cur.team != player.team()) continue;
                if(cur.tile != null){
                    cur.tile.getLinkedTiles(t -> {
                        int tpos = t.pos();
                        if(tmpHullTiles.add(tpos)){
                            tmpHullTileList.add(tpos);
                        }
                    });
                }

                cur.getPowerConnections(tmpConns);
                for(int i = 0; i < tmpConns.size; i++){
                    Building next = tmpConns.get(i);
                    if(next == null || next.power == null) continue;
                    if(next.power.graph != graph) continue;
                    int npos = next.pos();

                    //skip the tested link cut (in both directions)
                    if((pos == cutA && npos == cutB) || (pos == cutB && npos == cutA)) continue;

                    if(tmpVisited.add(npos)){
                        tmpQueue.addLast(npos);
                    }
                }
            }

            if(tmpHullTileList.isEmpty()) return false;

            //collect corner points in tile-coordinates, then compute a convex hull
            tmpHullCorners.clear();
            for(int i = 0; i < tmpHullTileList.size; i++){
                int tpos = tmpHullTileList.get(i);
                int tx = Point2.x(tpos);
                int ty = Point2.y(tpos);
                addCorner(tx, ty);
                addCorner(tx + 1, ty);
                addCorner(tx, ty + 1);
                addCorner(tx + 1, ty + 1);
            }

            if(tmpHullCorners.size < 3) return false;

            Arrays.sort(tmpHullCorners.items, 0, tmpHullCorners.size);

            tmpHullUnique.clear();
            long last = Long.MIN_VALUE;
            for(int i = 0; i < tmpHullCorners.size; i++){
                long p = tmpHullCorners.items[i];
                if(p != last){
                    tmpHullUnique.add(p);
                    last = p;
                }
            }
            if(tmpHullUnique.size < 3) return false;

            tmpHull.clear();

            //lower hull
            for(int i = 0; i < tmpHullUnique.size; i++){
                long p = tmpHullUnique.items[i];
                while(tmpHull.size >= 2 && cross(tmpHull.items[tmpHull.size - 2], tmpHull.items[tmpHull.size - 1], p) <= 0){
                    tmpHull.size--;
                }
                tmpHull.add(p);
            }

            //upper hull
            int lowerSize = tmpHull.size;
            for(int i = tmpHullUnique.size - 2; i >= 0; i--){
                long p = tmpHullUnique.items[i];
                while(tmpHull.size > lowerSize && cross(tmpHull.items[tmpHull.size - 2], tmpHull.items[tmpHull.size - 1], p) <= 0){
                    tmpHull.size--;
                }
                tmpHull.add(p);
            }

            //remove duplicate start
            if(tmpHull.size > 1){
                tmpHull.size--;
            }
            if(tmpHull.size < 3) return false;

            int n = tmpHull.size;
            float[] hull = hint.hull;
            if(hull == null || hull.length != n * 2){
                hull = new float[n * 2];
                hint.hull = hull;
            }

            float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY;
            float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY;

            for(int i = 0; i < n; i++){
                long p = tmpHull.items[i];
                int tx = (int)(p >> 32);
                int ty = (int)p;

                float wx = tx * tilesize;
                float wy = ty * tilesize;

                hull[i * 2] = wx;
                hull[i * 2 + 1] = wy;

                if(wx < minX) minX = wx;
                if(wy < minY) minY = wy;
                if(wx > maxX) maxX = wx;
                if(wy > maxY) maxY = wy;
            }

            hint.minX = minX;
            hint.minY = minY;
            hint.maxX = maxX;
            hint.maxY = maxY;

            //polygon centroid (world coords); fallback to bbox center if degenerate
            double area2 = 0.0;
            double cx = 0.0;
            double cy = 0.0;
            for(int i = 0; i < n; i++){
                int j = (i + 1) % n;
                double x0 = hull[i * 2];
                double y0 = hull[i * 2 + 1];
                double x1 = hull[j * 2];
                double y1 = hull[j * 2 + 1];
                double cross = x0 * y1 - x1 * y0;
                area2 += cross;
                cx += (x0 + x1) * cross;
                cy += (y0 + y1) * cross;
            }

            if(Math.abs(area2) < 0.0001){
                hint.centerX = (minX + maxX) / 2f;
                hint.centerY = (minY + maxY) / 2f;
            }else{
                double area6 = area2 * 3.0;
                hint.centerX = (float)(cx / area6);
                hint.centerY = (float)(cy / area6);
            }

            return true;
        }

        private void addCorner(int x, int y){
            tmpHullCorners.add((((long)x) << 32) | (y & 0xffffffffL));
        }

        private long cross(long o, long a, long b){
            long ox = (int)(o >> 32);
            long oy = (int)o;
            long ax = (int)(a >> 32);
            long ay = (int)a;
            long bx = (int)(b >> 32);
            long by = (int)b;

            return (ax - ox) * (by - oy) - (ay - oy) * (bx - ox);
        }

        private void insertTopK(Seq<RescueCutHint> best, RescueCutHint hint, int k){
            //sort descending by rescue net
            int idx = 0;
            for(; idx < best.size; idx++){
                if(hint.rescueNetPerTick > best.get(idx).rescueNetPerTick){
                    break;
                }
            }
            best.insert(idx, hint);
            if(best.size > k){
                best.truncate(k);
            }
        }

        private class ComponentStats{
            float produced, needed, stored;
            int count;
        }

        private ComponentStats computeComponent(PowerGraph graph, Building start, int cutA, int cutB){
            ComponentStats out = new ComponentStats();
            tmpVisited.clear();
            tmpQueue.clear();

            int startPos = start.pos();
            tmpVisited.add(startPos);
            tmpQueue.addLast(startPos);

            while(tmpQueue.size > 0){
                int pos = tmpQueue.removeFirst();
                Building cur = world.build(pos);
                if(cur == null || cur.power == null || cur.power.graph != graph) continue;
                if(cur.team != player.team()) continue;

                out.count++;
                out.produced += cur.getPowerProduction() * cur.delta();
                if(cur.shouldConsumePower && cur.block != null && cur.block.consPower != null){
                    out.needed += cur.block.consPower.requestedPower(cur) * cur.delta();
                }
                if(cur.enabled && cur.block != null && cur.block.consPower != null && cur.block.consPower.buffered && cur.block.consPower.capacity > 0f){
                    out.stored += cur.power.status * cur.block.consPower.capacity;
                }

                cur.getPowerConnections(tmpConns);
                for(int i = 0; i < tmpConns.size; i++){
                    Building next = tmpConns.get(i);
                    if(next == null || next.power == null) continue;
                    if(next.power.graph != graph) continue;
                    int npos = next.pos();

                    //skip the tested link cut (in both directions)
                    if((pos == cutA && npos == cutB) || (pos == cutB && npos == cutA)) continue;

                    if(tmpVisited.add(npos)){
                        tmpQueue.addLast(npos);
                    }
                }
            }

            return out;
        }
    }

    private static class ReconnectResult{
        float midX, midY;
        float ax, ay, bx, by;
    }

    /** Optional integration with MindustryX "mark" feature. Uses reflection so vanilla builds won't crash. */
    private static class MindustryXMarkers{
        private boolean initialized = false;
        private boolean available = false;
        private java.lang.reflect.Method newMarkFromChat;

        void tryInit(){
            if(initialized) return;
            initialized = true;
            try{
                Class<?> markerType = Class.forName("mindustryX.features.MarkerType");
                // public static void newMarkFromChat(String text, Vec2 pos)
                newMarkFromChat = markerType.getMethod("newMarkFromChat", String.class, Vec2.class);
                available = true;
                Log.info("PGMM: MindustryX marker API detected.");
            }catch(Throwable ignored){
                available = false;
            }
        }

        void markReconnect(int tileX, int tileY){
            if(!available || newMarkFromChat == null) return;
            try{
                //MindustryX FormatDefault.formatTile expects world coords; its newMarkFromChat scales tile coords by tilesize internally.
                String label = Core.bundle.get("pgmm.mark.reconnect", "Reconnect point");
                String text = "[orange]" + label + "[] (" + tileX + "," + tileY + ")";
                newMarkFromChat.invoke(null, text, new Vec2(tileX, tileY));
            }catch(Throwable t){
                //Disable after first failure to avoid spam.
                available = false;
                Log.err("PGMM: MindustryX marker call failed; disabling integration.", t);
            }
        }

        void markRescue(int tileX, int tileY){
            if(!available || newMarkFromChat == null) return;
            try{
                String label = Core.bundle.get("pgmm.mark.rescue", "Power rescue");
                String text = "[scarlet]" + label + "[] (" + tileX + "," + tileY + ")";
                newMarkFromChat.invoke(null, text, new Vec2(tileX, tileY));
            }catch(Throwable t){
                available = false;
                Log.err("PGMM: MindustryX marker call failed; disabling integration.", t);
            }
        }
    }
}
