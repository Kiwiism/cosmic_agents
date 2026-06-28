package server.bots;

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

        assertEquals(BotMovementProfile.fromCharacter(null), AgentBotMovementStateRuntime.movementProfile(entry));

        BotMovementProfile profile = new BotMovementProfile(147, 119);
        AgentBotMovementStateRuntime.setMovementProfile(entry, profile);
        assertEquals(new BotMovementProfile(145, 115), AgentBotMovementStateRuntime.movementProfile(entry));

        AgentBotMovementStateRuntime.setMovementProfile(entry, null);
        assertEquals(BotMovementProfile.fromCharacter(null), AgentBotMovementStateRuntime.movementProfile(entry));
    }

    @Test
    void refreshMovementProfileUsesCharacterStats() {
        Character bot = mock(Character.class);
        when(bot.getTotalMoveSpeedStat()).thenReturn(163);
        when(bot.getTotalJumpStat()).thenReturn(127);
        BotEntry entry = new BotEntry(bot, null, null);

        AgentBotMovementStateRuntime.refreshMovementProfile(entry, bot);

        assertEquals(new BotMovementProfile(160, 127), AgentBotMovementStateRuntime.movementProfile(entry));
    }
}
