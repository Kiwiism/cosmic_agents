package server.agents.runtime;

import server.agents.capabilities.movement.AgentMoveTargetStateRuntime;
import server.agents.integration.AgentMovementStateRuntime;
import server.agents.capabilities.movement.AgentMovementStuckStateRuntime;
import server.agents.integration.AgentNavigationDebugStateRuntime;
import server.agents.integration.AgentRuntimeIdentityRuntime;

import java.awt.Point;
import java.util.function.IntUnaryOperator;

/**
 * Agent-owned stuck detection and unstuck trigger used by the movement tick tail.
 */
public final class AgentStuckDetectionService {
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

        if (AgentMovementStateRuntime.inAir(entry)
                || AgentMovementStateRuntime.climbing(entry)
                || AgentNavigationDebugStateRuntime.graphWarmupFallback(entry)
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

        boolean moved = AgentMovementStuckStateRuntime.movedSinceStuckCheck(entry, agentPosition, 8);
        if (moved) {
            AgentMovementStuckStateRuntime.resetStuckMs(entry);
            AgentMovementStuckStateRuntime.rememberStuckCheckPosition(entry, agentPosition);
        } else {
            AgentMovementStuckStateRuntime.addStuckMs(entry, hooks.movementTickMs());
        }

        if (hooks.enableUnstuck()
                && AgentMovementStuckStateRuntime.stuckForAtLeast(entry, 500)
                && !AgentMovementStuckStateRuntime.hasUnstuckCooldown(entry)) {
            AgentMovementStuckStateRuntime.resetStuckProgress(entry);
            hooks.unstuckAction().tick(entry);
        }
    }
}
