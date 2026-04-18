package whousesthisbuilding;

import arc.math.Mathf;
import arc.struct.IntSeq;
import arc.struct.LongMap;
import arc.struct.ObjectMap;
import arc.struct.ObjectSet;
import arc.struct.Seq;
import mindustry.world.blocks.logic.LogicBlock.LogicBuild;
import mindustry.world.blocks.logic.LogicBlock.LogicLink;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class LogicReferenceAnalyzer{
    private static final int maxValueSetSize = 12;

    private LogicReferenceAnalyzer(){
    }

    static ProcessorAnalysis analyze(LogicBuild build, int codeHash, int linksHash){
        ProcessorAnalysis result = new ProcessorAnalysis(codeHash, linksHash);
        if(build == null || build.code == null || build.code.isEmpty()) return result;

        ObjectSet<String> linkNames = new ObjectSet<>();
        for(LogicLink link : build.links){
            if(link == null || link.name == null || link.name.isEmpty()) continue;
            linkNames.add(link.name);
        }

        Seq<Instruction> instructions = new Seq<>();
        String[] rawLines = build.code.split("\\R", -1);
        int instructionIndex = 0;
        for(int i = 0; i < rawLines.length; i++){
            String line = stripComment(rawLines[i]).trim();
            if(line.isEmpty()) continue;

            String[] tokens = tokenize(line);
            if(tokens.length == 0) continue;

            String opcode = tokens[0].toLowerCase(Locale.ROOT);
            int sourceLine = i + 1;

            if("getlink".equals(opcode) && result.getlinkReference == null){
                result.getlinkReference = new ReferenceLine(sourceLine, "getlink");
            }

            for(int t = 1; t < tokens.length; t++){
                String token = tokens[t];
                if(!linkNames.contains(token)) continue;
                if(!result.directReferences.containsKey(token)){
                    result.directReferences.put(token, new ReferenceLine(sourceLine, opcode));
                }
            }

            instructions.add(new Instruction(sourceLine, instructionIndex, opcode, tokens));
            instructionIndex++;
        }

        collectGetBlockReferences(build, instructions, result.getblockReferences);
        return result;
    }

    private static void collectGetBlockReferences(LogicBuild build, Seq<Instruction> instructions, LongMap<ReferenceLine> out){
        if(instructions.isEmpty()) return;

        VarState[] inStates = new VarState[instructions.size];
        boolean[] queued = new boolean[instructions.size];
        ArrayDeque<Integer> queue = new ArrayDeque<>();

        inStates[0] = VarState.initial(build.tileX(), build.tileY());
        queue.add(0);
        queued[0] = true;

        while(!queue.isEmpty()){
            int index = queue.removeFirst();
            queued[index] = false;

            VarState in = inStates[index];
            if(in == null) continue;

            Instruction inst = instructions.get(index);
            if("getblock".equals(inst.opcode) && inst.tokens.length >= 5){
                ValueSet x = resolveToken(inst.tokens[3], in);
                ValueSet y = resolveToken(inst.tokens[4], in);

                if(!x.unknown && !y.unknown){
                    for(double xv : x.values){
                        for(double yv : y.values){
                            int tx = Mathf.round((float)xv);
                            int ty = Mathf.round((float)yv);
                            long key = packCoord(tx, ty);
                            putEarliest(out, key, new ReferenceLine(inst.sourceLine, "getblock"));
                        }
                    }
                }
            }

            VarState outState = transfer(inst, in);
            IntSeq next = successors(instructions, index, in);
            for(int i = 0; i < next.size; i++){
                int to = next.get(i);
                if(to < 0 || to >= instructions.size) continue;

                boolean changed;
                if(inStates[to] == null){
                    inStates[to] = outState.copy();
                    changed = true;
                }else{
                    changed = inStates[to].mergeFrom(outState);
                }

                if(changed && !queued[to]){
                    queue.addLast(to);
                    queued[to] = true;
                }
            }
        }
    }

    private static VarState transfer(Instruction inst, VarState in){
        VarState out = in.copy();
        String[] t = inst.tokens;

        switch(inst.opcode){
            case "set":
                if(t.length >= 3){
                    out.set(t[1], resolveToken(t[2], in));
                }
                break;
            case "op":
                if(t.length >= 5){
                    ValueSet a = resolveToken(t[3], in);
                    ValueSet b = resolveToken(t[4], in);
                    out.set(t[2], evalOp(t[1], a, b));
                }
                break;
            case "lookup":
                if(t.length >= 3){
                    out.setUnknown(t[2]);
                }
                break;
            case "sensor":
            case "read":
            case "getlink":
            case "uradar":
            case "radar":
            case "packcolor":
                if(t.length >= 2){
                    out.setUnknown(t[t.length - 1]);
                }
                break;
            case "ulocate":
                if(t.length >= 8){
                    out.setUnknown(t[5]);
                    out.setUnknown(t[6]);
                    out.setUnknown(t[7]);
                    if(t.length >= 9){
                        out.setUnknown(t[8]);
                    }
                }
                break;
            case "getblock":
                if(t.length >= 3){
                    out.setUnknown(t[2]);
                }
                break;
            default:
                break;
        }

        return out;
    }

    private static IntSeq successors(Seq<Instruction> instructions, int index, VarState state){
        Instruction inst = instructions.get(index);
        IntSeq next = new IntSeq(2);

        if("end".equals(inst.opcode) || "stop".equals(inst.opcode)) return next;

        if("jump".equals(inst.opcode)){
            int fallback = index + 1;
            Integer target = parseJumpTarget(inst.tokens, state);
            Tri cond = evalJumpCondition(inst.tokens, state);

            if(cond != Tri.False && target != null && target >= 0 && target < instructions.size){
                next.add(target);
            }

            if(cond != Tri.True && fallback < instructions.size){
                next.add(fallback);
            }else if(cond == Tri.True && target == null && fallback < instructions.size){
                next.add(fallback);
            }

            return next;
        }

        if(index + 1 < instructions.size){
            next.add(index + 1);
        }
        return next;
    }

    private static Integer parseJumpTarget(String[] tokens, VarState state){
        if(tokens.length < 2) return null;
        ValueSet target = resolveToken(tokens[1], state);
        if(target.unknown || target.values.size() != 1) return null;
        double only = target.values.iterator().next();
        return Mathf.round((float)only);
    }

    private static Tri evalJumpCondition(String[] tokens, VarState state){
        if(tokens.length < 3) return Tri.True;

        String cond = tokens[2].toLowerCase(Locale.ROOT);
        if("always".equals(cond)) return Tri.True;
        if(tokens.length < 5) return Tri.Unknown;

        ValueSet a = resolveToken(tokens[3], state);
        ValueSet b = resolveToken(tokens[4], state);
        if(a.unknown || b.unknown) return Tri.Unknown;

        boolean sawTrue = false;
        boolean sawFalse = false;

        for(double av : a.values){
            for(double bv : b.values){
                boolean ok = evalCondition(cond, av, bv);
                if(ok) sawTrue = true;
                else sawFalse = true;
                if(sawTrue && sawFalse) return Tri.Unknown;
            }
        }

        if(sawTrue) return Tri.True;
        if(sawFalse) return Tri.False;
        return Tri.Unknown;
    }

    private static boolean evalCondition(String cond, double a, double b){
        switch(cond){
            case "equal":
                return a == b;
            case "notequal":
                return a != b;
            case "lessthan":
                return a < b;
            case "lessthaneq":
                return a <= b;
            case "greaterthan":
                return a > b;
            case "greaterthaneq":
                return a >= b;
            case "strictequal":
                return Double.doubleToLongBits(a) == Double.doubleToLongBits(b);
            default:
                return false;
        }
    }

    private static ValueSet evalOp(String op, ValueSet a, ValueSet b){
        if(a.unknown || b.unknown) return ValueSet.unknown();
        ValueSet out = ValueSet.empty();
        String normalized = op.toLowerCase(Locale.ROOT);

        for(double av : a.values){
            for(double bv : b.values){
                Double value = evalSingleOp(normalized, av, bv);
                if(value == null || !Double.isFinite(value)) return ValueSet.unknown();
                if(!out.add(value)) return ValueSet.unknown();
            }
        }
        return out.values.isEmpty() ? ValueSet.unknown() : out;
    }

    private static Double evalSingleOp(String op, double a, double b){
        switch(op){
            case "add":
                return a + b;
            case "sub":
                return a - b;
            case "mul":
                return a * b;
            case "div":
                return b == 0d ? null : a / b;
            case "idiv":
                return b == 0d ? null : Math.floor(a / b);
            case "mod":
                return b == 0d ? null : a % b;
            case "pow":
                return Math.pow(a, b);
            case "min":
                return Math.min(a, b);
            case "max":
                return Math.max(a, b);
            case "abs":
                return Math.abs(a);
            case "floor":
                return Math.floor(a);
            case "ceil":
                return Math.ceil(a);
            case "round":
                return (double)Math.round(a);
            case "sqrt":
                return a < 0d ? null : Math.sqrt(a);
            case "equal":
                return a == b ? 1d : 0d;
            case "notequal":
                return a != b ? 1d : 0d;
            case "lessthan":
                return a < b ? 1d : 0d;
            case "lessthaneq":
                return a <= b ? 1d : 0d;
            case "greaterthan":
                return a > b ? 1d : 0d;
            case "greaterthaneq":
                return a >= b ? 1d : 0d;
            default:
                return null;
        }
    }

    private static ValueSet resolveToken(String token, VarState state){
        if(token == null || token.isEmpty()) return ValueSet.unknown();

        Double literal = parseLiteral(token);
        if(literal != null){
            return ValueSet.of(literal);
        }

        ValueSet value = state.get(token);
        return value == null ? ValueSet.unknown() : value.copy();
    }

    private static Double parseLiteral(String token){
        String t = token.trim();
        if(t.isEmpty()) return null;
        if("true".equalsIgnoreCase(t)) return 1d;
        if("false".equalsIgnoreCase(t)) return 0d;
        if("null".equalsIgnoreCase(t)) return null;

        try{
            return Double.parseDouble(t);
        }catch(Throwable ignored){
            return null;
        }
    }

    private static void putEarliest(LongMap<ReferenceLine> map, long key, ReferenceLine line){
        ReferenceLine previous = map.get(key);
        if(previous == null || line.line < previous.line){
            map.put(key, line);
        }
    }

    static long packCoord(int x, int y){
        return ((long)x << 32) ^ (y & 0xffffffffL);
    }

    private static String stripComment(String line){
        boolean quoted = false;
        for(int i = 0; i < line.length(); i++){
            char c = line.charAt(i);
            if(c == '"'){
                quoted = !quoted;
            }else if(c == '#' && !quoted){
                return line.substring(0, i);
            }
        }
        return line;
    }

    private static String[] tokenize(String line){
        Seq<String> out = new Seq<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;

        for(int i = 0; i < line.length(); i++){
            char c = line.charAt(i);
            if(c == '"'){
                quoted = !quoted;
                continue;
            }

            if(!quoted && Character.isWhitespace(c)){
                if(current.length() > 0){
                    out.add(current.toString());
                    current.setLength(0);
                }
            }else{
                current.append(c);
            }
        }

        if(current.length() > 0){
            out.add(current.toString());
        }

        return out.toArray(String.class);
    }

    static final class ProcessorAnalysis{
        final int codeHash;
        final int linksHash;
        final ObjectMap<String, ReferenceLine> directReferences = new ObjectMap<>();
        final LongMap<ReferenceLine> getblockReferences = new LongMap<>();
        ReferenceLine getlinkReference;

        ProcessorAnalysis(int codeHash, int linksHash){
            this.codeHash = codeHash;
            this.linksHash = linksHash;
        }
    }

    static final class ReferenceLine{
        final int line;
        final String opcode;

        ReferenceLine(int line, String opcode){
            this.line = line;
            this.opcode = opcode;
        }
    }

    private enum Tri{
        True, False, Unknown
    }

    private static final class Instruction{
        final int sourceLine;
        final int instructionIndex;
        final String opcode;
        final String[] tokens;

        Instruction(int sourceLine, int instructionIndex, String opcode, String[] tokens){
            this.sourceLine = sourceLine;
            this.instructionIndex = instructionIndex;
            this.opcode = opcode;
            this.tokens = tokens;
        }
    }

    private static final class VarState{
        private final Map<String, ValueSet> vars = new HashMap<>();

        static VarState initial(int thisx, int thisy){
            VarState out = new VarState();
            out.vars.put("@thisx", ValueSet.of(thisx));
            out.vars.put("@thisy", ValueSet.of(thisy));
            return out;
        }

        VarState copy(){
            VarState out = new VarState();
            for(Map.Entry<String, ValueSet> entry : vars.entrySet()){
                out.vars.put(entry.getKey(), entry.getValue().copy());
            }
            return out;
        }

        boolean mergeFrom(VarState other){
            boolean changed = false;
            for(Map.Entry<String, ValueSet> entry : other.vars.entrySet()){
                String key = entry.getKey();
                ValueSet existing = vars.get(key);
                if(existing == null){
                    vars.put(key, entry.getValue().copy());
                    changed = true;
                }else{
                    changed |= existing.merge(entry.getValue());
                }
            }
            return changed;
        }

        void set(String key, ValueSet value){
            if(key == null || key.isEmpty()) return;
            vars.put(key, value.copy());
        }

        void setUnknown(String key){
            if(key == null || key.isEmpty()) return;
            vars.put(key, ValueSet.unknown());
        }

        ValueSet get(String key){
            return vars.get(key);
        }
    }

    private static final class ValueSet{
        boolean unknown;
        final Set<Double> values = new LinkedHashSet<>();

        static ValueSet unknown(){
            ValueSet out = new ValueSet();
            out.unknown = true;
            return out;
        }

        static ValueSet empty(){
            return new ValueSet();
        }

        static ValueSet of(double value){
            ValueSet out = new ValueSet();
            out.values.add(value);
            return out;
        }

        ValueSet copy(){
            ValueSet out = new ValueSet();
            out.unknown = unknown;
            out.values.addAll(values);
            return out;
        }

        boolean merge(ValueSet other){
            if(other == null) return false;
            if(unknown) return false;
            if(other.unknown){
                unknown = true;
                values.clear();
                return true;
            }

            int before = values.size();
            values.addAll(other.values);
            if(values.size() > maxValueSetSize){
                unknown = true;
                values.clear();
            }
            return unknown || values.size() != before;
        }

        boolean add(double value){
            if(unknown) return false;
            values.add(value);
            if(values.size() > maxValueSetSize){
                unknown = true;
                values.clear();
                return false;
            }
            return true;
        }
    }
}
