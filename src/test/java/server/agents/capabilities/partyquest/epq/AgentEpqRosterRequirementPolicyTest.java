package server.agents.capabilities.partyquest.epq;

import client.Character;
import client.Job;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentEpqRosterRequirementPolicyTest {
    @Test
    void acceptsExactlyOneMemberFromEveryExplorerFamily() {
        var coverage = AgentEpqRosterRequirementPolicy.evaluate(List.of(
                member(Job.SPEARMAN), member(Job.CLERIC), member(Job.HUNTER),
                member(Job.ASSASSIN), member(Job.GUNSLINGER)));

        assertTrue(coverage.complete());
    }

    @Test
    void rejectsDuplicateAndMissingFamilies() {
        var coverage = AgentEpqRosterRequirementPolicy.evaluate(List.of(
                member(Job.SPEARMAN), member(Job.CLERIC), member(Job.HUNTER),
                member(Job.ASSASSIN), member(Job.BANDIT)));

        assertFalse(coverage.complete());
        assertTrue(coverage.missingRequirements().contains("pirate"));
        assertTrue(coverage.missingRequirements().contains("only one thief"));
    }

    private static Character member(Job job) {
        Character member = mock(Character.class);
        when(member.getJob()).thenReturn(job);
        return member;
    }
}
