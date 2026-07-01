package server.agents.runtime;

import server.agents.integration.AgentBotRuntimeIdentityRuntime;
import server.bots.BotEntry;
import server.maps.MapleMap;

public final class AgentMapEnvironmentService {
    private AgentMapEnvironmentService() {
    }

    public static boolean isSwimMap(BotEntry entry) {
        MapleMap map = AgentBotRuntimeIdentityRuntime.botMap(entry);
        return map != null && map.isSwim();
    }
}
