package serverplayerdatabase;

import arc.Core;
import arc.struct.IntMap;
import arc.struct.Seq;
import arc.util.Log;
import arc.util.Time;
import bektools.profiler.NeonProfiler;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Comparator;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class EmbeddingIndex implements AutoCloseable{
    private static final int rebuildBatchSize = 100;
    private static final String metadataModelKey = "embedding.model";
    private static final float boostDelta = 0.05f;
    private static final float penalizeDelta = 0.08f;
    private static final Pattern hardExcludePattern = Pattern.compile("^-{3,}(\\S+)$");
    private static final Pattern penalizePattern = Pattern.compile("^-(\\S+)$");
    private static final Pattern boostPattern = Pattern.compile("^\\+(\\S+)$");

    private final EmbeddingEngine engine;
    private final ServerPlayerDataBaseMod.ChatDatabase chatDb;
    private final ExecutorService executor;
    private final IntMap<EntryVector> vectors = new IntMap<>();

    private volatile boolean available = true;
    private volatile boolean ready;
    private volatile boolean rebuilding;
    private volatile boolean dirty;
    private volatile String status = "未初始化";
    private volatile String failureReason;
    private volatile int indexedEntries;
    private volatile int totalEntries;
    private volatile long lastPersistAt;

    public EmbeddingIndex(EmbeddingEngine engine, ServerPlayerDataBaseMod.ChatDatabase chatDb){
        this.engine = engine;
        this.chatDb = chatDb;
        this.executor = Executors.newSingleThreadExecutor(new ThreadFactory(){
            @Override
            public Thread newThread(Runnable r){
                Thread thread = new Thread(r, "spdb-embedding-index");
                thread.setDaemon(true);
                return thread;
            }
        });
    }

    public void initialize(){
        if(!chatDb.usesSqlite()){
            available = false;
            failureReason = "当前聊天后端不是 SQLite，语义搜索不可用。";
            status = failureReason;
            return;
        }

        executor.execute(() -> {
            try{
                loadOrRebuild();
            }catch(Throwable t){
                available = false;
                ready = false;
                rebuilding = false;
                failureReason = "语义索引初始化失败: " + safeMessage(t);
                status = failureReason;
                Log.err("SPDB: failed to initialize embedding index.", t);
            }
        });
    }

    public boolean isAvailable(){
        return available;
    }

    public boolean isReady(){
        return ready;
    }

    public boolean isRebuilding(){
        return rebuilding;
    }

    public boolean isDirty(){
        return dirty;
    }

    public String status(){
        return status;
    }

    public String failureReason(){
        return failureReason;
    }

    public int indexedEntries(){
        return indexedEntries;
    }

    public int totalEntries(){
        return totalEntries;
    }

    public long lastPersistAt(){
        return lastPersistAt;
    }

    public void rebuildAsync(){
        if(!available) return;
        executor.execute(() -> {
            try(NeonProfiler.Scope ignored = NeonProfiler.timeDetail("SPDB", "Async", "embeddingRebuild", NeonProfiler.threadAsync)){
            try{
                rebuildAll();
            }catch(Throwable t){
                ready = false;
                rebuilding = false;
                failureReason = "重建语义索引失败: " + safeMessage(t);
                status = failureReason;
                Log.err("SPDB: failed rebuilding embedding index.", t);
            }
            }
        });
    }

    public void addEntryAsync(int entryId, ServerPlayerDataBaseMod.ChatEntry entry){
        if(!available || entryId <= 0 || entry == null || entry.message == null || entry.message.trim().isEmpty()) return;

        executor.execute(() -> {
            try(NeonProfiler.Scope ignored = NeonProfiler.timeDetail("SPDB", "Async", "embeddingAddEntry", NeonProfiler.threadAsync)){
            try{
                float[] vector = engine.embed(entry.message);
                synchronized(vectors){
                    boolean existed = vectors.get(entryId) != null;
                    vectors.put(entryId, new EntryVector(entryId, entry.copy(), vector));
                    indexedEntries = vectors.size;
                    totalEntries = existed ? Math.max(totalEntries, indexedEntries) : Math.max(totalEntries + 1, indexedEntries);
                }
                writeVector(entryId, vector);
                dirty = true;
                lastPersistAt = Time.millis();
                updateReadyStatus();
            }catch(Throwable t){
                Log.err("SPDB: failed adding embedding for chat entry @.", entryId, t);
            }
            }
        });
    }

    public Seq<SearchResult> search(String query, int limit){
        try(NeonProfiler.Scope ignored = NeonProfiler.timeDetail("SPDB", "Async", "embeddingSearch", NeonProfiler.threadAsync)){
        Seq<SearchResult> out = new Seq<>();
        if(!available || !ready) return out;

        String trimmed = query == null ? "" : query.trim();
        if(trimmed.isEmpty()) return out;

        ParsedQuery parsed = parseQuery(trimmed);
        float[] queryVector = engine.embed(parsed.semanticText);
        Seq<SearchResult> all = new Seq<>();
        synchronized(vectors){
            for(IntMap.Entry<EntryVector> entry : vectors){
                EntryVector value = entry.value;
                if(value == null || value.vector == null || value.chat == null) continue;
                float finalScore = cosine(queryVector, value.vector);
                for(String term : parsed.boostTerms){
                    if(matchesTerm(value.chat, term)){
                        finalScore += boostDelta;
                    }
                }
                for(String term : parsed.penalizeTerms){
                    if(matchesTerm(value.chat, term)){
                        finalScore -= penalizeDelta;
                    }
                }
                all.add(new SearchResult(value.entryId, value.chat.copy(), finalScore));
            }
        }

        all.sort(Comparator.comparingDouble((SearchResult result) -> result.score).reversed());
        int capped = Math.max(0, limit);
        for(int i = 0; i < all.size && out.size < capped; i++){
            SearchResult hit = all.get(i);
            if(matchesAny(hit.chat, parsed.hardExclude)) continue;
            out.add(hit);
        }
        return out;
        }
    }

    public void flushIfDirty(){
        if(!available || !dirty) return;
        executor.execute(() -> {
            try(NeonProfiler.Scope ignored = NeonProfiler.timeDetail("SPDB", "Async", "embeddingFlush", NeonProfiler.threadAsync)){
            dirty = false;
            lastPersistAt = Time.millis();
            }
        });
    }

    @Override
    public void close(){
        executor.shutdownNow();
    }

    private void loadOrRebuild() throws Exception{
        ensureSchema();
        if(invalidateVectorsIfModelChanged()){
            status = "检测到语义模型已切换，正在重建搜索索引 0/0";
        }
        totalEntries = countIndexableEntries();
        loadAllVectors();
        updateReadyStatus();
        if(ready){
            ready = true;
            rebuilding = false;
            dirty = false;
            status = "索引就绪，共 " + indexedEntries + " 条";
            return;
        }

        resumeBuild(findResumeCheckpoint());
    }

    private void rebuildAll() throws Exception{
        rebuilding = true;
        ready = false;
        totalEntries = countIndexableEntries();
        status = "正在建立搜索索引 0/" + totalEntries;

        synchronized(vectors){
            vectors.clear();
            indexedEntries = 0;
        }

        try(Connection conn = chatDb.openSqliteConnection()){
            conn.setAutoCommit(false);
            try(Statement clear = conn.createStatement()){
                clear.executeUpdate("delete from chat_vectors");
            }
            writeStoredModelId(conn, engine.modelId());
            conn.commit();
        }

        resumeBuild(new ResumeCheckpoint(0, indexedEntries));
    }

    private void resumeBuild(ResumeCheckpoint checkpoint) throws Exception{
        rebuilding = true;
        ready = false;
        dirty = false;
        totalEntries = countIndexableEntries();

        if(indexedEntries > 0){
            status = "检测到未完成索引，已恢复 " + indexedEntries + "/" + totalEntries + "，继续补齐缺失部分";
        }else{
            status = "正在建立搜索索引 0/" + totalEntries;
        }

        int lastId = Math.max(0, checkpoint.afterId);
        while(true){
            Seq<SqlChatRow> batch = loadBatch(lastId, rebuildBatchSize);
            if(batch.isEmpty()) break;

            for(SqlChatRow row : batch){
                lastId = row.id;
                if(row.message == null || row.message.trim().isEmpty()) continue;
                if(hasVector(row.id)) continue;

                float[] vector = engine.embed(row.message);
                ServerPlayerDataBaseMod.ChatEntry chat = new ServerPlayerDataBaseMod.ChatEntry();
                chat.uid = row.uid;
                chat.senderName = row.senderName;
                chat.message = row.message;
                chat.server = row.server;
                chat.time = row.time;

                synchronized(vectors){
                    vectors.put(row.id, new EntryVector(row.id, chat, vector));
                    indexedEntries = vectors.size;
                }
                writeVector(row.id, vector);
                dirty = true;
                lastPersistAt = Time.millis();
                totalEntries = Math.max(totalEntries, indexedEntries);
            }

            status = "正在建立搜索索引 " + indexedEntries + "/" + totalEntries;
        }

        rebuilding = false;
        dirty = false;
        lastPersistAt = Time.millis();
        updateReadyStatus();
        status = ready ? "索引就绪，共 " + indexedEntries + " 条" : "索引未完成";
    }

    private void updateReadyStatus(){
        ready = available && !rebuilding && indexedEntries == totalEntries;
    }

    private void ensureSchema() throws Exception{
        try(Connection conn = chatDb.openSqliteConnection();
            Statement stmt = conn.createStatement()){
            stmt.execute("create table if not exists chat_vectors (entry_id integer primary key references chat_entries(id) on delete cascade, vector blob not null)");
            stmt.execute("create index if not exists idx_chat_vectors_entry on chat_vectors(entry_id)");
            stmt.execute("create table if not exists spdb_meta (key text primary key, value text not null)");
        }
    }

    private boolean invalidateVectorsIfModelChanged() throws Exception{
        String currentModelId = engine.modelId();
        String storedModelId = readStoredModelId();
        if(storedModelId == null || storedModelId.trim().isEmpty()){
            persistModelId(currentModelId);
            return false;
        }
        if(storedModelId.equals(currentModelId)){
            return false;
        }

        Log.info("SPDB: embedding model changed from '@' to '@', clearing cached vectors.", storedModelId, currentModelId);
        synchronized(vectors){
            vectors.clear();
            indexedEntries = 0;
        }

        try(Connection conn = chatDb.openSqliteConnection()){
            conn.setAutoCommit(false);
            try(Statement stmt = conn.createStatement()){
                stmt.executeUpdate("delete from chat_vectors");
            }
            writeStoredModelId(conn, currentModelId);
            conn.commit();
        }
        dirty = false;
        return true;
    }

    private String readStoredModelId() throws Exception{
        try(Connection conn = chatDb.openSqliteConnection();
            PreparedStatement stmt = conn.prepareStatement("select value from spdb_meta where key = ?")){
            stmt.setString(1, metadataModelKey);
            try(ResultSet rs = stmt.executeQuery()){
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    private void persistModelId(String modelId) throws Exception{
        try(Connection conn = chatDb.openSqliteConnection()){
            writeStoredModelId(conn, modelId);
        }
    }

    private void writeStoredModelId(Connection conn, String modelId) throws Exception{
        try(PreparedStatement stmt = conn.prepareStatement("insert or replace into spdb_meta(key, value) values (?, ?)")){
            stmt.setString(1, metadataModelKey);
            stmt.setString(2, modelId);
            stmt.executeUpdate();
        }
    }

    private int countIndexableEntries() throws Exception{
        try(Connection conn = chatDb.openSqliteConnection();
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("select count(*) from chat_entries where message is not null and trim(message) <> ''")){
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private void loadAllVectors() throws Exception{
        synchronized(vectors){
            vectors.clear();
        }

        try(Connection conn = chatDb.openSqliteConnection();
            PreparedStatement stmt = conn.prepareStatement(
                "select e.id, e.uid, e.sender_name, e.message, e.server, e.time, v.vector " +
                "from chat_entries e join chat_vectors v on v.entry_id = e.id order by e.id asc"
            );
            ResultSet rs = stmt.executeQuery()){
            while(rs.next()){
                int id = rs.getInt(1);
                ServerPlayerDataBaseMod.ChatEntry chat = new ServerPlayerDataBaseMod.ChatEntry();
                chat.uid = rs.getString(2);
                chat.senderName = rs.getString(3);
                chat.message = rs.getString(4);
                chat.server = rs.getString(5);
                chat.time = rs.getLong(6);
                float[] vector = decodeVector(rs.getBytes(7));
                synchronized(vectors){
                    vectors.put(id, new EntryVector(id, chat, vector));
                }
            }
        }

        indexedEntries = vectors.size;
        totalEntries = countIndexableEntries();
    }

    private Seq<SqlChatRow> loadBatch(int afterId, int limit) throws Exception{
        Seq<SqlChatRow> rows = new Seq<>();
        try(Connection conn = chatDb.openSqliteConnection();
            PreparedStatement stmt = conn.prepareStatement(
                "select id, uid, sender_name, message, server, time from chat_entries where id > ? order by id asc limit ?"
            )){
            stmt.setInt(1, Math.max(0, afterId));
            stmt.setInt(2, Math.max(1, limit));
            try(ResultSet rs = stmt.executeQuery()){
                while(rs.next()){
                    SqlChatRow row = new SqlChatRow();
                    row.id = rs.getInt(1);
                    row.uid = rs.getString(2);
                    row.senderName = rs.getString(3);
                    row.message = rs.getString(4);
                    row.server = rs.getString(5);
                    row.time = rs.getLong(6);
                    rows.add(row);
                }
            }
        }
        return rows;
    }

    private void writeVector(int entryId, float[] vector) throws Exception{
        try(Connection conn = chatDb.openSqliteConnection();
            PreparedStatement stmt = conn.prepareStatement("insert or replace into chat_vectors(entry_id, vector) values (?, ?)")){
            stmt.setInt(1, entryId);
            stmt.setBytes(2, encodeVector(vector));
            stmt.executeUpdate();
        }
    }

    private ResumeCheckpoint findResumeCheckpoint() throws Exception{
        int afterId = 0;
        int contiguousIndexed = 0;
        try(Connection conn = chatDb.openSqliteConnection();
            PreparedStatement stmt = conn.prepareStatement(
                "select e.id, e.message, case when v.entry_id is null then 0 else 1 end " +
                "from chat_entries e left join chat_vectors v on v.entry_id = e.id order by e.id asc"
            );
            ResultSet rs = stmt.executeQuery()){
            while(rs.next()){
                int id = rs.getInt(1);
                String message = rs.getString(2);
                boolean indexable = message != null && !message.trim().isEmpty();
                boolean hasVector = rs.getInt(3) != 0;

                if(!indexable){
                    afterId = id;
                    continue;
                }
                if(!hasVector) break;

                afterId = id;
                contiguousIndexed++;
            }
        }
        return new ResumeCheckpoint(afterId, contiguousIndexed);
    }

    private boolean hasVector(int entryId){
        synchronized(vectors){
            return vectors.get(entryId) != null;
        }
    }

    private ParsedQuery parseQuery(String raw){
        ParsedQuery parsed = new ParsedQuery();
        if(raw == null || raw.trim().isEmpty()) return parsed;

        StringBuilder semantic = new StringBuilder();
        for(String token : raw.trim().split("\\s+")){
            if(token == null || token.isEmpty()) continue;
            if(isBareOperator(token)) continue;
            if(collectTerm(token, hardExcludePattern, parsed.hardExclude)) continue;
            if(collectTerm(token, boostPattern, parsed.boostTerms)) continue;
            if(collectTerm(token, penalizePattern, parsed.penalizeTerms)) continue;
            if(semantic.length() > 0) semantic.append(' ');
            semantic.append(token);
        }
        parsed.semanticText = semantic.toString();
        return parsed;
    }

    private static boolean collectTerm(String token, Pattern pattern, Seq<String> out){
        Matcher matcher = pattern.matcher(token);
        if(!matcher.matches()) return false;

        String term = matcher.group(1);
        if(term == null || term.isEmpty()) return false;
        char first = term.charAt(0);
        if(first == '+' || first == '-') return false;

        addUnique(out, term.toLowerCase(Locale.ROOT));
        return true;
    }

    private static void addUnique(Seq<String> out, String term){
        for(String existing : out){
            if(existing.equals(term)) return;
        }
        out.add(term);
    }

    private static boolean isBareOperator(String token){
        for(int i = 0; i < token.length(); i++){
            char ch = token.charAt(i);
            if(ch != '+' && ch != '-') return false;
        }
        return true;
    }

    private static boolean matchesAny(ServerPlayerDataBaseMod.ChatEntry chat, Seq<String> terms){
        for(String term : terms){
            if(matchesTerm(chat, term)) return true;
        }
        return false;
    }

    private static boolean matchesTerm(ServerPlayerDataBaseMod.ChatEntry chat, String term){
        return containsIgnoreCase(chat == null ? null : chat.message, term)
            || containsIgnoreCase(chat == null ? null : chat.uid, term)
            || containsIgnoreCase(chat == null ? null : chat.senderName, term)
            || containsIgnoreCase(chat == null ? null : chat.server, term);
    }

    private static boolean containsIgnoreCase(String text, String term){
        if(text == null || term == null || term.isEmpty()) return false;
        return text.toLowerCase(Locale.ROOT).contains(term);
    }

    private static byte[] encodeVector(float[] vector){
        ByteBuffer buffer = ByteBuffer.allocate(vector.length * 4).order(ByteOrder.LITTLE_ENDIAN);
        for(float value : vector){
            buffer.putFloat(value);
        }
        return buffer.array();
    }

    private static float[] decodeVector(byte[] bytes){
        if(bytes == null || bytes.length == 0) return new float[0];
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        float[] vector = new float[bytes.length / 4];
        for(int i = 0; i < vector.length; i++){
            vector[i] = buffer.getFloat();
        }
        EmbeddingEngine.normalize(vector);
        return vector;
    }

    private static float cosine(float[] left, float[] right){
        int len = Math.min(left.length, right.length);
        float sum = 0f;
        for(int i = 0; i < len; i++){
            sum += left[i] * right[i];
        }
        return sum;
    }

    private static String safeMessage(Throwable t){
        String message = t == null ? null : t.getMessage();
        return message == null || message.trim().isEmpty() ? t.getClass().getSimpleName() : message;
    }

    private static final class SqlChatRow{
        int id;
        String uid;
        String senderName;
        String message;
        String server;
        long time;
    }

    private static final class EntryVector{
        final int entryId;
        final ServerPlayerDataBaseMod.ChatEntry chat;
        final float[] vector;

        EntryVector(int entryId, ServerPlayerDataBaseMod.ChatEntry chat, float[] vector){
            this.entryId = entryId;
            this.chat = chat;
            this.vector = vector;
        }
    }

    private static final class ResumeCheckpoint{
        final int afterId;
        final int contiguousIndexed;

        ResumeCheckpoint(int afterId, int contiguousIndexed){
            this.afterId = afterId;
            this.contiguousIndexed = contiguousIndexed;
        }
    }

    static final class ParsedQuery{
        String semanticText = "";
        Seq<String> boostTerms = new Seq<>();
        Seq<String> penalizeTerms = new Seq<>();
        Seq<String> hardExclude = new Seq<>();
    }

    public static final class SearchResult{
        public final int entryId;
        public final ServerPlayerDataBaseMod.ChatEntry chat;
        public final float score;

        public SearchResult(int entryId, ServerPlayerDataBaseMod.ChatEntry chat, float score){
            this.entryId = entryId;
            this.chat = chat;
            this.score = score;
        }
    }
}
