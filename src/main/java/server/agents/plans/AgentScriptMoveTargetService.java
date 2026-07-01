package server.agents.plans;

import client.Character;
import server.agents.capabilities.navigation.AgentNavigationGraph;
import server.agents.capabilities.navigation.AgentNavigationGraphService;
import server.agents.integration.AgentBotMovementStateRuntime;
import server.agents.integration.AgentBotRuntimeIdentityRuntime;
import server.bots.BotEntry;
import server.bots.BotNavigationManager;
import server.maps.MapleMap;

import java.awt.Point;
import java.util.List;

public final class AgentScriptMoveTargetService {
    private AgentScriptMoveTargetService() {
    }

    public static boolean isCheapMoveTarget(BotEntry entry,
                                            Point targetPos,
                                            int maxPathCost,
                                            int fallbackRangeX,
                                            int fallbackRangeY,
                                            int nearTargetRadius) {
        if (!AgentBotRuntimeIdentityRuntime.hasBot(entry) || targetPos == null) {
            return false;
        }

        Character bot = AgentBotRuntimeIdentityRuntime.bot(entry);
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

        int startRegionId = BotNavigationManager.resolveCurrentRegionId(graph, entry, map, botPos);
        int targetRegionId = BotNavigationManager.resolvePointTargetRegionId(graph, map, targetPos);
        if (startRegionId < 0 || targetRegionId < 0) {
            return false;
        }
        if (startRegionId == targetRegionId) {
            return true;
        }

        List<AgentNavigationGraph.Edge> path = BotNavigationManager.findPath(graph, bot, startRegionId, targetRegionId, targetPos);
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
