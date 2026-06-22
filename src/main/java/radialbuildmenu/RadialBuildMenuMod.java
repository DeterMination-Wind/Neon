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
import arc.struct.Seq;
import arc.util.Align;
import arc.util.Log;
import arc.util.Scaling;
import arc.util.serialization.Jval.Jtype;
import arc.util.Strings;
import arc.util.Time;
import arc.util.serialization.Jval;
import arc.util.serialization.Jval.Jformat;
import mdtxcompat.LegacyMindustryXGuard;
import mdtxcompat.OverlayUiBridge;
import mindustry.game.EventType.ClientLoadEvent;
import mindustry.game.EventType.WorldLoadEvent;
import mindustry.game.Team;
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
    private static final int maxWheelProfiles = 32;
    private static final int maxRuleSlotGroups = 64;

    private static final String planetErekir = "erekir";
    private static final String planetSerpulo = "serpulo";
    private static final String planetSun = "sun";

    private static final String keyEnabled = "rbm-enabled";
    private static final String keyHudScale = "rbm-hudscale";
    private static final String keyHudAlpha = "rbm-hudalpha";
    private static final String keyPersistentHud = "rbm-persistent-hud";
    private static final String keyPersistentHudAlpha = "rbm-persistent-hud-alpha";
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
    private static final String keyWheelProfiles = "rbm-wheel-profiles";
    private static final String keyWheelNextId = "rbm-wheel-next-id";
    private static final String keyActiveWheelProfileId = "rbm-wheel-active-id";
    private static final String keyRuleSlotGroups = "rbm-rule-slot-groups";
    private static final String keyRuleSlotGroupNextId = "rbm-rule-slot-group-next-id";

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

    private final OverlayUiBridge xOverlayUi;
    private OverlayUiBridge.OverlayWindowHandle xMobileToggleWindow;
    private final Seq<WheelProfile> wheelProfiles = new Seq<>();
    private boolean wheelProfilesLoaded;
    private final Seq<RuleSlotGroup> ruleSlotGroups = new Seq<>();
    private boolean ruleSlotGroupsLoaded;
    private RuleSlotGroup activeRuleSlotGroup;
    private float ruleSlotGroupLastEval = -9999f;

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
        this(vanillaOverlayUi());
    }

    protected RadialBuildMenuMod(OverlayUiBridge overlayUi){
        xOverlayUi = overlayUi;
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

    private static OverlayUiBridge vanillaOverlayUi(){
        LegacyMindustryXGuard.rejectLegacyMindustryX("Radial Build Menu");
        return OverlayUiBridge.UNSUPPORTED;
    }

    private void ensureDefaults(){
        GithubUpdateCheck.applyDefaults();
        Core.settings.defaults(keyEnabled, true);
        Core.settings.defaults(keyHudScale, 100);
        Core.settings.defaults(keyHudAlpha, 100);
        Core.settings.defaults(keyPersistentHud, false);
        Core.settings.defaults(keyPersistentHudAlpha, 35);
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
        Core.settings.defaults(keyWheelProfiles, "");
        Core.settings.defaults(keyWheelNextId, 1);
        Core.settings.defaults(keyActiveWheelProfileId, 0);
        Core.settings.defaults(keyRuleSlotGroups, "");
        Core.settings.defaults(keyRuleSlotGroupNextId, 1);

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
        ensureWheelProfilesLoaded();
        ensureRuleSlotGroupsLoaded();
    }

    private void registerSettings(){
        if(ui == null || ui.settings == null) return;
        if(bekBundled) return;


        if(!bekBundled) ui.settings.addCategory("@rbm.category", this::bekBuildSettings);
    }
    /** Populates a {@link mindustry.ui.dialogs.SettingsMenuDialog.SettingsTable} with this mod's settings. */
    public void bekBuildSettings(SettingsMenuDialog.SettingsTable table){
            ensureWheelProfilesLoaded();
            ensureRuleSlotGroupsLoaded();

            table.checkPref(keyEnabled, true);
            table.pref(new HotkeySetting());

            table.sliderPref(keyHudScale, 100, 50, 200, 5, v -> v + "%");
            table.sliderPref(keyHudAlpha, 100, 0, 100, 5, v -> v + "%");
            table.pref(new IconCheckSetting(keyPersistentHud, false, null));
            table.pref(new IconSliderSetting(keyPersistentHudAlpha, 35, 0, 100, 5, null, v -> v + "%"));
            table.sliderPref(keyInnerRadius, 80, 40, 200, 5, v -> v + "px");
            table.sliderPref(keyOuterRadius, 140, 60, 360, 5, v -> v + "px");
            table.pref(new HudColorSetting());
            table.checkPref(keyCenterScreen, false);
            table.checkPref(keyShowEmptySlots, false);
            table.checkPref(keyProMode, false);
            table.pref(new WheelProfilesButtonSetting(RadialBuildMenuMod.this));

            for(int i = 0; i < maxSlots; i++) table.pref(new SlotSetting(i, keySlotPrefix, "rbm.setting.slot"));

            table.pref(new TimeMinutesSetting());
            for(int i = 0; i < maxSlots; i++) table.pref(new SlotSetting(i, keyTimeSlotPrefix, "rbm.setting.timeslot"));

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

    void showWheelProfilesDialog(){
        ensureWheelProfilesLoaded();

        BaseDialog dialog = new BaseDialog("@rbm.wheels.title");
        dialog.addCloseButton();

        final WheelProfile[] selected = {wheelProfiles.isEmpty() ? null : wheelProfiles.first()};
        Table left = new Table();
        Table right = new Table();

        Runnable[] rebuildRight = new Runnable[1];
        Runnable[] rebuildLeft = new Runnable[1];

        rebuildRight[0] = () -> {
            right.clearChildren();
            right.top().left();

            WheelProfile profile = selected[0];
            if(profile == null){
                right.add("@rbm.wheels.none").pad(20f);
                return;
            }

            right.table(Tex.button, header -> {
                header.left().margin(10f);
                header.add("@rbm.wheels.name").width(110f).left();
                TextField name = new TextField(profile.name);
                name.setMessageText(Core.bundle.get("rbm.wheels.name.placeholder"));
                name.changed(() -> {
                    profile.name = normalizeWheelName(name.getText(), profile.id);
                    saveWheelProfiles();
                    rebuildLeft[0].run();
                });
                header.add(name).growX().minWidth(0f);
            }).growX().padBottom(6f);
            right.row();

            right.table(Tex.button, keys -> {
                keys.left().margin(10f);
                keys.add("@rbm.wheels.hotkey").width(110f).left();
                keys.label(() -> keyName(profile.key)).color(profile.key == KeyCode.unset ? Color.gray : Pal.accent).growX().left();
                keys.button("@rbm.wheels.hotkey.set", Styles.flatt, () -> showWheelKeyDialog(profile, () -> {
                    saveWheelProfiles();
                    rebuildLeft[0].run();
                })).width(120f).height(40f).padLeft(8f);
                keys.button("@rbm.wheels.hotkey.clear", Styles.flatt, () -> {
                    profile.key = KeyCode.unset;
                    saveWheelProfiles();
                    rebuildLeft[0].run();
                }).width(120f).height(40f).padLeft(8f);
            }).growX().padBottom(6f);
            right.row();

            right.table(actions -> {
                actions.left();
                actions.button("@rbm.wheels.copy", Styles.flatt, () -> {
                    WheelProfile copy = copyWheelProfile(profile);
                    if(copy != null){
                        selected[0] = copy;
                        rebuildLeft[0].run();
                        rebuildRight[0].run();
                    }
                }).height(40f).minWidth(120f);
                actions.button("@rbm.wheels.delete", Styles.flatt, () -> {
                    if(wheelProfiles.size <= 1){
                        ui.showInfoFade("@rbm.wheels.delete.last");
                        return;
                    }
                    ui.showConfirm("@confirm", "@rbm.wheels.delete.confirm", () -> {
                        if(Core.settings.getInt(keyActiveWheelProfileId, 0) == profile.id){
                            setActiveWheelProfile(null);
                        }
                        wheelProfiles.remove(profile, true);
                        saveWheelProfiles();
                        selected[0] = wheelProfiles.isEmpty() ? null : wheelProfiles.first();
                        rebuildLeft[0].run();
                        rebuildRight[0].run();
                    });
                }).height(40f).minWidth(120f).padLeft(8f);
            }).growX().padBottom(8f);
            right.row();

            right.add("@rbm.wheels.slots").color(Pal.accent).left().padTop(4f).padBottom(4f);
            right.row();

            for(int i = 0; i < maxSlots; i++){
                final int slot = i;
                right.table(Tex.button, row -> {
                    row.left().margin(8f);
                    row.add(Core.bundle.format("rbm.setting.slot", slot + 1)).width(105f).left();

                    row.table(info -> {
                        info.left();
                        Image icon = info.image(Tex.clear).size(32f).padRight(8f).get();
                        icon.setScaling(Scaling.fit);
                        info.labelWrap(() -> {
                            Block block = wheelSlotBlock(profile, slot);
                            return block == null ? Core.bundle.get("rbm.setting.none") : block.localizedName;
                        }).left().growX().fillX().minWidth(0f);

                        final Block[] last = {null};
                        info.update(() -> {
                            Block block = wheelSlotBlock(profile, slot);
                            if(block == last[0]) return;
                            last[0] = block;
                            icon.setDrawable(block == null ? Tex.clear : new TextureRegionDrawable(block.uiIcon));
                        });
                    }).left().growX().minWidth(0f);

                    row.button("@rbm.setting.set", Styles.flatt, () -> showBlockSelectDialog(block -> {
                        profile.slots[slot] = block == null ? "" : block.name;
                        saveWheelProfiles();
                    })).width(110f).height(40f).padLeft(8f);
                }).growX().padTop(3f);
                right.row();
            }
        };

        rebuildLeft[0] = () -> {
            left.clearChildren();
            left.top().left();

            for(WheelProfile profile : wheelProfiles){
                Table row = left.table(Tex.button, b -> {
                    b.left().margin(8f);
                    b.add(profile.displayName()).growX().left().minWidth(0f).wrap();
                    b.add(keyName(profile.key)).color(profile.key == KeyCode.unset ? Color.gray : Pal.accent).padLeft(8f);
                }).growX().height(48f).padBottom(3f).get();
                row.clicked(() -> {
                    selected[0] = profile;
                    rebuildLeft[0].run();
                    rebuildRight[0].run();
                });
                row.update(() -> row.color.set(selected[0] == profile ? Pal.accent : Color.white));
                left.row();
            }

            left.button("@rbm.wheels.add", Styles.flatt, () -> {
                WheelProfile created = addWheelProfile();
                selected[0] = created;
                rebuildLeft[0].run();
                rebuildRight[0].run();
            }).growX().height(44f).disabled(b -> wheelProfiles.size >= maxWheelProfiles).padTop(6f);
        };

        rebuildLeft[0].run();
        rebuildRight[0].run();

        ScrollPane leftPane = new ScrollPane(left);
        leftPane.setFadeScrollBars(false);
        leftPane.setScrollingDisabled(true, false);
        ScrollPane rightPane = new ScrollPane(right);
        rightPane.setFadeScrollBars(false);
        rightPane.setScrollingDisabled(true, false);

        dialog.cont.table(root -> {
            root.add(leftPane).width(260f).growY().minHeight(430f).padRight(8f);
            root.add(rightPane).width(Math.max(520f, prefWidth() - 280f)).growY().minHeight(430f);
        }).grow();

        dialog.show();
    }

    private Seq<WheelProfile> wheelProfiles(){
        ensureWheelProfilesLoaded();
        return wheelProfiles;
    }

    private void ensureWheelProfilesLoaded(){
        if(wheelProfilesLoaded) return;
        wheelProfilesLoaded = true;
        wheelProfiles.clear();

        String raw = Core.settings.getString(keyWheelProfiles, "");
        if(raw != null && !raw.trim().isEmpty()){
            try{
                importWheelProfilesValue(Jval.read(raw), false);
            }catch(Throwable ignored){
                wheelProfiles.clear();
            }
        }

        if(wheelProfiles.isEmpty()){
            addLegacyDefaultWheelProfiles();
            saveWheelProfiles();
        }
    }

    private void addLegacyDefaultWheelProfiles(){
        wheelProfiles.clear();
        wheelProfiles.add(WheelProfile.fromPrefix(nextWheelId(), Core.bundle.get("rbm.wheels.default"), KeyCode.unset, keySlotPrefix, this));
        WheelProfile power = WheelProfile.powerPreset(nextWheelId(), Core.bundle.get("rbm.wheels.power"));
        power.key = KeyCode.num5;
        wheelProfiles.add(power);
    }

    private int nextWheelId(){
        int next = Math.max(1, Core.settings.getInt(keyWheelNextId, 1));
        Core.settings.put(keyWheelNextId, next + 1);
        return next;
    }

    private void normalizeNextWheelId(){
        int next = 1;
        for(WheelProfile profile : wheelProfiles){
            next = Math.max(next, profile.id + 1);
        }
        Core.settings.put(keyWheelNextId, next);
    }

    private WheelProfile addWheelProfile(){
        ensureWheelProfilesLoaded();
        if(wheelProfiles.size >= maxWheelProfiles){
            ui.showInfoFade("@rbm.wheels.max");
            return wheelProfiles.isEmpty() ? null : wheelProfiles.peek();
        }
        WheelProfile profile = new WheelProfile(nextWheelId(), Core.bundle.format("rbm.wheels.new", wheelProfiles.size + 1));
        wheelProfiles.add(profile);
        saveWheelProfiles();
        return profile;
    }

    private WheelProfile copyWheelProfile(WheelProfile src){
        ensureWheelProfilesLoaded();
        if(src == null) return null;
        if(wheelProfiles.size >= maxWheelProfiles){
            ui.showInfoFade("@rbm.wheels.max");
            return null;
        }
        WheelProfile copy = src.copy(nextWheelId(), Core.bundle.format("rbm.wheels.copy.name", src.displayName()));
        wheelProfiles.add(copy);
        saveWheelProfiles();
        return copy;
    }

    private void saveWheelProfiles(){
        normalizeNextWheelId();
        Core.settings.put(keyWheelProfiles, exportWheelProfiles().toString(Jformat.plain));
    }

    private void setActiveWheelProfile(WheelProfile profile){
        Core.settings.put(keyActiveWheelProfileId, profile == null ? 0 : profile.id);
    }

    private WheelProfile activeWheelProfile(){
        ensureWheelProfilesLoaded();
        int id = Core.settings.getInt(keyActiveWheelProfileId, 0);
        if(id <= 0) return null;
        for(WheelProfile profile : wheelProfiles){
            if(profile.id == id) return profile;
        }
        Core.settings.put(keyActiveWheelProfileId, 0);
        return null;
    }

    private Jval exportWheelProfiles(){
        Jval arr = Jval.newArray();
        for(WheelProfile profile : wheelProfiles){
            arr.add(profile.toJson());
        }
        return arr;
    }

    private void importWheelProfilesValue(Jval value, boolean save){
        wheelProfiles.clear();
        if(value != null && value.isArray()){
            for(Jval child : value.asArray()){
                if(wheelProfiles.size >= maxWheelProfiles) break;
                WheelProfile profile = WheelProfile.fromJson(child);
                if(profile != null) wheelProfiles.add(profile);
            }
        }

        if(wheelProfiles.isEmpty()){
            addLegacyDefaultWheelProfiles();
        }

        normalizeNextWheelId();
        if(save) saveWheelProfiles();
    }

    private String normalizeWheelName(String value, int id){
        String name = value == null ? "" : value.trim();
        if(name.isEmpty()) return Core.bundle.format("rbm.wheels.fallback", id);
        return name;
    }

    private Block wheelSlotBlock(WheelProfile profile, int slot){
        if(profile == null || slot < 0 || slot >= maxSlots) return null;
        String name = profile.slots[slot];
        if(name == null) return null;
        name = name.trim();
        return name.isEmpty() ? null : content.block(name);
    }

    private String keyName(KeyCode key){
        return key == null || key == KeyCode.unset ? Core.bundle.get("rbm.wheels.hotkey.unset") : key.toString();
    }

    private WheelProfile tappedWheelProfile(){
        ensureWheelProfilesLoaded();
        for(WheelProfile profile : wheelProfiles){
            if(profile.key != null && profile.key != KeyCode.unset && Core.input.keyTap(profile.key)){
                return profile;
            }
        }
        return null;
    }

    private void showWheelKeyDialog(WheelProfile profile, Runnable changed){
        if(profile == null) return;

        Dialog dialog = new Dialog(Core.bundle.get("rbm.wheels.hotkey.press"));
        dialog.cont.add("@rbm.wheels.hotkey.press.detail").pad(12f).wrap().width(360f);

        dialog.addListener(new InputListener(){
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, KeyCode button){
                if(Core.app.isAndroid()) return false;
                setKey(button);
                return false;
            }

            @Override
            public boolean keyDown(InputEvent event, KeyCode keycode){
                if(keycode == KeyCode.escape || keycode == KeyCode.back){
                    dialog.hide();
                    return false;
                }
                setKey(keycode);
                return false;
            }

            private void setKey(KeyCode key){
                if(key == null) return;
                profile.key = key;
                warnWheelKeyConflict(profile, key);
                if(changed != null) changed.run();
                dialog.hide();
            }
        });

        dialog.show();
    }

    private void warnWheelKeyConflict(WheelProfile profile, KeyCode key){
        if(key == null || key == KeyCode.unset || ui == null) return;
        if(isNativeBlockNumberKey(key)){
            ui.showInfoFade(Core.bundle.format("rbm.wheels.hotkey.nativewarning", key.toString()));
            return;
        }
        for(WheelProfile other : wheelProfiles){
            if(other != profile && other.key == key){
                ui.showInfoFade(Core.bundle.format("rbm.wheels.hotkey.duplicate", other.displayName()));
                return;
            }
        }
    }

    private boolean isNativeBlockNumberKey(KeyCode key){
        return key == KeyCode.num1 || key == KeyCode.num2 || key == KeyCode.num3 || key == KeyCode.num4 || key == KeyCode.num5
            || key == KeyCode.num6 || key == KeyCode.num7 || key == KeyCode.num8 || key == KeyCode.num9 || key == KeyCode.num0;
    }

    private static class WheelProfile{
        int id;
        String name;
        KeyCode key = KeyCode.unset;
        final String[] slots = new String[maxSlots];

        WheelProfile(int id, String name){
            this.id = id;
            this.name = name == null ? "" : name;
            for(int i = 0; i < maxSlots; i++){
                slots[i] = "";
            }
        }

        String displayName(){
            String text = name == null ? "" : name.trim();
            return text.isEmpty() ? "#" + id : text;
        }

        WheelProfile copy(int id, String name){
            WheelProfile out = new WheelProfile(id, name);
            out.key = KeyCode.unset;
            for(int i = 0; i < maxSlots; i++){
                out.slots[i] = slots[i] == null ? "" : slots[i];
            }
            return out;
        }

        Jval toJson(){
            Jval root = Jval.newObject();
            root.put("id", id);
            root.put("name", displayName());
            root.put("key", key == null ? KeyCode.unset.name() : key.name());
            Jval arr = Jval.newArray();
            for(int i = 0; i < maxSlots; i++){
                arr.add(slots[i] == null ? "" : slots[i]);
            }
            root.put("slots", arr);
            return root;
        }

        static WheelProfile fromPrefix(int id, String name, KeyCode key, String prefix, RadialBuildMenuMod mod){
            WheelProfile profile = new WheelProfile(id, name);
            profile.key = key == null ? KeyCode.unset : key;
            for(int i = 0; i < maxSlots; i++){
                profile.slots[i] = mod.slotName(prefix, i);
            }
            return profile;
        }

        static WheelProfile powerPreset(int id, String name){
            WheelProfile profile = new WheelProfile(id, name);
            String[] power = {
                "power-node",
                "power-node-large",
                "battery",
                "battery-large",
                "combustion-generator",
                "thermal-generator",
                "steam-generator",
                "solar-panel",
                "large-solar-panel",
                "differential-generator",
                "rtg-generator",
                "thorium-reactor",
                "impact-reactor",
                "beam-node",
                "beam-tower",
                "turbine-condenser"
            };
            for(int i = 0; i < maxSlots && i < power.length; i++){
                profile.slots[i] = power[i];
            }
            return profile;
        }

        static WheelProfile fromJson(Jval value){
            if(value == null || !value.isObject()) return null;
            int id = Math.max(1, value.getInt("id", 1));
            WheelProfile profile = new WheelProfile(id, value.getString("name", ""));
            profile.key = parseKey(value.getString("key", KeyCode.unset.name()));

            Jval slots = value.get("slots");
            if(slots != null && slots.isArray()){
                int count = Math.min(slots.asArray().size, maxSlots);
                for(int i = 0; i < count; i++){
                    Jval slot = slots.asArray().get(i);
                    profile.slots[i] = slot == null || slot.isNull() ? "" : slot.asString().trim();
                }
            }
            return profile;
        }

        private static KeyCode parseKey(String raw){
            if(raw == null || raw.trim().isEmpty()) return KeyCode.unset;
            try{
                return KeyCode.valueOf(raw.trim());
            }catch(Throwable ignored){
                return KeyCode.unset;
            }
        }
    }

    void showRuleSlotGroupsDialog(){
        ensureRuleSlotGroupsLoaded();

        BaseDialog dialog = new BaseDialog("@rbm.rulegroups.title");
        dialog.addCloseButton();

        final RuleSlotGroup[] selected = {ruleSlotGroups.isEmpty() ? null : ruleSlotGroups.first()};
        Table left = new Table();
        Table right = new Table();

        Runnable[] rebuildRight = new Runnable[1];
        Runnable[] rebuildLeft = new Runnable[1];

        rebuildRight[0] = () -> {
            right.clearChildren();
            right.top().left();

            RuleSlotGroup group = selected[0];
            if(group == null){
                right.add("@rbm.rulegroups.none").pad(20f);
                return;
            }

            right.table(Tex.button, header -> {
                header.left().margin(10f);
                header.add("@rbm.rulegroups.name").width(110f).left();
                TextField name = new TextField(group.name);
                name.setMessageText(Core.bundle.get("rbm.rulegroups.name.placeholder"));
                name.changed(() -> {
                    group.name = normalizeRuleSlotGroupName(name.getText(), group.id);
                    saveRuleSlotGroups();
                    rebuildLeft[0].run();
                });
                header.add(name).growX().minWidth(0f);
            }).growX().padBottom(6f);
            right.row();

            right.table(Tex.button, cond -> {
                cond.top().left().margin(10f);
                cond.add("@rbm.rulegroups.condition").left().padBottom(4f);
                cond.row();
                TextArea expr = new TextArea(group.condition);
                expr.setMessageText(Core.bundle.get("rbm.rulegroups.condition.placeholder"));
                expr.changed(() -> {
                    group.condition = expr.getText() == null ? "" : expr.getText();
                    saveRuleSlotGroups();
                    rebuildLeft[0].run();
                });
                cond.add(expr).growX().minHeight(70f).padBottom(6f);
                cond.row();
                cond.add("@rbm.rulegroups.condition.help").left().growX().wrap().minWidth(0f);
            }).growX().padBottom(6f);
            right.row();

            right.table(actions -> {
                actions.left();
                TextButton up = actions.button("@rbm.rulegroups.up", Styles.flatt, () -> {
                    int index = ruleSlotGroups.indexOf(group, true);
                    if(index > 0){
                        ruleSlotGroups.swap(index, index - 1);
                        saveRuleSlotGroups();
                        rebuildLeft[0].run();
                    }
                }).height(40f).minWidth(100f).get();
                up.update(() -> up.setDisabled(ruleSlotGroups.indexOf(group, true) <= 0));

                TextButton down = actions.button("@rbm.rulegroups.down", Styles.flatt, () -> {
                    int index = ruleSlotGroups.indexOf(group, true);
                    if(index >= 0 && index < ruleSlotGroups.size - 1){
                        ruleSlotGroups.swap(index, index + 1);
                        saveRuleSlotGroups();
                        rebuildLeft[0].run();
                    }
                }).height(40f).minWidth(100f).padLeft(8f).get();
                down.update(() -> {
                    int index = ruleSlotGroups.indexOf(group, true);
                    down.setDisabled(index < 0 || index >= ruleSlotGroups.size - 1);
                });

                actions.button("@rbm.rulegroups.copy", Styles.flatt, () -> {
                    RuleSlotGroup copy = copyRuleSlotGroup(group);
                    if(copy != null){
                        selected[0] = copy;
                        rebuildLeft[0].run();
                        rebuildRight[0].run();
                    }
                }).height(40f).minWidth(100f).padLeft(8f);

                actions.button("@rbm.rulegroups.delete", Styles.flatt, () -> {
                    ui.showConfirm("@confirm", "@rbm.rulegroups.delete.confirm", () -> {
                        ruleSlotGroups.remove(group, true);
                        saveRuleSlotGroups();
                        selected[0] = ruleSlotGroups.isEmpty() ? null : ruleSlotGroups.first();
                        rebuildLeft[0].run();
                        rebuildRight[0].run();
                    });
                }).height(40f).minWidth(100f).padLeft(8f);
            }).growX().padBottom(8f);
            right.row();

            right.add("@rbm.rulegroups.slots").color(Pal.accent).left().padTop(4f).padBottom(4f);
            right.row();

            for(int i = 0; i < maxSlots; i++){
                final int slot = i;
                right.table(Tex.button, row -> {
                    row.left().margin(8f);
                    row.add(Core.bundle.format("rbm.setting.slot", slot + 1)).width(105f).left();

                    row.table(info -> {
                        info.left();
                        Image icon = info.image(Tex.clear).size(32f).padRight(8f).get();
                        icon.setScaling(Scaling.fit);
                        info.labelWrap(() -> {
                            Block block = ruleSlotGroupBlock(group, slot);
                            return block == null ? Core.bundle.get("rbm.setting.none") : block.localizedName;
                        }).left().growX().fillX().minWidth(0f);

                        final Block[] last = {null};
                        info.update(() -> {
                            Block block = ruleSlotGroupBlock(group, slot);
                            if(block == last[0]) return;
                            last[0] = block;
                            icon.setDrawable(block == null ? Tex.clear : new TextureRegionDrawable(block.uiIcon));
                        });
                    }).left().growX().minWidth(0f);

                    row.button("@rbm.setting.set", Styles.flatt, () -> showBlockSelectDialog(block -> {
                        group.slots[slot] = block == null ? "" : block.name;
                        saveRuleSlotGroups();
                    })).width(110f).height(40f).padLeft(8f);
                }).growX().padTop(3f);
                right.row();
            }
        };

        rebuildLeft[0] = () -> {
            left.clearChildren();
            left.top().left();

            for(RuleSlotGroup group : ruleSlotGroups){
                Table row = left.table(Tex.button, b -> {
                    b.left().margin(8f);
                    b.table(labels -> {
                        labels.left();
                        labels.add(group.displayName()).growX().left().minWidth(0f).wrap();
                        labels.row();
                        labels.labelWrap(() -> {
                            String cond = group.condition == null ? "" : group.condition.trim();
                            return cond.isEmpty() ? Core.bundle.get("rbm.rulegroups.condition.empty") : cond;
                        }).color(Color.gray).growX().left().minWidth(0f);
                    }).growX().minWidth(0f);
                }).growX().height(58f).padBottom(3f).get();
                row.clicked(() -> {
                    selected[0] = group;
                    rebuildLeft[0].run();
                    rebuildRight[0].run();
                });
                row.update(() -> row.color.set(selected[0] == group ? Pal.accent : Color.white));
                left.row();
            }

            left.button("@rbm.rulegroups.add", Styles.flatt, () -> {
                RuleSlotGroup created = addRuleSlotGroup();
                selected[0] = created;
                rebuildLeft[0].run();
                rebuildRight[0].run();
            }).growX().height(44f).disabled(b -> ruleSlotGroups.size >= maxRuleSlotGroups).padTop(6f);
        };

        rebuildLeft[0].run();
        rebuildRight[0].run();

        ScrollPane leftPane = new ScrollPane(left);
        leftPane.setFadeScrollBars(false);
        leftPane.setScrollingDisabled(true, false);
        ScrollPane rightPane = new ScrollPane(right);
        rightPane.setFadeScrollBars(false);
        rightPane.setScrollingDisabled(true, false);

        dialog.cont.table(root -> {
            root.add(leftPane).width(280f).growY().minHeight(430f).padRight(8f);
            root.add(rightPane).width(Math.max(520f, prefWidth() - 300f)).growY().minHeight(430f);
        }).grow();

        dialog.show();
    }

    private Seq<RuleSlotGroup> ruleSlotGroups(){
        ensureRuleSlotGroupsLoaded();
        return ruleSlotGroups;
    }

    private void ensureRuleSlotGroupsLoaded(){
        if(ruleSlotGroupsLoaded) return;
        ruleSlotGroupsLoaded = true;
        ruleSlotGroups.clear();

        String raw = Core.settings.getString(keyRuleSlotGroups, "");
        if(raw != null && !raw.trim().isEmpty()){
            try{
                importRuleSlotGroupsValue(Jval.read(raw), false);
            }catch(Throwable ignored){
                ruleSlotGroups.clear();
            }
        }else{
            addLegacyRuleSlotGroups();
            saveRuleSlotGroups();
        }
    }

    private int nextRuleSlotGroupId(){
        int next = Math.max(1, Core.settings.getInt(keyRuleSlotGroupNextId, 1));
        Core.settings.put(keyRuleSlotGroupNextId, next + 1);
        return next;
    }

    private void normalizeNextRuleSlotGroupId(){
        int next = 1;
        for(RuleSlotGroup group : ruleSlotGroups){
            next = Math.max(next, group.id + 1);
        }
        Core.settings.put(keyRuleSlotGroupNextId, next);
    }

    private RuleSlotGroup addRuleSlotGroup(){
        ensureRuleSlotGroupsLoaded();
        if(ruleSlotGroups.size >= maxRuleSlotGroups){
            ui.showInfoFade("@rbm.rulegroups.max");
            return ruleSlotGroups.isEmpty() ? null : ruleSlotGroups.peek();
        }
        RuleSlotGroup group = new RuleSlotGroup(nextRuleSlotGroupId(), Core.bundle.format("rbm.rulegroups.new", ruleSlotGroups.size + 1));
        ruleSlotGroups.add(group);
        saveRuleSlotGroups();
        return group;
    }

    private RuleSlotGroup copyRuleSlotGroup(RuleSlotGroup src){
        ensureRuleSlotGroupsLoaded();
        if(src == null) return null;
        if(ruleSlotGroups.size >= maxRuleSlotGroups){
            ui.showInfoFade("@rbm.rulegroups.max");
            return null;
        }
        RuleSlotGroup copy = src.copy(nextRuleSlotGroupId(), Core.bundle.format("rbm.rulegroups.copy.name", src.displayName()));
        int index = ruleSlotGroups.indexOf(src, true);
        if(index >= 0 && index < ruleSlotGroups.size - 1){
            ruleSlotGroups.insert(index + 1, copy);
        }else{
            ruleSlotGroups.add(copy);
        }
        saveRuleSlotGroups();
        return copy;
    }

    private void saveRuleSlotGroups(){
        normalizeNextRuleSlotGroupId();
        activeRuleSlotGroup = null;
        ruleSlotGroupLastEval = -9999f;
        Core.settings.put(keyRuleSlotGroups, exportRuleSlotGroups().toString(Jformat.plain));
    }

    private Jval exportRuleSlotGroups(){
        Jval arr = Jval.newArray();
        for(RuleSlotGroup group : ruleSlotGroups){
            arr.add(group.toJson());
        }
        return arr;
    }

    private void importRuleSlotGroupsValue(Jval value, boolean save){
        ruleSlotGroups.clear();
        if(value != null && value.isArray()){
            for(Jval child : value.asArray()){
                if(ruleSlotGroups.size >= maxRuleSlotGroups) break;
                RuleSlotGroup group = RuleSlotGroup.fromJson(child);
                if(group != null) ruleSlotGroups.add(group);
            }
        }

        normalizeNextRuleSlotGroupId();
        activeRuleSlotGroup = null;
        ruleSlotGroupLastEval = -9999f;
        if(save) saveRuleSlotGroups();
    }

    private void addLegacyRuleSlotGroups(){
        ruleSlotGroups.clear();

        if(Core.settings.getBool(keyCondAfterEnabled, false)){
            addLegacyRuleSlotGroup(
                Core.bundle.get("rbm.rulegroups.legacy.cond.after"),
                Core.settings.getString(keyCondAfterExpr, ""),
                keyCondAfterSlotPrefix
            );
        }
        if(Core.settings.getBool(keyCondEnabled, false)){
            addLegacyRuleSlotGroup(
                Core.bundle.get("rbm.rulegroups.legacy.cond.initial"),
                Core.settings.getString(keyCondInitialExpr, ""),
                keyCondInitialSlotPrefix
            );
        }

        int minutes = Core.settings.getInt(keyTimeMinutes, 0);
        if(minutes > 0){
            String timeExpr = "@second >= " + (minutes * 60);
            addLegacyRuleSlotGroup(Core.bundle.get("rbm.rulegroups.legacy.time.erekir"), timeExpr + " && planet == " + planetErekir, keyTimeErekirSlotPrefix);
            addLegacyRuleSlotGroup(Core.bundle.get("rbm.rulegroups.legacy.time.serpulo"), timeExpr + " && planet == " + planetSerpulo, keyTimeSerpuloSlotPrefix);
            addLegacyRuleSlotGroup(Core.bundle.get("rbm.rulegroups.legacy.time.sun"), timeExpr + " && planet == " + planetSun, keyTimeSunSlotPrefix);
            addLegacyRuleSlotGroup(Core.bundle.get("rbm.rulegroups.legacy.time"), timeExpr, keyTimeSlotPrefix);
        }

        addLegacyRuleSlotGroup(Core.bundle.get("rbm.rulegroups.legacy.planet.erekir"), "planet == " + planetErekir, keyPlanetErekirSlotPrefix);
        addLegacyRuleSlotGroup(Core.bundle.get("rbm.rulegroups.legacy.planet.serpulo"), "planet == " + planetSerpulo, keyPlanetSerpuloSlotPrefix);
        addLegacyRuleSlotGroup(Core.bundle.get("rbm.rulegroups.legacy.planet.sun"), "planet == " + planetSun, keyPlanetSunSlotPrefix);
    }

    private void addLegacyRuleSlotGroup(String name, String condition, String prefix){
        if(ruleSlotGroups.size >= maxRuleSlotGroups) return;
        String expr = condition == null ? "" : condition.trim();
        if(expr.isEmpty() || !hasAnySlot(prefix)) return;
        ruleSlotGroups.add(RuleSlotGroup.fromPrefix(nextRuleSlotGroupId(), name, expr, prefix, this));
    }

    private boolean hasAnySlot(String prefix){
        for(int i = 0; i < maxSlots; i++){
            String value = Core.settings.getString(prefix + i, "");
            if(value != null && !value.trim().isEmpty()) return true;
        }
        return false;
    }

    private String normalizeRuleSlotGroupName(String value, int id){
        String name = value == null ? "" : value.trim();
        if(name.isEmpty()) return Core.bundle.format("rbm.rulegroups.fallback", id);
        return name;
    }

    private Block ruleSlotGroupBlock(RuleSlotGroup group, int slot){
        if(group == null || slot < 0 || slot >= maxSlots) return null;
        String name = group.slots[slot];
        if(name == null) return null;
        name = name.trim();
        return name.isEmpty() ? null : content.block(name);
    }

    private RuleSlotGroup activeRuleSlotGroup(){
        if(!Core.settings.getBool(keyProMode, false)) return null;
        ensureRuleSlotGroupsLoaded();
        if(ruleSlotGroups.isEmpty()) return null;
        if(!state.isGame()){
            activeRuleSlotGroup = null;
            return null;
        }

        if(Time.time - ruleSlotGroupLastEval < condEvalIntervalFrames){
            return activeRuleSlotGroup;
        }
        ruleSlotGroupLastEval = Time.time;
        activeRuleSlotGroup = null;

        for(RuleSlotGroup group : ruleSlotGroups){
            if(evalRuleSlotGroup(group)){
                activeRuleSlotGroup = group;
                break;
            }
        }
        return activeRuleSlotGroup;
    }

    private boolean evalRuleSlotGroup(RuleSlotGroup group){
        if(group == null) return false;
        String src = group.condition == null ? "" : group.condition.trim();
        if(src.isEmpty()) return false;

        try{
            if(!src.equals(group.conditionSrc)){
                group.conditionSrc = src;
                group.conditionExpr = ConditionParser.parse(src);
            }
            return group.conditionExpr != null && group.conditionExpr.eval(this) != 0f;
        }catch(Throwable ignored){
            group.conditionExpr = null;
            return false;
        }
    }

    private static class RuleSlotGroup{
        int id;
        String name;
        String condition = "";
        final String[] slots = new String[maxSlots];
        String conditionSrc;
        Expr conditionExpr;

        RuleSlotGroup(int id, String name){
            this.id = id;
            this.name = name == null ? "" : name;
            for(int i = 0; i < maxSlots; i++){
                slots[i] = "";
            }
        }

        String displayName(){
            String text = name == null ? "" : name.trim();
            return text.isEmpty() ? "#" + id : text;
        }

        RuleSlotGroup copy(int id, String name){
            RuleSlotGroup out = new RuleSlotGroup(id, name);
            out.condition = condition == null ? "" : condition;
            for(int i = 0; i < maxSlots; i++){
                out.slots[i] = slots[i] == null ? "" : slots[i];
            }
            return out;
        }

        Jval toJson(){
            Jval root = Jval.newObject();
            root.put("id", id);
            root.put("name", displayName());
            root.put("condition", condition == null ? "" : condition);
            Jval arr = Jval.newArray();
            for(int i = 0; i < maxSlots; i++){
                arr.add(slots[i] == null ? "" : slots[i]);
            }
            root.put("slots", arr);
            return root;
        }

        static RuleSlotGroup fromPrefix(int id, String name, String condition, String prefix, RadialBuildMenuMod mod){
            RuleSlotGroup group = new RuleSlotGroup(id, name);
            group.condition = condition == null ? "" : condition;
            for(int i = 0; i < maxSlots; i++){
                group.slots[i] = mod.slotName(prefix, i);
            }
            return group;
        }

        static RuleSlotGroup fromJson(Jval value){
            if(value == null || !value.isObject()) return null;
            int id = Math.max(1, value.getInt("id", 1));
            RuleSlotGroup group = new RuleSlotGroup(id, value.getString("name", ""));
            group.condition = value.getString("condition", "");

            Jval slots = value.get("slots");
            if(slots != null && slots.isArray()){
                int count = Math.min(slots.asArray().size, maxSlots);
                for(int i = 0; i < count; i++){
                    Jval slot = slots.asArray().get(i);
                    group.slots[i] = slot == null || slot.isNull() ? "" : slot.asString().trim();
                }
            }
            return group;
        }
    }

    void showAdvancedDialog(){
        BaseDialog dialog = new BaseDialog("@rbm.advanced.title");
        dialog.addCloseButton();

        SettingsMenuDialog.SettingsTable adv = new SettingsMenuDialog.SettingsTable();

        adv.pref(new RuleSlotGroupsButtonSetting(this));

        adv.sliderPref(keyIconScale, 100, 50, 200, 5, v -> v + "%");
        adv.sliderPref(keyBackStrength, 22, 0, 60, 2, v -> v + "%");
        adv.sliderPref(keyRingAlpha, 65, 0, 100, 5, v -> v + "%");
        adv.sliderPref(keyRingStroke, 2, 1, 6, 1, v -> v + "px");

        adv.checkPref(keyDirectionSelect, true);
        adv.sliderPref(keyDeadzoneScale, 35, 0, 100, 5, v -> v + "%");
        adv.sliderPref(keyHoverPadding, 12, 0, 30, 1, v -> v + "px");
        adv.sliderPref(keyHoverUpdateFrames, 0, 0, 10, 1, v -> v == 0 ? Core.bundle.get("rbm.advanced.everyframe") : v + "f");

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
        activeRuleSlotGroup = null;
        ruleSlotGroupLastEval = -9999f;
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

    private class WheelProfilesButtonSetting extends SettingsMenuDialog.SettingsTable.Setting{
        private final RadialBuildMenuMod mod;

        public WheelProfilesButtonSetting(RadialBuildMenuMod mod){
            super("rbm-wheel-profiles-open");
            this.mod = mod;
            title = Core.bundle.get("rbm.setting.wheels");
        }

        @Override
        public void add(SettingsMenuDialog.SettingsTable table){
            float prefWidth = prefWidth();
            table.table(Tex.button, t -> {
                t.left().margin(10f);

                t.image(mindustry.gen.Icon.list).size(20f).padRight(8f);
                t.add(title).left().growX().minWidth(0f).wrap();
                t.label(() -> Core.bundle.format("rbm.wheels.count", mod.wheelProfiles().size)).color(Pal.accent).padLeft(8f);
                t.button("@rbm.wheels.open", Styles.flatt, mod::showWheelProfilesDialog)
                    .width(150f)
                    .height(40f)
                    .padLeft(10f);
                TextButton advanced = t.button("@setting.rbm-advanced.name", Styles.flatt, mod::showAdvancedDialog)
                    .width(150f)
                    .height(40f)
                    .padLeft(8f)
                    .get();
                advanced.update(() -> advanced.setDisabled(!Core.settings.getBool(keyProMode, false)));
            }).width(prefWidth).padTop(6f);
            table.row();
        }
    }

    private class RuleSlotGroupsButtonSetting extends SettingsMenuDialog.SettingsTable.Setting{
        private final RadialBuildMenuMod mod;

        public RuleSlotGroupsButtonSetting(RadialBuildMenuMod mod){
            super("rbm-rule-slot-groups-open");
            this.mod = mod;
            title = Core.bundle.get("rbm.rulegroups.setting");
        }

        @Override
        public void add(SettingsMenuDialog.SettingsTable table){
            float prefWidth = prefWidth();
            table.table(Tex.button, t -> {
                t.left().margin(10f);

                t.image(mindustry.gen.Icon.logic).size(20f).padRight(8f);
                t.add(title).left().growX().minWidth(0f).wrap();
                t.label(() -> Core.bundle.format("rbm.rulegroups.count", mod.ruleSlotGroups().size)).color(Pal.accent).padLeft(8f);
                t.button("@rbm.rulegroups.open", Styles.flatt, mod::showRuleSlotGroupsDialog)
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
        RuleSlotGroup group = activeRuleSlotGroup();
        if(group != null){
            return ruleSlotGroupBlock(group, slot);
        }

        if(timeRuleActive()){
            Block time = slotBlock(keyTimeSlotPrefix, slot);
            if(time != null) return time;
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
        if(n.startsWith("@")) n = n.substring(1);

        if("true".equals(n)) return 1f;
        if("false".equals(n)) return 0f;

        if("second".equals(n)){
            return (float)(state.tick / 60.0);
        }

        if("planet".equals(n)){
            return symbolValue(currentPlanetName());
        }

        if("thisteam".equals(n)){
            return player == null || player.team() == null ? 0f : symbolValue(player.team().name);
        }

        int dot = n.indexOf('.');
        if(dot > 0 && dot < n.length() - 1){
            Team team = resolveTeam(n.substring(0, dot));
            if(team != null){
                return teamValue(team, n.substring(dot + 1));
            }
        }

        if("unitcount".equals(n)){
            return countUnits(player == null ? null : player.team(), null);
        }

        if(n.endsWith("count") && n.length() > 5){
            String unitName = n.substring(0, n.length() - 5);
            UnitType type = content.unit(unitName);
            if(type != null){
                return countUnits(player == null ? null : player.team(), type);
            }
        }

        Item item = content.item(n);
        if(item != null){
            // Uses the "main core" item module; fast + stable.
            return player == null || player.team() == null ? 0f : player.team().items().get(item);
        }

        Team team = resolveTeam(n);
        if(team != null){
            return symbolValue(team.name);
        }

        return symbolValue(n);
    }

    private float teamValue(Team team, String member){
        if(team == null || member == null) return 0f;
        String n = member.trim().toLowerCase(Locale.ROOT);
        if(n.isEmpty()) return 0f;

        if("unitcount".equals(n)){
            return countUnits(team, null);
        }

        Item item = content.item(n);
        if(item != null){
            return team.items().get(item);
        }

        UnitType type = content.unit(n);
        if(type != null){
            return countUnits(team, type);
        }

        if(n.endsWith("count") && n.length() > 5){
            UnitType counted = content.unit(n.substring(0, n.length() - 5));
            if(counted != null){
                return countUnits(team, counted);
            }
        }

        return 0f;
    }

    private int countUnits(Team team, UnitType type){
        if(team == null) return 0;
        int count = 0;
        for(Unit u : Groups.unit){
            if(u != null && u.team == team && (type == null || u.type == type)){
                count++;
            }
        }
        return count;
    }

    private Team resolveTeam(String raw){
        if(raw == null) return null;
        String n = raw.trim().toLowerCase(Locale.ROOT);
        if(n.isEmpty()) return null;
        if("thisteam".equals(n) || "self".equals(n) || "own".equals(n)){
            return player == null ? null : player.team();
        }
        for(Team team : Team.all){
            if(team != null && team.name != null && team.name.equalsIgnoreCase(n)){
                return team;
            }
        }
        if(n.startsWith("team#")){
            try{
                int id = Integer.parseInt(n.substring(5));
                return id >= 0 && id < Team.all.length ? Team.all[id] : null;
            }catch(Throwable ignored){
                return null;
            }
        }
        return null;
    }

    private float symbolValue(String raw){
        if(raw == null) return 0f;
        String n = raw.trim().toLowerCase(Locale.ROOT);
        if(n.isEmpty()) return 0f;
        return n.hashCode();
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
        ensureWheelProfilesLoaded();
        ensureRuleSlotGroupsLoaded();
        Jval root = Jval.newObject();
        root.put("schema", 6);

        root.put("hudScale", Core.settings.getInt(keyHudScale, 100));
        root.put("hudAlpha", Core.settings.getInt(keyHudAlpha, 100));
        root.put("persistentHud", Core.settings.getBool(keyPersistentHud, false));
        root.put("persistentHudAlpha", Core.settings.getInt(keyPersistentHudAlpha, 35));
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
        root.put("activeWheelProfileId", Core.settings.getInt(keyActiveWheelProfileId, 0));

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
        root.put("wheelProfiles", exportWheelProfiles());
        root.put("ruleSlotGroups", exportRuleSlotGroups());

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
            if(root.has("persistentHud")) Core.settings.put(keyPersistentHud, root.getBool("persistentHud", false));
            if(root.has("persistentHudAlpha")) Core.settings.put(keyPersistentHudAlpha, Mathf.clamp(root.getInt("persistentHudAlpha", 35), 0, 100));
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
            if(root.has("activeWheelProfileId")) Core.settings.put(keyActiveWheelProfileId, Math.max(0, root.getInt("activeWheelProfileId", 0)));

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
            if(root.has("wheelProfiles")){
                importWheelProfilesValue(root.get("wheelProfiles"), true);
            }else{
                wheelProfilesLoaded = true;
                addLegacyDefaultWheelProfiles();
                saveWheelProfiles();
            }
            if(root.has("ruleSlotGroups")){
                ruleSlotGroupsLoaded = true;
                importRuleSlotGroupsValue(root.get("ruleSlotGroups"), true);
            }else{
                ruleSlotGroupsLoaded = true;
                addLegacyRuleSlotGroups();
                saveRuleSlotGroups();
            }

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
        if(xOverlayUi.isSupported()){
            if(xMobileToggleWindow == null){
                try{
                    Table content = buildMobileToggleContent();
                    xMobileToggleWindow = xOverlayUi.registerWindow(mobileWindowName, content, () -> state != null && state.isGame());
                    if(xMobileToggleWindow != null && xMobileToggleWindow.asElement() != null){
                        xMobileToggleWindow.configure(false, true);
                        // Auto-enable only on first registration; afterwards keep OverlayUI's persisted visibility.
                        if(!hasStoredOverlayWindowState(mobileWindowName)){
                            xMobileToggleWindow.setEnabledAndPinned(true, false);
                        }
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

    private static boolean hasStoredOverlayWindowState(String windowName){
        return Core.settings != null && Core.settings.has("overlayUI." + windowName);
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
        private KeyCode activeKey;
        private boolean activeRadialBind;
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

                updateHovered();

                if(activeRadialBind && Core.input.keyRelease(radialMenu) || !activeRadialBind && activeKey != null && Core.input.keyRelease(activeKey)){
                    commitSelection();
                    close();
                }else if(activeRadialBind && !Core.input.keyDown(radialMenu) || !activeRadialBind && (activeKey == null || !Core.input.keyDown(activeKey))){
                    // failsafe: if focus changed and no release is received
                    close();
                }
            }else{
                if(mobile) return;
                syncPassivePreview();
                WheelProfile profile = mod.tappedWheelProfile();
                if(profile != null && canActivate()){
                    begin(profile);
                    return;
                }
                if(canActivate() && Core.input.keyTap(radialMenu)){
                    begin();
                }
            }
        }

        private void updateLayout(){
            HudLayout l = layout;

            // Read settings once, derive all geometry, and precompute slot positions.
            float alpha = parentAlpha * Mathf.clamp(Core.settings.getInt(keyHudAlpha, 100) / 100f);
            if(!active){
                alpha *= Mathf.clamp(Core.settings.getInt(keyPersistentHudAlpha, 35) / 100f);
            }
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
            if(!active && !shouldDrawPassivePreview()) return;

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

        private boolean shouldDrawPassivePreview(){
            return !mobile
                && !active
                && Core.settings.getBool(keyPersistentHud, false)
                && canActivate();
        }

        private void syncPassivePreview(){
            if(!shouldDrawPassivePreview()){
                hovered = -1;
                return;
            }

            if(Core.settings.getBool(keyCenterScreen, false)){
                centerX = getWidth() / 2f;
                centerY = getHeight() / 2f;
            }else{
                centerX = Core.input.mouseX();
                centerY = Core.input.mouseY();
            }

            fillSlots(mod.activeWheelProfile());

            rebuildActiveSlotLists();
            hovered = -1;
        }

        private void begin(){
            active = true;
            activeRadialBind = true;
            activeKey = null;
            mod.setActiveWheelProfile(null);
            if(Core.settings.getBool(keyCenterScreen, false)){
                centerX = getWidth() / 2f;
                centerY = getHeight() / 2f;
            }else{
                centerX = Core.input.mouseX();
                centerY = Core.input.mouseY();
            }

            fillSlots(null);

            rebuildActiveSlotLists();

            hovered = findHovered();
        }

        private void begin(WheelProfile profile){
            if(profile == null) return;
            active = true;
            activeRadialBind = false;
            activeKey = profile.key;
            mod.setActiveWheelProfile(profile);
            if(Core.settings.getBool(keyCenterScreen, false)){
                centerX = getWidth() / 2f;
                centerY = getHeight() / 2f;
            }else{
                centerX = Core.input.mouseX();
                centerY = Core.input.mouseY();
            }

            fillSlots(profile);

            rebuildActiveSlotLists();

            hovered = findHovered();
        }

        private void beginMobile(){
            if(active) return;
            if(!canActivate()) return;

            active = true;
            activeRadialBind = false;
            activeKey = null;
            hovered = -1;
            centerX = getWidth() / 2f;
            centerY = getHeight() / 2f;

            fillSlots(null);
            rebuildActiveSlotLists();
        }

        private void fillSlots(WheelProfile profile){
            for(int i = 0; i < slots.length; i++){
                slots[i] = profile == null ? mod.contextSlotBlock(i) : mod.wheelSlotBlock(profile, i);
            }
        }

        private void close(){
            active = false;
            activeRadialBind = false;
            activeKey = null;
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

    static float prefWidth(){
        // slightly wider so long texts don't get clipped in settings dialogs
        return Math.min(Core.graphics.getWidth() / 1.02f, 980f);
    }
    // WideSliderSetting extracted into `RbmSettingsExtracted`.

    // Condition expression parsing/eval extracted into `RbmConditionExpr` (same behavior).
}
