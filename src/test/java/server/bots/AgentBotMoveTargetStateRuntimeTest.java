package server.bots;

import org.junit.jupiter.api.Test;
import server.agents.integration.AgentBotMoveTargetStateRuntime;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentBotMoveTargetStateRuntimeTest {
    @Test
    void adaptsMoveTargetStateWithDefensiveCopies() {
        BotEntry entry = new BotEntry(null, null, null);
        Point target = new Point(100, 200);

        AgentBotMoveTargetStateRuntime.setMoveTarget(entry, target, false);
        target.x = 999;

        assertTrue(AgentBotMoveTargetStateRuntime.hasMoveTarget(entry));
        assertFalse(AgentBotMoveTargetStateRuntime.isPrecise(entry));
        assertEquals(new Point(100, 200), AgentBotMoveTargetStateRuntime.moveTarget(entry));
        assertTrue(AgentBotMoveTargetStateRuntime.moveTargetEquals(entry, new Point(100, 200)));

        Point exposed = AgentBotMoveTargetStateRuntime.moveTarget(entry);
        exposed.y = 999;

        assertEquals(new Point(100, 200), AgentBotMoveTargetStateRuntime.moveTarget(entry));
    }

    @Test
    void adaptsPreciseAndNormalArrivalChecks() {
        BotEntry entry = new BotEntry(null, null, null);

        AgentBotMoveTargetStateRuntime.setMoveTarget(entry, new Point(100, 100), false);

        assertTrue(AgentBotMoveTargetStateRuntime.hasReachedMoveTarget(entry, new Point(125, 100), 25));
        assertFalse(AgentBotMoveTargetStateRuntime.hasReachedMoveTarget(entry, new Point(126, 100), 25));

        AgentBotMoveTargetStateRuntime.setPreciseMoveTarget(entry, new Point(100, 100));

        assertTrue(AgentBotMoveTargetStateRuntime.isPrecise(entry));
        assertTrue(AgentBotMoveTargetStateRuntime.hasReachedMoveTarget(entry, new Point(108, 100), 25));
        assertFalse(AgentBotMoveTargetStateRuntime.hasReachedMoveTarget(entry, new Point(109, 100), 25));
    }

    @Test
    void clearingTargetAlsoClearsPreciseFlag() {
        BotEntry entry = new BotEntry(null, null, null);

        AgentBotMoveTargetStateRuntime.setPreciseMoveTarget(entry, new Point(100, 100));
        AgentBotMoveTargetStateRuntime.clearMoveTarget(entry);

        assertFalse(AgentBotMoveTargetStateRuntime.hasMoveTarget(entry));
        assertFalse(AgentBotMoveTargetStateRuntime.isPrecise(entry));
        assertNull(AgentBotMoveTargetStateRuntime.moveTarget(entry));

        AgentBotMoveTargetStateRuntime.setMoveTarget(entry, null, true);

        assertFalse(AgentBotMoveTargetStateRuntime.hasMoveTarget(entry));
        assertFalse(AgentBotMoveTargetStateRuntime.isPrecise(entry));
    }
}
