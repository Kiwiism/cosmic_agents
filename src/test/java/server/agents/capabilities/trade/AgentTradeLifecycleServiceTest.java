package server.agents.capabilities.trade;

import client.Character;
import client.inventory.Item;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentTradeLifecycleServiceTest {
    @Test
    void resetRestoresClearsAndRefillsThroughCallbacks() {
        Character agent = mock(Character.class);
        Character owner = mock(Character.class);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, owner, null);
        TraceCallbacks callbacks = new TraceCallbacks();
        callbacks.owner.set(owner);

        AgentTradeStateService.initializeSequence(entry, "pots", 7, false);
        AgentPendingTradeStateRuntime.rememberRestoreSlot(
                entry,
                new Item(1002000, (short) 1, (short) 1),
                (short) -1);
        AgentTradeLifecycleService.resetTradeState(entry, agent, callbacks);

        assertSame(entry, callbacks.restoreEntry.get());
        assertSame(agent, callbacks.restoreAgent.get());
        assertSame(entry, callbacks.clearEntry.get());
        assertSame(agent, callbacks.clearAgent.get());
        assertTrue(callbacks.refilled.get());
        assertSame(owner, callbacks.refillOwner.get());
    }

    @Test
    void cancelSequenceDelegatesCancelAndReset() {
        Character agent = mock(Character.class);
        when(agent.getTrade()).thenReturn(null);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        TraceCallbacks callbacks = new TraceCallbacks();

        try (MockedStatic<AgentTradeCancellationService> cancellations = mockStatic(AgentTradeCancellationService.class)) {
            AgentTradeLifecycleService.cancelTradeSequence(entry, agent, "stop", callbacks);

            cancellations.verify(() -> AgentTradeCancellationService.cancelSequence(
                    org.mockito.ArgumentMatchers.eq(entry),
                    org.mockito.ArgumentMatchers.eq(agent),
                    org.mockito.ArgumentMatchers.eq("stop"),
                    org.mockito.ArgumentMatchers.any(Runnable.class)));
        }
    }

    @Test
    void completeTradeAndReactUsesLegacyReplyCallbacks() {
        Character agent = mock(Character.class);
        AgentTradeWindow trade = mock(AgentTradeWindow.class);
        AgentTradeWindow partner = mock(AgentTradeWindow.class);
        Item equip = new Item(1002000, (short) 1, (short) 1);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        TraceCallbacks callbacks = new TraceCallbacks();
        callbacks.delay = 900;
        callbacks.thanks = "thanks";
        callbacks.freebie = "freebie";
        callbacks.roll = 7;
        callbacks.glare = true;
        when(trade.partner()).thenReturn(partner);
        when(partner.items()).thenReturn(java.util.List.of(equip));
        when(partner.hasAnyOffer()).thenReturn(true);

        try (MockedStatic<AgentTradeCompletionService> completions = mockStatic(AgentTradeCompletionService.class)) {
            AgentTradeLifecycleService.completeTradeAndReact(entry, agent, trade, callbacks);

            completions.verify(() -> AgentTradeCompletionService.completeAndReact(
                    org.mockito.ArgumentMatchers.eq(entry),
                    org.mockito.ArgumentMatchers.eq(agent),
                    org.mockito.ArgumentMatchers.eq(java.util.List.of(equip)),
                    org.mockito.ArgumentMatchers.eq(true),
                    org.mockito.ArgumentMatchers.any(),
                    org.mockito.ArgumentMatchers.any(),
                    org.mockito.ArgumentMatchers.any(),
                    org.mockito.ArgumentMatchers.any(),
                    org.mockito.ArgumentMatchers.any()));
        }

        verify(trade).partner();
        verify(partner).items();
        verify(partner).hasAnyOffer();
    }

    @Test
    void callbackFactoryPreservesSuppliedOperations() {
        Character agent = mock(Character.class);
        Character owner = mock(Character.class);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, owner, null);
        AtomicBoolean restored = new AtomicBoolean();
        AtomicBoolean cleared = new AtomicBoolean();
        AtomicBoolean refilled = new AtomicBoolean();

        AgentTradeLifecycleService.LifecycleCallbacks callbacks = AgentTradeLifecycleService.LifecycleCallbacks.of(
                (currentEntry, currentAgent) -> restored.set(currentEntry == entry && currentAgent == agent),
                (currentEntry, currentAgent) -> cleared.set(currentEntry == entry && currentAgent == agent),
                currentEntry -> owner,
                (currentAgent, currentOwner) -> refilled.set(currentAgent == agent && currentOwner == owner),
                (min, max) -> min + max,
                () -> "thanks",
                () -> "freebie",
                () -> 42,
                () -> true);

        callbacks.restoreTemporarilyUnequippedItems(entry, agent);
        callbacks.clearManualTradeState(entry, agent);
        callbacks.refillEquipmentSlots(agent, callbacks.owner(entry));

        assertTrue(restored.get());
        assertTrue(cleared.get());
        assertTrue(refilled.get());
        assertEquals(2100, callbacks.randomReplyDelayMs(800, 1300));
        assertEquals("thanks", callbacks.tradeThanksReply());
        assertEquals("freebie", callbacks.tradeFreebieReply());
        assertEquals(42, callbacks.freebieRoll());
        assertTrue(callbacks.glareExpression());
    }

    private static final class TraceCallbacks implements AgentTradeLifecycleService.LifecycleCallbacks {
        final AtomicReference<AgentRuntimeEntry> restoreEntry = new AtomicReference<>();
        final AtomicReference<Character> restoreAgent = new AtomicReference<>();
        final AtomicReference<AgentRuntimeEntry> clearEntry = new AtomicReference<>();
        final AtomicReference<Character> clearAgent = new AtomicReference<>();
        final AtomicReference<Character> owner = new AtomicReference<>();
        final AtomicBoolean refilled = new AtomicBoolean();
        final AtomicReference<Character> refillOwner = new AtomicReference<>();
        long delay;
        String thanks;
        String freebie;
        int roll;
        boolean glare;

        @Override public void restoreTemporarilyUnequippedItems(AgentRuntimeEntry entry, Character agent) {
            restoreEntry.set(entry);
            restoreAgent.set(agent);
        }
        @Override public void clearManualTradeState(AgentRuntimeEntry entry, Character agent) {
            clearEntry.set(entry);
            clearAgent.set(agent);
        }
        @Override public Character owner(AgentRuntimeEntry entry) { return owner.get(); }
        @Override public void refillEquipmentSlots(Character agent, Character owner) {
            refilled.set(true);
            refillOwner.set(owner);
        }
        @Override public long randomReplyDelayMs(int minMs, int maxMs) { return delay; }
        @Override public String tradeThanksReply() { return thanks; }
        @Override public String tradeFreebieReply() { return freebie; }
        @Override public int freebieRoll() { return roll; }
        @Override public boolean glareExpression() { return glare; }
    }
}

