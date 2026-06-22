package radialbuildmenu;

/**
 * Condition expression parser/evaluator for RBM.
 *
 * RBM 条件表达式解析与求值（用于“Conditional Switch”等高级功能）。
 * 这段逻辑原本堆在 {@link RadialBuildMenuMod} 文件末尾，拆出来后主类更容易阅读/维护，
 * 不改变语义，只做文件拆分与中英注释补充。
 */
interface Expr{
    /** Returns 0 for false, non-zero for true. / 0 表示 false，非 0 表示 true。 */
    float eval(RadialBuildMenuMod ctx);
}

final class ConditionParser{
    private final String s;
    private int i;

    private ConditionParser(String s){
        this.s = s == null ? "" : s;
    }

    public static Expr parse(String s){
        ConditionParser p = new ConditionParser(s);
        Expr out = p.parseOr();
        p.skipWs();
        if(!p.eof()){
            throw new IllegalArgumentException("Unexpected trailing input at " + p.i);
        }
        return out;
    }

    private Expr parseOr(){
        Expr left = parseXor();
        while(true){
            skipWs();
            if(match("||")){
                Expr right = parseXor();
                left = new OrExpr(left, right);
                continue;
            }
            return left;
        }
    }

    private Expr parseXor(){
        Expr left = parseAnd();
        while(true){
            skipWs();
            if(matchWord("xor")){
                Expr right = parseAnd();
                left = new XorExpr(left, right);
                continue;
            }
            return left;
        }
    }

    private Expr parseAnd(){
        Expr left = parseCompare();
        while(true){
            skipWs();
            if(match("&&")){
                Expr right = parseCompare();
                left = new AndExpr(left, right);
                continue;
            }
            return left;
        }
    }

    private Expr parseCompare(){
        Expr left = parseUnary();
        while(true){
            skipWs();

            if(match(">=")){
                Expr right = parseUnary();
                left = new CmpExpr(left, right, CmpOp.gte);
            }else if(match("<=")){
                Expr right = parseUnary();
                left = new CmpExpr(left, right, CmpOp.lte);
            }else if(match("==")){
                Expr right = parseUnary();
                left = new CmpExpr(left, right, CmpOp.eq);
            }else if(match("!=")){
                Expr right = parseUnary();
                left = new CmpExpr(left, right, CmpOp.neq);
            }else if(match(">")){
                Expr right = parseUnary();
                left = new CmpExpr(left, right, CmpOp.gt);
            }else if(match("<")){
                Expr right = parseUnary();
                left = new CmpExpr(left, right, CmpOp.lt);
            }else{
                return left;
            }
        }
    }

    private Expr parseUnary(){
        skipWs();
        if(match("!")){
            return new NotExpr(parseUnary());
        }
        if(match("-")){
            return new NegExpr(parseUnary());
        }
        return parsePrimary();
    }

    private Expr parsePrimary(){
        skipWs();
        if(match("(")){
            Expr e = parseOr();
            skipWs();
            if(!match(")")) throw new IllegalArgumentException("Missing ')' at " + i);
            return e;
        }

        if(peek() == '@'){
            i++;
            String name = readIdent();
            if(name.isEmpty()) throw new IllegalArgumentException("Empty variable name at " + i);
            return new VarExpr("@" + name);
        }

        if(isIdentStart(peek())){
            String name = readIdent();
            if(name.isEmpty()) throw new IllegalArgumentException("Empty variable name at " + i);
            return new VarExpr(name);
        }

        if(isDigit(peek()) || peek() == '.' && isDigit(peekNext())){
            String num = readNumber();
            try{
                return new NumExpr(Float.parseFloat(num));
            }catch(Throwable t){
                throw new IllegalArgumentException("Bad number: " + num);
            }
        }

        throw new IllegalArgumentException("Unexpected token at " + i);
    }

    private String readIdent(){
        int start = i;
        while(!eof()){
            char c = s.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z')
                || (c >= 'A' && c <= 'Z')
                || (c >= '0' && c <= '9')
                || c == '_' || c == '-' || c == '.';
            if(!ok) break;
            i++;
        }
        return s.substring(start, i);
    }

    private String readNumber(){
        int start = i;
        boolean dot = false;
        while(!eof()){
            char c = s.charAt(i);
            if(c == '.'){
                if(dot) break;
                dot = true;
                i++;
                continue;
            }
            if(!isDigit(c)) break;
            i++;
        }
        return s.substring(start, i);
    }

    private boolean match(String lit){
        if(lit == null || lit.isEmpty()) return false;
        if(i + lit.length() > s.length()) return false;
        if(s.regionMatches(i, lit, 0, lit.length())){
            i += lit.length();
            return true;
        }
        return false;
    }

    private boolean matchWord(String word){
        if(word == null || word.isEmpty()) return false;
        int len = word.length();
        if(i + len > s.length()) return false;
        if(!s.regionMatches(true, i, word, 0, len)) return false;

        // word boundary
        char before = i > 0 ? s.charAt(i - 1) : ' ';
        char after = (i + len) < s.length() ? s.charAt(i + len) : ' ';
        if(isIdentChar(before) || isIdentChar(after)) return false;

        i += len;
        return true;
    }

    private boolean isIdentChar(char c){
        return (c >= 'a' && c <= 'z')
            || (c >= 'A' && c <= 'Z')
            || (c >= '0' && c <= '9')
            || c == '_' || c == '-' || c == '.';
    }

    private void skipWs(){
        while(!eof()){
            char c = s.charAt(i);
            if(c != ' ' && c != '\t' && c != '\n' && c != '\r') break;
            i++;
        }
    }

    private char peek(){
        return eof() ? '\0' : s.charAt(i);
    }

    private char peekNext(){
        return i + 1 >= s.length() ? '\0' : s.charAt(i + 1);
    }

    private boolean eof(){
        return i >= s.length();
    }

    private boolean isIdentStart(char c){
        return (c >= 'a' && c <= 'z')
            || (c >= 'A' && c <= 'Z')
            || c == '_' || c == '-';
    }

    private boolean isDigit(char c){
        return c >= '0' && c <= '9';
    }
}

final class NumExpr implements Expr{
    private final float v;
    NumExpr(float v){ this.v = v; }
    @Override public float eval(RadialBuildMenuMod ctx){ return v; }
}

final class VarExpr implements Expr{
    private final String name;
    VarExpr(String name){ this.name = name; }
    @Override public float eval(RadialBuildMenuMod ctx){ return ctx.condVar(name); }
}

final class NegExpr implements Expr{
    private final Expr inner;
    NegExpr(Expr inner){ this.inner = inner; }
    @Override public float eval(RadialBuildMenuMod ctx){ return -inner.eval(ctx); }
}

final class NotExpr implements Expr{
    private final Expr inner;
    NotExpr(Expr inner){ this.inner = inner; }
    @Override public float eval(RadialBuildMenuMod ctx){ return inner.eval(ctx) != 0f ? 0f : 1f; }
}

final class AndExpr implements Expr{
    private final Expr a, b;
    AndExpr(Expr a, Expr b){ this.a = a; this.b = b; }
    @Override public float eval(RadialBuildMenuMod ctx){ return (a.eval(ctx) != 0f && b.eval(ctx) != 0f) ? 1f : 0f; }
}

final class OrExpr implements Expr{
    private final Expr a, b;
    OrExpr(Expr a, Expr b){ this.a = a; this.b = b; }
    @Override public float eval(RadialBuildMenuMod ctx){ return (a.eval(ctx) != 0f || b.eval(ctx) != 0f) ? 1f : 0f; }
}

final class XorExpr implements Expr{
    private final Expr a, b;
    XorExpr(Expr a, Expr b){ this.a = a; this.b = b; }
    @Override public float eval(RadialBuildMenuMod ctx){
        boolean av = a.eval(ctx) != 0f;
        boolean bv = b.eval(ctx) != 0f;
        return (av ^ bv) ? 1f : 0f;
    }
}

enum CmpOp{ gt, gte, lt, lte, eq, neq }

final class CmpExpr implements Expr{
    private final Expr a, b;
    private final CmpOp op;
    CmpExpr(Expr a, Expr b, CmpOp op){ this.a = a; this.b = b; this.op = op; }
    @Override public float eval(RadialBuildMenuMod ctx){
        float av = a.eval(ctx);
        float bv = b.eval(ctx);
        boolean out;
        switch(op){
            case gt: out = av > bv; break;
            case gte: out = av >= bv; break;
            case lt: out = av < bv; break;
            case lte: out = av <= bv; break;
            case eq: out = av == bv; break;
            case neq: out = av != bv; break;
            default: out = false; break;
        }
        return out ? 1f : 0f;
    }
}
