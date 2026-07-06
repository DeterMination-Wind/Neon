package advancedreplace

import arc.Core
import arc.Events
import arc.graphics.Color
import arc.input.KeyCode
import arc.math.geom.Bresenham2
import arc.math.geom.Point2
import arc.scene.event.InputEvent
import arc.scene.event.InputListener
import arc.scene.event.Touchable
import arc.scene.style.Drawable
import arc.scene.style.TextureRegionDrawable
import arc.scene.ui.Label
import arc.scene.ui.Slider
import arc.scene.ui.layout.Table
import arc.struct.IntSeq
import arc.util.Log
import arc.util.Strings
import mindustry.Vars.editor
import mindustry.Vars.ui
import mindustry.content.Blocks
import mindustry.editor.EditorTool
import mindustry.editor.MapView
import mindustry.game.EventType
import mindustry.gen.Icon
import mindustry.gen.Tex
import mindustry.gen.TileOp
import mindustry.gen.TileOpData
import mindustry.mod.Mod
import mindustry.ui.Styles
import mindustry.ui.dialogs.SettingsMenuDialog
import mindustry.world.Block
import mindustry.world.Tile
import mindustry.world.blocks.environment.Floor
import mindustry.world.blocks.environment.OverlayFloor
import java.lang.reflect.Field
import java.lang.reflect.Method
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

class AdvancedReplaceMod : Mod() {
    companion object {
        @JvmField
        var bekBundled = false
    }

    fun bekBuildSettings(table: SettingsMenuDialog.SettingsTable) {
        AdvancedReplaceFeature.buildSettings(table)
    }

    override fun init() {
        AdvancedReplaceFeature.install()
    }
}

private object AdvancedReplaceFeature {
    private const val sameColorWallKey = "advancedreplace-samecolorwall"
    private const val coloredFloorFillKey = "advancedreplace-coloredfloorfill"
    private const val colorWallBrushKey = "advancedreplace-colorwallbrush"
    private const val deltaSettingKey = "advancedreplace-deltae00"

    private var toolAltModesField: Field? = null
    private var sameColorWallMode = -1
    private var coloredFloorFillMode = -1
    private var colorWallBrushMode = -1
    private var attachedView: MapView? = null
    private var captureListener: InputListener? = null
    private var settingsAdded = false
    private var installed = false
    private var spoofingPreviewBlock = false
    private var storedPreviewBlock: Block? = null
    private var setMapViewMouseMethod: Method? = null

    fun install() {
        if (installed) return
        installed = true

        Events.on(EventType.ClientLoadEvent::class.java) {
            patchBundle()
            addSettingsCategory()
            patchToolAltModes(EditorTool.fill, sameColorWallKey, coloredFloorFillKey)
            sameColorWallMode = EditorTool.fill.altModes.indexOf(sameColorWallKey)
            coloredFloorFillMode = EditorTool.fill.altModes.indexOf(coloredFloorFillKey)

            patchToolAltModes(EditorTool.pencil, colorWallBrushKey)
            colorWallBrushMode = EditorTool.pencil.altModes.indexOf(colorWallBrushKey)

            attachCaptureListener()
        }

        Events.run(EventType.Trigger.update) {
            syncPreviewDrawBlock()
            attachCaptureListener()
        }
    }

    private fun patchBundle() {
        val bundle = Core.bundle ?: return
        val props = bundle.properties

        putBundleDefault(bundle, props, "settings.advancedreplace", "Advanced Replace")
        putBundleDefault(bundle, props, "setting.$deltaSettingKey.name", "Colored Floor Match Tolerance")
        putBundleDefault(bundle, props, "setting.$deltaSettingKey.description", "Maximum CIEDE2000 ΔE00 used by custom colored-floor matching.")

        putBundleDefault(bundle, props, "toolmode.$sameColorWallKey", "Fill Same-Color Walls")
        putBundleDefault(bundle, props, "toolmode.$sameColorWallKey.description", "Replace same-color colored floors with colored walls.")
        putBundleDefault(bundle, props, "toolmode.$coloredFloorFillKey", "Colored Floor Fill")
        putBundleDefault(bundle, props, "toolmode.$coloredFloorFillKey.description", "Treat differently colored colored floors as different blocks when filling.")
        putBundleDefault(bundle, props, "toolmode.$colorWallBrushKey", "Colored Floor To Wall Brush")
        putBundleDefault(bundle, props, "toolmode.$colorWallBrushKey.description", "Convert colored floors in the brush area into colored walls while preserving each tile color.")
    }

    private fun putBundleDefault(bundle: arc.util.I18NBundle, props: arc.struct.ObjectMap<String, String>, key: String, value: String) {
        if (!bundle.has(key)) {
            props.put(key, value)
        }
    }

    private fun addSettingsCategory() {
        if (settingsAdded || ui == null || ui.settings == null) return
        settingsAdded = true

        if (!AdvancedReplaceMod.bekBundled) {
            ui.settings.addCategory("@settings.advancedreplace", Icon.map) { table ->
                buildSettings(table)
            }
        }
    }

    fun buildSettings(table: SettingsMenuDialog.SettingsTable) {
        table.pref(DeltaSliderSetting())
    }

    private class DeltaSliderSetting : SettingsMenuDialog.SettingsTable.Setting(deltaSettingKey) {
        override fun add(table: SettingsMenuDialog.SettingsTable) {
            val slider = Slider(0f, 500f, 1f, false)
            slider.setValue(Core.settings.getInt(name, 0).toFloat())

            val value = Label("", Styles.outlineLabel)
            val content = Table()
            content.left()
            content.image(Icon.settingsSmall).size(20f).padRight(8f)
            content.add(title, Styles.outlineLabel).left().growX().minWidth(0f).wrap()
            content.add(value).padLeft(10f).right()
            content.margin(3f, 10f, 3f, 10f)
            content.touchable = Touchable.disabled

            slider.changed {
                val current = slider.value.toInt()
                Core.settings.put(name, current)
                value.setText("ΔE00 ${Strings.fixed(current / 10f, 1)}")
            }
            slider.change()

            val root = table.table(cardBackground()) { row ->
                row.left().margin(6f)
                row.stack(slider, content).growX().height(42f)
            }.width(rowWidth()).left().padTop(6f).get()
            addDesc(root)
            table.row()
        }
    }

    private fun rowWidth(): Float {
        val pref = min(Core.graphics.width - 56f, 980f)
        val content = max(420f, min(Core.graphics.width - 56f, 960f))
        return max(320f, min(pref - 72f, content))
    }

    private fun cardBackground(): Drawable {
        val base = Tex.whiteui ?: Tex.pane
        return if (base is TextureRegionDrawable) base.tint(Color.valueOf("223246")) else base
    }

    private fun patchToolAltModes(tool: EditorTool, vararg keys: String) {
        val current = tool.altModes
        val missing = keys.filter { current.indexOf(it) < 0 }
        if (missing.isEmpty()) return

        if (toolAltModesField == null) {
            toolAltModesField = EditorTool::class.java.getDeclaredField("altModes").also { it.isAccessible = true }
        }

        val updated = Array(current.size + missing.size) { index ->
            if (index < current.size) current[index] else missing[index - current.size]
        }
        toolAltModesField?.set(tool, updated)
    }

    private fun attachCaptureListener() {
        val editorDialog = ui?.editor ?: return
        val view = editorDialog.view ?: return

        if (attachedView === view && captureListener != null) return

        if (attachedView != null && captureListener != null) {
            attachedView?.removeCaptureListener(captureListener)
        }

        val listener = CustomToolCaptureListener(view)
        view.addCaptureListener(listener)
        attachedView = view
        captureListener = listener
    }

    private fun syncPreviewDrawBlock() {
        val current = editor.drawBlock ?: return
        val customBrushActive = attachedView?.tool === EditorTool.pencil && attachedView?.tool?.mode == colorWallBrushMode
        val previewBlock = Blocks.stone

        if (customBrushActive) {
            if (current !== previewBlock) {
                storedPreviewBlock = current
                editor.drawBlock = previewBlock
            }
            spoofingPreviewBlock = true
        } else if (spoofingPreviewBlock) {
            if (editor.drawBlock === previewBlock && storedPreviewBlock != null) {
                editor.drawBlock = storedPreviewBlock
            }
            storedPreviewBlock = null
            spoofingPreviewBlock = false
        }
    }

    private class CustomToolCaptureListener(private val view: MapView) : InputListener() {
        private var customPencilDragging = false
        private var lastTileX = 0
        private var lastTileY = 0

        override fun touchDown(event: InputEvent?, x: Float, y: Float, pointer: Int, button: KeyCode): Boolean {
            if (pointer != 0 || button != KeyCode.mouseLeft) return false

            val tool = view.tool
            val mode = tool.mode
            val point = view.project(x, y)

            if (tool === EditorTool.fill && isCustomFillMode(mode)) {
                val handled = handleFillMode(point.x, point.y, mode)
                if (handled && editor.ops() > 0) {
                    ui.editor.resetSaved()
                    editor.flushOp()
                }
                event?.stop()
                return true
            }

            if (tool === EditorTool.pencil && mode == colorWallBrushMode) {
                updatePreviewCursor(view, x, y)
                customPencilDragging = true
                lastTileX = point.x
                lastTileY = point.y
                if (runColorWallBrushAt(point.x, point.y)) {
                    ui.editor.resetSaved()
                }
                event?.stop()
                return true
            }

            return false
        }

        override fun touchDragged(event: InputEvent?, x: Float, y: Float, pointer: Int) {
            if (!customPencilDragging || view.tool !== EditorTool.pencil || view.tool.mode != colorWallBrushMode) return

            updatePreviewCursor(view, x, y)
            val point = view.project(x, y)
            if (point.x == lastTileX && point.y == lastTileY) {
                event?.stop()
                return
            }

            Bresenham2.line(lastTileX, lastTileY, point.x, point.y) { cx, cy ->
                runColorWallBrushAt(cx, cy)
            }
            lastTileX = point.x
            lastTileY = point.y
            event?.stop()
        }

        override fun touchUp(event: InputEvent?, x: Float, y: Float, pointer: Int, button: KeyCode) {
            if (!customPencilDragging || button != KeyCode.mouseLeft) return

            customPencilDragging = false
            if (editor.ops() > 0) {
                editor.flushOp()
            }
            event?.stop()
        }
    }

    private fun isCustomFillMode(mode: Int): Boolean {
        return mode == sameColorWallMode || mode == coloredFloorFillMode
    }

    private fun handleFillMode(x: Int, y: Int, mode: Int): Boolean {
        if (!inBounds(x, y)) return false
        return try {
            when (mode) {
                sameColorWallMode -> runSameColorWallFill(x, y)
                coloredFloorFillMode -> runColoredFloorFill(x, y)
                else -> false
            }
        } catch (error: Throwable) {
            Log.err("[AdvancedReplace] Failed to handle custom fill mode.", error)
            false
        }
    }

    private fun runSameColorWallFill(x: Int, y: Int): Boolean {
        val start = editor.tile(x, y) ?: return false
        if (start.floor() !== Blocks.coloredFloor || start.block() != Blocks.air) return false

        val sourceColor = start.extraData
        floodFill(x, y, { tile ->
            tile.floor() === Blocks.coloredFloor && tile.block() == Blocks.air && colorMatches(sourceColor, tile.extraData)
        }) { tile ->
            setBlockWithConfig(tile, Blocks.coloredWall, tile.extraData)
        }
        return true
    }

    private fun runColoredFloorFill(x: Int, y: Int): Boolean {
        val start = editor.tile(x, y) ?: return false
        val drawBlock = editor.drawBlock ?: return false

        if (drawBlock.isMultiblock()) {
            editor.drawBlocks(x, y)
            return true
        }

        if (drawBlock.isOverlay()) {
            val dest = start.overlay()
            if (dest == drawBlock) return true

            floodFill(x, y, { tile ->
                tile.overlay() == dest && (tile.floor().hasSurface() || !tile.floor().needsSurface)
            }) { tile ->
                tile.setOverlay(drawBlock)
            }
            return true
        }

        if (drawBlock.isFloor()) {
            val dest = start.floor()
            val desiredColor = if (drawBlock === Blocks.coloredFloor) (drawBlock.lastConfig as? Int ?: start.extraData) else null
            if (dest == drawBlock && (dest !== Blocks.coloredFloor || desiredColor == start.extraData)) return true

            val sourceColor = if (dest === Blocks.coloredFloor) start.extraData else null
            floodFill(x, y, { tile ->
                tile.floor() == dest && (sourceColor == null || colorMatches(sourceColor, tile.extraData))
            }) { tile ->
                setFloorWithConfig(tile, drawBlock, desiredColor)
            }
            return true
        }

        val dest = start.block()
        if (dest == drawBlock) return true

        floodFill(x, y, { tile -> tile.block() == dest }) { tile ->
            setBlockWithConfig(tile, drawBlock, drawBlock.lastConfig as? Int)
        }
        return true
    }

    private fun runColorWallBrushAt(x: Int, y: Int): Boolean {
        if (!inBounds(x, y)) return false

        var changed = false
        editor.drawCircle(x, y) { tile ->
            if (tile.floor() === Blocks.coloredFloor && tile.block() == Blocks.air) {
                val before = editor.ops()
                setBlockWithConfig(tile, Blocks.coloredWall, tile.extraData)
                if (editor.ops() != before) {
                    changed = true
                }
            }
        }
        return changed
    }

    private fun updatePreviewCursor(view: MapView, x: Float, y: Float) {
        try{
            if (setMapViewMouseMethod == null) {
                setMapViewMouseMethod = MapView::class.java.getDeclaredMethod("mouseMoved", Float::class.javaPrimitiveType, Float::class.javaPrimitiveType).also {
                    it.isAccessible = true
                }
            }
            setMapViewMouseMethod?.invoke(view, x, y)
        } catch (_: Throwable) {
            // fallback: directly update fields if method resolution fails
            try {
                val mouseXField = MapView::class.java.getDeclaredField("mousex").also { it.isAccessible = true }
                val mouseYField = MapView::class.java.getDeclaredField("mousey").also { it.isAccessible = true }
                mouseXField.setFloat(view, x)
                mouseYField.setFloat(view, y)
            } catch (_: Throwable) {
            }
        }
    }

    private fun setFloorWithConfig(tile: Tile, block: Block, config: Int?) {
        if (!block.isFloor()) return

        val oldData = captureData(tile, block)
        tile.setFloor(block as Floor)
        if (tile.overlay() !is OverlayFloor && !block.asFloor().supportsOverlay) {
            tile.setOverlay(Blocks.air)
        }

        if (!block.synthetic() && block.saveConfig) {
            block.placeEnded(tile, null, editor.rotation, config ?: block.lastConfig)
        }

        cleanupNoopData(tile, oldData)
    }

    private fun setBlockWithConfig(tile: Tile, block: Block, config: Int?) {
        val oldData = captureData(tile, block)
        tile.setBlock(block, editor.drawTeam, editor.rotation)

        if (!block.synthetic() && block.saveConfig) {
            block.placeEnded(tile, null, editor.rotation, config ?: block.lastConfig)
        }

        cleanupNoopData(tile, oldData)
    }

    private fun captureData(tile: Tile, block: Block): CapturedTileData? {
        if (!block.saveData && !tile.shouldSaveData()) return null

        editor.addTileOp(TileOp.get(tile.x.toInt(), tile.y.toInt(), 5, TileOpData.get(tile.data, tile.floorData, tile.overlayData)))
        editor.addTileOp(TileOp.get(tile.x.toInt(), tile.y.toInt(), 6, tile.extraData))
        return CapturedTileData(
            TileOpData.get(tile.data, tile.floorData, tile.overlayData),
            tile.extraData,
            beforeOpCount = editor.ops()
        )
    }

    private fun cleanupNoopData(tile: Tile, captured: CapturedTileData?) {
        if (captured == null) return
        if (editor.ops() != captured.beforeOpCount) return

        val currentData = TileOpData.get(tile.data, tile.floorData, tile.overlayData)
        if (captured.data == currentData && captured.extraData == tile.extraData) {
            editor.removeLastOps(2)
        }
    }

    private fun floodFill(startX: Int, startY: Int, tester: (Tile) -> Boolean, setter: (Tile) -> Unit) {
        val width = editor.width()
        val height = editor.height()
        val stack = IntSeq()
        stack.add(Point2.pack(startX, startY))

        while (stack.size > 0 && stack.size < width * height) {
            val packed = stack.pop()
            val y = Point2.y(packed).toInt()
            var scanX = Point2.x(packed).toInt()

            while (scanX >= 0) {
                val tile = editor.tile(scanX, y) ?: break
                if (!tester(tile)) break
                scanX--
            }
            scanX++

            var spanAbove = false
            var spanBelow = false
            while (scanX < width) {
                val tile = editor.tile(scanX, y) ?: break
                if (!tester(tile)) break

                setter(tile)

                if (!spanAbove && y > 0) {
                    val above = editor.tile(scanX, y - 1)
                    if (above != null && tester(above)) {
                        stack.add(Point2.pack(scanX, y - 1))
                        spanAbove = true
                    }
                } else if (spanAbove) {
                    val above = editor.tile(scanX, y - 1)
                    if (above == null || !tester(above)) {
                        spanAbove = false
                    }
                }

                if (!spanBelow && y < height - 1) {
                    val below = editor.tile(scanX, y + 1)
                    if (below != null && tester(below)) {
                        stack.add(Point2.pack(scanX, y + 1))
                        spanBelow = true
                    }
                } else if (spanBelow && y < height - 1) {
                    val below = editor.tile(scanX, y + 1)
                    if (below == null || !tester(below)) {
                        spanBelow = false
                    }
                }

                scanX++
            }
        }
    }

    private fun colorMatches(source: Int, other: Int): Boolean {
        val threshold = colorToleranceDeltaE00()
        if (threshold <= 0.0) {
            return rgbPacked(source) == rgbPacked(other)
        }
        return cieDe2000(source, other) <= threshold
    }

    private fun colorToleranceDeltaE00(): Double {
        return Core.settings.getInt(deltaSettingKey, 0) / 10.0
    }

    private fun rgbPacked(color: Int): Int {
        return color ushr 8
    }

    private fun cieDe2000(colorA: Int, colorB: Int): Double {
        val lab1 = rgbToLab(colorA)
        val lab2 = rgbToLab(colorB)

        val l1 = lab1[0]
        val a1 = lab1[1]
        val b1 = lab1[2]
        val l2 = lab2[0]
        val a2 = lab2[1]
        val b2 = lab2[2]

        val c1 = sqrt(a1 * a1 + b1 * b1)
        val c2 = sqrt(a2 * a2 + b2 * b2)
        val cBar = (c1 + c2) / 2.0
        val cBar7 = cBar.pow(7.0)
        val g = 0.5 * (1.0 - sqrt(cBar7 / (cBar7 + 25.0.pow(7.0))))

        val a1Prime = (1.0 + g) * a1
        val a2Prime = (1.0 + g) * a2
        val c1Prime = sqrt(a1Prime * a1Prime + b1 * b1)
        val c2Prime = sqrt(a2Prime * a2Prime + b2 * b2)
        val h1Prime = hueDegrees(b1, a1Prime)
        val h2Prime = hueDegrees(b2, a2Prime)

        val deltaLPrime = l2 - l1
        val deltaCPrime = c2Prime - c1Prime

        val deltahPrime = when {
            c1Prime == 0.0 || c2Prime == 0.0 -> 0.0
            abs(h2Prime - h1Prime) <= 180.0 -> h2Prime - h1Prime
            h2Prime <= h1Prime -> h2Prime - h1Prime + 360.0
            else -> h2Prime - h1Prime - 360.0
        }

        val deltaHPrime = 2.0 * sqrt(c1Prime * c2Prime) * sin(Math.toRadians(deltahPrime / 2.0))
        val lBarPrime = (l1 + l2) / 2.0
        val cBarPrime = (c1Prime + c2Prime) / 2.0

        val hBarPrime = when {
            c1Prime == 0.0 || c2Prime == 0.0 -> h1Prime + h2Prime
            abs(h1Prime - h2Prime) <= 180.0 -> (h1Prime + h2Prime) / 2.0
            h1Prime + h2Prime < 360.0 -> (h1Prime + h2Prime + 360.0) / 2.0
            else -> (h1Prime + h2Prime - 360.0) / 2.0
        }

        val t = 1.0 -
            0.17 * cos(Math.toRadians(hBarPrime - 30.0)) +
            0.24 * cos(Math.toRadians(2.0 * hBarPrime)) +
            0.32 * cos(Math.toRadians(3.0 * hBarPrime + 6.0)) -
            0.20 * cos(Math.toRadians(4.0 * hBarPrime - 63.0))

        val deltaTheta = 30.0 * exp(-((hBarPrime - 275.0) / 25.0).pow(2.0))
        val rc = 2.0 * sqrt(cBarPrime.pow(7.0) / (cBarPrime.pow(7.0) + 25.0.pow(7.0)))
        val sl = 1.0 + (0.015 * (lBarPrime - 50.0).pow(2.0)) / sqrt(20.0 + (lBarPrime - 50.0).pow(2.0))
        val sc = 1.0 + 0.045 * cBarPrime
        val sh = 1.0 + 0.015 * cBarPrime * t
        val rt = -sin(Math.toRadians(2.0 * deltaTheta)) * rc

        val termL = deltaLPrime / sl
        val termC = deltaCPrime / sc
        val termH = deltaHPrime / sh

        return sqrt(termL * termL + termC * termC + termH * termH + rt * termC * termH)
    }

    private fun rgbToLab(color: Int): DoubleArray {
        val r = channelToLinear((color ushr 24) and 0xff)
        val g = channelToLinear((color ushr 16) and 0xff)
        val b = channelToLinear((color ushr 8) and 0xff)

        val x = (0.4124564 * r + 0.3575761 * g + 0.1804375 * b) / 0.95047
        val y = (0.2126729 * r + 0.7151522 * g + 0.0721750 * b) / 1.00000
        val z = (0.0193339 * r + 0.1191920 * g + 0.9503041 * b) / 1.08883

        val fx = xyzPivot(x)
        val fy = xyzPivot(y)
        val fz = xyzPivot(z)

        val l = max(0.0, 116.0 * fy - 16.0)
        val a = 500.0 * (fx - fy)
        val bb = 200.0 * (fy - fz)
        return doubleArrayOf(l, a, bb)
    }

    private fun channelToLinear(value: Int): Double {
        val srgb = value / 255.0
        return if (srgb <= 0.04045) srgb / 12.92 else ((srgb + 0.055) / 1.055).pow(2.4)
    }

    private fun xyzPivot(value: Double): Double {
        return if (value > 216.0 / 24389.0) value.pow(1.0 / 3.0) else (24389.0 / 27.0 * value + 16.0) / 116.0
    }

    private fun hueDegrees(b: Double, a: Double): Double {
        if (a == 0.0 && b == 0.0) return 0.0
        val deg = Math.toDegrees(atan2(b, a))
        return if (deg < 0.0) deg + 360.0 else deg
    }

    private fun inBounds(x: Int, y: Int): Boolean {
        return x >= 0 && y >= 0 && x < editor.width() && y < editor.height()
    }

    private data class CapturedTileData(
        val data: Int,
        val extraData: Int,
        val beforeOpCount: Int
    )
}
