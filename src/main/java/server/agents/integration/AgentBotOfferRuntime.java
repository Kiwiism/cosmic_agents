package server.agents.integration;

import client.Character;
import server.agents.capabilities.dialogue.AgentChatReportRuntime;
import server.agents.commands.AgentReplyChannel;
import server.agents.runtime.AgentRuntimeEntry;
import server.bots.BotEntry;
import server.agents.capabilities.trade.AgentOfferService;

/**
 * Temporary Agent-owned bridge to legacy bot offer side effects.
 */
public final class AgentBotOfferRuntime {
    private AgentBotOfferRuntime() {
    }

    public static boolean isOwnerIdleForOffer(AgentRuntimeEntry entry) {
        return AgentBotChatStatusRuntime.isOwnerIdle(entry);
    }

    public static void replyNow(AgentRuntimeEntry entry, String message) {
        AgentBotReplyRuntime.replyNow(entry, message);
    }

    public static void queueSay(AgentRuntimeEntry entry, String message) {
        AgentBotReplyRuntime.queueSay(entry, message);
    }

    public static void sayMapNow(Character bot, String message) {
        AgentBotReplyRuntime.sayMapNow(bot, message);
    }

    public static void sayNow(Character bot, AgentReplyChannel channel, String message) {
        AgentBotReplyRuntime.sayNow(bot, channel, message);
    }

    public static long queueSayWithEstimatedDelay(AgentRuntimeEntry entry, String message) {
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

    public static boolean hasPendingGearPromptAfter(AgentRuntimeEntry entry, long nowMs) {
        return AgentBotOfferStateRuntime.hasPendingGearPromptAfter(entry, nowMs);
    }

    public static void reserveGearPrompt(AgentRuntimeEntry entry, long scheduledAt) {
        AgentBotOfferStateRuntime.reserveGearPrompt(entry, scheduledAt);
    }

    public static boolean isReservedGearPrompt(AgentRuntimeEntry entry, long scheduledAt) {
        return AgentBotOfferStateRuntime.isReservedGearPrompt(entry, scheduledAt);
    }

    public static void clearGearPrompt(AgentRuntimeEntry entry) {
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
                AgentBotReplyRuntime.queueReply(entry, line);
            }
        };
    }
}
