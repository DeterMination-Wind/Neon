package hidewhatprocessorsshow;

import arc.Core;
import arc.Events;
import arc.input.KeyBind;
import arc.input.KeyCode;
import arc.struct.ObjectMap;
import arc.struct.ObjectSet;
import mindustry.game.EventType;
import mindustry.game.MapObjectives.ObjectiveMarker;
import mindustry.gen.Building;
import mindustry.gen.Drawc;
import mindustry.gen.EffectStatec;
import mindustry.gen.Groups;
import mindustry.logic.LExecutor;
import mindustry.logic.LExecutor.EffectI;
import mindustry.logic.LExecutor.LInstruction;
import mindustry.world.blocks.logic.LogicBlock.LogicBuild;

import java.util.Iterator;

import static mindustry.Vars.player;
import static mindustry.Vars.state;
import static mindustry.Vars.ui;

final class ProcessorVisualController {
    private static final KeyBind toggleProcessorEffects = KeyBind.add(
        "hide_processor_effects", KeyCode.f9, "processor-visual"
    );
    private static final KeyBind toggleProcessorMarkers = KeyBind.add(
        "hide_processor_markers", KeyCode.f10, "processor-visual"
    );

    private static final ObjectMap<LExecutor, LInstruction[]> wrappedExecutors = new ObjectMap<>();
    private static final ObjectSet<LExecutor> activeExecutors = new ObjectSet<>();
    private static final ObjectSet<EffectStatec> trackedEffects = new ObjectSet<>();
    private static final ObjectMap<ObjectiveMarker, MarkerVisibility> suppressedMarkers = new ObjectMap<>();

    private static boolean initialized;
    private static boolean effectsVisible = true;
    private static boolean markersVisible = true;
    private static int executorPruneTimer;

    private ProcessorVisualController() {
    }

    static void registerKeybinds() {
        // Loading this class registers the static keybinds before the controls dialog is built.
    }

    static void init() {
        if (initialized) return;
        initialized = true;

        Events.run(EventType.Trigger.update, ProcessorVisualController::update);
        Events.run(EventType.Trigger.preDraw, ProcessorVisualController::beginMarkerSuppression);
        Events.run(EventType.Trigger.postDraw, ProcessorVisualController::endMarkerSuppression);
        Events.run(EventType.Trigger.uiDrawBegin, ProcessorVisualController::beginMarkerSuppression);
        Events.run(EventType.Trigger.uiDrawEnd, ProcessorVisualController::endMarkerSuppression);
        Events.on(EventType.WorldLoadEvent.class, event -> clearWorldState());
    }

    private static void update() {
        purgeFinishedEffects();

        if (canHandleHotkeys()) {
            if (Core.input.keyTap(toggleProcessorEffects)) {
                effectsVisible = !effectsVisible;
                if (!effectsVisible) removeTrackedEffects();
                showStateToast("hidewhatprocessorsshow.effects", effectsVisible);
            }

            if (Core.input.keyTap(toggleProcessorMarkers)) {
                markersVisible = !markersVisible;
                showStateToast("hidewhatprocessorsshow.markers", markersVisible);
            }
        }

        if (state == null || !state.isGame()) {
            clearProcessorReferences();
            return;
        }

        wrapWorldProcessorEffects();
    }

    private static boolean canHandleHotkeys() {
        if (state == null || !state.isGame() || player == null) return false;
        if (ui == null || ui.hudfrag == null || !ui.hudfrag.shown) return false;
        if (ui.chatfrag != null && ui.chatfrag.shown()) return false;
        if (ui.consolefrag != null && ui.consolefrag.shown()) return false;
        return Core.scene == null || (!Core.scene.hasDialog() && !Core.scene.hasField());
    }

    private static void showStateToast(String keyPrefix, boolean visible) {
        if (ui == null) return;
        String stateKey = visible ? "visible" : "hidden";
        ui.showInfoToast(Core.bundle.get(keyPrefix + "." + stateKey), 2f);
    }

    private static void wrapWorldProcessorEffects() {
        activeExecutors.clear();

        for (int i = 0; i < Groups.build.size(); i++) {
            Building building = Groups.build.index(i);
            if (!(building instanceof LogicBuild)) continue;

            LExecutor executor = ((LogicBuild) building).executor;
            if (!executor.privileged) continue;

            activeExecutors.add(executor);
            LInstruction[] instructions = executor.instructions;
            if (wrappedExecutors.get(executor) == instructions) continue;

            for (int instructionIndex = 0; instructionIndex < instructions.length; instructionIndex++) {
                LInstruction instruction = instructions[instructionIndex];
                if (instruction instanceof EffectI) {
                    instructions[instructionIndex] = new FilteredEffectInstruction((EffectI) instruction);
                }
            }

            wrappedExecutors.put(executor, instructions);
        }

        executorPruneTimer++;
        if (executorPruneTimer >= 60) {
            executorPruneTimer = 0;
            Iterator<LExecutor> iterator = wrappedExecutors.keys().iterator();
            while (iterator.hasNext()) {
                if (!activeExecutors.contains(iterator.next())) iterator.remove();
            }
        }
    }

    private static void beginMarkerSuppression() {
        endMarkerSuppression();
        if (markersVisible || state == null || !state.isGame()) return;

        for (ObjectiveMarker marker : state.markers) {
            suppressedMarkers.put(marker, new MarkerVisibility(marker.world, marker.minimap));
            marker.world = false;
            marker.minimap = false;
        }
    }

    private static void endMarkerSuppression() {
        for (ObjectMap.Entry<ObjectiveMarker, MarkerVisibility> entry : suppressedMarkers) {
            entry.key.world = entry.value.world;
            entry.key.minimap = entry.value.minimap;
        }
        suppressedMarkers.clear();
    }

    private static void purgeFinishedEffects() {
        Iterator<EffectStatec> iterator = trackedEffects.iterator();
        while (iterator.hasNext()) {
            if (!iterator.next().isAdded()) iterator.remove();
        }
    }

    private static void removeTrackedEffects() {
        for (EffectStatec effect : trackedEffects) {
            if (effect.isAdded()) effect.remove();
        }
        trackedEffects.clear();
    }

    private static void clearWorldState() {
        endMarkerSuppression();
        removeTrackedEffects();
        clearProcessorReferences();
    }

    private static void clearProcessorReferences() {
        wrappedExecutors.clear();
        activeExecutors.clear();
        executorPruneTimer = 0;
    }

    private static final class FilteredEffectInstruction implements LInstruction {
        private final EffectI delegate;

        private FilteredEffectInstruction(EffectI delegate) {
            this.delegate = delegate;
        }

        @Override
        public void run(LExecutor executor) {
            if (!effectsVisible) return;

            int previousDrawSize = Groups.draw.size();
            delegate.run(executor);
            int currentDrawSize = Groups.draw.size();

            for (int i = previousDrawSize; i < currentDrawSize; i++) {
                Drawc draw = Groups.draw.index(i);
                if (draw instanceof EffectStatec) {
                    trackedEffects.add((EffectStatec) draw);
                }
            }
        }
    }

    private static final class MarkerVisibility {
        private final boolean world;
        private final boolean minimap;

        private MarkerVisibility(boolean world, boolean minimap) {
            this.world = world;
            this.minimap = minimap;
        }
    }
}
