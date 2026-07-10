package server.agents.runtime;

import java.util.concurrent.ScheduledFuture;

public final class AgentTickSchedulingService {
    private AgentTickSchedulingService() {
    }

    public static ScheduledFuture<?> register(
            AgentRuntimeEntry entry,
            Runnable tick,
            long periodMs,
            AgentLifecycleService.AgentTickScheduler legacyScheduler) {
        if (AgentSchedulerConfig.centralEnabled()) {
            return AgentTickScheduler.instance().register(entry, tick, periodMs);
        }
        return legacyScheduler.schedule(tick, periodMs);
    }
}
