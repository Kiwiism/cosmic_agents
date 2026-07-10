package server.agents.runtime;

import server.agents.capabilities.follow.AgentFollowMotionObservationService;
import server.agents.capabilities.follow.AgentOwnerMotionStateRuntime;
import server.agents.capabilities.movement.AgentMovementPhysicsConfig;
import server.agents.capabilities.movement.AgentTargetSnapshot;
import server.agents.integration.AgentRuntimeIdentityRuntime;

import java.awt.Point;

/**
 * Prepares and executes one ownerless movement-only runtime tick.
 */
public final class AgentMovementOnlyTickCoordinator {
    private AgentMovementOnlyTickCoordinator() {
    }

    public static boolean stepMovementOnly(AgentRuntimeEntry entry, long tickAtMs) {
        return stepMovementOnly(entry, tickAtMs, defaultConfig());
    }

    public static boolean stepMovementOnly(AgentRuntimeEntry entry, long tickAtMs, TickConfig config) {
        if (!AgentRuntimeIdentityRuntime.hasBot(entry)) {
            return false;
        }

        boolean runAiTick = AgentTickOrchestrator.prepareTick(
                entry,
                config.tickMs(),
                config.aiTickMs(),
                tickAtMs);

        AgentTargetSnapshot targetSnapshot = AgentTargetSnapshotCoordinator.captureTargetSnapshot(entry);
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
                                        TickConfig config) {
        AgentMovementOnlyModeCoordinator.stepMovementOnly(
                entry,
                targetPosition,
                runAiTick,
                AgentTickStateRuntime.lastTickAtMs(entry),
                AgentTargetSnapshotCoordinator::resolveFollowAnchor,
                config.modeConfig());
    }

    private static TickConfig defaultConfig() {
        return new TickConfig(
                AgentMovementPhysicsConfig.configuredMovementTickMs(),
                AgentRuntimeConfig.cfg.AI_TICK_MS,
                AgentMovementPhysicsConfig.configuredTeleportDist(),
                AgentMovementPhysicsConfig.configuredOutOfBoundsTeleportDist(),
                AgentRuntimeConfig.cfg.GRIND_PARTY_TELEPORT_DIST_MULTIPLIER,
                AgentMovementPhysicsConfig.configuredFollowDist(),
                AgentMovementPhysicsConfig.configuredStopDist(),
                AgentRuntimeConfig.cfg.ENABLE_UNSTUCK);
    }

    public record TickConfig(int tickMs,
                             int aiTickMs,
                             int teleportDistance,
                             int outOfBoundsTeleportDistance,
                             int grindPartyTeleportDistanceMultiplier,
                             int followDistance,
                             int stopDistance,
                             boolean enableUnstuck) {
        private AgentMovementOnlyModeCoordinator.ModeConfig modeConfig() {
            return new AgentMovementOnlyModeCoordinator.ModeConfig(
                    teleportDistance,
                    outOfBoundsTeleportDistance,
                    grindPartyTeleportDistanceMultiplier,
                    followDistance,
                    stopDistance,
                    enableUnstuck);
        }
    }
}
