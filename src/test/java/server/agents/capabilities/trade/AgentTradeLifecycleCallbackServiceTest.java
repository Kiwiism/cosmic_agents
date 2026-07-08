package server.agents.capabilities.trade;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class AgentTradeLifecycleCallbackServiceTest {
    @Test
    void buildsLifecycleCallbacksFromLegacyOperations() {
        Character agent = mock(Character.class);
        Character owner = mock(Character.class);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, owner, null);
        AtomicBoolean restored = new AtomicBoolean();
        AtomicBoolean cleared = new AtomicBoolean();
        AtomicBoolean refilled = new AtomicBoolean();

        AgentTradeLifecycleService.LifecycleCallbacks callbacks =
                AgentTradeLifecycleCallbackService.lifecycleCallbacks(
                        (currentEntry, currentAgent) ->
                                restored.set(currentEntry == entry && currentAgent == agent),
                        (currentEntry, currentAgent) ->
                                cleared.set(currentEntry == entry && currentAgent == agent),
                        currentEntry -> owner,
                        (currentAgent, currentOwner) ->
                                refilled.set(currentAgent == agent && currentOwner == owner),
                        (minMs, maxMs) -> minMs + maxMs,
                        () -> "thanks",
                        () -> "freebie",
                        () -> 42,
                        () -> true);

        callbacks.restoreTemporarilyUnequippedItems(entry, agent);
        callbacks.clearManualTradeState(entry, agent);
        assertSame(owner, callbacks.owner(entry));
        callbacks.refillEquipmentSlots(agent, owner);

        assertTrue(restored.get());
        assertTrue(cleared.get());
        assertTrue(refilled.get());
        assertEquals(2100, callbacks.randomReplyDelayMs(800, 1300));
        assertEquals("thanks", callbacks.tradeThanksReply());
        assertEquals("freebie", callbacks.tradeFreebieReply());
        assertEquals(42, callbacks.freebieRoll());
        assertTrue(callbacks.glareExpression());
    }
}
