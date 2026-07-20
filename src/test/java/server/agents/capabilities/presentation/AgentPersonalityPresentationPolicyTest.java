package server.agents.capabilities.presentation;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.capabilities.movement.fidget.AgentFidgetMode;
import server.agents.capabilities.movement.fidget.AgentFidgetService;
import server.agents.capabilities.movement.fidget.AgentFidgetStateRuntime;
import server.agents.capabilities.movement.fidget.AgentFidgetTrigger;
import server.agents.integration.PrimitiveCapabilityGateway;
import server.agents.personality.AgentPersonalityAssignment;
import server.agents.personality.AgentPersonalityProfile;
import server.agents.personality.AgentPersonalityState;
import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentPersonalityPresentationPolicyTest {
    @Test
    void observerDepartureStopsActivePresentationFidget() {
        Character agent = mock(Character.class);
        when(agent.getPosition()).thenReturn(new Point(100, 100));
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        enablePresentation(entry);
        AgentFidgetService.startFidget(entry, AgentFidgetMode.WAIT, 1_000, 1_000,
                AgentFidgetTrigger.PERSONALITY_PRESENTATION);
        assertTrue(AgentFidgetStateRuntime.active(entry));

        boolean consumed = AgentPersonalityPresentationPolicy.tick(
                entry,
                agent,
                1_001,
                0,
                new Point(100, 100),
                mock(PrimitiveCapabilityGateway.class));

        assertFalse(consumed);
        assertFalse(AgentFidgetStateRuntime.active(entry));
    }

    private static void enablePresentation(AgentRuntimeEntry entry) {
        AgentPersonalityProfile profile = new AgentPersonalityProfile(
                "test-v1", 1, new AgentPersonalityProfile.Traits(50, 50, 50, 50, 50, 50, 50));
        AgentPersonalityAssignment assignment = new AgentPersonalityAssignment(
                1, 1, "TestAgent", profile.profileId(), profile.profileVersion(), 123L, 0L);
        entry.capabilityStates().require(AgentPersonalityState.STATE_KEY)
                .assign(assignment, profile, true);
    }
}
