package server.agents.integration;

import client.Character;
import server.bots.BotEntry;

/**
 * Agent-owned adapter for temporary BotEntry-backed live leader/anchor state.
 */
public final class AgentBotLeaderStateRuntime {
    private AgentBotLeaderStateRuntime() {
    }

    public static Character leader(BotEntry entry) {
        return entry == null ? null : entry.identityState().leader();
    }

    public static void setLeader(BotEntry entry, Character leader) {
        if (entry != null) {
            entry.identityState().setLeader(leader);
        }
    }

    public static boolean matchesLeaderId(BotEntry entry, int leaderCharId) {
        Character leader = leader(entry);
        return leader != null && leader.getId() == leaderCharId;
    }
}
