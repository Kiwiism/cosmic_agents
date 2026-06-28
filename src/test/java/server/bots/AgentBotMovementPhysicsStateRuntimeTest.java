package server.bots;

import org.junit.jupiter.api.Test;
import server.agents.integration.AgentBotMovementPhysicsStateRuntime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentBotMovementPhysicsStateRuntimeTest {
    @Test
    void jumpCooldownIsStoredAndClearedThroughAgentBoundary() {
        BotEntry entry = new BotEntry(null, null, null);

        AgentBotMovementPhysicsStateRuntime.setJumpCooldownMs(entry, 120);
        assertEquals(120, AgentBotMovementPhysicsStateRuntime.jumpCooldownMs(entry));

        AgentBotMovementPhysicsStateRuntime.clearJumpCooldown(entry);
        assertEquals(0, AgentBotMovementPhysicsStateRuntime.jumpCooldownMs(entry));
    }

    @Test
    void fixedAirArcIsStoredThroughAgentBoundary() {
        BotEntry entry = new BotEntry(null, null, null);

        assertFalse(AgentBotMovementPhysicsStateRuntime.fixedAirArc(entry));

        AgentBotMovementPhysicsStateRuntime.setFixedAirArc(entry, true);
        assertTrue(AgentBotMovementPhysicsStateRuntime.fixedAirArc(entry));

        AgentBotMovementPhysicsStateRuntime.setFixedAirArc(entry, false);
        assertFalse(AgentBotMovementPhysicsStateRuntime.fixedAirArc(entry));
    }

    @Test
    void lastGroundFootholdIsStoredThroughAgentBoundary() {
        BotEntry entry = new BotEntry(null, null, null);

        assertEquals(0, AgentBotMovementPhysicsStateRuntime.lastGroundFhId(entry));

        AgentBotMovementPhysicsStateRuntime.setLastGroundFhId(entry, 12345);
        assertEquals(12345, AgentBotMovementPhysicsStateRuntime.lastGroundFhId(entry));
    }
}
