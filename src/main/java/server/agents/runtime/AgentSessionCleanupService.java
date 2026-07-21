package server.agents.runtime;

import server.agents.capabilities.dialogue.conversation.AgentConversationRuntime;
import server.agents.monitoring.AgentSchedulerMetrics;

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
        AgentConversationRuntime.leave(entry);
        AgentSessionEventRuntime.close(entry);
        entry.capabilityStates().clear();
        AgentMailboxRuntime.close(entry);
    }
}
