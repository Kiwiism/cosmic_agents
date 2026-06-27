package server.agents.integration;

import server.bots.BotEntry;

/**
 * Agent-owned adapter for temporary BotEntry-backed pending trade sequence state.
 */
public final class AgentBotPendingTradeStateRuntime {
    private AgentBotPendingTradeStateRuntime() {
    }

    public static boolean hasActiveSequence(BotEntry entry) {
        return entry.pendingTradeCategory() != null;
    }

    public static boolean isIdle(BotEntry entry) {
        return !hasActiveSequence(entry);
    }

    public static boolean hasQueuedRetry(BotEntry entry) {
        return entry.pendingBotTradeRetry() != null;
    }

    public static void queueRetry(BotEntry entry, Runnable retry, int delayMs) {
        if (hasQueuedRetry(entry)) {
            return;
        }
        entry.setPendingBotTradeRetry(retry);
        entry.setPendingBotTradeRetryMs(delayMs);
    }

    public static int retryDelayMs(BotEntry entry) {
        return entry.pendingBotTradeRetryMs();
    }

    public static void setRetryDelayMs(BotEntry entry, int delayMs) {
        entry.setPendingBotTradeRetryMs(delayMs);
    }

    public static Runnable takeRetry(BotEntry entry) {
        Runnable retry = entry.pendingBotTradeRetry();
        entry.setPendingBotTradeRetry(null);
        return retry;
    }

    public static int shareBudget(BotEntry entry) {
        return entry.pendingPotShareBudget();
    }

    public static void setShareBudget(BotEntry entry, int maxQuantity) {
        entry.setPendingPotShareBudget(maxQuantity);
    }

    public static void clearShareBudget(BotEntry entry) {
        entry.setPendingPotShareBudget(0);
    }

    public static short capShareQuantity(BotEntry entry, short availableQuantity) {
        int budget = shareBudget(entry);
        if (budget <= 0) {
            return availableQuantity;
        }
        short tradeQuantity = (short) Math.min(availableQuantity, budget);
        entry.setPendingPotShareBudget(budget - tradeQuantity);
        return tradeQuantity;
    }

    public static String categoryMessage(BotEntry entry) {
        return entry.pendingTradeCategoryMsg();
    }

    public static void setCategoryMessage(BotEntry entry, String message) {
        entry.setPendingTradeCategoryMsg(message);
    }

    public static void clearCategoryMessage(BotEntry entry) {
        entry.setPendingTradeCategoryMsg(null);
    }

    public static String takeCategoryMessage(BotEntry entry) {
        String message = categoryMessage(entry);
        clearCategoryMessage(entry);
        return message;
    }

    public static int recipientId(BotEntry entry) {
        return entry.pendingTradeRecipientId();
    }

    public static void setRecipientId(BotEntry entry, int recipientId) {
        entry.setPendingTradeRecipientId(recipientId);
    }

    public static void clearRecipientId(BotEntry entry) {
        entry.setPendingTradeRecipientId(0);
    }

    public static boolean inviteAnnounced(BotEntry entry) {
        return entry.pendingTradeInviteAnnounced();
    }

    public static void markInviteAnnounced(BotEntry entry) {
        entry.setPendingTradeInviteAnnounced(true);
    }

    public static void clearInviteAnnounced(BotEntry entry) {
        entry.setPendingTradeInviteAnnounced(false);
    }

    public static int timerMs(BotEntry entry) {
        return entry.pendingTradeTimerMs();
    }

    public static void setTimerMs(BotEntry entry, int timerMs) {
        entry.setPendingTradeTimerMs(timerMs);
    }

    public static void addTimerMs(BotEntry entry, int deltaMs) {
        entry.setPendingTradeTimerMs(entry.pendingTradeTimerMs() + deltaMs);
    }

    public static void tickTimerDown(BotEntry entry, java.util.function.IntUnaryOperator tickDown) {
        entry.setPendingTradeTimerMs(tickDown.applyAsInt(entry.pendingTradeTimerMs()));
    }

    public static void clearTimer(BotEntry entry) {
        entry.setPendingTradeTimerMs(0);
    }
}
