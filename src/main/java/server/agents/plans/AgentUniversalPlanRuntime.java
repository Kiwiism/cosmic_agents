package server.agents.plans;

import client.Character;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.plans.amherst.AgentAmherstPlanRuntime;

import java.io.IOException;
import java.util.List;

/** Session-facing facade for the one universal plan executor. */
public final class AgentUniversalPlanRuntime {
    private static final long DEFAULT_GRACEFUL_EXIT_TIMEOUT_MS = config.AgentTuning.longValue(
            "server.agents.plans.AgentUniversalPlanRuntime.DEFAULT_GRACEFUL_EXIT_TIMEOUT_MS");
    private static final AgentPlanExecutor EXECUTOR = new AgentPlanExecutor(
            AgentPlanRepository.defaultRepository(),
            AgentPlanStepExecutorRegistry.defaultRegistry());

    private AgentUniversalPlanRuntime() {
    }

    public static boolean start(AgentRuntimeEntry entry,
                                Character agent,
                                String planId,
                                AgentPlanStartRequest request,
                                long nowMs) {
        return EXECUTOR.start(entry, agent, planId, request, nowMs);
    }

    public static AgentPlanSessionResult requestSession(
            AgentRuntimeEntry entry,
            Character agent,
            AgentPlanEntryRequest request,
            long nowMs) {
        if (entry == null || agent == null || request == null || nowMs < 0L) {
            return new AgentPlanSessionResult(
                    AgentPlanSessionResult.Status.REJECTED_INVALID_REQUEST, null,
                    "entry, Agent, request, and valid timing are required");
        }
        AgentPlanSessionState state = entry.capabilityStates()
                .require(AgentPlanSessionState.STATE_KEY);
        if (state.active()) {
            AgentPlanSessionHandle current = state.sessionHandle();
            if (current != null && current.requestId().equals(request.requestId())
                    && current.callerId().equals(request.callerId())) {
                return new AgentPlanSessionResult(
                        AgentPlanSessionResult.Status.ALREADY_ACTIVE_SAME_REQUEST,
                        current, "plan request is already active");
            }
            return new AgentPlanSessionResult(
                    AgentPlanSessionResult.Status.REJECTED_ALREADY_ACTIVE, null,
                    "another caller owns the active plan");
        }
        final boolean started;
        try {
            started = EXECUTOR.start(
                    entry, agent, request.planId(), request.startRequest(), nowMs);
        } catch (RuntimeException invalidPlan) {
            return new AgentPlanSessionResult(
                    AgentPlanSessionResult.Status.REJECTED_PLAN, null,
                    invalidPlan.getMessage());
        }
        if (!started) {
            boolean planRejected = state.status() != AgentPlanExecutionStatus.IDLE
                    && !state.reason().isBlank();
            return new AgentPlanSessionResult(
                    planRejected ? AgentPlanSessionResult.Status.REJECTED_PLAN
                            : AgentPlanSessionResult.Status.REJECTED_FOREGROUND_BUSY,
                    null, planRejected ? state.reason()
                    : "plan selection or foreground admission was rejected");
        }
        AgentPlanSessionHandle handle = new AgentPlanSessionHandle(
                "plan:" + agent.getId() + ':' + nowMs + ':' + request.requestId(),
                request.requestId(), request.callerId(), agent.getId(), request.planId(), nowMs);
        state.own(handle);
        AgentPlanCheckpointRuntime.persistIfDirty(entry, nowMs);
        return new AgentPlanSessionResult(AgentPlanSessionResult.Status.STARTED, handle, "");
    }

    public static AgentPlanExitResult requestExit(
            AgentRuntimeEntry entry,
            Character agent,
            AgentPlanExitRequest request) {
        if (entry == null || agent == null || request == null) {
            return new AgentPlanExitResult(
                    AgentPlanExitResult.Status.REJECTED_INVALID_REQUEST,
                    "entry, Agent, and request are required");
        }
        AgentPlanSessionState state = entry.capabilityStates()
                .require(AgentPlanSessionState.STATE_KEY);
        AgentPlanSessionHandle handle = state.sessionHandle();
        if (!state.active() || handle == null) {
            return new AgentPlanExitResult(
                    AgentPlanExitResult.Status.NOT_ACTIVE, "plan session is not active");
        }
        if (!handle.sessionId().equals(request.sessionId())
                || !handle.callerId().equals(request.callerId())) {
            return new AgentPlanExitResult(
                    AgentPlanExitResult.Status.REJECTED_NOT_OWNER,
                    "plan exit request does not own this session");
        }
        if (request.mode() == AgentPlanExitMode.FORCE_NOW) {
            EXECUTOR.cancel(entry, agent, request.reason(), request.requestedAtMs());
            return new AgentPlanExitResult(AgentPlanExitResult.Status.EXITED, request.reason());
        }
        state.requestExit(request);
        if (request.mode() == AgentPlanExitMode.SUSPEND_AFTER_STEP && state.atStepBoundary()) {
            state.suspendAtBoundary();
            AgentPlanCheckpointRuntime.persistIfDirty(entry, request.requestedAtMs());
            return new AgentPlanExitResult(
                    AgentPlanExitResult.Status.SUSPENDED, request.reason());
        }
        if (request.mode() == AgentPlanExitMode.EXIT_AFTER_STEP && state.atStepBoundary()) {
            EXECUTOR.cancel(entry, agent, request.reason(), request.requestedAtMs());
            return new AgentPlanExitResult(
                    AgentPlanExitResult.Status.EXITED, request.reason());
        }
        AgentPlanCheckpointRuntime.persistIfDirty(entry, request.requestedAtMs());
        return new AgentPlanExitResult(AgentPlanExitResult.Status.REQUESTED, request.reason());
    }

    public static boolean resumeSession(
            AgentRuntimeEntry entry,
            AgentPlanSessionHandle handle,
            long nowMs) {
        if (entry == null || handle == null) return false;
        AgentPlanSessionState state = entry.capabilityStates()
                .require(AgentPlanSessionState.STATE_KEY);
        AgentPlanSessionHandle current = state.sessionHandle();
        if (!state.active() || !state.suspended() || current == null
                || !current.sessionId().equals(handle.sessionId())
                || !current.callerId().equals(handle.callerId())) {
            return false;
        }
        state.resume();
        AgentPlanCheckpointRuntime.persistIfDirty(entry, nowMs);
        return true;
    }

    public static AgentPlanSessionPhase phase(AgentRuntimeEntry entry) {
        return entry == null ? AgentPlanSessionPhase.IDLE
                : entry.capabilityStates().find(AgentPlanSessionState.STATE_KEY)
                .map(AgentPlanSessionState::phase).orElse(AgentPlanSessionPhase.IDLE);
    }

    public static AgentPlanSessionHandle sessionHandle(AgentRuntimeEntry entry) {
        return entry == null ? null : entry.capabilityStates()
                .find(AgentPlanSessionState.STATE_KEY)
                .map(AgentPlanSessionState::sessionHandle).orElse(null);
    }

    public static AgentPlanOutcome outcome(AgentRuntimeEntry entry) {
        return entry == null ? null : entry.capabilityStates()
                .find(AgentPlanSessionState.STATE_KEY)
                .map(AgentPlanSessionState::lastOutcome).orElse(null);
    }

    public static boolean active(AgentRuntimeEntry entry) {
        return entry != null && entry.capabilityStates().find(AgentPlanSessionState.STATE_KEY)
                .map(AgentPlanSessionState::active).orElse(false);
    }

    /**
     * Foreground compatibility boundary. Old Amherst checkpoints remain executable, but no
     * longer register a second competing foreground activity.
     */
    public static boolean foregroundActive(AgentRuntimeEntry entry) {
        return active(entry) || AgentAmherstPlanRuntime.active(entry);
    }

    public static boolean foregroundTick(
            AgentRuntimeEntry entry, Character agent, long nowMs) {
        if (active(entry)) {
            return tick(entry, agent, nowMs);
        }
        return AgentAmherstPlanRuntime.active(entry)
                && AgentAmherstPlanRuntime.tickGate(entry, agent, nowMs);
    }

    public static boolean foregroundCancel(
            AgentRuntimeEntry entry, Character agent, String reason, long nowMs) {
        boolean cancelled = cancel(entry, agent, reason, nowMs);
        if (AgentAmherstPlanRuntime.active(entry)) {
            AgentAmherstPlanRuntime.cancel(entry);
            cancelled = true;
        }
        return cancelled;
    }

    public static boolean requestGracefulStop(
            AgentRuntimeEntry entry, Character agent, String reason, long nowMs) {
        AgentPlanSessionHandle handle = sessionHandle(entry);
        if (!active(entry)) {
            if (AgentAmherstPlanRuntime.active(entry)) {
                AgentAmherstPlanRuntime.cancel(entry);
            }
            return true;
        }
        if (handle == null) return false;
        requestExit(entry, agent, AgentPlanExitRequest.graceful(
                handle, reason, nowMs, nowMs + DEFAULT_GRACEFUL_EXIT_TIMEOUT_MS));
        return !active(entry);
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

    public static boolean deferSuccessor(AgentRuntimeEntry entry, String planId) {
        if (entry == null || planId == null || planId.isBlank()) {
            return false;
        }
        AgentPlanRepository.defaultRepository().require(planId);
        entry.capabilityStates().require(AgentPlanSessionState.STATE_KEY).deferSuccessor(planId);
        return true;
    }

    public static void clearDeferredSuccessor(
            AgentRuntimeEntry entry, String planId, long nowMs) {
        if (entry == null) {
            return;
        }
        entry.capabilityStates().require(AgentPlanSessionState.STATE_KEY)
                .clearDeferredSuccessor(planId);
        AgentPlanCheckpointRuntime.persistIfDirty(entry, nowMs);
    }

    public static void clearCheckpoint(AgentRuntimeEntry entry, int characterId) throws IOException {
        if (entry != null) {
            entry.capabilityStates().remove(AgentPlanSessionState.STATE_KEY);
            entry.capabilityStates().remove(AgentPlanAttachmentState.STATE_KEY);
        }
        if (characterId > 0) {
            AgentPlanCheckpointRuntime.delete(characterId);
        }
    }
}
