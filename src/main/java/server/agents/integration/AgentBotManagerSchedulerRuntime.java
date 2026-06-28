package server.agents.integration;

import server.bots.BotEntry;

/**
 * Temporary Agent-owned bridge for delayed callbacks still triggered by the
 * legacy BotManager shell.
 */
public final class AgentBotManagerSchedulerRuntime {
    private AgentBotManagerSchedulerRuntime() {
    }

    public static void afterDelay(long delayMs, Runnable action) {
        AgentBotSchedulerRuntime.afterDelay(delayMs, action);
    }

    public static boolean hasScheduledTask(BotEntry entry) {
        return entry != null && entry.hasScheduledTask();
    }

    public static void cancelScheduledTask(BotEntry entry) {
        if (entry != null) {
            entry.cancelScheduledTask();
        }
    }
}
