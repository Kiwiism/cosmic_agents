package server.agents.capabilities.trade;

import client.Character;
import client.inventory.InventoryType;
import client.inventory.Item;
import constants.inventory.ItemConstants;
import server.agents.capabilities.dialogue.AgentEmote;
import server.agents.capabilities.inventory.AgentInventoryRuntime;
import server.agents.integration.AgentTradeGatewayRuntime;
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
                                        Iterable<Item> partnerItems,
                                        boolean receivedSomething,
                                        LongSupplier replyDelayMs,
                                        Supplier<String> thanksReply,
                                        Supplier<String> freebieReply,
                                        IntSupplier freebieRoll,
                                        BooleanSupplier glareExpression) {
        if (!entry.isPartnerManaged() && partnerItems != null) {
            for (Item item : partnerItems) {
                if (ItemConstants.getInventoryType(item.getItemId()) == InventoryType.EQUIP) {
                    AgentPendingTradeStateRuntime.addOwnerGivenItem(entry, item);
                }
            }
        }

        AgentTradeGatewayRuntime.trade().completeTrade(agent);
        long replyDelay = replyDelayMs.getAsLong();
        if (receivedSomething) {
            agent.changeFaceExpression(AgentEmote.HAPPY.getValue());
            AgentInventoryRuntime.afterDelay(entry, replyDelay, () ->
                    AgentInventoryRuntime.visibleSayNow(entry, thanksReply.get()));
        } else if (freebieRoll.getAsInt() < 20) {
            agent.changeFaceExpression(glareExpression.getAsBoolean()
                    ? AgentEmote.GLARE.getValue()
                    : AgentEmote.ANNOYED.getValue());
            AgentInventoryRuntime.afterDelay(entry, replyDelay, () ->
                    AgentInventoryRuntime.visibleSayNow(entry, freebieReply.get()));
        }
    }
}
