package pinyinsearchsupport.match;

import java.util.Locale;

public final class MatchEngine{
    private MatchEngine(){}

    public static boolean accepts(String rawText, String query, MatchOptions opts){
        if(query == null || query.isEmpty()) return true;
        if(rawText == null || rawText.isEmpty()) return false;

        String q = query.toLowerCase(Locale.ROOT);
        // strip user separators, keep letters/digits/CJK
        StringBuilder qb = new StringBuilder(q.length());
        for(int i = 0; i < q.length(); i++){
            char c = q.charAt(i);
            if(Character.isLetterOrDigit(c) || PinyinIndex.isCjk(c)){
                qb.append(c);
            }
        }
        String qn = qb.toString();
        if(qn.isEmpty()) return true;

        PinyinIndex.Profile p = PinyinIndex.of(rawText);

        // 1. raw text lower-case contains (handles CJK direct match too)
        if(p.lower.contains(qn)) return true;

        // query contains CJK: pinyin channel not applicable
        if(hasCjk(qn)) return false;

        // 2. full pinyin contains
        if(p.pinyinFull.contains(qn)) return true;

        // 3. initials contains (only for short queries to avoid false positives)
        if(opts.initials && qn.length() >= 2 && qn.length() <= 8 && p.initials.contains(qn)) return true;

        // 4. heteronym / fuzzy walk match
        if(opts.heteronym || opts.fuzzy){
            if(walkMatch(p.tokens, qn, opts.fuzzy)) return true;
        }

        return false;
    }

    private static boolean hasCjk(String s){
        for(int i = 0; i < s.length(); i++){
            if(PinyinIndex.isCjk(s.charAt(i))) return true;
        }
        return false;
    }

    private static boolean walkMatch(String[][] tokens, String query, boolean fuzzy){
        for(int start = 0; start < tokens.length; start++){
            if(tryFrom(tokens, start, query, 0, fuzzy)) return true;
        }
        return false;
    }

    private static boolean tryFrom(String[][] tokens, int ti, String q, int qi, boolean fuzzy){
        if(qi >= q.length()) return true;
        if(ti >= tokens.length) return false;
        String[] cands = tokens[ti];
        if(cands == null || cands.length == 0){
            // skip decorative characters like "·"
            return tryFrom(tokens, ti + 1, q, qi, fuzzy);
        }
        for(String reading : cands){
            if(reading == null || reading.isEmpty()) continue;

            // try matching full reading or prefix of reading
            int eaten = consumeExpanded(q, qi, reading, fuzzy);
            if(eaten > 0){
                if(qi + eaten == q.length()) return true;
                if(eaten == reading.length()){
                    if(tryFrom(tokens, ti + 1, q, qi + eaten, fuzzy)) return true;
                }
            }

            // try matching only the initial of this reading
            char init = reading.charAt(0);
            if(q.charAt(qi) == init){
                if(qi + 1 == q.length()) return true;
                if(tryFrom(tokens, ti + 1, q, qi + 1, fuzzy)) return true;
            }
        }
        return false;
    }

    /**
     * Returns how many characters of `reading` are matched by `q` starting at `qi`.
     * Returns 0 if no match at all. Returns reading.length() for a full match.
     * Fuzzy: z/c/s in query can match zh/ch/sh in reading; trailing g after n can be omitted.
     */
    private static int consumeExpanded(String q, int qi, String reading, boolean fuzzy){
        int qp = qi;
        int rp = 0;
        while(rp < reading.length() && qp < q.length()){
            char rc = reading.charAt(rp);
            char qc = q.charAt(qp);
            if(rc == qc){
                rp++;
                qp++;
                continue;
            }
            if(fuzzy){
                // reading has zh/ch/sh but query has z/c/s
                if(rp + 1 < reading.length()
                    && reading.charAt(rp + 1) == 'h'
                    && (rc == 'z' || rc == 'c' || rc == 's')
                    && qc == rc){
                    rp += 2;
                    qp += 1;
                    continue;
                }
            }
            // mismatch
            return 0;
        }
        // fuzzy: reading ends with 'g' after 'n' (ang/eng/ing/iang/uang) and query ran out
        if(fuzzy && rp < reading.length() && qp == q.length()){
            if(reading.charAt(rp) == 'g' && rp > 0 && reading.charAt(rp - 1) == 'n'){
                return rp; // partial match up to before the trailing g
            }
        }
        return rp;
    }

    public static final class MatchOptions{
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
