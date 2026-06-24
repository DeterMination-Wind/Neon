package foreignservertranslator;

import arc.Core;
import arc.util.Time;

public final class TokenStats{
    private static final String dayStartKey = "fst.tokens.dayStart";
    private static final String weekStartKey = "fst.tokens.weekStart";
    private static final String dayInputKey = "fst.tokens.dayInput";
    private static final String dayOutputKey = "fst.tokens.dayOutput";
    private static final String weekInputKey = "fst.tokens.weekInput";
    private static final String weekOutputKey = "fst.tokens.weekOutput";
    private static final String totalInputKey = "fst.tokens.totalInput";
    private static final String totalOutputKey = "fst.tokens.totalOutput";

    private static final long dayMillis = 24L * 60L * 60L * 1000L;
    private static final long weekMillis = 7L * dayMillis;

    private TokenStats(){
    }

    public static void init(){
        Core.settings.defaults(
            dayStartKey, 0L,
            weekStartKey, 0L,
            dayInputKey, 0L,
            dayOutputKey, 0L,
            weekInputKey, 0L,
            weekOutputKey, 0L,
            totalInputKey, 0L,
            totalOutputKey, 0L
        );
        ensureWindows();
    }

    public static void record(long inputTokens, long outputTokens){
        ensureWindows();
        add(dayInputKey, inputTokens);
        add(dayOutputKey, outputTokens);
        add(weekInputKey, inputTokens);
        add(weekOutputKey, outputTokens);
        add(totalInputKey, inputTokens);
        add(totalOutputKey, outputTokens);
    }

    public static Snapshot snapshot(){
        ensureWindows();
        return new Snapshot(
            Core.settings.getLong(dayInputKey, 0L),
            Core.settings.getLong(dayOutputKey, 0L),
            Core.settings.getLong(weekInputKey, 0L),
            Core.settings.getLong(weekOutputKey, 0L),
            Core.settings.getLong(totalInputKey, 0L),
            Core.settings.getLong(totalOutputKey, 0L)
        );
    }

    public static long estimateTokens(String value){
        if(value == null || value.isEmpty()) return 0L;

        long tokens = 0L;
        int asciiRun = 0;
        for(int i = 0; i < value.length(); ){
            int codepoint = value.codePointAt(i);
            i += Character.charCount(codepoint);

            if(Character.isWhitespace(codepoint)){
                tokens += asciiRunTokens(asciiRun);
                asciiRun = 0;
            }else if(isCjk(codepoint)){
                tokens += asciiRunTokens(asciiRun) + 1L;
                asciiRun = 0;
            }else if(codepoint < 128 && Character.isLetterOrDigit(codepoint)){
                asciiRun++;
            }else{
                tokens += asciiRunTokens(asciiRun) + 1L;
                asciiRun = 0;
            }
        }

        return Math.max(1L, tokens + asciiRunTokens(asciiRun));
    }

    private static long asciiRunTokens(int length){
        return length <= 0 ? 0L : Math.max(1L, (length + 3L) / 4L);
    }

    private static boolean isCjk(int codepoint){
        return (codepoint >= 0x3400 && codepoint <= 0x9fff)
            || (codepoint >= 0xf900 && codepoint <= 0xfaff)
            || (codepoint >= 0x3040 && codepoint <= 0x30ff)
            || (codepoint >= 0xac00 && codepoint <= 0xd7af);
    }

    private static void ensureWindows(){
        long now = Time.millis();
        long dayStart = Core.settings.getLong(dayStartKey, 0L);
        if(dayStart <= 0L || now - dayStart >= dayMillis){
            Core.settings.put(dayStartKey, now);
            Core.settings.put(dayInputKey, 0L);
            Core.settings.put(dayOutputKey, 0L);
        }

        long weekStart = Core.settings.getLong(weekStartKey, 0L);
        if(weekStart <= 0L || now - weekStart >= weekMillis){
            Core.settings.put(weekStartKey, now);
            Core.settings.put(weekInputKey, 0L);
            Core.settings.put(weekOutputKey, 0L);
        }
    }

    private static void add(String key, long amount){
        if(amount <= 0L) return;
        Core.settings.put(key, Core.settings.getLong(key, 0L) + amount);
    }

    public static final class Snapshot{
        public final long dayInput, dayOutput;
        public final long weekInput, weekOutput;
        public final long totalInput, totalOutput;

        Snapshot(long dayInput, long dayOutput, long weekInput, long weekOutput, long totalInput, long totalOutput){
            this.dayInput = dayInput;
            this.dayOutput = dayOutput;
            this.weekInput = weekInput;
            this.weekOutput = weekOutput;
            this.totalInput = totalInput;
            this.totalOutput = totalOutput;
        }
    }
}
