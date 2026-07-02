package server.agents.runtime;

import client.Character;
import org.junit.jupiter.api.Test;
import server.bots.BotEntry;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class AgentIdleModeTickServiceTest {
    @Test
    void returnsFalseWhenIdleTickFallsThrough() {
        BotEntry entry = new BotEntry(mock(Character.class), mock(Character.class), null);
        Character agent = mock(Character.class);
        AtomicInteger idleTicks = new AtomicInteger();

        boolean consumed = AgentIdleModeTickService.tickIdleMode(
                entry,
                agent,
                new AgentIdleModeTickService.Hooks((tickEntry, tickAgent) -> {
                    idleTicks.incrementAndGet();
                    return false;
                }));

        assertFalse(consumed);
        assertEquals(1, idleTicks.get());
    }

    @Test
    void returnsTrueWhenIdleTickConsumes() {
        BotEntry entry = new BotEntry(mock(Character.class), mock(Character.class), null);
        Character agent = mock(Character.class);

        boolean consumed = AgentIdleModeTickService.tickIdleMode(
                entry,
                agent,
                new AgentIdleModeTickService.Hooks((tickEntry, tickAgent) -> true));

        assertTrue(consumed);
    }
}
