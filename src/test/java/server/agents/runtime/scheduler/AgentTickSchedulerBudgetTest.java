package server.agents.runtime.scheduler;

import client.Character;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import server.agents.monitoring.AgentSchedulerMetrics;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentTickSchedulerBudgetTest {
    @AfterEach
    void tearDown() {
        AgentRuntimeRegistry.clear();
    }

    @Test
    void priorityAndWorkGuardRunCriticalThenVisibleBeforeBackground() {
        AtomicLong now = new AtomicLong(1_000L);
        AtomicLong nanoTime = new AtomicLong();
        ArrayDeque<Runnable> continuations = new ArrayDeque<>();
        AgentTickScheduler scheduler = scheduler(now, nanoTime, continuations, 100L, 2, 50L);
        List<String> order = new ArrayList<>();

        scheduler.register(
                activeEntry(1, 101),
                () -> order.add("background"),
                50L,
                AgentWorkClass.BACKGROUND_GAMEPLAY,
                AgentPriorityClass.BACKGROUND_ACTIVE);
        scheduler.register(
                activeEntry(1, 102),
                () -> order.add("visible"),
                50L,
                AgentWorkClass.PRESENTATION_GAMEPLAY,
                AgentPriorityClass.VISIBLE);
        scheduler.register(
                activeEntry(1, 103),
                () -> order.add("critical"),
                50L,
                AgentWorkClass.LIFECYCLE_CRITICAL,
                AgentPriorityClass.CRITICAL);

        scheduler.tickAll();

        assertEquals(List.of("critical", "visible"), order);
        assertEquals(1, scheduler.readyRegistrationCount());
        assertEquals(1, continuations.size());

        continuations.removeFirst().run();
        assertEquals(List.of("critical", "visible", "background"), order);
        assertEquals(0, scheduler.readyRegistrationCount());
    }

    @Test
    void learnedBackgroundCostDefersAtDeadlineAndContinuesWithoutLoss() {
        AtomicLong now = new AtomicLong(1_000L);
        AtomicLong nanoTime = new AtomicLong();
        ArrayDeque<Runnable> continuations = new ArrayDeque<>();
        AgentTickScheduler scheduler = scheduler(now, nanoTime, continuations, 10L, 10, 50L);
        AtomicInteger updates = new AtomicInteger();
        Runnable expensiveTick = () -> {
            updates.incrementAndGet();
            nanoTime.addAndGet(TimeUnit.MILLISECONDS.toNanos(8L));
        };
        scheduler.register(
                activeEntry(1, 101), expensiveTick, 50L,
                AgentWorkClass.BACKGROUND_GAMEPLAY, AgentPriorityClass.BACKGROUND_ACTIVE);
        scheduler.register(
                activeEntry(1, 102), expensiveTick, 50L,
                AgentWorkClass.BACKGROUND_GAMEPLAY, AgentPriorityClass.BACKGROUND_ACTIVE);

        scheduler.tickAll();
        assertEquals(2, updates.get());

        now.addAndGet(50L);
        scheduler.tickAll();

        assertEquals(3, updates.get());
        assertEquals(1, scheduler.readyRegistrationCount());
        assertFalse(continuations.isEmpty());

        continuations.removeFirst().run();
        assertEquals(4, updates.get());
        assertEquals(0, scheduler.readyRegistrationCount());
        assertTrue(AgentSchedulerMetrics.snapshot().budgetExhaustions() > 0L);
    }

    @Test
    void repeatedAgingEventuallyRunsDeferredWorkUnderContinuousInteractiveLoad() {
        AtomicLong now = new AtomicLong(1_000L);
        AtomicLong nanoTime = new AtomicLong();
        ArrayDeque<Runnable> continuations = new ArrayDeque<>();
        AgentTickScheduler scheduler = scheduler(now, nanoTime, continuations, 100L, 1, 50L);
        AtomicInteger deferredUpdates = new AtomicInteger();
        scheduler.register(
                activeEntry(1, 101), deferredUpdates::incrementAndGet, 50L,
                AgentWorkClass.MAINTENANCE, AgentPriorityClass.DEFERRED);
        scheduler.register(
                activeEntry(1, 102), () -> { }, 50L,
                AgentWorkClass.PLAYER_DIRECTED, AgentPriorityClass.INTERACTIVE);

        for (int cycle = 0; cycle < 5; cycle++) {
            scheduler.tickAll();
            now.addAndGet(50L);
        }

        assertEquals(1, deferredUpdates.get());
        assertTrue(AgentSchedulerMetrics.snapshot().starvationPromotions() >= 4L);
    }

    @Test
    void defaultSizedWorkGuardContinuesFiveHundredAgentsWithoutLoss() {
        AtomicLong now = new AtomicLong(1_000L);
        AtomicLong nanoTime = new AtomicLong();
        ArrayDeque<Runnable> continuations = new ArrayDeque<>();
        AgentTickScheduler scheduler = scheduler(now, nanoTime, continuations, 1_000L, 256, 2_000L);
        AtomicInteger updates = new AtomicInteger();
        for (int i = 0; i < 500; i++) {
            scheduler.register(activeEntry(1, i + 1), updates::incrementAndGet, 50L);
        }

        scheduler.tickAll();

        assertEquals(256, updates.get());
        assertEquals(244, scheduler.readyRegistrationCount());
        assertEquals(1, continuations.size());

        continuations.removeFirst().run();
        assertEquals(500, updates.get());
        assertEquals(0, scheduler.readyRegistrationCount());
    }

    private static AgentTickScheduler scheduler(AtomicLong now,
                                                AtomicLong nanoTime,
                                                ArrayDeque<Runnable> continuations,
                                                long cycleBudgetMs,
                                                int maxWorkItems,
                                                long starvationPromotionMs) {
        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        AgentSchedulerConfig config = new AgentSchedulerConfig(
                AgentSchedulerMode.CENTRAL_SEQUENTIAL,
                50L,
                true,
                250L,
                0,
                4096,
                cycleBudgetMs,
                maxWorkItems,
                40,
                10,
                starvationPromotionMs);
        return new AgentTickScheduler(
                now::get,
                nanoTime::get,
                (loop, period) -> future,
                (task, delay) -> {
                    continuations.addLast(task);
                    return future;
                },
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
