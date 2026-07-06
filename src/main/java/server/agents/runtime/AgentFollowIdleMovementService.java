package server.agents.runtime;

import client.Character;
import server.agents.integration.AgentBotFidgetRuntime;
import server.agents.integration.AgentBotModeStateRuntime;
import server.agents.integration.AgentBotMoveTargetStateRuntime;
import server.agents.integration.AgentBotMovementStateRuntime;
import server.agents.integration.AgentBotMovementStuckStateRuntime;
import server.agents.integration.AgentBotNavigationDebugStateRuntime;
import server.agents.integration.AgentBotOwnerMotionStateRuntime;
import server.agents.integration.AgentBotShopStateRuntime;
import server.agents.integration.AgentBotTickStateRuntime;

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

        if (AgentBotTickStateRuntime.nextFollowIdleMovementCheckAtMs(entry) == 0L) {
            AgentBotTickStateRuntime.setNextFollowIdleMovementCheckAtMs(entry, nowMs + FOLLOW_IDLE_RECHECK_MS);
        } else if (nowMs >= AgentBotTickStateRuntime.nextFollowIdleMovementCheckAtMs(entry)) {
            AgentBotTickStateRuntime.setNextFollowIdleMovementCheckAtMs(entry, nowMs + FOLLOW_IDLE_RECHECK_MS);
            return false;
        }

        AgentBotNavigationDebugStateRuntime.setLastDecision(entry, "idle-fast");
        AgentBotMovementStuckStateRuntime.resetStuckProgress(entry);
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
        if (!AgentBotModeStateRuntime.following(entry)
                || AgentBotModeStateRuntime.grinding(entry)
                || AgentBotMoveTargetStateRuntime.hasMoveTarget(entry)) {
            return false;
        }
        if (AgentBotMovementStateRuntime.inAir(entry)
                || AgentBotMovementStateRuntime.climbing(entry)
                || AgentBotMovementStateRuntime.downJumpPending(entry)
                || AgentBotNavigationDebugStateRuntime.graphWarmupFallback(entry)) {
            return false;
        }
        if (AgentBotNavigationDebugStateRuntime.hasActiveNavigationEdge(entry)
                || AgentBotNavigationDebugStateRuntime.navPreciseTarget(entry)
                || AgentBotFidgetRuntime.hasActiveFidgetMode(entry)) {
            return false;
        }
        if (AgentBotShopStateRuntime.hasActiveShopTransition(entry)) {
            return false;
        }
        if (AgentBotMovementStateRuntime.wasMovingX(entry)
                || AgentBotMovementStateRuntime.hasMoveDirection(entry)
                || AgentBotMovementStateRuntime.hasMovementVelocity(entry)) {
            return false;
        }
        if (AgentBotOwnerMotionStateRuntime.observedOwnerMoved(entry)) {
            return false;
        }

        Point agentPosition = agent.getPosition();
        return Math.abs(targetPosition.x - agentPosition.x) <= followDistance
                && Math.abs(targetPosition.y - agentPosition.y) <= stopDistance;
    }
}
