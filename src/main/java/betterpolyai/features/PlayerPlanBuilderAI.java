package betterpolyai.features;

import arc.math.Mathf;
import mindustry.ai.types.CommandAI;
import mindustry.ai.types.FlyingAI;
import mindustry.ai.types.GroundAI;
import mindustry.ai.types.PrebuildAI;
import mindustry.entities.units.AIController;
import mindustry.entities.units.BuildPlan;
import mindustry.world.Build;
import mindustry.world.Tile;
import mindustry.world.blocks.ConstructBlock.ConstructBuild;

import static mindustry.Vars.world;

import static mindustry.Vars.state;

public class PlayerPlanBuilderAI extends AIController {

    public static float buildRadius = 1500f;
    private static final int tileSize = 8;
    private static final int minGapTiles = 0;
    private static final int maxGapTiles = 30;
    private static int buildGapTiles = 0;

    public static void setBuildGapTiles(int value) {
        buildGapTiles = Mathf.clamp(value, minGapTiles, maxGapTiles);
    }

    @Override
    public void updateMovement() {
        if (target != null && shouldShoot()) {
            unit.lookAt(target);
        } else if (!unit.type.flying) {
            unit.lookAt(unit.prefRotation());
        }

        unit.updateBuilding = true;

        boolean moving = false;
        BuildPlan req = unit.buildPlan();
        if (req != null) {
            Tile reqTile = req.tile();

            boolean validConstruct = false;
            if (reqTile != null && reqTile.build instanceof ConstructBuild) {
                ConstructBuild cons = (ConstructBuild) reqTile.build;
                validConstruct = cons.current == req.block;
            }

            boolean validAction = req.breaking
                ? Build.validBreak(unit.team(), req.x, req.y)
                : Build.validPlace(req.block, unit.team(), req.x, req.y, req.rotation);

            if (validConstruct || validAction) {
                float maxBuildRange = Math.min(unit.type.buildRange - unit.type.hitSize * 2f, buildRadius);
                Tile moveTile = reqTile != null ? reqTile : world.tile(req.x, req.y);
                if (moveTile != null) {
                    float desiredRange = Mathf.clamp(buildGapTiles * (float) tileSize, 0f, maxBuildRange);
                    float distance = unit.dst(moveTile.worldx(), moveTile.worldy());
                    boolean insideBuildRange = distance <= maxBuildRange;
                    boolean directBuildAtDefaultGap = buildGapTiles == 0 && insideBuildRange;
                    if (!directBuildAtDefaultGap && distance > desiredRange) {
                        moveTo(moveTile, desiredRange, 20f);
                        moving = !unit.within(moveTile, desiredRange);
                    }
                }
            } else {
                unit.plans.removeFirst();
            }
        }

        if (!unit.type.flying) {
            unit.updateBoosting(
                unit.type.boostWhenBuilding ||
                moving ||
                unit.floorOn().isDuct ||
                unit.floorOn().damageTaken > 0f ||
                unit.floorOn().isDeep()
            );
        }
    }

    @Override
    public AIController fallback() {
        if (unit.team.isAI() && unit.team.rules().prebuildAi) {
            return new PrebuildAI();
        }
        return unit.type.flying ? new FlyingAI() : new GroundAI();
    }

    @Override
    public boolean useFallback() {
        if (unit.team.isAI() && unit.team.rules().prebuildAi) {
            return true;
        }
        return state.rules.waves && unit.team == state.rules.waveTeam && !unit.team.rules().rtsAi;
    }

    @Override
    public boolean shouldFire() {
        return !(unit.controller() instanceof CommandAI) || ((CommandAI) unit.controller()).shouldFire();
    }

    @Override
    public boolean shouldShoot() {
        return !unit.isBuilding() && unit.type.canAttack;
    }
}
