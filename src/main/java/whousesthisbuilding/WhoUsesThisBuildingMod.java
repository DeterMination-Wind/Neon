package whousesthisbuilding;

import bektools.ui.RbmStyle;
import bektools.ui.VscodeSettingsStyle;
import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Font;
import arc.graphics.g2d.GlyphLayout;
import arc.graphics.g2d.Lines;
import arc.input.KeyCode;
import arc.math.geom.Point2;
import arc.math.geom.Vec2;
import arc.scene.Element;
import arc.scene.ui.layout.Scl;
import arc.scene.ui.Label;
import arc.scene.ui.TextButton;
import arc.scene.ui.layout.Table;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import arc.util.Align;
import arc.util.Time;
import arc.util.pooling.Pools;
import mindustry.game.EventType;
import mindustry.editor.MapView;
import mindustry.gen.Building;
import mindustry.gen.Groups;
import mindustry.gen.Icon;
import mindustry.graphics.Drawf;
import mindustry.graphics.Pal;
import mindustry.mod.Mod;
import mindustry.ui.Styles;
import mindustry.ui.Fonts;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.ui.dialogs.SettingsMenuDialog;
import mindustry.world.blocks.logic.LogicBlock;
import mindustry.world.blocks.logic.LogicBlock.LogicBuild;
import mindustry.world.blocks.logic.LogicBlock.LogicLink;

import java.util.Locale;
import java.util.Objects;

import static mindustry.Vars.player;
import static mindustry.Vars.state;
import static mindustry.Vars.tilesize;
import static mindustry.Vars.ui;
import static mindustry.Vars.world;

public class WhoUsesThisBuildingMod extends Mod{
    /** When true, this mod is running as a bundled component inside Neon. */
    public static boolean bekBundled = false;


    private static final String keyEnabled = "wutb-enabled";
    private static final String keyHotkey = "wutb-hotkey";
    private static final String keyFontScale = "wutb-font-scale";
    private static final String defaultHotkey = "altleft";
    private static final long rescanIntervalMs = 180L;

    private final ObjectMap<Integer, LogicReferenceAnalyzer.ProcessorAnalysis> analysisCache = new ObjectMap<>();
    private final Seq<ProcessorMatch> matches = new Seq<>();

    private boolean enabled = true;
    private KeyCode hotkey = KeyCode.altLeft;
    private Building currentTarget;
    private boolean overlayActive;
    private long lastScanAt;
    private float fontScale = 1f;

    public WhoUsesThisBuildingMod(){
        Core.settings.defaults(keyEnabled, true);
        Core.settings.defaults(keyHotkey, defaultHotkey);
        Core.settings.defaults(keyFontScale, 100);
        refreshSettings();

        Events.on(EventType.ClientLoadEvent.class, event -> {
            if(ui != null && ui.settings != null){
                if(!bekBundled) ui.settings.addCategory("@settings.whousesthisbuilding", Icon.logicSmall, this::bekBuildSettings);
            }
        });

        Events.on(EventType.WorldLoadEvent.class, event -> {
            analysisCache.clear();
            clearOverlay();
        });

        Events.run(EventType.Trigger.update, this::updateOverlayState);
        Events.run(EventType.Trigger.draw, this::drawOverlay);
    }

    private void buildSettings(SettingsMenuDialog.SettingsTable table){
        if(!bekBundled){
            table.pref(new RbmStyle.HeaderSetting(Core.bundle.get("settings.whousesthisbuilding", "Who Uses This Building"), Icon.logicSmall));
        }
        table.pref(new RbmStyle.IconCheckSetting(keyEnabled, true, Icon.eyeSmall, value -> {
            enabled = value;
            if(!enabled){
                clearOverlay();
            }
        }));
        table.pref(new HotkeySetting(keyHotkey, defaultHotkey));
        table.pref(new RbmStyle.IconSliderSetting(keyFontScale, 100, 50, 300, 5, Icon.resizeSmall, value -> value + "%", null));
    }

    /** Populates a {@link mindustry.ui.dialogs.SettingsMenuDialog.SettingsTable} with this mod's settings. */
    public void bekBuildSettings(SettingsMenuDialog.SettingsTable table){
        buildSettings(table);
    }


    private void updateOverlayState(){
        refreshSettings();
        if(!enabled || !isInputAllowed()){
            clearOverlay();
            return;
        }
        if(!isHotkeyPressed()){
            clearOverlay();
            return;
        }

        Building hovered = hoveredBuilding();
        if(hovered == null){
            clearOverlay();
            return;
        }

        overlayActive = true;
        long now = Time.millis();
        if(hovered != currentTarget || now - lastScanAt >= rescanIntervalMs){
            currentTarget = hovered;
            lastScanAt = now;
            rebuildMatches(hovered);
        }
    }

    private void drawOverlay(){
        if(!overlayActive || matches.isEmpty()) return;

        Seq<RectArea> blockObstacles = new Seq<>();
        for(ProcessorMatch match : matches){
            LogicBuild processor = match.processor;
            if(processor == null || !processor.isValid()) continue;
            Drawf.square(processor.x, processor.y, processor.block.size * tilesize / 2f + 1f, Color.sky);
            float half = processor.block.size * tilesize / 2f + 5f;
            blockObstacles.add(new RectArea(processor.id, processor.x - half, processor.y - half, processor.x + half, processor.y + half));
        }

        Seq<LabelPlacement> occupied = new Seq<>();
        for(ProcessorMatch match : matches){
            LogicBuild processor = match.processor;
            if(processor == null || !processor.isValid()) continue;
            if(isCardinallyCrowded(processor)){
                String compact = "L" + match.line;
                drawCenteredText(processor, compact, Pal.accent, fontScale);
                continue;
            }

            LabelPlacement placement = chooseLabelPlacement(processor, match.text, fontScale, occupied, blockObstacles);
            if(placement == null) continue;
            drawPlaceTextScaled(match.text, placement.centerX, placement.lineY, processor.x, processor.y, Pal.accent, false, fontScale);
            occupied.add(placement);
        }
    }

    private void rebuildMatches(Building target){
        matches.clear();
        if(target == null || !target.isValid()) return;

        long coordKey = LogicReferenceAnalyzer.packCoord(target.tileX(), target.tileY());
        Groups.build.each(building -> {
            if(!(building instanceof LogicBuild logic)) return;
            if(!logic.isValid()) return;
            if(!shouldInspect(logic)) return;

            LogicReferenceAnalyzer.ReferenceLine best = findBestReference(logic, target, coordKey);
            if(best == null) return;

            String text = "L" + best.line + "(" + best.opcode + ")";
            matches.add(new ProcessorMatch(logic, text, best.line));
        });

        matches.sort((a, b) -> {
            int lineCmp = Integer.compare(a.line, b.line);
            if(lineCmp != 0) return lineCmp;
            return Integer.compare(a.processor.id, b.processor.id);
        });
    }

    private LogicReferenceAnalyzer.ReferenceLine findBestReference(LogicBuild logic, Building target, long coordKey){
        LogicReferenceAnalyzer.ProcessorAnalysis analysis = getAnalysis(logic);
        if(analysis == null) return null;

        LogicReferenceAnalyzer.ReferenceLine best = null;
        boolean linkedToTarget = false;

        for(LogicLink link : logic.links){
            if(link == null || link.name == null || link.name.isEmpty()) continue;
            Building linked = world.build(link.x, link.y);
            if(linked != target) continue;
            linkedToTarget = true;

            LogicReferenceAnalyzer.ReferenceLine ref = analysis.directReferences.get(link.name);
            if(ref != null && (best == null || ref.line < best.line)){
                best = ref;
            }
        }

        LogicReferenceAnalyzer.ReferenceLine indirect = analysis.getblockReferences.get(coordKey);
        if(indirect != null && (best == null || indirect.line < best.line)){
            best = indirect;
        }

        if(linkedToTarget && analysis.getlinkReference != null && (best == null || analysis.getlinkReference.line < best.line)){
            best = analysis.getlinkReference;
        }

        return best;
    }

    private LogicReferenceAnalyzer.ProcessorAnalysis getAnalysis(LogicBuild logic){
        int codeHash = logic.code == null ? 0 : logic.code.hashCode();
        int linksHash = hashLinks(logic);
        LogicReferenceAnalyzer.ProcessorAnalysis cached = analysisCache.get(logic.id);
        if(cached != null && cached.codeHash == codeHash && cached.linksHash == linksHash){
            return cached;
        }

        LogicReferenceAnalyzer.ProcessorAnalysis rebuilt = LogicReferenceAnalyzer.analyze(logic, codeHash, linksHash);
        analysisCache.put(logic.id, rebuilt);
        return rebuilt;
    }

    private int hashLinks(LogicBuild logic){
        int hash = 1;
        for(LogicLink link : logic.links){
            if(link == null) continue;
            hash = 31 * hash + link.x;
            hash = 31 * hash + link.y;
            hash = 31 * hash + Objects.hashCode(link.name);
        }
        return hash;
    }

    private boolean shouldInspect(LogicBuild logic){
        if(logic == null || logic.block == null) return false;
        if(isEditorLike()) return true;
        LogicBlock block = (LogicBlock)logic.block;
        if(block.privileged) return true;
        return player != null && logic.team == player.team();
    }

    private Building hoveredBuilding(){
        if(world == null) return null;
        if(isEditorLike()){
            MapView view = findHoveredMapView();
            if(view == null) return null;
            Vec2 local = view.stageToLocalCoordinates(Core.input.mouse());
            Point2 tile = view.project(local.x, local.y);
            if(tile == null) return null;
            return world.build(tile.x, tile.y);
        }
        return world.buildWorld(Core.input.mouseWorldX(), Core.input.mouseWorldY());
    }

    private boolean isInputAllowed(){
        if(state == null || !(state.isGame() || isEditorLike())) return false;
        if(world == null) return false;
        boolean editorLike = isEditorLike();
        if(editorLike){
            return isEditorHoverAllowed();
        }else{
            if(player == null) return false;
            if(ui == null || ui.hudfrag == null || !ui.hudfrag.shown) return false;
            if(Core.scene != null && Core.scene.hasMouse()) return false;
        }
        if(ui != null && ui.chatfrag != null && ui.chatfrag.shown()) return false;
        if(ui != null && ui.consolefrag != null && ui.consolefrag.shown()) return false;
        return Core.scene == null || (!Core.scene.hasDialog() && !Core.scene.hasField());
    }

    private boolean isEditorLike(){
        return state != null && (state.isEditor() || (state.rules != null && state.rules.editor));
    }

    private boolean isEditorHoverAllowed(){
        if(Core.scene == null) return true;
        Element hover = Core.scene.getHoverElement();
        if(hover == null) return false;
        if(hover instanceof MapView) return true;
        return hover.isDescendantOf(e -> e instanceof MapView);
    }

    private MapView findHoveredMapView(){
        if(Core.scene == null) return null;
        Element hover = Core.scene.getHoverElement();
        if(hover == null) return null;
        while(hover != null){
            if(hover instanceof MapView view){
                return view;
            }
            hover = hover.parent;
        }
        return null;
    }

    private void clearOverlay(){
        overlayActive = false;
        currentTarget = null;
        matches.clear();
    }

    private void refreshSettings(){
        enabled = Core.settings.getBool(keyEnabled, true);
        hotkey = parseKeyCode(Core.settings.getString(keyHotkey, defaultHotkey));
        fontScale = Math.max(0.5f, Core.settings.getInt(keyFontScale, 100) / 100f);
        if(!isCaptureKeyCandidate(hotkey)){
            hotkey = KeyCode.altLeft;
        }
    }

    private boolean isHotkeyPressed(){
        if(hotkey == null) return false;
        if(hotkey == KeyCode.altLeft || hotkey == KeyCode.altRight){
            return Core.input.keyDown(KeyCode.altLeft) || Core.input.keyDown(KeyCode.altRight);
        }
        return Core.input.keyDown(hotkey);
    }

    private LabelPlacement chooseLabelPlacement(Building build, String text, float scale, Seq<LabelPlacement> occupied, Seq<RectArea> obstacles){
        if(build == null || build.block == null) return null;
        TextMetrics metrics = measureText(text, scale);
        if(metrics == null) return null;

        float centerX = build.tileX() * tilesize + build.block.offset;
        float centerY = build.tileY() * tilesize + build.block.offset;
        float half = build.block.size * tilesize / 2f;
        float sideDx = half + metrics.width / 2f + 3f;

        float aboveLine = centerY + half + 2f;
        float belowLine = centerY - half - metrics.height - 3f;
        float sideLine = centerY - metrics.height * 0.15f;
        float higherLine = aboveLine + metrics.height + 5f;

        float[][] candidates = new float[][]{
        {centerX, aboveLine},
        {centerX, belowLine},
        {centerX + sideDx, aboveLine},
        {centerX - sideDx, aboveLine},
        {centerX + sideDx, belowLine},
        {centerX - sideDx, belowLine},
        {centerX + sideDx, sideLine},
        {centerX - sideDx, sideLine},
        {centerX, higherLine}
        };

        LabelPlacement best = null;
        float bestPenalty = Float.MAX_VALUE;

        for(float[] candidate : candidates){
            LabelPlacement placement = new LabelPlacement(candidate[0], candidate[1], metrics.width, metrics.height);
            float penalty = placementPenalty(placement, occupied, obstacles, build.id, centerX, aboveLine);
            if(penalty <= 0f){
                return placement;
            }
            if(penalty < bestPenalty){
                bestPenalty = penalty;
                best = placement;
            }
        }

        return best;
    }

    private float placementPenalty(LabelPlacement candidate, Seq<LabelPlacement> occupied, Seq<RectArea> obstacles, int ownerId, float anchorX, float anchorY){
        float penalty = 0f;
        for(LabelPlacement placed : occupied){
            float area = overlapArea(candidate.minX, candidate.minY, candidate.maxX, candidate.maxY, placed.minX, placed.minY, placed.maxX, placed.maxY);
            if(area > 0f){
                penalty += area * 8f;
            }
        }
        for(RectArea obstacle : obstacles){
            if(obstacle.ownerId == ownerId) continue;
            float area = overlapArea(candidate.minX, candidate.minY, candidate.maxX, candidate.maxY, obstacle.minX, obstacle.minY, obstacle.maxX, obstacle.maxY);
            if(area > 0f){
                penalty += area * 16f;
            }
        }

        float dx = candidate.centerX - anchorX;
        float dy = candidate.lineY - anchorY;
        penalty += (dx * dx + dy * dy) * 0.02f;

        return penalty;
    }

    private float overlapArea(float aMinX, float aMinY, float aMaxX, float aMaxY, float bMinX, float bMinY, float bMaxX, float bMaxY){
        float overlapX = Math.min(aMaxX, bMaxX) - Math.max(aMinX, bMinX);
        float overlapY = Math.min(aMaxY, bMaxY) - Math.max(aMinY, bMinY);
        if(overlapX <= 0f || overlapY <= 0f) return 0f;
        return overlapX * overlapY;
    }

    private boolean isCardinallyCrowded(Building build){
        if(build == null || build.block == null || world == null) return false;
        int step = Math.max(1, build.block.size);
        int tx = build.tileX();
        int ty = build.tileY();
        return world.build(tx + step, ty) != null
        && world.build(tx - step, ty) != null
        && world.build(tx, ty + step) != null
        && world.build(tx, ty - step) != null;
    }

    private TextMetrics measureText(String text, float scale){
        if(text == null || text.isEmpty()) return null;
        Font font = Fonts.outline;
        GlyphLayout layout = Pools.obtain(GlyphLayout.class, GlyphLayout::new);
        boolean ints = font.usesIntegerPositions();
        font.setUseIntegerPositions(false);

        float actualScale = Math.max(0.5f, Math.min(3f, scale));
        font.getData().setScale(actualScale / 4f / Scl.scl(1f));
        layout.setText(font, text);

        TextMetrics out = new TextMetrics(layout.width, layout.height);

        font.setUseIntegerPositions(ints);
        font.getData().setScale(1f);
        Pools.free(layout);
        return out;
    }

    private float drawPlaceTextScaled(String text, float centerX, float lineY, float targetX, float targetY, Color color, boolean drawLine, float scale){
        if(text == null || text.isEmpty()) return 0f;
        if(mindustry.Vars.renderer.pixelate) return 0f;

        Font font = Fonts.outline;
        GlyphLayout layout = Pools.obtain(GlyphLayout.class, GlyphLayout::new);
        boolean ints = font.usesIntegerPositions();
        font.setUseIntegerPositions(false);

        float actualScale = Math.max(0.5f, Math.min(3f, scale));
        font.getData().setScale(actualScale / 4f / Scl.scl(1f));
        layout.setText(font, text);

        float width = layout.width;
        float dx = centerX;
        float dy = lineY;

        font.setColor(color);
        font.draw(text, dx, dy + layout.height + 1f, Align.center);
        dy -= 1f;

        if(drawLine){
            float lineWidth = Math.max(1f, 2f * actualScale);
            Lines.stroke(lineWidth, Color.darkGray);
            Lines.line(dx - layout.width / 2f - 2f, dy, dx + layout.width / 2f + 1.5f, dy);
            Lines.stroke(Math.max(1f, actualScale), color);
            Lines.line(dx - layout.width / 2f - 2f, dy, dx + layout.width / 2f + 1.5f, dy);
        }

        drawPointerArrow(dx, lineY - 3f, dx - layout.width / 2f - 4f, lineY + layout.height + 4f, targetX, targetY, color, actualScale);

        font.setUseIntegerPositions(ints);
        font.setColor(Color.white);
        font.getData().setScale(1f);
        Draw.reset();
        Pools.free(layout);
        return width;
    }

    private void drawPointerArrow(float centerX, float minY, float minX, float maxY, float targetX, float targetY, Color color, float scale){
        float maxX = centerX * 2f - minX;
        float anchorX = clamp(targetX, minX, maxX);
        float anchorY = clamp(targetY, minY, maxY);
        float vx = targetX - anchorX;
        float vy = targetY - anchorY;
        float len = (float)Math.sqrt(vx * vx + vy * vy);
        if(len < 0.001f) return;

        vx /= len;
        vy /= len;

        // Keep arrows compact and never overshoot the target direction distance.
        float lineLen = Math.max(2.5f, Math.min(7f, len - 2f));
        if(lineLen <= 2.5f) return;

        float startX = anchorX + vx * 0.8f;
        float startY = anchorY + vy * 0.8f;
        float endX = startX + vx * lineLen;
        float endY = startY + vy * lineLen;

        float w = Math.max(0.8f, 0.75f * scale);
        Lines.stroke(w + 0.7f, Color.darkGray);
        Lines.line(startX, startY, endX, endY);
        Lines.stroke(w, color);
        Lines.line(startX, startY, endX, endY);

        float head = Math.max(2.4f, 2.2f * scale);
        float wing = Math.max(1.6f, 1.6f * scale);
        float bx = endX - vx * head;
        float by = endY - vy * head;
        float lx = bx - vy * wing;
        float ly = by + vx * wing;
        float rx = bx + vy * wing;
        float ry = by - vx * wing;

        Draw.color(Color.darkGray);
        Fill.tri(endX, endY, lx, ly, rx, ry);
        Draw.color(color);
        Fill.tri(endX, endY, lx * 0.88f + endX * 0.12f, ly * 0.88f + endY * 0.12f, rx * 0.88f + endX * 0.12f, ry * 0.88f + endY * 0.12f);
        Draw.reset();
    }

    private float clamp(float v, float min, float max){
        return Math.max(min, Math.min(max, v));
    }

    private void drawCenteredText(Building build, String text, Color color, float scale){
        if(build == null || build.block == null || text == null || text.isEmpty()) return;
        if(mindustry.Vars.renderer.pixelate) return;

        Font font = Fonts.outline;
        GlyphLayout layout = Pools.obtain(GlyphLayout.class, GlyphLayout::new);
        boolean ints = font.usesIntegerPositions();
        font.setUseIntegerPositions(false);

        float actualScale = Math.max(0.5f, Math.min(3f, scale));
        font.getData().setScale(actualScale / 4f / Scl.scl(1f));
        layout.setText(font, text);

        font.setColor(color);
        font.draw(text, build.x, build.y + layout.height * 0.5f, Align.center);

        font.setUseIntegerPositions(ints);
        font.setColor(Color.white);
        font.getData().setScale(1f);
        Draw.reset();
        Pools.free(layout);
    }

    private void showHotkeyCaptureDialog(String settingName){
        BaseDialog dialog = new BaseDialog(Core.bundle.get("wutb.hotkey.capture.title"));
        dialog.addCloseButton();

        dialog.cont.add(Core.bundle.get("wutb.hotkey.capture.hint")).pad(8f).row();
        Label preview = dialog.cont.add(displayKeyName(Core.settings.getString(settingName, defaultHotkey)))
        .style(Styles.outlineLabel).pad(8f).get();
        dialog.cont.row();

        dialog.update(() -> {
            if(Core.input.keyTap(KeyCode.escape)){
                dialog.hide();
                return;
            }

            for(KeyCode code : KeyCode.all){
                if(!isCaptureKeyCandidate(code)) continue;
                if(!Core.input.keyTap(code)) continue;

                Core.settings.put(settingName, normalizeStoredKeyName(code));
                hotkey = code;
                preview.setText(displayKeyName(normalizeStoredKeyName(code)));
                dialog.hide();
                return;
            }
        });

        dialog.show();
    }

    private static boolean isCaptureKeyCandidate(KeyCode code){
        if(code == null || code == KeyCode.unset) return false;
        String n = code.name();
        if(n == null) return false;
        if(n.equalsIgnoreCase("anykey") || n.equalsIgnoreCase("unknown")) return false;
        String low = n.toLowerCase(Locale.ROOT);
        return !low.startsWith("mouse");
    }

    private static String normalizeStoredKeyName(KeyCode code){
        return code.name().toLowerCase(Locale.ROOT);
    }

    private static String displayKeyName(String stored){
        if(stored == null || stored.isEmpty()){
            return Core.bundle.get("wutb.hotkey.unset");
        }
        String s = stored.trim().toLowerCase(Locale.ROOT);

        if(s.startsWith("num") && s.length() == 4 && Character.isDigit(s.charAt(3))){
            return String.valueOf(s.charAt(3));
        }
        if(s.equals("num0")) return "0";
        if(s.equals("backtick") || s.equals("grave")) return "`";
        if(s.equals("minus")) return "-";
        if(s.equals("equals")) return "=";
        if(s.equals("comma")) return ",";
        if(s.equals("period")) return ".";
        if(s.equals("space")) return "SPACE";

        return s.toUpperCase(Locale.ROOT);
    }

    private static KeyCode parseKeyCode(String raw){
        if(raw == null) return null;
        String normalized = raw.trim();
        if(normalized.isEmpty()) return null;

        normalized = normalized.replace('-', '_').replace(' ', '_');

        if(normalized.length() == 1 && Character.isDigit(normalized.charAt(0))){
            try{
                return KeyCode.valueOf("num" + normalized);
            }catch(Throwable ignored){
            }
        }

        try{
            return KeyCode.valueOf(normalized);
        }catch(Throwable ignored){
        }

        try{
            return KeyCode.valueOf(normalized.toLowerCase(Locale.ROOT));
        }catch(Throwable ignored){
        }

        try{
            return KeyCode.valueOf(normalized.toUpperCase(Locale.ROOT));
        }catch(Throwable ignored){
        }

        for(KeyCode code : KeyCode.all){
            if(code == null) continue;
            if(code.name().equalsIgnoreCase(normalized)) return code;
        }
        return null;
    }

    private static final class ProcessorMatch{
        final LogicBuild processor;
        final String text;
        final int line;

        ProcessorMatch(LogicBuild processor, String text, int line){
            this.processor = processor;
            this.text = text;
            this.line = line;
        }
    }

    private static final class TextMetrics{
        final float width;
        final float height;

        TextMetrics(float width, float height){
            this.width = width;
            this.height = height;
        }
    }

    private static final class LabelPlacement{
        final float centerX;
        final float lineY;
        final float minX;
        final float minY;
        final float maxX;
        final float maxY;

        LabelPlacement(float centerX, float lineY, float width, float height){
            this.centerX = centerX;
            this.lineY = lineY;
            this.minX = centerX - width / 2f - 4f;
            this.maxX = centerX + width / 2f + 4f;
            this.minY = lineY - 3f;
            this.maxY = lineY + height + 4f;
        }
    }

    private static final class RectArea{
        final int ownerId;
        final float minX;
        final float minY;
        final float maxX;
        final float maxY;

        RectArea(int ownerId, float minX, float minY, float maxX, float maxY){
            this.ownerId = ownerId;
            this.minX = minX;
            this.minY = minY;
            this.maxX = maxX;
            this.maxY = maxY;
        }
    }

    private class HotkeySetting extends SettingsMenuDialog.SettingsTable.Setting{
        private final String defaultValue;

        HotkeySetting(String name, String defaultValue){
            super(name);
            this.defaultValue = defaultValue;
        }

        @Override
        public void add(SettingsMenuDialog.SettingsTable table){
            Table row = new Table(VscodeSettingsStyle.cardBackground());
            row.left().margin(10f);
            row.defaults().pad(3f);
            row.image(Icon.settingsSmall).size(20f).padRight(8f);
            row.add(title).left().growX().minWidth(0f).wrap();

            TextButton capture = row.button("", Styles.flatt, () -> showHotkeyCaptureDialog(name))
                .minWidth(180f)
                .height(RbmStyle.buttonHeight())
                .padLeft(8f)
                .get();
            capture.update(() -> capture.setText(displayKeyName(Core.settings.getString(name, defaultValue))));

            addDesc(table.add(row).left().width(RbmStyle.rowWidth()).padTop(6f).get());
            table.row();
        }
    }
}
