package bektools.ui;

import arc.graphics.Color;
import arc.scene.Element;
import arc.scene.style.Drawable;
import arc.scene.ui.Image;
import arc.scene.ui.Label;
import arc.scene.ui.TextButton;
import arc.scene.ui.layout.Cell;
import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import mindustry.gen.Tex;
import mindustry.graphics.Pal;
import mindustry.ui.Styles;

/**
 * Post-build visual normalizer for sub-mod settings rendered inside {@link SubmodSettingsHostTable}.
 *
 * <p>Harmonizes legacy sub-mod cards ({@link Tex#button}, {@link Styles#black3}, {@link Pal#accent})
 * into Neon's VSCode dark palette and clamps fixed-width rows so narrow / Android screens never clip.</p>
 */
public final class SettingsVisualNormalizer{
    private static final int maxReskinDepth = 5;

    private SettingsVisualNormalizer(){
    }

    public static void normalize(Table root, float contentWidth){
        if(root == null) return;
        float clampedWidth = Math.max(240f, contentWidth);
        normalizeTopLevelRows(root, clampedWidth);
        reskinSubtree(root, clampedWidth, 0);
    }

    private static void normalizeTopLevelRows(Table table, float contentWidth){
        Seq<Cell> cells = table.getCells();
        if(cells == null || cells.isEmpty()) return;

        int rowStart = 0;
        for(int i = 0; i < cells.size; i++){
            Cell cell = cells.get(i);
            boolean endOfRow = cell.isEndRow() || i == cells.size - 1;
            if(!endOfRow) continue;

            int rowLen = i - rowStart + 1;
            if(rowLen == 1){
                normalizeSingleCellRow(cells.get(rowStart), contentWidth);
            }else{
                normalizeMultiCellRow(cells, rowStart, i, contentWidth);
            }
            rowStart = i + 1;
        }
    }

    private static void normalizeSingleCellRow(Cell cell, float contentWidth){
        if(cell == null) return;
        Element actor = cell.get();
        if(actor == null) return;

        // Preserve pure vertical spacer cells (no actor or tiny fixed height empty image).
        if(actor instanceof Image && isDividerLine((Image)actor)){
            cell.minWidth(0f).maxWidth(contentWidth).growX().left();
            return;
        }

        if(actor instanceof Label){
            Label label = (Label)actor;
            label.setWrap(true);
            cell.minWidth(0f).maxWidth(contentWidth).growX().left();
            return;
        }

        if(actor instanceof TextButton){
            TextButton button = (TextButton)actor;
            if(button.getLabel() != null){
                button.getLabel().setWrap(true);
            }
            cell.minWidth(0f).maxWidth(contentWidth).growX();
            return;
        }

        if(actor instanceof Table){
            cell.minWidth(0f).maxWidth(contentWidth).growX().left();
        }
    }

    private static void normalizeMultiCellRow(Seq<Cell> cells, int start, int end, float contentWidth){
        float maxSingleCellMin = Math.max(80f, contentWidth * 0.42f);
        for(int i = start; i <= end; i++){
            Cell cell = cells.get(i);
            if(cell == null) continue;
            Element actor = cell.get();
            if(actor instanceof Label){
                ((Label)actor).setWrap(true);
                cell.minWidth(0f);
            }else if(actor != null && actor.getPrefWidth() > maxSingleCellMin){
                cell.minWidth(0f);
                if(actor.getPrefWidth() > contentWidth){
                    cell.maxWidth(contentWidth);
                }
            }
        }
    }

    private static void reskinSubtree(Table table, float contentWidth, int depth){
        if(table == null || depth > maxReskinDepth) return;

        Drawable bg = table.getBackground();
        if(bg != null){
            if(bg == Tex.button){
                table.background(VscodeSettingsStyle.cardBackground());
            }else if(bg == Styles.black3){
                table.background(VscodeSettingsStyle.headerBackground());
            }
        }

        Seq<Cell> cells = table.getCells();
        if(cells == null) return;
        for(int i = 0; i < cells.size; i++){
            Cell cell = cells.get(i);
            if(cell == null) continue;
            Element actor = cell.get();
            if(actor == null) continue;

            if(actor instanceof Image && isDividerLine((Image)actor)){
                Image img = (Image)actor;
                if(isAccentColor(img.color)){
                    img.setColor(VscodeSettingsStyle.accentColor());
                }
                cell.minWidth(0f).maxWidth(contentWidth).growX();
                continue;
            }

            if(actor instanceof Label){
                Label label = (Label)actor;
                if(isAccentColor(label.color)){
                    label.setColor(VscodeSettingsStyle.accentColor());
                }
                continue;
            }

            if(actor instanceof Table){
                Table childTable = (Table)actor;
                // Sub-mods such as StealthPath's AdvancedSectionSetting create nested SettingsTables.
                // Normalize their rows too so inner cards match the outer pane width.
                if(depth == 0 || childTable instanceof mindustry.ui.dialogs.SettingsMenuDialog.SettingsTable){
                    normalizeTopLevelRows(childTable, Math.max(220f, contentWidth - 16f));
                }
                reskinSubtree(childTable, contentWidth, depth + 1);
            }
        }
    }

    private static boolean isDividerLine(Image image){
        if(image == null) return false;
        Drawable d = image.getDrawable();
        return d == Tex.whiteui && image.getPrefHeight() <= 6f;
    }

    private static boolean isAccentColor(Color color){
        if(color == null || Pal.accent == null) return false;
        return Math.abs(color.r - Pal.accent.r) < 0.02f
            && Math.abs(color.g - Pal.accent.g) < 0.02f
            && Math.abs(color.b - Pal.accent.b) < 0.02f;
    }
}
