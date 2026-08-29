package mindustryX.features.ui;

import arc.util.serialization.Json;

/**
 * Single shared {@link Json} instance for WindowData persistence.
 *
 * Package-private on purpose: this is internal bridge plumbing, not part of the
 * compatibility surface. The format itself is pinned by WindowDataJsonFormatTest.
 */
final class OverlayCompatJson {
    private static final Json json = new Json();

    private OverlayCompatJson() {
    }

    static OverlayUI.WindowData parse(String raw) {
        return json.fromJson(OverlayUI.WindowData.class, raw);
    }

    static String write(OverlayUI.WindowData data) {
        return json.toJson(data);
    }
}
