package server.agents.runtime.autonomy;

import org.junit.jupiter.api.Test;
import server.agents.model.AgentPosition;
import server.agents.model.AgentSnapshot;
import server.agents.perception.AgentPerceptionSnapshot;
import server.agents.plans.AgentPlanRepository;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentGoalSelectorTest {
    private static final long NOW = 10_000L;

    @Test
    void choosesHighestPriorityEligibleProposalDeterministically() {
        AgentPlanRepository repository = AgentPlanRepository.defaultRepository();
        AgentGoalProposal lower = proposal(
                "lower", "maple-island-full-mvp", 10, true, Long.MAX_VALUE);
        AgentGoalProposal higher = proposal(
                "higher", "maple-island-full-mvp", 20, true, Long.MAX_VALUE);

        AgentGoalSelection selection = AgentGoalSelector.select(
                snapshot(), List.of(lower, higher), repository, NOW);

        assertTrue(selection.selected());
        assertEquals("higher", selection.accepted().proposalId());
        assertEquals("maple-island-full-mvp", selection.plan().planId());
    }

    @Test
    void rejectsIneligibleExpiredAndUnmappedProposalsWithReasons() {
        AgentPlanRepository repository = AgentPlanRepository.defaultRepository();

        AgentGoalSelection selection = AgentGoalSelector.select(
                snapshot(),
                List.of(
                        proposal("ineligible", "maple-island-full-mvp",
                                30, false, Long.MAX_VALUE),
                        proposal("expired", "maple-island-full-mvp",
                                20, true, NOW - 1),
                        new AgentGoalProposal(
                                "missing", "missing.goal", "missing-plan",
                                "test", 10, true, Long.MAX_VALUE,
                                "test-v1", List.of())),
                repository,
                NOW);

        assertFalse(selection.selected());
        assertEquals(3, selection.rejections().size());
        assertTrue(selection.rejections().stream()
                .allMatch(rejection -> !rejection.reason().isBlank()));
    }

    @Test
    void usesStableIdentityTieBreakInsteadOfInputOrder() {
        AgentPlanRepository repository = AgentPlanRepository.defaultRepository();
        AgentGoalProposal alpha = proposal(
                "alpha", "maple-island-full-mvp", 10, true, Long.MAX_VALUE);
        AgentGoalProposal beta = proposal(
                "beta", "maple-island-full-mvp", 10, true, Long.MAX_VALUE);

        AgentGoalSelection first = AgentGoalSelector.select(
                snapshot(), List.of(beta, alpha), repository, NOW);
        AgentGoalSelection second = AgentGoalSelector.select(
                snapshot(), List.of(alpha, beta), repository, NOW);

        assertEquals("alpha", first.accepted().proposalId());
        assertEquals(first.accepted(), second.accepted());
        assertEquals(first.plan().planId(), second.plan().planId());
    }

    private static AgentGoalProposal proposal(
            String id,
            String planId,
            int priority,
            boolean eligible,
            long expiresAtMs) {
        var plan = AgentPlanRepository.defaultRepository().require(planId);
        return new AgentGoalProposal(
                id,
                plan.objective().type(),
                planId,
                "test",
                priority,
                eligible,
                expiresAtMs,
                "test-v1",
                List.of("snapshot"));
    }

    private static AgentAutonomySnapshot snapshot() {
        return new AgentAutonomySnapshot(
                1L,
                NOW,
                new AgentSnapshot(
                        1, "Agent", 1, 1, 0, new AgentPosition(0, 0), true),
                AgentPerceptionSnapshot.unavailable());
    }
}
