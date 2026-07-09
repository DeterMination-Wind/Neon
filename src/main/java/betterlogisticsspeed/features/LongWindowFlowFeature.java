package betterlogisticsspeed.features;

import arc.Core;
import arc.Events;
import arc.func.Prov;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Font;
import arc.graphics.g2d.GlyphLayout;
import arc.graphics.g2d.Lines;
import arc.graphics.g2d.TextureRegion;
import arc.input.KeyBind;
import arc.input.KeyCode;
import arc.scene.Element;
import arc.scene.Group;
import arc.scene.event.Touchable;
import arc.scene.style.TextureRegionDrawable;
import arc.scene.ui.Image;
import arc.scene.ui.Label;
import arc.scene.ui.layout.Scl;
import arc.scene.ui.layout.Table;
import arc.struct.ObjectMap;
import arc.struct.ObjectSet;
import arc.struct.Seq;
import arc.util.Align;
import arc.util.Log;
import arc.util.Scaling;
import arc.util.Strings;
import arc.util.Time;
import arc.util.pooling.Pools;
import mindustry.game.EventType;
import mindustry.gen.Building;
import mindustry.gen.Icon;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.type.Category;
import mindustry.type.Item;
import mindustry.ui.Displayable;
import mindustry.ui.Fonts;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.SettingsMenuDialog;
import mindustry.world.Block;
import mindustry.world.meta.StatUnit;
import mindustry.world.modules.ItemModule;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.Arrays;

import static mindustry.Vars.content;
import static mindustry.Vars.control;
import static mindustry.Vars.headless;
import static mindustry.Vars.state;
import static mindustry.Vars.tilesize;
import static mindustry.Vars.ui;

public class LongWindowFlowFeature {

    private static final String keyEnabled = "bls-enabled";
    private static final String keyWindowSeconds = "bls-window-seconds";
    private static final String keyShowTotal = "bls-show-total";
    private static final String keyDecimals = "bls-decimals";

    private static final String extraTableName = "bls-flow-extra";
    private static final String overlayWindowName = "betterLogisticsSpeed";
    private static final int maxMarkedNodes = 12;
    private static final long sampleIntervalMs = 250L;
    private static final long overlayRebuildIntervalMs = 250L;

    private static final KeyBind markNodeKey = KeyBind.add("bls-mark-node", KeyCode.semicolon, "betterlogisticsspeed");
    private static final KeyBind deleteHoveredNodeKey = KeyBind.add("bls-delete-hovered-node", KeyCode.apostrophe, "betterlogisticsspeed");

    private static boolean inited;
    private static boolean reflectReady;

    private static boolean enabled;
    private static boolean showTotal;
    private static int windowSeconds;
    private static int decimals;

    private static Field fieldTopTable;
    private static Field fieldMenuHoverBlock;
    private static Field fieldHover;
    private static Field fieldNextFlowBuild;

    private static final ObjectSet<TextureRegion> itemIconRegions = new ObjectSet<>();
    private static boolean itemIconCacheReady;
    private static int itemIconCacheSize = -1;

    private static Building trackedBuild;
    private static BuildFlowTracker trackedTracker;

    private static final Seq<MarkedNode> markedNodes = new Seq<>();
    private static Table overlayTable;
    private static boolean overlayRegistered;
    private static boolean overlayDirty = true;
    private static long lastOverlayRebuildMs;
    private static long nextOverlayRegisterAttemptMs;

    // ---------- MI2-Utilities-Java reflection bridge ----------
    // Activates only when the user enables "MI2UI.replaceTopTable" inside MI2U.
    // In that mode MI2U pulls the vanilla hover info out of PlacementFragment.topTable
    // and re-runs build.display(...) inside HoverTopTable.buildt, while overwriting
    // PlacementFragment.hover with a Unit. We follow the build into hoverInfo.buildt
    // so the moving-average row still appears beneath the per-second flow row.
    private static boolean mi2uAvailable;
    private static boolean mi2uClassMissing;
    private static Object mi2uMi2uiInstance;
    private static Field mi2uMi2uiSettingsField;
    private static Method mi2uSettingsGetBool;
    private static Field mi2uHoverInfoField;
    private static Field mi2uHoverBuildField;
    private static Field mi2uHoverBuildtField;

    public static void init() {
        if (inited) return;
        inited = true;

        Core.settings.defaults(keyEnabled, true);
        Core.settings.defaults(keyWindowSeconds, 10);
        Core.settings.defaults(keyShowTotal, true);
        Core.settings.defaults(keyDecimals, 1);
        refreshSettings();

        Events.on(EventType.ClientLoadEvent.class, e -> {
            refreshSettings();
            tryInitReflection();
            probeMi2u();
            rebuildItemIconCache();
            clearTracking();
            clearPatchedRows();
            Time.runTask(2f, LongWindowFlowFeature::ensureOverlayRegistered);
        });

        Events.on(EventType.WorldLoadEvent.class, e -> {
            clearTracking();
            clearPatchedRows();
        });
        Events.on(EventType.ResetEvent.class, e -> {
            clearTracking();
            clearPatchedRows();
        });

        Events.run(EventType.Trigger.update, () -> {
            refreshSettings();
            ensureOverlayRegistered();
            updateInput();
            updateTracking();
            updateOverlayTable();
        });

        Events.run(EventType.Trigger.draw, LongWindowFlowFeature::drawWorldMarkers);
        Events.run(EventType.Trigger.uiDrawEnd, LongWindowFlowFeature::updateDisplay);
    }

    public static void buildSettings(SettingsMenuDialog.SettingsTable table) {
        table.checkPref(keyEnabled, true);
        table.sliderPref(keyWindowSeconds, 10, 2, 60, 1, i -> i + "s");
        table.checkPref(keyShowTotal, true);
        table.sliderPref(keyDecimals, 1, 1, 2, 1, String::valueOf);
        if (!OverlayCompat.hasOverlayUI()) {
            table.row();
            table.add(Core.bundle.get("bls.overlay.missing")).width(520f).left().wrap().padTop(8f);
        }
        refreshSettings();
    }

    private static void refreshSettings() {
        enabled = Core.settings.getBool(keyEnabled, true);
        showTotal = Core.settings.getBool(keyShowTotal, true);
        windowSeconds = clamp(Core.settings.getInt(keyWindowSeconds, 10), 2, 60);
        decimals = clamp(Core.settings.getInt(keyDecimals, 1), 1, 2);

        if (trackedTracker != null) {
            trackedTracker.setWindowSeconds(windowSeconds);
        }
        for (MarkedNode node : markedNodes) {
            node.tracker.setWindowSeconds(windowSeconds);
        }
    }

    private static void updateInput() {
        if (!enabled || !isInGame() || Core.scene == null || Core.scene.hasKeyboard()) return;

        if (Core.input.keyTap(markNodeKey)) {
            markHoveredNode();
        }

        if (Core.input.keyTap(deleteHoveredNodeKey)) {
            deleteHoveredOverlayRow();
        }
    }

    private static void markHoveredNode() {
        if (!overlayRegistered) {
            showToast(Core.bundle.get("bls.toast.overlay-unavailable"));
            return;
        }

        Building build = currentFlowBuild();
        if (!isTrackable(build)) {
            showToast(Core.bundle.get("bls.toast.no-node"));
            return;
        }

        MarkedNode existing = findMarkedNode(build);
        if (existing != null) {
            BuildFlowTracker tracker = new BuildFlowTracker(windowSeconds);
            existing.setTracker(tracker);
            tracker.sample(build, Time.millis(), 0L);
            overlayDirty = true;
            showToast(Core.bundle.get("bls.toast.reset"));
            return;
        }

        if (markedNodes.size >= maxMarkedNodes) {
            showToast(Core.bundle.format("bls.toast.max", maxMarkedNodes));
            return;
        }

        BuildFlowTracker tracker = new BuildFlowTracker(windowSeconds);
        FlowBinding binding = installMarkedFlow(build, tracker);
        if (binding == null) {
            showToast(Core.bundle.get("bls.toast.unsupported-node"));
            return;
        }

        MarkedNode node = new MarkedNode(build, tracker, binding);
        tracker.sample(build, Time.millis(), 0L);
        markedNodes.add(node);
        overlayDirty = true;
        showToast(Core.bundle.format("bls.toast.marked", markedNodes.size));
    }

    private static void deleteHoveredOverlayRow() {
        if (!overlayRegistered || markedNodes.isEmpty()) return;

        for (int i = 0; i < markedNodes.size; i++) {
            MarkedNode node = markedNodes.get(i);
            if (!node.rowHovered) continue;

            node.uninstall();
            markedNodes.remove(i);
            overlayDirty = true;
            showToast(Core.bundle.get("bls.toast.removed"));
            return;
        }
    }

    private static void updateTracking() {
        if (!enabled || !isInGame()) {
            clearTracking();
            return;
        }

        tryInitReflection();
        if (!reflectReady) {
            clearHoverTracking();
            return;
        }

        Building next = currentFlowBuild();
        if (next == null) {
            clearHoverTracking();
        } else {
            MarkedNode marked = findMarkedNode(next);
            if (marked != null) {
                clearHoverTracking();
                marked.tracker.sample(next, Time.millis(), sampleIntervalMs);
                updateMarkedNodes();
                return;
            }

            if (trackedBuild != next || trackedTracker == null) {
                trackedBuild = next;
                trackedTracker = new BuildFlowTracker(windowSeconds);
            }
            trackedTracker.sample(next, Time.millis(), sampleIntervalMs);
        }

        updateMarkedNodes();
    }

    private static void updateMarkedNodes() {
        long now = Time.millis();

        for (int i = markedNodes.size - 1; i >= 0; i--) {
            MarkedNode node = markedNodes.get(i);
            if (!isTrackable(node.build)) {
                node.uninstall();
                markedNodes.remove(i);
                overlayDirty = true;
                continue;
            }

            node.tracker.sample(node.build, now, sampleIntervalMs);
        }
    }

    private static void updateDisplay() {
        if (ui == null || ui.hudfrag == null || ui.hudfrag.blockfrag == null || state == null || !state.isGame()) {
            clearPatchedRows();
            return;
        }

        tryInitReflection();
        if (!reflectReady) {
            clearPatchedRows();
            return;
        }

        probeMi2u();
        boolean mi2uMode = isMi2uReplaceActive();

        try {
            Object pf = ui.hudfrag.blockfrag;
            Table topTable = (Table) fieldTopTable.get(pf);
            if (topTable == null) {
                clearPatchedRows();
                return;
            }

            Table searchRoot;
            Building displayBuild;

            if (mi2uMode) {
                Table mi2uBuildt = getMi2uBuildt();
                Building hoverBuild = getMi2uHoverBuild();
                if (mi2uBuildt == null || hoverBuild == null) {
                    clearPatchedRows();
                    return;
                }
                searchRoot = mi2uBuildt;
                displayBuild = hoverBuild;
                hideInjectedRows(topTable);
            } else {
                Block menuHoverBlock = (Block) fieldMenuHoverBlock.get(pf);
                Displayable hover = (Displayable) fieldHover.get(pf);

                if (menuHoverBlock != null || control.input.block != null || !(hover instanceof Building)) {
                    clearPatchedRows();
                    return;
                }

                searchRoot = topTable;
                displayBuild = (Building) hover;
                hideInjectedRows(getMi2uBuildt());
            }

            BuildFlowTracker displayTracker = trackerFor(displayBuild);
            if (!enabled || displayTracker == null || displayBuild.flowItems() == null) {
                hideInjectedRows(searchRoot);
                return;
            }

            Table flowTable = findItemFlowTable(searchRoot);
            if (flowTable == null) {
                hideInjectedRows(searchRoot);
                return;
            }

            Table extra = (Table) flowTable.find(extraTableName);
            if (extra == null) {
                flowTable.row();
                extra = flowTable.table(t -> t.left().marginTop(1f)).left().padTop(1f).colspan(2).get();
                extra.name = extraTableName;
            }
            extra.visible = true;

            rebuildExtraRows(extra, displayBuild.flowItems(), displayTracker);
        } catch (Throwable ignored) {
            clearPatchedRows();
        }
    }

    private static void rebuildExtraRows(Table extra, ItemModule flowItems, BuildFlowTracker tracker) {
        if (extra == null || flowItems == null || tracker == null) return;

        extra.clearChildren();
        extra.left();

        long now = Time.millis();
        String speedLabel = Core.bundle.format("bls.flow.avg.label", windowSeconds);
        String perSecond = " " + StatUnit.perSecond.localized();

        float total = 0f;
        int totalCount = 0;
        int shown = 0;

        for (Item item : content.items()) {
            if (!flowItems.hasFlowItem(item)) continue;

            float avg = tracker.average(item.id, now);
            String valueText = avg < 0f
                ? Core.bundle.get("bls.flow.na")
                : Strings.fixed(avg, decimals) + perSecond;

            extra.image(item.uiIcon).scaling(Scaling.fit).padRight(3f);
            Label line = extra.add(speedLabel + ": " + valueText).left().color(Color.lightGray).get();
            line.setWrap(false);
            extra.row();

            if (avg >= 0f) {
                total += avg;
                totalCount++;
            }
            shown++;
        }

        if (showTotal && shown > 0) {
            String valueText = totalCount == 0
                ? Core.bundle.get("bls.flow.na")
                : Strings.fixed(total, decimals) + perSecond;
            extra.add(Core.bundle.get("bls.flow.total.label") + ": " + valueText)
                .left()
                .color(Color.lightGray)
                .colspan(2);
            extra.row();
        }
    }

    private static void hideInjectedRows(Table topTable) {
        if (topTable == null) return;

        try {
            Element extra = topTable.find(extraTableName);
            if (extra instanceof Table) {
                Table table = (Table) extra;
                table.clearChildren();
                table.visible = false;
            }
        } catch (Throwable ignored) {
        }
    }

    private static void clearPatchedRows() {
        try {
            if (ui != null && ui.hudfrag != null && ui.hudfrag.blockfrag != null && reflectReady) {
                Object pf = ui.hudfrag.blockfrag;
                hideInjectedRows((Table) fieldTopTable.get(pf));
            }
            hideInjectedRows(getMi2uBuildt());
        } catch (Throwable ignored) {
        }
    }

    private static void ensureOverlayRegistered() {
        if (overlayRegistered || headless) return;

        long now = Time.millis();
        if (now < nextOverlayRegisterAttemptMs) return;
        nextOverlayRegisterAttemptMs = now + 1000L;

        ensureOverlayTable();
        overlayRegistered = OverlayCompat.tryRegister(
            overlayWindowName,
            overlayTable,
            () -> state != null && state.isGame() && enabled,
            true,
            true
        );
        if (!overlayRegistered && !OverlayCompat.hasOverlayUI()) {
            clearMarkedNodes();
        }
    }

    private static void ensureOverlayTable() {
        if (overlayTable != null) return;

        overlayTable = new OverlayContentTable();
        overlayTable.touchable = Touchable.childrenOnly;
        overlayTable.left();
        overlayTable.margin(6f);
        overlayTable.update(() -> {
            long now = Time.millis();
            if (overlayDirty || now - lastOverlayRebuildMs >= overlayRebuildIntervalMs) {
                rebuildOverlayTable();
            }
        });
        overlayDirty = true;
    }

    private static void updateOverlayTable() {
        if (overlayTable == null) return;
        long now = Time.millis();
        if (overlayDirty || now - lastOverlayRebuildMs >= overlayRebuildIntervalMs) {
            rebuildOverlayTable();
        }
    }

    private static void rebuildOverlayTable() {
        if (overlayTable == null) return;

        overlayDirty = false;
        lastOverlayRebuildMs = Time.millis();
        for (MarkedNode node : markedNodes) {
            node.rowHovered = false;
        }

        overlayTable.clearChildren();
        overlayTable.left();
        overlayTable.defaults().left();

        if (!enabled) {
            overlayTable.add(Core.bundle.get("bls.overlay.disabled")).color(Color.lightGray).left().pad(4f);
            return;
        }

        if (markedNodes.isEmpty()) {
            overlayTable.add(Core.bundle.get("bls.overlay.empty")).color(Color.lightGray).left().pad(4f);
        } else {
            for (int i = 0; i < markedNodes.size; i++) {
                MarkedNode node = markedNodes.get(i);
                Table row = new Table();
                row.touchable = Touchable.enabled;
                row.left();
                buildOverlayRow(row, node, i);
                row.update(() -> node.rowHovered = row.hasMouse());
                overlayTable.add(row).growX().height(28f).padBottom(2f).row();
            }
        }

        overlayTable.row();
        overlayTable.button(Core.bundle.get("bls.overlay.clear"), Styles.cleart, LongWindowFlowFeature::clearMarkedNodes)
            .growX()
            .height(34f)
            .padTop(4f);
    }

    private static void buildOverlayRow(Table row, MarkedNode node, int index) {
        row.add("[accent]" + (index + 1)).width(24f).center();
        if (node.build != null && node.build.block != null && node.build.block.uiIcon != null) {
            row.image(node.build.block.uiIcon).scaling(Scaling.fit).size(18f).padRight(5f);
        }

        long now = Time.millis();
        boolean addedAny = false;

        if (showTotal) {
            row.add(Core.bundle.get("bls.overlay.total") + rateText(node.totalAverage(now))).left().padRight(8f);
            addedAny = true;
        }

        for (Item item : content.items()) {
            float avg = node.tracker.average(item.id, now);
            if (avg < 0f) continue;

            row.image(item.uiIcon).scaling(Scaling.fit).size(14f).padLeft(2f).padRight(2f);
            row.add(rateText(avg)).left().padRight(6f);
            addedAny = true;
        }

        if (!addedAny) {
            row.add(Core.bundle.get("bls.flow.na")).color(Color.lightGray).left();
        }
    }

    private static String rateText(float value) {
        if (value < 0f) return Core.bundle.get("bls.flow.na");
        return Strings.fixed(value, decimals) + " " + StatUnit.perSecond.localized();
    }

    private static void drawWorldMarkers() {
        if (!enabled || !isInGame() || markedNodes.isEmpty()) return;

        Draw.z(Layer.overlayUI);
        for (int i = 0; i < markedNodes.size; i++) {
            MarkedNode node = markedNodes.get(i);
            Building build = node.build;
            if (!isTrackable(build)) continue;

            float radius = Math.max(5f, build.block.size * tilesize * 0.58f);
            float x = build.x;
            float y = build.y;
            float labelX = x + radius * 0.68f;
            float labelY = y + radius * 0.68f;

            Draw.color(Pal.accent, 0.72f);
            Lines.stroke(1.4f);
            Lines.square(x, y, radius, 45f);

            Draw.color(Color.black, 0.78f);
            Fill.circle(labelX, labelY, 5.2f);
            Draw.color(Pal.accent);
            Lines.stroke(1.2f);
            Lines.circle(labelX, labelY, 5.2f);
            drawMarkerText(String.valueOf(i + 1), labelX, labelY);
        }
        Draw.reset();
    }

    private static void drawMarkerText(String text, float x, float y) {
        Font font = Fonts.outline;
        GlyphLayout layout = Pools.obtain(GlyphLayout.class, GlyphLayout::new);
        boolean ints = font.usesIntegerPositions();
        float oldScaleX = font.getData().scaleX;
        float oldScaleY = font.getData().scaleY;
        font.setUseIntegerPositions(false);
        font.getData().setScale(0.22f / Scl.scl(1f));
        layout.setText(font, text);
        font.setColor(Color.white);
        font.draw(text, x, y + layout.height / 2f, Align.center);
        font.setColor(Color.white);
        font.getData().setScale(oldScaleX, oldScaleY);
        font.setUseIntegerPositions(ints);
        Pools.free(layout);
    }

    private static Table findItemFlowTable(Table root) {
        if (root == null) return null;

        String perSecond = StatUnit.perSecond.localized();
        Seq<Element> stack = new Seq<>();
        stack.add(root);

        while (stack.size > 0) {
            Element element = stack.pop();

            if (element instanceof Table) {
                Table table = (Table) element;
                if (isItemFlowTableCandidate(table, perSecond)) {
                    return table;
                }
            }

            if (element instanceof Group) {
                Group group = (Group) element;
                for (Element child : group.getChildren()) {
                    if (child != null) {
                        stack.add(child);
                    }
                }
            }
        }

        return null;
    }

    private static boolean isItemFlowTableCandidate(Table table, String perSecondLocalized) {
        if (table == null) return false;
        if (extraTableName.equals(table.name)) return false;

        int itemIconCount = 0;
        int speedLabelCount = 0;

        for (Element child : table.getChildren()) {
            if (child instanceof Image) {
                if (isItemIcon((Image) child)) {
                    itemIconCount++;
                }
            } else if (child instanceof Label) {
                String text;
                try {
                    text = ((Label) child).getText() == null ? "" : ((Label) child).getText().toString();
                } catch (Throwable ignored) {
                    text = "";
                }

                String stripped = Strings.stripColors(text);
                if (stripped != null && stripped.contains(perSecondLocalized)) {
                    speedLabelCount++;
                }
            }
        }

        return itemIconCount > 0 && speedLabelCount > 0;
    }

    private static boolean isItemIcon(Image image) {
        if (image == null) return false;
        if (!(image.getDrawable() instanceof TextureRegionDrawable)) return false;

        TextureRegion region = ((TextureRegionDrawable) image.getDrawable()).getRegion();
        if (region == null) return false;
        ensureItemIconCache();

        return itemIconRegions.contains(region);
    }

    private static void ensureItemIconCache() {
        if (!itemIconCacheReady || itemIconCacheSize != content.items().size) {
            rebuildItemIconCache();
        }
    }

    private static void rebuildItemIconCache() {
        itemIconRegions.clear();
        for (Item item : content.items()) {
            if (item != null && item.uiIcon != null) {
                itemIconRegions.add(item.uiIcon);
            }
        }
        itemIconCacheReady = true;
        itemIconCacheSize = content.items().size;
    }

    private static void tryInitReflection() {
        if (reflectReady) return;

        try {
            Class<?> cls = Class.forName("mindustry.ui.fragments.PlacementFragment");

            fieldTopTable = cls.getDeclaredField("topTable");
            fieldMenuHoverBlock = cls.getDeclaredField("menuHoverBlock");
            fieldHover = cls.getDeclaredField("hover");
            fieldNextFlowBuild = cls.getDeclaredField("nextFlowBuild");

            fieldTopTable.setAccessible(true);
            fieldMenuHoverBlock.setAccessible(true);
            fieldHover.setAccessible(true);
            fieldNextFlowBuild.setAccessible(true);

            reflectReady = true;
        } catch (Throwable ignored) {
            reflectReady = false;
        }
    }

    private static Building currentFlowBuild() {
        if (ui == null || ui.hudfrag == null || ui.hudfrag.blockfrag == null) return null;

        tryInitReflection();
        if (!reflectReady) return null;

        try {
            Object pf = ui.hudfrag.blockfrag;
            Building next = (Building) fieldNextFlowBuild.get(pf);
            return isTrackable(next) ? next : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static BuildFlowTracker trackerFor(Building build) {
        MarkedNode marked = findMarkedNode(build);
        if (marked != null) return marked.tracker;
        return build != null && build == trackedBuild ? trackedTracker : null;
    }

    private static MarkedNode findMarkedNode(Building build) {
        if (build == null) return null;
        for (MarkedNode node : markedNodes) {
            if (node.build == build) return node;
        }
        return null;
    }

    private static boolean isTrackable(Building build) {
        try {
            return build != null &&
                build.isValid() &&
                !build.dead() &&
                build.block != null &&
                build.block.displayFlow &&
                (build.block.category == Category.distribution || build.block.category == Category.liquid) &&
                build.flowItems() != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isInGame() {
        return state != null && state.isGame();
    }

    private static void showToast(String text) {
        try {
            if (ui != null && ui.hudfrag != null) {
                ui.hudfrag.showToast(Icon.info, text);
            } else if (ui != null) {
                ui.showInfoFade(text, 2f);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void probeMi2u() {
        if (mi2uAvailable || mi2uClassMissing) return;

        try {
            Class<?> vars = Class.forName("mi2u.MI2UVars");
            Field varsField = vars.getDeclaredField("mi2ui");
            varsField.setAccessible(true);
            Object instance = varsField.get(null);
            if (instance == null) return;

            Class<?> mindow2 = Class.forName("mi2u.ui.elements.Mindow2");
            Field settingsField = mindow2.getDeclaredField("settings");
            settingsField.setAccessible(true);

            Class<?> settingHandler = Class.forName("mi2u.io.SettingHandler");
            Method getBool = settingHandler.getMethod("getBool", String.class);

            Class<?> hover = Class.forName("mi2u.ui.HoverTopTable");
            Field hoverInfoField = hover.getDeclaredField("hoverInfo");
            hoverInfoField.setAccessible(true);
            Field buildField = hover.getDeclaredField("build");
            buildField.setAccessible(true);
            Field buildtField = hover.getDeclaredField("buildt");
            buildtField.setAccessible(true);

            mi2uMi2uiInstance = instance;
            mi2uMi2uiSettingsField = settingsField;
            mi2uSettingsGetBool = getBool;
            mi2uHoverInfoField = hoverInfoField;
            mi2uHoverBuildField = buildField;
            mi2uHoverBuildtField = buildtField;
            mi2uAvailable = true;
        } catch (ClassNotFoundException notFound) {
            mi2uClassMissing = true;
        } catch (NoClassDefFoundError notFoundDef) {
            mi2uClassMissing = true;
        } catch (Throwable ignored) {
        }
    }

    private static boolean isMi2uReplaceActive() {
        if (!mi2uAvailable) return false;
        try {
            Object handler = mi2uMi2uiSettingsField.get(mi2uMi2uiInstance);
            if (handler == null) return false;
            Object value = mi2uSettingsGetBool.invoke(handler, "replaceTopTable");
            return value instanceof Boolean && (Boolean) value;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Building getMi2uHoverBuild() {
        if (!mi2uAvailable) return null;
        try {
            Object hoverInfo = mi2uHoverInfoField.get(null);
            if (hoverInfo == null) return null;
            Object build = mi2uHoverBuildField.get(hoverInfo);
            return build instanceof Building ? (Building) build : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Table getMi2uBuildt() {
        if (!mi2uAvailable) return null;
        try {
            Object hoverInfo = mi2uHoverInfoField.get(null);
            if (hoverInfo == null) return null;
            Object buildt = mi2uHoverBuildtField.get(hoverInfo);
            return buildt instanceof Table ? (Table) buildt : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void clearTracking() {
        clearHoverTracking();
        clearMarkedNodes();
    }

    private static void clearHoverTracking() {
        trackedBuild = null;
        trackedTracker = null;
    }

    private static void clearMarkedNodes() {
        for (MarkedNode node : markedNodes) {
            node.uninstall();
        }
        markedNodes.clear();
        overlayDirty = true;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static class OverlayContentTable extends Table {
        @Override
        public float getMinWidth() {
            return 0f;
        }

        @Override
        public float getPrefWidth() {
            float pref = super.getPrefWidth();
            float parentWidth = parent != null ? parent.getWidth() : width;
            return parentWidth > 0.001f ? Math.max(pref, parentWidth) : pref;
        }
    }

    private static class MarkedNode {
        final Building build;
        final FlowBinding binding;
        BuildFlowTracker tracker;
        boolean rowHovered;

        MarkedNode(Building build, BuildFlowTracker tracker, FlowBinding binding) {
            this.build = build;
            this.tracker = tracker;
            this.binding = binding;
        }

        void setTracker(BuildFlowTracker tracker) {
            this.tracker = tracker;
            binding.setTracker(tracker);
        }

        void uninstall() {
            binding.restore();
        }

        float totalAverage(long nowMs) {
            float total = 0f;
            int count = 0;
            for (Item item : content.items()) {
                float avg = tracker.average(item.id, nowMs);
                if (avg < 0f) continue;
                total += avg;
                count++;
            }
            return count == 0 ? -1f : total;
        }
    }

    private static FlowBinding installMarkedFlow(Building build, BuildFlowTracker tracker) {
        try {
            ItemModule flow = build.flowItems();
            if (flow == null) return null;

            if (flow instanceof TrackedItemModule) {
                ((TrackedItemModule) flow).setTracker(tracker);
                return ((TrackedItemModule) flow).binding;
            }

            FlowSlot slot = findFlowSlot(build, flow);
            if (slot == null) return null;

            TrackedItemModule wrapper = new TrackedItemModule(flow, tracker);
            FlowBinding binding = new FlowBinding(build, slot, flow, wrapper);
            wrapper.binding = binding;
            slot.set(build, wrapper);
            return binding;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static FlowSlot findFlowSlot(Building build, ItemModule flow) {
        if (build == null || flow == null) return null;

        try {
            if (build.items == flow) {
                return DirectItemsSlot.instance;
            }
        } catch (Throwable ignored) {
        }

        Class<?> type = build.getClass();
        while (type != null) {
            for (Field field : type.getDeclaredFields()) {
                if (!ItemModule.class.isAssignableFrom(field.getType())) continue;

                try {
                    field.setAccessible(true);
                    if (field.get(build) == flow) {
                        return new FieldFlowSlot(field);
                    }
                } catch (Throwable ignored) {
                }
            }
            type = type.getSuperclass();
        }

        return null;
    }

    private interface FlowSlot {
        ItemModule get(Building build) throws IllegalAccessException;

        void set(Building build, ItemModule module) throws IllegalAccessException;
    }

    private static class DirectItemsSlot implements FlowSlot {
        static final DirectItemsSlot instance = new DirectItemsSlot();

        @Override
        public ItemModule get(Building build) {
            return build.items;
        }

        @Override
        public void set(Building build, ItemModule module) {
            build.items = module;
        }
    }

    private static class FieldFlowSlot implements FlowSlot {
        final Field field;

        FieldFlowSlot(Field field) {
            this.field = field;
        }

        @Override
        public ItemModule get(Building build) throws IllegalAccessException {
            return (ItemModule) field.get(build);
        }

        @Override
        public void set(Building build, ItemModule module) throws IllegalAccessException {
            field.set(build, module);
        }
    }

    private static class FlowBinding {
        final Building build;
        final FlowSlot slot;
        final ItemModule original;
        final TrackedItemModule wrapper;
        boolean restored;

        FlowBinding(Building build, FlowSlot slot, ItemModule original, TrackedItemModule wrapper) {
            this.build = build;
            this.slot = slot;
            this.original = original;
            this.wrapper = wrapper;
        }

        void setTracker(BuildFlowTracker tracker) {
            wrapper.setTracker(tracker);
        }

        void restore() {
            if (restored) return;
            restored = true;

            try {
                wrapper.setTracker(null);
                original.set(wrapper);
                if (slot.get(build) == wrapper) {
                    slot.set(build, original);
                }
            } catch (Throwable ignored) {
            }
        }
    }

    private static class TrackedItemModule extends ItemModule {
        BuildFlowTracker tracker;
        FlowBinding binding;

        TrackedItemModule(ItemModule original, BuildFlowTracker tracker) {
            set(original);
            this.tracker = tracker;
        }

        void setTracker(BuildFlowTracker tracker) {
            this.tracker = tracker;
        }

        @Override
        public void updateFlow() {
            BuildFlowTracker tracker = this.tracker;
            if (tracker != null) {
                tracker.sampleRecorded(Time.millis(), sampleIntervalMs);
            }
        }

        @Override
        public void stopFlow() {
        }

        @Override
        public float getFlowRate(Item item) {
            BuildFlowTracker tracker = this.tracker;
            return tracker == null ? -1f : tracker.currentRate(item.id, Time.millis());
        }

        @Override
        public boolean hasFlowItem(Item item) {
            BuildFlowTracker tracker = this.tracker;
            return tracker != null && tracker.hasItem(item.id, Time.millis());
        }

        @Override
        public void add(Item item, int amount) {
            super.add(item, amount);
            record(item, amount);
        }

        @Override
        public void add(ItemModule items) {
            super.add(items);
            if (items == null) return;
            for (Item item : content.items()) {
                int amount = items.get(item);
                if (amount > 0) record(item, amount);
            }
        }

        @Override
        public void handleFlow(Item item, int amount) {
            record(item, amount);
        }

        @Override
        public void undoFlow(Item item) {
            record(item, -1);
        }

        private void record(Item item, int amount) {
            BuildFlowTracker tracker = this.tracker;
            if (tracker != null && item != null && amount != 0) {
                tracker.record(item, amount);
            }
        }
    }

    private static class BuildFlowTracker {
        private final ObjectMap<Integer, ItemRateWindow> windows = new ObjectMap<>();
        private float[] pending = new float[content.items().size];
        private float[] lastRates = new float[content.items().size];
        private boolean[] seen = new boolean[content.items().size];
        private int windowSeconds;
        private long lastSampleMs;

        BuildFlowTracker(int windowSeconds) {
            this.windowSeconds = windowSeconds;
            Arrays.fill(lastRates, -1f);
        }

        void setWindowSeconds(int windowSeconds) {
            if (this.windowSeconds == windowSeconds) return;
            this.windowSeconds = windowSeconds;
            reset();
        }

        void sample(Building build, long nowMs, long sampleIntervalMs) {
            if (build == null || build.flowItems() == null) return;
            if (nowMs - lastSampleMs < sampleIntervalMs) return;

            ItemModule flow = build.flowItems();
            if (flow instanceof TrackedItemModule) {
                sampleRecorded(nowMs, sampleIntervalMs);
                return;
            }

            lastSampleMs = nowMs;
            long windowMs = windowSeconds * 1000L;

            try {
                flow.updateFlow();
            } catch (Throwable ignored) {
            }

            for (Item item : content.items()) {
                if (!flow.hasFlowItem(item)) continue;

                float rate = flow.getFlowRate(item);
                if (rate < 0f) continue;

                int itemId = item.id;
                ItemRateWindow window = windows.get(itemId);
                if (window == null) {
                    window = new ItemRateWindow();
                    windows.put(itemId, window);
                }
                window.add(nowMs, rate, windowMs);
                ensureCapacity();
                seen[itemId] = true;
                lastRates[itemId] = rate;
            }
        }

        void record(Item item, int amount) {
            if (item == null) return;
            ensureCapacity();
            if (item.id < 0 || item.id >= pending.length) return;

            pending[item.id] += amount;
            if (amount > 0) {
                seen[item.id] = true;
            }
        }

        void sampleRecorded(long nowMs, long sampleIntervalMs) {
            ensureCapacity();
            if (lastSampleMs == 0L) {
                lastSampleMs = nowMs;
                return;
            }
            if (nowMs - lastSampleMs < sampleIntervalMs) return;

            long elapsedMs = Math.max(1L, nowMs - lastSampleMs);
            lastSampleMs = nowMs;
            long windowMs = windowSeconds * 1000L;

            for (int itemId = 0; itemId < pending.length; itemId++) {
                float amount = pending[itemId];
                pending[itemId] = 0f;

                if (!seen[itemId] && amount <= 0f) continue;

                float rate = Math.max(amount, 0f) * 1000f / elapsedMs;
                ItemRateWindow window = windows.get(itemId);
                if (window == null) {
                    window = new ItemRateWindow();
                    windows.put(itemId, window);
                }
                window.add(nowMs, rate, windowMs);
                lastRates[itemId] = rate;
                if (rate > 0f) {
                    seen[itemId] = true;
                }
            }
        }

        float average(int itemId, long nowMs) {
            ItemRateWindow window = windows.get(itemId);
            if (window == null) return -1f;
            return window.average(nowMs, windowSeconds * 1000L);
        }

        float currentRate(int itemId, long nowMs) {
            if (!hasItem(itemId, nowMs)) return -1f;
            return itemId >= 0 && itemId < lastRates.length ? Math.max(lastRates[itemId], 0f) : -1f;
        }

        boolean hasItem(int itemId, long nowMs) {
            if (itemId < 0) return false;
            ItemRateWindow window = windows.get(itemId);
            return window != null && window.active(nowMs, windowSeconds * 1000L);
        }

        private void ensureCapacity() {
            int size = content.items().size;
            if (pending.length == size) return;

            pending = Arrays.copyOf(pending, size);
            int oldLength = lastRates.length;
            lastRates = Arrays.copyOf(lastRates, size);
            if (lastRates.length > oldLength) {
                Arrays.fill(lastRates, oldLength, lastRates.length, -1f);
            }
            seen = Arrays.copyOf(seen, size);
        }

        private void reset() {
            ensureCapacity();
            windows.clear();
            Arrays.fill(pending, 0f);
            Arrays.fill(lastRates, -1f);
            Arrays.fill(seen, false);
            lastSampleMs = 0L;
        }
    }

    private static class ItemRateWindow {
        private final ArrayDeque<SamplePoint> samples = new ArrayDeque<>();
        private float sum;

        void add(long timeMs, float value, long windowMs) {
            samples.addLast(new SamplePoint(timeMs, value));
            sum += value;
            trim(timeMs, windowMs);
        }

        float average(long nowMs, long windowMs) {
            trim(nowMs, windowMs);
            if (samples.size() < 2) return -1f;
            return Math.max(sum / samples.size(), 0f);
        }

        boolean active(long nowMs, long windowMs) {
            trim(nowMs, windowMs);
            return samples.size() >= 2;
        }

        private void trim(long nowMs, long windowMs) {
            while (!samples.isEmpty()) {
                SamplePoint first = samples.peekFirst();
                if (first == null || nowMs - first.timeMs <= windowMs) break;

                SamplePoint removed = samples.removeFirst();
                sum -= removed.value;
            }

            if (samples.isEmpty()) {
                sum = 0f;
            }
        }
    }

    private static class SamplePoint {
        final long timeMs;
        final float value;

        SamplePoint(long timeMs, float value) {
            this.timeMs = timeMs;
            this.value = value;
        }
    }

    private static class OverlayCompat {
        private static boolean loggedFailure;

        static boolean hasOverlayUI() {
            return classExists("mindustryX.features.ui.OverlayUI");
        }

        static boolean isMindustryX() {
            return classExists("mindustryX.VarsX") || classExists("mindustryX.loader.Main");
        }

        private static boolean classExists(String name) {
            try {
                Class.forName(name, false, OverlayCompat.class.getClassLoader());
                return true;
            } catch (ClassNotFoundException | LinkageError ignored) {
                return false;
            }
        }

        static boolean tryRegister(String name, Table table, Prov<Boolean> availability, boolean autoHeight, boolean resizable) {
            if (headless || Core.scene == null || table == null) return false;

            try {
                Class<?> overlayClass = Class.forName("mindustryX.features.ui.OverlayUI", true, OverlayCompat.class.getClassLoader());
                Object overlay = overlayClass.getField("INSTANCE").get(null);

                try {
                    Method init = overlayClass.getMethod("init");
                    init.invoke(overlay);
                } catch (NoSuchMethodException ignored) {
                }

                Method register = overlayClass.getMethod("registerWindow", String.class, Table.class);
                Object window = register.invoke(overlay, name, table);
                if (window == null) return false;

                Class<?> windowClass = window.getClass();
                windowClass.getMethod("setAvailability", Prov.class).invoke(window, availability);
                windowClass.getMethod("setAutoHeight", boolean.class).invoke(window, autoHeight);
                windowClass.getMethod("setResizable", boolean.class).invoke(window, resizable);

                return true;
            } catch (ClassNotFoundException | NoClassDefFoundError missing) {
                return false;
            } catch (Throwable error) {
                if (!loggedFailure) {
                    loggedFailure = true;
                    Log.err("[betterLogisticsSpeed] OverlayUI compatibility registration failed.", error);
                }
                return false;
            }
        }
    }
}
