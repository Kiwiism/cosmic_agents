package server.bots;

import server.agents.capabilities.dialogue.AgentChatRuntime;
import server.agents.integration.AgentBotChatOrchestratorContext;

public class BotChatManager {
    public static boolean wasLastChatHandled() {
        return AgentChatRuntime.wasLastChatHandled();
    }

    static void handleChat(BotEntry entry, String message) {
        AgentChatRuntime.handleChat(message, new AgentBotChatOrchestratorContext(entry));
    }

}
