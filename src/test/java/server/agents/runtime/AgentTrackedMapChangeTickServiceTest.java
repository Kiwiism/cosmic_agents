package server.agents.runtime;

import client.Character;
import org.junit.jupiter.api.Test;
import server.bots.BotEntry;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class AgentTrackedMapChangeTickServiceTest {
    @Test
    void fallsThroughWhenMapChangeHandlerFallsThrough() {
        BotEntry entry = new BotEntry(mock(Character.class), mock(Character.class), null);
        AtomicInteger calls = new AtomicInteger();

        boolean consumed = AgentTrackedMapChangeTickService.tickTrackedMapChange(
                entry,
                mock(Character.class),
                new AgentTrackedMapChangeTickService.Hooks((mapEntry, mapAgent) -> {
                    calls.incrementAndGet();
                    return false;
                }));

        assertFalse(consumed);
        assertEquals(1, calls.get());
    }

    @Test
    void consumesWhenMapChangeHandlerConsumes() {
        BotEntry entry = new BotEntry(mock(Character.class), mock(Character.class), null);

        boolean consumed = AgentTrackedMapChangeTickService.tickTrackedMapChange(
                entry,
                mock(Character.class),
                new AgentTrackedMapChangeTickService.Hooks((mapEntry, mapAgent) -> true));

        assertTrue(consumed);
    }
}
