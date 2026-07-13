package server.agents.runtime.scheduler;

import client.Character;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;
import server.agents.runtime.simulation.AgentSimulationMode;
import server.agents.runtime.simulation.AgentSimulationPolicy;
import server.agents.runtime.simulation.AgentSimulationSchedulePolicy;
import server.agents.runtime.simulation.AgentSimulationTransitionService;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentTickSchedulerSimulationTest {
    @AfterEach
    void tearDown() {
        AgentRuntimeRegistry.clear();
    }

    @Test
    void presentationModePreservesOriginalCadence() {
        AtomicLong now = new AtomicLong(1_000L);
        AtomicInteger updates = new AtomicInteger();
        AgentTickScheduler scheduler = scheduler(now, new AtomicReference<>(AgentSimulationMode.PRESENTATION));
        scheduler.register(activeEntry(1, 101), updates::incrementAndGet, 50L);

        scheduler.tickAll();
        now.addAndGet(50L);
        scheduler.tickAll();

        assertEquals(2, updates.get());
    }

    @Test
    void backgroundActiveRunsTheSameTickAtReducedCadence() {
        AtomicLong now = new AtomicLong(1_000L);
        AtomicInteger updates = new AtomicInteger();
        AgentTickScheduler scheduler = scheduler(now, new AtomicReference<>(AgentSimulationMode.BACKGROUND_ACTIVE));
        scheduler.register(activeEntry(1, 101), updates::incrementAndGet, 50L);

        scheduler.tickAll();
        now.addAndGet(50L);
        scheduler.tickAll();
        assertEquals(1, updates.get());

        now.addAndGet(200L);
        scheduler.tickAll();
        assertEquals(2, updates.get());
    }

    @Test
    void wakeReevaluatesModeAndRestoresPresentationCadence() {
        AtomicLong now = new AtomicLong(1_000L);
        AtomicInteger updates = new AtomicInteger();
        AtomicReference<AgentSimulationMode> mode = new AtomicReference<>(AgentSimulationMode.BACKGROUND_ACTIVE);
        AgentTickScheduler scheduler = scheduler(now, mode);
        AgentScheduleHandle handle = scheduler.register(activeEntry(1, 101), updates::incrementAndGet, 50L);
        scheduler.tickAll();

        now.addAndGet(50L);
        mode.set(AgentSimulationMode.PRESENTATION);
        handle.wake();
        scheduler.tickAll();

        assertEquals(2, updates.get());
    }

    @Test
    void oneFailingSimulationPolicyDoesNotStopOtherAgents() {
        AtomicLong now = new AtomicLong(1_000L);
        AtomicInteger updates = new AtomicInteger();
        AgentSimulationPolicy policy = entry -> {
            if (entry.bot().getId() == 101) {
                throw new IllegalStateException("broken policy");
            }
            return AgentSimulationMode.PRESENTATION;
        };
        AgentTickScheduler scheduler = scheduler(now, policy);
        scheduler.register(activeEntry(1, 101), updates::incrementAndGet, 50L);
        scheduler.register(activeEntry(1, 102), updates::incrementAndGet, 50L);

        scheduler.tickAll();

        assertEquals(2, updates.get());
    }

    @Test
    void backgroundMapBudgetLetsDifferentMapsProgressInOneCycle() {
        AtomicLong now = new AtomicLong(1_000L);
        AtomicInteger updates = new AtomicInteger();
        AgentTickScheduler scheduler = scheduler(
                now,
                entry -> AgentSimulationMode.BACKGROUND_ACTIVE,
                1);
        scheduler.register(activeEntry(1, 101, 10), updates::incrementAndGet, 50L);
        scheduler.register(activeEntry(1, 102, 10), updates::incrementAndGet, 50L);
        scheduler.register(activeEntry(1, 103, 20), updates::incrementAndGet, 50L);
        scheduler.register(activeEntry(1, 104, 20), updates::incrementAndGet, 50L);

        scheduler.tickAll();

        assertEquals(2, updates.get());
        assertEquals(2, scheduler.readyRegistrationCount());

        scheduler.tickAll();
        assertEquals(4, updates.get());
    }

    private static AgentTickScheduler scheduler(AtomicLong now,
                                                AtomicReference<AgentSimulationMode> mode) {
        return scheduler(now, entry -> mode.get());
    }

    private static AgentTickScheduler scheduler(AtomicLong now, AgentSimulationPolicy simulationPolicy) {
        return scheduler(now, simulationPolicy, 32);
    }

    private static AgentTickScheduler scheduler(AtomicLong now,
                                                AgentSimulationPolicy simulationPolicy,
                                                int backgroundMapLimit) {
        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        AgentSchedulerConfig config = new AgentSchedulerConfig(
                AgentSchedulerMode.CENTRAL_SEQUENTIAL,
                50L,
                true,
                250L,
                0,
                8,
                1_000L,
                8,
                40,
                10,
                2_000L,
                1,
                true,
                false,
                250L,
                5_000L,
                backgroundMapLimit);
        AgentSimulationSchedulePolicy policy = new AgentSimulationSchedulePolicy(
                config,
                simulationPolicy,
                new AgentSimulationTransitionService(entry -> true, entry -> true));
        return new AgentTickScheduler(
                now::get,
                System::nanoTime,
                (task, period) -> future,
                (task, delay) -> future,
                config,
                0,
                policy);
    }

    private static AgentRuntimeEntry activeEntry(int leaderId, int agentId) {
        return activeEntry(leaderId, agentId, 0);
    }

    private static AgentRuntimeEntry activeEntry(int leaderId, int agentId, int mapId) {
        Character agent = mock(Character.class);
        when(agent.getId()).thenReturn(agentId);
        when(agent.getMapId()).thenReturn(mapId);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        AgentRuntimeRegistry.registerEntry(leaderId, entry);
        return entry;
    }
}
