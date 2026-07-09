package server.agents.capabilities.trade;

import client.Character;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentManualOwnerTradeServiceTest {
    @Test
    void waitsWhenOwnerInviteHasNotBecomeFullTrade() {
        Character agent = mock(Character.class);
        Character owner = mock(Character.class);
        AgentTradeWindow trade = trade(false, false);
        TraceCallbacks callbacks = new TraceCallbacks();
        callbacks.acceptedTrade.set(trade);

        AgentManualOwnerTradeService.tickOwnerTrade(agent, owner, trade, callbacks);

        assertSame(owner, callbacks.acceptInviter.get());
        assertSame(trade, callbacks.acceptInputTrade.get());
        assertFalse(callbacks.greeted.get());
        assertFalse(callbacks.completed.get());
    }

    @Test
    void sendsGreetingOnceFullTradeIsAvailable() {
        Character agent = mock(Character.class);
        Character owner = mock(Character.class);
        AgentTradeWindow trade = trade(true, false);
        TraceCallbacks callbacks = new TraceCallbacks();

        AgentManualOwnerTradeService.tickOwnerTrade(agent, owner, trade, callbacks);

        assertTrue(callbacks.greeted.get());
        assertSame(agent, callbacks.greetingAgent.get());
        assertSame(trade, callbacks.greetingTrade.get());
        assertFalse(callbacks.completed.get());
    }

    @Test
    void acceptsInviteThenGreetsWhenJoinedTradeIsFull() {
        Character agent = mock(Character.class);
        Character owner = mock(Character.class);
        AgentTradeWindow pendingTrade = trade(false, false);
        AgentTradeWindow joinedTrade = trade(true, false);
        TraceCallbacks callbacks = new TraceCallbacks();
        callbacks.acceptedTrade.set(joinedTrade);

        AgentManualOwnerTradeService.tickOwnerTrade(agent, owner, pendingTrade, callbacks);

        assertTrue(callbacks.greeted.get());
        assertSame(joinedTrade, callbacks.greetingTrade.get());
        assertFalse(callbacks.completed.get());
    }

    @Test
    void completesAndRefillsWhenOwnerConfirmed() {
        Character agent = mock(Character.class);
        Character owner = mock(Character.class);
        AgentTradeWindow trade = trade(true, true);
        TraceCallbacks callbacks = new TraceCallbacks();

        AgentManualOwnerTradeService.tickOwnerTrade(agent, owner, trade, callbacks);

        assertTrue(callbacks.completed.get());
        assertSame(trade, callbacks.completedTrade.get());
        assertSame(owner, callbacks.refillOwner.get());
    }

    private static AgentTradeWindow trade(boolean full, boolean partnerConfirmed) {
        AgentTradeWindow trade = mock(AgentTradeWindow.class);
        when(trade.isFullTrade()).thenReturn(full);
        when(trade.isPartnerConfirmed()).thenReturn(partnerConfirmed);
        return trade;
    }

    private static final class TraceCallbacks implements AgentManualOwnerTradeService.OwnerTradeCallbacks {
        final AtomicReference<AgentTradeWindow> acceptedTrade = new AtomicReference<>();
        final AtomicReference<Character> acceptInviter = new AtomicReference<>();
        final AtomicReference<AgentTradeWindow> acceptInputTrade = new AtomicReference<>();
        final AtomicBoolean greeted = new AtomicBoolean();
        final AtomicReference<Character> greetingAgent = new AtomicReference<>();
        final AtomicReference<AgentTradeWindow> greetingTrade = new AtomicReference<>();
        final AtomicBoolean completed = new AtomicBoolean();
        final AtomicReference<AgentTradeWindow> completedTrade = new AtomicReference<>();
        final AtomicReference<Character> refillOwner = new AtomicReference<>();

        @Override
        public AgentTradeWindow acceptInvite(Character inviter, AgentTradeWindow trade) {
            acceptInviter.set(inviter);
            acceptInputTrade.set(trade);
            return acceptedTrade.get();
        }

        @Override
        public void sendGreeting(Character agent, AgentTradeWindow trade, Supplier<String> greeting) {
            greeted.set(true);
            greetingAgent.set(agent);
            greetingTrade.set(trade);
            greeting.get();
        }

        @Override
        public Supplier<String> manualTradeGreeting() {
            return () -> "hi";
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
    }
}
