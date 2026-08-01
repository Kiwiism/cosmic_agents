package server.agents.progression.events;

import client.Character;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import server.agents.events.AgentEvent;
import server.agents.runtime.AgentEventDispatchRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;
import server.agents.runtime.AgentSessionEventRuntime;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentQuestProgressMilestonePublisherTest {
    @AfterEach
    void clearRegistry() {
        AgentRuntimeRegistry.clear();
    }

    @Test
    void publishesEachConfiguredThresholdOnlyWhenTheCounterCrossesIt() {
        Character agent = mock(Character.class);
        when(agent.getId()).thenReturn(193);
        when(agent.getMapId()).thenReturn(100020100);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        AgentRuntimeRegistry.registerEntry(entry);
        List<AgentEvent> events = new ArrayList<>();
        var subscription = AgentSessionEventRuntime.bus(entry).subscribe(
                AgentQuestProgressMilestoneEvent.TYPE, events::add);

        try {
            AgentQuestProgressMilestonePublisher.publishMobProgress(
                    agent, 1001, 1210100, 14, 15, 30);
            AgentQuestProgressMilestonePublisher.publishMobProgress(
                    agent, 1001, 1210100, 15, 16, 30);
            AgentQuestProgressMilestonePublisher.publishMobProgress(
                    agent, 1001, 1210100, 26, 27, 30);

            AgentEventDispatchRuntime.drain(entry);
            assertEquals(2, events.size());
            AgentQuestProgressMilestoneEvent halfway = assertInstanceOf(
                    AgentQuestProgressMilestoneEvent.class, events.get(0));
            AgentQuestProgressMilestoneEvent nearlyDone = assertInstanceOf(
                    AgentQuestProgressMilestoneEvent.class, events.get(1));
            assertEquals(50, halfway.milestonePercent());
            assertEquals(15, halfway.currentCount());
            assertFalse(halfway.targetName().isBlank());
            assertEquals(90, nearlyDone.milestonePercent());
            assertEquals(27, nearlyDone.currentCount());
        } finally {
            subscription.close();
            AgentSessionEventRuntime.close(entry);
            AgentRuntimeRegistry.unregisterEntry(entry);
        }
    }

    @Test
    void publishesItemMilestonesWithAnItemLabel() {
        Character agent = mock(Character.class);
        when(agent.getId()).thenReturn(195);
        when(agent.getMapId()).thenReturn(103000000);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        AgentRuntimeRegistry.registerEntry(entry);
        List<AgentEvent> events = new ArrayList<>();
        var subscription = AgentSessionEventRuntime.bus(entry).subscribe(
                AgentQuestProgressMilestoneEvent.TYPE, events::add);

        try {
            AgentQuestProgressMilestonePublisher.publishItemProgress(
                    agent, 2091, 4000004, 19, 20, 40);

            AgentEventDispatchRuntime.drain(entry);
            assertEquals(1, events.size());
            AgentQuestProgressMilestoneEvent halfway = assertInstanceOf(
                    AgentQuestProgressMilestoneEvent.class, events.getFirst());
            assertEquals(20, halfway.currentCount());
            assertEquals(40, halfway.requiredCount());
            assertFalse(halfway.targetName().isBlank());
        } finally {
            subscription.close();
            AgentSessionEventRuntime.close(entry);
            AgentRuntimeRegistry.unregisterEntry(entry);
        }
    }

    @Test
    void nearlyCompleteUsesOneRemainingKillForObjectivesBelowTen() {
        Character agent = mock(Character.class);
        when(agent.getId()).thenReturn(194);
        when(agent.getMapId()).thenReturn(100020100);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        AgentRuntimeRegistry.registerEntry(entry);
        List<AgentEvent> events = new ArrayList<>();
        var subscription = AgentSessionEventRuntime.bus(entry).subscribe(
                AgentQuestProgressMilestoneEvent.TYPE, events::add);

        try {
            AgentQuestProgressMilestonePublisher.publishMobProgress(
                    agent, 1002, 1210100, 3, 4, 5);

            AgentEventDispatchRuntime.drain(entry);
            assertEquals(1, events.size());
            AgentQuestProgressMilestoneEvent nearlyDone = assertInstanceOf(
                    AgentQuestProgressMilestoneEvent.class, events.getFirst());
            assertEquals(90, nearlyDone.milestonePercent());
            assertEquals(4, nearlyDone.currentCount());
            assertEquals(5, nearlyDone.requiredCount());
        } finally {
            subscription.close();
            AgentSessionEventRuntime.close(entry);
            AgentRuntimeRegistry.unregisterEntry(entry);
        }
    }
}
