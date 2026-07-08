package server.agents.capabilities.trade;

import client.inventory.Item;
import server.agents.capabilities.inventory.AgentInventoryTradePolicy;
import server.agents.integration.AgentPendingTradeStateRuntime;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.ArrayList;
import java.util.List;

public final class AgentTradeStateService {
    private AgentTradeStateService() {
    }

    public static void initializeSequence(AgentRuntimeEntry entry, String category, int recipientId, boolean singleBatch) {
        AgentPendingTradeStateRuntime.setCategory(entry, category);
        AgentPendingTradeStateRuntime.setRecipientId(entry, recipientId);
        AgentPendingTradeStateRuntime.setSingleBatch(entry, singleBatch);
        AgentPendingTradeStateRuntime.clearInviteAnnounced(entry);
    }

    public static void initializeBatch(AgentRuntimeEntry entry, List<Item> items, int mesos) {
        AgentPendingTradeStateRuntime.setItems(entry, firstTradeWindowItems(items));
        AgentPendingTradeStateRuntime.setMeso(entry, mesos);
        clearBatchProgress(entry);
    }

    public static List<Item> firstTradeWindowItems(List<Item> items) {
        return items.size() > AgentInventoryTradePolicy.TRADE_WINDOW_ITEM_LIMIT
                ? new ArrayList<>(items.subList(0, AgentInventoryTradePolicy.TRADE_WINDOW_ITEM_LIMIT))
                : new ArrayList<>(items);
    }

    public static void clearBatchProgress(AgentRuntimeEntry entry) {
        AgentPendingTradeStateRuntime.clearItemIndex(entry);
        AgentPendingTradeStateRuntime.clearTimer(entry);
        AgentPendingTradeStateRuntime.clearMesoAdded(entry);
        AgentPendingTradeStateRuntime.clearAllItemsAdded(entry);
        AgentPendingTradeStateRuntime.clearBotDone(entry);
    }

    public static void enterBetweenBatches(AgentRuntimeEntry entry, int delayMs) {
        AgentPendingTradeStateRuntime.clearItems(entry);
        AgentPendingTradeStateRuntime.clearAllItemsAdded(entry);
        AgentPendingTradeStateRuntime.clearBotDone(entry);
        AgentPendingTradeStateRuntime.setTimerMs(entry, delayMs);
    }

    public static void clearSequence(AgentRuntimeEntry entry) {
        AgentPendingTradeStateRuntime.clearCategory(entry);
        AgentPendingTradeStateRuntime.clearCategoryMessage(entry);
        AgentPendingTradeStateRuntime.clearItems(entry);
        AgentPendingTradeStateRuntime.clearRecipientId(entry);
        AgentPendingTradeStateRuntime.clearMeso(entry);
        AgentPendingTradeStateRuntime.clearItemIndex(entry);
        AgentPendingTradeStateRuntime.clearTimer(entry);
        AgentPendingTradeStateRuntime.clearMesoAdded(entry);
        AgentPendingTradeStateRuntime.clearAllItemsAdded(entry);
        AgentPendingTradeStateRuntime.clearBotDone(entry);
        AgentPendingTradeStateRuntime.clearSingleBatch(entry);
        AgentPendingTradeStateRuntime.clearInviteAnnounced(entry);
        AgentPendingTradeStateRuntime.clearShareBudget(entry);
        AgentPendingTradeStateRuntime.clearOwnerGivenItems(entry);
    }
}
