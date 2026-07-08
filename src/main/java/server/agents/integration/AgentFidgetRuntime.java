package server.agents.integration;

import server.agents.capabilities.movement.fidget.AgentFidgetStateRuntime;
import server.agents.runtime.AgentRuntimeEntry;

/**
 * Temporary Agent-owned bridge for fidget status decisions while fidget
 * movement execution still lives in the legacy bot runtime.
 */
public final class AgentFidgetRuntime {
    private AgentFidgetRuntime() {
    }

    public static boolean isLeaderIdleForFidget(AgentRuntimeEntry entry) {
        return AgentChatStatusRuntime.isOwnerIdle(entry);
    }

    public static boolean hasActiveFidgetMode(AgentRuntimeEntry entry) {
        return AgentFidgetStateRuntime.active(entry);
    }
}
