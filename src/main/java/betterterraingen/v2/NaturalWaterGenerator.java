package betterterraingen.v2;

import arc.util.noise.Simplex;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class NaturalWaterGenerator {
    public static final byte land = 0;
    public static final byte shoal = 1;
    public static final byte shallow = 2;
    public static final byte deep = 3;

    private static final int parallelAreaThreshold = 65_536;
    private static final int[] dx4 = {1, -1, 0, 0};
    private static final int[] dy4 = {0, 0, 1, -1};

    private NaturalWaterGenerator() {
    }

    public static Result generate(Config config) {
        int width = Math.max(1, config.width);
        int height = Math.max(1, config.height);
        long longArea = (long) width * height;
        if (longArea > Integer.MAX_VALUE) throw new IllegalArgumentException("Map is too large");
        int area = (int) longArea;
        float coverage = clamp(config.coverage, 0f, 1f);
        int targetWater = Math.max(0, Math.min(area, Math.round(area * coverage)));

        int padding = generationPadding(config);
        int paddedWidth = checkedDimension(width, padding);
        int paddedHeight = checkedDimension(height, padding);
        long paddedArea = (long) paddedWidth * paddedHeight;
        if (paddedArea > Integer.MAX_VALUE) throw new IllegalArgumentException("Padded map is too large");

        float[] field = new float[(int) paddedArea];
        fillNoiseField(field, paddedWidth, paddedHeight, padding, width, height, config);
        float[] centerValues = sortedCenterValues(field, paddedWidth, padding, width, height);

        boolean[] water;
        int croppedWater;
        if (targetWater <= 0) {
            water = new boolean[field.length];
            croppedWater = 0;
        } else if (targetWater >= area) {
            water = new boolean[field.length];
            Arrays.fill(water, true);
            croppedWater = area;
        } else if (!config.cleanup) {
            float threshold = thresholdForFraction(centerValues, coverage);
            water = selectByThreshold(field, threshold);
            croppedWater = countCroppedWater(water, paddedWidth, padding, width, height);
        } else {
            Candidate candidate = fitNaturalizedCoverage(field, centerValues, paddedWidth, paddedHeight,
                padding, width, height, targetWater, coverage, Math.max(4f, config.scale));
            water = candidate.water;
            croppedWater = candidate.croppedWater;
        }

        byte[] paddedLayers = distanceLayers(water, paddedWidth, paddedHeight, config.seed,
            config.shoalWidth, config.shallowWidth,
            config.depthWarpScale, config.depthWarpMag,
            config.shoalFragmentation, config.shallowFragmentation);
        byte[] layers = cropLayers(paddedLayers, paddedWidth, padding, width, height);
        return new Result(width, height, layers, croppedWater);
    }

    private static int generationPadding(Config config) {
        float totalWaterBand = Math.max(0f, config.shoalWidth) + Math.max(0f, config.shallowWidth);
        return Math.max(8, Math.min(64, (int) Math.ceil(totalWaterBand) + 4));
    }

    private static int checkedDimension(int size, int padding) {
        long result = (long) size + padding * 2L;
        if (result > Integer.MAX_VALUE) throw new IllegalArgumentException("Padded dimension is too large");
        return (int) result;
    }

    private static void fillNoiseField(float[] field, int paddedWidth, int paddedHeight, int padding,
                                       int width, int height, Config config) {
        int processors = Runtime.getRuntime().availableProcessors();
        boolean parallel = config.allowParallel && field.length >= parallelAreaThreshold && processors > 1;
        if (!parallel) {
            fillRows(field, paddedWidth, padding, width, height, config, 0, paddedHeight);
            return;
        }

        int threads = Math.min(Math.min(processors, 8), Math.max(2, paddedHeight / 32));
        ExecutorService executor = null;
        try {
            executor = Executors.newFixedThreadPool(threads, runnable -> {
                Thread thread = new Thread(runnable, "natural-water-noise");
                thread.setDaemon(true);
                return thread;
            });

            List<Callable<Void>> tasks = new ArrayList<>(threads);
            for (int i = 0; i < threads; i++) {
                int from = paddedHeight * i / threads;
                int to = paddedHeight * (i + 1) / threads;
                tasks.add(() -> {
                    fillRows(field, paddedWidth, padding, width, height, config, from, to);
                    return null;
                });
            }

            List<Future<Void>> futures = executor.invokeAll(tasks);
            for (Future<Void> future : futures) future.get();
        } catch (Throwable ignored) {
            fillRows(field, paddedWidth, padding, width, height, config, 0, paddedHeight);
        } finally {
            if (executor != null) executor.shutdownNow();
        }
    }

    private static void fillRows(float[] field, int paddedWidth, int padding, int width, int height,
                                 Config config, int fromY, int toY) {
        float scale = Math.max(4f, config.scale);
        float complexity = clamp(config.complexity, 0f, 1f);
        float edgeBias = clamp(config.edgeBias, -1f, 1f);
        float warpScale = scale * (1.25f - complexity * 0.35f);
        float warpStrength = scale * (0.07f + complexity * 0.24f);
        float detailScale = Math.max(3f, scale * (0.22f + complexity * 0.18f));
        int octaves = 3 + Math.round(complexity * 3f);
        float persistence = 0.46f + complexity * 0.12f;
        float halfMin = Math.max(1f, Math.min(width, height) * 0.5f);

        for (int paddedY = fromY; paddedY < toY; paddedY++) {
            int y = paddedY - padding;
            for (int paddedX = 0; paddedX < paddedWidth; paddedX++) {
                int x = paddedX - padding;
                float warpX = centeredNoise(config.seed + 0x2f31, x, y, warpScale, 2, 0.55f);
                float warpY = centeredNoise(config.seed - 0x5a17, x, y, warpScale, 2, 0.55f);
                float sampleX = x + warpX * warpStrength;
                float sampleY = y + warpY * warpStrength;

                float base = centeredNoise(config.seed, sampleX, sampleY, scale, octaves, persistence);
                float detail = centeredNoise(config.seed + 0x714b, sampleX, sampleY, detailScale, 2, 0.5f);
                float minEdge = Math.min(Math.min(x, width - 1 - x), Math.min(y, height - 1 - y));
                float edgeInfluence = 1f - clamp(minEdge / halfMin, 0f, 1f);

                field[paddedX + paddedY * paddedWidth] = base + detail * (0.12f + complexity * 0.34f)
                    - edgeBias * edgeInfluence * 1.15f;
            }
        }
    }

    private static float centeredNoise(int seed, float x, float y, float scale, int octaves, float persistence) {
        return Simplex.noise2d(seed, octaves, persistence, 1f / Math.max(1f, scale), x + 10f, y + 10f) * 2f - 1f;
    }

    private static float[] sortedCenterValues(float[] field, int paddedWidth, int padding,
                                              int width, int height) {
        float[] values = new float[width * height];
        for (int y = 0; y < height; y++) {
            System.arraycopy(field, padding + (y + padding) * paddedWidth, values, y * width, width);
        }
        Arrays.sort(values);
        return values;
    }

    private static float thresholdForFraction(float[] sortedValues, float fraction) {
        int count = Math.max(0, Math.min(sortedValues.length, Math.round(sortedValues.length * clamp(fraction, 0f, 1f))));
        if (count <= 0) return Float.NEGATIVE_INFINITY;
        if (count >= sortedValues.length) return Float.POSITIVE_INFINITY;
        float lower = sortedValues[count - 1];
        float upper = sortedValues[count];
        return lower + (upper - lower) * 0.5f;
    }

    private static boolean[] selectByThreshold(float[] field, float threshold) {
        boolean[] water = new boolean[field.length];
        for (int i = 0; i < field.length; i++) water[i] = field[i] <= threshold;
        return water;
    }

    private static Candidate fitNaturalizedCoverage(float[] field, float[] sortedCenterValues,
                                                     int paddedWidth, int paddedHeight, int padding,
                                                     int width, int height, int targetWater,
                                                     float initialFraction, float scale) {
        int tolerance = Math.max(1, (int) Math.ceil(width * (double) height * 0.03));
        float low = 0f;
        float high = 1f;
        float fraction = initialFraction;
        Candidate best = null;

        for (int attempt = 0; attempt < 8; attempt++) {
            float threshold = thresholdForFraction(sortedCenterValues, fraction);
            boolean[] water = selectByThreshold(field, threshold);
            naturalize(water, paddedWidth, paddedHeight, scale);
            fillEnclosedLandInCrop(water, paddedWidth, padding, width, height);
            int croppedWater = countCroppedWater(water, paddedWidth, padding, width, height);
            Candidate candidate = new Candidate(water, croppedWater);
            if (best == null || Math.abs(croppedWater - targetWater) < Math.abs(best.croppedWater - targetWater)) {
                best = candidate;
            }
            if (Math.abs(croppedWater - targetWater) <= tolerance) break;

            if (croppedWater > targetWater) {
                high = fraction;
            } else {
                low = fraction;
            }
            fraction = (low + high) * 0.5f;
        }
        return best;
    }

    private static void naturalize(boolean[] water, int width, int height, float scale) {
        int minWaterComponent = Math.max(2, Math.min(12, Math.round(scale * 0.08f)));
        removeSmallWaterComponents(water, width, height, minWaterComponent);
        closeWater(water, width, height);
        openWater(water, width, height);
        smooth(water, width, height);
        smooth(water, width, height);
        pruneSpurs(water, width, height, true, 8);
        pruneSpurs(water, width, height, false, 8);
        pruneSpurs(water, width, height, true, 4);
        removeSmallWaterComponents(water, width, height, minWaterComponent);
    }

    private static void removeSmallWaterComponents(boolean[] water, int width, int height, int minSize) {
        boolean[] visited = new boolean[water.length];
        int[] queue = new int[water.length];

        for (int start = 0; start < water.length; start++) {
            if (!water[start] || visited[start]) continue;
            int head = 0;
            int tail = 0;
            queue[tail++] = start;
            visited[start] = true;

            while (head < tail) {
                int index = queue[head++];
                int x = index % width;
                int y = index / width;
                for (int dir = 0; dir < 4; dir++) {
                    int nx = x + dx4[dir];
                    int ny = y + dy4[dir];
                    if (nx < 0 || nx >= width || ny < 0 || ny >= height) continue;
                    int next = nx + ny * width;
                    if (water[next] && !visited[next]) {
                        visited[next] = true;
                        queue[tail++] = next;
                    }
                }
            }

            if (tail < minSize) {
                for (int i = 0; i < tail; i++) water[queue[i]] = false;
            }
        }
    }

    private static void fillEnclosedLandInCrop(boolean[] water, int paddedWidth, int padding,
                                               int width, int height) {
        boolean[] visited = new boolean[width * height];
        int[] queue = new int[visited.length];

        for (int start = 0; start < visited.length; start++) {
            int startX = start % width;
            int startY = start / width;
            int paddedStart = startX + padding + (startY + padding) * paddedWidth;
            if (water[paddedStart] || visited[start]) continue;
            int head = 0;
            int tail = 0;
            boolean touchesEdge = false;
            queue[tail++] = start;
            visited[start] = true;

            while (head < tail) {
                int index = queue[head++];
                int x = index % width;
                int y = index / width;
                if (x == 0 || y == 0 || x == width - 1 || y == height - 1) touchesEdge = true;

                for (int dir = 0; dir < 4; dir++) {
                    int nx = x + dx4[dir];
                    int ny = y + dy4[dir];
                    if (nx < 0 || nx >= width || ny < 0 || ny >= height) continue;
                    int next = nx + ny * width;
                    int paddedNext = nx + padding + (ny + padding) * paddedWidth;
                    if (!water[paddedNext] && !visited[next]) {
                        visited[next] = true;
                        queue[tail++] = next;
                    }
                }
            }

            if (!touchesEdge) {
                for (int i = 0; i < tail; i++) {
                    int index = queue[i];
                    int x = index % width;
                    int y = index / width;
                    water[x + padding + (y + padding) * paddedWidth] = true;
                }
            }
        }
    }

    private static void closeWater(boolean[] water, int width, int height) {
        boolean[] dilated = dilateWater(water, width, height);
        boolean[] closed = erodeWater(dilated, width, height);
        System.arraycopy(closed, 0, water, 0, water.length);
    }

    private static void openWater(boolean[] water, int width, int height) {
        boolean[] eroded = erodeWater(water, width, height);
        boolean[] opened = dilateWater(eroded, width, height);
        System.arraycopy(opened, 0, water, 0, water.length);
    }

    private static boolean[] dilateWater(boolean[] water, int width, int height) {
        boolean[] result = water.clone();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int index = x + y * width;
                if (water[index]) continue;
                for (int dir = 0; dir < 4; dir++) {
                    int nx = x + dx4[dir];
                    int ny = y + dy4[dir];
                    if (nx >= 0 && nx < width && ny >= 0 && ny < height && water[nx + ny * width]) {
                        result[index] = true;
                        break;
                    }
                }
            }
        }
        return result;
    }

    private static boolean[] erodeWater(boolean[] water, int width, int height) {
        boolean[] result = water.clone();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int index = x + y * width;
                if (!water[index]) continue;
                for (int dir = 0; dir < 4; dir++) {
                    int nx = x + dx4[dir];
                    int ny = y + dy4[dir];
                    if (nx < 0 || nx >= width || ny < 0 || ny >= height || !water[nx + ny * width]) {
                        result[index] = false;
                        break;
                    }
                }
            }
        }
        return result;
    }

    private static void smooth(boolean[] water, int width, int height) {
        boolean[] next = water.clone();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int neighbors = 0;
                for (int oy = -1; oy <= 1; oy++) {
                    for (int ox = -1; ox <= 1; ox++) {
                        if (ox == 0 && oy == 0) continue;
                        int nx = x + ox;
                        int ny = y + oy;
                        if (nx >= 0 && nx < width && ny >= 0 && ny < height && water[nx + ny * width]) {
                            neighbors++;
                        }
                    }
                }

                int index = x + y * width;
                if (water[index] && neighbors <= 2) next[index] = false;
                if (!water[index] && neighbors >= 6) next[index] = true;
            }
        }
        System.arraycopy(next, 0, water, 0, water.length);
    }

    private static void pruneSpurs(boolean[] water, int width, int height, boolean value, int maxPasses) {
        for (int pass = 0; pass < maxPasses; pass++) {
            boolean[] next = water.clone();
            boolean changed = false;
            for (int y = 1; y + 1 < height; y++) {
                for (int x = 1; x + 1 < width; x++) {
                    int index = x + y * width;
                    if (water[index] != value) continue;
                    int matching = 0;
                    if (water[index - 1] == value) matching++;
                    if (water[index + 1] == value) matching++;
                    if (water[index - width] == value) matching++;
                    if (water[index + width] == value) matching++;
                    if (matching <= 1) {
                        next[index] = !value;
                        changed = true;
                    }
                }
            }
            System.arraycopy(next, 0, water, 0, water.length);
            if (!changed) break;
        }
    }

    private static int countCroppedWater(boolean[] water, int paddedWidth, int padding,
                                         int width, int height) {
        int count = 0;
        for (int y = 0; y < height; y++) {
            int row = padding + (y + padding) * paddedWidth;
            for (int x = 0; x < width; x++) {
                if (water[row + x]) count++;
            }
        }
        return count;
    }

    private static byte[] cropLayers(byte[] paddedLayers, int paddedWidth, int padding,
                                     int width, int height) {
        byte[] layers = new byte[width * height];
        for (int y = 0; y < height; y++) {
            System.arraycopy(paddedLayers, padding + (y + padding) * paddedWidth,
                layers, y * width, width);
        }
        return layers;
    }

    private static boolean isEdge(int width, int height, int index) {
        int x = index % width;
        int y = index / width;
        return x == 0 || y == 0 || x == width - 1 || y == height - 1;
    }

    private static byte[] distanceLayers(boolean[] water, int width, int height, int seed,
                                          float shoalWidth, float shallowWidth,
                                          float depthWarpScale, float depthWarpMag,
                                          float shoalFragmentation, float shallowFragmentation) {
        int area = water.length;
        byte[] layers = new byte[area];
        int[] distance = new int[area];
        int infinity = width + height + 2;

        for (int i = 0; i < area; i++) {
            distance[i] = water[i] ? (isEdge(width, height, i) ? 1 : infinity) : 0;
        }

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int index = x + y * width;
                if (!water[index]) continue;
                if (x > 0) distance[index] = Math.min(distance[index], distance[index - 1] + 1);
                if (y > 0) distance[index] = Math.min(distance[index], distance[index - width] + 1);
            }
        }

        for (int y = height - 1; y >= 0; y--) {
            for (int x = width - 1; x >= 0; x--) {
                int index = x + y * width;
                if (!water[index]) continue;
                if (x + 1 < width) distance[index] = Math.min(distance[index], distance[index + 1] + 1);
                if (y + 1 < height) distance[index] = Math.min(distance[index], distance[index + width] + 1);
            }
        }

        int shoalLimit = Math.max(1, Math.round(Math.max(0f, shoalWidth)));
        int shallowLimit = shoalLimit + Math.max(0, Math.round(Math.max(0f, shallowWidth)));
        boolean useWarp = depthWarpMag > 0.001f && depthWarpScale > 0.0001f;
        boolean useFragShoal = shoalFragmentation > 0.001f;
        boolean useFragShallow = shallowFragmentation > 0.001f;
        for (int i = 0; i < area; i++) {
            if (!water[i]) continue;

            int baseLayer;
            if (distance[i] <= shoalLimit) {
                baseLayer = shoal;
            } else if (distance[i] <= shallowLimit) {
                baseLayer = shallow;
            } else {
                baseLayer = deep;
            }

            int layer = baseLayer;
            if (useWarp) {
                int x = i % width;
                int y = i / width;
                float warp = (float)Simplex.noise2d(seed + 777, 1, 1f,
                    1f / Math.max(1f, depthWarpScale), x + 10f, y + 10f) * 2f - 1f;
                float shifted = distance[i] + warp * depthWarpMag;

                int targetLayer;
                if (shifted <= shoalLimit) {
                    targetLayer = shoal;
                } else if (shifted <= shallowLimit) {
                    targetLayer = shallow;
                } else {
                    targetLayer = deep;
                }

                if (baseLayer == shoal) {
                    layer = Math.min(targetLayer, shallow);
                } else if (baseLayer == deep) {
                    layer = Math.max(targetLayer, shallow);
                } else {
                    layer = targetLayer;
                }
            }

            if (layer == shoal && useFragShoal && shoalLimit > 0) {
                int x = i % width;
                int y = i / width;
                float t = (float)distance[i] / shoalLimit;
                float keepNoise = (float)Simplex.noise2d(seed + 111, 1, 1f,
                    0.04f, x + 10f, y + 10f) * 2f - 1f;
                float threshold = t * shoalFragmentation - 1f;
                if (keepNoise < threshold) layer = land;
            } else if (layer == shallow && useFragShallow && shallowLimit > shoalLimit) {
                int x = i % width;
                int y = i / width;
                float t = (float)(distance[i] - shoalLimit) / (shallowLimit - shoalLimit);
                float keepNoise = (float)Simplex.noise2d(seed + 222, 1, 1f,
                    0.04f, x + 10f, y + 10f) * 2f - 1f;
                float threshold = t * shallowFragmentation - 1f;
                if (keepNoise < threshold) layer = shoal;
            }

            layers[i] = (byte)layer;
        }
        return layers;
    }

    private static float clamp(float value, float min, float max) {
        if (Float.isNaN(value) || Float.isInfinite(value)) return min;
        return Math.max(min, Math.min(max, value));
    }

    public static final class Config {
        public int width;
        public int height;
        public int seed;
        public float coverage = 0.35f;
        public float scale = 90f;
        public float complexity = 0.55f;
        public float edgeBias;
        public float shoalWidth = 2f;
        public float shallowWidth = 5f;
        public boolean cleanup = true;
        public float depthWarpScale = 0.02f;
        public float depthWarpMag = 3f;
        public float shoalFragmentation;
        public float shallowFragmentation;
        public boolean allowParallel = true;

        public Config size(int width, int height) {
            this.width = width;
            this.height = height;
            return this;
        }
    }

    private static final class Candidate {
        final boolean[] water;
        final int croppedWater;

        Candidate(boolean[] water, int croppedWater) {
            this.water = water;
            this.croppedWater = croppedWater;
        }
    }

    public static final class Result {
        public final int width;
        public final int height;
        public final byte[] layers;
        public final int waterTiles;

        Result(int width, int height, byte[] layers, int waterTiles) {
            this.width = width;
            this.height = height;
            this.layers = layers;
            this.waterTiles = waterTiles;
        }

        public float coverage() {
            return waterTiles / (float) layers.length;
        }
    }
}
