package server.agents.runtime.autonomy;

import server.agents.plans.AgentPlanExecutionStatus;

import java.util.List;

/**
 * Explainable snapshot-to-result chain for one universal-plan command.
 */
public record AgentAutonomyCycleRecord(
        long sequence,
        AgentAutonomySnapshot snapshot,
        String goalType,
        String planId,
        String planVersion,
        String stepId,
        String commandType,
        List<String> capabilityIds,
        String correlationId,
        AgentPlanExecutionStatus resultStatus,
        String resultReason,
        long completedAtMs) {

    public AgentAutonomyCycleRecord {
        if (sequence <= 0 || snapshot == null || goalType == null || goalType.isBlank()
                || planId == null || planId.isBlank()
                || planVersion == null || planVersion.isBlank()
                || stepId == null || stepId.isBlank()
                || commandType == null || commandType.isBlank()
                || correlationId == null || correlationId.isBlank()) {
            throw new IllegalArgumentException("A complete autonomy decision chain is required");
        }
        capabilityIds = List.copyOf(capabilityIds == null ? List.of() : capabilityIds);
        resultReason = resultReason == null ? "" : resultReason;
        if (resultStatus == null && completedAtMs != 0L) {
            throw new IllegalArgumentException("An incomplete cycle cannot have a completion time");
        }
        if (resultStatus != null && (resultStatus == AgentPlanExecutionStatus.IDLE
                || resultStatus == AgentPlanExecutionStatus.ACTIVE || completedAtMs < 0L)) {
            throw new IllegalArgumentException("A terminal result and valid completion time are required");
        }
    }

    public boolean complete() {
        return resultStatus != null;
    }

    AgentAutonomyCycleRecord complete(
            AgentPlanExecutionStatus status, String reason, long nowMs) {
        return new AgentAutonomyCycleRecord(
                sequence, snapshot, goalType, planId, planVersion, stepId,
                commandType, capabilityIds, correlationId, status, reason, nowMs);
    }
}
