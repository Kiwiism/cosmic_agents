package server.agents.capabilities.trade;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.runtime.AgentRuntimeEntry;

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
                mock(AgentTradeWindow.class),
                true,
                callbacks);

        assertFalse(handled);
        assertFalse(callbacks.clearedGreeting.get());
    }

    @Test
    void nonPeerTradeClearsGreetingAndStopsManualBranch() {
        Character agent = mock(Character.class);
        Character owner = owner(10);
        AgentTradeWindow trade = mock(AgentTradeWindow.class);
        AgentTradeWindow partner = mock(AgentTradeWindow.class);
        Character peer = owner(20);
        when(trade.partner()).thenReturn(partner);
        when(partner.character()).thenReturn(peer);
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
        AgentTradeWindow trade = peerTrade(owner(20), false, false);
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
        AgentTradeWindow trade = peerTrade(owner(20), true, true);
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

    private static AgentRuntimeEntry entry() {
        return new AgentRuntimeEntry(mock(Character.class), null, null);
    }

    private static Character owner(int id) {
        Character character = mock(Character.class);
        when(character.getId()).thenReturn(id);
        return character;
    }

    private static AgentTradeWindow peerTrade(Character peer, boolean full, boolean confirmed) {
        AgentTradeWindow trade = mock(AgentTradeWindow.class);
        AgentTradeWindow partner = mock(AgentTradeWindow.class);
        when(trade.partner()).thenReturn(partner);
        when(trade.isFullTrade()).thenReturn(full);
        when(trade.isPartnerConfirmed()).thenReturn(confirmed);
        when(partner.character()).thenReturn(peer);
        return trade;
    }

    private static final class TraceCallbacks implements AgentManualPeerTradeService.PeerTradeCallbacks {
        boolean peerAgent;
        boolean authorized;
        final AtomicBoolean clearedGreeting = new AtomicBoolean();
        final AtomicBoolean completed = new AtomicBoolean();
        final AtomicReference<AgentTradeWindow> completedTrade = new AtomicReference<>();
        final AtomicReference<AgentTradeWindow> acceptedTrade = new AtomicReference<>();
        final AtomicReference<AgentTradeWindow> acceptedInputTrade = new AtomicReference<>();
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
        public AgentTradeWindow acceptInvite(Character inviter, AgentTradeWindow trade) {
            acceptedInputTrade.set(trade);
            return acceptedTrade.get();
        }

        @Override
        public void completeTrade(AgentTradeWindow trade) {
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
