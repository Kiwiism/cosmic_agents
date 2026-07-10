package server.agents.commands;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class AgentCommandParserTest {
    @Test
    void shouldParseTransferCommands() {
        AgentCommandParser.AgentTransferCommand command =
                AgentCommandParser.matchTransferCommand("transfer Jason to Bob");

        assertNotNull(command);
        assertEquals("Jason", command.agentName());
        assertEquals("Bob", command.targetName());
    }

    @Test
    void shouldStillAllowTransferWithoutTo() {
        AgentCommandParser.AgentTransferCommand command =
                AgentCommandParser.matchTransferCommand("transfer Jason Bob");

        assertNotNull(command);
        assertEquals("Jason", command.agentName());
        assertEquals("Bob", command.targetName());
    }

    @Test
    void shouldNotTreatGivePhrasesAsTransfers() {
        assertNull(AgentCommandParser.matchTransferCommand("give Jason Bob"));
        assertNull(AgentCommandParser.matchTransferCommand("give me flaming feather"));
        assertNull(AgentCommandParser.matchTransferCommand("give flaming feather"));
    }

    @Test
    void shouldResolveTargetByPrefix() {
        AgentCommandParser.TargetedAgentMatch<TestTarget> match =
                AgentCommandParser.resolveTargetedAgent(List.of(new TestTarget("Jason"), new TestTarget("Bob")), "Ja pots?");

        assertEquals("Jason", match.target().name());
        assertEquals("pots?", match.commandText());
        assertNull(match.feedbackMessage());
    }

    @Test
    void shouldResolveTargetBySlot() {
        AgentCommandParser.TargetedAgentMatch<TestTarget> match =
                AgentCommandParser.resolveTargetedAgent(List.of(new TestTarget("Jason"), new TestTarget("Bob")), "2 follow Alice");

        assertEquals("Bob", match.target().name());
        assertEquals("follow Alice", match.commandText());
        assertNull(match.feedbackMessage());
    }

    @Test
    void shouldIgnoreOverflowingNumericSlot() {
        AgentCommandParser.TargetedAgentMatch<TestTarget> match =
                AgentCommandParser.resolveTargetedAgent(
                        List.of(new TestTarget("Jason")),
                        "999999999999999999999999 follow Alice");

        assertNull(match.target());
        assertNull(match.commandText());
        assertNull(match.feedbackMessage());
    }

    @Test
    void shouldReturnLegacyFeedbackForAmbiguousPrefix() {
        AgentCommandParser.TargetedAgentMatch<TestTarget> match =
                AgentCommandParser.resolveTargetedAgent(List.of(new TestTarget("Jane"), new TestTarget("Jason")), "Ja yes");

        assertNull(match.target());
        assertNull(match.commandText());
        assertEquals("Ambiguous bot prefix 'Ja': 1: Jane, 2: Jason. Use the full name or a slot number.",
                match.feedbackMessage());
    }

    private record TestTarget(String name) implements AgentCommandTarget {
    }
}
