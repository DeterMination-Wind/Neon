package buildxray;

import arc.Core;
import arc.Events;
import arc.files.Fi;
import arc.graphics.Blending;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.gl.FrameBuffer;
import arc.graphics.gl.Shader;
import arc.math.Interp;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.util.Log;
import mindustry.content.Blocks;
import mindustry.core.World;
import mindustry.game.EventType;
import mindustry.graphics.Layer;
import mindustry.input.Binding;
import mindustry.input.DesktopInput;
import mindustry.input.InputHandler;
import mindustry.input.MobileInput;
import mindustry.input.Placement;
import mindustry.ui.dialogs.SettingsMenuDialog;

import static arc.graphics.g2d.Draw.*;
import static mindustry.Vars.*;

/**
 * Build X-ray runtime: while the player is building, breaking or planning, what the
 * placement preview sits behind fades out, so the target area stays readable.
 *
 * Ported to Java from the JavaScript mod "build-xray" by minRi2 (Miner). The original
 * script is kept under {@code original/scripts/main.js} so the port can be audited
 * against it; this class keeps its three-stage structure:
 * <ol>
 *     <li>draw the regions that should become see-through (schematic/selection area and the
 *     cursor spot) into {@code transBuffer} with alpha,</li>
 *     <li>capture the world drawn inside three layer windows into {@code screenBuffer},</li>
 *     <li>re-blit that capture through a shader that subtracts the mask alpha, which makes
 *     the captured content transparent exactly where the mask was drawn.</li>
 * </ol>
 * The captured windows are the ones the original used: the plasma bore beam, the legged
 * units, and the flying-unit layer (which also carries bullets and effects). Buildings and
 * floors are never captured - the build preview must stay readable.
 *
 * v1.2 of the original added the fade in/out and with it a detail: while the effect fades
 * out there is no live input to redraw the mask from, so the last mask geometry is frozen
 * and replayed until the fade is over. Without that, a selection rectangle would pop away
 * the moment the mouse is released.
 */
public class BuildXrayFeature{
    /** Master switch; Neon renders it in its unified settings page, the standalone mod in its own category. */
    public static final String keyEnabled = "bx-enabled";
    /** Mask transparency in percent (how much of the captured content is removed). */
    public static final String keyTransparent = "bx-transparent";
    /** Radius of the always-transparent spot around the cursor, in tiles. */
    public static final String keyMouseRadius = "bx-mouse-radius";
    /** Time the x-ray takes to fully appear after building starts, in milliseconds. */
    public static final String keyFadeIn = "bx-fade-in";
    /** Time the x-ray takes to fully disappear after building stops, in milliseconds. */
    public static final String keyFadeOut = "bx-fade-out";

    /**
     * Slider defaults. v1.0 of the JavaScript original declared {@code transparent = 0.8} and
     * {@code mouseMaskRadius = tilesize * 32}, but handed those values to
     * {@code sliderPref}, whose default parameter is an int in percent/tiles - Rhino
     * coerced them to 0 and 256, so the effect only appeared after the player moved a
     * slider (and 256 tiles exceeds the 8..64 slider range). The port uses the values the
     * original variables clearly intended; v1.2 computes the slider defaults with
     * {@code Math.round()} itself, so the two now agree.
     */
    public static final int defaultTransparent = 80;
    public static final int defaultMouseRadius = 32;
    /** Fade defaults, matching the v1.2 script ({@code fadeInTime = 0.15}, {@code fadeOutTime = 1.2}). */
    public static final int defaultFadeIn = 150;
    public static final int defaultFadeOut = 1200;

    /** Selection masks use a rotated-square sprite, hence sqrt2. */
    private static final float maskScale = Mathf.sqrt2 * 1.5f;
    /** Selection length limit when the vanilla schematic limit does not apply. */
    private static final int defaultSelectionLimit = 100;

    private static FrameBuffer screenBuffer;
    private static FrameBuffer transBuffer;
    private static Shader transShader;
    private static boolean shaderUnavailable;
    /** 0..1 fade progress, set from the fade times; smoothed into {@link #transProgress}. */
    private static float fadeProgress;
    private static float transProgress;

    /**
     * Last mask geometry, replayed while the fade-out runs; the frame id records whether the
     * mask was drawn in the current frame (v1.2). {@code -1} means "nothing frozen yet".
     */
    private static Runnable areaMaskDraw, circleMaskDraw;
    private static long areaMaskFrame = -1L, circleMaskFrame = -1L;

    private static final String fragmentShader =
        "uniform sampler2D u_texture;\n" +
        "uniform sampler2D u_trans_texture;\n" +
        "\n" +
        "varying vec2 v_texCoords;\n" +
        "\n" +
        "void main(){\n" +
        "    vec4 color = texture2D(u_texture, v_texCoords);\n" +
        "    float alpha = texture2D(u_trans_texture, v_texCoords).a;\n" +
        "    color.a *= 1.0 - alpha;\n" +
        "    gl_FragColor = color;\n" +
        "}\n";

    public static void init(){
        Events.run(EventType.Trigger.draw, BuildXrayFeature::draw);
    }

    public static void buildSettings(SettingsMenuDialog.SettingsTable table){
        table.checkPref(keyEnabled, true);
        table.sliderPref(keyTransparent, defaultTransparent, 0, 100, 5, value -> value + "%");
        table.sliderPref(keyMouseRadius, defaultMouseRadius, 8, 64, 4, value -> Core.bundle.format("bx.mouse-radius.tile", value));
        table.sliderPref(keyFadeIn, defaultFadeIn, 0, 1000, 50, value -> Core.bundle.format("setting.milliseconds", value));
        table.sliderPref(keyFadeOut, defaultFadeOut, 0, 3000, 100, value -> Core.bundle.format("setting.milliseconds", value));
    }

    private static void draw(){
        if(!Core.settings.getBool(keyEnabled, true)){
            // The Neon module switch can flip at any time; drop the fade state so a stale
            // frozen mask cannot reappear when the module is turned back on.
            fadeProgress = 0f;
            areaMaskDraw = circleMaskDraw = null;
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
        boolean planning = schematicSelecting || breaking || rebuildSelecting || placingOrDropping;

        long frame = Core.graphics.getFrameId();

        // approachDelta advances by "speed * Time.delta"; one step per 60 FPS frame means the
        // fade takes the configured time (the script ran the same frame-based approximation).
        float fadeInTime = Core.settings.getInt(keyFadeIn, defaultFadeIn) / 1000f;
        float fadeOutTime = Core.settings.getInt(keyFadeOut, defaultFadeOut) / 1000f;
        fadeProgress = Mathf.approachDelta(fadeProgress, Mathf.num(planning), 1f / 60f / (planning ? fadeInTime : fadeOutTime));

        // Fully faded out: there is nothing to draw, drop the frozen masks (the original
        // clears them here as well).
        if(Mathf.zero(fadeProgress)){
            transProgress = 0f;
            areaMaskDraw = circleMaskDraw = null;
            return;
        }

        transProgress = Interp.smoother.apply(fadeProgress);

        float transparent = Core.settings.getInt(keyTransparent, defaultTransparent) / 100f;
        // Nothing to fade at 0%: skip the three full-screen framebuffer passes entirely.
        if(transparent <= 0f) return;

        if(!ensureShader()) return;

        // The mask is built in screen space, so both buffers follow the current resolution.
        int width = Core.graphics.getWidth(), height = Core.graphics.getHeight();
        transBuffer.resize(width, height);
        screenBuffer.resize(width, height);

        float mouseMaskRadius = Core.settings.getInt(keyMouseRadius, defaultMouseRadius) * tilesize;

        transBuffer.begin(Color.clear);

        alpha(transProgress * transparent);

        if(breaking){
            int maxLength = desktop && Core.input.keyDown(Binding.schematicSelect)
                && desktopInput.schemX != -1 && desktopInput.schemY != -1 ? maxSchematicSize : defaultSelectionLimit;
            freezeAreaMask(desktop ? desktopInput.selectX : mobileInput.lineStartX,
                desktop ? desktopInput.selectY : mobileInput.lineStartY, maxLength, frame);
        }else if(schematicSelecting){
            freezeAreaMask(schemX, schemY, maxSchematicSize, frame);
        }else if(rebuildSelecting){
            // Rebuild selection has no length limit.
            freezeAreaMask(schemX, schemY, 0, frame);
        }

        if(placingOrDropping){
            Vec2 mouse = Core.input.mouseWorld();
            float x = mouse.x, y = mouse.y, radius = mouseMaskRadius;
            rect("circle-shadow", x, y, radius, radius);
            circleMaskDraw = () -> rect("circle-shadow", x, y, radius, radius);
            circleMaskFrame = frame;
        }

        // No live mask this frame means the action just ended and only the alpha is decaying:
        // replay the frozen geometry. With a live mask, drop the other frozen geometry so it
        // cannot reappear later (v1.2).
        if(areaMaskFrame == frame || circleMaskFrame == frame){
            if(areaMaskFrame != frame) areaMaskDraw = null;
            if(circleMaskFrame != frame) circleMaskDraw = null;
        }else{
            if(areaMaskDraw != null) areaMaskDraw.run();
            if(circleMaskDraw != null) circleMaskDraw.run();
        }

        reset();

        transBuffer.end();

        // Plasma bore (impact drill) beam.
        applyTrans(Layer.power - 1.001f, Layer.power - 0.999f);
        // Legged units.
        applyTrans(Layer.legUnit - 2.001f, Layer.legUnit + 2.001f);
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
            // Unit 0 is the capture: Draw.blit() binds it before calling the shader.
            transBuffer.getTexture().bind(1);
            screenBuffer.blit(transShader);
        });
    }

    /** Computes the selection rectangle, draws it and freezes the geometry for the fade-out replay. */
    private static void freezeAreaMask(int startX, int startY, int maxLength, long frame){
        if(startX < 0 || startY < 0) return;

        Vec2 mouse = Core.input.mouseWorld();
        Placement.NormalizeDrawResult result = Placement.normalizeDrawArea(Blocks.air,
            startX, startY, World.toTile(mouse.x), World.toTile(mouse.y), false, maxLength, 1f);

        float centerX = (result.x + result.x2) / 2f, centerY = (result.y + result.y2) / 2f;
        // circle-shadow is a round sprite; scaling by sqrt2 * 1.5 makes one sprite cover the
        // axis-aligned selection rectangle whichever corner is dragged.
        float width = (result.x2 - result.x) * maskScale, height = (result.y2 - result.y) * maskScale;

        rect("circle-shadow", centerX, centerY, width, height);

        areaMaskDraw = () -> rect("circle-shadow", centerX, centerY, width, height);
        areaMaskFrame = frame;
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
            transBuffer = new FrameBuffer();
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

    /** Re-blits the captured world with the mask alpha removed. */
    private static class TransShader extends Shader{
        TransShader(String vertex, String fragment){
            super(vertex, fragment);
        }

        @Override
        public void apply(){
            // Draw.blit() binds this program before calling apply(), so the uniform can be set
            // here; applyTrans() bound the mask buffer to texture unit 1.
            super.apply();
            setUniformi("u_trans_texture", 1);
        }
    }
}
