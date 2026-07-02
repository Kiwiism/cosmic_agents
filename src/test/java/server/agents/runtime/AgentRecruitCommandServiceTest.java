package server.agents.runtime;

import client.Character;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentRecruitCommandServiceTest {
    @Test
    void ignoresNonRecruitMessages() {
        List<String> calls = new ArrayList<>();

        boolean handled = AgentRecruitCommandService.handleRecruitCommand(
                leader(7),
                "follow me",
                new AgentRecruitCommandService.Hooks(
                        (leaderId, leader, agentName) -> {
                            calls.add("recruit:" + leaderId + ":" + agentName);
                            return null;
                        },
                        (leader, message) -> calls.add(message)));

        assertFalse(handled);
        assertTrue(calls.isEmpty());
    }

    @Test
    void recruitsNamedAgentAndEmitsLegacySuccessMessage() {
        List<String> calls = new ArrayList<>();

        boolean handled = AgentRecruitCommandService.handleRecruitCommand(
                leader(7),
                "recruit agent123",
                new AgentRecruitCommandService.Hooks(
                        (leaderId, leader, agentName) -> {
                            calls.add("recruit:" + leaderId + ":" + agentName);
                            return null;
                        },
                        (leader, message) -> calls.add(message)));

        assertTrue(handled);
        assertEquals(List.of(
                "recruit:7:agent123",
                "Bot 'agent123' recruited!"), calls);
    }

    @Test
    void supportsLegacyRecruitAliasesAndForwardsFailureMessage() {
        List<String> calls = new ArrayList<>();

        boolean handled = AgentRecruitCommandService.handleRecruitCommand(
                leader(9),
                "please claim agent123",
                new AgentRecruitCommandService.Hooks(
                        (leaderId, leader, agentName) -> {
                            calls.add("recruit:" + leaderId + ":" + agentName);
                            return "No ownerless bot named 'agent123' found.";
                        },
                        (leader, message) -> calls.add(message)));

        assertTrue(handled);
        assertEquals(List.of(
                "recruit:9:agent123",
                "No ownerless bot named 'agent123' found."), calls);
    }

    private static Character leader(int id) {
        Character leader = mock(Character.class);
        when(leader.getId()).thenReturn(id);
        return leader;
    }
}
