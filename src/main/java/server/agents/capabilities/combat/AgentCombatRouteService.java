package server.agents.capabilities.combat;

import client.Character;
import server.agents.capabilities.movement.AgentMovementProfile;
import server.agents.capabilities.navigation.AgentNavigationEdgeReliabilityRuntime;
import server.agents.capabilities.navigation.AgentNavigationEdgeValidationService;
import server.agents.capabilities.navigation.AgentNavigationGraph;
import server.agents.capabilities.navigation.AgentNavigationPathService;
import server.agents.capabilities.navigation.AgentNavigationReliabilityConfig;
import server.agents.runtime.AgentRuntimeEntry;
import server.maps.MapleMap;

import java.awt.Point;
import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

/** Standard reachability gate between combat candidate policy and target scoring. */
final class AgentCombatRouteService {
    private AgentCombatRouteService() {
    }

    static long pathCost(AgentNavigationGraph graph,
                         MapleMap map,
                         Point startPos,
                         int startRegionId,
                         Point targetPos,
                         int targetRegionId,
                         AgentMovementProfile profile,
                         AgentRuntimeEntry entry,
                         Character agent,
                         long unreachableCost) {
        if (startPos == null || targetPos == null || startRegionId < 0 || targetRegionId < 0) {
            return AgentCombatGrindTargetPolicy.graphPathCost(
                    false, false, 0L, List.of(), unreachableCost);
        }
        if (startRegionId == targetRegionId) {
            return AgentCombatGrindTargetPolicy.graphPathCost(
                    true, true,
                    AgentCombatScoringPolicy.estimateLocalTravelCostMs(startPos, targetPos, profile),
                    List.of(), unreachableCost);
        }

        boolean strictRoutes = AgentCombatPolicyConfig.strictCombatRouteValidationEnabled();
        boolean reliabilityAware = AgentNavigationReliabilityConfig.edgeSuppressionEnabled()
                || AgentNavigationReliabilityConfig.routePenaltiesEnabled();
        if (!strictRoutes && !reliabilityAware) {
            List<AgentNavigationGraph.Edge> legacyPath =
                    AgentNavigationPathService.findPathForTargetScore(
                            graph, map, startPos, startRegionId, targetRegionId, targetPos);
            List<Long> edgeCosts = new ArrayList<>(legacyPath.size());
            for (AgentNavigationGraph.Edge edge : legacyPath) {
                edgeCosts.add((long) edge.cost);
            }
            return AgentCombatGrindTargetPolicy.graphPathCost(
                    true, false, 0L, edgeCosts, unreachableCost);
        }

        long nowMs = System.currentTimeMillis();
        ToIntFunction<AgentNavigationGraph.Edge> reliabilityPenalty =
                AgentNavigationEdgeReliabilityRuntime.edgePenalty(entry, graph.mapId, nowMs);
        AgentNavigationPathService.SearchOutcome outcome =
                AgentNavigationPathService.findRouteForTargetScore(
                        graph, map, startPos, startRegionId, targetRegionId, targetPos,
                        AgentNavigationEdgeReliabilityRuntime.edgeFilter(
                                entry, graph.mapId, nowMs),
                        reliabilityPenalty);
        if (strictRoutes && (completeRouteCost(outcome, unreachableCost) == unreachableCost
                || !firstEdgeExecutable(
                graph, entry, agent, startRegionId, startPos, outcome, nowMs))) {
            return unreachableCost;
        }

        List<Long> edgeCosts = new ArrayList<>(outcome.path().size());
        for (AgentNavigationGraph.Edge edge : outcome.path()) {
            long penalty = AgentNavigationReliabilityConfig.routePenaltiesEnabled()
                    ? Math.max(0, reliabilityPenalty.applyAsInt(edge)) : 0L;
            edgeCosts.add(Math.max(0L, (long) edge.cost + penalty));
        }
        return AgentCombatGrindTargetPolicy.graphPathCost(
                true, false, 0L, edgeCosts, unreachableCost);
    }

    static long completeRouteCost(AgentNavigationPathService.SearchOutcome outcome,
                                  long unreachableCost) {
        if (outcome == null
                || outcome.completeness()
                != AgentNavigationPathService.RouteCompleteness.COMPLETE) {
            return unreachableCost;
        }
        return Math.max(0L, outcome.cost());
    }

    static boolean firstEdgeExecutable(AgentNavigationGraph graph,
                                       AgentRuntimeEntry entry,
                                       Character agent,
                                       int startRegionId,
                                       Point startPos,
                                       AgentNavigationPathService.SearchOutcome outcome,
                                       long nowMs) {
        if (graph == null || entry == null || agent == null || startPos == null
                || outcome == null
                || outcome.completeness()
                != AgentNavigationPathService.RouteCompleteness.COMPLETE) {
            return false;
        }
        if (outcome.path().isEmpty()) {
            return true;
        }
        AgentNavigationGraph.Edge firstEdge =
                AgentNavigationPathService.collapseLeadingWalkEdges(outcome.path());
        if (firstEdge == null) {
            return true;
        }
        AgentNavigationEdgeValidationService.Result validation =
                AgentNavigationEdgeValidationService.validate(
                        graph, entry, agent, graph.mapId, startRegionId,
                        startPos, firstEdge, nowMs);
        return !validation.rejected();
    }
}
