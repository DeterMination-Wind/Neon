package foreignservertranslator;

import arc.Core;
import arc.scene.ui.TextField;
import arc.scene.ui.TextButton;
import arc.scene.ui.layout.Scl;
import arc.struct.Seq;
import bektools.ui.RbmStyle;
import bektools.ui.VscodeSettingsStyle;
import mindustry.gen.Icon;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.ui.dialogs.SettingsMenuDialog;

public final class TranslatorSettings{
    private TranslatorSettings(){
    }

    public static void build(SettingsMenuDialog.SettingsTable table){
        if(!ForeignServerTranslatorMod.bekBundled){
            table.pref(new RbmStyle.HeaderSetting("Foreign Server Translator", Icon.chat));
        }
        table.pref(new RbmStyle.SubHeaderSetting("Chat translation"));
        table.pref(new RbmStyle.IconCheckSetting(TranslatorFeature.openAiEnabledKey, false, Icon.chatSmall, null));
        table.pref(new RbmStyle.IconTextSetting(TranslatorFeature.baseUrlKey, "", Icon.linkSmall, null));
        table.pref(new ApiKeySetting());
        table.pref(new RbmStyle.IconTextSetting(TranslatorFeature.modelKey, "gpt-4o-mini", Icon.settingsSmall, null));
        table.pref(new RbmStyle.IconSliderSetting(TranslatorFeature.contextMessagesKey, 3, 0, 10, 1, Icon.listSmall, value -> value + "", null));
        table.pref(new RbmStyle.IconCheckSetting(TranslatorFeature.bundleHintsKey, false, Icon.infoSmall, null));
        table.pref(new RbmStyle.IconCheckSetting(TranslatorFeature.fullBundleContextKey, false, Icon.bookSmall, null));
        table.pref(new LanguageSetting(TranslatorFeature.incomingLanguageKey, "zh-Hans"));
        table.pref(new LanguageSetting(TranslatorFeature.outgoingLanguageKey, "en"));
        table.pref(new CacheControlsSetting());
        table.pref(new TokenStatsSetting());

        table.pref(new RbmStyle.HeaderSetting(Core.bundle.get("fst.markerSettings", "Marker Translation"), Icon.map));
        table.pref(new RbmStyle.IconCheckSetting("fst.marker.openAiEnabled", false, Icon.mapSmall, null));
        table.pref(new RbmStyle.IconTextSetting("fst.marker.baseUrl", "", Icon.linkSmall, null));
        table.pref(new MarkerApiKeySetting());
        table.pref(new RbmStyle.IconTextSetting("fst.marker.model", "gpt-4o-mini", Icon.settingsSmall, null));
    }

    private static float prefWidth(){
        return RbmStyle.rowWidth();
    }

    private static final class ApiKeySetting extends SettingsMenuDialog.SettingsTable.Setting{
        ApiKeySetting(){
            super(TranslatorFeature.apiKeyKey);
        }

        @Override
        public void add(SettingsMenuDialog.SettingsTable table){
            table.table(VscodeSettingsStyle.cardBackground(), row -> {
                row.left().margin(10f);
                row.add(title).left().growX().minWidth(0f).wrap();
                row.label(() -> mask(Core.settings.getString(name, ""))).left().width(140f).padLeft(10f);
                row.button("@fst.apiKey.edit", Styles.flatt, () -> showDialog(table)).width(120f).height(RbmStyle.buttonHeight()).padLeft(8f);
            }).width(prefWidth()).padTop(6f).left();
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
            table.table(VscodeSettingsStyle.cardBackground(), row -> {
                row.left().margin(10f);
                row.add(title).left().growX().minWidth(0f).wrap();

                TextButton select = row.button("", Styles.flatt, () -> showLanguageDialog(table))
                    .width(300f).height(RbmStyle.buttonHeight()).padLeft(10f).get();
                select.update(() -> select.setText(LanguageCatalog.display(Core.settings.getString(name, def))));

                row.button(Icon.refresh, Styles.emptyi, LanguageCatalog::refresh)
                    .size(46f).padLeft(4f).tooltip("@fst.languages.refresh");
            }).width(prefWidth()).padTop(6f).left();
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
            table.table(VscodeSettingsStyle.cardBackground(), box -> {
                box.left().margin(10f);
                box.add(title).left().growX().colspan(2).wrap().row();
                box.label(() -> Core.bundle.format("fst.cache.status", TranslatorCache.serverLanguageCount(), TranslatorCache.translationCount()))
                    .left().growX().colspan(2).padTop(8f).wrap().row();
                box.button("@fst.cache.clearServerLanguages", Styles.flatt, () -> {
                    TranslatorCache.clearServerLanguages();
                    table.rebuild();
                }).height(RbmStyle.buttonHeight()).growX().padTop(8f).padRight(4f);
                box.button("@fst.cache.clearTranslations", Styles.flatt, () -> {
                    TranslatorCache.clearTranslations();
                    table.rebuild();
                }).height(RbmStyle.buttonHeight()).growX().padTop(8f).padLeft(4f);
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
            table.table(VscodeSettingsStyle.cardBackground(), box -> {
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
            table.table(VscodeSettingsStyle.cardBackground(), row -> {
                row.left().margin(10f);
                row.add(title).left().growX().minWidth(0f).wrap();
                row.label(() -> mask(Core.settings.getString(name, ""))).left().width(140f).padLeft(10f);
                row.button("@fst.apiKey.edit", Styles.flatt, () -> showDialog(table)).width(120f).height(RbmStyle.buttonHeight()).padLeft(8f);
            }).width(prefWidth()).padTop(6f).left();
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
