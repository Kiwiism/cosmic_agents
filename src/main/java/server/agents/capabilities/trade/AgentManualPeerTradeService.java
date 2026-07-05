package server.agents.capabilities.trade;

import client.Character;
import server.Trade;
import server.agents.runtime.AgentRuntimeEntry;

public final class AgentManualPeerTradeService {
    private AgentManualPeerTradeService() {
    }

    public static boolean tickPeerTrade(AgentRuntimeEntry entry,
                                        Character agent,
                                        Character owner,
                                        Trade trade,
                                        boolean ownerTrade,
                                        PeerTradeCallbacks callbacks) {
        if (ownerTrade) {
            return false;
        }

        Trade partner = trade.getPartner();
        boolean peerTrade = partner != null
                && callbacks.isPeerAgent(partner.getChr())
                && owner != null
                && callbacks.isAuthorizedPeer(partner.getChr().getId(), owner.getId());
        if (!peerTrade) {
            callbacks.clearGreeting(agent);
            return true;
        }

        if (!trade.isFullTrade()) {
            trade = callbacks.acceptInvite(partner.getChr(), trade);
            if (trade == null || !trade.isFullTrade()) {
                return true;
            }
        }

        if (trade.isPartnerConfirmed()) {
            callbacks.completeTrade(trade);
            callbacks.refillEquipment(owner);
        }
        return true;
    }

    public interface PeerTradeCallbacks {
        boolean isPeerAgent(Character peer);
        boolean isAuthorizedPeer(int peerCharacterId, int ownerCharacterId);
        Trade acceptInvite(Character inviter, Trade trade);
        void completeTrade(Trade trade);
        void refillEquipment(Character owner);
        void clearGreeting(Character agent);

        static PeerTradeCallbacks of(java.util.function.Predicate<Character> isPeerAgent,
                                     java.util.function.BiPredicate<Integer, Integer> isAuthorizedPeer,
                                     java.util.function.BiFunction<Character, Trade, Trade> acceptInvite,
                                     java.util.function.Consumer<Trade> completeTrade,
                                     java.util.function.Consumer<Character> refillEquipment,
                                     java.util.function.Consumer<Character> clearGreeting) {
            return new PeerTradeCallbacks() {
                @Override public boolean isPeerAgent(Character peer) { return isPeerAgent.test(peer); }
                @Override public boolean isAuthorizedPeer(int peerCharacterId, int ownerCharacterId) {
                    return isAuthorizedPeer.test(peerCharacterId, ownerCharacterId);
                }
                @Override public Trade acceptInvite(Character inviter, Trade trade) {
                    return acceptInvite.apply(inviter, trade);
                }
                @Override public void completeTrade(Trade trade) { completeTrade.accept(trade); }
                @Override public void refillEquipment(Character owner) { refillEquipment.accept(owner); }
                @Override public void clearGreeting(Character agent) { clearGreeting.accept(agent); }
            };
        }
    }
}
