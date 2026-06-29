package server.bots;

import org.junit.jupiter.api.Test;
import server.agents.integration.AgentBotMovementPhysicsStateRuntime;

import java.awt.Point;

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
    void airborneVelocityReadsThroughAgentBoundary() {
        BotEntry entry = new BotEntry(null, null, null);
        entry.velY = -12.5f;
        entry.airVelX = 7;

        assertEquals(-12.5f, AgentBotMovementPhysicsStateRuntime.verticalVelocity(entry));
        assertEquals(7, AgentBotMovementPhysicsStateRuntime.airVelocityX(entry));

        AgentBotMovementPhysicsStateRuntime.setVerticalVelocity(entry, 3.5f);
        assertEquals(3.5f, AgentBotMovementPhysicsStateRuntime.verticalVelocity(entry));
    }

    @Test
    void lastGroundFootholdIsStoredThroughAgentBoundary() {
        BotEntry entry = new BotEntry(null, null, null);

        assertEquals(0, AgentBotMovementPhysicsStateRuntime.lastGroundFhId(entry));

        AgentBotMovementPhysicsStateRuntime.setLastGroundFhId(entry, 12345);
        assertEquals(12345, AgentBotMovementPhysicsStateRuntime.lastGroundFhId(entry));
    }

    @Test
    void groundTravelStateCopiesCurrentPhysicsValuesThroughAgentBoundary() {
        BotEntry entry = new BotEntry(null, null, null);
        entry.physX = 12.5;
        entry.hspeed = 3.25;
        entry.groundPhysicsCarryMs = 8.0;

        BotPhysicsEngine.GroundTravelState state =
                (BotPhysicsEngine.GroundTravelState) AgentBotMovementPhysicsStateRuntime.groundTravelState(entry);

        assertEquals(12.5, state.physX());
        assertEquals(3.25, state.hspeed());
        assertEquals(8.0, state.carryMs());
    }

    @Test
    void physicsPositionAndSpeedAreStoredThroughAgentBoundary() {
        BotEntry entry = new BotEntry(null, null, null);

        AgentBotMovementPhysicsStateRuntime.setHorizontalSpeed(entry, 4.5);
        AgentBotMovementPhysicsStateRuntime.setPhysicsPosition(entry, new Point(10, 20));

        assertEquals(4.5, AgentBotMovementPhysicsStateRuntime.horizontalSpeed(entry));
        assertEquals(10.0, AgentBotMovementPhysicsStateRuntime.physicsX(entry));
        assertEquals(20.0, AgentBotMovementPhysicsStateRuntime.physicsY(entry));
        assertEquals(new Point(10, 20), AgentBotMovementPhysicsStateRuntime.roundedPhysicsPosition(entry));

        AgentBotMovementPhysicsStateRuntime.setPhysicsX(entry, 12.6);
        assertEquals(12.6, AgentBotMovementPhysicsStateRuntime.physicsX(entry));
        assertEquals(13, AgentBotMovementPhysicsStateRuntime.roundedPhysicsX(entry));
    }
}
