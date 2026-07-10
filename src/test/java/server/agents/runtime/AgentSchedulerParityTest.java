package server.agents.runtime;

import client.Character;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class AgentSchedulerParityTest {
    @AfterEach
    void tearDown() {
        AgentRuntimeRegistry.entriesByLeaderId().clear();
    }

    @Test
    void legacyAndCentralPathsInvokeTheSameTickCallbackPerCadence() {
        List<Integer> legacy = new ArrayList<>();
        AtomicReference<Runnable> legacyTick = new AtomicReference<>();
        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        AgentRuntimeEntry legacyEntry = new AgentRuntimeEntry(mock(Character.class), null, null);
        AgentTickSchedulingService.register(
                legacyEntry,
                () -> legacy.add(1),
                50L,
                (tick, period) -> {
                    legacyTick.set(tick);
                    return future;
                });
        legacyTick.get().run();
        legacyTick.get().run();
        legacyTick.get().run();

        List<Integer> central = new ArrayList<>();
        AtomicLong now = new AtomicLong(1_000L);
        AgentTickScheduler scheduler = new AgentTickScheduler(now::get, (loop, period) -> future);
        AgentRuntimeEntry centralEntry = new AgentRuntimeEntry(mock(Character.class), null, null);
        AgentRuntimeRegistry.mutableEntriesForLeader(1).add(centralEntry);
        scheduler.register(centralEntry, () -> central.add(1), 50L);
        for (int i = 0; i < 3; i++) {
            scheduler.tickAll();
            now.addAndGet(50L);
        }

        assertEquals(legacy, central);
    }
}
