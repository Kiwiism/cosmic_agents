package server.agents.runtime;

import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.maps.MapleMap;

public final class AgentMapEnvironmentService {
    private AgentMapEnvironmentService() {
    }

    public static boolean isSwimMap(AgentRuntimeEntry entry) {
        MapleMap map = AgentRuntimeIdentityRuntime.botMap(entry);
        return map != null && map.isSwim();
    }
}
