package server.bots;

import client.Character;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import server.maps.MapleMap;
import server.maps.Rope;

import java.awt.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BotNavigationGraphProviderTest {
    private static MapleMap henesys;
    private static BotNavigationGraph henesysGraph;
    private static MapleMap ellinia;
    private static BotNavigationGraph elliniaGraph;

    @BeforeAll
    static void loadMaps() {
        System.setProperty("wz-path", Path.of("wz").toAbsolutePath().toString());

        henesys = BotNavigationMapLoader.loadMapGeometry(100000000);
        henesysGraph = BotNavigationGraphProvider.rebuildGraph(henesys);

        ellinia = BotNavigationMapLoader.loadMapGeometry(101000000);
        elliniaGraph = BotNavigationGraphProvider.rebuildGraph(ellinia);
    }

    @Test
    void shouldKeepHenesysLowerTownStreetInOneMergedRegion() {
        int firstRegionId = henesysGraph.findRegionId(henesys, new Point(990, 334));
        int secondRegionId = henesysGraph.findRegionId(henesys, new Point(1080, 334));

        assertEquals(firstRegionId, secondRegionId);
        assertTrue(firstRegionId > 0);
    }

    @Test
    void shouldGenerateDirectHenesysJumpEdgeFromBelowToFoothold315() {
        BotNavigationGraph.Edge edge = findNearbyEdge(henesysGraph, henesys, new Point(1080, 334),
                BotNavigationGraph.EdgeType.JUMP, 24, 16);

        assertNotNull(edge);
        assertEquals(new Point(1080, 334), edge.startPoint);
        assertEquals(new Point(1080, 275), edge.endPoint);
    }

    @Test
    void shouldFindSingleJumpPathFromHenesysStreetToUpperPlatform() {
        List<BotNavigationGraph.Edge> path = findPath(henesysGraph, henesys, new Point(1080, 334), new Point(1275, 275));

        assertEquals(1, path.size());
        assertEquals(BotNavigationGraph.EdgeType.JUMP, path.getFirst().type);
        assertEquals(new Point(1080, 334), path.getFirst().startPoint);
        assertEquals(new Point(1080, 275), path.getFirst().endPoint);
    }

    @Test
    void shouldFindSingleJumpPathFromHenesysStreetToLeftUpperPlatform() {
        List<BotNavigationGraph.Edge> path = findPath(henesysGraph, henesys, new Point(990, 334), new Point(938, 274));

        assertEquals(1, path.size());
        assertEquals(BotNavigationGraph.EdgeType.JUMP, path.getFirst().type);
        assertEquals(new Point(938, 274), path.getFirst().endPoint);
    }

    @Test
    void shouldNotMergeElliniaPivotFootholdsIntoOneRegion() {
        Point leftSlopePoint = new Point(-508, -421);
        Point rightSlopePoint = new Point(-464, -422);

        int leftRegionId = elliniaGraph.findRegionId(ellinia, leftSlopePoint);
        int rightRegionId = elliniaGraph.findRegionId(ellinia, rightSlopePoint);

        assertTrue(leftRegionId > 0);
        assertTrue(rightRegionId > 0);
        assertNotEquals(leftRegionId, rightRegionId);
    }

    @Test
    void shouldKeepElliniaPivotFootholdsWalkConnectedAfterSplit() {
        List<BotNavigationGraph.Edge> path = findPath(elliniaGraph, ellinia, new Point(-508, -421), new Point(-217, -351));

        assertFalse(path.isEmpty());
        assertTrue(path.stream().allMatch(edge -> edge.type == BotNavigationGraph.EdgeType.WALK));
    }

    @Test
    void shouldFindElliniaPathFromLowerPlatformToUpperLeftSlope() {
        List<BotNavigationGraph.Edge> path = findPath(elliniaGraph, ellinia, new Point(-445, -55), new Point(-491, -426));
        int targetRegionId = elliniaGraph.findRegionId(ellinia, new Point(-491, -426));

        assertFalse(path.isEmpty());
        assertEquals(targetRegionId, path.getLast().toRegionId);
        assertTrue(path.stream().anyMatch(edge -> edge.type == BotNavigationGraph.EdgeType.CLIMB));
    }

    @Test
    void shouldGenerateElliniaJumpEdgeFromLowerRightPlatformToPlatformAbove() {
        BotNavigationGraph.Edge edge = findNearbyEdge(elliniaGraph, ellinia, new Point(1355, -888),
                BotNavigationGraph.EdgeType.JUMP, 24, 16);

        assertNotNull(edge);
        assertEquals(new Point(1355, -888), edge.startPoint);
        assertEquals(new Point(1299, -955), edge.endPoint);
    }

    @Test
    void shouldGenerateElliniaClimbEdgesForTownRopes() {
        int climbEdgeCount = countEdges(elliniaGraph, BotNavigationGraph.EdgeType.CLIMB);

        assertTrue(climbEdgeCount > 0, "Ellinia town should expose climb edges for at least some ropes");
    }

    @Test
    void shouldPreferElliniaLocalRightSideJumpChainOverFarLeftDetour() {
        List<BotNavigationGraph.Edge> path = findPath(elliniaGraph, ellinia, new Point(1355, -888), new Point(1354, -1197));
        int targetRegionId = elliniaGraph.findRegionId(ellinia, new Point(1354, -1197));

        assertFalse(path.isEmpty());
        assertTrue(path.stream().allMatch(edge -> edge.type == BotNavigationGraph.EdgeType.JUMP));
        assertEquals(new Point(1355, -888), path.getFirst().startPoint);
        assertEquals(targetRegionId, path.getLast().toRegionId);
    }

    @Test
    void shouldPreferElliniaLedgeDropsOverDownJumpsWhenDroppingStraightDown() {
        List<BotNavigationGraph.Edge> path = findPath(elliniaGraph, ellinia, new Point(1354, -1197), new Point(1355, -888));

        assertEquals(BotNavigationGraph.EdgeType.DROP, path.getFirst().type);
        assertTrue(path.getFirst().launchStepX != 0, "Ledge drops should keep a horizontal walk-off step instead of down-jumping in place");
        assertTrue(path.stream().allMatch(edge -> edge.type == BotNavigationGraph.EdgeType.DROP
                || edge.type == BotNavigationGraph.EdgeType.WALK));
    }

    @Test
    void shouldAllowStraightDropExecutionFromCurrentXWhenLandingRegionMatches() {
        StraightDropCase dropCase = findStraightDropCaseWithAlternativeStart(elliniaGraph, ellinia);

        assertNotNull(dropCase, "Expected at least one straight drop edge with an alternate same-region start point");
        assertNotEquals(dropCase.edge().startPoint.x, dropCase.alternativeStart().x);
        assertTrue(BotNavigationManager.canExecuteDropFromCurrentPosition(
                elliniaGraph, ellinia, dropCase.alternativeStart(), dropCase.edge()));
    }

    @Test
    void shouldAllowJumpExecutionFromAlternativeStartWhenLandingRegionMatches() {
        AlternativeJumpCase jumpCase = findJumpCaseWithAlternativeStart(henesysGraph, henesys);

        assertNotNull(jumpCase, "Expected at least one jump edge that stays valid from an alternate same-region start point");
        assertNotEquals(jumpCase.edge().startPoint.x, jumpCase.alternativeStart().x);
        assertTrue(BotNavigationManager.canExecuteJumpFromCurrentPosition(
                henesysGraph, henesys, jumpCase.alternativeStart(), jumpCase.edge()));
    }

    @Test
    void shouldDiscardCommittedRopeEntryEdgeAfterBotHasAttachedToRope() {
        RopeEntryReuseCase reuseCase = findRopeEntryReuseCase(elliniaGraph, ellinia);

        assertNotNull(reuseCase, "Expected a rope-entry edge whose top attachment point resolves to a non-rope region");

        Character bot = mockBot(reuseCase.botPosition(), ellinia);
        BotEntry entry = new BotEntry(bot, null, null);
        entry.climbing = true;
        entry.climbRope = reuseCase.rope();
        entry.navEdge = reuseCase.edge();
        entry.navTargetRegionId = elliniaGraph.findRegionId(ellinia, reuseCase.rawTarget());

        BotNavigationManager.NavigationDirective directive =
                BotNavigationManager.resolveTarget(entry, reuseCase.rawTarget(), false);

        assertFalse(directive.consumedTick);
        assertEquals(reuseCase.rawTarget(), directive.targetPos);
        assertNull(entry.navEdge);
    }

    @Test
    void shouldResolveCurrentRegionFromRopeWhileBotIsClimbing() {
        RopeEntryReuseCase reuseCase = findRopeEntryReuseCase(elliniaGraph, ellinia);

        assertNotNull(reuseCase, "Expected a rope-entry edge whose attach point still resolves to ground");
        assertNotEquals(reuseCase.edge().toRegionId, elliniaGraph.findRegionId(ellinia, reuseCase.botPosition()));

        BotEntry entry = new BotEntry(mockBot(reuseCase.botPosition(), ellinia), null, null);
        entry.climbing = true;
        entry.climbRope = reuseCase.rope();

        assertEquals(reuseCase.edge().toRegionId,
                BotNavigationManager.resolveCurrentRegionId(elliniaGraph, entry, ellinia, reuseCase.botPosition()));
    }

    private static List<BotNavigationGraph.Edge> findPath(BotNavigationGraph graph,
                                                          MapleMap map,
                                                          Point start,
                                                          Point target) {
        int startRegionId = graph.findRegionId(map, start);
        int targetRegionId = graph.findRegionId(map, target);

        assertTrue(startRegionId > 0, "Missing graph region for start point " + start);
        assertTrue(targetRegionId > 0, "Missing graph region for target point " + target);

        return BotNavigationManager.findPath(graph, map, start, startRegionId, targetRegionId, target);
    }

    private static BotNavigationGraph.Edge findNearbyEdge(BotNavigationGraph graph,
                                                          MapleMap map,
                                                          Point point,
                                                          BotNavigationGraph.EdgeType type,
                                                          int maxDx,
                                                          int maxDy) {
        int regionId = graph.findRegionId(map, point);
        assertTrue(regionId > 0, "Missing graph region for point " + point);

        List<BotNavigationGraph.Edge> nearby = new ArrayList<>();
        for (BotNavigationGraph.Edge edge : graph.getOutgoing(regionId)) {
            if (edge.type != type) {
                continue;
            }
            if (Math.abs(edge.startPoint.x - point.x) > maxDx || Math.abs(edge.startPoint.y - point.y) > maxDy) {
                continue;
            }
            nearby.add(edge);
        }

        assertFalse(nearby.isEmpty(), "Missing " + type + " edge near " + point);
        nearby.sort((left, right) -> {
            int leftDistance = Math.abs(left.startPoint.x - point.x) + Math.abs(left.startPoint.y - point.y);
            int rightDistance = Math.abs(right.startPoint.x - point.x) + Math.abs(right.startPoint.y - point.y);
            return Integer.compare(leftDistance, rightDistance);
        });
        return nearby.getFirst();
    }

    private static int countEdges(BotNavigationGraph graph, BotNavigationGraph.EdgeType type) {
        int count = 0;
        for (BotNavigationGraph.Region region : graph.regions) {
            for (BotNavigationGraph.Edge edge : graph.getOutgoing(region.id)) {
                if (edge.type == type) {
                    count++;
                }
            }
        }
        return count;
    }

    private static StraightDropCase findStraightDropCaseWithAlternativeStart(BotNavigationGraph graph, MapleMap map) {
        for (BotNavigationGraph.Region region : graph.regions) {
            for (BotNavigationGraph.Edge edge : graph.getOutgoing(region.id)) {
                if (edge.type != BotNavigationGraph.EdgeType.DROP || edge.launchStepX != 0) {
                    continue;
                }

                Point alternative = findAlternativeStraightDropStart(graph, map, edge);
                if (alternative != null) {
                    return new StraightDropCase(edge, alternative);
                }
            }
        }
        return null;
    }

    private static Point findAlternativeStraightDropStart(BotNavigationGraph graph,
                                                          MapleMap map,
                                                          BotNavigationGraph.Edge edge) {
        BotNavigationGraph.Region region = graph.getRegion(edge.fromRegionId);
        if (region == null) {
            return null;
        }

        for (int x = region.minX; x <= region.maxX; x += 4) {
            Point start = region.pointAt(x);
            if (Math.abs(start.x - edge.startPoint.x) < 12) {
                continue;
            }

            BotPhysicsEngine.JumpLanding landing = BotPhysicsEngine.simulateDownJumpLanding(map, start);
            if (landing == null) {
                continue;
            }

            int landingRegionId = graph.regionIdByFootholdId.getOrDefault(landing.foothold().getId(), -1);
            if (landingRegionId == edge.toRegionId) {
                return start;
            }
        }
        return null;
    }

    private static AlternativeJumpCase findJumpCaseWithAlternativeStart(BotNavigationGraph graph, MapleMap map) {
        for (BotNavigationGraph.Region region : graph.regions) {
            for (BotNavigationGraph.Edge edge : graph.getOutgoing(region.id)) {
                if (edge.type != BotNavigationGraph.EdgeType.JUMP) {
                    continue;
                }

                Point alternative = findAlternativeJumpStart(graph, map, edge);
                if (alternative != null) {
                    return new AlternativeJumpCase(edge, alternative);
                }
            }
        }
        return null;
    }

    private static Point findAlternativeJumpStart(BotNavigationGraph graph,
                                                  MapleMap map,
                                                  BotNavigationGraph.Edge edge) {
        BotNavigationGraph.Region region = graph.getRegion(edge.fromRegionId);
        if (region == null) {
            return null;
        }

        for (int x = region.minX; x <= region.maxX; x += 4) {
            Point start = region.pointAt(x);
            if (Math.abs(start.x - edge.startPoint.x) < 12) {
                continue;
            }

            BotPhysicsEngine.JumpLanding landing = BotPhysicsEngine.simulateJumpLanding(map, start, edge.launchStepX);
            if (landing == null) {
                continue;
            }

            int landingRegionId = graph.regionIdByFootholdId.getOrDefault(landing.foothold().getId(), -1);
            if (landingRegionId == edge.toRegionId) {
                return start;
            }
        }
        return null;
    }

    private static RopeEntryReuseCase findRopeEntryReuseCase(BotNavigationGraph graph, MapleMap map) {
        for (BotNavigationGraph.Region region : graph.regions) {
            for (BotNavigationGraph.Edge edge : graph.getOutgoing(region.id)) {
                if (edge.type != BotNavigationGraph.EdgeType.CLIMB) {
                    continue;
                }

                BotNavigationGraph.Region from = graph.getRegion(edge.fromRegionId);
                BotNavigationGraph.Region to = graph.getRegion(edge.toRegionId);
                if (from == null || to == null || from.isRopeRegion || !to.isRopeRegion) {
                    continue;
                }

                Point botPosition = new Point(edge.endPoint);
                if (graph.findRegionId(map, botPosition) == edge.toRegionId) {
                    continue;
                }

                Point rawTarget = alternateTargetInRegion(from, edge.startPoint);
                if (rawTarget == null || graph.findRegionId(map, rawTarget) != from.id) {
                    continue;
                }

                Rope rope = BotNavigationGraphProvider.findRopeFromRegion(map, to);
                if (rope != null) {
                    return new RopeEntryReuseCase(edge, rope, botPosition, rawTarget);
                }
            }
        }
        return null;
    }

    private static Point alternateTargetInRegion(BotNavigationGraph.Region region, Point excluded) {
        Point[] candidates = new Point[]{region.centerPoint(), region.leftPoint(), region.rightPoint()};
        for (Point candidate : candidates) {
            if (!candidate.equals(excluded)) {
                return candidate;
            }
        }
        return null;
    }

    private static Character mockBot(Point startPosition, MapleMap map) {
        Character bot = mock(Character.class);
        AtomicReference<Point> position = new AtomicReference<>(new Point(startPosition));
        AtomicInteger stance = new AtomicInteger(BotPhysicsEngine.cfg.STAND_STANCE);

        when(bot.getPosition()).thenAnswer(invocation -> new Point(position.get()));
        doAnswer(invocation -> {
            position.set(new Point(invocation.getArgument(0)));
            return null;
        }).when(bot).setPosition(any(Point.class));
        when(bot.getMap()).thenReturn(map);
        when(bot.getHp()).thenReturn(100);
        when(bot.getStance()).thenAnswer(invocation -> stance.get());
        doAnswer(invocation -> {
            stance.set(invocation.getArgument(0));
            return null;
        }).when(bot).setStance(anyInt());
        return bot;
    }

    private record StraightDropCase(BotNavigationGraph.Edge edge, Point alternativeStart) {
    }

    private record AlternativeJumpCase(BotNavigationGraph.Edge edge, Point alternativeStart) {
    }

    private record RopeEntryReuseCase(BotNavigationGraph.Edge edge, Rope rope, Point botPosition, Point rawTarget) {
    }
}
