package server.agents.runtime.autonomy;

import java.util.List;

/** Immutable policy proposal submitted to the deterministic autonomy selector. */
public record AgentGoalProposal(
        String proposalId,
        String goalType,
        String requestedPlanId,
        String source,
        int priority,
        boolean eligible,
        long expiresAtMs,
        String policyVersion,
        List<String> evidenceReferences) {

    public AgentGoalProposal {
        if (proposalId == null || proposalId.isBlank()
                || goalType == null || goalType.isBlank()
                || source == null || source.isBlank()
                || policyVersion == null || policyVersion.isBlank()
                || expiresAtMs < 0L) {
            throw new IllegalArgumentException("A complete immutable goal proposal is required");
        }
        requestedPlanId = requestedPlanId == null ? "" : requestedPlanId;
        evidenceReferences = List.copyOf(
                evidenceReferences == null ? List.of() : evidenceReferences);
    }

    public boolean activeAt(long nowMs) {
        return eligible && nowMs <= expiresAtMs;
    }

    public static AgentGoalProposal explicitPlan(
            String planId,
            String goalType,
            int priority,
            String source,
            String policyVersion,
            long nowMs,
            List<String> evidenceReferences) {
        return new AgentGoalProposal(
                "plan-request:" + planId,
                goalType,
                planId,
                source,
                priority,
                true,
                Long.MAX_VALUE,
                policyVersion,
                evidenceReferences);
    }
}
