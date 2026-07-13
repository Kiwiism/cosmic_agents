package server.agents.runtime.scheduler;

import client.Character;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentTickSchedulerHeapTest {
    @AfterEach
    void tearDown() {
        AgentRuntimeRegistry.clear();
    }

    @Test
    void drainsIngressIntoHeapAndKeepsStableEqualDueOrder() {
        AtomicLong now = new AtomicLong(1_000L);
        AgentTickScheduler scheduler = scheduler(now, 8, 0);
        List<Integer> order = new ArrayList<>();
        scheduler.register(activeEntry(1, 101), () -> order.add(101), 50L);
        scheduler.register(activeEntry(1, 102), () -> order.add(102), 50L);

        assertEquals(2, scheduler.ingressDepth());
        assertEquals(0, scheduler.scheduledRegistrationCount());

        scheduler.tickAll();

        assertEquals(List.of(101, 102), order);
        assertEquals(0, scheduler.ingressDepth());
        assertEquals(2, scheduler.scheduledRegistrationCount());
        assertEquals(2, scheduler.ingressHighWaterMark());
    }

    @Test
    void cancellationDrainsAllSchedulerOwnedState() {
        AtomicLong now = new AtomicLong(1_000L);
        AgentTickScheduler scheduler = scheduler(now, 8, 0);
        AgentScheduleHandle handle = scheduler.register(activeEntry(1, 101), () -> { }, 50L);
        scheduler.tickAll();

        handle.cancel(false);
        assertEquals(0, scheduler.registrationCount());
        assertEquals(1, scheduler.ownedRegistrationCount());

        scheduler.tickAll();

        assertEquals(0, scheduler.ownedRegistrationCount());
        assertEquals(0, scheduler.scheduledRegistrationCount());
        assertEquals(0, scheduler.ingressDepth());
    }

    @Test
    void admittedRegistrationBoundKeepsIngressFinite() {
        AtomicLong now = new AtomicLong(1_000L);
        AgentTickScheduler scheduler = scheduler(now, 2, 0);
        scheduler.register(activeEntry(1, 101), () -> { }, 50L);
        scheduler.register(activeEntry(1, 102), () -> { }, 50L);

        assertThrows(
                RejectedExecutionException.class,
                () -> scheduler.register(activeEntry(1, 103), () -> { }, 50L));
        assertEquals(2, scheduler.registrationCount());
        assertEquals(2, scheduler.ownedRegistrationCount());
        assertEquals(2, scheduler.ingressDepth());
    }

    @Test
    void cancellationCannotBeDroppedWhenIngressIsFull() {
        AtomicLong now = new AtomicLong(1_000L);
        AgentTickScheduler scheduler = scheduler(now, 2, 0);
        AgentScheduleHandle first = scheduler.register(activeEntry(1, 101), () -> { }, 50L);
        scheduler.register(activeEntry(1, 102), () -> { }, 50L);
        assertEquals(2, scheduler.ingressDepth());

        first.cancel(false);
        scheduler.tickAll();

        assertEquals(1, scheduler.registrationCount());
        assertEquals(1, scheduler.ownedRegistrationCount());
        assertEquals(1, scheduler.scheduledRegistrationCount());
        assertEquals(0, scheduler.ingressDepth());
    }

    @Test
    void deterministicFiveHundredAgentRunKeepsQueueAndHeapBounded() {
        AtomicLong now = new AtomicLong(1_000L);
        AgentTickScheduler scheduler = scheduler(now, 512, 0);
        AtomicInteger updates = new AtomicInteger();
        for (int i = 0; i < 500; i++) {
            scheduler.register(activeEntry(1, i + 1), updates::incrementAndGet, 50L);
        }

        assertEquals(500, scheduler.ingressDepth());
        for (int cadence = 0; cadence < 20; cadence++) {
            scheduler.tickAll();
            now.addAndGet(50L);
        }

        assertEquals(10_000, updates.get());
        assertEquals(0, scheduler.ingressDepth());
        assertEquals(500, scheduler.scheduledRegistrationCount());
        assertEquals(500, scheduler.ingressHighWaterMark());
    }

    private static AgentTickScheduler scheduler(AtomicLong now, int ingressCapacity, int maxAgentsPerTick) {
        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        AgentSchedulerConfig config = new AgentSchedulerConfig(
                AgentSchedulerMode.CENTRAL_SEQUENTIAL,
                50L,
                true,
                250L,
                maxAgentsPerTick,
                ingressCapacity,
                1_000L,
                maxAgentsPerTick == 0 ? ingressCapacity : maxAgentsPerTick,
                40,
                10,
                2_000L,
                1);
        return new AgentTickScheduler(
                now::get,
                (loop, period) -> future,
                (task, delay) -> future,
                config);
    }

    private static AgentRuntimeEntry activeEntry(int leaderId, int agentId) {
        Character agent = mock(Character.class);
        when(agent.getId()).thenReturn(agentId);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        AgentRuntimeRegistry.registerEntry(leaderId, entry);
        return entry;
    }
}
