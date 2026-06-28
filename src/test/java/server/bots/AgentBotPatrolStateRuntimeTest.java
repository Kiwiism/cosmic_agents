package server.bots;

import org.junit.jupiter.api.Test;
import server.agents.integration.AgentBotPatrolStateRuntime;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentBotPatrolStateRuntimeTest {
    @Test
    void adaptsPatrolRegionAndWanderStateWithDefensiveCopies() {
        BotEntry entry = new BotEntry(null, null, null);
        Point wander = new Point(100, 200);

        AgentBotPatrolStateRuntime.startPatrol(entry, 7, 100000000);
        AgentBotPatrolStateRuntime.setPatrolWanderTarget(entry, wander);
        wander.x = 999;

        assertTrue(AgentBotPatrolStateRuntime.hasPatrolRegion(entry));
        assertEquals(7, AgentBotPatrolStateRuntime.patrolRegionId(entry));
        assertEquals(100000000, AgentBotPatrolStateRuntime.patrolMapId(entry));
        assertEquals(new Point(100, 200), AgentBotPatrolStateRuntime.patrolWanderTarget(entry));

        Point exposed = AgentBotPatrolStateRuntime.patrolWanderTarget(entry);
        exposed.y = 999;

        assertEquals(new Point(100, 200), AgentBotPatrolStateRuntime.patrolWanderTarget(entry));
    }

    @Test
    void clearingPatrolResetsRegionMapAndWanderTarget() {
        BotEntry entry = new BotEntry(null, null, null);

        AgentBotPatrolStateRuntime.startPatrol(entry, 7, 100000000);
        AgentBotPatrolStateRuntime.setPatrolWanderTarget(entry, new Point(100, 200));
        AgentBotPatrolStateRuntime.clearPatrol(entry);

        assertFalse(AgentBotPatrolStateRuntime.hasPatrolRegion(entry));
        assertEquals(-1, AgentBotPatrolStateRuntime.patrolRegionId(entry));
        assertEquals(-1, AgentBotPatrolStateRuntime.patrolMapId(entry));
        assertNull(AgentBotPatrolStateRuntime.patrolWanderTarget(entry));
    }

    @Test
    void clearsOnlyWhenMapChanges() {
        BotEntry entry = new BotEntry(null, null, null);

        AgentBotPatrolStateRuntime.startPatrol(entry, 7, 100000000);

        assertFalse(AgentBotPatrolStateRuntime.clearPatrolIfMapChanged(entry, 100000000));
        assertTrue(AgentBotPatrolStateRuntime.hasPatrolRegion(entry));
        assertTrue(AgentBotPatrolStateRuntime.clearPatrolIfMapChanged(entry, 200000000));
        assertFalse(AgentBotPatrolStateRuntime.hasPatrolRegion(entry));
    }
}
