package server.agents.integration;

import java.util.concurrent.ScheduledFuture;

@AgentGatewayAffinity(
        value = AgentGatewayThreadAffinity.ASYNC_EXTERNAL,
        rationale = "The server scheduler accepts callbacks and never runs as an Agent state mutation gateway.")
public interface SchedulerGateway {
    ScheduledFuture<?> schedule(Runnable action, long delayMs);

    ScheduledFuture<?> register(Runnable action, long periodMs);
}
