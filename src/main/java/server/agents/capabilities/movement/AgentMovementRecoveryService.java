package server.agents.capabilities.movement;

import client.Character;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.capabilities.movement.AgentMovementStateRuntime;
import server.agents.capabilities.movement.AgentMovementStuckStateRuntime;
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
        Character agent = AgentRuntimeIdentityRuntime.bot(entry);
        Point from = new Point(agent.getPosition());
        if (AgentMovementStateRuntime.inAir(entry) || AgentMovementStateRuntime.climbing(entry)) {
            AgentAirborneLaunchService.launchAirborne(entry, agent.getPosition(), 0f, 0, false);
        } else {
            int walkStep = AgentMovementKinematicsService.walkStep(
                    agent.getMap(), AgentMovementStateRuntime.movementProfile(entry));
            switch (ThreadLocalRandom.current().nextInt(2)) {
                case 0 -> AgentRopeMovementService.beginGroundJump(entry, agent, -walkStep);
                default -> AgentRopeMovementService.beginGroundJump(entry, agent, walkStep);
            }
        }
        AgentMovementStateResetService.clearNavigationState(entry);
        AgentMovementStuckStateRuntime.setUnstuckCooldownMs(
                entry,
                AgentMovementTimers.delayAfterCurrentTick(UNSTUCK_COOLDOWN_MS));
        AgentMovementBroadcastService.broadcastMovement(entry);
        AgentRunObservationRuntime.recovery(entry, agent, "movement-unstuck", System.currentTimeMillis());
        publishRecovery(entry, agent, "movement-unstuck", from, agent.getPosition());
    }

    /**
     * Clears stale navigation and lets an airborne or climbing Agent fall naturally before replanning.
     * This intentionally does not move or teleport a grounded Agent.
     */
    public static void nudgeForObjectiveReplan(AgentRuntimeEntry entry) {
        Character agent = AgentRuntimeIdentityRuntime.bot(entry);
        Point from = new Point(agent.getPosition());
        if (AgentMovementStateRuntime.inAir(entry) || AgentMovementStateRuntime.climbing(entry)) {
            AgentAirborneLaunchService.launchAirborne(entry, agent.getPosition(), 0f, 0, false);
        }
        AgentMovementStateResetService.clearNavigationState(entry);
        AgentRunObservationRuntime.recovery(
                entry, agent, "objective-navigation-nudge", System.currentTimeMillis());
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
