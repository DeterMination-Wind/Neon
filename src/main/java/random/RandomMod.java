package random;

import arc.Core;
import arc.Events;
import arc.scene.Element;
import arc.scene.ui.Button;
import arc.scene.ui.layout.Table;
import arc.util.Log;
import mindustry.Vars;
import mindustry.core.UI;
import mindustry.game.EventType;
import mindustry.mod.Mod;
import mindustry.ui.MobileButton;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.ContentInfoDialog;
import mindustry.ui.dialogs.DatabaseDialog;
import mindustry.ui.dialogs.RandomContentInfoDialog;
import mindustry.ui.dialogs.RandomDatabaseDialog;
import mindustry.ui.dialogs.SettingsMenuDialog;

import static mindustry.Vars.ui;

/** Client-only entry point for Random's display presentation layer. */
public class RandomMod extends Mod{
    private static final String bundledMenuTableName = "buttons";
    private static final String bundledMenuButtonName = "random-bundled-button";

    /** When true, Random is running as a bundled component inside Neon. */
    public static boolean bekBundled = false;

    private RandomStateStore store;
    private RandomTextResolver textResolver;
    private RandomTextureController textureController;
    private DatabaseDialog originalDatabase;
    private ContentInfoDialog originalContent;
    private String activeCacheKey;
    private String activeContentSignature;
    private boolean activeText;
    private boolean activeTexture;
    private boolean settingsAdded;
    private boolean bundledRandomEnabled;
    private boolean bundledMenuHooked;

    @Override
    public void init(){
        RandomSettings.registerDefaults();
        if(Vars.headless) return;

        store = new RandomStateStore();
        textResolver = new RandomTextResolver(store);
        textureController = new RandomTextureController(store);

        Events.on(EventType.ClientLoadEvent.class, event -> onClientLoad());
        Events.on(EventType.WorldLoadEvent.class, event -> onWorldLoad());
        Events.on(EventType.ResetEvent.class, event -> resetWorld());
        Events.on(EventType.SaveWriteEvent.class, event -> postSaveMigration());
        Events.run(EventType.Trigger.update, textureController::update);
    }

    private void onClientLoad(){
        if(ui == null) return;
        if(originalDatabase == null && ui.database != null && !(ui.database instanceof RandomDatabaseDialog)){
            originalDatabase = ui.database;
        }
        if(originalContent == null && ui.content != null && !(ui.content instanceof RandomContentInfoDialog)){
            originalContent = ui.content;
        }

        if(bekBundled){
            installBundledMenuButtonHook();
            return;
        }

        if(!settingsAdded){
            settingsAdded = true;
            ui.settings.addCategory("@random.settings", mindustry.gen.Icon.refresh, RandomSettings::build);
        }
    }

    /** Enables both Random presentation layers for this process and future worlds. */
    public void enableBundledRandom(){
        if(bekBundled) bundledRandomEnabled = true;
    }

    /** Bundled Random has no independent settings to add to Neon's settings page. */
    public void bekBuildSettings(SettingsMenuDialog.SettingsTable table){
    }

    private void installBundledMenuButtonHook(){
        if(bundledMenuHooked) return;
        bundledMenuHooked = true;

        Events.on(EventType.ResizeEvent.class, event -> {
            if(Core.app != null) Core.app.post(this::appendBundledMenuButton);
        });

        appendBundledMenuButton();
        if(Core.app != null) Core.app.post(this::appendBundledMenuButton);
    }

    private void appendBundledMenuButton(){
        if(!bekBundled || Vars.headless || Core.scene == null) return;

        Element found = Core.scene.find(element -> element instanceof Table && bundledMenuTableName.equals(element.name));
        if(!(found instanceof Table)) return;

        Table buttons = (Table)found;
        for(Element child : buttons.getChildren()){
            if(bundledMenuButtonName.equals(child.name)) return;
        }

        Runnable callback = () -> {
            enableBundledRandom();
            if(ui != null) ui.showInfo("@random.bundled.hint");
        };

        if(Vars.mobile){
            buttons.row();
            MobileButton button = new MobileButton(mindustry.gen.Icon.refresh, "@random.bundled.button", callback);
            button.name = bundledMenuButtonName;
            buttons.add(button);
        }else{
            Button button = buttons.button("@random.bundled.button", mindustry.gen.Icon.refresh, Styles.flatToggleMenut, callback)
                .marginLeft(11f).get();
            button.name = bundledMenuButtonName;
            buttons.row();
        }
    }

    private void onWorldLoad(){
        if(store == null || textResolver == null || textureController == null) return;

        activeText = bekBundled ? bundledRandomEnabled : RandomSettings.textEnabled();
        activeTexture = bekBundled ? bundledRandomEnabled : RandomSettings.textureEnabled();
        String identity = store.beginWorld();
        activeContentSignature = RandomStateStore.contentSignature();
        activeCacheKey = store.cacheKey(identity, activeContentSignature, activeText, activeTexture);

        textResolver.begin(activeCacheKey, activeText);
        if(activeText && !installTextDialogs()){
            Log.warn("Random text presentation disabled because another mod owns the database or content dialog.");
            activeText = false;
            textResolver.reset();
        }

        textureController.begin(activeCacheKey, activeTexture);
    }

    private boolean installTextDialogs(){
        if(ui == null || originalDatabase == null || originalContent == null) return false;
        if(ui.database instanceof RandomDatabaseDialog && ui.content instanceof RandomContentInfoDialog) return true;

        // Do not overwrite another mod's live wrapper. Only compose with the original vanilla fields.
        if(ui.database != originalDatabase || ui.content != originalContent){
            return false;
        }

        if(!isVanillaDialog(originalDatabase, DatabaseDialog.class) || !isVanillaDialog(originalContent, ContentInfoDialog.class)){
            return false;
        }

        ui.database = new RandomDatabaseDialog(textResolver);
        ui.content = new RandomContentInfoDialog(textResolver);
        return true;
    }

    private static boolean isVanillaDialog(Object value, Class<?> type){
        return value != null && value.getClass() == type;
    }

    private void resetWorld(){
        if(textureController != null) textureController.reset();
        if(textResolver != null) textResolver.reset();
        restoreTextDialogs();
        activeCacheKey = null;
        activeContentSignature = null;
        activeText = false;
        activeTexture = false;
    }

    private void restoreTextDialogs(){
        if(ui == null) return;
        if(ui.database instanceof RandomDatabaseDialog && originalDatabase != null) ui.database = originalDatabase;
        if(ui.content instanceof RandomContentInfoDialog && originalContent != null) ui.content = originalContent;
    }

    private void postSaveMigration(){
        if(store == null || activeCacheKey == null || (!activeText && !activeTexture) || Core.app == null) return;
        Core.app.post(() -> {
            String saved = store.savedIdentity();
            if(saved == null || activeContentSignature == null) return;
            String migrated = store.cacheKey(saved, activeContentSignature, activeText, activeTexture);
            store.migrate(activeCacheKey, migrated, activeText, activeTexture);
            activeCacheKey = migrated;
        });
    }

    /** Used by tests and diagnostics without exposing a public mod API. */
    RandomTextResolver textResolver(){
        return textResolver;
    }

    /** Used by tests and diagnostics without exposing a public mod API. */
    RandomTextureController textureController(){
        return textureController;
    }
}
