package server.agents.capabilities.trade;

import server.agents.capabilities.movement.AgentMovementTimers;

import client.Character;
import client.inventory.Item;
import server.agents.capabilities.dialogue.AgentDialogueCatalog;
import server.agents.capabilities.dialogue.AgentDialogueSelector;
import server.agents.capabilities.inventory.AgentInventoryRuntime;
import server.agents.integration.AgentTradeGatewayRuntime;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.List;

public final class AgentSupplyShareTradeService {
    private AgentSupplyShareTradeService() {
    }

    public static void startPotShareTransfer(List<Item> items,
                                             Character recipient,
                                             AgentRuntimeEntry entry,
                                             Character agent,
                                             int maxQty) {
        startSupplyShareTransfer("pot_share", items, recipient, entry, agent, maxQty);
    }

    public static void startAmmoShareTransfer(List<Item> items,
                                              Character recipient,
                                              AgentRuntimeEntry entry,
                                              Character agent,
                                              int maxQty) {
        startSupplyShareTransfer("ammo_share", items, recipient, entry, agent, maxQty);
    }

    private static void startSupplyShareTransfer(String category,
                                                 List<Item> items,
                                                 Character recipient,
                                                 AgentRuntimeEntry entry,
                                                 Character agent,
                                                 int maxQty) {
        if (items.isEmpty()) return;
        if (agent.getTrade() != null
                || AgentPendingTradeStateRuntime.hasActiveSequence(entry)
                || recipient.getTrade() != null) {
            AgentPendingTradeStateRuntime.queueRetry(
                    entry,
                    () -> startSupplyShareTransfer(category, items, recipient, entry, agent, maxQty),
                    AgentMovementTimers.delayAfterCurrentTick(10_000));
            return;
        }
        AgentPendingTradeStateRuntime.setShareBudget(entry, maxQty);
        startTradeSequence(category, recipient, items, entry, agent);
    }

    private static void startTradeSequence(String category,
                                           Character recipient,
                                           List<Item> items,
                                           AgentRuntimeEntry entry,
                                           Character agent) {
        if (recipient == null) {
            AgentInventoryRuntime.replyNow(entry, AgentDialogueCatalog.tradeRecipientNotFoundReply());
            return;
        }
        AgentTradeStateService.initializeSequence(entry, category, recipient.getId(), true);
        openTradeBatch(entry, agent, recipient, items);
    }

    private static void openTradeBatch(AgentRuntimeEntry entry, Character agent, Character recipient, List<Item> items) {
        AgentTradeBatchService.openBatch(
                entry,
                agent,
                items,
                0,
                () -> recipient,
                () -> {
                    AgentInventoryRuntime.replyNow(entry, "can't trade right now, stopping");
                    AgentTradeStateService.clearSequence(entry);
                },
                () -> AgentTradeGatewayRuntime.trade().startTrade(agent),
                AgentTradeGatewayRuntime.trade()::inviteTrade,
                () -> AgentDialogueSelector.randomReply(AgentDialogueCatalog.tradeInvitationReplies()),
                message -> AgentInventoryRuntime.replyNow(entry, message));
    }

}

