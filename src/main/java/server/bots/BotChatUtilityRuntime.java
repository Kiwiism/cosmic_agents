package server.bots;

import server.agents.capabilities.dialogue.AgentChatUtilityFlow;
import server.agents.integration.AgentBotUtilityRuntime;

final class BotChatUtilityRuntime {
    private BotChatUtilityRuntime() {
    }

    static AgentChatUtilityFlow.UtilityCallbacks utilityCallbacks(BotEntry entry) {
        return AgentBotUtilityRuntime.utilityCallbacks(entry);
    }
}
