package server.agents.capabilities.movement;

import server.agents.capabilities.navigation.AgentNavigationDebugStateRuntime;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.monitoring.AgentPerformanceMonitor;
import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;
import java.util.function.IntUnaryOperator;

/**
 * Agent-owned stuck detection and unstuck trigger used by the movement tick tail.
 */
public final class AgentStuckDetectionService {
    private static final int STUCK_DRIFT_RADIUS_PX = 16;
    private static final int GROUNDED_STUCK_THRESHOLD_MS = 500;
    private static final int SUSPENDED_STUCK_THRESHOLD_MS = 1_500;

    @FunctionalInterface
    public interface UnstuckAction {
        void tick(AgentRuntimeEntry entry);
    }

    public record StuckDetectionHooks(IntUnaryOperator tickDown,
                                      UnstuckAction unstuckAction,
                                      int movementTickMs,
                                      boolean enableUnstuck) {
    }

    private AgentStuckDetectionService() {
    }

    public static void tickStuckDetection(AgentRuntimeEntry entry, boolean enableUnstuck) {
        tickStuckDetection(
                entry,
                new StuckDetectionHooks(
                        AgentMovementTimers::tickDown,
                        AgentMovementRecoveryService::tickUnstuck,
                        AgentMovementPhysicsConfig.configuredMovementTickMs(),
                        enableUnstuck));
    }

    public static void tickStuckDetection(AgentRuntimeEntry entry, StuckDetectionHooks hooks) {
        if (!AgentPerformanceMonitor.enabled()) {
            doStuckDetection(entry, hooks);
            return;
        }

        long startedAt = System.nanoTime();
        try {
            doStuckDetection(entry, hooks);
        } finally {
            AgentPerformanceMonitor.record("stuck-detect", System.nanoTime() - startedAt);
        }
    }

    static void doStuckDetection(AgentRuntimeEntry entry, StuckDetectionHooks hooks) {
        AgentMovementStuckStateRuntime.setUnstuckCooldownMs(
                entry,
                hooks.tickDown().applyAsInt(AgentMovementStuckStateRuntime.unstuckCooldownMs(entry)));

        boolean suspended = AgentMovementStateRuntime.inAir(entry)
                || AgentMovementStateRuntime.climbing(entry);
        if (AgentNavigationDebugStateRuntime.graphWarmupFallback(entry)
                || (!AgentNavigationDebugStateRuntime.hasActiveNavigationEdge(entry)
                        && !AgentMoveTargetStateRuntime.hasMoveTarget(entry))) {
            AgentMovementStuckStateRuntime.resetStuckProgress(entry);
            return;
        }

        Point agentPosition = AgentRuntimeIdentityRuntime.botPosition(entry);
        if (!AgentMovementStuckStateRuntime.hasStuckCheckPosition(entry)) {
            AgentMovementStuckStateRuntime.rememberStuckCheckPosition(entry, agentPosition);
            return;
        }

        boolean moved = AgentMovementStuckStateRuntime.movedSinceStuckCheck(
                entry, agentPosition, STUCK_DRIFT_RADIUS_PX);
        if (moved) {
            AgentMovementStuckStateRuntime.resetStuckMs(entry);
            AgentMovementStuckStateRuntime.rememberStuckCheckPosition(entry, agentPosition);
        } else {
            AgentMovementStuckStateRuntime.addStuckMs(entry, hooks.movementTickMs());
        }

        int thresholdMs = suspended ? SUSPENDED_STUCK_THRESHOLD_MS : GROUNDED_STUCK_THRESHOLD_MS;
        if (hooks.enableUnstuck()
                && AgentMovementStuckStateRuntime.stuckForAtLeast(entry, thresholdMs)
                && !AgentMovementStuckStateRuntime.hasUnstuckCooldown(entry)) {
            AgentMovementStuckStateRuntime.resetStuckProgress(entry);
            hooks.unstuckAction().tick(entry);
        }
    }
}
