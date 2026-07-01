package server.agents.integration;

import client.Character;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.capabilities.navigation.AgentNavigationGraph;
import server.agents.capabilities.navigation.AgentNavigationGraphService;
import server.bots.BotEntry;
import server.maps.MapleMap;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class AgentBotMovementCommandRuntimeTest {
    @Test
    void followOwnerUsesAgentModeStateDirectly() {
        Character owner = character(100, 100000000);
        BotEntry entry = new BotEntry(character(200, 100000000), owner, null);

        AgentBotMovementCommandRuntime.followOwner(entry);

        assertTrue(AgentBotModeStateRuntime.following(entry));
        assertFalse(AgentBotModeStateRuntime.grinding(entry));
        assertEquals(0, AgentBotModeStateRuntime.followTargetId(entry));
    }

    @Test
    void stopUsesAgentModeStateDirectly() {
        BotEntry entry = new BotEntry(character(200, 100000000), character(100, 100000000), null);
        AgentBotModeStateRuntime.setFollowing(entry, true);
        AgentBotMoveTargetStateRuntime.setMoveTarget(entry, new Point(10, 20), true);

        AgentBotMovementCommandRuntime.stop(entry);

        assertFalse(AgentBotModeStateRuntime.following(entry));
        assertFalse(AgentBotModeStateRuntime.grinding(entry));
        assertFalse(AgentBotMoveTargetStateRuntime.hasMoveTarget(entry));
    }

    @Test
    void moveToUsesAgentMoveTargetStateDirectly() {
        BotEntry entry = new BotEntry(character(200, 100000000), character(100, 100000000), null);
        Point dest = new Point(10, 20);

        AgentBotMovementCommandRuntime.moveTo(entry, dest, true);

        assertEquals(dest, AgentBotMoveTargetStateRuntime.moveTarget(entry));
        assertTrue(AgentBotMoveTargetStateRuntime.isPrecise(entry));
        assertFalse(AgentBotModeStateRuntime.following(entry));
        assertFalse(AgentBotModeStateRuntime.grinding(entry));
    }

    @Test
    void farmAndGrindUseAgentModeStateDirectly() {
        BotEntry farmEntry = new BotEntry(character(200, 100000000), character(100, 100000000), null);
        Point dest = new Point(30, 40);

        AgentBotMovementCommandRuntime.farmHere(farmEntry, dest);

        assertTrue(AgentBotModeStateRuntime.grinding(farmEntry));
        assertEquals(dest, AgentBotMoveTargetStateRuntime.moveTarget(farmEntry));

        BotEntry grindEntry = new BotEntry(character(201, 100000000), character(100, 100000000), null);
        AgentBotMovementCommandRuntime.grind(grindEntry);

        assertTrue(AgentBotModeStateRuntime.grinding(grindEntry));
        assertFalse(AgentBotMoveTargetStateRuntime.hasMoveTarget(grindEntry));
    }

    @Test
    void patrolRepliesWhenNoGraphRegionExists() {
        MapleMap map = mock(MapleMap.class);
        Character bot = character(200, 100000000);
        when(bot.getMap()).thenReturn(map);
        BotEntry entry = new BotEntry(bot, character(100, 100000000), null);
        Point patrolPos = new Point(30, 40);

        try (MockedStatic<AgentNavigationGraphService> graphs = mockStatic(AgentNavigationGraphService.class);
             MockedStatic<AgentBotManagerReplyRuntime> replies = mockStatic(AgentBotManagerReplyRuntime.class)) {
            graphs.when(() -> AgentNavigationGraphService.peekBestGraph(map, AgentBotMovementStateRuntime.movementProfile(entry)))
                    .thenReturn(null);

            AgentBotMovementCommandRuntime.patrol(entry, patrolPos);

            replies.verify(() -> AgentBotManagerReplyRuntime.replyNow(entry, "can't find a patrol region here"));
            assertFalse(AgentBotPatrolStateRuntime.hasPatrolRegion(entry));
        }
    }

    @Test
    void patrolUsesAgentModeStateDirectlyWhenRegionExists() {
        MapleMap map = mock(MapleMap.class);
        when(map.getId()).thenReturn(100000000);
        Character bot = character(200, 100000000);
        when(bot.getMap()).thenReturn(map);
        BotEntry entry = new BotEntry(bot, character(100, 100000000), null);
        Point patrolPos = new Point(30, 40);
        AgentNavigationGraph graph = mock(AgentNavigationGraph.class);

        try (MockedStatic<AgentNavigationGraphService> graphs = mockStatic(AgentNavigationGraphService.class)) {
            graphs.when(() -> AgentNavigationGraphService.peekBestGraph(map, AgentBotMovementStateRuntime.movementProfile(entry)))
                    .thenReturn(graph);
            when(graph.findRegionId(map, patrolPos)).thenReturn(7);

            AgentBotMovementCommandRuntime.patrol(entry, patrolPos);

            assertTrue(AgentBotModeStateRuntime.grinding(entry));
            assertEquals(7, AgentBotPatrolStateRuntime.patrolRegionId(entry));
            assertEquals(100000000, AgentBotPatrolStateRuntime.patrolMapId(entry));
        }
    }

    private static Character character(int id, int mapId) {
        Character character = mock(Character.class);
        when(character.getId()).thenReturn(id);
        when(character.getMapId()).thenReturn(mapId);
        return character;
    }
}
