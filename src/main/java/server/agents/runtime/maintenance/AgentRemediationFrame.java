package server.agents.runtime.maintenance;

import java.util.Map;

/** Bounded remediation intent attached to one suspended foreground correlation. */
public record AgentRemediationFrame(
        String frameId,
        AgentRemediationKind kind,
        String maintenanceObjectiveId,
        String parentCorrelationId,
        int attempt,
        long startedAtMs,
        long deadlineAtMs,
        Map<String, String> requiredPostconditions) {

    public AgentRemediationFrame {
        if (frameId == null || frameId.isBlank()
                || kind == null
                || maintenanceObjectiveId == null || maintenanceObjectiveId.isBlank()
                || parentCorrelationId == null
                || attempt < 1
                || startedAtMs < 0
                || deadlineAtMs < startedAtMs) {
            throw new IllegalArgumentException("A valid bounded remediation frame is required");
        }
        frameId = frameId.trim();
        maintenanceObjectiveId = maintenanceObjectiveId.trim();
        parentCorrelationId = parentCorrelationId.trim();
        requiredPostconditions = requiredPostconditions == null
                ? Map.of() : Map.copyOf(requiredPostconditions);
    }
}
