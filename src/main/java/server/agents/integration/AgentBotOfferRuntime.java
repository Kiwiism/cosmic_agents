package server.agents.integration;

import client.Character;
import server.agents.capabilities.dialogue.AgentChatReportRuntime;
import server.agents.commands.AgentReplyChannel;
import server.bots.BotEntry;
import server.agents.capabilities.trade.AgentOfferService;

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
        AgentBotOfferReplyRuntime.replyNow(entry, message);
    }

    public static void queueSay(BotEntry entry, String message) {
        AgentBotOfferReplyRuntime.queueSay(entry, message);
    }

    public static void sayMapNow(Character bot, String message) {
        AgentBotOfferReplyRuntime.sayMapNow(bot, message);
    }

    public static void sayNow(Character bot, AgentReplyChannel channel, String message) {
        AgentBotOfferReplyRuntime.sayNow(bot, channel, message);
    }

    public static long queueSayWithEstimatedDelay(BotEntry entry, String message) {
        return AgentBotOfferReplyRuntime.queueSayWithEstimatedDelay(entry, message);
    }

    public static void afterDelay(long delayMs, Runnable action) {
        AgentBotOfferSchedulerRuntime.afterDelay(delayMs, action);
    }

    public static void afterRandomDelay(int minMs, int maxMs, Runnable action) {
        AgentBotOfferSchedulerRuntime.afterRandomDelay(minMs, maxMs, action);
    }

    public static long randomDelayMs(int minMs, int maxMs) {
        return AgentBotOfferSchedulerRuntime.randomDelayMs(minMs, maxMs);
    }

    public static boolean hasPendingGearPromptAfter(BotEntry entry, long nowMs) {
        return AgentBotOfferStateRuntime.hasPendingGearPromptAfter(entry, nowMs);
    }

    public static void reserveGearPrompt(BotEntry entry, long scheduledAt) {
        AgentBotOfferStateRuntime.reserveGearPrompt(entry, scheduledAt);
    }

    public static boolean isReservedGearPrompt(BotEntry entry, long scheduledAt) {
        return AgentBotOfferStateRuntime.isReservedGearPrompt(entry, scheduledAt);
    }

    public static void clearGearPrompt(BotEntry entry) {
        AgentBotOfferStateRuntime.clearGearPrompt(entry);
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
                return AgentOfferService.offerBestRecommendedGear(entry, bot, owner);
            }

            @Override
            public void queueReply(String line) {
                AgentBotOfferReplyRuntime.queueReply(entry, line);
            }
        };
    }
}
