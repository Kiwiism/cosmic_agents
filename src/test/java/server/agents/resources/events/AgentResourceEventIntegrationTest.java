package server.agents.resources.events;

import client.Character;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import server.agents.events.AgentEvent;
import server.agents.events.AgentEventBus;
import server.agents.events.AgentEventPriority;
import server.agents.runtime.AgentEventDispatchRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;
import server.agents.runtime.AgentSessionEventRuntime;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentResourceEventIntegrationTest {
    @AfterEach
    void clearRegistry() {
        AgentRuntimeRegistry.clear();
    }

    @Test
    void publisherResolvesRegisteredAgentAndCarriesActiveSessionFact() {
        Character agent = mock(Character.class);
        when(agent.getId()).thenReturn(301);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        AgentRuntimeRegistry.registerEntry(entry);
        AgentEventBus bus = AgentSessionEventRuntime.bus(entry);
        List<AgentEvent> received = new ArrayList<>();
        var subscription = bus.subscribe(AgentScrollResolvedEvent.TYPE, received::add);

        try {
            AgentResourceEventPublisher.publishFor(agent,
                    objectiveId -> new AgentScrollResolvedEvent(
                            agent.getId(), 25L, 2040001, "SUCCESS", 100000000, objectiveId),
                    AgentEventPriority.IMPORTANT);

            assertEquals(1, AgentEventDispatchRuntime.drain(entry));
            AgentScrollResolvedEvent event = assertInstanceOf(
                    AgentScrollResolvedEvent.class, received.getFirst());
            assertEquals(301, event.agentId());
            assertEquals("SUCCESS", event.result());
            assertEquals("", event.objectiveId());
        } finally {
            subscription.close();
            AgentSessionEventRuntime.close(entry);
            AgentRuntimeRegistry.unregisterEntry(entry);
        }
    }
}
