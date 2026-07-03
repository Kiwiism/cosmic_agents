package server.agents.runtime;

import server.agents.capabilities.movement.AgentMovementPhysicsConfig;
import server.agents.integration.AgentBotOwnerMotionStateRuntime;
import server.agents.integration.AgentBotRuntimeIdentityRuntime;
import server.agents.integration.AgentBotTickStateRuntime;
import server.bots.BotEntry;
import server.bots.BotMovementManager;

import java.awt.Point;

/**
 * Runtime wiring for ownerless movement-only ticks.
 */
public final class AgentMovementOnlyStepRuntime {
    private AgentMovementOnlyStepRuntime() {
    }

    public static boolean stepMovementOnly(BotEntry entry, long tickAtMs) {
        return stepMovementOnly(entry, tickAtMs, defaultConfig());
    }

    public static boolean stepMovementOnly(BotEntry entry, long tickAtMs, MovementOnlyStepConfig config) {
        if (!AgentBotRuntimeIdentityRuntime.hasBot(entry)) {
            return false;
        }

        boolean runAiTick = AgentTickOrchestrator.prepareTick(
                entry,
                config.tickMs(),
                config.aiTickMs(),
                tickAtMs);

        AgentTargetSnapshot targetSnapshot = AgentTargetSnapshotRuntime.captureTargetSnapshot(entry);
        Point leaderPosition = targetSnapshot.rawOwnerPos();
        AgentTickStateMaintenanceService.updateObservedLeaderMotion(entry, leaderPosition);
        AgentBotOwnerMotionStateRuntime.rememberOwnerPosition(entry, leaderPosition);
        stepMovementOnly(entry, targetSnapshot.primaryTargetPos(), runAiTick, config);
        return runAiTick;
    }

    public static void stepMovementOnly(BotEntry entry,
                                        Point targetPosition,
                                        boolean runAiTick) {
        stepMovementOnly(entry, targetPosition, runAiTick, defaultConfig());
    }

    public static void stepMovementOnly(BotEntry entry,
                                        Point targetPosition,
                                        boolean runAiTick,
                                        MovementOnlyStepConfig config) {
        AgentMovementOnlyRuntime.stepMovementOnly(
                entry,
                targetPosition,
                runAiTick,
                AgentBotTickStateRuntime.lastTickAtMs(entry),
                AgentTargetSnapshotRuntime::resolveFollowAnchor,
                config.movementOnlyConfig());
    }

    private static MovementOnlyStepConfig defaultConfig() {
        return new MovementOnlyStepConfig(
                AgentMovementPhysicsConfig.configuredMovementTickMs(),
                AgentRuntimeConfig.cfg.AI_TICK_MS,
                BotMovementManager.configuredTeleportDist(),
                BotMovementManager.configuredOutOfBoundsTeleportDist(),
                AgentRuntimeConfig.cfg.GRIND_PARTY_TELEPORT_DIST_MULTIPLIER,
                BotMovementManager.configuredFollowDist(),
                BotMovementManager.configuredStopDist(),
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
