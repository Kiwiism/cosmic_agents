package server.agents.capabilities.movement;

import client.Character;
import server.agents.capabilities.navigation.AgentNavigationGraphService;
import server.agents.integration.AgentBotMovementStateRuntime;
import server.agents.integration.AgentBotRuntimeIdentityRuntime;
import server.bots.BotEntry;
import server.maps.MapleMap;

public final class AgentMovementProfileService {
    private AgentMovementProfileService() {
    }

    public static boolean refreshMovementProfile(BotEntry entry) {
        Character agent = AgentBotRuntimeIdentityRuntime.bot(entry);
        AgentMovementProfile updated = AgentMovementProfile.fromCharacter(agent);
        if (updated.equals(AgentBotMovementStateRuntime.movementProfile(entry))) {
            return false;
        }

        MapleMap map = agent != null ? agent.getMap() : null;
        if (map != null
                && map.getFootholds() != null
                && AgentNavigationGraphService.peekGraph(map, updated) == null) {
            AgentNavigationGraphService.warmGraphAsync(map, updated);
        }

        AgentBotMovementStateRuntime.setMovementProfile(entry, updated);
        AgentMovementStateResetService.clearNavigationState(entry);
        return true;
    }
}
