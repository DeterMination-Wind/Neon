package bektools.ui;

import arc.graphics.Color;
import arc.scene.Element;
import arc.scene.Group;
import arc.scene.style.Drawable;
import arc.scene.ui.Button;
import arc.scene.ui.CheckBox;
import arc.scene.ui.Image;
import arc.scene.ui.ImageButton;
import arc.scene.ui.Label;
import arc.scene.ui.Slider;
import arc.scene.ui.TextArea;
import arc.scene.ui.TextButton;
import arc.scene.ui.TextField;
import arc.scene.ui.layout.Cell;
import arc.scene.ui.layout.Collapser;
import arc.scene.ui.layout.Scl;
import arc.scene.ui.layout.Stack;
import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import arc.util.Align;
import arc.struct.SnapshotSeq;
import mindustry.gen.Tex;
import mindustry.graphics.Pal;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.SettingsMenuDialog;

import java.lang.reflect.Field;

/**
 * Post-build visual and layout normalizer for sub-mod settings rendered inside {@link SubmodSettingsHostTable}.
 *
 * <p>Responsibilities across all 29 bundled sub-mods:
 * <ul>
 *   <li>Expands every top-level setting row (including {@link Stack} sliders, bare {@link CheckBox}es,
 *       bare {@link TextButton}s, and fixed-width {@code .width(prefWidth())} cards) to the full pane width
 *       with zero horizontal padding drift.</li>
 *   <li>Upgrades uncarded control rows (e.g. PatchViewer, LogicSugar, WhoUsesThisBuilding, BetterHotKey,
 *       ModUpdater, BetterMiniMap, AutoPruner) into unified {@link VscodeSettingsStyle#hudCardBackground()} cards.</li>
 *   <li>Aligns horizontal single-row control cards so titles expand cleanly on the left while right-side
 *       text fields, color swatches, and buttons line up flush along the right edge.</li>
 *   <li>Traverses {@link Stack}, {@link Collapser}, and dynamically rebuilt wrapper tables so nested
 *       sliders, collapsible sections, and Pro-mode panels all inherit the flat futuristic black-gold theme.</li>
 * </ul>
 */
public final class SettingsVisualNormalizer{
    private static final int maxReskinDepth = 8;
    private static final float cellUnset = Float.NEGATIVE_INFINITY;

    private static final Field cellExpandXField = resolveCellField("expandX");
    private static final Field cellRawMaxWidthField = resolveCellField("maxWidth");
    private static final Field cellRawMinWidthField = resolveCellField("minWidth");
    private static final Field cellPadTopField = resolveCellField("padTop");

    private SettingsVisualNormalizer(){
    }

    private static Field resolveCellField(String name){
        try{
            Field f = Cell.class.getDeclaredField(name);
            f.setAccessible(true);
            return f;
        }catch(Throwable ignored){
            return null;
        }
    }

    public static void normalize(Table root, float contentWidth){
        if(root == null) return;
        float clampedWidth = Math.max(240f, contentWidth);
        normalizeSettingsTableRows(root, clampedWidth);
        reskinSubtree(root, clampedWidth, 0, true);
    }

    private static Seq<IntRange> splitRows(Seq<Cell> cells){
        Seq<IntRange> rows = new Seq<>();
        if(cells == null || cells.isEmpty()) return rows;
        int rowStart = 0;
        for(int i = 0; i < cells.size; i++){
            Cell cell = cells.get(i);
            boolean endOfRow = (cell != null && cell.isEndRow()) || i == cells.size - 1;
            if(endOfRow){
                rows.add(new IntRange(rowStart, i));
                rowStart = i + 1;
            }
        }
        return rows;
    }

    /** Normalizes top-level rows of a {@link SettingsMenuDialog.SettingsTable} (or structural wrapper table). */
    private static void normalizeSettingsTableRows(Table table, float contentWidth){
        Seq<Cell> cells = table.getCells();
        if(cells == null || cells.isEmpty()) return;

        Seq<IntRange> rows = splitRows(cells);
        for(int r = 0; r < rows.size; r++){
            IntRange range = rows.get(r);
            int rowLen = range.end - range.start + 1;
            if(rowLen != 1){
                normalizeMultiCellRow(cells, range.start, range.end, contentWidth);
                continue;
            }

            Cell cell = cells.get(range.start);
            if(cell == null) continue;
            Element actor = cell.get();
            if(actor == null) continue;

            // Lookahead for paired Header + Divider rows (e.g. RadialBuildMenu.HeaderSetting, Tripwire.section)
            Cell nextSingleCell = null;
            if(r + 1 < rows.size){
                IntRange nextRange = rows.get(r + 1);
                if(nextRange.start == nextRange.end){
                    nextSingleCell = cells.get(nextRange.start);
                }
            }

            if(actor instanceof Table && isLegacyHeaderBackground(((Table)actor).getBackground())
                && nextSingleCell != null && isTopLevelHorizontalRule(nextSingleCell)){
                // RadialBuildMenu.HeaderSetting already has a black3 header followed by a 3dp accent bar:
                // upgrade header to sectionHeaderBackground() and collapse the duplicate bar.
                Table headerTable = (Table)actor;
                headerTable.background(VscodeSettingsStyle.sectionHeaderBackground());
                expandFullRowWidth(cell);
                Element dupLine = nextSingleCell.get();
                if(dupLine != null) dupLine.visible = false;
                nextSingleCell.height(0f).minHeight(0f).maxHeight(0f).pad(0f);
                r++;
                continue;
            }

            if(actor instanceof Label && nextSingleCell != null && isTopLevelHorizontalRule(nextSingleCell)){
                // TripwireSettings.section: standalone grey section title followed by a 3dp horizontal line.
                Label secLabel = (Label)actor;
                secLabel.setWrap(true);
                secLabel.setColor(VscodeSettingsStyle.accentColor());
                expandFullRowWidth(cell);
                cell.padTop(10f).padBottom(4f);

                Image ruleImg = (Image)nextSingleCell.get();
                ruleImg.setDrawable(VscodeSettingsStyle.goldRuleDrawable());
                ruleImg.setColor(Color.white);
                expandFullRowWidth(nextSingleCell);
                nextSingleCell.height(2f).padTop(0f).padBottom(4f);
                r++;
                continue;
            }

            normalizeSingleSettingRow(cell, contentWidth);
        }
    }

    private static void normalizeSingleSettingRow(Cell cell, float contentWidth){
        Element actor = cell.get();
        if(actor == null) return;

        // Pure vertical spacer (empty Image or 0-width spacer)
        if(isTopLevelHorizontalRule(cell)){
            Image rule = (Image)actor;
            if(rule.getDrawable() == null){
                rule.setDrawable(VscodeSettingsStyle.goldRuleDrawable());
                rule.setColor(Color.white);
            }else if(isAccentColor(rule.color) || VscodeSettingsStyle.isBlueHue(rule.color) || isNeutralGray(rule.color)){
                rule.setColor(VscodeSettingsStyle.accentColor());
            }
            expandFullRowWidth(cell);
            return;
        }

        if(actor instanceof Stack){
            // Sub-mod slider overlays (PGMM, StealthPath, RadialBuildMenu): stretch to full width & HUD height.
            Stack stack = (Stack)actor;
            expandFullRowWidth(cell);
            cell.height(RbmStyle.buttonHeight());
            if(getRawPadTop(cell) <= 0f){
                cell.padTop(6f);
            }
            normalizeSliderStack(stack, contentWidth);
            return;
        }

        if(actor instanceof CheckBox){
            // Bare top-level CheckBox rows (e.g. BetterHotKeyFeature.CenteredCheckSetting)
            CheckBox box = (CheckBox)actor;
            box.setStyle(VscodeSettingsStyle.hudCardCheckBoxStyle());
            box.left().margin(9f);
            if(box.getLabel() != null){
                box.getLabel().setAlignment(Align.left);
                box.getLabel().setWrap(true);
            }
            if(box.getLabelCell() != null){
                box.getLabelCell().padLeft(8f).minWidth(0f).maxWidth(cellUnset).growX().left();
            }
            expandFullRowWidth(cell);
            cell.padTop(6f).padBottom(0f);
            return;
        }

        if(actor instanceof TextButton){
            // Bare top-level action buttons (BetterHotKey, ModUpdateCenter, BetterMiniMap, AutoPruner)
            TextButton button = (TextButton)actor;
            TextButton.TextButtonStyle oldStyle = button.getStyle();
            boolean toggleLike = isToggleButtonStyle(oldStyle);
            button.setStyle(toggleLike ? VscodeSettingsStyle.hudCardToggleButtonStyle() : VscodeSettingsStyle.hudCardButtonStyle());
            button.margin(8f, 12f, 8f, 12f);
            if(button.getLabel() != null){
                button.getLabel().setWrap(true);
                if(VscodeSettingsStyle.isBlueHue(button.getLabel().color)){
                    button.getLabel().setColor(VscodeSettingsStyle.primaryTextColor());
                }
            }
            expandFullRowWidth(cell);
            cell.minHeight(RbmStyle.buttonHeight()).padTop(6f).padBottom(0f);
            return;
        }

        if(actor instanceof Label){
            Label label = (Label)actor;
            label.setWrap(true);
            CharSequence txt = label.getText();
            boolean hasMarkup = txt != null && txt.length() > 0 && txt.charAt(0) == '[';
            if(!hasMarkup && getRawPadTop(cell) >= Scl.scl(12f)){
                // Standalone section title (e.g. ForeignServerTranslator "Marker Translation")
                label.setColor(VscodeSettingsStyle.accentColor());
            }else if(isAccentColor(label.color) || VscodeSettingsStyle.isBlueHue(label.color)){
                label.setColor(VscodeSettingsStyle.accentColor());
            }else if(!hasMarkup){
                label.setColor(VscodeSettingsStyle.mutedColor());
            }
            expandFullRowWidth(cell);
            return;
        }

        if(actor instanceof Table){
            Table rowTable = (Table)actor;
            expandFullRowWidth(cell);

            if(isStructuralWrapperTable(rowTable)){
                // Wrapper holding a nested SettingsTable or dynamic Pro-mode container (e.g. StealthPath)
                rowTable.background(null);
                rowTable.margin(0f);
                normalizeSettingsTableRows(rowTable, contentWidth);
                installDynamicWrapperObserver(rowTable, contentWidth);
                return;
            }

            Drawable bg = rowTable.getBackground();
            if(bg == null){
                // Uncarded control row (PatchViewer, LogicSugar, WhoUsesThisBuilding, StealthPath locked row)
                rowTable.background(VscodeSettingsStyle.hudCardBackground());
                if(rowTable.getMarginLeft() < Scl.scl(6f) && rowTable.getMarginTop() < Scl.scl(6f)){
                    rowTable.margin(9f);
                }
                if(getRawPadTop(cell) <= 0f){
                    cell.padTop(6f);
                }
            }else if(isLegacyCardBackground(bg)){
                rowTable.background(VscodeSettingsStyle.hudCardBackground());
            }else if(isLegacyHeaderBackground(bg)){
                rowTable.background(VscodeSettingsStyle.sectionHeaderBackground());
            }

            if(isCardBackground(rowTable.getBackground())){
                normalizeCardInternalLayout(rowTable, contentWidth);
            }
        }
    }

    private static void expandFullRowWidth(Cell cell){
        cell.minWidth(0f)
            .maxWidth(cellUnset)
            .growX()
            .fillX()
            .left()
            .padLeft(0f)
            .padRight(0f);
    }

    private static void normalizeSliderStack(Stack stack, float contentWidth){
        SnapshotSeq<Element> children = stack.getChildren();
        for(int i = 0; i < children.size; i++){
            Element child = children.get(i);
            if(child instanceof Slider){
                ((Slider)child).setStyle(VscodeSettingsStyle.flatSliderStyle());
            }else if(child instanceof Table){
                Table overlay = (Table)child;
                overlay.margin(3f, 10f, 3f, 10f);
                Seq<Cell> oCells = overlay.getCells();
                for(int c = 0; c < oCells.size; c++){
                    Cell oc = oCells.get(c);
                    if(oc == null) continue;
                    Element oa = oc.get();
                    if(oa instanceof Image){
                        ((Image)oa).setColor(VscodeSettingsStyle.accentColor());
                    }else if(oa instanceof Label){
                        Label lbl = (Label)oa;
                        boolean isTrailingValue = (c == oCells.size - 1 && oCells.size >= 2);
                        if(isTrailingValue){
                            lbl.setColor(VscodeSettingsStyle.accentColor());
                        }else{
                            lbl.setWrap(true);
                            lbl.setColor(VscodeSettingsStyle.primaryTextColor());
                            oc.minWidth(0f).growX().left();
                        }
                    }
                }
            }else if(child instanceof Group){
                reskinGroupChildren((Group)child, contentWidth, 1);
            }
        }
    }

    /**
     * Harmonizes horizontal distribution inside a setting card so left-hand titles expand cleanly
     * while right-hand inputs, color swatches, and action buttons align flush along the right edge.
     */
    private static void normalizeCardInternalLayout(Table card, float contentWidth){
        Seq<Cell> cells = card.getCells();
        if(cells == null || cells.isEmpty()) return;

        Seq<IntRange> rows = splitRows(cells);
        if(rows.size == 1){
            IntRange onlyRow = rows.get(0);
            int count = onlyRow.end - onlyRow.start + 1;
            if(count == 1){
                Cell c = cells.get(onlyRow.start);
                if(c != null && c.get() != null){
                    Element a = c.get();
                    if(a instanceof CheckBox){
                        c.minWidth(0f).maxWidth(cellUnset).growX().left();
                    }else if(a instanceof Button && !(a instanceof ImageButton)){
                        c.minWidth(0f).maxWidth(cellUnset).height(36f).growX().fillX();
                    }else if(a instanceof Stack){
                        c.minWidth(0f).maxWidth(cellUnset).height(36f).growX().fillX();
                    }else if(a instanceof Table){
                        c.minWidth(0f).maxWidth(cellUnset).growX().fillX();
                    }
                }
                return;
            }

            // Detect [Icon?] + Label title + [Right-side controls...]
            int titleIdx = -1;
            Element first = cells.get(onlyRow.start) == null ? null : cells.get(onlyRow.start).get();
            if(first instanceof Label){
                titleIdx = onlyRow.start;
            }else if(first instanceof Image && onlyRow.start + 1 <= onlyRow.end){
                Element second = cells.get(onlyRow.start + 1) == null ? null : cells.get(onlyRow.start + 1).get();
                if(second instanceof Label){
                    titleIdx = onlyRow.start + 1;
                }
            }

            if(titleIdx >= 0 && titleIdx < onlyRow.end){
                // Keep fixed-column layouts intact when an inner non-Button Table already expands
                // (e.g. RadialBuildMenu.SlotSetting: [160dp slot label | expanding block info Table | 140dp Set button]).
                boolean siblingContainerExpands = false;
                for(int i = titleIdx + 1; i <= onlyRow.end; i++){
                    Cell sc = cells.get(i);
                    if(sc == null) continue;
                    Element sa = sc.get();
                    if(sa instanceof Table && !(sa instanceof Button) && getCellExpandX(sc) > 0 && !hasExplicitMaxWidth(sc)){
                        siblingContainerExpands = true;
                        break;
                    }
                }

                if(!siblingContainerExpands){
                    Cell titleCell = cells.get(titleIdx);
                    Label titleLabel = (Label)titleCell.get();
                    titleLabel.setWrap(true);
                    if(!VscodeSettingsStyle.isBlueHue(titleLabel.color) && !isAccentColor(titleLabel.color)){
                        titleLabel.setColor(VscodeSettingsStyle.primaryTextColor());
                    }
                    titleCell.left().minWidth(0f).maxWidth(cellUnset).growX().padRight(10f);

                    float uniformFieldW = Math.min(220f, Math.max(120f, contentWidth * 0.32f));
                    float maxRightControlW = Math.max(130f, contentWidth * 0.46f);

                    for(int i = titleIdx + 1; i <= onlyRow.end; i++){
                        Cell rc = cells.get(i);
                        if(rc == null || rc.get() == null) continue;
                        Element ra = rc.get();

                        if(ra instanceof TextField && !(ra instanceof TextArea)){
                            rc.width(uniformFieldW).height(34f).expand(0, 0).fill(0f, 0f).right();
                        }else if(ra instanceof CheckBox){
                            rc.expand(0, 0).fill(0f, 0f).right();
                        }else if(ra instanceof Button && !(ra instanceof ImageButton)){
                            if(getCellExpandX(rc) > 0){
                                rc.width(uniformFieldW).height(36f).expand(0, 0).fill(0f, 0f).right();
                            }else{
                                float curMinDp = toLogicalDp(getRawMinWidth(rc));
                                if(curMinDp > maxRightControlW){
                                    rc.width(maxRightControlW);
                                }else if(curMinDp <= 0f){
                                    rc.minWidth(84f);
                                }
                                rc.height(36f).right();
                            }
                        }
                    }
                }
            }
        }else{
            // Multi-row card: clamp oversized minimum widths on narrow viewports without breaking inner columns.
            for(int r = 0; r < rows.size; r++){
                IntRange range = rows.get(r);
                if(range.end > range.start){
                    normalizeMultiCellRow(cells, range.start, range.end, contentWidth);
                }
            }
        }
    }

    private static void normalizeMultiCellRow(Seq<Cell> cells, int start, int end, float contentWidth){
        float maxSingleCellMinPx = Scl.scl(Math.max(90f, contentWidth * 0.45f));
        for(int i = start; i <= end; i++){
            Cell cell = cells.get(i);
            if(cell == null) continue;
            Element actor = cell.get();
            if(actor instanceof Label){
                ((Label)actor).setWrap(true);
                if(getRawMinWidth(cell) > maxSingleCellMinPx){
                    cell.minWidth(0f);
                }
            }else if(actor != null && getRawMinWidth(cell) > maxSingleCellMinPx){
                cell.minWidth(0f);
                if(actor.getPrefWidth() > Scl.scl(contentWidth)){
                    cell.maxWidth(contentWidth);
                }
            }
        }
    }

    private static void reskinSubtree(Table table, float contentWidth, int depth, boolean isRootSettingsTable){
        if(table == null || depth > maxReskinDepth) return;

        if(!isRootSettingsTable){
            Drawable bg = table.getBackground();
            if(bg != null){
                if(isLegacyCardBackground(bg)){
                    table.background(VscodeSettingsStyle.hudCardBackground());
                    normalizeCardInternalLayout(table, contentWidth);
                }else if(isLegacyHeaderBackground(bg)){
                    table.background(VscodeSettingsStyle.sectionHeaderBackground());
                }
            }
        }

        Seq<Cell> cells = table.getCells();
        if(cells == null) return;
        for(int i = 0; i < cells.size; i++){
            Cell cell = cells.get(i);
            if(cell == null) continue;
            Element actor = cell.get();
            if(actor == null) continue;
            reskinActor(actor, contentWidth, depth);
        }
    }

    private static void reskinActor(Element actor, float contentWidth, int depth){
        if(actor == null || depth > maxReskinDepth) return;

        if(actor instanceof CheckBox){
            CheckBox check = (CheckBox)actor;
            if(check.getStyle() != VscodeSettingsStyle.hudCardCheckBoxStyle()){
                check.setStyle(VscodeSettingsStyle.flatCheckBoxStyle());
            }
            return;
        }

        if(actor instanceof Slider){
            ((Slider)actor).setStyle(VscodeSettingsStyle.flatSliderStyle());
            return;
        }

        if(actor instanceof TextField){
            TextField field = (TextField)actor;
            if(field.getStyle() != VscodeSettingsStyle.flatSearchFieldStyle()){
                field.setStyle(VscodeSettingsStyle.flatFieldStyle());
            }
            return;
        }

        if(actor instanceof ImageButton){
            ImageButton imgBtn = (ImageButton)actor;
            ImageButton.ImageButtonStyle oldStyle = imgBtn.getStyle();
            boolean hadFrame = oldStyle != null && oldStyle.up != null
                && oldStyle != Styles.emptyi && oldStyle != Styles.clearNonei && oldStyle != Styles.cleari;
            imgBtn.setStyle(hadFrame ? VscodeSettingsStyle.flatTileIconButtonStyle() : VscodeSettingsStyle.flatIconButtonStyle());
            return;
        }

        if(actor instanceof TextButton){
            TextButton btn = (TextButton)actor;
            TextButton.TextButtonStyle cur = btn.getStyle();
            if(cur != VscodeSettingsStyle.hudCardButtonStyle() && cur != VscodeSettingsStyle.hudCardToggleButtonStyle()){
                boolean toggleLike = isToggleButtonStyle(cur);
                btn.setStyle(toggleLike ? VscodeSettingsStyle.flatToggleButtonStyle() : VscodeSettingsStyle.flatButtonStyle());
            }
            if(btn.getLabel() != null && VscodeSettingsStyle.isBlueHue(btn.getLabel().color)){
                btn.getLabel().setColor(VscodeSettingsStyle.primaryTextColor());
            }
            return;
        }

        if(actor instanceof Image){
            Image img = (Image)actor;
            // Never alter explicit RGB color preview swatches (e.g. PatchViewer / RBM whiteui squares);
            // only remap Mindustry accent or forbidden blue UI icons.
            if(img.getDrawable() != Tex.whiteui && (isAccentColor(img.color) || VscodeSettingsStyle.isBlueHue(img.color))){
                img.setColor(VscodeSettingsStyle.accentColor());
            }
            return;
        }

        if(actor instanceof Label){
            Label label = (Label)actor;
            if(isAccentColor(label.color) || VscodeSettingsStyle.isBlueHue(label.color)){
                label.setColor(VscodeSettingsStyle.accentColor());
            }
            return;
        }

        if(actor instanceof Stack){
            normalizeSliderStack((Stack)actor, contentWidth);
            reskinGroupChildren((Stack)actor, contentWidth, depth + 1);
            return;
        }

        if(actor instanceof Table){
            if(actor instanceof Button){
                // Plain Button instances (e.g. LogicSugarSettings cycle buttons with Styles.logict)
                Button rawBtn = (Button)actor;
                rawBtn.setStyle(VscodeSettingsStyle.toolbarTileStyle(false));
                if(rawBtn.getMarginLeft() < Scl.scl(8f)){
                    rawBtn.margin(6f, 12f, 6f, 12f);
                }
            }
            Table childTable = (Table)actor;
            if(childTable instanceof SettingsMenuDialog.SettingsTable){
                normalizeSettingsTableRows(childTable, Math.max(220f, contentWidth - 16f));
                reskinSubtree(childTable, Math.max(220f, contentWidth - 16f), depth + 1, true);
            }else{
                reskinSubtree(childTable, contentWidth, depth + 1, false);
            }
            return;
        }

        if(actor instanceof Group){
            // Traverses Collapser (e.g. RadialBuildMenu.CollapsiblePlanetSetting), ScrollPane, WidgetGroup, etc.
            reskinGroupChildren((Group)actor, contentWidth, depth + 1);
        }
    }

    private static void reskinGroupChildren(Group group, float contentWidth, int depth){
        if(group == null || depth > maxReskinDepth) return;
        SnapshotSeq<Element> children = group.getChildren();
        for(int i = 0; i < children.size; i++){
            reskinActor(children.get(i), contentWidth, depth);
        }
    }

    /** Re-normalizes wrapper tables that rebuild their children dynamically on toggle (e.g. StealthPath Pro Mode). */
    private static void installDynamicWrapperObserver(Table wrapper, float contentWidth){
        final Element[] lastFirstChild = {wrapper.getChildren().isEmpty() ? null : wrapper.getChildren().first()};
        final int[] lastCount = {wrapper.getChildren().size};
        wrapper.update(() -> {
            SnapshotSeq<Element> kids = wrapper.getChildren();
            Element first = kids.isEmpty() ? null : kids.first();
            if(kids.size != lastCount[0] || first != lastFirstChild[0]){
                lastCount[0] = kids.size;
                lastFirstChild[0] = first;
                normalizeSettingsTableRows(wrapper, contentWidth);
                reskinSubtree(wrapper, contentWidth, 1, true);
                wrapper.invalidateHierarchy();
            }
        });
    }

    private static boolean isStructuralWrapperTable(Table table){
        if(table == null || table.getBackground() != null) return false;
        Seq<Cell> cells = table.getCells();
        if(cells == null || cells.isEmpty()) return false;
        for(int i = 0; i < cells.size; i++){
            Cell c = cells.get(i);
            if(c == null || c.get() == null) continue;
            Element a = c.get();
            if(a instanceof SettingsMenuDialog.SettingsTable || a instanceof Collapser){
                return true;
            }
            if(!(a instanceof Table) || a instanceof Button){
                return false;
            }
        }
        return true;
    }

    private static boolean isTopLevelHorizontalRule(Cell cell){
        if(cell == null || !(cell.get() instanceof Image)) return false;
        Image image = (Image)cell.get();
        Drawable d = image.getDrawable();
        if(d != null && d != Tex.whiteui && d != VscodeSettingsStyle.goldRuleDrawable() && d != VscodeSettingsStyle.dividerDrawable()){
            return false;
        }
        float maxH = cell.maxHeight();
        float minW = getRawMinWidth(cell);
        // Square swatches have minW == maxH (> 8px); horizontal rules have tiny height (<= 8dp) and non-square width.
        if(maxH > 0f && maxH <= Scl.scl(8f)){
            return !(minW > 0f && Math.abs(minW - maxH) < 1f);
        }
        return d == Tex.whiteui && image.getPrefHeight() <= Scl.scl(6f) && minW <= 0f && maxH <= 0f;
    }

    private static boolean isToggleButtonStyle(TextButton.TextButtonStyle style){
        if(style == null) return false;
        return style == Styles.togglet
            || style == Styles.flatTogglet
            || style == Styles.flatToggleMenut
            || style == Styles.logicTogglet
            || style == VscodeSettingsStyle.flatToggleButtonStyle()
            || style == VscodeSettingsStyle.hudCardToggleButtonStyle();
    }

    private static boolean isLegacyCardBackground(Drawable bg){
        return bg == Tex.button || bg == Tex.pane || bg == Styles.grayPanel;
    }

    private static boolean isLegacyHeaderBackground(Drawable bg){
        return bg == Styles.black3 || bg == Styles.black6 || bg == Styles.black8;
    }

    private static boolean isCardBackground(Drawable bg){
        return bg == VscodeSettingsStyle.hudCardBackground() || bg == VscodeSettingsStyle.cardBackground();
    }

    private static boolean isAccentColor(Color color){
        if(color == null || Pal.accent == null) return false;
        return Math.abs(color.r - Pal.accent.r) < 0.03f
            && Math.abs(color.g - Pal.accent.g) < 0.03f
            && Math.abs(color.b - Pal.accent.b) < 0.03f;
    }

    private static boolean isNeutralGray(Color color){
        if(color == null || color.a < 0.1f) return false;
        return Math.abs(color.r - color.g) < 0.03f && Math.abs(color.g - color.b) < 0.03f && color.r > 0.2f && color.r < 0.8f;
    }

    private static int getCellExpandX(Cell cell){
        if(cell == null || cellExpandXField == null) return 0;
        try{
            return cellExpandXField.getInt(cell);
        }catch(Throwable ignored){
            return 0;
        }
    }

    private static boolean hasExplicitMaxWidth(Cell cell){
        if(cell == null || cellRawMaxWidthField == null) return false;
        try{
            float raw = cellRawMaxWidthField.getFloat(cell);
            return raw > 0f;
        }catch(Throwable ignored){
            return false;
        }
    }

    private static float getRawMinWidth(Cell cell){
        if(cell == null || cellRawMinWidthField == null) return 0f;
        try{
            float raw = cellRawMinWidthField.getFloat(cell);
            return raw > 0f ? raw : 0f;
        }catch(Throwable ignored){
            return 0f;
        }
    }

    private static float getRawPadTop(Cell cell){
        if(cell == null || cellPadTopField == null) return 0f;
        try{
            return cellPadTopField.getFloat(cell);
        }catch(Throwable ignored){
            return 0f;
        }
    }

    private static float toLogicalDp(float scaledPx){
        if(scaledPx <= 0f) return 0f;
        float s = Scl.scl(1f);
        return s > 0f ? scaledPx / s : scaledPx;
    }

    private static final class IntRange{
        final int start;
        final int end;

        IntRange(int start, int end){
            this.start = start;
            this.end = end;
        }
    }
}
