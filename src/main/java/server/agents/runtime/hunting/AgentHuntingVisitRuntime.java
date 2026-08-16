package server.agents.runtime.hunting;

import client.Character;
import server.agents.integration.PrimitiveCapabilityGateway;
import server.agents.runtime.AgentRuntimeEntry;

/** Hunting-owned execution boundary for bounded child combat requested by Questing. */
public final class AgentHuntingVisitRuntime {
    private AgentHuntingVisitRuntime() {
    }

    public static void engage(
            AgentRuntimeEntry entry,
            Character agent,
            PrimitiveCapabilityGateway gateway,
            AgentHuntingVisitRequest request,
            long nowMs) {
        if (entry == null || agent == null || gateway == null || request == null) {
            throw new IllegalArgumentException("Hunting visit execution requires runtime context");
        }
        if (agent.getMapId() != request.mapId()) {
            throw new IllegalStateException("Hunting visit map does not match the Agent map");
        }
        entry.capabilityStates().require(AgentHuntingVisitState.STATE_KEY)
                .record(request, nowMs);
        if (request.incidentalMobIds().isEmpty()) {
            gateway.grind(entry, request.preferredMobIds());
        } else {
            gateway.grind(entry, request.preferredMobIds(), request.incidentalMobIds());
        }
    }
}
