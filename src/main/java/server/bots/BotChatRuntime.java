package server.bots;

import server.agents.integration.AgentBotChatOrchestratorContext;
import server.agents.capabilities.dialogue.AgentChatRuntime;

/**
 * Temporary bot-side chat runtime bridge while BotManager still invokes chat
 * handling from the legacy bot package.
 */
final class BotChatRuntime {
    private BotChatRuntime() {
    }

    static boolean wasLastChatHandled() {
        return AgentChatRuntime.wasLastChatHandled();
    }

    static void handleChat(BotEntry entry, String message) {
        AgentChatRuntime.handleChat(message, new AgentBotChatOrchestratorContext(entry));
    }
}
