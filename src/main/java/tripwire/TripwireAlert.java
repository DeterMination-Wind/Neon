package tripwire;

import arc.Core;
import arc.math.geom.Vec2;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import arc.util.Time;
import mindustry.gen.Call;
import mindustry.gen.Player;
import mindustry.gen.Unit;

import java.lang.reflect.Method;

import static mindustry.Vars.player;
import static mindustry.Vars.net;
import static mindustry.Vars.tilesize;
import static mindustry.Vars.ui;

public final class TripwireAlert {
    private static final ObjectMap<String, AlertStack> queuedAlerts = new ObjectMap<>();
    private static final Seq<AlertStack> alertOrder = new Seq<>();
    private static Method mindustryXNewMarkFromChat;
    private static boolean mindustryXMarkerResolved;
    private static Method vanillaPingMethod;
    private static boolean vanillaPingResolved;
    private static boolean chatFlushScheduled;

    private TripwireAlert() {
    }

    public static void crossed(float x, float y, Unit unit) {
        if (unit == null || unit.type == null || ui == null) return;
        int tileX = (int)(x / tilesize);
        int tileY = (int)(y / tilesize);
        String unitName = unit.type.localizedName;
        String message = Core.bundle.format("tripwire.alert.crossed", tileX, tileY, unitName);
        String markerMessage = Core.bundle.format("tripwire.alert.marker", tileX, tileY);
        if (TripwireSettings.toastAlert()) {
            ui.announce(message, 4f);
        }
        if (!markWithMindustryX(markerMessage, tileX, tileY)) {
            markWithVanillaPing(x, y, Core.bundle.format("tripwire.alert.ping", unitName));
        }
        queueChatAlert(tileX, tileY, unitName);
    }

    private static boolean markWithMindustryX(String markerMessage, int tileX, int tileY) {
        try {
            if (!mindustryXMarkerResolved) {
                mindustryXMarkerResolved = true;
                Class<?> markerType = Class.forName("mindustryX.features.MarkerType");
                mindustryXNewMarkFromChat = markerType.getMethod("newMarkFromChat", String.class, Vec2.class);
            }
            if (mindustryXNewMarkFromChat == null) return false;
            mindustryXNewMarkFromChat.invoke(null, markerMessage, new Vec2(tileX, tileY));
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void markWithVanillaPing(float x, float y, String text) {
        if (player == null) return;
        try {
            if (!vanillaPingResolved) {
                vanillaPingResolved = true;
                vanillaPingMethod = Class.forName("mindustry.gen.Call").getMethod("pingLocation", Player.class, float.class, float.class, String.class);
            }
            if (vanillaPingMethod != null) {
                vanillaPingMethod.invoke(null, player, x, y, text);
                return;
            }
        } catch (Throwable ignored) {
        }

        try {
            Class.forName("mindustry.input.InputHandler")
                .getMethod("pingLocation", Player.class, float.class, float.class, String.class)
                .invoke(null, player, x, y, text);
        } catch (Throwable ignored) {
        }
    }

    private static void queueChatAlert(int tileX, int tileY, String unitName) {
        if (!TripwireSettings.chatAlert()) return;

        String key = tileX + "|" + tileY + "|" + unitName;
        AlertStack stack = queuedAlerts.get(key);
        if (stack == null) {
            stack = new AlertStack(tileX, tileY, unitName);
            queuedAlerts.put(key, stack);
            alertOrder.add(stack);
        }
        stack.count++;

        if (!chatFlushScheduled) {
            chatFlushScheduled = true;
            Time.run(TripwireSettings.chatBatchDelayTicks(), TripwireAlert::flushChatAlerts);
        }
    }

    private static void flushChatAlerts() {
        chatFlushScheduled = false;
        if (alertOrder.isEmpty()) return;

        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < alertOrder.size; i++) {
            AlertStack stack = alertOrder.get(i);
            if (i > 0) builder.append('\n');
            builder.append(Core.bundle.format("tripwire.alert.chat", stack.tileX, stack.tileY, stack.unitName, stack.count));
        }

        String message = builder.toString();
        queuedAlerts.clear();
        alertOrder.clear();

        if (net != null && net.active()) {
            Call.sendChatMessage(message);
        } else if (ui != null && ui.chatfrag != null) {
            ui.chatfrag.addMessage(message);
        }
    }

    private static class AlertStack {
        final int tileX;
        final int tileY;
        final String unitName;
        int count;

        AlertStack(int tileX, int tileY, String unitName) {
            this.tileX = tileX;
            this.tileY = tileY;
            this.unitName = unitName;
        }
    }
}
