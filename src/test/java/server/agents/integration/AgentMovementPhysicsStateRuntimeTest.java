package server.agents.integration;

import server.agents.runtime.AgentRuntimeEntry;

import org.junit.jupiter.api.Test;
import server.agents.integration.AgentMovementPhysicsStateRuntime;
import server.agents.capabilities.movement.AgentGroundTravelState;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentMovementPhysicsStateRuntimeTest {
    @Test
    void jumpCooldownIsStoredAndClearedThroughAgentBoundary() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        AgentMovementPhysicsStateRuntime.setJumpCooldownMs(entry, 120);
        assertEquals(120, AgentMovementPhysicsStateRuntime.jumpCooldownMs(entry));

        AgentMovementPhysicsStateRuntime.clearJumpCooldown(entry);
        assertEquals(0, AgentMovementPhysicsStateRuntime.jumpCooldownMs(entry));
    }

    @Test
    void fixedAirArcIsStoredThroughAgentBoundary() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        assertFalse(AgentMovementPhysicsStateRuntime.fixedAirArc(entry));

        AgentMovementPhysicsStateRuntime.setFixedAirArc(entry, true);
        assertTrue(AgentMovementPhysicsStateRuntime.fixedAirArc(entry));

        AgentMovementPhysicsStateRuntime.setFixedAirArc(entry, false);
        assertFalse(AgentMovementPhysicsStateRuntime.fixedAirArc(entry));
    }

    @Test
    void airborneVelocityReadsThroughAgentBoundary() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        AgentMovementPhysicsStateRuntime.setVerticalVelocity(entry, -12.5f);
        AgentMovementPhysicsStateRuntime.setAirVelocityX(entry, 7);

        assertEquals(-12.5f, AgentMovementPhysicsStateRuntime.verticalVelocity(entry));
        assertEquals(7, AgentMovementPhysicsStateRuntime.airVelocityX(entry));

        AgentMovementPhysicsStateRuntime.setVerticalVelocity(entry, 3.5f);
        assertEquals(3.5f, AgentMovementPhysicsStateRuntime.verticalVelocity(entry));

        AgentMovementPhysicsStateRuntime.setAirVelocityX(entry, -9);
        assertEquals(-9, AgentMovementPhysicsStateRuntime.airVelocityX(entry));

        AgentMovementPhysicsStateRuntime.setAirSteerVelocityX(entry, 1.0);
        assertEquals(1.0, AgentMovementPhysicsStateRuntime.airSteerVelocityX(entry));
        AgentMovementPhysicsStateRuntime.addClampedAirSteerVelocityX(entry, 2.0, 1.5);
        assertEquals(1.5, AgentMovementPhysicsStateRuntime.airSteerVelocityX(entry));
        AgentMovementPhysicsStateRuntime.addClampedAirSteerVelocityX(entry, -5.0, 1.5);
        assertEquals(-1.5, AgentMovementPhysicsStateRuntime.airSteerVelocityX(entry));
    }

    @Test
    void lastGroundFootholdIsStoredThroughAgentBoundary() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        assertEquals(0, AgentMovementPhysicsStateRuntime.lastGroundFhId(entry));

        AgentMovementPhysicsStateRuntime.setLastGroundFhId(entry, 12345);
        assertEquals(12345, AgentMovementPhysicsStateRuntime.lastGroundFhId(entry));
    }

    @Test
    void groundTravelStateCopiesCurrentPhysicsValuesThroughAgentBoundary() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        AgentMovementPhysicsStateRuntime.setPhysicsX(entry, 12.5);
        AgentMovementPhysicsStateRuntime.setHorizontalSpeed(entry, 3.25);
        AgentMovementPhysicsStateRuntime.setGroundPhysicsCarryMs(entry, 8.0);

        AgentGroundTravelState state = AgentMovementPhysicsStateRuntime.groundTravelState(entry);

        assertEquals(12.5, state.physX());
        assertEquals(3.25, state.hspeed());
        assertEquals(8.0, state.carryMs());
    }

    @Test
    void physicsPositionAndSpeedAreStoredThroughAgentBoundary() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        AgentMovementPhysicsStateRuntime.setHorizontalSpeed(entry, 4.5);
        AgentMovementPhysicsStateRuntime.setPhysicsPosition(entry, new Point(10, 20));

        assertEquals(4.5, AgentMovementPhysicsStateRuntime.horizontalSpeed(entry));
        assertEquals(10.0, AgentMovementPhysicsStateRuntime.physicsX(entry));
        assertEquals(20.0, AgentMovementPhysicsStateRuntime.physicsY(entry));
        assertEquals(new Point(10, 20), AgentMovementPhysicsStateRuntime.roundedPhysicsPosition(entry));

        AgentMovementPhysicsStateRuntime.setPhysicsX(entry, 12.6);
        assertEquals(12.6, AgentMovementPhysicsStateRuntime.physicsX(entry));
        assertEquals(13, AgentMovementPhysicsStateRuntime.roundedPhysicsX(entry));

        AgentMovementPhysicsStateRuntime.setPhysicsY(entry, 22.4);
        assertEquals(22.4, AgentMovementPhysicsStateRuntime.physicsY(entry));

        AgentMovementPhysicsStateRuntime.setPhysicsPosition(entry, 15.5, 25.5);
        assertEquals(15.5, AgentMovementPhysicsStateRuntime.physicsX(entry));
        assertEquals(25.5, AgentMovementPhysicsStateRuntime.physicsY(entry));

        AgentMovementPhysicsStateRuntime.addPhysicsPosition(entry, 2.0, -3.0);
        assertEquals(17.5, AgentMovementPhysicsStateRuntime.physicsX(entry));
        assertEquals(22.5, AgentMovementPhysicsStateRuntime.physicsY(entry));

        AgentMovementPhysicsStateRuntime.setGroundPhysicsCarryMs(entry, 6.75);
        assertEquals(6.75, AgentMovementPhysicsStateRuntime.groundPhysicsCarryMs(entry));
    }

    @Test
    void fallPeakPhysicsYIsStoredAndResetThroughAgentBoundary() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        assertFalse(AgentMovementPhysicsStateRuntime.hasFallPeakPhysicsY(entry));

        AgentMovementPhysicsStateRuntime.setFallPeakPhysicsY(entry, 50.0);
        assertTrue(AgentMovementPhysicsStateRuntime.hasFallPeakPhysicsY(entry));
        assertEquals(50.0, AgentMovementPhysicsStateRuntime.fallPeakPhysicsY(entry));

        AgentMovementPhysicsStateRuntime.recordFallPeakPhysicsY(entry, 60.0);
        assertEquals(50.0, AgentMovementPhysicsStateRuntime.fallPeakPhysicsY(entry));

        AgentMovementPhysicsStateRuntime.recordFallPeakPhysicsY(entry, 40.0);
        assertEquals(40.0, AgentMovementPhysicsStateRuntime.fallPeakPhysicsY(entry));

        AgentMovementPhysicsStateRuntime.resetFallPeakPhysicsY(entry);
        assertFalse(AgentMovementPhysicsStateRuntime.hasFallPeakPhysicsY(entry));
    }
}
