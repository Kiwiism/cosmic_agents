package server.agents.runtime;

import server.bots.BotEntry;

/**
 * Temporary scheduled-task state bridge while BotEntry remains the live session
 * shell.
 */
public final class AgentScheduledTaskRuntime {
    private AgentScheduledTaskRuntime() {
    }

    public static boolean hasScheduledTask(BotEntry entry) {
        return entry != null && entry.scheduledTaskState().hasScheduledTask();
    }

    public static void cancelScheduledTask(BotEntry entry) {
        if (entry != null) {
            entry.scheduledTaskState().cancelScheduledTask();
        }
    }
}
