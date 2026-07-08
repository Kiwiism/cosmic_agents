package server.agents.capabilities.movement;

import client.Character;
import server.agents.capabilities.dialogue.AgentChatStatusOrchestrator;
import server.agents.runtime.AgentRuntimeEntry;

/**
 * Movement-facing facade for status side effects that still cross the
 * integration chat/status boundary.
 */
public final class AgentMovementStatusRuntime {
    private AgentMovementStatusRuntime() {
    }

    public static void prepareMovementActiveMode(AgentRuntimeEntry entry) {
        AgentChatStatusOrchestrator.prepareActiveModeEntry(entry);
    }

    public static void checkMovementStatus(AgentRuntimeEntry entry, Character bot) {
        AgentChatStatusOrchestrator.checkBotStatus(entry, bot);
    }

    public static int randomFidgetExpression() {
        return AgentChatStatusOrchestrator.randomFidgetExpression();
    }
}
