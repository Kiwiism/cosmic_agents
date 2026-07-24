package server.agents.progression.events;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.capabilities.dialogue.AgentDialogueIntentEvent;
import server.agents.events.AgentEventBus;
import server.agents.events.AgentEventPriority;
import server.agents.progression.AgentCareerProgressionState;
import server.agents.runtime.AgentEventDispatchRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentSessionEventRuntime;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentProgressionProjectionIntegrationTest {
    @Test
    void progressionFactsUpdateReadModelCoalesceCheckpointAndCreateDialogueIntents() {
        Character agent = mock(Character.class);
        when(agent.getId()).thenReturn(200);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        entry.capabilityStates().require(AgentCareerProgressionState.STATE_KEY);
        boolean previousLegacyDialogue =
                config.AgentYamlConfig.config.agent.AGENT_LEGACY_DIALOGUE_ENABLED;
        config.AgentYamlConfig.config.agent.AGENT_LEGACY_DIALOGUE_ENABLED = true;
        AgentEventBus bus = AgentSessionEventRuntime.bus(entry);
        List<AgentDialogueIntentEvent> intents = new ArrayList<>();
        var intentSubscription = bus.subscribe(AgentDialogueIntentEvent.TYPE,
                event -> intents.add(assertInstanceOf(AgentDialogueIntentEvent.class, event)));

        try {
            bus.publish(new AgentLevelChangedEvent(
                    200, 1_000L, 9, 10, 0, 104000000, "career:200"),
                    AgentEventPriority.IMPORTANT);
            bus.publish(new AgentJobAdvancedEvent(
                    200, 1_001L, 0, 100, 10, 102000003, "career:200"),
                    AgentEventPriority.IMPORTANT);

            assertEquals(4, AgentEventDispatchRuntime.drain(entry));
            AgentProgressionEventProjectionState.Snapshot snapshot = entry.capabilityStates()
                    .require(AgentProgressionEventProjectionState.STATE_KEY).snapshot();
            assertEquals(1, snapshot.levelTransitions());
            assertEquals(1, snapshot.jobAdvancements());
            assertEquals(2, snapshot.revision());
            assertEquals(1, entry.actionMailbox().size());
            assertEquals(2, intents.size());
        } finally {
            intentSubscription.close();
            AgentSessionEventRuntime.close(entry);
            config.AgentYamlConfig.config.agent.AGENT_LEGACY_DIALOGUE_ENABLED =
                    previousLegacyDialogue;
        }
    }
}
