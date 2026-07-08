package server.agents.capabilities.trade;

import client.Character;
import client.inventory.InventoryType;
import client.inventory.Item;
import constants.inventory.ItemConstants;
import server.Trade;
import server.agents.capabilities.dialogue.AgentEmote;
import server.agents.integration.AgentInventoryRuntime;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

public final class AgentTradeCompletionService {
    private AgentTradeCompletionService() {
    }

    public static void completeAndReact(AgentRuntimeEntry entry,
                                        Character agent,
                                        Trade trade,
                                        LongSupplier replyDelayMs,
                                        Supplier<String> thanksReply,
                                        Supplier<String> freebieReply,
                                        IntSupplier freebieRoll,
                                        BooleanSupplier glareExpression) {
        if (trade.getPartner() != null) {
            for (Item item : trade.getPartner().getItems()) {
                if (ItemConstants.getInventoryType(item.getItemId()) == InventoryType.EQUIP) {
                    AgentPendingTradeStateRuntime.addOwnerGivenItem(entry, item);
                }
            }
        }

        boolean receivedSomething = trade.getPartner() != null && trade.getPartner().hasAnyOffer();
        Trade.completeTrade(agent);
        long replyDelay = replyDelayMs.getAsLong();
        if (receivedSomething) {
            agent.changeFaceExpression(AgentEmote.HAPPY.getValue());
            AgentInventoryRuntime.afterDelay(replyDelay, () ->
                    AgentInventoryRuntime.visibleSayNow(entry, thanksReply.get()));
        } else if (freebieRoll.getAsInt() < 20) {
            agent.changeFaceExpression(glareExpression.getAsBoolean()
                    ? AgentEmote.GLARE.getValue()
                    : AgentEmote.ANNOYED.getValue());
            AgentInventoryRuntime.afterDelay(replyDelay, () ->
                    AgentInventoryRuntime.visibleSayNow(entry, freebieReply.get()));
        }
    }
}
