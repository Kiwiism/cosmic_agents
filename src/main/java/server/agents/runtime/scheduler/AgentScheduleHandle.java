package server.agents.runtime.scheduler;

import java.util.concurrent.ScheduledFuture;

public interface AgentScheduleHandle extends ScheduledFuture<Void> {
    AgentSessionId sessionId();

    AgentSchedulerMode mode();
}
