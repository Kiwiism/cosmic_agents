package server.agents.capabilities.combat;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentQuestLocalClearTargetPolicyTest {
    private static final Set<Integer> REQUIRED = Set.of(1);
    private static final Set<Integer> LOCAL = Set.of(1, 2);

    @Test
    void requiredMobOnCurrentPlatformWins() {
        AgentQuestLocalClearTargetPolicy.Selection<Integer> selection = select(true);

        assertEquals(List.of(1), selection.candidates());
        assertEquals(AgentCombatCandidateClass.REQUIRED, selection.candidateClass());
        assertEquals(AgentCombatDecisionReason.REQUIRED_LOCAL, selection.reason());
    }

    @Test
    void localIncidentalMobWinsBeforeRemoteRequiredMob() {
        AgentQuestLocalClearTargetPolicy.Selection<Integer> selection =
                AgentQuestLocalClearTargetPolicy.select(
                        List.of(2, 3, 1),
                        REQUIRED::contains,
                        Set.of(2, 3)::contains,
                        true);

        assertEquals(List.of(2, 3), selection.candidates());
        assertEquals(AgentCombatCandidateClass.INCIDENTAL, selection.candidateClass());
        assertEquals(AgentCombatDecisionReason.INCIDENTAL_PLATFORM_SWEEP, selection.reason());
    }

    @Test
    void sweepBudgetExhaustionForcesRequiredDebt() {
        AgentQuestLocalClearTargetPolicy.Selection<Integer> selection =
                AgentQuestLocalClearTargetPolicy.select(
                        List.of(2, 3, 1),
                        REQUIRED::contains,
                        Set.of(2, 3)::contains,
                        false);

        assertEquals(List.of(1), selection.candidates());
        assertEquals(AgentCombatDecisionReason.REQUIRED_DEBT, selection.reason());
    }

    @Test
    void incidentalTargetsRemainUsableWhenNoRequiredSpawnExists() {
        AgentQuestLocalClearTargetPolicy.Selection<Integer> selection =
                AgentQuestLocalClearTargetPolicy.select(
                        List.of(2, 3),
                        REQUIRED::contains,
                        value -> false,
                        false);

        assertEquals(List.of(2, 3), selection.candidates());
        assertEquals(AgentCombatDecisionReason.INCIDENTAL_NO_REQUIRED_AVAILABLE,
                selection.reason());
    }

    private static AgentQuestLocalClearTargetPolicy.Selection<Integer> select(
            boolean allowSweep) {
        return AgentQuestLocalClearTargetPolicy.select(
                List.of(3, 2, 1), REQUIRED::contains, LOCAL::contains, allowSweep);
    }
}
