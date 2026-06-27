package server.bots;

import server.agents.capabilities.dialogue.AgentChatBuffQueryFlow;
import server.agents.capabilities.dialogue.AgentChatRespecFlow;
import server.agents.capabilities.dialogue.AgentChatToggleFlow;
import server.agents.integration.AgentBotControlRuntime;

final class BotChatControlRuntime {
    private BotChatControlRuntime() {
    }

    static AgentChatToggleFlow.ToggleCallbacks toggleCallbacks(BotEntry entry) {
        return AgentBotControlRuntime.toggleCallbacks(entry);
    }

    static AgentChatBuffQueryFlow.BuffQueryCallbacks buffQueryCallbacks(BotEntry entry) {
        return AgentBotControlRuntime.buffQueryCallbacks(entry);
    }

    static AgentChatRespecFlow.RespecCallbacks respecCallbacks(BotEntry entry) {
        return AgentBotControlRuntime.respecCallbacks(entry);
    }
}
