package patchviewer;

import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.graphics.Colors;
import arc.graphics.g2d.TextureRegion;
import arc.scene.Element;
import arc.scene.Group;
import arc.scene.actions.Actions;
import arc.scene.style.TextureRegionDrawable;
import arc.scene.ui.CheckBox;
import arc.scene.ui.Image;
import arc.scene.ui.Label;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.TextField;
import arc.scene.ui.layout.Cell;
import arc.scene.ui.layout.Stack;
import arc.scene.ui.layout.Table;
import arc.files.Fi;
import arc.input.KeyBind;
import arc.input.KeyCode;
import arc.math.Mathf;
import arc.math.geom.Rect;
import arc.math.geom.Vec2;
import arc.struct.ObjectMap;
import arc.struct.OrderedMap;
import arc.struct.OrderedSet;
import arc.struct.Seq;
import arc.util.Log;
import arc.util.Scaling;
import arc.util.Strings;
import arc.util.Time;
import arc.util.Align;
import mindustry.Vars;
import mindustry.ctype.Content;
import mindustry.ctype.ContentType;
import mindustry.ctype.UnlockableContent;
import mindustry.game.Team;
import mindustry.game.EventType;
import mindustry.gen.Building;
import mindustry.gen.Groups;
import mindustry.gen.Icon;
import mindustry.gen.Tex;
import mindustry.gen.Unit;
import mindustry.graphics.Pal;
import mindustry.mod.Mod;
import mindustry.type.ItemStack;
import mindustry.type.UnitType;
import mindustry.ui.Displayable;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.ContentInfoDialog;
import mindustry.ui.dialogs.SettingsMenuDialog;
import mindustry.world.Block;
import mindustry.world.Tile;
import mindustry.world.blocks.units.Reconstructor;
import mindustry.world.meta.Stat;
import mindustry.world.meta.StatCat;
import mindustry.world.meta.StatValue;
import mindustry.world.meta.StatValues;
import mindustry.world.meta.Stats;

import java.lang.reflect.Field;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PatchViewerMod extends Mod{
    /** When true, this mod is running as a bundled component inside Neon. */
    public static boolean bekBundled = false;


    private static final float minContentWidth = 500f;
    private static final float maxMeasuredContentWidth = 1400f;
    private static final float maxDescriptionMeasureWidth = 620f;
    private static final float maxTextDiffMeasureWidth = 720f;
    private static final float detailsWidth = 400f;
    private static final String buildCostRowKey = "general::buildCost";
    private static final String arrowColor = "[lightgray]";
    private static final String keyEnabled = "patchviewer-enabled";
    private static final String keyRemovedColor = "patchviewer-color-removed";
    private static final String keyModifiedOldColor = "patchviewer-color-modified-old";
    private static final String keyModifiedNewColor = "patchviewer-color-modified-new";
    private static final String keyAddedColor = "patchviewer-color-added";
    private static final String keyQuickDisplayMode = "patchviewer-quick-display-mode";
    private static final String keyQuickHudOpacity = "patchviewer-quick-hud-opacity";
    private static final String keyQuickHudWidth = "patchviewer-quick-hud-width";
    private static final String keyQuickHudBackgroundColor = "patchviewer-quick-hud-background";
    private static final String defaultRemovedColor = "red";
    private static final String defaultModifiedOldColor = "gold";
    private static final String defaultModifiedNewColor = "green";
    private static final String defaultAddedColor = "green";
    private static final String defaultQuickHudBackgroundColor = "black";
    private static final String quickModeHud = "cursor";
    private static final String quickModeBuildInfo = "buildinfo";
    private static final String quickHudName = "patchviewer-quick-hud";
    private static final String quickBuildInfoName = "patchviewer-quick-build-info";
    private static final String iconTokenPrefix = "<patchviewer-icon:";
    private static final float defaultUnitStatIconSize = 40f;
    private static final Pattern numberPattern = Pattern.compile("-?\\d+(?:\\.\\d+)?");
    private static boolean settingsAdded;
    private static boolean keybindsRegistered;

    private final ObjectMap<UnlockableContent, ContentSnapshot> baselineSnapshots = new ObjectMap<>();
    private final ObjectMap<UnlockableContent, ContentSnapshot> afterSnapshots = new ObjectMap<>();
    private final ObjectMap<UnlockableContent, ContentDiff> diffsByContent = new ObjectMap<>();
    private final ObjectMap<UnlockableContent, CompactDiff> compactDiffsByContent = new ObjectMap<>();
    private KeyBind quickViewKey;
    private Table quickHud;
    private ScrollPane quickHudPane;
    private UnlockableContent lastQuickHudContent;
    private Table lastBuildInfoRoot;
    private boolean baselineCaptured;
    private boolean placementReflectReady;
    private boolean mi2uAvailable;
    private boolean mi2uClassMissing;
    private Object mi2uMi2uiInstance;
    private Field fieldTopTable;
    private Field fieldHover;
    private Field fieldHover2;
    private Field fieldMenuHoverBlock;
    private Field mi2uMi2uiSettingsField;
    private Field mi2uHoverInfoField;
    private Field mi2uHoverBuildField;
    private Field mi2uHoverBuildtField;
    private java.lang.reflect.Method mi2uSettingsGetBool;

    public PatchViewerMod(){
        setupDefaults();
        Events.on(EventType.ClientLoadEvent.class, event -> {
            installDialogHook();
            refreshSettingsBackupStorage();
            registerKeybinds();
            tryInitPlacementReflection();
            probeMi2u();
            Core.app.post(this::captureBaselineAtStartup);
        });
        Events.on(EventType.WorldLoadEvent.class, event -> {
            rebuildAfterSnapshots();
            refreshSettingsBackupStorage();
        });
        Events.on(EventType.ResetEvent.class, event -> {
            afterSnapshots.clear();
            diffsByContent.clear();
            compactDiffsByContent.clear();
            hideQuickHud();
            hideInjectedRows(lastBuildInfoRoot);
        });
        Events.run(EventType.Trigger.uiDrawEnd, this::updateQuickDisplay);
    }

    @Override
    public void init(){
        Events.on(EventType.ClientLoadEvent.class, event -> {
            if(settingsAdded) return;
            settingsAdded = true;
            if(Vars.ui == null || Vars.ui.settings == null) return;
        if(bekBundled) return;

            if(!bekBundled) Vars.ui.settings.addCategory("@settings.patchviewer", Icon.settingsSmall, this::bekBuildSettings);
        });
    }

    private void registerKeybinds(){
        if(keybindsRegistered) return;
        keybindsRegistered = true;
        quickViewKey = KeyBind.add("patchviewer-quick-view", KeyCode.altLeft, "patchviewer");
    }

    private void installDialogHook(){
        try{
            final ContentInfoDialog originalDialog = Vars.ui == null ? null : Vars.ui.content;
            Vars.ui.content = new ContentInfoDialog(){
                @Override
                public void show(UnlockableContent content){
                    if(!isPatchViewerEnabled()){
                        if(originalDialog != null && originalDialog != this){
                            originalDialog.show(content);
                        }else{
                            showPatched(content);
                        }
                        return;
                    }
                    showPatched(content);
                }
            };
        }catch(Throwable error){
            Log.err("[PatchViewer] Failed to hook ContentInfoDialog.", error);
        }
    }

    private void setupDefaults(){
        Core.settings.defaults(
            keyEnabled, true,
            keyRemovedColor, defaultRemovedColor,
            keyModifiedOldColor, defaultModifiedOldColor,
            keyModifiedNewColor, defaultModifiedNewColor,
            keyAddedColor, defaultAddedColor,
            keyQuickDisplayMode, quickModeHud,
            keyQuickHudOpacity, 70,
            keyQuickHudWidth, 420,
            keyQuickHudBackgroundColor, defaultQuickHudBackgroundColor
        );
    }

    private void refreshSettingsBackupStorage(){
        try{
            Fi backupFolder = Core.settings.getBackupFolder();
            if(backupFolder == null || !backupFolder.exists()) return;
            Seq<Fi> backups = backupFolder.seq();
            backups.sort((a, b) -> Long.compare(b.lastModified(), a.lastModified()));
            for(int i = 1; i < backups.size; i++){
                backups.get(i).delete();
            }
        }catch(Throwable error){
            Log.err("[PatchViewer] Failed to refresh settings_backups storage.", error);
        }
    }

    private void captureBaselineAtStartup(){
        if(baselineCaptured || Vars.content == null) return;
        baselineSnapshots.clear();

        Seq<UnlockableContent> all = collectAllContents();
        for(UnlockableContent content : all){
            ContentSnapshot snapshot = snapshot(content);
            if(snapshot != null) baselineSnapshots.put(content, snapshot);
        }
        baselineCaptured = true;
    }

    private void rebuildAfterSnapshots(){
        afterSnapshots.clear();
        diffsByContent.clear();
        compactDiffsByContent.clear();
        if(!baselineCaptured) captureBaselineAtStartup();
        if(Vars.state == null) return;

        Seq<UnlockableContent> all = collectAllContents();
        for(UnlockableContent content : all){
            ContentSnapshot before;
            ContentSnapshot after;
            try{
                before = baselineSnapshots.get(content);
                after = snapshot(content);
            }catch(Throwable t){
                Log.debug("[PatchViewer] Failed to snapshot @: @", content.name, Strings.getSimpleMessage(t));
                continue;
            }
            if(after != null) afterSnapshots.put(content, after);
            if(before == null || after == null) continue;

            ContentDiff diff = diff(before, after);
            if(diff != null && !diff.isEmpty()){
                diffsByContent.put(content, diff);
                CompactDiff compact = buildCompactDiff(content, before, after, diff);
                if(compact != null && !compact.isEmpty()) compactDiffsByContent.put(content, compact);
            }
        }
    }

    private void updateQuickDisplay(){
        if(!shouldShowQuickDiff()){
            hideQuickHud();
            hideInjectedRows(lastBuildInfoRoot);
            return;
        }

        QuickTarget target = findQuickTarget();
        CompactDiff compact = target == null ? null : compactDiffsByContent.get(target.content);
        if(compact == null || compact.isEmpty()){
            hideQuickHud();
            hideInjectedRows(lastBuildInfoRoot);
            return;
        }

        if(quickModeBuildInfo.equals(readQuickDisplayMode())){
            hideQuickHud();
            if(!showBuildInfoDiff(target, compact)){
                hideInjectedRows(lastBuildInfoRoot);
            }
        }else{
            hideInjectedRows(lastBuildInfoRoot);
            showQuickHud(target.content, compact);
        }
    }

    private boolean shouldShowQuickDiff(){
        if(!isPatchViewerEnabled()) return false;
        if(quickViewKey == null) registerKeybinds();
        if(quickViewKey == null || !Core.input.keyDown(quickViewKey)) return false;
        if(Vars.state == null || !Vars.state.isGame()) return false;
        if(Core.scene == null) return false;
        return !Core.scene.hasDialog() && !Core.scene.hasField() && !Core.scene.hasKeyboard();
    }

    private QuickTarget findQuickTarget(){
        tryInitPlacementReflection();
        probeMi2u();

        if(isMi2uReplaceActive()){
            Building build = getMi2uHoverBuild();
            Table table = getMi2uBuildt();
            if(build != null && build.block instanceof UnlockableContent){
                return new QuickTarget(build.block, table, true);
            }
        }

        try{
            Object pf = Vars.ui == null || Vars.ui.hudfrag == null ? null : Vars.ui.hudfrag.blockfrag;
            if(pf != null && placementReflectReady){
                Table topTable = (Table)fieldTopTable.get(pf);
                Block menuHoverBlock = (Block)fieldMenuHoverBlock.get(pf);
                if(menuHoverBlock != null){
                    return new QuickTarget(menuHoverBlock, topTable, false);
                }

                Displayable hover = (Displayable)fieldHover.get(pf);
                QuickTarget target = targetFromDisplayable(hover, topTable);
                if(target != null) return target;

                Displayable hover2 = (Displayable)fieldHover2.get(pf);
                target = targetFromDisplayable(hover2, topTable);
                if(target != null) return target;
            }
        }catch(Throwable ignored){
        }

        Building build = findMouseBuilding();
        if(build != null && build.block instanceof UnlockableContent){
            return new QuickTarget(build.block, null, false);
        }

        Unit unit = findMouseUnit();
        if(unit != null && unit.type != null){
            return new QuickTarget(unit.type, null, false);
        }
        return null;
    }

    private QuickTarget targetFromDisplayable(Displayable displayable, Table topTable){
        if(displayable instanceof Building){
            Building build = (Building)displayable;
            return build.block instanceof UnlockableContent ? new QuickTarget(build.block, topTable, false) : null;
        }
        if(displayable instanceof Unit){
            Unit unit = (Unit)displayable;
            return unit.type == null ? null : new QuickTarget(unit.type, topTable, false);
        }
        return null;
    }

    private Building findMouseBuilding(){
        if(Vars.world == null) return null;
        try{
            Tile tile = Vars.world.tileWorld(Core.input.mouseWorldX(), Core.input.mouseWorldY());
            if(tile == null || tile.build == null) return null;
            return tile.build;
        }catch(Throwable ignored){
            return null;
        }
    }

    private Unit findMouseUnit(){
        if(Vars.control == null || Vars.control.input == null || Vars.player == null) return null;
        Vec2 pos = Core.input.mouseWorld();
        if(pos == null) return null;
        final Unit[] best = {null};
        final float[] bestDst = {Float.MAX_VALUE};
        Groups.unit.intersect(pos.x - 48f, pos.y - 48f, 96f, 96f, unit -> {
            if(unit == null || unit.dead || unit.type == null) return;
            float radius = Math.max(unit.hitSize + 8f, 12f);
            float dst = unit.dst2(pos.x, pos.y);
            if(dst <= radius * radius && dst < bestDst[0]){
                best[0] = unit;
                bestDst[0] = dst;
            }
        });
        return best[0];
    }

    private void showQuickHud(UnlockableContent content, CompactDiff compact){
        if(Core.scene == null) return;
        ensureQuickHud();
        if(quickHud == null || quickHudPane == null) return;
        applyQuickHudBackground();

        if(lastQuickHudContent != content){
            rebuildQuickHudContent(content, compact);
            lastQuickHudContent = content;
        }

        float width = readQuickHudWidth();
        float margin = 8f;
        float mouseX = Core.input.mouseX();
        float mouseY = Core.input.mouseY();
        quickHudPane.setWidth(width);
        quickHud.pack();
        float maxHeight = Math.max(120f, Core.scene.getHeight() - margin * 2f);
        float height = Math.min(maxHeight, Math.max(60f, quickHud.getPrefHeight()));
        quickHudPane.setSize(width, height);

        boolean right = mouseX <= Core.scene.getWidth() / 2f;
        boolean above = mouseY <= Core.scene.getHeight() / 2f;
        float x = right ? mouseX + margin : mouseX - width - margin;
        float y = above ? mouseY + margin : mouseY - height - margin;
        x = Mathf.clamp(x, margin, Math.max(margin, Core.scene.getWidth() - width - margin));
        y = Mathf.clamp(y, margin, Math.max(margin, Core.scene.getHeight() - height - margin));
        quickHudPane.setPosition(x, y);
        quickHudPane.visible = true;
        quickHudPane.toFront();
    }

    private void ensureQuickHud(){
        if(quickHudPane != null && quickHudPane.parent != null) return;
        if(Core.scene == null || Core.scene.root == null) return;

        quickHud = new Table(Tex.whiteui);
        quickHud.name = quickHudName;
        quickHud.left().top().margin(8f);
        applyQuickHudBackground();
        quickHudPane = new ScrollPane(quickHud);
        quickHudPane.setScrollingDisabled(true, false);
        quickHudPane.setOverscroll(false, false);
        quickHudPane.name = quickHudName;
        Core.scene.root.addChild(quickHudPane);
    }

    private void rebuildQuickHudContent(UnlockableContent content, CompactDiff compact){
        quickHud.clearChildren();
        quickHud.left().top().defaults().left().top();
        applyQuickHudBackground();
        float width = readQuickHudWidth() - 20f;
        quickHud.table(title -> {
            title.left();
            title.image(content.uiIcon).size(28f).scaling(Scaling.fit).padRight(6f);
            Label label = new Label("[accent]" + escape(content.localizedName) + "[]");
            label.setWrap(true);
            title.add(label).growX().width(width - 40f);
        }).growX().fillX().width(width);
        quickHud.row();
        addCompactLines(quickHud, compact, width, true);
    }

    private void applyQuickHudBackground(){
        if(quickHud == null) return;
        Color color = new Color(readQuickHudBackgroundColor());
        color.a = readQuickHudOpacity();
        quickHud.setColor(color);
    }

    private boolean showBuildInfoDiff(QuickTarget target, CompactDiff compact){
        Table root = target == null ? null : target.topTable;
        if(root == null) return false;
        lastBuildInfoRoot = root;
        Table extra = (Table)root.find(quickBuildInfoName);
        if(extra == null){
            extra = new Table();
            extra.name = quickBuildInfoName;
            insertBuildInfoTable(root, extra);
        }
        extra.visible = true;
        extra.clearChildren();
        extra.left().top().defaults().left().top();

        float width = Math.max(120f, Math.min(420f, root.getWidth() > 0f ? root.getWidth() - 12f : readQuickHudWidth()));
        extra.table(title -> {
            title.left();
            title.add("[accent]" + escape(target.content.localizedName) + "[]").left().wrap().width(width);
        }).left().width(width).padTop(3f);
        extra.row();
        addCompactLines(extra, compact, width, false);
        return true;
    }

    private void insertBuildInfoTable(Table root, Table extra){
        int rows = 0;
        try{
            for(Cell cell : root.getCells()){
                if(cell != null && cell.get() != null) rows++;
            }
        }catch(Throwable ignored){
        }
        if(rows > 0) root.row();
        root.add(extra).left().top().growX().fillX().colspan(4).padTop(3f);
    }

    private void addCompactLines(Table table, CompactDiff compact, float width, boolean withHeaders){
        if(compact == null) return;
        for(String line : compact.added){
            addCompactLine(table, line, width, false);
        }
        if(!compact.modified.isEmpty()){
            if(withHeaders) addCompactHeader(table, bundle("patchviewer.quick.modified", "Modified"), width);
            for(String line : compact.modified){
                addCompactLine(table, line, width, true);
            }
        }
        if(!compact.removed.isEmpty()){
            if(withHeaders) addCompactHeader(table, bundle("patchviewer.quick.removed", "Removed"), width);
            for(String line : compact.removed){
                addCompactLine(table, line, width, true);
            }
        }
    }

    private void addCompactHeader(Table table, String text, float width){
        Label label = new Label("[accent]" + escape(text) + "[]", Styles.outlineLabel);
        label.setFontScale(1.35f);
        table.add(label).left().width(width).padTop(8f).row();
    }

    private void addCompactLine(Table table, String text, float width, boolean allowWrapByArrow){
        if(text == null) return;
        String normalized = compactDisplayText(text, allowWrapByArrow, width);
        if(normalized == null || normalized.isEmpty()) return;
        Seq<String> lines = splitCompactDisplayLines(normalized);
        for(String lineText : lines){
            if(lineText == null || Strings.stripColors(lineText).trim().isEmpty()) continue;
            Table line = new Table();
            line.left().top().defaults().left().top();
            addCompactInline(line, lineText, width);
            table.add(line).left().top().width(width).growX().fillX();
            table.row();
        }
    }

    private String compactDisplayText(String text, boolean allowWrapByArrow, float width){
        String cleaned = (text == null ? "" : text)
            .replace("<Image>", "")
            .replace("<Stack>", "")
            .replace("<Collapser>", "")
            .replaceAll("[ \\t]{2,}", " ")
            .trim();
        if(Strings.stripColors(cleaned).isEmpty()) return "";
        if(!allowWrapByArrow || !shouldPreBreakCompactArrow(cleaned, width)) return cleaned;
        return cleaned.replace(" -> ", " ->\n");
    }

    private boolean shouldPreBreakCompactArrow(String text, float width){
        if(text == null || !text.contains(" -> ") || text.indexOf('\n') >= 0) return false;
        return compactVisibleUnits(text) > Math.max(38f, width / 7f);
    }

    private int compactVisibleUnits(String text){
        if(text == null) return 0;
        StringBuilder out = new StringBuilder();
        int index = 0;
        while(index < text.length()){
            int at = text.indexOf(iconTokenPrefix, index);
            if(at < 0){
                out.append(text.substring(index));
                break;
            }
            out.append(text, index, at);
            int end = text.indexOf('>', at + iconTokenPrefix.length());
            if(end < 0){
                out.append(text.substring(at));
                break;
            }
            out.append("###");
            index = end + 1;
        }
        return Strings.stripColors(out.toString()).replace("[[", "[").trim().length();
    }

    private Seq<String> splitCompactDisplayLines(String text){
        Seq<String> lines = new Seq<>();
        if(text == null) return lines;
        StringBuilder line = new StringBuilder();
        String activeColor = "";
        for(int i = 0; i < text.length(); i++){
            char ch = text.charAt(i);
            if(ch == '\n'){
                lines.add(line.toString());
                line.setLength(0);
                if(!activeColor.isEmpty()) line.append(activeColor);
                continue;
            }
            if(ch == '['){
                if(i + 1 < text.length() && text.charAt(i + 1) == '['){
                    line.append("[[");
                    i++;
                    continue;
                }
                int close = text.indexOf(']', i + 1);
                if(close >= 0){
                    String tag = text.substring(i, close + 1);
                    line.append(tag);
                    activeColor = "[]".equals(tag) ? "" : tag;
                    i = close;
                    continue;
                }
            }
            line.append(ch);
        }
        lines.add(line.toString());
        return lines;
    }

    private void addCompactInline(Table table, String text, float width){
        if(table == null || text == null) return;
        boolean hasIcon = findIconMatch(text, 0) != null;
        if(!hasIcon){
            addCompactTextSegment(table, text, width, "", true);
            return;
        }
        boolean keepPlainIconsInline = shouldKeepPlainIconsInline(text);
        int index = 0;
        boolean added = false;
        float[] usedWidth = {0f};
        boolean[] rowHasContent = {false};
        while(index < text.length()){
            ContentMatch match = findIconMatch(text, index);
            if(match == null){
                added |= addCompactTextSegment(table, text.substring(index), width, activeColorPrefix(text, index), false, usedWidth, rowHasContent);
                break;
            }
            if(match.start > index){
                added |= addCompactTextSegment(table, text.substring(index, match.start), width, activeColorPrefix(text, index), false, usedWidth, rowHasContent);
            }
            String activeColor = activeColorPrefix(text, match.start);
            StackAmount amount = parseStackAmount(text, match.end, match.content);
            if(amount != null){
                Element stackView = compactStackView(match.content, amount.amountText, activeColor);
                addCompactElement(table, stackView, width, usedWidth, rowHasContent, 4f, added ? 2f : 0f);
                if(amount.suffix != null && !amount.suffix.isEmpty()){
                    addCompactTextSegment(table, amount.suffix, width, activeColor, false, usedWidth, rowHasContent);
                }
                added = true;
                index = amount.end;
                continue;
            }

            Image image = new Image(match.content.uiIcon);
            image.setScaling(Scaling.fit);
            image.setColor(Color.white);
            image.update(() -> image.setColor(Color.white));
            addCompactImage(table, image, width, usedWidth, rowHasContent, keepPlainIconsInline ? 5f : 3f, added ? 2f : 0f, !keepPlainIconsInline, keepPlainIconsInline ? 24f : 16f);
            added = true;
            int consumedLabel = consumeIconLabelTail(text, match.end, match.content);
            index = consumedLabel == match.end ? match.end : consumedLabel;
        }
        if(!added){
            addCompactTextSegment(table, text, width, "", true);
        }
    }

    private boolean shouldKeepPlainIconsInline(String text){
        if(text == null) return false;
        boolean hasPlainIcon = false;
        int index = 0;
        while(index < text.length()){
            ContentMatch match = findIconMatch(text, index);
            if(match == null) break;
            if(parseStackAmount(text, match.end, match.content) != null) return false;
            hasPlainIcon = true;
            index = match.end;
        }
        return hasPlainIcon;
    }

    private boolean addCompactTextSegment(Table table, String text, float width, String activeColor, boolean wrap){
        return addCompactTextSegment(table, text, width, activeColor, wrap, null, null);
    }

    private boolean addCompactTextSegment(Table table, String text, float width, String activeColor, boolean wrap, float[] usedWidth, boolean[] rowHasContent){
        if(table == null || text == null) return false;
        String visible = Strings.stripColors(text).replace("[[", "[").trim();
        if(visible.isEmpty()) return false;
        String markup = (activeColor == null ? "" : activeColor) + text;
        Label label = new Label(markup);
        label.setWrap(wrap);
        if(!wrap && usedWidth != null && rowHasContent != null){
            addCompactElement(table, label, width, usedWidth, rowHasContent, 0f, 0f, !isArrowSegment(visible));
            return true;
        }
        Cell<Label> cell = table.add(label).left().top();
        if(wrap){
            cell.growX().fillX().width(Math.max(1f, width - 20f));
        }
        return true;
    }

    private void addCompactElement(Table table, Element element, float width, float[] usedWidth, boolean[] rowHasContent, float padRight, float padLeft){
        addCompactElement(table, element, width, usedWidth, rowHasContent, padRight, padLeft, true);
    }

    private void addCompactElement(Table table, Element element, float width, float[] usedWidth, boolean[] rowHasContent, float padRight, float padLeft, boolean allowWrapBefore){
        if(table == null || element == null) return;
        float elementWidth = compactElementWidth(element) + padLeft + padRight;
        if(allowWrapBefore && rowHasContent != null && usedWidth != null && rowHasContent[0] && usedWidth[0] + elementWidth > Math.max(40f, width - 4f)){
            table.row();
            usedWidth[0] = 0f;
            rowHasContent[0] = false;
            padLeft = 0f;
            elementWidth = compactElementWidth(element) + padRight;
        }
        table.add(element).padRight(padRight).padLeft(padLeft).top();
        if(usedWidth != null) usedWidth[0] += elementWidth;
        if(rowHasContent != null) rowHasContent[0] = true;
    }

    private void addCompactImage(Table table, Image image, float width, float[] usedWidth, boolean[] rowHasContent, float padRight, float padLeft){
        addCompactImage(table, image, width, usedWidth, rowHasContent, padRight, padLeft, true, 16f);
    }

    private void addCompactImage(Table table, Image image, float width, float[] usedWidth, boolean[] rowHasContent, float padRight, float padLeft, boolean allowWrapBefore, float size){
        if(table == null || image == null) return;
        image.setColor(Color.white);
        float iconSize = Math.max(1f, size);
        float elementWidth = iconSize + padLeft + padRight;
        if(allowWrapBefore && rowHasContent != null && usedWidth != null && rowHasContent[0] && usedWidth[0] + elementWidth > Math.max(40f, width - 4f)){
            table.row();
            usedWidth[0] = 0f;
            rowHasContent[0] = false;
            padLeft = 0f;
            elementWidth = iconSize + padRight;
        }
        table.add(image).size(iconSize).scaling(Scaling.fit).padRight(padRight).padLeft(padLeft).top();
        if(usedWidth != null) usedWidth[0] += elementWidth;
        if(rowHasContent != null) rowHasContent[0] = true;
    }

    private float compactElementWidth(Element element){
        if(element == null) return 0f;
        element.pack();
        float width = Math.max(element.getPrefWidth(), element.getWidth());
        return width <= 0f ? 20f : width;
    }

    private boolean isArrowSegment(String text){
        return text != null && Strings.stripColors(text).trim().equals("->");
    }

    private Element compactStackView(UnlockableContent content, String amountText, String amountColorTag){
        if(content == null) return new Table();
        Table out = new Table();
        out.center();
        Image image = new Image(content.uiIcon);
        image.setScaling(Scaling.fit);
        image.setColor(Color.white);
        out.add(image).size(24f).scaling(Scaling.fit).center();
        out.row();

        if(amountText != null && !amountText.isEmpty()){
            Label label = new Label((amountColorTag == null ? "" : amountColorTag) + escape(formatCompactStackAmount(amountText)) + "[]", Styles.outlineLabel);
            label.setFontScale(1f);
            label.setAlignment(Align.center);
            out.add(label).center().width(42f).height(16f).padTop(-3f);
        }

        return out;
    }

    private String formatCompactStackAmount(String text){
        if(text == null) return "";
        String cleaned = text.trim();
        if(cleaned.isEmpty()) return cleaned;
        try{
            float value = Float.parseFloat(cleaned);
            if(Math.abs(value) >= 1000f){
                return String.format(Locale.ROOT, "%.1fK", value / 1000f);
            }
        }catch(Throwable ignored){
        }
        return cleaned;
    }

    private String activeColorPrefix(String text, int end){
        if(text == null || end <= 0) return "";
        String active = "";
        int limit = Math.min(end, text.length());
        for(int i = 0; i < limit; i++){
            char ch = text.charAt(i);
            if(ch != '[') continue;
            if(i + 1 < limit && text.charAt(i + 1) == '['){
                i++;
                continue;
            }
            int close = text.indexOf(']', i + 1);
            if(close < 0 || close >= limit) continue;
            String tag = text.substring(i, close + 1);
            active = "[]".equals(tag) ? "" : tag;
            i = close;
        }
        return active;
    }

    private StackAmount parseStackAmount(String text, int start, UnlockableContent content){
        if(text == null) return null;
        int i = skipCompactNoise(text, start);
        if(i >= text.length()) return null;
        i = consumeContentName(text, i, content);
        i = skipCompactNoise(text, i);
        if(i >= text.length()) return null;
        char prefix = text.charAt(i);
        if(prefix == 'x' || prefix == 'X' || prefix == '×'){
            i++;
            i = skipCompactNoise(text, i);
        }
        int numberStart = i;
        i = consumeCompactNumberEnd(text, numberStart);
        if(i < 0) return null;
        try{
            String amountText = text.substring(numberStart, i);
            RateSuffix directRate = consumeRateSuffix(text, i, content);
            if(directRate != null){
                return new StackAmount(amountText + directRate.suffix, directRate.end, "");
            }

            int end = consumeContentName(text, i, content);
            if(end != i){
                RateAmount rateAmount = consumeRateAmount(text, end, content);
                if(rateAmount != null){
                    return new StackAmount(rateAmount.amountText + rateAmount.suffix, rateAmount.end, "");
                }
                return new StackAmount(amountText, end, "");
            }
            return new StackAmount(amountText, i, "");
        }catch(Throwable ignored){
            return null;
        }
    }

    private int consumeContentName(String text, int start, UnlockableContent content){
        if(text == null || content == null) return start;
        int i = skipCompactNoise(text, start);
        int end = consumeContentNameAlias(text, i, content.localizedName);
        if(end >= 0) return skipCompactNoise(text, end);
        end = consumeContentNameAlias(text, i, content.name);
        if(end >= 0) return skipCompactNoise(text, end);
        return start;
    }

    private int consumeIconLabelTail(String text, int start, UnlockableContent content){
        int end = consumeContentName(text, start, content);
        if(end == start) return start;
        int i = skipCompactNoise(text, end);
        while(i < text.length() && text.charAt(i) == '?'){
            i = skipCompactNoise(text, i + 1);
        }
        return i;
    }

    private String normalizeStackCompactText(String text){
        if(text == null) return null;
        StringBuilder out = new StringBuilder();
        int index = 0;
        while(index < text.length()){
            ContentMatch match = findIconMatch(text, index);
            if(match == null) break;

            if(out.length() > 0) out.append(' ');
            out.append(iconToken(match.content));

            StackAmount amount = parseStackAmount(text, match.end, match.content);
            if(amount != null && amount.amountText != null && !amount.amountText.isEmpty()){
                out.append(' ').append(amount.amountText);
                if(amount.suffix != null && !amount.suffix.isEmpty()){
                    out.append(amount.suffix);
                }
                index = Math.max(amount.end, match.end);
            }else{
                int consumed = consumeIconLabelTail(text, match.end, match.content);
                index = consumed == match.end ? match.end : consumed;
            }
        }
        return out.length() == 0 ? null : out.toString();
    }

    private int consumeContentNameAlias(String text, int start, String name){
        if(text == null || name == null || name.isEmpty()) return -1;
        return start + name.length() <= text.length() && text.startsWith(name, start) ? start + name.length() : -1;
    }

    private int skipCompactNoise(String text, int start){
        if(text == null) return Math.max(0, start);
        int i = Math.max(0, start);
        while(i < text.length()){
            char ch = text.charAt(i);
            if(Character.isWhitespace(ch)){
                i++;
                continue;
            }
            if(ch == '[' && !(i + 1 < text.length() && text.charAt(i + 1) == '[')){
                int close = text.indexOf(']', i + 1);
                if(close >= 0){
                    i = close + 1;
                    continue;
                }
            }
            break;
        }
        return i;
    }

    private int consumeCompactNumberEnd(String text, int start){
        if(text == null) return -1;
        int i = skipCompactNoise(text, start);
        int numberStart = i;
        while(i < text.length() && Character.isDigit(text.charAt(i))) i++;
        if(i < text.length() && text.charAt(i) == '.'){
            int dot = i++;
            while(i < text.length() && Character.isDigit(text.charAt(i))) i++;
            if(i == dot + 1) i = dot;
        }
        return i == numberStart ? -1 : i;
    }

    private RateAmount consumeRateAmount(String text, int start, UnlockableContent content){
        if(text == null) return null;
        int numberStart = skipCompactNoise(text, start);
        int i = consumeCompactNumberEnd(text, numberStart);
        if(i < 0) return null;
        RateSuffix suffix = consumeRateSuffix(text, i, content);
        if(suffix == null) return null;
        return new RateAmount(text.substring(numberStart, i), suffix.suffix, suffix.end);
    }

    private RateSuffix consumeRateSuffix(String text, int start, UnlockableContent content){
        if(text == null) return null;
        int i = skipCompactNoise(text, start);
        if(i >= text.length() || text.charAt(i) != '/') return null;
        int suffixStart = skipCompactNoise(text, i + 1);
        int suffixEnd = suffixStart;
        if(suffixEnd < text.length() && text.charAt(suffixEnd) == '秒'){
            suffixEnd++;
        }else if(suffixEnd < text.length() && (text.charAt(suffixEnd) == 's' || text.charAt(suffixEnd) == 'S')){
            suffixEnd++;
        }else{
            return null;
        }
        int end = consumeContentName(text, suffixEnd, content);
        if(end == suffixEnd) end = skipCompactNoise(text, suffixEnd);
        return new RateSuffix("/s", end);
    }

    private ContentMatch findIconMatch(String text, int start){
        if(text == null || Vars.content == null) return null;
        int at = text.indexOf(iconTokenPrefix, Math.max(start, 0));
        while(at >= 0){
            int end = text.indexOf('>', at + iconTokenPrefix.length());
            if(end < 0) return null;
            UnlockableContent content = contentFromIconToken(text.substring(at, end + 1));
            if(content != null && content.uiIcon != null){
                return new ContentMatch(content, at, end + 1);
            }
            at = text.indexOf(iconTokenPrefix, end + 1);
        }
        return null;
    }

    private String iconToken(UnlockableContent content){
        if(content == null) return "";
        return iconTokenPrefix + content.getContentType().name() + ":" + content.id + ">";
    }

    private UnlockableContent contentFromIconToken(String token){
        if(token == null || !token.startsWith(iconTokenPrefix) || !token.endsWith(">") || Vars.content == null) return null;
        String body = token.substring(iconTokenPrefix.length(), token.length() - 1);
        int split = body.indexOf(':');
        if(split <= 0 || split >= body.length() - 1) return null;
        try{
            ContentType type = ContentType.valueOf(body.substring(0, split));
            int id = Integer.parseInt(body.substring(split + 1));
            Content content = Vars.content.getByID(type, id);
            return content instanceof UnlockableContent ? (UnlockableContent)content : null;
        }catch(Throwable ignored){
            return null;
        }
    }

    private Seq<UnlockableContent> iconContentTargets(){
        Seq<UnlockableContent> result = new Seq<>();
        if(Vars.content == null) return result;
        for(ContentType type : new ContentType[]{ContentType.item, ContentType.liquid, ContentType.block, ContentType.unit}){
            Seq<Content> seq = Vars.content.getBy(type);
            if(seq == null) continue;
            for(Content raw : seq){
                if(raw instanceof UnlockableContent) result.add((UnlockableContent)raw);
            }
        }
        result.sort((a, b) -> {
            int al = a == null || a.localizedName == null ? 0 : a.localizedName.length();
            int bl = b == null || b.localizedName == null ? 0 : b.localizedName.length();
            return Integer.compare(bl, al);
        });
        return result;
    }

    private void hideQuickHud(){
        if(quickHudPane != null) quickHudPane.visible = false;
        lastQuickHudContent = null;
    }

    private void hideInjectedRows(Table root){
        if(root == null) return;
        try{
            Element extra = root.find(quickBuildInfoName);
            if(extra instanceof Table){
                Table table = (Table)extra;
                table.clearChildren();
                table.visible = false;
            }
        }catch(Throwable ignored){
        }
    }

    private void tryInitPlacementReflection(){
        if(placementReflectReady) return;
        try{
            Class<?> cls = Class.forName("mindustry.ui.fragments.PlacementFragment");
            fieldTopTable = cls.getDeclaredField("topTable");
            fieldHover = cls.getDeclaredField("hover");
            fieldHover2 = cls.getDeclaredField("hover2");
            fieldMenuHoverBlock = cls.getDeclaredField("menuHoverBlock");
            fieldTopTable.setAccessible(true);
            fieldHover.setAccessible(true);
            fieldHover2.setAccessible(true);
            fieldMenuHoverBlock.setAccessible(true);
            placementReflectReady = true;
        }catch(Throwable ignored){
            placementReflectReady = false;
        }
    }

    private void probeMi2u(){
        if(mi2uAvailable || mi2uClassMissing) return;
        try{
            Class<?> vars = Class.forName("mi2u.MI2UVars");
            Field varsField = vars.getDeclaredField("mi2ui");
            varsField.setAccessible(true);
            Object instance = varsField.get(null);
            if(instance == null) return;

            Class<?> mindow2 = Class.forName("mi2u.ui.elements.Mindow2");
            Field settingsField = mindow2.getDeclaredField("settings");
            settingsField.setAccessible(true);
            Class<?> settingHandler = Class.forName("mi2u.io.SettingHandler");
            java.lang.reflect.Method getBool = settingHandler.getMethod("getBool", String.class);

            Class<?> hover = Class.forName("mi2u.ui.HoverTopTable");
            Field hoverInfoField = hover.getDeclaredField("hoverInfo");
            hoverInfoField.setAccessible(true);
            Field buildField = hover.getDeclaredField("build");
            buildField.setAccessible(true);
            Field buildtField = hover.getDeclaredField("buildt");
            buildtField.setAccessible(true);

            mi2uMi2uiInstance = instance;
            mi2uMi2uiSettingsField = settingsField;
            mi2uSettingsGetBool = getBool;
            mi2uHoverInfoField = hoverInfoField;
            mi2uHoverBuildField = buildField;
            mi2uHoverBuildtField = buildtField;
            mi2uAvailable = true;
        }catch(ClassNotFoundException | NoClassDefFoundError missing){
            mi2uClassMissing = true;
        }catch(Throwable ignored){
        }
    }

    private boolean isMi2uReplaceActive(){
        if(!mi2uAvailable) return false;
        try{
            Object handler = mi2uMi2uiSettingsField.get(mi2uMi2uiInstance);
            if(handler == null) return false;
            Object value = mi2uSettingsGetBool.invoke(handler, "replaceTopTable");
            return value instanceof Boolean && (Boolean)value;
        }catch(Throwable ignored){
            return false;
        }
    }

    private Building getMi2uHoverBuild(){
        if(!mi2uAvailable) return null;
        try{
            Object hoverInfo = mi2uHoverInfoField.get(null);
            if(hoverInfo == null) return null;
            Object build = mi2uHoverBuildField.get(hoverInfo);
            return build instanceof Building ? (Building)build : null;
        }catch(Throwable ignored){
            return null;
        }
    }

    private Table getMi2uBuildt(){
        if(!mi2uAvailable) return null;
        try{
            Object hoverInfo = mi2uHoverInfoField.get(null);
            if(hoverInfo == null) return null;
            Object buildt = mi2uHoverBuildtField.get(hoverInfo);
            return buildt instanceof Table ? (Table)buildt : null;
        }catch(Throwable ignored){
            return null;
        }
    }

    private Seq<UnlockableContent> collectAllContents(){
        Seq<UnlockableContent> result = new Seq<>();
        for(ContentType type : ContentType.all){
            if(type.name().indexOf('_') != -1) continue;
            Seq<Content> seq = Vars.content.getBy(type);
            if(seq == null) continue;
            for(Content raw : seq){
                if(raw instanceof UnlockableContent) result.add((UnlockableContent)raw);
            }
        }
        return result;
    }

    private ContentSnapshot snapshot(UnlockableContent content){
        if(content == null) return null;
        content.checkStats();

        ContentSnapshot snapshot = new ContentSnapshot();
        snapshot.title = normalizeText(content.localizedName);
        snapshot.description = normalizeText(content.displayDescription());
        snapshot.details = normalizeText(content.details);
        snapshot.buildCost = extractBuildCost(content);

        Stats stats = content.stats;
        for(StatCat cat : stats.toMap().keys()){
            OrderedMap<Stat, Seq<StatValue>> map = stats.toMap().get(cat);
            if(map.size == 0) continue;

            for(Stat stat : map.keys()){
                String key = rowKey(cat, stat);
                SnapshotRow row = new SnapshotRow(cat, stat, stat.localized());
                Table rendered = buildRenderedTable(map.get(stat));
                row.kind = classifyRow(stat, rendered);
                row.compactKind = classifyCompactRow(stat, rendered, row.kind);
                row.text = extractRowSignature(rendered);
                if(row.kind != RowKind.TEXT || row.compactKind != RowKind.TEXT){
                    row.rendered = rendered;
                    captureLabelStates(rendered, row.labels);
                    row.compactText = extractStructuredCompactText(rendered);
                    if(row.compactKind == RowKind.STACK_LIST){
                        row.compactText = normalizeStackCompactText(row.compactText);
                    }
                    if(row.compactText == null) row.compactText = extractCompactLabelText(row);
                    if(row.compactKind == RowKind.STACK_LIST){
                        row.compactText = normalizeStackCompactText(row.compactText);
                    }
                }
                if(row.compactText == null && row.compactKind != RowKind.STACK_LIST) row.compactText = row.text;
                row.numeric = extractNumbers(row.text);
                snapshot.rows.put(key, row);
                snapshot.order.add(key);
            }
        }

        return snapshot;
    }

    private Table buildRenderedTable(Seq<StatValue> values){
        Table table = new Table();
        table.left().top();
        if(values != null){
            for(StatValue value : values){
                try{
                    value.display(table);
                }catch(Throwable ignored){
                }
            }
        }
        normalizeUnitIconLinks(table);
        freezeElementTree(table);
        return simplifyRenderedTable(table);
    }

    private void sanitizeQuestionMarkButtons(Element element){
        if(element == null) return;
        if(element instanceof Label){
            Label label = (Label)element;
            String visible = Strings.stripColors(label.getText() == null ? "" : label.getText().toString()).trim();
            if("?".equals(visible)) label.setText("");
            return;
        }
        if(element instanceof ScrollPane){
            sanitizeQuestionMarkButtons(((ScrollPane)element).getWidget());
            return;
        }
        if(element instanceof Group){
            Seq<Element> children = ((Group)element).getChildren();
            for(int i = 0; i < children.size; i++){
                sanitizeQuestionMarkButtons(children.get(i));
            }
        }
    }

    private void normalizeUnitIconLinks(Element element){
        if(element == null || Vars.content == null) return;
        if(element instanceof Image){
            Image image = (Image)element;
            UnitType unit = unitTypeForImage(image);
            if(unit != null){
                image.setScaling(Scaling.fit);
                image.clicked(() -> {
                    if(Vars.ui != null && Vars.ui.content != null && !unit.isHidden()){
                        Vars.ui.content.show(unit);
                    }
                });
            }
            return;
        }
        if(element instanceof Group){
            Seq<Element> children = ((Group)element).getChildren();
            for(int i = 0; i < children.size; i++){
                normalizeUnitIconLinks(children.get(i));
            }
        }
        if(element instanceof Table){
            Seq<Cell> cells = ((Table)element).getCells();
            for(int i = 0; i < cells.size; i++){
                Element child = cells.get(i).get();
                if(child instanceof Image){
                    UnitType unit = unitTypeForImage((Image)child);
                    if(unit != null){
                        cells.get(i).size(calcUnitStatIconSize(unit)).scaling(Scaling.fit);
                    }
                }
            }
        }
    }

    private UnitType unitTypeForImage(Image image){
        UnlockableContent content = contentForImage(image);
        return content instanceof UnitType ? (UnitType)content : null;
    }

    private UnlockableContent contentForImage(Image image){
        if(image == null || !(image.getDrawable() instanceof TextureRegionDrawable)) return null;
        TextureRegion region = ((TextureRegionDrawable)image.getDrawable()).getRegion();
        if(region == null) return null;
        for(UnlockableContent content : iconContentTargets()){
            if(content != null && (sameRegion(content.uiIcon, region) || sameRegion(content.fullIcon, region))){
                return content;
            }
        }
        return null;
    }

    private boolean sameRegion(TextureRegion a, TextureRegion b){
        if(a == null || b == null) return false;
        return a == b || (a.texture == b.texture && a.getX() == b.getX() && a.getY() == b.getY() && a.width == b.width && a.height == b.height);
    }

    private float calcUnitStatIconSize(UnitType unit){
        if(unit == null || unit.uiIcon == null) return defaultUnitStatIconSize;
        UnitType root = firstTierUnit(unit);
        float rootSize = regionMax(root == null ? null : root.uiIcon);
        float currentSize = regionMax(unit.uiIcon);
        if(rootSize <= 0f || currentSize <= 0f) return defaultUnitStatIconSize;
        return Mathf.clamp(defaultUnitStatIconSize * rootSize / currentSize, 16f, defaultUnitStatIconSize);
    }

    private UnitType firstTierUnit(UnitType unit){
        UnitType current = unit;
        for(int guard = 0; guard < 16; guard++){
            UnitType previous = null;
            for(Block block : Vars.content.blocks()){
                if(!(block instanceof Reconstructor)) continue;
                Reconstructor reconstructor = (Reconstructor)block;
                for(UnitType[] upgrade : reconstructor.upgrades){
                    if(upgrade != null && upgrade.length >= 2 && upgrade[1] == current){
                        previous = upgrade[0];
                        break;
                    }
                }
                if(previous != null) break;
            }
            if(previous == null) return current;
            current = previous;
        }
        return current;
    }

    private float regionMax(TextureRegion region){
        return region == null ? 0f : Math.max(region.width, region.height);
    }

    private Table simplifyRenderedTable(Table table){
        Table current = table;
        while(current != null && current.getBackground() == null && current.getChildren().size == 1 && current.getChildren().first() instanceof Table){
            if(hasVisualCellContent(current)) break;
            current = (Table)current.getChildren().first();
            current.left().top();
        }
        return current;
    }

    private boolean hasVisualCellContent(Table table){
        if(table == null) return false;
        Seq<Cell> cells = table.getCells();
        for(int i = 0; i < cells.size; i++){
            Element child = cells.get(i).get();
            if(child != null && !(child instanceof Table)){
                return true;
            }
        }
        return false;
    }

    private void freezeElementTree(Element element){
        if(element == null) return;
        element.update(null);
        if(element instanceof ScrollPane){
            freezeElementTree(((ScrollPane)element).getWidget());
            return;
        }
        if(element instanceof Group){
            Seq<Element> children = ((Group)element).getChildren();
            for(int i = 0; i < children.size; i++){
                freezeElementTree(children.get(i));
            }
        }
    }

    private void captureLabelStates(Element element, Seq<LabelState> out){
        captureLabelStates(element, out, "root", false);
    }

    private String extractCompactLabelText(SnapshotRow row){
        if(row == null || row.labels == null || row.labels.isEmpty()) return row == null ? null : row.text;
        StringBuilder out = new StringBuilder();
        String last = null;
        for(LabelState state : row.labels){
            if(state == null) continue;
            String text = normalizeCompactLine(state.visibleText);
            if(text == null || text.isEmpty() || text.equals(last)) continue;
            if(out.length() > 0) out.append("\n");
            out.append(text);
            last = text;
        }
        return out.length() == 0 ? row.text : out.toString();
    }

    private String extractStructuredCompactText(Element element){
        Seq<CompactPart> rows = new Seq<>();
        collectStructuredCompactRows(element, rows);
        StringBuilder out = new StringBuilder();
        String last = null;
        for(CompactPart row : rows){
            if(row == null || !row.hasIcon) continue;
            String text = cleanCompactRow(row.text);
            if(text == null || text.equals(last)) continue;
            if(out.length() > 0) out.append("\n");
            out.append(text);
            last = text;
        }
        return out.length() == 0 ? null : out.toString();
    }

    private void collectStructuredCompactRows(Element element, Seq<CompactPart> out){
        if(element == null || out == null) return;
        if(element instanceof ScrollPane){
            collectStructuredCompactRows(((ScrollPane)element).getWidget(), out);
            return;
        }
        if(element instanceof Table){
            Table table = (Table)element;
            Seq<Cell> cells = table.getCells();
            if(cells.size > 0){
                CompactPart row = new CompactPart();
                for(int i = 0; i < cells.size; i++){
                    Element child = cells.get(i).get();
                    CompactPart part = compactPart(child);
                    appendCompactPart(row, part);
                    if(cells.get(i).isEndRow()){
                        addStructuredCompactRow(out, row);
                        row = new CompactPart();
                    }
                }
                addStructuredCompactRow(out, row);
                return;
            }
        }
        if(element instanceof Group){
            Seq<Element> children = ((Group)element).getChildren();
            for(int i = 0; i < children.size; i++){
                collectStructuredCompactRows(children.get(i), out);
            }
            return;
        }

        CompactPart part = compactPart(element);
        addStructuredCompactRow(out, part);
    }

    private CompactPart compactPart(Element element){
        if(element == null) return new CompactPart();
        if(element instanceof Label){
            String raw = ((Label)element).getText() == null ? "" : ((Label)element).getText().toString();
            String text = normalizeCompactLine(raw);
            return text == null ? new CompactPart() : new CompactPart(text, false);
        }
        if(element instanceof Image){
            UnlockableContent content = contentForImage((Image)element);
            return content == null ? new CompactPart() : new CompactPart(iconToken(content), true);
        }
        if(element instanceof ScrollPane){
            return compactPart(((ScrollPane)element).getWidget());
        }
        if(element instanceof Table){
            Table table = (Table)element;
            CompactPart part = new CompactPart();
            Seq<Cell> cells = table.getCells();
            if(cells.size > 0){
                for(int i = 0; i < cells.size; i++){
                    appendCompactPart(part, compactPart(cells.get(i).get()));
                }
            }else{
                Seq<Element> children = ((Group)element).getChildren();
                for(int i = 0; i < children.size; i++){
                    appendCompactPart(part, compactPart(children.get(i)));
                }
            }
            return part;
        }
        if(element instanceof Group){
            CompactPart out = new CompactPart();
            Seq<Element> children = ((Group)element).getChildren();
            for(int i = 0; i < children.size; i++){
                appendCompactPart(out, compactPart(children.get(i)));
            }
            return out;
        }
        return new CompactPart();
    }

    private void addStructuredCompactRow(Seq<CompactPart> out, CompactPart row){
        if(out == null || row == null || !row.hasIcon) return;
        String text = cleanCompactRow(row.text);
        if(text == null) return;
        out.add(new CompactPart(text, true));
    }

    private void appendCompactPart(CompactPart row, CompactPart part){
        if(row == null || part == null) return;
        String text = cleanCompactRow(part.text);
        if(text == null) return;
        if(row.text.length() > 0){
            if(row.hasIcon && part.hasIcon){
                row.text.append(' ');
            }else if(!startsWithPunctuation(text) && !endsWithPunctuation(row.text)){
                row.text.append(' ');
            }
        }
        row.text.append(text);
        row.hasIcon |= part.hasIcon;
    }

    private boolean startsWithPunctuation(String text){
        if(text == null || text.isEmpty()) return false;
        char ch = text.charAt(0);
        return ch == '/' || ch == '%' || ch == ',' || ch == '.' || ch == ')' || ch == ']' || ch == ':' || ch == ';';
    }

    private boolean endsWithPunctuation(StringBuilder text){
        if(text == null || text.length() == 0) return false;
        char ch = text.charAt(text.length() - 1);
        return ch == '(' || ch == '[' || ch == ':' || ch == '/' || ch == '-';
    }

    private String cleanCompactRow(CharSequence text){
        if(text == null) return null;
        String cleaned = text.toString()
            .replace("<Image>", "")
            .replace("<Stack>", "")
            .replace("<Collapser>", "")
            .replaceAll("[ \\t]+", " ")
            .replaceAll(" *\\n *", "\n")
            .trim();
        return cleaned.isEmpty() ? null : cleaned;
    }

    private String normalizeCompactLine(String text){
        if(text == null) return null;
        String stripped = Strings.stripColors(text)
            .replace("<Image>", "")
            .replace("<Stack>", "")
            .replace("<Collapser>", "")
            .replaceAll("\\s+", " ")
            .trim();
        if("?".equals(stripped)) return null;
        return stripped.isEmpty() ? null : stripped;
    }

    private void captureLabelStates(Element element, Seq<LabelState> out, String groupKey, boolean insideStack){
        if(element == null || out == null) return;
        boolean nextInsideStack = insideStack || element instanceof Stack;
        if(element instanceof Label){
            Label label = (Label)element;
            String raw = label.getText() == null ? "" : label.getText().toString();
            String visible = Strings.stripColors(raw);
            String normalized = normalizeText(visible);
            out.add(new LabelState(label, raw, visible, normalized, buildLabelMatchKey(normalized), groupKey, nextInsideStack, new Color(label.color), readLabelWrap(label), readLabelEllipsis(label)));
            return;
        }
        if(element instanceof Table){
            Seq<Cell> cells = ((Table)element).getCells();
            int childIndex = 0;
            for(int i = 0; i < cells.size; i++){
                Element child = cells.get(i).get();
                if(child == null) continue;
                String childGroupKey = "root".equals(groupKey) ? groupKey + "/" + childIndex : groupKey;
                captureLabelStates(child, out, childGroupKey, nextInsideStack);
                childIndex++;
            }
            return;
        }
        if(element instanceof Group){
            Seq<Element> children = ((Group)element).getChildren();
            for(int i = 0; i < children.size; i++){
                captureLabelStates(children.get(i), out, groupKey, nextInsideStack);
            }
        }
    }

    private String buildLabelMatchKey(String normalized){
        if(normalized == null) return "";
        String key = normalized.replaceAll("-?\\d+(?:\\.\\d+)?", "#").replaceAll("\\s+", " ").trim();
        return key.isEmpty() ? normalized : key;
    }

    private boolean readLabelWrap(Label label){
        Object value = Reflector.get(label, "wrap");
        return value instanceof Boolean && (Boolean)value;
    }

    private String readLabelEllipsis(Label label){
        Object value = Reflector.get(label, "ellipsis");
        return value instanceof String ? (String)value : null;
    }

    private void restoreLabelStates(Seq<LabelState> labels){
        if(labels == null) return;
        for(LabelState state : labels){
            if(state == null || state.label == null) continue;
            state.label.setText(state.rawText);
            state.label.setColor(state.originalColor);
            state.label.setWrap(state.originalWrap);
            if(state.originalEllipsis == null){
                state.label.setEllipsis(false);
            }else{
                state.label.setEllipsis(state.originalEllipsis);
            }
        }
    }

    private boolean labelsMatch(LabelState before, LabelState after){
        if(before == null || after == null) return false;
        if(before.normalizedText == null || after.normalizedText == null) return before.normalizedText == after.normalizedText;
        if(before.normalizedText.equals(after.normalizedText)) return true;
        return before.matchKey.equals(after.matchKey);
    }

    private void highlightWholeLabel(LabelState state, String colorTag){
        if(state == null || state.label == null) return;
        state.label.setColor(1f, 1f, 1f, state.originalColor.a);
        state.label.setText(colorTag + escape(state.visibleText) + "[]");
    }

    private void highlightMatchedLabels(LabelState before, LabelState after){
        if(before == null || after == null) return;
        if(before.normalizedText == null ? after.normalizedText == null : before.normalizedText.equals(after.normalizedText)) return;

        String beforeMarkup = buildNumericHighlightMarkup(before.visibleText, after.visibleText, modifiedOldColorTag());
        String afterMarkup = buildNumericHighlightMarkup(after.visibleText, before.visibleText, modifiedNewColorTag());

        if(beforeMarkup == null || afterMarkup == null){
            beforeMarkup = buildSegmentHighlightMarkup(before.visibleText, after.visibleText, modifiedOldColorTag());
            afterMarkup = buildSegmentHighlightMarkup(after.visibleText, before.visibleText, modifiedNewColorTag());
        }

        if(beforeMarkup == null) beforeMarkup = modifiedOldColorTag() + escape(before.visibleText) + "[]";
        if(afterMarkup == null) afterMarkup = modifiedNewColorTag() + escape(after.visibleText) + "[]";

        before.label.setColor(1f, 1f, 1f, before.originalColor.a);
        before.label.setText(beforeMarkup);
        after.label.setColor(1f, 1f, 1f, after.originalColor.a);
        after.label.setText(afterMarkup);
    }

    private String buildNumericHighlightMarkup(String source, String other, String colorTag){
        if(source == null || other == null) return null;
        Seq<String> sourceNumbers = extractNumbers(source);
        Seq<String> otherNumbers = extractNumbers(other);
        if(sourceNumbers.isEmpty() || sourceNumbers.size != otherNumbers.size) return null;

        boolean changed = false;
        StringBuilder out = new StringBuilder();
        Matcher matcher = numberPattern.matcher(source);
        int index = 0;
        int last = 0;
        while(matcher.find()){
            out.append(escape(source.substring(last, matcher.start())));
            String number = matcher.group();
            boolean different = !number.equals(otherNumbers.get(index));
            if(different){
                changed = true;
                out.append(colorTag).append(escape(number)).append("[]");
            }else{
                out.append(escape(number));
            }
            last = matcher.end();
            index++;
        }
        out.append(escape(source.substring(last)));
        return changed ? out.toString() : null;
    }

    private String buildSegmentHighlightMarkup(String source, String other, String colorTag){
        if(source == null || other == null) return null;
        if(source.equals(other)) return null;

        int prefix = 0;
        int maxPrefix = Math.min(source.length(), other.length());
        while(prefix < maxPrefix && source.charAt(prefix) == other.charAt(prefix)){
            prefix++;
        }

        int suffix = 0;
        int sourceRemaining = source.length() - prefix;
        int otherRemaining = other.length() - prefix;
        while(suffix < sourceRemaining && suffix < otherRemaining &&
            source.charAt(source.length() - 1 - suffix) == other.charAt(other.length() - 1 - suffix)){
            suffix++;
        }

        int middleEnd = source.length() - suffix;
        String changed = source.substring(prefix, middleEnd);
        if(changed.isEmpty()) return null;

        StringBuilder out = new StringBuilder();
        out.append(escape(source.substring(0, prefix)));
        out.append(colorTag).append(escape(changed)).append("[]");
        out.append(escape(source.substring(middleEnd)));
        return out.toString();
    }

    private void prepareRenderedDiff(Seq<LabelState> beforeLabels, Seq<LabelState> afterLabels){
        restoreLabelStates(beforeLabels);
        restoreLabelStates(afterLabels);
        if(beforeLabels == null || afterLabels == null) return;

        OrderedSet<String> changedBeforeGroups = new OrderedSet<>();
        OrderedSet<String> changedAfterGroups = new OrderedSet<>();
        int beforeSize = beforeLabels.size;
        int afterSize = afterLabels.size;
        int[][] lcs = new int[beforeSize + 1][afterSize + 1];

        for(int i = beforeSize - 1; i >= 0; i--){
            for(int j = afterSize - 1; j >= 0; j--){
                if(labelsMatch(beforeLabels.get(i), afterLabels.get(j))){
                    lcs[i][j] = lcs[i + 1][j + 1] + 1;
                }else{
                    lcs[i][j] = Math.max(lcs[i + 1][j], lcs[i][j + 1]);
                }
            }
        }

        int i = 0, j = 0;
        while(i < beforeSize || j < afterSize){
            if(i < beforeSize && j < afterSize && labelsMatch(beforeLabels.get(i), afterLabels.get(j)) &&
                lcs[i][j] == lcs[i + 1][j + 1] + 1){
                LabelState before = beforeLabels.get(i);
                LabelState after = afterLabels.get(j);
                boolean changed = before.normalizedText == null ? after.normalizedText != null : !before.normalizedText.equals(after.normalizedText);
                highlightMatchedLabels(before, after);
                if(changed){
                    changedBeforeGroups.add(before.groupKey);
                    changedAfterGroups.add(after.groupKey);
                }
                i++;
                j++;
            }else if(i < beforeSize && (j >= afterSize || lcs[i + 1][j] >= lcs[i][j + 1])){
                LabelState before = beforeLabels.get(i);
                highlightWholeLabel(before, removedColorTag());
                changedBeforeGroups.add(before.groupKey);
                i++;
            }else if(j < afterSize){
                LabelState after = afterLabels.get(j);
                highlightWholeLabel(after, addedColorTag());
                changedAfterGroups.add(after.groupKey);
                j++;
            }
        }

        highlightChangedStackAmounts(beforeLabels, changedBeforeGroups);
        highlightChangedStackAmounts(afterLabels, changedAfterGroups);
    }

    private void highlightChangedStackAmounts(Seq<LabelState> labels, OrderedSet<String> changedGroups){
        if(labels == null || changedGroups == null) return;
        for(LabelState state : labels){
            if(state == null || !state.insideStack) continue;
            if(changedGroups.contains(state.groupKey)){
                String colorTag = detectGroupHighlightColor(labels, state.groupKey);
                if(colorTag != null){
                    highlightWholeLabel(state, colorTag);
                }
            }
        }
    }

    private void highlightChangedGroups(Seq<LabelState> labels){
        if(labels == null) return;
        OrderedSet<String> changedGroups = new OrderedSet<>();
        for(LabelState state : labels){
            if(state == null || state.label == null) continue;
            String current = state.label.getText() == null ? "" : state.label.getText().toString();
            if(!current.equals(state.rawText)) changedGroups.add(state.groupKey);
        }
        for(LabelState state : labels){
            if(state == null) continue;
            if(changedGroups.contains(state.groupKey)){
                String colorTag = detectGroupHighlightColor(labels, state.groupKey);
                if(colorTag != null){
                    highlightWholeLabel(state, colorTag);
                }
            }
        }
    }

    private void highlightAllLabels(Seq<LabelState> labels, String colorTag){
        if(labels == null) return;
        for(LabelState state : labels){
            highlightWholeLabel(state, colorTag);
        }
    }

    private String detectGroupHighlightColor(Seq<LabelState> labels, String groupKey){
        if(labels == null || groupKey == null) return null;
        boolean hasRemoved = false;
        boolean hasAdded = false;
        boolean hasModifiedOld = false;
        boolean hasModifiedNew = false;
        for(LabelState state : labels){
            if(state == null || !groupKey.equals(state.groupKey) || state.label == null) continue;
            String current = state.label.getText() == null ? "" : state.label.getText().toString();
            if(current.contains(removedColorTag())){
                hasRemoved = true;
            }else if(current.contains(addedColorTag())){
                hasAdded = true;
            }else if(current.contains(modifiedOldColorTag())){
                hasModifiedOld = true;
            }else if(current.contains(modifiedNewColorTag())){
                hasModifiedNew = true;
            }
        }
        if(hasRemoved) return removedColorTag();
        if(hasAdded) return addedColorTag();
        if(hasModifiedOld) return modifiedOldColorTag();
        if(hasModifiedNew) return modifiedNewColorTag();
        return null;
    }

    private RowKind classifyRow(Stat stat, Element rendered){
        if(stat == Stat.buildCost) return RowKind.BUILD_COST;
        if(stat == Stat.weapons) return RowKind.WEAPON_PANEL;
        if(stat == Stat.abilities) return RowKind.ABILITY_PANEL;
        if(stat == Stat.ammo) return RowKind.AMMO_PANEL;
        if(stat == Stat.input || stat == Stat.output) return RowKind.STACK_LIST;
        if(rendered == null) return RowKind.TEXT;
        if(containsPanelElement(rendered)) return RowKind.NATIVE_PANEL;
        if(containsStackLikeElement(rendered)) return RowKind.STACK_LIST;
        return RowKind.TEXT;
    }

    private RowKind classifyCompactRow(Stat stat, Element rendered, RowKind displayKind){
        if(stat == Stat.buildCost) return RowKind.BUILD_COST;
        if(stat == Stat.input || stat == Stat.output || stat == Stat.tiles || stat == Stat.drillTier || stat == Stat.mineTier) return RowKind.STACK_LIST;
        return displayKind == null ? classifyRow(stat, rendered) : displayKind;
    }

    private boolean containsPanelElement(Element element){
        if(element == null) return false;
        String name = element.getClass().getSimpleName();
        if("Collapser".equals(name) || "ScrollPane".equals(name)) return true;
        if(element instanceof Table){
            Table table = (Table)element;
            if(table.getBackground() != null) return true;
            if(table.getCells().size > 2 && ((Group)element).getChildren().size > 2) return true;
        }
        if(element instanceof Group){
            Seq<Element> children = ((Group)element).getChildren();
            for(int i = 0; i < children.size; i++){
                if(containsPanelElement(children.get(i))) return true;
            }
        }
        return false;
    }

    private boolean containsStackLikeElement(Element element){
        if(element == null) return false;
        String name = element.getClass().getSimpleName();
        if("Image".equals(name) || "Stack".equals(name)) return true;
        if(element instanceof Group){
            Seq<Element> children = ((Group)element).getChildren();
            for(int i = 0; i < children.size; i++){
                if(containsStackLikeElement(children.get(i))) return true;
            }
        }
        return false;
    }

    private Seq<ItemStack> extractBuildCost(UnlockableContent content){
        Object value = Reflector.get(content, "requirements");
        if(!(value instanceof ItemStack[])) return null;
        ItemStack[] stacks = (ItemStack[])value;
        Seq<ItemStack> out = new Seq<>();
        for(ItemStack stack : stacks){
            if(stack == null || stack.item == null) continue;
            out.add(stack.copy());
        }
        return out.isEmpty() ? null : out;
    }

    private String extractRowSignature(Element rendered){
        return rendered == null ? null : normalizeText(extractTreeSignature(rendered));
    }

    private String extractTreeSignature(Element element){
        if(element == null) return "";
        StringBuilder out = new StringBuilder();
        appendTreeSignature(out, element);
        return out.toString().trim();
    }

    private void appendTreeSignature(StringBuilder out, Element element){
        if(element == null) return;
        if(element instanceof Label){
            CharSequence text = ((Label)element).getText();
            if(text != null){
                String cleaned = Strings.stripColors(text.toString()).trim();
                if(!cleaned.isEmpty()){
                    if(out.length() > 0) out.append(' ');
                    out.append(cleaned);
                }
            }
            return;
        }

        String name = element.getClass().getSimpleName();
        if("Image".equals(name) || "Stack".equals(name) || "Collapser".equals(name)){
            if(out.length() > 0) out.append(' ');
            out.append('<').append(name).append('>');
        }

        if(element instanceof Group){
            Seq<Element> children = ((Group)element).getChildren();
            for(int i = 0; i < children.size; i++){
                appendTreeSignature(out, children.get(i));
            }
        }
    }

    private Seq<String> extractNumbers(String text){
        Seq<String> out = new Seq<>();
        if(text == null) return out;
        Matcher matcher = numberPattern.matcher(text);
        while(matcher.find()){
            out.add(matcher.group());
        }
        return out;
    }

    private String normalizeText(String text){
        if(text == null) return null;
        String stripped = Strings.stripColors(text).replace('\n', ' ').replaceAll("\\s+", " ").trim();
        return stripped.isEmpty() ? null : stripped;
    }

    private String rowKey(StatCat cat, Stat stat){
        return cat.name + "::" + stat.name;
    }

    private ContentDiff diff(ContentSnapshot before, ContentSnapshot after){
        ContentDiff diff = new ContentDiff();
        diff.title = buildDiff(before.title, after.title);
        diff.description = buildDiff(before.description, after.description);
        diff.details = buildDiff(before.details, after.details);

        if(!sameStacks(before.buildCost, after.buildCost)){
            diff.rows.put(buildCostRowKey, InlineRow.forBuildCost(Stat.buildCost.localized(), copyStacks(before.buildCost), copyStacks(after.buildCost)));
        }

        OrderedSet<String> union = new OrderedSet<>();
        union.addAll(before.order);
        union.addAll(after.order);
        union.add(buildCostRowKey);

        for(String key : union){
            if(buildCostRowKey.equals(key)) continue;
            SnapshotRow beforeRow = before.rows.get(key);
            SnapshotRow afterRow = after.rows.get(key);
            if(beforeRow == null && afterRow == null) continue;

            if(beforeRow == null){
                if(afterRow.kind == RowKind.TEXT){
                    String markup = buildDiff(null, afterRow.text);
                    if(markup != null) diff.rows.put(key, InlineRow.forText(afterRow.label, markup));
                }else{
                    diff.rows.put(key, InlineRow.forNative(afterRow.label, afterRow.kind, null, null, afterRow.rendered, afterRow.labels));
                }
                continue;
            }

            if(afterRow == null){
                if(beforeRow.kind == RowKind.TEXT){
                    String markup = buildDiff(beforeRow.text, null);
                    if(markup != null) diff.rows.put(key, InlineRow.forText(beforeRow.label, markup));
                }else{
                    diff.rows.put(key, InlineRow.forNative(beforeRow.label, beforeRow.kind, beforeRow.rendered, beforeRow.labels, null, null));
                }
                continue;
            }

            if(afterRow.kind != beforeRow.kind){
                diff.rows.put(key, InlineRow.forNative(afterRow.label, afterRow.kind, beforeRow.rendered, beforeRow.labels, afterRow.rendered, afterRow.labels));
                continue;
            }

            RowKind compactKind = afterRow.compactKind == null ? afterRow.kind : afterRow.compactKind;
            switch(compactKind){
                case TEXT:
                    String markup = buildDiff(beforeRow.text, afterRow.text);
                    if(markup != null) diff.rows.put(key, InlineRow.forText(afterRow.label, markup));
                    break;
                case STACK_LIST:
                    if(!sameCompactRows(beforeRow, afterRow)) diff.rows.put(key, InlineRow.forNative(afterRow.label, afterRow.kind, beforeRow.rendered, beforeRow.labels, afterRow.rendered, afterRow.labels));
                    break;
                case AMMO_PANEL:
                    if(!sameNumbers(beforeRow, afterRow)) diff.rows.put(key, InlineRow.forNative(afterRow.label, afterRow.kind, beforeRow.rendered, beforeRow.labels, afterRow.rendered, afterRow.labels));
                    break;
                case WEAPON_PANEL:
                case ABILITY_PANEL:
                case NATIVE_PANEL:
                    if(!sameTextAndNumbers(beforeRow, afterRow)) diff.rows.put(key, InlineRow.forNative(afterRow.label, afterRow.kind, beforeRow.rendered, beforeRow.labels, afterRow.rendered, afterRow.labels));
                    break;
                default:
                    break;
            }
        }

        return diff.isEmpty() ? null : diff;
    }

    private CompactDiff buildCompactDiff(UnlockableContent content, ContentSnapshot before, ContentSnapshot after, ContentDiff diff){
        if(content == null || before == null || after == null || diff == null) return null;
        CompactDiff compact = new CompactDiff();
        addCompactChange(compact, bundle("patchviewer.quick.name", "Name"), before.title, after.title, RowKind.TEXT);
        addCompactChange(compact, bundle("patchviewer.quick.description", "Description"), before.description, after.description, RowKind.TEXT);
        addCompactChange(compact, bundle("patchviewer.quick.details", "Details"), before.details, after.details, RowKind.TEXT);

        OrderedSet<String> displayKeys = new OrderedSet<>();
        displayKeys.addAll(before.order);
        displayKeys.addAll(after.order);
        displayKeys.addAll(diff.rows.keys().toSeq());
        displayKeys.add(buildCostRowKey);

        for(String key : displayKeys){
            InlineRow row = diff.rows.get(key);
            if(row == null) continue;
            SnapshotRow beforeRow = before.rows.get(key);
            SnapshotRow afterRow = after.rows.get(key);
            String label = row.label == null ? key : row.label;
            RowKind kind = afterRow != null ? afterRow.compactKind : beforeRow != null ? beforeRow.compactKind : row.kind;
            if(kind == null) kind = afterRow != null ? afterRow.kind : beforeRow != null ? beforeRow.kind : row.kind;
            if(isBuildCostDiffRow(row)){
                addCompactChange(compact, label, compactStacks(row.buildCostBefore), compactStacks(row.buildCostAfter), RowKind.BUILD_COST);
            }else{
                String beforeText = compactRowText(beforeRow);
                String afterText = compactRowText(afterRow);
                if(kind == RowKind.STACK_LIST && (beforeText != null || afterText != null)){
                    addCompactStackItemChanges(compact, label, beforeText, afterText);
                    continue;
                }
                if(shouldFilterCommonCompactLines(kind) && beforeText != null && afterText != null){
                    String originalBefore = beforeText;
                    String originalAfter = afterText;
                    beforeText = compactDifferentLines(originalBefore, originalAfter);
                    afterText = compactDifferentLines(originalAfter, originalBefore);
                    if(beforeText == null && afterText == null) continue;
                }
                addCompactChange(compact, label, beforeText, afterText, kind);
            }
        }
        return compact;
    }

    private void addCompactStackItemChanges(CompactDiff compact, String label, String before, String after){
        Seq<CompactStackItem> left = parseCompactStackItems(before);
        Seq<CompactStackItem> right = parseCompactStackItems(after);
        if(left.isEmpty() && right.isEmpty()){
            addCompactChange(compact, label, before, after, RowKind.STACK_LIST);
            return;
        }

        boolean[] usedRight = new boolean[right.size];
        StringBuilder out = new StringBuilder();
        for(CompactStackItem item : left){
            int same = findCompactStackItem(right, usedRight, item, true);
            if(same >= 0){
                usedRight[same] = true;
                continue;
            }
            int changed = findCompactStackItem(right, usedRight, item, false);
            if(changed >= 0){
                usedRight[changed] = true;
                appendCompactStackChange(out, modifiedOldColorTag() + item.markup() + "[] " + arrowColor + "-> []" + modifiedNewColorTag() + right.get(changed).markup() + "[]");
            }else{
                appendCompactStackChange(out, removedColorTag() + item.markup() + "[]");
            }
        }
        for(int i = 0; i < right.size; i++){
            if(!usedRight[i]){
                appendCompactStackChange(out, addedColorTag() + right.get(i).markup() + "[]");
            }
        }
        if(out.length() == 0) return;
        compact.modified.add("[lightgray]" + escape(label == null ? "" : label) + ":[]\n" + out);
    }

    private void appendCompactStackChange(StringBuilder out, String text){
        if(out == null || text == null || text.isEmpty()) return;
        if(out.length() > 0) out.append(' ');
        out.append(text);
    }

    private int findCompactStackItem(Seq<CompactStackItem> items, boolean[] used, CompactStackItem needle, boolean requireSameAmount){
        if(items == null || needle == null) return -1;
        for(int i = 0; i < items.size; i++){
            if(used != null && used[i]) continue;
            CompactStackItem item = items.get(i);
            if(item == null || item.content != needle.content) continue;
            if(requireSameAmount && !sameCompactAmount(item.amount, needle.amount)) continue;
            return i;
        }
        return -1;
    }

    private boolean sameCompactAmount(String a, String b){
        return a == null ? b == null : a.equals(b);
    }

    private boolean sameCompactRows(SnapshotRow before, SnapshotRow after){
        if(before == null || after == null) return before == after;
        String left = compactRowText(before);
        String right = compactRowText(after);
        if(left == null || right == null) return sameTextAndNumbers(before, after);
        return Strings.stripColors(left).equals(Strings.stripColors(right));
    }

    private Seq<CompactStackItem> parseCompactStackItems(String text){
        Seq<CompactStackItem> out = new Seq<>();
        if(text == null) return out;
        int index = 0;
        while(index < text.length()){
            ContentMatch match = findIconMatch(text, index);
            if(match == null) break;
            StackAmount amount = parseStackAmount(text, match.end, match.content);
            if(amount != null && amount.amountText != null && !amount.amountText.isEmpty()){
                String amountText = amount.amountText + (amount.suffix == null ? "" : amount.suffix);
                out.add(new CompactStackItem(match.content, amountText));
                index = Math.max(amount.end, match.end);
            }else{
                out.add(new CompactStackItem(match.content, ""));
                int consumed = consumeIconLabelTail(text, match.end, match.content);
                index = consumed == match.end ? match.end : consumed;
            }
        }
        return out;
    }

    private boolean shouldFilterCommonCompactLines(RowKind kind){
        return kind == RowKind.WEAPON_PANEL || kind == RowKind.ABILITY_PANEL || kind == RowKind.NATIVE_PANEL || kind == RowKind.AMMO_PANEL;
    }

    private String compactDifferentLines(String source, String other){
        if(source == null) return null;
        if(other == null) return source;
        String[] sourceLines = source.split("\\n", -1);
        String[] otherLines = other.split("\\n", -1);
        boolean[] used = new boolean[otherLines.length];
        StringBuilder out = new StringBuilder();
        for(String line : sourceLines){
            String key = compactLineCompareKey(line);
            if(key == null) continue;
            boolean matched = false;
            for(int i = 0; i < otherLines.length; i++){
                if(used[i]) continue;
                String otherKey = compactLineCompareKey(otherLines[i]);
                if(key.equals(otherKey)){
                    used[i] = true;
                    matched = true;
                    break;
                }
            }
            if(matched) continue;
            if(out.length() > 0) out.append("\n");
            out.append(line.trim());
        }
        return out.length() == 0 ? null : out.toString();
    }

    private String compactLineCompareKey(String line){
        if(line == null) return null;
        String key = Strings.stripColors(line)
            .replaceAll("[ \\t]+", " ")
            .trim();
        return key.isEmpty() ? null : key;
    }

    private void addCompactChange(CompactDiff compact, String label, String before, String after, RowKind kind){
        String left = cleanCompactValue(before);
        String right = cleanCompactValue(after);
        String leftCompare = normalizeText(left);
        String rightCompare = normalizeText(right);
        if(leftCompare == null ? rightCompare == null : leftCompare.equals(rightCompare)) return;
        String name = escape(label == null ? "" : label);
        String labelValueSeparator = compactValueOnOwnLine(kind) ? ":\n" : ": ";
        String modifiedLabelValueSeparator = compactValueOnOwnLine(kind) ? ": []\n" : ": []";
        if(left == null){
            compact.added.add(addedColorTag() + "+[]" + name + labelValueSeparator + addedColorTag() + escape(formatCompactValue(right, kind)) + "[]");
        }else if(right == null){
            compact.removed.add(removedColorTag() + name + labelValueSeparator + escape(formatCompactValue(left, kind)) + "[]");
        }else{
            String oldValue = formatCompactValue(left, kind);
            String newValue = formatCompactValue(right, kind);
            compact.modified.add("[lightgray]" + name + modifiedLabelValueSeparator + modifiedOldColorTag() + escape(oldValue) + breakArrow(oldValue, newValue, kind) + modifiedNewColorTag() + escape(newValue) + "[]");
        }
    }

    private boolean compactValueOnOwnLine(RowKind kind){
        return kind == RowKind.STACK_LIST || kind == RowKind.BUILD_COST;
    }

    private String compactRowText(SnapshotRow row){
        if(row == null) return null;
        String text = row.compactText == null ? row.text : row.compactText;
        return effectiveCompactKind(row) == RowKind.STACK_LIST ? normalizeStackCompactText(text) : text;
    }

    private RowKind effectiveCompactKind(SnapshotRow row){
        if(row == null) return RowKind.TEXT;
        return row.compactKind == null ? row.kind : row.compactKind;
    }

    private String compactStacks(Seq<ItemStack> stacks){
        if(stacks == null || stacks.isEmpty()) return null;
        StringBuilder out = new StringBuilder();
        for(ItemStack stack : stacks){
            if(stack == null || stack.item == null) continue;
            if(out.length() > 0) out.append(" ");
            out.append(iconToken(stack.item)).append(" x").append(stack.amount);
        }
        return out.length() == 0 ? null : out.toString();
    }

    private String formatCompactValue(String value, RowKind kind){
        if(value == null) return null;
        if(kind == RowKind.BUILD_COST || kind == RowKind.STACK_LIST){
            return Strings.stripColors(value).replaceAll(" *\\n *", " ");
        }
        return value;
    }

    private String cleanCompactValue(String value){
        if(value == null) return null;
        String cleaned = value
            .replace("<Image>", "")
            .replace("<Stack>", "")
            .replace("<Collapser>", "")
            .replaceAll("[ \t]+", " ")
            .replaceAll(" *\\n *", "\n")
            .trim();
        return cleaned.isEmpty() ? null : cleaned;
    }

    private boolean sameNumbers(SnapshotRow before, SnapshotRow after){
        if(before == null || after == null) return before == after;
        if(before.numeric.size != after.numeric.size) return false;
        for(int i = 0; i < before.numeric.size; i++){
            if(!before.numeric.get(i).equals(after.numeric.get(i))) return false;
        }
        return true;
    }

    private boolean sameTextAndNumbers(SnapshotRow before, SnapshotRow after){
        if(before == null || after == null) return before == after;
        boolean sameText = before.text == null ? after.text == null : before.text.equals(after.text);
        if(!sameText) return false;
        if(before.numeric.size != after.numeric.size) return false;
        for(int i = 0; i < before.numeric.size; i++){
            if(!before.numeric.get(i).equals(after.numeric.get(i))) return false;
        }
        return true;
    }

    private boolean sameStacks(Seq<ItemStack> before, Seq<ItemStack> after){
        if(before == null || after == null) return before == after;
        if(before.size != after.size) return false;
        for(int i = 0; i < before.size; i++){
            ItemStack a = before.get(i), b = after.get(i);
            if(a == null || b == null) return a == b;
            if(a.item != b.item || a.amount != b.amount) return false;
        }
        return true;
    }

    private Seq<ItemStack> copyStacks(Seq<ItemStack> source){
        if(source == null) return null;
        Seq<ItemStack> out = new Seq<>();
        for(ItemStack stack : source){
            out.add(stack == null ? null : stack.copy());
        }
        return out;
    }

    private String buildDiff(String before, String after){
        String left = normalizeText(before);
        String right = normalizeText(after);
        if(left == null ? right == null : left.equals(right)) return null;
        if(left == null && right == null) return null;
        if(left == null) return addedColorTag() + escape(right) + "[]";
        if(right == null) return removedColorTag() + escape(left) + "[]";
        return modifiedOldColorTag() + escape(left) + breakArrow(left, right) + modifiedNewColorTag() + escape(right) + "[]";
    }

    private String breakArrow(String before, String after){
        if(before.length() + after.length() > 48 || before.contains("\n") || after.contains("\n")){
            return "[] " + arrowColor + "-> []\n";
        }
        return "[] " + arrowColor + "-> []";
    }

    private String breakArrow(String before, String after, RowKind kind){
        return breakArrow(before, after);
    }

    private float estimateDialogWidth(UnlockableContent content, ContentSnapshot before, ContentSnapshot after, ContentDiff diff, String titleMarkup, String visibleDescription, String visibleDetails){
        float width = minContentWidth;

        width = Math.max(width, measureMarkupWidth(titleMarkup) + Vars.iconXLarge + 28f);
        if(visibleDescription != null){
            width = Math.max(width, Math.min(maxDescriptionMeasureWidth, measureMarkupWidth(visibleDescription) + 24f));
        }

        OrderedSet<String> displayKeys = new OrderedSet<>();
        if(before != null) displayKeys.addAll(before.order);
        if(after != null) displayKeys.addAll(after.order);
        if(diff != null) displayKeys.addAll(diff.rows.keys().toSeq());

        for(String key : displayKeys){
            SnapshotRow currentRow = after == null ? null : after.rows.get(key);
            SnapshotRow beforeRow = before == null ? null : before.rows.get(key);
            InlineRow row = diff == null ? null : diff.rows.get(key);
            SnapshotRow source = currentRow != null ? currentRow : beforeRow;
            if(source == null){
                if(isBuildCostDiffRow(row)){
                    width = Math.max(width, estimateBuildCostDiffWidth(row));
                }
                continue;
            }
            width = Math.max(width, estimateRowWidth(source, currentRow, row));
        }

        if(visibleDetails != null){
            width = Math.max(width, Math.min(detailsWidth, measureMarkupWidth(visibleDetails) + 24f));
        }

        return Math.min(width, maxMeasuredContentWidth);
    }

    private float estimateRowWidth(SnapshotRow source, SnapshotRow currentRow, InlineRow row){
        float labelWidth = measureMarkupWidth(source.label);
        float width = labelWidth + 48f;
        float minValueWidth = minimumValueWidth(source.kind);
        if(row == null){
            if(currentRow == null) return width;
            if(currentRow.rendered != null){
                float valueWidth = Math.max(measureRenderedWidth(currentRow.rendered) + 12f, minValueWidth);
                return combineRowWidth(source.kind, labelWidth, width, valueWidth);
            }
            return Math.max(width + Math.min(maxTextDiffMeasureWidth, measureMarkupWidth(currentRow.text)) + 12f, width + minValueWidth);
        }

        if(row.buildCostBefore != null || row.buildCostAfter != null){
            return width + Math.max(measureStacksWidth(row.buildCostBefore), measureStacksWidth(row.buildCostAfter)) + 56f;
        }
        if(row.nativeWidgetDiff){
            float valueWidth = Math.max(Math.max(measureRenderedWidth(row.beforeRendered), measureRenderedWidth(row.afterRendered)) + 24f, minValueWidth);
            return combineRowWidth(source.kind, labelWidth, width, valueWidth);
        }
        return Math.max(width + Math.min(maxTextDiffMeasureWidth, measureMarkupWidth(Strings.stripColors(row.markup))) + 12f, width + minValueWidth);
    }

    private float estimateBuildCostDiffWidth(InlineRow row){
        if(row == null) return 0f;
        float labelWidth = measureMarkupWidth(row.label);
        float width = labelWidth + 48f;
        return width + Math.max(measureStacksWidth(row.buildCostBefore), measureStacksWidth(row.buildCostAfter)) + 56f;
    }

    private float combineRowWidth(RowKind kind, float labelWidth, float inlineWidth, float valueWidth){
        if(isBlockPanelKind(kind)){
            return Math.max(labelWidth + 24f, valueWidth + 24f);
        }
        return inlineWidth + valueWidth;
    }

    private float measureRenderedWidth(Table rendered){
        if(rendered == null) return 0f;
        rendered.pack();
        return Math.max(rendered.getPrefWidth(), rendered.getWidth());
    }

    private float measureStacksWidth(Seq<ItemStack> stacks){
        if(stacks == null || stacks.isEmpty()) return 0f;
        Table table = new Table();
        table.left().top().defaults().left().top();
        for(ItemStack stack : stacks){
            if(stack == null || stack.item == null) continue;
            table.add(StatValues.stack(stack)).padRight(5f);
        }
        return table.getPrefWidth();
    }

    private float measureMarkupWidth(String text){
        if(text == null) return 0f;
        String cleaned = Strings.stripColors(text);
        float max = 0f;
        for(String line : cleaned.split("\\n", -1)){
            Label label = new Label(line == null ? "" : line);
            max = Math.max(max, label.getPrefWidth());
        }
        return max;
    }

    private float minimumValueWidth(RowKind kind){
        if(kind == RowKind.STACK_LIST) return 500f;
        if(kind == RowKind.AMMO_PANEL || kind == RowKind.WEAPON_PANEL || kind == RowKind.ABILITY_PANEL || kind == RowKind.NATIVE_PANEL) return 360f;
        return 160f;
    }

    private float rowValueWidth(String label, float contentWidth, RowKind kind){
        if(isBlockPanelKind(kind)){
            return Math.max(minimumValueWidth(kind), contentWidth - 12f);
        }
        return Math.max(minimumValueWidth(kind), contentWidth - measureMarkupWidth(label) - 28f);
    }

    private boolean isBlockPanelKind(RowKind kind){
        return kind == RowKind.STACK_LIST
            || kind == RowKind.NATIVE_PANEL
            || kind == RowKind.AMMO_PANEL
            || kind == RowKind.WEAPON_PANEL
            || kind == RowKind.ABILITY_PANEL;
    }

    private boolean isCenteredPanelKind(RowKind kind){
        return kind == RowKind.STACK_LIST;
    }

    private boolean isBuildCostDiffRow(InlineRow row){
        return row != null && (row.buildCostBefore != null || row.buildCostAfter != null);
    }

    private void showPatched(UnlockableContent content){
        Vars.ui.content.cont.clear();

        Table table = new Table();
        table.margin(10f);
        content.checkStats();
        ContentDiff diff = diffsByContent.get(content);
        ContentSnapshot before = baselineSnapshots.get(content);
        ContentSnapshot after = afterSnapshots.get(content);
        int logicId = content.getLogicId();
        String titleMarkup = diff == null || diff.title == null
            ? "[accent]" + content.localizedName + "\n[gray]" + content.name + (logicId != -1 ? " <#" + logicId + ">" : "")
            : "[accent]" + diff.title + "\n[gray]" + content.name + (logicId != -1 ? " <#" + logicId + ">" : "");
        String visibleDescription = diff == null || diff.description == null ? content.displayDescription() : diff.description;
        String visibleDetails = diff == null || diff.details == null ? content.details : diff.details;
        float contentWidth = estimateDialogWidth(content, before, after, diff, titleMarkup, visibleDescription, visibleDetails);
        float titleWidth = Math.max(260f, contentWidth - Vars.iconXLarge - 16f);
        float descriptionWidth = contentWidth;
        table.table(title1 -> {
            title1.image(content.uiIcon).size(Vars.iconXLarge).scaling(Scaling.fit).get().clicked(() -> Core.app.setClipboardText(content.emoji()));
            Cell<Label> titleCell = title1.add(new Label(titleMarkup)).padLeft(5f).width(titleWidth);
            titleCell.growX().left();
            titleCell.get().setWrap(true);
        });
        table.row();

        if(Vars.state.isGame() && diffsByContent.containsKey(content)){
            table.table(t -> {
                t.image(Icon.info).color(Pal.lightishGray);
                t.add("@database.patched").color(Pal.lightishGray).padLeft(4f);
            }).pad(4f).left();
            table.row();
        }

        if(visibleDescription != null){
            boolean any = content.stats.toMap().size > 0;
            if(any){
                table.add("@category.purpose").color(Pal.accent).fillX().padTop(10f);
                table.row();
            }
            Cell<Label> descriptionCell = table.add(new Label("[lightgray]" + visibleDescription));
            descriptionCell.wrap().fillX().padLeft(any ? 10f : 0f).width(descriptionWidth).padTop(any ? 0f : 10f).left();
            descriptionCell.get().setWrap(true);
            table.row();
            if(!content.stats.useCategories && any){
                table.add("@category.general").fillX().color(Pal.accent);
                table.row();
            }
        }

        OrderedSet<String> displayKeys = new OrderedSet<>();
        if(before != null) displayKeys.addAll(before.order);
        if(after != null) displayKeys.addAll(after.order);
        if(diff != null) displayKeys.addAll(diff.rows.keys().toSeq());
        OrderedSet<String> displayedCategories = new OrderedSet<>();

        for(String key : displayKeys){
            SnapshotRow currentRow = after == null ? null : after.rows.get(key);
            SnapshotRow beforeRow = before == null ? null : before.rows.get(key);
            InlineRow row = diff == null ? null : diff.rows.get(key);
            SnapshotRow source = currentRow != null ? currentRow : beforeRow;
            if(source == null && !isBuildCostDiffRow(row)) continue;

            if(source != null){
                boolean showCategory = false;
                if(displayedCategories.add(source.cat.name)) showCategory = content.stats.useCategories;
                if(showCategory){
                    table.add("@category." + source.cat.name).color(Pal.accent).fillX();
                    table.row();
                }
            }

            table.table(inset -> {
                inset.left().top().defaults().left().top();
                String label = source != null ? source.label : row.label;
                RowKind kind = source != null ? source.kind : row.kind;
                boolean blockRow = isBlockPanelKind(kind);
                inset.add("[lightgray]" + label + ":[] ").top().left();
                if(blockRow) inset.row();
                float valueWidth = rowValueWidth(label, contentWidth, kind);
                if(row == null && currentRow != null){
                    renderSnapshotRow(inset, currentRow, valueWidth);
                }else if(isBuildCostDiffRow(row)){
                    renderBuildCostDiff(inset, row.buildCostBefore, row.buildCostAfter, valueWidth);
                }else if(row != null && row.nativeWidgetDiff){
                    renderNativeDiff(inset, row.beforeRendered, row.beforeLabels, row.afterRendered, row.afterLabels, row.kind, valueWidth);
                }else if(row != null){
                    Label diffLabel = new Label(row.markup);
                    diffLabel.setWrap(true);
                    Cell<Label> cell = inset.add(diffLabel);
                    cell.growX().top().fillX().width(valueWidth);
                }
            }).left().top().fillX().width(contentWidth).padLeft(10f);
            table.row();
        }

        if(visibleDetails != null){
            table.add("[gray]" + visibleDetails).pad(6f).padTop(20f).width(Math.min(detailsWidth, contentWidth)).wrap().fillX();
            table.row();
        }

        String credit = Reflector.getString(content, "credit");
        if(credit != null){
            table.row();
            table.add("Created by: " + credit).color(Color.gray).padTop(40f).row();
        }

        if(Core.settings.getBool("console")){
            table.button("@viewfields", Icon.link, Styles.grayt, () -> {
                Class<?> contentClass = content.getClass();
                if(contentClass.isAnonymousClass()) contentClass = contentClass.getSuperclass();
                Core.app.openURI("https://mindustrygame.github.io/wiki/Modding%20Classes/" + contentClass.getSimpleName());
            }).margin(8f).pad(4f).padTop(16f).size(300f, 50f).row();
        }

        content.displayExtra(table);

        table.table(t -> {
            t.defaults().size(40f);
            t.button(content.emoji(), Styles.cleart, () -> Core.app.setClipboardText(content.emoji())).tooltip(content.emoji());
            t.button(Icon.info, Styles.clearNonei, () -> Core.app.setClipboardText(content.name)).tooltip(content.name);
            if(content.description != null){
                t.button(Icon.book, Styles.clearNonei, () -> Core.app.setClipboardText(content.description)).tooltip(content.description);
            }
            if(Vars.net.active()){
                try{
                    t.button("♐简", Styles.cleart, () -> shareContent(content, false)).width(60f);
                    t.button("♐详", Styles.cleart, () -> shareContent(content, true)).width(60f);
                }catch(Throwable ignored){
                }
            }
        }).fillX().padLeft(10f);

        ScrollPane pane = new ScrollPane(table);
        table.marginRight(30f);
        pane.setScrollingDisabled(false, false);
        float paneWidth = contentWidth + 50f;
        if(Core.scene != null){
            paneWidth = Math.min(paneWidth, Math.max(minContentWidth, Core.scene.getWidth() - 40f));
        }
        Vars.ui.content.cont.add(pane).width(paneWidth).maxHeight(Core.scene == null ? 900f : Core.scene.getHeight() - 120f);
        if(Vars.ui.content.isShown()){
            Vars.ui.content.show(Core.scene, Actions.fadeIn(0f));
        }else{
            Vars.ui.content.show();
        }
    }

    private void renderSnapshotRow(Table table, SnapshotRow row, float textRowWidth){
        if(row == null){
            return;
        }
        if(row.rendered != null){
            restoreLabelStates(row.labels);
            if(isCenteredPanelKind(row.kind)){
                row.rendered.pack();
                renderCenteredRenderedRow(table, row.rendered, textRowWidth);
            }else if(isBlockPanelKind(row.kind)){
                row.rendered.invalidateHierarchy();
                table.add(row.rendered).left().top().fillX().growX().width(textRowWidth);
            }else{
                row.rendered.pack();
                table.add(row.rendered).left().top().fillX().growX().width(textRowWidth);
            }
            return;
        }
        if(row.text != null){
            Label label = new Label(row.text);
            label.setWrap(true);
            table.add(label).left().top().fillX().growX().width(textRowWidth);
        }
    }

    private void renderCenteredRenderedRow(Table table, Table rendered, float width){
        if(rendered == null){
            return;
        }
        rendered.pack();
        table.table(holder -> {
            holder.top();
            holder.defaults().top();
            holder.add(rendered).top().center();
        }).top().fillX().growX().width(width);
    }

    private void renderRenderedValue(Table table, Table rendered, RowKind kind){
        if(rendered == null){
            return;
        }
        if(isCenteredPanelKind(kind)){
            rendered.pack();
            table.top().defaults().top();
            table.table(holder -> {
                holder.top();
                holder.defaults().top();
                holder.add(rendered).top().center();
            }).top().fillX().growX();
        }else if(isBlockPanelKind(kind)){
            rendered.invalidateHierarchy();
            table.left().top().defaults().left().top();
            table.add(rendered).left().top().fillX().growX();
        }else{
            rendered.pack();
            table.left().top().defaults().left().top();
            table.add(rendered).left().top().fillX().growX();
        }
    }

    private void renderDiffPanel(Table table, String labelMarkup, Table rendered, RowKind kind, float contentWidth, float bottomPad){
        if(rendered == null) return;
        if(labelMarkup != null && !labelMarkup.isEmpty()){
            table.add(labelMarkup).left().padBottom(2f).row();
        }
        table.table(Styles.grayPanel, panel -> renderRenderedValue(panel, rendered, kind)).left().top().fillX().width(contentWidth).growX().padBottom(bottomPad);
        table.row();
    }

    private void renderNativeDiff(Table table, Table before, Seq<LabelState> beforeLabels, Table after, Seq<LabelState> afterLabels, RowKind kind, float contentWidth){
        Table root = new Table();
        root.left().top().defaults().left().top();
        if(before != null && after != null){
            prepareRenderedDiff(beforeLabels, afterLabels);
            if(kind == RowKind.STACK_LIST){
                highlightChangedGroups(beforeLabels);
                highlightChangedGroups(afterLabels);
            }
            renderDiffPanel(root, null, before, kind, contentWidth, 6f);
            root.add(arrowColor + "->[]").left().padTop(2f).padBottom(6f).row();
            renderDiffPanel(root, null, after, kind, contentWidth, 0f);
        }else if(before != null){
            restoreLabelStates(beforeLabels);
            highlightAllLabels(beforeLabels, removedColorTag());
            if(kind == RowKind.STACK_LIST){
                highlightChangedGroups(beforeLabels);
            }
            renderDiffPanel(root, null, before, kind, contentWidth, 0f);
        }else if(after != null){
            restoreLabelStates(afterLabels);
            highlightAllLabels(afterLabels, addedColorTag());
            if(kind == RowKind.STACK_LIST){
                highlightChangedGroups(afterLabels);
            }
            renderDiffPanel(root, null, after, kind, contentWidth, 0f);
        }
        table.add(root).left().top().fillX().growX();
    }

    private void renderBuildCostDiff(Table table, Seq<ItemStack> before, Seq<ItemStack> after, float availableWidth){
        Table line = new Table();
        line.left().top().defaults().left().top();
        if(before != null && after != null){
            boolean inline = shouldInlineBuildCost(before, after, availableWidth);
            if(inline){
                addBuildCostStacks(line, before, modifiedOldColorTag());
                line.add(arrowColor + "->[]").padLeft(6f).padRight(6f).top();
                addBuildCostStacks(line, after, modifiedNewColorTag());
            }else{
                addBuildCostStacks(line, before, modifiedOldColorTag());
                line.row();
                line.add(arrowColor + "->[]").padRight(5f).top().left();
                line.row();
                addBuildCostStacks(line, after, modifiedNewColorTag());
            }
        }else if(before != null){
            addBuildCostStacks(line, before, removedColorTag());
        }else if(after != null){
            addBuildCostStacks(line, after, addedColorTag());
        }
        table.add(line).growX().top().left();
    }

    private void addBuildCostStacks(Table table, Seq<ItemStack> stacks, String amountColorTag){
        if(table == null || stacks == null) return;
        for(ItemStack stack : stacks){
            if(stack == null || stack.item == null) continue;
            Element stackView = StatValues.stack(stack);
            colorStackAmountLabels(stackView, amountColorTag);
            table.add(stackView).padRight(5f).top();
        }
    }

    private void renderCompactMarkupValue(Table table, String text, float width){
        if(table == null || text == null) return;
        Seq<String> lines = splitCompactDisplayLines(compactDisplayText(text, false, width));
        for(String lineText : lines){
            if(lineText == null || Strings.stripColors(lineText).trim().isEmpty()) continue;
            Table line = new Table();
            line.left().top().defaults().left().top();
            addCompactInline(line, lineText, width);
            table.add(line).left().top().width(width).growX().fillX();
            table.row();
        }
    }

    private boolean renderCompactStackDiff(Table table, SnapshotRow beforeRow, SnapshotRow afterRow, float width){
        String beforeText = compactRowText(beforeRow);
        String afterText = compactRowText(afterRow);
        if(beforeText == null && afterText == null) return false;

        Table root = new Table();
        root.left().top().defaults().left().top();
        if(beforeText != null && afterText != null){
            renderCompactMarkupValue(root, modifiedOldColorTag() + beforeText + "[]", width);
            root.add(arrowColor + "->[]").left().padTop(2f).padBottom(4f).row();
            renderCompactMarkupValue(root, modifiedNewColorTag() + afterText + "[]", width);
        }else if(beforeText != null){
            renderCompactMarkupValue(root, removedColorTag() + beforeText + "[]", width);
        }else{
            renderCompactMarkupValue(root, addedColorTag() + afterText + "[]", width);
        }
        table.add(root).left().top().fillX().growX().width(width);
        return true;
    }

    private boolean shouldInlineBuildCost(Seq<ItemStack> before, Seq<ItemStack> after, float availableWidth){
        if(before == null || after == null) return false;
        float leftWidth = measureStacksWidth(before);
        float rightWidth = measureStacksWidth(after);
        float arrowWidth = measureMarkupWidth("->") + 20f;
        return leftWidth + arrowWidth + rightWidth <= Math.max(availableWidth, 0f);
    }

    private void colorStackAmountLabels(Element element, String amountColorTag){
        if(element == null || amountColorTag == null) return;
        if(element instanceof Label){
            Label label = (Label)element;
            String raw = label.getText() == null ? "" : label.getText().toString();
            String visible = Strings.stripColors(raw);
            if(visible != null && numberPattern.matcher(visible).find()){
                label.setColor(1f, 1f, 1f, label.color.a);
                label.setText(amountColorTag + escape(visible) + "[]");
            }
            return;
        }
        if(element instanceof ScrollPane){
            colorStackAmountLabels(((ScrollPane)element).getWidget(), amountColorTag);
            return;
        }
        if(element instanceof Group){
            Seq<Element> children = ((Group)element).getChildren();
            for(int i = 0; i < children.size; i++){
                colorStackAmountLabels(children.get(i), amountColorTag);
            }
        }
    }

    private void shareContent(UnlockableContent content, boolean detailed){
        try{
            Class<?> share = Class.forName("mindustryX.features.ShareFeature");
            share.getMethod("shareContent", UnlockableContent.class, boolean.class).invoke(null, content, detailed);
        }catch(Throwable ignored){
        }
    }

    private void buildSettings(SettingsMenuDialog.SettingsTable table){
        table.defaults().growX().pad(4f).left();

        table.checkPref(keyEnabled, true);
        table.pref(new QuickDisplayModeSetting());
        table.sliderPref(keyQuickHudOpacity, 70, 20, 100, 1, value -> value + "%");
        table.sliderPref(keyQuickHudWidth, 420, 100, 900, 10, value -> value + "px");
        addColorSetting(table, "patchviewer.settings.hud-background", "HUD background color", keyQuickHudBackgroundColor, defaultQuickHudBackgroundColor);
        table.pref(new MessageSetting("patchviewer-color-hint", "patchviewer.settings.color-hint", "Colors support named values like gold and hex values like #ffd700 or ffd700."));
        addColorSetting(table, "patchviewer.settings.removed", "Removed entry color", keyRemovedColor, defaultRemovedColor);
        addColorSetting(table, "patchviewer.settings.modified-old", "Modified entry color (before)", keyModifiedOldColor, defaultModifiedOldColor);
        addColorSetting(table, "patchviewer.settings.modified-new", "Modified entry color (after)", keyModifiedNewColor, defaultModifiedNewColor);
        addColorSetting(table, "patchviewer.settings.added", "Added entry color", keyAddedColor, defaultAddedColor);
    }

    /** Populates a {@link mindustry.ui.dialogs.SettingsMenuDialog.SettingsTable} with this mod's settings. */
    public void bekBuildSettings(SettingsMenuDialog.SettingsTable table){
        buildSettings(table);
    }


    private void addColorSetting(SettingsMenuDialog.SettingsTable table, String titleKey, String titleFallback, String key, String defaultValue){
        table.pref(new ColorSetting(key, titleKey, titleFallback, defaultValue));
    }

    private String settingTitle(String key, String fallbackKey, String fallback){
        String defaultTitle = bundle(fallbackKey, fallback);
        return Core.bundle == null ? defaultTitle : Core.bundle.get("setting." + key + ".name", defaultTitle);
    }

    private class QuickDisplayModeSetting extends SettingsMenuDialog.SettingsTable.Setting{
        QuickDisplayModeSetting(){
            super(keyQuickDisplayMode);
            title = settingTitle(name, "patchviewer.settings.quick-mode", "Quick display mode");
        }

        @Override
        public void add(SettingsMenuDialog.SettingsTable table){
            Cell<Table> cell = table.table(row -> {
                row.left().defaults().left();
                row.add(title).width(210f).wrap().padRight(8f);

                CheckBox cursor = new CheckBox(modeLabel(quickModeHud));
                CheckBox buildInfo = new CheckBox(modeLabel(quickModeBuildInfo));
                boolean[] updating = {false};
                Runnable refresh = () -> {
                    updating[0] = true;
                    String mode = readQuickDisplayMode();
                    cursor.setChecked(quickModeHud.equals(mode));
                    buildInfo.setChecked(quickModeBuildInfo.equals(mode));
                    updating[0] = false;
                };

                cursor.changed(() -> {
                    if(updating[0]) return;
                    Core.settings.put(name, cursor.isChecked() ? quickModeHud : quickModeBuildInfo);
                    refresh.run();
                });
                buildInfo.changed(() -> {
                    if(updating[0]) return;
                    Core.settings.put(name, buildInfo.isChecked() ? quickModeBuildInfo : quickModeHud);
                    refresh.run();
                });

                refresh.run();
                row.add(cursor).padRight(18f);
                row.add(buildInfo);
            }).growX().fillX();

            addDesc(cell.get());
            table.row();
        }
    }

    private class MessageSetting extends SettingsMenuDialog.SettingsTable.Setting{
        private final String messageKey;
        private final String messageFallback;

        MessageSetting(String name, String messageKey, String messageFallback){
            super(name);
            title = null;
            this.messageKey = messageKey;
            this.messageFallback = messageFallback;
        }

        @Override
        public void add(SettingsMenuDialog.SettingsTable table){
            table.add("[lightgray]" + bundle(messageKey, messageFallback) + "[]").left().wrap().growX();
            table.row();
        }
    }

    private class ColorSetting extends SettingsMenuDialog.SettingsTable.Setting{
        private final String defaultValue;

        ColorSetting(String name, String titleKey, String titleFallback, String defaultValue){
            super(name);
            title = settingTitle(name, titleKey, titleFallback);
            this.defaultValue = defaultValue;
        }

        @Override
        public void add(SettingsMenuDialog.SettingsTable table){
            Cell<Table> cell = table.table(row -> {
                row.left().defaults().left();

                row.add(title).width(210f).wrap().padRight(8f);

                TextField field = new TextField(readColorSetting(name, defaultValue));
                field.setMessageText(defaultValue);
                row.add(field).minWidth(180f).growX().maxWidth(280f);

                Image preview = new Image(Tex.whiteui);
                preview.setColor(readColorValue(name, defaultValue));
                row.add(preview).size(26f).padLeft(8f).padRight(8f);

                Label sample = new Label("");
                sample.setColor(Color.white);
                row.add(sample).minWidth(64f).left();

                boolean[] updating = {false};
                Runnable refresh = () -> {
                    String normalized = normalizeColorSpec(field.getText());
                    if(normalized != null){
                        if(!updating[0]){
                            Core.settings.put(name, normalized);
                        }
                        preview.setColor(parseColorSpec(normalized, readColorValue(name, defaultValue)));
                        sample.setText(colorTag(normalized) + bundle("patchviewer.settings.preview", "Preview") + "[]");
                    }else{
                        preview.setColor(readColorValue(name, defaultValue));
                        sample.setText("[lightgray]" + bundle("patchviewer.settings.invalid", "Invalid") + "[]");
                    }
                };

                field.changed(() -> {
                    if(updating[0]) return;
                    refresh.run();
                });
                updating[0] = true;
                refresh.run();
                updating[0] = false;

                row.button(bundle("patchviewer.settings.reset", "Reset"), Styles.flatt, () -> {
                    Core.settings.remove(name);
                    updating[0] = true;
                    field.setText(readColorSetting(name, defaultValue));
                    refresh.run();
                    updating[0] = false;
                }).height(42f).padLeft(8f);
            }).growX().fillX();

            addDesc(cell.get());
            table.row();
        }
    }

    private String bundle(String key, String fallback){
        return Core.bundle == null ? fallback : Core.bundle.get(key, fallback);
    }

    private String escape(String text){
        return text == null ? "null" : text.replace("[", "[[");
    }

    private boolean isPatchViewerEnabled(){
        return Core.settings.getBool(keyEnabled, true);
    }

    private String readQuickDisplayMode(){
        String mode = Core.settings.getString(keyQuickDisplayMode, quickModeHud);
        return quickModeBuildInfo.equals(mode) ? quickModeBuildInfo : quickModeHud;
    }

    private String modeLabel(String mode){
        return quickModeBuildInfo.equals(mode)
            ? bundle("patchviewer.settings.quick-mode.buildinfo", "Build info")
            : bundle("patchviewer.settings.quick-mode.cursor", "Near cursor");
    }

    private float readQuickHudOpacity(){
        return Mathf.clamp(Core.settings.getInt(keyQuickHudOpacity, 70), 20, 100) / 100f;
    }

    private float readQuickHudWidth(){
        return Mathf.clamp(Core.settings.getInt(keyQuickHudWidth, 420), 100, 900);
    }

    private Color readQuickHudBackgroundColor(){
        return readColorValue(keyQuickHudBackgroundColor, defaultQuickHudBackgroundColor);
    }

    private String removedColorTag(){
        return colorTag(readColorSetting(keyRemovedColor, defaultRemovedColor));
    }

    private String modifiedOldColorTag(){
        return colorTag(readColorSetting(keyModifiedOldColor, defaultModifiedOldColor));
    }

    private String modifiedNewColorTag(){
        return colorTag(readColorSetting(keyModifiedNewColor, defaultModifiedNewColor));
    }

    private String addedColorTag(){
        return colorTag(readColorSetting(keyAddedColor, defaultAddedColor));
    }

    private String readColorSetting(String key, String fallback){
        String raw = Core.settings.getString(key, fallback);
        String normalized = normalizeColorSpec(raw);
        if(normalized == null){
            normalized = normalizeColorSpec(fallback);
        }
        return normalized == null ? fallback : normalized;
    }

    private Color readColorValue(String key, String fallback){
        return parseColorSpec(readColorSetting(key, fallback), Color.white);
    }

    private String normalizeColorSpec(String raw){
        if(raw == null) return null;
        String text = raw.trim();
        if(text.isEmpty()) return null;

        if(isHexColorSpec(text)){
            return "#" + stripLeadingHash(text).toUpperCase(Locale.ROOT);
        }

        String lower = text.toLowerCase(Locale.ROOT);
        if(Colors.get(lower) != null) return lower;
        if(Colors.get(text) != null) return text;
        String upper = text.toUpperCase(Locale.ROOT);
        if(Colors.get(upper) != null) return upper;
        return null;
    }

    private boolean isHexColorSpec(String raw){
        String text = stripLeadingHash(raw);
        if(text.length() != 6 && text.length() != 8) return false;
        for(int i = 0; i < text.length(); i++){
            char c = text.charAt(i);
            boolean digit = c >= '0' && c <= '9';
            boolean lower = c >= 'a' && c <= 'f';
            boolean upper = c >= 'A' && c <= 'F';
            if(!digit && !lower && !upper) return false;
        }
        return true;
    }

    private String stripLeadingHash(String raw){
        if(raw == null) return "";
        return raw.startsWith("#") ? raw.substring(1) : raw;
    }

    private Color parseColorSpec(String raw, Color fallback){
        String normalized = normalizeColorSpec(raw);
        if(normalized == null) return fallback;
        try{
            if(normalized.startsWith("#")){
                return Color.valueOf(normalized.substring(1));
            }
            Color named = Colors.get(normalized);
            if(named == null) named = Colors.get(normalized.toLowerCase(Locale.ROOT));
            if(named == null) named = Colors.get(normalized.toUpperCase(Locale.ROOT));
            return named == null ? fallback : named;
        }catch(Throwable ignored){
            return fallback;
        }
    }

    private String colorTag(String raw){
        String normalized = normalizeColorSpec(raw);
        if(normalized == null) normalized = "#FFFFFF";
        if(normalized.startsWith("#")){
            return "[#" + normalized.substring(1) + "]";
        }
        return "[" + normalized + "]";
    }

    private static class Reflector{
        static Object get(Object object, String fieldName){
            try{
                Class<?> current = object.getClass();
                while(current != null && current != Object.class){
                    try{
                        Field field = current.getDeclaredField(fieldName);
                        field.setAccessible(true);
                        return field.get(object);
                    }catch(NoSuchFieldException ignored){
                        current = current.getSuperclass();
                    }
                }
            }catch(Throwable ignored){
            }
            return null;
        }

        static String getString(Object object, String fieldName){
            Object value = get(object, fieldName);
            return value == null ? null : String.valueOf(value);
        }
    }

    private static class ContentSnapshot{
        String title;
        String description;
        String details;
        Seq<ItemStack> buildCost;
        final OrderedSet<String> order = new OrderedSet<>();
        final ObjectMap<String, SnapshotRow> rows = new ObjectMap<>();
    }

    private static class SnapshotRow{
        final StatCat cat;
        final Stat stat;
        final String label;
        RowKind kind;
        RowKind compactKind;
        String text;
        String compactText;
        Seq<String> numeric = new Seq<>();
        Table rendered;
        final Seq<LabelState> labels = new Seq<>();

        SnapshotRow(StatCat cat, Stat stat, String label){
            this.cat = cat;
            this.stat = stat;
            this.label = label;
        }
    }

    private static class ContentDiff{
        String title;
        String description;
        String details;
        final ObjectMap<String, InlineRow> rows = new ObjectMap<>();

        boolean isEmpty(){
            return title == null && description == null && details == null && rows.isEmpty();
        }
    }

    private static class CompactDiff{
        final Seq<String> added = new Seq<>();
        final Seq<String> modified = new Seq<>();
        final Seq<String> removed = new Seq<>();

        boolean isEmpty(){
            return added.isEmpty() && modified.isEmpty() && removed.isEmpty();
        }
    }

    private static class ContentMatch{
        final UnlockableContent content;
        final int start;
        final int end;

        ContentMatch(UnlockableContent content, int start, int end){
            this.content = content;
            this.start = start;
            this.end = end;
        }
    }

    private static class StackAmount{
        final String amountText;
        final int end;
        final String suffix;

        StackAmount(String amountText, int end, String suffix){
            this.amountText = amountText;
            this.end = end;
            this.suffix = suffix;
        }
    }

    private static class RateSuffix{
        final String suffix;
        final int end;

        RateSuffix(String suffix, int end){
            this.suffix = suffix;
            this.end = end;
        }
    }

    private static class RateAmount{
        final String amountText;
        final String suffix;
        final int end;

        RateAmount(String amountText, String suffix, int end){
            this.amountText = amountText;
            this.suffix = suffix;
            this.end = end;
        }
    }

    private static class CompactPart{
        final StringBuilder text = new StringBuilder();
        boolean hasIcon;

        CompactPart(){
        }

        CompactPart(String text, boolean hasIcon){
            if(text != null) this.text.append(text);
            this.hasIcon = hasIcon;
        }
    }

    private class CompactStackItem{
        final UnlockableContent content;
        final String amount;

        CompactStackItem(UnlockableContent content, String amount){
            this.content = content;
            this.amount = amount == null ? "" : amount;
        }

        String markup(){
            return iconToken(content) + (amount.isEmpty() ? "" : " " + amount);
        }
    }

    private static class QuickTarget{
        final UnlockableContent content;
        final Table topTable;
        final boolean mi2u;

        QuickTarget(UnlockableContent content, Table topTable, boolean mi2u){
            this.content = content;
            this.topTable = topTable;
            this.mi2u = mi2u;
        }
    }

    private static class InlineRow{
        final String label;
        final String markup;
        final Seq<ItemStack> buildCostBefore;
        final Seq<ItemStack> buildCostAfter;
        final boolean nativeWidgetDiff;
        final RowKind kind;
        final Table beforeRendered;
        final Seq<LabelState> beforeLabels;
        final Table afterRendered;
        final Seq<LabelState> afterLabels;

        InlineRow(String label, String markup){
            this(label, markup, null, null, false, RowKind.TEXT, null, null, null, null);
        }

        InlineRow(String label, String markup, Seq<ItemStack> buildCostBefore, Seq<ItemStack> buildCostAfter, boolean nativeWidgetDiff, RowKind kind, Table beforeRendered, Seq<LabelState> beforeLabels, Table afterRendered, Seq<LabelState> afterLabels){
            this.label = label;
            this.markup = markup;
            this.buildCostBefore = buildCostBefore;
            this.buildCostAfter = buildCostAfter;
            this.nativeWidgetDiff = nativeWidgetDiff;
            this.kind = kind;
            this.beforeRendered = beforeRendered;
            this.beforeLabels = beforeLabels;
            this.afterRendered = afterRendered;
            this.afterLabels = afterLabels;
        }

        static InlineRow forText(String label, String markup){
            return new InlineRow(label, markup);
        }

        static InlineRow forBuildCost(String label, Seq<ItemStack> before, Seq<ItemStack> after){
            return new InlineRow(label, null, before, after, false, RowKind.BUILD_COST, null, null, null, null);
        }

        static InlineRow forNative(String label, RowKind kind, Table beforeRendered, Seq<LabelState> beforeLabels, Table afterRendered, Seq<LabelState> afterLabels){
            return new InlineRow(label, null, null, null, true, kind, beforeRendered, beforeLabels, afterRendered, afterLabels);
        }
    }

    private static class LabelState{
        final Label label;
        final String rawText;
        final String visibleText;
        final String normalizedText;
        final String matchKey;
        final String groupKey;
        final boolean insideStack;
        final Color originalColor;
        final boolean originalWrap;
        final String originalEllipsis;

        LabelState(Label label, String rawText, String visibleText, String normalizedText, String matchKey, String groupKey, boolean insideStack, Color originalColor, boolean originalWrap, String originalEllipsis){
            this.label = label;
            this.rawText = rawText;
            this.visibleText = visibleText == null ? "" : visibleText;
            this.normalizedText = normalizedText;
            this.matchKey = matchKey == null ? "" : matchKey;
            this.groupKey = groupKey == null ? "" : groupKey;
            this.insideStack = insideStack;
            this.originalColor = originalColor;
            this.originalWrap = originalWrap;
            this.originalEllipsis = originalEllipsis;
        }
    }

    private enum RowKind{
        TEXT,
        STACK_LIST,
        BUILD_COST,
        NATIVE_PANEL,
        AMMO_PANEL,
        WEAPON_PANEL,
        ABILITY_PANEL
    }
}
