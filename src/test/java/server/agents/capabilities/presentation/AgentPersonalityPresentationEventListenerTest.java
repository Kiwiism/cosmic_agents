package server.agents.capabilities.presentation;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.operations.events.AgentMapTransitionedEvent;
import server.agents.personality.AgentPersonalityAssignment;
import server.agents.personality.AgentPersonalityProfile;
import server.agents.personality.AgentPersonalityProfileRepository;
import server.agents.personality.AgentPersonalityState;
import server.agents.runtime.AgentRuntimeEntry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentPersonalityPresentationEventListenerTest {
    @Test
    void arrivalEventSchedulesOneBoundedPresentationIntent() {
        AgentPresentationTelemetry.resetForTests();
        AgentPersonalityProfile profile = AgentPersonalityProfileRepository.defaultRepository()
                .find("restless-v1").orElseThrow();
        long seed = activatingSeed(profile, AgentPresentationTrigger.ARRIVAL);
        Character agent = mock(Character.class);
        when(agent.getId()).thenReturn(42);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        entry.capabilityStates().require(AgentPersonalityState.STATE_KEY).assign(
                new AgentPersonalityAssignment(
                        1, 42, "EventFixture", profile.profileId(), profile.profileVersion(), seed, 0L),
                profile, true);

        new AgentPersonalityPresentationEventListener(entry).onAgentEvent(
                new AgentMapTransitionedEvent(42, 1_000L, 100000000, 100000001,
                        0, "test", "fixture"));

        assertNotNull(entry.capabilityStates().require(AgentPresentationState.STATE_KEY)
                .takeDue(Long.MAX_VALUE));
        assertEquals(1L, AgentPresentationTelemetry.snapshot().triggers());
        assertEquals(1L, AgentPresentationTelemetry.snapshot().scheduled());
    }

    private static long activatingSeed(AgentPersonalityProfile profile,
                                       AgentPresentationTrigger trigger) {
        for (long seed = 1L; seed < 10_000L; seed++) {
            if (AgentPersonalityPresentationResolver.resolve(
                    profile, seed, 1L, trigger, 1_000L) != null) {
                return seed;
            }
        }
        throw new AssertionError("no activating seed found");
    }
}
