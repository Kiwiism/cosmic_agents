package server.agents.integration;

import client.inventory.Item;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Agent-owned adapter for temporary AgentRuntimeEntry-backed pending trade sequence state.
 */
public final class AgentPendingTradeStateRuntime {
    private AgentPendingTradeStateRuntime() {
    }

    public static boolean hasActiveSequence(AgentRuntimeEntry entry) {
        return entry.pendingTradeSequenceState().category() != null;
    }

    public static boolean isIdle(AgentRuntimeEntry entry) {
        return !hasActiveSequence(entry);
    }

    public static String category(AgentRuntimeEntry entry) {
        return entry.pendingTradeSequenceState().category();
    }

    public static void setCategory(AgentRuntimeEntry entry, String category) {
        entry.pendingTradeSequenceState().setCategory(category);
    }

    public static void clearCategory(AgentRuntimeEntry entry) {
        entry.pendingTradeSequenceState().setCategory(null);
    }

    public static boolean isCategory(AgentRuntimeEntry entry, String category) {
        return category.equals(entry.pendingTradeSequenceState().category());
    }

    public static boolean isSupplyShareCategory(AgentRuntimeEntry entry) {
        return isCategory(entry, "pot_share") || isCategory(entry, "ammo_share");
    }

    public static boolean hasQueuedRetry(AgentRuntimeEntry entry) {
        return entry.tradeRetryState().hasRetry();
    }

    public static void queueRetry(AgentRuntimeEntry entry, Runnable retry, int delayMs) {
        entry.tradeRetryState().queueRetry(retry, delayMs);
    }

    public static int retryDelayMs(AgentRuntimeEntry entry) {
        return entry.tradeRetryState().delayMs();
    }

    public static void setRetryDelayMs(AgentRuntimeEntry entry, int delayMs) {
        entry.tradeRetryState().setDelayMs(delayMs);
    }

    public static Runnable takeRetry(AgentRuntimeEntry entry) {
        return entry.tradeRetryState().takeRetry();
    }

    public static int shareBudget(AgentRuntimeEntry entry) {
        return entry.pendingTradeSequenceState().shareBudget();
    }

    public static void setShareBudget(AgentRuntimeEntry entry, int maxQuantity) {
        entry.pendingTradeSequenceState().setShareBudget(maxQuantity);
    }

    public static void clearShareBudget(AgentRuntimeEntry entry) {
        entry.pendingTradeSequenceState().setShareBudget(0);
    }

    public static short capShareQuantity(AgentRuntimeEntry entry, short availableQuantity) {
        int budget = shareBudget(entry);
        if (budget <= 0) {
            return availableQuantity;
        }
        short tradeQuantity = (short) Math.min(availableQuantity, budget);
        entry.pendingTradeSequenceState().setShareBudget(budget - tradeQuantity);
        return tradeQuantity;
    }

    public static String categoryMessage(AgentRuntimeEntry entry) {
        return entry.pendingTradeSequenceState().categoryMessage();
    }

    public static void setCategoryMessage(AgentRuntimeEntry entry, String message) {
        entry.pendingTradeSequenceState().setCategoryMessage(message);
    }

    public static void clearCategoryMessage(AgentRuntimeEntry entry) {
        entry.pendingTradeSequenceState().setCategoryMessage(null);
    }

    public static String takeCategoryMessage(AgentRuntimeEntry entry) {
        String message = categoryMessage(entry);
        clearCategoryMessage(entry);
        return message;
    }

    public static int recipientId(AgentRuntimeEntry entry) {
        return entry.pendingTradeSequenceState().recipientId();
    }

    public static void setRecipientId(AgentRuntimeEntry entry, int recipientId) {
        entry.pendingTradeSequenceState().setRecipientId(recipientId);
    }

    public static void clearRecipientId(AgentRuntimeEntry entry) {
        entry.pendingTradeSequenceState().setRecipientId(0);
    }

    public static boolean inviteAnnounced(AgentRuntimeEntry entry) {
        return entry.pendingTradeSequenceState().inviteAnnounced();
    }

    public static void markInviteAnnounced(AgentRuntimeEntry entry) {
        entry.pendingTradeSequenceState().setInviteAnnounced(true);
    }

    public static void clearInviteAnnounced(AgentRuntimeEntry entry) {
        entry.pendingTradeSequenceState().setInviteAnnounced(false);
    }

    public static int timerMs(AgentRuntimeEntry entry) {
        return entry.pendingTradeSequenceState().timerMs();
    }

    public static void setTimerMs(AgentRuntimeEntry entry, int timerMs) {
        entry.pendingTradeSequenceState().setTimerMs(timerMs);
    }

    public static void addTimerMs(AgentRuntimeEntry entry, int deltaMs) {
        entry.pendingTradeSequenceState().setTimerMs(entry.pendingTradeSequenceState().timerMs() + deltaMs);
    }

    public static void tickTimerDown(AgentRuntimeEntry entry, java.util.function.IntUnaryOperator tickDown) {
        entry.pendingTradeSequenceState().setTimerMs(tickDown.applyAsInt(entry.pendingTradeSequenceState().timerMs()));
    }

    public static void clearTimer(AgentRuntimeEntry entry) {
        entry.pendingTradeSequenceState().setTimerMs(0);
    }

    public static boolean singleBatch(AgentRuntimeEntry entry) {
        return entry.pendingTradeSequenceState().singleBatch();
    }

    public static void setSingleBatch(AgentRuntimeEntry entry, boolean singleBatch) {
        entry.pendingTradeSequenceState().setSingleBatch(singleBatch);
    }

    public static void clearSingleBatch(AgentRuntimeEntry entry) {
        entry.pendingTradeSequenceState().setSingleBatch(false);
    }

    public static int meso(AgentRuntimeEntry entry) {
        return entry.pendingTradeSequenceState().meso();
    }

    public static void setMeso(AgentRuntimeEntry entry, int meso) {
        entry.pendingTradeSequenceState().setMeso(meso);
    }

    public static void clearMeso(AgentRuntimeEntry entry) {
        entry.pendingTradeSequenceState().setMeso(0);
    }

    public static boolean mesoAdded(AgentRuntimeEntry entry) {
        return entry.pendingTradeSequenceState().mesoAdded();
    }

    public static boolean hasMesoToAdd(AgentRuntimeEntry entry) {
        return !mesoAdded(entry) && meso(entry) > 0;
    }

    public static void markMesoAdded(AgentRuntimeEntry entry) {
        entry.pendingTradeSequenceState().setMesoAdded(true);
    }

    public static void clearMesoAdded(AgentRuntimeEntry entry) {
        entry.pendingTradeSequenceState().setMesoAdded(false);
    }

    public static boolean allItemsAdded(AgentRuntimeEntry entry) {
        return entry.pendingTradeSequenceState().allItemsAdded();
    }

    public static void markAllItemsAdded(AgentRuntimeEntry entry) {
        entry.pendingTradeSequenceState().setAllItemsAdded(true);
    }

    public static void clearAllItemsAdded(AgentRuntimeEntry entry) {
        entry.pendingTradeSequenceState().setAllItemsAdded(false);
    }

    public static boolean botDone(AgentRuntimeEntry entry) {
        return entry.pendingTradeSequenceState().agentDone();
    }

    public static void markBotDone(AgentRuntimeEntry entry) {
        entry.pendingTradeSequenceState().setAgentDone(true);
    }

    public static void clearBotDone(AgentRuntimeEntry entry) {
        entry.pendingTradeSequenceState().setAgentDone(false);
    }

    public static int itemIndex(AgentRuntimeEntry entry) {
        return entry.pendingTradeSequenceState().itemIndex();
    }

    public static void incrementItemIndex(AgentRuntimeEntry entry) {
        entry.pendingTradeSequenceState().incrementItemIndex();
    }

    public static void clearItemIndex(AgentRuntimeEntry entry) {
        entry.pendingTradeSequenceState().setItemIndex(0);
    }

    public static List<Item> items(AgentRuntimeEntry entry) {
        return entry.pendingTradeSequenceState().items();
    }

    public static void setItems(AgentRuntimeEntry entry, List<Item> items) {
        entry.pendingTradeSequenceState().setItems(items);
    }

    public static boolean isBetweenBatches(AgentRuntimeEntry entry) {
        return entry.pendingTradeSequenceState().items() == null;
    }

    public static void clearItems(AgentRuntimeEntry entry) {
        entry.pendingTradeSequenceState().setItems(null);
    }

    public static boolean hasRestoreSlots(AgentRuntimeEntry entry) {
        return !entry.pendingTradeSequenceState().restoreSlots().isEmpty();
    }

    public static void rememberRestoreSlot(AgentRuntimeEntry entry, Item item, short slot) {
        entry.pendingTradeSequenceState().restoreSlots().put(item, slot);
    }

    public static void transferRestoreSlot(AgentRuntimeEntry entry, Item fromItem, Item toItem) {
        Short restoreSlot = entry.pendingTradeSequenceState().restoreSlots().remove(fromItem);
        if (restoreSlot != null) {
            entry.pendingTradeSequenceState().restoreSlots().put(toItem, restoreSlot);
        }
    }

    public static List<Map.Entry<Item, Short>> restoreSlotEntries(AgentRuntimeEntry entry) {
        return new ArrayList<>(entry.pendingTradeSequenceState().restoreSlots().entrySet());
    }

    public static void clearRestoreSlots(AgentRuntimeEntry entry) {
        entry.pendingTradeSequenceState().restoreSlots().clear();
    }

    public static boolean hasOwnerGivenItems(AgentRuntimeEntry entry) {
        return entry.ownerGivenTradeItemState().hasItems();
    }

    public static void addOwnerGivenItem(AgentRuntimeEntry entry, Item item) {
        entry.ownerGivenTradeItemState().add(item);
    }

    public static void clearOwnerGivenItems(AgentRuntimeEntry entry) {
        entry.ownerGivenTradeItemState().clear();
    }
}
