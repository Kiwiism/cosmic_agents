package server.agents.capabilities.objective;

import client.Character;
import server.agents.capabilities.movement.AgentMovementStateRuntime;
import server.agents.capabilities.navigation.AgentNavigationGraph;
import server.agents.capabilities.navigation.AgentNavigationGraphService;
import server.agents.capabilities.navigation.AgentNavigationPathService;
import server.agents.runtime.AgentRuntimeEntry;
import server.maps.MapleMap;

import java.awt.Point;

/** Bounded interaction fallback for NPC sprite origins outside the navigable graph. */
public final class AgentNpcInteractionReachabilityService {
    private static final int UNREACHABLE_GRAPH_INTERACTION_DISTANCE_PX = config.AgentTuning.intValue(
            "server.agents.capabilities.objective.AgentNpcInteractionReachabilityService.UNREACHABLE_GRAPH_INTERACTION_DISTANCE_PX");

    private AgentNpcInteractionReachabilityService() {
    }

    public static boolean canInteract(AgentRuntimeEntry entry,
                                      Character agent,
                                      Point npcPosition,
                                      int ordinaryDistancePx) {
        return canInteract(entry, agent, agent == null ? null : agent.getPosition(),
                npcPosition, ordinaryDistancePx, UNREACHABLE_GRAPH_INTERACTION_DISTANCE_PX);
    }

    public static boolean canInteract(AgentRuntimeEntry entry,
                                      Character agent,
                                      Point npcPosition,
                                      int ordinaryDistancePx,
                                      int unreachableGraphDistancePx) {
        return canInteract(entry, agent, agent == null ? null : agent.getPosition(),
                npcPosition, ordinaryDistancePx, unreachableGraphDistancePx);
    }

    public static boolean canInteract(AgentRuntimeEntry entry,
                                      Character agent,
                                      Point currentPosition,
                                      Point npcPosition,
                                      int ordinaryDistancePx) {
        return canInteract(entry, agent, currentPosition, npcPosition, ordinaryDistancePx,
                UNREACHABLE_GRAPH_INTERACTION_DISTANCE_PX);
    }

    public static boolean canInteract(AgentRuntimeEntry entry,
                                      Character agent,
                                      Point currentPosition,
                                      Point npcPosition,
                                      int ordinaryDistancePx,
                                      int unreachableGraphDistancePx) {
        if (agent == null || currentPosition == null || npcPosition == null) {
            return false;
        }
        double distanceSquared = currentPosition.distanceSq(npcPosition);
        if (distanceSquared <= (long) ordinaryDistancePx * ordinaryDistancePx) {
            return true;
        }
        int fallbackDistancePx = Math.max(ordinaryDistancePx, unreachableGraphDistancePx);
        if (distanceSquared > (long) fallbackDistancePx * fallbackDistancePx) {
            return false;
        }
        MapleMap map = agent.getMap();
        AgentNavigationGraph graph = AgentNavigationGraphService.peekBestGraph(
                map, AgentMovementStateRuntime.movementProfile(entry));
        return graphRouteUnavailable(graph, map, currentPosition, npcPosition);
    }

    static boolean graphRouteUnavailable(AgentNavigationGraph graph,
                                         MapleMap map,
                                         Point currentPosition,
                                         Point npcPosition) {
        if (graph == null || map == null || currentPosition == null || npcPosition == null) {
            return false;
        }
        int currentRegionId = graph.findRegionId(map, currentPosition);
        int targetRegionId = graph.findRegionId(map, npcPosition);
        if (targetRegionId < 0) {
            return true;
        }
        if (currentRegionId < 0 || currentRegionId == targetRegionId) {
            return false;
        }
        AgentNavigationPathService.SearchOutcome outcome = AgentNavigationPathService.runSearch(
                graph, map, currentPosition, currentRegionId, targetRegionId, npcPosition,
                "npc-interaction-fallback", true, false);
        return outcome.completeness()
                == AgentNavigationPathService.RouteCompleteness.UNREACHABLE;
    }
}
