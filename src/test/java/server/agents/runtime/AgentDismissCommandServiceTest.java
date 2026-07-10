package server.agents.runtime;

import server.agents.commands.AgentDismissCommandService;
import client.Character;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentDismissCommandServiceTest {
    @Test
    void ignoresNonDismissMessages() {
        List<String> calls = new ArrayList<>();

        boolean handled = AgentDismissCommandService.handleDismissCommand(
                leader(7),
                "follow me",
                new AgentDismissCommandService.Hooks(
                        (leaderId, agentName) -> {
                            calls.add("dismiss:" + leaderId + ":" + agentName);
                            return true;
                        },
                        (leader, message) -> calls.add(message)));

        assertFalse(handled);
        assertTrue(calls.isEmpty());
    }

    @Test
    void dismissesNamedAgentAndEmitsLegacySuccessMessage() {
        List<String> calls = new ArrayList<>();

        boolean handled = AgentDismissCommandService.handleDismissCommand(
                leader(7),
                "dismiss agent123",
                new AgentDismissCommandService.Hooks(
                        (leaderId, agentName) -> {
                            calls.add("dismiss:" + leaderId + ":" + agentName);
                            return true;
                        },
                        (leader, message) -> calls.add(message)));

        assertTrue(handled);
        assertEquals(List.of(
                "dismiss:7:agent123",
                "Bot 'agent123' disowned - now idle."), calls);
    }

    @Test
    void supportsLegacyDismissAliasesAndEmitsFailureMessage() {
        List<String> calls = new ArrayList<>();

        boolean handled = AgentDismissCommandService.handleDismissCommand(
                leader(9),
                "please release agent123",
                new AgentDismissCommandService.Hooks(
                        (leaderId, agentName) -> {
                            calls.add("dismiss:" + leaderId + ":" + agentName);
                            return false;
                        },
                        (leader, message) -> calls.add(message)));

        assertTrue(handled);
        assertEquals(List.of(
                "dismiss:9:agent123",
                "No bot named 'agent123' in your group."), calls);
    }

    private static Character leader(int id) {
        Character leader = mock(Character.class);
        when(leader.getId()).thenReturn(id);
        return leader;
    }
}
