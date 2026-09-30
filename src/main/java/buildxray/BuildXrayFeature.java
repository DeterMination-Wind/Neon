package buildxray;

import arc.Core;
import arc.Events;
import arc.files.Fi;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.gl.FrameBuffer;
import arc.graphics.gl.Shader;
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
 * script is kept verbatim under {@code original/scripts/main.js} so the port can be
 * audited against it; this class keeps its three-stage structure:
 * <ol>
 *     <li>draw the regions that should become see-through (schematic/selection area and the
 *     cursor spot) into {@code transBuffer} with alpha,</li>
 *     <li>capture the world drawn inside two layer windows into {@code screenBuffer},</li>
 *     <li>re-blit that capture through a shader that subtracts the mask alpha, which makes
 *     the captured content transparent exactly where the mask was drawn.</li>
 * </ol>
 * The captured windows are the ones the original used: legged units, plus the
 * flying-unit layer (which also carries bullets and effects). Buildings and floors are
 * never captured - the build preview must stay readable.
 */
public class BuildXrayFeature{
    /** Master switch; Neon renders it in its unified settings page, the standalone mod in its own category. */
    public static final String keyEnabled = "bx-enabled";
    /** Mask transparency in percent (how much of the captured content is removed). */
    public static final String keyTransparent = "bx-transparent";
    /** Radius of the always-transparent spot around the cursor, in tiles. */
    public static final String keyMouseRadius = "bx-mouse-radius";

    /**
     * Slider defaults. The JavaScript original declared {@code transparent = 0.8} and
     * {@code mouseMaskRadius = tilesize * 32}, but handed those values to
     * {@code sliderPref}, whose default parameter is an int in percent/tiles - Rhino
     * coerced them to 0 and 256, so the effect only appeared after the player moved a
     * slider (and 256 tiles exceeds the 8..64 slider range). The port uses the values
     * the original variables clearly intended.
     */
    public static final int defaultTransparent = 80;
    public static final int defaultMouseRadius = 32;

    /** Fade speed; the original lerped with the same per-frame factor. */
    private static final float lerpSpeed = 0.02f;
    /** Selection masks use a rotated-square sprite, hence sqrt2. */
    private static final float maskScale = Mathf.sqrt2 * 1.5f;
    /** Selection length limit when the vanilla schematic limit does not apply. */
    private static final int defaultSelectionLimit = 100;

    private static FrameBuffer screenBuffer;
    private static FrameBuffer transBuffer;
    private static Shader transShader;
    private static boolean shaderUnavailable;
    private static float transProgress;

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
        table.sliderPref(keyTransparent, defaultTransparent, 0, 100, 10, value -> value + "%");
        table.sliderPref(keyMouseRadius, defaultMouseRadius, 8, 64, 4, value -> Core.bundle.format("bx.mouse-radius.tile", value));
    }

    private static void draw(){
        if(!Core.settings.getBool(keyEnabled, true)) return;
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

        boolean planning = schematicSelecting
            || input.isPlacing()
            || input.isBreaking()
            || input.isDroppingItem()
            || input.isRebuildSelecting();

        transProgress = Mathf.lerp(transProgress, Mathf.num(planning), lerpSpeed);
        if(Mathf.zero(transProgress)) return;

        float transparent = Core.settings.getInt(keyTransparent, defaultTransparent) / 100f;
        // Nothing to fade at 0%: skip the two full-screen framebuffer passes entirely.
        if(transparent <= 0f) return;

        if(!ensureShader()) return;

        // The mask is built in screen space, so both buffers follow the current resolution.
        int width = Core.graphics.getWidth(), height = Core.graphics.getHeight();
        transBuffer.resize(width, height);
        screenBuffer.resize(width, height);

        float mouseMaskRadius = Core.settings.getInt(keyMouseRadius, defaultMouseRadius) * tilesize;

        transBuffer.begin(Color.clear);

        alpha(transProgress * transparent);

        if(input.isBreaking()){
            int maxLength = desktop && Core.input.keyDown(Binding.schematicSelect)
                && desktopInput.schemX != -1 && desktopInput.schemY != -1 ? maxSchematicSize : defaultSelectionLimit;
            drawSelectionArea(desktop ? desktopInput.selectX : mobileInput.lineStartX,
                desktop ? desktopInput.selectY : mobileInput.lineStartY, maxLength);
        }else if(schematicSelecting){
            drawSelectionArea(schemX, schemY, maxSchematicSize);
        }else if(input.isRebuildSelecting()){
            // Rebuild selection has no length limit.
            drawSelectionArea(schemX, schemY, 0);
        }

        if(input.isPlacing() || input.isDroppingItem()){
            Vec2 mouse = Core.input.mouseWorld();
            rect("circle-shadow", mouse.x, mouse.y, mouseMaskRadius, mouseMaskRadius);
        }

        reset();

        transBuffer.end();

        applyTrans(Layer.legUnit - 2.001f, Layer.legUnit + 2.001f);
        applyTrans(Layer.flyingUnitLow - 2.001f, Layer.flyingUnit + 2.001f);
    }

    private static void applyTrans(float zStart, float zEnd){
        // Draw.draw() flushes the sprite batch, so the capture below covers exactly what the
        // renderer draws between the two layer markers. Qualified because this class has its
        // own draw() (the Trigger.draw entry point), which would shadow the static import.
        Draw.draw(zStart, () -> screenBuffer.begin(Color.clear));

        Draw.draw(zEnd, () -> {
            screenBuffer.end();
            // Unit 0 is the capture: Draw.blit() binds it before calling the shader.
            transBuffer.getTexture().bind(1);
            screenBuffer.blit(transShader);
        });
    }

    private static void drawSelectionArea(int startX, int startY, int maxLength){
        if(startX < 0 || startY < 0) return;

        Vec2 mouse = Core.input.mouseWorld();
        Placement.NormalizeDrawResult result = Placement.normalizeDrawArea(Blocks.air,
            startX, startY, World.toTile(mouse.x), World.toTile(mouse.y), false, maxLength, 1f);
        drawAreaMask(result.x, result.y, result.x2, result.y2);
    }

    private static void drawAreaMask(float x, float y, float x2, float y2){
        float centerX = (x + x2) / 2f, centerY = (y + y2) / 2f;
        // circle-shadow is a round sprite; scaling by sqrt2 * 1.5 makes one sprite cover the
        // axis-aligned selection rectangle whichever corner is dragged.
        float width = (x2 - x) * maskScale, height = (y2 - y) * maskScale;
        rect("circle-shadow", centerX, centerY, width, height);
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
