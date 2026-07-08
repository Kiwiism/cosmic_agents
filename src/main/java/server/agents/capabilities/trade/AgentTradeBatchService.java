package server.agents.capabilities.trade;

import client.Character;
import client.inventory.Item;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class AgentTradeBatchService {
    private AgentTradeBatchService() {
    }

    public static void openBatch(AgentRuntimeEntry entry,
                                 Character agent,
                                 List<Item> items,
                                 int mesos,
                                 Supplier<Character> recipientResolver,
                                 Runnable cancelSequence,
                                 Runnable startTrade,
                                 BiConsumer<Character, Character> inviteTrade,
                                 Supplier<String> invitationReply,
                                 Consumer<String> reply) {
        Character recipient = recipientResolver.get();
        if (recipient == null || recipient.getTrade() != null) {
            cancelSequence.run();
            return;
        }

        AgentTradeStateService.initializeBatch(entry, items, mesos);
        startTrade.run();
        inviteTrade.accept(agent, recipient);
        if (!AgentPendingTradeStateRuntime.inviteAnnounced(entry)
                && !AgentPendingTradeStateRuntime.isSupplyShareCategory(entry)) {
            AgentPendingTradeStateRuntime.markInviteAnnounced(entry);
            reply.accept(invitationReply.get());
        }
    }
}
