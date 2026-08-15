package server.agents.capabilities.townlife;

import server.agents.integration.AgentPrimitiveCapabilityGatewayRuntime;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.List;

/** Explicit operator-test seam over the production encounter coordinator. */
public final class AgentTownLifeDirectedEncounterRuntime {
    private AgentTownLifeDirectedEncounterRuntime() {
    }

    public static boolean start(List<AgentRuntimeEntry> participants,
                                AgentTownLifeEncounterState.Type type,
                                String venueId,
                                long nowMs) {
        return AgentTownLifeEncounterCoordinator.beginDirected(
                participants, type, venueId,
                AgentPrimitiveCapabilityGatewayRuntime.gateway(), nowMs);
    }
}
