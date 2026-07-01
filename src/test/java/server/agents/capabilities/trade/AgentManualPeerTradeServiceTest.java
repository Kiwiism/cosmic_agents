package server.agents.capabilities.trade;

import client.Character;
import org.junit.jupiter.api.Test;
import server.Trade;
import server.bots.BotEntry;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentManualPeerTradeServiceTest {
    @Test
    void ownerTradeIsNotHandledByPeerService() {
        TraceCallbacks callbacks = new TraceCallbacks();

        boolean handled = AgentManualPeerTradeService.tickPeerTrade(
                entry(),
                mock(Character.class),
                mock(Character.class),
                mock(Trade.class),
                true,
                callbacks);

        assertFalse(handled);
        assertFalse(callbacks.clearedGreeting.get());
    }

    @Test
    void nonPeerTradeClearsGreetingAndStopsManualBranch() {
        Character agent = mock(Character.class);
        Character owner = owner(10);
        Trade trade = mock(Trade.class);
        Trade partner = mock(Trade.class);
        Character peer = owner(20);
        when(trade.getPartner()).thenReturn(partner);
        when(partner.getChr()).thenReturn(peer);
        TraceCallbacks callbacks = new TraceCallbacks();

        boolean handled = AgentManualPeerTradeService.tickPeerTrade(
                entry(),
                agent,
                owner,
                trade,
                false,
                callbacks);

        assertTrue(handled);
        assertTrue(callbacks.clearedGreeting.get());
    }

    @Test
    void peerTradeWaitsUntilInviteAcceptedAndFull() {
        Character agent = mock(Character.class);
        Character owner = owner(10);
        Trade trade = peerTrade(owner(20), false, false);
        TraceCallbacks callbacks = new TraceCallbacks();
        callbacks.peerAgent = true;
        callbacks.authorized = true;
        callbacks.acceptedTrade.set(trade);

        boolean handled = AgentManualPeerTradeService.tickPeerTrade(
                entry(),
                agent,
                owner,
                trade,
                false,
                callbacks);

        assertTrue(handled);
        assertSame(trade, callbacks.acceptedInputTrade.get());
        assertFalse(callbacks.completed.get());
    }

    @Test
    void peerTradeCompletesAndRefillsWhenPartnerConfirmed() {
        Character agent = mock(Character.class);
        Character owner = owner(10);
        Trade trade = peerTrade(owner(20), true, true);
        TraceCallbacks callbacks = new TraceCallbacks();
        callbacks.peerAgent = true;
        callbacks.authorized = true;

        boolean handled = AgentManualPeerTradeService.tickPeerTrade(
                entry(),
                agent,
                owner,
                trade,
                false,
                callbacks);

        assertTrue(handled);
        assertTrue(callbacks.completed.get());
        assertSame(trade, callbacks.completedTrade.get());
        assertSame(owner, callbacks.refillOwner.get());
    }

    private static BotEntry entry() {
        return new BotEntry(mock(Character.class), null, null);
    }

    private static Character owner(int id) {
        Character character = mock(Character.class);
        when(character.getId()).thenReturn(id);
        return character;
    }

    private static Trade peerTrade(Character peer, boolean full, boolean confirmed) {
        Trade trade = mock(Trade.class);
        Trade partner = mock(Trade.class);
        when(trade.getPartner()).thenReturn(partner);
        when(trade.isFullTrade()).thenReturn(full);
        when(trade.isPartnerConfirmed()).thenReturn(confirmed);
        when(partner.getChr()).thenReturn(peer);
        return trade;
    }

    private static final class TraceCallbacks implements AgentManualPeerTradeService.PeerTradeCallbacks {
        boolean peerAgent;
        boolean authorized;
        final AtomicBoolean clearedGreeting = new AtomicBoolean();
        final AtomicBoolean completed = new AtomicBoolean();
        final AtomicReference<Trade> completedTrade = new AtomicReference<>();
        final AtomicReference<Trade> acceptedTrade = new AtomicReference<>();
        final AtomicReference<Trade> acceptedInputTrade = new AtomicReference<>();
        final AtomicReference<Character> refillOwner = new AtomicReference<>();

        @Override
        public boolean isPeerAgent(Character peer) {
            return peerAgent;
        }

        @Override
        public boolean isAuthorizedPeer(int peerCharacterId, int ownerCharacterId) {
            return authorized;
        }

        @Override
        public Trade acceptInvite(Character inviter, Trade trade) {
            acceptedInputTrade.set(trade);
            return acceptedTrade.get();
        }

        @Override
        public void completeTrade(Trade trade) {
            completed.set(true);
            completedTrade.set(trade);
        }

        @Override
        public void refillEquipment(Character owner) {
            refillOwner.set(owner);
        }

        @Override
        public void clearGreeting(Character agent) {
            clearedGreeting.set(true);
        }
    }
}
