package server.agents.commands;

import client.Character;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.runtime.AgentRuntimeRegistry;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyString;

class AgentLifecycleCommandCoordinatorTest {
    @BeforeEach
    void clearRegistry() {
        AgentRuntimeRegistry.clear();
    }

    @Test
    void preservesMissingOwnerlessAgentRecruitReply() {
        Character leader = leader(6);

        try (MockedStatic<AgentRuntimeRegistry> registry = mockStatic(AgentRuntimeRegistry.class)) {
            registry.when(() -> AgentRuntimeRegistry.findUnclaimedOnlineAgentByName("agent123", 0))
                    .thenReturn(null);

            String result = AgentLifecycleCommandCoordinator.recruitAgent(
                    6,
                    leader,
                    "agent123",
                    (leaderId, recruitLeader, agent) -> {
                    });

            assertEquals("No ownerless bot named 'agent123' found.", result);
        }
    }

    @Test
    void dismissReturnsFalseWhenLeaderHasNoAgents() {
        assertFalse(AgentLifecycleCommandCoordinator.dismissAgent(6, "agent123", entry -> {
        }));
    }

    @Test
    void handlesRecruitCommandWithLegacyReply() {
        Character leader = leader(7);
        List<String> calls = new ArrayList<>();

        boolean handled = AgentLifecycleCommandCoordinator.handleRecruitCommand(
                leader,
                "recruit agent123",
                (leaderId, commandLeader, agentName) -> {
                    calls.add("recruit:" + leaderId + ":" + agentName);
                    return null;
                });

        assertTrue(handled);
        assertEquals(List.of("recruit:7:agent123"), calls);
        verify(leader).yellowMessage("Bot 'agent123' recruited!");
    }

    @Test
    void handlesTransferCommandWithLegacyReply() {
        Character leader = leader(8);
        List<String> calls = new ArrayList<>();

        boolean handled = AgentLifecycleCommandCoordinator.handleTransferCommand(
                leader,
                "transfer agent123 Bob",
                (leaderId, commandLeader, agentName, targetName) -> {
                    calls.add("transfer:" + leaderId + ":" + agentName + ":" + targetName);
                    return null;
                });

        assertTrue(handled);
        assertEquals(List.of("transfer:8:agent123:Bob"), calls);
        verify(leader).yellowMessage("Bot 'agent123' transferred to Bob.");
    }

    @Test
    void handlesDismissCommandWithLegacyReply() {
        Character leader = leader(9);
        List<String> calls = new ArrayList<>();

        boolean handled = AgentLifecycleCommandCoordinator.handleDismissCommand(
                leader,
                "dismiss agent123",
                (leaderId, agentName) -> {
                    calls.add("dismiss:" + leaderId + ":" + agentName);
                    return true;
                });

        assertTrue(handled);
        assertEquals(List.of("dismiss:9:agent123"), calls);
        verify(leader).yellowMessage("Bot 'agent123' disowned - now idle.");
    }

    @Test
    void returnsFalseForUnmatchedLifecycleCommand() {
        Character leader = leader(10);

        boolean handled = AgentLifecycleCommandCoordinator.handleDismissCommand(
                leader,
                "follow me",
                (leaderId, agentName) -> true);

        assertFalse(handled);
        verify(leader, never()).yellowMessage(anyString());
    }

    private static Character leader(int id) {
        Character leader = mock(Character.class);
        when(leader.getId()).thenReturn(id);
        return leader;
    }
}
