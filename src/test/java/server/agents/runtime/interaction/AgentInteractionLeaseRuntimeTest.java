package server.agents.runtime.interaction;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.capabilities.townlife.AgentTownLifeAdmissionMode;
import server.agents.capabilities.townlife.AgentTownLifeEntryRequest;
import server.agents.capabilities.townlife.AgentTownLifeExitRequest;
import server.agents.capabilities.townlife.AgentTownLifeRuntime;
import server.agents.capabilities.townlife.AgentTownLifeState;
import server.agents.capabilities.townlife.AgentTownLifeVisitRequest;
import server.agents.integration.AgentPrimitiveCapabilityGatewayRuntime;
import server.agents.integration.PrimitiveCapabilityGateway;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.activity.AgentForegroundActivityTick;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class AgentInteractionLeaseRuntimeTest {
    @Test
    void chatParksTownLifeAndGracefulExitWaitsUntilTheInteractionCloses() {
        Character agent = mock(Character.class);
        when(agent.getId()).thenReturn(81);
        when(agent.getName()).thenReturn("InteractionTest");
        when(agent.getMapId()).thenReturn(104000000);
        when(agent.getPosition()).thenReturn(new Point(0, 0));
        when(agent.getChair()).thenReturn(-1);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, agent, null);
        var started = AgentTownLifeRuntime.requestSession(
                entry, agent,
                AgentTownLifeEntryRequest.external("interaction-visit", "test",
                        AgentTownLifeVisitRequest.leisure(agent.getMapId())),
                AgentTownLifeAdmissionMode.MANUAL_ONLY, 1_000L, agent.getId());
        AgentTownLifeState townState = entry.capabilityStates()
                .require(AgentTownLifeState.STATE_KEY);
        townState.select(AgentTownLifeState.Activity.LINGER,
                new Point(0, 0), 0, 1_100L);
        townState.beginDwell(5_000L);
        PrimitiveCapabilityGateway gateway = mock(PrimitiveCapabilityGateway.class);

        try (var gatewayRuntime = mockStatic(AgentPrimitiveCapabilityGatewayRuntime.class)) {
            gatewayRuntime.when(AgentPrimitiveCapabilityGatewayRuntime::gateway)
                    .thenReturn(gateway);

            String interactionId = AgentInteractionLeaseRuntime.beginChat(
                    entry, agent, 99, 1_200L);
            AgentTownLifeRuntime.requestExit(entry, agent,
                    AgentTownLifeExitRequest.graceful(
                            started.handle(), "next objective", 1_300L, 10_000L));

            assertFalse(interactionId.isBlank());
            assertTrue(townState.externalInteractionPaused());
            assertTrue(AgentTownLifeRuntime.active(entry));
            AgentInteractionLeaseRuntime.complete(
                    entry, AgentInteractionLeaseState.Type.CHAT);
            assertEquals(AgentForegroundActivityTick.CONSUMED,
                    AgentInteractionLeaseRuntime.tick(entry, agent, 2_699L));
            assertEquals(AgentForegroundActivityTick.PASS,
                    AgentInteractionLeaseRuntime.tick(entry, agent, 2_700L));
            assertFalse(townState.externalInteractionPaused());
            assertEquals(6_500L, townState.nextActionAtMs());

            assertTrue(AgentTownLifeRuntime.tick(entry, agent, 6_500L));
            assertFalse(AgentTownLifeRuntime.active(entry));
        } finally {
            AgentInteractionLeaseRuntime.cancel(entry, agent, "test cleanup", 20_000L);
            AgentTownLifeRuntime.forceStop(entry, agent, "test cleanup");
        }
    }
}
