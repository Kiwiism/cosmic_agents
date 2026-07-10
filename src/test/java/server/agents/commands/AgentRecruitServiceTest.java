package server.agents.commands;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.auth.AgentAuthorizationResult;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentRecruitServiceTest {
    @Test
    void recruitsOwnerlessAgentInLegacyOrder() {
        Character leader = character(1, "Leader", 10, 0);
        Character agent = character(2, "agent123", 20, 0);
        List<String> calls = new ArrayList<>();

        String error = AgentRecruitService.recruitAgent(
                leader.getId(),
                leader,
                "agent123",
                new AgentRecruitService.Hooks(
                        (agentName, world) -> {
                            calls.add("lookup:" + agentName + ":" + world);
                            return agent;
                        },
                        (tickLeader, resolvedAgent) -> {
                            calls.add("authorize:" + resolvedAgent.name());
                            return AgentAuthorizationResult.allowed(false);
                        },
                        (leaderCharId, tickLeader, tickAgent) -> calls.add("register:" + leaderCharId)));

        assertNull(error);
        assertEquals(List.of("lookup:agent123:0", "authorize:agent123", "register:1"), calls);
    }

    @Test
    void returnsLegacyMessageWhenOwnerlessAgentMissing() {
        Character leader = character(1, "Leader", 10, 0);

        String error = AgentRecruitService.recruitAgent(
                leader.getId(),
                leader,
                "agent123",
                new AgentRecruitService.Hooks(
                        (agentName, world) -> null,
                        (tickLeader, resolvedAgent) -> AgentAuthorizationResult.allowed(false),
                        (leaderCharId, tickLeader, tickAgent) -> { }));

        assertEquals("No ownerless bot named 'agent123' found.", error);
    }

    @Test
    void returnsAuthorizationFailureWithoutRegistering() {
        Character leader = character(1, "Leader", 10, 0);
        Character agent = character(2, "agent123", 20, 0);
        List<String> calls = new ArrayList<>();

        String error = AgentRecruitService.recruitAgent(
                leader.getId(),
                leader,
                "agent123",
                new AgentRecruitService.Hooks(
                        (agentName, world) -> agent,
                        (tickLeader, resolvedAgent) -> {
                            calls.add("authorize");
                            return AgentAuthorizationResult.denied("nope");
                        },
                        (leaderCharId, tickLeader, tickAgent) -> calls.add("register")));

        assertEquals("nope", error);
        assertEquals(List.of("authorize"), calls);
    }

    private static Character character(int id, String name, int accountId, int world) {
        Character character = mock(Character.class);
        when(character.getId()).thenReturn(id);
        when(character.getName()).thenReturn(name);
        when(character.getAccountID()).thenReturn(accountId);
        when(character.getWorld()).thenReturn(world);
        return character;
    }
}
