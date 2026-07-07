package server.agents.capabilities.trade;

import client.Character;
import server.Trade;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

public final class AgentTradeLifecycleRuntimeService {
    private AgentTradeLifecycleRuntimeService() {
    }

    public static void cancelTradeSequence(AgentRuntimeEntry entry,
                                           Character agent,
                                           String message,
                                           RuntimeCallbacks callbacks) {
        AgentTradeLifecycleService.cancelTradeSequence(entry, agent, message, lifecycleCallbacks(callbacks));
    }

    public static void clearManualTradeState(AgentRuntimeEntry entry,
                                             Character agent,
                                             RuntimeCallbacks callbacks) {
        AgentTradeLifecycleService.clearManualTradeState(entry, agent, lifecycleCallbacks(callbacks));
    }

    public static void resetTradeState(AgentRuntimeEntry entry,
                                       Character agent,
                                       RuntimeCallbacks callbacks) {
        AgentTradeLifecycleService.resetTradeState(entry, agent, lifecycleCallbacks(callbacks));
    }

    public static void completeTradeAndReact(AgentRuntimeEntry entry,
                                             Character agent,
                                             Trade trade,
                                             RuntimeCallbacks callbacks) {
        AgentTradeLifecycleService.completeTradeAndReact(entry, agent, trade, lifecycleCallbacks(callbacks));
    }

    public static AgentTradeLifecycleService.LifecycleCallbacks lifecycleCallbacks(RuntimeCallbacks callbacks) {
        return AgentTradeLifecycleCallbackService.lifecycleCallbacks(
                callbacks::restoreTemporarilyUnequippedItems,
                callbacks::clearManualTradeState,
                callbacks::owner,
                callbacks::refillEquipmentSlots,
                callbacks::randomReplyDelayMs,
                callbacks::tradeThanksReply,
                callbacks::tradeFreebieReply,
                () -> ThreadLocalRandom.current().nextInt(100),
                () -> ThreadLocalRandom.current().nextBoolean());
    }

    public interface RuntimeCallbacks {
        void restoreTemporarilyUnequippedItems(AgentRuntimeEntry entry, Character agent);

        void clearManualTradeState(AgentRuntimeEntry entry, Character agent);

        Character owner(AgentRuntimeEntry entry);

        void refillEquipmentSlots(Character agent, Character owner);

        long randomReplyDelayMs(int minMs, int maxMs);

        String tradeThanksReply();

        String tradeFreebieReply();

        static RuntimeCallbacks of(AgentTradeLifecycleService.RestoreSlots restoreTemporarilyUnequippedItems,
                                   AgentTradeLifecycleService.ClearManualTrade clearManualTradeState,
                                   AgentTradeLifecycleService.OwnerLookup owner,
                                   BiConsumer<Character, Character> refillEquipmentSlots,
                                   AgentTradeLifecycleService.ReplyDelay randomReplyDelayMs,
                                   Supplier<String> tradeThanksReply,
                                   Supplier<String> tradeFreebieReply) {
            return new RuntimeCallbacks() {
                @Override
                public void restoreTemporarilyUnequippedItems(AgentRuntimeEntry entry, Character agent) {
                    restoreTemporarilyUnequippedItems.restore(entry, agent);
                }

                @Override
                public void clearManualTradeState(AgentRuntimeEntry entry, Character agent) {
                    clearManualTradeState.clear(entry, agent);
                }

                @Override
                public Character owner(AgentRuntimeEntry entry) {
                    return owner.owner(entry);
                }

                @Override
                public void refillEquipmentSlots(Character agent, Character owner) {
                    refillEquipmentSlots.accept(agent, owner);
                }

                @Override
                public long randomReplyDelayMs(int minMs, int maxMs) {
                    return randomReplyDelayMs.delay(minMs, maxMs);
                }

                @Override
                public String tradeThanksReply() {
                    return tradeThanksReply.get();
                }

                @Override
                public String tradeFreebieReply() {
                    return tradeFreebieReply.get();
                }
            };
        }
    }
}
