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
        if (entry == null || agent == null || request == null) {
            return result(AgentTownLifeSessionResult.Status.REJECTED_INVALID_REQUEST, 0,
                    "entry, agent, and request are required");
        }
        if (!AgentTownLifeControlRuntime.enabled()) {
            return result(AgentTownLifeSessionResult.Status.REJECTED_DISABLED,
                    request.townMapId(), "TownLife is globally disabled");
        }
        AgentTownLifeProfile profile = AgentTownLifeProfileRepository.defaultRepository()
                .find(request.townMapId()).orElse(null);
        if (profile == null) {
            return result(AgentTownLifeSessionResult.Status.REJECTED_UNSUPPORTED_TOWN,
                    request.townMapId(), "town has no TownLife profile");
        }
        if (profile.admission().mode() == AgentTownLifeAdmissionMode.DISABLED) {
            return result(AgentTownLifeSessionResult.Status.REJECTED_DISABLED,
                    request.townMapId(), "TownLife is disabled for this town");
        }
        if (requestedMode == AgentTownLifeAdmissionMode.AMBIENT
                && profile.admission().mode() != AgentTownLifeAdmissionMode.AMBIENT) {
            return result(AgentTownLifeSessionResult.Status.REJECTED_DISABLED,
                    request.townMapId(), "ambient admission is disabled for this town");
        }
        if (agent.getMapId() != request.townMapId()) {
            return result(AgentTownLifeSessionResult.Status.REJECTED_NOT_LOCAL,
                    request.townMapId(), "travel must place the Agent in town before TownLife starts");
        }
        AgentTownLifeState state = entry.capabilityStates().require(AgentTownLifeState.STATE_KEY);
        if (state.enabled()) {
            return result(AgentTownLifeSessionResult.Status.ALREADY_ACTIVE,
                    state.townMapId(), "TownLife session is already active");
        }
        if (requestedMode == AgentTownLifeAdmissionMode.AMBIENT
                && profile.admission().maxAmbientAgents() > 0
                && AgentTownLifePopulationRuntime.count(agent) >= profile.admission().maxAmbientAgents()) {
            return result(AgentTownLifeSessionResult.Status.REJECTED_CAPACITY,
                    request.townMapId(), "ambient population cap reached");
        }
        AgentTownLifeRuntime.activateLocal(entry, agent, request, nowMs, identitySeed);
        return result(AgentTownLifeSessionResult.Status.STARTED, request.townMapId(), "");
    }

    public static AgentTownLifeSessionResult stop(AgentRuntimeEntry entry,
                                                  Character agent,
                                                  String reason) {
        if (entry == null) {
            return result(AgentTownLifeSessionResult.Status.REJECTED_INVALID_REQUEST, 0,
                    "entry is required");
        }
        AgentTownLifeState state = entry.capabilityStates().require(AgentTownLifeState.STATE_KEY);
        if (!state.enabled()) {
            return result(AgentTownLifeSessionResult.Status.NOT_ACTIVE, state.townMapId(), reason);
        }
        int townMapId = state.townMapId();
        AgentTownLifeRuntime.terminateLocal(entry, agent);
        return result(AgentTownLifeSessionResult.Status.STOPPED, townMapId, reason);
    }

    private static AgentTownLifeSessionResult result(AgentTownLifeSessionResult.Status status,
                                                     int townMapId,
                                                     String reason) {
        return new AgentTownLifeSessionResult(status, townMapId, reason);
    }
}
