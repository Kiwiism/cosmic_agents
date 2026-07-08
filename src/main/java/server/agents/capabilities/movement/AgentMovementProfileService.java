package server.agents.capabilities.movement;

import client.Character;
import server.agents.capabilities.navigation.AgentNavigationGraphService;
import server.agents.integration.AgentMovementStateRuntime;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.maps.MapleMap;

public final class AgentMovementProfileService {
    private AgentMovementProfileService() {
    }

    public static boolean refreshMovementProfile(AgentRuntimeEntry entry) {
        Character agent = AgentRuntimeIdentityRuntime.bot(entry);
        AgentMovementProfile updated = AgentMovementProfile.fromCharacter(agent);
        if (updated.equals(AgentMovementStateRuntime.movementProfile(entry))) {
            return false;
        }

        MapleMap map = agent != null ? agent.getMap() : null;
        if (map != null
                && map.getFootholds() != null
                && AgentNavigationGraphService.peekGraph(map, updated) == null) {
            AgentNavigationGraphService.warmGraphAsync(map, updated);
        }

        AgentMovementStateRuntime.setMovementProfile(entry, updated);
        AgentMovementStateResetService.clearNavigationState(entry);
        return true;
    }
}
