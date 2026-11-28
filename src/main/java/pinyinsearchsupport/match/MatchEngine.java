package pinyinsearchsupport.match;

import java.util.Locale;

public final class MatchEngine{
    private static final int MAX_QUERY_LENGTH = 64;
    private static final int MAX_WALK_TOKENS = 256;
    private static final int MAX_WALK_STEPS = 4096;

    private MatchEngine(){}

    public static boolean accepts(String rawText, String query, MatchOptions opts){
        if(query == null || query.isEmpty()) return true;
        if(rawText == null || rawText.isEmpty()) return false;

        MatchOptions safeOpts = opts != null ? opts : MatchOptions.DEFAULT;
        String qn = normalizeQuery(query);
        if(qn.isEmpty()) return true;

        PinyinIndex.Profile p = PinyinIndex.of(rawText);
        if(p == null || p.lower.isEmpty()) return false;

        // 1. Raw text lower-case contains (handles CJK, numbers, and ASCII directly).
        if(p.lower.contains(qn)) return true;

        // Query contains CJK: pinyin channels are not applicable.
        if(hasCjk(qn)) return false;

        // 2. Full pinyin contains.
        if(p.pinyinFull.contains(qn)) return true;

        // 3. Initials contains (short queries only to avoid false positives).
        if(safeOpts.initials && qn.length() >= 2 && qn.length() <= 8 && p.initials.contains(qn)) return true;

        // 4. Heteronym / fuzzy / mixed initial-full walk match with polynomial memoization.
        if(safeOpts.heteronym || safeOpts.fuzzy || safeOpts.initials){
            if(walkMatch(p.tokens, qn, safeOpts)) return true;
        }

        return false;
    }

    private static String normalizeQuery(String query){
        String q = query.toLowerCase(Locale.ROOT);
        int len = q.length();
        StringBuilder qb = new StringBuilder(Math.min(len, MAX_QUERY_LENGTH));
        int i = 0;
        int kept = 0;
        while(i < len && kept < MAX_QUERY_LENGTH){
            int cp = q.codePointAt(i);
            int charCount = Character.charCount(cp);
            i += charCount;
            if(charCount == 1 && Character.isSurrogate((char)cp)) continue;
            if(Character.isLetterOrDigit(cp) || PinyinIndex.isCjk(cp)){
                qb.appendCodePoint(cp);
                kept++;
            }
        }
        return qb.toString();
    }

    private static boolean hasCjk(String s){
        int len = s.length();
        int i = 0;
        while(i < len){
            int cp = s.codePointAt(i);
            if(PinyinIndex.isCjk(cp)) return true;
            i += Character.charCount(cp);
        }
        return false;
    }

    private static boolean walkMatch(String[][] tokens, String query, MatchOptions opts){
        if(tokens == null || tokens.length == 0 || query.isEmpty()) return false;
        int tokenLimit = Math.min(tokens.length, MAX_WALK_TOKENS);
        int qLen = query.length();
        // Memoization table of failed (ti, qi) states guarantees O(tokenLimit * qLen) worst-case time.
        boolean[] failed = new boolean[tokenLimit * (qLen + 1)];
        int[] steps = new int[]{0};

        for(int start = 0; start < tokenLimit; start++){
            String[] cands = tokens[start];
            if(cands == null || cands.length == 0) continue;
            if(steps[0] >= MAX_WALK_STEPS) return false;
            if(tryFrom(tokens, tokenLimit, start, query, 0, opts, failed, steps)) return true;
        }
        return false;
    }

    private static boolean tryFrom(String[][] tokens, int tokenLimit, int ti, String q, int qi,
                                   MatchOptions opts, boolean[] failed, int[] steps){
        int qLen = q.length();
        if(qi >= qLen) return true;

        // Skip decorative/empty tokens iteratively to prevent stack growth.
        while(ti < tokenLimit){
            String[] cands = tokens[ti];
            if(cands != null && cands.length > 0) break;
            ti++;
        }
        if(ti >= tokenLimit) return false;

        int stateIndex = ti * (qLen + 1) + qi;
        if(failed[stateIndex]) return false;
        if(++steps[0] > MAX_WALK_STEPS) return false;

        String[] cands = tokens[ti];
        int candLimit = opts.heteronym ? cands.length : 1;
        for(int c = 0; c < candLimit; c++){
            String reading = cands[c];
            if(reading == null || reading.isEmpty()) continue;

            // Match full reading or prefix of reading (returns packed query-eaten + full-match bit).
            int match = consumeExpanded(q, qi, reading, opts.fuzzy);
            if(match > 0){
                int queryEaten = match >>> 1;
                boolean fullReading = (match & 1) != 0;
                if(qi + queryEaten >= qLen) return true;
                if(fullReading){
                    if(tryFrom(tokens, tokenLimit, ti + 1, q, qi + queryEaten, opts, failed, steps)){
                        return true;
                    }
                }
            }

            // Optional single-initial step when initials matching is enabled.
            if(opts.initials){
                char init = reading.charAt(0);
                if(q.charAt(qi) == init){
                    if(qi + 1 >= qLen) return true;
                    if(tryFrom(tokens, tokenLimit, ti + 1, q, qi + 1, opts, failed, steps)){
                        return true;
                    }
                }
            }
        }

        failed[stateIndex] = true;
        return false;
    }

    /**
     * Matches `reading` against `q` starting at `qi`.
     * Returns 0 if no match. Otherwise returns `(queryCharsConsumed << 1) | (fullReadingMatched ? 1 : 0)`.
     * Fuzzy rules:
     * - z/c/s <-> zh/ch/sh at syllable initial
     * - trailing 'g' after 'n' (ang/eng/ing/iang/uang) may be omitted in query or added in query
     */
    private static int consumeExpanded(String q, int qi, String reading, boolean fuzzy){
        int qp = qi;
        int rp = 0;
        int qLen = q.length();
        int rLen = reading.length();

        while(rp < rLen && qp < qLen){
            char rc = reading.charAt(rp);
            char qc = q.charAt(qp);
            if(rc == qc){
                rp++;
                qp++;
                continue;
            }
            if(fuzzy){
                // reading has zh/ch/sh and query omitted 'h' (e.g. zong -> zhong)
                if(rc == 'h' && rp == 1 && isFlatRetroflex(reading.charAt(0))){
                    rp++;
                    continue;
                }
                // query has zh/ch/sh and reading is flat z/c/s (e.g. zhong -> zong)
                if(qc == 'h' && qp == qi + 1 && rp == 1 && isFlatRetroflex(reading.charAt(0))){
                    qp++;
                    continue;
                }
                // reading has trailing 'g' after 'n' and query omitted 'g' mid-query (e.g. fankuai -> fangkuai)
                if(rc == 'g' && rp == rLen - 1 && rp > 0 && reading.charAt(rp - 1) == 'n'){
                    rp++;
                    break;
                }
            }
            return 0;
        }

        // Fuzzy: query ran out right before reading's trailing 'g' after 'n' (e.g. fan -> fang)
        if(fuzzy && rp == rLen - 1 && rp > 0 && reading.charAt(rp) == 'g' && reading.charAt(rp - 1) == 'n'){
            rp++;
        }

        // Fuzzy: reading ended with 'n' (an/en/in) and query typed extra 'g' (e.g. fang -> fan)
        if(fuzzy && rp == rLen && qp < qLen && q.charAt(qp) == 'g' && rLen > 0 && reading.charAt(rLen - 1) == 'n'){
            qp++;
        }

        int queryConsumed = qp - qi;
        if(queryConsumed <= 0) return 0;

        boolean fullReading = rp >= rLen;
        return (queryConsumed << 1) | (fullReading ? 1 : 0);
    }

    private static boolean isFlatRetroflex(char c){
        return c == 'z' || c == 'c' || c == 's';
    }

    public static final class MatchOptions{
        public static final MatchOptions DEFAULT = new MatchOptions(true, true, true);

        public final boolean fuzzy;
        public final boolean initials;
        public final boolean heteronym;

        public MatchOptions(boolean fuzzy, boolean initials, boolean heteronym){
            this.fuzzy = fuzzy;
            this.initials = initials;
            this.heteronym = heteronym;
        }
    }
}
