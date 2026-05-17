package betterhotkey.features;

import arc.Core;
import arc.Events;
import arc.func.Cons;
import arc.func.Prov;
import arc.graphics.Color;
import arc.input.KeyBind;
import arc.input.KeyCode;
import arc.scene.Group;
import arc.scene.Element;
import arc.scene.ui.ImageButton;
import arc.scene.ui.Label;
import arc.scene.event.InputEvent;
import arc.scene.event.InputListener;
import arc.scene.style.TextureRegionDrawable;
import arc.scene.ui.CheckBox;
import arc.scene.ui.TextButton;
import arc.scene.ui.TextField;
import arc.scene.ui.layout.Cell;
import arc.scene.ui.layout.Table;
import arc.struct.ObjectMap;
import arc.struct.ObjectSet;
import arc.struct.Seq;
import arc.util.Strings;
import arc.util.Time;
import arc.util.Align;
import bektools.profiler.NeonProfiler;
import mdtxcompat.OverlayUiBridge;
import mindustry.game.EventType;
import mindustry.content.Blocks;
import mindustry.gen.Icon;
import mindustry.input.InputHandler;
import mindustry.type.Category;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.ui.dialogs.SettingsMenuDialog;
import mindustry.world.blocks.environment.Cliff;
import mindustry.world.blocks.environment.Floor;
import mindustry.world.blocks.environment.Prop;
import mindustry.world.blocks.environment.RemoveOre;
import mindustry.world.blocks.environment.RemoveWall;
import mindustry.world.Block;

import java.lang.reflect.Field;
import java.util.Locale;

import static mindustry.Vars.content;
import static mindustry.Vars.control;
import static mindustry.Vars.player;
import static mindustry.Vars.state;
import static mindustry.Vars.ui;

public class BetterHotKeyFeature {
    private static final String overlayWindowName = "betterhotkey-hotkeys";
    private static final String keyEnabled = "bhk-enabled";
    private static final String keyKeepOrder = "bhk-keep-order";
    private static final String keyCustomEnabled = "bhk-custom-enabled";
    private static final String keySkipTerrainHotkeys = "bhk-skip-terrain-hotkeys";
    private static final String keySkipTerrainIgnoreList = "bhk-skip-terrain-ignore-list";
    private static final String keyShowIconHotkeys = "bhk-show-icon-hotkeys";
    private static final String keyMenuColumns = "bhk-menu-columns";
    private static final String keyIconHotkeyColor = "bhk-icon-hotkey-color";
    private static final String keyBadgeFontScale = "bhk-badge-font-scale";
    private static final String legacyKeyInfoFontScale = "bhk-info-font-scale";
    private static final String keyGroups = "bhk-groups";

    private static final long comboTimeoutMs = 700L;
    private static final float defaultBadgeFontScale = 0.38f;

    private static boolean inited;
    private static boolean enabled;
    private static boolean keepOrder;
    private static boolean customEnabled;
    private static boolean skipTerrainHotkeys;
    private static boolean showIconHotkeys;
    private static int menuColumns;
    private static float badgeFontScale;

    private static final Color defaultIconHotkeyColor = Color.valueOf("ff4d4d");
    private static final Color iconHotkeyColor = new Color(defaultIconHotkeyColor);

    private static final Seq<String> skipTerrainIgnoreNames = new Seq<>();
    private static final ObjectSet<String> skipTerrainIgnoreSet = new ObjectSet<>();

    private static final Seq<GroupConfig> groups = new Seq<>();
    private static final Seq<CompiledBinding> compiledBindings = new Seq<>();
    private static final Seq<KeyCode> watchedKeys = new Seq<>();
    private static final Seq<KeyCode> tappedKeys = new Seq<>();
    private static boolean compiledDirty = true;

    private static KeyCode pendingFirst;
    private static long pendingAt;

    private static boolean reflectReady;
    private static Field fieldBlockTable;
    private static Field fieldCurrentCategory;
    private static Field fieldSelectedBlocks;
    private static Field fieldBlockSelectEnd;
    private static Field fieldBlockSelectSeq;
    private static Field fieldBlockSelectSeqMillis;
    private static Field fieldBlockSelect;

    private static boolean updateHooked;
    private static Block queuedBlock;
    private static Block deferredRemapBlock;

    private static boolean suppressSkipTerrainThisFrame;

    // Used to detect vanilla number-combo progress and to only remap once per tap.
    private static long lastHandledBlockSelectSeqMillis;
    private static int lastHandledBlockSelectSeq = -1;
    private static boolean lastHandledBlockSelectEnd;


    private static Table bhkInfoRow;
    private static Label bhkInfoCustom;
    private static Label bhkInfoNumber;
    private static Label bhkInfoOccupied;

    private static Table bhkOverlay;
    private static Label bhkOverlayCustom;
    private static Label bhkOverlayNumber;
    private static Label bhkOverlayOccupied;

    private static OverlayUiBridge xOverlayUi = OverlayUiBridge.UNSUPPORTED;
    private static OverlayUiBridge.OverlayWindowHandle xHotkeyWindow;
    private static boolean hostedByOverlayUI;
    private static boolean lastOverlaySyncEnabled;

    public static void configureOverlayUi(OverlayUiBridge overlayUi) {
        xOverlayUi = overlayUi == null ? OverlayUiBridge.UNSUPPORTED : overlayUi;
        xHotkeyWindow = null;
        hostedByOverlayUI = false;
        lastOverlaySyncEnabled = false;
    }

    private static final float overlayPad = 8f;
    private static final float overlayRightInset = 56f; // leave room for the '?' button
    private static final float overlayTopInset = 10f;

    private static Field fieldTopTable;
    private static Field fieldMenuHoverBlock;

    private static final String nameVanillaHeaderLabel = "bhk-vanilla-header-label";
    private static final String nameBuildHotkeyBadge = "bhk-block-hotkey-badge";

    public static void init() {
        if (inited) return;
        inited = true;

        Core.settings.defaults(keyEnabled, true);
        Core.settings.defaults(keyKeepOrder, true);
        Core.settings.defaults(keyCustomEnabled, true);
        Core.settings.defaults(keySkipTerrainHotkeys, false);
        Core.settings.defaults(keyShowIconHotkeys, true);
        Core.settings.defaults(keyMenuColumns, 0);
        Core.settings.defaults(keyIconHotkeyColor, "ff4d4d");
        Core.settings.defaults(keyBadgeFontScale, Strings.fixed(defaultBadgeFontScale, 2));

        loadGroups();
        loadSkipTerrainIgnoreList();
        refreshSettings();

        Events.on(EventType.ClientLoadEvent.class, e -> {
            loadGroups();
            loadSkipTerrainIgnoreList();
            refreshSettings();
            rebuildCompiledBindings();
        });

        Events.on(EventType.WorldLoadEvent.class, e -> {
            pendingFirst = null;
            deferredRemapBlock = null;
            lastHandledBlockSelectSeqMillis = 0L;
            lastHandledBlockSelectSeq = -1;
            lastHandledBlockSelectEnd = false;
        });

        // Hook update after the UI is fully created.
        // This ensures vanilla build-menu hotkeys run first, then this mod can override.
        Events.on(EventType.ClientLoadEvent.class, e -> {
            if (updateHooked) return;
            updateHooked = true;

            // Prefer MindustryX OverlayUI if available.
            // Retry once after a short delay; on some clients UI init is slightly later.
            ensureOverlayUiAttached();
            Time.runTask(10f, BetterHotKeyFeature::ensureOverlayUiAttached);

            // Flush queued selections after the vanilla build-menu code has processed inputs.
            Events.run(EventType.Trigger.uiDrawEnd, () -> {
                if (enabled && skipTerrainHotkeys) {
                    remapTerrainNumberHotkeysAfterVanilla();
                    sanitizeInvalidPlacementSelection();
                }

                Block b = queuedBlock;
                queuedBlock = null;
                if (b != null) {
                    selectBlock(b);
                }

                if (enabled) {
                    updateBuildMenuOverlay();
                }

                updateBuildMenuHotkeyBadges();
            });

            Events.run(EventType.Trigger.update, () -> {
                refreshSettings();
                if (!enabled) {
                    pendingFirst = null;
                    deferredRemapBlock = null;
                    return;
                }

                if (deferredRemapBlock != null) {
                    Block block = deferredRemapBlock;
                    deferredRemapBlock = null;
                    selectBlock(block);
                }

                suppressSkipTerrainThisFrame = false;

                if (state != null && state.rules != null && state.isGame()) {
                    state.rules.hideBannedBlocks = false;
                }

                if (keepOrder || menuColumns > 0) {
                    keepBannedBlocksInOriginalOrder();
                }

                if (customEnabled) {
                    updateCustomHotkeys();
                } else {
                    pendingFirst = null;
                }

                syncOverlayUiEnabled();

            });
        });
    }

    private static void ensureOverlayUiAttached() {
        if (hostedByOverlayUI) return;
        if (!xOverlayUi.isSupported()) return;

        // Create content first so OverlayUI can wrap it.
        ensureOverlayTable();

        if (bhkOverlay.hasParent()) {
            bhkOverlay.remove();
        }

        Prov<Boolean> availability = () -> state != null && state.isGame();
        boolean hadStoredState = hasStoredOverlayWindowState(overlayWindowName);
        xHotkeyWindow = xOverlayUi.registerWindow(overlayWindowName, bhkOverlay, availability);
        if (xHotkeyWindow == null || xHotkeyWindow.asElement() == null) return;

        hostedByOverlayUI = true;
        // Small info panel: auto-height, not resizable by default.
        xHotkeyWindow.configure(true, false);
        if (hadStoredState) {
            lastOverlaySyncEnabled = enabled;
        } else {
            // Only auto-show on first registration. Afterwards respect OverlayUI's persisted state.
            lastOverlaySyncEnabled = !enabled;
            syncOverlayUiEnabled();
        }
    }

    private static void syncOverlayUiEnabled() {
        if (xHotkeyWindow == null || xHotkeyWindow.asElement() == null) return;
        if (enabled == lastOverlaySyncEnabled) return;
        lastOverlaySyncEnabled = enabled;
        // Default behavior: when enabled, make it visible (pinned). Players can unpin/hide from OverlayUI.
        xHotkeyWindow.setEnabledAndPinned(enabled, enabled);
    }

    private static boolean hasStoredOverlayWindowState(String windowName) {
        return Core.settings != null && Core.settings.has("overlayUI." + windowName);
    }

    public static void buildSettings(SettingsMenuDialog.SettingsTable table) {
        table.pref(new CenteredCheckSetting(keyEnabled, true, null));
        table.pref(new CenteredCheckSetting(keyKeepOrder, true, null));
        table.pref(new CenteredCheckSetting(keyCustomEnabled, true, null));
        table.pref(new CenteredCheckSetting(keySkipTerrainHotkeys, false, null));
        table.pref(new CenteredCheckSetting(keyShowIconHotkeys, true, null));

        table.pref(new SettingsMenuDialog.SettingsTable.Setting("bhk-open-display-config") {
            @Override
            public void add(SettingsMenuDialog.SettingsTable t) {
                TextButton b = t.button(title, BetterHotKeyFeature::showDisplayConfigDialog).growX().margin(14f).pad(6f).center().get();
                b.getLabel().setAlignment(Align.center);
                b.getLabelCell().growX().align(Align.center);
                t.row();
            }
        });

        table.pref(new SettingsMenuDialog.SettingsTable.Setting("bhk-open-ignore-list") {
            @Override
            public void add(SettingsMenuDialog.SettingsTable t) {
                TextButton b = t.button(title, BetterHotKeyFeature::showSkipTerrainIgnoreDialog).growX().margin(14f).pad(6f).center().get();
                b.getLabel().setAlignment(Align.center);
                b.getLabelCell().growX().align(Align.center);
                t.row();
            }
        });

        table.pref(new SettingsMenuDialog.SettingsTable.Setting("bhk-open-config") {
            @Override
            public void add(SettingsMenuDialog.SettingsTable t) {
                TextButton b = t.button(title, BetterHotKeyFeature::showConfigDialog).growX().margin(14f).pad(6f).center().get();
                b.getLabel().setAlignment(Align.center);
                b.getLabelCell().growX().align(Align.center);
                t.row();
            }
        });

        table.pref(new SettingsMenuDialog.SettingsTable.Setting("bhk-reset-config") {
            @Override
            public void add(SettingsMenuDialog.SettingsTable t) {
                TextButton b = t.button(title, () -> {
                    groups.clear();
                    groups.add(defaultGroup());
                    saveGroups();
                    compiledDirty = true;
                }).growX().margin(14f).pad(6f).center().get();
                b.getLabel().setAlignment(Align.center);
                b.getLabelCell().growX().align(Align.center);
                t.row();
            }
        });

        loadGroups();
        loadSkipTerrainIgnoreList();
        refreshSettings();
        rebuildCompiledBindings();
    }

    private static void loadSkipTerrainIgnoreList() {
        skipTerrainIgnoreNames.clear();
        skipTerrainIgnoreSet.clear();

        Seq<String> names = Core.settings.getJson(keySkipTerrainIgnoreList, Seq.class, String.class, Seq::new);
        for (String s : names) {
            if (s == null) continue;
            String t = s.trim();
            if (t.isEmpty()) continue;
            if (skipTerrainIgnoreSet.add(t)) {
                skipTerrainIgnoreNames.add(t);
            }
        }
    }

    private static void saveSkipTerrainIgnoreList() {
        Core.settings.putJson(keySkipTerrainIgnoreList, String.class, skipTerrainIgnoreNames);
    }

    private static void refreshSettings() {
        enabled = Core.settings.getBool(keyEnabled, true);
        keepOrder = Core.settings.getBool(keyKeepOrder, true);
        customEnabled = Core.settings.getBool(keyCustomEnabled, true);
        skipTerrainHotkeys = Core.settings.getBool(keySkipTerrainHotkeys, false);
        showIconHotkeys = Core.settings.getBool(keyShowIconHotkeys, true);
        menuColumns = Math.max(0, Core.settings.getInt(keyMenuColumns, 0));
        iconHotkeyColor.set(parseStoredColor(Core.settings.getString(keyIconHotkeyColor, "ff4d4d"), defaultIconHotkeyColor));
        String storedBadgeFontScale = Core.settings.getString(keyBadgeFontScale, null);
        if (storedBadgeFontScale == null) {
            storedBadgeFontScale = Core.settings.getString(legacyKeyInfoFontScale, Strings.fixed(defaultBadgeFontScale, 2));
        }
        badgeFontScale = parseStoredBadgeFontScale(storedBadgeFontScale);
    }

    private static void applyLabelFontScale(Label label, float scale) {
        if (label == null) return;
        label.setFontScale(toRenderableFontScale(scale));
        label.invalidateHierarchy();
    }

    private static void loadGroups() {
        groups.clear();

        Seq<String> lines = Core.settings.getJson(keyGroups, Seq.class, String.class, Seq::new);
        GroupConfig current = null;

        for (String line : lines) {
            if (line == null || line.isEmpty()) continue;
            String[] parts = line.split("\\|", -1);
            if (parts.length == 0) continue;

            if ("G".equals(parts[0])) {
                if (parts.length < 3) continue;
                GroupConfig g = new GroupConfig();
                g.name = unescape(parts[1]);
                g.enabled = "1".equals(parts[2]);
                groups.add(g);
                current = g;
            } else if ("B".equals(parts[0])) {
                if (parts.length < 5 || current == null) continue;
                BindingConfig b = new BindingConfig();
                b.blockName = unescape(parts[1]);
                b.firstKey = unescape(parts[2]);
                b.secondKey = unescape(parts[3]);
                b.note = unescape(parts[4]);
                current.bindings.add(b);
            }
        }

        if (groups.isEmpty()) {
            groups.add(defaultGroup());
            saveGroups();
        }

        compiledDirty = true;
    }

    private static void saveGroups() {
        Seq<String> lines = new Seq<>();

        for (GroupConfig g : groups) {
            lines.add("G|" + escape(g.name) + "|" + (g.enabled ? "1" : "0"));
            for (BindingConfig b : g.bindings) {
                lines.add("B|" + escape(b.blockName) + "|" + escape(b.firstKey) + "|" + escape(b.secondKey) + "|" + escape(b.note));
            }
        }

        Core.settings.putJson(keyGroups, String.class, lines);
        compiledDirty = true;
    }

    private static GroupConfig defaultGroup() {
        GroupConfig g = new GroupConfig();
        g.name = Core.bundle.getOrNull("bhk.default.group") != null ? Core.bundle.get("bhk.default.group") : "Default Group";
        g.enabled = true;
        return g;
    }

    private static void rebuildCompiledBindings() {
        if (!compiledDirty) return;
        compiledDirty = false;

        compiledBindings.clear();
        watchedKeys.clear();
        ObjectSet<KeyCode> unique = new ObjectSet<>();

        for (GroupConfig g : groups) {
            if (g == null || !g.enabled) continue;

            for (BindingConfig b : g.bindings) {
                if (b == null || b.blockName == null || b.blockName.isEmpty()) continue;

                Block block = findBlockByName(b.blockName);
                KeyCode first = parseKeyCode(b.firstKey);
                KeyCode second = parseKeyCode(b.secondKey);

                if (block == null || first == null || second == null) continue;
                if (!isCaptureKeyCandidate(first) || !isCaptureKeyCandidate(second)) continue;

                CompiledBinding out = new CompiledBinding();
                out.groupName = g.name;
                out.block = block;
                out.first = first;
                out.second = second;
                compiledBindings.add(out);

                if (unique.add(first)) watchedKeys.add(first);
                if (unique.add(second)) watchedKeys.add(second);
            }
        }
    }

    private static void updateCustomHotkeys() {
        try(NeonProfiler.Scope scope = NeonProfiler.timeDetail("BHK", "Update", "updateCustomHotkeys", NeonProfiler.threadMain)){
        if (state == null || !state.isGame() || player == null || control == null || control.input == null) {
            pendingFirst = null;
            return;
        }
        if (ui == null || ui.hudfrag == null || !ui.hudfrag.shown) return;
        if (ui.chatfrag != null && ui.chatfrag.shown()) return;
        if (ui.consolefrag != null && ui.consolefrag.shown()) return;
        if (Core.scene != null && (Core.scene.hasDialog() || Core.scene.hasField())) return;

        rebuildCompiledBindings();
        if (compiledBindings.isEmpty()) return;

        tappedKeys.clear();
        for (KeyCode key : watchedKeys) {
            if (key == null || key == KeyCode.unset) continue;
            if (Core.input.keyTap(key)) tappedKeys.add(key);
        }

        if (pendingFirst != null && Time.timeSinceMillis(pendingAt) > comboTimeoutMs) {
            pendingFirst = null;
        }

        if (tappedKeys.isEmpty()) return;

        if (pendingFirst != null) {
            for (CompiledBinding b : compiledBindings) {
                if (b.first != pendingFirst) continue;
                if (!tappedKeys.contains(b.second, true)) continue;

                queueSelectBlock(b.block);
                suppressSkipTerrainThisFrame = true;
                pendingFirst = null;
                return;
            }
        }

        for (CompiledBinding b : compiledBindings) {
            if (!tappedKeys.contains(b.first, true)) continue;
            pendingFirst = b.first;
            pendingAt = Time.millis();

            if (isNumberKeyCode(pendingFirst)) {
                suppressSkipTerrainThisFrame = true;
            }
            return;
        }
        }
    }

    private static void queueSelectBlock(Block block) {
        if (block == null) return;
        // Apply immediately so placement logic uses the correct block,
        // then re-apply after vanilla UI hotkeys run (numbers are handled there).
        selectBlock(block);
        queuedBlock = block;
    }

    private static void remapTerrainNumberHotkeysAfterVanilla() {
        try(NeonProfiler.Scope scope = NeonProfiler.timeDetail("BHK", "Update", "remapTerrainNumbers", NeonProfiler.threadMain)){
        if (state == null || !state.isGame() || player == null || control == null || control.input == null) return;
        if (ui == null || ui.hudfrag == null || !ui.hudfrag.shown) return;
        if (ui.chatfrag != null && ui.chatfrag.shown()) return;
        if (ui.consolefrag != null && ui.consolefrag.shown()) return;
        if (Core.scene != null && (Core.scene.hasDialog() || Core.scene.hasField())) return;

        tryInitReflection();
        if (!reflectReady) return;

        try {
            Object pf = ui.hudfrag.blockfrag;
            if (pf == null) return;

            boolean end = fieldBlockSelectEnd.getBoolean(pf);
            int seq = fieldBlockSelectSeq.getInt(pf);
            long seqMillis = fieldBlockSelectSeqMillis.getLong(pf);

            // After timeout, a number tap starts category selection again.
            if (Time.timeSinceMillis(seqMillis) > 400L) return;

            // If a custom numeric hotkey was processed this frame, ignore/remap nothing.
            // Still mark it handled so it doesn't fire next frame.
            if (suppressSkipTerrainThisFrame) {
                lastHandledBlockSelectSeqMillis = seqMillis;
                lastHandledBlockSelectSeq = seq;
                lastHandledBlockSelectEnd = end;
                return;
            }

            // Only react once per vanilla combo update.
            if (seqMillis == lastHandledBlockSelectSeqMillis &&
                seq == lastHandledBlockSelectSeq &&
                end == lastHandledBlockSelectEnd) return;
            lastHandledBlockSelectSeqMillis = seqMillis;
            lastHandledBlockSelectSeq = seq;
            lastHandledBlockSelectEnd = end;

            // Category digit -> do nothing.
            if (seq == 0 && !end) return;

            Category cat = (Category) fieldCurrentCategory.get(pf);
            if (cat == null) return;

            int index;
            if (!end) {
                // 2-digit selection: the last digit is stored in seq (1..10).
                index = seq - 1;
            } else {
                // 3-digit selection: infer the final index by locating vanilla-selected block in vanilla's list.
                Block vanillaSelected = control.input.block;
                if (vanillaSelected == null) return;
                index = indexOf(getByCategoryWithTerrain(cat), vanillaSelected);
                if (index < 0) return;
            }

            Seq<Block> blocks = getByCategorySkipTerrain(cat);
            if (index < 0 || index >= blocks.size) return;

            Block remapped = blocks.get(index);
            if (remapped == null || !canSelect(remapped)) return;

            deferredRemapBlock = remapped;
        } catch (Throwable ignored) {
        }
        }
    }

    private static void updateBuildMenuOverlay() {
        try(NeonProfiler.Scope scope = NeonProfiler.timeDetail("BHK", "UI", "updateBuildMenuOverlay", NeonProfiler.threadMain)){
        if (ui == null || ui.hudfrag == null || ui.hudfrag.blockfrag == null) return;
        if (state == null || !state.isGame()) return;

        rebuildCompiledBindings();
        tryInitReflection();
        if (!reflectReady) {
            // Still render something in OverlayUI mode so the window isn't blank.
            ensureOverlayTable();
            bhkOverlayCustom.setText("[lightgray]" + Core.bundle.get("bhk.info.custom") + ":[] -");
            bhkOverlayCustom.visible = true;
            if (skipTerrainHotkeys) {
                bhkOverlayNumber.setText("[lightgray]" + Core.bundle.get("bhk.info.number") + ":[] -");
                bhkOverlayNumber.visible = true;
            } else {
                bhkOverlayNumber.setText("");
                bhkOverlayNumber.visible = false;
            }
            bhkOverlayOccupied.setText("");
            bhkOverlayOccupied.visible = false;
            bhkOverlay.visible = hostedByOverlayUI;
            return;
        }

        try {
            Object pf = ui.hudfrag.blockfrag;
            Table outer = (Table) fieldTopTable.get(pf);
            if (outer == null && !hostedByOverlayUI) return;

            ensureOverlayTable();
            if (!hostedByOverlayUI && bhkOverlay.hasParent()) {
                bhkOverlay.remove();
            }

            Block hover = (Block) fieldMenuHoverBlock.get(pf);
            Block display = hover != null ? hover : control.input.block;
            if (display == null) {
                // Keep OverlayUI windows informative even when nothing is selected.
                bhkOverlayCustom.setText("[lightgray]" + Core.bundle.get("bhk.info.custom") + ":[] -");
                bhkOverlayCustom.visible = true;
                if (skipTerrainHotkeys) {
                    bhkOverlayNumber.setText("[lightgray]" + Core.bundle.get("bhk.info.number") + ":[] -");
                    bhkOverlayNumber.visible = true;
                } else {
                    bhkOverlayNumber.setText("");
                    bhkOverlayNumber.visible = false;
                }
                bhkOverlayOccupied.setText("");
                bhkOverlayOccupied.visible = false;
                bhkOverlay.visible = hostedByOverlayUI;
                return;
            }

            if (outer != null) {
                patchVanillaPlacementKeyComboLabel(pf, outer, display);
            }

            String custom = enabled && customEnabled ? customComboFor(display) : "";

            boolean hasCustom = custom != null && !custom.isEmpty();

            String key = "";
            String number = "";
            boolean occupied = false;

            if (hasCustom) {
                key = custom;
            }
            if (enabled && skipTerrainHotkeys) {
                number = formatPlacementNumberCombo(pf, display, true);
                occupied = isNumberComboOccupiedByCustom(display, number);
            }

            boolean hasKey = key != null && !key.isEmpty();
            boolean hasNumber = number != null && !number.isEmpty();

            // In OverlayUI mode, always show at least a placeholder so the window isn't empty.
            if (hasKey) {
                bhkOverlayCustom.setText("[lightgray]" + Core.bundle.get("bhk.info.custom") + ":[] " + key);
            } else {
                bhkOverlayCustom.setText("[lightgray]" + Core.bundle.get("bhk.info.custom") + ":[] -");
            }
            bhkOverlayCustom.visible = true;

            if (skipTerrainHotkeys) {
                if (hasNumber) {
                    bhkOverlayNumber.setText("[lightgray]" + Core.bundle.get("bhk.info.number") + ":[] " + number);
                } else {
                    bhkOverlayNumber.setText("[lightgray]" + Core.bundle.get("bhk.info.number") + ":[] -");
                }
                bhkOverlayNumber.visible = true;
            } else {
                bhkOverlayNumber.setText("");
                bhkOverlayNumber.visible = false;
            }

            if (occupied) {
                bhkOverlayOccupied.setText("[red]" + Core.bundle.get("bhk.info.occupied") + "[]");
                bhkOverlayOccupied.visible = true;
            } else {
                bhkOverlayOccupied.setText("");
                bhkOverlayOccupied.visible = false;
            }

            bhkOverlay.visible = hostedByOverlayUI;
        } catch (Throwable ignored) {
        }
        }
    }

    private static void patchVanillaPlacementKeyComboLabel(Object pf, Table outer, Block display) {
        if (pf == null || outer == null || display == null) return;
        if (!reflectReady) return;

        try {
            if (outer.getChildren().isEmpty()) return;
            Element first = outer.getChildren().first();
            if (!(first instanceof Table)) return;
            Table inner = (Table) first;

            Table headerTable = null;
            for (Element e : inner.getChildren()) {
                if (e instanceof Table) {
                    headerTable = (Table) e;
                    break;
                }
            }
            if (headerTable == null) return;

            Label header = findVanillaHeaderLabel(headerTable, display);
            if (header == null) return;

            if (!nameVanillaHeaderLabel.equals(header.name)) {
                Label replacement = new Label("", header.getStyle());
                replacement.name = nameVanillaHeaderLabel;
                replacement.setWrap(true);
                replacement.setAlignment(header.getLabelAlign(), header.getLineAlign());
                replacement.setText(() -> currentPlacementHeaderText(pf));

                if (!replaceLabelInTable(headerTable, header, replacement)) return;
                header = replacement;
            }

            header.setText(() -> currentPlacementHeaderText(pf));
        } catch (Throwable ignored) {
        }
    }

    private static boolean replaceLabelInTable(Table table, Label oldLabel, Label newLabel) {
        if (table == null || oldLabel == null || newLabel == null) return false;

        for (Cell<?> cell : table.getCells()) {
            if (cell == null || !cell.hasElement()) continue;

            Element element = cell.get();
            if (element == oldLabel) {
                cell.setElement(newLabel);
                return true;
            }

            if (element instanceof Table && replaceLabelInTable((Table) element, oldLabel, newLabel)) {
                return true;
            }
        }

        return false;
    }

    private static String currentPlacementHeaderText(Object pf) {
        if (pf == null) return "";

        try {
            Block hover = (Block) fieldMenuHoverBlock.get(pf);
            Block display = hover != null ? hover : control.input.block;
            return display == null ? "" : buildPlacementHeaderText(pf, display);
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static String buildPlacementHeaderText(Object pf, Block display) {
        if (pf == null || display == null) return "";

        String title = display.localizedName == null ? "" : display.localizedName;
        StringBuilder text = new StringBuilder(title);

        boolean useSkipTerrainOrder = enabled && skipTerrainHotkeys;
        String vanillaCombo = formatPlacementVanillaCombo(pf, display, useSkipTerrainOrder);
        if (!vanillaCombo.isEmpty()) {
            text.append(vanillaCombo);
        }

        if (enabled && customEnabled) {
            String custom = customComboFor(display);
            if (custom != null && !custom.isEmpty()) {
                text.append("\n[lightgray]")
                    .append(Core.bundle.get("bhk.info.custom"))
                    .append(":[] ")
                    .append(custom);
            }
        }

        if (enabled && skipTerrainHotkeys) {
            String numberCombo = formatPlacementNumberCombo(pf, display, true);
            if (isNumberComboOccupiedByCustom(display, numberCombo)) {
                text.append("\n[red]")
                    .append(Core.bundle.get("bhk.info.occupied"))
                    .append("[]");
            }
        }

        return text.toString();
    }

    private static void sanitizeInvalidPlacementSelection() {
        if (control == null || control.input == null) return;

        Block current = control.input.block;
        if (!shouldForceDistributionFallback(current)) return;

        Block fallback = pickDistributionFallback();
        if (fallback == null || fallback == current) return;

        deferredRemapBlock = null;
        queueSelectBlock(fallback);
    }

    private static boolean shouldForceDistributionFallback(Block block) {
        if (block == null) return false;

        try {
            if (block.isAir() || block == Blocks.air) return true;
        } catch (Throwable ignored) {
        }

        if (block == Blocks.spawn) return true;

        String name = block.name == null ? "" : block.name.toLowerCase(Locale.ROOT);
        return "air".equals(name) || "spawn".equals(name);
    }

    private static Block pickDistributionFallback() {
        Block anchor = pickDistributionAnchor();
        if (anchor != null && canSelect(anchor)) {
            return anchor;
        }

        Seq<Block> blocks = getByCategorySkipTerrain(Category.distribution);
        for (Block block : blocks) {
            if (block != null && canSelect(block)) {
                return block;
            }
        }

        return null;
    }

    private static String formatPlacementVanillaCombo(Object pf, Block display, boolean skipTerrainOrder) {
        if (pf == null || display == null) return "";

        try {
            Category cat = (Category) fieldCurrentCategory.get(pf);
            if (cat == null) return "";
            if (skipTerrainOrder && isTerrainBlock(display)) return "";

            Seq<Block> blocks = skipTerrainOrder ? getByCategorySkipTerrain(cat) : getByCategoryWithTerrain(cat);
            int index = indexOf(blocks, display);
            return index >= 0 ? formatVanillaKeyCombo(pf, cat, index) : "";
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static String formatPlacementNumberCombo(Object pf, Block display, boolean skipTerrainOrder) {
        if (pf == null || display == null) return "";

        try {
            Category cat = (Category) fieldCurrentCategory.get(pf);
            if (cat == null) return "";
            if (skipTerrainOrder && isTerrainBlock(display)) return "";

            Seq<Block> blocks = skipTerrainOrder ? getByCategorySkipTerrain(cat) : getByCategoryWithTerrain(cat);
            int index = indexOf(blocks, display);
            return index >= 0 ? formatNumberCombo(cat, index) : "";
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static Label findVanillaHeaderLabel(Element root, Block display) {
        if (root == null || display == null) return null;

        String want = display.localizedName == null ? "" : Strings.stripColors(display.localizedName).trim();
        if (want.isEmpty()) return null;

        Seq<Element> stack = new Seq<>();
        stack.add(root);

        while (stack.size > 0) {
            Element e = stack.pop();
            if (e instanceof Label) {
                Label l = (Label) e;
                String t;
                try {
                    t = l.getText() == null ? "" : l.getText().toString();
                } catch (Throwable ignored) {
                    t = "";
                }
                String stripped = Strings.stripColors(t);
                if (stripped != null && stripped.contains(want)) {
                    return l;
                }
            }

            if (e instanceof Group) {
                Group g = (Group) e;
                for (Element child : g.getChildren()) {
                    if (child != null) stack.add(child);
                }
            }
        }

        return null;
    }

    private static String formatVanillaKeyCombo(Object pf, Category cat, int index) {
        if (pf == null || cat == null || index < 0) return "";
        if (fieldBlockSelect == null) return "";

        try {
            Object value = fieldBlockSelect.get(pf);
            if (!(value instanceof KeyBind[])) return "";
            KeyBind[] blockSelect = (KeyBind[]) value;
            if (cat.ordinal() < 0 || cat.ordinal() >= blockSelect.length) return "";

            String first = safeKeyBindToString(blockSelect[cat.ordinal()]);
            if (first.isEmpty()) return "";

            // Same format as vanilla: "\n[lightgray]按键：[{0},<...>]"
            String combo = Core.bundle.format("placement.blockselectkeys", first);

            if (index >= 10) {
                int tens = (index + 1) / 10 - 1;
                if (tens >= 0 && tens < blockSelect.length) {
                    String tensKey = safeKeyBindToString(blockSelect[tens]);
                    if (!tensKey.isEmpty()) {
                        combo += tensKey + ",";
                    } else {
                        return "";
                    }
                } else {
                    return "";
                }
            }

            String last = safeKeyBindToString(blockSelect[index % 10]);
            if (last.isEmpty()) return "";
            combo += last + "]";
            return combo;
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static String safeKeyBindToString(KeyBind bind) {
        if (bind == null) return "";
        try {
            if (bind.value == null || bind.value.key == null) return "";
            return bind.value.key.toString();
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static void ensureOverlayTable() {
        if (bhkOverlay != null) return;

        bhkOverlay = new Table();
        bhkOverlay.touchable = arc.scene.event.Touchable.disabled;
        bhkOverlay.left();
        bhkOverlay.defaults().left();

        bhkOverlayCustom = new Label("");
        bhkOverlayCustom.setWrap(true);
        bhkOverlayCustom.setAlignment(Align.left);

        bhkOverlayNumber = new Label("");
        bhkOverlayNumber.setWrap(true);
        bhkOverlayNumber.setAlignment(Align.left);

        bhkOverlayOccupied = new Label("");
        bhkOverlayOccupied.setWrap(true);
        bhkOverlayOccupied.setAlignment(Align.left);

        // Keep this overlay independent of layout; keep it compact.
        bhkOverlay.add(bhkOverlayCustom).maxWidth(220f).left().row();
        bhkOverlay.add(bhkOverlayNumber).maxWidth(220f).left().row();
        bhkOverlay.add(bhkOverlayOccupied).maxWidth(220f).left();
    }

    private static Seq<Block> getByCategoryWithTerrain(Category cat) {
        Seq<Block> out = new Seq<>();
        for (Block block : content.blocks()) {
            if (block == null) continue;
            if (block.category != cat) continue;
            if (!block.isVisible()) continue;
            if (!block.environmentBuildable()) continue;
            out.add(block);
        }
        return out;
    }

    private static Seq<Block> getByCategorySkipTerrain(Category cat) {
        Seq<Block> out = new Seq<>();

        Block anchor = null;
        if (cat == Category.distribution) {
            // When maps reveal a lot of "terrain-like" blocks, they often end up before conveyors
            // and shift all numeric hotkeys. Force the first real belt/duct to be index 0.
            anchor = pickDistributionAnchor();
            if (anchor != null && !(anchor.category == cat && anchor.isVisible() && anchor.environmentBuildable())) {
                anchor = null;
            }
        }

        boolean beforeAnchor = anchor != null;
        for (Block block : content.blocks()) {
            if (block == null) continue;
            if (block.category != cat) continue;
            if (!block.isVisible()) continue;
            if (!block.environmentBuildable()) continue;

            if (beforeAnchor) {
                if (block == anchor) {
                    beforeAnchor = false;
                } else {
                    // Hard skip: everything before the anchor counts as "terrain" for numbering.
                    continue;
                }
            }

            if (isTerrainBlock(block)) continue;
            if (!canSelect(block)) continue;
            out.add(block);
        }
        return out;
    }

    private static Block pickDistributionAnchor() {
        // Serpulo belt.
        if (Blocks.conveyor != null && Blocks.conveyor.isVisible() && Blocks.conveyor.environmentBuildable()) {
            return Blocks.conveyor;
        }
        // Erekir duct.
        if (Blocks.duct != null && Blocks.duct.isVisible() && Blocks.duct.environmentBuildable()) {
            return Blocks.duct;
        }
        return null;
    }

    private static boolean isTerrainBlock(Block block) {
        if (block == null) return false;

        // Never assign numeric hotkeys to air.
        try {
            if (block.isAir() || "air".equals(block.name)) {
                return true;
            }
        } catch (Throwable ignored) {
        }

        // Some modded map blocks are named "air" but are not the real Blocks.air.
        try {
            String ln = block.localizedName == null ? "" : Strings.stripColors(block.localizedName).trim();
            if ("air".equalsIgnoreCase(ln)) {
                return true;
            }
        } catch (Throwable ignored) {
        }

        // User override: always ignore these blocks when "skip terrain" is enabled.
        if (skipTerrainIgnoreSet.contains(block.name)) {
            return true;
        }

        // Hardcoded baseline: treat everything loaded before the first real building (conveyor) as terrain.
        // This stabilizes numeric hotkeys on maps that reveal many environment blocks.
        try {
            if (block.category == Category.distribution && Blocks.conveyor != null && block.id < Blocks.conveyor.id) {
                return true;
            }
        } catch (Throwable ignored) {
        }

        // Core terrain classes.
        if (block instanceof Floor ||
            block instanceof Prop ||
            block instanceof Cliff ||
            block instanceof RemoveWall ||
            block instanceof RemoveOre) {
            return true;
        }

        // Blocks that are normally hidden but are temporarily revealed by map rules are effectively "terrain".
        // This catches vanilla+modded map-build blocks that don't extend environment base classes.
        try {
            if (state != null && state.rules != null && state.rules.revealedBlocks != null) {
                if (!block.buildVisibility.visible() && state.rules.revealedBlocks.contains(block)) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }

        // Practical filter for many modded "terrain" blocks that show up in the build menu:
        // user-visible names often contain words like "tree"/"build"/"cluster".
        // This is intentionally opinionated, and only affects the "skip terrain hotkeys" feature.
        try {
            String internal = block.name == null ? "" : block.name.toLowerCase(Locale.ROOT);
            String local = block.localizedName == null ? "" : Strings.stripColors(block.localizedName);

            if (internal.contains("build") || internal.contains("tree") || internal.contains("cluster")) {
                return true;
            }

            // CN-focused keywords.
            if (local.contains("树") || local.contains("簇")) {
                return true;
            }

            if (internal.contains("air")) {
                return true;
            }
        } catch (Throwable ignored) {
        }

        // Heuristic catch-all for map-revealed "terrain-like" blocks (including modded ones):
        // these are usually instant-build, costless, and exist outside the normal tech/build flow.
        if (block.instantBuild && (block.requirements == null || block.requirements.length == 0)) {
            return true;
        }

        // Many decorative/map blocks are costless even when not instantBuild.
        if (block.requirements == null || block.requirements.length == 0) {
            return true;
        }

        return false;
    }

    private static boolean selectBlock(Block block) {
        if (block == null || control == null || control.input == null) return false;

        if (state != null && state.rules != null && !state.rules.editor && block.isBanned()) {
            ui.showInfoToast(Core.bundle.format("bhk.toast.banned", block.localizedName), 3f);
            return false;
        }
        if (!canSelect(block)) return false;

        control.input.block = block;
        setPlacementCategory(block.category);
        rememberSelectedBlock(block.category, block);

        if (ui != null && ui.hudfrag != null && ui.hudfrag.blockfrag != null) {
            ui.hudfrag.blockfrag.rebuild();
        }
        return true;
    }

    private static boolean canSelect(Block block) {
        return block.unlockedNowHost() && block.placeablePlayer && block.environmentBuildable() && block.supportsEnv(state.rules.env);
    }

    private static void setPlacementCategory(Category cat) {
        if (cat == null || ui == null || ui.hudfrag == null || ui.hudfrag.blockfrag == null) return;
        tryInitReflection();
        if (!reflectReady) return;

        try {
            fieldCurrentCategory.set(ui.hudfrag.blockfrag, cat);
        } catch (Throwable ignored) {
        }
    }

    @SuppressWarnings("unchecked")
    private static void rememberSelectedBlock(Category cat, Block block) {
        if (cat == null || block == null || ui == null || ui.hudfrag == null || ui.hudfrag.blockfrag == null) return;
        tryInitReflection();
        if (!reflectReady) return;

        try {
            Object value = fieldSelectedBlocks.get(ui.hudfrag.blockfrag);
            if (value instanceof ObjectMap) {
                ObjectMap<Category, Block> map = (ObjectMap<Category, Block>) value;
                map.put(cat, block);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void keepBannedBlocksInOriginalOrder() {
        if (ui == null || ui.hudfrag == null || ui.hudfrag.blockfrag == null) return;
        if (state == null || !state.isGame()) return;

        tryInitReflection();
        if (!reflectReady) return;

        try {
            Object tableObj = fieldBlockTable.get(ui.hudfrag.blockfrag);
            Object catObj = fieldCurrentCategory.get(ui.hudfrag.blockfrag);
            if (!(tableObj instanceof Table) || !(catObj instanceof Category)) return;

            Table table = (Table) tableObj;
            Category cat = (Category) catObj;

            ObjectMap<String, Element> actorByBlock = new ObjectMap<>();
            Seq<String> currentOrder = new Seq<>();

            for (Cell<?> cell : table.getCells()) {
                Element actor = cell.get();
                if (actor == null || actor.name == null) continue;
                if (!actor.name.startsWith("block-")) continue;

                String blockName = actor.name.substring("block-".length());
                currentOrder.add(blockName);
                actorByBlock.put(blockName, actor);
            }

            if (currentOrder.isEmpty()) return;

            Seq<String> desiredOrder = new Seq<>();
            if (keepOrder) {
                for (Block block : content.blocks()) {
                    if (block.category != cat) continue;
                    if (!block.isVisible()) continue;
                    if (!canSelect(block)) continue;
                    desiredOrder.add(block.name);
                }
                if (desiredOrder.size != currentOrder.size) return;
            } else {
                desiredOrder.addAll(currentOrder);
            }

            int currentColumns = detectBuildMenuColumns(table);
            int targetColumns = resolveBuildMenuColumns(currentColumns);
            if (targetColumns <= 0) targetColumns = 4;

            if (isSameOrder(currentOrder, desiredOrder) && currentColumns == targetColumns) return;

            table.clear();
            int index = 0;
            for (String name : desiredOrder) {
                Element actor = actorByBlock.get(name);
                if (actor == null) continue;

                table.add(actor).size(46f);
                if (++index % targetColumns == 0) table.row();
            }

            int remainder = index % targetColumns;
            if (index > 0 && remainder != 0) {
                for (int i = 0; i < targetColumns - remainder; i++) {
                    table.add().size(46f);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static int detectBuildMenuColumns(Table table) {
        if (table == null) return 4;

        int detected = 0;
        int current = 0;
        for (Cell<?> cell : table.getCells()) {
            Element actor = cell.get();
            if (actor != null && actor.name != null && actor.name.startsWith("block-")) {
                current++;
            }
            if (cell.isEndRow()) {
                if (current > 0) detected = Math.max(detected, current);
                current = 0;
            }
        }

        if (current > 0) detected = Math.max(detected, current);
        return detected > 0 ? detected : 4;
    }

    private static int resolveBuildMenuColumns(int detectedColumns) {
        if (menuColumns > 0) {
            return Math.min(menuColumns, 12);
        }
        return detectedColumns > 0 ? detectedColumns : 4;
    }

    private static boolean isSameOrder(Seq<String> a, Seq<String> b) {
        if (a.size != b.size) return false;
        for (int i = 0; i < a.size; i++) {
            if (!a.get(i).equals(b.get(i))) return false;
        }
        return true;
    }

    private static Block findBlockByName(String name) {
        if (name == null || name.isEmpty()) return null;
        for (Block block : content.blocks()) {
            if (name.equals(block.name)) return block;
        }
        return null;
    }

    private static KeyCode parseKeyCode(String raw) {
        if (raw == null) return null;
        String normalized = raw.trim();
        if (normalized.isEmpty()) return null;

        normalized = normalized.replace('-', '_').replace(' ', '_');

        // Back-compat: older configs stored number keys as "1".."0".
        if (normalized.length() == 1 && Character.isDigit(normalized.charAt(0))) {
            try {
                return KeyCode.valueOf("num" + normalized);
            } catch (Throwable ignored) {
            }
        }

        try {
            return KeyCode.valueOf(normalized);
        } catch (Throwable ignored) {
        }

        try {
            return KeyCode.valueOf(normalized.toLowerCase(Locale.ROOT));
        } catch (Throwable ignored) {
        }

        try {
            return KeyCode.valueOf(normalized.toUpperCase(Locale.ROOT));
        } catch (Throwable ignored) {
        }

        for (KeyCode code : KeyCode.all) {
            if (code == null) continue;
            if (code.name().equalsIgnoreCase(normalized)) return code;
        }

        return null;
    }

    private static void tryInitReflection() {
        if (reflectReady) return;
        try {
            Class<?> cls = Class.forName("mindustry.ui.fragments.PlacementFragment");
            fieldBlockTable = cls.getDeclaredField("blockTable");
            fieldCurrentCategory = cls.getDeclaredField("currentCategory");
            fieldSelectedBlocks = cls.getDeclaredField("selectedBlocks");
            fieldBlockSelectEnd = cls.getDeclaredField("blockSelectEnd");
            fieldBlockSelectSeq = cls.getDeclaredField("blockSelectSeq");
            fieldBlockSelectSeqMillis = cls.getDeclaredField("blockSelectSeqMillis");
            fieldBlockSelect = cls.getDeclaredField("blockSelect");
            fieldTopTable = cls.getDeclaredField("topTable");
            fieldMenuHoverBlock = cls.getDeclaredField("menuHoverBlock");

            fieldBlockTable.setAccessible(true);
            fieldCurrentCategory.setAccessible(true);
            fieldSelectedBlocks.setAccessible(true);
            fieldBlockSelectEnd.setAccessible(true);
            fieldBlockSelectSeq.setAccessible(true);
            fieldBlockSelectSeqMillis.setAccessible(true);
            fieldBlockSelect.setAccessible(true);
            fieldTopTable.setAccessible(true);
            fieldMenuHoverBlock.setAccessible(true);
            reflectReady = true;
        } catch (Throwable ignored) {
            reflectReady = false;
        }
    }

    private static boolean isNumberKeyCode(KeyCode code) {
        if (code == null) return false;
        String n = code.name();
        if (n == null) return false;
        String s = n.toLowerCase(Locale.ROOT);
        return s.startsWith("num") || s.startsWith("numpad");
    }

    private static void decorateBuildMenuInfo() {
        if (ui == null || ui.hudfrag == null || ui.hudfrag.blockfrag == null) return;
        if (state == null || !state.isGame()) return;

        rebuildCompiledBindings();
        tryInitReflection();
        if (!reflectReady) return;

        try {
            Object pf = ui.hudfrag.blockfrag;
            Table outer = (Table) fieldTopTable.get(pf);
            if (outer == null) return;
            if (outer.getChildren().isEmpty()) return;
            if (!(outer.getChildren().first() instanceof Table)) return;

            Table inner = (Table) outer.getChildren().first();

            if (bhkInfoRow == null) {
                bhkInfoRow = new Table();
                bhkInfoRow.name = "bhk-info";
                bhkInfoRow.left();
                bhkInfoRow.defaults().left();

                bhkInfoCustom = new Label("");
                bhkInfoCustom.setWrap(true);
                bhkInfoCustom.setAlignment(Align.left);

                bhkInfoNumber = new Label("");
                bhkInfoNumber.setWrap(true);
                bhkInfoNumber.setAlignment(Align.left);

                bhkInfoOccupied = new Label("");
                bhkInfoOccupied.setWrap(true);
                bhkInfoOccupied.setAlignment(Align.left);

                bhkInfoRow.add(bhkInfoCustom).width(220f).left().row();
                bhkInfoRow.add(bhkInfoNumber).width(220f).left();
                bhkInfoRow.row();
                bhkInfoRow.add(bhkInfoOccupied).width(220f).left();
            }

            Block hover = (Block) fieldMenuHoverBlock.get(pf);
            Block display = hover != null ? hover : control.input.block;
            if (display == null) {
                // If the info box is showing something else, don't leave stale rows behind.
                if (bhkInfoRow.hasParent()) bhkInfoRow.remove();
                return;
            }

            String custom = customComboFor(display);

            String vanilla = "";
            if (skipTerrainHotkeys) {
                Category cat = (Category) fieldCurrentCategory.get(pf);
                if (cat != null) {
                    int idx = indexOf(getByCategorySkipTerrain(cat), display);
                    if (idx >= 0) {
                        vanilla = formatNumberCombo(cat, idx);
                    }
                }
            }

            boolean occupied = isNumberComboOccupiedByCustom(display, vanilla);

            boolean hasCustom = custom != null && !custom.isEmpty();
            boolean hasVanilla = vanilla != null && !vanilla.isEmpty();
            if (!hasCustom && !hasVanilla && !occupied) {
                if (bhkInfoRow.hasParent()) bhkInfoRow.remove();
                return;
            }

            // Attach once per rebuild; do not call row() every frame.
            if (bhkInfoRow.parent != inner) {
                // Clean up any previously injected instances from older versions.
                for (int i = inner.getChildren().size - 1; i >= 0; i--) {
                    Element e = inner.getChildren().get(i);
                    if (e != null && e != bhkInfoRow && "bhk-info".equals(e.name)) {
                        e.remove();
                    }
                }

                if (bhkInfoRow.hasParent()) bhkInfoRow.remove();
                inner.row();
                inner.add(bhkInfoRow).growX().left().padTop(2f);
            }

            if (hasCustom) {
                bhkInfoCustom.setText("[lightgray]" + Core.bundle.get("bhk.info.custom") + ":[] " + custom);
                bhkInfoCustom.visible = true;
            } else {
                bhkInfoCustom.setText("");
                bhkInfoCustom.visible = false;
            }

            if (hasVanilla) {
                bhkInfoNumber.setText("[lightgray]" + Core.bundle.get("bhk.info.number") + ":[] " + vanilla);
                bhkInfoNumber.visible = true;
            } else {
                bhkInfoNumber.setText("");
                bhkInfoNumber.visible = false;
            }

            if (occupied) {
                bhkInfoOccupied.setText("[red]" + Core.bundle.get("bhk.info.occupied") + "[]");
                bhkInfoOccupied.visible = true;
            } else {
                bhkInfoOccupied.setText("");
                bhkInfoOccupied.visible = false;
            }
        } catch (Throwable ignored) {
        }
    }

    private static void updateBuildMenuHotkeyBadges() {
        try(NeonProfiler.Scope scope = NeonProfiler.timeDetail("BHK", "UI", "updateHotkeyBadges", NeonProfiler.threadMain)){
        if (ui == null || ui.hudfrag == null || ui.hudfrag.blockfrag == null) return;
        if (state == null || !state.isGame()) return;

        rebuildCompiledBindings();
        tryInitReflection();
        if (!reflectReady) return;

        try {
            Object pf = ui.hudfrag.blockfrag;
            Object tableObj = fieldBlockTable.get(pf);
            Object catObj = fieldCurrentCategory.get(pf);
            if (!(tableObj instanceof Table) || !(catObj instanceof Category)) return;

            Table table = (Table) tableObj;
            Category cat = (Category) catObj;
            for (Cell<?> cell : table.getCells()) {
                Element actor = cell.get();
                if (!(actor instanceof ImageButton) || actor.name == null || !actor.name.startsWith("block-")) continue;

                ImageButton button = (ImageButton) actor;
                Label badge = findOrCreateBuildHotkeyBadge(button);
                String badgeText = enabled && showIconHotkeys ? buildBlockHotkeyBadgeText(pf, actor.name.substring("block-".length())) : "";

                if (badgeText == null || badgeText.isEmpty()) {
                    badge.visible = false;
                    continue;
                }

                badge.setText(badgeText);
                badge.setColor(iconHotkeyColor);
                badge.setFontScale(toRenderableFontScale(badgeFontScale));
                badge.invalidateHierarchy();
                badge.pack();
                badge.setPosition(Math.max(1f, button.getWidth() - badge.getPrefWidth() - 1f), 1f);
                badge.toFront();
                badge.visible = true;
            }
        } catch (Throwable ignored) {
        }
        }
    }

    private static Label findOrCreateBuildHotkeyBadge(ImageButton button) {
        for (Element child : button.getChildren()) {
            if (child instanceof Label && nameBuildHotkeyBadge.equals(child.name)) {
                return (Label) child;
            }
        }

        Label badge = new Label("", Styles.outlineLabel);
        badge.name = nameBuildHotkeyBadge;
        badge.touchable = arc.scene.event.Touchable.disabled;
        badge.setAlignment(Align.right);
        badge.visible = false;
        button.addChild(badge);
        return badge;
    }

    private static String buildBlockHotkeyBadgeText(Object pf, String blockName) {
        Block block = findBlockByName(blockName);
        if (block == null) return "";

        if (customEnabled) {
            String custom = primaryCustomComboFor(block);
            if (!custom.isEmpty()) return custom;
        }

        return formatPlacementNumberCombo(pf, block, skipTerrainHotkeys);
    }

    private static boolean isNumberComboOccupiedByCustom(Block display, String vanillaCombo) {
        if (display == null || vanillaCombo == null || vanillaCombo.isEmpty()) return false;

        String cleaned = vanillaCombo.replace("[", "").replace("]", "").trim();
        String[] parts = cleaned.split(",");
        if (parts.length != 2) return false;

        KeyCode first = parseDigitKey(parts[0]);
        KeyCode second = parseDigitKey(parts[1]);
        if (first == null || second == null) return false;

        for (CompiledBinding b : compiledBindings) {
            if (b == null || b.block == null) continue;
            if (b.block == display) continue;
            if (b.first == first && b.second == second) return true;
        }
        return false;
    }

    private static KeyCode parseDigitKey(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.isEmpty()) return null;
        if (t.length() != 1 || !Character.isDigit(t.charAt(0))) return null;
        try {
            return KeyCode.valueOf("num" + t);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static int indexOf(Seq<Block> blocks, Block target) {
        if (blocks == null || target == null) return -1;
        for (int i = 0; i < blocks.size; i++) {
            if (blocks.get(i) == target) return i;
        }
        return -1;
    }

    private static String customComboFor(Block block) {
        if (block == null) return "";
        if (compiledBindings.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (CompiledBinding b : compiledBindings) {
            if (b == null || b.block != block) continue;
            if (count++ > 0) sb.append("  ");
            sb.append("[").append(shortKeyName(b.first)).append(",").append(shortKeyName(b.second)).append("]");
            if (count >= 3) break;
        }
        return sb.toString();
    }

    private static String primaryCustomComboFor(Block block) {
        if (block == null || compiledBindings.isEmpty()) return "";

        for (CompiledBinding b : compiledBindings) {
            if (b == null || b.block != block) continue;
            return "[" + shortKeyName(b.first) + "," + shortKeyName(b.second) + "]";
        }
        return "";
    }

    private static String shortKeyName(KeyCode code) {
        if (code == null) return "?";
        String n = code.name();
        if (n == null) return "?";
        String s = n.toLowerCase(Locale.ROOT);
        if (s.startsWith("num") && s.length() == 4 && Character.isDigit(s.charAt(3))) return String.valueOf(s.charAt(3));
        if (s.equals("num0")) return "0";
        if (s.equals("backtick") || s.equals("grave")) return "`";
        if (s.equals("backspace")) return "BKSP";
        if (s.equals("escape")) return "ESC";
        if (s.equals("space")) return "SPC";
        return s.toUpperCase(Locale.ROOT);
    }

    private static int digitForIndex(int i) {
        int v = i % 10;
        return v == 9 ? 0 : v + 1;
    }

    private static String formatNumberCombo(Category cat, int blockIndex) {
        if (cat == null || cat.ordinal() < 0) return "";

        int catDigit = digitForIndex(cat.ordinal());
        if (blockIndex < 10) {
            return "[" + catDigit + "," + digitForIndex(blockIndex) + "]";
        }
        int tensIndex = (blockIndex + 1) / 10 - 1;
        int onesIndex = blockIndex % 10;
        return "[" + catDigit + "," + digitForIndex(tensIndex) + "," + digitForIndex(onesIndex) + "]";
    }

    private static void showDisplayConfigDialog() {
        BaseDialog dialog = new BaseDialog(Core.bundle.get("bhk.display.title"));
        dialog.addCloseButton();
        dialog.cont.defaults().growX().pad(6f);

        TextField[] columnsField = new TextField[1];
        TextField[] colorField = new TextField[1];
        TextField[] badgeFontScaleField = new TextField[1];

        dialog.cont.table(cols -> {
            cols.left();
            cols.add(Core.bundle.get("bhk.display.columns")).padRight(6f);
            columnsField[0] = cols.field(String.valueOf(menuColumns), text -> {
            }).width(180f).get();
            columnsField[0].setMessageText("0");
            cols.row();
            cols.add(Core.bundle.get("bhk.display.columns.hint")).color(Color.lightGray).colspan(2).left().padTop(4f);
        }).row();

        dialog.cont.table(color -> {
            color.left();
            color.add(Core.bundle.get("bhk.display.color")).padRight(6f);
            colorField[0] = color.field(Core.settings.getString(keyIconHotkeyColor, "ff4d4d"), text -> {
            }).width(180f).get();
            colorField[0].setMessageText("ff4d4d");

            Label preview = color.add("[1,1]", Styles.outlineLabel).padLeft(12f).get();
            preview.setColor(iconHotkeyColor);
            colorField[0].changed(() -> preview.setColor(parseStoredColor(colorField[0].getText(), defaultIconHotkeyColor)));

            color.row();
            color.add(Core.bundle.get("bhk.display.color.hint")).color(Color.lightGray).colspan(3).left().padTop(4f);
        }).row();

        dialog.cont.table(font -> {
            font.left();
            font.add(Core.bundle.get("bhk.display.badge-font-scale")).padRight(6f);
            badgeFontScaleField[0] = font.field(formatBadgeFontScale(badgeFontScale), text -> {
            }).width(180f).get();
            badgeFontScaleField[0].setMessageText(Strings.fixed(defaultBadgeFontScale, 2));

            Label preview = font.add(Core.bundle.get("bhk.display.badge-font-preview"), Styles.outlineLabel).padLeft(12f).left().get();
            preview.setColor(iconHotkeyColor);
            applyLabelFontScale(preview, badgeFontScale);
            badgeFontScaleField[0].changed(() -> applyLabelFontScale(preview, parseStoredBadgeFontScale(badgeFontScaleField[0].getText())));

            font.row();
            font.add(Core.bundle.get("bhk.display.badge-font-scale.hint")).color(Color.lightGray).colspan(3).left().padTop(4f);
        }).row();

        dialog.buttons.defaults().size(220f, 54f).pad(6f);
        dialog.buttons.button("@ok", Icon.ok, () -> {
            int parsedColumns = Strings.parseInt(columnsField[0].getText(), 0);
            Core.settings.put(keyMenuColumns, Math.max(0, Math.min(parsedColumns, 12)));
            Core.settings.put(keyIconHotkeyColor, normalizeStoredColor(colorField[0].getText()));
            Core.settings.put(keyBadgeFontScale, formatBadgeFontScale(parseStoredBadgeFontScale(badgeFontScaleField[0].getText())));
            refreshSettings();
            updateBuildMenuHotkeyBadges();
            dialog.hide();
        });

        dialog.show();
    }

    private static String normalizeStoredColor(String raw) {
        String text = raw == null ? "" : raw.trim();
        if (text.startsWith("#")) text = text.substring(1);
        if (text.isEmpty()) return "ff4d4d";

        if (text.length() == 6 || text.length() == 8) {
            boolean ok = true;
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if ((c < '0' || c > '9') && (c < 'a' || c > 'f') && (c < 'A' || c > 'F')) {
                    ok = false;
                    break;
                }
            }
            if (ok) return text;
        }

        return "ff4d4d";
    }

    private static Color parseStoredColor(String raw, Color fallback) {
        try {
            return Color.valueOf(normalizeStoredColor(raw));
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static String formatBadgeFontScale(float scale) {
        return Strings.fixed(sanitizeBadgeFontScale(scale), 2);
    }

    private static float parseStoredBadgeFontScale(String raw) {
        if (raw == null) return defaultBadgeFontScale;

        String text = raw.trim();
        if (text.isEmpty()) return defaultBadgeFontScale;

        try {
            return sanitizeBadgeFontScale(Float.parseFloat(text));
        } catch (Throwable ignored) {
            return defaultBadgeFontScale;
        }
    }

    private static float sanitizeBadgeFontScale(float scale) {
        if (Float.isNaN(scale) || Float.isInfinite(scale)) return defaultBadgeFontScale;
        return Math.max(0f, scale);
    }

    private static float toRenderableFontScale(float scale) {
        float sanitized = sanitizeBadgeFontScale(scale);
        return sanitized <= 0f ? 0.0001f : sanitized;
    }

    private static void showSkipTerrainIgnoreDialog() {
        loadSkipTerrainIgnoreList();

        BaseDialog dialog = new BaseDialog(Core.bundle.get("bhk.ignore.title"));
        dialog.addCloseButton();

        Table list = new Table();
        Runnable[] rebuild = new Runnable[1];

        rebuild[0] = () -> {
            list.clear();
            list.top().left();

            if (skipTerrainIgnoreNames.isEmpty()) {
                list.add(Core.bundle.get("bhk.ignore.empty")).color(Color.lightGray).left().pad(8f).row();
                return;
            }

            for (String name : skipTerrainIgnoreNames) {
                Block block = findBlockByName(name);
                String title = block == null ? name : (block.localizedName + " [lightgray](" + block.name + ")[]");
                TextureRegionDrawable icon = block == null ? null : new TextureRegionDrawable(block.uiIcon);

                list.table(Styles.grayPanel, row -> {
                    row.left().defaults().pad(6f);
                    if (icon != null) {
                        row.image(icon).size(32f).padRight(6f);
                    }
                    row.add(title).growX().wrap().left();
                    row.button(Icon.trash, Styles.cleari, () -> {
                        skipTerrainIgnoreSet.remove(name);
                        skipTerrainIgnoreNames.remove(name);
                        saveSkipTerrainIgnoreList();
                        rebuild[0].run();
                    }).size(42f).tooltip(Core.bundle.get("bhk.remove"));
                }).growX().padBottom(6f).row();
            }
        };

        dialog.cont.table(top -> {
            top.left().defaults().pad(4f);
            top.add(Core.bundle.get("bhk.ignore.addbyname")).padRight(6f);

            TextField nameField = top.field("", t -> {
            }).growX().get();
            nameField.setMessageText(Core.bundle.get("bhk.ignore.namehint"));

            top.button(Icon.add, Styles.cleari, () -> {
                String raw = nameField.getText();
                if (raw == null) raw = "";
                raw = raw.trim();
                if (raw.isEmpty()) return;

                String[] tokens = raw.split("[\\s,;]+", -1);
                StringBuilder missing = new StringBuilder();
                for (String token : tokens) {
                    if (token == null) continue;
                    String t = token.trim();
                    if (t.isEmpty()) continue;

                    Block block = findBlockByName(t);
                    if (block == null) {
                        if (missing.length() > 0) missing.append(", ");
                        missing.append(t);
                        continue;
                    }
                    addSkipTerrainIgnore(block);
                }

                if (missing.length() > 0) {
                    ui.showInfoToast(Core.bundle.format("bhk.ignore.notfound", missing.toString()), 3f);
                }
                nameField.setText("");
                rebuild[0].run();
            }).size(42f);
        }).growX().row();

        dialog.cont.pane(Styles.noBarPane, list).grow().with(p -> p.setForceScroll(true, true)).row();

        dialog.buttons.defaults().size(220f, 54f).pad(6f);
        dialog.buttons.button(Core.bundle.get("bhk.ignore.pick"), Icon.add, () -> {
            showSkipTerrainIgnorePickDialog(block -> {
                addSkipTerrainIgnore(block);
                rebuild[0].run();
            });
        });

        dialog.buttons.button(Core.bundle.get("bhk.ignore.clear"), Icon.trash, () -> {
            skipTerrainIgnoreNames.clear();
            skipTerrainIgnoreSet.clear();
            saveSkipTerrainIgnoreList();
            rebuild[0].run();
        });

        rebuild[0].run();
        dialog.show();
    }

    private static void addSkipTerrainIgnore(Block block) {
        if (block == null || block.name == null || block.name.isEmpty()) return;
        if (skipTerrainIgnoreSet.add(block.name)) {
            skipTerrainIgnoreNames.add(block.name);
            saveSkipTerrainIgnoreList();
        }
    }

    private static void showSkipTerrainIgnorePickDialog(Cons<Block> onPick) {
        BaseDialog dialog = new BaseDialog(Core.bundle.get("bhk.ignore.pick"));
        dialog.addCloseButton();

        Seq<Block> all = new Seq<>();
        for (Block block : content.blocks()) {
            if (block == null || block.name == null) continue;
            if (!block.environmentBuildable()) continue;
            if (!block.isVisible()) continue;
            all.add(block);
        }

        all.sort((a, b) -> {
            String an = a.localizedName == null ? a.name : Strings.stripColors(a.localizedName);
            String bn = b.localizedName == null ? b.name : Strings.stripColors(b.localizedName);
            if (an == null) an = "";
            if (bn == null) bn = "";
            return an.compareToIgnoreCase(bn);
        });

        Seq<Block> filtered = new Seq<>();
        filtered.addAll(all);

        Table list = new Table();
        Runnable rebuild = () -> {
            list.clear();
            list.top().left();
            for (Block block : filtered) {
                list.button(block.localizedName + " [lightgray](" + block.name + ")[]", new TextureRegionDrawable(block.uiIcon), Styles.flatBordert, () -> {
                    if (onPick != null) onPick.get(block);
                    dialog.hide();
                }).growX().height(52f).left().padBottom(4f).row();
            }
        };

        dialog.cont.table(top -> {
            top.left();
            top.add(Core.bundle.get("bhk.search")).padRight(6f);
            TextField search = top.field("", t -> {
            }).growX().get();
            search.setMessageText(Core.bundle.get("bhk.search.hint"));

            search.changed(() -> {
                String q = search.getText();
                if (q == null) q = "";
                q = q.trim().toLowerCase(Locale.ROOT);

                filtered.clear();
                for (Block block : all) {
                    if (q.isEmpty()) {
                        filtered.add(block);
                        continue;
                    }
                    String n1 = block.localizedName == null ? "" : Strings.stripColors(block.localizedName).toLowerCase(Locale.ROOT);
                    String n2 = block.name == null ? "" : block.name.toLowerCase(Locale.ROOT);
                    if (n1.contains(q) || n2.contains(q)) filtered.add(block);
                }
                rebuild.run();
            });
        }).growX().row();

        dialog.cont.pane(Styles.noBarPane, list).grow().with(p -> p.setForceScroll(true, true)).row();

        rebuild.run();
        dialog.show();
    }

    private static void showConfigDialog() {
        BaseDialog dialog = new BaseDialog(Core.bundle.get("bhk.config.title"));
        dialog.addCloseButton();

        Table list = new Table();
        Runnable[] rebuild = new Runnable[1];

        rebuild[0] = () -> {
            list.clear();
            list.top().left();

            for (int i = 0; i < groups.size; i++) {
                GroupConfig g = groups.get(i);

                list.table(Styles.grayPanel, row -> {
                    row.center().defaults().pad(6f);
                    
                    CheckBox enabledBox = row.check("", g.enabled, v -> {
                        g.enabled = v;
                        saveGroups();
                    }).get();
                    enabledBox.setChecked(g.enabled);
                    
                    row.add(g.name).growX().wrap().center();

                    row.button(Icon.copy, Styles.cleari, () -> {
                        GroupConfig copy = new GroupConfig();
                        copy.name = g.name + " (Copy)";
                        copy.enabled = g.enabled;
                        for (BindingConfig b : g.bindings) {
                            BindingConfig nb = new BindingConfig();
                            nb.blockName = b.blockName;
                            nb.firstKey = b.firstKey;
                            nb.secondKey = b.secondKey;
                            nb.note = b.note;
                            copy.bindings.add(nb);
                        }
                        groups.add(copy);
                        saveGroups();
                        rebuild[0].run();
                    }).size(42f).tooltip(Core.bundle.get("bhk.duplicate"));

                    row.button(Icon.edit, Styles.cleari, () -> showGroupDialog(g, rebuild[0])).size(42f).tooltip(Core.bundle.get("bhk.edit"));
                    
                    row.button(Icon.trash, Styles.cleari, () -> {
                        groups.remove(g, true);
                        if (groups.isEmpty()) groups.add(defaultGroup());
                        saveGroups();
                        rebuild[0].run();
                    }).size(42f).tooltip(Core.bundle.get("bhk.remove"));
                }).growX().padBottom(6f).row();
            }

            list.row();
            list.table(buttons -> {
                buttons.defaults().size(160f, 54f).pad(4f);
                buttons.button(Core.bundle.get("bhk.group.add"), Icon.add, () -> {
                    GroupConfig g = new GroupConfig();
                    g.name = Core.bundle.format("bhk.group.name", groups.size + 1);
                    g.enabled = true;
                    groups.add(g);
                    saveGroups();
                    rebuild[0].run();
                });
                
                buttons.button(Core.bundle.get("bhk.export"), Icon.download, () -> exportConfig());
                buttons.button(Core.bundle.get("bhk.import"), Icon.upload, () -> importConfig(rebuild[0]));
            }).growX().padTop(8f);
        };

        rebuild[0].run();

        dialog.cont.pane(Styles.noBarPane, list).grow().with(p -> p.setForceScroll(true, true));
        dialog.show();
    }

    private static void showGroupDialog(GroupConfig group, Runnable onBackRefresh) {
        BaseDialog dialog = new BaseDialog(Core.bundle.get("bhk.group.title"));
        dialog.addCloseButton();

        dialog.cont.table(top -> {
            top.left();
            top.add(Core.bundle.get("bhk.group.label")).padRight(6f);
            TextField nameField = top.field(group.name, v -> {
                group.name = Strings.stripColors(v.trim());
                if (group.name.isEmpty()) group.name = Core.bundle.get("bhk.unnamed");
                saveGroups();
                onBackRefresh.run();
            }).growX().get();
            nameField.setMessageText(Core.bundle.get("bhk.group.hint"));
        }).growX().row();

        Table bindingsTable = new Table();
        Runnable[] rebuildBindings = new Runnable[1];

        rebuildBindings[0] = () -> {
            bindingsTable.clear();
            bindingsTable.top().left();

            for (BindingConfig binding : group.bindings) {
                bindingsTable.table(Styles.grayPanel, row -> {
                    row.left().defaults().pad(4f);

                    row.button(bindingDisplay(binding), Icon.edit, Styles.flatBordert, () -> {
                        showBlockSelectDialog(binding, rebuildBindings[0]);
                    }).left().width(220f).height(52f);

                    Table keyTable = new Table();
                    
                    TextButton firstBtn = keyTable.button("[accent]" + displayKeyName(binding.firstKey), Styles.defaultt, () -> {
                        captureKey(binding, true, rebuildBindings[0]);
                    }).width(160f).height(44f).get();
                    firstBtn.getLabel().setWrap(false);
                    firstBtn.getLabel().setAlignment(Align.center);
                    firstBtn.getLabelCell().growX().align(Align.center);
                    
                    keyTable.add("+").pad(4f);
                    
                    TextButton secondBtn = keyTable.button("[accent]" + displayKeyName(binding.secondKey), Styles.defaultt, () -> {
                        captureKey(binding, false, rebuildBindings[0]);
                    }).width(160f).height(44f).get();
                    secondBtn.getLabel().setWrap(false);
                    secondBtn.getLabel().setAlignment(Align.center);
                    secondBtn.getLabelCell().growX().align(Align.center);
                    
                    row.add(keyTable).padLeft(8f).center();

                    row.button(Icon.trash, Styles.cleari, () -> {
                        group.bindings.remove(binding, true);
                        saveGroups();
                        rebuildBindings[0].run();
                    }).size(42f).tooltip(Core.bundle.get("bhk.remove")).padLeft(8f);
                }).growX().padBottom(6f).row();
            }

            if (group.bindings.isEmpty()) {
                bindingsTable.add(Core.bundle.get("bhk.empty")).color(Color.lightGray).left().pad(8f).row();
            }
        };

        dialog.cont.row();
        dialog.cont.pane(Styles.noBarPane, bindingsTable).grow().with(p -> p.setForceScroll(true, true)).row();

        dialog.buttons.defaults().size(220f, 54f).pad(6f);
        dialog.buttons.button(Core.bundle.get("bhk.binding.add"), Icon.add, () -> {
            BindingConfig b = new BindingConfig();
            b.blockName = "";
            b.firstKey = "a";
            b.secondKey = "a";
            b.note = "";
            group.bindings.add(b);
            saveGroups();
            rebuildBindings[0].run();
        });

        rebuildBindings[0].run();

        dialog.hidden(onBackRefresh);
        dialog.show();
    }

    private static void captureKey(BindingConfig binding, boolean isFirst, Runnable onCapture) {
        BaseDialog dialog = new BaseDialog(Core.bundle.get("bhk.key.capture"));
        dialog.setFillParent(true);
        dialog.cont.add(Core.bundle.get("bhk.key.press")).pad(20f).center().row();
        
        Table keyDisplay = new Table();
        keyDisplay.background(Styles.black6);
        Label shown = keyDisplay.add("?").fontScale(2f).center().get();
        dialog.cont.add(keyDisplay).size(140f, 100f).row();
        
        dialog.addCloseButton();
        
        dialog.update(() -> {
            for (KeyCode code : KeyCode.all) {
                if (!isCaptureKeyCandidate(code)) continue;
                if (Core.input.keyTap(code)) {
                    shown.setText(code.toString());

                    String keyName = normalizeStoredKeyName(code);
                    if (isFirst) {
                        binding.firstKey = keyName;
                    } else {
                        binding.secondKey = keyName;
                    }
                    saveGroups();
                    onCapture.run();
                    dialog.hide();
                    return;
                }
            }
        });

        dialog.show();
    }

    private static boolean isCaptureKeyCandidate(KeyCode code) {
        if (code == null || code == KeyCode.unset) return false;
        String n = code.name();
        if (n == null) return false;
        if (n.equalsIgnoreCase("anyKey") || n.equalsIgnoreCase("anykey")) return false;
        if (n.equalsIgnoreCase("unknown")) return false;
        return true;
    }

    private static String normalizeStoredKeyName(KeyCode code) {
        return code.name().toLowerCase(Locale.ROOT);
    }

    private static String displayKeyName(String stored) {
        if (stored == null || stored.isEmpty()) return "?";
        String s = stored.trim().toLowerCase(Locale.ROOT);

        if (s.startsWith("num") && s.length() == 4 && Character.isDigit(s.charAt(3))) {
            return String.valueOf(s.charAt(3));
        }
        if (s.equals("num0")) return "0";
        if (s.equals("backtick") || s.equals("grave")) return "`";
        if (s.equals("minus")) return "-";
        if (s.equals("equals")) return "=";
        if (s.equals("comma")) return ",";
        if (s.equals("period")) return ".";
        if (s.equals("space")) return "SPACE";

        return s.toUpperCase(Locale.ROOT);
    }

    private static String bindingDisplay(BindingConfig binding) {
        Block block = findBlockByName(binding.blockName);
        if (block == null) {
            return Core.bundle.get("bhk.block.unset");
        }
        return block.localizedName + " [lightgray](" + block.name + ")[]";
    }

    private static void showBlockSelectDialog(BindingConfig binding, Runnable onSaved) {
        BaseDialog dialog = new BaseDialog(Core.bundle.get("bhk.block.pick"));
        dialog.addCloseButton();

        Seq<Block> all = new Seq<>();
        for (Block block : content.blocks()) {
            if (block == null) continue;
            if (block.isHidden()) continue;
            if (!block.environmentBuildable()) continue;
            all.add(block);
        }

        Seq<Block> filtered = new Seq<>();
        filtered.addAll(all);

        Table list = new Table();

        Runnable rebuild = () -> {
            list.clear();
            list.top().left();

            for (Block block : filtered) {
                list.button(block.localizedName + " [lightgray](" + block.name + ")[]", new TextureRegionDrawable(block.uiIcon), Styles.flatBordert, () -> {
                    binding.blockName = block.name;
                    saveGroups();
                    onSaved.run();
                    dialog.hide();
                }).growX().height(52f).left().padBottom(4f).row();
            }
        };

        dialog.cont.table(top -> {
            top.left();
            top.add(Core.bundle.get("bhk.search")).padRight(6f);
            TextField search = top.field("", t -> {
            }).growX().get();
            search.setMessageText(Core.bundle.get("bhk.search.hint"));

            search.changed(() -> {
                String q = search.getText();
                if (q == null) q = "";
                q = q.trim().toLowerCase(Locale.ROOT);

                filtered.clear();
                for (Block block : all) {
                    if (q.isEmpty()) {
                        filtered.add(block);
                        continue;
                    }
                    String n1 = block.localizedName == null ? "" : block.localizedName.toLowerCase(Locale.ROOT);
                    String n2 = block.name == null ? "" : block.name.toLowerCase(Locale.ROOT);
                    if (n1.contains(q) || n2.contains(q)) filtered.add(block);
                }

                rebuild.run();
            });
        }).growX().row();

        dialog.cont.pane(Styles.noBarPane, list).grow().with(p -> p.setForceScroll(true, true)).row();

        rebuild.run();
        dialog.show();
    }

    private static void exportConfig() {
        StringBuilder sb = new StringBuilder();
        for (GroupConfig g : groups) {
            sb.append("G|").append(escape(g.name)).append("|").append(g.enabled ? "1" : "0").append("\n");
            for (BindingConfig b : g.bindings) {
                sb.append("B|").append(escape(b.blockName)).append("|")
                  .append(escape(b.firstKey)).append("|")
                  .append(escape(b.secondKey)).append("|")
                  .append(escape(b.note)).append("\n");
            }
        }
        Core.app.setClipboardText(sb.toString());
        ui.showInfo(Core.bundle.get("bhk.exported"));
    }

    private static void importConfig(Runnable onRefresh) {
        String text = Core.app.getClipboardText();
        if (text == null || text.isEmpty()) {
            ui.showInfo(Core.bundle.get("bhk.noclipboard"));
            return;
        }
        
        try {
            String[] lines = text.split("\n");
            GroupConfig current = null;
            
            for (String line : lines) {
                if (line == null || line.isEmpty()) continue;
                String[] parts = line.split("\\|", -1);
                if (parts.length < 2) continue;
                
                if ("G".equals(parts[0])) {
                    if (parts.length >= 3) {
                        current = new GroupConfig();
                        current.name = unescape(parts[1]);
                        current.enabled = "1".equals(parts[2]);
                        groups.add(current);
                    }
                } else if ("B".equals(parts[0]) && current != null && parts.length >= 5) {
                    BindingConfig b = new BindingConfig();
                    b.blockName = unescape(parts[1]);
                    b.firstKey = unescape(parts[2]);
                    b.secondKey = unescape(parts[3]);
                    b.note = unescape(parts[4]);
                    current.bindings.add(b);
                }
            }
            
            if (!groups.isEmpty()) {
                saveGroups();
                onRefresh.run();
                ui.showInfo(Core.bundle.get("bhk.imported"));
            }
        } catch (Exception e) {
            ui.showInfo(Core.bundle.get("bhk.importfail"));
        }
    }

    private static class CenteredCheckSetting extends SettingsMenuDialog.SettingsTable.Setting {
        private final boolean def;
        private final arc.func.Boolc changed;

        CenteredCheckSetting(String name, boolean def, arc.func.Boolc changed) {
            super(name);
            this.def = def;
            this.changed = changed;
            Core.settings.defaults(name, def);
        }

        @Override
        public void add(SettingsMenuDialog.SettingsTable table) {
            CheckBox box = new CheckBox(title);
            box.getLabel().setAlignment(Align.center);
            box.getLabelCell().growX().align(Align.center);
            box.update(() -> box.setChecked(Core.settings.getBool(name, def)));
            box.changed(() -> {
                Core.settings.put(name, box.isChecked());
                if (changed != null) changed.get(box.isChecked());
            });
            box.center();
            table.add(box).growX().center().padTop(6f).padBottom(6f).row();
        }
    }

    private static String escape(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("|", "\\p").replace("\n", "\\n").replace("\r", "");
    }

    private static String unescape(String value) {
        if (value == null || value.isEmpty()) return "";
        String out = value.replace("\\n", "\n").replace("\\p", "|").replace("\\\\", "\\");
        return out;
    }

    private static class GroupConfig {
        String name = "";
        boolean enabled = true;
        Seq<BindingConfig> bindings = new Seq<>();
    }

    private static class BindingConfig {
        String blockName = "";
        String firstKey = "";
        String secondKey = "";
        String note = "";
    }

    private static class CompiledBinding {
        String groupName;
        Block block;
        KeyCode first;
        KeyCode second;
    }
}
