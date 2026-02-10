package radialbuildmenu;

import arc.Core;
import arc.Events;
import arc.func.Prov;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.input.KeyBind;
import arc.input.KeyCode;
import arc.math.Mathf;
import arc.scene.Element;
import arc.scene.event.InputEvent;
import arc.scene.event.InputListener;
import arc.scene.event.Touchable;
import arc.scene.ui.Dialog;
import arc.scene.ui.Image;
import arc.scene.ui.TextArea;
import arc.scene.ui.TextField;
import arc.scene.style.TextureRegionDrawable;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.TextButton;
import arc.scene.ui.layout.Table;
import arc.scene.ui.layout.Scl;
import arc.util.Align;
import arc.util.Log;
import arc.util.Scaling;
import arc.util.serialization.Jval.Jtype;
import arc.util.Strings;
import arc.util.Time;
import arc.util.serialization.Jval;
import arc.util.serialization.Jval.Jformat;
import mindustry.game.EventType.ClientLoadEvent;
import mindustry.game.EventType.WorldLoadEvent;
import mindustry.gen.Groups;
import mindustry.gen.Unit;
import mindustry.gen.Tex;
import mindustry.graphics.Pal;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.ui.dialogs.SettingsMenuDialog;
import mindustry.type.Item;
import mindustry.type.Planet;
import mindustry.type.UnitType;
import mindustry.world.blocks.storage.CoreBlock;
import mindustry.world.Block;
import mindustry.world.meta.BuildVisibility;

import java.lang.reflect.Method;
import java.util.Locale;

import static mindustry.Vars.player;
import static mindustry.Vars.state;
import static mindustry.Vars.ui;
import static mindustry.Vars.content;
import static mindustry.Vars.mobile;
import static mindustry.Vars.control;

public class RadialBuildMenuMod extends mindustry.mod.Mod{
    /** When true, this mod is running as a bundled component inside Neon. */
    public static boolean bekBundled = false;


    private static final String overlayName = "rbm-overlay";
    private static final String mobileToggleName = "rbm-mobile-toggle";
    private static final String mobileWindowName = "rbm-mobile";

    private static final int slotsPerRing = 8;
    private static final int maxSlots = 16;

    private static final String planetErekir = "erekir";
    private static final String planetSerpulo = "serpulo";
    private static final String planetSun = "sun";

    private static final String keyEnabled = "rbm-enabled";
    private static final String keyHudScale = "rbm-hudscale";
    private static final String keyHudAlpha = "rbm-hudalpha";
    private static final String keyInnerRadius = "rbm-inner-radius";
    private static final String keyOuterRadius = "rbm-outer-radius";
    private static final String keyIconScale = "rbm-icon-scale";
    private static final String keyBackStrength = "rbm-back-strength";
    private static final String keyRingAlpha = "rbm-ring-alpha";
    private static final String keyRingStroke = "rbm-ring-stroke";
    private static final String keyHudColor = "rbm-hudcolor";
    private static final String keyCenterScreen = "rbm-center-screen";
    static final String keyProMode = "rbm-pro-mode";
    private static final String keyTimeMinutes = "rbm-time-minutes";
    private static final String keyShowEmptySlots = "rbm-show-empty-slots";

    static final String keyToggleSlotGroupsEnabled = "rbm-toggle-slot-groups-enabled";
    private static final String keyToggleSlotGroupState = "rbm-toggle-slot-groups-state";
    private static final String keyToggleSlotGroupASlotPrefix = "rbm-toggle-slotgroup-a-";
    private static final String keyToggleSlotGroupBSlotPrefix = "rbm-toggle-slotgroup-b-";

    private static final String keyHoverUpdateFrames = "rbm-hover-update-frames";
    private static final String keyHoverPadding = "rbm-hover-padding";
    private static final String keyDeadzoneScale = "rbm-deadzone-scale";
    private static final String keyDirectionSelect = "rbm-direction-select";

    private static final String keyCondEnabled = "rbm-cond-enabled";
    private static final String keyCondInitialExpr = "rbm-cond-initial-expr";
    private static final String keyCondAfterEnabled = "rbm-cond-after-enabled";
    private static final String keyCondAfterExpr = "rbm-cond-after-expr";

    private static final String keyCondInitialSlotPrefix = "rbm-cond-initial-slot-";
    private static final String keyCondAfterSlotPrefix = "rbm-cond-after-slot-";

    private static final String keySlotPrefix = "rbm-slot-";
    private static final String keyTimeSlotPrefix = "rbm-time-slot-";
    private static final String keyTimeErekirSlotPrefix = "rbm-time-erekir-slot-";
    private static final String keyTimeSerpuloSlotPrefix = "rbm-time-serpulo-slot-";
    private static final String keyTimeSunSlotPrefix = "rbm-time-sun-slot-";

    private static final String keyPlanetErekirSlotPrefix = "rbm-planet-erekir-slot-";
    private static final String keyPlanetSerpuloSlotPrefix = "rbm-planet-serpulo-slot-";
    private static final String keyPlanetSunSlotPrefix = "rbm-planet-sun-slot-";

    private static final String keyPlanetErekirEnabled = "rbm-planet-erekir-enabled";
    private static final String keyPlanetSerpuloEnabled = "rbm-planet-serpulo-enabled";
    private static final String keyPlanetSunEnabled = "rbm-planet-sun-enabled";

    private static final String[] defaultSlotNames = {
        "conveyor",
        "router",
        "junction",
        "sorter",
        "overflow-gate",
        "underflow-gate",
        "bridge-conveyor",
        "power-node"
    };

    public static final KeyBind radialMenu = KeyBind.add("rbm_radial_menu", KeyCode.unset, "blocks");
    public static final KeyBind toggleSlotGroup = KeyBind.add("rbm_toggle_slot_group", KeyCode.unset, "blocks");

    private final MindustryXOverlayUI xOverlayUi = new MindustryXOverlayUI();
    private Object xMobileToggleWindow;

    private boolean condAfterLatched;
    private boolean condInitActive;
    private boolean condAfterActive;

    private String condInitSrc;
    private Expr condInitExpr;
    private String condAfterSrc;
    private Expr condAfterExpr;

    private float condLastEval = -9999f;
    private static final float condEvalIntervalFrames = 10f;

    public RadialBuildMenuMod(){
        Events.on(ClientLoadEvent.class, e -> {
            ensureDefaults();
            registerSettings();
            Time.runTask(10f, this::ensureOverlayAttached);
            Time.runTask(10f, this::ensureMobileToggleAttached);
            GithubUpdateCheck.checkOnce();
        });

        Events.on(WorldLoadEvent.class, e -> {
            resetMatchState();
            Time.runTask(10f, this::ensureOverlayAttached);
            Time.runTask(10f, this::ensureMobileToggleAttached);
        });
    }

    private void ensureDefaults(){
        GithubUpdateCheck.applyDefaults();
        Core.settings.defaults(keyEnabled, true);
        Core.settings.defaults(keyHudScale, 100);
        Core.settings.defaults(keyHudAlpha, 100);
        Core.settings.defaults(keyInnerRadius, 80);
        Core.settings.defaults(keyOuterRadius, 140);
        Core.settings.defaults(keyIconScale, 100);
        Core.settings.defaults(keyBackStrength, 22);
        Core.settings.defaults(keyRingAlpha, 65);
        Core.settings.defaults(keyRingStroke, 2);
        Core.settings.defaults(keyHudColor, defaultHudColorHex());
        Core.settings.defaults(keyCenterScreen, false);
        Core.settings.defaults(keyProMode, false);
        Core.settings.defaults(keyTimeMinutes, 0);
        Core.settings.defaults(keyShowEmptySlots, false);

        Core.settings.defaults(keyToggleSlotGroupsEnabled, false);
        Core.settings.defaults(keyToggleSlotGroupState, 0);

        Core.settings.defaults(keyHoverUpdateFrames, 0);
        Core.settings.defaults(keyHoverPadding, 12);
        Core.settings.defaults(keyDeadzoneScale, 35);
        Core.settings.defaults(keyDirectionSelect, true);

        Core.settings.defaults(keyCondEnabled, false);
        Core.settings.defaults(keyCondInitialExpr, "");
        Core.settings.defaults(keyCondAfterEnabled, false);
        Core.settings.defaults(keyCondAfterExpr, "");
        for(int i = 0; i < maxSlots; i++){
            String def = defaultSlotName(i);
            Core.settings.defaults(keySlotPrefix + i, def);
            // Time profile is a separate slot set; default it to the standard defaults.
            Core.settings.defaults(keyTimeSlotPrefix + i, def);
            // Slot-group toggle profiles. Group A starts from the standard defaults; Group B is empty.
            Core.settings.defaults(keyToggleSlotGroupASlotPrefix + i, def);
            Core.settings.defaults(keyToggleSlotGroupBSlotPrefix + i, "");
            // planet-specific overrides are empty by default
            Core.settings.defaults(keyTimeErekirSlotPrefix + i, "");
            Core.settings.defaults(keyTimeSerpuloSlotPrefix + i, "");
            Core.settings.defaults(keyTimeSunSlotPrefix + i, "");
            Core.settings.defaults(keyPlanetErekirSlotPrefix + i, "");
            Core.settings.defaults(keyPlanetSerpuloSlotPrefix + i, "");
            Core.settings.defaults(keyPlanetSunSlotPrefix + i, "");

            Core.settings.defaults(keyCondInitialSlotPrefix + i, "");
            Core.settings.defaults(keyCondAfterSlotPrefix + i, "");
        }

        Core.settings.defaults(keyPlanetErekirEnabled, true);
        Core.settings.defaults(keyPlanetSerpuloEnabled, true);
        Core.settings.defaults(keyPlanetSunEnabled, true);
    }

    private void registerSettings(){
        if(ui == null || ui.settings == null) return;
        if(bekBundled) return;


        ui.settings.addCategory("@rbm.category", this::bekBuildSettings);
    }
    /** Populates a {@link mindustry.ui.dialogs.SettingsMenuDialog.SettingsTable} with this mod's settings. */
    public void bekBuildSettings(SettingsMenuDialog.SettingsTable table){
            boolean toggleEnabled = Core.settings.getBool(keyToggleSlotGroupsEnabled, false);

            table.checkPref(keyEnabled, true);
            table.pref(new HotkeySetting());

            table.checkPref(keyToggleSlotGroupsEnabled, false);
            table.pref(new ToggleSlotGroupHotkeySetting());
            table.pref(new SlotGroupsButtonSetting(RadialBuildMenuMod.this));

            table.sliderPref(keyHudScale, 100, 50, 200, 5, v -> v + "%");
            table.sliderPref(keyHudAlpha, 100, 0, 100, 5, v -> v + "%");
            table.sliderPref(keyInnerRadius, 80, 40, 200, 5, v -> v + "px");
            table.sliderPref(keyOuterRadius, 140, 60, 360, 5, v -> v + "px");
            table.pref(new HudColorSetting());
            table.checkPref(keyCenterScreen, false);
            table.checkPref(keyShowEmptySlots, false);
            table.checkPref(keyProMode, false);
            table.pref(new AdvancedButtonSetting(RadialBuildMenuMod.this));

            if(!toggleEnabled){
                for(int i = 0; i < maxSlots; i++) table.pref(new SlotSetting(i, keySlotPrefix, "rbm.setting.slot"));

                table.pref(new TimeMinutesSetting());
                for(int i = 0; i < maxSlots; i++) table.pref(new SlotSetting(i, keyTimeSlotPrefix, "rbm.setting.timeslot"));
            }

            table.pref(new IoSetting());

            table.checkPref(GithubUpdateCheck.enabledKey(), true);
            table.checkPref(GithubUpdateCheck.showDialogKey(), true);
        
    }


    void showSlotGroupsDialog(){
        BaseDialog dialog = new BaseDialog("@rbm.slotgroups.title");
        dialog.addCloseButton();

        SettingsMenuDialog.SettingsTable groups = new SettingsMenuDialog.SettingsTable();
        groups.pref(new SubHeaderSetting("@rbm.slotgroup.a"));
        for(int i = 0; i < maxSlots; i++) groups.pref(new SlotSetting(i, keyToggleSlotGroupASlotPrefix, "rbm.setting.slot"));
        groups.pref(new SubHeaderSetting("@rbm.slotgroup.b"));
        for(int i = 0; i < maxSlots; i++) groups.pref(new SlotSetting(i, keyToggleSlotGroupBSlotPrefix, "rbm.setting.slot"));

        ScrollPane pane = new ScrollPane(groups);
        pane.setFadeScrollBars(false);
        pane.setScrollingDisabled(true, false);
        pane.setOverscroll(false, false);
        dialog.cont.table(t -> {
            t.center();
            t.add(pane).width(prefWidth()).growY().minHeight(380f);
        }).grow();
        dialog.show();
    }

    void showAdvancedDialog(){
        BaseDialog dialog = new BaseDialog("@rbm.advanced.title");
        dialog.addCloseButton();

        SettingsMenuDialog.SettingsTable adv = new SettingsMenuDialog.SettingsTable();

        adv.pref(new CollapsiblePlanetSetting(
            Core.bundle.get("rbm.advanced.planet.erekir"),
            mindustry.gen.Icon.modeAttack,
            "rbm-adv-erekir-open",
            t -> {
                t.checkPref(keyPlanetErekirEnabled, true);
                t.pref(new SubHeaderSetting("@rbm.advanced.initial"));
                for(int i = 0; i < maxSlots; i++) t.pref(new SlotSetting(i, keyPlanetErekirSlotPrefix, "rbm.setting.slot"));
                t.pref(new SubHeaderSetting("@rbm.advanced.time"));
                for(int i = 0; i < maxSlots; i++) t.pref(new SlotSetting(i, keyTimeErekirSlotPrefix, "rbm.setting.timeslot"));
            },
            this::readHudColor
        ));

        adv.pref(new CollapsiblePlanetSetting(
            Core.bundle.get("rbm.advanced.planet.serpulo"),
            mindustry.gen.Icon.modeAttack,
            "rbm-adv-serpulo-open",
            t -> {
                t.checkPref(keyPlanetSerpuloEnabled, true);
                t.pref(new SubHeaderSetting("@rbm.advanced.initial"));
                for(int i = 0; i < maxSlots; i++) t.pref(new SlotSetting(i, keyPlanetSerpuloSlotPrefix, "rbm.setting.slot"));
                t.pref(new SubHeaderSetting("@rbm.advanced.time"));
                for(int i = 0; i < maxSlots; i++) t.pref(new SlotSetting(i, keyTimeSerpuloSlotPrefix, "rbm.setting.timeslot"));
            },
            this::readHudColor
        ));

        adv.pref(new CollapsiblePlanetSetting(
            Core.bundle.get("rbm.advanced.planet.sun"),
            mindustry.gen.Icon.modeAttack,
            "rbm-adv-sun-open",
            t -> {
                t.checkPref(keyPlanetSunEnabled, true);
                t.pref(new SubHeaderSetting("@rbm.advanced.initial"));
                for(int i = 0; i < maxSlots; i++) t.pref(new SlotSetting(i, keyPlanetSunSlotPrefix, "rbm.setting.slot"));
                t.pref(new SubHeaderSetting("@rbm.advanced.time"));
                for(int i = 0; i < maxSlots; i++) t.pref(new SlotSetting(i, keyTimeSunSlotPrefix, "rbm.setting.timeslot"));
            },
            this::readHudColor
        ));

        adv.sliderPref(keyIconScale, 100, 50, 200, 5, v -> v + "%");
        adv.sliderPref(keyBackStrength, 22, 0, 60, 2, v -> v + "%");
        adv.sliderPref(keyRingAlpha, 65, 0, 100, 5, v -> v + "%");
        adv.sliderPref(keyRingStroke, 2, 1, 6, 1, v -> v + "px");

        adv.checkPref(keyDirectionSelect, true);
        adv.sliderPref(keyDeadzoneScale, 35, 0, 100, 5, v -> v + "%");
        adv.sliderPref(keyHoverPadding, 12, 0, 30, 1, v -> v + "px");
        adv.sliderPref(keyHoverUpdateFrames, 0, 0, 10, 1, v -> v == 0 ? Core.bundle.get("rbm.advanced.everyframe") : v + "f");

        adv.pref(new ConditionalSwitchSetting(this));

        ScrollPane pane = new ScrollPane(adv);
        pane.setFadeScrollBars(false);
        pane.setScrollingDisabled(true, false);
        pane.setOverscroll(false, false);
        dialog.cont.table(t -> {
            t.center();
            t.add(pane).width(prefWidth()).growY().minHeight(380f);
        }).grow();
        dialog.show();
    }

    private void resetMatchState(){
        condAfterLatched = false;
        condInitActive = false;
        condAfterActive = false;
        condLastEval = -9999f;
    }

    private class HeaderSetting extends SettingsMenuDialog.SettingsTable.Setting{
        private final arc.scene.style.Drawable icon;

        public HeaderSetting(String title, arc.scene.style.Drawable icon){
            super("rbm-header");
            this.title = title;
            this.icon = icon;
        }

        @Override
        public void add(SettingsMenuDialog.SettingsTable table){
            float width = prefWidth();
            table.row();
            table.table(Styles.black3, t -> {
                t.left().margin(8f);
                if(icon != null){
                    t.image(icon).size(18f).padRight(6f);
                }
                t.add(title).color(Pal.accent).left().growX().minWidth(0f).wrap();
            }).width(width).padTop(10f).padBottom(5f).left();
            table.row();
            table.image(Tex.whiteui).color(Pal.accent).height(3f).width(width).padBottom(10f).left();
            table.row();
        }
    }

    // SubHeaderSetting / AdvancedButtonSetting extracted into `RbmSettingsExtracted`.

    private class HotkeySetting extends SettingsMenuDialog.SettingsTable.Setting{
        public HotkeySetting(){
            super("rbm-hotkey");
            title = Core.bundle.get("rbm.setting.hotkey");
        }

        @Override
        public void add(SettingsMenuDialog.SettingsTable table){
            float prefWidth = prefWidth();
            table.table(Tex.button, t -> {
                t.left().margin(10f);

                t.image(mindustry.gen.Icon.settings).size(20f).padRight(8f);
                t.add(title).left().growX().minWidth(0f).wrap();
                t.label(() -> radialMenu.value.key.toString()).color(Pal.accent).padLeft(10f);
                t.button("@rbm.setting.opencontrols", Styles.flatt, () -> ui.controls.show())
                    .width(190f)
                    .height(40f)
                    .padLeft(10f);
            }).width(prefWidth).padTop(6f);
            table.row();
        }
    }

    private class ToggleSlotGroupHotkeySetting extends SettingsMenuDialog.SettingsTable.Setting{
        public ToggleSlotGroupHotkeySetting(){
            super("rbm-toggle-slot-group-hotkey");
            title = Core.bundle.get("rbm.setting.toggleHotkey");
        }

        @Override
        public void add(SettingsMenuDialog.SettingsTable table){
            float prefWidth = prefWidth();
            table.table(Tex.button, t -> {
                t.left().margin(10f);

                t.image(mindustry.gen.Icon.refresh).size(20f).padRight(8f);
                t.add(title).left().growX().minWidth(0f).wrap();
                t.label(() -> toggleSlotGroup.value.key.toString()).color(Pal.accent).padLeft(10f);
                t.label(() -> {
                    int g = Mathf.clamp(Core.settings.getInt(keyToggleSlotGroupState, 0), 0, 1);
                    return Core.bundle.get(g == 0 ? "rbm.slotgroup.a" : "rbm.slotgroup.b");
                }).color(Pal.accent).padLeft(8f);
                t.button("@rbm.setting.opencontrols", Styles.flatt, () -> ui.controls.show())
                    .width(190f)
                    .height(40f)
                    .padLeft(10f);
            }).width(prefWidth).padTop(6f);
            table.row();
        }
    }

    private class SlotGroupsButtonSetting extends SettingsMenuDialog.SettingsTable.Setting{
        private final RadialBuildMenuMod mod;

        public SlotGroupsButtonSetting(RadialBuildMenuMod mod){
            super("rbm-slot-groups-open");
            this.mod = mod;
            title = Core.bundle.get("rbm.setting.slotgroups");
        }

        @Override
        public void add(SettingsMenuDialog.SettingsTable table){
            float prefWidth = prefWidth();
            table.table(Tex.button, t -> {
                t.left().margin(10f);

                t.image(mindustry.gen.Icon.list).size(20f).padRight(8f);
                t.add(title).left().growX().minWidth(0f).wrap();
                t.button("@rbm.slotgroups.open", Styles.flatt, mod::showSlotGroupsDialog)
                    .width(190f)
                    .height(40f)
                    .padLeft(10f);
            }).width(prefWidth).padTop(6f);
            table.row();
        }
    }

    private class SlotSetting extends SettingsMenuDialog.SettingsTable.Setting{
        private final int slot;
        private final String prefix;
        private final String titleKey;

        public SlotSetting(int slot, String prefix, String titleKey){
            super(prefix + slot);
            this.slot = slot;
            this.prefix = prefix;
            this.titleKey = titleKey;
            title = Core.bundle.format(titleKey, slot + 1);
        }

        @Override
        public void add(SettingsMenuDialog.SettingsTable table){
            float prefWidth = prefWidth();
            table.table(Tex.button, t -> {
                 t.left().margin(10f);
  
                 t.add(title).width(160f).left().wrap();
                 t.table(info -> {
                     info.left();
  
                     Image icon = info.image(Tex.clear).size(32f).padRight(8f).get();
                     icon.setScaling(Scaling.fit);

                    info.labelWrap(() -> {
                        Block block = slotBlock(prefix, slot);
                        return block == null ? Core.bundle.get("rbm.setting.none") : block.localizedName;
                    }).left().growX().fillX().minWidth(0f);

                    final Block[] lastBlock = {null};
                    info.update(() -> {
                        Block block = slotBlock(prefix, slot);
                        if(block == lastBlock[0]) return;
                        lastBlock[0] = block;
                        icon.setDrawable(block == null ? Tex.clear : new TextureRegionDrawable(block.uiIcon));
                    });
                }).left().growX().fillX().minWidth(0f);

                t.button("@rbm.setting.set", Styles.flatt, () -> showBlockSelectDialog(block -> {
                    Core.settings.put(name, block == null ? "" : block.name);
                })).width(140f).height(40f).padLeft(8f);
            }).width(prefWidth).padTop(6f);
            table.row();
        }
    }

    private class HudColorSetting extends SettingsMenuDialog.SettingsTable.Setting{
        public HudColorSetting(){
            super(keyHudColor);
            title = Core.bundle.get("setting.rbm-hudcolor.name");
            description = Core.bundle.getOrNull("setting.rbm-hudcolor.description");
        }

        @Override
        public void add(SettingsMenuDialog.SettingsTable table){
            Table root = table.table(Tex.button, t -> {
                t.left().margin(10f);

                t.table(top -> {
                    top.left();
                    top.image(mindustry.gen.Icon.pencil).size(20f).padRight(8f);
                    top.add(title).left().growX().minWidth(0f).wrap();
                }).growX().fillX();
                t.row();

                Image preview = new Image(Tex.whiteui);
                preview.setScaling(Scaling.stretch);
                preview.setColor(readHudColor());

                TextField field = new TextField(Core.settings.getString(keyHudColor, defaultHudColorHex()));
                field.setMessageText(defaultHudColorHex());
                field.setFilter((text, c) -> isHexChar(c) || c == '#');

                Runnable applyField = () -> {
                    String hex = normalizeHex(field.getText());
                    Core.settings.put(keyHudColor, hex);
                    preview.setColor(readHudColor());
                };

                field.changed(applyField);

                field.update(() -> {
                    if(Core.scene.getKeyboardFocus() == field) return;
                    String value = Core.settings.getString(keyHudColor, defaultHudColorHex());
                    if(value == null) value = defaultHudColorHex();
                    if(!value.equals(field.getText())){
                        field.setText(value);
                    }
                    preview.setColor(readHudColor());
                });

                t.table(row -> {
                    row.left();
                    row.add(preview).size(22f).padRight(8f);
                    row.add(field).minWidth(160f).growX().maxWidth(320f);
                }).growX().fillX().minWidth(0f).padTop(6f);
                t.row();

                t.table(btns -> {
                    btns.left();
                    btns.button("@rbm.color.pick", Styles.flatt, () -> showHudColorPicker(color -> {
                        // picker returns color in #RRGGBB or #RRGGBBAA
                        String hex = color.toString();
                        if(hex.length() > 6) hex = hex.substring(0, 6);
                        Core.settings.put(keyHudColor, normalizeHex(hex));
                        preview.setColor(readHudColor());
                    })).minWidth(140f).height(40f);

                    btns.button("@rbm.color.reset", Styles.flatt, () -> {
                        Core.settings.put(keyHudColor, defaultHudColorHex());
                        field.setText(Core.settings.getString(keyHudColor, defaultHudColorHex()));
                        preview.setColor(readHudColor());
                    }).minWidth(140f).height(40f).padLeft(8f);
                }).growX().fillX().minWidth(0f).padTop(6f);
            }).width(prefWidth()).padTop(6f).get();
            addDesc(root);
            table.row();
        }

        private boolean isHexChar(char c){
            return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
        }
    }

    private static class CollapsiblePlanetSetting extends SettingsMenuDialog.SettingsTable.Setting{
        private final String titleText;
        private final arc.scene.style.Drawable icon;
        private final String openKey;
        private final arc.func.Cons<SettingsMenuDialog.SettingsTable> builder;
        private final arc.func.Prov<Color> accent;

        public CollapsiblePlanetSetting(String titleText, arc.scene.style.Drawable icon, String openKey, arc.func.Cons<SettingsMenuDialog.SettingsTable> builder, arc.func.Prov<Color> accent){
            super("rbm-adv-collapsible");
            this.titleText = titleText;
            this.icon = icon;
            this.openKey = openKey;
            this.builder = builder;
            this.accent = accent;
        }

        @Override
        public void add(SettingsMenuDialog.SettingsTable table){
            boolean startOpen = Core.settings.getBool(openKey, true);
            final boolean[] open = {startOpen};

            float width = prefWidth();
            final Image[] arrow = {null};
            Table header = table.table(Tex.button, t -> {
                t.left().margin(10f);
                if(icon != null) t.image(icon).size(18f).padRight(6f);
                t.add(titleText).color(Pal.accent).left().growX().minWidth(0f).wrap();
                arrow[0] = t.image(startOpen ? mindustry.gen.Icon.downOpen : mindustry.gen.Icon.rightOpen).size(18f).padLeft(6f).get();
            }).width(width).padTop(10f).get();
            table.row();

            SettingsMenuDialog.SettingsTable inner = new SettingsMenuDialog.SettingsTable();
            builder.get(inner);

            arc.scene.ui.layout.Collapser collapser = new arc.scene.ui.layout.Collapser(inner, true);
            collapser.setDuration(0.12f);
            collapser.setCollapsed(!startOpen, false);

            table.table(Tex.button, t -> {
                t.left().top().margin(10f);
                t.add(collapser).growX().minWidth(0f);
            }).width(width).padTop(6f);
            table.row();

            Runnable toggle = () -> {
                open[0] = !open[0];
                Core.settings.put(openKey, open[0]);
                if(arrow[0] != null) arrow[0].setDrawable(open[0] ? mindustry.gen.Icon.downOpen : mindustry.gen.Icon.rightOpen);
                collapser.toggle();
            };
            header.clicked(toggle);
        }
    }

    private void showHudColorPicker(arc.func.Cons<Color> cons){
        if(ui == null || ui.picker == null){
            BaseDialog dialog = new BaseDialog("@pickcolor");
            dialog.addCloseButton();
            dialog.show();
            return;
        }

        Color color = readHudColor();
        color.a = 1f;
        ui.picker.show(color, false, picked -> {
            if(picked == null) return;
            cons.get(picked);
        });
    }

    private class TimeMinutesSetting extends SettingsMenuDialog.SettingsTable.Setting{
        public TimeMinutesSetting(){
            super(keyTimeMinutes);
            title = Core.bundle.get("setting.rbm-time-minutes.name");
        }

        @Override
        public void add(SettingsMenuDialog.SettingsTable table){
            TextField field = new TextField();
            field.setMessageText("0");
            field.setFilter((text, c) -> Character.isDigit(c));

            field.changed(() -> {
                int minutes = Strings.parseInt(field.getText(), 0);
                if(minutes < 0) minutes = 0;
                Core.settings.put(name, minutes);
            });

            field.update(() -> {
                if(Core.scene.getKeyboardFocus() == field) return;
                String value = Integer.toString(Core.settings.getInt(name, 0));
                if(!value.equals(field.getText())){
                    field.setText(value);
                }
            });

            float prefWidth = prefWidth();
            table.table(Tex.button, t -> {
                t.left().margin(10f);
                t.image(mindustry.gen.Icon.refresh).size(20f).padRight(8f);
                t.add(title, Styles.outlineLabel).left().growX().minWidth(0f).wrap();
                t.add(field).width(140f).padLeft(8f);
            }).width(prefWidth).padTop(6f);
            addDesc(field);
            table.row();
        }
    }

    private static class ConditionalSwitchSetting extends SettingsMenuDialog.SettingsTable.Setting{
        private final RadialBuildMenuMod mod;

        public ConditionalSwitchSetting(RadialBuildMenuMod mod){
            super("rbm-cond");
            this.mod = mod;
            title = Core.bundle.get("setting.rbm-cond-enabled.name");
            description = Core.bundle.getOrNull("setting.rbm-cond-enabled.description");
        }

        @Override
        public void add(SettingsMenuDialog.SettingsTable table){
            float prefWidth = prefWidth();

            Table root = table.table(Tex.button, t -> {
                t.left().margin(10f);
                t.image(mindustry.gen.Icon.logic).size(20f).padRight(8f);
                t.add(title, Styles.outlineLabel).left().growX().minWidth(0f).wrap();

                arc.scene.ui.CheckBox box = new arc.scene.ui.CheckBox("");
                box.update(() -> box.setChecked(Core.settings.getBool(keyCondEnabled, false)));
                box.changed(() -> Core.settings.put(keyCondEnabled, box.isChecked()));
                t.add(box).right().padLeft(10f);
            }).width(prefWidth).padTop(6f).get();

            addDesc(root);
            table.row();

            Table inner = table.table(Tex.button, t -> {
                t.top().left().margin(10f);

                t.add("@rbm.cond.help").left().growX().wrap().minWidth(0f).padBottom(6f);
                t.row();

                t.add("@rbm.cond.initial.condition").left().padBottom(4f);
                t.row();

                arc.scene.ui.TextArea init = new arc.scene.ui.TextArea(Core.settings.getString(keyCondInitialExpr, ""));
                init.setMessageText(Core.bundle.get("rbm.cond.placeholder"));
                init.changed(() -> Core.settings.put(keyCondInitialExpr, init.getText()));
                t.add(init).growX().minHeight(70f).padBottom(8f);
                t.row();

                t.add("@rbm.cond.initial.slots").left().padBottom(4f);
                t.row();

                // 16 slots
                for(int i = 0; i < maxSlots; i++){
                    final int slot = i;
                    t.table(row -> {
                        row.left();
                        row.add(Core.bundle.format("rbm.setting.slot", slot + 1)).width(140f).left();

                        row.table(info -> {
                            info.left();
                            Image icon = info.image(Tex.clear).size(32f).padRight(8f).get();
                            icon.setScaling(Scaling.fit);
                            info.labelWrap(() -> {
                                Block b = mod.slotBlock(keyCondInitialSlotPrefix, slot);
                                return b == null ? Core.bundle.get("rbm.setting.none") : b.localizedName;
                            }).left().growX().fillX().minWidth(0f);

                            final Block[] last = {null};
                            info.update(() -> {
                                Block b = mod.slotBlock(keyCondInitialSlotPrefix, slot);
                                if(b == last[0]) return;
                                last[0] = b;
                                icon.setDrawable(b == null ? Tex.clear : new TextureRegionDrawable(b.uiIcon));
                            });
                        }).left().growX().minWidth(0f);

                        row.button("@rbm.setting.set", Styles.flatt, () -> mod.showBlockSelectDialog(block -> {
                            Core.settings.put(keyCondInitialSlotPrefix + slot, block == null ? "" : block.name);
                        })).width(120f).height(40f).padLeft(8f);
                    }).growX().padTop(3f);
                    t.row();
                }

                t.add("@rbm.cond.after.enable").left().padTop(10f).padBottom(4f);
                arc.scene.ui.CheckBox afterBox = new arc.scene.ui.CheckBox("");
                afterBox.update(() -> afterBox.setChecked(Core.settings.getBool(keyCondAfterEnabled, false)));
                afterBox.changed(() -> Core.settings.put(keyCondAfterEnabled, afterBox.isChecked()));
                t.add(afterBox).right().padLeft(10f);
                t.row();

                Table afterSection = t.table().left().growX().get();
                afterSection.visible(() -> Core.settings.getBool(keyCondAfterEnabled, false));

                afterSection.add("@rbm.cond.after.condition").left().padBottom(4f);
                afterSection.row();

                arc.scene.ui.TextArea after = new arc.scene.ui.TextArea(Core.settings.getString(keyCondAfterExpr, ""));
                after.setMessageText(Core.bundle.get("rbm.cond.placeholder"));
                after.changed(() -> Core.settings.put(keyCondAfterExpr, after.getText()));
                afterSection.add(after).growX().minHeight(70f).padBottom(8f);
                afterSection.row();

                afterSection.add("@rbm.cond.after.slots").left().padBottom(4f);
                afterSection.row();

                for(int i = 0; i < maxSlots; i++){
                    final int slot = i;
                    afterSection.table(row -> {
                        row.left();
                        row.add(Core.bundle.format("rbm.setting.slot", slot + 1)).width(140f).left();

                        row.table(info -> {
                            info.left();
                            Image icon = info.image(Tex.clear).size(32f).padRight(8f).get();
                            icon.setScaling(Scaling.fit);
                            info.labelWrap(() -> {
                                Block b = mod.slotBlock(keyCondAfterSlotPrefix, slot);
                                return b == null ? Core.bundle.get("rbm.setting.none") : b.localizedName;
                            }).left().growX().fillX().minWidth(0f);

                            final Block[] last = {null};
                            info.update(() -> {
                                Block b = mod.slotBlock(keyCondAfterSlotPrefix, slot);
                                if(b == last[0]) return;
                                last[0] = b;
                                icon.setDrawable(b == null ? Tex.clear : new TextureRegionDrawable(b.uiIcon));
                            });
                        }).left().growX().minWidth(0f);

                        row.button("@rbm.setting.set", Styles.flatt, () -> mod.showBlockSelectDialog(block -> {
                            Core.settings.put(keyCondAfterSlotPrefix + slot, block == null ? "" : block.name);
                        })).width(120f).height(40f).padLeft(8f);
                    }).growX().padTop(3f);
                    afterSection.row();
                }
            }).width(prefWidth).padTop(6f).get();

            inner.visible(() -> Core.settings.getBool(keyCondEnabled, false));
            table.row();
        }
    }

    private class IoSetting extends SettingsMenuDialog.SettingsTable.Setting{
        public IoSetting(){
            super("rbm-io");
            title = Core.bundle.get("rbm.io.title");
        }

        @Override
        public void add(SettingsMenuDialog.SettingsTable table){
            float prefWidth = prefWidth();
            table.table(Tex.button, t -> {
                t.left().margin(10f);

                t.image(mindustry.gen.Icon.info).size(20f).padRight(8f);
                t.add(title).width(140f).left().wrap();
                t.button("@rbm.io.export", Styles.flatt, RadialBuildMenuMod.this::showExportDialog)
                    .width(160f).height(40f).padLeft(8f);
                t.button("@rbm.io.import", Styles.flatt, RadialBuildMenuMod.this::showImportDialog)
                    .width(160f).height(40f).padLeft(8f);
            }).width(prefWidth).padTop(6f);
            table.row();
        }
    }

    private void showBlockSelectDialog(arc.func.Cons<Block> consumer){
        BaseDialog dialog = new BaseDialog("@rbm.selectblock.title");
        dialog.addCloseButton();

        String[] searchText = {""};

        Table list = new Table();
        list.top().left();
        list.defaults().growX().height(54f).pad(2f);

        ScrollPane pane = new ScrollPane(list);
        pane.setFadeScrollBars(false);

        Runnable rebuild = () -> {
            list.clearChildren();

            list.button("@rbm.selectblock.none", Styles.flatt, () -> {
                dialog.hide();
                consumer.get(null);
            }).row();

            String query = searchText[0] == null ? "" : searchText[0].trim().toLowerCase(Locale.ROOT);

            for(Block block : content.blocks()){
                if(block == null) continue;
                if(block.category == null) continue;
                if(!block.isVisible()) continue;
                if(block.buildVisibility == BuildVisibility.hidden) continue;
                if(!block.placeablePlayer) continue;

                if(!query.isEmpty()){
                    String name = block.name.toLowerCase(Locale.ROOT);
                    String localized = Strings.stripColors(block.localizedName).toLowerCase(Locale.ROOT);
                    if(!name.contains(query) && !localized.contains(query)){
                        continue;
                    }
                }

                list.button(b -> {
                    b.left();
                    b.image(block.uiIcon).size(32f).padRight(8f);
                    b.add(block.localizedName).left().growX().wrap();
                    b.add(block.name).color(Color.gray).padLeft(8f).right();
                }, Styles.flatt, () -> {
                    dialog.hide();
                    consumer.get(block);
                }).row();
            }
        };

        dialog.cont.table(t -> {
            t.left();
            t.image(mindustry.gen.Icon.zoom).padRight(8f);
            t.field("", text -> {
                searchText[0] = text;
                rebuild.run();
            }).growX().get().setMessageText("@players.search");
        }).growX().padBottom(6f);

        dialog.cont.row();
        dialog.cont.add(pane).grow().minHeight(320f);

        dialog.shown(rebuild);
        dialog.show();
    }

    private static String defaultSlotName(int slot){
        if(slot >= 0 && slot < defaultSlotNames.length) return defaultSlotNames[slot];
        return "";
    }

    private String slotName(String prefix, int slot){
        if(slot < 0 || slot >= maxSlots) return "";
        String value = Core.settings.getString(prefix + slot, defaultSlotName(slot));
        if(value == null) return "";
        return value.trim();
    }

    private Block slotBlock(String prefix, int slot){
        String name = slotName(prefix, slot);
        if(name.isEmpty()) return null;
        return content.block(name);
    }

    private boolean timeRuleActive(){
        if(!state.isGame() || state.rules.editor) return false;
        int minutes = Core.settings.getInt(keyTimeMinutes, 0);
        if(minutes <= 0) return false;
        double currentMinutes = state.tick / 60.0 / 60.0;
        return currentMinutes >= minutes;
    }

    private String currentPlanetName(){
        if(!state.isGame()) return "";
        Planet planet = state.getPlanet();
        return planet == null ? "" : planet.name;
    }

    private String planetPrefix(String planetName){
        if(!Core.settings.getBool(keyProMode, false)) return "";
        if(planetErekir.equals(planetName)) return Core.settings.getBool(keyPlanetErekirEnabled, true) ? keyPlanetErekirSlotPrefix : "";
        if(planetSerpulo.equals(planetName)) return Core.settings.getBool(keyPlanetSerpuloEnabled, true) ? keyPlanetSerpuloSlotPrefix : "";
        if(planetSun.equals(planetName)) return Core.settings.getBool(keyPlanetSunEnabled, true) ? keyPlanetSunSlotPrefix : "";
        return "";
    }

    private String timePlanetPrefix(String planetName){
        if(!Core.settings.getBool(keyProMode, false)) return "";
        if(planetErekir.equals(planetName)) return Core.settings.getBool(keyPlanetErekirEnabled, true) ? keyTimeErekirSlotPrefix : "";
        if(planetSerpulo.equals(planetName)) return Core.settings.getBool(keyPlanetSerpuloEnabled, true) ? keyTimeSerpuloSlotPrefix : "";
        if(planetSun.equals(planetName)) return Core.settings.getBool(keyPlanetSunEnabled, true) ? keyTimeSunSlotPrefix : "";
        return "";
    }

    private Block contextSlotBlock(int slot){
        // Slot-group toggle mode: ignore all other rule systems and only use the two configured groups.
        if(Core.settings.getBool(keyToggleSlotGroupsEnabled, false)){
            int group = Mathf.clamp(Core.settings.getInt(keyToggleSlotGroupState, 0), 0, 1);
            return slotBlock(group == 0 ? keyToggleSlotGroupASlotPrefix : keyToggleSlotGroupBSlotPrefix, slot);
        }

        boolean pro = Core.settings.getBool(keyProMode, false);

        if(pro){
            updateConditionalState();

            if(condAfterActive){
                Block b = slotBlock(keyCondAfterSlotPrefix, slot);
                if(b != null) return b;
            }else if(condInitActive){
                Block b = slotBlock(keyCondInitialSlotPrefix, slot);
                if(b != null) return b;
            }
        }

        String planet = currentPlanetName();

        if(timeRuleActive()){
            if(pro){
                String tp = timePlanetPrefix(planet);
                if(!tp.isEmpty()){
                    Block b = slotBlock(tp, slot);
                    if(b != null) return b;
                }
            }
            Block time = slotBlock(keyTimeSlotPrefix, slot);
            if(time != null) return time;
        }else{
            if(pro){
                String pp = planetPrefix(planet);
                if(!pp.isEmpty()){
                    Block b = slotBlock(pp, slot);
                    if(b != null) return b;
                }
            }
        }

        return slotBlock(keySlotPrefix, slot);
    }

    private void toggleSlotGroupNow(boolean showToast){
        if(!Core.settings.getBool(keyToggleSlotGroupsEnabled, false)) return;
        int cur = Mathf.clamp(Core.settings.getInt(keyToggleSlotGroupState, 0), 0, 1);
        int next = 1 - cur;
        Core.settings.put(keyToggleSlotGroupState, next);
        if(showToast && ui != null){
            String groupName = Core.bundle.get(next == 0 ? "rbm.slotgroup.a" : "rbm.slotgroup.b");
            ui.showInfoFade(Core.bundle.format("rbm.slotgroup.switched", groupName));
        }
    }

    private void updateConditionalState(){
        if(!Core.settings.getBool(keyCondEnabled, false)){
            condInitActive = false;
            condAfterActive = false;
            condAfterLatched = false;
            return;
        }

        if(!state.isGame() || player == null || player.team() == null){
            condInitActive = false;
            condAfterActive = false;
            return;
        }

        // throttle evaluation to reduce overhead (contextSlotBlock may be called 16 times per open)
        if(Time.time - condLastEval < condEvalIntervalFrames){
            return;
        }
        condLastEval = Time.time;

        boolean afterEnabled = Core.settings.getBool(keyCondAfterEnabled, false);
        if(!afterEnabled){
            condAfterLatched = false;
        }

        boolean afterNow = false;
        if(afterEnabled){
            afterNow = evalCondition(keyCondAfterExpr, false);
            if(afterNow) condAfterLatched = true;
        }

        condAfterActive = afterEnabled && condAfterLatched;
        if(condAfterActive){
            condInitActive = false;
            return;
        }

        condInitActive = evalCondition(keyCondInitialExpr, true);
    }

    private boolean evalCondition(String key, boolean initial){
        String src = Core.settings.getString(key, "");
        if(src == null) src = "";
        src = src.trim();
        if(src.isEmpty()) return false;

        try{
            if(initial){
                if(!src.equals(condInitSrc)){
                    condInitSrc = src;
                    condInitExpr = ConditionParser.parse(src);
                }
                return condInitExpr != null && condInitExpr.eval(this) != 0f;
            }else{
                if(!src.equals(condAfterSrc)){
                    condAfterSrc = src;
                    condAfterExpr = ConditionParser.parse(src);
                }
                return condAfterExpr != null && condAfterExpr.eval(this) != 0f;
            }
        }catch(Throwable t){
            // Don't spam UI; just treat as false.
            if(initial){
                condInitExpr = null;
            }else{
                condAfterExpr = null;
            }
            return false;
        }
    }

    // Used by condition expression evaluator (extracted into RbmConditionExpr).
    float condVar(String name){
        if(name == null) return 0f;
        String n = name.trim().toLowerCase(Locale.ROOT);
        if(n.isEmpty()) return 0f;

        if("second".equals(n)){
            return (float)(state.tick / 60.0);
        }

        if("unitcount".equals(n)){
            int count = 0;
            for(Unit u : Groups.unit){
                if(u != null && u.team == player.team()){
                    count++;
                }
            }
            return count;
        }

        if(n.endsWith("count") && n.length() > 5){
            String unitName = n.substring(0, n.length() - 5);
            UnitType type = content.unit(unitName);
            if(type != null){
                int count = 0;
                for(Unit u : Groups.unit){
                    if(u != null && u.team == player.team() && u.type == type){
                        count++;
                    }
                }
                return count;
            }
        }

        Item item = content.item(n);
        if(item != null){
            // Uses the "main core" item module; fast + stable.
            return player.team().items().get(item);
        }

        return 0f;
    }

    private static String defaultHudColorHex(){
        int r = Math.min(255, Math.max(0, (int)(Pal.accent.r * 255f)));
        int g = Math.min(255, Math.max(0, (int)(Pal.accent.g * 255f)));
        int b = Math.min(255, Math.max(0, (int)(Pal.accent.b * 255f)));
        return String.format(Locale.ROOT, "%02x%02x%02x", r, g, b);
    }

    private static String normalizeHex(String text){
        if(text == null) return defaultHudColorHex();
        String hex = text.trim();
        if(hex.startsWith("#")) hex = hex.substring(1);
        hex = hex.toLowerCase(Locale.ROOT);
        if(hex.length() > 6) hex = hex.substring(0, 6);
        while(hex.length() < 6) hex += "0";
        for(int i = 0; i < hex.length(); i++){
            char c = hex.charAt(i);
            boolean ok = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f');
            if(!ok) return defaultHudColorHex();
        }
        return hex;
    }

    private final Color hudColorCache = new Color();
    private String hudColorCacheRaw = null;
    private String hudColorCacheHex = null;

    private Color readHudColor(){
        String raw = Core.settings.getString(keyHudColor, defaultHudColorHex());
        if(raw == null) raw = defaultHudColorHex();
        if(raw.equals(hudColorCacheRaw)){
            return hudColorCache;
        }

        hudColorCacheRaw = raw;
        String hex = normalizeHex(raw);

        if(!hex.equals(hudColorCacheHex)){
            hudColorCacheHex = hex;
            try{
                int r = Integer.parseInt(hex.substring(0, 2), 16);
                int g = Integer.parseInt(hex.substring(2, 4), 16);
                int b = Integer.parseInt(hex.substring(4, 6), 16);
                hudColorCache.set(r / 255f, g / 255f, b / 255f, 1f);
            }catch(Throwable t){
                hudColorCache.set(Pal.accent);
            }
        }

        return hudColorCache;
    }

    private void showExportDialog(){
        String json = exportConfig();

        BaseDialog dialog = new BaseDialog("@rbm.io.export");
        dialog.addCloseButton();

        TextArea area = new TextArea(json);
        area.setDisabled(true);
        area.setPrefRows(12);

        ScrollPane pane = new ScrollPane(area);
        pane.setFadeScrollBars(false);

        dialog.cont.add(pane).grow().minHeight(220f);
        dialog.cont.row();
        dialog.cont.button("@rbm.io.copy", Styles.flatt, () -> {
            Core.app.setClipboardText(json);
            ui.showInfoFade("@rbm.io.copied");
        }).height(44f).growX().padTop(8f);

        dialog.show();
    }

    private void showImportDialog(){
        BaseDialog dialog = new BaseDialog("@rbm.io.import");
        dialog.addCloseButton();

        TextArea area = new TextArea("");
        area.setMessageText(Core.bundle.get("rbm.io.pastehere"));
        area.setPrefRows(12);

        ScrollPane pane = new ScrollPane(area);
        pane.setFadeScrollBars(false);

        dialog.cont.add(pane).grow().minHeight(220f);
        dialog.cont.row();
        dialog.cont.button("@rbm.io.import.apply", Styles.flatt, () -> {
            if(importConfig(area.getText())){
                ui.showInfoFade("@rbm.io.import.success");
                dialog.hide();
            }else{
                ui.showInfoFade("@rbm.io.import.invalid");
            }
        }).height(44f).growX().padTop(8f);

        dialog.show();
    }

    private String exportConfig(){
        Jval root = Jval.newObject();
        root.put("schema", 4);

        root.put("hudScale", Core.settings.getInt(keyHudScale, 100));
        root.put("hudAlpha", Core.settings.getInt(keyHudAlpha, 100));
        root.put("innerRadius", Core.settings.getInt(keyInnerRadius, 80));
        root.put("outerRadius", Core.settings.getInt(keyOuterRadius, 140));
        root.put("iconScale", Core.settings.getInt(keyIconScale, 100));
        root.put("backStrength", Core.settings.getInt(keyBackStrength, 22));
        root.put("ringAlpha", Core.settings.getInt(keyRingAlpha, 65));
        root.put("ringStroke", Core.settings.getInt(keyRingStroke, 2));
        root.put("hudColor", normalizeHex(Core.settings.getString(keyHudColor, defaultHudColorHex())));
        root.put("showEmptySlots", Core.settings.getBool(keyShowEmptySlots, false));
        root.put("proMode", Core.settings.getBool(keyProMode, false));
        root.put("planetErekirEnabled", Core.settings.getBool(keyPlanetErekirEnabled, true));
        root.put("planetSerpuloEnabled", Core.settings.getBool(keyPlanetSerpuloEnabled, true));
        root.put("planetSunEnabled", Core.settings.getBool(keyPlanetSunEnabled, true));

        root.put("hoverUpdateFrames", Core.settings.getInt(keyHoverUpdateFrames, 0));
        root.put("hoverPadding", Core.settings.getInt(keyHoverPadding, 12));
        root.put("deadzoneScale", Core.settings.getInt(keyDeadzoneScale, 35));
        root.put("directionSelect", Core.settings.getBool(keyDirectionSelect, true));

        root.put("timeMinutes", Core.settings.getInt(keyTimeMinutes, 0));

        root.put("condEnabled", Core.settings.getBool(keyCondEnabled, false));
        root.put("condInitialExpr", Core.settings.getString(keyCondInitialExpr, ""));
        root.put("condAfterEnabled", Core.settings.getBool(keyCondAfterEnabled, false));
        root.put("condAfterExpr", Core.settings.getString(keyCondAfterExpr, ""));
        root.put("condInitialSlots", exportSlots(keyCondInitialSlotPrefix));
        root.put("condAfterSlots", exportSlots(keyCondAfterSlotPrefix));

        root.put("toggleSlotGroupsEnabled", Core.settings.getBool(keyToggleSlotGroupsEnabled, false));
        root.put("toggleSlotGroupState", Mathf.clamp(Core.settings.getInt(keyToggleSlotGroupState, 0), 0, 1));
        root.put("toggleSlotsA", exportSlots(keyToggleSlotGroupASlotPrefix));
        root.put("toggleSlotsB", exportSlots(keyToggleSlotGroupBSlotPrefix));

        root.put("slots", exportSlots(keySlotPrefix));
        root.put("timeSlots", exportSlots(keyTimeSlotPrefix));
        root.put("timeSlotsErekir", exportSlots(keyTimeErekirSlotPrefix));
        root.put("timeSlotsSerpulo", exportSlots(keyTimeSerpuloSlotPrefix));
        root.put("timeSlotsSun", exportSlots(keyTimeSunSlotPrefix));

        root.put("planetSlotsErekir", exportSlots(keyPlanetErekirSlotPrefix));
        root.put("planetSlotsSerpulo", exportSlots(keyPlanetSerpuloSlotPrefix));
        root.put("planetSlotsSun", exportSlots(keyPlanetSunSlotPrefix));

        return root.toString(Jformat.plain);
    }

    private Jval exportSlots(String prefix){
        Jval arr = Jval.newArray();
        for(int i = 0; i < maxSlots; i++){
            arr.add(slotName(prefix, i));
        }
        return arr;
    }

    private boolean importConfig(String text){
        if(text == null) return false;
        try{
            Jval root = Jval.read(text);
            if(root == null || !root.isObject()) return false;

            if(root.has("hudScale")) Core.settings.put(keyHudScale, root.getInt("hudScale", 100));
            if(root.has("hudAlpha")) Core.settings.put(keyHudAlpha, root.getInt("hudAlpha", 100));
            if(root.has("innerRadius")) Core.settings.put(keyInnerRadius, root.getInt("innerRadius", 80));
            if(root.has("outerRadius")) Core.settings.put(keyOuterRadius, root.getInt("outerRadius", 140));
            if(root.has("iconScale")) Core.settings.put(keyIconScale, root.getInt("iconScale", 100));
            if(root.has("backStrength")) Core.settings.put(keyBackStrength, root.getInt("backStrength", 22));
            if(root.has("ringAlpha")) Core.settings.put(keyRingAlpha, root.getInt("ringAlpha", 65));
            if(root.has("ringStroke")) Core.settings.put(keyRingStroke, root.getInt("ringStroke", 2));
            if(root.has("hudColor")) Core.settings.put(keyHudColor, normalizeHex(root.getString("hudColor", defaultHudColorHex())));
            if(root.has("showEmptySlots")) Core.settings.put(keyShowEmptySlots, root.getBool("showEmptySlots", false));
            if(root.has("proMode")) Core.settings.put(keyProMode, root.getBool("proMode", false));
            if(root.has("planetErekirEnabled")) Core.settings.put(keyPlanetErekirEnabled, root.getBool("planetErekirEnabled", true));
            if(root.has("planetSerpuloEnabled")) Core.settings.put(keyPlanetSerpuloEnabled, root.getBool("planetSerpuloEnabled", true));
            if(root.has("planetSunEnabled")) Core.settings.put(keyPlanetSunEnabled, root.getBool("planetSunEnabled", true));

            if(root.has("hoverUpdateFrames")) Core.settings.put(keyHoverUpdateFrames, Math.max(0, root.getInt("hoverUpdateFrames", 0)));
            if(root.has("hoverPadding")) Core.settings.put(keyHoverPadding, Math.max(0, root.getInt("hoverPadding", 12)));
            if(root.has("deadzoneScale")) Core.settings.put(keyDeadzoneScale, Mathf.clamp(root.getInt("deadzoneScale", 35), 0, 100));
            if(root.has("directionSelect")) Core.settings.put(keyDirectionSelect, root.getBool("directionSelect", true));

            if(root.has("timeMinutes")) Core.settings.put(keyTimeMinutes, Math.max(0, root.getInt("timeMinutes", 0)));

            if(root.has("condEnabled")) Core.settings.put(keyCondEnabled, root.getBool("condEnabled", false));
            if(root.has("condInitialExpr")) Core.settings.put(keyCondInitialExpr, root.getString("condInitialExpr", ""));
            if(root.has("condAfterEnabled")) Core.settings.put(keyCondAfterEnabled, root.getBool("condAfterEnabled", false));
            if(root.has("condAfterExpr")) Core.settings.put(keyCondAfterExpr, root.getString("condAfterExpr", ""));
            if(root.has("condInitialSlots")) importSlots(root.get("condInitialSlots"), keyCondInitialSlotPrefix);
            if(root.has("condAfterSlots")) importSlots(root.get("condAfterSlots"), keyCondAfterSlotPrefix);

            if(root.has("toggleSlotGroupsEnabled")) Core.settings.put(keyToggleSlotGroupsEnabled, root.getBool("toggleSlotGroupsEnabled", false));
            if(root.has("toggleSlotGroupState")) Core.settings.put(keyToggleSlotGroupState, Mathf.clamp(root.getInt("toggleSlotGroupState", 0), 0, 1));
            if(root.has("toggleSlotsA")) importSlots(root.get("toggleSlotsA"), keyToggleSlotGroupASlotPrefix);
            if(root.has("toggleSlotsB")) importSlots(root.get("toggleSlotsB"), keyToggleSlotGroupBSlotPrefix);

            if(root.has("slots")) importSlots(root.get("slots"), keySlotPrefix);
            if(root.has("timeSlots")) importSlots(root.get("timeSlots"), keyTimeSlotPrefix);
            if(root.has("timeSlotsErekir")) importSlots(root.get("timeSlotsErekir"), keyTimeErekirSlotPrefix);
            if(root.has("timeSlotsSerpulo")) importSlots(root.get("timeSlotsSerpulo"), keyTimeSerpuloSlotPrefix);
            if(root.has("timeSlotsSun")) importSlots(root.get("timeSlotsSun"), keyTimeSunSlotPrefix);

            if(root.has("planetSlotsErekir")) importSlots(root.get("planetSlotsErekir"), keyPlanetErekirSlotPrefix);
            if(root.has("planetSlotsSerpulo")) importSlots(root.get("planetSlotsSerpulo"), keyPlanetSerpuloSlotPrefix);
            if(root.has("planetSlotsSun")) importSlots(root.get("planetSlotsSun"), keyPlanetSunSlotPrefix);

            return true;
        }catch(Throwable t){
            return false;
        }
    }

    private void importSlots(Jval arr, String prefix){
        if(arr == null || !arr.isArray()) return;
        int size = Math.min(arr.asArray().size, maxSlots);
        for(int i = 0; i < size; i++){
            String value = arr.asArray().get(i).asString();
            Core.settings.put(prefix + i, (value == null ? "" : value).trim());
        }
        for(int i = size; i < maxSlots; i++){
            Core.settings.put(prefix + i, "");
        }
    }

    private void ensureOverlayAttached(){
        if(ui == null || ui.hudGroup == null) return;

        if(ui.hudGroup.find(overlayName) != null) return;

        RadialHud hud = new RadialHud(this);
        hud.name = overlayName;
        hud.touchable = Touchable.disabled;
        ui.hudGroup.addChild(hud);
    }

    private void ensureMobileToggleAttached(){
        if(!mobile) return;
        if(ui == null || ui.hudGroup == null) return;

        ensureOverlayAttached();

        // Prefer MindustryX OverlayUI if available.
        if(xOverlayUi.isInstalled()){
            if(xMobileToggleWindow == null){
                try{
                    Table content = buildMobileToggleContent();
                    xMobileToggleWindow = xOverlayUi.registerWindow(mobileWindowName, content, () -> state != null && state.isGame());
                    if(xMobileToggleWindow != null){
                        xOverlayUi.configureWindow(xMobileToggleWindow, true, false);
                        // Auto-enable once, but don't force pinned (allow hiding/closing from OverlayUI).
                        xOverlayUi.setEnabledAndPinned(xMobileToggleWindow, true, false);
                        return;
                    }
                }catch(Throwable t){
                    xMobileToggleWindow = null;
                }
            }else{
                return;
            }
        }

        // Fallback: attach a fixed-center toggle button directly to the HUD.
        if(ui.hudGroup.find(mobileToggleName) != null) return;

        Table content = buildMobileToggleContent();
        content.name = mobileToggleName;
        ui.hudGroup.addChild(content);
        content.update(() -> {
            // Keep centered and above most HUD elements.
            content.setPosition(Core.graphics.getWidth() / 2f, Core.graphics.getHeight() / 2f, Align.center);
            content.toFront();
        });
    }

    private Table buildMobileToggleContent(){
        Table t = new Table(Tex.button);
        t.touchable = Touchable.enabled;
        t.margin(8f);

        t.button(mindustry.gen.Icon.hammer, Styles.clearNonei, () -> {
            if(ui == null || ui.hudGroup == null) return;
            Element e = ui.hudGroup.find(overlayName);
            if(e instanceof RadialHud){
                ((RadialHud)e).beginMobile();
            }
        }).size(56f);

        return t;
    }

    private static class RadialHud extends Element{
        private final RadialBuildMenuMod mod;

        private boolean active;
        private float centerX, centerY;
        private int hovered = -1;
        private float nextHoverUpdate = 0f;
        private final Block[] slots = new Block[maxSlots];
        private boolean outerActive;
        private final Color hudColor = new Color();

        // Cached per-frame HUD geometry/metrics (avoids recomputing settings + trig in multiple methods).
        private final HudLayout layout = new HudLayout();

        private final int[] innerIndices = new int[slotsPerRing];
        private final int[] outerIndices = new int[slotsPerRing];
        private int innerCount;
        private int outerCount;

        private static final class HudLayout{
            float alpha;
            float scale;
            float iconSize;
            float innerRadius;
            float innerRadius2;
            float outerRadius;

            float slotBack;
            float strokeNorm;
            float strokeHover;

            float hit2;
            float deadzone2;
            float backRadius;
            float backRadius2;

            float ringStroke;
            float ringAlpha;
            float backStrength;

            final float[] innerX = new float[slotsPerRing];
            final float[] innerY = new float[slotsPerRing];
            final float[] outerX = new float[slotsPerRing];
            final float[] outerY = new float[slotsPerRing];
        }

        public RadialHud(RadialBuildMenuMod mod){
            this.mod = mod;

            // Mobile: tap-to-select; close by tapping outside the HUD.
            addListener(new InputListener(){
                @Override
                public boolean touchDown(InputEvent event, float x, float y, int pointer, KeyCode button){
                    if(!mobile) return false;
                    if(!active) return false;
                    if(pointer != 0) return false;

                    float sx = event.stageX;
                    float sy = event.stageY;

                    int slot = findSlotAt(sx, sy);
                    if(slot != -1){
                        hovered = slot;
                        commitSelection();
                        close();
                        return true;
                    }

                    // Tap outside to close; taps inside the HUD but not on an icon do nothing.
                    if(isOutsideHud(sx, sy)){
                        close();
                        return true;
                    }

                    return true; // consume while the HUD is open
                }
            });
        }

        @Override
        public void act(float delta){
            super.act(delta);

            if(parent != null){
                setBounds(0f, 0f, parent.getWidth(), parent.getHeight());
            }else{
                setSize(Core.graphics.getWidth(), Core.graphics.getHeight());
            }

            // Keep touch handling disabled unless we're actively showing the HUD on mobile.
            touchable = (mobile && active) ? Touchable.enabled : Touchable.disabled;

            if(active){
                if(!canStayActive()){
                    close();
                    return;
                }

                if(mobile){
                    // Mobile HUD is always centered.
                    centerX = getWidth() / 2f;
                    centerY = getHeight() / 2f;
                    return;
                }

                if(Core.settings.getBool(keyCenterScreen, false)){
                    centerX = getWidth() / 2f;
                    centerY = getHeight() / 2f;
                }

                if(Core.settings.getBool(keyToggleSlotGroupsEnabled, false) && Core.input.keyTap(toggleSlotGroup)){
                    mod.toggleSlotGroupNow(true);
                    for(int i = 0; i < slots.length; i++){
                        slots[i] = mod.contextSlotBlock(i);
                    }
                    rebuildActiveSlotLists();
                    hovered = findHovered();
                }

                updateHovered();

                if(Core.input.keyRelease(radialMenu)){
                    commitSelection();
                    close();
                }else if(!Core.input.keyDown(radialMenu)){
                    // failsafe: if focus changed and no release is received
                    close();
                }
            }else{
                if(Core.settings.getBool(keyToggleSlotGroupsEnabled, false) && Core.input.keyTap(toggleSlotGroup)){
                    mod.toggleSlotGroupNow(true);
                }
                if(mobile) return;
                if(canActivate() && Core.input.keyTap(radialMenu)){
                    begin();
                }
            }
        }

        private void updateLayout(){
            HudLayout l = layout;

            // Read settings once, derive all geometry, and precompute slot positions.
            float alpha = parentAlpha * Mathf.clamp(Core.settings.getInt(keyHudAlpha, 100) / 100f);
            float scale = Mathf.clamp(Core.settings.getInt(keyHudScale, 100) / 100f, 0.1f, 5f);
            int innerSetting = Core.settings.getInt(keyInnerRadius, 80);
            int outerSetting = Core.settings.getInt(keyOuterRadius, 140);
            float radiusScale = Mathf.clamp((innerSetting / 80f + outerSetting / 140f) / 2f, 0.5f, 3f);

            float iconSizeScale = Mathf.clamp(Core.settings.getInt(keyIconScale, 100) / 100f, 0.2f, 5f);
            float iconSize = Scl.scl(46f) * scale * radiusScale * iconSizeScale;

            float innerRadius = Scl.scl(innerSetting) * scale;
            float outerRadius = Scl.scl(outerSetting) * scale;
            outerRadius = Math.max(outerRadius, innerRadius + iconSize * 1.15f);

            float hoverPadding = Math.max(0, Core.settings.getInt(keyHoverPadding, 12));
            float hit = iconSize / 2f + Scl.scl(hoverPadding) * scale;

            float deadzone = iconSize * Mathf.clamp(Core.settings.getInt(keyDeadzoneScale, 35) / 100f);

            float outer = outerActive ? outerRadius : innerRadius;
            float backRadius = outer + iconSize * 0.75f;

            l.alpha = alpha;
            l.scale = scale;
            l.iconSize = iconSize;
            l.innerRadius = innerRadius;
            l.innerRadius2 = innerRadius * innerRadius;
            l.outerRadius = outerRadius;

            l.slotBack = iconSize / 2f + Scl.scl(10f) * scale;
            l.strokeNorm = Scl.scl(1.6f) * scale;
            l.strokeHover = Scl.scl(2.4f) * scale;

            l.hit2 = hit * hit;
            l.deadzone2 = deadzone * deadzone;
            l.backRadius = backRadius;
            l.backRadius2 = backRadius * backRadius;

            l.backStrength = Mathf.clamp(Core.settings.getInt(keyBackStrength, 22) / 100f);
            l.ringAlpha = Mathf.clamp(Core.settings.getInt(keyRingAlpha, 65) / 100f);
            l.ringStroke = Scl.scl(Core.settings.getInt(keyRingStroke, 2)) * scale;

            for(int order = 0; order < innerCount; order++){
                float angle = angleForOrder(order, innerCount);
                l.innerX[order] = centerX + Mathf.cosDeg(angle) * innerRadius;
                l.innerY[order] = centerY + Mathf.sinDeg(angle) * innerRadius;
            }

            for(int order = 0; order < outerCount; order++){
                float angle = angleForOrder(order, outerCount);
                l.outerX[order] = centerX + Mathf.cosDeg(angle) * outerRadius;
                l.outerY[order] = centerY + Mathf.sinDeg(angle) * outerRadius;
            }
        }

        @Override
        public void draw(){
            if(!active) return;

            updateLayout();
            HudLayout l = layout;

            float alpha = l.alpha;
            if(alpha <= 0.001f) return;

            float iconSize = l.iconSize;
            float innerRadius = l.innerRadius;
            float outerRadius = l.outerRadius;
            float slotBack = l.slotBack;
            float strokeNorm = l.strokeNorm;
            float strokeHover = l.strokeHover;

            Draw.z(1000f);

            hudColor.set(mod.readHudColor());

            // soft background disc around the cursor
            float backStrength = l.backStrength;
            Draw.color(hudColor, backStrength * alpha);
            Fill.circle(centerX, centerY, l.backRadius);

            // ring
            float ringAlpha = l.ringAlpha;
            Draw.color(Pal.accent, ringAlpha * alpha);
            Lines.stroke(l.ringStroke);
            Lines.circle(centerX, centerY, innerRadius);
            if(outerActive){
                Lines.circle(centerX, centerY, outerRadius);
            }

            // draw inner ring slots (only configured)
            for(int order = 0; order < innerCount; order++){
                int slotIndex = innerIndices[order];
                float px = l.innerX[order];
                float py = l.innerY[order];

                boolean isHovered = slotIndex == hovered;

                // slot background
                Draw.color(hudColor, (isHovered ? 0.40f : 0.28f) * alpha);
                Fill.circle(px, py, slotBack);

                // slot border
                Draw.color(isHovered ? Pal.accent : Color.gray, (isHovered ? 1f : 0.35f) * alpha);
                Lines.stroke(isHovered ? strokeHover : strokeNorm);
                Lines.circle(px, py, slotBack);

                Block block = slots[slotIndex];
                if(block == null) continue;
                Draw.color(Color.white, alpha);
                Draw.rect(block.uiIcon, px, py, iconSize, iconSize);
            }

            // draw outer ring slots (only configured)
            if(outerActive){
                for(int order = 0; order < outerCount; order++){
                    int slotIndex = outerIndices[order];
                    float angle = angleForOrder(order, outerCount);
                    float px = centerX + Mathf.cosDeg(angle) * outerRadius;
                    float py = centerY + Mathf.sinDeg(angle) * outerRadius;

                    boolean isHovered = slotIndex == hovered;

                    Draw.color(hudColor, (isHovered ? 0.40f : 0.28f) * alpha);
                    Fill.circle(px, py, slotBack);

                    Draw.color(isHovered ? Pal.accent : Color.gray, (isHovered ? 1f : 0.35f) * alpha);
                    Lines.stroke(isHovered ? strokeHover : strokeNorm);
                    Lines.circle(px, py, slotBack);

                    Block block = slots[slotIndex];
                    if(block == null) continue;
                    Draw.color(Color.white, alpha);
                    Draw.rect(block.uiIcon, px, py, iconSize, iconSize);
                }
            }

            Draw.reset();
        }

        private boolean canActivate(){
            if(!Core.settings.getBool(keyEnabled, true)) return false;
            if(ui == null || ui.hudfrag == null || !ui.hudfrag.shown) return false;
            if(Core.scene.hasDialog()) return false;
            if(Core.scene.hasKeyboard()) return false;
            if(ui.chatfrag != null && ui.chatfrag.shown()) return false;
            if(ui.consolefrag != null && ui.consolefrag.shown()) return false;
            if(player == null || player.dead()) return false;
            if(!state.rules.editor && !player.isBuilder()) return false;
            return true;
        }

        private boolean canStayActive(){
            // allow staying active even if the keybind changes mid-hold
            if(!Core.settings.getBool(keyEnabled, true)) return false;
            if(ui == null || ui.hudfrag == null || !ui.hudfrag.shown) return false;
            if(Core.scene.hasDialog()) return false;
            if(Core.scene.hasKeyboard()) return false;
            if(ui.chatfrag != null && ui.chatfrag.shown()) return false;
            if(ui.consolefrag != null && ui.consolefrag.shown()) return false;
            if(player == null || player.dead()) return false;
            return state.rules.editor || player.isBuilder();
        }

        private void begin(){
            active = true;
            if(Core.settings.getBool(keyCenterScreen, false)){
                centerX = getWidth() / 2f;
                centerY = getHeight() / 2f;
            }else{
                centerX = Core.input.mouseX();
                centerY = Core.input.mouseY();
            }

            for(int i = 0; i < slots.length; i++){
                slots[i] = mod.contextSlotBlock(i);
            }

            rebuildActiveSlotLists();

            hovered = findHovered();
        }

        private void beginMobile(){
            if(active) return;
            if(!canActivate()) return;

            active = true;
            hovered = -1;
            centerX = getWidth() / 2f;
            centerY = getHeight() / 2f;

            for(int i = 0; i < slots.length; i++){
                slots[i] = mod.contextSlotBlock(i);
            }
            rebuildActiveSlotLists();
        }

        private void close(){
            active = false;
            hovered = -1;
        }

        private int findSlotAt(float sx, float sy){
            updateLayout();
            HudLayout l = layout;

            float centerDx = sx - centerX;
            float centerDy = sy - centerY;
            boolean preferInner = innerCount > 0 && (centerDx * centerDx + centerDy * centerDy) <= l.innerRadius2;

            int bestSlot = -1;
            float bestDst2 = l.hit2;

            for(int order = 0; order < innerCount; order++){
                int slotIndex = innerIndices[order];
                float dx = sx - l.innerX[order];
                float dy = sy - l.innerY[order];
                float dst2 = dx * dx + dy * dy;
                if(dst2 <= bestDst2){
                    bestDst2 = dst2;
                    bestSlot = slotIndex;
                }
            }

            if(outerActive && !preferInner){
                for(int order = 0; order < outerCount; order++){
                    int slotIndex = outerIndices[order];
                    float dx = sx - l.outerX[order];
                    float dy = sy - l.outerY[order];
                    float dst2 = dx * dx + dy * dy;
                    if(dst2 <= bestDst2){
                        bestDst2 = dst2;
                        bestSlot = slotIndex;
                    }
                }
            }

            return bestSlot;
        }

        private boolean isOutsideHud(float sx, float sy){
            updateLayout();
            HudLayout l = layout;

            float dx = sx - centerX;
            float dy = sy - centerY;
            return dx * dx + dy * dy > l.backRadius2;
        }

        private void updateHovered(){
            int frames = Math.max(0, Core.settings.getInt(keyHoverUpdateFrames, 0));
            if(frames == 0){
                hovered = findHovered();
                return;
            }

            if(Time.time >= nextHoverUpdate){
                hovered = findHovered();
                nextHoverUpdate = Time.time + frames;
            }
        }

        private void rebuildActiveSlotLists(){
            boolean showEmpty = Core.settings.getBool(keyShowEmptySlots, false);

            if(showEmpty){
                innerCount = slotsPerRing;
                for(int i = 0; i < slotsPerRing; i++){
                    innerIndices[i] = i;
                }

                int configuredOuter = 0;
                for(int i = 0; i < slotsPerRing; i++){
                    if(slots[slotsPerRing + i] != null) configuredOuter++;
                }

                outerActive = configuredOuter > 0;
                if(outerActive){
                    outerCount = slotsPerRing;
                    for(int i = 0; i < slotsPerRing; i++){
                        outerIndices[i] = slotsPerRing + i;
                    }
                }else{
                    outerCount = 0;
                }

                return;
            }

            innerCount = 0;
            outerCount = 0;

            for(int i = 0; i < slotsPerRing; i++){
                if(slots[i] != null){
                    innerIndices[innerCount++] = i;
                }
            }

            for(int i = 0; i < slotsPerRing; i++){
                int slotIndex = slotsPerRing + i;
                if(slots[slotIndex] != null){
                    outerIndices[outerCount++] = slotIndex;
                }
            }

            outerActive = outerCount > 0;
        }

        private float angleForOrder(int order, int count){
            if(count <= 0) return 90f;
            float step = 360f / count;
            return 90f - order * step;
        }

        private int findHovered(){
            updateLayout();
            HudLayout l = layout;

            float mx = Core.input.mouseX();
            float my = Core.input.mouseY();

            // When the cursor is on/inside the inner ring radius, never allow selecting outer ring slots.
            float centerDx = mx - centerX;
            float centerDy = my - centerY;
            boolean preferInner = innerCount > 0 && (centerDx * centerDx + centerDy * centerDy) <= l.innerRadius2;

            // hover hit-test (inner + outer)
            int bestSlot = -1;
            float bestDst2 = l.hit2;

            for(int order = 0; order < innerCount; order++){
                int slotIndex = innerIndices[order];
                float dx = mx - l.innerX[order];
                float dy = my - l.innerY[order];
                float dst2 = dx * dx + dy * dy;
                if(dst2 <= bestDst2){
                    bestDst2 = dst2;
                    bestSlot = slotIndex;
                }
            }

            if(outerActive && !preferInner){
                for(int order = 0; order < outerCount; order++){
                    int slotIndex = outerIndices[order];
                    float dx = mx - l.outerX[order];
                    float dy = my - l.outerY[order];
                    float dst2 = dx * dx + dy * dy;
                    if(dst2 <= bestDst2){
                        bestDst2 = dst2;
                        bestSlot = slotIndex;
                    }
                }
            }

            if(bestSlot != -1) return bestSlot;

            if(!Core.settings.getBool(keyDirectionSelect, true)) return -1;

            // direction-based selection
            float dx = centerDx;
            float dy = centerDy;
            if(dx * dx + dy * dy < l.deadzone2) return -1;

            if(preferInner){
                if(innerCount <= 0) return -1;
                int order = orderIndex(dx, dy, innerCount);
                if(order < 0 || order >= innerCount) return -1;
                return innerIndices[order];
            }

            if(outerActive){
                if(outerCount <= 0) return -1;
                int order = orderIndex(dx, dy, outerCount);
                if(order < 0 || order >= outerCount) return -1;
                return outerIndices[order];
            }else{
                // only inner ring exists; direction selection selects inner slot
                if(innerCount <= 0) return -1;
                int order = orderIndex(dx, dy, innerCount);
                if(order < 0 || order >= innerCount) return -1;
                return innerIndices[order];
            }
        }

        private int orderIndex(float dx, float dy, int count){
            if(count <= 0) return -1;
            // NOTE: use angleExact(x, y). Mathf.atan2() has unusual parameter order.
            float angle = Mathf.angleExact(dx, dy);

            float rotated = 90f - angle;
            rotated = ((rotated % 360f) + 360f) % 360f;
            float step = 360f / count;
            int idx = (int)Math.floor((rotated + step / 2f) / step) % count;
            if(idx < 0) idx += count;
            return idx;
        }

        private void commitSelection(){
            if(hovered < 0 || hovered >= slots.length) return;
            Block block = slots[hovered];
            if(block == null) return;

            if(!state.rules.editor && !unlocked(block)){
                ui.showInfoFade("@rbm.block.unavailable");
                return;
            }

            control.input.block = block;
            if(ui != null && ui.hudfrag != null && block.isVisible() && block.category != null){
                ui.hudfrag.blockfrag.currentCategory = block.category;
            }
        }

        private static boolean unlocked(Block block){
            return block.unlockedNowHost()
                && block.placeablePlayer
                && block.environmentBuildable()
                && block.supportsEnv(state.rules.env);
        }
    }

    /** Optional integration with MindustryX OverlayUI. Uses reflection so vanilla builds won't crash. */
    private static class MindustryXOverlayUI{
        private boolean initialized = false;
        private boolean installed = false;
        private Object instance;
        private Method registerWindow;
        private Method setAvailability;
        private Method getData;
        private Method setEnabled;
        private Method setPinned;
        private Method setResizable;
        private Method setAutoHeight;

        boolean isInstalled(){
            if(initialized) return installed;
            initialized = true;
            try{
                installed = mindustry.Vars.mods != null && mindustry.Vars.mods.locateMod("mindustryx") != null;
            }catch(Throwable ignored){
                installed = false;
            }
            if(!installed) return false;

            try{
                Class<?> c = Class.forName("mindustryX.features.ui.OverlayUI");
                instance = c.getField("INSTANCE").get(null);
                registerWindow = c.getMethod("registerWindow", String.class, Table.class);
            }catch(Throwable t){
                installed = false;
                Log.err("RBM: MindustryX detected but OverlayUI reflection init failed.", t);
                return false;
            }
            return true;
        }

        Object registerWindow(String name, Table table, Prov<Boolean> availability){
            if(!isInstalled()) return null;
            try{
                Object window = registerWindow.invoke(instance, name, table);
                tryInitWindowAccessors(window);
                if(window != null && availability != null && setAvailability != null){
                    setAvailability.invoke(window, availability);
                }
                return window;
            }catch(Throwable t){
                Log.err("RBM: OverlayUI.registerWindow failed.", t);
                return null;
            }
        }

        void configureWindow(Object window, boolean resizable, boolean autoHeight){
            if(window == null) return;
            try{
                tryInitWindowAccessors(window);
                if(setResizable != null) setResizable.invoke(window, resizable);
                if(setAutoHeight != null) setAutoHeight.invoke(window, autoHeight);
            }catch(Throwable ignored){
            }
        }

        void setEnabledAndPinned(Object window, boolean enabled, boolean pinned){
            if(window == null) return;
            try{
                tryInitWindowAccessors(window);
                if(getData == null) return;
                Object data = getData.invoke(window);
                if(data == null) return;
                if(setEnabled != null) setEnabled.invoke(data, enabled);
                if(pinned && setPinned != null) setPinned.invoke(data, true);
            }catch(Throwable ignored){
            }
        }

        private void tryInitWindowAccessors(Object window){
            if(window == null) return;
            if(getData != null || setAvailability != null) return;
            try{
                Class<?> wc = window.getClass();
                try{
                    setAvailability = wc.getMethod("setAvailability", Prov.class);
                }catch(Throwable ignored){
                    setAvailability = null;
                }
                try{
                    setResizable = wc.getMethod("setResizable", boolean.class);
                }catch(Throwable ignored){
                    setResizable = null;
                }
                try{
                    setAutoHeight = wc.getMethod("setAutoHeight", boolean.class);
                }catch(Throwable ignored){
                    setAutoHeight = null;
                }
                getData = wc.getMethod("getData");

                Object data = getData.invoke(window);
                if(data != null){
                    Class<?> dc = data.getClass();
                    try{
                        setEnabled = dc.getMethod("setEnabled", boolean.class);
                    }catch(Throwable ignored){
                        setEnabled = null;
                    }
                    try{
                        setPinned = dc.getMethod("setPinned", boolean.class);
                    }catch(Throwable ignored){
                        setPinned = null;
                    }
                }
            }catch(Throwable ignored){
            }
        }
    }

    static float prefWidth(){
        // slightly wider so long texts don't get clipped in settings dialogs
        return Math.min(Core.graphics.getWidth() / 1.02f, 980f);
    }
    // WideSliderSetting extracted into `RbmSettingsExtracted`.

    // Condition expression parsing/eval extracted into `RbmConditionExpr` (same behavior).
}
