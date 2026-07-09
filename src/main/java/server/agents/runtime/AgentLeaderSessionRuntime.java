package server.agents.runtime;

import client.Character;
import server.agents.integration.AgentCharacterGatewayRuntime;
import server.agents.integration.AgentRuntimeIdentityRuntime;

import java.util.function.IntFunction;

/**
 * Runtime wiring for resolving the live leader backing an Agent tick.
 */
public final class AgentLeaderSessionRuntime {
    private AgentLeaderSessionRuntime() {
    }

    public static Character resolveTickLeader(AgentRuntimeEntry entry, int leaderCharId) {
        return resolveTickLeader(entry, leaderCharId, id -> AgentCharacterGatewayRuntime.characters()
                .findWorldCharacterById(AgentRuntimeIdentityRuntime.bot(entry).getWorld(), id));
    }

    static Character resolveTickLeader(AgentRuntimeEntry entry,
                                       int leaderCharId,
                                       IntFunction<Character> leaderLookup) {
        return AgentLeaderSessionService.resolveTickLeader(entry, leaderCharId, leaderLookup);
    }
}
