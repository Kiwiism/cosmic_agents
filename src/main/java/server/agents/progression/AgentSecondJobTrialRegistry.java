package server.agents.progression;

import java.util.HashMap;
import java.util.Map;
import server.agents.runtime.AgentRuntimeRegistry;

/** Session-local admission guard for shared second-job trial maps. */
final class AgentSecondJobTrialRegistry {
    private static final Map<Integer, Integer> OWNERS = new HashMap<>();

    private AgentSecondJobTrialRegistry() { }

    static synchronized boolean claim(int mapId, int characterId) {
        Integer owner = OWNERS.get(mapId);
        if (owner != null && owner != characterId
                && AgentRuntimeRegistry.findByAgentCharacterId(owner) != null) return false;
        OWNERS.put(mapId, characterId);
        return true;
    }

    static synchronized void release(int mapId, int characterId) {
        OWNERS.remove(mapId, characterId);
    }
}
