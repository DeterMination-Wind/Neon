package betterterraingen.v2.filters;

import arc.struct.IntSeq;
import arc.util.noise.Simplex;
import betterterraingen.v2.BetterTerrainGenV2Mod;
import betterterraingen.v2.ui.ModFilterOptions;
import betterterraingen.v2.ui.RuinFilterOptions;
import mindustry.content.Blocks;
import mindustry.gen.Iconc;
import mindustry.maps.filters.FilterOption;
import mindustry.maps.filters.GenerateFilter;
import mindustry.world.Block;
import mindustry.world.Tile;
import mindustry.world.blocks.environment.Floor;
import mindustry.world.blocks.environment.OreBlock;

import java.util.ArrayList;
import java.util.List;

import static mindustry.maps.filters.FilterOption.oresFloorsOptional;
import static mindustry.maps.filters.FilterOption.floorsOptional;
import static mindustry.maps.filters.FilterOption.wallsOptional;

/** Generates ordered, configurable ruin shapes around overlay or automatic centers. */
public class RuinGenerateFilter extends GenerateFilter {
    public Block replaceOverlay = Blocks.metalTiles1;
    public CenterMode centerMode = CenterMode.overlay;
    public GenerationMode generationMode = GenerationMode.scatter;
    public StructurePreset structurePreset = StructurePreset.large;
    public Block structureFloor = Blocks.metalFloor;
    public Block structureMainFloor = Blocks.metalTiles1;
    public Block structureWall = Blocks.metalWall2;
    public Block structureAccentWall = Blocks.metalWall1;
    public Block structureDamageFloor = Blocks.metalFloorDamaged;
    public float autoDensity = 25f;
    public float minCenterSpacing = 24f;

    public RuinStep[] steps = RuinStep.defaults();

    public float scl = 40f, threshold = 0.5f, octaves = 3f, falloff = 0.5f, tilt = 0f;
    public float distortScl = 10f, distortMag = 5f;

    private transient IntSeq centers;
    private transient CenterIndex centerIndex;
    private transient TileState[] computed;
    private transient boolean[] floorModified;
    private transient boolean[] blockModified;
    private transient boolean mapComputed;
    private transient int tilesLeft;
    private transient int computedWidth;
    private transient int computedHeight;
    private transient GenerateInput computedInput;

    @Override
    public FilterOption[] options() {
        ensureDefaults();
        ArrayList<FilterOption> options = new ArrayList<>();
        options.add(new ModFilterOptions.BlockOption("replace", () -> replaceOverlay,
            block -> replaceOverlay = block, oresFloorsOptional));
        options.add(new RuinFilterOptions.CenterModeOption(this));
        options.add(new ModFilterOptions.SliderOption("auto-density", () -> autoDensity,
            value -> autoDensity = value, 0f, 100f, 1f));
        options.add(new ModFilterOptions.SliderOption("min-center-spacing", () -> minCenterSpacing,
            value -> minCenterSpacing = value, 0f, 256f, 1f));
        options.add(new RuinFilterOptions.GenerationModeOption(this));

        if (generationMode == GenerationMode.structured) {
            options.add(new RuinFilterOptions.StructurePresetOption(this));
            options.add(new ModFilterOptions.BlockOption("structure-floor", () -> structureFloor,
                block -> structureFloor = block, floorsOptional));
            options.add(new ModFilterOptions.BlockOption("structure-main-floor", () -> structureMainFloor,
                block -> structureMainFloor = block, floorsOptional));
            options.add(new ModFilterOptions.BlockOption("structure-wall", () -> structureWall,
                block -> structureWall = block,
                block -> block == Blocks.air || wallsOptional.get(block) && block.isStatic()));
            options.add(new ModFilterOptions.BlockOption("structure-accent-wall", () -> structureAccentWall,
                block -> structureAccentWall = block,
                block -> block == Blocks.air || wallsOptional.get(block) && block.isStatic()));
            options.add(new ModFilterOptions.BlockOption("structure-damage-floor", () -> structureDamageFloor,
                block -> structureDamageFloor = block, floorsOptional));
        } else {
            options.add(new RuinFilterOptions.StepsEditOption(this));
        }

        options.add(new ModFilterOptions.SliderOption("scale", () -> scl, value -> scl = value, 1f, 500f, 1f));
        options.add(new ModFilterOptions.SliderOption("threshold", () -> threshold, value -> threshold = value, 0f, 1f, 0.01f));
        options.add(new ModFilterOptions.SliderOption("octaves", () -> octaves, value -> octaves = value, 1f, 10f, 1f));
        options.add(new ModFilterOptions.SliderOption("falloff", () -> falloff, value -> falloff = value, 0f, 1f, 0.01f));
        options.add(new ModFilterOptions.SliderOption("tilt", () -> tilt, value -> tilt = value, -4f, 4f, 0.01f));
        options.add(new ModFilterOptions.SliderOption("distort-scale", () -> distortScl,
            value -> distortScl = value, 1f, 20f, 0.1f));
        options.add(new ModFilterOptions.SliderOption("distort-mag", () -> distortMag,
            value -> distortMag = value, 0f, 10f, 0.1f));
        return options.toArray(new FilterOption[0]);
    }

    @Override
    public char icon() {
        return Iconc.blockStoneWall;
    }

    @Override
    public void apply(GenerateInput input) {
        BetterTerrainGenV2Mod.markUsed();
        if (input == null || input.width <= 0 || input.height <= 0) return;

        int size = input.width * input.height;
        if (mapComputed && computedInput != input) resetComputedMap();
        if (mapComputed && input.x == 0 && input.y == 0 && tilesLeft < size) {
            resetComputedMap();
        }

        if (!mapComputed || computedWidth != input.width || computedHeight != input.height) {
            ensureDefaults();
            computedWidth = input.width;
            computedHeight = input.height;
            computedInput = input;
            rebuildCenters(input);
            computeMap(input);
            mapComputed = true;
            tilesLeft = size;
        }

        writeTile(input);

        if (--tilesLeft <= 0) resetComputedMap();
    }

    /** Returns the number of centers selected during the most recent map computation. */
    public int centerCount() {
        return centers == null ? 0 : centers.size;
    }

    private void computeMap(GenerateInput input) {
        int width = input.width;
        int height = input.height;
        int size = width * height;

        computed = new TileState[size];
        floorModified = new boolean[size];
        blockModified = new boolean[size];

        for (int index = 0; index < size; index++) {
            int x = index % width;
            int y = index / width;
            computed[index] = readState(input, x, y);
        }

        TileState[] baseline = copyStates(computed);
        boolean structured = usesStructuredMode(width, height);
        if (structured) {
            computeStructured(baseline, width, height);
        } else if (steps != null && steps.length > 0 && centers != null && centers.size > 0) {
            float maxRadius = maxStepRadius();
            centerIndex = new CenterIndex(width, height, maxRadius);
            for (int i = 0; i < centers.size; i++) {
                int center = centers.get(i);
                centerIndex.add(center % width, center / width);
            }

            for (RuinStep step : steps) {
                if (step == null) continue;
                for (int x = 0; x < width; x++) {
                    for (int y = 0; y < height; y++) {
                        if (!passesNoise(x, y)) continue;

                        int index = x + y * width;
                        if (centerIndex == null || !centerIndex.hasMatch(x, y, step)) continue;
                        if (centerMode == CenterMode.auto && !isAutoProcessable(baseline[index])) continue;

                        applyStep(computed[index], baseline[index], step, index);
                    }
                }
            }
        }

        if (!structured) applyDistort(computed, baseline, width, height);
    }

    private void computeStructured(TileState[] baseline, int width, int height) {
        int size = width * height;
        StructuredRuinLayout.Spec structureSpec = StructuredRuinLayout.spec(width, height, structurePreset);
        Block[] generatedFloors = new Block[size];
        Block[] generatedWalls = new Block[size];
        boolean[] generatedFloor = new boolean[size];
        boolean[] generatedWall = new boolean[size];
        boolean[] criticalFloor = new boolean[size];
        boolean[] criticalWall = new boolean[size];
        Block staticWall = structureWallBlock();
        Block accentWall = structureAccentWallBlock();
        Block debrisWall = structureDebrisWallBlock();

        for (int centerIndex = 0; centerIndex < centers.size; centerIndex++) {
            int packed = centers.get(centerIndex);
            int centerX = packed % width;
            int centerY = packed / width;
            StructuredRuinLayout.Layout layout = StructuredRuinLayout.generate(
                width, height, centerX, centerY, seed, structurePreset);

            for (int localY = 0; localY < layout.span; localY++) {
                for (int localX = 0; localX < layout.span; localX++) {
                    int local = localX + localY * layout.span;
                    int x = layout.originX + localX;
                    int y = layout.originY + localY;
                    if (x < 0 || y < 0 || x >= width || y >= height) continue;

                    int index = x + y * width;
                    if (centerMode == CenterMode.auto && !isAutoProcessable(baseline[index])) continue;

                    if (layout.floor[local]) {
                        boolean protectedFloor = layout.criticalFloor[local];
                        Block floor;
                        if (layout.mainFloor[local]) {
                            floor = structureMainFloor;
                        } else if (layout.panelFloor[local]) {
                            floor = structurePanelFloor(x, y, centerX, centerY);
                        } else {
                            floor = structureFloor;
                        }
                        if (!(floor instanceof Floor)) floor = structureFloor;
                        if (!protectedFloor && structuredDamage(x, y, centerX, centerY,
                            structureSpec.floorDamage, 0x31)) {
                            floor = structureDamageFloorVariant(x, y, centerX, centerY);
                        }
                        if (floor instanceof Floor) {
                            if (!generatedFloor[index] || protectedFloor || !criticalFloor[index]) {
                                generatedFloors[index] = floor;
                                criticalFloor[index] = protectedFloor;
                            }
                            generatedFloor[index] = true;
                            // A floor always wins over a wall from an overlapping overlay center.
                            generatedWall[index] = false;
                            generatedWalls[index] = null;
                        }
                    } else if ((layout.wall[local] || layout.debrisWall[local])
                        && !generatedFloor[index]) {
                        boolean protectedWall = layout.criticalWall[local];
                        Block wall = layout.mainWall[local] ? staticWall
                            : layout.debrisWall[local] ? debrisWall : accentWall;
                        if (!protectedWall && structuredDamage(x, y, centerX, centerY,
                            structureSpec.wallDamage, 0x57)) {
                            wall = Blocks.air;
                        }
                        if (wall != null) {
                            if (!generatedWall[index] || protectedWall || !criticalWall[index]) {
                                generatedWalls[index] = wall;
                                criticalWall[index] = protectedWall;
                            }
                            generatedWall[index] = true;
                        }
                    }
                }
            }
        }

        for (int index = 0; index < size; index++) {
            if (generatedFloor[index]) {
                TileState state = computed[index];
                state.floor = (Floor)generatedFloors[index];
                floorModified[index] = true;
            }
            if (generatedWall[index] && !generatedFloor[index]) {
                computed[index].block = generatedWalls[index];
                blockModified[index] = true;
            }
        }
    }

    private boolean structuredDamage(int x, int y, int centerX, int centerY, float chance, int salt) {
        if (chance <= 0f) return false;
        float scale = finiteAtLeast(distortScl, 1f);
        float magnitude = finiteAtLeast(distortMag, 0f);
        float warpedX = x + distortionNoise(salt, x, y, scale, magnitude / 2f);
        float warpedY = y + distortionNoise(salt + 1, x, y, scale, magnitude / 2f);
        return passesNoise(warpedX, warpedY)
            && random01(seed ^ salt ^ centerX * 0x632be59b ^ centerY * 0x85157af5, x, y) < chance;
    }

    private Block structureWallBlock() {
        return structureWall == Blocks.air || structureWall.isStatic() ? structureWall : Blocks.metalWall2;
    }

    private Block structureAccentWallBlock() {
        return structureAccentWall == Blocks.air || structureAccentWall.isStatic()
            ? structureAccentWall : Blocks.metalWall1;
    }

    private Block structureDebrisWallBlock() {
        if (structureWall == Blocks.metalWall3) return structureWall;
        if (structureAccentWall == Blocks.metalWall3) return structureAccentWall;
        return Blocks.metalWall3;
    }

    private Block structurePanelFloor(int x, int y, int centerX, int centerY) {
        int value = seed ^ x * 0x9e3779b9 ^ y * 0x85ebca6b
            ^ centerX * 0xc2b2ae35 ^ centerY * 0x27d4eb2d;
        value ^= value >>> 16;
        return switch (value & 3) {
            case 0 -> Blocks.metalTiles2;
            case 1 -> Blocks.metalTiles3;
            case 2 -> Blocks.metalTiles4;
            default -> Blocks.metalTiles2;
        };
    }

    private Block structureDamageFloorVariant(int x, int y, int centerX, int centerY) {
        int value = seed ^ x * 0x632be59b ^ y * 0x85157af5
            ^ centerX * 0xc2b2ae35 ^ centerY * 0x27d4eb2d;
        value ^= value >>> 16;
        return switch (value & 15) {
            case 0, 1, 2, 3, 4, 12, 13, 14, 15 -> structureDamageFloor;
            case 5 -> Blocks.metalFloorDamaged;
            case 6 -> Blocks.metalFloor2;
            case 7 -> Blocks.metalFloor3;
            case 8 -> Blocks.metalFloor4;
            case 9 -> Blocks.metalFloor5;
            case 10 -> Blocks.metalTiles8;
            default -> Blocks.metalTiles11;
        };
    }

    private void applyDistort(TileState[] state, TileState[] baseline, int width, int height) {
        float scale = finiteAtLeast(distortScl, 1f);
        float magnitude = finiteAtLeast(distortMag, 0f);
        if (magnitude <= 0f) return;

        int size = width * height;
        TileState[] source = copyStates(state);
        TileState[] destination = copyStates(baseline);
        boolean[] newFloorModified = new boolean[size];
        boolean[] newBlockModified = new boolean[size];

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                int index = x + y * width;
                if (!floorModified[index] && !blockModified[index]) continue;

                int outputX = sampleCoordinate(x, distortionNoise(0, x, y, scale, magnitude / 2f), width);
                int outputY = sampleCoordinate(y, distortionNoise(1, x, y, scale, magnitude / 2f), height);
                int destinationIndex = outputX + outputY * width;

                if (floorModified[index]) {
                    destination[destinationIndex].floor = source[index].floor;
                    newFloorModified[destinationIndex] = true;
                }
                if (blockModified[index]) {
                    destination[destinationIndex].block = source[index].block;
                    newBlockModified[destinationIndex] = true;
                }
            }
        }

        System.arraycopy(destination, 0, state, 0, size);
        floorModified = newFloorModified;
        blockModified = newBlockModified;
    }

    private void writeTile(GenerateInput input) {
        int x = clampCoordinate(input.x, input.width);
        int y = clampCoordinate(input.y, input.height);
        int index = x + y * input.width;
        if (computed == null || (!floorModified[index] && !blockModified[index])) return;

        TileState state = computed[index];
        if (floorModified[index] && state.floor != null) {
            Floor floor = state.floor;
            input.floor = floor;
            Block overlay = input.overlay == null ? Blocks.air : input.overlay;
            if (!floor.hasSurface() && overlay instanceof OreBlock
                && overlay instanceof Floor overlayFloor && overlayFloor.needsSurface) {
                input.overlay = Blocks.air;
            }
        }

        if (!blockModified[index]) return;
        Block currentBlock = input.block == null ? Blocks.air : input.block;
        if (currentBlock.synthetic()) return;

        if (state.block == Blocks.air) {
            input.block = Blocks.air;
        } else if (state.block != null && !state.block.synthetic()) {
            input.block = state.block;
        }
    }

    private void rebuildCenters(GenerateInput input) {
        if (centers == null) centers = new IntSeq();
        centers.clear();

        if (centerMode == CenterMode.auto) {
            rebuildAutomaticCenters(input);
            return;
        }

        Block marker = replaceOverlay == null ? Blocks.metalTiles1 : replaceOverlay;
        if (marker == null) return;

        for (int x = 0; x < input.width; x++) {
            for (int y = 0; y < input.height; y++) {
                Tile tile = readTile(input, x, y);
                if (tile != null && tile.overlay() == marker) {
                    centers.add(x + y * input.width);
                } else if (tile == null && x == input.x && y == input.y && input.overlay == marker) {
                    centers.add(x + y * input.width);
                }
            }
        }
    }

    private void rebuildAutomaticCenters(GenerateInput input) {
        float density = clamp(autoDensity, 0f, 100f) / 100f;
        float spacing = finiteAtLeast(minCenterSpacing, 0f);
        boolean structured = usesStructuredMode(input.width, input.height);
        StructuredRuinLayout.Spec structureSpec = structured
            ? StructuredRuinLayout.spec(input.width, input.height, structurePreset) : null;
        float effectiveSpacing = Math.max(spacing, structured ? structureSpec.span : 0f);
        int spacingCells = Math.max(1, (int)Math.ceil(effectiveSpacing));
        CenterIndex selected = new CenterIndex(input.width, input.height, spacingCells);
        StructureIndex occupied = structured ? new StructureIndex(input.width, input.height,
            structureSpec.span) : null;

        int startX = positiveHash(seed, 0x13579bdf, 0) % input.width;
        int startY = positiveHash(seed, 0x2468ace0, 1) % input.height;
        boolean singleLarge = structured && structurePreset == StructurePreset.large;
        outer:
        for (int offsetX = 0; offsetX < input.width; offsetX++) {
            int x = (startX + offsetX) % input.width;
            for (int offsetY = 0; offsetY < input.height; offsetY++) {
                int y = (startY + offsetY) % input.height;
                TileState state = readState(input, x, y);
                if (!isAutoCenterCandidate(state)) continue;
                if (density <= 0f || (density < 1f && random01(seed, x, y) > density)) continue;
                if (effectiveSpacing > 0f && selected.hasNearby(x, y, effectiveSpacing)) continue;

                StructuredRuinLayout.Bounds bounds = null;
                if (structured) {
                    bounds = StructuredRuinLayout.bounds(input.width, input.height, x, y, structurePreset);
                    if (!bounds.contains(input.width, input.height) || occupied.conflicts(bounds)) continue;
                }

                centers.add(x + y * input.width);
                selected.add(x, y);
                if (structured) occupied.add(bounds);
                if (singleLarge) break outer;
            }
        }
    }

    private boolean isAutoCenterCandidate(TileState state) {
        return state.floor != null && !state.floor.isLiquid
            && state.block != null && !state.block.synthetic();
    }

    private boolean isAutoProcessable(TileState state) {
        return isAutoCenterCandidate(state);
    }

    private void applyStep(TileState state, TileState baseline, RuinStep step, int index) {
        if (step.preservesFloor()) {
            state.floor = baseline.floor;
            floorModified[index] = false;
        } else if (step.hasFloor() && step.floor instanceof Floor) {
            state.floor = (Floor)step.floor;
            floorModified[index] = true;
        }

        if (step.removesWall()) {
            state.block = Blocks.air;
            blockModified[index] = true;
        } else if (step.hasWall() && step.wall != null) {
            state.block = step.wall;
            blockModified[index] = true;
        }
    }

    private boolean passesNoise(int x, int y) {
        return passesNoise((float)x, (float)y);
    }

    private boolean passesNoise(float x, float y) {
        float scale = finiteAtLeast(scl, 1f);
        float value = finiteOr(threshold, 0.5f);
        float octaveCount = finiteAtLeast(octaves, 1f);
        float persistence = clamp(finiteOr(falloff, 0.5f), 0f, 1f);
        float skew = finiteOr(tilt, 0f);
        return noise(x, y + x * skew, scale, 1f, octaveCount, persistence) <= value;
    }

    private float distortionNoise(int seedOffset, float x, float y, float scale, float magnitude) {
        return Simplex.noise2d(seed + seedOffset, 1f, 0f, 1f / scale, x + 10f, y + 10f) * magnitude;
    }

    private float maxStepRadius() {
        float max = 0f;
        if (steps == null) return max;
        for (RuinStep step : steps) {
            if (step != null && Float.isFinite(step.radius)) max = Math.max(max, step.radius);
        }
        return max;
    }

    private TileState readState(GenerateInput input, int x, int y) {
        Tile tile = readTile(input, x, y);
        if (tile != null) return TileState.from(tile);
        if (x == input.x && y == input.y) return TileState.from(input);
        return TileState.empty();
    }

    private static Tile readTile(GenerateInput input, int x, int y) {
        try {
            return input.tile(x, y);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private void ensureDefaults() {
        if (replaceOverlay == null) replaceOverlay = Blocks.metalTiles1 == null ? Blocks.air : Blocks.metalTiles1;
        if (centerMode == null) centerMode = CenterMode.overlay;
        if (generationMode == null) generationMode = GenerationMode.scatter;
        if (structurePreset == null) structurePreset = StructurePreset.large;
        if (structureFloor == null) structureFloor = Blocks.metalFloor;
        if (structureMainFloor == null) structureMainFloor = Blocks.metalTiles1;
        if (structureWall == null) structureWall = Blocks.metalWall2;
        if (structureAccentWall == null) structureAccentWall = Blocks.metalWall1;
        if (structureDamageFloor == null) structureDamageFloor = Blocks.metalFloorDamaged;
        if (steps == null) steps = RuinStep.defaults();
    }

    private boolean usesStructuredMode(int width, int height) {
        return generationMode == GenerationMode.structured && Math.min(width, height) >= 24;
    }

    private void resetComputedMap() {
        mapComputed = false;
        tilesLeft = 0;
        computed = null;
        floorModified = null;
        blockModified = null;
        centerIndex = null;
        computedInput = null;
    }

    private static TileState[] copyStates(TileState[] states) {
        TileState[] copy = new TileState[states.length];
        for (int i = 0; i < states.length; i++) copy[i] = states[i].copy();
        return copy;
    }

    private static int sampleCoordinate(int coordinate, float offset, int size) {
        return clampCoordinate((int)(coordinate + offset), size);
    }

    private static int clampCoordinate(int coordinate, int size) {
        return Math.max(0, Math.min(size - 1, coordinate));
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float finiteOr(float value, float fallback) {
        return Float.isFinite(value) ? value : fallback;
    }

    private static float finiteAtLeast(float value, float min) {
        return Math.max(min, finiteOr(value, min));
    }

    private static int positiveHash(int seed, int salt, int axis) {
        int value = seed ^ salt ^ axis * 0x9e3779b9;
        value ^= value >>> 16;
        value *= 0x7feb352d;
        value ^= value >>> 15;
        return value & 0x7fffffff;
    }

    private static float random01(int seed, int x, int y) {
        int value = seed;
        value ^= x * 0x9e3779b9;
        value = Integer.rotateLeft(value, 13);
        value ^= y * 0x85ebca6b;
        value = Integer.rotateLeft(value, 17);
        value *= 0xc2b2ae35;
        value ^= value >>> 16;
        return (value & 0x7fffffff) / 2147483648f;
    }

    private static class TileState {
        Block block;
        Floor floor;
        Block overlay;

        static TileState from(Tile tile) {
            TileState state = new TileState();
            state.block = tile.block();
            state.floor = tile.floor();
            state.overlay = tile.overlay();
            return state;
        }

        static TileState from(GenerateInput input) {
            TileState state = new TileState();
            state.block = input.block == null ? Blocks.air : input.block;
            state.floor = input.floor instanceof Floor floor ? floor : airFloor();
            state.overlay = input.overlay == null ? Blocks.air : input.overlay;
            return state;
        }

        static TileState empty() {
            TileState state = new TileState();
            state.block = Blocks.air;
            state.floor = airFloor();
            state.overlay = Blocks.air;
            return state;
        }

        TileState copy() {
            TileState state = new TileState();
            state.block = block;
            state.floor = floor;
            state.overlay = overlay;
            return state;
        }

        private static Floor airFloor() {
            return Blocks.air instanceof Floor floor ? floor : null;
        }
    }

    private static final class CenterIndex {
        private final int mapWidth;
        private final int cellSize;
        private final int cellsWide;
        private final int cellsHigh;
        private final IntSeq[] buckets;

        CenterIndex(int width, int height, float radius) {
            mapWidth = width;
            cellSize = Math.max(1, (int)Math.ceil(Math.max(0f, radius)));
            cellsWide = Math.max(1, (width + cellSize - 1) / cellSize);
            cellsHigh = Math.max(1, (height + cellSize - 1) / cellSize);
            buckets = new IntSeq[cellsWide * cellsHigh];
        }

        void add(int x, int y) {
            int bucket = bucketIndex(x, y);
            IntSeq values = buckets[bucket];
            if (values == null) buckets[bucket] = values = new IntSeq();
            values.add(x + y * mapWidth);
        }

        boolean hasNearby(int x, int y, float radius) {
            if (radius <= 0f) return false;
            int minX = Math.max(0, (int)Math.floor((x - radius) / cellSize));
            int maxX = Math.min(cellsWide - 1, (int)Math.floor((x + radius) / cellSize));
            int minY = Math.max(0, (int)Math.floor((y - radius) / cellSize));
            int maxY = Math.min(cellsHigh - 1, (int)Math.floor((y + radius) / cellSize));
            float squared = radius * radius;

            for (int bx = minX; bx <= maxX; bx++) {
                for (int by = minY; by <= maxY; by++) {
                    IntSeq values = buckets[bx + by * cellsWide];
                    if (values == null) continue;
                    for (int i = 0; i < values.size; i++) {
                        int packed = values.get(i);
                        int centerX = packed % mapWidth;
                        int centerY = packed / mapWidth;
                        float dx = x - centerX;
                        float dy = y - centerY;
                        if (dx * dx + dy * dy < squared) return true;
                    }
                }
            }
            return false;
        }

        boolean hasMatch(int x, int y, RuinStep step) {
            float radius = step == null || !Float.isFinite(step.radius) ? 0f : Math.max(0f, step.radius);
            int minX = Math.max(0, (int)Math.floor((x - radius) / cellSize));
            int maxX = Math.min(cellsWide - 1, (int)Math.floor((x + radius) / cellSize));
            int minY = Math.max(0, (int)Math.floor((y - radius) / cellSize));
            int maxY = Math.min(cellsHigh - 1, (int)Math.floor((y + radius) / cellSize));

            for (int bx = minX; bx <= maxX; bx++) {
                for (int by = minY; by <= maxY; by++) {
                    IntSeq values = buckets[bx + by * cellsWide];
                    if (values == null) continue;
                    for (int i = 0; i < values.size; i++) {
                        int packed = values.get(i);
                        int centerX = packed % mapWidth;
                        int centerY = packed / mapWidth;
                        if (step.matches(centerX, centerY, x, y)) return true;
                    }
                }
            }
            return false;
        }

        private int bucketIndex(int x, int y) {
            int bx = Math.max(0, Math.min(cellsWide - 1, x / cellSize));
            int by = Math.max(0, Math.min(cellsHigh - 1, y / cellSize));
            return bx + by * cellsWide;
        }
    }

    private static final class StructureIndex {
        private final int cellSize;
        private final int cellsWide;
        private final int cellsHigh;
        private final IntSeq[] buckets;
        private final List<StructuredRuinLayout.Bounds> bounds = new ArrayList<>();

        StructureIndex(int width, int height, int cellSize) {
            this.cellSize = Math.max(1, cellSize);
            cellsWide = Math.max(1, (width + this.cellSize - 1) / this.cellSize);
            cellsHigh = Math.max(1, (height + this.cellSize - 1) / this.cellSize);
            buckets = new IntSeq[cellsWide * cellsHigh];
        }

        void add(StructuredRuinLayout.Bounds value) {
            int index = bounds.size();
            bounds.add(value);
            int minX = Math.max(0, value.minX / cellSize);
            int maxX = Math.min(cellsWide - 1, value.maxX / cellSize);
            int minY = Math.max(0, value.minY / cellSize);
            int maxY = Math.min(cellsHigh - 1, value.maxY / cellSize);
            for (int x = minX; x <= maxX; x++) {
                for (int y = minY; y <= maxY; y++) {
                    int bucket = x + y * cellsWide;
                    if (buckets[bucket] == null) buckets[bucket] = new IntSeq();
                    buckets[bucket].add(index);
                }
            }
        }

        boolean conflicts(StructuredRuinLayout.Bounds value) {
            int minX = Math.max(0, value.minX / cellSize);
            int maxX = Math.min(cellsWide - 1, value.maxX / cellSize);
            int minY = Math.max(0, value.minY / cellSize);
            int maxY = Math.min(cellsHigh - 1, value.maxY / cellSize);
            for (int x = minX; x <= maxX; x++) {
                for (int y = minY; y <= maxY; y++) {
                    IntSeq bucket = buckets[x + y * cellsWide];
                    if (bucket == null) continue;
                    for (int i = 0; i < bucket.size; i++) {
                        if (value.intersects(bounds.get(bucket.get(i)))) return true;
                    }
                }
            }
            return false;
        }
    }

    public enum CenterMode {
        overlay,
        auto;

        public CenterMode next() {
            return this == overlay ? auto : overlay;
        }
    }

    public enum GenerationMode {
        scatter,
        structured;

        public GenerationMode next() {
            return this == scatter ? structured : scatter;
        }
    }

    public enum StructurePreset {
        small,
        medium,
        large;

        public StructurePreset next() {
            StructurePreset[] values = values();
            return values[(ordinal() + 1) % values.length];
        }
    }
}
