package pinyinsearchsupport.ui;

import arc.Core;
import arc.Graphics;
import arc.Application;
import arc.graphics.Color;
import arc.graphics.GL20;
import arc.graphics.g2d.Font;
import arc.graphics.g2d.Font.FontData;
import arc.graphics.g2d.TextureRegion;
import arc.mock.MockGraphics;
import arc.mock.MockGL20;
import arc.mock.MockApplication;
import arc.scene.Element;
import arc.scene.Scene;
import arc.scene.ui.Dialog;
import arc.scene.ui.Dialog.DialogStyle;
import arc.scene.ui.Label;
import arc.scene.ui.Label.LabelStyle;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.ScrollPane.ScrollPaneStyle;
import arc.scene.ui.TextField;
import arc.scene.ui.TextField.TextFieldStyle;
import arc.scene.ui.layout.Table;
import arc.scene.ui.layout.WidgetGroup;
import arc.struct.Seq;
import arc.struct.StringMap;
import mindustry.Vars;
import mindustry.core.UI;
import mindustry.game.Schematic;
import pinyinsearchsupport.match.MatchEngine;
import sun.misc.Unsafe;

import java.lang.reflect.Field;

public final class PinyinScopeContextTest{
    private static final Field keyboardFocus = field(Scene.class, "keyboardFocus");
    private static final Unsafe unsafe = unsafe();

    private PinyinScopeContextTest(){
    }

    public static void main(String[] args) throws Exception{
        run();
        System.out.println("PinyinScopeContextTest passed");
    }

    public static void run() throws Exception{
        Graphics originalGraphics = Core.graphics;
        Application originalApp = Core.app;
        GL20 originalGl = Core.gl;
        GL20 originalGl20 = Core.gl20;
        Scene originalScene = Core.scene;
        UI originalUi = Vars.ui;
        try{
            Core.graphics = new MockGraphics();
            Core.app = new MockApplication();
            Core.gl = Core.gl20 = new MockGL20();
            Vars.ui = null;
            testOtherDialogsDoNotInvalidateFocusedEditor();
            testAmbiguousPanelsFallBack();
            testSceneRootIsNeverAScope();
            testLocalGlobalUiScope();
            testGlobalUiGroupsAreNeverScopes();
            testSchematicCandidatesRespectNativeFiltering();
        }finally{
            Core.graphics = originalGraphics;
            Core.app = originalApp;
            Core.gl = originalGl;
            Core.gl20 = originalGl20;
            Core.scene = originalScene;
            Vars.ui = originalUi;
        }
    }

    private static void testOtherDialogsDoNotInvalidateFocusedEditor() throws Exception{
        Fixture fixture = new Fixture();
        Dialog behind = fixture.dialog();
        Dialog editor = fixture.dialog();
        Dialog above = fixture.dialog();

        TextField field = fixture.field();
        Table originalParent = new Table();
        originalParent.add(field);
        Table results = resultsTable();
        editor.cont.add(originalParent).row();
        editor.cont.add(new ScrollPane(results, new ScrollPaneStyle()));

        fixture.scene.root.addChild(behind);
        fixture.scene.root.addChild(editor);
        fixture.scene.root.addChild(above);
        focus(fixture.scene, field);

        ScopeTree.Context context = ScopeTree.capture(field);
        check(context != null && context.isActive(field), "another top-level Dialog disabled the focused editor");
        check(ScopeTree.locate(field, context) != null, "the editor result pane was not located");

        focus(fixture.scene, above);
        check(!context.isActive(field), "focus moving to another Dialog did not invalidate the context");

        focus(fixture.scene, field);
        Table replacementParent = new Table();
        editor.cont.add(replacementParent);
        field.remove();
        replacementParent.add(field);
        check(!context.isActive(field), "reparenting the search field did not invalidate the context");

        field.remove();
        originalParent.add(field);
        ScopeTree.Context closeContext = ScopeTree.capture(field);
        focus(fixture.scene, field);
        editor.remove();
        check(closeContext != null && !closeContext.isActive(field), "closing the Dialog left its context active");
    }

    private static void testAmbiguousPanelsFallBack() throws Exception{
        Fixture fixture = new Fixture();
        Dialog editor = fixture.dialog();
        TextField field = fixture.field();
        Table fieldParent = new Table();
        fieldParent.add(field);

        Table resultBranch = new Table();
        resultBranch.add(new ScrollPane(resultsTable(), new ScrollPaneStyle()));
        resultBranch.add(new ScrollPane(resultsTable(), new ScrollPaneStyle()));
        editor.cont.add(fieldParent);
        editor.cont.add(resultBranch);
        fixture.scene.root.addChild(editor);
        focus(fixture.scene, field);

        ScopeTree.Context context = ScopeTree.capture(field);
        check(context != null && context.isActive(field), "ambiguous fixture context was inactive");
        check(ScopeTree.locate(field, context) == null, "ambiguous result panes did not fall back to vanilla");
    }

    private static void testSceneRootIsNeverAScope() throws Exception{
        Fixture fixture = new Fixture();
        TextField field = fixture.field();
        fixture.scene.root.addChild(field);
        fixture.scene.root.addChild(new ScrollPane(resultsTable(), new ScrollPaneStyle()));
        focus(fixture.scene, field);
        check(ScopeTree.capture(field) == null, "the scene root was accepted as a search scope");
    }

    private static void testLocalGlobalUiScope() throws Exception{
        Fixture fixture = new Fixture();
        UI ui = installUi(fixture);

        Table unrelatedResults = resultsTable();
        Table unrelatedPanel = new Table();
        unrelatedPanel.add(new ScrollPane(unrelatedResults, new ScrollPaneStyle()));
        ui.hudGroup.addChild(unrelatedPanel);

        TextField field = fixture.field();
        Table searchRow = new Table();
        searchRow.add(field);
        Label water = fixture.label("水");
        Label copper = fixture.label("铜");
        Table results = new Table();
        results.add(water);
        results.add(copper);
        Table localPanel = new Table();
        localPanel.add(searchRow).row();
        localPanel.add(new ScrollPane(results, new ScrollPaneStyle()));
        ui.hudGroup.addChild(localPanel);
        focus(fixture.scene, field);

        ScopeTree.Context hudContext = ScopeTree.capture(field);
        check(hudContext != null && hudContext.isActive(field), "a local HUD search scope was rejected");
        check(hudContext.root == localPanel, "the HUD boundary was selected instead of the local panel");
        ScopeTree hudScope = ScopeTree.locate(field, hudContext);
        check(hudScope != null, "the local HUD result pane was not located");
        check(hudScope.primaryTable() == results, "an unrelated HUD sibling pane was selected");
        check(hudScope.primaryTable() != unrelatedResults, "the HUD scope crossed its local panel boundary");
        hudScope.postFilter("shui", new MatchEngine.MatchOptions(true, true, true));
        check(results.getCells().size == 1 && results.getCells().first().get() == water,
            "the local HUD scope did not apply pinyin filtering");

        localPanel.remove();
        ui.menuGroup.addChild(localPanel);
        check(!hudContext.isActive(field), "moving a local scope across global UI boundaries kept the old context active");

        focus(fixture.scene, field);
        ScopeTree.Context menuContext = ScopeTree.capture(field);
        check(menuContext != null && menuContext.isActive(field), "a local menu search scope was rejected");
        check(ScopeTree.locate(field, menuContext) != null, "the local menu result pane was not located");
    }

    private static void testGlobalUiGroupsAreNeverScopes() throws Exception{
        Fixture fixture = new Fixture();
        UI ui = installUi(fixture);
        TextField field = fixture.field();
        ScrollPane pane = new ScrollPane(resultsTable(), new ScrollPaneStyle());

        ui.hudGroup.addChild(field);
        ui.hudGroup.addChild(pane);
        focus(fixture.scene, field);
        check(ScopeTree.capture(field) == null, "hudGroup was accepted as a search scope");

        field.remove();
        pane.remove();
        ui.menuGroup.addChild(field);
        ui.menuGroup.addChild(pane);
        focus(fixture.scene, field);
        check(ScopeTree.capture(field) == null, "menuGroup was accepted as a search scope");
    }

    private static void testSchematicCandidatesRespectNativeFiltering(){
        Schematic included = schematic("目标蓝图");
        Schematic filteredOut = schematic("目标蓝图");
        Table nativeResults = new Table();
        Table card = new Table();
        card.add(new SchematicImage(included));
        nativeResults.add(card);

        Seq<Schematic> candidates = SchematicsAdapter.visibleCandidates(nativeResults);
        check(candidates.size == 1 && candidates.first() == included,
            "schematic candidates did not come from native result cards");
        check(candidates.first() != filteredOut,
            "a schematic removed by native filtering was restored");
        check(MatchEngine.accepts(candidates.first().name(), "mubiao", new MatchEngine.MatchOptions(true, true, true)),
            "the retained schematic did not match its pinyin query");
    }

    private static Schematic schematic(String name){
        StringMap tags = new StringMap();
        tags.put("name", name);
        return new Schematic(new Seq<Schematic.Stile>(), tags, 1, 1);
    }

    private static UI installUi(Fixture fixture){
        UI ui;
        try{
            ui = (UI)unsafe.allocateInstance(UI.class);
        }catch(InstantiationException failure){
            throw new AssertionError(failure);
        }
        ui.menuGroup = new WidgetGroup();
        ui.hudGroup = new WidgetGroup();
        fixture.scene.root.addChild(ui.menuGroup);
        fixture.scene.root.addChild(ui.hudGroup);
        Vars.ui = ui;
        return ui;
    }

    private static Unsafe unsafe(){
        try{
            Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            return (Unsafe)field.get(null);
        }catch(ReflectiveOperationException failure){
            throw new ExceptionInInitializerError(failure);
        }
    }

    private static Table resultsTable(){
        Table results = new Table();
        results.add(new Element());
        return results;
    }

    private static void focus(Scene scene, Element element) throws IllegalAccessException{
        keyboardFocus.set(scene, element);
    }

    private static Field field(Class<?> owner, String name){
        try{
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        }catch(ReflectiveOperationException failure){
            throw new ExceptionInInitializerError(failure);
        }
    }

    private static void check(boolean condition, String message){
        if(!condition) throw new AssertionError(message);
    }

    private static final class Fixture{
        final Scene scene;
        final Font font;
        final DialogStyle dialogStyle;
        final TextFieldStyle fieldStyle;

        Fixture(){
            scene = new Scene();
            Core.scene = scene;

            FontData data = new FontData();
            data.lineHeight = 1f;
            data.down = -1f;
            data.spaceXadvance = 1f;
            font = new Font(data, new TextureRegion(), true);

            dialogStyle = new DialogStyle();
            dialogStyle.titleFont = font;
            dialogStyle.titleFontColor = Color.white;

            fieldStyle = new TextFieldStyle();
            fieldStyle.font = font;
            fieldStyle.fontColor = Color.white;
        }

        Dialog dialog(){
            return new Dialog("", dialogStyle);
        }

        TextField field(){
            return new TextField("", fieldStyle);
        }

        Label label(String text){
            LabelStyle style = new LabelStyle();
            style.font = font;
            style.fontColor = Color.white;
            return new Label(text, style);
        }
    }

    private static final class SchematicImage extends Element{
        private final Schematic schematic;

        SchematicImage(Schematic schematic){
            this.schematic = schematic;
        }
    }
}
