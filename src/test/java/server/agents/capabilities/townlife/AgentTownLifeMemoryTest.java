package server.agents.capabilities.townlife;

import org.junit.jupiter.api.Test;
import server.agents.personality.AgentPersonalityProfile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentTownLifeMemoryTest {
    @Test
    void destinationCooldownSeparatesNormalAndFailedTargets() {
        AgentTownLifeMemory memory = new AgentTownLifeMemory();

        memory.remember(AgentTownLifeState.Activity.REST, "bench:1", 1_000L);

        assertTrue(memory.recentlyUsed(AgentTownLifeState.Activity.REST));
        assertFalse(memory.destinationAvailable("bench:1", 60_999L));
        assertTrue(memory.destinationAvailable("bench:1", 61_000L));

        memory.rememberFailure("bench:1", 70_000L);
        assertFalse(memory.destinationAvailable("bench:1", 189_999L));
        assertTrue(memory.destinationAvailable("bench:1", 190_000L));
    }

    @Test
    void socialMemorySurvivesVisitResetAndAppliesBoundedPeerCooldown() {
        AgentTownLifeMemory memory = new AgentTownLifeMemory();
        memory.rememberSocial(22, AgentTownLifeEncounterState.Type.SOCIAL_CHAT,
                "central-benches", AgentTownLifeMemory.SocialOutcome.COMPLETED, 1_000L);

        memory.clearVisit();

        assertEquals(1, memory.relationshipSummariesSnapshot().size());
        assertFalse(memory.peerAvailable(22, 50_000L));
        assertTrue(memory.peerAvailable(22, 100_000L));
    }

    @Test
    void routinePreferenceBiasesFamiliarPeersWhileNovelProfilesDiversify() {
        AgentTownLifeMemory memory = new AgentTownLifeMemory();
        memory.rememberSocial(22, AgentTownLifeEncounterState.Type.SOCIAL_CHAT,
                "central-benches", AgentTownLifeMemory.SocialOutcome.COMPLETED, 0L);
        AgentPersonalityProfile.Traits routine = traits(90);
        AgentPersonalityProfile.Traits novelty = traits(10);

        assertTrue(memory.peerPreferenceScore(22, routine, 200_000L)
                > memory.peerPreferenceScore(33, routine, 200_000L));
        assertTrue(memory.peerPreferenceScore(22, novelty, 200_000L)
                < memory.peerPreferenceScore(33, novelty, 200_000L));
    }

    @Test
    void relationshipHistoryIsStrictlyBounded() {
        AgentTownLifeMemory memory = new AgentTownLifeMemory();
        for (int peer = 1; peer <= 40; peer++) {
            memory.rememberSocial(peer, AgentTownLifeEncounterState.Type.SOCIAL_CHAT,
                    "venue", AgentTownLifeMemory.SocialOutcome.COMPLETED, peer);
        }

        assertEquals(24, memory.relationshipSummariesSnapshot().size());
        assertEquals(16, memory.recentSocialSnapshot().size());
    }

    private static AgentPersonalityProfile.Traits traits(int routinePreference) {
        return new AgentPersonalityProfile.Traits(
                50, 50, 50, 50, 50, 50, routinePreference);
    }
}
