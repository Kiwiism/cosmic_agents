package server.agents.runtime.townlife;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.capabilities.townlife.AgentTownLifeRuntime;
import server.agents.integration.AgentPrimitiveCapabilityGatewayRuntime;
import server.agents.integration.PrimitiveCapabilityGateway;
import server.agents.runtime.AgentRuntimeEntry;
import server.maps.MapleMap;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class AgentTownLifeTestScenarioRuntimeTest {
    @Test
    void exitsStagesWaitsAndStartsANewSessionWithANewCycle() {
        Character agent = mock(Character.class);
        MapleMap map = mock(MapleMap.class);
        when(agent.getId()).thenReturn(610);
        when(agent.getName()).thenReturn("CycleTest");
        when(agent.getMapId()).thenReturn(104000000);
        when(agent.getMap()).thenReturn(map);
        when(agent.getPosition()).thenReturn(new Point(0, 0));
        when(agent.getChair()).thenReturn(-1);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, agent, null);
        PrimitiveCapabilityGateway gateway = mock(PrimitiveCapabilityGateway.class);
        when(gateway.groundPoint(any(MapleMap.class), any(Point.class)))
                .thenAnswer(invocation -> new Point(invocation.getArgument(1, Point.class)));
        when(gateway.grounded(agent)).thenReturn(true);

        try (var gatewayRuntime = mockStatic(AgentPrimitiveCapabilityGatewayRuntime.class)) {
            gatewayRuntime.when(AgentPrimitiveCapabilityGatewayRuntime::gateway)
                    .thenReturn(gateway);
            AgentTownLifeTestScenarioRequest request = new AgentTownLifeTestScenarioRequest(
                    "cycle-test", "operator", 104000000,
                    10_000L, 2_000L, 5_000L, 2,
                    AgentTownLifeStandbyTarget.fallback());

            assertTrue(AgentTownLifeTestScenarioRuntime.start(
                    entry, agent, request, 1_000L).started());
            String firstSession = entry.capabilityStates()
                    .require(server.agents.capabilities.townlife.AgentTownLifeState.STATE_KEY)
                    .sessionId();
            AgentTownLifeRuntime.forceStop(entry, agent, "simulate lease completion");
            AgentTownLifeTestScenarioRuntime.tick(entry, agent, 11_000L);
            Point standby = AgentTownLifeTestScenarioRuntime.snapshot(entry).standbyPoint();
            when(gateway.position(agent)).thenReturn(standby);
            AgentTownLifeTestScenarioRuntime.tick(entry, agent, 11_100L);
            assertEquals(AgentTownLifeTestScenarioState.Phase.OUTSIDE_IDLE,
                    AgentTownLifeTestScenarioRuntime.snapshot(entry).phase());

            AgentTownLifeTestScenarioRuntime.tick(entry, agent, 13_100L);
            var snapshot = AgentTownLifeTestScenarioRuntime.snapshot(entry);
            assertEquals(2, snapshot.cyclesStarted());
            assertEquals(AgentTownLifeTestScenarioState.Phase.IN_TOWN_LIFE, snapshot.phase());
            String secondSession = entry.capabilityStates()
                    .require(server.agents.capabilities.townlife.AgentTownLifeState.STATE_KEY)
                    .sessionId();
            assertTrue(!firstSession.equals(secondSession));
        } finally {
            AgentTownLifeRuntime.forceStop(entry, agent, "test cleanup");
            AgentTownLifeVisitLeaseRuntime.clear(entry, agent);
        }
    }
}
