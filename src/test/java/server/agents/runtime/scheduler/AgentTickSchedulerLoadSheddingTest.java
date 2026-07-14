package server.agents.runtime.scheduler;

import client.Character;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;
import server.agents.runtime.simulation.AgentSimulationMode;
import server.agents.runtime.simulation.AgentSimulationSchedulePolicy;
import server.agents.runtime.simulation.AgentSimulationTransitionService;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentTickSchedulerLoadSheddingTest {
    @AfterEach
    void tearDown() {
        AgentRuntimeRegistry.clear();
        AgentLoadSheddingRuntime.resetForTests();
    }

    @Test
    void overloadRunsVisibleCriticalAndMailboxWorkButDefersIdleBackgroundWithoutBusyWake() {
        AtomicLong now = new AtomicLong(1_000L);
        AtomicInteger visibleTicks = new AtomicInteger();
        AtomicInteger backgroundTicks = new AtomicInteger();
        AtomicInteger criticalTicks = new AtomicInteger();
        AtomicInteger mailboxTicks = new AtomicInteger();
        AtomicInteger immediateWakes = new AtomicInteger();
        AgentTickScheduler scheduler = scheduler(now, immediateWakes);

        AgentRuntimeEntry visible = activeEntry(1, 100);
        scheduler.register(visible, visibleTicks::incrementAndGet, 50L,
                AgentWorkClass.PRESENTATION_GAMEPLAY, AgentPriorityClass.VISIBLE);
        for (int id = 101; id <= 108; id++) {
            AgentRuntimeEntry background = activeEntry(1, id);
            scheduler.register(background, backgroundTicks::incrementAndGet, 50L,
                    AgentWorkClass.BACKGROUND_GAMEPLAY, AgentPriorityClass.BACKGROUND_ACTIVE);
        }
        AgentRuntimeEntry critical = activeEntry(1, 109);
        scheduler.register(critical, criticalTicks::incrementAndGet, 50L,
                AgentWorkClass.LIFECYCLE_CRITICAL, AgentPriorityClass.CRITICAL);
        AgentRuntimeEntry mailbox = activeEntry(1, 110);
        mailbox.actionMailbox().submit(mailbox.sessionGeneration(), entry -> null);
        scheduler.register(mailbox, mailboxTicks::incrementAndGet, 50L,
                AgentWorkClass.BACKGROUND_GAMEPLAY, AgentPriorityClass.BACKGROUND_ACTIVE);

        scheduler.tickAll();

        assertEquals(1, visibleTicks.get());
        assertEquals(0, backgroundTicks.get());
        assertEquals(1, criticalTicks.get());
        assertEquals(1, mailboxTicks.get());
        assertEquals(0, immediateWakes.get());
        assertEquals(AgentLoadSheddingLevel.PAUSE_LOW_PRIORITY_BACKGROUND,
                AgentLoadSheddingRuntime.globalState().level());
    }

    private static AgentTickScheduler scheduler(AtomicLong now, AtomicInteger immediateWakes) {
        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        AgentSchedulerConfig schedulerConfig = new AgentSchedulerConfig(
                AgentSchedulerMode.CENTRAL_SEQUENTIAL,
                50L,
                true,
                250L,
                0,
                32,
                1_000L,
                32,
                40,
                10,
                2_000L,
                1,
                true,
                false,
                250L,
                5_000L,
                32,
                false,
                2,
                8);
        AgentSimulationSchedulePolicy simulationPolicy = new AgentSimulationSchedulePolicy(
                schedulerConfig,
                entry -> entry.bot().getId() == 100
                        ? AgentSimulationMode.PRESENTATION
                        : AgentSimulationMode.BACKGROUND_ACTIVE,
                new AgentSimulationTransitionService(entry -> true, entry -> true));
        AgentLoadSheddingConfig loadConfig = new AgentLoadSheddingConfig(
                true,
                1,
                2,
                1L,
                10_000L,
                1,
                95,
                100.0d,
                100.0d,
                Long.MAX_VALUE,
                2,
                2_000);
        AgentLoadSheddingController loadController = new AgentLoadSheddingController(
                0,
                loadConfig,
                new AgentDefaultLoadSheddingPolicy(),
                AgentServerHealthSnapshot::healthy);
        return new AgentTickScheduler(
                now::get,
                System::nanoTime,
                (task, period) -> future,
                (task, delay) -> {
                    immediateWakes.incrementAndGet();
                    return future;
                },
                schedulerConfig,
                0,
                simulationPolicy,
                loadController);
    }

    private static AgentRuntimeEntry activeEntry(int leaderId, int agentId) {
        Character agent = mock(Character.class);
        when(agent.getId()).thenReturn(agentId);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        AgentRuntimeRegistry.registerEntry(leaderId, entry);
        return entry;
    }
}
