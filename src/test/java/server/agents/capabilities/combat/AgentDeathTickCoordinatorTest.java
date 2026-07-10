package server.agents.capabilities.combat;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class AgentDeathTickCoordinatorTest {
    @Test
    void returnsFalseWhenAgentIsAliveAndDoesNotNeedDeadState() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(mock(Character.class), mock(Character.class), null);
        AtomicInteger deadEntries = new AtomicInteger();
        AtomicInteger respawns = new AtomicInteger();

        boolean consumed = AgentDeathTickCoordinator.handleDeadTick(
                entry,
                entry.bot(),
                () -> false,
                (deadEntry, deadAgent) -> deadEntries.incrementAndGet(),
                respawns::incrementAndGet,
                () -> 1_000L);

        assertFalse(consumed);
        assertEquals(0, deadEntries.get());
        assertEquals(0, respawns.get());
    }

    @Test
    void delegatesDeadStateEntryAndRespawnActionThroughService() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(mock(Character.class), mock(Character.class), null);
        AtomicInteger deadEntries = new AtomicInteger();
        AtomicInteger respawns = new AtomicInteger();

        boolean consumed = AgentDeathTickCoordinator.handleDeadTick(
                entry,
                entry.bot(),
                () -> true,
                (deadEntry, deadAgent) -> {
                    deadEntries.incrementAndGet();
                    AgentDeathStateRuntime.enterDeadState(deadEntry, 1_000L, 500L);
                },
                respawns::incrementAndGet,
                () -> 1_500L);

        assertTrue(consumed);
        assertEquals(1, deadEntries.get());
        assertEquals(1, respawns.get());
    }
}
