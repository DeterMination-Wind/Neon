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
import arc.math.Mathf;
import arc.math.geom.Rect;
import arc.scene.ui.layout.Scl;
import arc.struct.IntSet;
import arc.struct.IntSeq;
import arc.struct.Seq;
import arc.util.Align;
import arc.util.Time;
import arc.util.pooling.Pools;
import mindustry.game.EventType;
import mindustry.gen.Building;
import mindustry.gen.Groups;
import mindustry.gen.Icon;
import mindustry.gen.Payloadc;
import mindustry.gen.Unit;
import mindustry.graphics.Layer;
import mindustry.input.Binding;
import mindustry.input.InputHandler;
import mindustry.world.blocks.payloads.Payload;
import mindustry.world.blocks.payloads.UnitPayload;
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
    private static final String keyCreateControlGroup = "brf-create-control-group";
    private static final String keyExclusiveAddControlGroup = "brf-exclusive-add-control-group";
    private static final String keySelectControlGroup = "brf-select-control-group";
    private static final String keyDeleteControlGroup = "brf-delete-control-group";
    private static final String keybindCategory = "better-rts-formation";

    private static final int groupCount = 10;
    private static final int groupEditNone = 0;
    private static final int groupEditAdd = 1;
    private static final int groupEditAddExclusive = 2;
    private static final int groupEditRemoveAll = 3;
    private static final float transportGraceSeconds = 5f;
    private static final float badgeClusterPixels = 48f;
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
    private static final Seq<TransportedUnit> transportedUnits = new Seq<>();
    private static final Seq<BadgeCluster> badgeClusters = new Seq<>();
    private static final Seq<Unit> payloadUnits = new Seq<>();
    private static final Seq<Unit> clusterUnits = new Seq<>();
    private static final IntSet consumedShortcutKeys = new IntSet();
    private static final Rect drawBounds = new Rect();
    private static final Rect formationBoxRect = new Rect();

    private static KeyBind addControlGroup;
    private static KeyBind createControlGroup;
    private static KeyBind exclusiveAddControlGroup;
    private static KeyBind selectControlGroup;
    private static KeyBind deleteControlGroup;
    private static InputProcessor boxInputProcessor;
    private static boolean initialized;
    private static boolean keybindRegistered;
    private static boolean settingsAdded;
    private static BetterRTSFormationMod.BundledSettingsRenderer bundledSettingsRenderer =
        BetterRTSFormationFeature::buildDefaultSettings;
    private static boolean enabled = true;
    private static boolean outsideCommandMode;
    private static boolean formationBoxActive;
    private static boolean formationBoxDeleteMode;
    private static boolean formationBoxExclusiveMode;
    private static boolean formationBoxMouseCaptured;
    private static boolean formationBoxMouseReleased;
    private static float formationBoxStartX;
    private static float formationBoxStartY;
    private static int formationBoxGroup = -1;

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

        Events.on(EventType.WorldLoadEvent.class, event -> resetState());
        Events.on(EventType.PickupEvent.class, event -> rememberTransportedUnit(event.unit));
        Events.on(EventType.PayloadDropEvent.class, event -> restoreTransportedUnit(event.unit));
        Events.on(EventType.UnitUnloadEvent.class, event -> restoreTransportedUnit(event.unit));
        Events.on(EventType.UnitDestroyEvent.class, event -> forgetTransportedUnit(event.unit));
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
        addControlGroup = KeyBind.add(keyAddControlGroup, KeyCode.controlLeft, keybindCategory);
        createControlGroup = KeyBind.add(keyCreateControlGroup, KeyCode.shiftLeft, keybindCategory);
        exclusiveAddControlGroup = KeyBind.add(keyExclusiveAddControlGroup, KeyCode.unset, keybindCategory);
        selectControlGroup = KeyBind.add(keySelectControlGroup, KeyCode.altLeft, keybindCategory);
        deleteControlGroup = KeyBind.add(keyDeleteControlGroup, KeyCode.del, keybindCategory);
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
                    if (canStartDeleteFormationBox(input)) {
                        beginDeleteFormationBox(input, screenX, screenY);
                    } else if (canStartExclusiveFormationBox(input)) {
                        beginExclusiveFormationBox(input, screenX, screenY);
                    } else if (canStartFormationBox(input)) {
                        beginFormationBox(input, screenX, screenY);
                    }
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

            @Override
            public boolean keyDown(KeyCode keycode) {
                if (consumedShortcutKeys.contains(keycode.ordinal())) return true;
                if (!handleFormationShortcut(keycode)) return false;

                consumedShortcutKeys.add(keycode.ordinal());
                return true;
            }

            @Override
            public boolean keyUp(KeyCode keycode) {
                if (consumedShortcutKeys.contains(keycode.ordinal())) {
                    consumedShortcutKeys.remove(keycode.ordinal());
                    return true;
                }
                return false;
            }

            @Override
            public boolean keyTyped(char character) {
                return false;
            }

            @Override
            public boolean mouseMoved(int screenX, int screenY) {
                return false;
            }

            @Override
            public boolean scrolled(float amountX, float amountY) {
                return false;
            }
        };

        Core.input.getInputProcessors().insert(0, boxInputProcessor);
    }

    private static boolean handleFormationShortcut(KeyCode keycode) {
        InputHandler input = control == null ? null : control.input;
        if (!canHandleFormationShortcut(input)) return false;

        int groupIndex = shortcutGroupIndex(keycode);
        if (groupIndex < 0) return false;

        boolean commandMode = input.commandMode || isKeyDown(Binding.commandMode);
        boolean nativeCreateChord = commandMode && isKeyDown(Binding.createControlGroup);

        cancelFormationBox();
        if (isKeyDown(exclusiveAddControlGroup)) {
            applyGroupEdit(input, groupIndex, collectValidUnitIds(input.selectedUnits), groupEditAddExclusive);
            return true;
        }
        if (isKeyDown(createControlGroup)) {
            createGroup(input, groupIndex);
            return true;
        }
        if (nativeCreateChord) {
            return true;
        }
        if (isKeyDown(addControlGroup)) {
            applyGroupEdit(input, groupIndex, collectValidUnitIds(input.selectedUnits), groupEditAdd);
            return true;
        }
        if (isKeyDown(selectControlGroup)) {
            selectGroup(input, groupIndex);
            return true;
        }

        // When a select prefix is configured, bare native group selection is
        // blocked in command mode so selection remains prefix-only.
        return commandMode && hasKey(selectControlGroup);
    }

    private static boolean canHandleFormationShortcut(InputHandler input) {
        return enabled && input != null && Core.input != null && state != null && state.isGame()
            && Core.scene != null && !Core.scene.hasField() && !Core.scene.hasDialog() && !Core.scene.hasKeyboard();
    }

    private static int shortcutGroupIndex(KeyCode keycode) {
        int groupIndex = defaultGroupIndex(keycode);
        if (groupIndex >= 0) return groupIndex;

        for (int i = 0; i < groupBindings.length; i++) {
            if (isBoundTo(groupBindings[i], keycode)) return i;
        }
        return -1;
    }

    private static int defaultGroupIndex(KeyCode keycode) {
        switch (keycode) {
            case num1:
            case numpad1:
                return 0;
            case num2:
            case numpad2:
                return 1;
            case num3:
            case numpad3:
                return 2;
            case num4:
            case numpad4:
                return 3;
            case num5:
            case numpad5:
                return 4;
            case num6:
            case numpad6:
                return 5;
            case num7:
            case numpad7:
                return 6;
            case num8:
            case numpad8:
                return 7;
            case num9:
            case numpad9:
                return 8;
            case num0:
            case numpad0:
                return 9;
            default:
                return -1;
        }
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
        reconcileTransportedUnits(input);
        if (control == null || input == null || Core.input == null || state == null || !state.isGame()) {
            cancelFormationBox();
            return;
        }

        if (!enabled) {
            cancelFormationBox();
            return;
        }

        if (Core.scene == null || Core.scene.hasField() || Core.scene.hasDialog() || Core.scene.hasKeyboard()) {
            cancelFormationBox();
            return;
        }

        boolean commandMode = input.commandMode || isKeyDown(Binding.commandMode);
        boolean outsideMode = !commandMode && outsideCommandMode;
        boolean addPrefix = isKeyDown(addControlGroup);
        boolean exclusivePrefix = isKeyDown(exclusiveAddControlGroup);

        if (!addPrefix && isKeyTap(deleteControlGroup)) {
            applyGroupEdit(input, -1, collectValidUnitIds(input.selectedUnits), groupEditRemoveAll);
        }

        if (outsideMode) {
            updateFormationBox(input, addPrefix, exclusivePrefix);
        } else {
            cancelFormationBox();
        }
    }

    private static void rememberTransportedUnit(Unit unit) {
        if (unit == null || player == null || unit.team != player.team() || unit.isCommandable() == false) return;

        int mask = groupMask(unit.id);
        if (mask == 0) return;

        TransportedUnit tracked = findTransportedUnit(unit);
        if (tracked == null) {
            tracked = new TransportedUnit(unit, unit.id, mask);
            tracked.wasPayload = true;
            tracked.payloadObservedLastFrame = true;
            tracked.payloadObservedThisFrame = true;
            transportedUnits.add(tracked);
        } else {
            tracked.id = unit.id;
            tracked.lastX = unit.x;
            tracked.lastY = unit.y;
            tracked.groupMask = mask;
            tracked.wasPayload = true;
            tracked.payloadObservedLastFrame = true;
            tracked.payloadObservedThisFrame = true;
            tracked.missingSeconds = 0f;
        }
    }

    private static void restoreTransportedUnit(Unit unit) {
        if (unit == null) return;

        TransportedUnit tracked = findTransportedUnit(unit);
        if (tracked == null) return;

        if (tracked.id != unit.id) {
            migrateGroupMemberId(tracked.id, unit.id, tracked.groupMask);
            tracked.id = unit.id;
        }
        tracked.unit = unit;
        tracked.lastX = unit.x;
        tracked.lastY = unit.y;
        tracked.missingSeconds = 0f;
        tracked.groupMask = groupMask(unit.id) | tracked.groupMask;
    }

    private static void forgetTransportedUnit(Unit unit) {
        if (unit == null) return;

        for (int i = transportedUnits.size - 1; i >= 0; i--) {
            TransportedUnit tracked = transportedUnits.get(i);
            if (tracked.unit == unit) transportedUnits.remove(i);
        }
    }

    private static void reconcileTransportedUnits(InputHandler input) {
        if (input == null || input.controlGroups == null || state == null || !state.isGame() || player == null) return;

        for (int i = 0; i < transportedUnits.size; i++) {
            TransportedUnit tracked = transportedUnits.get(i);
            tracked.payloadObservedLastFrame = tracked.payloadObservedThisFrame;
            tracked.payloadObservedThisFrame = false;
            tracked.claimedThisFrame = false;
            tracked.wasAddedLastFrame = tracked.unit != null && tracked.unit.isAdded();
        }

        payloadUnits.clear();
        for (int i = 0; i < Groups.unit.size(); i++) {
            Unit carrier = Groups.unit.index(i);
            if (carrier instanceof Payloadc) collectPayloadUnits((Payloadc)carrier, payloadUnits);
        }
        for (int i = 0; i < Groups.build.size(); i++) {
            Building building = Groups.build.index(i);
            if (building != null) collectPayloadUnits(building.getPayload(), payloadUnits);
        }

        for (int i = 0; i < payloadUnits.size; i++) {
            Unit unit = payloadUnits.get(i);
            if (!isTrackableUnit(unit)) continue;

            TransportedUnit tracked = findTransportedUnit(unit);
            int mask = groupMask(unit.id);
            if (tracked == null && mask == 0) tracked = claimReappearedUnit(unit, true);
            if (tracked == null) {
                if (mask != 0) {
                    tracked = new TransportedUnit(unit, unit.id, mask);
                    tracked.wasPayload = true;
                    tracked.payloadObservedThisFrame = true;
                    transportedUnits.add(tracked);
                }
                continue;
            }

            if (tracked.id != unit.id) {
                migrateGroupMemberId(tracked.id, unit.id, tracked.groupMask);
                tracked.id = unit.id;
                mask = groupMask(unit.id) | tracked.groupMask;
            }
            tracked.unit = unit;
            tracked.lastX = unit.x;
            tracked.lastY = unit.y;
            tracked.groupMask = mask;
            tracked.wasPayload = true;
            tracked.payloadObservedThisFrame = true;
            tracked.missingSeconds = 0f;
            if (tracked.groupMask == 0) transportedUnits.remove(tracked);
        }

        for (int i = 0; i < Groups.unit.size(); i++) {
            Unit unit = Groups.unit.index(i);
            if (!isTrackableUnit(unit)) continue;

            TransportedUnit tracked = findTransportedUnit(unit);
            int mask = groupMask(unit.id);
            if (tracked == null && mask == 0) tracked = claimReappearedUnit(unit);
            if (tracked == null) {
                if (mask != 0) transportedUnits.add(new TransportedUnit(unit, unit.id, mask));
                continue;
            }

            if (tracked.id != unit.id) {
                migrateGroupMemberId(tracked.id, unit.id, tracked.groupMask);
                tracked.id = unit.id;
                mask = groupMask(unit.id) | tracked.groupMask;
            }

            tracked.unit = unit;
            tracked.lastX = unit.x;
            tracked.lastY = unit.y;
            tracked.groupMask = mask;
            tracked.missingSeconds = 0f;
            if (tracked.groupMask == 0) transportedUnits.remove(tracked);
        }

        for (int i = transportedUnits.size - 1; i >= 0; i--) {
            TransportedUnit tracked = transportedUnits.get(i);
            if (tracked.unit == null || tracked.unit.dead() || tracked.groupMask == 0) {
                transportedUnits.remove(i);
                continue;
            }
            if (!tracked.unit.isAdded() && !tracked.payloadObservedThisFrame) {
                tracked.missingSeconds += Math.max(0f, Time.delta) / 60f;
                if (tracked.missingSeconds > transportGraceSeconds) transportedUnits.remove(i);
            }
        }
    }

    private static void collectPayloadUnits(Payloadc carrier, Seq<Unit> result) {
        Seq<Payload> payloads = carrier.payloads();
        if (payloads == null) return;

        for (int i = 0; i < payloads.size; i++) {
            collectPayloadUnits(payloads.get(i), result);
        }
    }

    private static void collectPayloadUnits(Payload payload, Seq<Unit> result) {
        if (payload instanceof UnitPayload) {
            Unit unit = ((UnitPayload)payload).unit;
            if (unit == null || containsIdentity(result, unit)) return;
            result.add(unit);
            if (unit instanceof Payloadc) collectPayloadUnits((Payloadc)unit, result);
        } else if (payload instanceof mindustry.world.blocks.payloads.BuildPayload) {
            Building building = ((mindustry.world.blocks.payloads.BuildPayload)payload).build;
            if (building != null) collectPayloadUnits(building.getPayload(), result);
        }
    }

    private static boolean containsIdentity(Seq<Unit> units, Unit target) {
        for (int i = 0; i < units.size; i++) {
            if (units.get(i) == target) return true;
        }
        return false;
    }

    private static boolean isTrackableUnit(Unit unit) {
        return unit != null && unit.isCommandable() && !unit.dead() && player != null && unit.team == player.team();
    }

    private static TransportedUnit findTransportedUnit(Unit unit) {
        for (int i = 0; i < transportedUnits.size; i++) {
            if (transportedUnits.get(i).unit == unit) return transportedUnits.get(i);
        }
        return null;
    }

    private static TransportedUnit claimReappearedUnit(Unit unit) {
        return claimReappearedUnit(unit, false);
    }

    private static TransportedUnit claimReappearedUnit(Unit unit, boolean payloadCandidate) {
        if (unit == null || unit.type == null) return null;

        TransportedUnit candidate = null;
        float nearestDistance2 = Float.MAX_VALUE;
        for (int i = 0; i < transportedUnits.size; i++) {
            TransportedUnit tracked = transportedUnits.get(i);
            if (!tracked.wasPayload || !tracked.payloadObservedLastFrame || tracked.claimedThisFrame
                || tracked.unit == null || tracked.unit == unit || tracked.unit.type != unit.type) continue;
            if (!payloadCandidate && tracked.wasAddedLastFrame) continue;
            if (tracked.unit.isAdded() || tracked.unit.team != unit.team
                || tracked.missingSeconds > transportGraceSeconds) continue;
            if (!payloadCandidate && tracked.payloadObservedThisFrame) continue;

            float distance2 = Mathf.dst2(tracked.lastX, tracked.lastY, unit.x, unit.y);
            float radius = Math.max(32f, Math.max(1f, unit.type.hitSize) * 3f);
            if (distance2 <= radius * radius && distance2 < nearestDistance2) {
                candidate = tracked;
                nearestDistance2 = distance2;
            }
        }
        if (candidate != null) candidate.claimedThisFrame = true;
        return candidate;
    }

    private static int groupMask(int unitId) {
        int mask = 0;
        InputHandler input = control == null ? null : control.input;
        if (input == null || input.controlGroups == null) return mask;

        int availableGroups = Math.min(groupCount, input.controlGroups.length);
        for (int groupIndex = 0; groupIndex < availableGroups; groupIndex++) {
            IntSeq group = input.controlGroups[groupIndex];
            if (group != null && group.contains(unitId)) mask |= 1 << groupIndex;
        }
        return mask;
    }

    private static boolean isTrackedTransportId(int unitId) {
        for (int i = 0; i < transportedUnits.size; i++) {
            TransportedUnit tracked = transportedUnits.get(i);
            if (tracked.wasPayload && tracked.id == unitId && tracked.groupMask != 0
                && tracked.unit != null && !tracked.unit.isAdded()) return true;
        }
        return false;
    }

    private static void refreshTransportedGroupMasks() {
        for (int i = transportedUnits.size - 1; i >= 0; i--) {
            TransportedUnit tracked = transportedUnits.get(i);
            tracked.groupMask = groupMask(tracked.id);
            if (tracked.groupMask == 0) transportedUnits.remove(i);
        }
    }

    private static void migrateGroupMemberId(int oldId, int newId, int mask) {
        if (oldId == newId || mask == 0) return;

        InputHandler input = control == null ? null : control.input;
        if (input == null || input.controlGroups == null) return;

        int availableGroups = Math.min(groupCount, input.controlGroups.length);
        for (int groupIndex = 0; groupIndex < availableGroups; groupIndex++) {
            if ((mask & (1 << groupIndex)) == 0) continue;
            IntSeq group = input.controlGroups[groupIndex];
            if (group == null || !group.contains(oldId)) continue;

            while (group.removeValue(oldId)) {
                // Replace every stale copy left by an older save or edit.
            }
            group.addUnique(newId);
        }
    }

    private static void applyGroupEdit(InputHandler input, int groupIndex, IntSeq unitIds, int edit) {
        if (input == null || input.controlGroups == null || edit == groupEditNone) return;

        if (edit == groupEditRemoveAll || edit == groupEditAddExclusive) {
            removeUnitIdsFromAllGroups(input, unitIds);
        }

        if (edit == groupEditRemoveAll || groupIndex < 0 || groupIndex >= input.controlGroups.length) {
            refreshTransportedGroupMasks();
            return;
        }

        IntSeq group = input.controlGroups[groupIndex];
        if (group == null) {
            group = input.controlGroups[groupIndex] = new IntSeq();
        }

        // Remove all existing copies first so every edited unit occurs exactly once.
        removeUnitIds(group, unitIds);
        for (int i = 0; i < unitIds.size; i++) {
            int unitId = unitIds.get(i);
            if (isValidGroupUnit(Groups.unit.getByID(unitId))) {
                group.addUnique(unitId);
            }
        }
        refreshTransportedGroupMasks();
    }

    private static IntSeq collectValidUnitIds(Seq<Unit> units) {
        IntSeq unitIds = new IntSeq();
        if (units == null) return unitIds;

        for (int i = 0; i < units.size; i++) {
            Unit unit = units.get(i);
            if (isValidGroupUnit(unit)) {
                unitIds.addUnique(unit.id);
            }
        }
        return unitIds;
    }

    private static void removeUnitIdsFromAllGroups(InputHandler input, IntSeq unitIds) {
        for (int i = 0; i < input.controlGroups.length; i++) {
            IntSeq group = input.controlGroups[i];
            if (group != null) {
                removeUnitIds(group, unitIds);
            }
        }
    }

    private static void removeUnitIds(IntSeq group, IntSeq unitIds) {
        for (int i = 0; i < unitIds.size; i++) {
            while (group.removeValue(unitIds.get(i))) {
                // Existing saves may already contain duplicates; remove every copy.
            }
        }
    }

    private static void updateFormationBox(InputHandler input, boolean addPrefix, boolean exclusivePrefix) {
        boolean deletePrefix = isKeyDown(deleteControlGroup);
        if (!formationBoxActive) {
            if (addPrefix && deletePrefix && !Core.scene.hasMouse()
                && (isKeyTap(addControlGroup) || isKeyTap(deleteControlGroup))) {
                beginDeleteFormationBox(input);
            } else if (exclusivePrefix && isKeyTap(exclusiveAddControlGroup) && !Core.scene.hasMouse()) {
                beginExclusiveFormationBox(input);
            } else if (addPrefix && !exclusivePrefix && !deletePrefix
                && isKeyTap(addControlGroup) && !Core.scene.hasMouse()) {
                beginFormationBox(input);
            }
            return;
        }

        boolean prefixReleased = formationBoxDeleteMode ? !addPrefix || !deletePrefix
            : formationBoxExclusiveMode ? !exclusivePrefix : !addPrefix;
        if (formationBoxMouseReleased || prefixReleased) {
            finishFormationBox(input);
        }
    }

    private static boolean canStartFormationBox(InputHandler input) {
        return canStartAnyFormationBox(input) && isKeyDown(addControlGroup);
    }

    private static boolean canStartExclusiveFormationBox(InputHandler input) {
        return canStartAnyFormationBox(input) && isKeyDown(exclusiveAddControlGroup);
    }

    private static boolean canStartAnyFormationBox(InputHandler input) {
        return enabled && outsideCommandMode && input != null && Core.input != null && state != null && state.isGame()
            && Core.scene != null && !Core.scene.hasField() && !Core.scene.hasDialog() && !Core.scene.hasMouse()
            && !Core.scene.hasKeyboard() && !input.commandMode && !isKeyDown(Binding.commandMode);
    }

    private static boolean canStartDeleteFormationBox(InputHandler input) {
        return isKeyDown(deleteControlGroup) && canStartFormationBox(input);
    }

    private static void beginFormationBox(InputHandler input) {
        if (input == null || formationBoxActive) return;

        int emptyGroup = firstEmptyGroup(input);
        if (emptyGroup < 0) return;

        formationBoxDeleteMode = false;
        formationBoxExclusiveMode = false;
        formationBoxGroup = emptyGroup;
        formationBoxStartX = Core.input.mouseWorldX();
        formationBoxStartY = Core.input.mouseWorldY();
        formationBoxMouseReleased = false;
        formationBoxActive = true;
    }

    private static void beginExclusiveFormationBox(InputHandler input) {
        if (input == null || formationBoxActive) return;

        int emptyGroup = firstEmptyGroup(input);
        if (emptyGroup < 0) return;

        formationBoxDeleteMode = false;
        formationBoxExclusiveMode = true;
        formationBoxGroup = emptyGroup;
        formationBoxStartX = Core.input.mouseWorldX();
        formationBoxStartY = Core.input.mouseWorldY();
        formationBoxMouseReleased = false;
        formationBoxActive = true;
    }

    private static void beginDeleteFormationBox(InputHandler input) {
        if (input == null || formationBoxActive) return;

        formationBoxDeleteMode = true;
        formationBoxExclusiveMode = false;
        formationBoxGroup = -1;
        formationBoxStartX = Core.input.mouseWorldX();
        formationBoxStartY = Core.input.mouseWorldY();
        formationBoxMouseReleased = false;
        formationBoxActive = true;
    }

    private static void beginFormationBox(InputHandler input, int screenX, int screenY) {
        if (input == null || formationBoxActive) return;

        int emptyGroup = firstEmptyGroup(input);
        if (emptyGroup < 0) return;

        formationBoxDeleteMode = false;
        formationBoxExclusiveMode = false;
        formationBoxGroup = emptyGroup;
        formationBoxMouseReleased = false;
        formationBoxActive = true;
        setFormationBoxStart(screenX, screenY);
    }

    private static void beginExclusiveFormationBox(InputHandler input, int screenX, int screenY) {
        if (input == null || formationBoxActive) return;

        int emptyGroup = firstEmptyGroup(input);
        if (emptyGroup < 0) return;

        formationBoxDeleteMode = false;
        formationBoxExclusiveMode = true;
        formationBoxGroup = emptyGroup;
        formationBoxMouseReleased = false;
        formationBoxActive = true;
        setFormationBoxStart(screenX, screenY);
    }

    private static void beginDeleteFormationBox(InputHandler input, int screenX, int screenY) {
        if (input == null || formationBoxActive) return;

        formationBoxDeleteMode = true;
        formationBoxExclusiveMode = false;
        formationBoxGroup = -1;
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

        float endX = Core.input.mouseWorldX();
        float endY = Core.input.mouseWorldY();
        Seq<Unit> units = input.selectedCommandUnits(
            formationBoxStartX,
            formationBoxStartY,
            endX - formationBoxStartX,
            endY - formationBoxStartY
        );

        if (formationBoxDeleteMode) {
            deleteFormations(input, units);
        } else if (formationBoxGroup >= 0 && formationBoxGroup < input.controlGroups.length) {
            if (formationBoxExclusiveMode) {
                applyGroupEdit(input, formationBoxGroup, collectValidUnitIds(units), groupEditAddExclusive);
            } else {
                createGroup(input, formationBoxGroup, units);
            }
        }

        formationBoxActive = false;
        formationBoxDeleteMode = false;
        formationBoxExclusiveMode = false;
        formationBoxGroup = -1;
        formationBoxMouseReleased = false;
    }

    private static void deleteFormations(InputHandler input, Seq<Unit> units) {
        if (input == null || units == null || units.isEmpty() || input.controlGroups == null) return;
        removeUnitIdsFromAllGroups(input, collectValidUnitIds(units));
    }

    private static void cancelFormationBox() {
        formationBoxActive = false;
        formationBoxDeleteMode = false;
        formationBoxExclusiveMode = false;
        formationBoxGroup = -1;
        formationBoxMouseReleased = false;
    }

    private static int firstEmptyGroup(InputHandler input) {
        int availableGroups = Math.min(groupCount, input.controlGroups.length);
        for (int i = 0; i < availableGroups; i++) {
            IntSeq group = input.controlGroups[i];
            if (group != null) {
                for (int j = 0; j < group.size; j++) {
                    int unitId = group.get(j);
                    if (!isValidGroupUnit(Groups.unit.getByID(unitId)) && !isTrackedTransportId(unitId)) {
                        group.removeIndex(j--);
                    }
                }
            }
            if (group == null || group.isEmpty()) return i;
        }
        return -1;
    }

    private static boolean isKeyDown(KeyBind keybind) {
        return hasKey(keybind) && Core.input.keyDown(keybind);
    }

    private static boolean isKeyTap(KeyBind keybind) {
        return hasKey(keybind) && Core.input.keyTap(keybind);
    }

    private static boolean hasKey(KeyBind keybind) {
        return keybind != null && keybind.value != null && keybind.value.key != null
            && keybind.value.key != KeyCode.unset;
    }

    private static boolean isBoundTo(KeyBind keybind, KeyCode keycode) {
        return hasKey(keybind) && keybind.value.key == keycode;
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
        group.addAll(collectValidUnitIds(units));
        refreshTransportedGroupMasks();
    }

    private static void selectGroup(InputHandler input, int groupIndex) {
        if (groupIndex < 0 || groupIndex >= input.controlGroups.length) return;

        IntSeq group = input.controlGroups[groupIndex];
        if (group == null) return;

        reconcileTransportedUnits(input);
        for (int i = 0; i < group.size; i++) {
            Unit unit = Groups.unit.getByID(group.get(i));
            if (!isValidGroupUnit(unit) && !isTrackedTransportId(group.get(i))) {
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

    private static void resetState() {
        cancelFormationBox();
        consumedShortcutKeys.clear();
        transportedUnits.clear();
        badgeClusters.clear();
        payloadUnits.clear();
        clusterUnits.clear();
    }

    private static boolean isValidGroupUnit(Unit unit) {
        return unit != null && unit.isCommandable() && unit.isValid() && player != null && unit.team == player.team();
    }

    private static void drawFormationBox() {
        if (!formationBoxActive || state == null || !state.isGame() || Core.input == null || Core.camera == null) return;

        float endX = Core.input.mouseWorldX();
        float endY = Core.input.mouseWorldY();
        formationBoxRect.set(formationBoxStartX, formationBoxStartY, endX - formationBoxStartX, endY - formationBoxStartY).normalize();

        if (formationBoxDeleteMode) {
            Draw.color(Color.scarlet, 0.3f);
        } else {
            if (formationBoxGroup < 0 || formationBoxGroup >= groupColors.length) return;
            Draw.color(groupColors[formationBoxGroup], 0.3f);
        }
        Fill.crect(formationBoxRect.x, formationBoxRect.y, formationBoxRect.width, formationBoxRect.height);
        Draw.reset();
    }

    private static void drawBadges() {
        InputHandler input = control == null ? null : control.input;
        if (input == null || state == null || !state.isGame()) {
            return;
        }

        if (!enabled || player == null || Core.camera == null) {
            return;
        }

        IntSeq[] groups = input.controlGroups;
        if (groups == null || groups.length == 0 || Fonts.outline == null) {
            return;
        }

        int availableGroups = Math.min(groupCount, groups.length);
        rebuildMembership(groups, availableGroups);
        Core.camera.bounds(drawBounds);

        float clusterWorld = badgeClusterPixels;
        if (Core.graphics != null && Core.graphics.getWidth() > 0) {
            clusterWorld *= Core.camera.width / Core.graphics.getWidth();
        }
        clusterWorld = Math.max(1f, clusterWorld);
        float clusterDistance2 = clusterWorld * clusterWorld;

        clusterUnits.clear();
        for (int i = 0; i < Groups.unit.size(); i++) {
            Unit unit = Groups.unit.index(i);
            if (!isValidGroupUnit(unit)) continue;

            float unitSize = Math.max(1f, unit.type.hitSize);
            if (drawBounds.overlaps(unit.x - unitSize / 2f, unit.y - unitSize / 2f, unitSize, unitSize)) {
                clusterUnits.add(unit);
            }
        }

        badgeClusters.clear();
        for (int groupIndex = 0; groupIndex < availableGroups; groupIndex++) {
            for (int unitIndex = 0; unitIndex < clusterUnits.size; unitIndex++) {
                Unit unit = clusterUnits.get(unitIndex);
                if (!groupMembership[groupIndex].contains(unit.id)) continue;

                BadgeCluster nearest = null;
                float nearestDistance2 = Float.MAX_VALUE;
                for (int clusterIndex = 0; clusterIndex < badgeClusters.size; clusterIndex++) {
                    BadgeCluster cluster = badgeClusters.get(clusterIndex);
                    if (cluster.groupIndex != groupIndex) continue;

                    float distance2 = Mathf.dst2(cluster.x, cluster.y, unit.x, unit.y);
                    float allowed = clusterWorld + Math.max(cluster.maxHitSize, unit.type.hitSize) * 0.5f;
                    if (distance2 <= allowed * allowed && distance2 < nearestDistance2) {
                        nearest = cluster;
                        nearestDistance2 = distance2;
                    }
                }

                if (nearest == null) {
                    badgeClusters.add(new BadgeCluster(groupIndex, unit));
                } else {
                    nearest.add(unit);
                }
            }
        }

        Font font = Fonts.outline;
        boolean integerPositions = font.usesIntegerPositions();
        float oldScaleX = font.getScaleX();
        float oldScaleY = font.getScaleY();
        font.setUseIntegerPositions(false);
        font.getData().setScale(0.25f / Scl.scl(1f));

        for (int i = 0; i < badgeClusters.size; i++) {
            BadgeCluster cluster = badgeClusters.get(i);
            float scale = clusterScale(cluster.count);
            float offset = 0f;

            for (int j = 0; j < i; j++) {
                BadgeCluster previous = badgeClusters.get(j);
                if (previous.groupIndex == cluster.groupIndex) continue;

                float distance2 = Mathf.dst2(previous.x, previous.y, cluster.x, cluster.y);
                if (distance2 <= clusterDistance2) {
                    offset += 9f * clusterScale(previous.count);
                }
            }

            float reach = Math.max(cluster.maxHitSize, clusterWorld) + 10f * scale + offset;
            if (!drawBounds.overlaps(cluster.minX - reach, cluster.minY - reach,
                cluster.maxX - cluster.minX + reach * 2f, cluster.maxY - cluster.minY + reach * 2f)) continue;

            drawBadge(font, cluster.x, cluster.y, cluster.maxHitSize, cluster.groupIndex, offset, scale);
        }

        font.getData().setScale(oldScaleX, oldScaleY);
        font.setColor(Color.white);
        font.setUseIntegerPositions(integerPositions);
        Draw.reset();
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

    private static float clusterScale(int count) {
        return count <= 1 ? 1f : Math.min(1.8f, 1f + 0.16f * Mathf.sqrt(count - 1f));
    }

    private static void drawBadge(Font font, float x, float y, float hitSize, int groupIndex, float offset, float scale) {
        String label = groupIndex == 9 ? "0" : String.valueOf(groupIndex + 1);
        float oldScaleX = font.getScaleX();
        float oldScaleY = font.getScaleY();
        font.getData().setScale(oldScaleX * scale, oldScaleY * scale);

        GlyphLayout layout = Pools.obtain(GlyphLayout.class, GlyphLayout::new);
        layout.setText(font, label);

        float width = Math.max(7f * scale, layout.width + 4f * scale);
        float height = Math.max(7f * scale, layout.height + 3f * scale);
        float left = x - hitSize / 2f + offset;
        float centerX = left + width / 2f;
        float centerY = y - hitSize / 2f - height / 2f - 1f;

        Draw.color(Color.black);
        Fill.rect(centerX, centerY, width + 2f, height + 2f);
        Draw.color(groupColors[groupIndex]);
        Fill.rect(centerX, centerY, width, height);

        font.setColor(Color.white);
        font.draw(label, centerX, centerY + layout.height / 2f, 0, Align.center, false);
        Pools.free(layout);
        font.getData().setScale(oldScaleX, oldScaleY);
    }

    private static class TransportedUnit {
        Unit unit;
        int id;
        int groupMask;
        float missingSeconds;
        boolean wasPayload;
        boolean payloadObservedLastFrame;
        boolean payloadObservedThisFrame;
        boolean claimedThisFrame;
        boolean wasAddedLastFrame;

        float lastX;
        float lastY;

        TransportedUnit(Unit unit, int id, int groupMask) {
            this.unit = unit;
            this.id = id;
            this.groupMask = groupMask;
            this.lastX = unit.x;
            this.lastY = unit.y;
        }
    }

    private static class BadgeCluster {
        final int groupIndex;
        float x;
        float y;
        float minX;
        float minY;
        float maxX;
        float maxY;
        float maxHitSize;
        int count;

        BadgeCluster(int groupIndex, Unit unit) {
            this.groupIndex = groupIndex;
            x = unit.x;
            y = unit.y;
            minX = maxX = unit.x;
            minY = maxY = unit.y;
            maxHitSize = Math.max(1f, unit.type.hitSize);
            count = 1;
        }

        void add(Unit unit) {
            float nextCount = count + 1f;
            x = (x * count + unit.x) / nextCount;
            y = (y * count + unit.y) / nextCount;
            minX = Math.min(minX, unit.x);
            minY = Math.min(minY, unit.y);
            maxX = Math.max(maxX, unit.x);
            maxY = Math.max(maxY, unit.y);
            maxHitSize = Math.max(maxHitSize, Math.max(1f, unit.type.hitSize));
            count++;
        }
    }
}
