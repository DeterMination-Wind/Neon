package betterterraingen.v2.filters;

import betterterraingen.v2.NaturalWaterGenerator;
import betterterraingen.v2.BetterTerrainGenV2Mod;
import betterterraingen.v2.ui.ModFilterOptions;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.gen.Iconc;
import mindustry.maps.filters.FilterOption;
import mindustry.maps.filters.GenerateFilter;
import mindustry.world.Block;
import mindustry.world.blocks.environment.Floor;

import static mindustry.maps.filters.FilterOption.floorsOnly;

public class NaturalWaterFilter extends GenerateFilter {
    public float waterCoverage = 35f;
    public float waterScale = 90f;
    public float contourComplexity = 0.55f;
    public float edgeBias = 0f;
    public float shoalWidth = 2f;
    public float shallowWidth = 5f;
    public float depthWarpScale = 0.02f;
    public float depthWarpMag = 3f;
    public float shoalFragmentation;
    public float shallowFragmentation;
    public Block shoalFloor = Blocks.sandWater;
    public Block shallowFloor = Blocks.water;
    public Block deepFloor = Blocks.deepwater;
    public boolean naturalCleanup = true;

    private transient volatile CacheEntry cache;

    @Override
    public FilterOption[] options() {
        ensureDefaultFloors();
        return new FilterOption[] {
            new ModFilterOptions.SliderOption("watercoverage", () -> waterCoverage, value -> waterCoverage = value, 0f, 100f, 1f),
            new ModFilterOptions.SliderOption("waterscale", () -> waterScale, value -> waterScale = value, 4f, 300f, 1f),
            new ModFilterOptions.SliderOption("contourcomplexity", () -> contourComplexity, value -> contourComplexity = value, 0f, 1f, 0.01f),
            new ModFilterOptions.SliderOption("edgebias", () -> edgeBias, value -> edgeBias = value, -1f, 1f, 0.01f),
            new ModFilterOptions.SliderOption("shoalwidth", () -> shoalWidth, value -> shoalWidth = value, 0f, 30f, 1f),
            new ModFilterOptions.SliderOption("shallowwidth", () -> shallowWidth, value -> shallowWidth = value, 0f, 50f, 1f),
            new ModFilterOptions.SliderOption("depthwarpscale", () -> depthWarpScale, value -> depthWarpScale = value, 0.005f, 0.2f, 0.005f),
            new ModFilterOptions.SliderOption("depthwarpmag", () -> depthWarpMag, value -> depthWarpMag = value, 0f, 10f, 0.5f),
            new ModFilterOptions.SliderOption("shoalfragmentation", () -> shoalFragmentation, value -> shoalFragmentation = value, 0f, 1f, 0.01f),
            new ModFilterOptions.SliderOption("shallowfragmentation", () -> shallowFragmentation, value -> shallowFragmentation = value, 0f, 1f, 0.01f),
            new ModFilterOptions.BlockOption("shoalfloor", () -> shoalFloor, value -> shoalFloor = value, floorsOnly),
            new ModFilterOptions.BlockOption("shallowfloor", () -> shallowFloor, value -> shallowFloor = value, floorsOnly),
            new ModFilterOptions.BlockOption("deepfloor", () -> deepFloor, value -> deepFloor = value, floorsOnly),
            new ModFilterOptions.ToggleOption("naturalcleanup", () -> naturalCleanup, value -> naturalCleanup = value)
        };
    }

    @Override
    public char icon() {
        return Iconc.blockDeepWater;
    }

    @Override
    public void apply(GenerateInput input) {
        BetterTerrainGenV2Mod.markUsed();
        ensureDefaultFloors();
        CacheEntry entry = ensureCache(input.width, input.height);
        int x = Math.max(0, Math.min(entry.width - 1, input.x));
        int y = Math.max(0, Math.min(entry.height - 1, input.y));
        byte layer = entry.layers[x + y * entry.width];
        if (layer == NaturalWaterGenerator.land) return;

        input.floor = switch (layer) {
            case NaturalWaterGenerator.shoal -> validFloor(shoalFloor, Blocks.sandWater);
            case NaturalWaterGenerator.shallow -> validFloor(shallowFloor, Blocks.water);
            default -> validFloor(deepFloor, Blocks.deepwater);
        };

        if (!input.block.synthetic()) input.block = Blocks.air;
        if (input.overlay != Blocks.spawn) input.overlay = Blocks.air;
    }

    private CacheEntry ensureCache(int width, int height) {
        CacheKey key = new CacheKey(width, height, seed, waterCoverage, waterScale, contourComplexity,
            edgeBias, shoalWidth, shallowWidth, depthWarpScale, depthWarpMag,
            shoalFragmentation, shallowFragmentation, naturalCleanup, blockName(shoalFloor),
            blockName(shallowFloor), blockName(deepFloor));
        CacheEntry current = cache;
        if (current != null && current.key.equals(key)) return current;

        synchronized (this) {
            current = cache;
            if (current != null && current.key.equals(key)) return current;

            NaturalWaterGenerator.Config config = new NaturalWaterGenerator.Config().size(width, height);
            config.seed = seed;
            config.coverage = clamp(waterCoverage / 100f, 0f, 1f);
            config.scale = Math.max(4f, waterScale);
            config.complexity = clamp(contourComplexity, 0f, 1f);
            config.edgeBias = clamp(edgeBias, -1f, 1f);
            config.shoalWidth = Math.max(0f, shoalWidth);
            config.shallowWidth = Math.max(0f, shallowWidth);
            config.depthWarpScale = Math.max(0.005f, depthWarpScale);
            config.depthWarpMag = Math.max(0f, depthWarpMag);
            config.shoalFragmentation = clamp(shoalFragmentation, 0f, 1f);
            config.shallowFragmentation = clamp(shallowFragmentation, 0f, 1f);
            config.cleanup = naturalCleanup;
            config.allowParallel = !Vars.mobile;

            NaturalWaterGenerator.Result result = NaturalWaterGenerator.generate(config);
            current = new CacheEntry(key, result.width, result.height, result.layers);
            cache = current;
            return current;
        }
    }

    private void ensureDefaultFloors() {
        if (!(shoalFloor instanceof Floor)) shoalFloor = Blocks.sandWater;
        if (!(shallowFloor instanceof Floor)) shallowFloor = Blocks.water;
        if (!(deepFloor instanceof Floor)) deepFloor = Blocks.deepwater;
    }

    private static Block validFloor(Block value, Block fallback) {
        return value instanceof Floor ? value : fallback;
    }

    private static String blockName(Block block) {
        return block == null ? "" : block.name;
    }

    private static float clamp(float value, float min, float max) {
        if (Float.isNaN(value) || Float.isInfinite(value)) return min;
        return Math.max(min, Math.min(max, value));
    }

    private static final class CacheEntry {
        final CacheKey key;
        final int width;
        final int height;
        final byte[] layers;

        CacheEntry(CacheKey key, int width, int height, byte[] layers) {
            this.key = key;
            this.width = width;
            this.height = height;
            this.layers = layers;
        }
    }

    private static final class CacheKey {
        final int width;
        final int height;
        final int seed;
        final int waterCoverage;
        final int waterScale;
        final int contourComplexity;
        final int edgeBias;
        final int shoalWidth;
        final int shallowWidth;
        final int depthWarpScale;
        final int depthWarpMag;
        final int shoalFragmentation;
        final int shallowFragmentation;
        final boolean naturalCleanup;
        final String shoalFloor;
        final String shallowFloor;
        final String deepFloor;

        CacheKey(int width, int height, int seed, float waterCoverage, float waterScale,
                 float contourComplexity, float edgeBias, float shoalWidth, float shallowWidth,
                 float depthWarpScale, float depthWarpMag,
                 float shoalFragmentation, float shallowFragmentation,
                 boolean naturalCleanup, String shoalFloor, String shallowFloor, String deepFloor) {
            this.width = width;
            this.height = height;
            this.seed = seed;
            this.waterCoverage = Float.floatToIntBits(waterCoverage);
            this.waterScale = Float.floatToIntBits(waterScale);
            this.contourComplexity = Float.floatToIntBits(contourComplexity);
            this.edgeBias = Float.floatToIntBits(edgeBias);
            this.shoalWidth = Float.floatToIntBits(shoalWidth);
            this.shallowWidth = Float.floatToIntBits(shallowWidth);
            this.depthWarpScale = Float.floatToIntBits(depthWarpScale);
            this.depthWarpMag = Float.floatToIntBits(depthWarpMag);
            this.shoalFragmentation = Float.floatToIntBits(shoalFragmentation);
            this.shallowFragmentation = Float.floatToIntBits(shallowFragmentation);
            this.naturalCleanup = naturalCleanup;
            this.shoalFloor = shoalFloor;
            this.shallowFloor = shallowFloor;
            this.deepFloor = deepFloor;
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) return true;
            if (!(object instanceof CacheKey other)) return false;
            return width == other.width && height == other.height && seed == other.seed
                && waterCoverage == other.waterCoverage && waterScale == other.waterScale
                && contourComplexity == other.contourComplexity && edgeBias == other.edgeBias
                && shoalWidth == other.shoalWidth && shallowWidth == other.shallowWidth
                && depthWarpScale == other.depthWarpScale && depthWarpMag == other.depthWarpMag
                && shoalFragmentation == other.shoalFragmentation && shallowFragmentation == other.shallowFragmentation
                && naturalCleanup == other.naturalCleanup && shoalFloor.equals(other.shoalFloor)
                && shallowFloor.equals(other.shallowFloor) && deepFloor.equals(other.deepFloor);
        }

        @Override
        public int hashCode() {
            int result = width;
            result = 31 * result + height;
            result = 31 * result + seed;
            result = 31 * result + waterCoverage;
            result = 31 * result + waterScale;
            result = 31 * result + contourComplexity;
            result = 31 * result + edgeBias;
            result = 31 * result + shoalWidth;
            result = 31 * result + shallowWidth;
            result = 31 * result + depthWarpScale;
            result = 31 * result + depthWarpMag;
            result = 31 * result + shoalFragmentation;
            result = 31 * result + shallowFragmentation;
            result = 31 * result + (naturalCleanup ? 1 : 0);
            result = 31 * result + shoalFloor.hashCode();
            result = 31 * result + shallowFloor.hashCode();
            result = 31 * result + deepFloor.hashCode();
            return result;
        }
    }
}
