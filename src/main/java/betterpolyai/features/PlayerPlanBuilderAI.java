package betterpolyai.features;

import arc.math.Mathf;
import arc.util.Time;
import mindustry.ai.types.CommandAI;
import mindustry.ai.types.FlyingAI;
import mindustry.ai.types.GroundAI;
import mindustry.ai.types.PrebuildAI;
import mindustry.entities.units.AIController;
import mindustry.entities.units.BuildPlan;
import mindustry.gen.Unit;
import mindustry.world.Build;
import mindustry.world.Tile;
import mindustry.world.blocks.ConstructBlock.ConstructBuild;

import static mindustry.Vars.world;

import static mindustry.Vars.state;

public class PlayerPlanBuilderAI extends AIController {

    public static float buildRadius = 1500f;
    private static final float manualMoveDetectDistance = 1.5f;
    private static final float manualMoveDetectSpeed = 0.35f;
    private static final float manualMoveAwayDistance = 1.5f;
    private static final float manualMoveResumeSpeed = 0.15f;
    private static final float manualMoveResumeDelay = 12f;

    private Unit trackedUnit;
    private boolean pausedByManualMove;
    private boolean hasLastPosition;
    private float lastX;
    private float lastY;
    private boolean hadLastTarget;
    private boolean lastNeededAutoMove;
    private boolean settlingAfterAutoMove;
    private float lastDistanceToTarget;
    private float stoppedTime;

    @Override
    public void updateMovement() {
        if (trackedUnit != unit) {
            trackedUnit = unit;
            resetAutonomyState();
        }

        if (target != null && shouldShoot()) {
            unit.lookAt(target);
        } else if (!unit.type.flying) {
            unit.lookAt(unit.prefRotation());
        }

        unit.updateBuilding = true;

        BuildPlan req = unit.buildPlan();
        boolean moving = false;
        boolean hasBuildTarget = false;
        boolean needsAutoMove = false;
        float targetDistance = -1f;

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
                float maxBuildRange = Math.max(0f, Math.min(unit.type.buildRange - unit.type.hitSize * 2f, buildRadius));
                Tile moveTile = reqTile != null ? reqTile : world.tile(req.x, req.y);
                if (moveTile != null) {
                    hasBuildTarget = true;
                    targetDistance = unit.dst(moveTile.worldx(), moveTile.worldy());
                    needsAutoMove = targetDistance > maxBuildRange;

                    updateManualMovePause(hasBuildTarget, needsAutoMove, targetDistance);

                    if (needsAutoMove && PolyAiFeature.autonomousMovementAllowed()) {
                        moveTo(moveTile, maxBuildRange, 20f);
                        settlingAfterAutoMove = true;
                        moving = !unit.within(moveTile, maxBuildRange);
                    }
                }
            } else {
                unit.plans.removeFirst();
            }
        } else {
            updateManualMovePause(false, false, -1f);
        }

        updateLastMovementSample(hasBuildTarget, needsAutoMove, targetDistance);

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

    boolean isPausedByManualMove() {
        return pausedByManualMove;
    }

    void resetAutonomyState() {
        pausedByManualMove = false;
        hasLastPosition = false;
        hadLastTarget = false;
        lastNeededAutoMove = false;
        settlingAfterAutoMove = false;
        lastDistanceToTarget = -1f;
        stoppedTime = 0f;
    }

    private void updateManualMovePause(boolean hasBuildTarget, boolean needsAutoMove, float currentDistanceToTarget) {
        float movement = hasLastPosition ? Mathf.dst(lastX, lastY, unit.x, unit.y) : 0f;
        float speed = unit.vel.len();

        if (pausedByManualMove) {
            if (speed <= manualMoveResumeSpeed && movement <= manualMoveDetectDistance) {
                stoppedTime += Time.delta;
                if (stoppedTime >= manualMoveResumeDelay) {
                    pausedByManualMove = false;
                    stoppedTime = 0f;
                    PolyAiFeature.onManualMovePauseChanged(false);
                }
            } else {
                stoppedTime = 0f;
            }
            return;
        }

        stoppedTime = 0f;
        if (!hasBuildTarget) {
            settlingAfterAutoMove = false;
            return;
        }

        if (settlingAfterAutoMove && speed <= manualMoveResumeSpeed && movement <= manualMoveDetectDistance) {
            settlingAfterAutoMove = false;
        }

        if (!hasLastPosition) return;

        boolean moved = movement >= manualMoveDetectDistance || speed >= manualMoveDetectSpeed;
        if (!moved) return;

        boolean movedInsideBuildRange = !needsAutoMove && !lastNeededAutoMove && !settlingAfterAutoMove;
        boolean movedAwayFromTarget = needsAutoMove && hadLastTarget && lastDistanceToTarget >= 0f
            && currentDistanceToTarget > lastDistanceToTarget + manualMoveAwayDistance;
        boolean movedAgainstTarget = needsAutoMove && speed >= manualMoveDetectSpeed && isMovingAwayFromTarget();

        if (movedInsideBuildRange || movedAwayFromTarget || movedAgainstTarget) {
            pausedByManualMove = true;
            settlingAfterAutoMove = false;
            stoppedTime = 0f;
            PolyAiFeature.onManualMovePauseChanged(true);
        }
    }

    private boolean isMovingAwayFromTarget() {
        BuildPlan req = unit.buildPlan();
        Tile reqTile = req != null ? req.tile() : null;
        Tile moveTile = reqTile != null ? reqTile : (req != null ? world.tile(req.x, req.y) : null);
        if (moveTile == null) return false;

        float dx = moveTile.worldx() - unit.x;
        float dy = moveTile.worldy() - unit.y;
        float len = Mathf.len(dx, dy);
        if (len <= 0.001f) return false;

        float dot = (unit.vel.x * dx + unit.vel.y * dy) / len;
        return dot < -manualMoveDetectSpeed;
    }

    private void updateLastMovementSample(boolean hasBuildTarget, boolean needsAutoMove, float distanceToTarget) {
        lastX = unit.x;
        lastY = unit.y;
        hasLastPosition = true;

        if (hasBuildTarget) {
            hadLastTarget = true;
            lastNeededAutoMove = needsAutoMove;
            lastDistanceToTarget = distanceToTarget;
        } else {
            hadLastTarget = false;
            lastNeededAutoMove = false;
            lastDistanceToTarget = -1f;
        }
    }
}
