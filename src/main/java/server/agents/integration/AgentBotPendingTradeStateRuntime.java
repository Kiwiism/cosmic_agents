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
        return entry.pendingTradeSequenceState().category() != null;
    }

    public static boolean isIdle(BotEntry entry) {
        return !hasActiveSequence(entry);
    }

    public static String category(BotEntry entry) {
        return entry.pendingTradeSequenceState().category();
    }

    public static void setCategory(BotEntry entry, String category) {
        entry.pendingTradeSequenceState().setCategory(category);
    }

    public static void clearCategory(BotEntry entry) {
        entry.pendingTradeSequenceState().setCategory(null);
    }

    public static boolean isCategory(BotEntry entry, String category) {
        return category.equals(entry.pendingTradeSequenceState().category());
    }

    public static boolean isSupplyShareCategory(BotEntry entry) {
        return isCategory(entry, "pot_share") || isCategory(entry, "ammo_share");
    }

    public static boolean hasQueuedRetry(BotEntry entry) {
        return entry.tradeRetryState().hasRetry();
    }

    public static void queueRetry(BotEntry entry, Runnable retry, int delayMs) {
        entry.tradeRetryState().queueRetry(retry, delayMs);
    }

    public static int retryDelayMs(BotEntry entry) {
        return entry.tradeRetryState().delayMs();
    }

    public static void setRetryDelayMs(BotEntry entry, int delayMs) {
        entry.tradeRetryState().setDelayMs(delayMs);
    }

    public static Runnable takeRetry(BotEntry entry) {
        return entry.tradeRetryState().takeRetry();
    }

    public static int shareBudget(BotEntry entry) {
        return entry.pendingTradeSequenceState().shareBudget();
    }

    public static void setShareBudget(BotEntry entry, int maxQuantity) {
        entry.pendingTradeSequenceState().setShareBudget(maxQuantity);
    }

    public static void clearShareBudget(BotEntry entry) {
        entry.pendingTradeSequenceState().setShareBudget(0);
    }

    public static short capShareQuantity(BotEntry entry, short availableQuantity) {
        int budget = shareBudget(entry);
        if (budget <= 0) {
            return availableQuantity;
        }
        short tradeQuantity = (short) Math.min(availableQuantity, budget);
        entry.pendingTradeSequenceState().setShareBudget(budget - tradeQuantity);
        return tradeQuantity;
    }

    public static String categoryMessage(BotEntry entry) {
        return entry.pendingTradeSequenceState().categoryMessage();
    }

    public static void setCategoryMessage(BotEntry entry, String message) {
        entry.pendingTradeSequenceState().setCategoryMessage(message);
    }

    public static void clearCategoryMessage(BotEntry entry) {
        entry.pendingTradeSequenceState().setCategoryMessage(null);
    }

    public static String takeCategoryMessage(BotEntry entry) {
        String message = categoryMessage(entry);
        clearCategoryMessage(entry);
        return message;
    }

    public static int recipientId(BotEntry entry) {
        return entry.pendingTradeSequenceState().recipientId();
    }

    public static void setRecipientId(BotEntry entry, int recipientId) {
        entry.pendingTradeSequenceState().setRecipientId(recipientId);
    }

    public static void clearRecipientId(BotEntry entry) {
        entry.pendingTradeSequenceState().setRecipientId(0);
    }

    public static boolean inviteAnnounced(BotEntry entry) {
        return entry.pendingTradeSequenceState().inviteAnnounced();
    }

    public static void markInviteAnnounced(BotEntry entry) {
        entry.pendingTradeSequenceState().setInviteAnnounced(true);
    }

    public static void clearInviteAnnounced(BotEntry entry) {
        entry.pendingTradeSequenceState().setInviteAnnounced(false);
    }

    public static int timerMs(BotEntry entry) {
        return entry.pendingTradeSequenceState().timerMs();
    }

    public static void setTimerMs(BotEntry entry, int timerMs) {
        entry.pendingTradeSequenceState().setTimerMs(timerMs);
    }

    public static void addTimerMs(BotEntry entry, int deltaMs) {
        entry.pendingTradeSequenceState().setTimerMs(entry.pendingTradeSequenceState().timerMs() + deltaMs);
    }

    public static void tickTimerDown(BotEntry entry, java.util.function.IntUnaryOperator tickDown) {
        entry.pendingTradeSequenceState().setTimerMs(tickDown.applyAsInt(entry.pendingTradeSequenceState().timerMs()));
    }

    public static void clearTimer(BotEntry entry) {
        entry.pendingTradeSequenceState().setTimerMs(0);
    }

    public static boolean singleBatch(BotEntry entry) {
        return entry.pendingTradeSequenceState().singleBatch();
    }

    public static void setSingleBatch(BotEntry entry, boolean singleBatch) {
        entry.pendingTradeSequenceState().setSingleBatch(singleBatch);
    }

    public static void clearSingleBatch(BotEntry entry) {
        entry.pendingTradeSequenceState().setSingleBatch(false);
    }

    public static int meso(BotEntry entry) {
        return entry.pendingTradeSequenceState().meso();
    }

    public static void setMeso(BotEntry entry, int meso) {
        entry.pendingTradeSequenceState().setMeso(meso);
    }

    public static void clearMeso(BotEntry entry) {
        entry.pendingTradeSequenceState().setMeso(0);
    }

    public static boolean mesoAdded(BotEntry entry) {
        return entry.pendingTradeSequenceState().mesoAdded();
    }

    public static boolean hasMesoToAdd(BotEntry entry) {
        return !mesoAdded(entry) && meso(entry) > 0;
    }

    public static void markMesoAdded(BotEntry entry) {
        entry.pendingTradeSequenceState().setMesoAdded(true);
    }

    public static void clearMesoAdded(BotEntry entry) {
        entry.pendingTradeSequenceState().setMesoAdded(false);
    }

    public static boolean allItemsAdded(BotEntry entry) {
        return entry.pendingTradeSequenceState().allItemsAdded();
    }

    public static void markAllItemsAdded(BotEntry entry) {
        entry.pendingTradeSequenceState().setAllItemsAdded(true);
    }

    public static void clearAllItemsAdded(BotEntry entry) {
        entry.pendingTradeSequenceState().setAllItemsAdded(false);
    }

    public static boolean botDone(BotEntry entry) {
        return entry.pendingTradeSequenceState().agentDone();
    }

    public static void markBotDone(BotEntry entry) {
        entry.pendingTradeSequenceState().setAgentDone(true);
    }

    public static void clearBotDone(BotEntry entry) {
        entry.pendingTradeSequenceState().setAgentDone(false);
    }

    public static int itemIndex(BotEntry entry) {
        return entry.pendingTradeSequenceState().itemIndex();
    }

    public static void incrementItemIndex(BotEntry entry) {
        entry.pendingTradeSequenceState().incrementItemIndex();
    }

    public static void clearItemIndex(BotEntry entry) {
        entry.pendingTradeSequenceState().setItemIndex(0);
    }

    public static List<Item> items(BotEntry entry) {
        return entry.pendingTradeSequenceState().items();
    }

    public static void setItems(BotEntry entry, List<Item> items) {
        entry.pendingTradeSequenceState().setItems(items);
    }

    public static boolean isBetweenBatches(BotEntry entry) {
        return entry.pendingTradeSequenceState().items() == null;
    }

    public static void clearItems(BotEntry entry) {
        entry.pendingTradeSequenceState().setItems(null);
    }

    public static boolean hasRestoreSlots(BotEntry entry) {
        return !entry.pendingTradeSequenceState().restoreSlots().isEmpty();
    }

    public static void rememberRestoreSlot(BotEntry entry, Item item, short slot) {
        entry.pendingTradeSequenceState().restoreSlots().put(item, slot);
    }

    public static void transferRestoreSlot(BotEntry entry, Item fromItem, Item toItem) {
        Short restoreSlot = entry.pendingTradeSequenceState().restoreSlots().remove(fromItem);
        if (restoreSlot != null) {
            entry.pendingTradeSequenceState().restoreSlots().put(toItem, restoreSlot);
        }
    }

    public static List<Map.Entry<Item, Short>> restoreSlotEntries(BotEntry entry) {
        return new ArrayList<>(entry.pendingTradeSequenceState().restoreSlots().entrySet());
    }

    public static void clearRestoreSlots(BotEntry entry) {
        entry.pendingTradeSequenceState().restoreSlots().clear();
    }

    public static boolean hasOwnerGivenItems(BotEntry entry) {
        return !entry.ownerGivenItems().isEmpty();
    }

    public static void addOwnerGivenItem(BotEntry entry, Item item) {
        entry.ownerGivenItems().add(item);
    }

    public static void clearOwnerGivenItems(BotEntry entry) {
        entry.ownerGivenItems().clear();
    }
}
