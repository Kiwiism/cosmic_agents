package server.agents.capabilities.presentation;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.integration.PrimitiveCapabilityGateway;
import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentPresentationSafetyGateTest {
    @Test
    void stationaryIntentMayRunNearDestination() {
        Character agent = mock(Character.class);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        PrimitiveCapabilityGateway gateway = groundedGateway(agent, new Point(100, 100));
        AgentPresentationDecision decision = new AgentPresentationDecision(
                AgentPresentationIntent.WAIT,
                AgentPresentationTrigger.ARRIVAL,
                0,
                500);

        assertEquals(AgentPresentationSafetyGate.Result.SAFE,
                AgentPresentationSafetyGate.evaluate(
                        entry, agent, decision, new Point(105, 100), gateway));
    }

    @Test
    void movementIntentIsBlockedNearDestination() {
        Character agent = mock(Character.class);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        PrimitiveCapabilityGateway gateway = groundedGateway(agent, new Point(100, 100));
        AgentPresentationDecision decision = new AgentPresentationDecision(
                AgentPresentationIntent.HOP,
                AgentPresentationTrigger.ARRIVAL,
                0,
                500);

        assertEquals(AgentPresentationSafetyGate.Result.NEAR_DESTINATION,
                AgentPresentationSafetyGate.evaluate(
                        entry, agent, decision, new Point(105, 100), gateway));
    }

    private static PrimitiveCapabilityGateway groundedGateway(Character agent, Point position) {
        PrimitiveCapabilityGateway gateway = mock(PrimitiveCapabilityGateway.class);
        when(gateway.alive(agent)).thenReturn(true);
        when(gateway.position(agent)).thenReturn(position);
        when(gateway.grounded(agent)).thenReturn(true);
        return gateway;
    }
}
