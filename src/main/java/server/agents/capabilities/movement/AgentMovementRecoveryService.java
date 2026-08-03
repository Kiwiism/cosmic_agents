package server.agents.capabilities.movement;

import client.Character;
import server.agents.capabilities.navigation.AgentNavigationDebugStateRuntime;
import server.agents.capabilities.navigation.AgentNavigationTraceRuntime;
import server.agents.capabilities.recovery.AgentNavigationRecoveryRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.diagnostics.AgentRunObservationRuntime;
import server.agents.events.AgentEventPriority;
import server.agents.operations.events.AgentOperationalEventPublisher;
import server.agents.operations.events.AgentRecoveryPerformedEvent;

import java.awt.Point;
import java.util.concurrent.ThreadLocalRandom;

public final class AgentMovementRecoveryService {
    private static final int UNSTUCK_COOLDOWN_MS = config.AgentTuning.intValue(
            "server.agents.capabilities.movement.AgentMovementRecoveryService.UNSTUCK_COOLDOWN_MS");
    private AgentMovementRecoveryService() {
    }

    /**
     * Fires a random recovery action when the agent has been stuck in the same spot.
     * Clears the nav edge so A* replans on the next AI tick.
     */
    public static void tickUnstuck(AgentRuntimeEntry entry) {
        long nowMs = System.currentTimeMillis();
        if (!AgentNavigationRecoveryRuntime.tryAcquire(entry, "movement-unstuck", nowMs)) {
            return;
        }
        Character agent = AgentRuntimeIdentityRuntime.bot(entry);
        Point from = new Point(agent.getPosition());
        Point currentWaypoint = AgentNavigationDebugStateRuntime.navTargetPosition(entry);
        Point plannedTarget = AgentNavigationDebugStateRuntime.plannedNavigationTargetPosition(entry);
        if (AgentMovementStateRuntime.inAir(entry) || AgentMovementStateRuntime.climbing(entry)) {
            AgentAirborneLaunchService.launchAirborne(entry, agent.getPosition(), 0f, 0, false);
        } else {
            int walkStep = AgentMovementKinematicsService.walkStep(
                    agent.getMap(), AgentMovementStateRuntime.movementProfile(entry));
            int recoveryDirection = recoveryDirection(from, currentWaypoint, plannedTarget);
            AgentRopeMovementService.beginGroundJump(entry, agent, recoveryDirection * walkStep);
        }
        AgentMovementStateResetService.clearNavigationStep(entry);
        AgentMovementStuckStateRuntime.setUnstuckCooldownMs(
                entry,
                AgentMovementTimers.delayAfterCurrentTick(UNSTUCK_COOLDOWN_MS));
        AgentMovementBroadcastService.broadcastMovement(entry);
        AgentRunObservationRuntime.recovery(entry, agent, "movement-unstuck", nowMs);
        AgentNavigationTraceRuntime.recovered(entry, "movement-unstuck", nowMs);
        publishRecovery(entry, agent, "movement-unstuck", from, agent.getPosition());
    }

    static int recoveryDirection(Point position, Point currentWaypoint, Point plannedTarget) {
        Point target = currentWaypoint != null ? currentWaypoint : plannedTarget;
        if (target != null && target.x != position.x) {
            return target.x > position.x ? -1 : 1;
        }
        return ThreadLocalRandom.current().nextBoolean() ? -1 : 1;
    }

    /**
     * Clears stale navigation and lets an airborne or climbing Agent fall naturally before replanning.
     * This intentionally does not move or teleport a grounded Agent.
     */
    public static void nudgeForObjectiveReplan(AgentRuntimeEntry entry) {
        long nowMs = System.currentTimeMillis();
        if (!AgentNavigationRecoveryRuntime.tryAcquire(entry, "objective-navigation-nudge", nowMs)) {
            return;
        }
        Character agent = AgentRuntimeIdentityRuntime.bot(entry);
        Point from = new Point(agent.getPosition());
        if (AgentMovementStateRuntime.inAir(entry) || AgentMovementStateRuntime.climbing(entry)) {
            AgentAirborneLaunchService.launchAirborne(entry, agent.getPosition(), 0f, 0, false);
        }
        AgentMovementStateResetService.clearNavigationStep(entry);
        AgentRunObservationRuntime.recovery(
                entry, agent, "objective-navigation-nudge", nowMs);
        AgentNavigationTraceRuntime.recovered(
                entry, "objective-navigation-nudge", nowMs);
        publishRecovery(entry, agent, "objective-navigation-nudge", from, agent.getPosition());
    }

    private static void publishRecovery(AgentRuntimeEntry entry,
                                        Character agent,
                                        String recoveryType,
                                        Point from,
                                        Point to) {
        AgentOperationalEventPublisher.publish(entry,
                objectiveId -> new AgentRecoveryPerformedEvent(
                        agent.getId(), System.currentTimeMillis(), agent.getMapId(), recoveryType,
                        from.x, from.y, to.x, to.y, objectiveId),
                AgentEventPriority.IMPORTANT);
    }
}
