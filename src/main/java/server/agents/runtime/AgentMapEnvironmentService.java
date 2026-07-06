package server.agents.runtime;

import server.agents.integration.AgentBotRuntimeIdentityRuntime;
import server.maps.MapleMap;

public final class AgentMapEnvironmentService {
    private AgentMapEnvironmentService() {
    }

    public static boolean isSwimMap(AgentRuntimeEntry entry) {
        MapleMap map = AgentBotRuntimeIdentityRuntime.botMap(entry);
        return map != null && map.isSwim();
    }
}
