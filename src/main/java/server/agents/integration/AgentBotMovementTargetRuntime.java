package server.agents.integration;

import server.agents.capabilities.movement.AgentMovementTargetSnapshot;
import server.bots.BotEntry;

/**
 * Agent-owned target snapshot facade.
 */
public final class AgentBotMovementTargetRuntime {
    private AgentBotMovementTargetRuntime() {
    }

    public static AgentMovementTargetSnapshot snapshot(BotEntry entry) {
        return AgentBotMovementTargetSideEffects.captureTargetSnapshot(entry);
    }
}
