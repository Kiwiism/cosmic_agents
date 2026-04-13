package server.bots;

import client.Character;
import constants.game.CharacterStance;
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
    private static MapleMap perion;
    private static BotNavigationGraph perionGraph;
    private static MapleMap kerning;
    private static BotNavigationGraph kerningGraph;
    private static MapleMap kpqS1;
    private static BotNavigationGraph kpqS1Graph;
    private static MapleMap swamp1;
    private static BotNavigationGraph swamp1Graph;

    @BeforeAll
    static void loadMaps() {
        System.setProperty("wz-path", Path.of("wz").toAbsolutePath().toString());

        henesys = BotNavigationMapLoader.loadMapGeometry(100000000);
        henesysGraph = BotNavigationGraphProvider.rebuildGraph(henesys);

        ellinia = BotNavigationMapLoader.loadMapGeometry(101000000);
        elliniaGraph = BotNavigationGraphProvider.rebuildGraph(ellinia);

        perion = BotNavigationMapLoader.loadMapGeometry(102000000);
        perionGraph = BotNavigationGraphProvider.rebuildGraph(perion);

        kerning = BotNavigationMapLoader.loadMapGeometry(103000000);
        kerningGraph = BotNavigationGraphProvider.rebuildGraph(kerning);

        kpqS1 = BotNavigationMapLoader.loadMapGeometry(103000800);
        kpqS1Graph = BotNavigationGraphProvider.rebuildGraph(kpqS1);

        swamp1 = BotNavigationMapLoader.loadMapGeometry(107000000);
        swamp1Graph = BotNavigationGraphProvider.rebuildGraph(swamp1);
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
        Point start = new Point(1080, 334);
        Point target = new Point(1275, 275);
        int targetRegionId = henesysGraph.findRegionId(henesys, target);
        BotNavigationGraph.Edge edge = findPath(henesysGraph, henesys, start, target).getFirst();

        assertNotNull(edge);
        assertEquals(BotNavigationGraph.EdgeType.JUMP, edge.type);
        assertTrue(edge.containsLaunchX(start.x));
        assertEquals(targetRegionId, edge.toRegionId);
        assertJumpEdgeLandsInRegion(henesysGraph, henesys, edge, targetRegionId);
    }

    @Test
    void shouldFindSingleJumpPathFromHenesysStreetToUpperPlatform() {
        Point start = new Point(1080, 334);
        Point target = new Point(1275, 275);
        int targetRegionId = henesysGraph.findRegionId(henesys, target);
        List<BotNavigationGraph.Edge> path = findPath(henesysGraph, henesys, start, target);

        assertEquals(1, path.size());
        assertEquals(BotNavigationGraph.EdgeType.JUMP, path.getFirst().type);
        assertTrue(path.getFirst().containsLaunchX(start.x));
        assertEquals(targetRegionId, path.getFirst().toRegionId);
        assertJumpEdgeLandsInRegion(henesysGraph, henesys, path.getFirst(), targetRegionId);
    }

    @Test
    void shouldFindSingleJumpPathFromHenesysStreetToLeftUpperPlatform() {
        int targetRegionId = henesysGraph.findRegionId(henesys, new Point(938, 274));
        List<BotNavigationGraph.Edge> path = findPath(henesysGraph, henesys, new Point(990, 334), new Point(938, 274));

        assertEquals(1, path.size());
        assertEquals(BotNavigationGraph.EdgeType.JUMP, path.getFirst().type);
        assertEquals(targetRegionId, path.getFirst().toRegionId);
        assertJumpEdgeLandsInRegion(henesysGraph, henesys, path.getFirst(), targetRegionId);
    }

    @Test
    void shouldNotLaunchHenesysLeftPlatformJumpFromWallBlockedBoundary() {
        Point blockedStart = new Point(939, 334);
        int blockedStartRegionId = henesysGraph.findRegionId(henesys, blockedStart);
        List<BotNavigationGraph.Edge> path = findPath(henesysGraph, henesys, blockedStart, new Point(420, 274));

        assertFalse(path.isEmpty());
        assertEquals(BotNavigationGraph.EdgeType.JUMP, path.getFirst().type);
        assertFalse(path.getFirst().containsLaunchX(blockedStart.x),
                "wall-blocked boundary position should be outside the jump launch window");

        BotPhysicsEngine.JumpLanding landing = BotPhysicsEngine.simulateJumpLanding(
                henesys, blockedStart, path.getFirst().launchStepX, henesysGraph.movementProfile);
        assertNotNull(landing);
        assertEquals(blockedStartRegionId,
                henesysGraph.regionIdByFootholdId.getOrDefault(landing.foothold().getId(), -1),
                "jumping from the blocked boundary should not be treated as a valid upper-platform launch");
    }

    @Test
    void shouldGenerateLocalHenesysJumpEdgesNearRightWall() {
        int lowerRegionId = henesysGraph.findRegionId(henesys, new Point(3711, 454));
        int middleRegionId = henesysGraph.findRegionId(henesys, new Point(3532, 394));
        int upperRegionId = henesysGraph.findRegionId(henesys, new Point(3352, 334));

        assertHasHenesysJumpEdge(lowerRegionId, middleRegionId);
        assertHasHenesysJumpEdge(middleRegionId, upperRegionId);
    }

    @Test
    void shouldGenerateKpqRopeTransferPathToRightUpperPlatform() {
        int leftRopeRegionId = kpqS1Graph.findRopeRegionId(new Point(-437, -892));
        int rightRopeRegionId = kpqS1Graph.findRopeRegionId(new Point(-337, -1000));
        int targetRegionId = kpqS1Graph.findRegionId(kpqS1, new Point(-86, -897));

        assertTrue(leftRopeRegionId > 0);
        assertTrue(rightRopeRegionId > 0);
        assertTrue(targetRegionId > 0);
        assertTrue(kpqS1Graph.getOutgoing(leftRopeRegionId).stream()
                        .anyMatch(edge -> edge.type == BotNavigationGraph.EdgeType.CLIMB
                                && edge.toRegionId == rightRopeRegionId
                                && edge.launchStepX > 0),
                "Expected rope-to-rope transfer from the left KPQ rope to the adjacent right rope");

        List<BotNavigationGraph.Edge> path = BotNavigationManager.findPath(kpqS1Graph, kpqS1,
                new Point(-437, -892), leftRopeRegionId, targetRegionId, new Point(-86, -897));

        assertFalse(path.isEmpty(), "Left KPQ rope should route to the right upper platform");
        assertEquals(targetRegionId, path.getLast().toRegionId);
    }

    @Test
    void shouldResolveOwnerToUpperPlatformWhenPositionHasSubpixelRounding() {
        // Regression: pathlog-SLASH-2026-04-02T125933 — owner at (2596,1696), bot at (2573,1935),
        // both resolved to region 187. findGroundFoothold returned the lower foothold because the
        // sloped upper foothold's interpolated Y rounds to 1px above the stored position, causing
        // findBelow to skip it. Must resolve to different regions.
        int ownerRegionId = perionGraph.findRegionId(perion, new Point(2596, 1696));
        int botRegionId = perionGraph.findRegionId(perion, new Point(2573, 1935));

        assertTrue(ownerRegionId > 0, "Owner position (2596,1696) must resolve to a valid region");
        assertTrue(botRegionId > 0, "Bot position (2573,1935) must resolve to a valid region");
        assertNotEquals(ownerRegionId, botRegionId, "Owner on upper platform and bot on lower platform must be in different regions");
    }

    @Test
    void shouldCaptureGraphBuildReportForRebuild() {
        BotNavigationGraph graph = BotNavigationGraphProvider.rebuildGraph(kpqS1);
        BotNavigationGraphProvider.GraphBuildReport report = BotNavigationGraphProvider.getLastBuildReport(kpqS1.getId());

        assertNotNull(report);
        assertEquals(kpqS1.getId(), report.mapId);
        assertEquals(graph.regions.size(), report.regionCount);
        assertTrue(report.totalBuildNs > 0);
        assertTrue(report.totalEdgeCount > 0);
    }

    @Test
    void shouldPrecomputeLaunchWindowForKerningConstructionSlopeJump() {
        List<BotNavigationGraph.Edge> path = findPath(kpqS1Graph, kpqS1,
                new Point(449, 113), new Point(1, -341));

        assertFalse(path.isEmpty());
        assertEquals(BotNavigationGraph.EdgeType.JUMP, path.getFirst().type);
        assertTrue(path.getFirst().launchMinX < path.getFirst().launchMaxX);
        assertTrue(path.getFirst().containsLaunchX(523),
                "The slope-platform jump should expose a real launch interval around the valid takeoff point");
        assertFalse(path.getFirst().containsLaunchX(path.getFirst().launchMinX - 1),
                "launch windows should reject positions just outside the left boundary");
        assertFalse(path.getFirst().containsLaunchX(path.getFirst().launchMaxX + 1),
                "launch windows should reject positions just outside the right boundary");
    }

    @Test
    void shouldKeepElliniaPivotFootholdsInOneWalkRegion() {
        Point leftSlopePoint = new Point(-508, -421);
        Point rightSlopePoint = new Point(-464, -422);

        int leftRegionId = elliniaGraph.findRegionId(ellinia, leftSlopePoint);
        int rightRegionId = elliniaGraph.findRegionId(ellinia, rightSlopePoint);

        assertTrue(leftRegionId > 0);
        assertTrue(rightRegionId > 0);
        assertEquals(leftRegionId, rightRegionId);
    }

    @Test
    void shouldTreatElliniaPivotFootholdTraversalAsSameRegion() {
        List<BotNavigationGraph.Edge> path = findPath(elliniaGraph, ellinia, new Point(-508, -421), new Point(-390, -400));

        assertTrue(path.isEmpty());
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
                BotNavigationGraph.EdgeType.JUMP, 40, 16);

        assertNotNull(edge);
        assertTrue(edge.containsLaunchX(1355));
        assertNotEquals(edge.fromRegionId, edge.toRegionId);
        assertJumpEdgeLandsInRegion(elliniaGraph, ellinia, edge, edge.toRegionId);
    }

    @Test
    void shouldGenerateElliniaClimbEdgesForTownRopes() {
        int climbEdgeCount = countEdges(elliniaGraph, BotNavigationGraph.EdgeType.CLIMB);

        assertTrue(climbEdgeCount > 0, "Ellinia town should expose climb edges for at least some ropes");
    }

    @Test
    void shouldGenerateRopeToRopeClimbEdgeWhenJumpArcCanCatchTargetRope() {
        MapleMap map = createEmptyTestMap(910000002);
        Rope sourceRope = new Rope(0, 100, 200, false);
        Rope targetRope = new Rope(48, 140, 150, false);
        map.addRope(sourceRope);
        map.addRope(targetRope);

        BotNavigationGraph graph = BotNavigationGraphProvider.rebuildGraph(map);
        BotNavigationGraph.Edge ropeTransfer = findFirstRopeToRopeClimbEdge(graph);

        assertNotNull(ropeTransfer);
        assertEquals(1, ropeTransfer.fromRegionId);
        assertEquals(2, ropeTransfer.toRegionId);
        assertEquals(BotPhysicsEngine.walkStep(map), ropeTransfer.launchStepX);
        assertEquals(targetRope.x(), ropeTransfer.endPoint.x);
        assertTrue(ropeTransfer.endPoint.y >= targetRope.topY() && ropeTransfer.endPoint.y <= targetRope.bottomY());
    }

    @Test
    void shouldPreferElliniaLocalRightSideJumpChainOverFarLeftDetour() {
        List<BotNavigationGraph.Edge> path = findPath(elliniaGraph, ellinia, new Point(1355, -888), new Point(1354, -1197));
        int targetRegionId = elliniaGraph.findRegionId(ellinia, new Point(1354, -1197));

        assertFalse(path.isEmpty());
        assertTrue(path.stream().allMatch(edge -> edge.type == BotNavigationGraph.EdgeType.JUMP));
        assertTrue(path.getFirst().containsLaunchX(1355));
        assertEquals(targetRegionId, path.getLast().toRegionId);
    }

    @Test
    void shouldPreferElliniaLedgeDropsOverDownJumpsWhenDroppingStraightDown() {
        List<BotNavigationGraph.Edge> path = findPath(elliniaGraph, ellinia, new Point(1354, -1197), new Point(1355, -888));

        assertEquals(BotNavigationGraph.EdgeType.DROP, path.getFirst().type);
        assertTrue(path.stream().allMatch(edge -> edge.type == BotNavigationGraph.EdgeType.DROP
                || edge.type == BotNavigationGraph.EdgeType.WALK),
                "dropping to a lower Ellinia platform should stay on drop/walk edges even if the exact drop style changes");
    }

    @Test
    void shouldRequireStraightDropExecutionNearItsAuthoredAnchor() {
        BotNavigationGraph.Edge dropEdge = new BotNavigationGraph.Edge(
                1, 2, BotNavigationGraph.EdgeType.DROP,
                new Point(100, 100), new Point(100, 160),
                0, 0, 0, 0, 0, 100
        );

        assertTrue(BotNavigationManager.canExecuteDropFromCurrentPosition(
                null, null, new Point(100, 100), dropEdge));
        assertTrue(BotNavigationManager.canExecuteDropFromCurrentPosition(
                null, null, new Point(114, 100), dropEdge));
        assertFalse(BotNavigationManager.canExecuteDropFromCurrentPosition(
                null, null, new Point(115, 100), dropEdge));
    }

    @Test
    void shouldTreatWalkOffDropsAsDirectionalMovementInsteadOfExecutableAnchors() {
        LedgeDropCase dropCase = findLedgeDropCaseWithWalkableEarlyStart(elliniaGraph, ellinia);

        assertNotNull(dropCase, "Expected at least one ledge drop with a still-walkable early start");
        assertTrue(BotPhysicsEngine.canWalkGroundStep(ellinia, dropCase.earlyStart(), dropCase.edge().launchStepX));
        assertFalse(BotNavigationManager.canExecuteDropFromCurrentPosition(
                elliniaGraph, ellinia, dropCase.edge().startPoint, dropCase.edge()));
        assertFalse(BotNavigationManager.canExecuteDropFromCurrentPosition(
                elliniaGraph, ellinia, dropCase.earlyStart(), dropCase.edge()));
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

    @Test
    void shouldResolveFollowTargetFromOwnerRopeRegionWhileOwnerIsHanging() {
        RopeEntryReuseCase reuseCase = findRopeEntryReuseCase(elliniaGraph, ellinia);

        assertNotNull(reuseCase, "Expected a rope-entry edge whose rope point still resolves to ground");
        assertNotEquals(reuseCase.edge().toRegionId, elliniaGraph.findRegionId(ellinia, reuseCase.botPosition()));

        Character owner = mockBot(reuseCase.botPosition(), ellinia);
        when(owner.getStance()).thenReturn(CharacterStance.ROPE_STANCE);

        BotEntry entry = new BotEntry(mockBot(reuseCase.rawTarget(), ellinia), owner, null);
        entry.following = true;

        assertEquals(reuseCase.edge().toRegionId,
                BotNavigationManager.resolveTargetRegionId(elliniaGraph, entry, ellinia, owner.getPosition()));
    }

    @Test
    void bumpyKerningSwampIsSameRegion() {
        assertEquals(swamp1Graph.findRegionId(swamp1, new Point(1378, 123)), swamp1Graph.findRegionId(swamp1, new Point(-1723, 118)));
    }

    @Test
    void shouldPreferDirectElliniaJumpFromRegion64ToRegion65() {
        Point botPosition = new Point(-61, -938);
        Point ownerPosition = new Point(-268, -907);
        int botRegionId = elliniaGraph.findRegionId(ellinia, botPosition);
        int ownerRegionId = elliniaGraph.findRegionId(ellinia, ownerPosition);

        assertEquals(64, botRegionId);
        assertEquals(65, ownerRegionId);

        BotNavigationGraph.Edge directJump = elliniaGraph.getOutgoing(botRegionId).stream()
                .filter(edge -> edge.type == BotNavigationGraph.EdgeType.JUMP)
                .filter(edge -> edge.toRegionId == ownerRegionId)
                .filter(edge -> edge.containsLaunchX(botPosition.x))
                .findFirst()
                .orElse(null);

        assertNotNull(directJump, "Expected direct Ellinia jump edge from region 64 to region 65 at the logged bot X");
        assertTrue(directJump.launchMinX < 0, "jump launch window should keep valid negative X coordinates");
        assertJumpEdgeLandsInRegion(elliniaGraph, ellinia, directJump, ownerRegionId);

        List<BotNavigationGraph.Edge> path = findPath(elliniaGraph, ellinia, botPosition, ownerPosition);

        assertEquals(1, path.size(), "expected direct jump path instead of Ellinia detour");
        assertEquals(BotNavigationGraph.EdgeType.JUMP, path.getFirst().type);
        assertEquals(ownerRegionId, path.getFirst().toRegionId);
    }

    @Test
    void shouldTrimJumpWindowAtInvalidInteriorWallBoundary() {
        MapleMap map = BotNavigationMapLoader.loadMapGeometry(100000202);
        BotNavigationGraph graph = BotNavigationGraphProvider.rebuildGraph(map);
        Point badLaunch = new Point(-1422, -642);
        int fromRegionId = graph.findRegionId(map, badLaunch);
        int targetRegionId = graph.findRegionId(map, new Point(-1375, -664));

        assertEquals(36, fromRegionId);
        assertEquals(32, targetRegionId);

        BotPhysicsEngine.JumpLanding badLanding = BotPhysicsEngine.simulateJumpLanding(
                map, badLaunch, BotPhysicsEngine.walkStep(map), graph.movementProfile);
        assertNotNull(badLanding);
        assertEquals(fromRegionId, graph.regionIdByFootholdId.getOrDefault(badLanding.foothold().getId(), -1),
                "logged bad launch should bounce back to the original region");

        assertTrue(graph.getOutgoing(fromRegionId).stream()
                        .anyMatch(edge -> edge.type == BotNavigationGraph.EdgeType.JUMP
                                && edge.toRegionId == targetRegionId
                                && edge.containsLaunchX(-1427)),
                "nearby valid launch point should still route to the target platform");
        assertFalse(graph.getOutgoing(fromRegionId).stream()
                        .anyMatch(edge -> edge.type == BotNavigationGraph.EdgeType.JUMP
                                && edge.toRegionId == targetRegionId
                                && edge.containsLaunchX(badLaunch.x)),
                "jump launch windows must not include X positions that no longer land in the target region");
    }

    @Test
    void shouldTrimJumpWindowThatFallsOffLandingPlatformWithMomentum() {
        MapleMap map = BotNavigationMapLoader.loadMapGeometry(100000202);
        BotNavigationGraph graph = BotNavigationGraphProvider.rebuildGraph(map);
        int stepX = BotPhysicsEngine.walkStep(map, graph.movementProfile);
        Point badLaunch = new Point(-1881, -1341);
        Point stableLaunch = new Point(-1898, -1341);
        int fromRegionId = graph.findRegionId(map, badLaunch);
        int targetRegionId = graph.findRegionId(map, new Point(-1841, -1379));

        assertEquals(8, fromRegionId);
        assertEquals(7, targetRegionId);

        BotPhysicsEngine.PostLandingJump unstableLanding =
                BotPhysicsEngine.simulateJumpLandingWithPostLandingTicks(map, badLaunch, stepX, graph.movementProfile, 3);
        BotPhysicsEngine.PostLandingJump stableLanding =
                BotPhysicsEngine.simulateJumpLandingWithPostLandingTicks(map, stableLaunch, stepX, graph.movementProfile, 3);

        assertNotNull(unstableLanding);
        assertTrue(unstableLanding.lostGround(),
                "logged right-edge launch should fall off region 7 after landing momentum is applied");
        assertNotNull(stableLanding);
        assertFalse(stableLanding.lostGround(),
                "nearby deeper launch should stay on the destination platform after landing momentum");

        assertTrue(graph.getOutgoing(fromRegionId).stream()
                        .anyMatch(edge -> edge.type == BotNavigationGraph.EdgeType.JUMP
                                && edge.toRegionId == targetRegionId
                                && edge.containsLaunchX(stableLaunch.x)),
                "stable launches should still route to the target platform");
        assertFalse(graph.getOutgoing(fromRegionId).stream()
                        .anyMatch(edge -> edge.type == BotNavigationGraph.EdgeType.JUMP
                                && edge.toRegionId == targetRegionId
                                && edge.containsLaunchX(badLaunch.x)),
                "jump launch windows must exclude launches that immediately walk off the landing platform");
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

    private static BotNavigationGraph.Edge findFirstRopeToRopeClimbEdge(BotNavigationGraph graph) {
        for (BotNavigationGraph.Region region : graph.regions) {
            if (!region.isRopeRegion) {
                continue;
            }
            for (BotNavigationGraph.Edge edge : graph.getOutgoing(region.id)) {
                if (edge.type != BotNavigationGraph.EdgeType.CLIMB) {
                    continue;
                }
                BotNavigationGraph.Region toRegion = graph.getRegion(edge.toRegionId);
                if (toRegion != null && toRegion.isRopeRegion) {
                    return edge;
                }
            }
        }
        return null;
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

    private static void assertHasHenesysJumpEdge(int fromRegionId, int toRegionId) {
        assertTrue(henesysGraph.getOutgoing(fromRegionId).stream()
                        .anyMatch(edge -> edge.type == BotNavigationGraph.EdgeType.JUMP
                                && edge.toRegionId == toRegionId),
                "Expected Henesys jump edge r" + fromRegionId + "->r" + toRegionId);
    }

    private static MapleMap createEmptyTestMap(int mapId) {
        MapleMap map = new MapleMap(mapId, 0, 0, mapId, 1.0f);
        map.setFootholds(new server.maps.FootholdTree(new Point(-2000, -2000), new Point(2000, 2000)));
        return map;
    }

    private static LedgeDropCase findLedgeDropCaseWithWalkableEarlyStart(BotNavigationGraph graph, MapleMap map) {
        for (BotNavigationGraph.Region region : graph.regions) {
            for (BotNavigationGraph.Edge edge : graph.getOutgoing(region.id)) {
                if (edge.type != BotNavigationGraph.EdgeType.DROP || edge.launchStepX == 0) {
                    continue;
                }

                Point earlyStart = findWalkableEarlyDropStart(graph, map, edge);
                if (earlyStart != null) {
                    return new LedgeDropCase(edge, earlyStart);
                }
            }
        }
        return null;
    }

    private static Point findWalkableEarlyDropStart(BotNavigationGraph graph,
                                                    MapleMap map,
                                                    BotNavigationGraph.Edge edge) {
        BotNavigationGraph.Region region = graph.getRegion(edge.fromRegionId);
        if (region == null) {
            return null;
        }

        int direction = Integer.signum(edge.launchStepX);
        if (direction == 0) {
            return null;
        }

        for (int delta = 1; delta <= 14; delta++) {
            int candidateX = edge.startPoint.x - (direction * delta);
            if (candidateX < region.minX || candidateX > region.maxX) {
                continue;
            }

            Point candidate = region.pointAt(candidateX);
            if (candidate == null || candidate.equals(edge.startPoint)) {
                continue;
            }
            if (graph.findRegionId(map, candidate) != edge.fromRegionId) {
                continue;
            }
            if (BotPhysicsEngine.canWalkGroundStep(map, candidate, edge.launchStepX)) {
                return candidate;
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
        AtomicInteger stance = new AtomicInteger(CharacterStance.STAND_RIGHT_STANCE);

        when(bot.getPosition()).thenAnswer(invocation -> new Point(position.get()));
        doAnswer(invocation -> {
            position.set(new Point(invocation.getArgument(0)));
            return null;
        }).when(bot).setPosition(any(Point.class));
        when(bot.getMap()).thenReturn(map);
        when(bot.getHp()).thenReturn(100);
        when(bot.getTotalMoveSpeedStat()).thenReturn(100);
        when(bot.getTotalJumpStat()).thenReturn(100);
        when(bot.getStance()).thenAnswer(invocation -> stance.get());
        doAnswer(invocation -> {
            stance.set(invocation.getArgument(0));
            return null;
        }).when(bot).setStance(anyInt());
        return bot;
    }

    private static void assertJumpEdgeLandsInRegion(BotNavigationGraph graph,
                                                    MapleMap map,
                                                    BotNavigationGraph.Edge edge,
                                                    int expectedRegionId) {
        BotPhysicsEngine.JumpLanding landing = BotPhysicsEngine.simulateJumpLanding(
                map, edge.startPoint, edge.launchStepX, graph.movementProfile);

        assertNotNull(landing, "jump edge should reproduce a landing when simulated from its authored anchor");
        assertEquals(expectedRegionId,
                graph.regionIdByFootholdId.getOrDefault(landing.foothold().getId(), -1),
                "jump edge should land in the expected destination region");
    }

    private record LedgeDropCase(BotNavigationGraph.Edge edge, Point earlyStart) {
    }

    private record AlternativeJumpCase(BotNavigationGraph.Edge edge, Point alternativeStart) {
    }

    private record RopeEntryReuseCase(BotNavigationGraph.Edge edge, Rope rope, Point botPosition, Point rawTarget) {
    }
}
