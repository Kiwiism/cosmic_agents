package server.agents.capabilities.townlife;

import client.Character;
import server.agents.integration.AgentMapGatewayRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.simulation.AgentSimulationMode;

final class AgentTownLifeFidelityPolicy {
    private AgentTownLifeFidelityPolicy() {
    }

    static AgentTownLifeFidelity resolve(AgentRuntimeEntry entry, Character agent) {
        if (agent == null || agent.getMap() == null) {
            return AgentTownLifeFidelity.PRESENTATION;
        }
        if (AgentMapGatewayRuntime.map().isObservedByPlayer(agent.getMap())) {
            return AgentTownLifeFidelity.PRESENTATION;
        }
        AgentSimulationMode mode = entry == null
                ? AgentSimulationMode.PRESENTATION : entry.simulationState().mode();
        return mode == AgentSimulationMode.BACKGROUND_ABSTRACT
                ? AgentTownLifeFidelity.BACKGROUND_ABSTRACT
                : AgentTownLifeFidelity.BACKGROUND_ACTIVE;
    }

    static boolean rendersAmbientActions(AgentTownLifeFidelity fidelity) {
        return fidelity == AgentTownLifeFidelity.PRESENTATION;
    }

    static boolean usesPhysicalNavigation(AgentTownLifeFidelity fidelity) {
        return fidelity != AgentTownLifeFidelity.BACKGROUND_ABSTRACT;
    }

    static boolean createsEncounters(AgentTownLifeFidelity fidelity) {
        return fidelity != AgentTownLifeFidelity.BACKGROUND_ABSTRACT;
    }
}
