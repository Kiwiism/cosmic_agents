package server.agents.capabilities.combat;

import server.agents.runtime.AgentRuntimeEntry;

/**
 * Capability-owned adapter for surround-breakout state.
 */
public final class AgentBreakoutStateRuntime {
    private AgentBreakoutStateRuntime() {
    }

    public static boolean hasBreakoutCommitment(AgentRuntimeEntry entry) {
        return AgentRangedTacticalStateRuntime.state(entry).breakout().hasCommitment();
    }

    public static int direction(AgentRuntimeEntry entry) {
        return AgentRangedTacticalStateRuntime.state(entry).breakout().direction();
    }

    public static long untilMs(AgentRuntimeEntry entry) {
        return AgentRangedTacticalStateRuntime.state(entry).breakout().untilMs();
    }

    public static void setBreakoutCommitment(AgentRuntimeEntry entry, int direction, long untilMs) {
        AgentRangedTacticalStateRuntime.state(entry).breakout().setCommitment(direction, untilMs);
    }

    public static boolean isExpired(AgentRuntimeEntry entry, long nowMs) {
        return AgentRangedTacticalStateRuntime.state(entry).breakout().expired(nowMs);
    }

    public static void clear(AgentRuntimeEntry entry) {
        AgentRangedTacticalStateRuntime.state(entry).breakout().clear();
    }
}
