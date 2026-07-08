package server.agents.capabilities.movement;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.runtime.AgentFarmAnchorStateRuntime;
import server.agents.runtime.AgentModeStateRuntime;
import server.agents.runtime.AgentPatrolStateRuntime;
import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentMovementStateRuntimeTest {
    @Test
    void snapshotCopiesFollowAndGrindFlagsAndPositions() {
        Character bot = mock(Character.class);
        Character owner = mock(Character.class);
        when(bot.getPosition()).thenReturn(new Point(10, 20));
        when(owner.getPosition()).thenReturn(new Point(30, 40));
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, owner, null);
        AgentModeStateRuntime.setFollowing(entry, true);
        AgentModeStateRuntime.setGrinding(entry, true);
        AgentModeStateRuntime.setFollowTargetId(entry, 123);

        AgentMovementSnapshot snapshot = AgentMovementStateRuntime.snapshot(entry);

        assertTrue(snapshot.following());
        assertTrue(snapshot.grinding());
        assertEquals(123, snapshot.followTargetId());
        assertEquals(new Point(10, 20), snapshot.botPosition());
        assertEquals(new Point(30, 40), snapshot.ownerPosition());
        assertEquals(AgentMovementMode.GRINDING, snapshot.mode());
    }

    @Test
    void snapshotDefensivelyCopiesMoveTarget() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        AgentMoveTargetStateRuntime.setPreciseMoveTarget(entry, new Point(100, 200));

        AgentMovementSnapshot snapshot = AgentMovementStateRuntime.snapshot(entry);
        Point exposed = snapshot.moveTarget();
        exposed.x = 999;

        assertEquals(new Point(100, 200), snapshot.moveTarget());
        assertEquals(new Point(100, 200), AgentMoveTargetStateRuntime.moveTarget(entry));
        assertTrue(snapshot.moveTargetPrecise());
        assertEquals(AgentMovementMode.MOVING, snapshot.mode());
    }

    @Test
    void snapshotReportsFarmAndPatrolState() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        AgentFarmAnchorStateRuntime.setFarmAnchor(entry, new Point(50, 60), 100000000);
        AgentPatrolStateRuntime.startPatrol(entry, 7, 100000001);
        AgentPatrolStateRuntime.setPatrolWanderTarget(entry, new Point(70, 80));

        AgentMovementSnapshot snapshot = AgentMovementStateRuntime.snapshot(entry);

        assertEquals(new Point(50, 60), snapshot.farmAnchor());
        assertEquals(100000000, snapshot.farmAnchorMapId());
        assertEquals(7, snapshot.patrolRegionId());
        assertEquals(100000001, snapshot.patrolMapId());
        assertEquals(new Point(70, 80), snapshot.patrolWanderTarget());
        assertEquals(AgentMovementMode.PATROLLING, snapshot.mode());
    }

    @Test
    void modeSummaryUsesCurrentMovementPriority() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        assertEquals(AgentMovementMode.STOPPED, AgentMovementStateRuntime.mode(entry));

        AgentModeStateRuntime.setFollowing(entry, true);
        assertEquals(AgentMovementMode.FOLLOWING, AgentMovementStateRuntime.mode(entry));

        AgentMoveTargetStateRuntime.setMoveTarget(entry, new Point(1, 1), false);
        assertEquals(AgentMovementMode.MOVING, AgentMovementStateRuntime.mode(entry));

        AgentFarmAnchorStateRuntime.setFarmAnchor(entry, new Point(2, 2), 100000000);
        assertEquals(AgentMovementMode.FARMING, AgentMovementStateRuntime.mode(entry));

        AgentPatrolStateRuntime.startPatrol(entry, 9, 100000000);
        assertEquals(AgentMovementMode.PATROLLING, AgentMovementStateRuntime.mode(entry));

        AgentModeStateRuntime.setGrinding(entry, true);
        assertEquals(AgentMovementMode.GRINDING, AgentMovementStateRuntime.mode(entry));
    }

    @Test
    void snapshotAllowsMissingPositions() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        AgentMovementSnapshot snapshot = AgentMovementStateRuntime.snapshot(entry);

        assertNull(snapshot.botPosition());
        assertNull(snapshot.ownerPosition());
    }

    @Test
    void movementProfileDefaultsAndStoresThroughAgentBoundary() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        assertEquals(AgentMovementProfile.fromCharacter(null), AgentMovementStateRuntime.movementProfile(entry));

        AgentMovementProfile profile = new AgentMovementProfile(147, 119);
        AgentMovementStateRuntime.setMovementProfile(entry, profile);
        assertEquals(new AgentMovementProfile(145, 115), AgentMovementStateRuntime.movementProfile(entry));

        AgentMovementStateRuntime.setMovementProfile(entry, null);
        assertEquals(AgentMovementProfile.fromCharacter(null), AgentMovementStateRuntime.movementProfile(entry));
    }

    @Test
    void refreshMovementProfileUsesCharacterStats() {
        Character bot = mock(Character.class);
        when(bot.getTotalMoveSpeedStat()).thenReturn(163);
        when(bot.getTotalJumpStat()).thenReturn(127);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);

        AgentMovementStateRuntime.refreshMovementProfile(entry, bot);

        assertEquals(new AgentMovementProfile(160, 127), AgentMovementStateRuntime.movementProfile(entry));
    }

    @Test
    void movementProfileFallbackHandlesMissingEntryForCombatCallers() {
        assertEquals(AgentMovementProfile.fromCharacter(null), AgentMovementStateRuntime.movementProfileOrCharacter(null, null));
    }

    @Test
    void moveDirectionClampsAndClearsThroughAgentBoundary() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        assertEquals(0, AgentMovementStateRuntime.moveDirection(entry));
        assertFalse(AgentMovementStateRuntime.hasMoveDirection(entry));

        AgentMovementStateRuntime.setMoveDirection(entry, 25);
        assertEquals(1, AgentMovementStateRuntime.moveDirection(entry));
        assertTrue(AgentMovementStateRuntime.hasMoveDirection(entry));

        AgentMovementStateRuntime.setMoveDirection(entry, -10);
        assertEquals(-1, AgentMovementStateRuntime.moveDirection(entry));

        AgentMovementStateRuntime.clearMoveDirection(entry);
        assertEquals(0, AgentMovementStateRuntime.moveDirection(entry));
        assertFalse(AgentMovementStateRuntime.hasMoveDirection(entry));
    }

    @Test
    void facingDirectionClampsThroughAgentBoundary() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        assertEquals(1, AgentMovementStateRuntime.facingDirection(entry));
        assertEquals(1, AgentMovementStateRuntime.facingDirectionSign(entry));

        AgentMovementStateRuntime.setFacingDirection(entry, -3);
        assertEquals(-1, AgentMovementStateRuntime.facingDirection(entry));
        assertEquals(-1, AgentMovementStateRuntime.facingDirectionSign(entry));

        AgentMovementStateRuntime.setFacingDirection(entry, 0);
        assertEquals(1, AgentMovementStateRuntime.facingDirection(entry));
        assertEquals(1, AgentMovementStateRuntime.facingDirectionSign(entry));
    }

    @Test
    void physicalMovementStatusReadsThroughAgentBoundary() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        assertFalse(AgentMovementStateRuntime.inAir(entry));
        assertTrue(AgentMovementStateRuntime.grounded(entry));
        assertFalse(AgentMovementStateRuntime.climbing(entry));
        assertTrue(AgentMovementStateRuntime.notClimbing(entry));
        assertFalse(AgentMovementStateRuntime.downJumpPending(entry));
        assertFalse(AgentMovementStateRuntime.crouching(entry));
        assertFalse(AgentMovementStateRuntime.hasDownJumpPending(entry));
        assertFalse(AgentMovementStateRuntime.hasDownJumpGracePeriod(entry));
        assertFalse(AgentMovementStateRuntime.wasMovingX(entry));
        assertEquals(0, AgentMovementStateRuntime.movementVelocityX(entry));
        assertEquals(0, AgentMovementStateRuntime.movementVelocityY(entry));
        assertFalse(AgentMovementStateRuntime.hasMovementVelocity(entry));

        AgentMovementStateRuntime.setInAir(entry, true);
        entry.climbState().setClimbingFlag(true);
        AgentMovementStateRuntime.setDownJumpPending(entry, true);
        AgentMovementStateRuntime.setCrouching(entry, true);
        AgentMovementStateRuntime.setDownJumpGracePeriodMs(entry, 100L);
        AgentMovementStateRuntime.setWasMovingX(entry, true);
        AgentMovementStateRuntime.setMovementVelocity(entry, 12, -3);

        assertTrue(AgentMovementStateRuntime.inAir(entry));
        assertFalse(AgentMovementStateRuntime.grounded(entry));
        assertTrue(AgentMovementStateRuntime.climbing(entry));
        assertFalse(AgentMovementStateRuntime.notClimbing(entry));
        assertTrue(AgentMovementStateRuntime.downJumpPending(entry));
        assertTrue(AgentMovementStateRuntime.crouching(entry));
        assertTrue(AgentMovementStateRuntime.hasDownJumpPending(entry));
        assertTrue(AgentMovementStateRuntime.hasDownJumpGracePeriod(entry));
        assertEquals(100L, AgentMovementStateRuntime.downJumpGracePeriodMs(entry));
        assertTrue(AgentMovementStateRuntime.wasMovingX(entry));
        AgentMovementStateRuntime.setWasMovingX(entry, false);
        assertFalse(AgentMovementStateRuntime.wasMovingX(entry));
        assertEquals(12, AgentMovementStateRuntime.movementVelocityX(entry));
        assertEquals(-3, AgentMovementStateRuntime.movementVelocityY(entry));
        assertTrue(AgentMovementStateRuntime.hasMovementVelocity(entry));

        AgentMovementStateRuntime.setMovementVelocity(entry, -20, 4);
        assertEquals(-20, AgentMovementStateRuntime.movementVelocityX(entry));
        assertEquals(4, AgentMovementStateRuntime.movementVelocityY(entry));
        assertEquals(-1, AgentMovementStateRuntime.facingDirection(entry));

        AgentMovementStateRuntime.setMovementVelocity(entry, 0, 0);
        assertEquals(0, AgentMovementStateRuntime.movementVelocityX(entry));
        assertEquals(0, AgentMovementStateRuntime.movementVelocityY(entry));
        assertEquals(-1, AgentMovementStateRuntime.facingDirection(entry));

        AgentMovementStateRuntime.setDownJumpPending(entry, false);
        AgentMovementStateRuntime.setDownJumpGracePeriodMs(entry, 0L);
        AgentMovementStateRuntime.setCrouching(entry, false);
        assertFalse(AgentMovementStateRuntime.downJumpPending(entry));
        assertFalse(AgentMovementStateRuntime.hasDownJumpPending(entry));
        assertEquals(0L, AgentMovementStateRuntime.downJumpGracePeriodMs(entry));
        assertFalse(AgentMovementStateRuntime.hasDownJumpGracePeriod(entry));
        assertFalse(AgentMovementStateRuntime.crouching(entry));

        AgentMovementStateRuntime.setInAir(entry, false);
        assertFalse(AgentMovementStateRuntime.inAir(entry));
        assertTrue(AgentMovementStateRuntime.grounded(entry));
        AgentMovementStateRuntime.setInAir(entry, true);
        assertTrue(AgentMovementStateRuntime.inAir(entry));
        assertFalse(AgentMovementStateRuntime.grounded(entry));
    }
}
