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
        boolean previousDialogueTransport =
                config.AgentYamlConfig.config.agent.AGENT_DIALOGUE_TRANSPORT_ENABLED;
        config.AgentYamlConfig.config.agent.AGENT_DIALOGUE_TRANSPORT_ENABLED = true;
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
            bus.publish(new AgentQuestProgressMilestoneEvent(
                    200, 1_002L, 1001, 1210100, "Ribbon Pig", 15, 30, 50,
                    100020100, "quest:1001"),
                    AgentEventPriority.NORMAL);

            assertEquals(6, AgentEventDispatchRuntime.drain(entry));
            AgentProgressionEventProjectionState.Snapshot snapshot = entry.capabilityStates()
                    .require(AgentProgressionEventProjectionState.STATE_KEY).snapshot();
            assertEquals(1, snapshot.levelTransitions());
            assertEquals(1, snapshot.jobAdvancements());
            assertEquals(1, snapshot.questProgressMilestones());
            assertEquals(3, snapshot.revision());
            assertEquals(1, entry.actionMailbox().size());
            assertEquals(3, intents.size());
        } finally {
            intentSubscription.close();
            AgentSessionEventRuntime.close(entry);
            config.AgentYamlConfig.config.agent.AGENT_DIALOGUE_TRANSPORT_ENABLED =
                    previousDialogueTransport;
        }
    }
}
