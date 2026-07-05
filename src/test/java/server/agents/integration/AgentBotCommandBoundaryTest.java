package server.agents.integration;

import org.junit.jupiter.api.Test;
import server.agents.commands.AgentNamedCommandTarget;
import server.agents.commands.AgentTargetedCommandMatch;
import server.agents.commands.AgentTransferCommand;
import server.bots.BotEntry;

import static org.mockito.Mockito.mock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AgentBotCommandBoundaryTest {
    @Test
    void transferCommandPreservesParsedNames() {
        AgentTransferCommand command = new AgentTransferCommand("agent123", "Admin");

        assertEquals("agent123", command.botName());
        assertEquals("Admin", command.targetName());
    }

    @Test
    void targetedCommandMatchPreservesTargetAndFeedback() {
        AgentTargetedCommandMatch<?> match = new AgentTargetedCommandMatch<>(null, "follow me", "not found");

        assertNull(match.entry());
        assertEquals("follow me", match.commandText());
        assertEquals("not found", match.feedbackMessage());
    }

    @Test
    void commandTargetPreservesEntryAndName() {
        BotEntry entry = mock(BotEntry.class);
        AgentNamedCommandTarget<BotEntry> target = new AgentNamedCommandTarget<>(entry, "agent123");

        assertEquals(entry, target.entry());
        assertEquals("agent123", target.name());
    }
}
