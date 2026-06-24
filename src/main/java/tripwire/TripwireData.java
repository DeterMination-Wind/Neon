package tripwire;

import arc.Events;
import arc.math.geom.Vec2;
import arc.struct.Seq;
import mindustry.ctype.ContentType;
import mindustry.game.EventType;
import mindustry.game.Team;
import mindustry.io.SaveFileReader;
import mindustry.io.SaveVersion;
import mindustry.type.UnitType;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import static mindustry.Vars.content;

public final class TripwireData {
    public static final Seq<TripwireFence> fences = new Seq<>();
    private static final int chunkVersion = 2;
    private static int nextId = 1;
    private static boolean inited;

    private TripwireData() {
    }

    public static void init() {
        if (inited) return;
        inited = true;

        SaveVersion.addCustomChunk("tripwire-data", new SaveFileReader.CustomChunk() {
            @Override
            public void write(DataOutput stream) throws IOException {
                stream.writeInt(chunkVersion);
                stream.writeInt(fences.size);
                for (TripwireFence fence : fences) {
                    stream.writeInt(fence.id);
                    stream.writeInt(fence.points.size);
                    for (Vec2 point : fence.points) {
                        stream.writeFloat(point.x);
                        stream.writeFloat(point.y);
                    }
                    stream.writeInt(fence.direction.id);
                    stream.writeInt(fence.selectedUnits.size);
                    for (UnitType type : fence.selectedUnits) {
                        stream.writeUTF(type.name);
                    }
                    stream.writeInt(fence.team == null ? Team.derelict.id : fence.team.id);
                }
            }

            @Override
            public void read(DataInput stream) throws IOException {
                clear();
                int version = stream.readInt();
                if (version < 1 || version > chunkVersion) return;
                int count = stream.readInt();
                for (int i = 0; i < count; i++) {
                    int id = stream.readInt();
                    TripwireFence fence = new TripwireFence(id, Team.derelict);
                    int pointCount = stream.readInt();
                    for (int p = 0; p < pointCount; p++) {
                        fence.points.add(new Vec2(stream.readFloat(), stream.readFloat()));
                    }
                    fence.direction = version == 1
                        ? TripwireFence.DirectionMode.fromLegacyRightSide(stream.readBoolean())
                        : TripwireFence.DirectionMode.byId(stream.readInt());
                    int unitCount = stream.readInt();
                    for (int u = 0; u < unitCount; u++) {
                        UnitType type = content.getByName(ContentType.unit, stream.readUTF());
                        if (type != null) fence.selectedUnits.add(type);
                    }
                    fence.team = Team.get(stream.readInt());
                    fences.add(fence);
                    nextId = Math.max(nextId, id + 1);
                }
            }

            @Override
            public boolean shouldWrite() {
                return !fences.isEmpty();
            }

            @Override
            public boolean writeNet() {
                return true;
            }
        });

        Events.on(EventType.WorldLoadEvent.class, e -> clear());
    }

    public static TripwireFence create(Team team) {
        TripwireFence fence = new TripwireFence(nextId++, team == null ? Team.derelict : team);
        selectDefaultUnits(fence);
        return fence;
    }

    public static void add(TripwireFence fence) {
        if (fence != null && fence.points.size >= 2) fences.add(fence);
    }

    public static void clear() {
        fences.clear();
        nextId = 1;
        TripwireDetector.clearCache();
    }

    public static TripwireFence nearest(float x, float y, float maxDistance) {
        TripwireFence best = null;
        float bestDistance = maxDistance;
        for (TripwireFence fence : fences) {
            float distance = fence.distanceTo(x, y);
            if (distance <= bestDistance) {
                best = fence;
                bestDistance = distance;
            }
        }
        return best;
    }

    private static void selectDefaultUnits(TripwireFence fence) {
        if (content == null) return;
        for (UnitType type : content.units()) {
            if (isDefaultSelected(type)) fence.selectedUnits.add(type);
        }
    }

    private static boolean isDefaultSelected(UnitType type) {
        return type != null && !TripwireFence.isCoreUnit(type);
    }
}
