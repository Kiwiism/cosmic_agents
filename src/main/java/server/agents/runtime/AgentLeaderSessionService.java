package server.agents.runtime;

import client.Character;

import java.util.function.IntFunction;

public final class AgentLeaderSessionService {
    private AgentLeaderSessionService() {
    }

    public static Character resolveTickLeader(AgentRuntimeEntry entry,
                                              int leaderCharId,
                                              IntFunction<Character> leaderLookup) {
        Character leader = AgentLeaderStateRuntime.leader(entry);
        if (leader == null
                || !AgentLeaderStateRuntime.matchesLeaderId(entry, leaderCharId)
                || !leader.isLoggedinWorld()) {
            leader = leaderLookup.apply(leaderCharId);
            AgentLeaderStateRuntime.setLeader(entry, leader);
        }
        return leader;
    }
}
