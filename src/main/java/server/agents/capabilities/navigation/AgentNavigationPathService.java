package server.agents.capabilities.navigation;

import client.Character;
import server.bots.BotNavigationManager;
import server.maps.MapleMap;

import java.awt.Point;
import java.util.List;

/**
 * Agent-owned seam for navigation path search while the legacy search body migrates.
 */
public final class AgentNavigationPathService {
    private AgentNavigationPathService() {
    }

    public static List<AgentNavigationGraph.Edge> findPath(AgentNavigationGraph graph,
                                                           Character bot,
                                                           int startRegionId,
                                                           int targetRegionId,
                                                           Point targetPos) {
        return BotNavigationManager.findPath(graph, bot, startRegionId, targetRegionId, targetPos);
    }

    public static List<AgentNavigationGraph.Edge> findPath(AgentNavigationGraph graph,
                                                           MapleMap map,
                                                           Point startPos,
                                                           int startRegionId,
                                                           int targetRegionId,
                                                           Point targetPos) {
        return BotNavigationManager.findPath(graph, map, startPos, startRegionId, targetRegionId, targetPos);
    }

    public static List<AgentNavigationGraph.Edge> findPathForTargetScore(AgentNavigationGraph graph,
                                                                         MapleMap map,
                                                                         Point startPos,
                                                                         int startRegionId,
                                                                         int targetRegionId,
                                                                         Point targetPos) {
        return BotNavigationManager.findPathForTargetScore(graph, map, startPos, startRegionId, targetRegionId, targetPos);
    }
}
