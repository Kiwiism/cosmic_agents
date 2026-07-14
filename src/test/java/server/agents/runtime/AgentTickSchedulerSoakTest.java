package server.agents.runtime;

import client.Character;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import server.agents.runtime.scheduler.AgentTickScheduler;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentTickSchedulerSoakTest {
    @AfterEach
    void tearDown() {
        System.clearProperty("agents.scheduler.central.enabled");
        System.clearProperty("agents.scheduler.maxWorkItemsPerCycle");
        System.clearProperty("agents.scheduler.cycleBudgetMs");
        AgentRuntimeRegistry.clear();
    }

    @ParameterizedTest
    @ValueSource(ints = {50, 100, 250, 500, 1_000, 1_500, 2_000})
    void centralSequentialDispatchesTwentyCadencesAcrossBaselinePopulations(int population) {
        System.setProperty("agents.scheduler.maxWorkItemsPerCycle", "4096");
        System.setProperty("agents.scheduler.cycleBudgetMs", "60000");
        AtomicLong now = new AtomicLong(1_000L);
        ScheduledFuture<?> centralFuture = mock(ScheduledFuture.class);
        AgentTickScheduler scheduler = new AgentTickScheduler(now::get, (loop, period) -> centralFuture);
        AtomicInteger updates = new AtomicInteger();

        for (int i = 0; i < population; i++) {
            Character agent = mock(Character.class);
            when(agent.getId()).thenReturn(i + 1);
            AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
            AgentRuntimeRegistry.registerEntry(1, entry);
            scheduler.register(entry, updates::incrementAndGet, 50L);
        }

        for (int cadence = 0; cadence < 20; cadence++) {
            scheduler.tickAll();
            now.addAndGet(50L);
        }

        assertEquals(population * 20, updates.get());
    }

    @ParameterizedTest
    @ValueSource(ints = {50, 100, 250, 500})
    void legacyDispatchesTwentyCadencesAcrossBaselinePopulations(int population) {
        List<Runnable> scheduledTicks = new ArrayList<>(population);
        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        AtomicInteger updates = new AtomicInteger();
        Character agent = mock(Character.class);

        for (int i = 0; i < population; i++) {
            AgentTickSchedulingService.register(
                    new AgentRuntimeEntry(agent, null, null),
                    updates::incrementAndGet,
                    50L,
                    (tick, periodMs) -> {
                        scheduledTicks.add(tick);
                        return future;
                    });
        }

        for (int cadence = 0; cadence < 20; cadence++) {
            scheduledTicks.forEach(Runnable::run);
        }

        assertEquals(population * 20, updates.get());
    }
}
