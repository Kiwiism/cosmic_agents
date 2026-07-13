package server.agents.runtime.simulation;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.integration.MapGateway;
import server.agents.runtime.AgentRuntimeEntry;
import server.maps.MapleMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentSimulationPolicyTest {
    @Test
    void disabledPolicyAlwaysPreservesPresentation() {
        MapGateway maps = mock(MapGateway.class);
        AgentRuntimeEntry entry = entryWithMap(mock(MapleMap.class));
        AgentSimulationPolicy policy = new AgentDefaultSimulationPolicy(
                false, true, maps, (runtime, map) -> true);

        assertEquals(AgentSimulationMode.PRESENTATION, policy.selectMode(entry));
    }

    @Test
    void observedMapUsesPresentationAndUnobservedMapUsesBackgroundActive() {
        MapleMap map = mock(MapleMap.class);
        MapGateway maps = mock(MapGateway.class);
        AgentRuntimeEntry entry = entryWithMap(map);
        AgentSimulationPolicy policy = new AgentDefaultSimulationPolicy(
                true, false, maps, AgentBackgroundExecutionPolicy.denyAll());

        when(maps.isObservedByPlayer(map)).thenReturn(true);
        assertEquals(AgentSimulationMode.PRESENTATION, policy.selectMode(entry));

        when(maps.isObservedByPlayer(map)).thenReturn(false);
        assertEquals(AgentSimulationMode.BACKGROUND_ACTIVE, policy.selectMode(entry));
    }

    @Test
    void abstractModeRequiresBothFlagAndExecutionPolicyApproval() {
        MapleMap map = mock(MapleMap.class);
        MapGateway maps = mock(MapGateway.class);
        AgentRuntimeEntry entry = entryWithMap(map);

        AgentSimulationPolicy denied = new AgentDefaultSimulationPolicy(
                true, true, maps, AgentBackgroundExecutionPolicy.denyAll());
        assertEquals(AgentSimulationMode.BACKGROUND_ACTIVE, denied.selectMode(entry));

        AgentSimulationPolicy permitted = new AgentDefaultSimulationPolicy(
                true, true, maps, (runtime, candidateMap) -> true);
        assertEquals(AgentSimulationMode.BACKGROUND_ABSTRACT, permitted.selectMode(entry));
    }

    private static AgentRuntimeEntry entryWithMap(MapleMap map) {
        Character agent = mock(Character.class);
        when(agent.getMap()).thenReturn(map);
        return new AgentRuntimeEntry(agent, null, null);
    }
}
