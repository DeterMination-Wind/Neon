package foreignservertranslator;

import arc.Core;
import arc.scene.ui.TextField;
import arc.scene.ui.TextButton;
import arc.scene.ui.layout.Scl;
import arc.struct.Seq;
import mindustry.gen.Icon;
import mindustry.gen.Tex;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.ui.dialogs.SettingsMenuDialog;

public final class TranslatorSettings{
    private TranslatorSettings(){
    }

    public static void build(SettingsMenuDialog.SettingsTable table){
        table.checkPref(TranslatorFeature.openAiEnabledKey, false);
        table.textPref(TranslatorFeature.baseUrlKey, "");
        table.pref(new ApiKeySetting());
        table.textPref(TranslatorFeature.modelKey, "gpt-4o-mini");
        table.sliderPref(TranslatorFeature.contextMessagesKey, 3, 0, 10, 1, value -> value + "");
        table.checkPref(TranslatorFeature.bundleHintsKey, false);
        table.checkPref(TranslatorFeature.fullBundleContextKey, false);
        table.pref(new LanguageSetting(TranslatorFeature.incomingLanguageKey, "zh-Hans"));
        table.pref(new LanguageSetting(TranslatorFeature.outgoingLanguageKey, "en"));
        table.pref(new CacheControlsSetting());
        table.pref(new TokenStatsSetting());

        // Marker translation settings header
        table.row();
        table.add(Core.bundle.get("fst.markerSettings", "Marker Translation")).left().growX().padTop(16f).padBottom(4f);
        table.row();

        table.checkPref("fst.marker.openAiEnabled", false);
        table.textPref("fst.marker.baseUrl", "");
        table.pref(new MarkerApiKeySetting());
        table.textPref("fst.marker.model", "gpt-4o-mini");
    }

    private static float prefWidth(){
        return Math.min(Core.graphics.getWidth() / Scl.scl() / 1.15f, 560f);
    }

    private static final class ApiKeySetting extends SettingsMenuDialog.SettingsTable.Setting{
        ApiKeySetting(){
            super(TranslatorFeature.apiKeyKey);
        }

        @Override
        public void add(SettingsMenuDialog.SettingsTable table){
            table.table(Tex.button, row -> {
                row.left().margin(10f);
                row.add(title).left().growX().minWidth(0f).wrap();
                row.label(() -> mask(Core.settings.getString(name, ""))).left().width(140f).padLeft(10f);
                row.button("@fst.apiKey.edit", () -> showDialog(table)).width(120f).height(46f).padLeft(8f);
            }).width(prefWidth()).padTop(4f).left();
            table.row();
        }

        private void showDialog(SettingsMenuDialog.SettingsTable owner){
            BaseDialog dialog = new BaseDialog(title);
            dialog.addCloseButton();
            TextField field = dialog.cont.field(Core.settings.getString(name, ""), text -> {}).width(prefWidth() * 0.8f).height(54f).get();
            field.setPasswordMode(true);
            field.setPasswordCharacter('*');

            dialog.buttons.button("@cancel", dialog::hide).size(140f, 54f);
            dialog.buttons.button("@ok", () -> {
                Core.settings.put(name, field.getText().trim());
                owner.rebuild();
                dialog.hide();
            }).size(140f, 54f);
            dialog.show();
            Core.scene.setKeyboardFocus(field);
            field.setCursorPosition(field.getText().length());
        }

        private String mask(String value){
            if(value == null || value.isEmpty()) return Core.bundle.get("fst.apiKey.empty", "(empty)");
            int length = Math.min(value.length(), 12);
            StringBuilder builder = new StringBuilder(length);
            for(int i = 0; i < length; i++){
                builder.append('*');
            }
            return builder.toString();
        }
    }

    private static final class LanguageSetting extends SettingsMenuDialog.SettingsTable.Setting{
        private final String def;

        LanguageSetting(String name, String def){
            super(name);
            this.def = def;
        }

        @Override
        public void add(SettingsMenuDialog.SettingsTable table){
            table.table(Tex.button, row -> {
                row.left().margin(10f);
                row.add(title).left().growX().minWidth(0f).wrap();

                TextButton select = row.button("", Styles.flatt, () -> showLanguageDialog(table))
                    .width(300f).height(46f).padLeft(10f).get();
                select.update(() -> select.setText(LanguageCatalog.display(Core.settings.getString(name, def))));

                row.button(Icon.refresh, Styles.emptyi, LanguageCatalog::refresh)
                    .size(46f).padLeft(4f).tooltip("@fst.languages.refresh");
            }).width(prefWidth()).padTop(4f).left();
            table.row();
        }

        private void showLanguageDialog(SettingsMenuDialog.SettingsTable owner){
            BaseDialog dialog = new BaseDialog(title);
            dialog.addCloseButton();

            dialog.cont.label(() -> Core.bundle.format("fst.languages.status", Core.bundle.get("fst.languages.status." + LanguageCatalog.status(), LanguageCatalog.status())))
                .left().growX().wrap().padBottom(8f).row();

            Seq<LanguageCatalog.Language> all = LanguageCatalog.languages();
            String selected = Core.settings.getString(name, def);
            dialog.cont.pane(list -> {
                list.defaults().growX().height(48f).pad(2f);
                for(LanguageCatalog.Language language : all){
                    String label = (language.code.equalsIgnoreCase(selected) ? "[accent]* []" : "") + language.label();
                    list.button(label, Styles.flatt, () -> {
                        Core.settings.put(name, language.code);
                        owner.rebuild();
                        dialog.hide();
                    }).row();
                }
            }).width(prefWidth()).height(Math.min(Core.graphics.getHeight() / Scl.scl() / 1.25f, 560f)).row();

            dialog.buttons.button("@fst.languages.refresh", Icon.refresh, () -> {
                LanguageCatalog.refresh();
                dialog.hide();
            }).size(210f, 64f);
            dialog.show();
        }
    }

    private static final class CacheControlsSetting extends SettingsMenuDialog.SettingsTable.Setting{
        CacheControlsSetting(){
            super("fst.cacheControls");
        }

        @Override
        public void add(SettingsMenuDialog.SettingsTable table){
            table.table(Tex.button, box -> {
                box.left().margin(10f);
                box.add(title).left().growX().colspan(2).wrap().row();
                box.label(() -> Core.bundle.format("fst.cache.status", TranslatorCache.serverLanguageCount(), TranslatorCache.translationCount()))
                    .left().growX().colspan(2).padTop(8f).wrap().row();
                box.button("@fst.cache.clearServerLanguages", () -> {
                    TranslatorCache.clearServerLanguages();
                    table.rebuild();
                }).height(46f).growX().padTop(8f).padRight(4f);
                box.button("@fst.cache.clearTranslations", () -> {
                    TranslatorCache.clearTranslations();
                    table.rebuild();
                }).height(46f).growX().padTop(8f).padLeft(4f);
            }).width(prefWidth()).padTop(8f).left();
            table.row();
        }
    }

    private static final class TokenStatsSetting extends SettingsMenuDialog.SettingsTable.Setting{
        TokenStatsSetting(){
            super("fst.tokenStats");
        }

        @Override
        public void add(SettingsMenuDialog.SettingsTable table){
            table.table(Tex.button, box -> {
                box.left().margin(10f);
                box.add(title).left().growX().colspan(4).wrap().row();
                box.add(Core.bundle.get("fst.tokens.kind")).left().padTop(8f).growX();
                box.add(Core.bundle.get("fst.tokens.day")).center().padTop(8f).width(95f);
                box.add(Core.bundle.get("fst.tokens.week")).center().padTop(8f).width(95f);
                box.add(Core.bundle.get("fst.tokens.total")).center().padTop(8f).width(95f).row();

                box.add(Core.bundle.get("fst.tokens.input")).left().growX();
                box.label(() -> format(TokenStats.snapshot().dayInput)).center().width(95f);
                box.label(() -> format(TokenStats.snapshot().weekInput)).center().width(95f);
                box.label(() -> format(TokenStats.snapshot().totalInput)).center().width(95f).row();

                box.add(Core.bundle.get("fst.tokens.output")).left().growX();
                box.label(() -> format(TokenStats.snapshot().dayOutput)).center().width(95f);
                box.label(() -> format(TokenStats.snapshot().weekOutput)).center().width(95f);
                box.label(() -> format(TokenStats.snapshot().totalOutput)).center().width(95f).row();
            }).width(prefWidth()).padTop(8f).left();
            table.row();
        }

        private String format(long value){
            return Long.toString(value);
        }
    }

    private static final class MarkerApiKeySetting extends SettingsMenuDialog.SettingsTable.Setting{
        MarkerApiKeySetting(){
            super("fst.marker.apiKey");
        }

        @Override
        public void add(SettingsMenuDialog.SettingsTable table){
            table.table(Tex.button, row -> {
                row.left().margin(10f);
                row.add(title).left().growX().minWidth(0f).wrap();
                row.label(() -> mask(Core.settings.getString(name, ""))).left().width(140f).padLeft(10f);
                row.button("@fst.apiKey.edit", () -> showDialog(table)).width(120f).height(46f).padLeft(8f);
            }).width(prefWidth()).padTop(4f).left();
            table.row();
        }

        private void showDialog(SettingsMenuDialog.SettingsTable owner){
            BaseDialog dialog = new BaseDialog(title);
            dialog.addCloseButton();
            TextField field = dialog.cont.field(Core.settings.getString(name, ""), text -> {}).width(prefWidth() * 0.8f).height(54f).get();
            field.setPasswordMode(true);
            field.setPasswordCharacter('*');

            dialog.buttons.button("@cancel", dialog::hide).size(140f, 54f);
            dialog.buttons.button("@ok", () -> {
                Core.settings.put(name, field.getText().trim());
                owner.rebuild();
                dialog.hide();
            }).size(140f, 54f);
            dialog.show();
            Core.scene.setKeyboardFocus(field);
            field.setCursorPosition(field.getText().length());
        }

        private String mask(String value){
            if(value == null || value.isEmpty()) return Core.bundle.get("fst.apiKey.empty", "(empty)");
            int length = Math.min(value.length(), 12);
            StringBuilder builder = new StringBuilder(length);
            for(int i = 0; i < length; i++){
                builder.append('*');
            }
            return builder.toString();
        }
    }
}
