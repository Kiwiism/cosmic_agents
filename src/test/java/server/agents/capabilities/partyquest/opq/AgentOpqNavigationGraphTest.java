package server.agents.capabilities.partyquest.opq;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import server.agents.capabilities.movement.AgentMovementProfile;
import server.agents.capabilities.movement.AgentJumpProbeService;
import server.agents.capabilities.navigation.AgentNavigationGraph;
import server.agents.capabilities.navigation.AgentNavigationGraphService;
import server.agents.capabilities.navigation.AgentNavigationMapLoader;
import server.agents.capabilities.navigation.AgentNavigationPathService;
import server.agents.capabilities.navigation.AgentPortalApproachService;
import server.maps.MapleMap;
import server.maps.Rope;

import java.awt.Point;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentOpqNavigationGraphTest {
    @BeforeAll
    static void initWzPath() {
        System.setProperty("wz-path", Path.of("wz").toAbsolutePath().toString());
    }

    @Test
    void loungeSpawnCanReachUpperPuzzleRowWithGearedJump() {
        MapleMap map = AgentNavigationMapLoader.loadMapGeometry(AgentOpqDefinition.LOUNGE_MAP);
        Point portal = AgentPortalApproachService.navigableTarget(map, map.getPortal(11));
        assertRoute(AgentOpqDefinition.LOUNGE_MAP, new Point(-45, -728), portal,
                new AgentMovementProfile(100, 123));
    }

    @Test
    void wayUpInitialRopesConnectSpawnToFirstPuzzleRow() {
        MapleMap map = AgentNavigationMapLoader.loadMapGeometry(AgentOpqDefinition.ON_WAY_UP_MAP);
        Rope longRope = map.getRopes().stream()
                .filter(rope -> rope.x() == -43 && rope.topY() == -1_623)
                .findFirst().orElseThrow();
        assertTrue(AgentJumpProbeService.canReachRopeFromGround(
                map, new Point(-112, -846), longRope, AgentMovementProfile.base()));
        assertRopeRoute(map, longRope, new Point(-43, -878), new Point(-43, -1_625));
    }

    @Test
    void lobbyFirstRopeLandingConnectsToSecondRope() {
        MapleMap map = AgentNavigationMapLoader.loadMapGeometry(AgentOpqDefinition.LOBBY_MAP);
        Rope firstRope = map.getRopes().stream()
                .filter(rope -> rope.x() == -1_598)
                .findFirst().orElseThrow();
        assertRopeRoute(map, firstRope,
                new Point(-1_598, -556), new Point(-1_777, -528));
    }

    @Test
    void lobbyAscentTargetsTheNextRegionInsteadOfTheCurrentRopeHead() {
        assertEquals(new Point(-1_598, -408), AgentOpqCoordinator.lobbyAscentTarget(
                new Point(-1_623, -127)));
        assertEquals(new Point(-1_777, -528), AgentOpqCoordinator.lobbyAscentTarget(
                new Point(-1_598, -438)));
        assertEquals(new Point(-1_777, -528), AgentOpqCoordinator.lobbyAscentTarget(
                new Point(-1_702, -511)));
        assertEquals(new Point(-1_717, -926), AgentOpqCoordinator.lobbyAscentTarget(
                new Point(-1_777, -616)));
        assertEquals(null, AgentOpqCoordinator.lobbyAscentTarget(
                new Point(-1_717, -900)));
    }

    @Test
    void entranceCloudsAreDeterministicallyDistributedAcrossAllSixAgents() {
        List<Point> authoredClouds = List.of(
                new Point(-799, -260), new Point(-369, -258), new Point(-4, -917),
                new Point(-268, -903), new Point(334, -762), new Point(452, -453),
                new Point(194, -300), new Point(1388, -887), new Point(1068, -929),
                new Point(744, -595), new Point(1082, -680), new Point(1450, -399),
                new Point(921, -242), new Point(201, -443), new Point(728, 83),
                new Point(-504, -540), new Point(-54, -554), new Point(681, -931),
                new Point(1132, -223));

        Set<Integer> owners = authoredClouds.stream()
                .map(point -> AgentOpqCoordinator.entranceCloudOwner(point, 6))
                .collect(Collectors.toSet());

        assertEquals(Set.of(0, 1, 2, 3, 4, 5), owners);
        assertEquals(3, AgentOpqCoordinator.entranceCloudOwner(new Point(-799, -260), 6));
    }

    @Test
    void entranceCenterCloudStrikePlatformHasALegalRouteFromLowerLeft() {
        assertRoute(AgentOpqDefinition.ENTRANCE_MAP,
                new Point(-307, -201), new Point(-54, -498));
    }

    private static void assertRoute(int mapId, Point start, Point target) {
        assertRoute(mapId, start, target, AgentMovementProfile.base());
    }

    private static void assertRoute(int mapId, Point start, Point target, AgentMovementProfile profile) {
        MapleMap map = AgentNavigationMapLoader.loadMapGeometry(mapId);
        AgentNavigationGraph graph = AgentNavigationGraphService.rebuildGraph(map, profile);
        int startRegion = graph.findRegionId(map, start);
        int targetRegion = graph.findRegionId(map, target);
        assertTrue(startRegion > 0, "Missing start region for " + start);
        assertTrue(targetRegion > 0, "Missing target region for " + target);
        List<AgentNavigationGraph.Edge> path = AgentNavigationPathService.findPath(
                graph, map, start, startRegion, targetRegion, target);
        assertFalse(path.isEmpty(), () -> "No legal route on map " + mapId + " from r" + startRegion
                + " " + start + " to r" + targetRegion + " " + target
                + "; start=" + describeRegion(graph.getRegion(startRegion))
                + "; target=" + describeRegion(graph.getRegion(targetRegion))
                + "; outgoing=" + graph.getOutgoing(startRegion).stream()
                        .map(AgentOpqNavigationGraphTest::describeEdge).collect(Collectors.joining(", "))
                + "; targetOutgoing=" + graph.getOutgoing(targetRegion).stream()
                        .map(AgentOpqNavigationGraphTest::describeEdge).collect(Collectors.joining(", ")));
    }

    private static void assertRopeRoute(MapleMap map, Rope rope, Point start, Point target) {
        AgentNavigationGraph graph = AgentNavigationGraphService.rebuildGraph(map, AgentMovementProfile.base());
        int startRegion = graph.regions.stream()
                .filter(region -> region.isRopeRegion && region.minX == rope.x()
                        && region.minY == rope.topY() && region.maxY == rope.bottomY())
                .mapToInt(region -> region.id).findFirst().orElse(-1);
        int targetRegion = graph.findRegionId(map, target);
        assertTrue(startRegion > 0, "Missing rope region for " + rope);
        assertTrue(targetRegion > 0, "Missing target region for " + target);
        List<AgentNavigationGraph.Edge> path = AgentNavigationPathService.findPath(
                graph, map, start, startRegion, targetRegion, target);
        assertFalse(path.isEmpty(), "Attached rope has no legal route to its top platform");
    }

    private static String describeRegion(AgentNavigationGraph.Region region) {
        return "r" + region.id + "[" + region.minX + ".." + region.maxX + ","
                + region.minY + ".." + region.maxY + ",rope=" + region.isRopeRegion + "]";
    }

    private static String describeEdge(AgentNavigationGraph.Edge edge) {
        return edge.type + "(r" + edge.fromRegionId + "->r" + edge.toRegionId + ","
                + edge.startPoint + "->" + edge.endPoint + ",rope=" + edge.ropeX + "/"
                + edge.ropeTopY + ".." + edge.ropeBottomY + ")";
    }
}
