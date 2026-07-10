package server.agents.runtime;

import server.agents.capabilities.follow.AgentOwnerMotionStateRuntime;
import server.agents.capabilities.movement.AgentMovementPhysicsConfig;
import server.agents.capabilities.movement.AgentTargetSnapshot;
import server.agents.capabilities.follow.AgentFollowMotionObservationService;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.runtime.AgentTickStateRuntime;

import java.awt.Point;

/**
 * Runtime wiring for ownerless movement-only ticks.
 */
public final class AgentMovementOnlyStepRuntime {
    private AgentMovementOnlyStepRuntime() {
    }

    public static boolean stepMovementOnly(AgentRuntimeEntry entry, long tickAtMs) {
        return stepMovementOnly(entry, tickAtMs, defaultConfig());
    }

    public static boolean stepMovementOnly(AgentRuntimeEntry entry, long tickAtMs, MovementOnlyStepConfig config) {
        if (!AgentRuntimeIdentityRuntime.hasBot(entry)) {
            return false;
        }

        boolean runAiTick = AgentTickOrchestrator.prepareTick(
                entry,
                config.tickMs(),
                config.aiTickMs(),
                tickAtMs);

        AgentTargetSnapshot targetSnapshot = AgentTargetSnapshotRuntime.captureTargetSnapshot(entry);
        Point leaderPosition = targetSnapshot.rawOwnerPos();
        AgentFollowMotionObservationService.updateObservedLeaderMotion(entry, leaderPosition);
        AgentOwnerMotionStateRuntime.rememberOwnerPosition(entry, leaderPosition);
        stepMovementOnly(entry, targetSnapshot.primaryTargetPos(), runAiTick, config);
        return runAiTick;
    }

    public static void stepMovementOnly(AgentRuntimeEntry entry,
                                        Point targetPosition,
                                        boolean runAiTick) {
        stepMovementOnly(entry, targetPosition, runAiTick, defaultConfig());
    }

    public static void stepMovementOnly(AgentRuntimeEntry entry,
                                        Point targetPosition,
                                        boolean runAiTick,
                                        MovementOnlyStepConfig config) {
        AgentMovementOnlyRuntime.stepMovementOnly(
                entry,
                targetPosition,
                runAiTick,
                AgentTickStateRuntime.lastTickAtMs(entry),
                AgentTargetSnapshotRuntime::resolveFollowAnchor,
                config.movementOnlyConfig());
    }

    private static MovementOnlyStepConfig defaultConfig() {
        return new MovementOnlyStepConfig(
                AgentMovementPhysicsConfig.configuredMovementTickMs(),
                AgentRuntimeConfig.cfg.AI_TICK_MS,
                AgentMovementPhysicsConfig.configuredTeleportDist(),
                AgentMovementPhysicsConfig.configuredOutOfBoundsTeleportDist(),
                AgentRuntimeConfig.cfg.GRIND_PARTY_TELEPORT_DIST_MULTIPLIER,
                AgentMovementPhysicsConfig.configuredFollowDist(),
                AgentMovementPhysicsConfig.configuredStopDist(),
                AgentRuntimeConfig.cfg.ENABLE_UNSTUCK);
    }

    public record MovementOnlyStepConfig(int tickMs,
                                         int aiTickMs,
                                         int teleportDistance,
                                         int outOfBoundsTeleportDistance,
                                         int grindPartyTeleportDistanceMultiplier,
                                         int followDistance,
                                         int stopDistance,
                                         boolean enableUnstuck) {
        private AgentMovementOnlyRuntime.MovementOnlyConfig movementOnlyConfig() {
            return new AgentMovementOnlyRuntime.MovementOnlyConfig(
                    teleportDistance,
                    outOfBoundsTeleportDistance,
                    grindPartyTeleportDistanceMultiplier,
                    followDistance,
                    stopDistance,
                    enableUnstuck);
        }
    }
}
