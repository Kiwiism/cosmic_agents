package server.agents.integration;

import server.agents.runtime.AgentRuntimeEntry;

import server.agents.capabilities.movement.AgentMovementPhysicsStateRuntime;

import server.agents.capabilities.navigation.AgentNavigationGraphService;
import server.agents.capabilities.navigation.AgentNavigationWaypointService;

import server.agents.capabilities.navigation.AgentNavigationGraph;

import client.Character;
import org.junit.jupiter.api.Test;
import server.maps.Foothold;
import server.maps.MapleMap;

import java.awt.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BotDirectionalDropNavigationTest {
    @Test
    void shouldKeepDirectionalDropDirectionWhileBotIsStillOnRunway() {
        DropTestFixture fixture = createDirectionalDropFixture(910000040);
        // New O(1) runway semantics: startPoint is placed launchRunwayPx behind the ledge.
        // Once the bot has crossed the runway anchor in the launch direction, nav should
        // feed endPoint until physics performs the walk-off.
        Character bot = mockBot(new Point(fixture.edge.startPoint.x + 2, fixture.edge.startPoint.y), fixture.map);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        AgentMovementPhysicsStateRuntime.setPhysicsX(entry, bot.getPosition().x);
        AgentMovementPhysicsStateRuntime.setPhysicsY(entry, bot.getPosition().y);

        Point waypoint = AgentNavigationWaypointService.selectDropWaypoint(entry, fixture.graph, bot.getPosition(), fixture.edge);

        assertEquals(fixture.edge.endPoint, waypoint,
                "directional drops should keep the held walk direction while the bot is already on the runway");
    }

    @Test
    void shouldKeepDirectionalDropLandingTargetWhenNaturalWalkOffAlreadyMatchesEdge() {
        DropTestFixture fixture = createDirectionalDropFixture(910000041);
        Character bot = mockBot(new Point(fixture.edge.startPoint), fixture.map);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        AgentMovementPhysicsStateRuntime.setPhysicsX(entry, bot.getPosition().x);
        AgentMovementPhysicsStateRuntime.setPhysicsY(entry, bot.getPosition().y);

        Point waypoint = AgentNavigationWaypointService.selectDropWaypoint(entry, fixture.graph, bot.getPosition(), fixture.edge);

        assertEquals(fixture.edge.endPoint, waypoint,
                "once the walk-off already has enough runway, nav should keep feeding the landing-side direction");
    }

    @Test
    void shouldKeepDirectionalDropDirectionAfterCrossingNegativeRunwayAnchor() {
        DropTestFixture fixture = createDirectionalDropFixture(910000042, false);
        Character bot = mockBot(new Point(fixture.edge.startPoint.x - 5, fixture.edge.startPoint.y), fixture.map);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        AgentMovementPhysicsStateRuntime.setPhysicsX(entry, bot.getPosition().x);
        AgentMovementPhysicsStateRuntime.setPhysicsY(entry, bot.getPosition().y);

        Point waypoint = AgentNavigationWaypointService.selectDropWaypoint(entry, fixture.graph, bot.getPosition(), fixture.edge);

        assertEquals(fixture.edge.endPoint, waypoint,
                "once the bot has crossed the negative-direction drop anchor, nav should keep holding the drop direction");
    }

    @Test
    void shouldSteerBackToRunwayWhenBotStandsOnWrongFootholdPastAnchor() {
        MapleMap map = new MapleMap(910000043, 0, 0, 910000043, 1.0f);
        Foothold wrongLedge = new Foothold(new Point(-200, 100), new Point(40, 100), 1);
        Foothold runway = new Foothold(new Point(60, 100), new Point(200, 100), 2);
        Foothold lower = new Foothold(new Point(-300, 160), new Point(300, 160), 3);
        server.maps.FootholdTree footholds = new server.maps.FootholdTree(
                new Point(-2000, -2000), new Point(2000, 2000));
        footholds.insert(wrongLedge);
        footholds.insert(runway);
        footholds.insert(lower);
        map.setFootholds(footholds);

        AgentNavigationGraph graph = AgentNavigationGraphService.rebuildGraph(map);
        AgentNavigationGraph.Edge edge = graph.regions.stream()
                .flatMap(region -> graph.getOutgoing(region.id).stream())
                .filter(candidate -> candidate.type == AgentNavigationGraph.EdgeType.DROP
                        && candidate.launchStepX < 0
                        && candidate.endPoint.y > candidate.startPoint.y
                        && candidate.startPoint.x > 40)
                .findFirst()
                .orElse(null);
        assertNotNull(edge);

        Character bot = mockBot(new Point(0, 100), map);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        AgentMovementPhysicsStateRuntime.setPhysicsX(entry, bot.getPosition().x);
        AgentMovementPhysicsStateRuntime.setPhysicsY(entry, bot.getPosition().y);

        Point waypoint = AgentNavigationWaypointService.selectDropWaypoint(entry, graph, bot.getPosition(), edge);

        assertEquals(edge.startPoint, waypoint,
                "a different foothold beyond the x anchor must not satisfy the committed drop");
    }

    @Test
    void shouldNotAuthorWalkOffWhoseLandingRegionChangesWithLaunchState() {
        MapleMap map = new MapleMap(910000044, 0, 0, 910000044, 1.0f);
        Foothold upper = new Foothold(new Point(0, 100), new Point(100, 100), 1);
        Foothold knifeShelf = new Foothold(new Point(138, 160), new Point(280, 160), 2);
        Foothold floor = new Foothold(new Point(-100, 400), new Point(600, 400), 3);
        server.maps.FootholdTree footholds = new server.maps.FootholdTree(
                new Point(-2000, -2000), new Point(2000, 2000));
        footholds.insert(upper);
        footholds.insert(knifeShelf);
        footholds.insert(floor);
        map.setFootholds(footholds);

        AgentNavigationGraph graph = AgentNavigationGraphService.rebuildGraph(map);

        boolean hasRightWalkOffFromUpper = graph.regions.stream()
                .flatMap(region -> graph.getOutgoing(region.id).stream())
                .anyMatch(edge -> edge.type == AgentNavigationGraph.EdgeType.DROP
                        && edge.launchStepX > 0
                        && edge.startPoint.y == 100);
        assertFalse(hasRightWalkOffFromUpper,
                "variant-sensitive walk-off landings must not become committed graph edges");
    }

    private static DropTestFixture createDirectionalDropFixture(int mapId) {
        return createDirectionalDropFixture(mapId, true);
    }

    private static DropTestFixture createDirectionalDropFixture(int mapId, boolean dropRight) {
        MapleMap map = new MapleMap(mapId, 0, 0, mapId, 1.0f);
        Foothold upper = new Foothold(new Point(0, 100), new Point(100, 100), 1);
        Foothold lower = dropRight
                ? new Foothold(new Point(106, 160), new Point(280, 160), 2)
                : new Foothold(new Point(-180, 160), new Point(-6, 160), 2);
        server.maps.FootholdTree footholds = new server.maps.FootholdTree(new Point(-2000, -2000), new Point(2000, 2000));
        footholds.insert(upper);
        footholds.insert(lower);
        map.setFootholds(footholds);

        AgentNavigationGraph graph = AgentNavigationGraphService.rebuildGraph(map);
        AgentNavigationGraph.Edge edge = graph.regions.stream()
                .flatMap(region -> graph.getOutgoing(region.id).stream())
                .filter(candidate -> candidate.type == AgentNavigationGraph.EdgeType.DROP
                        && (dropRight ? candidate.launchStepX > 0 : candidate.launchStepX < 0))
                .filter(candidate -> candidate.endPoint.y > candidate.startPoint.y)
                .findFirst()
                .orElse(null);
        assertNotNull(edge, "fixture should produce a directional walk-off drop edge");
        return new DropTestFixture(map, graph, edge);
    }

    private static Character mockBot(Point startPosition, MapleMap map) {
        Character bot = mock(Character.class);
        AtomicReference<Point> position = new AtomicReference<>(new Point(startPosition));
        when(bot.getPosition()).thenAnswer(invocation -> new Point(position.get()));
        doAnswer(invocation -> {
            position.set(new Point(invocation.getArgument(0)));
            return null;
        }).when(bot).setPosition(any(Point.class));
        when(bot.getMap()).thenReturn(map);
        when(bot.getId()).thenReturn(1);
        when(bot.getHp()).thenReturn(100);
        when(bot.getTotalMoveSpeedStat()).thenReturn(100);
        when(bot.getTotalJumpStat()).thenReturn(100);
        return bot;
    }

    private record DropTestFixture(MapleMap map,
                                   AgentNavigationGraph graph,
                                   AgentNavigationGraph.Edge edge) {
    }
}
