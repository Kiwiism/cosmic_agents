package server.agents.runtime.activity.control.binding;

import client.Character;
import server.agents.integration.AgentPrimitiveCapabilityGatewayRuntime;
import server.agents.progression.AgentVictoriaRouteRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.activity.session.AgentActivityTransferPort;

/** Uses the ordinary Victoria portal-route engine; it never relocates an Agent directly. */
public final class AgentVictoriaWorldMapTransfer implements AgentWorldMapTransfer {
    @Override
    public AgentActivityTransferPort.Result travel(
            AgentRuntimeEntry entry, Character agent, int destinationMapId, long nowMs) {
        if (entry == null || agent == null || destinationMapId <= 0) {
            return AgentActivityTransferPort.Result.failed("valid normal-world travel is required");
        }
        if (agent.getMapId() == destinationMapId) {
            return AgentActivityTransferPort.Result.ready();
        }
        AgentVictoriaRouteRuntime.TravelOutcome outcome = AgentVictoriaRouteRuntime.travelStatus(
                entry, agent, destinationMapId,
                AgentPrimitiveCapabilityGatewayRuntime.gateway(), nowMs);
        return switch (outcome.status()) {
            case ARRIVED -> AgentActivityTransferPort.Result.ready();
            case MOVING, PORTAL_UNAVAILABLE -> AgentActivityTransferPort.Result.pending(
                    "traveling normally to map " + destinationMapId, nowMs + 500L);
            case NO_ROUTE -> AgentActivityTransferPort.Result.failed(
                    "no normal portal route reaches map " + destinationMapId);
        };
    }
}
