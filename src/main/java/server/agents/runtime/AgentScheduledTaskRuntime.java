package server.agents.runtime;

/**
 * Scheduled-task state bridge for the live Agent runtime session.
 */
public final class AgentScheduledTaskRuntime {
    private AgentScheduledTaskRuntime() {
    }

    public static boolean hasScheduledTask(AgentRuntimeEntry entry) {
        return entry != null && entry.scheduledTaskState().hasScheduledTask();
    }

    public static void cancelScheduledTask(AgentRuntimeEntry entry) {
        if (entry != null) {
            entry.scheduledTaskState().cancelScheduledTask();
            entry.scheduledTaskScope().cancelAll();
        }
    }
}
