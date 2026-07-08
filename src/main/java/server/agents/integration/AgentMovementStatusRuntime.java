package server.agents.integration;

import client.Character;
import server.agents.runtime.AgentRuntimeEntry;

/**
 * Temporary Agent-owned bridge for movement-triggered status side effects.
 */
public final class AgentMovementStatusRuntime {
    private AgentMovementStatusRuntime() {
    }

    public static void prepareMovementActiveMode(AgentRuntimeEntry entry) {
        AgentChatStatusRuntime.prepareActiveModeEntry(entry);
    }

    public static void checkMovementStatus(AgentRuntimeEntry entry, Character bot) {
        AgentChatStatusRuntime.checkBotStatus(entry, bot);
    }

    public static int randomFidgetExpression() {
        return AgentChatStatusRuntime.randomFidgetExpression();
    }
}
