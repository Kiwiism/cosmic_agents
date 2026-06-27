package server.bots;

import server.agents.capabilities.dialogue.AgentChatBuildFlow;
import server.agents.capabilities.dialogue.AgentChatJobAdvancementFlow;
import server.agents.integration.AgentBotBuildRuntime;

final class BotChatBuildRuntime {
    private BotChatBuildRuntime() {
    }

    static AgentChatBuildFlow.SpVariantCallbacks spVariantCallbacks(BotEntry entry) {
        return AgentBotBuildRuntime.spVariantCallbacks(entry);
    }

    static AgentChatBuildFlow.ApBuildCallbacks apBuildCallbacks(BotEntry entry) {
        return AgentBotBuildRuntime.apBuildCallbacks(entry);
    }

    static AgentChatJobAdvancementFlow.JobAdvancementCallbacks jobAdvancementCallbacks(BotEntry entry) {
        return AgentBotBuildRuntime.jobAdvancementCallbacks(entry);
    }
}
