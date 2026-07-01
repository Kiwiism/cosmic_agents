package server.agents.capabilities.trade;

import client.Character;
import org.junit.jupiter.api.Test;
import server.Trade;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentManualTradeTickServiceTest {
    @Test
    void skipsWhileTransferSequenceIsActive() {
        Character agent = mock(Character.class);
        TraceCallbacks callbacks = new TraceCallbacks();
        callbacks.activeSequence = true;

        AgentManualTradeTickService.tickManualTrade(agent, mock(Character.class), callbacks);

        assertFalse(callbacks.agentTradeRead.get());
        assertFalse(callbacks.ownerTradeTicked.get());
    }

    @Test
    void clearsStateWhenNoManualTradeWindowExists() {
        Character agent = mock(Character.class);
        TraceCallbacks callbacks = new TraceCallbacks();

        AgentManualTradeTickService.tickManualTrade(agent, mock(Character.class), callbacks);

        assertTrue(callbacks.cleared.get());
        assertSame(agent, callbacks.clearedAgent.get());
    }

    @Test
    void stopsWhenTimeoutHandlerCancelsTrade() {
        Character agent = mock(Character.class);
        TraceCallbacks callbacks = new TraceCallbacks();
        callbacks.agentTrade.set(mock(Trade.class));
        callbacks.timeoutHandled = true;

        AgentManualTradeTickService.tickManualTrade(agent, mock(Character.class), callbacks);

        assertTrue(callbacks.timeoutChecked.get());
        assertFalse(callbacks.ownerTradeTicked.get());
    }

    @Test
    void waitsWhenNoOwnerIsAvailable() {
        Character agent = mock(Character.class);
        TraceCallbacks callbacks = new TraceCallbacks();
        callbacks.agentTrade.set(mock(Trade.class));

        AgentManualTradeTickService.tickManualTrade(agent, null, callbacks);

        assertTrue(callbacks.timeoutChecked.get());
        assertFalse(callbacks.ownerTradeRead.get());
        assertFalse(callbacks.ownerTradeTicked.get());
    }

    @Test
    void routesPeerTradeBeforeOwnerTrade() {
        Character agent = mock(Character.class);
        Character owner = owner(7);
        Trade trade = mock(Trade.class);
        callbacksPartnerless(trade);
        TraceCallbacks callbacks = new TraceCallbacks();
        callbacks.agentTrade.set(trade);
        callbacks.peerHandled = true;

        AgentManualTradeTickService.tickManualTrade(agent, owner, callbacks);

        assertTrue(callbacks.peerTradeTicked.get());
        assertFalse(callbacks.ownerTradeTicked.get());
    }

    @Test
    void routesOwnerTradeWhenPeerServiceDoesNotHandleOwnerWindow() {
        Character agent = mock(Character.class);
        Character owner = owner(7);
        Trade trade = mock(Trade.class);
        Trade ownerTrade = mock(Trade.class);
        when(trade.getPartner()).thenReturn(ownerTrade);
        when(ownerTrade.getPartner()).thenReturn(trade);
        when(ownerTrade.getChr()).thenReturn(owner);
        TraceCallbacks callbacks = new TraceCallbacks();
        callbacks.agentTrade.set(trade);
        callbacks.ownerTrade.set(ownerTrade);

        AgentManualTradeTickService.tickManualTrade(agent, owner, callbacks);

        assertTrue(callbacks.peerTradeTicked.get());
        assertTrue(callbacks.peerReceivedOwnerTrade.get());
        assertTrue(callbacks.ownerTradeTicked.get());
        assertSame(trade, callbacks.ownerTickTrade.get());
    }

    private static Character owner(int id) {
        Character owner = mock(Character.class);
        when(owner.getId()).thenReturn(id);
        return owner;
    }

    private static void callbacksPartnerless(Trade trade) {
        when(trade.getPartner()).thenReturn(null);
    }

    private static final class TraceCallbacks implements AgentManualTradeTickService.ManualTradeTickCallbacks {
        boolean activeSequence;
        boolean timeoutHandled;
        boolean peerHandled;
        final AtomicReference<Trade> agentTrade = new AtomicReference<>();
        final AtomicReference<Trade> ownerTrade = new AtomicReference<>();
        final AtomicBoolean agentTradeRead = new AtomicBoolean();
        final AtomicBoolean cleared = new AtomicBoolean();
        final AtomicReference<Character> clearedAgent = new AtomicReference<>();
        final AtomicBoolean timeoutChecked = new AtomicBoolean();
        final AtomicBoolean ownerTradeRead = new AtomicBoolean();
        final AtomicBoolean peerTradeTicked = new AtomicBoolean();
        final AtomicBoolean peerReceivedOwnerTrade = new AtomicBoolean();
        final AtomicBoolean ownerTradeTicked = new AtomicBoolean();
        final AtomicReference<Trade> ownerTickTrade = new AtomicReference<>();

        @Override
        public boolean hasActiveSequence() {
            return activeSequence;
        }

        @Override
        public Trade agentTrade(Character agent) {
            agentTradeRead.set(true);
            return agentTrade.get();
        }

        @Override
        public void clearState(Character agent) {
            cleared.set(true);
            clearedAgent.set(agent);
        }

        @Override
        public boolean beginOrTickTimeout(Character agent, Trade trade) {
            timeoutChecked.set(true);
            return timeoutHandled;
        }

        @Override
        public Trade ownerTrade(Character owner) {
            ownerTradeRead.set(true);
            return ownerTrade.get();
        }

        @Override
        public boolean tickPeerTrade(Character agent, Character owner, Trade trade, boolean ownerTrade) {
            peerTradeTicked.set(true);
            peerReceivedOwnerTrade.set(ownerTrade);
            return peerHandled;
        }

        @Override
        public void tickOwnerTrade(Character agent, Character owner, Trade trade) {
            ownerTradeTicked.set(true);
            ownerTickTrade.set(trade);
        }
    }
}
