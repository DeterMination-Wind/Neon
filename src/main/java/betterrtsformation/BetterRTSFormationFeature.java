package betterrtsformation;

import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Font;
import arc.graphics.g2d.GlyphLayout;
import arc.input.KeyBind;
import arc.input.KeyCode;
import arc.input.InputProcessor;
import arc.math.geom.Rect;
import arc.scene.ui.layout.Scl;
import arc.struct.IntSet;
import arc.struct.IntSeq;
import arc.struct.Seq;
import arc.util.Align;
import arc.util.pooling.Pools;
import mindustry.game.EventType;
import mindustry.gen.Building;
import mindustry.gen.Groups;
import mindustry.gen.Icon;
import mindustry.gen.Unit;
import mindustry.graphics.Layer;
import mindustry.input.Binding;
import mindustry.input.InputHandler;
import mindustry.ui.Fonts;
import mindustry.ui.dialogs.SettingsMenuDialog;

import static mindustry.Vars.control;
import static mindustry.Vars.player;
import static mindustry.Vars.state;
import static mindustry.Vars.ui;

public final class BetterRTSFormationFeature {
    private static final String keyEnabled = "brf-enabled";
    private static final String keyOutsideCommandMode = "brf-outside-command-mode";
    private static final String keyAddControlGroup = "brf-add-control-group";
    private static final String keySelectControlGroup = "brf-select-control-group";
    private static final String keybindCategory = "better-rts-formation";

    private static final int groupCount = 10;
    private static final KeyBind[] groupBindings = {
        Binding.blockSelect01,
        Binding.blockSelect02,
        Binding.blockSelect03,
        Binding.blockSelect04,
        Binding.blockSelect05,
        Binding.blockSelect06,
        Binding.blockSelect07,
        Binding.blockSelect08,
        Binding.blockSelect09,
        Binding.blockSelect10
    };

    private static final Color[] groupColors = {
        Color.valueOf("d64b5b"),
        Color.valueOf("d46a2e"),
        Color.valueOf("a967c7"),
        Color.valueOf("3c78ce"),
        Color.valueOf("228d81"),
        Color.valueOf("4f9848"),
        Color.valueOf("8d704d"),
        Color.valueOf("2587b7"),
        Color.valueOf("b34b8f"),
        Color.valueOf("5c69a8")
    };

    private static final IntSet[] groupMembership = new IntSet[groupCount];
    private static final Seq<Unit> previousSelectedUnits = new Seq<>();
    private static final Seq<Building> previousCommandBuildings = new Seq<>(false);
    private static final Rect drawBounds = new Rect();
    private static final Rect formationBoxRect = new Rect();

    private static KeyBind addControlGroup;
    private static KeyBind selectControlGroup;
    private static InputProcessor boxInputProcessor;
    private static boolean initialized;
    private static boolean keybindRegistered;
    private static boolean settingsAdded;
    private static BetterRTSFormationMod.BundledSettingsRenderer bundledSettingsRenderer =
        BetterRTSFormationFeature::buildDefaultSettings;
    private static boolean enabled = true;
    private static boolean outsideCommandMode;
    private static boolean previousSelectionReady;
    private static boolean restoreSelectionBeforeDraw;
    private static boolean formationBoxActive;
    private static boolean formationBoxMouseCaptured;
    private static boolean formationBoxMouseReleased;
    private static float formationBoxStartX;
    private static float formationBoxStartY;
    private static int formationBoxGroup = -1;
    private static IntSeq[] nativeCreateGroupSnapshot;
    private static int nativeCreateGroupIndex = -1;

    static {
        for (int i = 0; i < groupMembership.length; i++) {
            groupMembership[i] = new IntSet();
        }
    }

    private BetterRTSFormationFeature() {
    }

    public static void init() {
        if (initialized) return;
        initialized = true;

        Events.on(EventType.WorldLoadEvent.class, event -> resetPreviousSelection());
        Events.run(EventType.Trigger.update, BetterRTSFormationFeature::updateInput);
        Events.run(EventType.Trigger.draw, () -> {
            Draw.draw(Layer.overlayUI - 0.1f, BetterRTSFormationFeature::drawFormationBox);
            Draw.draw(Layer.playerName + 1f, BetterRTSFormationFeature::drawBadges);
        });
    }

    public static void loadClient(EventType.ClientLoadEvent event) {
        Core.settings.defaults(keyEnabled, true);
        registerKeybind();
        registerBoxInputProcessor();
        refreshSettings();

        if (!BetterRTSFormationMod.bekBundled && !settingsAdded && ui != null) {
            settingsAdded = true;
            ui.settings.addCategory("@settings.brf", Icon.map, BetterRTSFormationFeature::buildSettings);
        }
    }

    public static void buildSettings(SettingsMenuDialog.SettingsTable table) {
        buildDefaultSettings(table, keyEnabled, keyOutsideCommandMode);
        refreshSettings();
    }

    public static void configureBundledSettingsRenderer(BetterRTSFormationMod.BundledSettingsRenderer renderer) {
        bundledSettingsRenderer = renderer == null
            ? BetterRTSFormationFeature::buildDefaultSettings
            : renderer;
    }

    public static void buildBundledSettings(SettingsMenuDialog.SettingsTable table) {
        bundledSettingsRenderer.build(table, keyEnabled, keyOutsideCommandMode);
        refreshSettings();
    }

    private static void buildDefaultSettings(SettingsMenuDialog.SettingsTable table, String enabledKey, String outsideCommandModeKey) {
        table.checkPref(enabledKey, true);
        table.checkPref(outsideCommandModeKey, false);
    }

    private static void registerKeybind() {
        if (keybindRegistered) return;
        keybindRegistered = true;
        addControlGroup = KeyBind.add(keyAddControlGroup, KeyCode.unset, keybindCategory);
        selectControlGroup = KeyBind.add(keySelectControlGroup, KeyCode.unset, keybindCategory);
    }

    private static void registerBoxInputProcessor() {
        if (boxInputProcessor != null || Core.input == null) return;

        boxInputProcessor = new InputProcessor() {
            @Override
            public boolean touchDown(int screenX, int screenY, int pointer, KeyCode button) {
                if (button != KeyCode.mouseLeft || pointer != 0) return false;
                if (Core.scene != null && Core.scene.hasMouse()) return false;
                if (!formationBoxActive) {
                    InputHandler input = control == null ? null : control.input;
                    if (!canStartFormationBox(input)) return false;
                    beginFormationBox(input, screenX, screenY);
                }
                if (!formationBoxActive) return false;

                formationBoxMouseCaptured = true;
                formationBoxMouseReleased = false;
                setFormationBoxStart(screenX, screenY);
                return true;
            }

            @Override
            public boolean touchUp(int screenX, int screenY, int pointer, KeyCode button) {
                if (button != KeyCode.mouseLeft || pointer != 0 || !formationBoxMouseCaptured) return false;
                formationBoxMouseCaptured = false;
                formationBoxMouseReleased = true;
                return true;
            }

            @Override
            public boolean touchDragged(int screenX, int screenY, int pointer) {
                return pointer == 0 && formationBoxMouseCaptured;
            }
        };

        Core.input.getInputProcessors().insert(0, boxInputProcessor);
    }

    private static void refreshSettings() {
        if (Core.settings != null) {
            enabled = Core.settings.getBool(keyEnabled, true);
            outsideCommandMode = Core.settings.getBool(keyOutsideCommandMode, false);
        }
    }

    private static void updateInput() {
        refreshSettings();
        registerBoxInputProcessor();

        InputHandler input = control == null ? null : control.input;
        if (input != null) {
            restoreNativeCreateGroups(input);
        }

        if (control == null || input == null || Core.input == null || state == null || !state.isGame()) {
            previousSelectionReady = false;
            restoreSelectionBeforeDraw = false;
            cancelFormationBox();
            return;
        }

        if (!enabled) {
            restoreSelectionBeforeDraw = false;
            cancelFormationBox();
            rememberSelection(input);
            return;
        }

        if (Core.scene == null || Core.scene.hasField() || Core.scene.hasDialog()) {
            cancelFormationBox();
            rememberSelection(input);
            return;
        }

        boolean commandMode = input.commandMode || isKeyDown(Binding.commandMode);
        boolean outsideMode = !commandMode && outsideCommandMode;
        boolean addPrefix = isKeyDown(addControlGroup);

        if (outsideMode) {
            updateFormationBox(input, addPrefix);
        } else {
            cancelFormationBox();
        }

        if (commandMode || outsideMode || addPrefix) {
            handleFormationShortcuts(input, commandMode, outsideMode, addPrefix);
        }

        rememberSelection(input);
    }

    private static void handleFormationShortcuts(InputHandler input, boolean commandMode, boolean outsideMode, boolean addPrefix) {
        int groupIndex = tappedGroupIndex();
        if (groupIndex < 0) return;

        boolean nativeCreatePrefix = Core.input.keyDown(Binding.createControlGroup);
        boolean customCreate = addPrefix;

        if (commandMode && nativeCreatePrefix) {
            snapshotNativeCreateGroups(input, groupIndex);
        }

        // The native Ctrl+Shift path remains authoritative in command mode. The
        // added prefix is independent and creates groups without requiring Shift.
        if (customCreate || (outsideMode && nativeCreatePrefix)) {
            cancelFormationBox();
            if (previousSelectionReady) {
                restorePreviousSelection(input);
            }
            createGroup(input, groupIndex);
            return;
        }

        if (commandMode) {
            if (nativeCreatePrefix) return;
            if (!hasSelectKey()) return;

            // Restore the previous frame before the native input handler processes
            // the same digit, then apply the strict modifier when present.
            if (previousSelectionReady) {
                restorePreviousSelection(input);
            }
            if (isKeyDown(selectControlGroup)) {
                selectGroup(input, groupIndex);
            } else {
                restoreSelectionBeforeDraw = true;
            }
        } else if (outsideMode && (!hasSelectKey() || isKeyDown(selectControlGroup))) {
            selectGroup(input, groupIndex);
        }
    }

    private static int tappedGroupIndex() {
        for (int i = 0; i < groupBindings.length; i++) {
            if (Core.input.keyTap(groupBindings[i])) return i;
        }
        return -1;
    }

    private static void snapshotNativeCreateGroups(InputHandler input, int groupIndex) {
        if (nativeCreateGroupSnapshot != null) return;

        nativeCreateGroupIndex = groupIndex;
        nativeCreateGroupSnapshot = new IntSeq[input.controlGroups.length];
        for (int i = 0; i < input.controlGroups.length; i++) {
            IntSeq group = input.controlGroups[i];
            if (group != null) {
                nativeCreateGroupSnapshot[i] = new IntSeq(group);
            }
        }
    }

    private static void restoreNativeCreateGroups(InputHandler input) {
        if (nativeCreateGroupSnapshot == null) return;

        int count = Math.min(nativeCreateGroupSnapshot.length, input.controlGroups.length);
        for (int i = 0; i < count; i++) {
            if (i == nativeCreateGroupIndex) continue;

            IntSeq snapshot = nativeCreateGroupSnapshot[i];
            if (snapshot == null) {
                input.controlGroups[i] = null;
            } else if (input.controlGroups[i] == null) {
                input.controlGroups[i] = new IntSeq(snapshot);
            } else {
                input.controlGroups[i].clear();
                input.controlGroups[i].addAll(snapshot);
            }
        }

        nativeCreateGroupSnapshot = null;
        nativeCreateGroupIndex = -1;
    }

    private static void updateFormationBox(InputHandler input, boolean addPrefix) {
        if (!formationBoxActive) {
            if (addPrefix && Core.input.keyTap(addControlGroup) && !Core.scene.hasMouse()) {
                beginFormationBox(input);
            }
            return;
        }

        if (formationBoxMouseReleased || !addPrefix) {
            finishFormationBox(input);
        }
    }

    private static boolean canStartFormationBox(InputHandler input) {
        return enabled && outsideCommandMode && input != null && Core.input != null && state != null && state.isGame()
            && Core.scene != null && !Core.scene.hasField() && !Core.scene.hasDialog() && !Core.scene.hasMouse()
            && !input.commandMode && !isKeyDown(Binding.commandMode) && isKeyDown(addControlGroup);
    }

    private static void beginFormationBox(InputHandler input) {
        if (input == null || formationBoxActive) return;

        int emptyGroup = firstEmptyGroup(input);
        if (emptyGroup < 0) return;

        formationBoxGroup = emptyGroup;
        formationBoxStartX = Core.input.mouseWorldX();
        formationBoxStartY = Core.input.mouseWorldY();
        formationBoxMouseReleased = false;
        formationBoxActive = true;
    }

    private static void beginFormationBox(InputHandler input, int screenX, int screenY) {
        if (input == null || formationBoxActive) return;

        int emptyGroup = firstEmptyGroup(input);
        if (emptyGroup < 0) return;

        formationBoxGroup = emptyGroup;
        formationBoxMouseReleased = false;
        formationBoxActive = true;
        setFormationBoxStart(screenX, screenY);
    }

    private static void setFormationBoxStart(int screenX, int screenY) {
        formationBoxStartX = Core.input.mouseWorld(screenX, screenY).x;
        formationBoxStartY = Core.input.mouseWorld(screenX, screenY).y;
    }

    private static void finishFormationBox(InputHandler input) {
        if (!formationBoxActive) {
            formationBoxMouseReleased = false;
            return;
        }

        if (formationBoxGroup >= 0 && formationBoxGroup < input.controlGroups.length) {
            float endX = Core.input.mouseWorldX();
            float endY = Core.input.mouseWorldY();
            Seq<Unit> units = input.selectedCommandUnits(
                formationBoxStartX,
                formationBoxStartY,
                endX - formationBoxStartX,
                endY - formationBoxStartY
            );
            createGroup(input, formationBoxGroup, units);
        }

        formationBoxActive = false;
        formationBoxGroup = -1;
        formationBoxMouseReleased = false;
    }

    private static void cancelFormationBox() {
        formationBoxActive = false;
        formationBoxGroup = -1;
        formationBoxMouseReleased = false;
    }

    private static int firstEmptyGroup(InputHandler input) {
        int availableGroups = Math.min(groupCount, input.controlGroups.length);
        for (int i = 0; i < availableGroups; i++) {
            IntSeq group = input.controlGroups[i];
            if (group != null) {
                for (int j = 0; j < group.size; j++) {
                    if (!isValidGroupUnit(Groups.unit.getByID(group.get(j)))) {
                        group.removeIndex(j--);
                    }
                }
            }
            if (group == null || group.isEmpty()) return i;
        }
        return -1;
    }

    private static boolean isKeyDown(KeyBind keybind) {
        return keybind != null && keybind.value != null && keybind.value.key != null
            && keybind.value.key != KeyCode.unset && Core.input.keyDown(keybind);
    }

    private static boolean hasSelectKey() {
        return selectControlGroup != null && selectControlGroup.value != null
            && selectControlGroup.value.key != null && selectControlGroup.value.key != KeyCode.unset;
    }

    private static void createGroup(InputHandler input, int groupIndex) {
        createGroup(input, groupIndex, input.selectedUnits);
    }

    private static void createGroup(InputHandler input, int groupIndex, Seq<Unit> units) {
        if (groupIndex < 0 || groupIndex >= input.controlGroups.length) return;

        IntSeq group = input.controlGroups[groupIndex];
        if (group == null) {
            group = input.controlGroups[groupIndex] = new IntSeq();
        }

        group.clear();
        IntSeq selectedUnitIds = units.mapInt(unit -> unit.id);
        group.addAll(selectedUnitIds);
    }

    private static void selectGroup(InputHandler input, int groupIndex) {
        if (groupIndex < 0 || groupIndex >= input.controlGroups.length) return;

        IntSeq group = input.controlGroups[groupIndex];
        if (group == null) return;

        for (int i = 0; i < group.size; i++) {
            Unit unit = Groups.unit.getByID(group.get(i));
            if (!isValidGroupUnit(unit)) {
                group.removeIndex(i--);
            }
        }

        if (group.isEmpty()) return;

        input.selectedUnits.clear();
        input.commandBuildings.clear();
        for (int i = 0; i < group.size; i++) {
            Unit unit = Groups.unit.getByID(group.get(i));
            if (unit != null) {
                input.selectedUnits.add(unit);
            }
        }
    }

    private static void restorePreviousSelection(InputHandler input) {
        input.selectedUnits.clear();
        for (int i = 0; i < previousSelectedUnits.size; i++) {
            Unit unit = previousSelectedUnits.get(i);
            if (isValidSelectedUnit(unit)) {
                input.selectedUnits.add(unit);
            }
        }

        input.commandBuildings.clear();
        for (int i = 0; i < previousCommandBuildings.size; i++) {
            Building building = previousCommandBuildings.get(i);
            if (building != null && building.isValid() && building.isCommandable() && player != null && building.team == player.team()) {
                input.commandBuildings.add(building);
            }
        }
    }

    private static void rememberSelection(InputHandler input) {
        previousSelectedUnits.clear();
        previousSelectedUnits.addAll(input.selectedUnits);
        previousCommandBuildings.clear();
        previousCommandBuildings.addAll(input.commandBuildings);
        previousSelectionReady = true;
    }

    private static void resetPreviousSelection() {
        previousSelectedUnits.clear();
        previousCommandBuildings.clear();
        previousSelectionReady = true;
        restoreSelectionBeforeDraw = false;
        cancelFormationBox();
        nativeCreateGroupSnapshot = null;
        nativeCreateGroupIndex = -1;
    }

    private static boolean isValidSelectedUnit(Unit unit) {
        return unit != null && unit.allowCommand() && unit.isValid() && player != null && unit.team == player.team();
    }

    private static boolean isValidGroupUnit(Unit unit) {
        return unit != null && unit.isCommandable() && unit.isValid() && player != null && unit.team == player.team();
    }

    private static void drawFormationBox() {
        if (!formationBoxActive || formationBoxGroup < 0 || formationBoxGroup >= groupColors.length
            || state == null || !state.isGame() || Core.input == null || Core.camera == null) return;

        float endX = Core.input.mouseWorldX();
        float endY = Core.input.mouseWorldY();
        formationBoxRect.set(formationBoxStartX, formationBoxStartY, endX - formationBoxStartX, endY - formationBoxStartY).normalize();

        Draw.color(groupColors[formationBoxGroup], 0.3f);
        Fill.crect(formationBoxRect.x, formationBoxRect.y, formationBoxRect.width, formationBoxRect.height);
        Draw.reset();
    }

    private static void drawBadges() {
        InputHandler input = control == null ? null : control.input;
        if (input != null) {
            restoreNativeCreateGroups(input);
        }
        if (restoreSelectionBeforeDraw && input != null) {
            restorePreviousSelection(input);
            restoreSelectionBeforeDraw = false;
        }

        if (input == null || state == null || !state.isGame()) {
            return;
        }

        if (!enabled || player == null || Core.camera == null) {
            rememberSelection(input);
            return;
        }

        IntSeq[] groups = input.controlGroups;
        if (groups == null || groups.length == 0 || Fonts.outline == null) {
            rememberSelection(input);
            return;
        }

        int availableGroups = Math.min(groupCount, groups.length);
        rebuildMembership(groups, availableGroups);
        Core.camera.bounds(drawBounds);

        Font font = Fonts.outline;
        boolean integerPositions = font.usesIntegerPositions();
        float oldScaleX = font.getScaleX();
        float oldScaleY = font.getScaleY();
        font.setUseIntegerPositions(false);
        font.getData().setScale(0.25f / Scl.scl(1f));

        for (int i = 0; i < Groups.unit.size(); i++) {
            Unit unit = Groups.unit.index(i);
            if (!isValidGroupUnit(unit)) continue;

            float unitSize = Math.max(1f, unit.type.hitSize);
            if (!drawBounds.overlaps(unit.x - unitSize / 2f, unit.y - unitSize / 2f, unitSize, unitSize)) continue;

            float offset = 0f;
            for (int groupIndex = 0; groupIndex < availableGroups; groupIndex++) {
                if (groupMembership[groupIndex].contains(unit.id)) {
                    offset += drawBadge(font, unit, groupIndex, offset);
                }
            }
        }

        font.getData().setScale(oldScaleX, oldScaleY);
        font.setColor(Color.white);
        font.setUseIntegerPositions(integerPositions);
        Draw.reset();
        rememberSelection(input);
    }

    private static void rebuildMembership(IntSeq[] groups, int availableGroups) {
        for (int i = 0; i < groupCount; i++) {
            groupMembership[i].clear();
        }

        for (int groupIndex = 0; groupIndex < availableGroups; groupIndex++) {
            IntSeq group = groups[groupIndex];
            if (group == null) continue;

            for (int i = 0; i < group.size; i++) {
                groupMembership[groupIndex].add(group.get(i));
            }
        }
    }

    private static float drawBadge(Font font, Unit unit, int groupIndex, float offset) {
        String label = groupIndex == 9 ? "0" : String.valueOf(groupIndex + 1);
        GlyphLayout layout = Pools.obtain(GlyphLayout.class, GlyphLayout::new);
        layout.setText(font, label);

        float width = Math.max(7f, layout.width + 4f);
        float height = Math.max(7f, layout.height + 3f);
        float left = unit.x - unit.type.hitSize / 2f + offset;
        float centerX = left + width / 2f;
        float centerY = unit.y - unit.type.hitSize / 2f - height / 2f - 1f;

        Draw.color(Color.black);
        Fill.rect(centerX, centerY, width + 2f, height + 2f);
        Draw.color(groupColors[groupIndex]);
        Fill.rect(centerX, centerY, width, height);

        font.setColor(Color.white);
        font.draw(label, centerX, centerY + layout.height / 2f, 0, Align.center, false);
        Pools.free(layout);

        return width;
    }
}
