package server.agents.runtime.activity.control.rollout;

import server.agents.runtime.activity.AgentActivityOwnershipReconciliation;
import server.agents.runtime.activity.control.facade.AgentLiveActivityFacade;
import server.agents.runtime.activity.world.AgentWorldDirective;
import server.agents.runtime.activity.world.AgentWorldDirectorMode;

/** Fail-closed Assisted-mode admission; it does not claim or execute a directive. */
public final class AgentWorldDirectorAssistedGate {
    private final AgentWorldDirectorCanaryConfig config;

    public AgentWorldDirectorAssistedGate(AgentWorldDirectorCanaryConfig config) {
        if (config == null) throw new IllegalArgumentException("canary config is required");
        this.config = config;
    }

    public AgentWorldDirectorRolloutGateResult inspect(
            AgentWorldDirectorMode mode,
            AgentWorldDirective directive,
            AgentLiveActivityFacade sourceFacade,
            AgentActivityOwnershipReconciliation ownership,
            int concurrentHandoffs) {
        if (!config.assistedEnabled()) return block("Assisted rollout is disabled");
        if (mode != AgentWorldDirectorMode.ASSISTED) return block("Agent is not in Assisted mode");
        if (directive == null || sourceFacade == null || ownership == null) {
            return block("directive, source facade, and ownership evidence are required");
        }
        if (!config.allowedAgentIds().contains(directive.agentId())) {
            return block("Agent is outside the Assisted canary cohort");
        }
        if (concurrentHandoffs < 0
                || concurrentHandoffs >= config.maximumConcurrentHandoffs()) {
            return block("Assisted handoff capacity is exhausted");
        }
        if (!config.allowedTargetKinds().contains(directive.targetActivityKind())) {
            return block("target activity is outside the Assisted canary scope");
        }
        if (!ownership.permitsExecution()) {
            return block("restored activity ownership is not clean");
        }
        boolean switching = sourceFacade.kind() != directive.targetActivityKind();
        if (switching && config.requireRollbackForSwitch() && !sourceFacade.rollbackSupported()) {
            return block("source activity lacks exact-session rollback");
        }
        return AgentWorldDirectorRolloutGateResult.allow(
                "Assisted canary cohort, capacity, ownership, and rollback gates passed");
    }

    private static AgentWorldDirectorRolloutGateResult block(String reason) {
        return AgentWorldDirectorRolloutGateResult.block(reason);
    }
}
