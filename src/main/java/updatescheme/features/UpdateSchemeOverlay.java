package updatescheme.features;

import arc.Core;
import arc.func.Prov;
import arc.graphics.Color;
import arc.scene.ui.Label;
import arc.scene.ui.TextButton;
import arc.scene.ui.layout.Table;
import arc.util.Align;
import arc.util.Time;
import mindustry.Vars;
import mindustry.gen.Icon;
import mindustry.ui.Styles;
import mdtxcompat.OverlayUiBridge;

final class UpdateSchemeOverlay {

    private static boolean initialized;
    private static Table content;
    private static OverlayUiBridge.OverlayWindowHandle window;
    private static boolean lastEnabled;

    private UpdateSchemeOverlay() {
    }

    static void init() {
        if (initialized) return;
        initialized = true;
        ensureAttached();
        Time.runTask(10f, UpdateSchemeOverlay::ensureAttached);
    }

    static void syncVisibility() {
        if (window == null || window.asElement() == null) return;
        if (lastEnabled == UpdateSchemeFeature.enabled) return;
        lastEnabled = UpdateSchemeFeature.enabled;
        window.setEnabledAndPinned(lastEnabled, lastEnabled);
    }

    private static void ensureAttached() {
        if (window != null || !UpdateSchemeFeature.overlayUi.isSupported()) return;
        ensureContent();
        Prov<Boolean> availability = () -> Vars.state != null && Vars.state.isGame();
        window = UpdateSchemeFeature.overlayUi.registerWindow("updatescheme-quick-parse", content, availability);
        if (window == null || window.asElement() == null) return;
        window.configure(true, false);
        lastEnabled = !UpdateSchemeFeature.enabled;
        syncVisibility();
    }

    private static void ensureContent() {
        if (content != null) return;
        content = new Table();
        content.margin(4f);

        TextButton button = content.button("", Styles.flatt, UpdateSchemeOverlay::parseClipboardNow).size(96f).get();
        button.clearChildren();
        button.table(inner -> {
            inner.defaults().center();
            inner.image(Icon.book).size(40f).padTop(4f).row();
            Label label = new Label(Core.bundle.get("us.overlay.parse"));
            label.setAlignment(Align.center);
            label.setColor(Color.white);
            label.setFontScale(0.7f);
            inner.add(label).padTop(2f).padBottom(4f).center();
        }).grow();
    }

    private static void parseClipboardNow() {
        String raw = Core.app == null ? "" : Core.app.getClipboardText();
        raw = raw == null ? "" : raw.trim();
        if (raw.isEmpty()) {
            UpdateSchemeUI.toast(Core.bundle.get("us.overlay.empty"));
            return;
        }

        UpdateSchemeDefs.ParsedRef ref = UpdateSchemeDefs.parseRefFromText(raw);
        if (ref == null) ref = UpdateSchemeDefs.parseRefFromUrl(raw);
        if (ref == null || ref.source != UpdateSchemeDefs.Source.manifest) {
            UpdateSchemeUI.toast(Core.bundle.get("us.overlay.invalid"));
            return;
        }

        UpdateSchemeFetcher.subscribeAndImport(ref, false);
    }
}
