package server.agents.runtime;

import server.agents.monitoring.AgentSchedulerMetrics;
import server.agents.behavior.AgentBehaviorAdaptationPersistenceRuntime;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.journey.AgentJourneyRuntime;

/** Owns cancellation and cleanup of one Agent session's runtime resources. */
public final class AgentSessionCleanupService {
    private AgentSessionCleanupService() {
    }

    public static void cancelScheduledWork(AgentRuntimeEntry entry) {
        if (entry == null) {
            return;
        }
        if (entry.scheduledTaskState().hasScheduledTask()
                && entry.scheduledTaskState().cancelScheduledTask()) {
            AgentSchedulerMetrics.recordLifecycleCancellationRequested();
            AgentSchedulerMetrics.recordLifecycleCleanedUp();
        }
        entry.scheduledTaskScope().cancelAll();
        entry.tickSliceState().clear();
        AgentJourneyRuntime.onSessionClosed(
                entry, "session cleanup", System.currentTimeMillis());
        AgentSessionEventRuntime.close(entry);
        AgentBehaviorAdaptationPersistenceRuntime.checkpointAndClear(
                entry, AgentRuntimeIdentityRuntime.botId(entry), System.currentTimeMillis());
        entry.capabilityStates().clear();
        AgentMailboxRuntime.close(entry);
    }
}
