package server.agents.capabilities.trade;

import client.Character;

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

        AgentTradeWindow trade = callbacks.agentTrade(agent);
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

        AgentTradeWindow ownerTrade = callbacks.ownerTrade(owner);
        AgentTradeWindow partner = trade.partner();
        boolean ownerTradeActive = ownerTrade != null
                && partner != null
                && partner.identity() == ownerTrade.identity()
                && ownerTrade.partner() != null
                && ownerTrade.partner().identity() == trade.identity()
                && owner.getId() == ownerTrade.character().getId();
        if (callbacks.tickPeerTrade(agent, owner, trade, ownerTradeActive)) {
            return;
        }

        callbacks.tickOwnerTrade(agent, owner, trade);
    }

    public interface ManualTradeTickCallbacks {
        boolean hasActiveSequence();
        AgentTradeWindow agentTrade(Character agent);
        void clearState(Character agent);
        boolean beginOrTickTimeout(Character agent, AgentTradeWindow trade);
        AgentTradeWindow ownerTrade(Character owner);
        boolean tickPeerTrade(Character agent, Character owner, AgentTradeWindow trade, boolean ownerTrade);
        void tickOwnerTrade(Character agent, Character owner, AgentTradeWindow trade);

        static ManualTradeTickCallbacks of(BooleanSupplier hasActiveSequence,
                                          Function<Character, AgentTradeWindow> agentTrade,
                                          Consumer<Character> clearState,
                                          BiPredicate<Character, AgentTradeWindow> beginOrTickTimeout,
                                          Function<Character, AgentTradeWindow> ownerTrade,
                                          PeerTradeTicker tickPeerTrade,
                                          OwnerTradeTicker tickOwnerTrade) {
            return new ManualTradeTickCallbacks() {
                @Override
                public boolean hasActiveSequence() {
                    return hasActiveSequence.getAsBoolean();
                }

                @Override
                public AgentTradeWindow agentTrade(Character agent) {
                    return agentTrade.apply(agent);
                }

                @Override
                public void clearState(Character agent) {
                    clearState.accept(agent);
                }

                @Override
                public boolean beginOrTickTimeout(Character agent, AgentTradeWindow trade) {
                    return beginOrTickTimeout.test(agent, trade);
                }

                @Override
                public AgentTradeWindow ownerTrade(Character owner) {
                    return ownerTrade.apply(owner);
                }

                @Override
                public boolean tickPeerTrade(Character agent, Character owner, AgentTradeWindow trade, boolean ownerTrade) {
                    return tickPeerTrade.tick(agent, owner, trade, ownerTrade);
                }

                @Override
                public void tickOwnerTrade(Character agent, Character owner, AgentTradeWindow trade) {
                    tickOwnerTrade.accept(agent, owner, trade);
                }
            };
        }
    }

    @FunctionalInterface
    public interface PeerTradeTicker {
        boolean tick(Character agent, Character owner, AgentTradeWindow trade, boolean ownerTrade);
    }

    @FunctionalInterface
    public interface OwnerTradeTicker {
        void accept(Character agent, Character owner, AgentTradeWindow trade);
    }
}
