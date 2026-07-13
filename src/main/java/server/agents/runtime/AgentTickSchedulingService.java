package server.agents.runtime;

import server.agents.runtime.scheduler.AgentScheduleHandle;
import server.agents.runtime.scheduler.AgentScheduler;

public final class AgentTickSchedulingService {
    private AgentTickSchedulingService() {
    }

    public static AgentScheduleHandle register(
            AgentRuntimeEntry entry,
            Runnable tick,
            long periodMs,
            AgentLifecycleService.AgentTickScheduler legacyScheduler) {
        return AgentScheduler.register(entry, tick, periodMs, legacyScheduler);
    }
}
