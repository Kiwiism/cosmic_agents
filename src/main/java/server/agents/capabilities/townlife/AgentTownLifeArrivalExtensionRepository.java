package server.agents.capabilities.townlife;

import client.Character;
import server.agents.integration.PrimitiveCapabilityGateway;
import server.agents.progression.AgentVictoriaRouteRuntime;
import server.agents.runtime.AgentRuntimeEntry;

final class AgentTownLifeArrivalExtensionRepository {
    private static final AgentTownLifeArrivalExtension LITH_HARBOR =
            new LithHarborTownLifeArrivalExtension();
    private static final AgentTownLifeArrivalExtension GENERIC = new AgentTownLifeArrivalExtension() {
        @Override
        public boolean tickTravel(AgentRuntimeEntry entry,
                                  Character agent,
                                  AgentTownLifeState state,
                                  long nowMs,
                                  PrimitiveCapabilityGateway gateway) {
            AgentVictoriaRouteRuntime.TravelOutcome outcome = AgentVictoriaRouteRuntime.travelStatus(
                    entry, agent, state.townMapId(), gateway, nowMs);
            if (outcome.status() == AgentVictoriaRouteRuntime.Status.ARRIVED) {
                state.transition(AgentTownLifeState.Stage.COMPLETE_ARRIVAL, nowMs);
            }
            return outcome.status() != AgentVictoriaRouteRuntime.Status.MOVING;
        }

        @Override
        public boolean tickArrival(AgentRuntimeEntry entry,
                                   Character agent,
                                   AgentTownLifeState state,
                                   long nowMs,
                                   PrimitiveCapabilityGateway gateway) {
            state.transition(AgentTownLifeState.Stage.SETTLING, nowMs);
            return true;
        }
    };

    private AgentTownLifeArrivalExtensionRepository() {
    }

    static AgentTownLifeArrivalExtension forTown(int mapId) {
        String extension = AgentTownLifeProfileRepository.defaultRepository()
                .require(mapId).extensions().arrival();
        return switch (extension) {
            case "generic" -> GENERIC;
            case "lith-harbor" -> LITH_HARBOR;
            default -> throw new IllegalArgumentException(
                    "unknown town-life arrival extension " + extension + " for map " + mapId);
        };
    }
}
