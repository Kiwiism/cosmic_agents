package server.agents.integration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AgentBotCommandBoundaryTest {
    @Test
    void transferCommandPreservesParsedNames() {
        AgentBotTransferCommand command = new AgentBotTransferCommand("agent123", "Admin");

        assertEquals("agent123", command.botName());
        assertEquals("Admin", command.targetName());
    }

    @Test
    void targetedCommandMatchPreservesTargetAndFeedback() {
        AgentBotTargetedCommandMatch match = new AgentBotTargetedCommandMatch(null, "follow me", "not found");

        assertNull(match.entry());
        assertEquals("follow me", match.commandText());
        assertEquals("not found", match.feedbackMessage());
    }
}
