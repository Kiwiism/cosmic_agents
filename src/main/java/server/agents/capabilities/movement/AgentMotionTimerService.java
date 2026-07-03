package server.agents.capabilities.movement;

import server.bots.BotEntry;
import server.bots.BotPhysicsEngine;

/**
 * Agent-owned seam for movement physics timer countdowns.
 */
public final class AgentMotionTimerService {
    private AgentMotionTimerService() {
    }

    public static void tickMotionTimers(BotEntry entry) {
        BotPhysicsEngine.tickMotionTimers(entry);
    }
}
