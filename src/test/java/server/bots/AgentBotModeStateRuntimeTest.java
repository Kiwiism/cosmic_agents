package server.bots;

import org.junit.jupiter.api.Test;
import server.agents.integration.AgentBotModeStateRuntime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentBotModeStateRuntimeTest {
    @Test
    void adaptsModeFlagsAndFollowTarget() {
        BotEntry entry = new BotEntry(null, null, null);

        assertFalse(AgentBotModeStateRuntime.following(entry));
        assertFalse(AgentBotModeStateRuntime.grinding(entry));
        assertEquals(0, AgentBotModeStateRuntime.followTargetId(entry));

        AgentBotModeStateRuntime.startFollowing(entry, 123);

        assertTrue(AgentBotModeStateRuntime.following(entry));
        assertFalse(AgentBotModeStateRuntime.grinding(entry));
        assertEquals(123, AgentBotModeStateRuntime.followTargetId(entry));

        AgentBotModeStateRuntime.startGrinding(entry);

        assertFalse(AgentBotModeStateRuntime.following(entry));
        assertTrue(AgentBotModeStateRuntime.grinding(entry));
        assertEquals(0, AgentBotModeStateRuntime.followTargetId(entry));

        AgentBotModeStateRuntime.stopMovementModes(entry);

        assertFalse(AgentBotModeStateRuntime.following(entry));
        assertFalse(AgentBotModeStateRuntime.grinding(entry));
        assertEquals(0, AgentBotModeStateRuntime.followTargetId(entry));
    }
}
