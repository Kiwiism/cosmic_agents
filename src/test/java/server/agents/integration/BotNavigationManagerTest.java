package server.agents.integration;

import server.agents.runtime.AgentRuntimeEntry;

import server.agents.capabilities.movement.AgentClimbStateRuntime;


import server.agents.capabilities.movement.AgentMovementStateRuntime;
import server.agents.capabilities.navigation.AgentNavigationCommittedEdgeService;
import server.agents.capabilities.movement.AgentClimbMovementService;
import server.agents.capabilities.movement.AgentGroundMovementRuntimeService;
import server.agents.capabilities.movement.AgentMovementPhysicsConfig;
import server.agents.capabilities.movement.AgentRopeMovementService;
import server.agents.capabilities.navigation.AgentNavigationEdgeReadinessService;
import server.agents.capabilities.navigation.AgentNavigationGraphService;
import server.agents.capabilities.navigation.AgentNavigationTargetService;
import server.agents.capabilities.navigation.AgentNavigationLaunchWindowService;
import server.agents.capabilities.navigation.AgentNavigationPhysicsService;
import server.agents.capabilities.navigation.AgentNavigationPathService;
import server.agents.capabilities.navigation.AgentNavigationRegionService;
import server.agents.capabilities.navigation.AgentNavigationRopeEdgeService;

import server.agents.capabilities.navigation.AgentNavigationGraph;

import server.agents.capabilities.navigation.AgentNavigationMapLoader;
import server.agents.capabilities.navigation.AgentNavigationWaypointService;

import server.agents.capabilities.movement.AgentMovementProfile;
import server.agents.capabilities.movement.AgentFallbackMovementService;

import client.Character;
import constants.game.CharacterStance;
import org.junit.jupiter.api.BeforeAll;
import org.mockito.MockedStatic;
import server.agents.runtime.AgentModeStateRuntime;
import server.agents.capabilities.navigation.AgentNavigationDebugStateRuntime;
import server.agents.runtime.AgentSessionLifecycleRuntime;
import server.maps.MapleMap;
import server.maps.Foothold;
import server.maps.Rope;
import org.junit.jupiter.api.Test;
import server.maps.FootholdTree;

import java.awt.*;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class BotNavigationManagerTest {
    private static volatile MapleMap kerningCached;

    private static MapleMap kerning() {
        MapleMap v = kerningCached;
        if (v != null) return v;
        synchronized (BotNavigationManagerTest.class) {
            if (kerningCached == null) {
                kerningCached = AgentNavigationMapLoader.loadMapGeometry(103000000);
            }
            return kerningCached;
        }
    }

    @BeforeAll
    static void loadMaps() {
        // Avoid loading big maps; create a functionally equivalent synthetic test when possible.
        // Map fixtures are lazy-loaded on first use.
        System.setProperty("wz-path", Path.of("wz").toAbsolutePath().toString());
    }

    @Test
    void shouldPromoteFirstActionableEdgePastLeadingZeroDistanceWalks() {
        AgentNavigationGraph.Edge collapsed = AgentNavigationPathService.collapseLeadingWalkEdges(List.of(
                new AgentNavigationGraph.Edge(1, 2, AgentNavigationGraph.EdgeType.WALK,
                        new Point(528, -914), new Point(528, -914),
                        0, 0, 0, 0, 0, 50),
                new AgentNavigationGraph.Edge(2, 3, AgentNavigationGraph.EdgeType.WALK,
                        new Point(528, -914), new Point(528, -914),
                        0, 0, 0, 0, 0, 50),
                new AgentNavigationGraph.Edge(3, 4, AgentNavigationGraph.EdgeType.JUMP,
                        new Point(540, -914), new Point(612, -980),
                        9, 0, 0, 0, 0, 300)
        ));

        assertNotNull(collapsed);
        assertEquals(AgentNavigationGraph.EdgeType.JUMP, collapsed.type);
        assertEquals(1, collapsed.fromRegionId);
        assertEquals(4, collapsed.toRegionId);
        assertEquals(new Point(540, -914), collapsed.startPoint);
        assertEquals(new Point(612, -980), collapsed.endPoint);
        assertEquals(400, collapsed.cost);
    }

    @Test
    void shouldKeepFirstRealWalkInsteadOfCollapsingPastLaterZeroDistanceHandoff() {
        AgentNavigationGraph.Edge collapsed = AgentNavigationPathService.collapseLeadingWalkEdges(List.of(
                new AgentNavigationGraph.Edge(24, 22, AgentNavigationGraph.EdgeType.WALK,
                        new Point(-947, 153), new Point(-751, 142),
                        0, 0, 0, 0, 0, 120),
                new AgentNavigationGraph.Edge(22, 20, AgentNavigationGraph.EdgeType.WALK,
                        new Point(-751, 142), new Point(-751, 142),
                        0, 0, 0, 0, 0, 50),
                new AgentNavigationGraph.Edge(20, 27, AgentNavigationGraph.EdgeType.CLIMB,
                        new Point(-437, 121), new Point(-437, 84),
                        0, 0, -437, 84, 121, 250)
        ));

        assertNotNull(collapsed);
        assertEquals(AgentNavigationGraph.EdgeType.WALK, collapsed.type);
        assertEquals(24, collapsed.fromRegionId);
        assertEquals(22, collapsed.toRegionId);
        assertEquals(new Point(-947, 153), collapsed.startPoint);
        assertEquals(new Point(-751, 142), collapsed.endPoint);
        assertEquals(120, collapsed.cost);
    }

    @Test
    void shouldDropLeadingWalkChainWhenItConsumesNoMovement() {
        AgentNavigationGraph.Edge collapsed = AgentNavigationPathService.collapseLeadingWalkEdges(List.of(
                new AgentNavigationGraph.Edge(181, 184, AgentNavigationGraph.EdgeType.WALK,
                        new Point(565, -2135), new Point(565, -2135),
                        0, 0, 0, 0, 0, 50),
                new AgentNavigationGraph.Edge(184, 190, AgentNavigationGraph.EdgeType.WALK,
                        new Point(565, -2135), new Point(565, -2135),
                        0, 0, 0, 0, 0, 50)
        ));

        assertNull(collapsed);
    }

    @Test
    void shouldOnlySnapZeroStepClimbExitAtRopeTop() {
        Rope rope = new Rope(675, 143, 215, false);
        AgentNavigationGraph.Edge topExit = new AgentNavigationGraph.Edge(
                49, 45, AgentNavigationGraph.EdgeType.CLIMB,
                new Point(675, 143), new Point(675, 141),
                0, 0, 675, 143, 215, 250
        );
        AgentNavigationGraph.Edge bottomExit = new AgentNavigationGraph.Edge(
                49, 45, AgentNavigationGraph.EdgeType.CLIMB,
                new Point(675, 215), new Point(675, 215),
                0, 0, 675, 143, 215, 250
        );

        assertTrue(AgentNavigationRopeEdgeService.isTopStepOffExit(rope, new Point(675, 145), topExit));
        assertTrue(AgentNavigationRopeEdgeService.isTopStepOffExit(rope, new Point(675, 171), topExit));
        assertFalse(AgentNavigationRopeEdgeService.isTopStepOffExit(rope, new Point(675, 215), bottomExit));
    }

    @Test
    void shouldOnlyExecuteStraightDownJumpInsideLaunchWindow() {
        MapleMap map = new MapleMap(910000031, 0, 0, 910000031, 1.0f);
        FootholdTree footholds = new FootholdTree(new Point(-2000, -2000), new Point(2000, 2000));
        footholds.insert(new Foothold(new Point(0, 0), new Point(200, 0), 1));
        footholds.insert(new Foothold(new Point(40, 120), new Point(160, 120), 2));
        map.setFootholds(footholds);

        AgentNavigationGraph graph = AgentNavigationGraphService.rebuildGraph(map);
        AgentNavigationGraph.Edge downJump = findFirstStraightDropEdge(graph);

        assertNotNull(downJump, "fixture should produce a straight down-jump edge");
        assertTrue(downJump.launchMinX < downJump.launchMaxX);

        int insideX = (downJump.launchMinX + downJump.launchMaxX) / 2;
        int outsideX = downJump.launchMaxX + 1;

        assertTrue(AgentNavigationEdgeReadinessService.canExecuteDropFromCurrentPosition(
                graph, new Point(insideX, 0), downJump));
        assertFalse(AgentNavigationEdgeReadinessService.canExecuteDropFromCurrentPosition(
                graph, new Point(outsideX, 0), downJump));
    }

    @Test
    void shouldUsePreciseTargetForCommittedWalkRegionHandoffs() {
        AgentNavigationGraph.Edge walkHandoff = new AgentNavigationGraph.Edge(
                343, 341, AgentNavigationGraph.EdgeType.WALK,
                new Point(28, -1167), new Point(13, -1170),
                0, 0, 0, 0, 0, 100
        );
        AgentNavigationGraph.Edge noMoveWalk = new AgentNavigationGraph.Edge(
                343, 342, AgentNavigationGraph.EdgeType.WALK,
                new Point(28, -1167), new Point(28, -1167),
                0, 0, 0, 0, 0, 50
        );

        assertTrue(AgentNavigationPathService.shouldUsePreciseWalkTarget(walkHandoff));
        assertFalse(AgentNavigationPathService.shouldUsePreciseWalkTarget(noMoveWalk));
    }

    @Test
    void shouldDropStaleCollapsedWalkEdgeWhenBotEntersIntermediateRegion() {
        // Regression: pathlog-SLASH-2026-04-02 ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡Ãƒâ€šÃ‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€šÃ‚Â collapsed r358ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€šÃ‚Â ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÂ¢Ã¢â‚¬Å¾Ã‚Â¢r355 WALK edge (via r359),
        // bot steps into r359 mid-traverse; old code returned null here (fromRegionId mismatch),
        // dropping the edge every tick and causing an oscillation loop.
        AgentNavigationGraph.Edge collapsedWalk = new AgentNavigationGraph.Edge(
                358, 355, AgentNavigationGraph.EdgeType.WALK,
                new Point(46, -61), new Point(54, -58),
                0, 0, 0, 0, 0, 100
        );
        Character bot = mock(Character.class);
        when(bot.getMap()).thenReturn(mock(MapleMap.class));
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        AgentNavigationDebugStateRuntime.setActiveNavigationEdge(entry, collapsedWalk);
        AgentNavigationDebugStateRuntime.setNavTargetRegionId(entry, 355);
        AgentNavigationGraph graph = mock(AgentNavigationGraph.class);

        // Bot is in intermediate region 359 ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡Ãƒâ€šÃ‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€šÃ‚Â neither source (358) nor destination (355)
        AgentNavigationGraph.Edge result = AgentNavigationCommittedEdgeService.reuseCommittedEdge(graph, entry, 359, 355);

        assertNull(result, "Stale collapsed WALK edge must be dropped once the bot leaves its source region");
    }

    @Test
    void shouldDropCollapsedWalkEdgeOnceDestinationRegionReached() {
        AgentNavigationGraph.Edge collapsedWalk = new AgentNavigationGraph.Edge(
                358, 355, AgentNavigationGraph.EdgeType.WALK,
                new Point(46, -61), new Point(54, -58),
                0, 0, 0, 0, 0, 100
        );
        Character bot = mock(Character.class);
        when(bot.getMap()).thenReturn(mock(MapleMap.class));
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        AgentNavigationDebugStateRuntime.setActiveNavigationEdge(entry, collapsedWalk);
        AgentNavigationDebugStateRuntime.setNavTargetRegionId(entry, 355);
        AgentNavigationGraph graph = mock(AgentNavigationGraph.class);

        AgentNavigationGraph.Edge result = AgentNavigationCommittedEdgeService.reuseCommittedEdge(graph, entry, 355, 355);

        assertNull(result, "WALK edge must be dropped once bot reaches destination region");
    }

    @Test
    void shouldResolveFollowTargetRegionFromSiblingAgentThroughAgentRuntime() {
        MapleMap map = mock(MapleMap.class);
        AgentNavigationGraph graph = mock(AgentNavigationGraph.class);
        Point siblingPosition = new Point(320, 120);
        Character owner = mock(Character.class);
        Character bot = mock(Character.class);
        Character sibling = mock(Character.class);
        when(owner.getId()).thenReturn(100);
        when(bot.getId()).thenReturn(200);
        when(sibling.getId()).thenReturn(300);
        when(sibling.isLoggedinWorld()).thenReturn(true);
        when(sibling.getMap()).thenReturn(map);
        when(sibling.getPosition()).thenReturn(siblingPosition);
        when(sibling.getStance()).thenReturn(CharacterStance.ROPE_RIGHT_STANCE);
        when(graph.findRopeRegionId(siblingPosition)).thenReturn(77);

        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, owner, null);
        AgentRuntimeEntry siblingEntry = new AgentRuntimeEntry(sibling, owner, null);
        AgentModeStateRuntime.setFollowing(entry, true);
        AgentModeStateRuntime.setFollowTargetId(entry, sibling.getId());

        try (MockedStatic<AgentSessionLifecycleRuntime> lifecycle =
                     mockStatic(AgentSessionLifecycleRuntime.class)) {
            lifecycle.when(() -> AgentSessionLifecycleRuntime.getBotEntries(owner.getId()))
                    .thenReturn(List.of(siblingEntry));

            int regionId = AgentNavigationRegionService.resolveTargetRegionId(graph, entry, map, siblingPosition);

            assertEquals(77, regionId);
        }
    }

    @Test
    void shouldUseGraphDerivedJumpLaunchWindowInsteadOfGenericTolerance() {
        Foothold foothold = new Foothold(new Point(500, 107), new Point(530, 107), 1);
        AgentNavigationGraph.Region fromRegion = new AgentNavigationGraph.Region(20, List.of(new AgentNavigationGraph.Segment(foothold)));
        AgentNavigationGraph graph = mock(AgentNavigationGraph.class);
        when(graph.getRegion(20)).thenReturn(fromRegion);

        AgentNavigationGraph.Edge jump = new AgentNavigationGraph.Edge(
                20, 15, AgentNavigationGraph.EdgeType.JUMP,
                new Point(520, 107), new Point(480, 36),
                516, 523, -8, 0, 0, 0, 0, 850
        );

        assertFalse(AgentNavigationLaunchWindowService.isWithinJumpLaunchWindow(graph, new Point(513, 107), jump));
        assertFalse(AgentNavigationLaunchWindowService.isWithinJumpLaunchWindow(graph, new Point(514, 107), jump));
        assertTrue(AgentNavigationLaunchWindowService.isWithinJumpLaunchWindow(graph, new Point(516, 107), jump));
        assertTrue(AgentNavigationLaunchWindowService.isWithinJumpLaunchWindow(graph, new Point(523, 107), jump));
        assertFalse(AgentNavigationLaunchWindowService.isWithinJumpLaunchWindow(graph, new Point(525, 107), jump));
        assertFalse(AgentNavigationLaunchWindowService.isWithinJumpLaunchWindow(graph, new Point(526, 107), jump));
        assertFalse(AgentNavigationLaunchWindowService.isWithinJumpLaunchWindow(graph, new Point(520, 160), jump));
    }

    @Test
    void shouldPickStableJumpLaunchTargetInsideWindow() {
        MapleMap map = new MapleMap(910000010, 0, 0, 910000010, 1.0f);
        server.maps.FootholdTree footholds = new server.maps.FootholdTree(new Point(-2000, -2000), new Point(2000, 2000));
        footholds.insert(new Foothold(new Point(500, 107), new Point(530, 107), 1));
        map.setFootholds(footholds);
        AgentNavigationGraph graph = AgentNavigationGraphService.rebuildGraph(map);

        Character bot = mock(Character.class);
        when(bot.getMap()).thenReturn(map);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);

        AgentNavigationGraph.Edge jump = new AgentNavigationGraph.Edge(
                1, 15, AgentNavigationGraph.EdgeType.JUMP,
                new Point(520, 107), new Point(480, 36),
                516, 523, -8, 0, 0, 0, 0, 850
        );

        Point firstTarget = AgentNavigationWaypointService.selectJumpWaypoint(entry, new Point(449, 113), jump);
        Point secondTarget = AgentNavigationWaypointService.selectJumpWaypoint(entry, new Point(540, 113), jump);
        Point thirdTarget = AgentNavigationWaypointService.selectJumpWaypoint(entry, new Point(520, 113), jump);

        assertTrue(firstTarget.x >= 519 && firstTarget.x <= 520);
        assertEquals(107, firstTarget.y);
        assertEquals(firstTarget, secondTarget);
        assertEquals(firstTarget, thirdTarget);

        assertEquals(new Point(516, 107), AgentNavigationWaypointService.selectJumpWaypoint(graph, new Point(449, 113), jump));
        assertEquals(new Point(523, 107), AgentNavigationWaypointService.selectJumpWaypoint(graph, new Point(540, 113), jump));
        assertEquals(new Point(520, 107), AgentNavigationWaypointService.selectJumpWaypoint(graph, new Point(520, 113), jump));
    }

    @Test
    void shouldChooseTargetRegionEntryBasedOnInRegionPathTarget() {
        MapleMap map = new MapleMap(910000026, 0, 0, 910000026, 1.0f);
        AgentNavigationGraph.Region startRegion = new AgentNavigationGraph.Region(
                1, List.of(new AgentNavigationGraph.Segment(new Foothold(new Point(0, 100), new Point(100, 100), 1))));
        AgentNavigationGraph.Region targetRegion = new AgentNavigationGraph.Region(
                2, List.of(new AgentNavigationGraph.Segment(new Foothold(new Point(0, 200), new Point(200, 200), 2))));
        Map<Integer, AgentNavigationGraph.Region> regionsById = new HashMap<>();
        regionsById.put(1, startRegion);
        regionsById.put(2, targetRegion);
        AgentNavigationGraph.Edge leftEntry = new AgentNavigationGraph.Edge(
                1, 2, AgentNavigationGraph.EdgeType.CLIMB,
                new Point(50, 100), new Point(0, 200),
                0, 0, 0, 0, 0, 100
        );
        AgentNavigationGraph.Edge rightEntry = new AgentNavigationGraph.Edge(
                1, 2, AgentNavigationGraph.EdgeType.CLIMB,
                new Point(50, 100), new Point(200, 200),
                0, 0, 0, 0, 0, 100
        );
        AgentNavigationGraph graph = new AgentNavigationGraph(
                map.getId(),
                1,
                List.of(startRegion, targetRegion),
                regionsById,
                Map.of(1, 1, 2, 2),
                Map.of(1, List.of(leftEntry, rightEntry)),
                Set.of()
        );

        List<AgentNavigationGraph.Edge> leftPath = AgentNavigationPathService.findPath(
                graph, map, new Point(50, 100), 1, 2, new Point(40, 200));
        List<AgentNavigationGraph.Edge> rightPath = AgentNavigationPathService.findPath(
                graph, map, new Point(50, 100), 1, 2, new Point(160, 200));

        assertEquals(List.of(leftEntry), leftPath,
                "pathfinding should prefer the entry closest to the left-side in-region target");
        assertEquals(List.of(rightEntry), rightPath,
                "pathfinding should prefer the entry closest to the clamped interior target, not a fixed nearest edge");
    }

    @Test
    void shouldRefreshStaleCommittedGroundDropWhenBestFirstEdgeChanges() {
        MapleMap map = new MapleMap(910000032, 0, 0, 910000032, 1.0f);
        FootholdTree footholds = new FootholdTree(new Point(-2000, -2000), new Point(2000, 2000));
        footholds.insert(new Foothold(new Point(0, 0), new Point(300, 0), 1));
        footholds.insert(new Foothold(new Point(0, 120), new Point(100, 120), 2));
        footholds.insert(new Foothold(new Point(200, 120), new Point(300, 120), 3));
        map.setFootholds(footholds);

        AgentNavigationGraph graph = AgentNavigationGraphService.rebuildGraph(map);
        Point botPos = new Point(50, 0);
        Point leftTarget = new Point(50, 120);
        Point rightTarget = new Point(250, 120);
        int startRegionId = graph.findRegionId(map, botPos);
        int leftTargetRegionId = graph.findRegionId(map, leftTarget);
        int rightTargetRegionId = graph.findRegionId(map, rightTarget);

        List<AgentNavigationGraph.Edge> leftPath = AgentNavigationPathService.findPath(
                graph, map, botPos, startRegionId, leftTargetRegionId, leftTarget);
        List<AgentNavigationGraph.Edge> rightPath = AgentNavigationPathService.findPath(
                graph, map, botPos, startRegionId, rightTargetRegionId, rightTarget);

        assertFalse(leftPath.isEmpty(), "fixture should produce a left-side drop path");
        assertFalse(rightPath.isEmpty(), "fixture should produce a right-side drop path");

        AgentNavigationGraph.Edge staleEdge = leftPath.getFirst();
        AgentNavigationGraph.Edge freshEdge = rightPath.getFirst();
        assertEquals(AgentNavigationGraph.EdgeType.DROP, staleEdge.type);
        assertEquals(AgentNavigationGraph.EdgeType.DROP, freshEdge.type);
        assertNotEquals(staleEdge.toRegionId, freshEdge.toRegionId,
                "regression requires different first actionable drop edges from the same source region");

        Character bot = mockBot(botPos, map);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        AgentMovementStateRuntime.setMovementProfile(entry, AgentMovementProfile.base());
        AgentModeStateRuntime.setFollowing(entry, true);
        AgentNavigationDebugStateRuntime.setActiveNavigationEdge(entry, staleEdge);
        AgentNavigationDebugStateRuntime.setNavTargetRegionId(entry, leftTargetRegionId);

        AgentNavigationTargetService.NavigationDirective directive =
                AgentNavigationTargetService.resolveTarget(entry, rightTarget, true);

        assertFalse(directive.consumedTick());
        assertEquals(freshEdge.toRegionId, ((AgentNavigationGraph.Edge) AgentNavigationDebugStateRuntime.activeNavigationEdge(entry)).toRegionId,
                "grounded reuse must discard a stale drop edge once the current best first edge changes");
        assertEquals(freshEdge.startPoint, ((AgentNavigationGraph.Edge) AgentNavigationDebugStateRuntime.activeNavigationEdge(entry)).startPoint);
    }

    @Test
    void shouldDropStaleCommittedGroundEdgeWhenLiveTargetRegionDiffersFromEdgeDestination() {
        MapleMap map = mock(MapleMap.class);
        AgentNavigationGraph.Region source = new AgentNavigationGraph.Region(
                1, List.of(new AgentNavigationGraph.Segment(new Foothold(new Point(0, 100), new Point(200, 100), 1))));
        AgentNavigationGraph.Region staleLower = new AgentNavigationGraph.Region(
                2, List.of(new AgentNavigationGraph.Segment(new Foothold(new Point(0, 220), new Point(100, 220), 2))));
        AgentNavigationGraph.Region ownerUpper = new AgentNavigationGraph.Region(
                3, List.of(new AgentNavigationGraph.Segment(new Foothold(new Point(90, 40), new Point(210, 40), 3))));
        Map<Integer, AgentNavigationGraph.Region> regionsById = new HashMap<>();
        regionsById.put(1, source);
        regionsById.put(2, staleLower);
        regionsById.put(3, ownerUpper);

        AgentNavigationGraph.Edge staleDrop = new AgentNavigationGraph.Edge(
                1, 2, AgentNavigationGraph.EdgeType.DROP,
                new Point(20, 100), new Point(40, 220),
                20, 20, 0, 0, 0, 0, 0, 300);
        AgentNavigationGraph.Edge directJump = new AgentNavigationGraph.Edge(
                1, 3, AgentNavigationGraph.EdgeType.JUMP,
                new Point(100, 100), new Point(140, 40),
                90, 110, 6, 0, 0, 0, 0, 250);
        AgentNavigationGraph graph = new AgentNavigationGraph(
                910000213, 1, AgentMovementProfile.base(),
                List.of(source, staleLower, ownerUpper),
                regionsById,
                Map.of(1, 1, 2, 2, 3, 3),
                Map.of(1, List.of(staleDrop, directJump)),
                Set.of());

        Point botPos = new Point(100, 100);
        Point ownerPos = new Point(140, 40);
        List<AgentNavigationGraph.Edge> path = AgentNavigationPathService.findPath(
                graph, map, botPos, 1, 3, ownerPos);
        assertEquals(List.of(directJump), path,
                "synthetic fixture should prefer the direct jump to the live owner region");

        Character bot = mockBot(botPos, map);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        AgentNavigationDebugStateRuntime.setActiveNavigationEdge(entry, staleDrop);
        AgentNavigationDebugStateRuntime.setNavTargetRegionId(entry, staleDrop.toRegionId);

        assertNull(AgentNavigationCommittedEdgeService.reuseCommittedEdge(graph, entry, 1, 3),
                "non-AI reuse must drop stale grounded edges whose destination no longer matches the live target");
    }

    @Test
    void shouldRetainCommittedGroundEdgeWhenAlternativeLeadsToSameDestinationRegion() {
        AgentNavigationGraph.Edge committedDrop = new AgentNavigationGraph.Edge(
                80, 83, AgentNavigationGraph.EdgeType.DROP,
                new Point(7, -34), new Point(-84, 99),
                7, 7, 0, 0, 0, 655
        );
        AgentNavigationGraph.Edge replacementJump = new AgentNavigationGraph.Edge(
                80, 83, AgentNavigationGraph.EdgeType.JUMP,
                new Point(5, -34), new Point(-99, 95),
                -35, 45, -7, 0, 0, 0, 0, 750
        );

        assertTrue(AgentNavigationCommittedEdgeService.shouldRetainCommittedGroundEdge(committedDrop, replacementJump),
                "equivalent first exits into the same destination region should not thrash mid-approach");
    }

    @Test
    void shouldNotRetainCommittedGroundEdgeWhenAlternativeChangesDestinationRegion() {
        AgentNavigationGraph.Edge committedDrop = new AgentNavigationGraph.Edge(
                1, 2, AgentNavigationGraph.EdgeType.DROP,
                new Point(10, 0), new Point(10, 100),
                10, 10, 0, 0, 0, 300
        );
        AgentNavigationGraph.Edge replacementDrop = new AgentNavigationGraph.Edge(
                1, 3, AgentNavigationGraph.EdgeType.DROP,
                new Point(40, 0), new Point(40, 100),
                40, 40, 0, 0, 0, 300
        );

        assertFalse(AgentNavigationCommittedEdgeService.shouldRetainCommittedGroundEdge(committedDrop, replacementDrop),
                "grounded replans must still refresh when the better first edge changes destination region");
    }

    @Test
    void shouldUseRawTargetWhileMovementGraphWarmsInBackground() {
        MapleMap map = new MapleMap(910000030, 0, 0, 910000030, 1.0f);
        server.maps.FootholdTree footholds = new server.maps.FootholdTree(new Point(-2000, -2000), new Point(2000, 2000));
        footholds.insert(new Foothold(new Point(0, 100), new Point(200, 100), 1));
        map.setFootholds(footholds);

        Character bot = mockBot(new Point(20, 100), map);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        AgentMovementStateRuntime.setMovementProfile(entry, new AgentMovementProfile(105, 105));

        AgentNavigationTargetService.NavigationDirective directive =
                AgentNavigationTargetService.resolveTarget(entry, new Point(180, 100), true);

        assertFalse(directive.consumedTick());
        assertEquals(new Point(180, 100), directive.targetPos());
        assertEquals("graph-warmup", AgentNavigationDebugStateRuntime.lastDecision(entry));
        assertTrue(AgentNavigationDebugStateRuntime.graphWarmupFallback(entry));
        assertNull(AgentNavigationDebugStateRuntime.activeNavigationEdge(entry));

        AgentNavigationGraphService.getGraph(map, AgentMovementStateRuntime.movementProfile(entry));
    }

    @Test
    void shouldHoldCurrentPositionOnlyAtNonTopClimbExitLaunchAnchor() {
        Character bot = mock(Character.class);
        when(bot.getMap()).thenReturn(kerning());
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        AgentClimbStateRuntime.setClimbingOnRope(entry, new Rope(-1251, -137, 2, true));

        AgentNavigationGraph.Edge climbExit = new AgentNavigationGraph.Edge(
                189, 157, AgentNavigationGraph.EdgeType.CLIMB,
                new Point(-1251, -107), new Point(-1132, 156),
                8, 0, -1251, -137, 2, 650
        );

        assertEquals(new Point(-1251, -107),
                AgentNavigationWaypointService.selectClimbWaypoint(entry, new Point(-1251, -107), climbExit));
        assertEquals(new Point(-1251, -107),
                AgentNavigationWaypointService.selectClimbWaypoint(entry, new Point(-1251, -109), climbExit));
    }

    @Test
    void shouldKeepSteeringToClimbLaunchAnchorWhileBelowExitAnchor() {
        Character bot = mock(Character.class);
        when(bot.getMap()).thenReturn(kerning());
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        AgentClimbStateRuntime.setClimbingOnRope(entry, new Rope(-1251, -137, 2, true));

        AgentNavigationGraph.Edge climbExit = new AgentNavigationGraph.Edge(
                189, 157, AgentNavigationGraph.EdgeType.CLIMB,
                new Point(-1251, -107), new Point(-1132, 156),
                8, 0, -1251, -137, 2, 650
        );

        assertEquals(new Point(-1251, -107),
                AgentNavigationWaypointService.selectClimbWaypoint(entry, new Point(-1251, -104), climbExit));
    }

    @Test
    void shouldKeepSteeringToNonTopRopeExitAnchorWhileAboveExitAnchor() {
        Character bot = mock(Character.class);
        when(bot.getMap()).thenReturn(kerning());
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        AgentClimbStateRuntime.setClimbingOnRope(entry, new Rope(707, -769, -455, false));

        AgentNavigationGraph.Edge climbExit = new AgentNavigationGraph.Edge(
                104, 101, AgentNavigationGraph.EdgeType.CLIMB,
                new Point(707, -734), new Point(627, -602),
                -6, 0, 707, -769, -455, 950
        );

        assertEquals(new Point(707, -734),
                AgentNavigationWaypointService.selectClimbWaypoint(entry, new Point(707, -764), climbExit));
    }

    @Test
    void shouldKeepCommittedRopeExitClimbEdgeWhileAirborne() {
        Character bot = mock(Character.class);
        when(bot.getMap()).thenReturn(mock(MapleMap.class));
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        AgentMovementStateRuntime.setInAir(entry, true);
        AgentNavigationDebugStateRuntime.setActiveNavigationEdge(entry, new AgentNavigationGraph.Edge(
                25, 14, AgentNavigationGraph.EdgeType.CLIMB,
                new Point(-437, -181), new Point(-473, -211),
                -8, 0, -437, -1471, 84, 250
        ));
        AgentNavigationDebugStateRuntime.setNavTargetRegionId(entry, 14);
        AgentNavigationGraph graph = mock(AgentNavigationGraph.class);

        AgentNavigationGraph.Edge reused = AgentNavigationCommittedEdgeService.reuseCommittedEdge(graph, entry, 20, 14);

        assertEquals(AgentNavigationDebugStateRuntime.activeNavigationEdge(entry), reused);
    }

    @Test
    void shouldUseTopRopeEntryInsteadOfDroppingToBottomInLithHarbor() {
        MapleMap lithHarbor = AgentNavigationMapLoader.loadMapGeometry(104000000);
        AgentNavigationGraph graph = AgentNavigationGraphService.rebuildGraph(lithHarbor);
        Point start = new Point(1189, 287);
        Point target = new Point(1265, 331);
        int startRegionId = graph.findRegionId(lithHarbor, start);
        int targetRegionId = graph.findRopeRegionId(target);

        List<AgentNavigationGraph.Edge> path = AgentNavigationPathService.findPath(
                graph, lithHarbor, start, startRegionId, targetRegionId, target);

        assertFalse(path.isEmpty());
        assertEquals(AgentNavigationGraph.EdgeType.CLIMB, path.getFirst().type);
        assertEquals(targetRegionId, path.getFirst().toRegionId);
        assertTrue(path.getFirst().endPoint.y <= target.y + AgentMovementPhysicsConfig.configuredJumpYThreshold());
    }

    @Test
    void shouldNotLaunchVerticalRopeEntryFromOutsideRopeGrabWindow() {
        MapleMap lithHarbor = AgentNavigationMapLoader.loadMapGeometry(104000000);
        AgentNavigationGraph graph = AgentNavigationGraphService.rebuildGraph(lithHarbor);
        Point start = new Point(1245, 647);
        Point target = new Point(1265, 331);
        int startRegionId = graph.findRegionId(lithHarbor, start);
        int targetRegionId = graph.findRopeRegionId(target);
        // The intent of this test is to verify out-of-launch-window rope entries are rejected.
        // Look up the vertical (stepX=0) rope-entry edge in the graph directly rather than via
        // findPath, which now picks the time-cheapest entry (often a horizontal jump-grab).
        AgentNavigationGraph.Edge ropeEntry = graph.getOutgoing(startRegionId).stream()
                .filter(edge -> edge.type == AgentNavigationGraph.EdgeType.CLIMB
                        && edge.toRegionId == targetRegionId
                        && edge.launchStepX == 0
                        && edge.containsLaunchX(1257))
                .findFirst()
                .orElse(null);
        assertNotNull(ropeEntry, "expected a vertical (stepX=0) rope-entry CLIMB edge containing x=1257");
        assertTrue(ropeEntry.launchMinX < ropeEntry.launchMaxX);

        AgentNavigationGraph.Region fromRegion = graph.getRegion(ropeEntry.fromRegionId);
        int outsideLaunchX = ropeEntry.launchMaxX < fromRegion.maxX
                ? ropeEntry.launchMaxX + 1
                : ropeEntry.launchMinX - 1;
        assertFalse(ropeEntry.containsLaunchX(outsideLaunchX));

        Character bot = mockBot(fromRegion.pointAt(outsideLaunchX), lithHarbor);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        AgentMovementStateRuntime.setMovementProfile(entry, AgentMovementProfile.base());
        AgentNavigationDebugStateRuntime.setActiveNavigationEdge(entry, ropeEntry);
        AgentNavigationDebugStateRuntime.setNavTargetRegionId(entry, targetRegionId);

        AgentNavigationTargetService.NavigationDirective directive =
                AgentNavigationTargetService.resolveTarget(entry, target, true);

        assertFalse(directive.consumedTick());
        assertFalse(AgentMovementStateRuntime.inAir(entry));
        assertEquals("climb-pos", AgentNavigationDebugStateRuntime.lastEdgeBlockReason(entry));
    }

    @Test
    void shouldJumpOffTopRopeBeforePhysicsAutoDismountsToUpperPlatform() {
        MapleMap lithHarbor = AgentNavigationMapLoader.loadMapGeometry(104000000);
        AgentNavigationGraph graph = AgentNavigationGraphService.rebuildGraph(lithHarbor);
        Point botPos = new Point(1265, 294);
        Point target = new Point(1802, 647);
        int startRegionId = graph.findRopeRegionId(botPos);
        int targetRegionId = graph.findRegionId(lithHarbor, target);
        List<AgentNavigationGraph.Edge> path = AgentNavigationPathService.findPath(
                graph, lithHarbor, botPos, startRegionId, targetRegionId, target);
        assertFalse(path.isEmpty());
        AgentNavigationGraph.Edge ropeExit = path.getFirst();
        assertEquals(AgentNavigationGraph.EdgeType.CLIMB, ropeExit.type);
        assertTrue(ropeExit.launchStepX > 0);
        assertEquals(new Point(1265, 290), ropeExit.startPoint);

        Character bot = mockBot(botPos, lithHarbor);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        AgentMovementStateRuntime.setMovementProfile(entry, AgentMovementProfile.base());
        AgentClimbStateRuntime.setClimbingOnRope(entry, new Rope(1265, 289, 597, false));

        AgentNavigationTargetService.NavigationDirective directive =
                AgentNavigationTargetService.resolveTarget(entry, target, true);

        assertTrue(directive.consumedTick());
        assertTrue(AgentMovementStateRuntime.inAir(entry));
        assertFalse(AgentClimbStateRuntime.climbing(entry));
        assertEquals(new Point(1265, 290), bot.getPosition());
    }

    @Test
    void shouldPreferCurrentRopeRegionAtRopeTopWhenBotStanceIsClimbing() {
        MapleMap map = topRopeSyntheticMap(910000101);
        AgentNavigationGraph graph = AgentNavigationGraphService.rebuildGraph(map, new AgentMovementProfile(105, 100));
        Point ropeTop = new Point(100, 100);
        assertNotEquals(graph.findRopeRegionId(ropeTop), graph.findRegionId(map, ropeTop));

        Character bot = mockBot(ropeTop, map);
        bot.setStance(CharacterStance.ROPE_RIGHT_STANCE);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        AgentMovementStateRuntime.setMovementProfile(entry, new AgentMovementProfile(105, 100));

        assertEquals(graph.findRopeRegionId(ropeTop),
                AgentNavigationRegionService.resolveCurrentRegionId(graph, entry, map, ropeTop));
    }

    @Test
    void shouldModelTopStepOffAtPhysicsLandingX() {
        MapleMap map = topRopeSyntheticMap(910000102);
        AgentNavigationGraph graph = AgentNavigationGraphService.rebuildGraph(map, new AgentMovementProfile(105, 100));
        Point ropeTop = new Point(100, 100);
        int startRegionId = graph.findRopeRegionId(ropeTop);
        AgentNavigationGraph.Edge topExit = graph.getOutgoing(startRegionId).stream()
                .filter(edge -> edge.type == AgentNavigationGraph.EdgeType.CLIMB)
                .filter(edge -> edge.launchStepX == 0)
                .filter(edge -> edge.startPoint.equals(ropeTop))
                .findFirst()
                .orElseThrow();

        assertEquals(new Point(100, 100), topExit.endPoint);
    }

    @Test
    void shouldNotDismountFromRopeTopOnNonAiTickWhenFollowTargetIsAbove() {
        // Regression: pathlog-Preston-2026-05-07T034012 ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡Ãƒâ€šÃ‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€šÃ‚Â bot at firstClimbableY of a rope
        // whose top sits 1px below an above-foothold. Owner above the rope makes the raw follow
        // target's dy negative, so on every non-AI physics tick tickClimbing computed
        // climbVerticalDir=-1 and advanceClimb landed the bot onto the foothold above (climbing
        // cleared). The following AI tick saw the bot in the foothold region and re-grabbed the
        // rope. Region oscillated r=foothold ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€šÃ‚Â ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€šÃ‚Â r=rope at 50ms cadence for 10+ seconds.
        //
        // Climb direction is an AI-decided intent. Non-AI ticks must integrate the previously
        // chosen climbVerticalDir, not derive a fresh direction from the raw follow target.
        MapleMap map = new MapleMap(910000200, 0, 0, 910000200, 1.0f);
        FootholdTree footholds = new FootholdTree(new Point(-2000, -2000), new Point(2000, 2000));
        // Foothold above the rope top (y=0), 2px gap to rope.topY=2 ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡Ãƒâ€šÃ‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€šÃ‚Â same geometry as Preston r114/r173.
        footholds.insert(new Foothold(new Point(80, 0), new Point(120, 0), 1));
        map.setFootholds(footholds);
        Rope rope = new Rope(100, 2, 154, false);
        map.addRope(rope);

        Character bot = mockBot(new Point(100, 0), map);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        AgentMovementStateRuntime.setMovementProfile(entry, AgentMovementProfile.base());

        // Simulate the state right after AI tick attached the bot to the rope at firstClimbableY.
        AgentClimbStateRuntime.setClimbVerticalDirection(entry, -1);
        AgentRopeMovementService.attachToRope(entry, bot, rope, AgentNavigationPhysicsService.firstClimbableY(rope));
        assertTrue(AgentClimbStateRuntime.climbing(entry));
        assertEquals(AgentNavigationPhysicsService.firstClimbableY(rope), bot.getPosition().y);
        assertEquals(0, AgentClimbStateRuntime.climbVerticalDirection(entry), "fresh attach must not carry stale climb intent");

        // Follow target far above the bot ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡Ãƒâ€šÃ‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€šÃ‚Â without the fix, dy<0 forces climb-up which dismounts.
        Point followTargetAbove = new Point(50, -54);

        // No nav edge committed (rope-entry was just executed; reuseCommittedEdge would drop it
        // because the bot is now in the rope region == edge.toRegionId). This is the no-edge
        // window between AI ticks where the bug manifests.
        AgentNavigationDebugStateRuntime.setActiveNavigationEdge(entry, null);

        // Run one non-AI physics tick.
        AgentClimbMovementService.tickClimbing(entry, followTargetAbove, false);

        assertTrue(AgentClimbStateRuntime.climbing(entry),
                "Non-AI tick must not dismount: AI is the only place climb direction is decided.");
        assertEquals(rope, AgentClimbStateRuntime.climbRope(entry));
        assertEquals(new Point(100, AgentNavigationPhysicsService.firstClimbableY(rope)), bot.getPosition(),
                "Bot must hold position on the rope without AI-decided intent.");
    }

    @Test
    void shouldUseExplicitRopeEntryInsteadOfDownJumpFromTopPlatform() {
        MapleMap map = topRopeSyntheticMap(910000201);
        AgentNavigationGraphService.rebuildGraph(map);

        Character bot = mockBot(new Point(100, 100), map);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        AgentMovementStateRuntime.setMovementProfile(entry, AgentMovementProfile.base());

        AgentNavigationTargetService.NavigationDirective directive =
                AgentNavigationTargetService.resolveTarget(entry, new Point(100, 150), true);

        assertTrue(directive.consumedTick());
        assertTrue(AgentClimbStateRuntime.ropeEntryPending(entry));
        assertFalse(AgentClimbStateRuntime.climbing(entry));
        assertFalse(AgentMovementStateRuntime.downJumpPending(entry));
        assertFalse(AgentMovementStateRuntime.crouching(entry));

        AgentGroundMovementRuntimeService.tickGrounded(entry, new Point(100, 150));

        assertFalse(AgentClimbStateRuntime.ropeEntryPending(entry));
        assertTrue(AgentClimbStateRuntime.climbing(entry));
        assertEquals(new Point(100, 101), bot.getPosition());
    }

    @Test
    void shouldUseDownJumpInSwimFallbackWhenTargetIsDirectlyBelow() {
        MapleMap map = new MapleMap(910000202, 0, 0, 910000202, 1.0f);
        map.setSwim(true);
        FootholdTree footholds = new FootholdTree(new Point(-2000, -2000), new Point(2000, 2000));
        footholds.insert(new Foothold(new Point(0, 100), new Point(200, 100), 1));
        map.setFootholds(footholds);

        Character bot = mockBot(new Point(120, 100), map);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        AgentMovementStateRuntime.setMovementProfile(entry, AgentMovementProfile.base());
        AgentNavigationDebugStateRuntime.setGraphWarmupFallback(entry, true);

        Point target = new Point(130, 220);
        boolean immediateAction = AgentFallbackMovementService.tryImmediateAction(entry, bot.getPosition(), target);

        assertTrue(immediateAction);
        assertTrue(AgentMovementStateRuntime.downJumpPending(entry));
    }

    @Test
    void shouldUseDownJumpInNonSwimFallbackWhenTargetIsDirectlyBelow() {
        MapleMap map = new MapleMap(910000204, 0, 0, 910000204, 1.0f);
        FootholdTree footholds = new FootholdTree(new Point(-2000, -2000), new Point(2000, 2000));
        footholds.insert(new Foothold(new Point(0, 100), new Point(200, 100), 1));
        footholds.insert(new Foothold(new Point(20, 220), new Point(180, 220), 2));
        map.setFootholds(footholds);

        Character bot = mockBot(new Point(120, 100), map);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        AgentMovementStateRuntime.setMovementProfile(entry, AgentMovementProfile.base());
        AgentNavigationDebugStateRuntime.setGraphWarmupFallback(entry, true);

        Point target = new Point(130, 220);
        boolean immediateAction = AgentFallbackMovementService.tryImmediateAction(entry, bot.getPosition(), target);

        assertTrue(immediateAction);
        assertTrue(AgentMovementStateRuntime.downJumpPending(entry));
    }

    @Test
    void shouldSteerTowardNearestSwimWalkOffLedgeWhenFallbackCannotDownJump() {
        MapleMap map = new MapleMap(910000203, 0, 0, 910000203, 1.0f);
        map.setSwim(true);
        FootholdTree footholds = new FootholdTree(new Point(-2000, -2000), new Point(2000, 2000));
        footholds.insert(new Foothold(new Point(0, 100), new Point(200, 100), 1));
        map.setFootholds(footholds);

        Character bot = mockBot(new Point(120, 100), map);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        AgentMovementStateRuntime.setMovementProfile(entry, AgentMovementProfile.base());
        AgentNavigationDebugStateRuntime.setGraphWarmupFallback(entry, true);

        Point target = new Point(260, 220);
        AgentFallbackMovementService.Steering steering =
                AgentFallbackMovementService.resolveSteeringTarget(entry, bot.getPosition(), target);
        boolean immediateAction = AgentFallbackMovementService.tryImmediateAction(entry, bot.getPosition(), target);

        assertFalse(immediateAction);
        assertFalse(AgentMovementStateRuntime.downJumpPending(entry));
        assertNotNull(steering.target());
        assertTrue(steering.walkOffLedge());
        assertTrue(steering.target().y > bot.getPosition().y);
        assertTrue(steering.target().x > 200 || steering.target().x < 0,
                "fallback should steer past a legal ledge so normal walk-off physics handles the drop");
    }

    @Test
    void shouldNotPreemptivelyDownJumpWhileFollowingDownSameSlopeInFallback() {
        MapleMap map = new MapleMap(910000205, 0, 0, 910000205, 1.0f);
        FootholdTree footholds = new FootholdTree(new Point(-2000, -2000), new Point(2000, 2000));
        footholds.insert(new Foothold(new Point(0, 100), new Point(400, 240), 1));
        map.setFootholds(footholds);

        Character bot = mockBot(new Point(40, 114), map);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        AgentMovementStateRuntime.setMovementProfile(entry, AgentMovementProfile.base());
        AgentNavigationDebugStateRuntime.setGraphWarmupFallback(entry, true);

        Point target = new Point(320, 212);
        AgentFallbackMovementService.Steering steering =
                AgentFallbackMovementService.resolveSteeringTarget(entry, bot.getPosition(), target);
        boolean immediateAction = AgentFallbackMovementService.tryImmediateAction(entry, bot.getPosition(), target);

        assertFalse(immediateAction);
        assertFalse(AgentMovementStateRuntime.downJumpPending(entry));
        assertEquals(target, steering.target());
        assertFalse(steering.walkOffLedge());
    }

    private static Character mockBot(Point startPosition, MapleMap map) {
        Character bot = mock(Character.class);
        AtomicReference<Point> position = new AtomicReference<>(new Point(startPosition));
        AtomicInteger stance = new AtomicInteger(CharacterStance.STAND_RIGHT_STANCE);
        when(bot.getPosition()).thenAnswer(invocation -> new Point(position.get()));
        doAnswer(invocation -> {
            position.set(new Point(invocation.getArgument(0)));
            return null;
        }).when(bot).setPosition(any(Point.class));
        when(bot.getStance()).thenAnswer(invocation -> stance.get());
        doAnswer(invocation -> {
            stance.set(invocation.getArgument(0));
            return null;
        }).when(bot).setStance(anyInt());
        when(bot.getMap()).thenReturn(map);
        when(bot.getId()).thenReturn(1);
        when(bot.getHp()).thenReturn(100);
        when(bot.getTotalMoveSpeedStat()).thenReturn(100);
        when(bot.getTotalJumpStat()).thenReturn(100);
        return bot;
    }

    private static AgentNavigationGraph.Edge findFirstStraightDropEdge(AgentNavigationGraph graph) {
        for (AgentNavigationGraph.Region region : graph.regions) {
            for (AgentNavigationGraph.Edge edge : graph.getOutgoing(region.id)) {
                if (edge.type == AgentNavigationGraph.EdgeType.DROP && edge.launchStepX == 0) {
                    return edge;
                }
            }
        }
        return null;
    }

    private static MapleMap topRopeSyntheticMap(int mapId) {
        MapleMap map = new MapleMap(mapId, 0, 0, mapId, 1.0f);
        server.maps.FootholdTree footholds = new server.maps.FootholdTree(new Point(-2000, -2000), new Point(2000, 2000));
        footholds.insert(new Foothold(new Point(80, 100), new Point(120, 100), 1));
        map.setFootholds(footholds);
        map.addRope(new Rope(100, 100, 200, false));
        return map;
    }
}
