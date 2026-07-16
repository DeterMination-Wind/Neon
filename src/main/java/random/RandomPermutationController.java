package random;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Deterministic, no-duplication permutations shared by the text and texture layers. */
public final class RandomPermutationController{
    private RandomPermutationController(){
    }

    public static long seed(String value){
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        long result = 0xcbf29ce484222325L;
        for(byte b : bytes){
            result ^= b & 0xffL;
            result *= 0x100000001b3L;
        }
        return result;
    }

    /** Returns a permutation of the supplied list with fixed points removed where possible. */
    public static <T> List<T> derangement(List<T> input, long seed){
        ArrayList<T> result = new ArrayList<>(input);
        if(result.size() <= 1) return result;

        ArrayList<Integer> indices = new ArrayList<>(result.size());
        for(int i = 0; i < result.size(); i++) indices.add(i);
        Random random = new Random(seed);

        for(int attempt = 0; attempt < 128; attempt++){
            Collections.shuffle(indices, random);
            boolean valid = true;
            for(int i = 0; i < indices.size(); i++){
                if(indices.get(i) == i){
                    valid = false;
                    break;
                }
            }
            if(valid){
                return remap(input, indices);
            }
        }

        // A one-step rotation is a guaranteed derangement for every pool larger than one.
        for(int i = 0; i < indices.size(); i++) indices.set(i, (i + 1) % indices.size());
        return remap(input, indices);
    }

    private static <T> List<T> remap(List<T> input, List<Integer> indices){
        ArrayList<T> result = new ArrayList<>(input.size());
        for(int index : indices) result.add(input.get(index));
        return result;
    }

    public static Map<String, String> mapping(List<String> ids, long seed){
        List<String> shuffled = derangement(ids, seed);
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        for(int i = 0; i < ids.size(); i++) result.put(ids.get(i), shuffled.get(i));
        return result;
    }

    public static boolean isPermutation(List<String> ids, Map<String, String> mapping){
        if(mapping == null || mapping.size() != ids.size()) return false;
        java.util.HashSet<String> values = new java.util.HashSet<>();
        for(String id : ids){
            String value = mapping.get(id);
            if(value == null || !ids.contains(value) || !values.add(value)) return false;
        }
        return true;
    }

    public static boolean avoidsFixedPoints(List<String> ids, Map<String, String> mapping){
        if(ids.size() <= 1) return true;
        for(String id : ids){
            if(id.equals(mapping.get(id))) return false;
        }
        return true;
    }

    public static String digest(String value){
        try{
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(bytes.length * 2);
            for(byte b : bytes) out.append(String.format("%02x", b & 0xff));
            return out.toString();
        }catch(Exception e){
            return Integer.toHexString(value.hashCode());
        }
    }
}
