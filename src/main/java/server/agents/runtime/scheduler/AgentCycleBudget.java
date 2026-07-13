package server.agents.runtime.scheduler;

import java.util.concurrent.TimeUnit;

/** Mutable scheduler-cycle accounting owned by one shard invocation. */
final class AgentCycleBudget {
    private final long deadlineNs;
    private final int maxWorkItems;
    private final long criticalReserveNs;
    private final long visibleReserveNs;
    private int processed;
    private long criticalSpentNs;
    private long visibleSpentNs;

    AgentCycleBudget(long cycleStartedNs, AgentSchedulerConfig config) {
        long budgetNs = TimeUnit.MILLISECONDS.toNanos(config.cycleBudgetMs());
        deadlineNs = cycleStartedNs + budgetNs;
        maxWorkItems = config.effectiveMaxWorkItemsPerCycle();
        criticalReserveNs = percentage(budgetNs, config.criticalReservePercent());
        visibleReserveNs = percentage(budgetNs, config.visibleReservePercent());
    }

    boolean exhausted(long nowNs) {
        return processed >= maxWorkItems || processed > 0 && nowNs >= deadlineNs;
    }

    boolean admits(AgentPriorityClass priority, long estimatedCostNs, long nowNs) {
        if (processed == 0 || priority.isVisibleOrHigher()) {
            return true;
        }
        return estimatedCostNs <= Math.max(0L, deadlineNs - nowNs);
    }

    int preferredMaximumPriority(boolean criticalReady, boolean visibleReady) {
        if (criticalReady && criticalSpentNs < criticalReserveNs) {
            return AgentPriorityClass.CRITICAL.ordinal();
        }
        if (visibleReady && visibleSpentNs < visibleReserveNs) {
            return AgentPriorityClass.VISIBLE.ordinal();
        }
        return AgentPriorityClass.DEFERRED.ordinal();
    }

    void record(AgentPriorityClass priority, long elapsedNs) {
        processed++;
        long boundedElapsed = Math.max(0L, elapsedNs);
        if (priority == AgentPriorityClass.CRITICAL) {
            criticalSpentNs += boundedElapsed;
        } else if (priority.isVisibleOrHigher()) {
            visibleSpentNs += boundedElapsed;
        }
    }

    int processed() {
        return processed;
    }

    boolean deadlineExceeded(long nowNs) {
        return nowNs > deadlineNs;
    }

    private static long percentage(long value, int percent) {
        return value * percent / 100L;
    }
}
