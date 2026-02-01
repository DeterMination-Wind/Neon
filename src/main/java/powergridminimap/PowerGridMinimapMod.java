package powergridminimap;

import arc.Core;
import arc.Events;
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
import arc.util.Structs;
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
import mindustry.graphics.Drawf;
import mindustry.gen.Player;
import mindustry.mod.Scripts;
import mindustry.ui.Fonts;
import mindustry.ui.dialogs.SettingsMenuDialog;
import mindustry.world.blocks.power.PowerNode;
import mindustry.world.blocks.power.PowerGraph;
import mindustry.mod.Mods;
import rhino.ScriptableObject;

import java.lang.reflect.Field;
import java.util.Arrays;

import static mindustry.Vars.player;
import static mindustry.Vars.renderer;
import static mindustry.Vars.state;
import static mindustry.Vars.tilesize;
import static mindustry.Vars.ui;
import static mindustry.Vars.world;

public class PowerGridMinimapMod extends mindustry.mod.Mod{
    /** When true, this mod is running as a bundled component inside BEK-Tools. */
    public static boolean bekBundled = false;

    private static final String overlayName = "pgmm-overlay";
    private static final String mi2OverlayName = "pgmm-overlay-mi2-minimap";

    private static final String keyEnabled = "pgmm-enabled";
    private static final String keyGridAlpha = "pgmm-gridalpha";
    private static final String keyMarkerScale = "pgmm-markerscale";
    private static final String keyMarkerColor = "pgmm-markercolor";
    private static final String keyHudMarkerFollowScale = "pgmm-hudmarkerscale";
    private static final String keyShowBalance = "pgmm-showbalance";
    private static final String keyDrawOnMi2Minimap = "pgmm-mi2minimap";
    private static final String keyClaimDistance = "pgmm-claimdistance";
    private static final String keySplitAlertThreshold = "pgmm-splitalertthreshold";
    private static final String keySplitAlertWindowSeconds = "pgmm-splitwindow";
    private static final String keyClusterMarkerDistance = "pgmm-clustermarkerdistance";
    private static final String keyReconnectStroke = "pgmm-markerstroke";
    private static final String keyReconnectColor = "pgmm-markerlinecolor";
    //debounce cache rebuilds after block changes (tenths of a second)
    private static final String keyUpdateWaitTenths = "pgmm-updatewait";

    private final PowerGridCache cache = new PowerGridCache();
    private final Color markerColor = new Color(Color.white);
    private final Color reconnectColor = new Color(Color.orange);

    private float nextAttachAttempt = 0f;
    private boolean shownAttachToast = false;

    private final FullMinimapAccess fullAccess = new FullMinimapAccess();
    private final Rect fullBounds = new Rect();
    private final Mat fullTransform = new Mat();
    private final Mat oldTransform = new Mat();

    private final SplitWatcher splitWatcher = new SplitWatcher();
    private final SplitAlert alert = new SplitAlert();
    private final MindustryXMarkers xMarkers = new MindustryXMarkers();
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
            Core.settings.defaults(keySplitAlertThreshold, 10000);
            Core.settings.defaults(keySplitAlertWindowSeconds, 4);
            Core.settings.defaults(keyClusterMarkerDistance, 15);
            Core.settings.defaults(keyReconnectStroke, 2);
            Core.settings.defaults(keyReconnectColor, "ffa500");
            Core.settings.defaults(keyUpdateWaitTenths, 10);

            registerSettings();
            refreshMarkerColor();
            refreshReconnectColor();
            xMarkers.tryInit();
            mi2.tryInit();
            installConsoleApi();
            Time.runTask(10f, this::installConsoleApi);
            Time.runTask(10f, this::ensureOverlayAttached);
        });

        Events.on(WorldLoadEvent.class, e -> {
            cache.clear();
            Time.runTask(10f, this::ensureOverlayAttached);
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
                mi2.ensureAttached(cache, markerColor, alert);
            }

            splitWatcher.update();
        });

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
            splitWatcher.reset();
        }catch(Throwable ignored){
        }

        try{
            nextAttachAttempt = 0f;
            ensureOverlayAttached();
        }catch(Throwable ignored){
        }

        try{
            mi2.ensureAttached(cache, markerColor, alert);
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
        mi2.ensureAttached(cache, markerColor, alert);
        //ensure something is available to draw immediately
        rescanNow();
    }

    private void registerSettings(){
        if(ui == null || ui.settings == null) return;
        if(bekBundled) return;

        String defaultCategory = "Power Grid Minimap";
        try{
            //fallback in case bundles fail to load / missing key
            if(Core.bundle != null && Core.bundle.getLocale() != null && "zh".equals(Core.bundle.getLocale().getLanguage())){
                defaultCategory = "电网小地图";
            }
        }catch(Throwable ignored){
        }

        ui.settings.addCategory(Core.bundle.get("pgmm.category", defaultCategory), this::bekBuildSettings);
    }

    /** Populates a {@link mindustry.ui.dialogs.SettingsMenuDialog.SettingsTable} with this mod's settings. */
    public void bekBuildSettings(SettingsMenuDialog.SettingsTable table){
        table.checkPref(keyEnabled, true);
        table.sliderPref(keyGridAlpha, 40, 0, 100, 5, v -> v + "%");
        table.checkPref(keyShowBalance, true);
        table.sliderPref(keyMarkerScale, 100, 50, 300, 10, v -> v + "%");
        table.textPref(keyMarkerColor, "ffffff", v -> refreshMarkerColor());
        table.sliderPref(keyHudMarkerFollowScale, 100, 0, 200, 10, v -> v + "%");

        //MI2 minimap integration toggle (disabled if MI2 not installed).
        table.pref(new Mi2MinimapSetting());

        table.sliderPref(keyClaimDistance, 5, 1, 20, 1, v -> v + "");
        table.sliderPref(keySplitAlertThreshold, 10000, 1000, 50000, 500, v -> v + "/s");
        table.sliderPref(keySplitAlertWindowSeconds, 4, 1, 15, 1, v -> v + "s");
        table.sliderPref(keyClusterMarkerDistance, 15, 0, 60, 1, v -> v + "");
        table.sliderPref(keyReconnectStroke, 2, 1, 8, 1, v -> v + "");
        table.textPref(keyReconnectColor, "ffa500", v -> refreshReconnectColor());
        table.sliderPref(keyUpdateWaitTenths, 10, 0, 50, 1, v -> (v / 10f) + "s");
    }

    private class Mi2MinimapSetting extends SettingsMenuDialog.SettingsTable.Setting{
        public Mi2MinimapSetting(){
            super(keyDrawOnMi2Minimap);
        }

        @Override
        public void add(SettingsMenuDialog.SettingsTable table){
            arc.scene.ui.CheckBox mi2Box = new arc.scene.ui.CheckBox(title);
            addDesc(mi2Box);

            mi2Box.getLabel().setWrap(true);
            mi2Box.getLabelCell().growX();

            mi2Box.changed(() -> {
                Core.settings.put(name, mi2Box.isChecked());
                //If MI2 exists, attach/detach immediately; otherwise, no-op.
                mi2.ensureAttached(cache, markerColor, alert);
            });

            mi2Box.update(() -> mi2Box.setChecked(Core.settings.getBool(name, false)));

            table.row();
            table.add(mi2Box).width(Math.min(Core.graphics.getWidth() / 1.2f, 460f)).left().padTop(3f);
            table.row();
        }
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

    private void ensureOverlayAttached(){
        if(ui == null || ui.hudGroup == null) return;

        Element minimap = ui.hudGroup.find("minimap");
        if(!(minimap instanceof Table)) return;

        Table table = (Table)minimap;
        if(table.find(overlayName) != null) return;

        if(table.getChildren().isEmpty()) return;
        Element base = table.getChildren().get(0);

        MinimapOverlay overlay = new MinimapOverlay(base, cache, markerColor, alert);
        overlay.name = overlayName;
        overlay.touchable = Touchable.disabled;
        table.addChild(overlay);

        if(!shownAttachToast && ui.hudfrag != null){
            shownAttachToast = true;
            ui.hudfrag.showToast(Core.bundle.get("pgmm.toast.enabled"));
        }

        Log.info("PGMM: overlay attached.");
    }

    private void drawFullMinimapOverlay(){
        if(!Core.settings.getBool(keyEnabled, true)) return;
        if(ui == null || ui.minimapfrag == null || !ui.minimapfrag.shown()) return;
        if(renderer == null || renderer.minimap == null || renderer.minimap.getTexture() == null) return;
        if(world == null || !state.isGame() || world.isGenerating()) return;
        if(player == null) return;

        cache.updateBasic();
        cache.updateFullOverlay();

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
        Texture overlayTex = cache.getFullOverlayTexture();
        if(overlayTex != null){
            overlayTex.setFilter(Texture.TextureFilter.nearest);
            TextureRegion reg = Draw.wrap(overlayTex);
            Draw.color();
            Draw.rect(reg, w/2f + panx*zoom, h/2f + pany*zoom, size, size * ratio);
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
        if(!cache.markerRects.isEmpty()){
            float alpha = Mathf.clamp(Core.settings.getInt(keyGridAlpha, 40) / 100f) * 0.12f;
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

        Draw.trans(oldTransform);
        Draw.reset();

        alert.drawScreenText();
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

        private final Rect viewRect = new Rect();
        private final Mat transform = new Mat();
        private final Mat oldTransform = new Mat();

        public MinimapOverlay(Element base, PowerGridCache cache, Color markerColor, SplitAlert alert){
            this.base = base;
            this.cache = cache;
            this.markerColor = markerColor;
            this.alert = alert;
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
            cache.updateFullOverlay();

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

        void ensureAttached(PowerGridCache cache, Color markerColor, SplitAlert alert){
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

                Mi2Overlay overlay = new Mi2Overlay(minimap, cache, markerColor, alert, rectField, setRectMethod);
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

        private final java.lang.reflect.Field rectField;
        private final java.lang.reflect.Method setRectMethod;

        private final Rect viewRect = new Rect();
        private boolean loggedDraw = false;
        private boolean loggedClipFail = false;

        Mi2Overlay(Element base, PowerGridCache cache, Color markerColor, SplitAlert alert, java.lang.reflect.Field rectField, java.lang.reflect.Method setRectMethod){
            this.base = base;
            this.cache = cache;
            this.markerColor = markerColor;
            this.alert = alert;
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
            cache.updateFullOverlay();

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

            float alpha = Mathf.clamp(Core.settings.getInt(keyGridAlpha, 40) / 100f) * parentAlpha;
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

    private static class GridInfo{
        PowerGraph graph;
        int colorKey;
    }

    private static class MarkerInfo{
        PowerGraph graph;
        float x, y;
    }

    private static class MarkerRectInfo{
        PowerGraph graph;
        int colorKey;
        final Rect worldRect = new Rect();
    }

    private static class FullMinimapAccess{
        private boolean initialized;
        private Field panx, pany, zoom, baseSize;

        private void init(Object frag){
            if(initialized) return;
            initialized = true;
            try{
                Class<?> type = frag.getClass();
                panx = type.getDeclaredField("panx");
                pany = type.getDeclaredField("pany");
                zoom = type.getDeclaredField("zoom");
                baseSize = type.getDeclaredField("baseSize");
                panx.setAccessible(true);
                pany.setAccessible(true);
                zoom.setAccessible(true);
                baseSize.setAccessible(true);
            }catch(Throwable t){
                Log.err("PGMM: failed to reflect MinimapFragment fields, fullscreen overlay may be misaligned.");
            }
        }

        float getPanX(Object frag){
            init(frag);
            try{
                return panx == null ? 0f : panx.getFloat(frag);
            }catch(Throwable ignored){
                return 0f;
            }
        }

        float getPanY(Object frag){
            init(frag);
            try{
                return pany == null ? 0f : pany.getFloat(frag);
            }catch(Throwable ignored){
                return 0f;
            }
        }

        float getZoom(Object frag){
            init(frag);
            try{
                return zoom == null ? 1f : zoom.getFloat(frag);
            }catch(Throwable ignored){
                return 1f;
            }
        }

        float getBaseSize(Object frag, float fallback){
            init(frag);
            try{
                return baseSize == null ? fallback : baseSize.getFloat(frag);
            }catch(Throwable ignored){
                return fallback;
            }
        }
    }

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

        public void clear(){
            graphs.clear();
            grids.clear();
            markers.clear();
            markerRects.clear();
            nextUpdateTime = 0f;
            nextFullUpdateTime = 0f;
            basicDirty = true;
            fullDirty = true;

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

            if(basicDirty && Time.time < nextUpdateTime) return;
            if(!basicDirty && Time.time < nextUpdateTime) return;
            if(!basicDirty && Time.time >= nextUpdateTime){
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
                if(build == null || build.team != player.team() || build.power == null) continue;
                if(build.power.graph == null || build.power.graph.all == null || build.power.graph.all.isEmpty()) continue;
                graphs.add(build.power.graph);
            }

            for(PowerGraph graph : graphs){
                GridInfo info = new GridInfo();
                info.graph = graph;
                grids.add(info);

                //For sparse laser-linked grids, render one balance marker per contiguous "chunk" of buildings.
                addClusterMarkers(info);
            }
        }

        private void addClusterMarkers(GridInfo info){
            PowerGraph graph = info.graph;
            if(graph == null || graph.all == null || graph.all.isEmpty()) return;
            info.colorKey = graph.getID();

            //Collect occupied tiles (all linked tiles for each building) for this graph.
            tmpOccupied.clear();
            tmpOccupiedList.clear();

            Seq<mindustry.gen.Building> all = graph.all;
            for(int i = 0; i < all.size; i++){
                mindustry.gen.Building b = all.get(i);
                if(b == null || b.team != player.team() || b.tile == null) continue;
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
            Arrays.fill(ownerPower, -1);
            Arrays.fill(overlayOwner, -1);

            //seed: power-using buildings tiles
            for(int gi = 0; gi < grids.size; gi++){
                final int gridIndex = gi;
                PowerGraph graph = grids.get(gi).graph;
                if(graph == null || graph.all == null) continue;
                Seq<mindustry.gen.Building> all = graph.all;
                for(int bi = 0; bi < all.size; bi++){
                    mindustry.gen.Building b = all.get(bi);
                    if(b == null || b.team != player.team()) continue;
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

            //rule 1: adjacent non-power buildings join if adjacent to exactly one grid (by power tiles)
            //collect non-power buildings first
            Seq<mindustry.gen.Building> nonPower = tmpNonPower;
            nonPower.clear();
            for(int i = 0; i < mindustry.gen.Groups.build.size(); i++){
                mindustry.gen.Building b = mindustry.gen.Groups.build.index(i);
                if(b == null || b.team != player.team()) continue;
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
                    ncount = addNeighborGrid(ownerPower, w, h, tx + 1, ty, neighborGrids, ncount);
                    ncount = addNeighborGrid(ownerPower, w, h, tx - 1, ty, neighborGrids, ncount);
                    ncount = addNeighborGrid(ownerPower, w, h, tx, ty + 1, neighborGrids, ncount);
                    ncount = addNeighborGrid(ownerPower, w, h, tx, ty - 1, neighborGrids, ncount);
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

            //rule 2: distance BFS from power tiles; non-power buildings within threshold join nearest grid
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
                    if(d >= 0 && d < claimDistance && g >= 0){
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

            //rule 4: for each grid, compute boundary+interior fill per connected component and render into pixmap
            boolean[] visited = tmpVisitedTiles;
            Arrays.fill(visited, false);
            IntQueue bfs = tmpBfs;
            bfs.clear();
            IntSeq comp = tmpComp;
            comp.clear();

            float alpha = Mathf.clamp(gridAlphaInt / 100f);

            //Precompute colors per grid once, then scan tiles once.
            int gridCount = grids.size;
            int[] baseRgbaByGrid = new int[gridCount];
            int[] darkRgbaByGrid = new int[gridCount];
            int[] lightRgbaByGrid = new int[gridCount];
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

        private static int addNeighborGrid(int[] ownerPower, int w, int h, int x, int y, int[] out, int count){
            if(x < 0 || y < 0 || x >= w || y >= h) return count;
            int g = ownerPower[x + y * w];
            if(g < 0) return count;
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
            if(Time.time < nextScan) return;
            nextScan = Time.time + scanInterval;

            if(!state.isGame() || world == null || world.isGenerating() || player == null){
                lastGraphByBuildPos.clear();
                lastGraphPowerIn.clear();
                lastSplitTime.clear();
                pendingSplits.clear();
                return;
            }

            int threshold = Core.settings.getInt(keySplitAlertThreshold, 10000);
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

                if(Time.time > pending.expiresAt){
                    tmpToRemove.add(pending.prevId);
                    continue;
                }

                final int[] negativeId = {-1};
                final float[] mostNegative = {0f};

                pending.resultIds.each(id -> {
                    float bal = currentBalance.get(id, 0f);
                    if(bal < mostNegative[0]){
                        mostNegative[0] = bal;
                        negativeId[0] = id;
                    }
                });

                if(negativeId[0] == -1) continue;

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

                ReconnectResult reconnect = findReconnectPoint(aId, bId);
                if(reconnect == null){
                    //fallback: try any other candidate
                    final ReconnectResult[] found = {null};
                    final int[] foundB = {bId};
                    pending.resultIds.each(id -> {
                        if(found[0] != null) return;
                        if(id == aId) return;
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
    }
}
