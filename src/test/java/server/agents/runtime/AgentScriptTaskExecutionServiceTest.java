package server.agents.runtime;

import client.Character;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.integration.AgentBotMovementCommandRuntime;
import server.agents.plans.AgentTask;
import server.bots.BotEntry;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class AgentScriptTaskExecutionServiceTest {
    @Test
    void startFollowTargetResolvesThroughRuntimeRegistry() {
        Character leader = character(100, 100000000, new Point(0, 0), true);
        Character agent = character(200, 100000000, new Point(0, 0), true);
        Character sibling = character(300, 100000000, new Point(20, 0), true);
        BotEntry entry = new BotEntry(agent, leader, null);
        AgentRuntimeRegistry.entriesByLeaderId().clear();
        AgentRuntimeRegistry.mutableEntriesForLeader(leader.getId()).add(entry);
        AgentRuntimeRegistry.mutableEntriesForLeader(leader.getId()).add(new BotEntry(sibling, leader, null));

        try (MockedStatic<AgentBotMovementCommandRuntime> movement =
                     mockStatic(AgentBotMovementCommandRuntime.class)) {
            AgentScriptTaskExecutionService.start(entry, AgentTask.follow(sibling));

            movement.verify(() -> AgentBotMovementCommandRuntime.follow(entry, sibling));
        } finally {
            AgentRuntimeRegistry.entriesByLeaderId().clear();
        }
    }

    @Test
    void completionUsesRuntimeRegistryForFollowUntilNear() {
        Character leader = character(100, 100000000, new Point(0, 0), true);
        Character agent = character(200, 100000000, new Point(0, 0), true);
        Character sibling = character(300, 100000000, new Point(20, 0), true);
        BotEntry entry = new BotEntry(agent, leader, null);
        AgentRuntimeRegistry.entriesByLeaderId().clear();
        AgentRuntimeRegistry.mutableEntriesForLeader(leader.getId()).add(entry);
        AgentRuntimeRegistry.mutableEntriesForLeader(leader.getId()).add(new BotEntry(sibling, leader, null));

        try {
            assertTrue(AgentScriptTaskExecutionService.isComplete(
                    entry, AgentTask.followUntilNear(sibling, 25), 50));
        } finally {
            AgentRuntimeRegistry.entriesByLeaderId().clear();
        }
    }

    private static Character character(int id, int mapId, Point position, boolean loggedIn) {
        Character character = mock(Character.class);
        when(character.getId()).thenReturn(id);
        when(character.getMapId()).thenReturn(mapId);
        when(character.getPosition()).thenReturn(new Point(position));
        when(character.isLoggedinWorld()).thenReturn(loggedIn);
        return character;
    }
}
