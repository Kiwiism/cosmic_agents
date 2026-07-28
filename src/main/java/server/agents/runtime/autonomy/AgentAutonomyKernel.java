package server.agents.runtime.autonomy;

import server.agents.plans.AgentPlanDefinition;
import server.agents.plans.AgentPlanExecutionStatus;
import server.agents.plans.AgentPlanSessionState;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.decision.AgentDecisionProvenanceState;

import java.util.List;
import java.util.function.Supplier;

/**
 * Records the authoritative snapshot, goal, universal plan, capability command,
 * and terminal result for each universal-plan step.
 *
 * <p>This kernel is policy-neutral and performs no Cosmic mutation. Feature
 * runners may implement commands, but they do not own the top-level decision
 * record.</p>
 */
public final class AgentAutonomyKernel {
    private static final String SOURCE = "universal-plan-executor";
    private static final String VERSION = "autonomy-kernel-v1";

    private AgentAutonomyKernel() {
    }

    public static void recordGoalSelection(
            AgentRuntimeEntry entry,
            AgentAutonomySnapshot snapshot,
            AgentGoalSelection selection,
            String correlationId,
            long nowMs) {
        if (entry == null || snapshot == null || selection == null
                || correlationId == null || correlationId.isBlank()) {
            throw new IllegalArgumentException("Goal-selection evidence is required");
        }
        AgentDecisionProvenanceState decisions =
                entry.capabilityStates().require(AgentDecisionProvenanceState.STATE_KEY);
        if (!selection.selected()) {
            decisions.record(nowMs, "autonomy-goal", "NO_OP", SOURCE,
                    VERSION, selection.reason(), correlationId,
                    selection.rejections().stream()
                            .map(AgentGoalSelection.Rejection::proposalId).toList());
            return;
        }
        decisions.record(nowMs, "autonomy-goal", selection.accepted().goalType(),
                selection.accepted().source(), selection.accepted().policyVersion(),
                selection.reason(), correlationId,
                List.of(selection.accepted().proposalId()));
        decisions.record(nowMs, "autonomy-plan", selection.plan().planId(), SOURCE,
                VERSION, "goal resolved to versioned universal plan "
                        + selection.plan().planVersion(), correlationId,
                List.of(selection.plan().planId()));
    }

    public static String beginPlanStep(
            AgentRuntimeEntry entry,
            Supplier<AgentAutonomySnapshot> snapshotCapture,
            AgentPlanSessionState session,
            AgentPlanDefinition plan,
            AgentPlanDefinition.Step step,
            long nowMs) {
        if (entry == null || snapshotCapture == null
                || session == null || plan == null || step == null) {
            throw new IllegalArgumentException("Plan-step autonomy inputs are required");
        }
        AgentAutonomyCycleState state =
                entry.capabilityStates().require(AgentAutonomyCycleState.STATE_KEY);
        String correlationId = correlationId(session, plan, step);
        AgentAutonomyCycleRecord latest = state.latest();
        if (latest != null && !latest.complete()
                && latest.correlationId().equals(correlationId)) {
            return correlationId;
        }

        AgentAutonomySnapshot snapshot = snapshotCapture.get();
        if (snapshot == null) {
            throw new IllegalStateException("The autonomy snapshot capture returned no snapshot");
        }
        state.begin(snapshot, plan.objective().type(), plan.planId(), plan.planVersion(),
                step.stepId(), step.operation(), step.capabilityIds(), correlationId);

        AgentDecisionProvenanceState decisions =
                entry.capabilityStates().require(AgentDecisionProvenanceState.STATE_KEY);
        decisions.record(nowMs, "autonomy-command", step.operation(), SOURCE,
                VERSION, "plan step issued capability command", correlationId,
                step.capabilityIds());
        return correlationId;
    }

    public static boolean completePlanStep(
            AgentRuntimeEntry entry,
            AgentPlanSessionState session,
            AgentPlanDefinition plan,
            AgentPlanDefinition.Step step,
            AgentPlanExecutionStatus status,
            String reason,
            long nowMs) {
        if (status == null || status == AgentPlanExecutionStatus.IDLE
                || status == AgentPlanExecutionStatus.ACTIVE) {
            return false;
        }
        String correlationId = correlationId(session, plan, step);
        AgentAutonomyCycleRecord completed = entry.capabilityStates()
                .require(AgentAutonomyCycleState.STATE_KEY)
                .complete(correlationId, status, reason, nowMs);
        if (completed == null) {
            return false;
        }
        entry.capabilityStates().require(AgentDecisionProvenanceState.STATE_KEY).record(
                nowMs, "autonomy-result", status.name(), SOURCE, VERSION,
                reason, correlationId, List.of());
        return true;
    }

    private static String correlationId(
            AgentPlanSessionState session,
            AgentPlanDefinition plan,
            AgentPlanDefinition.Step step) {
        return session.chainId() + ':' + plan.planId() + ':'
                + step.stepId() + ':' + session.stepAttempt();
    }
}
