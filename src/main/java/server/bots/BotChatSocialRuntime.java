package server.bots;

import server.agents.capabilities.dialogue.AgentChatSocialFlow;
import server.agents.integration.AgentBotSocialRuntime;

final class BotChatSocialRuntime {
    private BotChatSocialRuntime() {
    }

    static AgentChatSocialFlow.SocialCallbacks socialCallbacks(BotEntry entry) {
        return AgentBotSocialRuntime.socialCallbacks(entry);
    }
}
