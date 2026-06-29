package neoncompat.overlay;

import arc.Core;
import arc.Events;
import arc.input.KeyBind;
import arc.input.KeyCode;
import arc.util.Log;
import arc.util.Time;
import mindustry.Vars;
import mindustry.game.EventType.ClientLoadEvent;
import mindustry.game.EventType.Trigger;
import mindustry.input.Binding;

public final class NeonOverlayBootstrap {
    private static boolean installed;
    private static KeyBind overlayBind;

    private NeonOverlayBootstrap() {
    }

    public static void ensureInitialized() {
        if (installed) return;
        installed = true;

        Log.info("[Neon Embedded Overlay] bootstrap installed. headless=" + Vars.headless);
        Events.on(ClientLoadEvent.class, event -> {
            Log.info("[Neon Embedded Overlay] ClientLoadEvent. " + runtimeState());
            scheduleInit("client-load");
        });

        Events.run(Trigger.update, NeonOverlayBootstrap::update);
        scheduleInit("bootstrap");
    }

    private static void ensureKeybind() {
        if (overlayBind != null) return;
        overlayBind = KeyBind.add("overlayUI", KeyCode.z, "mindustryX");
        Log.info("[Neon Embedded Overlay] keybind registered: overlayUI default=Z category=mindustryX");
    }

    private static void update() {
        if (Vars.headless || Core.scene == null) return;
        ensureKeybind();
        if (!OverlayUI.INSTANCE.isInitialized() || !OverlayUI.INSTANCE.isAttached()) {
            initializeOverlay("update");
        }

        if (Core.scene.hasField()) {
            if (overlayBind != null && Core.input.keyTap(overlayBind)) {
                Log.info("[Neon Embedded Overlay] toggle ignored: text field focused. " + runtimeState());
            }
            return;
        }
        if (overlayBind == null || !Core.input.keyTap(overlayBind)) return;

        if (Vars.control != null && Vars.control.input != null
            && Core.input.keyTap(Binding.schematicFlipX)
            && !Vars.control.input.selectPlans.isEmpty()) {
            Log.info("[Neon Embedded Overlay] toggle ignored: schematic flip shortcut is active.");
            return;
        }

        if (!Core.input.ctrl()) {
            Log.info("[Neon Embedded Overlay] toggle key pressed.");
            if (!OverlayUI.INSTANCE.isInitialized() || !OverlayUI.INSTANCE.isAttached()) {
                initializeOverlay("toggle");
            }
            OverlayUI.INSTANCE.toggle();
        } else {
            Log.info("[Neon Embedded Overlay] toggle ignored: ctrl is held.");
        }
    }

    private static void scheduleInit(String source) {
        ensureKeybind();
        Time.runTask(1f, () -> initializeOverlay(source));
    }

    private static void initializeOverlay(String source) {
        if (Vars.headless) return;
        if (Core.scene == null) {
            Log.info("[Neon Embedded Overlay] init postponed from " + source + ": scene is null. " + runtimeState());
            return;
        }
        if (OverlayUI.INSTANCE.isInitialized() && OverlayUI.INSTANCE.isAttached()) return;

        try {
            Log.info("[Neon Embedded Overlay] initializing OverlayUI from " + source + ". " + runtimeState());
            OverlayUI.INSTANCE.init();
            OverlayUI.INSTANCE.debugLogState("after-init-" + source);
            Log.info("[Neon Embedded Overlay] OverlayUI initialized from " + source + ".");
        } catch (Throwable t) {
            Log.err("[Neon Embedded Overlay] OverlayUI init failed from " + source + ".", t);
        }
    }

    private static String runtimeState() {
        boolean scene = Core.scene != null;
        boolean settings = Core.settings != null;
        boolean ui = Vars.ui != null;
        boolean hudGroup = ui && Vars.ui.hudGroup != null;
        boolean hudfrag = ui && Vars.ui.hudfrag != null;
        boolean hudShown = hudfrag && Vars.ui.hudfrag.shown;
        boolean menu = Vars.state != null && Vars.state.isMenu();
        String size = Core.graphics == null ? "unknown" : Core.graphics.getWidth() + "x" + Core.graphics.getHeight();
        return "scene=" + scene
            + ", settings=" + settings
            + ", ui=" + ui
            + ", hudGroup=" + hudGroup
            + ", hudfrag=" + hudfrag
            + ", hudShown=" + hudShown
            + ", menu=" + menu
            + ", graphics=" + size;
    }
}
