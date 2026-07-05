package server.agents.integration;

import server.agents.runtime.AgentRuntimeEntry;

/**
 * Agent-owned adapter for temporary AgentRuntimeEntry-backed surround-breakout state.
 */
public final class AgentBotBreakoutStateRuntime {
    private AgentBotBreakoutStateRuntime() {
    }

    public static boolean hasBreakoutCommitment(AgentRuntimeEntry entry) {
        return entry.breakoutState().hasCommitment();
    }

    public static int direction(AgentRuntimeEntry entry) {
        return entry.breakoutState().direction();
    }

    public static long untilMs(AgentRuntimeEntry entry) {
        return entry.breakoutState().untilMs();
    }

    public static void setBreakoutCommitment(AgentRuntimeEntry entry, int direction, long untilMs) {
        entry.breakoutState().setCommitment(direction, untilMs);
    }

    public static boolean isExpired(AgentRuntimeEntry entry, long nowMs) {
        return entry.breakoutState().expired(nowMs);
    }

    public static void clear(AgentRuntimeEntry entry) {
        entry.breakoutState().clear();
    }
}
