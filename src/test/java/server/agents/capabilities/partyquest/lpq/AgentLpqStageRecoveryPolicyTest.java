package server.agents.capabilities.partyquest.lpq;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentLpqStageRecoveryPolicyTest {
    private static final String PROFILE = "submission=30000;missingPass=45000;portal=30000;"
            + "reactor=90000;traversal=45000;roomExitPlacement=15000;"
            + "rallyRetry=10000;rallyRecovery=15000";

    @Test
    void parsesOneCompleteIndependentlyOwnedStageProfile() {
        AgentLpqStageRecoveryPolicy policy = AgentLpqStageRecoveryPolicy.parse(5, PROFILE);

        assertEquals(5, policy.stage());
        assertEquals(30_000L, policy.submissionMs());
        assertEquals(45_000L, policy.missingPassMs());
        assertEquals(15_000L, policy.roomExitPlacementMs());
        assertEquals(10_000L, policy.rallyRetryMs());
        assertEquals(15_000L, policy.rallyRecoveryMs());
    }

    @Test
    void rejectsProfilesWhoseRecoveryWouldPrecedeTheirNaturalRetry() {
        assertThrows(IllegalArgumentException.class, () ->
                AgentLpqStageRecoveryPolicy.parse(4,
                        PROFILE.replace("rallyRetry=10000", "rallyRetry=15000")));
        assertThrows(IllegalArgumentException.class, () ->
                AgentLpqStageRecoveryPolicy.parse(5,
                        PROFILE.replace("roomExitPlacement=15000", "roomExitPlacement=30000")));
    }

    @Test
    void rejectsMissingDuplicateAndUnknownFields() {
        assertThrows(IllegalArgumentException.class, () ->
                AgentLpqStageRecoveryPolicy.parse(4,
                        PROFILE.replace(";reactor=90000", "")));
        assertThrows(IllegalArgumentException.class, () ->
                AgentLpqStageRecoveryPolicy.parse(4, PROFILE + ";portal=31000"));
        assertThrows(IllegalArgumentException.class, () ->
                AgentLpqStageRecoveryPolicy.parse(4, PROFILE + ";other=1"));
    }
}
