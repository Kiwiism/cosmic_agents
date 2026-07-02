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

class AgentTransferCommandServiceTest {
    @Test
    void ignoresNonTransferMessages() {
        List<String> calls = new ArrayList<>();

        boolean handled = AgentTransferCommandService.handleTransferCommand(
                leader(7),
                "give me flaming feather",
                new AgentTransferCommandService.Hooks(
                        (leaderId, leader, agentName, targetName) -> {
                            calls.add("transfer:" + leaderId + ":" + agentName + ":" + targetName);
                            return null;
                        },
                        (leader, message) -> calls.add(message)));

        assertFalse(handled);
        assertTrue(calls.isEmpty());
    }

    @Test
    void transfersNamedAgentAndEmitsLegacySuccessMessage() {
        List<String> calls = new ArrayList<>();

        boolean handled = AgentTransferCommandService.handleTransferCommand(
                leader(7),
                "transfer agent123 to Bob",
                new AgentTransferCommandService.Hooks(
                        (leaderId, leader, agentName, targetName) -> {
                            calls.add("transfer:" + leaderId + ":" + agentName + ":" + targetName);
                            return null;
                        },
                        (leader, message) -> calls.add(message)));

        assertTrue(handled);
        assertEquals(List.of(
                "transfer:7:agent123:Bob",
                "Bot 'agent123' transferred to Bob."), calls);
    }

    @Test
    void forwardsLegacyTransferErrorMessage() {
        List<String> calls = new ArrayList<>();

        boolean handled = AgentTransferCommandService.handleTransferCommand(
                leader(9),
                "transfer agent123 Bob",
                new AgentTransferCommandService.Hooks(
                        (leaderId, leader, agentName, targetName) -> {
                            calls.add("transfer:" + leaderId + ":" + agentName + ":" + targetName);
                            return "Player 'Bob' not found in this map.";
                        },
                        (leader, message) -> calls.add(message)));

        assertTrue(handled);
        assertEquals(List.of(
                "transfer:9:agent123:Bob",
                "Player 'Bob' not found in this map."), calls);
    }

    private static Character leader(int id) {
        Character leader = mock(Character.class);
        when(leader.getId()).thenReturn(id);
        return leader;
    }
}
