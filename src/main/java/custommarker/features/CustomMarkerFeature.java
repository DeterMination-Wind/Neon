package custommarker.features;

import arc.Core;
import arc.Events;
import arc.func.Prov;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Font;
import arc.graphics.g2d.GlyphLayout;
import arc.graphics.g2d.Lines;
import arc.input.KeyBind;
import arc.input.KeyCode;
import arc.math.Mathf;
import arc.math.geom.Circle;
import arc.math.geom.Vec2;
import arc.scene.Element;
import arc.scene.event.InputEvent;
import arc.scene.event.InputListener;
import arc.scene.event.Touchable;
import arc.scene.style.TextureRegionDrawable;
import arc.scene.ui.CheckBox;
import arc.scene.ui.TextButton;
import arc.scene.ui.TextField;
import arc.scene.ui.layout.Scl;
import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import arc.util.Align;
import arc.util.Strings;
import arc.util.Time;
import arc.util.Tmp;
import arc.util.pooling.Pools;
import bektools.ui.VscodeSettingsStyle;
import mindustry.game.EventType;
import mindustry.gen.Call;
import mindustry.gen.Icon;
import mindustry.gen.Tex;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.ui.Fonts;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.ui.dialogs.SettingsMenuDialog;
import mindustry.core.World;

import java.lang.reflect.Method;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static mindustry.Vars.control;
import static mindustry.Vars.headless;
import static mindustry.Vars.mods;
import static mindustry.Vars.net;
import static mindustry.Vars.player;
import static mindustry.Vars.state;
import static mindustry.Vars.tilesize;
import static mindustry.Vars.ui;
import static mindustry.Vars.world;

public class CustomMarkerFeature {
    private static final String panelName = "custommarker-panel";
    private static final String overlayButtonName = "custommarker-overlay-button";
    private static final String overlayWindowName = "custommarker-mark-button";
    private static final String chatListWindowName = "custommarker-chatmarks";

    private static final String keyEnabled = "cm-enabled";
    private static final String keyTemplateInit = "cm-templates-init";
    private static final String keyTemplates = "cm-templates";

    private static final int templateCount = 5;
    private static final int maxChatMarks = 300;
    private static final float markCooldown = 30f;

    private static final String[] defaultFirst = {"Mark", "Gather", "Attack", "Defend", "What"};
    private static final String[] defaultSecond = {"标记", "集合", "攻击", "防御", "问号"};
    private static final Color[] markerColors = {
        Color.valueOf("4ea8ff"),
        Color.valueOf("7dd9c4"),
        Color.valueOf("ff9b71"),
        Color.valueOf("9ad06b"),
        Color.valueOf("ffcc66")
    };

    private static final KeyBind keyShowPanel = KeyBind.add("cm-open-panel", KeyCode.j, "custommarker");
    private static final KeyBind keyPickLocation = KeyBind.add("cm-pick-location", KeyCode.unset, "custommarker");

    private static final String[] firstParts = new String[templateCount];
    private static final String[] secondParts = new String[templateCount];

    private static final Vec2 panelWorld = new Vec2();
    private static final Pattern coordPattern = Pattern.compile("\\((-?\\d+)\\s*,\\s*(-?\\d+)\\)");
    private static final Seq<ChatMarkEntry> chatMarks = new Seq<>();

    private static boolean panelVisible;
    private static boolean chatMarksDirty;
    private static float nextMarkAllowedAt;
    private static float nextAttachTryAt;
    private static float lastChatCaptureAt;
    private static String lastCapturedKey = "";

    private static boolean enabled;
    private static boolean inited;

    private static MarkerPanel panelElement;
    private static Table fallbackOverlayHost;
    private static Table markHitter;

    private static final MindustryXMarkers xMarkers = new MindustryXMarkers();
    private static final MindustryXOverlayUI xOverlayUi = new MindustryXOverlayUI();
    private static Object xOverlayWindow;
    private static Object xChatListWindow;
    private static boolean lastOverlayVisible;
    private static ChatMarksWindow chatMarksWindowContent;

    public static void init() {
        if (inited) return;
        inited = true;

        Events.on(EventType.ClientLoadEvent.class, e -> {
            Core.settings.defaults(keyEnabled, true);
            Core.settings.defaults(keyTemplateInit, false);

            ensureTemplateDefaults();
            reloadRuntimeSettings();
            reloadTemplatesFromSettings();
            xMarkers.tryInit();
            ensureUiAttached();
            syncOverlayButtonWindow(true);
        });

        Events.on(EventType.WorldLoadEvent.class, e -> {
            closePanel();
            removeMarkHitter();
            clearChatMarks();
            lastCapturedKey = "";
            lastChatCaptureAt = 0f;
        });

        Events.on(EventType.ClientChatEvent.class, e -> {
            String selfName = player == null ? "You" : player.name;
            captureChatMarks(e.message, selfName);
        });
        Events.on(EventType.PlayerChatEvent.class, e -> {
            String senderName = e.player == null ? "Unknown" : e.player.name;
            captureChatMarks(e.message, senderName);
        });

        Events.run(EventType.Trigger.update, CustomMarkerFeature::update);
    }

    public static void buildSettings(SettingsMenuDialog.SettingsTable table) {
        table.pref(new SettingsMenuDialog.SettingsTable.Setting(keyEnabled) {
            @Override
            public void add(SettingsMenuDialog.SettingsTable t) {
                CheckBox box = new CheckBox(title);
                box.getLabel().setWrap(true);
                box.getLabelCell().growX().minWidth(0f);
                box.update(() -> box.setChecked(Core.settings.getBool(name, true)));
                box.changed(() -> Core.settings.put(name, box.isChecked()));

                t.table(VscodeSettingsStyle.cardBackground(), row -> {
                    row.left().margin(10f);
                    row.add(box).left().growX().minWidth(0f);
                }).growX().padTop(6f);
                addDesc(box);
                t.row();
            }
        });

        table.pref(new SettingsMenuDialog.SettingsTable.Setting("cm-edit-templates") {
            @Override
            public void add(SettingsMenuDialog.SettingsTable t) {
                t.table(VscodeSettingsStyle.cardBackground(), row -> {
                    row.left().margin(6f);
                    row.button(title, Styles.flatt, CustomMarkerFeature::showTemplateEditor)
                        .growX()
                        .height(42f)
                        .padLeft(8f);
                }).growX().padTop(6f);
                t.row();
            }
        });

        table.pref(new SettingsMenuDialog.SettingsTable.Setting("cm-reset-templates") {
            @Override
            public void add(SettingsMenuDialog.SettingsTable t) {
                t.table(VscodeSettingsStyle.cardBackground(), row -> {
                    row.left().margin(6f);
                    row.button(title, Styles.flatt, CustomMarkerFeature::resetTemplatesToDefaults)
                        .growX()
                        .height(42f)
                        .padLeft(8f);
                }).growX().padTop(6f);
                t.row();
            }
        });

        reloadRuntimeSettings();
        reloadTemplatesFromSettings();
    }

    private static void update() {
        reloadRuntimeSettings();
        guardChatWindowScrollFocus();

        if (Time.time >= nextAttachTryAt) {
            nextAttachTryAt = Time.time + 60f;
            ensureUiAttached();
        }
        syncOverlayButtonWindow(false);

        if (!enabled) {
            closePanel();
            removeMarkHitter();
            return;
        }
        if (!canUseMarkerUi()) {
            closePanel();
            removeMarkHitter();
            return;
        }

        if (Core.scene != null && (Core.scene.hasField() || Core.scene.hasDialog())) return;

        if (Core.input.keyTap(keyShowPanel)) {
            showPanelAtMouse();
        }
        if (Core.input.keyTap(keyPickLocation)) {
            toggleMarkHitterUI();
        }
    }

    private static void guardChatWindowScrollFocus() {
        if (Core.scene == null || chatMarksWindowContent == null) return;

        Element scrollFocus = Core.scene.getScrollFocus();
        if (scrollFocus == null || !scrollFocus.isDescendantOf(chatMarksWindowContent)) return;

        Vec2 stagePos = Core.scene.screenToStageCoordinates(Tmp.v1.set(Core.input.mouseX(), Core.input.mouseY()));
        Element hover = Core.scene.hit(stagePos.x, stagePos.y, true);
        if (hover == null || !hover.isDescendantOf(chatMarksWindowContent)) {
            clearSceneUiFocus();
        }
    }

    private static void reloadRuntimeSettings() {
        enabled = Core.settings.getBool(keyEnabled, true);
    }

    private static boolean canUseMarkerUi() {
        if (headless) return false;
        if (ui == null || ui.hudGroup == null || ui.hudfrag == null) return false;
        if (!ui.hudfrag.shown) return false;
        if (state == null || !state.isGame()) return false;
        if (world == null || world.isGenerating()) return false;
        return Core.camera != null;
    }

    private static void ensureUiAttached() {
        if (ui == null || ui.hudGroup == null) return;

        Element existingPanel = ui.hudGroup.find(panelName);
        if (existingPanel instanceof MarkerPanel) {
            panelElement = (MarkerPanel) existingPanel;
        } else {
            if (existingPanel != null) existingPanel.remove();
            panelElement = new MarkerPanel();
            panelElement.name = panelName;
            ui.hudGroup.addChildAt(0, panelElement);
        }

        ensureOverlayButtonAttached();
        ensureChatListWindowAttached();
    }

    private static void ensureOverlayButtonAttached() {
        if (xOverlayUi.isInstalled()) {
            removeFallbackOverlayHost();

            if (xOverlayWindow == null) {
                xOverlayWindow = xOverlayUi.registerWindow(
                    overlayWindowName,
                    createOverlayButtonContent(),
                    () -> true
                );
                xOverlayUi.tryConfigureWindow(xOverlayWindow, true, false);
                syncOverlayButtonWindow(true);
            }
            return;
        }

        Element existing = ui.hudGroup.find(overlayButtonName);
        if (existing instanceof Table) {
            fallbackOverlayHost = (Table) existing;
        } else {
            if (existing != null) existing.remove();
            fallbackOverlayHost = createFallbackOverlayHost();
            ui.hudGroup.addChild(fallbackOverlayHost);
            fallbackOverlayHost.toFront();
        }

        if (fallbackOverlayHost != null) {
            fallbackOverlayHost.visible = desiredOverlayVisible() && canUseMarkerUi();
        }
    }

    private static void ensureChatListWindowAttached() {
        if (!xOverlayUi.isInstalled()) return;
        if (xChatListWindow != null) return;

        chatMarksWindowContent = new ChatMarksWindow();
        xChatListWindow = xOverlayUi.registerWindow(
            chatListWindowName,
            chatMarksWindowContent,
            () -> state != null && state.isGame()
        );
        xOverlayUi.tryConfigureWindow(xChatListWindow, false, true);
        xOverlayUi.setEnabledAndPinned(xChatListWindow, true, false);
    }

    private static void removeFallbackOverlayHost() {
        if (fallbackOverlayHost != null) {
            fallbackOverlayHost.remove();
            fallbackOverlayHost = null;
        }
    }

    private static Table createFallbackOverlayHost() {
        Table host = new Table();
        host.name = overlayButtonName;
        host.setFillParent(true);
        host.touchable = Touchable.childrenOnly;
        host.bottom().left();
        host.add(createOverlayButtonContent()).pad(6f);
        host.update(() -> host.visible = desiredOverlayVisible() && canUseMarkerUi() && !xOverlayUi.isInstalled());
        return host;
    }

    private static Table createOverlayButtonContent() {
        Table content = new Table(VscodeSettingsStyle.cardBackground());
        content.defaults().size(mindustry.Vars.iconMed + 6f).pad(1f);

        Table trigger = new Table();
        trigger.touchable = Touchable.enabled;
        trigger.background(VscodeSettingsStyle.headerBackground());
        trigger.image(Icon.mapSmall).size(mindustry.Vars.iconMed - 4f).color(VscodeSettingsStyle.accentColor());
        trigger.addListener(new InputListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, KeyCode button) {
                onOverlayButtonPressed();
                event.stop();
                return false;
            }
        });

        content.add(trigger).tooltip("@cm.overlay.tooltip");
        return content;
    }

    private static void onOverlayButtonPressed() {
        if (!canUseMarkerUi()) return;

        // Defer to next frame; toggling OverlayUI during the same touch event can invalidate the button actor.
        Core.app.post(() -> {
            if (!canUseMarkerUi()) return;

            xOverlayUi.tryCloseOverlayEditor();
            if (markHitter == null) {
                markHitter = createMarkHitter();
            }
            if (markHitter.parent == null) {
                ui.hudGroup.addChildAt(0, markHitter);
            }
        });
    }

    private static boolean desiredOverlayVisible() {
        return enabled;
    }

    private static void syncOverlayButtonWindow(boolean force) {
        if (xOverlayWindow == null) return;

        boolean visible = desiredOverlayVisible();
        if (!force && visible == lastOverlayVisible) return;
        lastOverlayVisible = visible;
        xOverlayUi.setEnabledAndPinned(xOverlayWindow, visible, visible);
    }

    private static void showPanelAtMouse() {
        if (!canUseMarkerUi()) return;
        panelWorld.set(Core.input.mouseWorldX(), Core.input.mouseWorldY());
        panelVisible = true;
        removeMarkHitter();
    }

    private static void closePanel() {
        panelVisible = false;
    }

    private static void toggleMarkHitterUI() {
        if (!canUseMarkerUi()) return;

        if (markHitter == null) {
            markHitter = createMarkHitter();
        }

        if (markHitter.parent == null) {
            ui.hudGroup.addChildAt(0, markHitter);
        } else {
            markHitter.remove();
        }
    }

    private static Table createMarkHitter() {
        Table t = new Table();
        t.setFillParent(true);
        t.touchable = Touchable.enabled;
        t.background(((TextureRegionDrawable) Tex.whiteui).tint(0.07f, 0.12f, 0.18f, 0.45f));
        t.center().add("@cm.pickmode.tip", Styles.outlineLabel);
        t.addListener(new InputListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, KeyCode button) {
                showPanelAtMouse();
                Core.app.post(CustomMarkerFeature::removeMarkHitter);
                event.stop();
                return false;
            }
        });
        return t;
    }

    private static void removeMarkHitter() {
        if (markHitter != null) {
            markHitter.remove();
        }
    }

    private static void clearChatMarks() {
        chatMarks.clear();
        chatMarksDirty = true;
        if (chatMarksWindowContent != null) {
            chatMarksWindowContent.rebuildIfNeeded();
        }
    }

    private static void captureChatMarks(String message, String senderName) {
        if (message == null || message.isEmpty()) return;

        String stripped = Strings.stripColors(message).trim();
        if (stripped.isEmpty()) return;
        String sender = Strings.stripColors(senderName == null ? "Unknown" : senderName).trim();
        if (sender.isEmpty()) sender = "Unknown";

        // Sending in multiplayer can fire both ClientChatEvent and PlayerChatEvent for the same line.
        String dedupeKey = sender + "|" + stripped;
        if (dedupeKey.equals(lastCapturedKey) && Time.time - lastChatCaptureAt <= 30f) return;
        lastCapturedKey = dedupeKey;
        lastChatCaptureAt = Time.time;

        Matcher matcher = coordPattern.matcher(stripped);
        boolean changed = false;
        while (matcher.find()) {
            int x, y;
            try {
                x = Integer.parseInt(matcher.group(1));
                y = Integer.parseInt(matcher.group(2));
            } catch (Throwable ignored) {
                continue;
            }

            String coordText = "(" + x + "," + y + ")";
            chatMarks.add(new ChatMarkEntry(x, y, coordText, sender));
            changed = true;

            if (chatMarks.size > maxChatMarks) {
                chatMarks.remove(0);
            }
        }

        if (changed) {
            chatMarksDirty = true;
            if (chatMarksWindowContent != null) {
                chatMarksWindowContent.rebuildIfNeeded();
            }
        }
    }

    private static void focusChatMark(ChatMarkEntry entry) {
        if (entry == null) return;
        if (!canUseMarkerUi()) return;
        if (control == null || control.input == null) return;

        float wx = entry.tileX * tilesize + tilesize / 2f;
        float wy = entry.tileY * tilesize + tilesize / 2f;

        float minx = tilesize / 2f;
        float miny = tilesize / 2f;
        float maxx = Math.max(minx, world.unitWidth() - tilesize / 2f);
        float maxy = Math.max(miny, world.unitHeight() - tilesize / 2f);
        wx = Mathf.clamp(wx, minx, maxx);
        wy = Mathf.clamp(wy, miny, maxy);

        control.input.panCamera(Tmp.v1.set(wx, wy));

        String markText = "<CM><Saved>(" + entry.tileX + "," + entry.tileY + ")";
        xMarkers.tryMark(markText, entry.tileX, entry.tileY);

        // Run twice to survive late focus re-assignments from click handlers.
        Core.app.post(() -> Core.app.post(CustomMarkerFeature::clearSceneUiFocus));
    }

    private static void clearSceneUiFocus() {
        if (Core.scene == null) return;
        if (chatMarksWindowContent != null) {
            Core.scene.unfocus(chatMarksWindowContent);
        }
        Core.scene.setScrollFocus(null);
        Core.scene.setKeyboardFocus(null);
        Core.scene.cancelTouchFocus();
    }

    private static void markCurrent(int index) {
        if (!canUseMarkerUi()) return;
        if (index < 0 || index >= templateCount) return;

        if (Time.time < nextMarkAllowedAt) {
            if (ui != null) {
                ui.announce(Core.bundle.get("cm.toast.cooldown", "Please do not mark too frequently."));
            }
            return;
        }
        nextMarkAllowedAt = Time.time + markCooldown;

        int tileX = World.toTile(panelWorld.x);
        int tileY = World.toTile(panelWorld.y);
        String message = formatMarkerMessage(index, tileX, tileY);

        if (net != null && net.active()) {
            Call.sendChatMessage(message);
        } else if (ui != null && ui.hudfrag != null) {
            ui.hudfrag.showToast("[accent]" + message + "[]");
        }

        xMarkers.tryMark(message, tileX, tileY);

        // Ensure wheel focus returns to world zoom after marker interactions.
        Core.app.post(() -> Core.app.post(CustomMarkerFeature::clearSceneUiFocus));
    }

    private static String formatMarkerMessage(int index, int x, int y) {
        String first = sanitizeToken(firstParts[index], defaultFirst[index]);
        String second = sanitizeToken(secondParts[index], defaultSecond[index]);
        return "<" + first + "><" + second + ">(" + x + "," + y + ")";
    }

    private static void ensureTemplateDefaults() {
        if (Core.settings.getBool(keyTemplateInit, false) && Core.settings.has(keyTemplates)) return;
        saveTemplateEntries(defaultTemplateEntries());
        Core.settings.put(keyTemplateInit, true);
    }

    private static void resetTemplatesToDefaults() {
        saveTemplateEntries(defaultTemplateEntries());
        reloadTemplatesFromSettings();
        if (ui != null && ui.hudfrag != null) {
            ui.hudfrag.showToast(Core.bundle.get("cm.toast.templates-reset", "Marker templates reset to defaults."));
        }
    }

    private static Seq<String> defaultTemplateEntries() {
        Seq<String> out = new Seq<>();
        for (int i = 0; i < templateCount; i++) {
            out.add(encodeEntry(defaultFirst[i], defaultSecond[i]));
        }
        return out;
    }

    private static void reloadTemplatesFromSettings() {
        Seq<String> entries = normalizeTemplateEntries(loadTemplateEntries());
        saveTemplateEntries(entries);

        for (int i = 0; i < templateCount; i++) {
            String[] pair = decodeEntry(entries.get(i));
            firstParts[i] = sanitizeToken(pair[0], defaultFirst[i]);
            secondParts[i] = sanitizeToken(pair[1], defaultSecond[i]);
        }
    }

    private static Seq<String> normalizeTemplateEntries(Seq<String> entries) {
        Seq<String> out = new Seq<>();
        for (int i = 0; i < templateCount; i++) {
            String first = defaultFirst[i];
            String second = defaultSecond[i];

            if (entries != null && i < entries.size) {
                String[] pair = decodeEntry(entries.get(i));
                if (!pair[0].isEmpty()) first = pair[0];
                if (!pair[1].isEmpty()) second = pair[1];
            }
            out.add(encodeEntry(first, second));
        }
        return out;
    }

    private static Seq<String> loadTemplateEntries() {
        try {
            return Core.settings.getJson(keyTemplates, Seq.class, String.class, Seq::new);
        } catch (Throwable ignored) {
            return new Seq<>();
        }
    }

    private static void saveTemplateEntries(Seq<String> entries) {
        Core.settings.putJson(keyTemplates, String.class, entries);
    }

    private static String encodeEntry(String first, String second) {
        return sanitizeToken(first, "") + "|" + sanitizeToken(second, "");
    }

    private static String[] decodeEntry(String entry) {
        if (entry == null) return new String[]{"", ""};
        int split = entry.indexOf('|');
        if (split < 0) return new String[]{entry.trim(), ""};
        return new String[]{entry.substring(0, split).trim(), entry.substring(split + 1).trim()};
    }

    private static String sanitizeToken(String text, String fallback) {
        String out = text == null ? "" : text.trim();
        if (out.isEmpty()) out = fallback;
        out = out.replace("\n", " ")
            .replace("\r", " ")
            .replace("<", "")
            .replace(">", "")
            .replace("(", "")
            .replace(")", "");
        return out.trim();
    }

    private static void showTemplateEditor() {
        BaseDialog dialog = new BaseDialog(Core.bundle.get("cm.templates.title", "Custom marker templates"));
        dialog.addCloseButton();
        dialog.cont.background(VscodeSettingsStyle.panelBackground());
        dialog.cont.margin(8f);

        reloadTemplatesFromSettings();

        String[] firstEdit = new String[templateCount];
        String[] secondEdit = new String[templateCount];
        TextField[] firstFields = new TextField[templateCount];
        TextField[] secondFields = new TextField[templateCount];

        for (int i = 0; i < templateCount; i++) {
            firstEdit[i] = firstParts[i];
            secondEdit[i] = secondParts[i];
        }

        dialog.cont.table(VscodeSettingsStyle.headerBackground(), header -> {
            header.left().margin(8f);
            header.add(Core.bundle.get("cm.templates.col.first", "First content")).growX().left().pad(6f);
            header.add(Core.bundle.get("cm.templates.col.second", "Second content")).growX().left().pad(6f);
        }).growX().row();

        dialog.cont.pane(Styles.noBarPane, body -> {
            body.defaults().growX().pad(4f);
            for (int i = 0; i < templateCount; i++) {
                final int index = i;
                body.table(VscodeSettingsStyle.cardBackground(), line -> {
                    line.left().margin(6f);
                    line.add((index + 1) + ".").width(28f).color(Pal.accent);

                    firstFields[index] = line.field(firstEdit[index], text -> firstEdit[index] = text).growX().get();
                    firstFields[index].setMessageText(Core.bundle.get("cm.templates.first.hint", "First content in <...>"));

                    line.add(" ").width(8f);

                    secondFields[index] = line.field(secondEdit[index], text -> secondEdit[index] = text).growX().get();
                    secondFields[index].setMessageText(Core.bundle.get("cm.templates.second.hint", "Second content in <...>"));
                }).growX().row();
            }
        }).grow().maxHeight(Math.min(440f, Core.graphics.getHeight() * 0.75f)).row();

        dialog.buttons.defaults().size(220f, 54f).pad(4f);
        dialog.buttons.button("@cm.templates.reset", Icon.refresh, Styles.flatt, () -> {
            for (int i = 0; i < templateCount; i++) {
                firstFields[i].setText(defaultFirst[i]);
                secondFields[i].setText(defaultSecond[i]);
            }
        });
        dialog.buttons.button("@cm.templates.save", Icon.ok, Styles.flatt, () -> {
            Seq<String> out = new Seq<>();
            for (int i = 0; i < templateCount; i++) {
                String first = sanitizeToken(firstFields[i].getText(), defaultFirst[i]);
                String second = sanitizeToken(secondFields[i].getText(), defaultSecond[i]);
                out.add(encodeEntry(first, second));
            }
            saveTemplateEntries(out);
            reloadTemplatesFromSettings();
            dialog.hide();
        });

        dialog.show();
    }

    private static void drawLabel(float x, float y, String text, Color color) {
        Font font = Fonts.outline;
        GlyphLayout layout = Pools.obtain(GlyphLayout.class, GlyphLayout::new);

        boolean ints = font.usesIntegerPositions();
        font.setUseIntegerPositions(false);

        float prevScale = font.getScaleX();
        font.getData().setScale(0.8f / Math.max(0.0001f, Scl.scl(1f)));

        layout.setText(font, text);

        font.setColor(color);
        font.draw(text, x, y + layout.height / 2f, 0f, Align.center, false);

        font.getData().setScale(prevScale);
        font.setColor(Color.white);
        font.setUseIntegerPositions(ints);
        Pools.free(layout);
    }

    private static class MarkerPanel extends Element {
        private final Circle outer = new Circle(0f, 0f, Scl.scl(120f));
        private final Circle inner = new Circle(0f, 0f, Scl.scl(60f));

        MarkerPanel() {
            touchable = Touchable.enabled;

            addListener(new InputListener() {
                @Override
                public boolean touchDown(InputEvent event, float x, float y, int pointer, KeyCode button) {
                    if (!panelVisible) return false;

                    if (inner.contains(x, y)) {
                        markCurrent(0);
                    } else if (outer.contains(x, y)) {
                        int sectors = templateCount - 1;
                        int selected = Mathf.round(Mathf.angle(x, y) / 360f * sectors) % sectors;
                        markCurrent(1 + selected);
                    }

                    closePanel();
                    event.stop();
                    return true;
                }
            });
        }

        @Override
        public void updateVisibility() {
            visible = panelVisible && enabled && canUseMarkerUi();
        }

        @Override
        public void act(float delta) {
            super.act(delta);
            if (!panelVisible) return;
            Vec2 projected = Core.camera.project(Tmp.v1.set(panelWorld));
            setPosition(projected.x, projected.y);
        }

        @Override
        public void draw() {
            if (!visible) return;

            float outerRadius = outer.radius;
            float innerRadius = inner.radius;
            int sectors = templateCount - 1;

            Draw.z(Layer.overlayUI + 1f);
            Draw.color(Color.valueOf("111821"), 0.9f);
            Fill.circle(x, y, outerRadius);

            Draw.color(VscodeSettingsStyle.accentColor());
            Lines.stroke(Scl.scl(1.2f));
            Lines.circle(x, y, outerRadius);
            Lines.circle(x, y, innerRadius);

            for (int i = 0; i < sectors; i++) {
                Lines.lineAngle(x, y, 360f * (i - 0.5f) / sectors, outerRadius - innerRadius, innerRadius);
            }

            drawLabel(
                x,
                y,
                secondParts[0] + "\n(" + World.toTile(panelWorld.x) + "," + World.toTile(panelWorld.y) + ")",
                markerColors[0]
            );

            for (int i = 0; i < sectors; i++) {
                float angle = 360f * i / sectors;
                Vec2 center = Tmp.v1.trns(angle, (outerRadius + innerRadius) / 2f).add(x, y);
                drawLabel(center.x, center.y, secondParts[1 + i], markerColors[1 + i]);
            }

            Draw.reset();
        }

        @Override
        public Element hit(float x, float y, boolean touchable) {
            return this;
        }
    }

    private static class ChatMarkEntry {
        final int tileX;
        final int tileY;
        final String coordText;
        final String senderName;

        ChatMarkEntry(int tileX, int tileY, String coordText, String senderName) {
            this.tileX = tileX;
            this.tileY = tileY;
            this.coordText = coordText;
            this.senderName = senderName;
        }
    }

    private static class ChatMarksWindow extends Table {
        private final Table list = new Table();

        ChatMarksWindow() {
            super(VscodeSettingsStyle.panelBackground());
            margin(6f);
            touchable = Touchable.childrenOnly;
            defaults().growX().left().minWidth(0f);

            table(VscodeSettingsStyle.headerBackground(), header -> {
                header.left().defaults().left();
                header.margin(6f);
                header.add("@cm.chatmarks.title").color(VscodeSettingsStyle.accentColor()).padRight(8f);
                header.button(Icon.trashSmall, Styles.clearNonei, mindustry.Vars.iconMed, CustomMarkerFeature::clearChatMarks)
                    .tooltip("@cm.chatmarks.clear");
            }).growX().row();

            list.left().top();
            list.defaults().growX().padTop(2f).minWidth(0f);

            pane(Styles.noBarPane, body -> body.add(list).growX().top().left())
                .grow()
                .minWidth(240f)
                .minHeight(120f)
                .maxHeight(420f)
                .padTop(6f)
                .row();

            // Let OverlayUI resize this window freely without snap-back.
            add(new PreferAnySize()).grow().row();
            rebuildIfNeeded();
        }

        void rebuildIfNeeded() {
            if (!chatMarksDirty) return;
            chatMarksDirty = false;

            list.clearChildren();
            if (chatMarks.isEmpty()) {
                list.add("@cm.chatmarks.empty").color(Color.lightGray).left().pad(6f).row();
                return;
            }

            for (int i = chatMarks.size - 1; i >= 0; i--) {
                ChatMarkEntry entry = chatMarks.get(i);
                String buttonText = entry.coordText + "  " + entry.senderName;
                TextButton button = new TextButton(buttonText, Styles.flatt);
                button.getLabel().setWrap(true);
                button.getLabelCell().growX().minWidth(0f).left().pad(3f);
                button.clicked(() -> focusChatMark(entry));
                list.add(button).growX().minHeight(42f).left().row();
            }
        }

        @Override
        public void act(float delta) {
            super.act(delta);
            rebuildIfNeeded();
        }
    }

    private static class PreferAnySize extends Element {
        @Override
        public float getMinWidth() {
            return 0f;
        }

        @Override
        public float getPrefWidth() {
            return getWidth();
        }

        @Override
        public float getMinHeight() {
            return 0f;
        }

        @Override
        public float getPrefHeight() {
            return getHeight();
        }
    }

    private static class MindustryXOverlayUI {
        private boolean initialized;
        private boolean installed;
        private Object instance;
        private Method registerWindow;
        private Method setAvailability;
        private Method setResizable;
        private Method setAutoHeight;
        private Method getData;
        private Method setEnabled;
        private Method setPinned;
        private Method getOpen;
        private Method toggleOverlay;
        private boolean accessorsInitialized;

        boolean isInstalled() {
            if (initialized) return installed;
            initialized = true;

            try {
                installed = mods != null && mods.locateMod("mindustryx") != null;
            } catch (Throwable ignored) {
                installed = false;
            }
            if (!installed) return false;

            try {
                Class<?> c = Class.forName("mindustryX.features.ui.OverlayUI");
                instance = c.getField("INSTANCE").get(null);
                registerWindow = c.getMethod("registerWindow", String.class, Table.class);
                try {
                    getOpen = c.getMethod("getOpen");
                } catch (Throwable ignored) {
                    getOpen = null;
                }
                try {
                    toggleOverlay = c.getMethod("toggle");
                } catch (Throwable ignored) {
                    toggleOverlay = null;
                }
            } catch (Throwable t) {
                installed = false;
                return false;
            }
            return true;
        }

        Object registerWindow(String name, Table table, Prov<Boolean> availability) {
            if (!isInstalled()) return null;
            try {
                Object window = registerWindow.invoke(instance, name, table);
                tryInitWindowAccessors(window);
                if (window != null && availability != null && setAvailability != null) {
                    setAvailability.invoke(window, availability);
                }
                return window;
            } catch (Throwable ignored) {
                return null;
            }
        }

        void setEnabledAndPinned(Object window, boolean enabled, boolean pinned) {
            if (window == null) return;
            try {
                tryInitWindowAccessors(window);
                if (getData == null) return;

                Object data = getData.invoke(window);
                if (data == null) return;

                if (setEnabled != null) setEnabled.invoke(data, enabled);
                if (setPinned != null) setPinned.invoke(data, pinned);
            } catch (Throwable ignored) {
            }
        }

        void tryConfigureWindow(Object window, boolean autoHeight, boolean resizable) {
            if (window == null) return;
            try {
                tryInitWindowAccessors(window);
                if (setAutoHeight != null) setAutoHeight.invoke(window, autoHeight);
                if (setResizable != null) setResizable.invoke(window, resizable);
            } catch (Throwable ignored) {
            }
        }

        void tryCloseOverlayEditor() {
            if (!isInstalled()) return;
            if (instance == null || getOpen == null || toggleOverlay == null) return;

            try {
                Object openObj = getOpen.invoke(instance);
                boolean open = openObj instanceof Boolean && (Boolean) openObj;
                if (open) {
                    toggleOverlay.invoke(instance);
                }
            } catch (Throwable ignored) {
            }
        }

        private void tryInitWindowAccessors(Object window) {
            if (window == null) return;
            if (accessorsInitialized && (getData != null || setAvailability != null || setResizable != null || setAutoHeight != null)) return;

            try {
                Class<?> wc = window.getClass();
                try {
                    setAvailability = wc.getMethod("setAvailability", Prov.class);
                } catch (Throwable ignored) {
                    setAvailability = null;
                }
                try {
                    setResizable = wc.getMethod("setResizable", boolean.class);
                } catch (Throwable ignored) {
                    setResizable = null;
                }
                try {
                    setAutoHeight = wc.getMethod("setAutoHeight", boolean.class);
                } catch (Throwable ignored) {
                    setAutoHeight = null;
                }

                getData = wc.getMethod("getData");
                Object data = getData.invoke(window);
                if (data != null) {
                    Class<?> dc = data.getClass();
                    try {
                        setEnabled = dc.getMethod("setEnabled", boolean.class);
                    } catch (Throwable ignored) {
                        setEnabled = null;
                    }
                    try {
                        setPinned = dc.getMethod("setPinned", boolean.class);
                    } catch (Throwable ignored) {
                        setPinned = null;
                    }
                }

                accessorsInitialized = true;
            } catch (Throwable ignored) {
            }
        }
    }

    private static class MindustryXMarkers {
        private boolean initialized;
        private boolean available;
        private Method newMarkFromChat;

        void tryInit() {
            if (initialized) return;
            initialized = true;

            try {
                if (mods == null || mods.locateMod("mindustryx") == null) {
                    available = false;
                    return;
                }

                Class<?> markerType = Class.forName("mindustryX.features.MarkerType");
                newMarkFromChat = markerType.getMethod("newMarkFromChat", String.class, Vec2.class);
                available = true;
            } catch (Throwable ignored) {
                available = false;
            }
        }

        void tryMark(String message, int tileX, int tileY) {
            if (!available || newMarkFromChat == null) return;

            try {
                newMarkFromChat.invoke(null, message, new Vec2(tileX, tileY));
            } catch (Throwable ignored) {
                available = false;
            }
        }
    }
}
