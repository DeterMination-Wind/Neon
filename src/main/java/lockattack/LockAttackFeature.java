package lockattack;

import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Font;
import arc.graphics.g2d.GlyphLayout;
import arc.graphics.g2d.Lines;
import arc.input.KeyBind;
import arc.input.KeyCode;
import arc.math.geom.Vec2;
import arc.scene.ui.layout.Scl;
import arc.util.Align;
import arc.util.Time;
import arc.util.pooling.Pools;
import mindustry.content.Blocks;
import mindustry.game.EventType;
import mindustry.gen.Building;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Healthc;
import mindustry.gen.Icon;
import mindustry.gen.Teamc;
import mindustry.gen.Unit;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.input.InputHandler;
import mindustry.ui.Fonts;
import mindustry.ui.dialogs.SettingsMenuDialog;

import static mindustry.Vars.control;
import static mindustry.Vars.player;
import static mindustry.Vars.state;
import static mindustry.Vars.ui;
import static mindustry.Vars.world;

/**
 * Lock-on focus fire.
 *
 * Tap the lock key (default L) to lock on to the enemy unit or building under the
 * cursor:
 * - the directly controlled unit is forced to aim at and fire on the target every frame
 *   (the aim is applied in the Trigger.draw phase, which runs after the vanilla input
 *   handler, so it wins for the next frame's weapon update);
 * - any selected commandable units receive a one-shot {@link Call#commandUnits} attack
 *   order, which is executed through the vanilla network command path on both
 *   singleplayer and multiplayer, and is automatically cleared by the game when the
 *   target dies.
 *
 * Tapping the key again on another target switches the lock; tapping on empty ground or
 * a friendly target unlocks. The lock is also dropped automatically when the target
 * dies or becomes invalid.
 */
public final class LockAttackFeature {
    private static final String keyShowHp = "lockattack-show-hp";
    private static final String keybindName = "lock-attack";
    private static final String keybindCategory = "lock-attack";

    /** Max distance from the cursor to the target's edge that still counts as a hit. */
    private static final float pickRadius = 16f;

    private static KeyBind lockKey;
    private static boolean initialized;
    private static boolean keybindRegistered;
    private static boolean settingsAdded;
    private static LockAttackMod.BundledSettingsRenderer bundledSettingsRenderer = LockAttackFeature::buildDefaultSettings;

    private static Teamc locked;
    /** Whether the currently locked target has already received a command order. */
    private static boolean commandedLock;

    private LockAttackFeature() {
    }

    public static void init() {
        if (initialized) return;
        initialized = true;

        Events.on(EventType.WorldLoadEvent.class, event -> clearLock());
        Events.run(EventType.Trigger.update, LockAttackFeature::updateLock);
        Events.run(EventType.Trigger.draw, () -> {
            Draw.draw(Layer.overlayUI - 0.1f, LockAttackFeature::drawLock);
        });
    }

    public static void loadClient(EventType.ClientLoadEvent event) {
        Core.settings.defaults(keyShowHp, true);
        registerKeybind();

        if (!LockAttackMod.bekBundled && !settingsAdded && ui != null) {
            settingsAdded = true;
            ui.settings.addCategory("@settings.lockattack", Icon.lock, LockAttackFeature::buildSettings);
        }
    }

    private static void buildSettings(SettingsMenuDialog.SettingsTable table) {
        buildDefaultSettings(table);
    }

    private static void buildDefaultSettings(SettingsMenuDialog.SettingsTable table) {
        table.checkPref(keyShowHp, true);
    }

    public static void configureBundledSettingsRenderer(LockAttackMod.BundledSettingsRenderer renderer) {
        bundledSettingsRenderer = renderer == null
            ? LockAttackFeature::buildDefaultSettings
            : renderer;
    }

    public static void buildBundledSettings(SettingsMenuDialog.SettingsTable table) {
        bundledSettingsRenderer.build(table);
    }

    private static void registerKeybind() {
        if (keybindRegistered) return;
        keybindRegistered = true;
        lockKey = KeyBind.add(keybindName, KeyCode.l, keybindCategory);
    }

    private static void updateLock() {
        if (state == null || !state.isGame() || Core.scene == null
            || Core.scene.hasMouse() || Core.scene.hasField() || Core.scene.hasDialog()) {
            return;
        }

        // drop the lock as soon as the target dies, leaves fog, or turns friendly
        if (locked != null && !isValidLockTarget(locked)) {
            locked = null;
            commandedLock = false;
        }

        // single tap of the lock key locks on to / switches / releases the target
        if (!isLockKeyTapped()) return;

        Vec2 worldPos = Core.input.mouseWorld();
        Teamc target = pickTarget(worldPos.x, worldPos.y);

        if (target == null) {
            // tapped on empty ground or a friendly target: unlock
            locked = null;
            commandedLock = false;
        } else if (locked != target) {
            // lock on / switch target
            locked = target;
            commandedLock = false;
            commandSelectedUnits();
        }
    }

    private static Teamc pickTarget(float mx, float my) {
        Unit best = null;
        float bestDist = Float.MAX_VALUE;

        for (int i = 0; i < Groups.unit.size(); i++) {
            Unit unit = Groups.unit.index(i);
            if (unit.team() == player.team() || unit.inFogTo(player.team()) || !unit.isValid()) continue;

            float dist = unit.dst(mx, my) - unit.hitSize / 2f;
            if (dist < pickRadius && dist < bestDist) {
                bestDist = dist;
                best = unit;
            }
        }

        if (best != null) return best;

        Building building = world.buildWorld(mx, my);
        if (building != null && building.team() != player.team()
            && !building.inFogTo(player.team()) && building.block != Blocks.air) {
            return building;
        }

        return null;
    }

    private static boolean isValidLockTarget(Teamc target) {
        return target instanceof Healthc && ((Healthc) target).isValid()
            && target.team() != player.team() && !target.inFogTo(player.team());
    }

    private static void commandSelectedUnits() {
        InputHandler input = control == null ? null : control.input;
        if (input == null || locked == null || input.selectedUnits.isEmpty() || commandedLock) return;

        int[] ids = input.selectedUnits.mapInt(unit -> unit.id).toArray();
        Building buildingTarget = locked instanceof Building ? (Building) locked : null;
        Unit unitTarget = locked instanceof Unit ? (Unit) locked : null;
        Call.commandUnits(player, ids, buildingTarget, unitTarget, null, false, true);
        commandedLock = true;
    }

    private static boolean isLockKeyTapped() {
        return lockKey != null && lockKey.value != null && lockKey.value.key != null
            && lockKey.value.key != KeyCode.unset && Core.input.keyTap(lockKey);
    }

    private static void drawLock() {
        if (state == null || !state.isGame() || locked == null || player == null || Core.camera == null) return;
        if (Core.scene != null && Core.scene.hasDialog()) return;

        // force the directly controlled unit to aim at and fire on the locked target.
        // this runs in the Trigger.draw phase, after the vanilla input handler, so the
        // aim survives until the next frame's weapon update; unlock to hand control
        // back to vanilla aiming.
        Unit controlled = player.unit();
        if (controlled != null && controlled.isValid()) {
            controlled.aim(locked.x(), locked.y());
            controlled.controlWeapons(true, true);
        }

        float size = lockedSize(locked);
        float x = locked.x();
        float y = locked.y();

        // rotating lock box + line to the controlled unit
        Lines.stroke(2f);
        Draw.color(Pal.remove, 0.9f);
        Drawf.square(x, y, size * 0.85f, Time.time * 2.5f);
        if (controlled != null) {
            Drawf.line(Pal.remove, controlled.x, controlled.y, x, y);
        }
        Draw.reset();

        if (Core.settings.getBool(keyShowHp, true)) {
            drawTargetInfo(locked, x, y, size);
        }
    }

    private static float lockedSize(Teamc target) {
        if (target instanceof Unit) return Math.max(8f, ((Unit) target).hitSize);
        if (target instanceof Building) return Math.max(10f, ((Building) target).block.size * 8f);
        return 10f;
    }

    private static void drawTargetInfo(Teamc target, float x, float y, float size) {
        if (!(target instanceof Healthc)) return;
        Healthc h = (Healthc) target;

        String name;
        if (target instanceof Unit) {
            name = ((Unit) target).type.localizedName;
        } else if (target instanceof Building) {
            name = ((Building) target).block.localizedName;
        } else {
            name = null;
        }

        float barWidth = 48f;
        float barHeight = 5f;
        float barX = x - barWidth / 2f;
        float barY = y + size * 0.6f + 6f;

        // Fill.rect is center-anchored, Fill.crect is corner-anchored:
        // the background is centered on the target x, the health fill grows from the left edge.
        Draw.color(Color.black);
        Fill.rect(x, barY, barWidth, barHeight);
        Draw.color(Pal.remove);
        Fill.crect(barX, barY - barHeight / 2f, barWidth * h.healthf(), barHeight);

        if (name != null && Fonts.outline != null) {
            Font font = Fonts.outline;
            boolean integerPositions = font.usesIntegerPositions();
            float oldScaleX = font.getScaleX();
            float oldScaleY = font.getScaleY();
            font.setUseIntegerPositions(false);
            font.getData().setScale(0.28f / Scl.scl(1f));

            String text = name + "  " + (int) h.health() + "/" + (int) h.maxHealth();
            GlyphLayout layout = Pools.obtain(GlyphLayout.class, GlyphLayout::new);
            layout.setText(font, text);

            font.setColor(Color.white);
            font.draw(text, x, barY + barHeight / 2f + layout.height / 2f + 2f, 0, Align.center, false);
            Pools.free(layout);

            font.getData().setScale(oldScaleX, oldScaleY);
            font.setColor(Color.white);
            font.setUseIntegerPositions(integerPositions);
        }

        Draw.reset();
    }

    private static void clearLock() {
        locked = null;
        commandedLock = false;
    }
}
