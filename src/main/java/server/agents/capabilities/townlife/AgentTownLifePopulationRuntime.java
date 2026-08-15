package server.agents.capabilities.townlife;

import client.Character;
import server.agents.integration.AgentClientGatewayRuntime;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;

import java.util.List;

/** Installs the Cosmic adapter while keeping policy scoped to immutable views. */
public final class AgentTownLifePopulationRuntime {
    private static final AgentTownLifePopulationPort COSMIC = () ->
            AgentRuntimeRegistry.activeEntriesSnapshot().stream()
                    .filter(AgentTownLifeRuntime::active)
                    .map(AgentTownLifePopulationRuntime::view)
                    .filter(java.util.Objects::nonNull)
                    .toList();
    private static volatile AgentTownLifePopulationPort port = COSMIC;

    private AgentTownLifePopulationRuntime() {
    }

    public static List<AgentTownLifePopulationPort.AgentView> sameTown(Character agent) {
        if (agent == null || !AgentClientGatewayRuntime.clients().hasClient(agent)) {
            return List.of();
        }
        int world = agent.getWorld();
        int channel = AgentClientGatewayRuntime.clients().channel(agent);
        int mapId = agent.getMapId();
        return port.activeTownAgents().stream()
                .filter(view -> view.world() == world && view.channel() == channel
                        && view.mapId() == mapId)
                .toList();
    }

    public static int count(Character agent) {
        return sameTown(agent).size();
    }

    static void installForTest(AgentTownLifePopulationPort replacement) {
        port = replacement == null ? COSMIC : replacement;
    }

    static void resetForTest() {
        port = COSMIC;
    }

    private static AgentTownLifePopulationPort.AgentView view(AgentRuntimeEntry entry) {
        Character agent = AgentRuntimeIdentityRuntime.bot(entry);
        if (agent == null || !AgentClientGatewayRuntime.clients().hasClient(agent)) {
            return null;
        }
        AgentTownLifeState state = entry.capabilityStates()
                .find(AgentTownLifeState.STATE_KEY).orElse(null);
        return new AgentTownLifePopulationPort.AgentView(
                agent.getId(), agent.getWorld(), AgentClientGatewayRuntime.clients().channel(agent),
                agent.getMapId(), state == null ? "" : state.venueId());
    }
}
