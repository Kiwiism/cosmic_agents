package server.agents.capabilities.townlife;

import client.Character;
import server.agents.runtime.AgentRuntimeEntry;

/**
 * Owns admission and the local-session boundary. It deliberately has no travel, quest, shop,
 * storage, or progression dependencies.
 */
public final class AgentTownLifeLifecycleRuntime {
    private AgentTownLifeLifecycleRuntime() {
    }

    public static AgentTownLifeSessionResult start(
            AgentRuntimeEntry entry,
            Character agent,
            AgentTownLifeVisitRequest request,
            AgentTownLifeAdmissionMode requestedMode,
            long nowMs,
            int identitySeed) {
        if (agent == null || request == null) {
            return result(AgentTownLifeSessionResult.Status.REJECTED_INVALID_REQUEST, 0,
                    "entry, agent, and request are required", null);
        }
        if (entry != null) {
            AgentTownLifeState state = entry.capabilityStates()
                    .find(AgentTownLifeState.STATE_KEY).orElse(null);
            if (state != null && state.enabled()) {
                return result(AgentTownLifeSessionResult.Status.ALREADY_ACTIVE,
                        state.townMapId(), "TownLife is already active",
                        state.sessionHandle(agent.getId()));
            }
        }
        return start(entry, agent,
                AgentTownLifeEntryRequest.legacy(agent.getId(), nowMs, request),
                requestedMode, nowMs, identitySeed);
    }

    public static AgentTownLifeSessionResult start(
            AgentRuntimeEntry entry,
            Character agent,
            AgentTownLifeEntryRequest entryRequest,
            AgentTownLifeAdmissionMode requestedMode,
            long nowMs,
            int identitySeed) {
        return start(entry, agent, entryRequest, requestedMode, nowMs, identitySeed, null);
    }

    static AgentTownLifeSessionResult restore(
            AgentRuntimeEntry entry,
            Character agent,
            AgentTownLifeEntryRequest entryRequest,
            long nowMs,
            int identitySeed,
            String restoredSessionId) {
        return start(entry, agent, entryRequest, AgentTownLifeAdmissionMode.MANUAL_ONLY,
                nowMs, identitySeed, restoredSessionId);
    }

    private static AgentTownLifeSessionResult start(
            AgentRuntimeEntry entry,
            Character agent,
            AgentTownLifeEntryRequest entryRequest,
            AgentTownLifeAdmissionMode requestedMode,
            long nowMs,
            int identitySeed,
            String restoredSessionId) {
        AgentTownLifeVisitRequest request = entryRequest == null ? null : entryRequest.visit();
        if (entry == null || agent == null || request == null) {
            return result(AgentTownLifeSessionResult.Status.REJECTED_INVALID_REQUEST, 0,
                    "entry, agent, and request are required", null);
        }
        if (!AgentTownLifeControlRuntime.enabled()) {
            return result(AgentTownLifeSessionResult.Status.REJECTED_DISABLED,
                    request.townMapId(), "TownLife is globally disabled", null);
        }
        AgentTownLifeProfile profile = AgentTownLifeProfileRepository.defaultRepository()
                .find(request.townMapId()).orElse(null);
        if (profile == null) {
            return result(AgentTownLifeSessionResult.Status.REJECTED_UNSUPPORTED_TOWN,
                    request.townMapId(), "town has no TownLife profile", null);
        }
        if (profile.admission().mode() == AgentTownLifeAdmissionMode.DISABLED) {
            return result(AgentTownLifeSessionResult.Status.REJECTED_DISABLED,
                    request.townMapId(), "TownLife is disabled for this town", null);
        }
        if (requestedMode == AgentTownLifeAdmissionMode.AMBIENT
                && profile.admission().mode() != AgentTownLifeAdmissionMode.AMBIENT) {
            return result(AgentTownLifeSessionResult.Status.REJECTED_DISABLED,
                    request.townMapId(), "ambient admission is disabled for this town", null);
        }
        if (agent.getMapId() != request.townMapId()) {
            return result(AgentTownLifeSessionResult.Status.REJECTED_NOT_LOCAL,
                    request.townMapId(), "travel must place the Agent in town before TownLife starts", null);
        }
        AgentTownLifeState state = entry.capabilityStates().require(AgentTownLifeState.STATE_KEY);
        if (state.enabled()) {
            AgentTownLifeSessionHandle activeHandle = state.sessionHandle(agent.getId());
            AgentTownLifeSessionResult.Status status = state.sameEntryRequest(entryRequest)
                    ? AgentTownLifeSessionResult.Status.ALREADY_ACTIVE_SAME_REQUEST
                    : AgentTownLifeSessionResult.Status.REJECTED_ALREADY_ACTIVE_OTHER_REQUEST;
            return result(status, state.townMapId(),
                    status == AgentTownLifeSessionResult.Status.ALREADY_ACTIVE_SAME_REQUEST
                            ? "TownLife request is already active"
                            : "another TownLife request owns the active session",
                    activeHandle);
        }
        if (requestedMode == AgentTownLifeAdmissionMode.AMBIENT
                && profile.admission().maxAmbientAgents() > 0
                && AgentTownLifePopulationRuntime.count(agent) >= profile.admission().maxAmbientAgents()) {
            return result(AgentTownLifeSessionResult.Status.REJECTED_CAPACITY,
                    request.townMapId(), "ambient population cap reached", null);
        }
        String sessionId = restoredSessionId == null || restoredSessionId.isBlank()
                ? sessionId(agent.getId(), entryRequest.requestId(), nowMs)
                : restoredSessionId.trim();
        AgentTownLifeRuntime.activateLocal(
                entry, agent, entryRequest, sessionId, nowMs, identitySeed);
        return result(AgentTownLifeSessionResult.Status.STARTED, request.townMapId(), "",
                state.sessionHandle(agent.getId()));
    }

    public static AgentTownLifeExitResult requestExit(
            AgentRuntimeEntry entry,
            Character agent,
            AgentTownLifeExitRequest request) {
        if (entry == null || agent == null || request == null) {
            return exitResult(AgentTownLifeExitResult.Status.REJECTED_INVALID_REQUEST, "", "invalid exit request");
        }
        AgentTownLifeState state = entry.capabilityStates().require(AgentTownLifeState.STATE_KEY);
        if (!state.enabled()) {
            return exitResult(AgentTownLifeExitResult.Status.NOT_ACTIVE, request.sessionId(), "TownLife is not active");
        }
        if (!state.sessionId().equals(request.sessionId())) {
            return exitResult(AgentTownLifeExitResult.Status.REJECTED_STALE_SESSION,
                    request.sessionId(), "exit request does not own the active session");
        }
        if (!state.callerId().equals(request.callerId())) {
            return exitResult(AgentTownLifeExitResult.Status.REJECTED_CALLER_MISMATCH,
                    request.sessionId(), "exit caller does not own the active session");
        }
        if (request.mode() == AgentTownLifeExitMode.FORCE_NOW) {
            AgentTownLifeRuntime.terminateLocal(
                    entry, agent, AgentTownLifeLifecycleEvent.Phase.FORCED,
                    request.reason(), request.requestedAtMs());
            return exitResult(AgentTownLifeExitResult.Status.FORCED, request.sessionId(), request.reason());
        }
        if (!state.requestExit(request)) {
            return exitResult(AgentTownLifeExitResult.Status.ALREADY_DRAINING,
                    request.sessionId(), state.exitReason());
        }
        AgentTownLifeEventPublisher.lifecycle(
                entry, agent, state, AgentTownLifeLifecycleEvent.Phase.EXIT_REQUESTED,
                request.reason(), request.requestedAtMs());
        AgentTownLifeCheckpointRuntime.persist(entry, agent, request.requestedAtMs());
        if (!state.hasCommittedActivity() && !AgentTownLifeEncounterCoordinator.active(entry)
                && !state.externalInteractionPaused()) {
            AgentTownLifeRuntime.terminateLocal(
                    entry, agent, AgentTownLifeLifecycleEvent.Phase.EXITED,
                    request.reason(), request.requestedAtMs());
            return exitResult(AgentTownLifeExitResult.Status.EXITED,
                    request.sessionId(), request.reason());
        }
        return exitResult(AgentTownLifeExitResult.Status.EXIT_REQUESTED,
                request.sessionId(), request.reason());
    }

    public static AgentTownLifeSessionResult stop(AgentRuntimeEntry entry,
                                                  Character agent,
                                                  String reason) {
        if (entry == null) {
            return result(AgentTownLifeSessionResult.Status.REJECTED_INVALID_REQUEST, 0,
                    "entry is required", null);
        }
        AgentTownLifeState state = entry.capabilityStates().require(AgentTownLifeState.STATE_KEY);
        if (!state.enabled()) {
            return result(AgentTownLifeSessionResult.Status.NOT_ACTIVE, state.townMapId(), reason);
        }
        int townMapId = state.townMapId();
        AgentTownLifeRuntime.terminateLocal(
                entry, agent, AgentTownLifeLifecycleEvent.Phase.FORCED,
                reason, System.currentTimeMillis());
        return result(AgentTownLifeSessionResult.Status.STOPPED, townMapId, reason);
    }

    private static AgentTownLifeSessionResult result(AgentTownLifeSessionResult.Status status,
                                                     int townMapId,
                                                     String reason) {
        return result(status, townMapId, reason, null);
    }

    private static AgentTownLifeSessionResult result(AgentTownLifeSessionResult.Status status,
                                                     int townMapId,
                                                     String reason,
                                                     AgentTownLifeSessionHandle handle) {
        return new AgentTownLifeSessionResult(status, townMapId, reason, handle);
    }

    private static AgentTownLifeExitResult exitResult(
            AgentTownLifeExitResult.Status status, String sessionId, String reason) {
        return new AgentTownLifeExitResult(status, sessionId, reason);
    }

    private static String sessionId(int agentId, String requestId, long nowMs) {
        return "townlife:" + agentId + ':' + Long.toUnsignedString(nowMs, 36)
                + ':' + Integer.toUnsignedString(requestId.hashCode(), 36);
    }
}
