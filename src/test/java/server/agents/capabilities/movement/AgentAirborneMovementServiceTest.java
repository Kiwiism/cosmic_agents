package server.agents.capabilities.movement;

import org.junit.jupiter.api.Test;
import server.agents.integration.AgentMovementPhysicsStateRuntime;
import server.agents.integration.AgentMovementStateRuntime;
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
        AgentMovementPhysicsStateRuntime.setFixedAirArc(entry, true);

        assertFalse(AgentAirborneMovementService.shouldApplyAirSteering(entry));
    }

    @Test
    void downJumpGraceSuppressesAirSteering() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        AgentMovementStateRuntime.setDownJumpGracePeriodMs(entry, 10L);

        assertFalse(AgentAirborneMovementService.shouldApplyAirSteering(entry));
    }
}
