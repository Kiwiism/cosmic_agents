package server.agents.capabilities.movement;

import client.Character;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.integration.AgentBotMovementStateRuntime;
import server.agents.integration.AgentBotMovementStuckStateRuntime;
import server.agents.integration.AgentBotRuntimeIdentityRuntime;
import server.bots.BotEntry;

import java.util.concurrent.ThreadLocalRandom;

public final class AgentMovementRecoveryService {
    private AgentMovementRecoveryService() {
    }

    /**
     * Fires a random recovery action when the agent has been stuck in the same spot.
     * Clears the nav edge so A* replans on the next AI tick.
     */
    public static void tickUnstuck(AgentRuntimeEntry entry) {
        tickUnstuck(asBotEntry(entry));
    }

    public static void tickUnstuck(BotEntry entry) {
        Character agent = AgentBotRuntimeIdentityRuntime.bot(entry);
        int walkStep = AgentMovementKinematicsService.walkStep(agent.getMap(), AgentBotMovementStateRuntime.movementProfile(entry));
        switch (ThreadLocalRandom.current().nextInt(2)) {
            case 0 -> AgentRopeMovementService.beginGroundJump(entry, agent, -walkStep);
            default -> AgentRopeMovementService.beginGroundJump(entry, agent, walkStep);
        }
        AgentMovementStateResetService.clearNavigationState(entry);
        AgentBotMovementStuckStateRuntime.setUnstuckCooldownMs(entry, AgentMovementTimers.delayAfterCurrentTick(5000));
        AgentMovementBroadcastService.broadcastMovement(entry);
    }

    private static BotEntry asBotEntry(AgentRuntimeEntry entry) {
        return (BotEntry) entry;
    }
}
