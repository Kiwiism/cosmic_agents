package server.agents.resources.events;

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

class AgentResourceProjectionIntegrationTest {
    @Test
    void capacityFactUpdatesReadModelCoalescesMaintenanceAndCreatesDialogueIntent() {
        Character agent = mock(Character.class);
        when(agent.getId()).thenReturn(302);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        AgentEventBus bus = AgentSessionEventRuntime.bus(entry);
        List<AgentDialogueIntentEvent> intents = new ArrayList<>();
        var subscription = bus.subscribe(AgentDialogueIntentEvent.TYPE,
                event -> intents.add(assertInstanceOf(AgentDialogueIntentEvent.class, event)));

        try {
            bus.publish(new AgentInventoryThresholdChangedEvent(
                    302, 1_000L, "USE", 0, 24, "FULL", "grind:302"),
                    AgentEventPriority.IMPORTANT);

            assertEquals(2, AgentEventDispatchRuntime.drain(entry));
            AgentResourceEventProjectionState.Snapshot snapshot = entry.capabilityStates()
                    .require(AgentResourceEventProjectionState.STATE_KEY).snapshot();
            assertEquals(1, snapshot.inventoryThresholds());
            assertEquals(1, snapshot.revision());
            assertEquals(1, entry.actionMailbox().size());
            assertEquals(1, intents.size());
            assertEquals(AgentResourceDialogueReactionService.INVENTORY_FULL_INTENT,
                    intents.getFirst().intentKey());
        } finally {
            subscription.close();
            AgentSessionEventRuntime.close(entry);
        }
    }
}
