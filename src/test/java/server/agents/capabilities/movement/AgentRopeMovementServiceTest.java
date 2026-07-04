package server.agents.capabilities.movement;

import client.Character;
import constants.game.CharacterStance;
import org.junit.jupiter.api.Test;
import server.agents.integration.AgentBotClimbStateRuntime;
import server.agents.integration.AgentBotMovementPhysicsStateRuntime;
import server.agents.integration.AgentBotMovementStateRuntime;
import server.agents.integration.AgentBotSwimStateRuntime;
import server.bots.BotEntry;
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
        BotEntry entry = new BotEntry(agent, null, null);
        Rope rope = new Rope(100, 0, 80, false);

        AgentRopeMovementService.attachToRope(entry, agent, rope, 30);

        assertTrue(AgentBotClimbStateRuntime.climbing(entry));
        assertSame(rope, AgentBotClimbStateRuntime.climbRope(entry));
        verify(agent).setPosition(new Point(100, 30));
        verify(agent, atLeastOnce()).setStance(CharacterStance.ROPE_STANCE);
    }

    @Test
    void holdClimbPreservesCurrentRopePosition() {
        Character agent = mock(Character.class);
        when(agent.getPosition()).thenReturn(new Point(100, 30));
        when(agent.getHp()).thenReturn(1);
        BotEntry entry = new BotEntry(agent, null, null);
        Rope rope = new Rope(100, 0, 80, false);
        AgentRopeMovementService.attachToRope(entry, agent, rope, 30);

        AgentRopeMovementService.holdClimb(entry, agent);

        assertEquals(0, AgentBotClimbStateRuntime.climbVerticalDirection(entry));
        verify(agent, atLeastOnce()).setStance(CharacterStance.ROPE_STANCE);
    }

    @Test
    void advanceClimbMovesAlongRopeByLegacyStep() {
        Character agent = mock(Character.class);
        when(agent.getPosition()).thenReturn(new Point(100, 30));
        when(agent.getHp()).thenReturn(1);
        BotEntry entry = new BotEntry(agent, null, null);
        Rope rope = new Rope(100, 0, 80, false);
        AgentRopeMovementService.attachToRope(entry, agent, rope, 30);
        AgentBotClimbStateRuntime.setClimbVerticalDirection(entry, 1);

        AgentRopeMovementService.advanceClimb(entry, agent);

        verify(agent).setPosition(new Point(100, 30 + AgentMovementKinematicsService.climbStepPerTick()));
        assertSame(rope, AgentBotClimbStateRuntime.climbRope(entry));
        assertFalse(AgentBotMovementStateRuntime.inAir(entry));
    }

    @Test
    void beginGroundJumpStartsAirborneWithLegacyJumpImpulse() {
        Character agent = mock(Character.class);
        when(agent.getPosition()).thenReturn(new Point(10, 20));
        when(agent.getHp()).thenReturn(1);
        BotEntry entry = new BotEntry(agent, null, null);

        AgentRopeMovementService.beginGroundJump(entry, agent, 4);

        assertTrue(AgentBotMovementStateRuntime.inAir(entry));
        assertFalse(AgentBotClimbStateRuntime.climbUpIntent(entry));
        assertEquals(4, AgentBotMovementPhysicsStateRuntime.airVelocityX(entry));
        assertEquals(-AgentMovementKinematicsService.jumpForcePerTick(AgentBotMovementStateRuntime.movementProfile(entry)),
                AgentBotMovementPhysicsStateRuntime.verticalVelocity(entry), 0.0001f);
    }

    @Test
    void beginClimbUpJumpStartsAirborneWithClimbIntent() {
        Character agent = mock(Character.class);
        when(agent.getPosition()).thenReturn(new Point(10, 20));
        when(agent.getHp()).thenReturn(1);
        BotEntry entry = new BotEntry(agent, null, null);

        AgentRopeMovementService.beginClimbUpJump(entry, agent, -3);

        assertTrue(AgentBotMovementStateRuntime.inAir(entry));
        assertTrue(AgentBotClimbStateRuntime.climbUpIntent(entry));
        assertEquals(-3, AgentBotMovementPhysicsStateRuntime.airVelocityX(entry));
    }

    @Test
    void beginJumpOffRopeUsesLegacyRopeJumpImpulse() {
        Character agent = mock(Character.class);
        when(agent.getPosition()).thenReturn(new Point(10, 20));
        when(agent.getHp()).thenReturn(1);
        BotEntry entry = new BotEntry(agent, null, null);

        AgentRopeMovementService.beginJumpOffRope(entry, agent, 2);

        assertTrue(AgentBotMovementStateRuntime.inAir(entry));
        assertFalse(AgentBotClimbStateRuntime.climbUpIntent(entry));
        assertEquals(2, AgentBotMovementPhysicsStateRuntime.airVelocityX(entry));
        assertEquals(-AgentMovementKinematicsService.ropeJumpForcePerTick(AgentBotMovementStateRuntime.movementProfile(entry)),
                AgentBotMovementPhysicsStateRuntime.verticalVelocity(entry), 0.0001f);
    }

    @Test
    void beginRopeTransferJumpBlocksSourceRopeAndKeepsClimbIntent() {
        Character agent = mock(Character.class);
        when(agent.getPosition()).thenReturn(new Point(10, 20));
        when(agent.getHp()).thenReturn(1);
        BotEntry entry = new BotEntry(agent, null, null);
        Rope sourceRope = new Rope(100, 0, 80, false);

        AgentRopeMovementService.beginRopeTransferJump(entry, agent, sourceRope, 1);

        assertTrue(AgentBotMovementStateRuntime.inAir(entry));
        assertTrue(AgentBotClimbStateRuntime.climbUpIntent(entry));
        assertSame(sourceRope, AgentBotClimbStateRuntime.blockedRopeGrab(entry));
    }

    @Test
    void beginGroundJumpInSwimMapUsesSwimImpulseWithoutPacketVelocityConversion() {
        MapleMap map = mock(MapleMap.class);
        when(map.isSwim()).thenReturn(true);
        Character agent = mock(Character.class);
        when(agent.getMap()).thenReturn(map);
        when(agent.getPosition()).thenReturn(new Point(10, 20));
        when(agent.getHp()).thenReturn(1);
        BotEntry entry = new BotEntry(agent, null, null);

        AgentRopeMovementService.beginGroundJump(entry, agent, 9);

        assertTrue(AgentBotMovementStateRuntime.inAir(entry));
        assertTrue(AgentBotSwimStateRuntime.swimming(entry));
        assertEquals(0, AgentBotMovementPhysicsStateRuntime.airVelocityX(entry));
        assertEquals(-AgentMovementProfile.base().jumpSpeedPxs(),
                AgentBotMovementPhysicsStateRuntime.verticalVelocity(entry), 0.0001f);
        assertEquals(Math.round(AgentBotMovementPhysicsStateRuntime.verticalVelocity(entry)),
                AgentBotMovementStateRuntime.movementVelocityY(entry));
        assertTrue(AgentBotSwimStateRuntime.swimNextJumpAtMs(entry) > 0L);
    }
}
