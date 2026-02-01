package radialbuildmenu;

import arc.Core;
import arc.Events;
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

import java.util.Locale;

import static mindustry.Vars.player;
import static mindustry.Vars.state;
import static mindustry.Vars.ui;
import static mindustry.Vars.content;
import static mindustry.Vars.mobile;
import static mindustry.Vars.control;

public class RadialBuildMenuMod extends mindustry.mod.Mod{
    /** When true, this mod is running as a bundled component inside BEK-Tools. */
    public static boolean bekBundled = false;

    private static final String overlayName = "rbm-overlay";

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
    private static final String keyProMode = "rbm-pro-mode";
    private static final String keyTimeMinutes = "rbm-time-minutes";

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
        });

        Events.on(WorldLoadEvent.class, e -> {
            resetMatchState();
            Time.runTask(10f, this::ensureOverlayAttached);
        });
    }

    private void ensureDefaults(){
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
            Core.settings.defaults(keyTimeSlotPrefix + i, def);
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
    }

    private void registerSettings(){
        if(ui == null || ui.settings == null) return;
        if(bekBundled) return;

        ui.settings.addCategory("@rbm.category", this::bekBuildSettings);
    }

    /** Populates a {@link mindustry.ui.dialogs.SettingsMenuDialog.SettingsTable} with this mod's settings. */
    public void bekBuildSettings(SettingsMenuDialog.SettingsTable table){
        table.checkPref(keyEnabled, true);
        table.pref(new HotkeySetting());

        table.pref(new HeaderSetting(Core.bundle.get("rbm.section.appearance"), mindustry.gen.Icon.pencil));
        table.sliderPref(keyHudScale, 100, 50, 200, 5, v -> v + "%");
        table.sliderPref(keyHudAlpha, 100, 0, 100, 5, v -> v + "%");
        table.sliderPref(keyInnerRadius, 80, 40, 200, 5, v -> v + "px");
        table.sliderPref(keyOuterRadius, 140, 60, 360, 5, v -> v + "px");
        table.pref(new HudColorSetting());
        table.checkPref(keyCenterScreen, false);
        table.checkPref(keyProMode, false);
        table.pref(new AdvancedButtonSetting(RadialBuildMenuMod.this));

        table.pref(new HeaderSetting(Core.bundle.get("rbm.section.base"), mindustry.gen.Icon.list));
        for(int i = 0; i < maxSlots; i++) table.pref(new SlotSetting(i, keySlotPrefix, "rbm.setting.slot"));

        table.pref(new HeaderSetting(Core.bundle.get("rbm.section.time"), mindustry.gen.Icon.refresh));
        table.pref(new TimeMinutesSetting());
        for(int i = 0; i < maxSlots; i++) table.pref(new SlotSetting(i, keyTimeSlotPrefix, "rbm.setting.timeslot"));

        table.pref(new HeaderSetting(Core.bundle.get("rbm.section.io"), mindustry.gen.Icon.info));
        table.pref(new IoSetting());
    }

    private void showAdvancedDialog(){
        BaseDialog dialog = new BaseDialog("@rbm.advanced.title");
        dialog.addCloseButton();

        SettingsMenuDialog.SettingsTable adv = new SettingsMenuDialog.SettingsTable();

        adv.pref(new CollapsiblePlanetSetting(
            Core.bundle.get("rbm.advanced.planet.erekir"),
            mindustry.gen.Icon.modeAttack,
            "rbm-adv-erekir-open",
            t -> {
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
                t.pref(new SubHeaderSetting("@rbm.advanced.initial"));
                for(int i = 0; i < maxSlots; i++) t.pref(new SlotSetting(i, keyPlanetSunSlotPrefix, "rbm.setting.slot"));
                t.pref(new SubHeaderSetting("@rbm.advanced.time"));
                for(int i = 0; i < maxSlots; i++) t.pref(new SlotSetting(i, keyTimeSunSlotPrefix, "rbm.setting.timeslot"));
            },
            this::readHudColor
        ));

        adv.pref(new HeaderSetting(Core.bundle.get("rbm.advanced.section.appearance"), mindustry.gen.Icon.pencil));
        adv.pref(new WideSliderSetting(keyIconScale, 100, 50, 200, 5, v -> v + "%"));
        adv.pref(new WideSliderSetting(keyBackStrength, 22, 0, 60, 2, v -> v + "%"));
        adv.pref(new WideSliderSetting(keyRingAlpha, 65, 0, 100, 5, v -> v + "%"));
        adv.pref(new WideSliderSetting(keyRingStroke, 2, 1, 6, 1, v -> v + "px"));

        adv.pref(new HeaderSetting(Core.bundle.get("rbm.advanced.section.interaction"), mindustry.gen.Icon.modeAttack));
        adv.checkPref(keyDirectionSelect, true);
        adv.pref(new WideSliderSetting(keyDeadzoneScale, 35, 0, 100, 5, v -> v + "%"));
        adv.pref(new WideSliderSetting(keyHoverPadding, 12, 0, 30, 1, v -> v + "px"));

        adv.pref(new HeaderSetting(Core.bundle.get("rbm.advanced.section.performance"), mindustry.gen.Icon.info));
        adv.pref(new WideSliderSetting(keyHoverUpdateFrames, 0, 0, 10, 1, v -> v == 0 ? Core.bundle.get("rbm.advanced.everyframe") : v + "f"));

        adv.pref(new HeaderSetting(Core.bundle.get("rbm.advanced.section.cond"), mindustry.gen.Icon.logic));
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
            table.table(Tex.button, t -> {
                t.left().margin(10f);

                Image stripe = t.image(Tex.whiteui).size(4f, 18f).padRight(10f).get();
                stripe.setScaling(Scaling.stretch);
                stripe.update(() -> stripe.setColor(readHudColor()));

                if(icon != null){
                    Image ic = t.image(icon).size(18f).padRight(8f).get();
                    ic.update(() -> ic.setColor(readHudColor()));
                }

                t.add(title).color(Color.lightGray).left().growX().minWidth(0f).wrap();
            }).width(width).padTop(10f).padBottom(4f).left();
            table.row();
        }
    }

    private static class SubHeaderSetting extends SettingsMenuDialog.SettingsTable.Setting{
        private final String titleKeyOrText;

        public SubHeaderSetting(String titleKeyOrText){
            super("rbm-subheader");
            this.titleKeyOrText = titleKeyOrText;
        }

        @Override
        public void add(SettingsMenuDialog.SettingsTable table){
            table.row();
            table.add(titleKeyOrText.startsWith("@") ? Core.bundle.get(titleKeyOrText.substring(1)) : titleKeyOrText)
                .color(Color.gray)
                .padTop(8f)
                .padBottom(2f)
                .left()
                .growX()
                .minWidth(0f)
                .wrap();
            table.row();
        }
    }

    private static class AdvancedButtonSetting extends SettingsMenuDialog.SettingsTable.Setting{
        private final RadialBuildMenuMod mod;

        public AdvancedButtonSetting(RadialBuildMenuMod mod){
            super("rbm-advanced");
            this.mod = mod;
        }

        @Override
        public void add(SettingsMenuDialog.SettingsTable table){
            float prefWidth = prefWidth();
            Table root = table.table(Tex.button, t -> {
                t.left().margin(10f);
                t.image(mindustry.gen.Icon.settings).size(20f).padRight(8f);
                t.add(title, Styles.outlineLabel).left().growX().minWidth(0f).wrap();

                TextButton btn = t.button("@rbm.advanced.open", Styles.flatt, mod::showAdvancedDialog)
                    .width(190f)
                    .height(40f)
                    .padLeft(10f)
                    .get();

                btn.update(() -> btn.setDisabled(!Core.settings.getBool(keyProMode, false)));
            }).width(prefWidth).padTop(6f).get();

            addDesc(root);
            table.row();
        }
    }

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

                Image stripe = t.image(Tex.whiteui).size(4f, 32f).padRight(10f).get();
                stripe.setScaling(Scaling.stretch);
                stripe.update(() -> stripe.setColor(readHudColor()));

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

                 Image stripe = t.image(Tex.whiteui).size(4f, 32f).padRight(10f).get();
                 stripe.setScaling(Scaling.stretch);
                 stripe.update(() -> stripe.setColor(readHudColor()));
 
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
                    Image stripe = top.image(Tex.whiteui).size(4f, 20f).padRight(10f).get();
                    stripe.setScaling(Scaling.stretch);
                    stripe.update(() -> stripe.setColor(readHudColor()));
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

                Image stripe = t.image(Tex.whiteui).size(4f, 18f).padRight(10f).get();
                stripe.setScaling(Scaling.stretch);
                stripe.update(() -> stripe.setColor(accent == null ? Pal.accent : accent.get()));

                if(icon != null) t.image(icon).size(18f).padRight(6f);
                t.add(titleText).color(Color.gray).left().growX().minWidth(0f).wrap();
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
                Image stripe = t.image(Tex.whiteui).size(4f, 32f).padRight(10f).get();
                stripe.setScaling(Scaling.stretch);
                stripe.update(() -> stripe.setColor(readHudColor()));
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
                Image stripe = t.image(Tex.whiteui).size(4f, 32f).padRight(10f).get();
                stripe.setScaling(Scaling.stretch);
                stripe.update(() -> stripe.setColor(mod.readHudColor()));
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

                Image stripe = t.image(Tex.whiteui).size(4f, 32f).padRight(10f).get();
                stripe.setScaling(Scaling.stretch);
                stripe.update(() -> stripe.setColor(readHudColor()));

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
        if(planetErekir.equals(planetName)) return keyPlanetErekirSlotPrefix;
        if(planetSerpulo.equals(planetName)) return keyPlanetSerpuloSlotPrefix;
        if(planetSun.equals(planetName)) return keyPlanetSunSlotPrefix;
        return "";
    }

    private String timePlanetPrefix(String planetName){
        if(planetErekir.equals(planetName)) return keyTimeErekirSlotPrefix;
        if(planetSerpulo.equals(planetName)) return keyTimeSerpuloSlotPrefix;
        if(planetSun.equals(planetName)) return keyTimeSunSlotPrefix;
        return "";
    }

    private Block contextSlotBlock(int slot){
        updateConditionalState();

        if(condAfterActive){
            Block b = slotBlock(keyCondAfterSlotPrefix, slot);
            if(b != null) return b;
        }else if(condInitActive){
            Block b = slotBlock(keyCondInitialSlotPrefix, slot);
            if(b != null) return b;
        }

        String planet = currentPlanetName();

        if(timeRuleActive()){
            String tp = timePlanetPrefix(planet);
            if(!tp.isEmpty()){
                Block b = slotBlock(tp, slot);
                if(b != null) return b;
            }
            Block time = slotBlock(keyTimeSlotPrefix, slot);
            if(time != null) return time;
        }else{
            String pp = planetPrefix(planet);
            if(!pp.isEmpty()){
                Block b = slotBlock(pp, slot);
                if(b != null) return b;
            }
        }

        return slotBlock(keySlotPrefix, slot);
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

    private float condVar(String name){
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
        root.put("schema", 3);

        root.put("hudScale", Core.settings.getInt(keyHudScale, 100));
        root.put("hudAlpha", Core.settings.getInt(keyHudAlpha, 100));
        root.put("innerRadius", Core.settings.getInt(keyInnerRadius, 80));
        root.put("outerRadius", Core.settings.getInt(keyOuterRadius, 140));
        root.put("iconScale", Core.settings.getInt(keyIconScale, 100));
        root.put("backStrength", Core.settings.getInt(keyBackStrength, 22));
        root.put("ringAlpha", Core.settings.getInt(keyRingAlpha, 65));
        root.put("ringStroke", Core.settings.getInt(keyRingStroke, 2));
        root.put("hudColor", normalizeHex(Core.settings.getString(keyHudColor, defaultHudColorHex())));
        root.put("proMode", Core.settings.getBool(keyProMode, false));

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
            if(root.has("proMode")) Core.settings.put(keyProMode, root.getBool("proMode", false));

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
        if(mobile) return;
        if(ui == null || ui.hudGroup == null) return;

        if(ui.hudGroup.find(overlayName) != null) return;

        RadialHud hud = new RadialHud(this);
        hud.name = overlayName;
        hud.touchable = Touchable.disabled;
        ui.hudGroup.addChild(hud);
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
        private final int[] innerIndices = new int[slotsPerRing];
        private final int[] outerIndices = new int[slotsPerRing];
        private int innerCount;
        private int outerCount;

        public RadialHud(RadialBuildMenuMod mod){
            this.mod = mod;
        }

        @Override
        public void act(float delta){
            super.act(delta);

            if(parent != null){
                setBounds(0f, 0f, parent.getWidth(), parent.getHeight());
            }else{
                setSize(Core.graphics.getWidth(), Core.graphics.getHeight());
            }

            if(active){
                if(!canStayActive()){
                    active = false;
                    hovered = -1;
                    return;
                }

                if(Core.settings.getBool(keyCenterScreen, false)){
                    centerX = getWidth() / 2f;
                    centerY = getHeight() / 2f;
                }

                updateHovered();

                if(Core.input.keyRelease(radialMenu)){
                    commitSelection();
                    active = false;
                    hovered = -1;
                }else if(!Core.input.keyDown(radialMenu)){
                    // failsafe: if focus changed and no release is received
                    active = false;
                    hovered = -1;
                }
            }else{
                if(canActivate() && Core.input.keyTap(radialMenu)){
                    begin();
                }
            }
        }

        @Override
        public void draw(){
            if(!active) return;

            float alpha = parentAlpha * Mathf.clamp(Core.settings.getInt(keyHudAlpha, 100) / 100f);
            float scale = Mathf.clamp(Core.settings.getInt(keyHudScale, 100) / 100f, 0.1f, 5f);
            float innerSetting = Core.settings.getInt(keyInnerRadius, 80);
            float outerSetting = Core.settings.getInt(keyOuterRadius, 140);
            float radiusScale = Mathf.clamp((innerSetting / 80f + outerSetting / 140f) / 2f, 0.5f, 3f);

            float iconSizeScale = Mathf.clamp(Core.settings.getInt(keyIconScale, 100) / 100f, 0.2f, 5f);
            float iconSize = Scl.scl(46f) * scale * radiusScale * iconSizeScale;
            float innerRadius = Scl.scl(Core.settings.getInt(keyInnerRadius, 80)) * scale;
            float outerRadius = Scl.scl(Core.settings.getInt(keyOuterRadius, 140)) * scale;
            outerRadius = Math.max(outerRadius, innerRadius + iconSize * 1.15f);
            float slotBack = iconSize / 2f + Scl.scl(10f) * scale;
            float strokeNorm = Scl.scl(1.6f) * scale;
            float strokeHover = Scl.scl(2.4f) * scale;

            Draw.z(1000f);

            hudColor.set(mod.readHudColor());

            // soft background disc around the cursor
            float backStrength = Mathf.clamp(Core.settings.getInt(keyBackStrength, 22) / 100f);
            Draw.color(hudColor, backStrength * alpha);
            float backRadius = (outerActive ? outerRadius : innerRadius) + iconSize * 0.75f;
            Fill.circle(centerX, centerY, backRadius);

            // ring
            float ringAlpha = Mathf.clamp(Core.settings.getInt(keyRingAlpha, 65) / 100f);
            Draw.color(Pal.accent, ringAlpha * alpha);
            Lines.stroke(Scl.scl(Core.settings.getInt(keyRingStroke, 2)) * scale);
            Lines.circle(centerX, centerY, innerRadius);
            if(outerActive){
                Lines.circle(centerX, centerY, outerRadius);
            }

            // draw inner ring slots (only configured)
            for(int order = 0; order < innerCount; order++){
                int slotIndex = innerIndices[order];
                float angle = angleForOrder(order, innerCount);
                float px = centerX + Mathf.cosDeg(angle) * innerRadius;
                float py = centerY + Mathf.sinDeg(angle) * innerRadius;

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
            float scale = Mathf.clamp(Core.settings.getInt(keyHudScale, 100) / 100f, 0.1f, 5f);
            float innerSetting = Core.settings.getInt(keyInnerRadius, 80);
            float outerSetting = Core.settings.getInt(keyOuterRadius, 140);
            float radiusScale = Mathf.clamp((innerSetting / 80f + outerSetting / 140f) / 2f, 0.5f, 3f);
            float iconSizeScale = Mathf.clamp(Core.settings.getInt(keyIconScale, 100) / 100f, 0.2f, 5f);
            float iconSize = Scl.scl(46f) * scale * radiusScale * iconSizeScale;
            float innerRadius = Scl.scl(innerSetting) * scale;
            float outerRadius = Scl.scl(outerSetting) * scale;
            outerRadius = Math.max(outerRadius, innerRadius + iconSize * 1.15f);
            float hit = iconSize / 2f + Scl.scl(Math.max(0, Core.settings.getInt(keyHoverPadding, 12))) * scale;
            float hit2 = hit * hit;

            float mx = Core.input.mouseX();
            float my = Core.input.mouseY();

            // hover hit-test (inner + outer)
            int bestSlot = -1;
            float bestDst2 = hit2;

            for(int order = 0; order < innerCount; order++){
                int slotIndex = innerIndices[order];
                float angle = angleForOrder(order, innerCount);
                float px = centerX + Mathf.cosDeg(angle) * innerRadius;
                float py = centerY + Mathf.sinDeg(angle) * innerRadius;
                float dx = mx - px;
                float dy = my - py;
                float dst2 = dx * dx + dy * dy;
                if(dst2 <= bestDst2){
                    bestDst2 = dst2;
                    bestSlot = slotIndex;
                }
            }

            if(outerActive){
                for(int order = 0; order < outerCount; order++){
                    int slotIndex = outerIndices[order];
                    float angle = angleForOrder(order, outerCount);
                    float px = centerX + Mathf.cosDeg(angle) * outerRadius;
                    float py = centerY + Mathf.sinDeg(angle) * outerRadius;
                    float dx = mx - px;
                    float dy = my - py;
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
            float dx = mx - centerX;
            float dy = my - centerY;
            float deadzone = iconSize * Mathf.clamp(Core.settings.getInt(keyDeadzoneScale, 35) / 100f);
            if(dx * dx + dy * dy < deadzone * deadzone) return -1;

            if(outerActive){
                // only applies to outer ring; inner ring requires hover
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

    private static float prefWidth(){
        // slightly wider so long texts don't get clipped in settings dialogs
        return Math.min(Core.graphics.getWidth() / 1.02f, 980f);
    }

    private static class WideSliderSetting extends SettingsMenuDialog.SettingsTable.Setting{
        private final int def, min, max, step;
        private final SettingsMenuDialog.StringProcessor sp;

        public WideSliderSetting(String name, int def, int min, int max, int step, SettingsMenuDialog.StringProcessor sp){
            super(name);
            this.def = def;
            this.min = min;
            this.max = max;
            this.step = step;
            this.sp = sp;
        }

        @Override
        public void add(SettingsMenuDialog.SettingsTable table){
            arc.scene.ui.Slider slider = new arc.scene.ui.Slider(min, max, step, false);
            slider.setValue(Core.settings.getInt(name, def));

            arc.scene.ui.Label value = new arc.scene.ui.Label("", Styles.outlineLabel);

            Table content = new Table();
            content.add(title, Styles.outlineLabel).left().growX().minWidth(0f).wrap();
            content.add(value).padLeft(10f).right();
            content.margin(3f, 33f, 3f, 33f);
            content.touchable = Touchable.disabled;

            slider.changed(() -> {
                Core.settings.put(name, (int)slider.getValue());
                value.setText(sp.get((int)slider.getValue()));
            });

            slider.change();

            // leave room for the vertical scrollbar on the right side
            addDesc(table.stack(slider, content).width(prefWidth() - 64f).left().padTop(4f).get());
            table.row();
        }
    }

    private interface Expr{
        /** Returns 0 for false, non-zero for true. */
        float eval(RadialBuildMenuMod ctx);
    }

    private static class ConditionParser{
        private final String s;
        private int i;

        private ConditionParser(String s){
            this.s = s == null ? "" : s;
        }

        public static Expr parse(String s){
            ConditionParser p = new ConditionParser(s);
            Expr out = p.parseOr();
            p.skipWs();
            if(!p.eof()){
                throw new IllegalArgumentException("Unexpected trailing input at " + p.i);
            }
            return out;
        }

        private Expr parseOr(){
            Expr left = parseXor();
            while(true){
                skipWs();
                if(match("||")){
                    Expr right = parseXor();
                    left = new OrExpr(left, right);
                    continue;
                }
                return left;
            }
        }

        private Expr parseXor(){
            Expr left = parseAnd();
            while(true){
                skipWs();
                if(matchWord("xor")){
                    Expr right = parseAnd();
                    left = new XorExpr(left, right);
                    continue;
                }
                return left;
            }
        }

        private Expr parseAnd(){
            Expr left = parseCompare();
            while(true){
                skipWs();
                if(match("&&")){
                    Expr right = parseCompare();
                    left = new AndExpr(left, right);
                    continue;
                }
                return left;
            }
        }

        private Expr parseCompare(){
            Expr left = parseUnary();
            while(true){
                skipWs();

                if(match(">=")){
                    Expr right = parseUnary();
                    left = new CmpExpr(left, right, CmpOp.gte);
                }else if(match("<=")){
                    Expr right = parseUnary();
                    left = new CmpExpr(left, right, CmpOp.lte);
                }else if(match("==")){
                    Expr right = parseUnary();
                    left = new CmpExpr(left, right, CmpOp.eq);
                }else if(match("!=")){
                    Expr right = parseUnary();
                    left = new CmpExpr(left, right, CmpOp.neq);
                }else if(match(">")){
                    Expr right = parseUnary();
                    left = new CmpExpr(left, right, CmpOp.gt);
                }else if(match("<")){
                    Expr right = parseUnary();
                    left = new CmpExpr(left, right, CmpOp.lt);
                }else{
                    return left;
                }
            }
        }

        private Expr parseUnary(){
            skipWs();
            if(match("!")){
                return new NotExpr(parseUnary());
            }
            if(match("-")){
                return new NegExpr(parseUnary());
            }
            return parsePrimary();
        }

        private Expr parsePrimary(){
            skipWs();
            if(match("(")){
                Expr e = parseOr();
                skipWs();
                if(!match(")")) throw new IllegalArgumentException("Missing ')' at " + i);
                return e;
            }

            if(peek() == '@'){
                i++;
                String name = readIdent();
                if(name.isEmpty()) throw new IllegalArgumentException("Empty variable name at " + i);
                return new VarExpr(name);
            }

            if(isDigit(peek()) || peek() == '.'){
                String num = readNumber();
                try{
                    return new NumExpr(Float.parseFloat(num));
                }catch(Throwable t){
                    throw new IllegalArgumentException("Bad number: " + num);
                }
            }

            throw new IllegalArgumentException("Unexpected token at " + i);
        }

        private String readIdent(){
            int start = i;
            while(!eof()){
                char c = s.charAt(i);
                boolean ok = (c >= 'a' && c <= 'z')
                    || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9')
                    || c == '_' || c == '-';
                if(!ok) break;
                i++;
            }
            return s.substring(start, i);
        }

        private String readNumber(){
            int start = i;
            boolean dot = false;
            while(!eof()){
                char c = s.charAt(i);
                if(c == '.'){
                    if(dot) break;
                    dot = true;
                    i++;
                    continue;
                }
                if(!isDigit(c)) break;
                i++;
            }
            return s.substring(start, i);
        }

        private boolean match(String lit){
            if(lit == null || lit.isEmpty()) return false;
            if(i + lit.length() > s.length()) return false;
            if(s.regionMatches(i, lit, 0, lit.length())){
                i += lit.length();
                return true;
            }
            return false;
        }

        private boolean matchWord(String word){
            if(word == null || word.isEmpty()) return false;
            int len = word.length();
            if(i + len > s.length()) return false;
            if(!s.regionMatches(true, i, word, 0, len)) return false;

            // word boundary
            char before = i > 0 ? s.charAt(i - 1) : ' ';
            char after = (i + len) < s.length() ? s.charAt(i + len) : ' ';
            if(isIdentChar(before) || isIdentChar(after)) return false;

            i += len;
            return true;
        }

        private boolean isIdentChar(char c){
            return (c >= 'a' && c <= 'z')
                || (c >= 'A' && c <= 'Z')
                || (c >= '0' && c <= '9')
                || c == '_' || c == '-';
        }

        private void skipWs(){
            while(!eof()){
                char c = s.charAt(i);
                if(c != ' ' && c != '\t' && c != '\n' && c != '\r') break;
                i++;
            }
        }

        private char peek(){
            return eof() ? '\0' : s.charAt(i);
        }

        private boolean eof(){
            return i >= s.length();
        }

        private boolean isDigit(char c){
            return c >= '0' && c <= '9';
        }
    }

    private static class NumExpr implements Expr{
        private final float v;
        NumExpr(float v){ this.v = v; }
        @Override public float eval(RadialBuildMenuMod ctx){ return v; }
    }

    private static class VarExpr implements Expr{
        private final String name;
        VarExpr(String name){ this.name = name; }
        @Override public float eval(RadialBuildMenuMod ctx){ return ctx.condVar(name); }
    }

    private static class NegExpr implements Expr{
        private final Expr inner;
        NegExpr(Expr inner){ this.inner = inner; }
        @Override public float eval(RadialBuildMenuMod ctx){ return -inner.eval(ctx); }
    }

    private static class NotExpr implements Expr{
        private final Expr inner;
        NotExpr(Expr inner){ this.inner = inner; }
        @Override public float eval(RadialBuildMenuMod ctx){ return inner.eval(ctx) != 0f ? 0f : 1f; }
    }

    private static class AndExpr implements Expr{
        private final Expr a, b;
        AndExpr(Expr a, Expr b){ this.a = a; this.b = b; }
        @Override public float eval(RadialBuildMenuMod ctx){ return (a.eval(ctx) != 0f && b.eval(ctx) != 0f) ? 1f : 0f; }
    }

    private static class OrExpr implements Expr{
        private final Expr a, b;
        OrExpr(Expr a, Expr b){ this.a = a; this.b = b; }
        @Override public float eval(RadialBuildMenuMod ctx){ return (a.eval(ctx) != 0f || b.eval(ctx) != 0f) ? 1f : 0f; }
    }

    private static class XorExpr implements Expr{
        private final Expr a, b;
        XorExpr(Expr a, Expr b){ this.a = a; this.b = b; }
        @Override public float eval(RadialBuildMenuMod ctx){
            boolean av = a.eval(ctx) != 0f;
            boolean bv = b.eval(ctx) != 0f;
            return (av ^ bv) ? 1f : 0f;
        }
    }

    private enum CmpOp{ gt, gte, lt, lte, eq, neq }

    private static class CmpExpr implements Expr{
        private final Expr a, b;
        private final CmpOp op;
        CmpExpr(Expr a, Expr b, CmpOp op){ this.a = a; this.b = b; this.op = op; }
        @Override public float eval(RadialBuildMenuMod ctx){
            float av = a.eval(ctx);
            float bv = b.eval(ctx);
            boolean out;
            switch(op){
                case gt: out = av > bv; break;
                case gte: out = av >= bv; break;
                case lt: out = av < bv; break;
                case lte: out = av <= bv; break;
                case eq: out = av == bv; break;
                case neq: out = av != bv; break;
                default: out = false; break;
            }
            return out ? 1f : 0f;
        }
    }
}
