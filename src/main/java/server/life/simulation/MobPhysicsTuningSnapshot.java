package server.life.simulation;

import server.agents.capabilities.combat.AgentCombatConfig;

/** Hot-path tuning captured once per outer physics pass. Live changes apply on the next pass. */
public record MobPhysicsTuningSnapshot(
        int maxCatchUpSteps,
        int publicationIntervalMs,
        long publicationIntervalNanos,
        long aggroTimeoutNanos,
        double speedMultiplier,
        double knockbackMultiplier,
        int flyDeadZoneX,
        int flyDeadZoneY,
        double leftEdgeInsetPx,
        double rightEdgeInsetPx,
        int stopDistanceX,
        int resumeDistanceX,
        int jumpTargetHeight,
        int maxSafeEdgePx) {

    public static MobPhysicsTuningSnapshot capture() {
        int publicationMs = Math.max(20,
                AgentCombatConfig.cfg.MOB_PHYSICS_PUBLICATION_INTERVAL_MS);
        long aggroMs = Math.max(0L, AgentCombatConfig.cfg.MOB_PHYSICS_AGGRO_TIMEOUT_MS);
        return new MobPhysicsTuningSnapshot(
                Math.max(1, AgentCombatConfig.cfg.MOB_PHYSICS_MAX_CATCH_UP_STEPS),
                publicationMs,
                publicationMs * 1_000_000L,
                aggroMs * 1_000_000L,
                Math.max(0, AgentCombatConfig.cfg.MOB_PHYSICS_SPEED_PERCENT) / 100.0,
                Math.max(0, AgentCombatConfig.cfg.MOB_PHYSICS_KNOCKBACK_PERCENT) / 100.0,
                Math.max(0, AgentCombatConfig.cfg.MOB_PHYSICS_FLY_DEAD_ZONE_X),
                Math.max(0, AgentCombatConfig.cfg.MOB_PHYSICS_FLY_DEAD_ZONE_Y),
                Math.max(0, AgentCombatConfig.cfg.MOB_PHYSICS_LEFT_EDGE_INSET_PX),
                Math.max(0, AgentCombatConfig.cfg.MOB_PHYSICS_RIGHT_EDGE_INSET_PX),
                Math.max(0, AgentCombatConfig.cfg.MOB_PHYSICS_STOP_DISTANCE_X),
                Math.max(0, AgentCombatConfig.cfg.MOB_PHYSICS_RESUME_DISTANCE_X),
                Math.max(1, AgentCombatConfig.cfg.MOB_PHYSICS_JUMP_TARGET_HEIGHT),
                Math.max(0, AgentCombatConfig.cfg.MOB_PHYSICS_MAX_SAFE_EDGE_PX));
    }
}
