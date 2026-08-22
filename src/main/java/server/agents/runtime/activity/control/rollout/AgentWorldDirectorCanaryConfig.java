package server.agents.runtime.activity.control.rollout;

import server.agents.runtime.activity.session.AgentActivityKind;

import java.util.Set;

/** Explicit Assisted-mode cohort. Empty/default configuration cannot execute. */
public record AgentWorldDirectorCanaryConfig(
        boolean assistedEnabled,
        Set<Integer> allowedAgentIds,
        int maximumConcurrentHandoffs,
        Set<AgentActivityKind> allowedTargetKinds,
        boolean requireRollbackForSwitch) {

    public AgentWorldDirectorCanaryConfig {
        allowedAgentIds = Set.copyOf(allowedAgentIds == null ? Set.of() : allowedAgentIds);
        allowedTargetKinds = Set.copyOf(
                allowedTargetKinds == null ? Set.of() : allowedTargetKinds);
        if (maximumConcurrentHandoffs < 0 || allowedAgentIds.stream().anyMatch(id -> id <= 0)
                || allowedTargetKinds.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("valid Assisted canary limits are required");
        }
        if (assistedEnabled && (allowedAgentIds.isEmpty()
                || maximumConcurrentHandoffs == 0 || allowedTargetKinds.isEmpty())) {
            throw new IllegalArgumentException(
                    "enabled Assisted canary requires a cohort, capacity, and target kinds");
        }
    }

    public static AgentWorldDirectorCanaryConfig disabled() {
        return new AgentWorldDirectorCanaryConfig(false, Set.of(), 0, Set.of(), true);
    }
}
