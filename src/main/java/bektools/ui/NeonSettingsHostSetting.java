package bektools.ui;

import arc.Core;
import arc.func.Boolp;
import arc.func.Prov;
import arc.input.KeyCode;
import arc.scene.Element;
import arc.scene.Group;
import arc.scene.event.InputEvent;
import arc.scene.event.Touchable;
import arc.scene.ui.Button;
import arc.scene.ui.Dialog;
import arc.scene.ui.Label;
import arc.scene.ui.TextButton;
import arc.scene.ui.layout.Cell;
import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import arc.struct.SnapshotSeq;
import arc.util.Log;
import mindustry.ui.dialogs.SettingsMenuDialog;

import java.util.Locale;

/**
 * Single top-level {@link SettingsMenuDialog.SettingsTable.Setting} mounted into Neon's settings category.
 *
 * <p>Keeping the outer {@link SettingsMenuDialog.SettingsTable} at a constant {@code list.size == 1}
 * guarantees that MindustryX's {@code SettingsTable.act()} ({@code lastSize != list.size}) never
 * triggers spurious rebuilds.</p>
 *
 * <p>Reliability contract: {@link #add(SettingsMenuDialog.SettingsTable)} immediately mounts
 * {@link NeonSettingsWorkbench} inline with concrete non-zero dimensions so the category never
 * collapses to a zero-height placeholder showing only the vanilla "Reset to Defaults" button
 * (across both vanilla Mindustry and MindustryX settings layouts). When Neon's category is the
 * sole active category inside a shown {@link Dialog} with valid bounds, {@link OverlayController}
 * safely promotes the workbench to a full-screen dialog overlay and keeps its bounds explicitly
 * synced on every frame, automatically falling back to inline mode if full-screen takeover is
 * unavailable or fails.</p>
 */
public class NeonSettingsHostSetting extends SettingsMenuDialog.SettingsTable.Setting{
    private static final String hostSettingName = "neon-settings-workbench";

    private final Seq<NeonSettingsModule> modules;
    private final NeonSettingsContext context;
    private NeonSettingsWorkbench sharedWorkbench;
    private OverlayController activeController;

    public NeonSettingsHostSetting(Seq<NeonSettingsModule> modules, NeonSettingsContext context){
        super(hostSettingName);
        this.modules = modules;
        this.context = context;
    }

    @Override
    public void add(SettingsMenuDialog.SettingsTable table){
        if(activeController != null){
            activeController.dispose();
            activeController = null;
        }

        try{
            if(sharedWorkbench == null){
                sharedWorkbench = new NeonSettingsWorkbench(modules, context, this::exitActiveCategory);
            }
        }catch(Throwable t){
            Log.err("Neon: failed to construct NeonSettingsWorkbench", t);
            table.row();
            Label err = new Label("Neon settings failed to initialize: " + t.getClass().getSimpleName()
                + (t.getMessage() == null ? "" : " - " + t.getMessage()));
            err.setWrap(true);
            table.add(err).growX().pad(12f).row();
            return;
        }

        OverlayController controller = new OverlayController(table, sharedWorkbench);
        activeController = controller;

        table.row();
        float initialW = sharedWorkbench.computeWorkbenchWidthDp();
        float initialH = sharedWorkbench.computeWorkbenchHeightDp();
        Cell<HostAnchor> anchorCell = table.add(controller.anchor)
            .width(initialW)
            .height(initialH)
            .center()
            .pad(0f);
        controller.setAnchorCell(anchorCell);
        // Mount inline immediately so the workbench is always visible even before act() runs
        // or when hosted inside MindustryX/custom settings containers outside dialog.cont.
        controller.mountInline();
        table.row();
    }

    private void exitActiveCategory(){
        if(activeController != null){
            activeController.exitCategoryToSettingsMenu();
        }
    }

    private static final class SavedSiblingState{
        final Element element;
        final boolean visible;
        final Boolp visibility;
        final Touchable touchable;
        final Prov<Touchable> touchability;

        SavedSiblingState(Element element, boolean visible, Boolp visibility, Touchable touchable, Prov<Touchable> touchability){
            this.element = element;
            this.visible = visible;
            this.visibility = visibility;
            this.touchable = touchable;
            this.touchability = touchability;
        }

        void restore(){
            element.visibility = visibility;
            element.visible = visible;
            element.touchablility = touchability;
            element.touchable = touchable;
            element.updateVisibility();
        }
    }

    private static final class HostAnchor extends Table{
        private final Runnable onAct;

        HostAnchor(Runnable onAct){
            this.onAct = onAct;
            this.touchable = Touchable.childrenOnly;
        }

        @Override
        public void act(float delta){
            super.act(delta);
            if(onAct != null){
                onAct.run();
            }
        }
    }

    private static final class OverlayController{
        final SettingsMenuDialog.SettingsTable categoryTable;
        final NeonSettingsWorkbench workbench;
        final HostAnchor anchor;
        private Cell<HostAnchor> anchorCell;

        private Dialog mountedDialog;
        private final Seq<SavedSiblingState> hiddenSiblings = new Seq<>();
        private boolean inlineMounted;
        private float lastInlineWidthDp = -1f;
        private float lastInlineHeightDp = -1f;
        private float lastFullScreenWidth = -1f;
        private float lastFullScreenHeight = -1f;
        private boolean fullScreenDisabled;
        private boolean exiting;
        private boolean disposed;

        OverlayController(SettingsMenuDialog.SettingsTable categoryTable, NeonSettingsWorkbench workbench){
            this.categoryTable = categoryTable;
            this.workbench = workbench;
            this.anchor = new HostAnchor(this::sync);
            this.workbench.setOverlaySyncHook(this::sync);
        }

        void setAnchorCell(Cell<HostAnchor> cell){
            this.anchorCell = cell;
        }

        void dispose(){
            disposed = true;
            unmountFullScreen();
            unmountInline();
            workbench.setOverlaySyncHook(null);
        }

        void sync(){
            if(disposed) return;

            boolean attachedToScene = categoryTable.getScene() != null;
            if(!attachedToScene){
                exiting = false;
                if(mountedDialog != null){
                    unmountFullScreen();
                }
                if(!inlineMounted){
                    mountInline();
                }
                return;
            }

            if(exiting){
                return;
            }

            Dialog dialog = findAncestorDialog(categoryTable);
            boolean canFullScreen = !fullScreenDisabled
                && dialog != null
                && dialog.getScene() != null
                && dialog.isShown()
                && dialog.getWidth() > 0f
                && dialog.getHeight() > 0f
                && categoryTable.isDescendantOf(dialog)
                && isSoleCategoryTable(dialog.cont != null && categoryTable.isDescendantOf(dialog.cont) ? dialog.cont : dialog, categoryTable);

            if(canFullScreen){
                if(inlineMounted){
                    unmountInline();
                }
                if(mountedDialog != dialog){
                    if(mountedDialog != null){
                        unmountFullScreen();
                    }
                    try{
                        mountFullScreen(dialog);
                    }catch(Throwable t){
                        Log.err("Neon: full-screen settings overlay failed, falling back to inline mode", t);
                        fullScreenDisabled = true;
                        unmountFullScreen();
                        mountInline();
                    }
                }else{
                    suppressNewlyVisibleSiblings(dialog);
                    syncFullScreenBounds(dialog);
                    SnapshotSeq<Element> children = dialog.getChildren();
                    if(children.size > 0 && children.peek() != workbench){
                        workbench.toFront();
                    }
                }
            }else{
                if(mountedDialog != null){
                    unmountFullScreen();
                }
                if(!inlineMounted){
                    mountInline();
                }else{
                    syncInlineCellSize();
                }
            }
        }

        private void mountFullScreen(Dialog dialog){
            mountedDialog = dialog;
            suppressSiblings(dialog);

            workbench.remove();
            workbench.setFullScreenMode(true);
            workbench.setFillParent(true);
            workbench.visible = true;
            workbench.touchable = Touchable.enabled;

            dialog.addChild(workbench);
            lastFullScreenWidth = -1f;
            lastFullScreenHeight = -1f;
            syncFullScreenBounds(dialog);
            workbench.toFront();
            workbench.onFullScreenMounted();
            dialog.invalidateHierarchy();
        }

        private void syncFullScreenBounds(Dialog dialog){
            if(dialog == null) return;
            if(!workbench.fillParent){
                workbench.setFillParent(true);
            }
            if(workbench.x != 0f || workbench.y != 0f){
                workbench.setPosition(0f, 0f);
            }
            float targetW = dialog.getWidth();
            float targetH = dialog.getHeight();
            if(targetW > 0f && targetH > 0f
                && (Math.abs(targetW - lastFullScreenWidth) > 0.5f
                    || Math.abs(targetH - lastFullScreenHeight) > 0.5f
                    || Math.abs(workbench.getWidth() - targetW) > 0.5f
                    || Math.abs(workbench.getHeight() - targetH) > 0.5f)){
                lastFullScreenWidth = targetW;
                lastFullScreenHeight = targetH;
                workbench.setSize(targetW, targetH);
                workbench.invalidateHierarchy();
                workbench.validate();
            }
        }

        private void suppressSiblings(Dialog dialog){
            hiddenSiblings.clear();
            SnapshotSeq<Element> children = dialog.getChildren();
            for(int i = 0; i < children.size; i++){
                Element child = children.get(i);
                if(child == null || child == workbench) continue;
                hiddenSiblings.add(new SavedSiblingState(
                    child,
                    child.visible,
                    child.visibility,
                    child.touchable,
                    child.touchablility
                ));
                child.visibility = null;
                child.visible = false;
                child.touchablility = null;
                child.touchable = Touchable.disabled;
            }
        }

        private void suppressNewlyVisibleSiblings(Dialog dialog){
            SnapshotSeq<Element> children = dialog.getChildren();
            for(int i = 0; i < children.size; i++){
                Element child = children.get(i);
                if(child == null || child == workbench) continue;
                if(child.visible || child.visibility != null){
                    if(!isTrackedSibling(child)){
                        hiddenSiblings.add(new SavedSiblingState(
                            child,
                            child.visible,
                            child.visibility,
                            child.touchable,
                            child.touchablility
                        ));
                    }
                    child.visibility = null;
                    child.visible = false;
                    child.touchablility = null;
                    child.touchable = Touchable.disabled;
                }
            }
        }

        private boolean isTrackedSibling(Element candidate){
            for(int i = 0; i < hiddenSiblings.size; i++){
                if(hiddenSiblings.get(i).element == candidate) return true;
            }
            return false;
        }

        private void unmountFullScreen(){
            Dialog dialog = mountedDialog;
            mountedDialog = null;
            lastFullScreenWidth = -1f;
            lastFullScreenHeight = -1f;

            workbench.setFillParent(false);
            if(workbench.parent == dialog && dialog != null){
                workbench.remove();
            }

            for(int i = 0; i < hiddenSiblings.size; i++){
                hiddenSiblings.get(i).restore();
            }
            hiddenSiblings.clear();

            if(dialog != null){
                dialog.invalidateHierarchy();
                if(dialog.isShown() && Core.scene != null){
                    dialog.updateScrollFocus();
                }
            }
        }

        void mountInline(){
            inlineMounted = true;
            workbench.remove();
            workbench.setFillParent(false);
            workbench.setFullScreenMode(false);
            workbench.visible = true;
            workbench.touchable = Touchable.enabled;
            anchor.clearChildren();
            float w = workbench.computeWorkbenchWidthDp();
            float h = workbench.computeWorkbenchHeightDp();
            lastInlineWidthDp = w;
            lastInlineHeightDp = h;
            anchor.add(workbench).width(w).height(h).center();
            if(anchorCell != null){
                anchorCell.width(w).height(h);
            }
            categoryTable.invalidateHierarchy();
        }

        private void syncInlineCellSize(){
            float w = workbench.computeWorkbenchWidthDp();
            float h = workbench.computeWorkbenchHeightDp();
            if(Math.abs(w - lastInlineWidthDp) >= 16f || Math.abs(h - lastInlineHeightDp) >= 16f){
                lastInlineWidthDp = w;
                lastInlineHeightDp = h;
                Cell<NeonSettingsWorkbench> cell = anchor.getCell(workbench);
                if(cell != null){
                    cell.width(w).height(h);
                }
                if(anchorCell != null){
                    anchorCell.width(w).height(h);
                }
                categoryTable.invalidateHierarchy();
            }
        }

        private void unmountInline(){
            inlineMounted = false;
            if(workbench.parent == anchor){
                workbench.remove();
            }
            anchor.clearChildren();
            if(anchorCell != null){
                anchorCell.width(0f).height(0f);
            }
            lastInlineWidthDp = -1f;
            lastInlineHeightDp = -1f;
        }

        void exitCategoryToSettingsMenu(){
            Dialog dialog = mountedDialog != null ? mountedDialog : findAncestorDialog(categoryTable);
            exiting = true;
            if(Core.app != null){
                Core.app.post(() -> exiting = false);
            }

            unmountFullScreen();
            unmountInline();

            if(dialog == null) return;

            Button backBtn = findBackButton(dialog.buttons);
            if(backBtn != null){
                backBtn.fireClick();
            }

            if(isCategoryStillMounted(dialog) && backBtn != null){
                backBtn.change();
            }

            if(isCategoryStillMounted(dialog)){
                InputEvent esc = new InputEvent();
                esc.type = InputEvent.InputEventType.keyDown;
                esc.keyCode = KeyCode.escape;
                dialog.fire(esc);
            }

            if(isCategoryStillMounted(dialog)){
                dialog.hide();
            }

            if(!isCategoryStillMounted(dialog)){
                exiting = false;
            }
        }

        private boolean isCategoryStillMounted(Dialog dialog){
            return categoryTable.getScene() != null
                && dialog != null
                && categoryTable.isDescendantOf(dialog);
        }

        private static Dialog findAncestorDialog(Element element){
            Group p = element.parent;
            while(p != null){
                if(p instanceof Dialog){
                    return (Dialog)p;
                }
                p = p.parent;
            }
            return null;
        }

        private static boolean isSoleCategoryTable(Group root, SettingsMenuDialog.SettingsTable target){
            if(root == null) return true;
            SnapshotSeq<Element> children = root.getChildren();
            for(int i = 0; i < children.size; i++){
                Element child = children.get(i);
                if(child == null || child == target || !child.visible) continue;
                if(child instanceof SettingsMenuDialog.SettingsTable){
                    return false;
                }
                if(child instanceof Group && !isSoleCategoryTable((Group)child, target)){
                    return false;
                }
            }
            return true;
        }

        private static Button findBackButton(Group buttonsRoot){
            if(buttonsRoot == null) return null;
            Seq<Button> candidates = new Seq<>();
            collectButtons(buttonsRoot, candidates);
            if(candidates.isEmpty()) return null;

            String localizedBack = Core.bundle == null ? "Back" : Core.bundle.get("back", "Back");
            for(int i = 0; i < candidates.size; i++){
                Button b = candidates.get(i);
                if(b.name != null){
                    String lowerName = b.name.toLowerCase(Locale.ROOT);
                    if(lowerName.contains("back") || lowerName.contains("close")){
                        return b;
                    }
                }
                if(b instanceof TextButton){
                    CharSequence raw = ((TextButton)b).getText();
                    String text = raw == null ? "" : raw.toString().trim();
                    if(text.equalsIgnoreCase(localizedBack)
                        || text.equalsIgnoreCase("@back")
                        || text.equals("返回")
                        || text.equalsIgnoreCase("Back")){
                        return b;
                    }
                }
            }
            return candidates.first();
        }

        private static void collectButtons(Group group, Seq<Button> out){
            SnapshotSeq<Element> children = group.getChildren();
            for(int i = 0; i < children.size; i++){
                Element child = children.get(i);
                if(child instanceof Button){
                    out.add((Button)child);
                }else if(child instanceof Group){
                    collectButtons((Group)child, out);
                }
            }
        }
    }
}
