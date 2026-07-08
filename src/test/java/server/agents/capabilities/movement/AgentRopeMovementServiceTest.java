package server.agents.capabilities.movement;

import client.Character;
import constants.game.CharacterStance;
import org.junit.jupiter.api.Test;
import server.agents.integration.AgentMovementPhysicsStateRuntime;
import server.agents.integration.AgentMovementStateRuntime;
import server.agents.integration.AgentSwimStateRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.maps.MapleMap;
import server.maps.Rope;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentRopeMovementServiceTest {
    @Test
    void attachToRopePreservesLegacyClimbStateAndPosition() {
        Character agent = mock(Character.class);
        when(agent.getPosition()).thenReturn(new Point(100, 40));
        when(agent.getHp()).thenReturn(1);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        Rope rope = new Rope(100, 0, 80, false);

        AgentRopeMovementService.attachToRope(entry, agent, rope, 30);

        assertTrue(AgentClimbStateRuntime.climbing(entry));
        assertSame(rope, AgentClimbStateRuntime.climbRope(entry));
        verify(agent).setPosition(new Point(100, 30));
        verify(agent, atLeastOnce()).setStance(CharacterStance.ROPE_STANCE);
    }

    @Test
    void holdClimbPreservesCurrentRopePosition() {
        Character agent = mock(Character.class);
        when(agent.getPosition()).thenReturn(new Point(100, 30));
        when(agent.getHp()).thenReturn(1);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        Rope rope = new Rope(100, 0, 80, false);
        AgentRopeMovementService.attachToRope(entry, agent, rope, 30);

        AgentRopeMovementService.holdClimb(entry, agent);

        assertEquals(0, AgentClimbStateRuntime.climbVerticalDirection(entry));
        verify(agent, atLeastOnce()).setStance(CharacterStance.ROPE_STANCE);
    }

    @Test
    void advanceClimbMovesAlongRopeByLegacyStep() {
        Character agent = mock(Character.class);
        when(agent.getPosition()).thenReturn(new Point(100, 30));
        when(agent.getHp()).thenReturn(1);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        Rope rope = new Rope(100, 0, 80, false);
        AgentRopeMovementService.attachToRope(entry, agent, rope, 30);
        AgentClimbStateRuntime.setClimbVerticalDirection(entry, 1);

        AgentRopeMovementService.advanceClimb(entry, agent);

        verify(agent).setPosition(new Point(100, 30 + AgentMovementKinematicsService.climbStepPerTick()));
        assertSame(rope, AgentClimbStateRuntime.climbRope(entry));
        assertFalse(AgentMovementStateRuntime.inAir(entry));
    }

    @Test
    void beginGroundJumpStartsAirborneWithLegacyJumpImpulse() {
        Character agent = mock(Character.class);
        when(agent.getPosition()).thenReturn(new Point(10, 20));
        when(agent.getHp()).thenReturn(1);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);

        AgentRopeMovementService.beginGroundJump(entry, agent, 4);

        assertTrue(AgentMovementStateRuntime.inAir(entry));
        assertFalse(AgentClimbStateRuntime.climbUpIntent(entry));
        assertEquals(4, AgentMovementPhysicsStateRuntime.airVelocityX(entry));
        assertEquals(-AgentMovementKinematicsService.jumpForcePerTick(AgentMovementStateRuntime.movementProfile(entry)),
                AgentMovementPhysicsStateRuntime.verticalVelocity(entry), 0.0001f);
    }

    @Test
    void beginClimbUpJumpStartsAirborneWithClimbIntent() {
        Character agent = mock(Character.class);
        when(agent.getPosition()).thenReturn(new Point(10, 20));
        when(agent.getHp()).thenReturn(1);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);

        AgentRopeMovementService.beginClimbUpJump(entry, agent, -3);

        assertTrue(AgentMovementStateRuntime.inAir(entry));
        assertTrue(AgentClimbStateRuntime.climbUpIntent(entry));
        assertEquals(-3, AgentMovementPhysicsStateRuntime.airVelocityX(entry));
    }

    @Test
    void beginJumpOffRopeUsesLegacyRopeJumpImpulse() {
        Character agent = mock(Character.class);
        when(agent.getPosition()).thenReturn(new Point(10, 20));
        when(agent.getHp()).thenReturn(1);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);

        AgentRopeMovementService.beginJumpOffRope(entry, agent, 2);

        assertTrue(AgentMovementStateRuntime.inAir(entry));
        assertFalse(AgentClimbStateRuntime.climbUpIntent(entry));
        assertEquals(2, AgentMovementPhysicsStateRuntime.airVelocityX(entry));
        assertEquals(-AgentMovementKinematicsService.ropeJumpForcePerTick(AgentMovementStateRuntime.movementProfile(entry)),
                AgentMovementPhysicsStateRuntime.verticalVelocity(entry), 0.0001f);
    }

    @Test
    void beginRopeTransferJumpBlocksSourceRopeAndKeepsClimbIntent() {
        Character agent = mock(Character.class);
        when(agent.getPosition()).thenReturn(new Point(10, 20));
        when(agent.getHp()).thenReturn(1);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        Rope sourceRope = new Rope(100, 0, 80, false);

        AgentRopeMovementService.beginRopeTransferJump(entry, agent, sourceRope, 1);

        assertTrue(AgentMovementStateRuntime.inAir(entry));
        assertTrue(AgentClimbStateRuntime.climbUpIntent(entry));
        assertSame(sourceRope, AgentClimbStateRuntime.blockedRopeGrab(entry));
    }

    @Test
    void beginGroundJumpInSwimMapUsesSwimImpulseWithoutPacketVelocityConversion() {
        MapleMap map = mock(MapleMap.class);
        when(map.isSwim()).thenReturn(true);
        Character agent = mock(Character.class);
        when(agent.getMap()).thenReturn(map);
        when(agent.getPosition()).thenReturn(new Point(10, 20));
        when(agent.getHp()).thenReturn(1);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);

        AgentRopeMovementService.beginGroundJump(entry, agent, 9);

        assertTrue(AgentMovementStateRuntime.inAir(entry));
        assertTrue(AgentSwimStateRuntime.swimming(entry));
        assertEquals(0, AgentMovementPhysicsStateRuntime.airVelocityX(entry));
        assertEquals(-AgentMovementProfile.base().jumpSpeedPxs(),
                AgentMovementPhysicsStateRuntime.verticalVelocity(entry), 0.0001f);
        assertEquals(Math.round(AgentMovementPhysicsStateRuntime.verticalVelocity(entry)),
                AgentMovementStateRuntime.movementVelocityY(entry));
        assertTrue(AgentSwimStateRuntime.swimNextJumpAtMs(entry) > 0L);
    }
}
