package server.agents.capabilities.movement.fidget;

import server.agents.capabilities.dialogue.AgentChatStatusOrchestrator;
import server.agents.runtime.AgentRuntimeEntry;

/** Connects fidget capability decisions to Agent dialogue and fidget state services. */
public final class AgentFidgetRuntime {
    private AgentFidgetRuntime() {
    }

    public static boolean isLeaderIdleForFidget(AgentRuntimeEntry entry) {
        return AgentChatStatusOrchestrator.isOwnerIdle(entry);
    }

    public static boolean hasActiveFidgetMode(AgentRuntimeEntry entry) {
        return AgentFidgetStateRuntime.active(entry);
    }
}
