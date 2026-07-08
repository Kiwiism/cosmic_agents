package server.agents.runtime;

import server.agents.integration.AgentBotMoveTargetStateRuntime;
import server.agents.integration.AgentBotMovementStateRuntime;
import server.agents.integration.AgentBotMovementStuckStateRuntime;
import server.agents.integration.AgentBotNavigationDebugStateRuntime;
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
        AgentBotMovementStuckStateRuntime.setUnstuckCooldownMs(
                entry,
                hooks.tickDown().applyAsInt(AgentBotMovementStuckStateRuntime.unstuckCooldownMs(entry)));

        if (AgentBotMovementStateRuntime.inAir(entry)
                || AgentBotMovementStateRuntime.climbing(entry)
                || AgentBotNavigationDebugStateRuntime.graphWarmupFallback(entry)
                || (!AgentBotNavigationDebugStateRuntime.hasActiveNavigationEdge(entry)
                        && !AgentBotMoveTargetStateRuntime.hasMoveTarget(entry))) {
            AgentBotMovementStuckStateRuntime.resetStuckProgress(entry);
            return;
        }

        Point agentPosition = AgentRuntimeIdentityRuntime.botPosition(entry);
        if (!AgentBotMovementStuckStateRuntime.hasStuckCheckPosition(entry)) {
            AgentBotMovementStuckStateRuntime.rememberStuckCheckPosition(entry, agentPosition);
            return;
        }

        boolean moved = AgentBotMovementStuckStateRuntime.movedSinceStuckCheck(entry, agentPosition, 8);
        if (moved) {
            AgentBotMovementStuckStateRuntime.resetStuckMs(entry);
            AgentBotMovementStuckStateRuntime.rememberStuckCheckPosition(entry, agentPosition);
        } else {
            AgentBotMovementStuckStateRuntime.addStuckMs(entry, hooks.movementTickMs());
        }

        if (hooks.enableUnstuck()
                && AgentBotMovementStuckStateRuntime.stuckForAtLeast(entry, 500)
                && !AgentBotMovementStuckStateRuntime.hasUnstuckCooldown(entry)) {
            AgentBotMovementStuckStateRuntime.resetStuckProgress(entry);
            hooks.unstuckAction().tick(entry);
        }
    }
}
