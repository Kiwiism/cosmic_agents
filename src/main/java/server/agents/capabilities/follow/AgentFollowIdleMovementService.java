package server.agents.capabilities.follow;

import client.Character;
import server.agents.capabilities.movement.AgentMoveTargetStateRuntime;
import server.agents.capabilities.movement.AgentMovementStateRuntime;
import server.agents.capabilities.movement.AgentMovementStuckStateRuntime;
import server.agents.capabilities.movement.AgentMovementPhysicsConfig;
import server.agents.capabilities.movement.fidget.AgentFidgetRuntime;
import server.agents.capabilities.navigation.AgentNavigationDebugStateRuntime;
import server.agents.capabilities.shop.AgentShopStateRuntime;
import server.agents.runtime.AgentModeStateRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentTickStateRuntime;

import java.awt.Point;

/**
 * Agent-owned parked-follow fast path used to skip expensive movement work.
 */
public final class AgentFollowIdleMovementService {
    private static final long FOLLOW_IDLE_RECHECK_MS = config.AgentTuning.longValue("server.agents.capabilities.follow.AgentFollowIdleMovementService.FOLLOW_IDLE_RECHECK_MS");

    private AgentFollowIdleMovementService() {
    }

    public static boolean tryFollowIdleMovementFastPath(AgentRuntimeEntry entry,
                                                        Character agent,
                                                        Point targetPosition,
                                                        long nowMs) {
        return tryFollowIdleMovementFastPath(
                entry,
                agent,
                targetPosition,
                nowMs,
                AgentMovementPhysicsConfig.configuredFollowDist(),
                AgentMovementPhysicsConfig.configuredStopDist());
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
