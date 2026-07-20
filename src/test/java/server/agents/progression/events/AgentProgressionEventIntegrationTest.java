package server.agents.progression.events;

import client.Character;
import client.Job;
import org.junit.jupiter.api.Test;
import server.agents.capabilities.build.AgentBuildService;
import server.agents.capabilities.build.AgentBuildStateRuntime;
import server.agents.events.AgentEvent;
import server.agents.events.AgentEventBus;
import server.agents.runtime.AgentEventDispatchRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentSessionEventRuntime;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentProgressionEventIntegrationTest {
    @Test
    void buildBoundariesPublishLevelAndJobFactsInMutationOrder() {
        Character agent = mock(Character.class);
        when(agent.getId()).thenReturn(200);
        when(agent.getLevel()).thenReturn(10);
        when(agent.getJob()).thenReturn(Job.BEGINNER);
        when(agent.getMapId()).thenReturn(104000000);
        when(agent.getRemainingAp()).thenReturn(0);
        when(agent.getRemainingSps()).thenReturn(new int[5]);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        AgentBuildStateRuntime.setLastKnownLevel(entry, 9);
        AgentEventBus bus = AgentSessionEventRuntime.bus(entry);
        List<AgentEvent> events = new ArrayList<>();
        var levelSubscription = bus.subscribe(AgentLevelChangedEvent.TYPE, events::add);
        var jobSubscription = bus.subscribe(AgentJobAdvancedEvent.TYPE, events::add);

        try {
            AgentBuildService.checkLevelUp(entry, agent);
            AgentBuildService.handleJobAdvance(entry, agent, Job.BEGINNER, Job.WARRIOR);

            assertEquals(4, AgentEventDispatchRuntime.drain(entry));
            assertEquals(2, events.size());
            AgentLevelChangedEvent level = assertInstanceOf(AgentLevelChangedEvent.class, events.get(0));
            AgentJobAdvancedEvent job = assertInstanceOf(AgentJobAdvancedEvent.class, events.get(1));
            assertEquals(9, level.previousLevel());
            assertEquals(10, level.level());
            assertEquals(Job.BEGINNER.getId(), job.previousJobId());
            assertEquals(Job.WARRIOR.getId(), job.jobId());
        } finally {
            levelSubscription.close();
            jobSubscription.close();
            AgentSessionEventRuntime.close(entry);
        }
    }
}
