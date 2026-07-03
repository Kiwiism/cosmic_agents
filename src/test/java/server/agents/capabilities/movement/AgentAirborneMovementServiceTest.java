package server.agents.capabilities.movement;

import org.junit.jupiter.api.Test;
import server.agents.integration.AgentBotMovementPhysicsStateRuntime;
import server.agents.integration.AgentBotMovementStateRuntime;
import server.bots.BotEntry;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentAirborneMovementServiceTest {
    @Test
    void appliesAirSteeringWhenNoFixedArcGraceOrNavigationEdgeExists() {
        BotEntry entry = new BotEntry(null, null, null);

        assertTrue(AgentAirborneMovementService.shouldApplyAirSteering(entry));
    }

    @Test
    void fixedAirArcSuppressesAirSteering() {
        BotEntry entry = new BotEntry(null, null, null);
        AgentBotMovementPhysicsStateRuntime.setFixedAirArc(entry, true);

        assertFalse(AgentAirborneMovementService.shouldApplyAirSteering(entry));
    }

    @Test
    void downJumpGraceSuppressesAirSteering() {
        BotEntry entry = new BotEntry(null, null, null);
        AgentBotMovementStateRuntime.setDownJumpGracePeriodMs(entry, 10L);

        assertFalse(AgentAirborneMovementService.shouldApplyAirSteering(entry));
    }
}
