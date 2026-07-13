package server.agents.runtime;

import server.agents.capabilities.trade.AgentTransferService;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.auth.AgentAuthorizationResult;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentTransferServiceTest {
    @Test
    void transfersAgentInLegacyOrder() {
        Character leader = character(1, "Leader", 10);
        Character target = character(2, "Target", 20);
        Character agent = character(3, "agent123", 30);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, leader, null);
        List<AgentRuntimeEntry> entries = new ArrayList<>(List.of(entry));
        List<String> calls = new ArrayList<>();

        String error = AgentTransferService.transferAgent(
                leader.getId(),
                leader,
                "agent123",
                "Target",
                new AgentTransferService.Hooks(
                        leaderId -> entries,
                        (leaderId, agentName) -> entry,
                        (leaderId, removedEntry) -> entries.remove(removedEntry),
                        (tickLeader, targetName) -> target,
                        (tickTarget, resolvedAgent) -> {
                            calls.add("authorize:" + resolvedAgent.name());
                            return AgentAuthorizationResult.allowed(false);
                        },
                        tickEntry -> calls.add("cancel"),
                        tickEntry -> calls.add("stop"),
                        (leaderId, tickLeader, tickAgent) -> {
                            calls.add("register:" + leaderId);
                            return entry;
                        },
                        (registeredEntry, delayMs, action) -> {
                            calls.add("delay:" + delayMs);
                            action.run();
                        },
                        () -> 800L,
                        (tickAgent, text) -> calls.add("say:" + text),
                        () -> "ok!"));

        assertNull(error);
        assertEquals(List.of(
                "authorize:agent123",
                "cancel",
                "stop",
                "register:2",
                "delay:800",
                "say:ok!"), calls);
        assertEquals(List.of(), entries);
    }

    @Test
    void returnsLegacyErrorWhenLeaderHasNoAgents() {
        Character leader = character(1, "Leader", 10);

        String error = AgentTransferService.transferAgent(
                leader.getId(),
                leader,
                "agent123",
                "Target",
                new AgentTransferService.Hooks(
                        leaderId -> null,
                        (leaderId, agentName) -> null,
                        (leaderId, removedEntry) -> false,
                        (tickLeader, targetName) -> null,
                        (tickTarget, resolvedAgent) -> AgentAuthorizationResult.allowed(false),
                        tickEntry -> { },
                        tickEntry -> { },
                        (leaderId, tickLeader, tickAgent) -> null,
                        (registeredEntry, delayMs, action) -> { },
                        () -> 800L,
                        (tickAgent, text) -> { },
                        () -> "ok!"));

        assertEquals("You have no bots.", error);
    }

    private static Character character(int id, String name, int accountId) {
        Character character = mock(Character.class);
        when(character.getId()).thenReturn(id);
        when(character.getName()).thenReturn(name);
        when(character.getAccountID()).thenReturn(accountId);
        return character;
    }
}
