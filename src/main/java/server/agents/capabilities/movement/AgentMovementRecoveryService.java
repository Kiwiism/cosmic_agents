package server.agents.capabilities.movement;

import client.Character;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.integration.AgentMovementStateRuntime;
import server.agents.capabilities.movement.AgentMovementStuckStateRuntime;
import server.agents.integration.AgentRuntimeIdentityRuntime;

import java.util.concurrent.ThreadLocalRandom;

public final class AgentMovementRecoveryService {
    private AgentMovementRecoveryService() {
    }

    /**
     * Fires a random recovery action when the agent has been stuck in the same spot.
     * Clears the nav edge so A* replans on the next AI tick.
     */
    public static void tickUnstuck(AgentRuntimeEntry entry) {
        Character agent = AgentRuntimeIdentityRuntime.bot(entry);
        int walkStep = AgentMovementKinematicsService.walkStep(agent.getMap(), AgentMovementStateRuntime.movementProfile(entry));
        switch (ThreadLocalRandom.current().nextInt(2)) {
            case 0 -> AgentRopeMovementService.beginGroundJump(entry, agent, -walkStep);
            default -> AgentRopeMovementService.beginGroundJump(entry, agent, walkStep);
        }
        AgentMovementStateResetService.clearNavigationState(entry);
        AgentMovementStuckStateRuntime.setUnstuckCooldownMs(entry, AgentMovementTimers.delayAfterCurrentTick(5000));
        AgentMovementBroadcastService.broadcastMovement(entry);
    }
}
