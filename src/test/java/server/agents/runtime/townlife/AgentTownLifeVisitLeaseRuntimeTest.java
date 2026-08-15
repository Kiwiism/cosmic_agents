package server.agents.runtime.townlife;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.capabilities.townlife.AgentTownLifeAdmissionMode;
import server.agents.capabilities.townlife.AgentTownLifeEntryRequest;
import server.agents.capabilities.townlife.AgentTownLifeRuntime;
import server.agents.capabilities.townlife.AgentTownLifeSessionResult;
import server.agents.capabilities.townlife.AgentTownLifeVisitRequest;
import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentTownLifeVisitLeaseRuntimeTest {
    @Test
    void externalDeadlineRequestsGracefulExitAndThenReleasesTheWatcher() {
        Character agent = mock(Character.class);
        when(agent.getId()).thenReturn(900_003);
        when(agent.getName()).thenReturn("TownLeaseTest");
        when(agent.getMapId()).thenReturn(104000000);
        when(agent.getPosition()).thenReturn(new Point(0, 0));
        when(agent.getChair()).thenReturn(-1);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, agent, null);
        AgentTownLifeVisitLeaseRequest request = new AgentTownLifeVisitLeaseRequest(
                AgentTownLifeEntryRequest.external(
                        "lease-test-900003", "lease-test",
                        AgentTownLifeVisitRequest.leisure(agent.getMapId())),
                AgentTownLifeAdmissionMode.MANUAL_ONLY,
                2_000L, 500L, "lease complete");

        try {
            AgentTownLifeSessionResult result = AgentTownLifeVisitLeaseRuntime.start(
                    entry, agent, request, 1_000L, agent.getId());

            assertTrue(result.started());
            assertTrue(AgentTownLifeVisitLeaseRuntime.active(entry));
            AgentTownLifeVisitLeaseRuntime.start(entry, agent,
                    new AgentTownLifeVisitLeaseRequest(
                            request.entryRequest(), request.admissionMode(),
                            9_000L, 900L, "must not replace the original lease"),
                    1_100L, agent.getId());
            AgentTownLifeVisitLeaseState leaseState = entry.capabilityStates()
                    .require(AgentTownLifeVisitLeaseState.STATE_KEY);
            assertEquals(2_000L, leaseState.exitAtMs());
            assertEquals(500L, leaseState.gracefulTimeoutMs());
            assertFalse(AgentTownLifeVisitLeaseRuntime.tick(entry, agent, 1_999L));
            assertTrue(AgentTownLifeRuntime.active(entry));
            assertFalse(AgentTownLifeVisitLeaseRuntime.tick(entry, agent, 2_000L));
            assertFalse(AgentTownLifeRuntime.active(entry));
            assertTrue(AgentTownLifeVisitLeaseRuntime.active(entry));
            assertFalse(AgentTownLifeVisitLeaseRuntime.tick(entry, agent, 2_001L));
            assertFalse(AgentTownLifeVisitLeaseRuntime.active(entry));
        } finally {
            AgentTownLifeRuntime.forceStop(entry, agent, "test cleanup");
            AgentTownLifeVisitLeaseRuntime.clear(entry, agent);
        }
    }
}
