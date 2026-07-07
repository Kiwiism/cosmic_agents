package server.agents.capabilities.trade;

import client.Character;
import client.inventory.Item;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class AgentTradeSequenceOrchestrator {
    private AgentTradeSequenceOrchestrator() {
    }

    public static void startTradeSequence(String category,
                                          Character recipient,
                                          List<Item> items,
                                          int mesos,
                                          boolean singleBatch,
                                          AgentRuntimeEntry entry,
                                          Character agent,
                                          SequenceCallbacks callbacks) {
        AgentTradeSequenceService.startSequence(
                category,
                recipient,
                items,
                mesos,
                singleBatch,
                entry,
                (batchItems, batchMesos) -> openTradeBatch(entry, agent, batchItems, batchMesos, callbacks));
    }

    public static void openTradeBatch(AgentRuntimeEntry entry,
                                      Character agent,
                                      List<Item> items,
                                      int mesos,
                                      SequenceCallbacks callbacks) {
        AgentTradeBatchService.openBatch(
                entry,
                agent,
                items,
                mesos,
                callbacks::resolveTradeRecipient,
                callbacks::cancelUnavailableTrade,
                callbacks::startTrade,
                callbacks::inviteTrade,
                callbacks::invitationReply,
                callbacks::reply);
    }

    public interface SequenceCallbacks {
        Character resolveTradeRecipient();
        void cancelUnavailableTrade();
        void startTrade();
        void inviteTrade(Character agent, Character recipient);
        String invitationReply();
        void reply(String message);

        static SequenceCallbacks of(Supplier<Character> resolveTradeRecipient,
                                    Runnable cancelUnavailableTrade,
                                    Runnable startTrade,
                                    BiConsumer<Character, Character> inviteTrade,
                                    Supplier<String> invitationReply,
                                    Consumer<String> reply) {
            return new SequenceCallbacks() {
                @Override public Character resolveTradeRecipient() { return resolveTradeRecipient.get(); }
                @Override public void cancelUnavailableTrade() { cancelUnavailableTrade.run(); }
                @Override public void startTrade() { startTrade.run(); }
                @Override public void inviteTrade(Character agent, Character recipient) {
                    inviteTrade.accept(agent, recipient);
                }
                @Override public String invitationReply() { return invitationReply.get(); }
                @Override public void reply(String message) { reply.accept(message); }
            };
        }
    }
}
