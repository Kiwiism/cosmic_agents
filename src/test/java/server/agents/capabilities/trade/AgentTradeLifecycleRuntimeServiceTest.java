package server.agents.capabilities.trade;

import client.Character;
import org.junit.jupiter.api.Test;
import server.bots.BotEntry;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class AgentTradeLifecycleRuntimeServiceTest {
    @Test
    void buildsLifecycleCallbacksFromRuntimeHooks() {
        BotEntry entry = mock(BotEntry.class);
        Character agent = mock(Character.class);
        Character owner = mock(Character.class);
        AtomicInteger restored = new AtomicInteger();
        AtomicInteger cleared = new AtomicInteger();
        AtomicInteger refilled = new AtomicInteger();

        AgentTradeLifecycleService.LifecycleCallbacks callbacks =
                AgentTradeLifecycleRuntimeService.lifecycleCallbacks(
                        AgentTradeLifecycleRuntimeService.RuntimeCallbacks.of(
                                (seenEntry, seenAgent) -> restored.incrementAndGet(),
                                (seenEntry, seenAgent) -> cleared.incrementAndGet(),
                                seenEntry -> owner,
                                (seenAgent, seenOwner) -> refilled.incrementAndGet(),
                                (minMs, maxMs) -> maxMs,
                                () -> "thanks",
                                () -> "freebie"));

        callbacks.restoreTemporarilyUnequippedItems(entry, agent);
        callbacks.clearManualTradeState(entry, agent);
        callbacks.refillEquipmentSlots(agent, callbacks.owner(entry));

        assertEquals(owner, callbacks.owner(entry));
        assertEquals(1, restored.get());
        assertEquals(1, cleared.get());
        assertEquals(1, refilled.get());
        assertEquals(1300, callbacks.randomReplyDelayMs(800, 1300));
        assertEquals("thanks", callbacks.tradeThanksReply());
        assertEquals("freebie", callbacks.tradeFreebieReply());
    }
}
