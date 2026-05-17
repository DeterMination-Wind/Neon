package patchviewer;

import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.graphics.Colors;
import arc.scene.Element;
import arc.scene.Group;
import arc.scene.actions.Actions;
import arc.scene.ui.CheckBox;
import arc.scene.ui.Image;
import arc.scene.ui.Label;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.TextField;
import arc.scene.ui.layout.Cell;
import arc.scene.ui.layout.Stack;
import arc.scene.ui.layout.Table;
import arc.scene.style.Drawable;
import arc.files.Fi;
import arc.struct.ObjectMap;
import arc.struct.ObjectFloatMap;
import arc.struct.OrderedMap;
import arc.struct.OrderedSet;
import arc.struct.Seq;
import arc.util.Log;
import arc.util.Scaling;
import arc.util.Strings;
import bektools.profiler.NeonProfiler;
import mindustry.Vars;
import mindustry.ctype.Content;
import mindustry.ctype.ContentType;
import mindustry.ctype.UnlockableContent;
import mindustry.game.EventType;
import mindustry.gen.Icon;
import mindustry.gen.Tex;
import mindustry.graphics.Pal;
import mindustry.mod.Mod;
import mindustry.type.ItemStack;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.ContentInfoDialog;
import mindustry.ui.dialogs.SettingsMenuDialog;
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
    private static final String defaultRemovedColor = "red";
    private static final String defaultModifiedOldColor = "gold";
    private static final String defaultModifiedNewColor = "green";
    private static final String defaultAddedColor = "green";
    private static final Pattern numberPattern = Pattern.compile("-?\\d+(?:\\.\\d+)?");
    private static boolean settingsAdded;

    private final ObjectMap<UnlockableContent, ContentSnapshot> baselineSnapshots = new ObjectMap<>();
    private final ObjectMap<UnlockableContent, ContentSnapshot> afterSnapshots = new ObjectMap<>();
    private final ObjectMap<UnlockableContent, ContentDiff> diffsByContent = new ObjectMap<>();
    private final ObjectFloatMap<String> markupWidthCache = new ObjectFloatMap<>();
    private boolean baselineCaptured;
    private boolean baselineWindowClosed;
    private Label widthMeasureLabel;

    public PatchViewerMod(){
        setupDefaults();
        Events.on(EventType.ClientLoadEvent.class, event -> {
            installDialogHook();
            refreshSettingsBackupStorage();
            if(isPatchViewerEnabled()){
                Core.app.post(this::captureBaselineAtStartup);
            }
        });
        Events.on(EventType.WorldLoadEvent.class, event -> {
            baselineWindowClosed = true;
            invalidateAfterCaches();
            refreshSettingsBackupStorage();
        });
        Events.on(EventType.ResetEvent.class, event -> {
            invalidateAfterCaches();
        });
    }

    @Override
    public void init(){
        Events.on(EventType.ClientLoadEvent.class, event -> {
            if(settingsAdded) return;
            settingsAdded = true;
            if(Vars.ui == null || Vars.ui.settings == null) return;
            if(bekBundled) return;
            Vars.ui.settings.addCategory("@settings.patchviewer", Icon.settingsSmall, this::bekBuildSettings);
        });
    }

    private void installDialogHook(){
        try(NeonProfiler.Scope ignored = NeonProfiler.timeRoot("PV", "UI", "installDialogHook", NeonProfiler.threadMain)){
        try{
            Vars.ui.content = new ContentInfoDialog(){
                @Override
                public void show(UnlockableContent content){
                    if(!isPatchViewerEnabled()){
                        super.show(content);
                        return;
                    }
                    showPatched(content);
                }
            };
        }catch(Throwable error){
            Log.err("[PatchViewer] Failed to hook ContentInfoDialog.", error);
        }
        }
    }

    private void setupDefaults(){
        Core.settings.defaults(
            keyEnabled, true,
            keyRemovedColor, defaultRemovedColor,
            keyModifiedOldColor, defaultModifiedOldColor,
            keyModifiedNewColor, defaultModifiedNewColor,
            keyAddedColor, defaultAddedColor
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
        if(baselineCaptured || baselineWindowClosed || Vars.content == null || !isPatchViewerEnabled()) return;
        baselineSnapshots.clear();

        Seq<UnlockableContent> all = collectAllContents();
        for(UnlockableContent content : all){
            ContentSnapshot snapshot = snapshot(content, SnapshotMode.baseline);
            if(snapshot != null) baselineSnapshots.put(content, snapshot);
        }
        baselineCaptured = true;
    }

    private void invalidateAfterCaches(){
        afterSnapshots.clear();
        diffsByContent.clear();
    }

    private ContentSnapshot getAfterSnapshot(UnlockableContent content){
        if(content == null || !isPatchViewerEnabled()) return null;
        if(Vars.state == null || Vars.state.patcher == null || !Vars.state.isGame()) return null;
        ContentSnapshot cached = afterSnapshots.get(content);
        if(cached != null) return cached;

        ContentSnapshot snapshot = snapshot(content, SnapshotMode.current);
        if(snapshot != null){
            afterSnapshots.put(content, snapshot);
        }
        return snapshot;
    }

    private ContentDiff getContentDiff(UnlockableContent content, ContentSnapshot before, ContentSnapshot after){
        if(content == null || before == null || after == null) return null;
        ContentDiff cached = diffsByContent.get(content);
        if(cached != null) return cached;

        ContentDiff diff = diff(before, after);
        if(diff != null && !diff.isEmpty()){
            diffsByContent.put(content, diff);
        }
        return diff;
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

    private ContentSnapshot snapshot(UnlockableContent content, SnapshotMode mode){
        if(content == null) return null;
        content.checkStats();

        ContentSnapshot snapshot = new ContentSnapshot();
        snapshot.title = normalizeText(content.localizedName);
        snapshot.description = normalizeText(content.displayDescription());
        snapshot.details = normalizeText(content.details);
        snapshot.buildCost = extractBuildCost(content);
        snapshot.buildCostWidth = measureStacksWidth(snapshot.buildCost);

        Stats stats = content.stats;
        OrderedMap<StatCat, OrderedMap<Stat, Seq<StatValue>>> statMap = stats.toMap();
        for(StatCat cat : statMap.keys()){
            OrderedMap<Stat, Seq<StatValue>> map = statMap.get(cat);
            if(map.size == 0) continue;

            for(Stat stat : map.keys()){
                String key = rowKey(cat, stat);
                SnapshotRow row = new SnapshotRow(cat, stat, stat.localized());
                Table rendered = buildRenderedTable(map.get(stat));
                row.kind = classifyRow(stat, rendered);
                row.text = extractRowSignature(rendered);
                row.labelWidth = measureMarkupWidth(row.label);
                if(row.kind != RowKind.TEXT){
                    row.renderedWidth = measureRenderedWidth(rendered);
                    if(mode == SnapshotMode.baseline){
                        row.renderedSnapshot = snapshotElement(rendered);
                    }else{
                        row.renderedSnapshot = snapshotElement(rendered);
                    }
                }
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
        freezeElementTree(table);
        return simplifyRenderedTable(table);
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

    private UiSnapshot snapshotElement(Element element){
        if(element == null) return null;
        if(element instanceof ScrollPane){
            return snapshotElement(((ScrollPane)element).getWidget());
        }
        if(element instanceof Label){
            Label label = (Label)element;
            return new LabelSnapshot(
                label.getText() == null ? "" : label.getText().toString(),
                label.getStyle(),
                new Color(label.color),
                Reflector.getBoolean(label, "wrap"),
                label.getLabelAlign(),
                label.getLineAlign(),
                label.getFontScaleX(),
                label.getFontScaleY()
            );
        }
        if(element instanceof Image){
            Image image = (Image)element;
            Scaling scaling = (Scaling)Reflector.get(image, "scaling");
            return new ImageSnapshot(
                image.getDrawable(),
                new Color(image.color),
                scaling == null ? Scaling.fit : scaling,
                Math.max(image.getWidth(), image.getPrefWidth()),
                Math.max(image.getHeight(), image.getPrefHeight())
            );
        }
        if(element instanceof Stack){
            Stack stack = (Stack)element;
            StackSnapshot snapshot = new StackSnapshot();
            Seq<Element> children = stack.getChildren();
            for(int i = 0; i < children.size; i++){
                UiSnapshot child = snapshotElement(children.get(i));
                if(child != null) snapshot.children.add(child);
            }
            return snapshot.children.isEmpty() ? null : snapshot;
        }
        if(element instanceof Table){
            Table table = (Table)element;
            TableSnapshot snapshot = new TableSnapshot(table.getBackground());
            Seq<Cell> cells = table.getCells();
            for(int i = 0; i < cells.size; i++){
                Cell cell = cells.get(i);
                Element childElement = cell.get();
                if(childElement == null) continue;
                UiSnapshot child = snapshotElement(childElement);
                if(child == null) continue;
                CellSnapshot cellSnapshot = new CellSnapshot(child);
                cellSnapshot.padTop = cellPadding(cell, "computedPadTop", "padTop");
                cellSnapshot.padLeft = cellPadding(cell, "computedPadLeft", "padLeft");
                cellSnapshot.padBottom = cellPadding(cell, "computedPadBottom", "padBottom");
                cellSnapshot.padRight = cellPadding(cell, "computedPadRight", "padRight");
                cellSnapshot.fillX = Reflector.getFloat(cell, "fillX") > 0f;
                cellSnapshot.fillY = Reflector.getFloat(cell, "fillY") > 0f;
                cellSnapshot.expandX = Reflector.getInt(cell, "expandX") > 0;
                cellSnapshot.expandY = Reflector.getInt(cell, "expandY") > 0;
                cellSnapshot.align = Reflector.getInt(cell, "align");
                cellSnapshot.colspan = Math.max(1, Reflector.getInt(cell, "colspan"));
                cellSnapshot.endRow = cell.isEndRow();
                snapshot.cells.add(cellSnapshot);
            }
            return snapshot;
        }
        if(element instanceof Group){
            Seq<Element> children = ((Group)element).getChildren();
            if(children.size == 1){
                return snapshotElement(children.first());
            }
            GroupSnapshot snapshot = new GroupSnapshot();
            for(int i = 0; i < children.size; i++){
                UiSnapshot child = snapshotElement(children.get(i));
                if(child != null) snapshot.children.add(child);
            }
            return snapshot.children.isEmpty() ? null : snapshot;
        }
        String text = normalizeText(extractTreeSignature(element));
        return text == null ? null : new LabelSnapshot(text, null, new Color(Color.white), false, 0, 0, 1f, 1f);
    }

    private float cellPadding(Cell cell, String computedField, String rawField){
        float computed = Reflector.getFloat(cell, computedField);
        if(computed != 0f) return computed;
        return Reflector.getFloat(cell, rawField);
    }

    private RenderedRow materializeRow(SnapshotRow row){
        if(row == null || row.renderedSnapshot == null) return null;
        Element root = materializeElement(row.renderedSnapshot);
        Table table = root instanceof Table ? (Table)root : wrapMaterializedElement(root);
        freezeElementTree(table);
        Seq<LabelState> labels = new Seq<>();
        captureLabelStates(table, labels);
        return new RenderedRow(table, labels);
    }

    private Table wrapMaterializedElement(Element element){
        Table table = new Table();
        table.left().top().defaults().left().top();
        if(element != null){
            table.add(element).left().top();
        }
        return table;
    }

    private Element materializeElement(UiSnapshot snapshot){
        if(snapshot == null) return null;
        if(snapshot instanceof LabelSnapshot){
            LabelSnapshot labelSnapshot = (LabelSnapshot)snapshot;
            Label label = labelSnapshot.style == null ? new Label(labelSnapshot.text) : new Label(labelSnapshot.text, labelSnapshot.style);
            label.setColor(labelSnapshot.color);
            label.setWrap(labelSnapshot.wrap);
            if(labelSnapshot.labelAlign != 0){
                label.setAlignment(labelSnapshot.labelAlign, labelSnapshot.lineAlign == 0 ? labelSnapshot.labelAlign : labelSnapshot.lineAlign);
            }
            if(labelSnapshot.fontScaleX != 1f || labelSnapshot.fontScaleY != 1f){
                label.setFontScale(labelSnapshot.fontScaleX, labelSnapshot.fontScaleY);
            }
            return label;
        }
        if(snapshot instanceof ImageSnapshot){
            ImageSnapshot imageSnapshot = (ImageSnapshot)snapshot;
            Image image = imageSnapshot.drawable == null ? new Image() : new Image(imageSnapshot.drawable);
            image.setColor(imageSnapshot.color);
            image.setScaling(imageSnapshot.scaling);
            if(imageSnapshot.width > 0f || imageSnapshot.height > 0f){
                image.setSize(
                    imageSnapshot.width > 0f ? imageSnapshot.width : image.getPrefWidth(),
                    imageSnapshot.height > 0f ? imageSnapshot.height : image.getPrefHeight()
                );
            }
            return image;
        }
        if(snapshot instanceof StackSnapshot){
            Stack stack = new Stack();
            Seq<UiSnapshot> children = ((StackSnapshot)snapshot).children;
            for(int i = 0; i < children.size; i++){
                Element child = materializeElement(children.get(i));
                if(child != null) stack.addChild(child);
            }
            return stack;
        }
        if(snapshot instanceof GroupSnapshot){
            Table table = new Table();
            table.left().top().defaults().left().top();
            Seq<UiSnapshot> children = ((GroupSnapshot)snapshot).children;
            for(int i = 0; i < children.size; i++){
                Element child = materializeElement(children.get(i));
                if(child == null) continue;
                table.add(child).left().top();
                if(i < children.size - 1) table.row();
            }
            return table;
        }
        if(snapshot instanceof TableSnapshot){
            TableSnapshot tableSnapshot = (TableSnapshot)snapshot;
            Table table = new Table();
            table.left().top().defaults().left().top();
            if(tableSnapshot.background != null){
                table.background(tableSnapshot.background);
            }
            for(int i = 0; i < tableSnapshot.cells.size; i++){
                CellSnapshot cellSnapshot = tableSnapshot.cells.get(i);
                Element child = materializeElement(cellSnapshot.child);
                if(child == null) continue;
                Cell<Element> cell = table.add(child).left().top();
                if(cellSnapshot.padTop != 0f || cellSnapshot.padLeft != 0f || cellSnapshot.padBottom != 0f || cellSnapshot.padRight != 0f){
                    cell.pad(cellSnapshot.padTop, cellSnapshot.padLeft, cellSnapshot.padBottom, cellSnapshot.padRight);
                }
                if(cellSnapshot.fillX || cellSnapshot.fillY){
                    cell.fill(cellSnapshot.fillX, cellSnapshot.fillY);
                }
                if(cellSnapshot.expandX || cellSnapshot.expandY){
                    cell.expand(cellSnapshot.expandX ? 1 : 0, cellSnapshot.expandY ? 1 : 0);
                }
                if(cellSnapshot.align != 0){
                    cell.align(cellSnapshot.align);
                }
                if(cellSnapshot.colspan > 1){
                    cell.colspan(cellSnapshot.colspan);
                }
                if(cellSnapshot.endRow){
                    table.row();
                }
            }
            return table;
        }
        return null;
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

    private void captureLabelStates(Element element, Seq<LabelState> out, String groupKey, boolean insideStack){
        if(element == null || out == null) return;
        boolean nextInsideStack = insideStack || element instanceof Stack;
        if(element instanceof Label){
            Label label = (Label)element;
            String raw = label.getText() == null ? "" : label.getText().toString();
            String visible = Strings.stripColors(raw);
            String normalized = normalizeText(visible);
            out.add(new LabelState(label, raw, visible, normalized, buildLabelMatchKey(normalized), groupKey, nextInsideStack, new Color(label.color)));
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

    private void restoreLabelStates(Seq<LabelState> labels){
        if(labels == null) return;
        for(LabelState state : labels){
            if(state == null || state.label == null) continue;
            state.label.setText(state.rawText);
            state.label.setColor(state.originalColor);
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

    private void highlightMatchedLabels(LabelState before, LabelState after, DiffColors colors){
        if(before == null || after == null) return;
        if(before.normalizedText == null ? after.normalizedText == null : before.normalizedText.equals(after.normalizedText)) return;

        String beforeMarkup = buildNumericHighlightMarkup(before.visibleText, after.visibleText, colors.modifiedOldTag);
        String afterMarkup = buildNumericHighlightMarkup(after.visibleText, before.visibleText, colors.modifiedNewTag);

        if(beforeMarkup == null || afterMarkup == null){
            beforeMarkup = buildSegmentHighlightMarkup(before.visibleText, after.visibleText, colors.modifiedOldTag);
            afterMarkup = buildSegmentHighlightMarkup(after.visibleText, before.visibleText, colors.modifiedNewTag);
        }

        if(beforeMarkup == null) beforeMarkup = colors.modifiedOldTag + escape(before.visibleText) + "[]";
        if(afterMarkup == null) afterMarkup = colors.modifiedNewTag + escape(after.visibleText) + "[]";

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

    private void prepareRenderedDiff(Seq<LabelState> beforeLabels, Seq<LabelState> afterLabels, DiffColors colors){
        restoreLabelStates(beforeLabels);
        restoreLabelStates(afterLabels);
        if(beforeLabels == null || afterLabels == null) return;

        OrderedSet<String> changedBeforeGroups = new OrderedSet<>();
        OrderedSet<String> changedAfterGroups = new OrderedSet<>();
        int beforeSize = beforeLabels.size;
        int afterSize = afterLabels.size;
        if(tryPrepareRenderedDiffSequential(beforeLabels, afterLabels, changedBeforeGroups, changedAfterGroups, colors)){
            highlightChangedStackAmounts(beforeLabels, changedBeforeGroups, colors);
            highlightChangedStackAmounts(afterLabels, changedAfterGroups, colors);
            return;
        }
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
                highlightMatchedLabels(before, after, colors);
                if(changed){
                    changedBeforeGroups.add(before.groupKey);
                    changedAfterGroups.add(after.groupKey);
                }
                i++;
                j++;
            }else if(i < beforeSize && (j >= afterSize || lcs[i + 1][j] >= lcs[i][j + 1])){
                LabelState before = beforeLabels.get(i);
                highlightWholeLabel(before, colors.removedTag);
                changedBeforeGroups.add(before.groupKey);
                i++;
            }else if(j < afterSize){
                LabelState after = afterLabels.get(j);
                highlightWholeLabel(after, colors.addedTag);
                changedAfterGroups.add(after.groupKey);
                j++;
            }
        }

        highlightChangedStackAmounts(beforeLabels, changedBeforeGroups, colors);
        highlightChangedStackAmounts(afterLabels, changedAfterGroups, colors);
    }

    private boolean tryPrepareRenderedDiffSequential(Seq<LabelState> beforeLabels, Seq<LabelState> afterLabels, OrderedSet<String> changedBeforeGroups, OrderedSet<String> changedAfterGroups, DiffColors colors){
        if(beforeLabels.size != afterLabels.size) return false;
        for(int i = 0; i < beforeLabels.size; i++){
            if(!labelsMatch(beforeLabels.get(i), afterLabels.get(i))){
                return false;
            }
        }
        for(int i = 0; i < beforeLabels.size; i++){
            LabelState before = beforeLabels.get(i);
            LabelState after = afterLabels.get(i);
            boolean changed = before.normalizedText == null ? after.normalizedText != null : !before.normalizedText.equals(after.normalizedText);
            highlightMatchedLabels(before, after, colors);
            if(changed){
                changedBeforeGroups.add(before.groupKey);
                changedAfterGroups.add(after.groupKey);
            }
        }
        return true;
    }

    private void highlightChangedStackAmounts(Seq<LabelState> labels, OrderedSet<String> changedGroups, DiffColors colors){
        if(labels == null || changedGroups == null) return;
        for(LabelState state : labels){
            if(state == null || !state.insideStack) continue;
            if(changedGroups.contains(state.groupKey)){
                String colorTag = detectGroupHighlightColor(labels, state.groupKey, colors);
                if(colorTag != null){
                    highlightWholeLabel(state, colorTag);
                }
            }
        }
    }

    private void highlightChangedGroups(Seq<LabelState> labels, DiffColors colors){
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
                String colorTag = detectGroupHighlightColor(labels, state.groupKey, colors);
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

    private String detectGroupHighlightColor(Seq<LabelState> labels, String groupKey, DiffColors colors){
        if(labels == null || groupKey == null) return null;
        boolean hasRemoved = false;
        boolean hasAdded = false;
        boolean hasModifiedOld = false;
        boolean hasModifiedNew = false;
        for(LabelState state : labels){
            if(state == null || !groupKey.equals(state.groupKey) || state.label == null) continue;
            String current = state.label.getText() == null ? "" : state.label.getText().toString();
            if(current.contains(colors.removedTag)){
                hasRemoved = true;
            }else if(current.contains(colors.addedTag)){
                hasAdded = true;
            }else if(current.contains(colors.modifiedOldTag)){
                hasModifiedOld = true;
            }else if(current.contains(colors.modifiedNewTag)){
                hasModifiedNew = true;
            }
        }
        if(hasRemoved) return colors.removedTag;
        if(hasAdded) return colors.addedTag;
        if(hasModifiedOld) return colors.modifiedOldTag;
        if(hasModifiedNew) return colors.modifiedNewTag;
        return null;
    }

    private void tintElementTree(Element element, Color tint){
        if(element == null || tint == null) return;
        element.setColor(tint);
        if(element instanceof ScrollPane){
            tintElementTree(((ScrollPane)element).getWidget(), tint);
        }
        if(element instanceof Group){
            Seq<Element> children = ((Group)element).getChildren();
            for(int i = 0; i < children.size; i++){
                tintElementTree(children.get(i), tint);
            }
        }
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

        if(!sameStacks(before.buildCost, after.buildCost)){
            diff.rows.put(buildCostRowKey, InlineRow.forBuildCost(Stat.buildCost.localized(), copyStacks(before.buildCost), before.buildCostWidth, copyStacks(after.buildCost), after.buildCostWidth));
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
                    diff.rows.put(key, InlineRow.forNative(afterRow.label, afterRow.kind, null, afterRow));
                }
                continue;
            }

            if(afterRow == null){
                if(beforeRow.kind == RowKind.TEXT){
                    String markup = buildDiff(beforeRow.text, null);
                    if(markup != null) diff.rows.put(key, InlineRow.forText(beforeRow.label, markup));
                }else{
                    diff.rows.put(key, InlineRow.forNative(beforeRow.label, beforeRow.kind, beforeRow, null));
                }
                continue;
            }

            if(afterRow.kind != beforeRow.kind){
                diff.rows.put(key, InlineRow.forNative(afterRow.label, afterRow.kind, beforeRow, afterRow));
                continue;
            }

            switch(afterRow.kind){
                case TEXT:
                    String markup = buildDiff(beforeRow.text, afterRow.text);
                    if(markup != null) diff.rows.put(key, InlineRow.forText(afterRow.label, markup));
                    break;
                case STACK_LIST:
                    if(!sameNumbers(beforeRow, afterRow)) diff.rows.put(key, InlineRow.forNative(afterRow.label, afterRow.kind, beforeRow, afterRow));
                    break;
                case AMMO_PANEL:
                    if(!sameNumbers(beforeRow, afterRow)) diff.rows.put(key, InlineRow.forNative(afterRow.label, afterRow.kind, beforeRow, afterRow));
                    break;
                case WEAPON_PANEL:
                case ABILITY_PANEL:
                case NATIVE_PANEL:
                    if(!sameTextAndNumbers(beforeRow, afterRow)) diff.rows.put(key, InlineRow.forNative(afterRow.label, afterRow.kind, beforeRow, afterRow));
                    break;
                default:
                    break;
            }
        }

        return diff.isEmpty() ? null : diff;
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
            return "[]\n" + arrowColor + "-> []";
        }
        return "[] " + arrowColor + "-> []";
    }

    private float estimateDialogWidth(UnlockableContent content, ContentSnapshot before, ContentSnapshot after, ContentDiff diff, String titleMarkup, String visibleDescription){
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
            if(buildCostRowKey.equals(key)) continue;
            SnapshotRow currentRow = after == null ? null : after.rows.get(key);
            SnapshotRow beforeRow = before == null ? null : before.rows.get(key);
            InlineRow row = diff == null ? null : diff.rows.get(key);
            SnapshotRow source = currentRow != null ? currentRow : beforeRow;
            if(source == null) continue;
            width = Math.max(width, estimateRowWidth(source, currentRow, row));
        }

        if(content.details != null){
            width = Math.max(width, Math.min(detailsWidth, measureMarkupWidth(content.details) + 24f));
        }

        return Math.min(width, maxMeasuredContentWidth);
    }

    private float estimateRowWidth(SnapshotRow source, SnapshotRow currentRow, InlineRow row){
        float labelWidth = source.labelWidth;
        float width = labelWidth + 48f;
        float minValueWidth = minimumValueWidth(source.kind);
        if(row == null){
            if(currentRow == null) return width;
            if(currentRow.renderedSnapshot != null){
                float valueWidth = Math.max(currentRow.renderedWidth + 12f, minValueWidth);
                return combineRowWidth(source.kind, labelWidth, width, valueWidth);
            }
            return Math.max(width + Math.min(maxTextDiffMeasureWidth, measureMarkupWidth(currentRow.text)) + 12f, width + minValueWidth);
        }

        if(row.buildCostBefore != null || row.buildCostAfter != null){
            return width + Math.max(row.buildCostBeforeWidth, row.buildCostAfterWidth) + 56f;
        }
        if(row.nativeWidgetDiff){
            float valueWidth = Math.max(Math.max(row.beforeRow == null ? 0f : row.beforeRow.renderedWidth, row.afterRow == null ? 0f : row.afterRow.renderedWidth) + 24f, minValueWidth);
            return combineRowWidth(source.kind, labelWidth, width, valueWidth);
        }
        return Math.max(width + Math.min(maxTextDiffMeasureWidth, measureMarkupWidth(Strings.stripColors(row.markup))) + 12f, width + minValueWidth);
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
        float width = 0f;
        for(ItemStack stack : stacks){
            if(stack == null || stack.item == null) continue;
            Element stackView = StatValues.stack(stack);
            if(stackView instanceof Table){
                ((Table)stackView).pack();
            }
            width += Math.max(stackView.getPrefWidth(), stackView.getWidth()) + 5f;
        }
        return width;
    }

    private float measureMarkupWidth(String text){
        if(text == null) return 0f;
        if(markupWidthCache.containsKey(text)) return markupWidthCache.get(text, 0f);
        String cleaned = Strings.stripColors(text);
        float max = 0f;
        Label label = widthMeasureLabel;
        if(label == null){
            widthMeasureLabel = label = new Label("");
        }
        for(String line : cleaned.split("\\n", -1)){
            label.setText(line == null ? "" : line);
            max = Math.max(max, label.getPrefWidth());
        }
        markupWidthCache.put(text, max);
        return max;
    }

    private float minimumValueWidth(RowKind kind){
        if(kind == RowKind.STACK_LIST) return 500f;
        if(kind == RowKind.AMMO_PANEL || kind == RowKind.WEAPON_PANEL || kind == RowKind.ABILITY_PANEL || kind == RowKind.NATIVE_PANEL) return 360f;
        return 160f;
    }

    private float rowValueWidth(SnapshotRow row, float contentWidth){
        RowKind kind = row.kind;
        if(isBlockPanelKind(kind)){
            return Math.max(minimumValueWidth(kind), contentWidth - 12f);
        }
        return Math.max(minimumValueWidth(kind), contentWidth - row.labelWidth - 28f);
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

    private void showPatched(UnlockableContent content){
        Vars.ui.content.cont.clear();

        Table table = new Table();
        table.margin(10f);
        if(!baselineCaptured && !baselineWindowClosed && isPatchViewerEnabled()){
            captureBaselineAtStartup();
        }
        content.checkStats();
        ContentSnapshot before = baselineSnapshots.get(content);
        ContentSnapshot after = getAfterSnapshot(content);
        if(after == null && before == null){
            after = snapshot(content, SnapshotMode.current);
        }
        ContentDiff diff = getContentDiff(content, before, after);
        int logicId = content.getLogicId();
        String titleMarkup = diff == null || diff.title == null
            ? "[accent]" + content.localizedName + "\n[gray]" + content.name + (logicId != -1 ? " <#" + logicId + ">" : "")
            : "[accent]" + diff.title + "\n[gray]" + content.name + (logicId != -1 ? " <#" + logicId + ">" : "");
        String visibleDescription = diff == null || diff.description == null ? content.displayDescription() : diff.description;
        float contentWidth = estimateDialogWidth(content, before, after, diff, titleMarkup, visibleDescription);
        float titleWidth = Math.max(260f, contentWidth - Vars.iconXLarge - 16f);
        float descriptionWidth = contentWidth;
        table.table(title1 -> {
            title1.image(content.uiIcon).size(Vars.iconXLarge).scaling(Scaling.fit).get().clicked(() -> Core.app.setClipboardText(content.emoji()));
            Cell<Label> titleCell = title1.add(new Label(titleMarkup)).padLeft(5f).width(titleWidth);
            titleCell.growX().left();
            titleCell.get().setWrap(true);
        });
        table.row();

        if(Vars.state.isGame() && diff != null){
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
            if(buildCostRowKey.equals(key)) continue;
            SnapshotRow currentRow = after == null ? null : after.rows.get(key);
            SnapshotRow beforeRow = before == null ? null : before.rows.get(key);
            InlineRow row = diff == null ? null : diff.rows.get(key);
            SnapshotRow source = currentRow != null ? currentRow : beforeRow;
            if(source == null) continue;

            boolean showCategory = false;
            if(displayedCategories.add(source.cat.name)) showCategory = content.stats.useCategories;
            if(showCategory){
                table.add("@category." + source.cat.name).color(Pal.accent).fillX();
                table.row();
            }

            table.table(inset -> {
                inset.left().top().defaults().left().top();
                boolean blockRow = isBlockPanelKind(source.kind);
                inset.add("[lightgray]" + source.label + ":[] ").top().left();
                if(blockRow) inset.row();
                float valueWidth = rowValueWidth(source, contentWidth);
                if(row == null && currentRow != null){
                    renderSnapshotRow(inset, currentRow, valueWidth);
                }else if(row == null && beforeRow != null){
                    renderSnapshotRow(inset, beforeRow, valueWidth);
                }else if(row != null && (row.buildCostBefore != null || row.buildCostAfter != null)){
                    renderBuildCostDiff(inset, row.buildCostBefore, row.buildCostAfter);
                }else if(row != null && row.nativeWidgetDiff){
                    renderNativeDiff(inset, row.beforeRow, row.afterRow, row.kind, valueWidth);
                }else if(row != null){
                    Label diffLabel = new Label(row.markup);
                    diffLabel.setWrap(true);
                    Cell<Label> cell = inset.add(diffLabel);
                    cell.growX().top().fillX().width(valueWidth);
                }
            }).left().top().fillX().width(contentWidth).padLeft(10f);
            table.row();
        }

        if(content.details != null){
            table.add("[gray]" + content.details).pad(6f).padTop(20f).width(Math.min(detailsWidth, contentWidth)).wrap().fillX();
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
        if(row.renderedSnapshot != null){
            RenderedRow rendered = materializeRow(row);
            if(rendered == null || rendered.table == null) return;
            if(isCenteredPanelKind(row.kind)){
                rendered.table.pack();
                renderCenteredRenderedRow(table, rendered.table, textRowWidth);
            }else if(isBlockPanelKind(row.kind)){
                rendered.table.invalidateHierarchy();
                table.add(rendered.table).left().top().fillX().growX().width(textRowWidth);
            }else{
                rendered.table.pack();
                table.add(rendered.table).left().top().fillX().growX().width(textRowWidth);
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
        table.add(labelMarkup).left().padBottom(2f).row();
        table.table(Styles.grayPanel, panel -> renderRenderedValue(panel, rendered, kind)).left().top().fillX().width(contentWidth).growX().padBottom(bottomPad);
        table.row();
    }

    private void renderNativeDiff(Table table, SnapshotRow beforeRow, SnapshotRow afterRow, RowKind kind, float contentWidth){
        Table root = new Table();
        root.left().top().defaults().left().top();
        String beforeLabel = beforeLabelText();
        String afterLabel = afterLabelText();
        DiffColors colors = currentDiffColors();
        RenderedRow before = materializeRow(beforeRow);
        RenderedRow after = materializeRow(afterRow);
        if(before != null && after != null){
            prepareRenderedDiff(before.labels, after.labels, colors);
            if(kind == RowKind.STACK_LIST){
                highlightChangedGroups(before.labels, colors);
                highlightChangedGroups(after.labels, colors);
            }
            renderDiffPanel(root, colors.modifiedOldTag + beforeLabel + "[]", before.table, kind, contentWidth, 6f);
            root.add(arrowColor + "->[]").left().padTop(2f).padBottom(6f).row();
            renderDiffPanel(root, colors.modifiedNewTag + afterLabel + "[]", after.table, kind, contentWidth, 0f);
        }else if(before != null){
            restoreLabelStates(before.labels);
            highlightAllLabels(before.labels, colors.removedTag);
            if(kind == RowKind.STACK_LIST){
                highlightChangedGroups(before.labels, colors);
            }
            renderDiffPanel(root, colors.removedTag + beforeLabel + "[]", before.table, kind, contentWidth, 0f);
        }else if(after != null){
            restoreLabelStates(after.labels);
            highlightAllLabels(after.labels, colors.addedTag);
            if(kind == RowKind.STACK_LIST){
                highlightChangedGroups(after.labels, colors);
            }
            renderDiffPanel(root, colors.addedTag + afterLabel + "[]", after.table, kind, contentWidth, 0f);
        }
        table.add(root).left().top().fillX().growX();
    }

    private void renderBuildCostDiff(Table table, Seq<ItemStack> before, Seq<ItemStack> after){
        Table line = new Table();
        line.left().top().defaults().left().top();
        String beforeLabel = beforeLabelText();
        String afterLabel = afterLabelText();
        DiffColors colors = currentDiffColors();
        if(before != null && after != null){
            line.add(colors.modifiedOldTag + beforeLabel + "[]").padRight(6f).top();
            for(ItemStack stack : before){
                if(stack == null || stack.item == null) continue;
                Element stackView = StatValues.stack(stack);
                tintElementTree(stackView, colors.modifiedOldColor);
                line.add(stackView).padRight(5f);
            }
            line.row();
            line.add(arrowColor + "->[]").padRight(5f).top().left();
            line.row();
            line.add(colors.modifiedNewTag + afterLabel + "[]").padRight(6f).top();
            for(ItemStack stack : after){
                if(stack == null || stack.item == null) continue;
                Element stackView = StatValues.stack(stack);
                tintElementTree(stackView, colors.modifiedNewColor);
                line.add(stackView).padRight(5f);
            }
        }else if(before != null){
            line.add(colors.removedTag + beforeLabel + "[]").padRight(6f).top();
            for(ItemStack stack : before){
                if(stack == null || stack.item == null) continue;
                Element stackView = StatValues.stack(stack);
                tintElementTree(stackView, colors.removedColor);
                line.add(stackView).padRight(5f);
            }
        }else if(after != null){
            line.add(colors.addedTag + afterLabel + "[]").padRight(6f).top();
            for(ItemStack stack : after){
                if(stack == null || stack.item == null) continue;
                Element stackView = StatValues.stack(stack);
                tintElementTree(stackView, colors.addedColor);
                line.add(stackView).padRight(5f);
            }
        }
        table.add(line).growX().top().left();
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

        table.table(row -> {
            row.left();
            CheckBox box = new CheckBox(bundle("patchviewer.settings.enabled", "Enable PatchViewer"));
            box.setChecked(isPatchViewerEnabled());
            box.changed(() -> handleEnabledSettingChanged(box.isChecked()));
            row.add(box).left();
        }).growX().fillX();
        table.row();

        table.add("[lightgray]" + bundle("patchviewer.settings.color-hint", "Colors support named values like gold and hex values like #ffd700 or ffd700.") + "[]").left().wrap().growX();
        table.row();

        addColorSettingRow(table, "patchviewer.settings.removed", "Removed entry color", keyRemovedColor, defaultRemovedColor);
        addColorSettingRow(table, "patchviewer.settings.modified-old", "Modified entry color (before)", keyModifiedOldColor, defaultModifiedOldColor);
        addColorSettingRow(table, "patchviewer.settings.modified-new", "Modified entry color (after)", keyModifiedNewColor, defaultModifiedNewColor);
        addColorSettingRow(table, "patchviewer.settings.added", "Added entry color", keyAddedColor, defaultAddedColor);
    }

    /** Populates a {@link mindustry.ui.dialogs.SettingsMenuDialog.SettingsTable} with this mod's settings. */
    public void bekBuildSettings(SettingsMenuDialog.SettingsTable table){
        buildSettings(table);
    }

    private void addColorSettingRow(SettingsMenuDialog.SettingsTable table, String titleKey, String titleFallback, String key, String defaultValue){
        table.table(row -> {
            row.left().defaults().left();

            row.add(bundle(titleKey, titleFallback)).width(210f).wrap().padRight(8f);

            TextField field = new TextField(readColorSetting(key, defaultValue));
            field.setMessageText(defaultValue);
            row.add(field).minWidth(180f).growX().maxWidth(280f);

            Image preview = new Image(Tex.whiteui);
            preview.setColor(readColorValue(key, defaultValue));
            row.add(preview).size(26f).padLeft(8f).padRight(8f);

            Label sample = new Label("");
            sample.setColor(Color.white);
            row.add(sample).minWidth(64f).left();

            Runnable refresh = () -> {
                String normalized = normalizeColorSpec(field.getText());
                if(normalized != null){
                    Core.settings.put(key, normalized);
                    preview.setColor(parseColorSpec(normalized, readColorValue(key, defaultValue)));
                    sample.setText(colorTag(normalized) + bundle("patchviewer.settings.preview", "Preview") + "[]");
                }else{
                    preview.setColor(readColorValue(key, defaultValue));
                    sample.setText("[lightgray]" + bundle("patchviewer.settings.invalid", "Invalid") + "[]");
                }
            };

            field.changed(refresh);
            refresh.run();

            row.button(bundle("patchviewer.settings.reset", "Reset"), Styles.flatt, () -> {
                field.setText(defaultValue);
                Core.settings.put(key, readColorSetting(key, defaultValue));
                refresh.run();
            }).height(42f).padLeft(8f);
        }).growX().fillX();
        table.row();
    }

    private void handleEnabledSettingChanged(boolean enabled){
        Core.settings.put(keyEnabled, enabled);
        if(enabled){
            if(!baselineCaptured && !baselineWindowClosed){
                captureBaselineAtStartup();
            }
        }else{
            invalidateAfterCaches();
        }
    }

    private DiffColors currentDiffColors(){
        return new DiffColors(
            removedColorTag(),
            modifiedOldColorTag(),
            modifiedNewColorTag(),
            addedColorTag(),
            removedColorValue(),
            modifiedOldColorValue(),
            modifiedNewColorValue(),
            addedColorValue()
        );
    }

    private String bundle(String key, String fallback){
        return Core.bundle == null ? fallback : Core.bundle.get(key, fallback);
    }

    private String beforeLabelText(){
        return bundle("patchviewer.label.before", "Before");
    }

    private String afterLabelText(){
        return bundle("patchviewer.label.after", "After");
    }

    private String escape(String text){
        return text == null ? "null" : text.replace("[", "[[");
    }

    private boolean isPatchViewerEnabled(){
        return Core.settings.getBool(keyEnabled, true);
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

    private Color removedColorValue(){
        return readColorValue(keyRemovedColor, defaultRemovedColor);
    }

    private Color modifiedOldColorValue(){
        return readColorValue(keyModifiedOldColor, defaultModifiedOldColor);
    }

    private Color modifiedNewColorValue(){
        return readColorValue(keyModifiedNewColor, defaultModifiedNewColor);
    }

    private Color addedColorValue(){
        return readColorValue(keyAddedColor, defaultAddedColor);
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
            if(object == null || fieldName == null) return null;
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

        static float getFloat(Object object, String fieldName){
            Object value = get(object, fieldName);
            return value instanceof Number ? ((Number)value).floatValue() : 0f;
        }

        static int getInt(Object object, String fieldName){
            Object value = get(object, fieldName);
            return value instanceof Number ? ((Number)value).intValue() : 0;
        }

        static boolean getBoolean(Object object, String fieldName){
            Object value = get(object, fieldName);
            return value instanceof Boolean && (Boolean)value;
        }
    }

    private static class ContentSnapshot{
        String title;
        String description;
        String details;
        Seq<ItemStack> buildCost;
        float buildCostWidth;
        final OrderedSet<String> order = new OrderedSet<>();
        final ObjectMap<String, SnapshotRow> rows = new ObjectMap<>();
    }

    private static class SnapshotRow{
        final StatCat cat;
        final Stat stat;
        final String label;
        RowKind kind;
        String text;
        Seq<String> numeric = new Seq<>();
        float labelWidth;
        float renderedWidth;
        UiSnapshot renderedSnapshot;

        SnapshotRow(StatCat cat, Stat stat, String label){
            this.cat = cat;
            this.stat = stat;
            this.label = label;
        }
    }

    private static class ContentDiff{
        String title;
        String description;
        final ObjectMap<String, InlineRow> rows = new ObjectMap<>();

        boolean isEmpty(){
            return title == null && description == null && rows.isEmpty();
        }
    }

    private static class InlineRow{
        final String label;
        final String markup;
        final Seq<ItemStack> buildCostBefore;
        final float buildCostBeforeWidth;
        final Seq<ItemStack> buildCostAfter;
        final float buildCostAfterWidth;
        final boolean nativeWidgetDiff;
        final RowKind kind;
        final SnapshotRow beforeRow;
        final SnapshotRow afterRow;

        InlineRow(String label, String markup){
            this(label, markup, null, 0f, null, 0f, false, RowKind.TEXT, null, null);
        }

        InlineRow(String label, String markup, Seq<ItemStack> buildCostBefore, float buildCostBeforeWidth, Seq<ItemStack> buildCostAfter, float buildCostAfterWidth, boolean nativeWidgetDiff, RowKind kind, SnapshotRow beforeRow, SnapshotRow afterRow){
            this.label = label;
            this.markup = markup;
            this.buildCostBefore = buildCostBefore;
            this.buildCostBeforeWidth = buildCostBeforeWidth;
            this.buildCostAfter = buildCostAfter;
            this.buildCostAfterWidth = buildCostAfterWidth;
            this.nativeWidgetDiff = nativeWidgetDiff;
            this.kind = kind;
            this.beforeRow = beforeRow;
            this.afterRow = afterRow;
        }

        static InlineRow forText(String label, String markup){
            return new InlineRow(label, markup);
        }

        static InlineRow forBuildCost(String label, Seq<ItemStack> before, float beforeWidth, Seq<ItemStack> after, float afterWidth){
            return new InlineRow(label, null, before, beforeWidth, after, afterWidth, false, RowKind.BUILD_COST, null, null);
        }

        static InlineRow forNative(String label, RowKind kind, SnapshotRow beforeRow, SnapshotRow afterRow){
            return new InlineRow(label, null, null, 0f, null, 0f, true, kind, beforeRow, afterRow);
        }
    }

    private static class RenderedRow{
        final Table table;
        final Seq<LabelState> labels;

        RenderedRow(Table table, Seq<LabelState> labels){
            this.table = table;
            this.labels = labels;
        }
    }

    private interface UiSnapshot{
    }

    private static class TableSnapshot implements UiSnapshot{
        final Drawable background;
        final Seq<CellSnapshot> cells = new Seq<>();

        TableSnapshot(Drawable background){
            this.background = background;
        }
    }

    private static class CellSnapshot{
        final UiSnapshot child;
        float padTop;
        float padLeft;
        float padBottom;
        float padRight;
        boolean fillX;
        boolean fillY;
        boolean expandX;
        boolean expandY;
        int align;
        int colspan = 1;
        boolean endRow;

        CellSnapshot(UiSnapshot child){
            this.child = child;
        }
    }

    private static class LabelSnapshot implements UiSnapshot{
        final String text;
        final Label.LabelStyle style;
        final Color color;
        final boolean wrap;
        final int labelAlign;
        final int lineAlign;
        final float fontScaleX;
        final float fontScaleY;

        LabelSnapshot(String text, Label.LabelStyle style, Color color, boolean wrap, int labelAlign, int lineAlign, float fontScaleX, float fontScaleY){
            this.text = text;
            this.style = style;
            this.color = color;
            this.wrap = wrap;
            this.labelAlign = labelAlign;
            this.lineAlign = lineAlign;
            this.fontScaleX = fontScaleX;
            this.fontScaleY = fontScaleY;
        }
    }

    private static class ImageSnapshot implements UiSnapshot{
        final Drawable drawable;
        final Color color;
        final Scaling scaling;
        final float width;
        final float height;

        ImageSnapshot(Drawable drawable, Color color, Scaling scaling, float width, float height){
            this.drawable = drawable;
            this.color = color;
            this.scaling = scaling;
            this.width = width;
            this.height = height;
        }
    }

    private static class StackSnapshot implements UiSnapshot{
        final Seq<UiSnapshot> children = new Seq<>();
    }

    private static class GroupSnapshot implements UiSnapshot{
        final Seq<UiSnapshot> children = new Seq<>();
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

        LabelState(Label label, String rawText, String visibleText, String normalizedText, String matchKey, String groupKey, boolean insideStack, Color originalColor){
            this.label = label;
            this.rawText = rawText;
            this.visibleText = visibleText == null ? "" : visibleText;
            this.normalizedText = normalizedText;
            this.matchKey = matchKey == null ? "" : matchKey;
            this.groupKey = groupKey == null ? "" : groupKey;
            this.insideStack = insideStack;
            this.originalColor = originalColor;
        }
    }

    private static class DiffColors{
        final String removedTag;
        final String modifiedOldTag;
        final String modifiedNewTag;
        final String addedTag;
        final Color removedColor;
        final Color modifiedOldColor;
        final Color modifiedNewColor;
        final Color addedColor;

        DiffColors(String removedTag, String modifiedOldTag, String modifiedNewTag, String addedTag, Color removedColor, Color modifiedOldColor, Color modifiedNewColor, Color addedColor){
            this.removedTag = removedTag;
            this.modifiedOldTag = modifiedOldTag;
            this.modifiedNewTag = modifiedNewTag;
            this.addedTag = addedTag;
            this.removedColor = removedColor;
            this.modifiedOldColor = modifiedOldColor;
            this.modifiedNewColor = modifiedNewColor;
            this.addedColor = addedColor;
        }
    }

    private enum SnapshotMode{
        baseline,
        current
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
