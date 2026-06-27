package server.agents.integration;

import client.Character;
import server.bots.BotEntry;

/**
 * Temporary Agent-owned bridge for movement-triggered status side effects.
 */
public final class AgentBotMovementStatusRuntime {
    private AgentBotMovementStatusRuntime() {
    }

    public static void prepareMovementActiveMode(BotEntry entry) {
        AgentBotChatStatusRuntime.prepareActiveModeEntry(entry);
    }

    public static void checkMovementStatus(BotEntry entry, Character bot) {
        AgentBotChatStatusRuntime.checkBotStatus(entry, bot);
    }

    public static int randomFidgetExpression() {
        return AgentBotChatStatusRuntime.randomFidgetExpression();
    }
}
