package server.agents.integration;

import server.agents.capabilities.movement.AgentMovementTargetSnapshot;
import server.agents.runtime.AgentRuntimeEntry;

/**
 * Agent-owned target snapshot facade.
 */
public final class AgentBotMovementTargetRuntime {
    private AgentBotMovementTargetRuntime() {
    }

    public static AgentMovementTargetSnapshot snapshot(AgentRuntimeEntry entry) {
        return AgentBotMovementTargetSideEffects.captureTargetSnapshot(entry);
    }
}
