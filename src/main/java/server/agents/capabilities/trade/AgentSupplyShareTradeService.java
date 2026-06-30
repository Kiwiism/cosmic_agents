package server.agents.capabilities.trade;

import client.Character;
import client.inventory.Item;
import server.Trade;
import server.agents.capabilities.dialogue.AgentDialogueCatalog;
import server.agents.capabilities.inventory.AgentInventoryTradePolicy;
import server.agents.integration.AgentBotInventoryRuntime;
import server.agents.integration.AgentBotPendingTradeStateRuntime;
import server.bots.BotEntry;
import server.bots.BotManager;
import server.bots.BotMovementManager;

import java.util.ArrayList;
import java.util.List;

public final class AgentSupplyShareTradeService {
    private AgentSupplyShareTradeService() {
    }

    public static void startPotShareTransfer(List<Item> items,
                                             Character recipient,
                                             BotEntry entry,
                                             Character agent,
                                             int maxQty) {
        startSupplyShareTransfer("pot_share", items, recipient, entry, agent, maxQty);
    }

    public static void startAmmoShareTransfer(List<Item> items,
                                              Character recipient,
                                              BotEntry entry,
                                              Character agent,
                                              int maxQty) {
        startSupplyShareTransfer("ammo_share", items, recipient, entry, agent, maxQty);
    }

    private static void startSupplyShareTransfer(String category,
                                                 List<Item> items,
                                                 Character recipient,
                                                 BotEntry entry,
                                                 Character agent,
                                                 int maxQty) {
        if (items.isEmpty()) return;
        if (agent.getTrade() != null
                || AgentBotPendingTradeStateRuntime.hasActiveSequence(entry)
                || recipient.getTrade() != null) {
            AgentBotPendingTradeStateRuntime.queueRetry(
                    entry,
                    () -> startSupplyShareTransfer(category, items, recipient, entry, agent, maxQty),
                    BotMovementManager.delayAfterCurrentTick(10_000));
            return;
        }
        AgentBotPendingTradeStateRuntime.setShareBudget(entry, maxQty);
        startTradeSequence(category, recipient, items, entry, agent);
    }

    private static void startTradeSequence(String category,
                                           Character recipient,
                                           List<Item> items,
                                           BotEntry entry,
                                           Character agent) {
        if (recipient == null) {
            AgentBotInventoryRuntime.replyNow(entry, AgentDialogueCatalog.tradeRecipientNotFoundReply());
            return;
        }
        AgentBotPendingTradeStateRuntime.setCategory(entry, category);
        AgentBotPendingTradeStateRuntime.setRecipientId(entry, recipient.getId());
        AgentBotPendingTradeStateRuntime.setSingleBatch(entry, true);
        AgentBotPendingTradeStateRuntime.clearInviteAnnounced(entry);
        openTradeBatch(entry, agent, recipient, items);
    }

    private static void openTradeBatch(BotEntry entry, Character agent, Character recipient, List<Item> items) {
        if (recipient.getTrade() != null) {
            AgentBotInventoryRuntime.replyNow(entry, "can't trade right now, stopping");
            clearTradeState(entry);
            return;
        }
        AgentBotPendingTradeStateRuntime.setItems(entry, items.size() > AgentInventoryTradePolicy.TRADE_WINDOW_ITEM_LIMIT
                ? new ArrayList<>(items.subList(0, AgentInventoryTradePolicy.TRADE_WINDOW_ITEM_LIMIT))
                : new ArrayList<>(items));
        AgentBotPendingTradeStateRuntime.setMeso(entry, 0);
        AgentBotPendingTradeStateRuntime.clearItemIndex(entry);
        AgentBotPendingTradeStateRuntime.clearTimer(entry);
        AgentBotPendingTradeStateRuntime.clearMesoAdded(entry);
        AgentBotPendingTradeStateRuntime.clearAllItemsAdded(entry);
        AgentBotPendingTradeStateRuntime.clearBotDone(entry);
        Trade.startTrade(agent);
        Trade.inviteTrade(agent, recipient);
        if (!AgentBotPendingTradeStateRuntime.inviteAnnounced(entry)
                && !AgentBotPendingTradeStateRuntime.isSupplyShareCategory(entry)) {
            AgentBotPendingTradeStateRuntime.markInviteAnnounced(entry);
            AgentBotInventoryRuntime.replyNow(entry, BotManager.randomReply(AgentDialogueCatalog.tradeInvitationReplies()));
        }
    }

    private static void clearTradeState(BotEntry entry) {
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
    }
}
