package server.agents.capabilities.movement;

import client.Character;
import constants.game.CharacterStance;
import org.junit.jupiter.api.Test;
import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentSpawnFallServiceTest {
    @Test
    void dropThresholdDistinguishesSnapFromVisibleFall() {
        Point spawn = new Point(10, 20);

        assertFalse(AgentSpawnFallService.shouldFall(spawn, new Point(10, 36)));
        assertTrue(AgentSpawnFallService.shouldFall(spawn, new Point(10, 37)));
        assertFalse(AgentSpawnFallService.shouldFall(spawn, null));
    }

    @Test
    void trainingCampPortalGapSnapsToGroundInsteadOfStartingAirPhysics() {
        Point portalSpawn = new Point(315, -760);
        Point foothold = new Point(315, -745);

        assertFalse(AgentSpawnFallService.shouldFall(portalSpawn, foothold));
    }

    @Test
    void beginFallKeepsPositionAndInitializesZeroVelocityAirState() {
        Character agent = mock(Character.class);
        when(agent.getPosition()).thenReturn(new Point(10, 20));
        when(agent.getHp()).thenReturn(100);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        AgentMovementPhysicsStateRuntime.setHorizontalSpeed(entry, 50.0);
        AgentMovementPhysicsStateRuntime.setVerticalVelocity(entry, 9f);

        AgentSpawnFallService.beginFall(entry, agent);

        assertTrue(AgentMovementStateRuntime.inAir(entry));
        assertEquals(0.0, AgentMovementPhysicsStateRuntime.horizontalSpeed(entry));
        assertEquals(0f, AgentMovementPhysicsStateRuntime.verticalVelocity(entry));
        assertEquals(new Point(10, 20), AgentMovementPhysicsStateRuntime.roundedPhysicsPosition(entry));
        assertEquals(CharacterStance.JUMP_RIGHT_STANCE, AgentMovementPoseService.resolveStance(entry));
    }
}
