package server.agents.capabilities.navigation;

import client.Character;
import server.bots.BotEntry;
import server.bots.BotNavigationManager;
import server.maps.MapleMap;

import java.awt.Point;

/**
 * Agent-owned seam for navigation region classification while path internals migrate.
 */
public final class AgentNavigationRegionService {
    private AgentNavigationRegionService() {
    }

    public static int resolveCurrentRegionId(AgentNavigationGraph graph,
                                             BotEntry entry,
                                             MapleMap map,
                                             Point botPos) {
        return BotNavigationManager.resolveCurrentRegionId(graph, entry, map, botPos);
    }

    public static int resolveTargetRegionId(AgentNavigationGraph graph,
                                            BotEntry entry,
                                            MapleMap map,
                                            Point targetPos) {
        return BotNavigationManager.resolveTargetRegionId(graph, entry, map, targetPos);
    }

    public static int resolveCharacterRegionId(AgentNavigationGraph graph,
                                               MapleMap map,
                                               Character character) {
        return BotNavigationManager.resolveCharacterRegionId(graph, map, character);
    }

    public static int resolvePointTargetRegionId(AgentNavigationGraph graph,
                                                 MapleMap map,
                                                 Point position) {
        return BotNavigationManager.resolvePointTargetRegionId(graph, map, position);
    }
}
