package server.bots;

import org.junit.jupiter.api.Test;
import server.agents.integration.AgentBotClimbStateRuntime;
import server.maps.Rope;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentBotClimbStateRuntimeTest {
    @Test
    void adaptsClimbRopeAndDirectionState() {
        BotEntry entry = new BotEntry(null, null, null);
        Rope rope = new Rope(100, 20, 200, false);

        assertFalse(AgentBotClimbStateRuntime.climbing(entry));
        assertFalse(AgentBotClimbStateRuntime.hasClimbRope(entry));
        assertNull(AgentBotClimbStateRuntime.climbRope(entry));
        assertFalse(AgentBotClimbStateRuntime.hasClimbVerticalDirection(entry));

        AgentBotClimbStateRuntime.setClimbingOnRope(entry, rope);
        AgentBotClimbStateRuntime.setClimbVerticalDirection(entry, -5);

        assertTrue(AgentBotClimbStateRuntime.climbing(entry));
        assertTrue(AgentBotClimbStateRuntime.hasClimbRope(entry));
        assertSame(rope, AgentBotClimbStateRuntime.climbRope(entry));
        assertEquals(-1, AgentBotClimbStateRuntime.climbVerticalDirection(entry));
        assertTrue(AgentBotClimbStateRuntime.hasClimbVerticalDirection(entry));

        AgentBotClimbStateRuntime.setClimbVerticalDirection(entry, 0);

        assertFalse(AgentBotClimbStateRuntime.hasClimbVerticalDirection(entry));
    }
}
