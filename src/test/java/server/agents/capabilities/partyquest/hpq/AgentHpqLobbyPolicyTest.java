package server.agents.capabilities.partyquest.hpq;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentHpqLobbyPolicyTest {
    @Test
    void preservesConfiguredHumanCapacity() {
        assertTrue(AgentHpqLobbyPolicy.backgroundSlotAvailable(0, 0, 2, 2, 1, 1));
        assertFalse(AgentHpqLobbyPolicy.backgroundSlotAvailable(1, 1, 2, 2, 1, 1));
        assertFalse(AgentHpqLobbyPolicy.backgroundSlotAvailable(0, 0, 1, 2, 1, 1));
    }

    @Test
    void respectsGlobalBackgroundLimit() {
        assertTrue(AgentHpqLobbyPolicy.backgroundSlotAvailable(1, 0, 2, 2, 1, 1));
        assertFalse(AgentHpqLobbyPolicy.backgroundSlotAvailable(2, 0, 2, 2, 1, 1));
    }
}
