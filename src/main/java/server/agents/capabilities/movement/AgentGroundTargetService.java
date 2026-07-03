package server.agents.capabilities.movement;

import server.agents.capabilities.navigation.AgentNavigationGraph;
import server.agents.capabilities.navigation.AgentNavigationGraphService;
import server.agents.integration.AgentBotModeStateRuntime;
import server.agents.integration.AgentBotMovementStateRuntime;
import server.agents.integration.AgentBotNavigationDebugStateRuntime;
import server.agents.integration.AgentBotRuntimeIdentityRuntime;
import server.bots.BotEntry;
import server.bots.BotNavigationManager;
import server.maps.Foothold;
import server.maps.MapleMap;

import java.awt.Point;

public final class AgentGroundTargetService {
    private AgentGroundTargetService() {
    }

    public static Point adjustGrindingTargetPosition(BotEntry entry, Foothold currentFoothold, Point targetPos) {
        if (!AgentBotModeStateRuntime.grinding(entry)
                || AgentBotNavigationDebugStateRuntime.hasActiveNavigationEdge(entry)
                || currentFoothold == null
                || targetPos == null) {
            return targetPos;
        }

        MapleMap map = AgentBotRuntimeIdentityRuntime.botMap(entry);
        AgentMovementProfile profile = AgentBotMovementStateRuntime.movementProfile(entry);
        AgentNavigationGraph graph = AgentNavigationGraphService.peekGraph(map, profile);
        if (graph == null) {
            AgentNavigationGraphService.warmGraphAsync(map, profile);
            return targetPos;
        }
        Point agentPosition = AgentBotRuntimeIdentityRuntime.bot(entry).getPosition();
        int currentRegionId = BotNavigationManager.resolveCurrentRegionId(graph, entry, map, agentPosition);
        int targetRegionId = BotNavigationManager.resolveTargetRegionId(graph, entry, map, targetPos);
        if (currentRegionId < 0 || currentRegionId != targetRegionId) {
            return targetPos;
        }

        AgentNavigationGraph.Region currentRegion = graph.getRegion(currentRegionId);
        if (currentRegion == null || currentRegion.isRopeRegion) {
            return targetPos;
        }

        int safeLeft = currentRegion.minX + AgentMovementPhysicsConfig.configuredGrindEdgeMargin();
        int safeRight = currentRegion.maxX - AgentMovementPhysicsConfig.configuredGrindEdgeMargin();
        if (safeLeft >= safeRight) {
            return targetPos;
        }

        int clampedX = Math.max(safeLeft, Math.min(safeRight, targetPos.x));
        return currentRegion.pointAt(clampedX);
    }
}
