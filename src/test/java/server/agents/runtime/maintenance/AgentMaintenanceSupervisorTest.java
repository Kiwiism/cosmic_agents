package server.agents.runtime.maintenance;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class AgentMaintenanceSupervisorTest {
    @Test
    void stopsAtFirstMaintenanceHandlerThatConsumesTheTick() {
        List<String> calls = new ArrayList<>();
        AgentMaintenanceSupervisor supervisor = new AgentMaintenanceSupervisor(List.of(
                (entry, agent, nowMs) -> {
                    calls.add("recovery");
                    return false;
                },
                (entry, agent, nowMs) -> {
                    calls.add("supplies");
                    return true;
                },
                (entry, agent, nowMs) -> {
                    calls.add("cleanup");
                    return true;
                }));

        assertTrue(supervisor.tick(new AgentRuntimeEntry(null, null, null),
                mock(Character.class), 10L));
        assertEquals(List.of("recovery", "supplies"), calls);
    }
}
