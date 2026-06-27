package server.agents.integration;

import client.inventory.Item;
import server.bots.BotEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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

    public static String category(BotEntry entry) {
        return entry.pendingTradeCategory();
    }

    public static void setCategory(BotEntry entry, String category) {
        entry.setPendingTradeCategory(category);
    }

    public static void clearCategory(BotEntry entry) {
        entry.setPendingTradeCategory(null);
    }

    public static boolean isCategory(BotEntry entry, String category) {
        return category.equals(entry.pendingTradeCategory());
    }

    public static boolean isSupplyShareCategory(BotEntry entry) {
        return isCategory(entry, "pot_share") || isCategory(entry, "ammo_share");
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

    public static boolean singleBatch(BotEntry entry) {
        return entry.pendingTradeSingleBatch();
    }

    public static void setSingleBatch(BotEntry entry, boolean singleBatch) {
        entry.setPendingTradeSingleBatch(singleBatch);
    }

    public static void clearSingleBatch(BotEntry entry) {
        entry.setPendingTradeSingleBatch(false);
    }

    public static int meso(BotEntry entry) {
        return entry.pendingTradeMeso();
    }

    public static void setMeso(BotEntry entry, int meso) {
        entry.setPendingTradeMeso(meso);
    }

    public static void clearMeso(BotEntry entry) {
        entry.setPendingTradeMeso(0);
    }

    public static boolean mesoAdded(BotEntry entry) {
        return entry.pendingTradeMesoAdded();
    }

    public static boolean hasMesoToAdd(BotEntry entry) {
        return !mesoAdded(entry) && meso(entry) > 0;
    }

    public static void markMesoAdded(BotEntry entry) {
        entry.setPendingTradeMesoAdded(true);
    }

    public static void clearMesoAdded(BotEntry entry) {
        entry.setPendingTradeMesoAdded(false);
    }

    public static boolean allItemsAdded(BotEntry entry) {
        return entry.pendingTradeAllAdded();
    }

    public static void markAllItemsAdded(BotEntry entry) {
        entry.setPendingTradeAllAdded(true);
    }

    public static void clearAllItemsAdded(BotEntry entry) {
        entry.setPendingTradeAllAdded(false);
    }

    public static boolean botDone(BotEntry entry) {
        return entry.pendingTradeBotDone();
    }

    public static void markBotDone(BotEntry entry) {
        entry.setPendingTradeBotDone(true);
    }

    public static void clearBotDone(BotEntry entry) {
        entry.setPendingTradeBotDone(false);
    }

    public static int itemIndex(BotEntry entry) {
        return entry.pendingTradeIdx();
    }

    public static void incrementItemIndex(BotEntry entry) {
        entry.setPendingTradeIdx(entry.pendingTradeIdx() + 1);
    }

    public static void clearItemIndex(BotEntry entry) {
        entry.setPendingTradeIdx(0);
    }

    public static List<Item> items(BotEntry entry) {
        return entry.pendingTradeItems();
    }

    public static void setItems(BotEntry entry, List<Item> items) {
        entry.setPendingTradeItems(items);
    }

    public static boolean isBetweenBatches(BotEntry entry) {
        return entry.pendingTradeItems() == null;
    }

    public static void clearItems(BotEntry entry) {
        entry.setPendingTradeItems(null);
    }

    public static boolean hasRestoreSlots(BotEntry entry) {
        return !entry.pendingTradeRestoreSlots().isEmpty();
    }

    public static void rememberRestoreSlot(BotEntry entry, Item item, short slot) {
        entry.pendingTradeRestoreSlots().put(item, slot);
    }

    public static void transferRestoreSlot(BotEntry entry, Item fromItem, Item toItem) {
        Short restoreSlot = entry.pendingTradeRestoreSlots().remove(fromItem);
        if (restoreSlot != null) {
            entry.pendingTradeRestoreSlots().put(toItem, restoreSlot);
        }
    }

    public static List<Map.Entry<Item, Short>> restoreSlotEntries(BotEntry entry) {
        return new ArrayList<>(entry.pendingTradeRestoreSlots().entrySet());
    }

    public static void clearRestoreSlots(BotEntry entry) {
        entry.pendingTradeRestoreSlots().clear();
    }
}
