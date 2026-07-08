package server.agents.capabilities.movement;

import client.Character;
import server.agents.integration.AgentChatStatusRuntime;
import server.agents.runtime.AgentRuntimeEntry;

/**
 * Movement-facing facade for status side effects that still cross the
 * integration chat/status boundary.
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
