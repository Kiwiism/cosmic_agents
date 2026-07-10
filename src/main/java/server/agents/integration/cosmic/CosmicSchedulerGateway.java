package server.agents.integration.cosmic;

import server.TimerManager;
import server.agents.integration.SchedulerGateway;

import java.util.concurrent.ScheduledFuture;

public enum CosmicSchedulerGateway implements SchedulerGateway {
    INSTANCE;

    @Override
    public ScheduledFuture<?> schedule(Runnable action, long delayMs) {
        return TimerManager.getInstance().schedule(action, delayMs);
    }

    @Override
    public ScheduledFuture<?> register(Runnable action, long periodMs) {
        return TimerManager.getInstance().register(action, periodMs);
    }
}
