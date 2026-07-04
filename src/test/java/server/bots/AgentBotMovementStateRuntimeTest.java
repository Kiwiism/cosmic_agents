package server.bots;

import server.agents.capabilities.movement.AgentMovementProfile;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.capabilities.movement.AgentMovementMode;
import server.agents.capabilities.movement.AgentMovementSnapshot;
import server.agents.integration.AgentBotFarmAnchorStateRuntime;
import server.agents.integration.AgentBotMoveTargetStateRuntime;
import server.agents.integration.AgentBotMovementStateRuntime;
import server.agents.integration.AgentBotPatrolStateRuntime;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentBotMovementStateRuntimeTest {
    @Test
    void snapshotCopiesFollowAndGrindFlagsAndPositions() {
        Character bot = mock(Character.class);
        Character owner = mock(Character.class);
        when(bot.getPosition()).thenReturn(new Point(10, 20));
        when(owner.getPosition()).thenReturn(new Point(30, 40));
        BotEntry entry = new BotEntry(bot, owner, null);
        entry.following = true;
        entry.grinding = true;
        entry.followTargetId = 123;

        AgentMovementSnapshot snapshot = AgentBotMovementStateRuntime.snapshot(entry);

        assertTrue(snapshot.following());
        assertTrue(snapshot.grinding());
        assertEquals(123, snapshot.followTargetId());
        assertEquals(new Point(10, 20), snapshot.botPosition());
        assertEquals(new Point(30, 40), snapshot.ownerPosition());
        assertEquals(AgentMovementMode.GRINDING, snapshot.mode());
    }

    @Test
    void snapshotDefensivelyCopiesMoveTarget() {
        BotEntry entry = new BotEntry(null, null, null);
        AgentBotMoveTargetStateRuntime.setPreciseMoveTarget(entry, new Point(100, 200));

        AgentMovementSnapshot snapshot = AgentBotMovementStateRuntime.snapshot(entry);
        Point exposed = snapshot.moveTarget();
        exposed.x = 999;

        assertEquals(new Point(100, 200), snapshot.moveTarget());
        assertEquals(new Point(100, 200), AgentBotMoveTargetStateRuntime.moveTarget(entry));
        assertTrue(snapshot.moveTargetPrecise());
        assertEquals(AgentMovementMode.MOVING, snapshot.mode());
    }

    @Test
    void snapshotReportsFarmAndPatrolState() {
        BotEntry entry = new BotEntry(null, null, null);
        AgentBotFarmAnchorStateRuntime.setFarmAnchor(entry, new Point(50, 60), 100000000);
        AgentBotPatrolStateRuntime.startPatrol(entry, 7, 100000001);
        AgentBotPatrolStateRuntime.setPatrolWanderTarget(entry, new Point(70, 80));

        AgentMovementSnapshot snapshot = AgentBotMovementStateRuntime.snapshot(entry);

        assertEquals(new Point(50, 60), snapshot.farmAnchor());
        assertEquals(100000000, snapshot.farmAnchorMapId());
        assertEquals(7, snapshot.patrolRegionId());
        assertEquals(100000001, snapshot.patrolMapId());
        assertEquals(new Point(70, 80), snapshot.patrolWanderTarget());
        assertEquals(AgentMovementMode.PATROLLING, snapshot.mode());
    }

    @Test
    void modeSummaryUsesCurrentMovementPriority() {
        BotEntry entry = new BotEntry(null, null, null);
        assertEquals(AgentMovementMode.STOPPED, AgentBotMovementStateRuntime.mode(entry));

        entry.following = true;
        assertEquals(AgentMovementMode.FOLLOWING, AgentBotMovementStateRuntime.mode(entry));

        AgentBotMoveTargetStateRuntime.setMoveTarget(entry, new Point(1, 1), false);
        assertEquals(AgentMovementMode.MOVING, AgentBotMovementStateRuntime.mode(entry));

        AgentBotFarmAnchorStateRuntime.setFarmAnchor(entry, new Point(2, 2), 100000000);
        assertEquals(AgentMovementMode.FARMING, AgentBotMovementStateRuntime.mode(entry));

        AgentBotPatrolStateRuntime.startPatrol(entry, 9, 100000000);
        assertEquals(AgentMovementMode.PATROLLING, AgentBotMovementStateRuntime.mode(entry));

        entry.grinding = true;
        assertEquals(AgentMovementMode.GRINDING, AgentBotMovementStateRuntime.mode(entry));
    }

    @Test
    void snapshotAllowsMissingPositions() {
        BotEntry entry = new BotEntry(null, null, null);

        AgentMovementSnapshot snapshot = AgentBotMovementStateRuntime.snapshot(entry);

        assertNull(snapshot.botPosition());
        assertNull(snapshot.ownerPosition());
    }

    @Test
    void movementProfileDefaultsAndStoresThroughAgentBoundary() {
        BotEntry entry = new BotEntry(null, null, null);

        assertEquals(AgentMovementProfile.fromCharacter(null), AgentBotMovementStateRuntime.movementProfile(entry));

        AgentMovementProfile profile = new AgentMovementProfile(147, 119);
        AgentBotMovementStateRuntime.setMovementProfile(entry, profile);
        assertEquals(new AgentMovementProfile(145, 115), AgentBotMovementStateRuntime.movementProfile(entry));

        AgentBotMovementStateRuntime.setMovementProfile(entry, null);
        assertEquals(AgentMovementProfile.fromCharacter(null), AgentBotMovementStateRuntime.movementProfile(entry));
    }

    @Test
    void refreshMovementProfileUsesCharacterStats() {
        Character bot = mock(Character.class);
        when(bot.getTotalMoveSpeedStat()).thenReturn(163);
        when(bot.getTotalJumpStat()).thenReturn(127);
        BotEntry entry = new BotEntry(bot, null, null);

        AgentBotMovementStateRuntime.refreshMovementProfile(entry, bot);

        assertEquals(new AgentMovementProfile(160, 127), AgentBotMovementStateRuntime.movementProfile(entry));
    }

    @Test
    void movementProfileFallbackHandlesMissingEntryForCombatCallers() {
        assertEquals(AgentMovementProfile.fromCharacter(null), AgentBotMovementStateRuntime.movementProfileOrCharacter(null, null));
    }

    @Test
    void moveDirectionClampsAndClearsThroughAgentBoundary() {
        BotEntry entry = new BotEntry(null, null, null);

        assertEquals(0, AgentBotMovementStateRuntime.moveDirection(entry));
        assertFalse(AgentBotMovementStateRuntime.hasMoveDirection(entry));

        AgentBotMovementStateRuntime.setMoveDirection(entry, 25);
        assertEquals(1, AgentBotMovementStateRuntime.moveDirection(entry));
        assertTrue(AgentBotMovementStateRuntime.hasMoveDirection(entry));

        AgentBotMovementStateRuntime.setMoveDirection(entry, -10);
        assertEquals(-1, AgentBotMovementStateRuntime.moveDirection(entry));

        AgentBotMovementStateRuntime.clearMoveDirection(entry);
        assertEquals(0, AgentBotMovementStateRuntime.moveDirection(entry));
        assertFalse(AgentBotMovementStateRuntime.hasMoveDirection(entry));
    }

    @Test
    void facingDirectionClampsThroughAgentBoundary() {
        BotEntry entry = new BotEntry(null, null, null);

        assertEquals(1, AgentBotMovementStateRuntime.facingDirection(entry));
        assertEquals(1, AgentBotMovementStateRuntime.facingDirectionSign(entry));

        AgentBotMovementStateRuntime.setFacingDirection(entry, -3);
        assertEquals(-1, AgentBotMovementStateRuntime.facingDirection(entry));
        assertEquals(-1, AgentBotMovementStateRuntime.facingDirectionSign(entry));

        AgentBotMovementStateRuntime.setFacingDirection(entry, 0);
        assertEquals(1, AgentBotMovementStateRuntime.facingDirection(entry));
        assertEquals(1, AgentBotMovementStateRuntime.facingDirectionSign(entry));
    }

    @Test
    void physicalMovementStatusReadsThroughAgentBoundary() {
        BotEntry entry = new BotEntry(null, null, null);

        assertFalse(AgentBotMovementStateRuntime.inAir(entry));
        assertTrue(AgentBotMovementStateRuntime.grounded(entry));
        assertFalse(AgentBotMovementStateRuntime.climbing(entry));
        assertTrue(AgentBotMovementStateRuntime.notClimbing(entry));
        assertFalse(AgentBotMovementStateRuntime.downJumpPending(entry));
        assertFalse(AgentBotMovementStateRuntime.crouching(entry));
        assertFalse(AgentBotMovementStateRuntime.hasDownJumpPending(entry));
        assertFalse(AgentBotMovementStateRuntime.hasDownJumpGracePeriod(entry));
        assertFalse(AgentBotMovementStateRuntime.wasMovingX(entry));
        assertEquals(0, AgentBotMovementStateRuntime.movementVelocityX(entry));
        assertEquals(0, AgentBotMovementStateRuntime.movementVelocityY(entry));
        assertFalse(AgentBotMovementStateRuntime.hasMovementVelocity(entry));

        entry.inAir = true;
        entry.climbing = true;
        entry.setDownJumpPending(true);
        entry.crouching = true;
        entry.setDownJumpGracePeriodMs(100L);
        AgentBotMovementStateRuntime.setWasMovingX(entry, true);
        entry.movementVelX = 12;
        entry.movementVelY = -3;

        assertTrue(AgentBotMovementStateRuntime.inAir(entry));
        assertFalse(AgentBotMovementStateRuntime.grounded(entry));
        assertTrue(AgentBotMovementStateRuntime.climbing(entry));
        assertFalse(AgentBotMovementStateRuntime.notClimbing(entry));
        assertTrue(AgentBotMovementStateRuntime.downJumpPending(entry));
        assertTrue(AgentBotMovementStateRuntime.crouching(entry));
        assertTrue(AgentBotMovementStateRuntime.hasDownJumpPending(entry));
        assertTrue(AgentBotMovementStateRuntime.hasDownJumpGracePeriod(entry));
        assertEquals(100L, AgentBotMovementStateRuntime.downJumpGracePeriodMs(entry));
        assertTrue(AgentBotMovementStateRuntime.wasMovingX(entry));
        AgentBotMovementStateRuntime.setWasMovingX(entry, false);
        assertFalse(AgentBotMovementStateRuntime.wasMovingX(entry));
        assertEquals(12, AgentBotMovementStateRuntime.movementVelocityX(entry));
        assertEquals(-3, AgentBotMovementStateRuntime.movementVelocityY(entry));
        assertTrue(AgentBotMovementStateRuntime.hasMovementVelocity(entry));

        AgentBotMovementStateRuntime.setMovementVelocity(entry, -20, 4);
        assertEquals(-20, AgentBotMovementStateRuntime.movementVelocityX(entry));
        assertEquals(4, AgentBotMovementStateRuntime.movementVelocityY(entry));
        assertEquals(-1, AgentBotMovementStateRuntime.facingDirection(entry));

        AgentBotMovementStateRuntime.setMovementVelocity(entry, 0, 0);
        assertEquals(0, AgentBotMovementStateRuntime.movementVelocityX(entry));
        assertEquals(0, AgentBotMovementStateRuntime.movementVelocityY(entry));
        assertEquals(-1, AgentBotMovementStateRuntime.facingDirection(entry));

        AgentBotMovementStateRuntime.setDownJumpPending(entry, false);
        AgentBotMovementStateRuntime.setDownJumpGracePeriodMs(entry, 0L);
        AgentBotMovementStateRuntime.setCrouching(entry, false);
        assertFalse(AgentBotMovementStateRuntime.downJumpPending(entry));
        assertFalse(AgentBotMovementStateRuntime.hasDownJumpPending(entry));
        assertEquals(0L, AgentBotMovementStateRuntime.downJumpGracePeriodMs(entry));
        assertFalse(AgentBotMovementStateRuntime.hasDownJumpGracePeriod(entry));
        assertFalse(AgentBotMovementStateRuntime.crouching(entry));

        AgentBotMovementStateRuntime.setInAir(entry, false);
        assertFalse(AgentBotMovementStateRuntime.inAir(entry));
        assertTrue(AgentBotMovementStateRuntime.grounded(entry));
        AgentBotMovementStateRuntime.setInAir(entry, true);
        assertTrue(AgentBotMovementStateRuntime.inAir(entry));
        assertFalse(AgentBotMovementStateRuntime.grounded(entry));
    }
}
