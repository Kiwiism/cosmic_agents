package server.agents.capabilities.reactor;

import server.agents.capabilities.AgentCapability;
import server.agents.capabilities.AgentCapabilityStatus;
import server.agents.capabilities.quest.AmherstScopeDecision;
import server.maps.Reactor;

import java.util.List;
import java.util.Optional;

public final class AgentReactorInteractionCapability implements AgentCapability {
    private final AgentReactorScopePolicy scopePolicy;
    private final AgentReactorTargetSelector targetSelector;
    private final AgentReactorExecutionPort executionPort;

    public AgentReactorInteractionCapability() {
        this(new AgentReactorScopePolicy(), new AgentReactorTargetSelector(), null);
    }

    public AgentReactorInteractionCapability(AgentReactorScopePolicy scopePolicy,
            AgentReactorTargetSelector targetSelector,
            AgentReactorExecutionPort executionPort) {
        this.scopePolicy = scopePolicy;
        this.targetSelector = targetSelector;
        this.executionPort = executionPort;
    }

    public AgentReactorInteractionResult plan(List<Reactor> reactors, AgentReactorInteractionRequest request) {
        AmherstScopeDecision scope = scopePolicy.check(request);
        if (!scope.allowed()) {
            return AgentReactorInteractionResult.blocked(scope.status(), scope.reason());
        }

        Optional<AgentReactorTarget> target = targetSelector.select(reactors, request);
        if (target.isEmpty()) {
            return AgentReactorInteractionResult.blocked(AgentCapabilityStatus.MISSING_REQUIREMENT,
                    "no matching active reactor found");
        }

        return AgentReactorInteractionResult.pending(AgentCapabilityStatus.NOT_READY,
                "reactor target selected; live execution is not wired yet",
                target.get());
    }

    public AgentReactorInteractionResult execute(List<Reactor> reactors, AgentReactorInteractionRequest request) {
        AgentReactorInteractionResult plan = plan(reactors, request);
        if (plan.target() == null || plan.status() != AgentCapabilityStatus.NOT_READY) {
            return plan;
        }
        if (executionPort == null) {
            return plan;
        }
        return executionPort.execute(request, plan.target());
    }
}
