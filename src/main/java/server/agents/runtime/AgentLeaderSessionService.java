package server.agents.runtime;

import client.Character;
import server.agents.integration.AgentBotLeaderStateRuntime;
import server.bots.BotEntry;

import java.util.function.IntFunction;

public final class AgentLeaderSessionService {
    private AgentLeaderSessionService() {
    }

    public static Character resolveTickLeader(BotEntry entry,
                                              int leaderCharId,
                                              IntFunction<Character> leaderLookup) {
        Character leader = AgentBotLeaderStateRuntime.leader(entry);
        if (leader == null
                || !AgentBotLeaderStateRuntime.matchesLeaderId(entry, leaderCharId)
                || !leader.isLoggedinWorld()) {
            leader = leaderLookup.apply(leaderCharId);
            AgentBotLeaderStateRuntime.setLeader(entry, leader);
        }
        return leader;
    }
}
