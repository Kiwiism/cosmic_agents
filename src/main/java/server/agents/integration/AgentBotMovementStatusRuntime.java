package server.agents.integration;

import client.Character;
import server.agents.runtime.AgentRuntimeEntry;

/**
 * Temporary Agent-owned bridge for movement-triggered status side effects.
 */
public final class AgentBotMovementStatusRuntime {
    private AgentBotMovementStatusRuntime() {
    }

    public static void prepareMovementActiveMode(AgentRuntimeEntry entry) {
        AgentBotChatStatusRuntime.prepareActiveModeEntry(entry);
    }

    public static void checkMovementStatus(AgentRuntimeEntry entry, Character bot) {
        AgentBotChatStatusRuntime.checkBotStatus(entry, bot);
    }

    public static int randomFidgetExpression() {
        return AgentBotChatStatusRuntime.randomFidgetExpression();
    }
}
