package server.agents.integration;

import client.Character;
import server.agents.capabilities.dialogue.AgentChatReportRuntime;
import server.agents.commands.AgentReplyChannel;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.capabilities.trade.AgentOfferService;

/**
 * Temporary Agent-owned bridge to legacy bot offer side effects.
 */
public final class AgentBotOfferRuntime {
    private AgentBotOfferRuntime() {
    }

    public static boolean isOwnerIdleForOffer(AgentRuntimeEntry entry) {
        return AgentChatStatusRuntime.isOwnerIdle(entry);
    }

    public static void replyNow(AgentRuntimeEntry entry, String message) {
        AgentReplyRuntime.replyNow(entry, message);
    }

    public static void queueSay(AgentRuntimeEntry entry, String message) {
        AgentReplyRuntime.queueSay(entry, message);
    }

    public static void sayMapNow(Character bot, String message) {
        AgentReplyRuntime.sayMapNow(bot, message);
    }

    public static void sayNow(Character bot, AgentReplyChannel channel, String message) {
        AgentReplyRuntime.sayNow(bot, channel, message);
    }

    public static long queueSayWithEstimatedDelay(AgentRuntimeEntry entry, String message) {
        return AgentReplyRuntime.queueSayWithEstimatedDelay(entry, message);
    }

    public static void afterDelay(long delayMs, Runnable action) {
        AgentSchedulerRuntime.afterDelay(delayMs, action);
    }

    public static void afterRandomDelay(int minMs, int maxMs, Runnable action) {
        AgentSchedulerRuntime.afterRandomDelay(minMs, maxMs, action);
    }

    public static long randomDelayMs(int minMs, int maxMs) {
        return AgentSchedulerRuntime.randomDelayMs(minMs, maxMs);
    }

    public static boolean hasPendingGearPromptAfter(AgentRuntimeEntry entry, long nowMs) {
        return AgentOfferStateRuntime.hasPendingGearPromptAfter(entry, nowMs);
    }

    public static void reserveGearPrompt(AgentRuntimeEntry entry, long scheduledAt) {
        AgentOfferStateRuntime.reserveGearPrompt(entry, scheduledAt);
    }

    public static boolean isReservedGearPrompt(AgentRuntimeEntry entry, long scheduledAt) {
        return AgentOfferStateRuntime.isReservedGearPrompt(entry, scheduledAt);
    }

    public static void clearGearPrompt(AgentRuntimeEntry entry) {
        AgentOfferStateRuntime.clearGearPrompt(entry);
    }

    public static AgentChatReportRuntime.RecommendedGearActions recommendedGearActions(
            AgentRuntimeEntry entry,
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
                AgentReplyRuntime.queueReply(entry, line);
            }
        };
    }
}
