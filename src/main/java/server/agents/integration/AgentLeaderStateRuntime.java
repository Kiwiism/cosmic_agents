package server.agents.integration;

import client.Character;
import server.agents.runtime.AgentRuntimeEntry;

/**
 * Agent-owned adapter for AgentRuntimeEntry-backed live leader/anchor state.
 */
public final class AgentLeaderStateRuntime {
    private AgentLeaderStateRuntime() {
    }

    public static Character leader(AgentRuntimeEntry entry) {
        return entry == null ? null : entry.identityState().leader();
    }

    public static void setLeader(AgentRuntimeEntry entry, Character leader) {
        if (entry != null) {
            entry.identityState().setLeader(leader);
        }
    }

    public static boolean matchesLeaderId(AgentRuntimeEntry entry, int leaderCharId) {
        Character leader = leader(entry);
        return leader != null && leader.getId() == leaderCharId;
    }
}
