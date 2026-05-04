package server.bots;

import client.Character;
import constants.game.CharacterStance;
import org.junit.jupiter.api.BeforeAll;
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
import static org.mockito.Mockito.when;

class BotNavigationManagerTest {
    private static volatile MapleMap kerningCached;

    private static MapleMap kerning() {
        MapleMap v = kerningCached;
        if (v != null) return v;
        synchronized (BotNavigationManagerTest.class) {
            if (kerningCached == null) {
                kerningCached = BotNavigationMapLoader.loadMapGeometry(103000000);
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
        BotNavigationGraph.Edge collapsed = BotNavigationManager.collapseLeadingWalkEdges(List.of(
                new BotNavigationGraph.Edge(1, 2, BotNavigationGraph.EdgeType.WALK,
                        new Point(528, -914), new Point(528, -914),
                        0, 0, 0, 0, 0, 50),
                new BotNavigationGraph.Edge(2, 3, BotNavigationGraph.EdgeType.WALK,
                        new Point(528, -914), new Point(528, -914),
                        0, 0, 0, 0, 0, 50),
                new BotNavigationGraph.Edge(3, 4, BotNavigationGraph.EdgeType.JUMP,
                        new Point(540, -914), new Point(612, -980),
                        9, 0, 0, 0, 0, 300)
        ));

        assertNotNull(collapsed);
        assertEquals(BotNavigationGraph.EdgeType.JUMP, collapsed.type);
        assertEquals(1, collapsed.fromRegionId);
        assertEquals(4, collapsed.toRegionId);
        assertEquals(new Point(540, -914), collapsed.startPoint);
        assertEquals(new Point(612, -980), collapsed.endPoint);
        assertEquals(400, collapsed.cost);
    }

    @Test
    void shouldKeepFirstRealWalkInsteadOfCollapsingPastLaterZeroDistanceHandoff() {
        BotNavigationGraph.Edge collapsed = BotNavigationManager.collapseLeadingWalkEdges(List.of(
                new BotNavigationGraph.Edge(24, 22, BotNavigationGraph.EdgeType.WALK,
                        new Point(-947, 153), new Point(-751, 142),
                        0, 0, 0, 0, 0, 120),
                new BotNavigationGraph.Edge(22, 20, BotNavigationGraph.EdgeType.WALK,
                        new Point(-751, 142), new Point(-751, 142),
                        0, 0, 0, 0, 0, 50),
                new BotNavigationGraph.Edge(20, 27, BotNavigationGraph.EdgeType.CLIMB,
                        new Point(-437, 121), new Point(-437, 84),
                        0, 0, -437, 84, 121, 250)
        ));

        assertNotNull(collapsed);
        assertEquals(BotNavigationGraph.EdgeType.WALK, collapsed.type);
        assertEquals(24, collapsed.fromRegionId);
        assertEquals(22, collapsed.toRegionId);
        assertEquals(new Point(-947, 153), collapsed.startPoint);
        assertEquals(new Point(-751, 142), collapsed.endPoint);
        assertEquals(120, collapsed.cost);
    }

    @Test
    void shouldDropLeadingWalkChainWhenItConsumesNoMovement() {
        BotNavigationGraph.Edge collapsed = BotNavigationManager.collapseLeadingWalkEdges(List.of(
                new BotNavigationGraph.Edge(181, 184, BotNavigationGraph.EdgeType.WALK,
                        new Point(565, -2135), new Point(565, -2135),
                        0, 0, 0, 0, 0, 50),
                new BotNavigationGraph.Edge(184, 190, BotNavigationGraph.EdgeType.WALK,
                        new Point(565, -2135), new Point(565, -2135),
                        0, 0, 0, 0, 0, 50)
        ));

        assertNull(collapsed);
    }

    @Test
    void shouldOnlySnapZeroStepClimbExitAtRopeTop() {
        Rope rope = new Rope(675, 143, 215, false);
        BotNavigationGraph.Edge topExit = new BotNavigationGraph.Edge(
                49, 45, BotNavigationGraph.EdgeType.CLIMB,
                new Point(675, 143), new Point(675, 141),
                0, 0, 675, 143, 215, 250
        );
        BotNavigationGraph.Edge bottomExit = new BotNavigationGraph.Edge(
                49, 45, BotNavigationGraph.EdgeType.CLIMB,
                new Point(675, 215), new Point(675, 215),
                0, 0, 675, 143, 215, 250
        );

        assertTrue(BotNavigationManager.isTopStepOffExit(rope, new Point(675, 145), topExit));
        assertTrue(BotNavigationManager.isTopStepOffExit(rope, new Point(675, 171), topExit));
        assertFalse(BotNavigationManager.isTopStepOffExit(rope, new Point(675, 215), bottomExit));
    }

    @Test
    void shouldOnlyExecuteStraightDownJumpInsideLaunchWindow() {
        MapleMap map = new MapleMap(910000031, 0, 0, 910000031, 1.0f);
        FootholdTree footholds = new FootholdTree(new Point(-2000, -2000), new Point(2000, 2000));
        footholds.insert(new Foothold(new Point(0, 0), new Point(200, 0), 1));
        footholds.insert(new Foothold(new Point(40, 120), new Point(160, 120), 2));
        map.setFootholds(footholds);

        BotNavigationGraph graph = BotNavigationGraphProvider.rebuildGraph(map);
        BotNavigationGraph.Edge downJump = findFirstStraightDropEdge(graph);

        assertNotNull(downJump, "fixture should produce a straight down-jump edge");
        assertTrue(downJump.launchMinX < downJump.launchMaxX);

        int insideX = (downJump.launchMinX + downJump.launchMaxX) / 2;
        int outsideX = downJump.launchMaxX + 1;

        assertTrue(BotNavigationManager.canExecuteDropFromCurrentPosition(
                graph, map, new Point(insideX, 0), downJump));
        assertFalse(BotNavigationManager.canExecuteDropFromCurrentPosition(
                graph, map, new Point(outsideX, 0), downJump));
    }

    @Test
    void shouldUsePreciseTargetForCommittedWalkRegionHandoffs() {
        BotNavigationGraph.Edge walkHandoff = new BotNavigationGraph.Edge(
                343, 341, BotNavigationGraph.EdgeType.WALK,
                new Point(28, -1167), new Point(13, -1170),
                0, 0, 0, 0, 0, 100
        );
        BotNavigationGraph.Edge noMoveWalk = new BotNavigationGraph.Edge(
                343, 342, BotNavigationGraph.EdgeType.WALK,
                new Point(28, -1167), new Point(28, -1167),
                0, 0, 0, 0, 0, 50
        );

        assertTrue(BotNavigationManager.shouldUsePreciseWalkTarget(walkHandoff));
        assertFalse(BotNavigationManager.shouldUsePreciseWalkTarget(noMoveWalk));
    }

    @Test
    void shouldDropStaleCollapsedWalkEdgeWhenBotEntersIntermediateRegion() {
        // Regression: pathlog-SLASH-2026-04-02 — collapsed r358→r355 WALK edge (via r359),
        // bot steps into r359 mid-traverse; old code returned null here (fromRegionId mismatch),
        // dropping the edge every tick and causing an oscillation loop.
        BotNavigationGraph.Edge collapsedWalk = new BotNavigationGraph.Edge(
                358, 355, BotNavigationGraph.EdgeType.WALK,
                new Point(46, -61), new Point(54, -58),
                0, 0, 0, 0, 0, 100
        );
        Character bot = mock(Character.class);
        when(bot.getMap()).thenReturn(mock(MapleMap.class));
        BotEntry entry = new BotEntry(bot, null, null);
        entry.navEdge = collapsedWalk;
        entry.navTargetRegionId = 355;
        BotNavigationGraph graph = mock(BotNavigationGraph.class);

        // Bot is in intermediate region 359 — neither source (358) nor destination (355)
        BotNavigationGraph.Edge result = BotNavigationManager.reuseCommittedEdge(graph, entry, 359, 355);

        assertNull(result, "Stale collapsed WALK edge must be dropped once the bot leaves its source region");
    }

    @Test
    void shouldDropCollapsedWalkEdgeOnceDestinationRegionReached() {
        BotNavigationGraph.Edge collapsedWalk = new BotNavigationGraph.Edge(
                358, 355, BotNavigationGraph.EdgeType.WALK,
                new Point(46, -61), new Point(54, -58),
                0, 0, 0, 0, 0, 100
        );
        Character bot = mock(Character.class);
        when(bot.getMap()).thenReturn(mock(MapleMap.class));
        BotEntry entry = new BotEntry(bot, null, null);
        entry.navEdge = collapsedWalk;
        entry.navTargetRegionId = 355;
        BotNavigationGraph graph = mock(BotNavigationGraph.class);

        BotNavigationGraph.Edge result = BotNavigationManager.reuseCommittedEdge(graph, entry, 355, 355);

        assertNull(result, "WALK edge must be dropped once bot reaches destination region");
    }

    @Test
    void shouldUseGraphDerivedJumpLaunchWindowInsteadOfGenericTolerance() {
        Foothold foothold = new Foothold(new Point(500, 107), new Point(530, 107), 1);
        BotNavigationGraph.Region fromRegion = new BotNavigationGraph.Region(20, List.of(new BotNavigationGraph.Segment(foothold)));
        BotNavigationGraph graph = mock(BotNavigationGraph.class);
        when(graph.getRegion(20)).thenReturn(fromRegion);

        BotNavigationGraph.Edge jump = new BotNavigationGraph.Edge(
                20, 15, BotNavigationGraph.EdgeType.JUMP,
                new Point(520, 107), new Point(480, 36),
                516, 523, -8, 0, 0, 0, 0, 850
        );

        assertFalse(BotNavigationManager.isWithinJumpLaunchWindow(graph, new Point(513, 107), jump));
        assertFalse(BotNavigationManager.isWithinJumpLaunchWindow(graph, new Point(514, 107), jump));
        assertTrue(BotNavigationManager.isWithinJumpLaunchWindow(graph, new Point(516, 107), jump));
        assertTrue(BotNavigationManager.isWithinJumpLaunchWindow(graph, new Point(523, 107), jump));
        assertFalse(BotNavigationManager.isWithinJumpLaunchWindow(graph, new Point(525, 107), jump));
        assertFalse(BotNavigationManager.isWithinJumpLaunchWindow(graph, new Point(526, 107), jump));
        assertFalse(BotNavigationManager.isWithinJumpLaunchWindow(graph, new Point(520, 160), jump));
    }

    @Test
    void shouldPickStableJumpLaunchTargetInsideWindow() {
        MapleMap map = new MapleMap(910000010, 0, 0, 910000010, 1.0f);
        server.maps.FootholdTree footholds = new server.maps.FootholdTree(new Point(-2000, -2000), new Point(2000, 2000));
        footholds.insert(new Foothold(new Point(500, 107), new Point(530, 107), 1));
        map.setFootholds(footholds);
        BotNavigationGraph graph = BotNavigationGraphProvider.rebuildGraph(map);

        Character bot = mock(Character.class);
        when(bot.getMap()).thenReturn(map);
        BotEntry entry = new BotEntry(bot, null, null);

        BotNavigationGraph.Edge jump = new BotNavigationGraph.Edge(
                1, 15, BotNavigationGraph.EdgeType.JUMP,
                new Point(520, 107), new Point(480, 36),
                516, 523, -8, 0, 0, 0, 0, 850
        );

        Point firstTarget = BotNavigationManager.selectJumpWaypoint(entry, new Point(449, 113), jump);
        Point secondTarget = BotNavigationManager.selectJumpWaypoint(entry, new Point(540, 113), jump);
        Point thirdTarget = BotNavigationManager.selectJumpWaypoint(entry, new Point(520, 113), jump);

        assertTrue(firstTarget.x >= 519 && firstTarget.x <= 520);
        assertEquals(107, firstTarget.y);
        assertEquals(firstTarget, secondTarget);
        assertEquals(firstTarget, thirdTarget);

        assertEquals(new Point(516, 107), BotNavigationManager.selectJumpWaypoint(graph, new Point(449, 113), jump));
        assertEquals(new Point(523, 107), BotNavigationManager.selectJumpWaypoint(graph, new Point(540, 113), jump));
        assertEquals(new Point(520, 107), BotNavigationManager.selectJumpWaypoint(graph, new Point(520, 113), jump));
    }

    @Test
    void shouldChooseTargetRegionEntryBasedOnInRegionPathTarget() {
        MapleMap map = new MapleMap(910000026, 0, 0, 910000026, 1.0f);
        BotNavigationGraph.Region startRegion = new BotNavigationGraph.Region(
                1, List.of(new BotNavigationGraph.Segment(new Foothold(new Point(0, 100), new Point(100, 100), 1))));
        BotNavigationGraph.Region targetRegion = new BotNavigationGraph.Region(
                2, List.of(new BotNavigationGraph.Segment(new Foothold(new Point(0, 200), new Point(200, 200), 2))));
        Map<Integer, BotNavigationGraph.Region> regionsById = new HashMap<>();
        regionsById.put(1, startRegion);
        regionsById.put(2, targetRegion);
        BotNavigationGraph.Edge leftEntry = new BotNavigationGraph.Edge(
                1, 2, BotNavigationGraph.EdgeType.CLIMB,
                new Point(50, 100), new Point(0, 200),
                0, 0, 0, 0, 0, 100
        );
        BotNavigationGraph.Edge rightEntry = new BotNavigationGraph.Edge(
                1, 2, BotNavigationGraph.EdgeType.CLIMB,
                new Point(50, 100), new Point(200, 200),
                0, 0, 0, 0, 0, 100
        );
        BotNavigationGraph graph = new BotNavigationGraph(
                map.getId(),
                1,
                List.of(startRegion, targetRegion),
                regionsById,
                Map.of(1, 1, 2, 2),
                Map.of(1, List.of(leftEntry, rightEntry)),
                Set.of()
        );

        List<BotNavigationGraph.Edge> leftPath = BotNavigationManager.findPath(
                graph, map, new Point(50, 100), 1, 2, new Point(40, 200));
        List<BotNavigationGraph.Edge> rightPath = BotNavigationManager.findPath(
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

        BotNavigationGraph graph = BotNavigationGraphProvider.rebuildGraph(map);
        Point botPos = new Point(50, 0);
        Point leftTarget = new Point(50, 120);
        Point rightTarget = new Point(250, 120);
        int startRegionId = graph.findRegionId(map, botPos);
        int leftTargetRegionId = graph.findRegionId(map, leftTarget);
        int rightTargetRegionId = graph.findRegionId(map, rightTarget);

        List<BotNavigationGraph.Edge> leftPath = BotNavigationManager.findPath(
                graph, map, botPos, startRegionId, leftTargetRegionId, leftTarget);
        List<BotNavigationGraph.Edge> rightPath = BotNavigationManager.findPath(
                graph, map, botPos, startRegionId, rightTargetRegionId, rightTarget);

        assertFalse(leftPath.isEmpty(), "fixture should produce a left-side drop path");
        assertFalse(rightPath.isEmpty(), "fixture should produce a right-side drop path");

        BotNavigationGraph.Edge staleEdge = leftPath.getFirst();
        BotNavigationGraph.Edge freshEdge = rightPath.getFirst();
        assertEquals(BotNavigationGraph.EdgeType.DROP, staleEdge.type);
        assertEquals(BotNavigationGraph.EdgeType.DROP, freshEdge.type);
        assertNotEquals(staleEdge.toRegionId, freshEdge.toRegionId,
                "regression requires different first actionable drop edges from the same source region");

        Character bot = mockBot(botPos, map);
        BotEntry entry = new BotEntry(bot, null, null);
        entry.movementProfile = BotMovementProfile.base();
        entry.following = true;
        entry.navEdge = staleEdge;
        entry.navTargetRegionId = leftTargetRegionId;

        BotNavigationManager.NavigationDirective directive =
                BotNavigationManager.resolveTarget(entry, rightTarget, true);

        assertFalse(directive.consumedTick);
        assertEquals(freshEdge.toRegionId, entry.navEdge.toRegionId,
                "grounded reuse must discard a stale drop edge once the current best first edge changes");
        assertEquals(freshEdge.startPoint, entry.navEdge.startPoint);
    }

    @Test
    void shouldRetainCommittedGroundEdgeWhenAlternativeLeadsToSameDestinationRegion() {
        BotNavigationGraph.Edge committedDrop = new BotNavigationGraph.Edge(
                80, 83, BotNavigationGraph.EdgeType.DROP,
                new Point(7, -34), new Point(-84, 99),
                7, 7, 0, 0, 0, 655
        );
        BotNavigationGraph.Edge replacementJump = new BotNavigationGraph.Edge(
                80, 83, BotNavigationGraph.EdgeType.JUMP,
                new Point(5, -34), new Point(-99, 95),
                -35, 45, -7, 0, 0, 0, 0, 750
        );

        assertTrue(BotNavigationManager.shouldRetainCommittedGroundEdge(committedDrop, replacementJump),
                "equivalent first exits into the same destination region should not thrash mid-approach");
    }

    @Test
    void shouldNotRetainCommittedGroundEdgeWhenAlternativeChangesDestinationRegion() {
        BotNavigationGraph.Edge committedDrop = new BotNavigationGraph.Edge(
                1, 2, BotNavigationGraph.EdgeType.DROP,
                new Point(10, 0), new Point(10, 100),
                10, 10, 0, 0, 0, 300
        );
        BotNavigationGraph.Edge replacementDrop = new BotNavigationGraph.Edge(
                1, 3, BotNavigationGraph.EdgeType.DROP,
                new Point(40, 0), new Point(40, 100),
                40, 40, 0, 0, 0, 300
        );

        assertFalse(BotNavigationManager.shouldRetainCommittedGroundEdge(committedDrop, replacementDrop),
                "grounded replans must still refresh when the better first edge changes destination region");
    }

    @Test
    void shouldUseRawTargetWhileMovementGraphWarmsInBackground() {
        MapleMap map = new MapleMap(910000030, 0, 0, 910000030, 1.0f);
        server.maps.FootholdTree footholds = new server.maps.FootholdTree(new Point(-2000, -2000), new Point(2000, 2000));
        footholds.insert(new Foothold(new Point(0, 100), new Point(200, 100), 1));
        map.setFootholds(footholds);

        Character bot = mockBot(new Point(20, 100), map);
        BotEntry entry = new BotEntry(bot, null, null);
        entry.movementProfile = new BotMovementProfile(105, 105);

        BotNavigationManager.NavigationDirective directive =
                BotNavigationManager.resolveTarget(entry, new Point(180, 100), true);

        assertFalse(directive.consumedTick);
        assertEquals(new Point(180, 100), directive.targetPos);
        assertEquals("graph-warmup", entry.lastNavDecision);
        assertTrue(entry.graphWarmupFallback);
        assertNull(entry.navEdge);

        BotNavigationGraphProvider.getGraph(map, entry.movementProfile);
    }

    @Test
    void shouldHoldCurrentPositionOnlyAtNonTopClimbExitLaunchAnchor() {
        Character bot = mock(Character.class);
        when(bot.getMap()).thenReturn(kerning());
        BotEntry entry = new BotEntry(bot, null, null);
        entry.climbing = true;
        entry.climbRope = new Rope(-1251, -137, 2, true);

        BotNavigationGraph.Edge climbExit = new BotNavigationGraph.Edge(
                189, 157, BotNavigationGraph.EdgeType.CLIMB,
                new Point(-1251, -107), new Point(-1132, 156),
                8, 0, -1251, -137, 2, 650
        );

        assertEquals(new Point(-1251, -107),
                BotNavigationManager.selectClimbWaypoint(entry, new Point(-1251, -107), climbExit));
        assertEquals(new Point(-1251, -107),
                BotNavigationManager.selectClimbWaypoint(entry, new Point(-1251, -109), climbExit));
    }

    @Test
    void shouldKeepSteeringToClimbLaunchAnchorWhileBelowExitAnchor() {
        Character bot = mock(Character.class);
        when(bot.getMap()).thenReturn(kerning());
        BotEntry entry = new BotEntry(bot, null, null);
        entry.climbing = true;
        entry.climbRope = new Rope(-1251, -137, 2, true);

        BotNavigationGraph.Edge climbExit = new BotNavigationGraph.Edge(
                189, 157, BotNavigationGraph.EdgeType.CLIMB,
                new Point(-1251, -107), new Point(-1132, 156),
                8, 0, -1251, -137, 2, 650
        );

        assertEquals(new Point(-1251, -107),
                BotNavigationManager.selectClimbWaypoint(entry, new Point(-1251, -104), climbExit));
    }

    @Test
    void shouldKeepSteeringToNonTopRopeExitAnchorWhileAboveExitAnchor() {
        Character bot = mock(Character.class);
        when(bot.getMap()).thenReturn(kerning());
        BotEntry entry = new BotEntry(bot, null, null);
        entry.climbing = true;
        entry.climbRope = new Rope(707, -769, -455, false);

        BotNavigationGraph.Edge climbExit = new BotNavigationGraph.Edge(
                104, 101, BotNavigationGraph.EdgeType.CLIMB,
                new Point(707, -734), new Point(627, -602),
                -6, 0, 707, -769, -455, 950
        );

        assertEquals(new Point(707, -734),
                BotNavigationManager.selectClimbWaypoint(entry, new Point(707, -764), climbExit));
    }

    @Test
    void shouldKeepCommittedRopeExitClimbEdgeWhileAirborne() {
        Character bot = mock(Character.class);
        when(bot.getMap()).thenReturn(mock(MapleMap.class));
        BotEntry entry = new BotEntry(bot, null, null);
        entry.inAir = true;
        entry.navEdge = new BotNavigationGraph.Edge(
                25, 14, BotNavigationGraph.EdgeType.CLIMB,
                new Point(-437, -181), new Point(-473, -211),
                -8, 0, -437, -1471, 84, 250
        );
        entry.navTargetRegionId = 14;
        BotNavigationGraph graph = mock(BotNavigationGraph.class);

        BotNavigationGraph.Edge reused = BotNavigationManager.reuseCommittedEdge(graph, entry, 20, 14);

        assertEquals(entry.navEdge, reused);
    }

    @Test
    void shouldUseTopRopeEntryInsteadOfDroppingToBottomInLithHarbor() {
        MapleMap lithHarbor = BotNavigationMapLoader.loadMapGeometry(104000000);
        BotNavigationGraph graph = BotNavigationGraphProvider.rebuildGraph(lithHarbor);
        Point start = new Point(1189, 287);
        Point target = new Point(1265, 331);
        int startRegionId = graph.findRegionId(lithHarbor, start);
        int targetRegionId = graph.findRopeRegionId(target);

        List<BotNavigationGraph.Edge> path = BotNavigationManager.findPath(
                graph, lithHarbor, start, startRegionId, targetRegionId, target);

        assertFalse(path.isEmpty());
        assertEquals(BotNavigationGraph.EdgeType.CLIMB, path.getFirst().type);
        assertEquals(targetRegionId, path.getFirst().toRegionId);
        assertTrue(path.getFirst().endPoint.y <= target.y + BotMovementManager.cfg.JUMP_Y_THRESH);
    }

    @Test
    void shouldNotLaunchVerticalRopeEntryFromOutsideRopeGrabWindow() {
        MapleMap lithHarbor = BotNavigationMapLoader.loadMapGeometry(104000000);
        BotNavigationGraph graph = BotNavigationGraphProvider.rebuildGraph(lithHarbor);
        Point start = new Point(1245, 647);
        Point target = new Point(1265, 331);
        int startRegionId = graph.findRegionId(lithHarbor, start);
        int targetRegionId = graph.findRopeRegionId(target);
        List<BotNavigationGraph.Edge> path = BotNavigationManager.findPath(
                graph, lithHarbor, start, startRegionId, targetRegionId, target);
        assertFalse(path.isEmpty());
        BotNavigationGraph.Edge ropeEntry = path.getFirst();
        assertEquals(BotNavigationGraph.EdgeType.CLIMB, ropeEntry.type);
        assertEquals(0, ropeEntry.launchStepX);
        assertTrue(ropeEntry.launchMinX < ropeEntry.launchMaxX);
        assertTrue(ropeEntry.containsLaunchX(1257));

        BotNavigationGraph.Region fromRegion = graph.getRegion(ropeEntry.fromRegionId);
        int outsideLaunchX = ropeEntry.launchMaxX < fromRegion.maxX
                ? ropeEntry.launchMaxX + 1
                : ropeEntry.launchMinX - 1;
        assertFalse(ropeEntry.containsLaunchX(outsideLaunchX));

        Character bot = mockBot(fromRegion.pointAt(outsideLaunchX), lithHarbor);
        BotEntry entry = new BotEntry(bot, null, null);
        entry.movementProfile = BotMovementProfile.base();
        entry.navEdge = ropeEntry;
        entry.navTargetRegionId = targetRegionId;

        BotNavigationManager.NavigationDirective directive =
                BotNavigationManager.resolveTarget(entry, target, true);

        assertFalse(directive.consumedTick);
        assertFalse(entry.inAir);
        assertEquals("climb-pos", entry.lastEdgeBlockReason);
    }

    @Test
    void shouldJumpOffTopRopeBeforePhysicsAutoDismountsToUpperPlatform() {
        MapleMap lithHarbor = BotNavigationMapLoader.loadMapGeometry(104000000);
        BotNavigationGraph graph = BotNavigationGraphProvider.rebuildGraph(lithHarbor);
        Point botPos = new Point(1265, 294);
        Point target = new Point(1802, 647);
        int startRegionId = graph.findRopeRegionId(botPos);
        int targetRegionId = graph.findRegionId(lithHarbor, target);
        List<BotNavigationGraph.Edge> path = BotNavigationManager.findPath(
                graph, lithHarbor, botPos, startRegionId, targetRegionId, target);
        assertFalse(path.isEmpty());
        BotNavigationGraph.Edge ropeExit = path.getFirst();
        assertEquals(BotNavigationGraph.EdgeType.CLIMB, ropeExit.type);
        assertTrue(ropeExit.launchStepX > 0);
        assertEquals(new Point(1265, 290), ropeExit.startPoint);

        Character bot = mockBot(botPos, lithHarbor);
        BotEntry entry = new BotEntry(bot, null, null);
        entry.movementProfile = BotMovementProfile.base();
        entry.climbing = true;
        entry.climbRope = new Rope(1265, 289, 597, false);

        BotNavigationManager.NavigationDirective directive =
                BotNavigationManager.resolveTarget(entry, target, true);

        assertTrue(directive.consumedTick);
        assertTrue(entry.inAir);
        assertFalse(entry.climbing);
        assertEquals(new Point(1265, 290), bot.getPosition());
    }

    @Test
    void shouldPreferCurrentRopeRegionAtRopeTopWhenBotStanceIsClimbing() {
        MapleMap map = topRopeSyntheticMap(910000101);
        BotNavigationGraph graph = BotNavigationGraphProvider.rebuildGraph(map, new BotMovementProfile(105, 100));
        Point ropeTop = new Point(100, 100);
        assertNotEquals(graph.findRopeRegionId(ropeTop), graph.findRegionId(map, ropeTop));

        Character bot = mockBot(ropeTop, map);
        bot.setStance(CharacterStance.ROPE_RIGHT_STANCE);
        BotEntry entry = new BotEntry(bot, null, null);
        entry.movementProfile = new BotMovementProfile(105, 100);

        assertEquals(graph.findRopeRegionId(ropeTop),
                BotNavigationManager.resolveCurrentRegionId(graph, entry, map, ropeTop));
    }

    @Test
    void shouldModelTopStepOffAtPhysicsLandingX() {
        MapleMap map = topRopeSyntheticMap(910000102);
        BotNavigationGraph graph = BotNavigationGraphProvider.rebuildGraph(map, new BotMovementProfile(105, 100));
        Point ropeTop = new Point(100, 100);
        int startRegionId = graph.findRopeRegionId(ropeTop);
        BotNavigationGraph.Edge topExit = graph.getOutgoing(startRegionId).stream()
                .filter(edge -> edge.type == BotNavigationGraph.EdgeType.CLIMB)
                .filter(edge -> edge.launchStepX == 0)
                .filter(edge -> edge.startPoint.equals(ropeTop))
                .findFirst()
                .orElseThrow();

        assertEquals(new Point(100, 100), topExit.endPoint);
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

    private static BotNavigationGraph.Edge findFirstStraightDropEdge(BotNavigationGraph graph) {
        for (BotNavigationGraph.Region region : graph.regions) {
            for (BotNavigationGraph.Edge edge : graph.getOutgoing(region.id)) {
                if (edge.type == BotNavigationGraph.EdgeType.DROP && edge.launchStepX == 0) {
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
