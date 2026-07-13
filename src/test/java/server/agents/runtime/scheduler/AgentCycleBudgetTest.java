package server.agents.runtime.scheduler;

import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentCycleBudgetTest {
    @Test
    void reservesCriticalThenVisibleCapacityBeforeGeneralWork() {
        AgentCycleBudget budget = new AgentCycleBudget(0L, config(10L, 10, 40, 10));

        assertEquals(
                AgentPriorityClass.CRITICAL.ordinal(),
                budget.preferredMaximumPriority(true, true));
        budget.record(AgentPriorityClass.CRITICAL, TimeUnit.MILLISECONDS.toNanos(1L));

        assertEquals(
                AgentPriorityClass.VISIBLE.ordinal(),
                budget.preferredMaximumPriority(true, true));
        budget.record(AgentPriorityClass.VISIBLE, TimeUnit.MILLISECONDS.toNanos(4L));

        assertEquals(
                AgentPriorityClass.DEFERRED.ordinal(),
                budget.preferredMaximumPriority(true, true));
    }

    @Test
    void enforcesCountAndPredictedBackgroundCostWithoutRejectingVisibleWork() {
        AgentCycleBudget budget = new AgentCycleBudget(0L, config(10L, 2, 0, 0));
        long nearDeadline = TimeUnit.MILLISECONDS.toNanos(9L);
        long twoMilliseconds = TimeUnit.MILLISECONDS.toNanos(2L);

        budget.record(AgentPriorityClass.BACKGROUND_ACTIVE, TimeUnit.MILLISECONDS.toNanos(1L));
        assertFalse(budget.admits(AgentPriorityClass.BACKGROUND_ACTIVE, twoMilliseconds, nearDeadline));
        assertTrue(budget.admits(AgentPriorityClass.VISIBLE, twoMilliseconds, nearDeadline));

        budget.record(AgentPriorityClass.VISIBLE, 1L);
        assertTrue(budget.exhausted(nearDeadline));
    }

    private static AgentSchedulerConfig config(long cycleBudgetMs,
                                               int maxWorkItems,
                                               int visibleReservePercent,
                                               int criticalReservePercent) {
        return new AgentSchedulerConfig(
                AgentSchedulerMode.CENTRAL_SEQUENTIAL,
                50L,
                true,
                250L,
                0,
                4096,
                cycleBudgetMs,
                maxWorkItems,
                visibleReservePercent,
                criticalReservePercent,
                2_000L,
                1);
    }
}
