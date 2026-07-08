package server.agents.capabilities.movement;

import server.agents.runtime.AgentRuntimeEntry;

import org.junit.jupiter.api.Test;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentMoveTargetStateRuntimeTest {
    @Test
    void adaptsMoveTargetStateWithDefensiveCopies() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        Point target = new Point(100, 200);

        AgentMoveTargetStateRuntime.setMoveTarget(entry, target, false);
        target.x = 999;

        assertTrue(AgentMoveTargetStateRuntime.hasMoveTarget(entry));
        assertFalse(AgentMoveTargetStateRuntime.isPrecise(entry));
        assertEquals(new Point(100, 200), AgentMoveTargetStateRuntime.moveTarget(entry));
        assertTrue(AgentMoveTargetStateRuntime.moveTargetEquals(entry, new Point(100, 200)));

        Point exposed = AgentMoveTargetStateRuntime.moveTarget(entry);
        exposed.y = 999;

        assertEquals(new Point(100, 200), AgentMoveTargetStateRuntime.moveTarget(entry));
    }

    @Test
    void adaptsPreciseAndNormalArrivalChecks() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        AgentMoveTargetStateRuntime.setMoveTarget(entry, new Point(100, 100), false);

        assertTrue(AgentMoveTargetStateRuntime.hasReachedMoveTarget(entry, new Point(125, 100), 25));
        assertFalse(AgentMoveTargetStateRuntime.hasReachedMoveTarget(entry, new Point(126, 100), 25));

        AgentMoveTargetStateRuntime.setPreciseMoveTarget(entry, new Point(100, 100));

        assertTrue(AgentMoveTargetStateRuntime.isPrecise(entry));
        assertTrue(AgentMoveTargetStateRuntime.hasReachedMoveTarget(entry, new Point(108, 100), 25));
        assertFalse(AgentMoveTargetStateRuntime.hasReachedMoveTarget(entry, new Point(109, 100), 25));
    }

    @Test
    void clearingTargetAlsoClearsPreciseFlag() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        AgentMoveTargetStateRuntime.setPreciseMoveTarget(entry, new Point(100, 100));
        AgentMoveTargetStateRuntime.clearMoveTarget(entry);

        assertFalse(AgentMoveTargetStateRuntime.hasMoveTarget(entry));
        assertFalse(AgentMoveTargetStateRuntime.isPrecise(entry));
        assertNull(AgentMoveTargetStateRuntime.moveTarget(entry));

        AgentMoveTargetStateRuntime.setMoveTarget(entry, null, true);

        assertFalse(AgentMoveTargetStateRuntime.hasMoveTarget(entry));
        assertFalse(AgentMoveTargetStateRuntime.isPrecise(entry));
    }
}
