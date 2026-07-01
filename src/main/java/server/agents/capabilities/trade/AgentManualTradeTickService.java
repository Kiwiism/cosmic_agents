package server.agents.capabilities.trade;

import client.Character;
import server.Trade;

import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;

public final class AgentManualTradeTickService {
    private AgentManualTradeTickService() {
    }

    public static void tickManualTrade(Character agent,
                                       Character owner,
                                       ManualTradeTickCallbacks callbacks) {
        if (callbacks.hasActiveSequence()) {
            return;
        }

        Trade trade = callbacks.agentTrade(agent);
        if (trade == null) {
            callbacks.clearState(agent);
            return;
        }

        if (callbacks.beginOrTickTimeout(agent, trade)) {
            return;
        }

        if (owner == null) {
            return;
        }

        Trade ownerTrade = callbacks.ownerTrade(owner);
        Trade partner = trade.getPartner();
        boolean ownerTradeActive = ownerTrade != null
                && partner == ownerTrade
                && ownerTrade.getPartner() == trade
                && owner.getId() == ownerTrade.getChr().getId();
        if (callbacks.tickPeerTrade(agent, owner, trade, ownerTradeActive)) {
            return;
        }

        callbacks.tickOwnerTrade(agent, owner, trade);
    }

    public interface ManualTradeTickCallbacks {
        boolean hasActiveSequence();
        Trade agentTrade(Character agent);
        void clearState(Character agent);
        boolean beginOrTickTimeout(Character agent, Trade trade);
        Trade ownerTrade(Character owner);
        boolean tickPeerTrade(Character agent, Character owner, Trade trade, boolean ownerTrade);
        void tickOwnerTrade(Character agent, Character owner, Trade trade);

        static ManualTradeTickCallbacks of(BooleanSupplier hasActiveSequence,
                                          Function<Character, Trade> agentTrade,
                                          Consumer<Character> clearState,
                                          BiPredicate<Character, Trade> beginOrTickTimeout,
                                          Function<Character, Trade> ownerTrade,
                                          PeerTradeTicker tickPeerTrade,
                                          OwnerTradeTicker tickOwnerTrade) {
            return new ManualTradeTickCallbacks() {
                @Override
                public boolean hasActiveSequence() {
                    return hasActiveSequence.getAsBoolean();
                }

                @Override
                public Trade agentTrade(Character agent) {
                    return agentTrade.apply(agent);
                }

                @Override
                public void clearState(Character agent) {
                    clearState.accept(agent);
                }

                @Override
                public boolean beginOrTickTimeout(Character agent, Trade trade) {
                    return beginOrTickTimeout.test(agent, trade);
                }

                @Override
                public Trade ownerTrade(Character owner) {
                    return ownerTrade.apply(owner);
                }

                @Override
                public boolean tickPeerTrade(Character agent, Character owner, Trade trade, boolean ownerTrade) {
                    return tickPeerTrade.tick(agent, owner, trade, ownerTrade);
                }

                @Override
                public void tickOwnerTrade(Character agent, Character owner, Trade trade) {
                    tickOwnerTrade.accept(agent, owner, trade);
                }
            };
        }
    }

    @FunctionalInterface
    public interface PeerTradeTicker {
        boolean tick(Character agent, Character owner, Trade trade, boolean ownerTrade);
    }

    @FunctionalInterface
    public interface OwnerTradeTicker {
        void accept(Character agent, Character owner, Trade trade);
    }
}
