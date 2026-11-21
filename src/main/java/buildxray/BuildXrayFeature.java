package buildxray;

import arc.Core;
import arc.Events;
import arc.files.Fi;
import arc.graphics.Blending;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.gl.FrameBuffer;
import arc.graphics.gl.Shader;
import arc.input.KeyBind;
import arc.input.KeyCode;
import arc.math.Interp;
import arc.math.Mathf;
import arc.math.geom.QuadTree;
import arc.math.geom.Rect;
import arc.math.geom.Vec2;
import arc.struct.Seq;
import arc.util.Log;
import mindustry.content.Blocks;
import mindustry.core.World;
import mindustry.entities.units.BuildPlan;
import mindustry.game.EventType;
import mindustry.gen.Building;
import mindustry.gen.Unit;
import mindustry.graphics.Layer;
import mindustry.input.Binding;
import mindustry.input.DesktopInput;
import mindustry.input.InputHandler;
import mindustry.input.MobileInput;
import mindustry.input.Placement;
import mindustry.ui.dialogs.SettingsMenuDialog;
import mindustry.ui.fragments.BlockConfigFragment;
import mindustry.world.blocks.logic.LogicBlock;

import java.lang.reflect.Field;

import static arc.graphics.g2d.Draw.*;
import static mindustry.Vars.*;

/**
 * Build X-ray runtime: while the player is building, breaking or planning, what the
 * placement preview sits behind fades out, so the target area stays readable.
 *
 * Ported to Java from the JavaScript mod "build-xray" by minRi2 (Miner). The original
 * scripts are kept under {@code original/scripts/} so the port can be audited against
 * them; this class mirrors the v1.5 script structure:
 * <ol>
 *     <li>capture the world drawn inside a few layer windows into {@code screenBuffer},</li>
 *     <li>re-blit that capture through a shader that computes the mask per pixel from the
 *     camera transform and two shape uniforms, which makes the captured content transparent
 *     exactly where the mask is.</li>
 * </ol>
 * v1.5 replaced the v1.3 two-buffer mask (a {@code transBuffer} painted with sprites plus an
 * alpha-copy shader) with this analytic single-buffer shader mask. The captured windows are
 * the ones the original used: the plasma bore beam, the ground/naval units (new in v1.5), the
 * legged units, the darkness pass that carries unit and wall shadows, and the flying-unit layer
 * (which also carries bullets and effects). Buildings and floors are never captured - the
 * build preview must stay readable.
 *
 * v1.2 of the original added the fade in/out and with it a detail: while the effect fades
 * out there is no live input to rebuild the mask from, so the last mask geometry is frozen
 * and replayed until the fade is over. Without that, a selection rectangle would pop away
 * the moment the mouse is released.
 *
 * v1.3 added the cursor peek (hold the peek key, Alt by default, to x-ray only the spot
 * around the pointer) and the darkness pass. v1.4 added the plan-area mask (the union of
 * all build plans inside the player unit's build range). v1.5 added the logic-config area
 * (a circle around the logic block being configured), the ground/naval unit window and the
 * two extra shape slots that replace the old sprite mask.
 */
public class BuildXrayFeature{
    /** Master switch; Neon renders it in its unified settings page, the standalone mod in its own category. */
    public static final String keyEnabled = "bx-enabled";
    /** Mask transparency in percent (how much of the captured content is removed). */
    public static final String keyTransparent = "bx-transparent";
    /** Radius of the always-transparent spot around the cursor, in tiles. */
    public static final String keyMouseRadius = "bx-mouse-radius";
    /** How soft the mask edge is (v1.5 {@code maskSoft}), in percent. */
    public static final String keyMaskSoft = "bx-mask-soft";
    /** Width of the gradient band added around rect masks (v1.5 {@code rectExpandDist}), in tiles. */
    public static final String keyRectExpand = "bx-rect-expand";
    /** Time the x-ray takes to fully appear after building starts, in milliseconds. */
    public static final String keyFadeIn = "bx-fade-in";
    /** Time the x-ray takes to fully disappear after building stops, in milliseconds. */
    public static final String keyFadeOut = "bx-fade-out";
    /** X-ray ground and naval units as well (v1.5). */
    public static final String keyGroundUnits = "bx-ground-units";
    /** X-ray the area covered by build plans (v1.4). */
    public static final String keyPlans = "bx-plans";
    /** X-ray around a logic block while its config is open (v1.5). */
    public static final String keyLogic = "bx-logic";

    /**
     * Slider defaults, matching the v1.5 script ({@code transparent = 0.8},
     * {@code mouseMaskRadius = tilesize * 16}, {@code maskSoft = 0.5},
     * {@code rectExpandDist = tilesize * 5}, {@code fadeInTime = 0.15},
     * {@code fadeOutTime = 1.2}).
     *
     * v1.5 changed the cursor slider to 4..32 tiles and treats the value as a RADIUS, not a
     * diameter, which is why the port's default dropped from the v1.3 32 to 16.
     */
    public static final int defaultTransparent = 80;
    public static final int defaultMouseRadius = 16;
    public static final int defaultMaskSoft = 50;
    public static final int defaultRectExpand = 5;
    public static final int defaultFadeIn = 150;
    public static final int defaultFadeOut = 1200;

    /** Edge width in pixels, interpolated between these two by {@code maskSoft} (upstream v1.5). */
    private static final float maskEdgePxMin = 2f, maskEdgePxMax = 48f;
    /** Selection length limit when the vanilla schematic limit does not apply. */
    private static final int defaultSelectionLimit = 100;

    private static FrameBuffer screenBuffer;
    private static Shader transShader;
    private static boolean shaderUnavailable;
    /** 0..1 fade progress, set from the fade times; smoothed into {@link #transProgress}. */
    private static float fadeProgress;
    private static float transProgress;
    /** Cursor peek keybind (upstream v1.3), registered in {@link #init()}. */
    private static KeyBind peekKey;

    // Shader inputs copied from Core.settings on every frame (upstream reads them in Trigger.update).
    private static float transparent, maskSoft, rectExpandDist;

    /**
     * Mask shapes, one per slot like the upstream {@code areaMask.shape} / {@code circleMask.shape}.
     * They double as the frozen geometry: while the fade-out runs no live shape is written, so the
     * last one stays in place and is re-blitted with a decaying {@code u_strength} (v1.2).
     */
    private static final Shape areaShape = new Shape(), circleShape = new Shape();
    private static final Shape liveShape = new Shape();
    /** Frame id the shape was last written on, {@code -1} meaning "nothing frozen yet". */
    private static long areaMaskFrame = -1L, circleMaskFrame = -1L;

    // Plan-area mask state (upstream plan-area.js, v1.4).
    private static Field planTreeField;
    private static boolean planTreeResolved;
    private static final Rect planArea = new Rect();
    private static final Rect planRect = new Rect();
    private static final Seq<BuildPlan> planList = new Seq<>();
    private static boolean planAreaPresent;
    private static float planMinX, planMinY, planMaxX, planMaxY;

    // Logic-config area state (upstream v1.5 getLogicSelectArea()).
    private static boolean logicAreaPresent;
    private static float logicX, logicY, logicRadius;

    /**
     * v1.5 fragment shader, ported line by line from {@code original/scripts/main.js}. The mask is
     * computed analytically in world space instead of being pre-painted into a buffer: the fragment
     * shader rebuilds the world position from the camera transform and evaluates at most two shapes.
     */
    private static final String fragmentShader =
        // World coordinates are large; GLES mediump is not precise enough (upstream comment).
        "#define HIGHP\n" +
        "uniform sampler2D u_texture;\n" +
        "uniform vec2 u_camPos;\n" +
        "uniform vec2 u_camSize;\n" +
        "uniform float u_strength;\n" +
        "uniform float u_bandScl;\n" +
        "uniform float u_edge;\n" +
        "uniform float u_rectExpand;\n" +
        "uniform vec4 u_shapeA;\n" +
        "uniform vec4 u_shapeB;\n" +
        "uniform vec2 u_shapeType;\n" +
        "\n" +
        "varying vec2 v_texCoords;\n" +
        "\n" +
        "float soft(float d, float band){\n" +
        "    return 1.0 - smoothstep(0.0, band, d);\n" +
        "}\n" +
        "\n" +
        "float rectMask(vec2 p, vec4 s){\n" +
        "    vec2 q = abs(p - s.xy) - s.zw;\n" +
        "    return soft(length(max(q, 0.0)), max(u_edge, min(s.z, s.w) * u_bandScl) + u_rectExpand);\n" +
        "}\n" +
        "\n" +
        "float circleMask(vec2 p, vec4 s){\n" +
        "    return soft(length(p - s.xy) - s.z, max(u_edge, s.z * u_bandScl));\n" +
        "}\n" +
        "\n" +
        "float shapeMask(vec2 p, vec4 s, float type){\n" +
        "    if(type > 1.5) return circleMask(p, s);\n" +
        "    if(type > 0.5) return rectMask(p, s);\n" +
        "    return 0.0;\n" +
        "}\n" +
        "\n" +
        "void main(){\n" +
        // v_texCoords always spans 0..1 over the current viewport; gl_FragCoord would follow
        // the pixelate resolution instead (upstream comment).
        "    vec2 ndc = v_texCoords * 2.0 - 1.0;\n" +
        "    vec2 world = u_camPos + ndc * u_camSize * 0.5;\n" +
        "\n" +
        "    float mask = max(shapeMask(world, u_shapeA, u_shapeType.x), shapeMask(world, u_shapeB, u_shapeType.y));\n" +
        "\n" +
        "    vec4 color = texture2D(u_texture, v_texCoords);\n" +
        "    gl_FragColor = vec4(color.rgb, color.a * (1.0 - mask * u_strength));\n" +
        "}\n";

    public static void init(){
        // Kept on the upstream name and category so the display key stays the upstream one
        // (keybind.build-xray.peek.name); only the settings keys use the Neon bx-* naming.
        peekKey = KeyBind.add("build-xray.peek", KeyCode.altLeft, "build-xray");
        Events.run(EventType.Trigger.draw, BuildXrayFeature::draw);
    }

    public static void buildSettings(SettingsMenuDialog.SettingsTable table){
        table.checkPref(keyEnabled, true);
        table.sliderPref(keyTransparent, defaultTransparent, 0, 100, 5, value -> value + "%");
        table.sliderPref(keyMaskSoft, defaultMaskSoft, 0, 100, 5, value -> value + "%");
        // v1.5: the value is a radius (4..32 tiles) and formats through the shared "setting.tile".
        table.sliderPref(keyMouseRadius, defaultMouseRadius, 4, 32, 4, value -> Core.bundle.format("setting.tile", value));
        table.sliderPref(keyRectExpand, defaultRectExpand, 0, 15, 1, value -> Core.bundle.format("setting.tile", value));
        table.sliderPref(keyFadeIn, defaultFadeIn, 0, 1000, 50, value -> Core.bundle.format("setting.milliseconds", value));
        table.sliderPref(keyFadeOut, defaultFadeOut, 0, 3000, 100, value -> Core.bundle.format("setting.milliseconds", value));
        table.checkPref(keyGroundUnits, true);
        table.checkPref(keyPlans, true);
        table.checkPref(keyLogic, true);
    }

    private static void draw(){
        if(!Core.settings.getBool(keyEnabled, true)){
            // The Neon module switch can flip at any time; drop the fade state so a stale
            // frozen shape cannot reappear when the module is turned back on.
            fadeProgress = 0f;
            areaShape.reset();
            circleShape.reset();
            areaMaskFrame = circleMaskFrame = -1L;
            return;
        }
        // Both are rebuilt per session; there is no world to fade before the first one exists.
        if(control == null || control.input == null) return;

        InputHandler input = control.input;
        boolean desktop = input instanceof DesktopInput;
        DesktopInput desktopInput = desktop ? (DesktopInput)input : null;
        MobileInput mobileInput = desktop ? null : (MobileInput)input;
        int schemX = desktop ? desktopInput.schemX : mobileInput.lineStartX;
        int schemY = desktop ? desktopInput.schemY : mobileInput.lineStartY;

        // Dragging a schematic selection box is planning too, on desktop only while the
        // schematic key is held; mobile keeps the same intent in schematicMode.
        boolean schematicSelecting = desktop
            ? Core.input.keyDown(Binding.schematicSelect) && desktopInput.schemX != -1 && desktopInput.schemY != -1 && !Core.scene.hasKeyboard()
            : mobileInput.schematicMode;

        boolean breaking = input.isBreaking();
        boolean rebuildSelecting = input.isRebuildSelecting();
        boolean placingOrDropping = input.isPlacing() || input.isDroppingItem();
        // Same keyboard-focus guard as the schematic branch: a text field owning the keyboard
        // must swallow the peek key as well.
        boolean peeking = peekKey != null && Core.input.keyDown(peekKey) && !Core.scene.hasKeyboard();

        // v1.4 plan area and v1.5 logic-config area; both only contribute while their setting is on.
        logicAreaPresent = Core.settings.getBool(keyLogic, true) && updateLogicArea();
        planAreaPresent = Core.settings.getBool(keyPlans, true) && updatePlanArea();

        boolean planning = schematicSelecting || breaking || rebuildSelecting || placingOrDropping
            || peeking || logicAreaPresent || planAreaPresent;

        long frame = Core.graphics.getFrameId();

        // approachDelta advances by "speed * Time.delta"; one step per 60 FPS frame means the
        // fade takes the configured time (the script ran the same frame-based approximation).
        float fadeInTime = Core.settings.getInt(keyFadeIn, defaultFadeIn) / 1000f;
        float fadeOutTime = Core.settings.getInt(keyFadeOut, defaultFadeOut) / 1000f;
        fadeProgress = Mathf.approachDelta(fadeProgress, Mathf.num(planning), 1f / 60f / (planning ? fadeInTime : fadeOutTime));

        // Fully faded out: there is nothing to draw, drop the frozen shapes (the original
        // clears them here as well).
        if(Mathf.zero(fadeProgress)){
            transProgress = 0f;
            areaShape.reset();
            circleShape.reset();
            areaMaskFrame = circleMaskFrame = -1L;
            return;
        }

        transProgress = Interp.smoother.apply(fadeProgress);

        transparent = Core.settings.getInt(keyTransparent, defaultTransparent) / 100f;
        // Nothing to fade at 0%: skip the full-screen framebuffer passes entirely.
        if(transparent <= 0f) return;

        maskSoft = Core.settings.getInt(keyMaskSoft, defaultMaskSoft) / 100f;
        rectExpandDist = Core.settings.getInt(keyRectExpand, defaultRectExpand) * tilesize;

        if(!ensureShader()) return;

        // The capture is full-screen, so the buffer follows the current resolution.
        screenBuffer.resize(Core.graphics.getWidth(), Core.graphics.getHeight());

        float mouseMaskRadius = Core.settings.getInt(keyMouseRadius, defaultMouseRadius) * tilesize;

        // Area mask, in upstream priority order: breaking > schematic > rebuild > logic > plans.
        liveShape.reset();
        if(breaking){
            int maxLength = desktop && Core.input.keyDown(Binding.schematicSelect)
                && desktopInput.schemX != -1 && desktopInput.schemY != -1 ? maxSchematicSize : defaultSelectionLimit;
            areaRectShape(desktop ? desktopInput.selectX : mobileInput.lineStartX,
                desktop ? desktopInput.selectY : mobileInput.lineStartY, maxLength);
        }else if(schematicSelecting){
            areaRectShape(schemX, schemY, maxSchematicSize);
        }else if(rebuildSelecting){
            // Rebuild selection has no length limit.
            areaRectShape(schemX, schemY, 0);
        }else if(logicAreaPresent){
            liveShape.setCircle(logicX, logicY, logicRadius);
        }else if(planAreaPresent){
            liveShape.setRect(planArea.x, planArea.y, planArea.x + planArea.width, planArea.y + planArea.height);
        }

        if(liveShape.type != Shape.none){
            areaShape.set(liveShape);
            areaMaskFrame = frame;
        }

        if(placingOrDropping || peeking){
            Vec2 mouse = Core.input.mouseWorld();
            circleShape.setCircle(mouse.x, mouse.y, mouseMaskRadius);
            circleMaskFrame = frame;
        }

        // No live shape this frame means the action just ended and only the strength is decaying:
        // keep the frozen geometry so it can fade out. With a live shape, drop the other frozen
        // geometry so it cannot reappear later (v1.2).
        if(areaMaskFrame == frame || circleMaskFrame == frame){
            if(areaMaskFrame != frame) areaShape.reset();
            if(circleMaskFrame != frame) circleShape.reset();
        }

        // Plasma bore (impact drill) beam.
        applyTrans(Layer.power - 1.001f, Layer.power - 0.999f);
        // Ground and naval units (upstream v1.5, optional).
        if(Core.settings.getBool(keyGroundUnits, true)) applyTrans(Layer.groundUnit - 2.001f, Layer.groundUnit + 2.001f);
        // Legged units.
        applyTrans(Layer.legUnit - 2.001f, Layer.legUnit + 2.001f);
        // Unit and wall shadows (upstream v1.3).
        applyTrans(Layer.darkness - 0.001f, Layer.darkness + 0.001f);
        // Flying units, plus the bullets and effects sharing their window.
        applyTrans(Layer.flyingUnitLow - 2.001f, Layer.flyingUnit + 2.001f);
    }

    private static void applyTrans(float zStart, float zEnd){
        // Draw.draw() flushes the sprite batch, so the capture below covers exactly what the
        // renderer draws between the two layer markers. Qualified because this class has its
        // own draw() (the Trigger.draw entry point), which would shadow the static import.
        Draw.draw(zStart, () -> screenBuffer.begin(Color.clear));

        Draw.draw(zEnd, () -> {
            screenBuffer.end();
            // The last queued draw request can leave a non-normal blend mode behind and the
            // vanilla batch does not restore it; without this reset the blit inherits additive
            // blending and glowing content smears (upstream v1.1 fix).
            Blending.normal.apply();
            // v1.5: a single buffer; the shader computes the mask itself, so no second texture.
            screenBuffer.blit(transShader);
        });
    }

    /** Computes the selection rectangle into {@link #liveShape} (upstream v1.5 areaRectShape). */
    private static void areaRectShape(int startX, int startY, int maxLength){
        if(startX < 0 || startY < 0) return;

        Vec2 mouse = Core.input.mouseWorld();
        Placement.NormalizeDrawResult result = Placement.normalizeDrawArea(Blocks.air,
            startX, startY, World.toTile(mouse.x), World.toTile(mouse.y), false, maxLength, 1f);

        liveShape.setRect(result.x, result.y, result.x2, result.y2);
    }

    /**
     * v1.4 plan area: the bounding rectangle of every build plan (player plan tree, plus the
     * line and selection plan lists) inside the player unit's {@code type.buildRange}.
     */
    private static boolean updatePlanArea(){
        if(!resolvePlanTreeField()) return false;
        if(player == null) return false;

        Unit unit = player.unit();
        if(unit == null) return false;

        float range = unit.type.buildRange;

        planMinX = planMinY = Float.POSITIVE_INFINITY;
        planMaxX = planMaxY = Float.NEGATIVE_INFINITY;

        try{
            // Raw call into the reflected tree: v160 keeps playerPlanTree private.
            @SuppressWarnings("unchecked")
            QuadTree<BuildPlan> tree = (QuadTree<BuildPlan>)planTreeField.get(control.input);
            if(tree != null){
                planList.clear();
                tree.getObjects(planList);
                for(BuildPlan plan : planList) includePlan(plan, unit, range);
            }
        }catch(Throwable t){
            // A broken read must not take the whole effect down; the other masks still work.
            // The field is dropped so this logs and disables the branch once instead of per frame.
            Log.err("BuildXray: failed to read InputHandler.playerPlanTree.", t);
            planTreeField = null;
            return false;
        }

        for(BuildPlan plan : control.input.linePlans) includePlan(plan, unit, range);
        for(BuildPlan plan : control.input.selectPlans) includePlan(plan, unit, range);

        if(planMinX == Float.POSITIVE_INFINITY) return false;

        planArea.set(planMinX, planMinY, planMaxX - planMinX, planMaxY - planMinY);
        return true;
    }

    private static void includePlan(BuildPlan plan, Unit unit, float range){
        if(plan == null) return;
        // getX/getY are the draw positions (block centre); plans outside buildRange are ignored.
        if(!Mathf.within(plan.getX(), plan.getY(), unit.x, unit.y, range)) return;

        plan.hitbox(planRect);
        planMinX = Math.min(planMinX, planRect.x);
        planMinY = Math.min(planMinY, planRect.y);
        planMaxX = Math.max(planMaxX, planRect.x + planRect.width);
        planMaxY = Math.max(planMaxY, planRect.y + planRect.height);
    }

    /** Resolves the private plan tree field once; missing on an unsupported version logs once. */
    private static boolean resolvePlanTreeField(){
        if(planTreeResolved) return planTreeField != null;
        planTreeResolved = true;

        try{
            Field field = InputHandler.class.getDeclaredField("playerPlanTree");
            field.setAccessible(true);
            planTreeField = field;
        }catch(Throwable t){
            Log.err("BuildXray: InputHandler.playerPlanTree not found; plan x-ray disabled. " +
                "Check compatibility with this Mindustry version.");
        }

        return planTreeField != null;
    }

    /** v1.5 logic-config area: a circle around the logic block whose config UI is open. */
    private static boolean updateLogicArea(){
        BlockConfigFragment config = control.input.config;
        if(config == null || !config.isShown()) return false;

        Building selected = config.getSelected();
        if(!(selected instanceof LogicBlock.LogicBuild)) return false;

        // Building.x/y are world coordinates in v160; the mask radius is the block's range.
        logicX = selected.x;
        logicY = selected.y;
        logicRadius = ((LogicBlock)selected.block).range;
        return true;
    }

    private static boolean ensureShader(){
        if(shaderUnavailable) return false;
        if(screenBuffer != null) return true;

        String vertex = loadShaderSource("screenspace.vert");
        if(vertex == null){
            shaderUnavailable = true;
            Log.err("BuildXray: shaders/screenspace.vert is missing; build x-ray disabled.");
            return false;
        }

        try{
            // Shader compilation happens in the constructor, so this must stay on the render
            // thread (first draw frame) instead of running in init().
            transShader = new TransShader(vertex, fragmentShader);
            screenBuffer = new FrameBuffer();
            return true;
        }catch(Throwable t){
            // A device whose GLSL version cannot compile the shader should lose the effect,
            // not the whole render loop.
            shaderUnavailable = true;
            Log.err("BuildXray: build x-ray shader unavailable on this device; module disabled.", t);
            return false;
        }
    }

    private static String loadShaderSource(String name){
        // Same lookup order as the original findShaderFi(): game shader tree first, then the
        // flat tree path, then the internal file tree.
        Fi[] candidates = {tree.get("shaders/" + name), tree.get(name), Core.files.internal("shaders/" + name)};
        for(Fi file : candidates){
            if(file != null && file.exists()) return file.readString();
        }
        return null;
    }

    /**
     * Mask shape in the upstream {@code {type, x, y, halfW, halfH}} form: type 0 = unused,
     * 1 = rectangle centred on (x, y), 2 = circle of radius halfW (= halfH).
     */
    private static class Shape{
        static final int none = 0, rect = 1, circle = 2;

        int type;
        float x, y, halfW, halfH;

        void set(Shape other){
            type = other.type;
            x = other.x;
            y = other.y;
            halfW = other.halfW;
            halfH = other.halfH;
        }

        void setRect(float x1, float y1, float x2, float y2){
            type = rect;
            x = (x1 + x2) / 2f;
            y = (y1 + y2) / 2f;
            halfW = (x2 - x1) / 2f;
            halfH = (y2 - y1) / 2f;
        }

        void setCircle(float x, float y, float radius){
            type = circle;
            this.x = x;
            this.y = y;
            halfW = radius;
            halfH = radius;
        }

        void reset(){
            type = none;
            x = y = halfW = halfH = 0f;
        }
    }

    /** Blits the captured world with the analytic mask applied (upstream v1.5 transShader). */
    private static class TransShader extends Shader{
        TransShader(String vertex, String fragment){
            super(vertex, fragment);
        }

        @Override
        public void apply(){
            // Draw.blit() binds this program and the capture texture (unit 0) before calling
            // apply(), so every uniform can be uploaded here (upstream apply() reads the same
            // state: maskSoft, rectExpandDist and both frozen shapes).
            super.apply();

            Shape a = areaShape, b = circleShape;
            float edgePx = maskEdgePxMin + (maskEdgePxMax - maskEdgePxMin) * maskSoft;

            setUniformf("u_camPos", Core.camera.position.x, Core.camera.position.y);
            setUniformf("u_camSize", Core.camera.width, Core.camera.height);
            setUniformf("u_strength", transProgress * transparent);
            setUniformf("u_bandScl", maskSoft);
            // Pixels are converted to world units with the camera height ratio (upstream).
            setUniformf("u_edge", (edgePx * Core.camera.height) / Core.graphics.getHeight());
            setUniformf("u_rectExpand", rectExpandDist);

            setUniformf("u_shapeA", a.x, a.y, a.halfW, a.halfH);
            setUniformf("u_shapeB", b.x, b.y, b.halfW, b.halfH);
            setUniformf("u_shapeType", (float)a.type, (float)b.type);
        }
    }
}
