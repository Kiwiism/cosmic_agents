package server.agents.runtime;

import client.Character;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class AgentTickSchedulerSoakTest {
    @AfterEach
    void tearDown() {
        AgentRuntimeRegistry.entriesByLeaderId().clear();
    }

    @Test
    void dispatchesTwentyCadencesAcrossFiveHundredSessions() {
        AtomicLong now = new AtomicLong(1_000L);
        ScheduledFuture<?> centralFuture = mock(ScheduledFuture.class);
        AgentTickScheduler scheduler = new AgentTickScheduler(now::get, (loop, period) -> centralFuture);
        AtomicInteger updates = new AtomicInteger();
        Character agent = mock(Character.class);

        for (int i = 0; i < 500; i++) {
            AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
            AgentRuntimeRegistry.mutableEntriesForLeader(1).add(entry);
            scheduler.register(entry, updates::incrementAndGet, 50L);
        }

        for (int cadence = 0; cadence < 20; cadence++) {
            scheduler.tickAll();
            now.addAndGet(50L);
        }

        assertEquals(10_000, updates.get());
    }
}
