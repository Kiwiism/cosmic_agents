package server.agents.runtime.autonomy;

import server.agents.plans.AgentPlanDefinition;
import server.agents.plans.AgentPlanRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Pure deterministic goal arbitration and versioned-plan resolution. */
public final class AgentGoalSelector {
    private static final Comparator<AgentGoalProposal> ORDER =
            Comparator.comparingInt(AgentGoalProposal::priority).reversed()
                    .thenComparing(AgentGoalProposal::source)
                    .thenComparing(AgentGoalProposal::policyVersion)
                    .thenComparing(AgentGoalProposal::proposalId);

    private AgentGoalSelector() {
    }

    public static AgentGoalSelection select(
            AgentAutonomySnapshot snapshot,
            List<AgentGoalProposal> proposals,
            AgentPlanRepository repository,
            long nowMs) {
        if (snapshot == null || proposals == null || repository == null || nowMs < 0L) {
            throw new IllegalArgumentException(
                    "Snapshot, proposals, plan repository and selection time are required");
        }
        List<AgentGoalSelection.Rejection> rejections = new ArrayList<>();
        List<AgentGoalProposal> ordered = proposals.stream()
                .sorted(ORDER)
                .toList();
        for (AgentGoalProposal proposal : ordered) {
            if (!proposal.eligible()) {
                rejections.add(new AgentGoalSelection.Rejection(
                        proposal.proposalId(), "proposal is not eligible"));
                continue;
            }
            if (nowMs > proposal.expiresAtMs()) {
                rejections.add(new AgentGoalSelection.Rejection(
                        proposal.proposalId(), "proposal expired"));
                continue;
            }
            AgentPlanDefinition plan = resolvePlan(proposal, repository);
            if (plan == null) {
                rejections.add(new AgentGoalSelection.Rejection(
                        proposal.proposalId(), "no versioned plan matches the proposal"));
                continue;
            }
            return new AgentGoalSelection(
                    proposal, plan, rejections,
                    "selected from snapshot " + snapshot.sequence());
        }
        return new AgentGoalSelection(
                null, null, rejections,
                ordered.isEmpty() ? "no goal proposals" : "all goal proposals were rejected");
    }

    private static AgentPlanDefinition resolvePlan(
            AgentGoalProposal proposal,
            AgentPlanRepository repository) {
        if (!proposal.requestedPlanId().isBlank()) {
            AgentPlanDefinition requested =
                    repository.find(proposal.requestedPlanId()).orElse(null);
            return requested != null
                    && requested.objective().type().equals(proposal.goalType())
                    ? requested : null;
        }
        return repository.all().stream()
                .filter(plan -> plan.objective().type().equals(proposal.goalType()))
                .sorted(Comparator.comparing(AgentPlanDefinition::planId)
                        .thenComparing(AgentPlanDefinition::planVersion))
                .findFirst()
                .orElse(null);
    }
}
