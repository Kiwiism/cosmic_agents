package server.agents.capabilities.trade;

import client.Character;
import server.agents.runtime.AgentRuntimeEntry;

public final class AgentManualPeerTradeService {
    private AgentManualPeerTradeService() {
    }

    public static boolean tickPeerTrade(AgentRuntimeEntry entry,
                                        Character agent,
                                        Character owner,
                                        AgentTradeWindow trade,
                                        boolean ownerTrade,
                                        PeerTradeCallbacks callbacks) {
        if (ownerTrade) {
            return false;
        }

        AgentTradeWindow partner = trade.partner();
        boolean peerTrade = partner != null
                && callbacks.isPeerAgent(partner.character())
                && owner != null
                && callbacks.isAuthorizedPeer(partner.character().getId(), owner.getId());
        if (!peerTrade) {
            callbacks.clearGreeting(agent);
            return true;
        }

        if (!trade.isFullTrade()) {
            trade = callbacks.acceptInvite(partner.character(), trade);
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
        AgentTradeWindow acceptInvite(Character inviter, AgentTradeWindow trade);
        void completeTrade(AgentTradeWindow trade);
        void refillEquipment(Character owner);
        void clearGreeting(Character agent);

        static PeerTradeCallbacks of(java.util.function.Predicate<Character> isPeerAgent,
                                     java.util.function.BiPredicate<Integer, Integer> isAuthorizedPeer,
                                     java.util.function.BiFunction<Character, AgentTradeWindow, AgentTradeWindow> acceptInvite,
                                     java.util.function.Consumer<AgentTradeWindow> completeTrade,
                                     java.util.function.Consumer<Character> refillEquipment,
                                     java.util.function.Consumer<Character> clearGreeting) {
            return new PeerTradeCallbacks() {
                @Override public boolean isPeerAgent(Character peer) { return isPeerAgent.test(peer); }
                @Override public boolean isAuthorizedPeer(int peerCharacterId, int ownerCharacterId) {
                    return isAuthorizedPeer.test(peerCharacterId, ownerCharacterId);
                }
                @Override public AgentTradeWindow acceptInvite(Character inviter, AgentTradeWindow trade) {
                    return acceptInvite.apply(inviter, trade);
                }
                @Override public void completeTrade(AgentTradeWindow trade) { completeTrade.accept(trade); }
                @Override public void refillEquipment(Character owner) { refillEquipment.accept(owner); }
                @Override public void clearGreeting(Character agent) { clearGreeting.accept(agent); }
            };
        }
    }
}
