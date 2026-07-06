package server.agents.runtime;

import client.Character;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class AgentCommandModeServiceTest {
    @Test
    void skipsWhenEntryIsMissing() {
        AtomicInteger calls = new AtomicInteger();

        AgentCommandModeService.runPreparedModeCommand(
                null, calls::incrementAndGet, calls::incrementAndGet, calls::incrementAndGet);

        assertEquals(0, calls.get());
    }

    @Test
    void clearsTasksCancelsShopThenStartsMode() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(mock(Character.class), mock(Character.class), null);
        AtomicInteger order = new AtomicInteger();

        AgentCommandModeService.runPreparedModeCommand(
                entry,
                () -> assertEquals(0, order.getAndIncrement()),
                () -> assertEquals(1, order.getAndIncrement()),
                () -> assertEquals(2, order.getAndIncrement()));

        assertEquals(3, order.get());
    }

    @Test
    void skipsWhenGuardFails() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(mock(Character.class), mock(Character.class), null);
        AtomicInteger calls = new AtomicInteger();

        AgentCommandModeService.runPreparedModeCommand(
                entry,
                () -> false,
                calls::incrementAndGet,
                calls::incrementAndGet,
                calls::incrementAndGet);

        assertEquals(0, calls.get());
    }

    @Test
    void evaluatesGuardBeforeClearingTasks() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(mock(Character.class), mock(Character.class), null);
        AtomicInteger order = new AtomicInteger();

        AgentCommandModeService.runPreparedModeCommand(
                entry,
                () -> {
                    assertEquals(0, order.getAndIncrement());
                    return true;
                },
                () -> assertEquals(1, order.getAndIncrement()),
                () -> assertEquals(2, order.getAndIncrement()),
                () -> assertEquals(3, order.getAndIncrement()));

        assertEquals(4, order.get());
    }
}
