package hidewhatprocessorsshow;

import arc.struct.Seq;
import mindustry.game.MapObjectives.ObjectiveMarker;
import mindustry.logic.LMarkerControl;

import java.lang.reflect.Field;

/**
 * Version-tolerant access to an {@code ObjectiveMarker}'s world / minimap visibility.
 *
 * <p>Every build up to Mindustry v160 keeps {@code world} and {@code minimap} as booleans.
 * Since v160.1 (upstream #10762) they are {@code @IndexBool int}s holding an index into
 * {@code MapMarkers.worldMarkers} / {@code mapMarkers} ({@code -1} = hidden), and the
 * upstream comment says not to write them directly once the marker is registered: the
 * index lists drive rendering, so a raw write leaves stale entries behind. Writes go
 * through {@code ObjectiveMarker.control(LMarkerControl, ...)}, which both API generations
 * implement with the same visible/hidden meaning (the enum constants exist since much older
 * builds), while reads have to know whether the field is a boolean or an index.</p>
 */
final class MarkerVisibilityCompat{
    /** True when the marker fields are index ints (Mindustry v160.1+) instead of booleans. */
    static final boolean indexed;

    private static final Field worldField = field("world");
    private static final Field minimapField = field("minimap");

    static{
        indexed = worldField != null && worldField.getType() == int.class;
    }

    private MarkerVisibilityCompat(){
    }

    static boolean world(ObjectiveMarker marker){
        return visible(marker, worldField);
    }

    static boolean minimap(ObjectiveMarker marker){
        return visible(marker, minimapField);
    }

    static void world(ObjectiveMarker marker, boolean visible){
        marker.control(LMarkerControl.world, visible ? 1d : 0d, 0d, 0d);
    }

    static void minimap(ObjectiveMarker marker, boolean visible){
        marker.control(LMarkerControl.minimap, visible ? 1d : 0d, 0d, 0d);
    }

    /**
     * Snapshot of every marker of the current game state. Hiding a marker through
     * {@code control()} removes it from the upstream lists, so callers must not iterate the
     * live collection while hiding. v160.1+ keeps one list per view and its own iterator only
     * walks the world markers, so the minimap list is appended reflectively.
     */
    static Seq<ObjectiveMarker> snapshot(Object markers){
        Seq<ObjectiveMarker> snapshot = new Seq<>();
        if(!(markers instanceof Iterable)) return snapshot;

        collect((Iterable<?>)markers, snapshot);

        if(indexed){
            Object minimapMarkers = listField(markers, "mapMarkers");
            if(minimapMarkers instanceof Iterable){
                collect((Iterable<?>)minimapMarkers, snapshot);
            }
        }
        return snapshot;
    }

    private static void collect(Iterable<?> markers, Seq<ObjectiveMarker> snapshot){
        for(Object entry : markers){
            if(entry instanceof ObjectiveMarker){
                ObjectiveMarker marker = (ObjectiveMarker)entry;
                if(!snapshot.contains(marker, true)){
                    snapshot.add(marker);
                }
            }
        }
    }

    private static boolean visible(ObjectiveMarker marker, Field field){
        if(field == null) return false;
        try{
            return indexed ? ((Number)field.get(marker)).intValue() != -1 : field.getBoolean(marker);
        }catch(Throwable ignored){
            return false;
        }
    }

    private static Object listField(Object markers, String name){
        try{
            return markers.getClass().getField(name).get(markers);
        }catch(Throwable ignored){
            return null;
        }
    }

    private static Field field(String name){
        try{
            return ObjectiveMarker.class.getField(name);
        }catch(NoSuchFieldException ignored){
            return null;
        }
    }
}