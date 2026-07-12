package server.agents.capabilities.primitive;

import server.agents.capabilities.AgentCapabilityStatus;
import server.agents.capabilities.runtime.AgentCapabilityReasonCode;
import server.agents.capabilities.runtime.AgentCapabilityResult;
import server.agents.capabilities.runtime.AgentCapabilityStep;

final class AgentPrimitiveResults {
    private AgentPrimitiveResults() {
    }

    static AgentCapabilityStep missing(String message) {
        return AgentCapabilityStep.terminal(new AgentCapabilityResult(
                AgentCapabilityStatus.MISSING_REQUIREMENT,
                AgentCapabilityReasonCode.MISSING_REQUIREMENT,
                message));
    }

    static AgentCapabilityStep blocked(AgentCapabilityStatus status, String message) {
        return AgentCapabilityStep.terminal(new AgentCapabilityResult(
                status,
                AgentCapabilityReasonCode.BLOCKED_BY_SCOPE,
                message));
    }

    static AgentCapabilityStep mismatch(String message) {
        return AgentCapabilityStep.terminal(AgentCapabilityResult.failed(
                AgentCapabilityReasonCode.LIVE_STATE_MISMATCH,
                message));
    }
}
