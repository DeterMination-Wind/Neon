package bektools.profiler;

import arc.util.Time;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;

public final class NeonProfiler{
    public static final String threadMain = "Main";
    public static final String threadAsync = "Async";

    private static final int bucketCount = 10;
    private static final int initialKeyCapacity = 256;
    private static final int initialRowCapacity = 256;
    private static final String[] registeredModules = {
        "Neon",
        "PGMM",
        "SP",
        "RBM",
        "BMM",
        "SPDB",
        "BME",
        "BPO",
        "BLS",
        "BHK",
        "MU",
        "HM",
        "US",
        "WUTB",
        "PV",
        "CM",
        "BSS",
        "Profiler"
    };

    private static boolean enabled;
    private static boolean showAll;
    private static long enableEpochMillis;
    private static long windowStartMillis = nowMillis();
    private static long lastRotateMillis = windowStartMillis;

    private static RowKey[] keys = new RowKey[initialKeyCapacity];
    private static BucketSeries[] series = new BucketSeries[initialKeyCapacity];
    private static int size;

    private static SnapshotRow[] snapshotRows = new SnapshotRow[initialRowCapacity];
    private static int snapshotRowCount;
    private static final long[] snapshotBucketCalls = new long[bucketCount];
    private static final long[] snapshotBucketTotalNanos = new long[bucketCount];

    private static long selfUiRefreshNanos;
    private static long selfCopyNanos;
    private static long selfSnapshotNanos;
    private static final ThreadLocal<HashMap<String, Integer>> activeModules = ThreadLocal.withInitial(HashMap::new);

    static{
        for(int i = 0; i < snapshotRows.length; i++){
            snapshotRows[i] = new SnapshotRow();
        }
        registerModules();
    }

    private NeonProfiler(){
    }

    public static synchronized void setEnabled(boolean value){
        rotateBucketsIfNeeded(nowMillis());
        enabled = value;
        if(value){
            enableEpochMillis = nowMillis();
        }
    }

    public static synchronized boolean isEnabled(){
        return enabled;
    }

    public static synchronized void setShowAll(boolean value){
        showAll = value;
    }

    public static synchronized boolean isShowAll(){
        return showAll;
    }

    public static synchronized void reset(){
        for(int i = 0; i < size; i++){
            BucketSeries bucket = series[i];
            if(bucket != null){
                bucket.reset();
            }
        }
        selfUiRefreshNanos = 0L;
        selfCopyNanos = 0L;
        selfSnapshotNanos = 0L;
        long now = nowMillis();
        windowStartMillis = now;
        lastRotateMillis = now;
        if(enabled){
            enableEpochMillis = now;
        }
    }

    public static Scope timeRoot(String module, String category, String operation, String threadGroup){
        if(!enabled) return Scope.disabled();
        return new Scope(module, category, operation, normalizeThread(threadGroup), true);
    }

    public static Scope timeDetail(String module, String category, String operation, String threadGroup){
        if(!enabled) return Scope.disabled();
        return new Scope(module, category, operation, normalizeThread(threadGroup), false);
    }

    public static void record(String module, String category, String operation, String threadGroup, long elapsedNanos, boolean rollup){
        if(!enabled || elapsedNanos <= 0L) return;
        recordInternal(module, category, operation, normalizeThread(threadGroup), elapsedNanos, 1L, rollup, false);
    }

    public static synchronized void addSelfUiRefresh(long elapsedNanos){
        if(elapsedNanos > 0L) selfUiRefreshNanos += elapsedNanos;
    }

    public static synchronized void addSelfCopy(long elapsedNanos){
        if(elapsedNanos > 0L) selfCopyNanos += elapsedNanos;
    }

    public static synchronized void addSelfSnapshot(long elapsedNanos){
        if(elapsedNanos > 0L) selfSnapshotNanos += elapsedNanos;
    }

    public static synchronized Snapshot snapshot(){
        long started = Time.nanos();
        rotateBucketsIfNeeded(nowMillis());
        ensureSnapshotCapacity(size * 4 + 64);
        snapshotRowCount = 0;

        long neonRollupTotalNanos = 0L;
        for(int i = 0; i < size; i++){
            RowKey key = keys[i];
            if(key == null || !key.rollup || "Profiler".equals(key.module)) continue;
            if(!"Module".equals(key.category) || !"total".equals(key.operation)) continue;
            neonRollupTotalNanos += series[i].totalNanos();
        }

        for(int i = 0; i < size; i++){
            RowKey key = keys[i];
            BucketSeries bucket = series[i];
            if(key == null || bucket == null) continue;

            long calls = bucket.totalCalls();
            long totalNanos = bucket.totalNanos();
            long maxNanos = bucket.maxNanos();
            boolean active = calls > 0L;
            if(!active) continue;

            SnapshotRow row = snapshotRows[snapshotRowCount++];
            row.module = key.module;
            row.category = key.category;
            row.operation = key.operation;
            row.threadGroup = key.threadGroup;
            row.rollup = key.rollup;
            row.selfCost = key.selfCost;
            row.calls = calls;
            row.totalNanos = totalNanos;
            row.maxNanos = maxNanos;
            row.msPerSecond = totalNanos / 1_000_000.0 / bucketCount;
            row.avgMs = calls <= 0L ? 0.0 : (totalNanos / 1_000_000.0) / calls;
            row.maxMs = maxNanos / 1_000_000.0;
            row.callsPerSecond = calls / (double)bucketCount;
            row.percentOfNeon = key.selfCost || !key.rollup || neonRollupTotalNanos <= 0L ? 0.0 : totalNanos * 100.0 / neonRollupTotalNanos;
            row.active = calls > 0L;
        }

        Arrays.sort(snapshotRows, 0, snapshotRowCount, SnapshotRow.sorter);

        Snapshot snapshot = new Snapshot();
        snapshot.rows = Arrays.copyOf(snapshotRows, snapshotRowCount);
        snapshot.neonTotalMsPerSecond = neonRollupTotalNanos / 1_000_000.0 / bucketCount;
        snapshot.windowSeconds = bucketCount;
        snapshot.enabled = enabled;
        snapshot.showAll = showAll;
        snapshot.topN = 10;
        snapshot.selfUiRefreshMsPerSecond = selfUiRefreshNanos / 1_000_000.0 / bucketCount;
        snapshot.selfCopyMsPerSecond = selfCopyNanos / 1_000_000.0 / bucketCount;
        snapshot.selfSnapshotMsPerSecond = selfSnapshotNanos / 1_000_000.0 / bucketCount;
        snapshot.selfTotalMsPerSecond = snapshot.selfUiRefreshMsPerSecond + snapshot.selfCopyMsPerSecond + snapshot.selfSnapshotMsPerSecond;
        snapshot.enabledMillis = enabled && enableEpochMillis > 0L ? Math.max(0L, nowMillis() - enableEpochMillis) : 0L;
        Runtime runtime = Runtime.getRuntime();
        snapshot.memUsedBytes = runtime.totalMemory() - runtime.freeMemory();
        snapshot.memFreeBytes = runtime.freeMemory();
        snapshot.memTotalBytes = runtime.totalMemory();
        snapshot.memMaxBytes = runtime.maxMemory();
        addSelfSnapshot(Time.timeSinceNanos(started));
        return snapshot;
    }

    public static String copySummaryText(){
        long started = Time.nanos();
        Snapshot snapshot = snapshot();
        StringBuilder out = new StringBuilder(4096);
        out.append("Neon Profiler");
        out.append(" | window=").append(snapshot.windowSeconds).append("s");
        out.append(" | neon=").append(formatMs(snapshot.neonTotalMsPerSecond)).append(" ms/s");
        out.append(" | self=").append(formatMs(snapshot.selfTotalMsPerSecond)).append(" ms/s");
        out.append('\n');
        out.append("Memory used=").append(formatMiB(snapshot.memUsedBytes));
        out.append("MiB total=").append(formatMiB(snapshot.memTotalBytes));
        out.append("MiB max=").append(formatMiB(snapshot.memMaxBytes));
        out.append("MiB\n");

        int detailCount = 0;
        for(SnapshotRow row : snapshot.rows){
            if(!snapshot.showAll && detailCount >= snapshot.topN && !row.rollup) continue;
            if(row.selfCost) continue;
            if(!row.rollup){
                detailCount++;
            }
            out.append(row.module).append(" / ").append(row.category).append(" / ").append(row.operation);
            out.append(" [").append(row.threadGroup).append(']');
            out.append(" | ").append(formatMs(row.msPerSecond)).append(" ms/s");
            out.append(" | avg ").append(formatMs(row.avgMs)).append(" ms");
            out.append(" | max ").append(formatMs(row.maxMs)).append(" ms");
            out.append(" | ").append(formatCalls(row.callsPerSecond)).append(" calls/s");
            if(row.rollup){
                out.append(" | ").append(formatPercent(row.percentOfNeon)).append("% Neon");
            }
            out.append('\n');
        }

        addSelfCopy(Time.timeSinceNanos(started));
        return out.toString().trim();
    }

    private static void registerModules(){
        for(String module : registeredModules){
            ensureIdleRollup(module, threadMain);
            ensureIdleRollup(module, threadAsync);
        }
    }

    private static void ensureIdleRollup(String module, String threadGroup){
        indexOf(module, "Module", "total", threadGroup, true, false);
    }

    private static synchronized void recordInternal(String module, String category, String operation, String threadGroup, long elapsedNanos, long calls, boolean rollup, boolean selfCost){
        rotateBucketsIfNeeded(nowMillis());
        int index = indexOf(module, category, operation, threadGroup, rollup, selfCost);
        series[index].add(elapsedNanos, calls);

    }

    private static int indexOf(String module, String category, String operation, String threadGroup, boolean rollup, boolean selfCost){
        for(int i = 0; i < size; i++){
            RowKey key = keys[i];
            if(key != null && key.matches(module, category, operation, threadGroup, rollup, selfCost)){
                return i;
            }
        }
        ensureCapacity(size + 1);
        keys[size] = new RowKey(module, category, operation, threadGroup, rollup, selfCost);
        series[size] = new BucketSeries();
        return size++;
    }

    private static void ensureCapacity(int desired){
        if(desired <= keys.length) return;
        int next = Math.max(desired, keys.length * 2);
        keys = Arrays.copyOf(keys, next);
        series = Arrays.copyOf(series, next);
    }

    private static void ensureSnapshotCapacity(int desired){
        if(desired <= snapshotRows.length) return;
        int next = Math.max(desired, snapshotRows.length * 2);
        SnapshotRow[] grown = new SnapshotRow[next];
        System.arraycopy(snapshotRows, 0, grown, 0, snapshotRows.length);
        for(int i = snapshotRows.length; i < grown.length; i++){
            grown[i] = new SnapshotRow();
        }
        snapshotRows = grown;
    }

    private static synchronized void rotateBucketsIfNeeded(long nowMillis){
        long elapsed = nowMillis - lastRotateMillis;
        if(elapsed < 1000L) return;
        int shift = (int)Math.min(bucketCount, elapsed / 1000L);
        for(int i = 0; i < size; i++){
            BucketSeries bucket = series[i];
            if(bucket != null){
                bucket.rotate(shift);
            }
        }
        lastRotateMillis += shift * 1000L;
        if(nowMillis - windowStartMillis > bucketCount * 1000L){
            windowStartMillis = nowMillis - bucketCount * 1000L;
        }
    }

    private static String normalizeThread(String value){
        if(value == null || value.trim().isEmpty()) return threadMain;
        return value;
    }

    private static long nowMillis(){
        return System.currentTimeMillis();
    }

    private static String formatMs(double value){
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private static String formatCalls(double value){
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static String formatPercent(double value){
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static String formatMiB(long bytes){
        return String.format(Locale.ROOT, "%.1f", bytes / 1024.0 / 1024.0);
    }

    private static boolean enterModule(String module){
        HashMap<String, Integer> counts = activeModules.get();
        Integer current = counts.get(module);
        counts.put(module, current == null ? 1 : current + 1);
        return current == null || current <= 0;
    }

    private static void exitModule(String module){
        HashMap<String, Integer> counts = activeModules.get();
        Integer current = counts.get(module);
        if(current == null) return;
        if(current <= 1){
            counts.remove(module);
        }else{
            counts.put(module, current - 1);
        }
    }

    public static final class Scope implements AutoCloseable{
        private static final Scope disabled = new Scope();

        private final String module;
        private final String category;
        private final String operation;
        private final String threadGroup;
        private final boolean rollup;
        private final long started;
        private final boolean countTowardModuleTotal;
        private final boolean active;

        private Scope(){
            module = null;
            category = null;
            operation = null;
            threadGroup = null;
            rollup = false;
            started = 0L;
            countTowardModuleTotal = false;
            active = false;
        }

        private Scope(String module, String category, String operation, String threadGroup, boolean rollup){
            this.module = module;
            this.category = category;
            this.operation = operation;
            this.threadGroup = threadGroup;
            this.rollup = rollup;
            this.started = Time.nanos();
            this.countTowardModuleTotal = enterModule(module);
            this.active = true;
        }

        public static Scope disabled(){
            return disabled;
        }

        @Override
        public void close(){
            if(!active) return;
            long elapsed = Time.timeSinceNanos(started);
            try{
                if(!enabled || elapsed <= 0L) return;
                recordInternal(module, category, operation, threadGroup, elapsed, 1L, rollup, false);
                if(countTowardModuleTotal){
                    recordInternal(module, "Module", "total", threadGroup, elapsed, 1L, true, false);
                }
            }finally{
                exitModule(module);
            }
        }
    }

    private static final class RowKey{
        final String module;
        final String category;
        final String operation;
        final String threadGroup;
        final boolean rollup;
        final boolean selfCost;

        RowKey(String module, String category, String operation, String threadGroup, boolean rollup, boolean selfCost){
            this.module = module;
            this.category = category;
            this.operation = operation;
            this.threadGroup = threadGroup;
            this.rollup = rollup;
            this.selfCost = selfCost;
        }

        boolean matches(String module, String category, String operation, String threadGroup, boolean rollup, boolean selfCost){
            return this.rollup == rollup
                && this.selfCost == selfCost
                && this.module.equals(module)
                && this.category.equals(category)
                && this.operation.equals(operation)
                && this.threadGroup.equals(threadGroup);
        }
    }

    private static final class BucketSeries{
        final long[] calls = new long[bucketCount];
        final long[] totalNanos = new long[bucketCount];
        final long[] maxNanos = new long[bucketCount];
        int current;

        void add(long nanos, long count){
            calls[current] += count;
            totalNanos[current] += nanos;
            if(nanos > maxNanos[current]){
                maxNanos[current] = nanos;
            }
        }

        void rotate(int steps){
            if(steps <= 0) return;
            for(int i = 0; i < steps; i++){
                current = (current + 1) % bucketCount;
                calls[current] = 0L;
                totalNanos[current] = 0L;
                maxNanos[current] = 0L;
            }
        }

        long totalCalls(){
            long total = 0L;
            for(long call : calls){
                total += call;
            }
            return total;
        }

        long totalNanos(){
            long total = 0L;
            for(long value : totalNanos){
                total += value;
            }
            return total;
        }

        long maxNanos(){
            long max = 0L;
            for(long value : maxNanos){
                if(value > max) max = value;
            }
            return max;
        }

        void reset(){
            Arrays.fill(calls, 0L);
            Arrays.fill(totalNanos, 0L);
            Arrays.fill(maxNanos, 0L);
            current = 0;
        }
    }

    public static final class Snapshot{
        public SnapshotRow[] rows;
        public double neonTotalMsPerSecond;
        public int windowSeconds;
        public boolean enabled;
        public boolean showAll;
        public int topN;
        public double selfUiRefreshMsPerSecond;
        public double selfCopyMsPerSecond;
        public double selfSnapshotMsPerSecond;
        public double selfTotalMsPerSecond;
        public long enabledMillis;
        public long memUsedBytes;
        public long memFreeBytes;
        public long memTotalBytes;
        public long memMaxBytes;
    }

    public static final class SnapshotRow{
        private static final Comparator<SnapshotRow> sorter = (a, b) -> {
            if(a.rollup != b.rollup) return a.rollup ? -1 : 1;
            int metricCompare = Double.compare(b.msPerSecond, a.msPerSecond);
            if(metricCompare != 0) return metricCompare;
            int moduleCompare = a.module.compareToIgnoreCase(b.module);
            if(moduleCompare != 0) return moduleCompare;
            int threadCompare = a.threadGroup.compareToIgnoreCase(b.threadGroup);
            if(threadCompare != 0) return threadCompare;
            int categoryCompare = a.category.compareToIgnoreCase(b.category);
            if(categoryCompare != 0) return categoryCompare;
            return a.operation.compareToIgnoreCase(b.operation);
        };

        public String module;
        public String category;
        public String operation;
        public String threadGroup;
        public boolean rollup;
        public boolean selfCost;
        public boolean active;
        public long calls;
        public long totalNanos;
        public long maxNanos;
        public double msPerSecond;
        public double avgMs;
        public double maxMs;
        public double callsPerSecond;
        public double percentOfNeon;
    }
}
