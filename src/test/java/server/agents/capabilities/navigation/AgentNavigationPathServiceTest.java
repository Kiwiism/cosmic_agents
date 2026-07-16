package server.agents.capabilities.navigation;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.capabilities.movement.AgentMovementPhysicsConfig;
import server.agents.capabilities.movement.AgentMovementProfile;
import server.life.Monster;
import server.maps.MapleMap;
import server.maps.Foothold;
import server.maps.FootholdTree;

import java.awt.Point;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentNavigationPathServiceTest {
    @Test
    void intraRegionTravelCostUsesWalkVelocityForGroundRegion() {
        AgentNavigationGraph graph = graphWithRegion(
                new AgentNavigationGraph.Region(1, List.of(new AgentNavigationGraph.Segment(
                        new Foothold(new Point(0, 100), new Point(100, 100), 1)))));

        int expected = Math.max(0, (int) Math.round((50 * 1000.0) / Math.max(1.0, graph.movementProfile.walkVelocityPxs())));

        assertEquals(expected, AgentNavigationPathService.intraRegionTravelCost(graph, 1, new Point(0, 100), new Point(50, 100)));
        assertEquals(expected, AgentNavigationPathService.heuristic(graph, new Point(0, 100), new Point(50, 100)));
    }

    @Test
    void intraRegionTravelCostUsesClimbSpeedForRopeRegion() {
        AgentNavigationGraph graph = graphWithRegion(new AgentNavigationGraph.Region(1, 100, 50, 250, false));
        int expected = Math.max(0, (int) Math.round((75 * 1000.0) / Math.max(1, AgentMovementPhysicsConfig.configuredClimbSpeedPxs())));

        assertEquals(expected, AgentNavigationPathService.intraRegionTravelCost(graph, 1, new Point(100, 50), new Point(100, 125)));
    }

    @Test
    void collapseLeadingWalkEdgesPromotesFirstActionableEdge() {
        AgentNavigationGraph.Edge collapsed = AgentNavigationPathService.collapseLeadingWalkEdges(List.of(
                edge(1, 2, AgentNavigationGraph.EdgeType.WALK, new Point(528, -914), new Point(528, -914), 50),
                edge(2, 3, AgentNavigationGraph.EdgeType.WALK, new Point(528, -914), new Point(528, -914), 50),
                edge(3, 4, AgentNavigationGraph.EdgeType.JUMP, new Point(540, -914), new Point(612, -980), 300)
        ));

        assertNotNull(collapsed);
        assertEquals(AgentNavigationGraph.EdgeType.JUMP, collapsed.type);
        assertEquals(1, collapsed.fromRegionId);
        assertEquals(4, collapsed.toRegionId);
        assertEquals(400, collapsed.cost);
    }

    @Test
    void collapseLeadingWalkEdgesReturnsNullWhenAllLeadingWalksConsumeNoMovement() {
        assertNull(AgentNavigationPathService.collapseLeadingWalkEdges(List.of(
                edge(1, 2, AgentNavigationGraph.EdgeType.WALK, new Point(10, 10), new Point(10, 10), 50),
                edge(2, 3, AgentNavigationGraph.EdgeType.WALK, new Point(10, 10), new Point(10, 10), 50)
        )));
    }

    @Test
    void preciseWalkTargetRequiresRealWalkMovement() {
        AgentNavigationGraph.Edge walkHandoff = edge(1, 2, AgentNavigationGraph.EdgeType.WALK,
                new Point(28, -1167), new Point(13, -1170), 100);
        AgentNavigationGraph.Edge noMoveWalk = edge(1, 2, AgentNavigationGraph.EdgeType.WALK,
                new Point(28, -1167), new Point(28, -1167), 50);

        assertTrue(AgentNavigationPathService.shouldUsePreciseWalkTarget(walkHandoff));
        assertFalse(AgentNavigationPathService.shouldUsePreciseWalkTarget(noMoveWalk));
        assertFalse(AgentNavigationPathService.shouldUsePreciseWalkTarget(null));
    }

    @Test
    void edgeUsabilityAllowsNonPortalEdgesAndRequiresOpenPortal() {
        MapleMap map = new MapleMap(910000001, 0, 0, 910000001, 1.0f);
        AgentNavigationGraph graph = graphWithRegion(new AgentNavigationGraph.Region(1, List.of(new AgentNavigationGraph.Segment(
                new Foothold(new Point(0, 100), new Point(100, 100), 1)))));

        assertTrue(AgentNavigationPathService.isEdgeUsable(graph, map,
                edge(1, 2, AgentNavigationGraph.EdgeType.WALK, new Point(0, 100), new Point(10, 100), 10)));
        assertFalse(AgentNavigationPathService.isEdgeUsable(graph, map,
                new AgentNavigationGraph.Edge(1, 2, AgentNavigationGraph.EdgeType.PORTAL,
                        new Point(0, 100), new Point(10, 100), 0, 0, 0, 42, 0, 10)));
    }

    @Test
    void findPathRunsAgentOwnedSearch() {
        AgentNavigationGraph graph = graphWithRegionsAndEdges(
                List.of(
                        groundRegion(1, 0, 100, 100),
                        groundRegion(2, 100, 200, 100),
                        groundRegion(3, 200, 300, 100)),
                Map.of(
                        1, List.of(
                                edge(1, 2, AgentNavigationGraph.EdgeType.WALK, new Point(0, 100), new Point(100, 100), 10),
                                edge(1, 3, AgentNavigationGraph.EdgeType.WALK, new Point(0, 100), new Point(300, 100), 1_000)),
                        2, List.of(edge(2, 3, AgentNavigationGraph.EdgeType.WALK, new Point(100, 100), new Point(200, 100), 10))));

        List<AgentNavigationGraph.Edge> path = AgentNavigationPathService.findPath(
                graph, null, new Point(0, 100), 1, 3, new Point(200, 100));

        assertEquals(2, path.size());
        assertEquals(2, path.get(0).toRegionId);
        assertEquals(3, path.get(1).toRegionId);
    }

    @Test
    void findNextEdgeCollapsesNoMovementLeadingWalks() {
        AgentNavigationGraph graph = graphWithRegionsAndEdges(
                List.of(
                        groundRegion(1, 0, 100, 100),
                        groundRegion(2, 100, 200, 100),
                        groundRegion(3, 200, 300, 100)),
                Map.of(
                        1, List.of(edge(1, 2, AgentNavigationGraph.EdgeType.WALK,
                                new Point(0, 100), new Point(0, 100), 10)),
                        2, List.of(edge(2, 3, AgentNavigationGraph.EdgeType.JUMP,
                                new Point(0, 100), new Point(200, 100), 20))));
        Character bot = mock(Character.class);
        when(bot.getMap()).thenReturn(null);
        when(bot.getPosition()).thenReturn(new Point(0, 100));

        AgentNavigationGraph.Edge next = AgentNavigationPathService.findNextEdge(
                graph, bot, 1, 3, new Point(200, 100));

        assertNotNull(next);
        assertEquals(1, next.fromRegionId);
        assertEquals(3, next.toRegionId);
        assertEquals(AgentNavigationGraph.EdgeType.JUMP, next.type);
        assertEquals(30, next.cost);
    }

    @Test
    void findNextEdgePrefersClearDetourOverCrowdedShortRoute() {
        AgentNavigationGraph.Edge crowdedFirst = edge(1, 2, AgentNavigationGraph.EdgeType.JUMP,
                new Point(50, 100), new Point(150, 100), 100);
        AgentNavigationGraph.Edge crowdedSecond = edge(2, 4, AgentNavigationGraph.EdgeType.JUMP,
                new Point(150, 100), new Point(350, 100), 100);
        AgentNavigationGraph.Edge clearFirst = edge(1, 3, AgentNavigationGraph.EdgeType.DROP,
                new Point(50, 100), new Point(150, 200), 300);
        AgentNavigationGraph.Edge clearSecond = edge(3, 4, AgentNavigationGraph.EdgeType.JUMP,
                new Point(150, 200), new Point(350, 100), 300);
        List<AgentNavigationGraph.Region> regions = List.of(
                groundRegion(1, 0, 100, 100),
                groundRegion(2, 100, 200, 100),
                groundRegion(3, 100, 200, 200),
                groundRegion(4, 300, 400, 100));
        AgentNavigationGraph graph = graphWithRegionsAndEdges(regions, Map.of(
                1, List.of(crowdedFirst, clearFirst),
                2, List.of(crowdedSecond),
                3, List.of(clearSecond)));

        FootholdTree footholds = new FootholdTree(new Point(-1000, -1000), new Point(1000, 1000));
        for (AgentNavigationGraph.Region region : regions) {
            AgentNavigationGraph.Segment segment = region.segments.getFirst();
            footholds.insert(new Foothold(
                    new Point(segment.x1, segment.y1), new Point(segment.x2, segment.y2), segment.footholdId));
        }
        MapleMap map = mock(MapleMap.class);
        when(map.getFootholds()).thenReturn(footholds);
        Monster monster = mock(Monster.class);
        when(monster.isAlive()).thenReturn(true);
        when(monster.getPosition()).thenReturn(new Point(150, 100));
        when(map.getAllMonsters()).thenReturn(List.of(monster));
        Character bot = mock(Character.class);
        when(bot.getMap()).thenReturn(map);
        when(bot.getPosition()).thenReturn(new Point(50, 100));

        AgentNavigationGraph.Edge selected = AgentNavigationPathService.findNextEdge(
                graph, bot, 1, 4, new Point(350, 100));

        assertEquals(clearFirst, selected,
                "a populated intermediate platform should lose to a reasonably longer clear route");
    }

    @Test
    void seededVariationCanChooseBoundedReachableAlternativeAndIsStable() {
        AgentNavigationGraph.Edge viaFirst = edge(1, 2, AgentNavigationGraph.EdgeType.WALK,
                new Point(0, 100), new Point(100, 100), 100);
        AgentNavigationGraph.Edge viaSecond = edge(2, 3, AgentNavigationGraph.EdgeType.WALK,
                new Point(100, 100), new Point(200, 100), 100);
        AgentNavigationGraph.Edge direct = edge(1, 3, AgentNavigationGraph.EdgeType.WALK,
                new Point(0, 100), new Point(200, 100), 210);
        AgentNavigationGraph graph = graphWithRegionsAndEdges(
                List.of(
                        groundRegion(1, 0, 100, 100),
                        groundRegion(2, 100, 200, 100),
                        groundRegion(3, 200, 300, 100)),
                Map.of(1, List.of(viaFirst, direct), 2, List.of(viaSecond)));
        Character bot = mock(Character.class);
        when(bot.getMap()).thenReturn(null);
        when(bot.getPosition()).thenReturn(new Point(0, 100));

        AgentNavigationGraph.Edge selected = null;
        long selectedSeed = -1L;
        for (long seed = 0L; seed < 10_000L; seed++) {
            AgentMapleIslandTravelRuntime.RouteVariation variation =
                    new AgentMapleIslandTravelRuntime.RouteVariation(seed, 2.0d);
            AgentNavigationGraph.Edge candidate = AgentNavigationPathService.findNextEdgeVaried(
                    graph, bot, 1, 3, new Point(200, 100), variation);
            if (candidate == direct) {
                selected = candidate;
                selectedSeed = seed;
                break;
            }
        }

        assertEquals(direct, selected);
        assertTrue(direct.cost <= Math.ceil((viaFirst.cost + viaSecond.cost) * 2.0d));
        assertEquals(direct, AgentNavigationPathService.findNextEdgeVaried(
                graph, bot, 1, 3, new Point(200, 100),
                new AgentMapleIslandTravelRuntime.RouteVariation(selectedSeed, 2.0d)));
    }

    @Test
    void transitionVariationIsNeverCheaperAndHonorsStretchBound() {
        AgentNavigationGraph.Edge edge = edge(1, 2, AgentNavigationGraph.EdgeType.JUMP,
                new Point(10, 100), new Point(80, 60), 250);
        AgentMapleIslandTravelRuntime.RouteVariation variation =
                new AgentMapleIslandTravelRuntime.RouteVariation(123L, 1.2d);

        int varied = AgentNavigationPathService.variedTransitionCost(250, edge, variation);

        assertTrue(varied >= 250);
        assertTrue(varied <= 300);
    }

    @Test
    void measureOptimalityRunsAgentOwnedSearch() {
        AgentNavigationGraph graph = graphWithRegionsAndEdges(
                List.of(
                        groundRegion(1, 0, 100, 100),
                        groundRegion(2, 100, 200, 100)),
                Map.of(1, List.of(edge(1, 2, AgentNavigationGraph.EdgeType.WALK,
                        new Point(0, 100), new Point(100, 100), 10))));

        AgentNavigationPathService.PathOptimality optimality = AgentNavigationPathService.measureOptimality(
                graph, null, new Point(0, 100), 1, 2, new Point(100, 100));

        assertTrue(optimality.reachable());
        assertFalse(optimality.suboptimal());
        assertEquals(0, optimality.costDelta());
    }

    @Test
    void cappedMovementSearchReturnsClosestReachedFrontier() {
        AgentNavigationGraph graph = graphWithRegionsAndEdges(
                List.of(
                        groundRegion(1, 0, 100, 100),
                        groundRegion(2, 100, 200, 100),
                        groundRegion(3, 200, 300, 100)),
                Map.of(
                        1, List.of(edge(1, 2, AgentNavigationGraph.EdgeType.WALK,
                                new Point(0, 100), new Point(100, 100), 10)),
                        2, List.of(edge(2, 3, AgentNavigationGraph.EdgeType.WALK,
                                new Point(100, 100), new Point(200, 100), 10))));

        AgentNavigationPathService.SearchOutcome outcome = AgentNavigationPathService.runSearch(
                graph, null, new Point(0, 100), 1, 3, new Point(200, 100),
                "committed", true, false, 1);

        assertTrue(outcome.capped());
        assertTrue(outcome.bestEffort());
        assertFalse(outcome.reached());
        assertEquals(1, outcome.path().size());
        assertEquals(2, outcome.finalRegionId());
    }

    @Test
    void cappedScoringSearchDoesNotReturnPartialRoute() {
        AgentNavigationGraph graph = graphWithRegionsAndEdges(
                List.of(
                        groundRegion(1, 0, 100, 100),
                        groundRegion(2, 100, 200, 100),
                        groundRegion(3, 200, 300, 100)),
                Map.of(
                        1, List.of(edge(1, 2, AgentNavigationGraph.EdgeType.WALK,
                                new Point(0, 100), new Point(100, 100), 10)),
                        2, List.of(edge(2, 3, AgentNavigationGraph.EdgeType.WALK,
                                new Point(100, 100), new Point(200, 100), 10))));

        AgentNavigationPathService.SearchOutcome outcome = AgentNavigationPathService.runSearch(
                graph, null, new Point(0, 100), 1, 3, new Point(200, 100),
                "target-score", true, false, 1);

        assertTrue(outcome.capped());
        assertFalse(outcome.bestEffort());
        assertTrue(outcome.path().isEmpty());
    }

    @Test
    void disconnectedComponentsExitWithoutSearch() {
        AgentNavigationGraph graph = graphWithRegionsAndEdges(
                List.of(groundRegion(1, 0, 100, 100), groundRegion(2, 200, 300, 100)),
                Map.of());

        AgentNavigationPathService.SearchOutcome outcome = AgentNavigationPathService.runSearch(
                graph, null, new Point(0, 100), 1, 2, new Point(200, 100),
                "committed", true, false);

        assertFalse(outcome.reached());
        assertFalse(outcome.capped());
        assertEquals(0, outcome.expandedNodes());
    }

    @Test
    void retreatProbeKeepsOptimalRouteCost() {
        AgentNavigationGraph.Edge first = edge(1, 2, AgentNavigationGraph.EdgeType.WALK,
                new Point(0, 100), new Point(100, 100), 100);
        AgentNavigationGraph.Edge second = edge(2, 3, AgentNavigationGraph.EdgeType.WALK,
                new Point(100, 100), new Point(200, 100), 100);
        AgentNavigationGraph.Edge direct = edge(1, 3, AgentNavigationGraph.EdgeType.JUMP,
                new Point(0, 100), new Point(200, 100), 250);
        AgentNavigationGraph graph = graphWithRegionsAndEdges(
                List.of(
                        groundRegion(1, 0, 100, 100),
                        groundRegion(2, 100, 200, 100),
                        groundRegion(3, 200, 300, 100)),
                Map.of(1, List.of(direct, first), 2, List.of(second)));

        List<AgentNavigationGraph.Edge> path = AgentNavigationPathService.findPathForRetreatProbe(
                graph, null, new Point(0, 100), 1, 3, new Point(200, 100));

        assertEquals(List.of(first, second), path);
        assertEquals(200, path.stream().mapToInt(edge -> edge.cost).sum());
    }

    private static AgentNavigationGraph graphWithRegion(AgentNavigationGraph.Region region) {
        return new AgentNavigationGraph(
                1,
                1,
                AgentMovementProfile.base(),
                List.of(region),
                Map.of(region.id, region),
                Map.of(),
                Map.of(),
                Set.of());
    }

    private static AgentNavigationGraph graphWithRegionsAndEdges(List<AgentNavigationGraph.Region> regions,
                                                                  Map<Integer, List<AgentNavigationGraph.Edge>> outgoingByRegionId) {
        Map<Integer, AgentNavigationGraph.Region> regionsById = new java.util.HashMap<>();
        Map<Integer, Integer> regionIdByFootholdId = new java.util.HashMap<>();
        for (AgentNavigationGraph.Region region : regions) {
            regionsById.put(region.id, region);
            for (AgentNavigationGraph.Segment segment : region.segments) {
                regionIdByFootholdId.put(segment.footholdId, region.id);
            }
        }
        return new AgentNavigationGraph(
                1,
                1,
                AgentMovementProfile.base(),
                regions,
                regionsById,
                regionIdByFootholdId,
                outgoingByRegionId,
                Set.of());
    }

    private static AgentNavigationGraph.Region groundRegion(int id, int x1, int x2, int y) {
        return new AgentNavigationGraph.Region(id, List.of(new AgentNavigationGraph.Segment(
                new Foothold(new Point(x1, y), new Point(x2, y), id))));
    }

    private static AgentNavigationGraph.Edge edge(int fromRegionId,
                                                  int toRegionId,
                                                  AgentNavigationGraph.EdgeType type,
                                                  Point start,
                                                  Point end,
                                                  int cost) {
        return new AgentNavigationGraph.Edge(fromRegionId, toRegionId, type,
                start, end, 0, 0, 0, 0, 0, cost);
    }
}
