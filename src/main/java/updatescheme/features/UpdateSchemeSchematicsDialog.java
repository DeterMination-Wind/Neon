package updatescheme.features;

import arc.Core;
import arc.scene.ui.TextButton;
import arc.scene.ui.TextButton.TextButtonStyle;
import mindustry.game.Schematic;
import mindustry.game.Schematics;
import mindustry.gen.Icon;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.ui.dialogs.SchematicsDialog;

import static mindustry.Vars.platform;
import static mindustry.Vars.schematicExtension;
import static mindustry.Vars.schematics;
import static mindustry.Vars.steam;
import static mindustry.Vars.ui;

final class UpdateSchemeSchematicsDialog extends SchematicsDialog {

    UpdateSchemeSchematicsDialog() {
        super();
    }

    @Override
    public void showExport(Schematic s) {
        BaseDialog dialog = new BaseDialog("@editor.export");
        dialog.cont.pane(p -> {
            p.margin(10f);
            p.table(Styles.black6, t -> {
                TextButtonStyle style = Styles.flatt;
                t.defaults().growX().height(58f).minWidth(460f).left().pad(4f);

                if (steam && !s.hasSteamID()) {
                    styleButton(t.button("@schematic.shareworkshop", Icon.book, style, () -> {
                        dialog.hide();
                        platform.publish(s);
                    }).get());
                    t.row();
                }

                styleButton(t.button("@schematic.copy", Icon.copy, style, () -> {
                    dialog.hide();
                    if (ui != null) ui.showInfoFade("@copied");
                    String base64 = UpdateSchemeUtil.exportBase64ForPublish(s);
                    Core.app.setClipboardText(base64.isEmpty() ? schematics.writeBase64(s) : base64);
                }).get());
                t.row();

                styleButton(t.button("@schematic.exportfile", Icon.export, style, () -> {
                    dialog.hide();
                    platform.export(s.name(), schematicExtension, file -> Schematics.write(s, file));
                }).get());
                t.row();

                if (UpdateSchemeFeature.shareBridge.isSupported()) {
                    styleButton(t.button("@us.uc.mdx-share-chat", Icon.chat, style, () -> {
                        dialog.hide();
                        UpdateSchemeFeature.shareBridge.shareToChat(s);
                    }).get());
                    t.row();
                    styleButton(t.button("@us.uc.mdx-share-clipboard", Icon.star, style, () -> {
                        dialog.hide();
                        UpdateSchemeFeature.shareBridge.shareToClipboard(s);
                    }).get());
                    t.row();
                }

                styleButton(t.button(Core.bundle.get("us.uc.upload"), Icon.upload, style, () -> {
                    dialog.hide();
                    UpdateSchemeUploadDialog.oneClickUpload(s);
                }).get());
                t.row();

                styleButton(t.button(Core.bundle.get("us.uc.update"), Icon.refresh, style, () -> {
                    dialog.hide();
                    UpdateSchemeUploadDialog.oneClickUpdateOrShow(s);
                }).get());
            });
        });

        dialog.addCloseButton();
        dialog.show();
    }

    private static void styleButton(TextButton button) {
        if (button == null || button.getLabel() == null) return;
        button.getLabel().setWrap(false);
        button.getLabel().setEllipsis(true);
    }
}
