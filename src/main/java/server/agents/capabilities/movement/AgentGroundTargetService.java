package server.agents.capabilities.movement;

import server.agents.capabilities.navigation.AgentNavigationGraph;
import server.agents.capabilities.navigation.AgentNavigationGraphService;
import server.agents.capabilities.navigation.AgentNavigationRegionService;
import server.agents.integration.AgentBotModeStateRuntime;
import server.agents.integration.AgentBotMovementStateRuntime;
import server.agents.integration.AgentBotNavigationDebugStateRuntime;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.maps.Foothold;
import server.maps.MapleMap;

import java.awt.Point;

public final class AgentGroundTargetService {
    private AgentGroundTargetService() {
    }

    public static Point adjustGrindingTargetPosition(AgentRuntimeEntry entry, Foothold currentFoothold, Point targetPos) {
        if (!AgentBotModeStateRuntime.grinding(entry)
                || AgentBotNavigationDebugStateRuntime.hasActiveNavigationEdge(entry)
                || currentFoothold == null
                || targetPos == null) {
            return targetPos;
        }

        MapleMap map = AgentRuntimeIdentityRuntime.botMap(entry);
        AgentMovementProfile profile = AgentBotMovementStateRuntime.movementProfile(entry);
        AgentNavigationGraph graph = AgentNavigationGraphService.peekGraph(map, profile);
        if (graph == null) {
            AgentNavigationGraphService.warmGraphAsync(map, profile);
            return targetPos;
        }
        Point agentPosition = AgentRuntimeIdentityRuntime.bot(entry).getPosition();
        int currentRegionId = AgentNavigationRegionService.resolveCurrentRegionId(graph, entry, map, agentPosition);
        int targetRegionId = AgentNavigationRegionService.resolveTargetRegionId(graph, entry, map, targetPos);
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
