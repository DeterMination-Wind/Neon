package stealthpath;

import arc.graphics.Color;
import arc.struct.IntSeq;
import arc.struct.Seq;
import arc.util.Strings;
import mindustry.entities.bullet.BulletType;
import mindustry.gen.Building;
import mindustry.gen.Unit;

/**
 * Pathfinding / rendering data types for StealthPath.
 *
 * StealthPath 的寻路/渲染数据结构。
 * 这些类型原本是 {@link StealthPathMod} 的私有静态内部类，迁移为包内可见的顶层类：
 * - 不改变任何字段语义/构造逻辑
 * - 仅减少主文件体积，便于维护与后续继续拆分
 */
final class Pos{
    final float x, y;

    Pos(float x, float y){
        this.x = x;
        this.y = y;
    }
}

final class RenderPath{
    final Seq<Pos> points;
    final Color color;
    final String damageText;

    RenderPath(Seq<Pos> points, Color color, float damage){
        this.points = points == null ? new Seq<Pos>() : points;
        this.color = color == null ? Color.white : color;
        this.damageText = Float.isFinite(damage) ? Strings.autoFixed(Math.max(0f, damage), 1) : null;
    }
}

final class Threat{
    final float x, y;
    final float range;
    final float minRange;
    final float dps;
    final Building building;
    final Unit unit;
    final BulletType bullet;
    final boolean targetAir;
    final boolean targetGround;
    final boolean tractor;
    final boolean continuous;
    final boolean preferStrongestTarget;
    final float shotsPerSecond;
    final float inaccuracy;
    final int maxTargets;

    Threat(float x, float y, float range, float minRange, float dps){
        this(x, y, range, minRange, dps, null, null, null, true, true, false, false, false, 0f, 0f, 1);
    }

    Threat(Building building, BulletType bullet, float range, float minRange, float dps, boolean targetAir, boolean targetGround, boolean tractor, boolean continuous, float shotsPerSecond, float inaccuracy){
        this(building == null ? 0f : building.x, building == null ? 0f : building.y, range, minRange, dps,
            building, null, bullet, targetAir, targetGround, tractor, continuous, false, shotsPerSecond, inaccuracy, 1);
    }

    Threat(Building building, BulletType bullet, float range, float minRange, float dps, boolean targetAir, boolean targetGround, boolean tractor, boolean continuous, boolean preferStrongestTarget, float shotsPerSecond, float inaccuracy){
        this(building == null ? 0f : building.x, building == null ? 0f : building.y, range, minRange, dps,
            building, null, bullet, targetAir, targetGround, tractor, continuous, preferStrongestTarget, shotsPerSecond, inaccuracy, 1);
    }

    Threat(Unit unit, BulletType bullet, float range, float minRange, float dps, boolean targetAir, boolean targetGround, boolean continuous, float shotsPerSecond, float inaccuracy){
        this(unit == null ? 0f : unit.x, unit == null ? 0f : unit.y, range, minRange, dps,
            null, unit, bullet, targetAir, targetGround, false, continuous, false, shotsPerSecond, inaccuracy, 1);
    }

    Threat(Unit unit, float range, float minRange, float dps, boolean targetAir, boolean targetGround, int maxTargets){
        this(unit == null ? 0f : unit.x, unit == null ? 0f : unit.y, range, minRange, dps,
            null, unit, null, targetAir, targetGround, false, false, false, 0f, 0f, maxTargets);
    }

    private Threat(float x, float y, float range, float minRange, float dps, Building building, Unit unit, BulletType bullet,
                   boolean targetAir, boolean targetGround, boolean tractor, boolean continuous, boolean preferStrongestTarget, float shotsPerSecond, float inaccuracy, int maxTargets){
        this.x = x;
        this.y = y;
        this.range = Math.max(0f, range);
        this.minRange = Math.max(0f, minRange);
        this.dps = Math.max(0f, dps);
        this.building = building;
        this.unit = unit;
        this.bullet = bullet;
        this.targetAir = targetAir;
        this.targetGround = targetGround;
        this.tractor = tractor;
        this.continuous = continuous;
        this.preferStrongestTarget = preferStrongestTarget;
        this.shotsPerSecond = Math.max(0f, shotsPerSecond);
        this.inaccuracy = Math.max(0f, inaccuracy);
        this.maxTargets = Math.max(1, maxTargets);
    }
}

final class ThreatMap{
    final int width, height, size;
    final boolean[] passable;
    final float[] risk;
    final float[] displayRisk;
    final float[] controlRisk;
    final float[] floorRisk;
    final float[] drownRate;
    short[] safeDist;
    float safeBias;

    ThreatMap(int width, int height){
        this.width = width;
        this.height = height;
        this.size = width * height;
        this.passable = new boolean[size];
        this.risk = new float[size];
        this.displayRisk = new float[size];
        this.controlRisk = new float[size];
        this.floorRisk = new float[size];
        this.drownRate = new float[size];
    }
}

final class PathResult{
    final IntSeq path;

    PathResult(IntSeq path){
        this.path = path;
    }
}

final class Node implements Comparable<Node>{
    final int idx;
    final float f;
    final float g;

    Node(int idx, float f, float g){
        this.idx = idx;
        this.f = f;
        this.g = g;
    }

    @Override
    public int compareTo(Node other){
        return Float.compare(this.f, other.f);
    }
}

final class ClusterPath{
    final Building target;
    final IntSeq path;
    final boolean safe;
    final float damage;

    ClusterPath(Building target, IntSeq path, boolean safe, float damage){
        this.target = target;
        this.path = path;
        this.safe = safe;
        this.damage = damage;
    }
}

final class ShiftedPath{
    final IntSeq path;
    final float dx, dy;
    final float dmgGround, dmgAir;
    final float maxDmg;
    final float routeScore;
    final short minSafeDist;

    ShiftedPath(IntSeq path, float dx, float dy, float dmgGround, float dmgAir, float maxDmg, float routeScore, short minSafeDist){
        this.path = path;
        this.dx = dx;
        this.dy = dy;
        this.dmgGround = dmgGround;
        this.dmgAir = dmgAir;
        this.maxDmg = maxDmg;
        this.routeScore = routeScore;
        this.minSafeDist = minSafeDist;
    }
}

final class ControlledCluster{
    final Seq<Unit> units;
    final int key;
    final float x, y;
    final Unit moveUnit;
    final float speed;
    final int threatMode;
    final boolean moveFlying;
    final boolean threatsAir, threatsGround;
    final boolean hasGround;
    final float minSpeedGround;
    final float minHpGround;
    final boolean hasAir;
    final float minSpeedAir;
    final float minHpAir;

    final float leftX, leftY;
    final float rightX, rightY;
    final float topX, topY;
    final float bottomX, bottomY;

    final float maxHitRadiusWorld;
    final float threatClearanceWorld;

    ControlledCluster(Seq<Unit> units, int key, float x, float y, Unit moveUnit, float speed, int threatMode, boolean moveFlying, boolean threatsAir, boolean threatsGround,
                      boolean hasGround, float minSpeedGround, float minHpGround,
                      boolean hasAir, float minSpeedAir, float minHpAir,
                      float leftX, float leftY, float rightX, float rightY, float topX, float topY, float bottomX, float bottomY,
                      float maxHitRadiusWorld, float threatClearanceWorld){
        this.units = units == null ? new Seq<>() : units;
        this.key = key;
        this.x = x;
        this.y = y;
        this.moveUnit = moveUnit;
        this.speed = speed;
        this.threatMode = threatMode;
        this.moveFlying = moveFlying;
        this.threatsAir = threatsAir;
        this.threatsGround = threatsGround;
        this.hasGround = hasGround;
        this.minSpeedGround = minSpeedGround;
        this.minHpGround = minHpGround;
        this.hasAir = hasAir;
        this.minSpeedAir = minSpeedAir;
        this.minHpAir = minHpAir;

        this.leftX = leftX;
        this.leftY = leftY;
        this.rightX = rightX;
        this.rightY = rightY;
        this.topX = topX;
        this.topY = topY;
        this.bottomX = bottomX;
        this.bottomY = bottomY;
        this.maxHitRadiusWorld = maxHitRadiusWorld;
        this.threatClearanceWorld = threatClearanceWorld;
    }
}
