package server.agents.capabilities.trade;

import client.Character;
import server.Trade;
import server.bots.BotEntry;

import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

public final class AgentTradeLifecycleService {
    private AgentTradeLifecycleService() {
    }

    public static void cancelTradeSequence(BotEntry entry,
                                           Character agent,
                                           String message,
                                           LifecycleCallbacks callbacks) {
        AgentTradeCancellationService.cancelSequence(
                entry,
                agent,
                message,
                () -> resetTradeState(entry, agent, callbacks));
    }

    public static void clearManualTradeState(BotEntry entry,
                                             Character agent,
                                             LifecycleCallbacks callbacks) {
        callbacks.clearManualTradeState(entry, agent);
    }

    public static void resetTradeState(BotEntry entry,
                                       Character agent,
                                       LifecycleCallbacks callbacks) {
        AgentTradeResetService.reset(
                entry,
                agent,
                () -> callbacks.restoreTemporarilyUnequippedItems(entry, agent),
                () -> clearManualTradeState(entry, agent, callbacks),
                () -> callbacks.refillEquipmentSlots(agent, callbacks.owner(entry)));
    }

    public static void completeTradeAndReact(BotEntry entry,
                                             Character agent,
                                             Trade trade,
                                             LifecycleCallbacks callbacks) {
        AgentTradeCompletionService.completeAndReact(
                entry,
                agent,
                trade,
                () -> callbacks.randomReplyDelayMs(800, 1300),
                callbacks::tradeThanksReply,
                callbacks::tradeFreebieReply,
                callbacks::freebieRoll,
                callbacks::glareExpression);
    }

    public interface LifecycleCallbacks {
        void restoreTemporarilyUnequippedItems(BotEntry entry, Character agent);
        void clearManualTradeState(BotEntry entry, Character agent);
        Character owner(BotEntry entry);
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
                public void restoreTemporarilyUnequippedItems(BotEntry entry, Character agent) {
                    restoreTemporarilyUnequippedItems.restore(entry, agent);
                }

                @Override
                public void clearManualTradeState(BotEntry entry, Character agent) {
                    clearManualTradeState.clear(entry, agent);
                }

                @Override
                public Character owner(BotEntry entry) {
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
        void restore(BotEntry entry, Character agent);
    }

    @FunctionalInterface
    public interface ClearManualTrade {
        void clear(BotEntry entry, Character agent);
    }

    @FunctionalInterface
    public interface OwnerLookup {
        Character owner(BotEntry entry);
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
