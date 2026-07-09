package stealthpath;

import arc.math.Angles;
import arc.math.Mathf;
import arc.struct.Seq;
import mindustry.content.StatusEffects;
import mindustry.core.World;
import mindustry.entities.bullet.BulletType;
import mindustry.entities.bullet.ContinuousBulletType;
import mindustry.entities.bullet.FlakBulletType;
import mindustry.entities.bullet.LaserBulletType;
import mindustry.entities.bullet.RailBulletType;
import mindustry.entities.bullet.SapBulletType;
import mindustry.entities.bullet.ShrapnelBulletType;
import mindustry.gen.Building;
import mindustry.gen.Unit;
import mindustry.type.Liquid;
import mindustry.type.StatusEffect;
import mindustry.world.Tile;
import mindustry.world.blocks.defense.turrets.ContinuousTurret;
import mindustry.world.blocks.defense.turrets.LaserTurret;
import mindustry.world.blocks.defense.turrets.TractorBeamTurret;
import mindustry.world.blocks.defense.turrets.Turret;
import mindustry.world.consumers.ConsumeLiquidBase;
import mindustry.world.consumers.ConsumeLiquidFilter;

import java.lang.reflect.Field;

import static mindustry.Vars.tilesize;
import static mindustry.Vars.world;
import static mindustry.Vars.content;
import static mindustry.entities.Damage.applyArmor;

final class StealthPathThreatModel{
    private static final float maxStatusCarrySeconds = 3f;
    private static Field bulletArmorMultiplierField;
    private static boolean bulletArmorMultiplierFieldChecked;

    private StealthPathThreatModel(){
    }

    static final class RiskContext{
        float threatInflate;
        float horizonSeconds;
        boolean useLineOfSight;
        long deadlineNanos;
        boolean budgetExceeded;

        RiskContext(float threatInflate, float horizonSeconds, boolean useLineOfSight, long deadlineNanos){
            this.threatInflate = Math.max(0f, threatInflate);
            this.horizonSeconds = Math.max(0.05f, horizonSeconds);
            this.useLineOfSight = useLineOfSight;
            this.deadlineNanos = deadlineNanos;
        }

        boolean exceeded(){
            if(deadlineNanos <= 0L) return false;
            if(System.nanoTime() <= deadlineNanos) return false;
            budgetExceeded = true;
            return true;
        }
    }

    static final class RiskSample{
        float routeDps;
        float displayDps;
        float controlDps;

        void clear(){
            routeDps = 0f;
            displayDps = 0f;
            controlDps = 0f;
        }
    }

    static FormationThreatProfile buildFormation(Unit fallback, Seq<Unit> units){
        float cx = 0f, cy = 0f;
        int count = 0;

        if(units != null && units.any()){
            for(int i = 0; i < units.size; i++){
                Unit u = units.get(i);
                if(!validUnit(u)) continue;
                cx += u.x;
                cy += u.y;
                count++;
            }
        }

        if(count <= 0 && validUnit(fallback)){
            cx = fallback.x;
            cy = fallback.y;
            count = 1;
        }

        if(count <= 0){
            return new FormationThreatProfile(new Seq<UnitThreatProfile>(), 0f, 0f);
        }

        cx /= count;
        cy /= count;

        Seq<UnitThreatProfile> profiles = new Seq<>();
        if(units != null && units.any()){
            for(int i = 0; i < units.size; i++){
                Unit u = units.get(i);
                if(validUnit(u)){
                    profiles.add(new UnitThreatProfile(u, u.x - cx, u.y - cy));
                }
            }
        }

        if(profiles.isEmpty() && validUnit(fallback)){
            profiles.add(new UnitThreatProfile(fallback, fallback.x - cx, fallback.y - cy));
        }

        return new FormationThreatProfile(profiles, cx, cy);
    }

    static float outerRange(Threat threat, FormationThreatProfile formation, float threatInflate){
        if(threat == null) return 0f;
        float formationRadius = formation == null ? 0f : formation.maxOffsetRadius;
        return Math.max(0f, threat.range) + Math.max(0f, threatInflate) + formationRadius + secondaryRadius(threat);
    }

    static float riskAt(Threat threat, FormationThreatProfile formation, float centerX, float centerY, RiskContext ctx){
        RiskSample sample = new RiskSample();
        riskAt(threat, formation, centerX, centerY, ctx, sample);
        return sample.routeDps;
    }

    static float displayRiskAt(Threat threat, FormationThreatProfile formation, float centerX, float centerY, RiskContext ctx){
        RiskSample sample = new RiskSample();
        riskAt(threat, formation, centerX, centerY, ctx, sample);
        return sample.displayDps;
    }

    static void riskAt(Threat threat, FormationThreatProfile formation, float centerX, float centerY, RiskContext ctx, RiskSample out){
        if(out != null) out.clear();
        if(threat == null || formation == null || formation.units.isEmpty() || threat.dps <= 0.0001f) return;
        if(!sourceAvailable(threat)) return;

        float threatInflate = ctx == null ? 0f : ctx.threatInflate;
        UnitThreatProfile primary = choosePrimaryTarget(threat, formation, centerX, centerY, threatInflate);
        if(primary == null) return;

        if(ctx != null && ctx.useLineOfSight && isLineLike(threat) && lineOfSightBlocked(threat.x, threat.y, centerX + primary.offsetX, centerY + primary.offsetY)){
            return;
        }

        float availability = threatAvailability(threat, primary, centerX, centerY, ctx);
        if(availability <= 0.0001f) return;

        float total;
        if(threat.tractor){
            total = effectiveDps(threat.dps, threat.dps / 60f, threat.bullet, primary);
        }else if(threat.bullet == null){
            total = radialThreatDps(threat, formation, centerX, centerY, threatInflate);
        }else if(isLineLike(threat)){
            total = lineThreatDps(threat, formation, centerX, centerY, primary, threatInflate);
        }else{
            total = projectileThreatDps(threat, formation, centerX, centerY, primary);
        }

        if(total <= 0.0001f && !isLineLike(threat)){
            total = effectiveDps(threat.dps, threat.dps / 60f, threat.bullet, primary);
        }

        total *= availability;
        float control = controlThreatDps(threat, formation, centerX, centerY, primary, threatInflate, ctx) * availability;
        if(total <= 0.0001f && control <= 0.0001f) return;

        if(out != null){
            out.routeDps = total <= 0.0001f ? 0f : total + killPressureBonus(total, formation);
            out.displayDps = total <= 0.0001f ? 0f : displayDps(total, formation);
            out.controlDps = control;
        }
    }

    private static float radialThreatDps(Threat threat, FormationThreatProfile formation, float centerX, float centerY, float threatInflate){
        float total = 0f;
        int hits = 0;
        int cap = Math.max(1, threat.maxTargets);
        float shotRate = Math.max(1f, shotsPerSecond(threat));

        for(int i = 0; i < formation.units.size; i++){
            UnitThreatProfile profile = formation.units.get(i);
            if(!canHit(threat, profile)) continue;

            float px = centerX + profile.offsetX;
            float py = centerY + profile.offsetY;
            if(!withinThreatRange(threat, profile, px, py, threatInflate)) continue;

            total += effectiveDps(threat.dps, perHit(threat.dps, shotRate, threat.dps / 60f), null, profile);
            hits++;
            if(hits >= cap) break;
        }

        return total;
    }

    private static float projectileThreatDps(Threat threat, FormationThreatProfile formation, float centerX, float centerY, UnitThreatProfile primary){
        BulletType bullet = threat.bullet;
        float px = centerX + primary.offsetX;
        float py = centerY + primary.offsetY;
        float distance = Mathf.dst(threat.x, threat.y, px, py);
        float hitChance = projectileHitChance(threat, primary, distance);

        float total = 0f;
        float directDps = directDps(threat);
        if(directDps > 0.0001f){
            total += effectiveDps(directDps * hitChance, perHit(directDps, shotsPerSecond(threat), bullet.damage), bullet, primary);
        }

        total += splashThreatDps(threat, formation, centerX, centerY, primary, hitChance);
        total += pierceThreatDps(threat, formation, centerX, centerY, primary, directDps * hitChance);
        total += lightningThreatDps(threat, formation, centerX, centerY, primary, hitChance);
        total += fragThreatDps(threat, formation, centerX, centerY, primary, hitChance);
        total += intervalThreatDps(threat, formation, centerX, centerY, primary, hitChance);
        total += statusThreatDps(threat, primary, hitChance);
        total += fireThreatDps(threat, primary, hitChance);
        return total;
    }

    private static float lineThreatDps(Threat threat, FormationThreatProfile formation, float centerX, float centerY, UnitThreatProfile primary, float threatInflate){
        BulletType bullet = threat.bullet;
        float tx = centerX + primary.offsetX;
        float ty = centerY + primary.offsetY;
        float direct = directDps(threat);
        if(direct <= 0.0001f) direct = threat.dps;

        int cap = pierceCap(threat);
        float aimLen = Mathf.dst(threat.x, threat.y, tx, ty);
        if(aimLen > 0.0001f){
            float lineLen = Math.max(aimLen, Math.max(0f, threat.range + threatInflate));
            float scale = lineLen / aimLen;
            tx = threat.x + (tx - threat.x) * scale;
            ty = threat.y + (ty - threat.y) * scale;
        }

        Seq<LineHitCandidate> hits = collectLineHits(threat, formation, centerX, centerY, tx, ty, threatInflate);
        hits.sort((a, b) -> Float.compare(a.distance, b.distance));

        float shotRate = Math.max(0.0001f, shotsPerSecond(threat));
        float rawPerShot = lineRawPerHit(threat, direct, shotRate);
        float remainingPerShot = rawPerShot;
        int hitCount = 0;
        float total = 0f;

        for(int i = 0; i < hits.size; i++){
            if(cap > 0 && hitCount >= cap) break;

            UnitThreatProfile profile = hits.get(i).profile;
            float rawHit = rawPerShot;
            if(pierceBudgetCost(bullet, profile) > 0f){
                rawHit = Math.min(rawHit, Math.max(0f, remainingPerShot));
            }
            if(rawHit <= 0.0001f) break;

            total += effectiveDps(shotRate * rawHit, rawHit, bullet, profile);
            total += statusThreatDps(threat, profile, 1f);
            total += fireThreatDps(threat, profile, 1f);

            hitCount++;

            float cost = pierceBudgetCost(bullet, profile);
            if(cost > 0f){
                remainingPerShot -= Math.min(remainingPerShot, cost);
                if(remainingPerShot <= 0.0001f) break;
            }
        }

        return total;
    }

    private static float splashThreatDps(Threat threat, FormationThreatProfile formation, float centerX, float centerY, UnitThreatProfile primary, float hitChance){
        BulletType bullet = threat.bullet;
        if(bullet == null || bullet.splashDamageRadius <= 0.0001f || bullet.splashDamage <= 0.0001f) return 0f;

        float shotRate = shotsPerSecond(threat);
        float impactX = centerX + primary.offsetX;
        float impactY = centerY + primary.offsetY;
        float radius = bullet.splashDamageRadius;
        float total = 0f;

        for(int i = 0; i < formation.units.size; i++){
            UnitThreatProfile profile = formation.units.get(i);
            if(!canHit(threat, profile)) continue;

            float px = centerX + profile.offsetX;
            float py = centerY + profile.offsetY;
            float distance = Mathf.dst(px, py, impactX, impactY);
            float effectiveDistance = bullet.scaledSplashDamage ? Math.max(0f, distance - profile.radius) : distance;
            if(effectiveDistance > radius + profile.radius) continue;

            float falloff = splashFalloff(effectiveDistance, radius);
            float splashChance = splashHitChance(threat, profile == primary, hitChance);
            float rawDps = shotRate * bullet.splashDamage * falloff * splashChance;
            total += effectiveDps(rawDps, bullet.splashDamage * falloff, bullet, profile);

            if(bullet.status != null && bullet.status != StatusEffects.none){
                total += statusDpsFor(profile, bullet.status, bullet.statusDuration, shotRate * splashChance * falloff);
            }
        }

        return total;
    }

    private static float pierceThreatDps(Threat threat, FormationThreatProfile formation, float centerX, float centerY, UnitThreatProfile primary, float rawDirectDps){
        BulletType bullet = threat.bullet;
        if(bullet == null || !bullet.pierce || rawDirectDps <= 0.0001f) return 0f;

        float tx = centerX + primary.offsetX;
        float ty = centerY + primary.offsetY;
        int cap = pierceCap(threat);
        int hits = 1;
        float total = 0f;

        for(int i = 0; i < formation.units.size; i++){
            UnitThreatProfile profile = formation.units.get(i);
            if(profile == primary || !canHit(threat, profile)) continue;

            float px = centerX + profile.offsetX;
            float py = centerY + profile.offsetY;
            if(!withinThreatRange(threat, profile, px, py, 0f)) continue;

            float pad = profile.radius + bulletHitRadius(bullet) + 3f;
            if(segmentDistance(threat.x, threat.y, tx, ty, px, py) > pad) continue;

            float decay = bullet.pierceDamageFactor <= 0.0001f ? 0.7f : Math.max(0.1f, 1f - bullet.pierceDamageFactor);
            float raw = rawDirectDps * decay;
            total += effectiveDps(raw, perHit(raw, shotsPerSecond(threat), bullet.damage), bullet, profile);
            hits++;
            if(cap > 0 && hits >= cap) break;
        }

        return total;
    }

    private static float lightningThreatDps(Threat threat, FormationThreatProfile formation, float centerX, float centerY, UnitThreatProfile primary, float hitChance){
        BulletType bullet = threat.bullet;
        if(bullet == null) return 0f;

        int lightning = Math.max(0, bullet.lightning);
        if(lightning <= 0 && bullet.lightningLength <= 0) return 0f;

        float radius = lightningRadius(bullet);
        if(radius <= 0.0001f) return 0f;

        float shotRate = shotsPerSecond(threat);
        float damage = bullet.lightningDamage < 0f ? Math.max(0f, bullet.damage) : Math.max(0f, bullet.lightningDamage);
        if(damage <= 0.0001f) return 0f;
        if(lightning <= 0) lightning = 1;

        float impactX = centerX + primary.offsetX;
        float impactY = centerY + primary.offsetY;
        float total = 0f;

        for(int i = 0; i < formation.units.size; i++){
            UnitThreatProfile profile = formation.units.get(i);
            if(!canHit(threat, profile)) continue;

            float px = centerX + profile.offsetX;
            float py = centerY + profile.offsetY;
            float distance = Math.max(0f, Mathf.dst(px, py, impactX, impactY) - profile.radius);
            if(distance > radius) continue;

            float chance = hitChance * Mathf.clamp(1f - distance / Math.max(0.0001f, radius));
            chance = (float)Math.pow(chance, 0.75f);
            total += effectiveDps(shotRate * lightning * damage * chance, damage, bullet, profile);
        }

        return total;
    }

    private static float fragThreatDps(Threat threat, FormationThreatProfile formation, float centerX, float centerY, UnitThreatProfile primary, float hitChance){
        BulletType bullet = threat.bullet;
        if(bullet == null || bullet.fragBullet == null || bullet.fragBullet == bullet || bullet.fragBullets <= 0) return 0f;

        BulletType frag = bullet.fragBullet;
        float radius = Math.max(0f, bullet.fragOffsetMax) + bulletRange(frag);
        if(radius <= 0.0001f) return 0f;

        float impactX = centerX + primary.offsetX;
        float impactY = centerY + primary.offsetY;
        float shotRate = shotsPerSecond(threat);
        float fragDamage = Math.max(0f, frag.damage + Math.max(0f, frag.splashDamage) * 0.75f);
        if(fragDamage <= 0.0001f) fragDamage = Math.max(0f, frag.estimateDPS()) * 0.35f;
        if(fragDamage <= 0.0001f) return 0f;

        float total = 0f;
        for(int i = 0; i < formation.units.size; i++){
            UnitThreatProfile profile = formation.units.get(i);
            if(!canHitBullet(threat, frag, profile)) continue;

            float px = centerX + profile.offsetX;
            float py = centerY + profile.offsetY;
            float distance = Math.max(0f, Mathf.dst(px, py, impactX, impactY) - profile.radius);
            if(distance > radius) continue;

            float falloff = Mathf.clamp(1f - distance / Math.max(0.0001f, radius));
            float fragFactor = bullet instanceof FlakBulletType ? 0.06f : 0.12f;
            float expectedHits = bullet.fragBullets * fragFactor * falloff * hitChance;
            total += effectiveDps(shotRate * fragDamage * expectedHits, fragDamage, frag, profile);
        }

        return total;
    }

    private static float intervalThreatDps(Threat threat, FormationThreatProfile formation, float centerX, float centerY, UnitThreatProfile primary, float hitChance){
        BulletType bullet = threat.bullet;
        if(bullet == null || bullet.intervalBullet == null || bullet.intervalBullets <= 0 || bullet.bulletInterval <= 0.0001f) return 0f;

        BulletType interval = bullet.intervalBullet;
        float radius = Math.max(tilesize, bulletRange(interval));
        float impactX = centerX + primary.offsetX;
        float impactY = centerY + primary.offsetY;
        float rate = 60f / bullet.bulletInterval * bullet.intervalBullets * hitChance;
        float damage = Math.max(0f, interval.damage + Math.max(0f, interval.splashDamage) * 0.75f);
        if(damage <= 0.0001f) damage = Math.max(0f, interval.estimateDPS()) * 0.35f;
        if(damage <= 0.0001f) return 0f;

        float total = 0f;
        for(int i = 0; i < formation.units.size; i++){
            UnitThreatProfile profile = formation.units.get(i);
            if(!canHitBullet(threat, interval, profile)) continue;

            float px = centerX + profile.offsetX;
            float py = centerY + profile.offsetY;
            float distance = Math.max(0f, Mathf.dst(px, py, impactX, impactY) - profile.radius);
            if(distance > radius) continue;

            float falloff = Mathf.clamp(1f - distance / Math.max(0.0001f, radius));
            total += effectiveDps(rate * damage * falloff * 0.25f, damage, interval, profile);
        }

        return total;
    }

    private static float statusThreatDps(Threat threat, UnitThreatProfile profile, float hitChance){
        BulletType bullet = threat.bullet;
        if(bullet == null || bullet.status == null || bullet.status == StatusEffects.none) return 0f;
        return statusDpsFor(profile, bullet.status, bullet.statusDuration, shotsPerSecond(threat) * hitChance);
    }

    private static float fireThreatDps(Threat threat, UnitThreatProfile profile, float hitChance){
        BulletType bullet = threat.bullet;
        if(bullet == null || !bullet.makeFire || profile.flying) return 0f;
        return statusDpsFor(profile, StatusEffects.burning, 60f * maxStatusCarrySeconds, shotsPerSecond(threat) * hitChance * 0.4f);
    }

    private static float controlThreatDps(Threat threat, FormationThreatProfile formation, float centerX, float centerY, UnitThreatProfile primary, float threatInflate, RiskContext ctx){
        BulletType bullet = threat == null ? null : threat.bullet;
        if(bullet == null || bullet.status == null || bullet.status == StatusEffects.none || formation == null || primary == null) return 0f;

        StatusEffect effect = bullet.status;
        if(effect.speedMultiplier >= 0.999f && effect.dragMultiplier >= 0.999f && !effect.disarm) return 0f;

        float shotRate = shotsPerSecond(threat);
        if(shotRate <= 0.0001f) return 0f;

        float exposure = ctx == null ? 2f : Math.max(0.05f, ctx.horizonSeconds);
        float duration = Math.min(Math.max(0f, bullet.statusDuration) / 60f, Math.max(exposure, maxStatusCarrySeconds));
        if(duration <= 0.0001f) return 0f;

        float slow = Math.max(0f, 1f - Math.min(effect.speedMultiplier, effect.dragMultiplier));
        if(effect.disarm) slow = Math.max(slow, 0.35f);
        if(slow <= 0.0001f) return 0f;

        float damageDensity = Math.max(threat.dps, localFormationDps(threat, formation, centerX, centerY, threatInflate));
        if(damageDensity <= 0.0001f) return 0f;

        float primaryX = centerX + primary.offsetX;
        float primaryY = centerY + primary.offsetY;
        float distance = Mathf.dst(threat.x, threat.y, primaryX, primaryY);
        float hitChance = projectileHitChance(threat, primary, distance);

        float total = 0f;
        for(int i = 0; i < formation.units.size; i++){
            UnitThreatProfile profile = formation.units.get(i);
            if(profile == null || !canHit(threat, profile)) continue;
            if(profile.unit != null && profile.unit.isImmune(effect)) continue;

            float px = centerX + profile.offsetX;
            float py = centerY + profile.offsetY;
            if(!withinThreatRange(threat, profile, px, py, threatInflate)) continue;

            float chance = profile == primary ? hitChance : splashStatusChance(threat, profile, primary, centerX, centerY, hitChance);
            if(chance <= 0.0001f) continue;

            float expected = Math.min(1f, shotRate * chance * duration);
            total += damageDensity * slow * expected * 0.45f;
        }

        return total;
    }

    private static float localFormationDps(Threat threat, FormationThreatProfile formation, float centerX, float centerY, float threatInflate){
        if(threat == null || formation == null) return 0f;
        float total = 0f;
        int count = 0;
        for(int i = 0; i < formation.units.size; i++){
            UnitThreatProfile profile = formation.units.get(i);
            if(profile == null || !canHit(threat, profile)) continue;
            if(withinThreatRange(threat, profile, centerX + profile.offsetX, centerY + profile.offsetY, threatInflate)){
                total += Math.max(0f, threat.dps);
                count++;
            }
        }
        return count <= 0 ? 0f : total / count;
    }

    private static float splashStatusChance(Threat threat, UnitThreatProfile profile, UnitThreatProfile primary, float centerX, float centerY, float hitChance){
        BulletType bullet = threat == null ? null : threat.bullet;
        if(bullet == null || profile == null || primary == null) return 0f;

        float chance = 0f;
        if(bullet.splashDamageRadius > 0.0001f || bullet instanceof FlakBulletType){
            float radius = bullet.splashDamageRadius > 0.0001f ? bullet.splashDamageRadius : 30f;
            float impactX = centerX + primary.offsetX;
            float impactY = centerY + primary.offsetY;
            float px = centerX + profile.offsetX;
            float py = centerY + profile.offsetY;
            float distance = Math.max(0f, Mathf.dst(px, py, impactX, impactY) - profile.radius);
            if(distance <= radius + profile.radius){
                chance = Math.max(chance, splashHitChance(threat, false, hitChance) * splashFalloff(distance, radius));
            }
        }

        if(bullet.fragBullet != null && bullet.fragBullet != bullet && bullet.fragBullets > 0 && canHitBullet(threat, bullet.fragBullet, profile)){
            float radius = Math.max(0f, bullet.fragOffsetMax) + bulletRange(bullet.fragBullet);
            float impactX = centerX + primary.offsetX;
            float impactY = centerY + primary.offsetY;
            float px = centerX + profile.offsetX;
            float py = centerY + profile.offsetY;
            float distance = Math.max(0f, Mathf.dst(px, py, impactX, impactY) - profile.radius);
            if(radius > 0.0001f && distance <= radius){
                float fragFactor = bullet instanceof FlakBulletType ? 0.04f : 0.08f;
                chance = Math.max(chance, Mathf.clamp(bullet.fragBullets * fragFactor * Mathf.clamp(1f - distance / radius) * hitChance));
            }
        }

        return chance;
    }

    private static float statusDpsFor(UnitThreatProfile profile, StatusEffect effect, float durationTicks, float applicationsPerSecond){
        if(effect == null || effect == StatusEffects.none || effect.damage <= 0.0001f || durationTicks <= 0.0001f || applicationsPerSecond <= 0.0001f) return 0f;
        if(profile.unit != null && profile.unit.isImmune(effect)) return 0f;

        float maxDps = effect.damage * 60f;
        float appliedDps = applicationsPerSecond * effect.damage * Math.min(durationTicks, maxStatusCarrySeconds * 60f);
        return Math.min(maxDps, appliedDps);
    }

    private static UnitThreatProfile choosePrimaryTarget(Threat threat, FormationThreatProfile formation, float centerX, float centerY, float threatInflate){
        UnitThreatProfile best = null;
        float bestScore = -1f;

        for(int i = 0; i < formation.units.size; i++){
            UnitThreatProfile profile = formation.units.get(i);
            if(!canHit(threat, profile)) continue;

            float px = centerX + profile.offsetX;
            float py = centerY + profile.offsetY;
            if(!withinThreatRange(threat, profile, px, py, threatInflate)) continue;
            if(sameTeam(threat, profile)) continue;

            float dst2 = Mathf.dst2(threat.x, threat.y, px, py);
            float score;
            if(threat.preferStrongestTarget){
                score = profile.maxHealth * 100f + profile.effectiveHp + profile.radius * 25f - dst2 / 6400f;
            }else{
                float pressure = profile.effectiveHp <= 0.0001f ? 0f : threat.dps / profile.effectiveHp;
                score = 1000000f / (dst2 + 64f) + pressure * 35f;
            }
            if(score > bestScore){
                bestScore = score;
                best = profile;
            }
        }

        return best;
    }

    private static float threatAvailability(Threat threat, UnitThreatProfile primary, float centerX, float centerY, RiskContext ctx){
        float horizon = ctx == null ? 2f : Math.max(0.05f, ctx.horizonSeconds);
        float delay = 0f;

        Building building = threat.building;
        if(building instanceof Turret.TurretBuild && building.block instanceof Turret){
            Turret.TurretBuild tb = (Turret.TurretBuild)building;
            Turret turret = (Turret)building.block;
            float efficiency = Math.max(0f, Math.max(tb.efficiency, tb.potentialEfficiency));
            float timeScale = Math.max(0.0001f, tb.timeScale());

            if(tb.target == null){
                delay += turret.targetInterval / 60f;
            }else{
                delay += Math.max(0f, turret.newTargetInterval) / 60f * 0.25f;
            }

            float targetAngle = Angles.angle(threat.x, threat.y, centerX + primary.offsetX, centerY + primary.offsetY);
            float degreesPerSecond = Math.max(0.001f, turret.rotateSpeed * 60f * Math.max(0.05f, efficiency));
            delay += Angles.angleDist(tb.rotation, targetAngle) / degreesPerSecond;

            if(turret.minWarmup > 0.0001f && tb.shootWarmup < turret.minWarmup){
                delay += (turret.minWarmup - tb.shootWarmup) / Math.max(0.0001f, turret.shootWarmupSpeed * Math.max(0.05f, efficiency)) / 60f;
            }

            if(!threat.continuous && !threat.tractor){
                delay += Math.max(0f, turret.shoot.firstShotDelay) / 60f;
                float reloadRate = turretReloadRate(tb, turret, threat.bullet, efficiency, timeScale);
                delay += Math.max(0f, turret.reload - tb.reloadCounter) / reloadRate / 60f;
            }
        }else if(threat.building instanceof TractorBeamTurret.TractorBeamBuild && threat.building.block instanceof TractorBeamTurret){
            TractorBeamTurret.TractorBeamBuild tb = (TractorBeamTurret.TractorBeamBuild)threat.building;
            TractorBeamTurret turret = (TractorBeamTurret)threat.building.block;
            float efficiency = Math.max(0.05f, Math.max(tb.efficiency, tb.potentialEfficiency));
            float targetAngle = Angles.angle(threat.x, threat.y, centerX + primary.offsetX, centerY + primary.offsetY);
            delay += Angles.angleDist(tb.rotation, targetAngle) / Math.max(0.001f, turret.rotateSpeed * 60f * efficiency);
        }else if(threat.unit != null){
            if(!threat.continuous){
                float shotRate = shotsPerSecond(threat);
                if(shotRate > 0.0001f) delay += Math.min(horizon * 0.5f, 0.5f / shotRate);
            }
            delay += 0.05f;
        }

        return Mathf.clamp((horizon - Math.min(delay, horizon)) / horizon, 0f, 1f);
    }

    private static boolean withinThreatRange(Threat threat, UnitThreatProfile profile, float x, float y, float threatInflate){
        float range = Math.max(0f, threat.range + threatInflate + profile.radius);
        if(range <= 0.0001f) return false;

        float min = Math.max(0f, threat.minRange - profile.radius);
        float dst2 = Mathf.dst2(threat.x, threat.y, x, y);
        return dst2 <= range * range && dst2 >= min * min;
    }

    private static boolean canHit(Threat threat, UnitThreatProfile profile){
        if(threat == null || profile == null) return false;
        return canHitBullet(threat, threat.bullet, profile);
    }

    private static boolean canHitBullet(Threat threat, BulletType bullet, UnitThreatProfile profile){
        boolean air = threat.targetAir;
        boolean ground = threat.targetGround;
        if(bullet != null){
            air &= bullet.collidesAir;
            ground &= bullet.collidesGround;
        }
        if(profile.unit != null){
            return profile.unit.checkTarget(air, ground);
        }
        return profile.flying ? air : ground;
    }

    private static boolean sameTeam(Threat threat, UnitThreatProfile profile){
        if(profile == null || profile.unit == null) return false;
        if(threat.building != null) return threat.building.team == profile.unit.team;
        return threat.unit != null && threat.unit.team == profile.unit.team;
    }

    private static float turretReloadRate(Turret.TurretBuild tb, Turret turret, BulletType bullet, float efficiency, float timeScale){
        float reloadRate = Math.max(0f, efficiency) * Math.max(0f, timeScale);
        if(turret != null){
            reloadRate *= 1f + Math.max(0f, turretReloadCoolantBonus(tb, turret.coolantMultiplier, turret.coolant));
        }
        if(bullet != null){
            reloadRate *= Math.max(0f, bullet.reloadMultiplier);
        }
        return Math.max(0.0001f, reloadRate);
    }

    private static float turretReloadCoolantBonus(Building b, float coolantMultiplier, ConsumeLiquidBase coolant){
        if(b == null || coolant == null || b.liquids == null) return 0f;

        Liquid used = pickConsumedCoolantLiquid(b, coolant);
        if(used == null) return 0f;

        float amount = Math.min(Math.max(0f, coolant.amount), Math.max(0f, b.liquids.get(used)));
        if(amount <= 0.0001f) return 0f;

        return Math.max(0f, amount * Math.max(0f, used.heatCapacity) * Math.max(0f, coolantMultiplier));
    }

    private static Liquid pickConsumedCoolantLiquid(Building b, ConsumeLiquidBase coolant){
        if(b == null || coolant == null || b.liquids == null) return null;

        if(coolant instanceof ConsumeLiquidFilter){
            Liquid used = ((ConsumeLiquidFilter)coolant).getConsumed(b);
            if(used != null && b.liquids.get(used) > 0.0001f) return used;
        }

        Liquid current = b.liquids.current();
        if(current != null && b.liquids.get(current) > 0.0001f && coolant.consumes(current)){
            return current;
        }

        Seq<Liquid> liquids = content.liquids();
        for(int i = 0; i < liquids.size; i++){
            Liquid liquid = liquids.get(i);
            if(b.liquids.get(liquid) > 0.0001f && coolant.consumes(liquid)){
                return liquid;
            }
        }
        return null;
    }

    private static boolean sourceAvailable(Threat threat){
        if(threat.building != null){
            if(!threat.building.isAdded() || threat.building.dead()) return false;
            if(threat.building.block instanceof Turret && threat.building instanceof Turret.TurretBuild){
                Turret turret = (Turret)threat.building.block;
                if(turret.targetHealing) return false;
                BulletType ammo = ((Turret.TurretBuild)threat.building).peekAmmo();
                return ammo != null && !ammo.heals();
            }
            return true;
        }
        return threat.unit == null || validUnit(threat.unit);
    }

    private static float directDps(Threat threat){
        BulletType bullet = threat.bullet;
        if(bullet == null) return threat.dps;

        if(threat.continuous || bullet instanceof ContinuousBulletType){
            return Math.max(0f, threat.dps);
        }

        float shotRate = shotsPerSecond(threat);
        float direct = shotRate * Math.max(0f, bullet.damage);
        if(direct <= 0.0001f && bullet.splashDamageRadius <= 0.0001f){
            direct = threat.dps;
        }
        return Math.max(0f, direct);
    }

    private static float shotsPerSecond(Threat threat){
        if(threat == null) return 0f;
        if(threat.continuous || threat.tractor) return threat.shotsPerSecond > 0.0001f ? threat.shotsPerSecond : 60f;
        if(threat.shotsPerSecond > 0.0001f) return threat.shotsPerSecond;
        BulletType bullet = threat.bullet;
        if(bullet == null) return 60f;
        float estimate = Math.max(0.0001f, bullet.estimateDPS());
        return Math.max(0f, threat.dps / estimate);
    }

    private static float perHit(float dps, float shotRate, float fallback){
        if(shotRate > 0.0001f) return Math.max(0.0001f, dps / shotRate);
        return Math.max(0.0001f, fallback);
    }

    private static float effectiveDps(float rawDps, float rawPerHit, BulletType bullet, UnitThreatProfile profile){
        if(rawDps <= 0.0001f) return 0f;

        float perHit = Math.max(0.0001f, rawPerHit);
        if(bullet != null && bullet.maxDamageFraction > 0f){
            float cap = profile.maxHealth * bullet.maxDamageFraction + profile.shield;
            if(cap > 0f && perHit > cap){
                rawDps *= cap / perHit;
                perHit = cap;
            }
        }

        if(bullet != null && bullet.pierceArmor) return rawDps;

        float armor = profile.armor;
        if(bullet != null) armor *= bulletArmorMultiplier(bullet);
        if(Math.abs(armor) <= 0.0001f) return rawDps;

        float adjusted = applyArmor(perHit, armor);
        return rawDps * adjusted / perHit;
    }

    private static float projectileHitChance(Threat threat, UnitThreatProfile profile, float distance){
        BulletType bullet = threat.bullet;
        if(bullet == null || bullet.speed <= 0.0001f || threat.continuous) return 1f;

        float travelTicks = distance / Math.max(0.0001f, bullet.speed);
        float motion = profile.speed * travelTicks * 0.18f;
        float angle = threat.inaccuracy + bullet.inaccuracy;
        float spread = angle <= 0.0001f ? 0f : (float)Math.tan(angle * Math.PI / 180.0) * distance * 0.35f;
        float catchRadius = profile.radius + bulletHitRadius(bullet) + tilesize * 0.25f;

        float chance = catchRadius / Math.max(catchRadius, catchRadius + motion + spread);
        if(bullet.homingPower > 0.0001f) chance = Math.max(chance, 0.72f);
        if(bullet.splashDamageRadius > profile.radius) chance = Math.max(chance, 0.55f);
        return Mathf.clamp(chance, 0.18f, 1f);
    }

    private static boolean isLineLike(Threat threat){
        BulletType bullet = threat.bullet;
        return threat.continuous
            || bullet instanceof ContinuousBulletType
            || bullet instanceof LaserBulletType
            || bullet instanceof RailBulletType
            || bullet instanceof ShrapnelBulletType
            || bullet instanceof SapBulletType;
    }

    private static int pierceCap(Threat threat){
        BulletType bullet = threat.bullet;
        if(bullet == null) return 1;
        if(bullet.pierceCap > 0) return bullet.pierceCap;
        if(bullet.pierce || threat.continuous) return -1;
        return 1;
    }

    private static float bulletHitRadius(BulletType bullet){
        return bullet == null ? 0f : Math.max(0f, bullet.hitSize * 0.5f);
    }

    private static Seq<LineHitCandidate> collectLineHits(Threat threat, FormationThreatProfile formation, float centerX, float centerY, float tx, float ty, float threatInflate){
        Seq<LineHitCandidate> hits = new Seq<>();
        if(threat == null || formation == null) return hits;

        float vx = tx - threat.x;
        float vy = ty - threat.y;
        float len = Mathf.len(vx, vy);
        if(len <= 0.0001f) return hits;

        float invLen = 1f / len;
        for(int i = 0; i < formation.units.size; i++){
            UnitThreatProfile profile = formation.units.get(i);
            if(!canHit(threat, profile)) continue;

            float px = centerX + profile.offsetX;
            float py = centerY + profile.offsetY;
            if(!withinThreatRange(threat, profile, px, py, threatInflate)) continue;

            float projection = ((px - threat.x) * vx + (py - threat.y) * vy) * invLen;
            if(projection < -profile.radius || projection > len + profile.radius) continue;

            float pad = profile.radius + bulletHitRadius(threat.bullet) + 3f;
            if(segmentDistance(threat.x, threat.y, tx, ty, px, py) > pad) continue;

            hits.add(new LineHitCandidate(profile, Math.max(0f, projection)));
        }

        return hits;
    }

    private static float lineRawPerHit(Threat threat, float directDps, float shotRate){
        BulletType bullet = threat == null ? null : threat.bullet;
        float fallback = shotRate > 0.0001f ? directDps / shotRate : directDps / 60f;
        if(bullet == null) return Math.max(0.0001f, fallback);
        return Math.max(0.0001f, Math.max(bullet.damage, fallback));
    }

    private static float pierceBudgetCost(BulletType bullet, UnitThreatProfile profile){
        if(bullet == null || profile == null || bullet.pierceDamageFactor <= 0.0001f) return 0f;

        float health = Math.max(0.0001f, profile.health + profile.shield);
        if(bullet.maxDamageFraction > 0f){
            float cap = profile.maxHealth * bullet.maxDamageFraction + profile.shield;
            health = Math.min(health, cap);
        }

        return Math.max(0f, health * bullet.pierceDamageFactor);
    }

    private static float splashFalloff(float distance, float radius){
        if(radius <= 0.0001f) return 1f;
        float scaled = Mathf.clamp(1f - distance / radius);
        return 0.4f + scaled * 0.6f;
    }

    private static float splashHitChance(Threat threat, boolean primary, float hitChance){
        BulletType bullet = threat == null ? null : threat.bullet;
        float chance = Mathf.clamp(hitChance);

        if(bullet instanceof FlakBulletType){
            // Flak reliably proximity-detonates near the aimed target, but its small secondary
            // explosion should not be treated as guaranteed formation-wide damage.
            chance *= primary ? 0.9f : 0.45f;
        }

        return Mathf.clamp(chance, 0f, 1f);
    }

    private static float secondaryRadius(Threat threat){
        BulletType bullet = threat == null ? null : threat.bullet;
        if(bullet == null) return 0f;

        float radius = 0f;
        if(bullet.splashDamageRadius > 0f) radius = Math.max(radius, bullet.splashDamageRadius);
        if(bullet.lightning > 0 || bullet.lightningLength > 0) radius = Math.max(radius, lightningRadius(bullet));
        if(bullet.fragBullet != null && bullet.fragBullet != bullet) radius = Math.max(radius, Math.max(0f, bullet.fragOffsetMax) + bulletRange(bullet.fragBullet));
        if(bullet.intervalBullet != null) radius = Math.max(radius, bulletRange(bullet.intervalBullet));
        return radius;
    }

    private static float lightningRadius(BulletType bullet){
        if(bullet == null) return 0f;
        return Math.max(0f, (bullet.lightningLength + bullet.lightningLengthRand * 0.5f) * 6f);
    }

    private static float bulletRange(BulletType bullet){
        if(bullet == null) return 0f;
        if(bullet.range > 0f) return bullet.range;
        if(bullet.rangeOverride > 0f) return bullet.rangeOverride;
        if(bullet.maxRange > 0f) return bullet.maxRange;
        if(bullet.speed <= 0.0001f) return Math.max(0f, bullet.splashDamageRadius);
        return Math.max(0f, bullet.speed * bullet.lifetime);
    }

    private static float bulletArmorMultiplier(BulletType bullet){
        if(bullet == null) return 1f;
        Field field = bulletArmorMultiplierField();
        if(field == null) return 1f;

        try{
            return field.getFloat(bullet);
        }catch(Throwable ignored){
            return 1f;
        }
    }

    private static Field bulletArmorMultiplierField(){
        if(!bulletArmorMultiplierFieldChecked){
            bulletArmorMultiplierFieldChecked = true;
            try{
                bulletArmorMultiplierField = BulletType.class.getField("armorMultiplier");
            }catch(Throwable ignored){
                bulletArmorMultiplierField = null;
            }
        }
        return bulletArmorMultiplierField;
    }

    private static boolean lineOfSightBlocked(float x1, float y1, float x2, float y2){
        if(world == null) return false;
        try{
            return World.raycast(World.toTile(x1), World.toTile(y1), World.toTile(x2), World.toTile(y2), (x, y) -> {
                Tile tile = world.tile(x, y);
                return tile != null && tile.solid();
            });
        }catch(Throwable ignored){
            return false;
        }
    }

    private static float segmentDistance(float ax, float ay, float bx, float by, float px, float py){
        float vx = bx - ax;
        float vy = by - ay;
        float len2 = vx * vx + vy * vy;
        if(len2 <= 0.0001f) return Mathf.dst(ax, ay, px, py);

        float t = ((px - ax) * vx + (py - ay) * vy) / len2;
        t = Mathf.clamp(t, 0f, 1f);
        float qx = ax + vx * t;
        float qy = ay + vy * t;
        return Mathf.dst(px, py, qx, qy);
    }

    private static float killPressureBonus(float totalDps, FormationThreatProfile formation){
        if(totalDps <= 0.0001f || formation.minEffectiveHp <= 0.0001f) return 0f;
        float pressure = totalDps / formation.minEffectiveHp;
        float cappedGroupHp = Math.min(formation.totalEffectiveHp, 400f);
        return pressure * cappedGroupHp * 0.18f;
    }

    private static float displayDps(float totalDps, FormationThreatProfile formation){
        if(totalDps <= 0.0001f) return 0f;
        int count = formation == null || formation.units == null ? 1 : Math.max(1, formation.units.size);
        return totalDps / count;
    }

    private static boolean validUnit(Unit unit){
        return unit != null && unit.type != null && unit.isAdded() && !unit.dead();
    }

    static final class FormationThreatProfile{
        final Seq<UnitThreatProfile> units;
        final float centerX, centerY;
        final float maxOffsetRadius;
        final float totalEffectiveHp;
        final float minEffectiveHp;

        FormationThreatProfile(Seq<UnitThreatProfile> units, float centerX, float centerY){
            this.units = units == null ? new Seq<UnitThreatProfile>() : units;
            this.centerX = centerX;
            this.centerY = centerY;

            float max = 0f;
            float totalHp = 0f;
            float minHp = Float.POSITIVE_INFINITY;
            for(int i = 0; i < this.units.size; i++){
                UnitThreatProfile profile = this.units.get(i);
                max = Math.max(max, Mathf.len(profile.offsetX, profile.offsetY) + profile.radius);
                totalHp += profile.effectiveHp;
                minHp = Math.min(minHp, profile.effectiveHp);
            }

            this.maxOffsetRadius = max;
            this.totalEffectiveHp = totalHp;
            this.minEffectiveHp = minHp == Float.POSITIVE_INFINITY ? 0f : minHp;
        }
    }

    private static final class LineHitCandidate{
        final UnitThreatProfile profile;
        final float distance;

        LineHitCandidate(UnitThreatProfile profile, float distance){
            this.profile = profile;
            this.distance = distance;
        }
    }

    static final class UnitThreatProfile{
        final Unit unit;
        final float offsetX, offsetY;
        final boolean flying;
        final float radius;
        final float health;
        final float shield;
        final float maxHealth;
        final float armor;
        final float speed;
        final float effectiveHp;

        UnitThreatProfile(Unit unit, float offsetX, float offsetY){
            this.unit = unit;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.flying = unit != null && unit.isFlying();
            this.radius = unit == null ? tilesize * 0.5f : Math.max(1f, unit.hitSize / 2f);
            this.health = unit == null ? 1f : Math.max(0.0001f, unit.health());
            this.shield = unit == null ? 0f : Math.max(0f, unit.shield());
            this.maxHealth = unit == null ? this.health : Math.max(this.health, unit.maxHealth());
            this.armor = unit == null ? 0f : Math.max(0f, unit.armor());
            this.speed = unit == null ? 0f : Math.max(0.0001f, unit.speed());
            this.effectiveHp = Math.max(0.0001f, this.health + this.shield);
        }
    }
}
