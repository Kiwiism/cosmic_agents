package server.agents.capabilities.movement;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentMovementPhysicsCacheStateTest {
    @Test
    void defaultsPreserveLegacyAgentRuntimeEntryValues() {
        AgentMovementPhysicsCacheState state = new AgentMovementPhysicsCacheState();

        assertEquals(0, state.lastGroundFootholdId());
    }

    @Test
    void storesLastGroundFootholdId() {
        AgentMovementPhysicsCacheState state = new AgentMovementPhysicsCacheState();

        state.setLastGroundFootholdId(12345);

        assertEquals(12345, state.lastGroundFootholdId());
    }
}
