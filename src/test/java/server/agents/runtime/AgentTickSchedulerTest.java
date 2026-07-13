package server.agents.runtime;

import client.Character;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import server.agents.runtime.scheduler.AgentSchedulerConfig;
import server.agents.runtime.scheduler.AgentSchedulerMode;
import server.agents.runtime.scheduler.AgentScheduleHandle;
import server.agents.runtime.scheduler.AgentTickScheduler;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AgentTickSchedulerTest {
    private final AtomicLong now = new AtomicLong(1_000L);
    private final AtomicReference<Runnable> centralLoop = new AtomicReference<>();
    private final AtomicReference<Runnable> wakeTask = new AtomicReference<>();
    private final AtomicInteger wakeSchedules = new AtomicInteger();
    private ScheduledFuture<?> centralFuture;
    private AgentTickScheduler scheduler;

    @BeforeEach
    void setUp() {
        clearProperties();
        AgentRuntimeRegistry.clear();
        centralFuture = mock(ScheduledFuture.class);
        scheduler = scheduler();
    }

    @AfterEach
    void tearDown() {
        clearProperties();
        AgentRuntimeRegistry.clear();
    }

    @Test
    void ticksActiveAgentsInStableRegistrationOrder() {
        AgentRuntimeEntry first = activeEntry(1, 101);
        AgentRuntimeEntry second = activeEntry(1, 102);
        List<Integer> order = new ArrayList<>();
        scheduler.register(first, () -> order.add(101), 50L);
        scheduler.register(second, () -> order.add(102), 50L);

        centralLoop.get().run();

        assertEquals(List.of(101, 102), order);
    }

    @Test
    void skipsPausedRemovedDespawningAndInvalidAgents() {
        AgentRuntimeEntry paused = activeEntry(1, 101);
        AgentRuntimeEntry removed = entry(102);
        AgentRuntimeEntry despawning = activeEntry(1, 103);
        AgentRuntimeEntry invalid = new AgentRuntimeEntry(null, null, null);
        AgentRuntimeRegistry.registerEntry(1, invalid);
        AtomicInteger ticks = new AtomicInteger();
        scheduler.register(paused, ticks::incrementAndGet, 50L);
        scheduler.pause(paused);
        scheduler.register(removed, ticks::incrementAndGet, 50L);
        scheduler.register(despawning, ticks::incrementAndGet, 50L);
        despawning.actionMailbox().close();
        scheduler.register(invalid, ticks::incrementAndGet, 50L);

        scheduler.tickAll();

        assertEquals(0, ticks.get());
    }

    @Test
    void oneFailingAgentDoesNotStopOtherAgents() {
        AgentRuntimeEntry failing = activeEntry(1, 101);
        AgentRuntimeEntry healthy = activeEntry(1, 102);
        AtomicInteger healthyTicks = new AtomicInteger();
        scheduler.register(failing, () -> { throw new IllegalStateException("boom"); }, 50L);
        scheduler.register(healthy, healthyTicks::incrementAndGet, 50L);

        scheduler.tickAll();

        assertEquals(1, healthyTicks.get());
    }

    @Test
    void cadenceSkipsUntilRegistrationIsDueAgain() {
        AgentRuntimeEntry entry = activeEntry(1, 101);
        AtomicInteger ticks = new AtomicInteger();
        scheduler.register(entry, ticks::incrementAndGet, 50L);

        scheduler.tickAll();
        now.addAndGet(49L);
        scheduler.tickAll();
        now.incrementAndGet();
        scheduler.tickAll();

        assertEquals(2, ticks.get());
    }

    @Test
    void cappedCyclesContinueWithNextDueAgent() {
        System.setProperty("agents.scheduler.maxAgentsPerTick", "1");
        scheduler = scheduler();
        AgentRuntimeEntry first = activeEntry(1, 101);
        AgentRuntimeEntry second = activeEntry(1, 102);
        List<Integer> order = new ArrayList<>();
        scheduler.register(first, () -> order.add(101), 50L);
        scheduler.register(second, () -> order.add(102), 50L);

        scheduler.tickAll();
        scheduler.tickAll();

        assertEquals(List.of(101, 102), order);
    }

    @Test
    void cancellationUnregistersAgentAndStopsEmptyCentralLoop() {
        AgentRuntimeEntry entry = activeEntry(1, 101);
        ScheduledFuture<?> handle = scheduler.register(entry, () -> { }, 50L);

        assertEquals(1, scheduler.registrationCount());
        assertTrue(handle.cancel(false));
        assertEquals(0, scheduler.registrationCount());
        scheduler.tickAll();
        verify(centralFuture).cancel(false);
    }

    @Test
    void cancellationCompletesScheduleHandleWithoutPolling() throws Exception {
        AgentRuntimeEntry entry = activeEntry(1, 101);
        AgentScheduleHandle handle = scheduler.register(entry, () -> { }, 50L);

        assertThrows(TimeoutException.class, () -> handle.get(1L, TimeUnit.MILLISECONDS));
        assertTrue(handle.cancel(false));
        assertNull(handle.get(1L, TimeUnit.MILLISECONDS));
    }

    @Test
    void centralSchedulerIsDisabledByDefault() {
        assertEquals(AgentSchedulerMode.LEGACY_PER_AGENT, AgentSchedulerConfig.fromSystemProperties().mode());
        System.setProperty("agents.scheduler.central.enabled", "true");
        assertEquals(AgentSchedulerMode.CENTRAL_SEQUENTIAL, AgentSchedulerConfig.fromSystemProperties().mode());
        System.setProperty("agents.scheduler.mode", "central-sharded");
        assertEquals(AgentSchedulerMode.CENTRAL_SHARDED, AgentSchedulerConfig.fromSystemProperties().mode());
    }

    @Test
    void wakeMakesAgentImmediatelyDueAndCoalescesPendingWakeTask() {
        AgentRuntimeEntry entry = activeEntry(1, 101);
        AtomicInteger ticks = new AtomicInteger();
        AgentScheduleHandle handle = scheduler.register(entry, ticks::incrementAndGet, 50L);
        scheduler.tickAll();
        now.addAndGet(10L);

        assertTrue(handle.wake());
        assertTrue(handle.wake());
        assertEquals(1, wakeSchedules.get());

        wakeTask.get().run();

        assertEquals(2, ticks.get());
    }

    @Test
    void missedCadencesCoalesceWithoutReplayStorm() {
        AgentRuntimeEntry entry = activeEntry(1, 101);
        AtomicInteger ticks = new AtomicInteger();
        scheduler.register(entry, ticks::incrementAndGet, 50L);

        scheduler.tickAll();
        now.set(10_000L);
        scheduler.tickAll();
        scheduler.tickAll();

        assertEquals(2, ticks.get());
        now.addAndGet(50L);
        scheduler.tickAll();
        assertEquals(3, ticks.get());
    }

    private AgentTickScheduler scheduler() {
        return new AgentTickScheduler(
                now::get,
                (loop, period) -> {
                    centralLoop.set(loop);
                    return centralFuture;
                },
                (task, delay) -> {
                    wakeSchedules.incrementAndGet();
                    wakeTask.set(task);
                    return mock(ScheduledFuture.class);
                });
    }

    private AgentRuntimeEntry activeEntry(int leaderId, int agentId) {
        AgentRuntimeEntry entry = entry(agentId);
        AgentRuntimeRegistry.registerEntry(leaderId, entry);
        return entry;
    }

    private AgentRuntimeEntry entry(int agentId) {
        Character agent = mock(Character.class);
        org.mockito.Mockito.when(agent.getId()).thenReturn(agentId);
        return new AgentRuntimeEntry(agent, null, null);
    }

    private static void clearProperties() {
        System.clearProperty("agents.scheduler.central.enabled");
        System.clearProperty("agents.scheduler.mode");
        System.clearProperty("agents.scheduler.maxAgentsPerTick");
        System.clearProperty("agents.scheduler.baseTickMs");
        System.clearProperty("agents.scheduler.logSlowTicks");
        System.clearProperty("agents.scheduler.slowTickMs");
        System.clearProperty("agents.scheduler.ingressCapacityPerShard");
    }
}
