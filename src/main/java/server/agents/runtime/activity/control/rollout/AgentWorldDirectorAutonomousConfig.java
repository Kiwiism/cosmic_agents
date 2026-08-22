package server.agents.runtime.activity.control.rollout;

import server.agents.runtime.activity.session.AgentActivityKind;

import java.util.Set;

/** Staged Autonomous rollout. Basis points make cohort assignment deterministic and reviewable. */
public record AgentWorldDirectorAutonomousConfig(
        boolean enabled,
        int rolloutBasisPoints,
        Set<Integer> explicitAgentIds,
        int maximumConcurrentHandoffs,
        long minimumObserveSamples,
        int maximumRecentFailures,
        Set<AgentActivityKind> allowedTargetKinds,
        boolean requireRollbackForSwitch) {

    public AgentWorldDirectorAutonomousConfig {
        explicitAgentIds = Set.copyOf(explicitAgentIds == null ? Set.of() : explicitAgentIds);
        allowedTargetKinds = Set.copyOf(
                allowedTargetKinds == null ? Set.of() : allowedTargetKinds);
        if (rolloutBasisPoints < 0 || rolloutBasisPoints > 10_000
                || maximumConcurrentHandoffs < 0 || minimumObserveSamples < 0L
                || maximumRecentFailures < 0
                || explicitAgentIds.stream().anyMatch(id -> id <= 0)) {
            throw new IllegalArgumentException("valid Autonomous rollout limits are required");
        }
        if (enabled && rolloutBasisPoints == 0 && explicitAgentIds.isEmpty()) {
            throw new IllegalArgumentException("enabled Autonomous rollout requires a cohort");
        }
        if (enabled && (maximumConcurrentHandoffs == 0 || allowedTargetKinds.isEmpty())) {
            throw new IllegalArgumentException(
                    "enabled Autonomous rollout requires capacity and target kinds");
        }
    }

    public static AgentWorldDirectorAutonomousConfig disabled() {
        return new AgentWorldDirectorAutonomousConfig(
                false, 0, Set.of(), 0, Long.MAX_VALUE, 0, Set.of(), true);
    }

    public boolean includes(int agentId) {
        if (explicitAgentIds.contains(agentId)) return true;
        return rolloutBasisPoints > 0 && Math.floorMod(Integer.hashCode(agentId), 10_000)
                < rolloutBasisPoints;
    }
}
