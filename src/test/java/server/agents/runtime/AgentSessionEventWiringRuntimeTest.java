package server.agents.runtime;

import client.Character;
import config.YamlConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import server.agents.events.AgentEventBus;
import server.agents.behavior.AgentBehaviorFeatureProfile;
import server.agents.capabilities.presentation.AgentPresentationProfile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class AgentSessionEventWiringRuntimeTest {
    private boolean previousTransportEnabled;

    @BeforeEach
    void enableDialogueForExistingWiringExpectations() {
        previousTransportEnabled = config.AgentYamlConfig.config.agent.AGENT_DIALOGUE_TRANSPORT_ENABLED;
        config.AgentYamlConfig.config.agent.AGENT_DIALOGUE_TRANSPORT_ENABLED = true;
    }

    @AfterEach
    void clearRolloutProperties() {
        config.AgentYamlConfig.config.agent.AGENT_DIALOGUE_TRANSPORT_ENABLED = previousTransportEnabled;
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
        int personalityListeners = AgentPresentationProfile.current().enabled()
                ? 2 : 0;
        int behaviorListener = AgentBehaviorFeatureProfile.current().enabled() ? 1 : 0;

        assertEquals(32 + personalityListeners + behaviorListener, bus.snapshot().subscriptions());
        assertEquals(32 + personalityListeners + behaviorListener,
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
        int personalityListeners = AgentPresentationProfile.current().enabled()
                ? 2 : 0;
        int behaviorListener = AgentBehaviorFeatureProfile.current().enabled() ? 1 : 0;

        assertEquals(19 + personalityListeners + behaviorListener, bus.snapshot().subscriptions());

        AgentSessionEventRuntime.close(entry);
    }

    @Test
    void disabledDialogueTransportLeavesIntentionProgressDialogueAvailable() {
        config.AgentYamlConfig.config.agent.AGENT_DIALOGUE_TRANSPORT_ENABLED = false;
        AgentRuntimeEntry entry = new AgentRuntimeEntry(mock(Character.class), null, null);
        AgentEventBus bus = AgentSessionEventRuntime.bus(entry);
        int personalityListeners = AgentPresentationProfile.current().enabled()
                ? 2 : 0;
        int behaviorListener = AgentBehaviorFeatureProfile.current().enabled() ? 1 : 0;

        assertEquals(26 + personalityListeners + behaviorListener, bus.snapshot().subscriptions());

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
