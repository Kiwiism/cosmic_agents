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

    public static void replyNow(BotEntry entry, String message) {
        AgentBotReplyRuntime.replyNow(entry, message);
    }

    public static void queueSay(BotEntry entry, String message) {
        AgentBotReplyRuntime.queueSay(entry, message);
    }

    public static long queueSayWithEstimatedDelay(BotEntry entry, String message) {
        return AgentBotReplyRuntime.queueSayWithEstimatedDelay(entry, message);
    }

    public static void afterDelay(long delayMs, Runnable action) {
        AgentBotSchedulerRuntime.afterDelay(delayMs, action);
    }

    public static void afterRandomDelay(int minMs, int maxMs, Runnable action) {
        AgentBotSchedulerRuntime.afterRandomDelay(minMs, maxMs, action);
    }

    public static long randomDelayMs(int minMs, int maxMs) {
        return AgentBotSchedulerRuntime.randomDelayMs(minMs, maxMs);
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
