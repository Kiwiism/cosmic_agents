package server.agents.capabilities.trade;

import client.Character;

import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class AgentManualOwnerTradeService {
    private AgentManualOwnerTradeService() {
    }

    public static void tickOwnerTrade(Character agent,
                                      Character owner,
                                      AgentTradeWindow trade,
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
        AgentTradeWindow acceptInvite(Character inviter, AgentTradeWindow trade);
        void sendGreeting(Character agent, AgentTradeWindow trade, Supplier<String> greeting);
        Supplier<String> manualTradeGreeting();
        void completeTrade(AgentTradeWindow trade);
        void refillEquipment(Character owner);

        static OwnerTradeCallbacks of(BiFunction<Character, AgentTradeWindow, AgentTradeWindow> acceptInvite,
                                      GreetingSender sendGreeting,
                                      Supplier<String> manualTradeGreeting,
                                      Consumer<AgentTradeWindow> completeTrade,
                                      Consumer<Character> refillEquipment) {
            return new OwnerTradeCallbacks() {
                @Override
                public AgentTradeWindow acceptInvite(Character inviter, AgentTradeWindow trade) {
                    return acceptInvite.apply(inviter, trade);
                }

                @Override
                public void sendGreeting(Character agent, AgentTradeWindow trade, Supplier<String> greeting) {
                    sendGreeting.send(agent, trade, greeting);
                }

                @Override
                public Supplier<String> manualTradeGreeting() {
                    return manualTradeGreeting;
                }

                @Override
                public void completeTrade(AgentTradeWindow trade) {
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
        void send(Character agent, AgentTradeWindow trade, Supplier<String> greeting);
    }
}
