package server.agents.capabilities.movement;

import server.agents.integration.AgentMovementTargetSideEffects;
import server.agents.runtime.AgentRuntimeEntry;

/**
 * Agent-owned target snapshot facade.
 */
public final class AgentMovementTargetRuntime {
    private AgentMovementTargetRuntime() {
    }

    public static AgentMovementTargetSnapshot snapshot(AgentRuntimeEntry entry) {
        return AgentMovementTargetSideEffects.captureTargetSnapshot(entry);
    }
}
