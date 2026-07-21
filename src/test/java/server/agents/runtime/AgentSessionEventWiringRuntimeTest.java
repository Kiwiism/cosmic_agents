package server.agents.runtime;

import client.Character;
import config.YamlConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import server.agents.events.AgentEventBus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class AgentSessionEventWiringRuntimeTest {
    private boolean previousLegacyDialogue;

    @BeforeEach
    void enableDialogueForExistingWiringExpectations() {
        previousLegacyDialogue = YamlConfig.config.server.AGENT_LEGACY_DIALOGUE_ENABLED;
        YamlConfig.config.server.AGENT_LEGACY_DIALOGUE_ENABLED = true;
    }

    @AfterEach
    void clearRolloutProperties() {
        YamlConfig.config.server.AGENT_LEGACY_DIALOGUE_ENABLED = previousLegacyDialogue;
        System.clearProperty("agents.events.reactions.enabled");
        System.clearProperty("agents.events.dialogue.enabled");
        System.clearProperty("agents.events.coordination.enabled");
        System.clearProperty("agents.events.llmContext.enabled");
        System.clearProperty("agents.events.capacity");
    }

    @Test
    void productionSubscriptionsAreRegisteredOnceAndClosedWithSession() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(mock(Character.class), null, null);
        AgentEventBus bus = AgentSessionEventRuntime.bus(entry);
        int personalityListener = YamlConfig.config.server.AGENT_PERSONALITY_PRESENTATION_ENABLED
                ? 1 : 0;

        assertEquals(16 + personalityListener, bus.snapshot().subscriptions());
        assertEquals(16 + personalityListener,
                AgentSessionEventRuntime.bus(entry).snapshot().subscriptions());

        AgentSessionEventRuntime.close(entry);

        assertTrue(bus.snapshot().closed());
        assertEquals(0, bus.snapshot().subscriptions());
        assertFalse(entry.capabilityStates().find(AgentSessionEventWiringState.STATE_KEY).isPresent());
    }

    @Test
    void optionalConsumersCanBeRolledBackIndependentlyOfMonitoring() {
        System.setProperty("agents.events.reactions.enabled", "false");
        System.setProperty("agents.events.dialogue.enabled", "false");
        System.setProperty("agents.events.coordination.enabled", "false");
        System.setProperty("agents.events.llmContext.enabled", "false");
        AgentRuntimeEntry entry = new AgentRuntimeEntry(mock(Character.class), null, null);
        AgentEventBus bus = AgentSessionEventRuntime.bus(entry);
        int personalityListener = YamlConfig.config.server.AGENT_PERSONALITY_PRESENTATION_ENABLED
                ? 1 : 0;

        assertEquals(5 + personalityListener, bus.snapshot().subscriptions());

        AgentSessionEventRuntime.close(entry);
    }

    @Test
    void legacyDialogueYamlGateAlsoDisablesEventDialogueConsumers() {
        YamlConfig.config.server.AGENT_LEGACY_DIALOGUE_ENABLED = false;
        AgentRuntimeEntry entry = new AgentRuntimeEntry(mock(Character.class), null, null);
        AgentEventBus bus = AgentSessionEventRuntime.bus(entry);
        int personalityListener = YamlConfig.config.server.AGENT_PERSONALITY_PRESENTATION_ENABLED
                ? 1 : 0;

        assertEquals(11 + personalityListener, bus.snapshot().subscriptions());

        AgentSessionEventRuntime.close(entry);
    }

    @Test
    void sessionQueueCapacityCanBeTunedAtStartup() {
        System.setProperty("agents.events.capacity", "7");
        AgentRuntimeEntry entry = new AgentRuntimeEntry(mock(Character.class), null, null);
        AgentEventBus bus = AgentSessionEventRuntime.bus(entry);

        assertEquals(7, bus.snapshot().capacity());

        AgentSessionEventRuntime.close(entry);
    }
}
