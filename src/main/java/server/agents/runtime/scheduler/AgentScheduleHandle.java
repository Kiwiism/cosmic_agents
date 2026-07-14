package server.agents.runtime.scheduler;

import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ScheduledFuture;

public interface AgentScheduleHandle extends ScheduledFuture<Void> {
    AgentSessionId sessionId();

    AgentSchedulerMode mode();

    /** Requests prompt execution without changing the periodic cadence. */
    boolean wake();

    CompletionStage<AgentQuiescenceToken> quiesce(AgentQuiescenceReason reason, Duration timeout);

    boolean resume(AgentQuiescenceToken token);

    boolean validatesQuiescence(AgentQuiescenceToken token);

    boolean isQuiescent();
}
