package server.agents.integration;

import client.Character;
import server.agents.capabilities.dialogue.AgentChatReportRuntime;
import server.bots.BotEntry;
import server.bots.BotOfferManager;

/**
 * Temporary Agent-owned bridge to legacy bot offer side effects.
 */
public final class AgentBotOfferRuntime {
    private AgentBotOfferRuntime() {
    }

    public static boolean isOwnerIdleForOffer(BotEntry entry) {
        return AgentBotChatStatusRuntime.isOwnerIdle(entry);
    }

    public static AgentChatReportRuntime.RecommendedGearActions recommendedGearActions(
            BotEntry entry,
            Character bot,
            Character owner) {
        return new AgentChatReportRuntime.RecommendedGearActions() {
            @Override
            public boolean hasOwner() {
                return owner != null;
            }

            @Override
            public boolean offerBestRecommendedGear() {
                return BotOfferManager.offerBestRecommendedGear(entry, bot, owner);
            }

            @Override
            public void queueReply(String line) {
                AgentBotReplyRuntime.queueReply(entry, line);
            }
        };
    }
}
