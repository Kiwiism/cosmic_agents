package server.agents.operations.events;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.capabilities.dialogue.AgentDialogueIntentEvent;
import server.agents.events.AgentEventBus;
import server.agents.events.AgentEventPriority;
import server.agents.runtime.AgentEventDispatchRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentSessionEventRuntime;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentOperationalProjectionIntegrationTest {
    @Test
    void operationalFactsUpdateReadModelMaintenanceAndLifeDialogue() {
        Character agent = mock(Character.class);
        when(agent.getId()).thenReturn(402);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        AgentEventBus bus = AgentSessionEventRuntime.bus(entry);
        List<AgentDialogueIntentEvent> intents = new ArrayList<>();
        var subscription = bus.subscribe(AgentDialogueIntentEvent.TYPE,
                event -> intents.add(assertInstanceOf(AgentDialogueIntentEvent.class, event)));

        try {
            bus.publish(new AgentNavigationRouteFailedEvent(
                    402, 1_000L, 1010100, 1, 2, 30, 40, "no-path", "grind:402"),
                    AgentEventPriority.IMPORTANT);
            bus.publish(new AgentLifeStateChangedEvent(
                    402, 1_001L, "ALIVE", "DEAD", 1010100, true, "grind:402"),
                    AgentEventPriority.CRITICAL);

            assertEquals(3, AgentEventDispatchRuntime.drain(entry));
            AgentOperationalEventProjectionState.Snapshot snapshot = entry.capabilityStates()
                    .require(AgentOperationalEventProjectionState.STATE_KEY).snapshot();
            assertEquals(1, snapshot.routeFailures());
            assertEquals(1, snapshot.lifeTransitions());
            assertEquals(2, snapshot.revision());
            assertEquals(1, entry.actionMailbox().size());
            assertEquals(1, intents.size());
            assertEquals(AgentOperationalDialogueReactionService.LIFE_STATE_INTENT,
                    intents.getFirst().intentKey());
        } finally {
            subscription.close();
            AgentSessionEventRuntime.close(entry);
        }
    }
}
