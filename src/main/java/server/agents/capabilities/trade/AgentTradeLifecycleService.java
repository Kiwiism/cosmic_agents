package server.agents.capabilities.trade;

import client.Character;
import client.inventory.Item;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.Collections;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

public final class AgentTradeLifecycleService {
    private AgentTradeLifecycleService() {
    }

    public static void cancelTradeSequence(AgentRuntimeEntry entry,
                                           Character agent,
                                           String message,
                                           LifecycleCallbacks callbacks) {
        AgentTradeCancellationService.cancelSequence(
                entry,
                agent,
                message,
                () -> resetTradeState(entry, agent, callbacks));
    }

    public static void clearManualTradeState(AgentRuntimeEntry entry,
                                             Character agent,
                                             LifecycleCallbacks callbacks) {
        callbacks.clearManualTradeState(entry, agent);
    }

    public static void resetTradeState(AgentRuntimeEntry entry,
                                       Character agent,
                                       LifecycleCallbacks callbacks) {
        AgentTradeResetService.reset(
                entry,
                agent,
                () -> callbacks.restoreTemporarilyUnequippedItems(entry, agent),
                () -> clearManualTradeState(entry, agent, callbacks),
                () -> callbacks.refillEquipmentSlots(agent, callbacks.owner(entry)));
    }

    public static void completeTradeAndReact(AgentRuntimeEntry entry,
                                             Character agent,
                                             AgentTradeWindow trade,
                                             LifecycleCallbacks callbacks) {
        AgentTradeWindow partner = trade.partner();
        List<Item> partnerItems = partner != null ? partner.items() : Collections.emptyList();
        boolean receivedSomething = partner != null && partner.hasAnyOffer();
        AgentTradeCompletionService.completeAndReact(
                entry,
                agent,
                partnerItems,
                receivedSomething,
                () -> callbacks.randomReplyDelayMs(800, 1300),
                callbacks::tradeThanksReply,
                callbacks::tradeFreebieReply,
                callbacks::freebieRoll,
                callbacks::glareExpression);
    }

    public interface LifecycleCallbacks {
        void restoreTemporarilyUnequippedItems(AgentRuntimeEntry entry, Character agent);
        void clearManualTradeState(AgentRuntimeEntry entry, Character agent);
        Character owner(AgentRuntimeEntry entry);
        void refillEquipmentSlots(Character agent, Character owner);
        long randomReplyDelayMs(int minMs, int maxMs);
        String tradeThanksReply();
        String tradeFreebieReply();
        int freebieRoll();
        boolean glareExpression();

        static LifecycleCallbacks of(RestoreSlots restoreTemporarilyUnequippedItems,
                                     ClearManualTrade clearManualTradeState,
                                     OwnerLookup owner,
                                     RefillEquipment refillEquipmentSlots,
                                     ReplyDelay randomReplyDelayMs,
                                     Supplier<String> tradeThanksReply,
                                     Supplier<String> tradeFreebieReply,
                                     IntSupplier freebieRoll,
                                     BooleanSupplier glareExpression) {
            return new LifecycleCallbacks() {
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
                    refillEquipmentSlots.refill(agent, owner);
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

                @Override
                public int freebieRoll() {
                    return freebieRoll.getAsInt();
                }

                @Override
                public boolean glareExpression() {
                    return glareExpression.getAsBoolean();
                }
            };
        }
    }

    @FunctionalInterface
    public interface RestoreSlots {
        void restore(AgentRuntimeEntry entry, Character agent);
    }

    @FunctionalInterface
    public interface ClearManualTrade {
        void clear(AgentRuntimeEntry entry, Character agent);
    }

    @FunctionalInterface
    public interface OwnerLookup {
        Character owner(AgentRuntimeEntry entry);
    }

    @FunctionalInterface
    public interface RefillEquipment {
        void refill(Character agent, Character owner);
    }

    @FunctionalInterface
    public interface ReplyDelay {
        long delay(int minMs, int maxMs);
    }
}
