package server.agents.integration;

import server.bots.BotEntry;

/**
 * Agent-owned adapter for temporary BotEntry-backed tick failure window state.
 */
public final class AgentBotTickFailureStateRuntime {
    private AgentBotTickFailureStateRuntime() {
    }

    public static int failureCount(BotEntry entry) {
        return entry.tickFailureState().failureCount();
    }

    public static long windowStartedAtMs(BotEntry entry) {
        return entry.tickFailureState().windowStartedAtMs();
    }

    public static int recordFailure(BotEntry entry, long nowMs, long windowMs) {
        if (nowMs - windowStartedAtMs(entry) > windowMs) {
            entry.tickFailureState().resetWindow(nowMs);
        }
        return entry.tickFailureState().incrementFailureCount();
    }

    public static boolean hasFailures(BotEntry entry) {
        return failureCount(entry) > 0;
    }

    public static void clear(BotEntry entry) {
        entry.tickFailureState().clear();
    }
}
