package server.agents.runtime.activity.control.rollout;

import server.agents.runtime.activity.AgentActivityOwnershipReconciliation;
import server.agents.runtime.activity.control.facade.AgentLiveActivityFacade;
import server.agents.runtime.activity.world.AgentWorldDirective;
import server.agents.runtime.activity.world.AgentWorldDirectiveSource;
import server.agents.runtime.activity.world.AgentWorldDirectorMode;

/** Final fail-closed authority check before an automatic directive may be claimed. */
public final class AgentWorldDirectorAutonomousGate {
    private final AgentWorldDirectorAutonomousConfig config;

    public AgentWorldDirectorAutonomousGate(AgentWorldDirectorAutonomousConfig config) {
        if (config == null) throw new IllegalArgumentException("Autonomous config is required");
        this.config = config;
    }

    public AgentWorldDirectorRolloutGateResult inspect(
            AgentWorldDirectorMode mode,
            AgentWorldDirective directive,
            AgentLiveActivityFacade sourceFacade,
            AgentActivityOwnershipReconciliation ownership,
            long observeSamples,
            int recentFailures,
            int concurrentHandoffs) {
        if (!config.enabled()) return block("Autonomous rollout is disabled");
        if (mode != AgentWorldDirectorMode.AUTONOMOUS) {
            return block("Agent is not in Autonomous mode");
        }
        if (directive == null || sourceFacade == null || ownership == null) {
            return block("directive, source facade, and ownership evidence are required");
        }
        if (directive.source() != AgentWorldDirectiveSource.POLICY) {
            return block("Autonomous gate accepts only policy-produced directives");
        }
        if (!config.includes(directive.agentId())) {
            return block("Agent is outside the deterministic Autonomous cohort");
        }
        if (observeSamples < config.minimumObserveSamples()) {
            return block("Agent lacks the required Observe-mode evidence");
        }
        if (recentFailures > config.maximumRecentFailures()) {
            return block("recent Director failures exceed the rollout budget");
        }
        if (concurrentHandoffs < 0
                || concurrentHandoffs >= config.maximumConcurrentHandoffs()) {
            return block("Autonomous handoff capacity is exhausted");
        }
        if (!config.allowedTargetKinds().contains(directive.targetActivityKind())) {
            return block("target activity is outside the Autonomous rollout scope");
        }
        if (!ownership.permitsExecution()) {
            return block("restored activity ownership is not clean");
        }
        boolean switching = sourceFacade.kind() != directive.targetActivityKind();
        if (switching && config.requireRollbackForSwitch() && !sourceFacade.rollbackSupported()) {
            return block("source activity lacks exact-session rollback");
        }
        return AgentWorldDirectorRolloutGateResult.allow(
                "Autonomous cohort, evidence, failure, capacity, ownership, and rollback gates passed");
    }

    private static AgentWorldDirectorRolloutGateResult block(String reason) {
        return AgentWorldDirectorRolloutGateResult.block(reason);
    }
}
