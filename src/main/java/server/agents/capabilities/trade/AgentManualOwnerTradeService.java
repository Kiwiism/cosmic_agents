package server.agents.capabilities.trade;

import client.Character;
import server.Trade;

import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class AgentManualOwnerTradeService {
    private AgentManualOwnerTradeService() {
    }

    public static void tickOwnerTrade(Character agent,
                                      Character owner,
                                      Trade trade,
                                      OwnerTradeCallbacks callbacks) {
        if (!trade.isFullTrade()) {
            trade = callbacks.acceptInvite(owner, trade);
            if (trade == null || !trade.isFullTrade()) {
                return;
            }
        }

        callbacks.sendGreeting(agent, trade, callbacks.manualTradeGreeting());

        if (trade.isPartnerConfirmed()) {
            callbacks.completeTrade(trade);
            callbacks.refillEquipment(owner);
        }
    }

    public interface OwnerTradeCallbacks {
        Trade acceptInvite(Character inviter, Trade trade);
        void sendGreeting(Character agent, Trade trade, Supplier<String> greeting);
        Supplier<String> manualTradeGreeting();
        void completeTrade(Trade trade);
        void refillEquipment(Character owner);

        static OwnerTradeCallbacks of(BiFunction<Character, Trade, Trade> acceptInvite,
                                      GreetingSender sendGreeting,
                                      Supplier<String> manualTradeGreeting,
                                      Consumer<Trade> completeTrade,
                                      Consumer<Character> refillEquipment) {
            return new OwnerTradeCallbacks() {
                @Override
                public Trade acceptInvite(Character inviter, Trade trade) {
                    return acceptInvite.apply(inviter, trade);
                }

                @Override
                public void sendGreeting(Character agent, Trade trade, Supplier<String> greeting) {
                    sendGreeting.send(agent, trade, greeting);
                }

                @Override
                public Supplier<String> manualTradeGreeting() {
                    return manualTradeGreeting;
                }

                @Override
                public void completeTrade(Trade trade) {
                    completeTrade.accept(trade);
                }

                @Override
                public void refillEquipment(Character owner) {
                    refillEquipment.accept(owner);
                }
            };
        }
    }

    @FunctionalInterface
    public interface GreetingSender {
        void send(Character agent, Trade trade, Supplier<String> greeting);
    }
}
