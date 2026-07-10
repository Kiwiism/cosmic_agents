package server.agents.integration;

import java.util.concurrent.ScheduledFuture;

public interface SchedulerGateway {
    ScheduledFuture<?> schedule(Runnable action, long delayMs);

    ScheduledFuture<?> register(Runnable action, long periodMs);
}
