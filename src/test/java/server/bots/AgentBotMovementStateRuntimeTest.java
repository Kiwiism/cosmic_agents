package server.bots;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.capabilities.movement.AgentMovementMode;
import server.agents.capabilities.movement.AgentMovementSnapshot;
import server.agents.integration.AgentBotMovementStateRuntime;

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
        entry.moveTarget = new Point(100, 200);
        entry.moveTargetPrecise = true;

        AgentMovementSnapshot snapshot = AgentBotMovementStateRuntime.snapshot(entry);
        Point exposed = snapshot.moveTarget();
        exposed.x = 999;

        assertEquals(new Point(100, 200), snapshot.moveTarget());
        assertEquals(new Point(100, 200), entry.moveTarget);
        assertTrue(snapshot.moveTargetPrecise());
        assertEquals(AgentMovementMode.MOVING, snapshot.mode());
    }

    @Test
    void snapshotReportsFarmAndPatrolState() {
        BotEntry entry = new BotEntry(null, null, null);
        entry.farmAnchor = new Point(50, 60);
        entry.farmAnchorMapId = 100000000;
        entry.patrolRegionId = 7;
        entry.patrolMapId = 100000001;
        entry.patrolWanderTarget = new Point(70, 80);

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

        entry.moveTarget = new Point(1, 1);
        assertEquals(AgentMovementMode.MOVING, AgentBotMovementStateRuntime.mode(entry));

        entry.farmAnchor = new Point(2, 2);
        assertEquals(AgentMovementMode.FARMING, AgentBotMovementStateRuntime.mode(entry));

        entry.patrolRegionId = 9;
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
}
