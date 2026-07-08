package server.agents.runtime;

import client.Character;
import server.agents.integration.AgentFidgetRuntime;
import server.agents.integration.AgentModeStateRuntime;
import server.agents.integration.AgentMoveTargetStateRuntime;
import server.agents.integration.AgentMovementStateRuntime;
import server.agents.integration.AgentMovementStuckStateRuntime;
import server.agents.integration.AgentNavigationDebugStateRuntime;
import server.agents.integration.AgentOwnerMotionStateRuntime;
import server.agents.integration.AgentShopStateRuntime;
import server.agents.integration.AgentTickStateRuntime;

import java.awt.Point;

/**
 * Agent-owned parked-follow fast path used to skip expensive movement work.
 */
public final class AgentFollowIdleMovementService {
    private static final long FOLLOW_IDLE_RECHECK_MS = 1000L;

    private AgentFollowIdleMovementService() {
    }

    public static boolean tryFollowIdleMovementFastPath(AgentRuntimeEntry entry,
                                                        Character agent,
                                                        Point targetPosition,
                                                        long nowMs,
                                                        int followDistance,
                                                        int stopDistance) {
        if (!isFollowIdleMovementFastPathEligible(entry, agent, targetPosition, followDistance, stopDistance)) {
            return false;
        }

        if (AgentTickStateRuntime.nextFollowIdleMovementCheckAtMs(entry) == 0L) {
            AgentTickStateRuntime.setNextFollowIdleMovementCheckAtMs(entry, nowMs + FOLLOW_IDLE_RECHECK_MS);
        } else if (nowMs >= AgentTickStateRuntime.nextFollowIdleMovementCheckAtMs(entry)) {
            AgentTickStateRuntime.setNextFollowIdleMovementCheckAtMs(entry, nowMs + FOLLOW_IDLE_RECHECK_MS);
            return false;
        }

        AgentNavigationDebugStateRuntime.setLastDecision(entry, "idle-fast");
        AgentMovementStuckStateRuntime.resetStuckProgress(entry);
        return true;
    }

    private static boolean isFollowIdleMovementFastPathEligible(AgentRuntimeEntry entry,
                                                                Character agent,
                                                                Point targetPosition,
                                                                int followDistance,
                                                                int stopDistance) {
        if (entry == null || agent == null || targetPosition == null) {
            return false;
        }
        if (!AgentModeStateRuntime.following(entry)
                || AgentModeStateRuntime.grinding(entry)
                || AgentMoveTargetStateRuntime.hasMoveTarget(entry)) {
            return false;
        }
        if (AgentMovementStateRuntime.inAir(entry)
                || AgentMovementStateRuntime.climbing(entry)
                || AgentMovementStateRuntime.downJumpPending(entry)
                || AgentNavigationDebugStateRuntime.graphWarmupFallback(entry)) {
            return false;
        }
        if (AgentNavigationDebugStateRuntime.hasActiveNavigationEdge(entry)
                || AgentNavigationDebugStateRuntime.navPreciseTarget(entry)
                || AgentFidgetRuntime.hasActiveFidgetMode(entry)) {
            return false;
        }
        if (AgentShopStateRuntime.hasActiveShopTransition(entry)) {
            return false;
        }
        if (AgentMovementStateRuntime.wasMovingX(entry)
                || AgentMovementStateRuntime.hasMoveDirection(entry)
                || AgentMovementStateRuntime.hasMovementVelocity(entry)) {
            return false;
        }
        if (AgentOwnerMotionStateRuntime.observedOwnerMoved(entry)) {
            return false;
        }

        Point agentPosition = agent.getPosition();
        return Math.abs(targetPosition.x - agentPosition.x) <= followDistance
                && Math.abs(targetPosition.y - agentPosition.y) <= stopDistance;
    }
}
