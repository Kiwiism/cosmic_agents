package server.agents.integration;

import server.agents.runtime.AgentRuntimeEntry;

import org.junit.jupiter.api.Test;
import server.agents.integration.AgentGrindWanderStateRuntime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentGrindWanderStateRuntimeTest {
    @Test
    void adaptsAndNormalizesWanderDirection() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        assertEquals(0, AgentGrindWanderStateRuntime.wanderDirection(entry));

        AgentGrindWanderStateRuntime.setWanderDirection(entry, 10);
        assertEquals(1, AgentGrindWanderStateRuntime.wanderDirection(entry));

        AgentGrindWanderStateRuntime.setWanderDirection(entry, -10);
        assertEquals(-1, AgentGrindWanderStateRuntime.wanderDirection(entry));

        AgentGrindWanderStateRuntime.clearWanderDirection(entry);
        assertEquals(0, AgentGrindWanderStateRuntime.wanderDirection(entry));
    }

    @Test
    void ensureWanderDirectionKeepsExistingDirectionOrChoosesOne() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        int chosen = AgentGrindWanderStateRuntime.ensureWanderDirection(entry);

        assertTrue(chosen == -1 || chosen == 1);
        assertEquals(chosen, AgentGrindWanderStateRuntime.wanderDirection(entry));
        assertEquals(chosen, AgentGrindWanderStateRuntime.ensureWanderDirection(entry));
    }
}
