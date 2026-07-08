package server.agents.integration;

import client.Character;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.capabilities.navigation.AgentNavigationGraph;
import server.agents.capabilities.navigation.AgentNavigationGraphService;
import server.agents.runtime.AgentModeStateRuntime;
import server.agents.runtime.AgentPatrolStateRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.maps.MapleMap;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class AgentMovementCommandRuntimeTest {
    @Test
    void followOwnerUsesAgentModeStateDirectly() {
        Character owner = character(100, 100000000);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(character(200, 100000000), owner, null);

        AgentMovementCommandRuntime.followOwner(entry);

        assertTrue(AgentModeStateRuntime.following(entry));
        assertFalse(AgentModeStateRuntime.grinding(entry));
        assertEquals(0, AgentModeStateRuntime.followTargetId(entry));
    }

    @Test
    void followTargetUsesAgentModeStateDirectly() {
        Character target = character(300, 100000000);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(character(200, 100000000), character(100, 100000000), null);

        AgentMovementCommandRuntime.follow(entry, target);

        assertTrue(AgentModeStateRuntime.following(entry));
        assertFalse(AgentModeStateRuntime.grinding(entry));
        assertEquals(target.getId(), AgentModeStateRuntime.followTargetId(entry));
    }

    @Test
    void stopUsesAgentModeStateDirectly() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(character(200, 100000000), character(100, 100000000), null);
        AgentModeStateRuntime.setFollowing(entry, true);
        AgentMoveTargetStateRuntime.setMoveTarget(entry, new Point(10, 20), true);

        AgentMovementCommandRuntime.stop(entry);

        assertFalse(AgentModeStateRuntime.following(entry));
        assertFalse(AgentModeStateRuntime.grinding(entry));
        assertFalse(AgentMoveTargetStateRuntime.hasMoveTarget(entry));
    }

    @Test
    void moveToUsesAgentMoveTargetStateDirectly() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(character(200, 100000000), character(100, 100000000), null);
        Point dest = new Point(10, 20);

        AgentMovementCommandRuntime.moveTo(entry, dest, true);

        assertEquals(dest, AgentMoveTargetStateRuntime.moveTarget(entry));
        assertTrue(AgentMoveTargetStateRuntime.isPrecise(entry));
        assertFalse(AgentModeStateRuntime.following(entry));
        assertFalse(AgentModeStateRuntime.grinding(entry));
    }

    @Test
    void farmAndGrindUseAgentModeStateDirectly() {
        AgentRuntimeEntry farmEntry = new AgentRuntimeEntry(character(200, 100000000), character(100, 100000000), null);
        Point dest = new Point(30, 40);

        AgentMovementCommandRuntime.farmHere(farmEntry, dest);

        assertTrue(AgentModeStateRuntime.grinding(farmEntry));
        assertEquals(dest, AgentMoveTargetStateRuntime.moveTarget(farmEntry));

        AgentRuntimeEntry grindEntry = new AgentRuntimeEntry(character(201, 100000000), character(100, 100000000), null);
        AgentMovementCommandRuntime.grind(grindEntry);

        assertTrue(AgentModeStateRuntime.grinding(grindEntry));
        assertFalse(AgentMoveTargetStateRuntime.hasMoveTarget(grindEntry));
    }

    @Test
    void patrolRepliesWhenNoGraphRegionExists() {
        MapleMap map = mock(MapleMap.class);
        Character bot = character(200, 100000000);
        when(bot.getMap()).thenReturn(map);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, character(100, 100000000), null);
        Point patrolPos = new Point(30, 40);

        try (MockedStatic<AgentNavigationGraphService> graphs = mockStatic(AgentNavigationGraphService.class);
             MockedStatic<AgentReplyRuntime> replies = mockStatic(AgentReplyRuntime.class)) {
            graphs.when(() -> AgentNavigationGraphService.peekBestGraph(map, AgentMovementStateRuntime.movementProfile(entry)))
                    .thenReturn(null);

            AgentMovementCommandRuntime.patrol(entry, patrolPos);

            replies.verify(() -> AgentReplyRuntime.replyNow(entry, "can't find a patrol region here"));
            assertFalse(AgentPatrolStateRuntime.hasPatrolRegion(entry));
        }
    }

    @Test
    void patrolUsesAgentModeStateDirectlyWhenRegionExists() {
        MapleMap map = mock(MapleMap.class);
        when(map.getId()).thenReturn(100000000);
        Character bot = character(200, 100000000);
        when(bot.getMap()).thenReturn(map);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, character(100, 100000000), null);
        Point patrolPos = new Point(30, 40);
        AgentNavigationGraph graph = mock(AgentNavigationGraph.class);

        try (MockedStatic<AgentNavigationGraphService> graphs = mockStatic(AgentNavigationGraphService.class)) {
            graphs.when(() -> AgentNavigationGraphService.peekBestGraph(map, AgentMovementStateRuntime.movementProfile(entry)))
                    .thenReturn(graph);
            when(graph.findRegionId(map, patrolPos)).thenReturn(7);

            AgentMovementCommandRuntime.patrol(entry, patrolPos);

            assertTrue(AgentModeStateRuntime.grinding(entry));
            assertEquals(7, AgentPatrolStateRuntime.patrolRegionId(entry));
            assertEquals(100000000, AgentPatrolStateRuntime.patrolMapId(entry));
        }
    }

    private static Character character(int id, int mapId) {
        Character character = mock(Character.class);
        when(character.getId()).thenReturn(id);
        when(character.getMapId()).thenReturn(mapId);
        return character;
    }
}
