package server.agents.capabilities.movement;

import org.junit.jupiter.api.Test;
import server.agents.integration.AgentBotMovementPhysicsStateRuntime;
import server.agents.integration.AgentBotMovementStateRuntime;
import server.agents.runtime.AgentRuntimeEntry;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentAirborneMovementServiceTest {
    @Test
    void appliesAirSteeringWhenNoFixedArcGraceOrNavigationEdgeExists() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        assertTrue(AgentAirborneMovementService.shouldApplyAirSteering(entry));
    }

    @Test
    void fixedAirArcSuppressesAirSteering() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        AgentBotMovementPhysicsStateRuntime.setFixedAirArc(entry, true);

        assertFalse(AgentAirborneMovementService.shouldApplyAirSteering(entry));
    }

    @Test
    void downJumpGraceSuppressesAirSteering() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        AgentBotMovementStateRuntime.setDownJumpGracePeriodMs(entry, 10L);

        assertFalse(AgentAirborneMovementService.shouldApplyAirSteering(entry));
    }
}
