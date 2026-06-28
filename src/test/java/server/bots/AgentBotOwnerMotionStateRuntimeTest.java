package server.bots;

import org.junit.jupiter.api.Test;
import server.agents.integration.AgentBotOwnerMotionStateRuntime;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentBotOwnerMotionStateRuntimeTest {
    @Test
    void adaptsLastOwnerPositionWithDefensiveCopies() {
        BotEntry entry = new BotEntry(null, null, null);
        Point original = new Point(10, 20);

        assertNull(AgentBotOwnerMotionStateRuntime.lastOwnerPosition(entry));

        AgentBotOwnerMotionStateRuntime.rememberOwnerPosition(entry, original);
        original.x = 99;
        Point stored = AgentBotOwnerMotionStateRuntime.lastOwnerPosition(entry);

        assertEquals(new Point(10, 20), stored);
        assertNotSame(stored, AgentBotOwnerMotionStateRuntime.lastOwnerPosition(entry));
    }

    @Test
    void updatesObservedOwnerStepFromLastPosition() {
        BotEntry entry = new BotEntry(null, null, null);

        AgentBotOwnerMotionStateRuntime.updateObservedOwnerStep(entry, new Point(10, 20));

        assertEquals(0, AgentBotOwnerMotionStateRuntime.observedOwnerStepX(entry));
        assertEquals(0, AgentBotOwnerMotionStateRuntime.observedOwnerStepY(entry));
        assertFalse(AgentBotOwnerMotionStateRuntime.observedOwnerMoved(entry));
        assertTrue(AgentBotOwnerMotionStateRuntime.ownerMostlyIdle(entry));

        AgentBotOwnerMotionStateRuntime.rememberOwnerPosition(entry, new Point(10, 20));
        AgentBotOwnerMotionStateRuntime.updateObservedOwnerStep(entry, new Point(15, 18));

        assertEquals(5, AgentBotOwnerMotionStateRuntime.observedOwnerStepX(entry));
        assertEquals(-2, AgentBotOwnerMotionStateRuntime.observedOwnerStepY(entry));
        assertEquals(5, AgentBotOwnerMotionStateRuntime.maxObservedOwnerStep(entry));
        assertTrue(AgentBotOwnerMotionStateRuntime.observedOwnerMoved(entry));
        assertFalse(AgentBotOwnerMotionStateRuntime.ownerMostlyIdle(entry));

        AgentBotOwnerMotionStateRuntime.clearObservedOwnerStep(entry);

        assertFalse(AgentBotOwnerMotionStateRuntime.observedOwnerMoved(entry));
        assertTrue(AgentBotOwnerMotionStateRuntime.ownerMostlyIdle(entry));
    }
}
