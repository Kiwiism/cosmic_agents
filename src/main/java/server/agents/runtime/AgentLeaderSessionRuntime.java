package server.agents.runtime;

import client.Character;
import net.server.Server;
import server.agents.integration.AgentRuntimeIdentityRuntime;

import java.util.function.IntFunction;

/**
 * Runtime wiring for resolving the live leader backing an Agent tick.
 */
public final class AgentLeaderSessionRuntime {
    private AgentLeaderSessionRuntime() {
    }

    public static Character resolveTickLeader(AgentRuntimeEntry entry, int leaderCharId) {
        return resolveTickLeader(entry, leaderCharId, id -> Server.getInstance()
                .getWorld(AgentRuntimeIdentityRuntime.bot(entry).getWorld())
                .getPlayerStorage()
                .getCharacterById(id));
    }

    static Character resolveTickLeader(AgentRuntimeEntry entry,
                                       int leaderCharId,
                                       IntFunction<Character> leaderLookup) {
        return AgentLeaderSessionService.resolveTickLeader(entry, leaderCharId, leaderLookup);
    }
}
