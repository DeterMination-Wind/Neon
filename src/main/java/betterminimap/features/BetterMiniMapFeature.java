package betterminimap.features;

import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Angles;
import arc.math.Mathf;
import arc.math.Mat;
import arc.math.geom.Rect;
import arc.scene.Element;
import arc.scene.event.Touchable;
import arc.scene.ui.TextButton;
import arc.scene.ui.TextField;
import arc.scene.ui.layout.Scl;
import arc.scene.ui.layout.Table;
import arc.struct.ObjectSet;
import arc.struct.Seq;
import arc.util.Interval;
import arc.util.Tmp;
import mindustry.game.EventType;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.gen.Groups;
import mindustry.gen.Icon;
import mindustry.gen.Unit;
import mindustry.graphics.Pal;
import mindustry.type.UnitType;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.ui.dialogs.SettingsMenuDialog;
import mindustry.world.Block;

import static mindustry.Vars.content;
import static mindustry.Vars.player;
import static mindustry.Vars.renderer;
import static mindustry.Vars.state;
import static mindustry.Vars.tilesize;
import static mindustry.Vars.ui;
import static mindustry.Vars.world;

public class BetterMiniMapFeature {
    private static final String overlayName = "betterminimap-overlay";

    private static final String keyEnabled = "mmplus-enabled";

    private static final String keyUnitsEnabled = "mmplus-units-enabled";
    private static final String keyBuildingsEnabled = "mmplus-buildings-enabled";

    private static final String keyShowFriendlyUnits = "mmplus-units-friendly";
    private static final String keyShowEnemyUnits = "mmplus-units-enemy";
    private static final String keyShowFriendlyBuildings = "mmplus-buildings-friendly";
    private static final String keyShowEnemyBuildings = "mmplus-buildings-enemy";
    private static final String keyTintBuildingIcons = "mmplus-buildings-tint";

    private static final String keyUnitScale = "mmplus-unitScale";
    private static final String keyUnitSizeLegacy = "mmplus-unitSize";
    private static final String keyUnitAlpha = "mmplus-unitAlpha";
    private static final String keyUnitClusterPx = "mmplus-unitClusterPx";
    private static final String keyBuildingScale = "mmplus-buildingScale";
    private static final String keyIconAlpha = "mmplus-iconAlpha";
    private static final String keyIconBgAlpha = "mmplus-iconBgAlpha";

    private static final String keyUnitList = "mmplus-units";
    private static final String keyBlockList = "mmplus-blocks";

    private static final String keyFilterInit = "mmplus-filter-init";

    private static final Interval interval = new Interval(5);
    private static final int idSettings = 0;
    private static final int idAttach = 1;
    private static final int idVisible = 2;

    private static final float minimapBaseSize = 16f;
    private static final float settingsRefreshTime = 0.5f;
    private static final float attachRefreshTime = 1.0f;
    private static final float visibleRefreshTime = 0.25f;

    private static boolean inited;

    private static boolean enabled;
    private static boolean unitsEnabled;
    private static boolean buildingsEnabled;
    private static boolean showFriendlyUnits;
    private static boolean showEnemyUnits;
    private static boolean showFriendlyBuildings;
    private static boolean showEnemyBuildings;
    private static boolean tintBuildingIcons;
    private static float unitSizePx;
    private static float unitAlpha;
    private static float unitClusterPx;
    private static float buildingScale;
    private static float iconAlpha;
    private static float iconBgAlpha;

    private static boolean[] unitEnabledById;
    private static boolean[] blockEnabledById;

    private static final Seq<Unit> visibleUnits = new Seq<>(false, 256);
    private static final Seq<Building> visibleBuildings = new Seq<>(false, 256);
    private static final Seq<UnitCluster> visibleUnitClusters = new Seq<>(false, 128);

    private static final Rect viewRect = new Rect();
    private static final Mat transform = new Mat();
    private static final Mat oldTransform = new Mat();

    public static void init() {
        if (inited) return;
        inited = true;

        if (ui != null && ui.hudGroup != null) {
            Element old = ui.hudGroup.find(overlayName);
            if (old != null) old.remove();
        }

        Events.on(EventType.ClientLoadEvent.class, e -> {
            ensureDefaultFilterLists();
            refreshSettings();
            rebuildUnitFilterCache();
            rebuildBlockFilterCache();
            ensureOverlayAttached();
        });

        Events.on(EventType.WorldLoadEvent.class, e -> {
            visibleUnits.clear();
            visibleBuildings.clear();
        });

        Events.run(EventType.Trigger.update, () -> {
            if (interval.check(idSettings, settingsRefreshTime)) refreshSettings();
            if (interval.check(idAttach, attachRefreshTime)) ensureOverlayAttached();
        });
    }

    public static void buildSettings(SettingsMenuDialog.SettingsTable table) {
        table.checkPref(keyEnabled, false);
        table.checkPref(keyUnitsEnabled, true);
        table.checkPref(keyBuildingsEnabled, true);

        table.checkPref(keyShowEnemyUnits, true);
        table.checkPref(keyShowFriendlyUnits, true);

        table.checkPref(keyShowEnemyBuildings, true);
        table.checkPref(keyShowFriendlyBuildings, true);
        table.checkPref(keyTintBuildingIcons, true);

        table.sliderPref(keyUnitScale, 100, 10, 1000, 5, i -> i + "%");
        table.sliderPref(keyUnitAlpha, 90, 10, 100, 5, i -> i + "%");
        table.sliderPref(keyUnitClusterPx, 12, 2, 80, 1, i -> i + "px");
        table.sliderPref(keyBuildingScale, 120, 10, 1000, 5, i -> i + "%");
        table.sliderPref(keyIconAlpha, 90, 10, 100, 5, i -> i + "%");
        table.sliderPref(keyIconBgAlpha, 35, 0, 100, 5, i -> i + "%");

        table.pref(new SettingsMenuDialog.SettingsTable.Setting("mmplus-units-filter") {
            @Override
            public void add(SettingsMenuDialog.SettingsTable t) {
                t.button(title, BetterMiniMapFeature::showUnitFilterDialog).growX().margin(14f).pad(6f).row();
            }
        });

        table.pref(new SettingsMenuDialog.SettingsTable.Setting("mmplus-blocks-filter") {
            @Override
            public void add(SettingsMenuDialog.SettingsTable t) {
                t.button(title, BetterMiniMapFeature::showBlockFilterDialog).growX().margin(14f).pad(6f).row();
            }
        });

        ensureDefaultFilterLists();
        refreshSettings();
        rebuildUnitFilterCache();
        rebuildBlockFilterCache();
    }

    private static void ensureDefaultFilterLists() {
        if (Core.settings.getBool(keyFilterInit, false)) return;

        Seq<String> units = Core.settings.getJson(keyUnitList, Seq.class, String.class, Seq::new);
        if (units.isEmpty()) {
            for (UnitType t : content.units()) {
                units.add(t.name);
            }
            Core.settings.putJson(keyUnitList, String.class, units);
        }

        if (!Core.settings.has(keyBlockList)) {
            Core.settings.putJson(keyBlockList, String.class, new Seq<String>());
        }

        Core.settings.put(keyFilterInit, true);
    }

    private static void refreshSettings() {
        enabled = Core.settings.getBool(keyEnabled);
        unitsEnabled = Core.settings.getBool(keyUnitsEnabled, true);
        buildingsEnabled = Core.settings.getBool(keyBuildingsEnabled, true);

        showFriendlyUnits = Core.settings.getBool(keyShowFriendlyUnits, true);
        showEnemyUnits = Core.settings.getBool(keyShowEnemyUnits, true);
        showFriendlyBuildings = Core.settings.getBool(keyShowFriendlyBuildings, true);
        showEnemyBuildings = Core.settings.getBool(keyShowEnemyBuildings, true);
        tintBuildingIcons = Core.settings.getBool(keyTintBuildingIcons, true);

        if (Core.settings.has(keyUnitScale)) {
            float unitScale = Mathf.clamp(Core.settings.getInt(keyUnitScale, 100) / 100f, 0.1f, 10f);
            unitSizePx = 6f * unitScale;
        } else {
            unitSizePx = Math.max(1f, Core.settings.getInt(keyUnitSizeLegacy, 6));
        }
        unitAlpha = Mathf.clamp(Core.settings.getInt(keyUnitAlpha, 90) / 100f);
        unitClusterPx = Mathf.clamp(Core.settings.getInt(keyUnitClusterPx, 12), 2f, 80f);
        buildingScale = Mathf.clamp(Core.settings.getInt(keyBuildingScale, 120) / 100f, 0.1f, 10f);
        iconAlpha = Mathf.clamp(Core.settings.getInt(keyIconAlpha, 90) / 100f);
        iconBgAlpha = Mathf.clamp(Core.settings.getInt(keyIconBgAlpha, 35) / 100f);
    }

    private static void ensureOverlayAttached() {
        if (ui == null || ui.hudGroup == null) return;
        if (!Core.settings.getBool("minimap")) return;

        Element minimap = ui.hudGroup.find("minimap");
        if (!(minimap instanceof Table)) return;

        Table table = (Table) minimap;
        if (table.find(overlayName) != null) return;
        if (table.getChildren().isEmpty()) return;

        Element base = table.getChildren().get(0);
        HudMinimapOverlay overlay = new HudMinimapOverlay(base);
        overlay.name = overlayName;
        overlay.touchable = Touchable.disabled;
        table.addChild(overlay);
        overlay.toFront();
    }

    private static void rebuildUnitFilterCache() {
        Seq<String> enabledNames = Core.settings.getJson(keyUnitList, Seq.class, String.class, Seq::new);
        ObjectSet<String> enabledSet = new ObjectSet<>();
        enabledNames.each(v -> enabledSet.add((String) v));

        unitEnabledById = new boolean[content.units().size];
        for (UnitType type : content.units()) {
            unitEnabledById[type.id] = enabledSet.contains(type.name);
        }
    }

    private static void rebuildBlockFilterCache() {
        Seq<String> enabledNames = Core.settings.getJson(keyBlockList, Seq.class, String.class, Seq::new);
        ObjectSet<String> enabledSet = new ObjectSet<>();
        enabledNames.each(v -> enabledSet.add((String) v));

        blockEnabledById = new boolean[content.blocks().size];
        for (Block block : content.blocks()) {
            blockEnabledById[block.id] = enabledSet.contains(block.name);
        }
    }

    private static void rebuildVisibleCache(Rect viewRect) {
        visibleUnits.clear();
        visibleBuildings.clear();

        if (unitEnabledById == null) rebuildUnitFilterCache();
        if (blockEnabledById == null) rebuildBlockFilterCache();

        if (enabled && unitsEnabled) {
            Groups.unit.each(u -> {
                if (u == null || !u.isValid()) return;
                if (u.type == null) return;
                if (u.type.id < 0 || u.type.id >= unitEnabledById.length) return;
                if (!unitEnabledById[u.type.id]) return;
                if (player == null) return;
                boolean enemy = u.team != player.team();
                if (enemy && !showEnemyUnits) return;
                if (!enemy && !showFriendlyUnits) return;
                if (!viewRect.contains(u.x, u.y)) return;
                visibleUnits.add(u);
            });
        }

        if (enabled && buildingsEnabled) {
            Groups.build.each(b -> {
                if (!(b instanceof Building)) return;
                Building build = (Building) b;
                if (build.block == null) return;
                int id = build.block.id;
                if (id < 0 || id >= blockEnabledById.length) return;
                if (!blockEnabledById[id]) return;
                if (player == null) return;
                boolean enemy = build.team != player.team();
                if (enemy && !showEnemyBuildings) return;
                if (!enemy && !showFriendlyBuildings) return;
                if (!viewRect.contains(build.x, build.y)) return;
                visibleBuildings.add(build);
            });
        }
    }

    private static void drawMarkers(float invScale, float minimapScale) {
        if (!enabled) return;
        if (world == null || !state.isGame() || world.isGenerating()) return;
        if (player == null) return;
        if (unitEnabledById == null) rebuildUnitFilterCache();
        if (blockEnabledById == null) rebuildBlockFilterCache();

        if (enabled && unitsEnabled) {
            buildUnitClusters(minimapScale);
            Color friendly = Tmp.c1.set(Color.gray);
            for (int i = 0; i < visibleUnitClusters.size; i++) {
                UnitCluster cluster = visibleUnitClusters.get(i);
                if (cluster == null || cluster.count <= 0 || cluster.type == null) continue;
                boolean enemy = cluster.team != player.team();
                Color c = enemy ? cluster.team.color : friendly;
                drawUnitCluster(cluster, c, invScale);
            }
        }

        if (enabled && buildingsEnabled) {
            for (int i = 0; i < visibleBuildings.size; i++) {
                Building b = visibleBuildings.get(i);
                if (b == null || !b.isValid() || b.block == null) continue;

                float s = Math.max(tilesize, b.block.size * tilesize) * buildingScale;
                float bx = normalizeWorldCoord(b.x);
                float by = normalizeWorldCoord(b.y);

                if (tintBuildingIcons) {
                    boolean enemy = b.team != player.team();
                    Draw.color(enemy ? b.team.color : Color.gray, iconBgAlpha);
                    Fill.rect(bx, by, s * 1.08f, s * 1.08f);
                }

                Draw.color(1f, 1f, 1f, iconAlpha);
                Draw.rect(b.block.uiIcon, bx, by, s, s);
            }
        }

        Draw.reset();
    }

    private static float normalizeWorldCoord(float v) {
        float step = tilesize / 2f;
        return Mathf.round(v / step) * step;
    }

    private static void buildUnitClusters(float minimapScale) {
        visibleUnitClusters.clear();
        if (visibleUnits.isEmpty()) return;

        float scale = Math.max(0.0001f, minimapScale);
        float clusterWorld = Math.max(0.1f, unitClusterPx / scale);
        float clusterDst2 = clusterWorld * clusterWorld;

        for (int i = 0; i < visibleUnits.size; i++) {
            Unit u = visibleUnits.get(i);
            if (u == null || !u.isValid() || u.type == null) continue;

            UnitCluster nearest = null;
            float nearestDst2 = Float.MAX_VALUE;

            for (int j = 0; j < visibleUnitClusters.size; j++) {
                UnitCluster c = visibleUnitClusters.get(j);
                if (c.type != u.type || c.team != u.team) continue;

                float dst2 = Mathf.dst2(c.x, c.y, u.x, u.y);
                if (dst2 <= clusterDst2 && dst2 < nearestDst2) {
                    nearestDst2 = dst2;
                    nearest = c;
                }
            }

            if (nearest == null) {
                visibleUnitClusters.add(new UnitCluster(u));
            } else {
                nearest.add(u);
            }
        }
    }

    private static void drawUnitCluster(UnitCluster c, Color color, float invScale) {
        float sizeMul = c.count <= 1 ? 1f : Mathf.clamp(1f + 0.22f * Mathf.sqrt(c.count - 1f), 1f, 2.8f);
        float iconSize = unitSizePx * invScale * sizeMul;
        if (iconSize <= 0.001f) return;

        float a = unitAlpha;
        Draw.color(color, a * 0.20f);
        Fill.circle(c.x, c.y, iconSize * 0.62f);

        Draw.color(1f, 1f, 1f, a);
        Draw.rect(c.type.uiIcon, c.x, c.y, iconSize, iconSize, c.rotation() - 90f);

        Draw.color(Color.black, 0.20f * a);
        Lines.stroke(Math.max(0.5f, invScale));
        Lines.circle(c.x, c.y, iconSize * 0.58f);
        Draw.reset();
    }

    private static Rect computeViewRect() {
        float zoom = renderer.minimap.getZoom();
        float sz = minimapBaseSize * zoom;
        float dx = (Core.camera.position.x / tilesize);
        float dy = (Core.camera.position.y / tilesize);
        dx = Mathf.clamp(dx, sz, world.width() - sz);
        dy = Mathf.clamp(dy, sz, world.height() - sz);

        viewRect.set((dx - sz) * tilesize, (dy - sz) * tilesize, sz * 2f * tilesize, sz * 2f * tilesize);
        return viewRect;
    }

    private static void showUnitFilterDialog() {
        BaseDialog dialog = new BaseDialog(Core.bundle.get("mmplus.units.title"));
        dialog.addCloseButton();

        Seq<String> enabled = Core.settings.getJson(keyUnitList, Seq.class, String.class, Seq::new);
        ObjectSet<String> enabledSet = new ObjectSet<>();
        enabled.each(v -> enabledSet.add((String) v));

        Seq<UnitType> all = content.units().copy();
        Seq<UnitType> filtered = new Seq<>();

        Table grid = new Table();

        Runnable commit = () -> {
            enabled.clear();
            enabledSet.each(v -> enabled.add((String) v));
            Core.settings.putJson(keyUnitList, String.class, enabled);
            rebuildUnitFilterCache();
        };

        dialog.cont.table(top -> {
            top.left();
            top.add(Core.bundle.get("mmplus.search")).padRight(6f);
            TextField search = top.field("", t -> { }).growX().get();
            search.setMessageText(Core.bundle.get("mmplus.search.hint"));

            top.button(Icon.ok, Styles.cleari, () -> {
                enabledSet.clear();
                for (UnitType t : all) enabledSet.add(t.name);
                commit.run();
            }).size(44f).tooltip(Core.bundle.get("mmplus.allon"));

            top.button(Icon.cancel, Styles.cleari, () -> {
                enabledSet.clear();
                commit.run();
            }).size(44f).tooltip(Core.bundle.get("mmplus.alloff"));

            top.button(Icon.refresh, Styles.cleari, () -> {
                for (UnitType t : all) {
                    if (enabledSet.contains(t.name)) enabledSet.remove(t.name);
                    else enabledSet.add(t.name);
                }
                commit.run();
            }).size(44f).tooltip(Core.bundle.get("mmplus.invert"));

            top.row();
            top.image().color(Pal.gray).height(4f).growX().padTop(6f);

            search.changed(() -> {
                filtered.clear();
                String q = search.getText();
                if (q == null) q = "";
                q = q.trim().toLowerCase();
                for (UnitType t : all) {
                    if (q.isEmpty()) {
                        filtered.add(t);
                    } else {
                        String n1 = t.localizedName == null ? "" : t.localizedName.toLowerCase();
                        String n2 = t.name == null ? "" : t.name.toLowerCase();
                        if (n1.contains(q) || n2.contains(q)) filtered.add(t);
                    }
                }
                buildUnitGrid(grid, filtered, enabledSet, commit);
            });
        }).growX().row();

        dialog.cont.pane(Styles.noBarPane, grid).grow().with(p -> p.setForceScroll(true, true)).row();

        filtered.set(all);
        buildUnitGrid(grid, filtered, enabledSet, commit);

        dialog.show();
    }

    private static void buildUnitGrid(Table grid, Seq<UnitType> types, ObjectSet<String> enabledSet, Runnable commit) {
        grid.clear();
        int col = Math.max(1, Mathf.floor(Core.scene.getWidth() / Scl.scl(180f)));
        int i = 0;

        for (UnitType t : types) {
            TextButton button = new TextButton(t.localizedName, Styles.flatToggleMenut);
            button.add(new arc.scene.ui.Image(t.uiIcon)).size(32f).pad(8f);
            button.getCells().reverse();
            button.clicked(() -> {
                if (enabledSet.contains(t.name)) enabledSet.remove(t.name);
                else enabledSet.add(t.name);
                commit.run();
            });
            grid.add(button).size(170f, 100f).pad(4f).update(tb -> tb.setChecked(enabledSet.contains(t.name)));
            if (++i % col == 0) grid.row();
        }
    }

    private static void showBlockFilterDialog() {
        BaseDialog dialog = new BaseDialog(Core.bundle.get("mmplus.blocks.title"));
        dialog.addCloseButton();

        Seq<String> enabled = Core.settings.getJson(keyBlockList, Seq.class, String.class, Seq::new);
        ObjectSet<String> enabledSet = new ObjectSet<>();
        enabled.each(v -> enabledSet.add((String) v));

        Seq<Block> all = content.blocks().select(Block::hasBuilding);
        Seq<Block> filtered = new Seq<>();

        Table grid = new Table();

        Runnable commit = () -> {
            enabled.clear();
            enabledSet.each(v -> enabled.add((String) v));
            Core.settings.putJson(keyBlockList, String.class, enabled);
            rebuildBlockFilterCache();
        };

        dialog.cont.table(top -> {
            top.left();
            top.add(Core.bundle.get("mmplus.search")).padRight(6f);
            TextField search = top.field("", t -> { }).growX().get();
            search.setMessageText(Core.bundle.get("mmplus.search.hint"));

            top.button(Icon.ok, Styles.cleari, () -> {
                enabledSet.clear();
                for (Block b : all) enabledSet.add(b.name);
                commit.run();
            }).size(44f).tooltip(Core.bundle.get("mmplus.allon"));

            top.button(Icon.cancel, Styles.cleari, () -> {
                enabledSet.clear();
                commit.run();
            }).size(44f).tooltip(Core.bundle.get("mmplus.alloff"));

            top.button(Icon.refresh, Styles.cleari, () -> {
                for (Block b : all) {
                    if (enabledSet.contains(b.name)) enabledSet.remove(b.name);
                    else enabledSet.add(b.name);
                }
                commit.run();
            }).size(44f).tooltip(Core.bundle.get("mmplus.invert"));

            top.row();
            top.image().color(Pal.gray).height(4f).growX().padTop(6f);

            search.changed(() -> {
                filtered.clear();
                String q = search.getText();
                if (q == null) q = "";
                q = q.trim().toLowerCase();
                for (Block b : all) {
                    if (q.isEmpty()) {
                        filtered.add(b);
                    } else {
                        String n1 = b.localizedName == null ? "" : b.localizedName.toLowerCase();
                        String n2 = b.name == null ? "" : b.name.toLowerCase();
                        if (n1.contains(q) || n2.contains(q)) filtered.add(b);
                    }
                }
                buildBlockGrid(grid, filtered, enabledSet, commit);
            });
        }).growX().row();

        dialog.cont.pane(Styles.noBarPane, grid).grow().with(p -> p.setForceScroll(true, true)).row();

        filtered.set(all);
        buildBlockGrid(grid, filtered, enabledSet, commit);

        dialog.show();
    }

    private static void buildBlockGrid(Table grid, Seq<Block> blocks, ObjectSet<String> enabledSet, Runnable commit) {
        grid.clear();
        int col = Math.max(1, Mathf.floor(Core.scene.getWidth() / Scl.scl(180f)));
        int i = 0;

        for (Block b : blocks) {
            TextButton button = new TextButton(b.localizedName, Styles.flatToggleMenut);
            button.add(new arc.scene.ui.Image(b.uiIcon)).size(32f).pad(8f);
            button.getCells().reverse();
            button.clicked(() -> {
                if (enabledSet.contains(b.name)) enabledSet.remove(b.name);
                else enabledSet.add(b.name);
                commit.run();
            });
            grid.add(button).size(170f, 100f).pad(4f).update(tb -> tb.setChecked(enabledSet.contains(b.name)));
            if (++i % col == 0) grid.row();
        }
    }

    private static class HudMinimapOverlay extends Element {
        private final Element base;

        HudMinimapOverlay(Element base) {
            this.base = base;
        }

        @Override
        public void act(float delta) {
            if (base != null) setBounds(base.x, base.y, base.getWidth(), base.getHeight());
            super.act(delta);
        }

        @Override
        public void draw() {
            if (!enabled) return;
            if (ui == null || ui.hudfrag == null || !ui.hudfrag.shown) return;
            if (ui.minimapfrag != null && ui.minimapfrag.shown()) return;
            if (renderer == null || renderer.minimap == null || renderer.minimap.getRegion() == null) return;
            if (world == null || !state.isGame() || world.isGenerating()) return;
            if (Core.camera == null) return;

            if (!clipBegin()) return;

            Rect r = computeViewRect();
            float scaleX = width / r.width;
            float scaleY = height / r.height;
            float minimapScale = Math.min(scaleX, scaleY);
            float invScale = 1f / minimapScale;

            if (interval.check(idVisible, visibleRefreshTime)) rebuildVisibleCache(r);

            oldTransform.set(Draw.trans());

            transform.set(oldTransform);
            transform.translate(x, y);
            transform.scl(Tmp.v1.set(scaleX, scaleY));
            transform.translate(-r.x, -r.y);
            transform.translate(tilesize / 2f, tilesize / 2f);
            Draw.trans(transform);

            drawMarkers(invScale, minimapScale);

            Draw.trans(oldTransform);
            Draw.reset();

            clipEnd();
        }
    }

    private static class UnitCluster {
        final UnitType type;
        final Team team;
        float x;
        float y;
        float dirx;
        float diry;
        float fallbackRotation;
        int count;

        UnitCluster(Unit u) {
            type = u.type;
            team = u.team;
            x = u.x;
            y = u.y;
            dirx = Angles.trnsx(u.rotation, 1f);
            diry = Angles.trnsy(u.rotation, 1f);
            fallbackRotation = u.rotation;
            count = 1;
        }

        void add(Unit u) {
            count++;
            x += (u.x - x) / count;
            y += (u.y - y) / count;
            dirx += Angles.trnsx(u.rotation, 1f);
            diry += Angles.trnsy(u.rotation, 1f);
            fallbackRotation = u.rotation;
        }

        float rotation() {
            if (Math.abs(dirx) + Math.abs(diry) < 0.0001f) return fallbackRotation;
            return Mathf.angle(dirx, diry);
        }
    }
}
