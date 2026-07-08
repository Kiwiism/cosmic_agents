package server.agents.integration;

import server.agents.runtime.AgentRuntimeEntry;


import server.agents.integration.AgentMovementStateRuntime;
import server.agents.capabilities.navigation.AgentNavigationGraphService;
import server.agents.capabilities.navigation.AgentNavigationTargetService;

import server.agents.capabilities.navigation.AgentNavigationGraph;

import server.agents.capabilities.movement.AgentMovementProfile;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.capabilities.navigation.AgentNavigationDebugStateRuntime;
import server.maps.Foothold;
import server.maps.MapleMap;

import java.awt.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentNavigationGraphFallbackTest {
    @Test
    void shouldUseClosestCachedGraphBeforeHeuristicFallback() {
        MapleMap map = new MapleMap(910000042, 0, 0, 910000042, 1.0f);
        server.maps.FootholdTree footholds = new server.maps.FootholdTree(new Point(-2000, -2000), new Point(2000, 2000));
        footholds.insert(new Foothold(new Point(0, 100), new Point(100, 100), 1));
        footholds.insert(new Foothold(new Point(106, 160), new Point(280, 160), 2));
        map.setFootholds(footholds);

        AgentNavigationGraph cachedGraph = AgentNavigationGraphService.rebuildGraph(map, AgentMovementProfile.base());
        assertNotNull(cachedGraph);

        Character bot = mockBot(new Point(60, 100), map);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        AgentMovementStateRuntime.setMovementProfile(entry, new AgentMovementProfile(125, 110));

        AgentNavigationTargetService.NavigationDirective directive =
                AgentNavigationTargetService.resolveTarget(entry, new Point(180, 160), true);

        assertFalse(AgentNavigationDebugStateRuntime.graphWarmupFallback(entry), "closest cached graph should be used before heuristics");
        assertFalse(directive.consumedTick() && AgentNavigationDebugStateRuntime.activeNavigationEdge(entry) == null
                        && "graph-warmup".equals(AgentNavigationDebugStateRuntime.lastDecision(entry)),
                "cached fallback graph should route with a graph edge, not heuristic warmup");
        assertNotNull(AgentNavigationDebugStateRuntime.activeNavigationEdge(entry), "cached fallback graph should provide a real nav edge");
        assertFalse("graph-warmup".equals(AgentNavigationDebugStateRuntime.lastDecision(entry)),
                "nav should stay on graph-based routing instead of dropping straight into heuristics");
    }

    @Test
    void shouldGenerateDirectionalDropAtWallTopLedge() {
        MapleMap map = new MapleMap(910000051, 0, 0, 910000051, 1.0f);
        server.maps.FootholdTree footholds = new server.maps.FootholdTree(new Point(-2000, -2000), new Point(2000, 2000));
        Foothold lower = new Foothold(new Point(0, 100), new Point(50, 100), 1);
        Foothold wall = new Foothold(new Point(50, 80), new Point(50, 100), 2);
        Foothold upper = new Foothold(new Point(50, 80), new Point(140, 80), 3);
        wall.setNext(lower.getId());
        wall.setPrev(upper.getId());
        footholds.insert(lower);
        footholds.insert(wall);
        footholds.insert(upper);
        map.setFootholds(footholds);

        AgentNavigationGraph graph = AgentNavigationGraphService.rebuildGraph(map, AgentMovementProfile.base());
        int upperRegionId = graph.findRegionId(map, new Point(100, 80));

        assertTrue(graph.getOutgoing(upperRegionId).stream()
                        .anyMatch(edge -> edge.type == AgentNavigationGraph.EdgeType.DROP && edge.launchStepX < 0),
                "a wall whose top is level with the current ground is a walk-off ledge");
    }

    @Test
    void shouldNotGenerateJumpWindowFromUnapproachableWallBoundaryLaunchPoint() {
        MapleMap map = new MapleMap(910000053, 0, 0, 910000053, 1.0f);
        server.maps.FootholdTree footholds = new server.maps.FootholdTree(new Point(-2000, -2000), new Point(2000, 2000));
        Foothold upper = new Foothold(new Point(0, 60), new Point(50, 60), 1);
        Foothold wall = new Foothold(new Point(50, 60), new Point(50, 100), 2);
        Foothold lower = new Foothold(new Point(50, 100), new Point(140, 100), 3);
        wall.setPrev(upper.getId());
        wall.setNext(lower.getId());
        footholds.insert(upper);
        footholds.insert(wall);
        footholds.insert(lower);
        map.setFootholds(footholds);

        AgentNavigationGraph graph = AgentNavigationGraphService.rebuildGraph(map, AgentMovementProfile.base());
        int lowerRegionId = graph.findRegionId(map, new Point(51, 100));
        int upperRegionId = graph.findRegionId(map, new Point(49, 60));

        assertFalse(graph.getOutgoing(lowerRegionId).stream()
                        .anyMatch(edge -> edge.type == AgentNavigationGraph.EdgeType.JUMP
                                && edge.toRegionId == upperRegionId
                                && edge.containsLaunchX(50)),
                "jump launch windows must not include a collidable wall boundary");
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
}
