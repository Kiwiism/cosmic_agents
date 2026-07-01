package server.agents.runtime;

import server.agents.integration.AgentBotTickFailureStateRuntime;
import server.bots.BotEntry;

/**
 * Agent-owned policy for per-agent tick failure counting and escalation.
 */
public final class AgentTickFailurePolicy {
    public static final int FAILURE_LIMIT = 3;
    public static final long FAILURE_WINDOW_MS = 10_000L;

    public record Decision(int failureCount, boolean forceIdle, boolean disableAgent) {
    }

    private AgentTickFailurePolicy() {
    }

    public static Decision recordFailure(BotEntry entry, long nowMs) {
        int failureCount = AgentBotTickFailureStateRuntime.recordFailure(entry, nowMs, FAILURE_WINDOW_MS);
        return new Decision(failureCount, failureCount == 2, failureCount >= FAILURE_LIMIT);
    }

    public static void resetFailures(BotEntry entry) {
        if (entry != null && AgentBotTickFailureStateRuntime.hasFailures(entry)) {
            AgentBotTickFailureStateRuntime.clear(entry);
        }
    }
}
