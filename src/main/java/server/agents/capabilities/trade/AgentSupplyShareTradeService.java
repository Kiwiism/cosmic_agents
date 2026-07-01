package server.agents.capabilities.trade;

import client.Character;
import client.inventory.Item;
import server.agents.capabilities.dialogue.AgentDialogueCatalog;
import server.agents.integration.AgentBotInventoryRuntime;
import server.agents.integration.AgentBotPendingTradeStateRuntime;
import server.bots.BotEntry;
import server.bots.BotManager;
import server.bots.BotMovementManager;

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
        AgentTradeStateService.initializeSequence(entry, category, recipient.getId(), true);
        openTradeBatch(entry, agent, recipient, items);
    }

    private static void openTradeBatch(BotEntry entry, Character agent, Character recipient, List<Item> items) {
        AgentTradeBatchService.openBatch(
                entry,
                agent,
                items,
                0,
                () -> recipient,
                () -> {
                    AgentBotInventoryRuntime.replyNow(entry, "can't trade right now, stopping");
                    AgentTradeStateService.clearSequence(entry);
                },
                () -> server.Trade.startTrade(agent),
                server.Trade::inviteTrade,
                () -> BotManager.randomReply(AgentDialogueCatalog.tradeInvitationReplies()),
                message -> AgentBotInventoryRuntime.replyNow(entry, message));
    }

}
