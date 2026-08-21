package server.agents.integration.cosmic;

import client.Character;
import client.Job;
import org.junit.jupiter.api.Test;
import server.agents.capabilities.partyquest.kpq.AgentKpqMemberState;
import server.agents.capabilities.partyquest.kpq.AgentKpqSession;
import server.agents.capabilities.partyquest.kpq.AgentKpqSessionRegistry;
import server.agents.runtime.AgentCommerceControlRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.activity.AgentActivityHostState;
import server.agents.runtime.activity.session.AgentActivityKind;
import server.agents.runtime.activity.world.AgentWorldContext;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CosmicAgentWorldContextFactoryTest {
    @Test
    void captureDoesNotRegisterOrMutateAgentCapabilityState() {
        Character agent = mock(Character.class);
        when(agent.getId()).thenReturn(27);
        when(agent.getName()).thenReturn("KiwiAgent");
        when(agent.getLevel()).thenReturn(15);
        when(agent.getJob()).thenReturn(Job.WARRIOR);
        when(agent.getMapId()).thenReturn(100_000_000);
        when(agent.getHp()).thenReturn(100);
        when(agent.getMaxHp()).thenReturn(100);
        when(agent.getMp()).thenReturn(50);
        when(agent.getMaxMp()).thenReturn(50);
        when(agent.getMeso()).thenReturn(1_000);
        when(agent.isAlive()).thenReturn(true);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        Set<String> before = entry.capabilityStates().registeredStateIds();

        AgentWorldContext context =
                CosmicAgentWorldContextFactory.capture(entry, agent, 1_000L);

        assertEquals(before, entry.capabilityStates().registeredStateIds());
        assertTrue(context.evidence().containsKey("captureMode"));
        assertEquals("read-only", context.evidence().get("captureMode"));
    }

    @Test
    void captureProjectsPartyQuestAndCompatibilityCommerceOwnership() {
        Character agent = agent(28);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        AgentKpqSession kpq = new AgentKpqSession(
                AgentKpqSession.Mode.TEST_OBSERVATION, 1L, 999, 3, 1_000L);
        kpq.addMember(28, AgentKpqMemberState.MemberType.AGENT);
        AgentKpqSessionRegistry.registerComplete(kpq);
        try {
            entry.capabilityStates().require(AgentActivityHostState.STATE_KEY)
                    .select("party-quest", AgentActivityKind.PARTY_QUEST, 1_000L);
            AgentWorldContext partyQuest =
                    CosmicAgentWorldContextFactory.capture(entry, agent, 1_100L);
            assertEquals(AgentActivityKind.PARTY_QUEST, partyQuest.currentActivityKind());
            assertEquals(kpq.sessionId(), partyQuest.currentSessionId());
        } finally {
            AgentKpqSessionRegistry.remove(kpq);
        }

        AgentCommerceControlRuntime.claim(28, "economy:test");
        try {
            entry.capabilityStates().require(AgentActivityHostState.STATE_KEY)
                    .select("commerce", AgentActivityKind.COMMERCE, 2_000L);
            AgentWorldContext commerce =
                    CosmicAgentWorldContextFactory.capture(entry, agent, 2_100L);
            assertEquals(AgentActivityKind.COMMERCE, commerce.currentActivityKind());
            assertTrue(commerce.currentSessionId().startsWith("commerce-control:28:"));
        } finally {
            AgentCommerceControlRuntime.releaseCharacter(28, "economy:test");
        }
    }

    private static Character agent(int id) {
        Character agent = mock(Character.class);
        when(agent.getId()).thenReturn(id);
        when(agent.getName()).thenReturn("Agent" + id);
        when(agent.getLevel()).thenReturn(15);
        when(agent.getJob()).thenReturn(Job.WARRIOR);
        when(agent.getMapId()).thenReturn(100_000_000);
        when(agent.getHp()).thenReturn(100);
        when(agent.getMaxHp()).thenReturn(100);
        when(agent.getMp()).thenReturn(50);
        when(agent.getMaxMp()).thenReturn(50);
        when(agent.getMeso()).thenReturn(1_000);
        when(agent.isAlive()).thenReturn(true);
        return agent;
    }
}
