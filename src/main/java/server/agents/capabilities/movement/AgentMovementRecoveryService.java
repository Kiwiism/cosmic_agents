package server.agents.capabilities.movement;

import client.Character;
import server.agents.integration.AgentBotMovementStateRuntime;
import server.agents.integration.AgentBotMovementStuckStateRuntime;
import server.agents.integration.AgentBotRuntimeIdentityRuntime;
import server.bots.BotEntry;
import server.bots.BotPhysicsEngine;

import java.util.concurrent.ThreadLocalRandom;

public final class AgentMovementRecoveryService {
    private AgentMovementRecoveryService() {
    }

    /**
     * Fires a random recovery action when the agent has been stuck in the same spot.
     * Clears the nav edge so A* replans on the next AI tick.
     */
    public static void tickUnstuck(BotEntry entry) {
        Character agent = AgentBotRuntimeIdentityRuntime.bot(entry);
        int walkStep = AgentMovementKinematicsService.walkStep(agent.getMap(), AgentBotMovementStateRuntime.movementProfile(entry));
        switch (ThreadLocalRandom.current().nextInt(2)) {
            case 0 -> BotPhysicsEngine.beginGroundJump(entry, agent, -walkStep);
            default -> BotPhysicsEngine.beginGroundJump(entry, agent, walkStep);
        }
        AgentMovementStateResetService.clearNavigationState(entry);
        AgentBotMovementStuckStateRuntime.setUnstuckCooldownMs(entry, AgentMovementTimers.delayAfterCurrentTick(5000));
        AgentMovementBroadcastService.broadcastMovement(entry);
    }
}
