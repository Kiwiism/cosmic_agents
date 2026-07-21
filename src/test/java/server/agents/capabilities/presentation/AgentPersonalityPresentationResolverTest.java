package server.agents.capabilities.presentation;

import org.junit.jupiter.api.Test;
import server.agents.personality.AgentPersonalityProfile;
import server.agents.personality.AgentPersonalityProfileRepository;
import server.agents.personality.AgentPersonalityAssignment;
import server.agents.personality.AgentPersonalityState;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentPersonalityPresentationResolverTest {
    @Test
    void decisionsReplayExactlyForTheSameIdentityAndEventSequence() {
        AgentPersonalityProfile profile = profile("restless-v1");

        List<AgentPresentationDecision> first = decisions(profile, 8811L);
        List<AgentPresentationDecision> replay = decisions(profile, 8811L);

        assertEquals(first, replay);
        assertTrue(first.stream().anyMatch(java.util.Objects::nonNull));
    }

    @Test
    void semanticArchetypesResolveToDifferentPresentationSequences() {
        List<AgentPresentationDecision> relaxed = decisions(profile("relaxed-v1"), 9911L);
        List<AgentPresentationDecision> restless = decisions(profile("restless-v1"), 9911L);

        assertNotEquals(relaxed, restless);
    }

    @Test
    void restlessTravelHopRateIsHigherThanRelaxedWithoutExceedingBounds() {
        double relaxed = hopProbability(profile("relaxed-v1"));
        double restless = hopProbability(profile("restless-v1"));

        assertTrue(restless > relaxed);
        assertTrue(relaxed >= 0.01d);
        assertTrue(restless <= 0.12d);
    }

    private static List<AgentPresentationDecision> decisions(
            AgentPersonalityProfile profile, long seed) {
        List<AgentPresentationDecision> decisions = new ArrayList<>();
        for (int sequence = 1; sequence <= 50; sequence++) {
            decisions.add(AgentPersonalityPresentationResolver.resolve(
                    profile, seed, sequence, AgentPresentationTrigger.COMBAT_IDLE, 1_000L));
        }
        return decisions;
    }

    private static double hopProbability(AgentPersonalityProfile profile) {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        entry.capabilityStates().require(AgentPersonalityState.STATE_KEY).assign(
                new AgentPersonalityAssignment(
                        1, 1, "Fixture", profile.profileId(), profile.profileVersion(), 55L, 0L),
                profile, true);
        return AgentPersonalityPresentationRuntime.travelHopProbability(entry, 0.04d);
    }

    private static AgentPersonalityProfile profile(String id) {
        return AgentPersonalityProfileRepository.defaultRepository().find(id).orElseThrow();
    }
}
