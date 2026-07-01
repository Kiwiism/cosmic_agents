package server.agents.capabilities.trade;

import client.inventory.Item;
import server.agents.capabilities.inventory.AgentInventoryTradePolicy;
import server.agents.integration.AgentBotPendingTradeStateRuntime;
import server.bots.BotEntry;

import java.util.ArrayList;
import java.util.List;

public final class AgentTradeStateService {
    private AgentTradeStateService() {
    }

    public static void initializeSequence(BotEntry entry, String category, int recipientId, boolean singleBatch) {
        AgentBotPendingTradeStateRuntime.setCategory(entry, category);
        AgentBotPendingTradeStateRuntime.setRecipientId(entry, recipientId);
        AgentBotPendingTradeStateRuntime.setSingleBatch(entry, singleBatch);
        AgentBotPendingTradeStateRuntime.clearInviteAnnounced(entry);
    }

    public static void initializeBatch(BotEntry entry, List<Item> items, int mesos) {
        AgentBotPendingTradeStateRuntime.setItems(entry, firstTradeWindowItems(items));
        AgentBotPendingTradeStateRuntime.setMeso(entry, mesos);
        clearBatchProgress(entry);
    }

    public static List<Item> firstTradeWindowItems(List<Item> items) {
        return items.size() > AgentInventoryTradePolicy.TRADE_WINDOW_ITEM_LIMIT
                ? new ArrayList<>(items.subList(0, AgentInventoryTradePolicy.TRADE_WINDOW_ITEM_LIMIT))
                : new ArrayList<>(items);
    }

    public static void clearBatchProgress(BotEntry entry) {
        AgentBotPendingTradeStateRuntime.clearItemIndex(entry);
        AgentBotPendingTradeStateRuntime.clearTimer(entry);
        AgentBotPendingTradeStateRuntime.clearMesoAdded(entry);
        AgentBotPendingTradeStateRuntime.clearAllItemsAdded(entry);
        AgentBotPendingTradeStateRuntime.clearBotDone(entry);
    }

    public static void enterBetweenBatches(BotEntry entry, int delayMs) {
        AgentBotPendingTradeStateRuntime.clearItems(entry);
        AgentBotPendingTradeStateRuntime.clearAllItemsAdded(entry);
        AgentBotPendingTradeStateRuntime.clearBotDone(entry);
        AgentBotPendingTradeStateRuntime.setTimerMs(entry, delayMs);
    }

    public static void clearSequence(BotEntry entry) {
        AgentBotPendingTradeStateRuntime.clearCategory(entry);
        AgentBotPendingTradeStateRuntime.clearCategoryMessage(entry);
        AgentBotPendingTradeStateRuntime.clearItems(entry);
        AgentBotPendingTradeStateRuntime.clearRecipientId(entry);
        AgentBotPendingTradeStateRuntime.clearMeso(entry);
        AgentBotPendingTradeStateRuntime.clearItemIndex(entry);
        AgentBotPendingTradeStateRuntime.clearTimer(entry);
        AgentBotPendingTradeStateRuntime.clearMesoAdded(entry);
        AgentBotPendingTradeStateRuntime.clearAllItemsAdded(entry);
        AgentBotPendingTradeStateRuntime.clearBotDone(entry);
        AgentBotPendingTradeStateRuntime.clearSingleBatch(entry);
        AgentBotPendingTradeStateRuntime.clearInviteAnnounced(entry);
        AgentBotPendingTradeStateRuntime.clearShareBudget(entry);
        AgentBotPendingTradeStateRuntime.clearOwnerGivenItems(entry);
    }
}
