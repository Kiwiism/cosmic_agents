package server.agents.plans;

import client.Character;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.List;
import java.io.IOException;

/** Session-facing facade for the one universal plan executor. */
public final class AgentUniversalPlanRuntime {
    private static final AgentPlanExecutor EXECUTOR = new AgentPlanExecutor(
            AgentPlanRepository.defaultRepository(),
            new AgentPlanStepExecutorRegistry(List.of(
                    new AgentOrderedObjectivePlanStepExecutor(),
                    new AgentSouthperryLithTransferStepExecutor(),
                    new AgentFirstJobPlanStepExecutor(),
                    new AgentVictoriaTrainingPlanStepExecutor())));

    private AgentUniversalPlanRuntime() {
    }

    public static boolean start(AgentRuntimeEntry entry,
                                Character agent,
                                String planId,
                                AgentPlanStartRequest request,
                                long nowMs) {
        return EXECUTOR.start(entry, agent, planId, request, nowMs);
    }

    public static boolean active(AgentRuntimeEntry entry) {
        return entry != null && entry.capabilityStates().find(AgentPlanSessionState.STATE_KEY)
                .map(AgentPlanSessionState::active).orElse(false);
    }

    public static boolean tick(AgentRuntimeEntry entry, Character agent, long nowMs) {
        return EXECUTOR.tick(entry, agent, nowMs);
    }

    public static boolean cancel(AgentRuntimeEntry entry,
                                 Character agent,
                                 String reason,
                                 long nowMs) {
        return EXECUTOR.cancel(entry, agent, reason, nowMs);
    }

    public static boolean reattach(AgentRuntimeEntry entry, Character agent, long nowMs) {
        return EXECUTOR.reattach(entry, agent, nowMs);
    }

    public static boolean startAvailableSuccessor(AgentRuntimeEntry entry,
                                                  Character agent,
                                                  String planId,
                                                  AgentPlanStartRequest request,
                                                  long nowMs) {
        return EXECUTOR.startAvailableSuccessor(entry, agent, planId, request, nowMs);
    }

    public static AgentPlanExecutionStatus status(AgentRuntimeEntry entry) {
        return entry == null ? AgentPlanExecutionStatus.IDLE
                : entry.capabilityStates().find(AgentPlanSessionState.STATE_KEY)
                .map(AgentPlanSessionState::status).orElse(AgentPlanExecutionStatus.IDLE);
    }

    public static List<String> availableSuccessors(AgentRuntimeEntry entry) {
        return entry == null ? List.of()
                : entry.capabilityStates().find(AgentPlanSessionState.STATE_KEY)
                .map(AgentPlanSessionState::availableSuccessorPlanIds).orElse(List.of());
    }

    public static void clearCheckpoint(AgentRuntimeEntry entry, int characterId) throws IOException {
        if (entry != null) {
            entry.capabilityStates().remove(AgentPlanSessionState.STATE_KEY);
        }
        if (characterId > 0) {
            AgentPlanCheckpointRuntime.delete(characterId);
        }
    }
}
