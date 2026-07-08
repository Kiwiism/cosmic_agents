package server.agents.plans;

import client.Character;
import server.agents.capabilities.navigation.AgentNavigationGraph;
import server.agents.capabilities.navigation.AgentNavigationGraphService;
import server.agents.capabilities.navigation.AgentNavigationPathService;
import server.agents.capabilities.navigation.AgentNavigationRegionService;
import server.agents.integration.AgentBotMovementStateRuntime;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.runtime.AgentRuntimeConfig;
import server.agents.runtime.AgentRuntimeEntry;
import server.maps.MapleMap;

import java.awt.Point;
import java.util.List;

public final class AgentScriptMoveTargetService {
    private AgentScriptMoveTargetService() {
    }

    public static boolean isCheapMoveTarget(AgentRuntimeEntry entry,
                                            Point targetPos,
                                            int maxPathCost,
                                            int fallbackRangeX,
                                            int fallbackRangeY) {
        return isCheapMoveTarget(
                entry,
                targetPos,
                maxPathCost,
                fallbackRangeX,
                fallbackRangeY,
                AgentRuntimeConfig.cfg.LOOT_RADIUS);
    }

    public static boolean isCheapMoveTarget(AgentRuntimeEntry entry,
                                            Point targetPos,
                                            int maxPathCost,
                                            int fallbackRangeX,
                                            int fallbackRangeY,
                                            int nearTargetRadius) {
        if (!AgentRuntimeIdentityRuntime.hasBot(entry) || targetPos == null) {
            return false;
        }

        Character bot = AgentRuntimeIdentityRuntime.bot(entry);
        Point botPos = bot.getPosition();
        if (botPos == null) {
            return false;
        }
        if (Math.abs(targetPos.x - botPos.x) <= nearTargetRadius
                && Math.abs(targetPos.y - botPos.y) <= nearTargetRadius) {
            return false;
        }

        MapleMap map = bot.getMap();
        if (map == null || map.getFootholds() == null) {
            return false;
        }

        AgentNavigationGraph graph = AgentNavigationGraphService.peekBestGraph(map, AgentBotMovementStateRuntime.movementProfile(entry));
        if (graph == null) {
            return Math.abs(targetPos.x - botPos.x) <= fallbackRangeX
                    && Math.abs(targetPos.y - botPos.y) <= fallbackRangeY;
        }

        int startRegionId = AgentNavigationRegionService.resolveCurrentRegionId(graph, entry, map, botPos);
        int targetRegionId = AgentNavigationRegionService.resolvePointTargetRegionId(graph, map, targetPos);
        if (startRegionId < 0 || targetRegionId < 0) {
            return false;
        }
        if (startRegionId == targetRegionId) {
            return true;
        }

        List<AgentNavigationGraph.Edge> path = AgentNavigationPathService.findPath(graph, bot, startRegionId, targetRegionId, targetPos);
        if (path.isEmpty()) {
            return false;
        }

        int totalCost = 0;
        for (AgentNavigationGraph.Edge edge : path) {
            totalCost += edge.cost;
            if (totalCost > maxPathCost) {
                return false;
            }
        }
        return true;
    }
}
