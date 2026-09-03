package server.agents.capabilities.partyquest.ppq;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentPpqDefinitionTest {
    @Test
    void describesTheAuthoredLinearRouteAndSideRooms() {
        assertEquals(3, AgentPpqDefinition.nextPortalId(AgentPpqDefinition.ENTRY_MAP));
        assertEquals(1, AgentPpqDefinition.nextPortalId(AgentPpqDefinition.MEDAL_MAP));
        assertEquals(2, AgentPpqDefinition.nextPortalId(AgentPpqDefinition.DECK_ONE_MAP));
        assertEquals(2, AgentPpqDefinition.nextPortalId(AgentPpqDefinition.DECK_TWO_MAP));
        assertEquals(1, AgentPpqDefinition.nextPortalId(AgentPpqDefinition.DOOR_MAP));
        assertTrue(AgentPpqDefinition.isEventMap(AgentPpqDefinition.CHEST_ONE_MAP));
        assertTrue(AgentPpqDefinition.isEventMap(AgentPpqDefinition.BOSS_MAP));
        assertFalse(AgentPpqDefinition.isEventMap(AgentPpqDefinition.RECRUIT_MAP));
        assertEquals("ppq", AgentPpqLobbyProfile.profile().questKey());
        assertEquals(AgentPpqDefinition.RECRUIT_MAP, AgentPpqLobbyProfile.profile().mapId());
    }

    @Test
    void sessionKeepsChestPolicyAndHumanLeadershipIndependent() {
        AgentPpqSession session = new AgentPpqSession(
                AgentPpqSession.Mode.HUMAN_LEADER, 7L, 99, true, 1L);
        session.addMember(99, AgentPpqMemberState.MemberType.HUMAN);
        for (int id = 100; id < 105; id++) session.addMember(id, AgentPpqMemberState.MemberType.AGENT);
        session.setLeadership(99, 100);

        assertEquals(99, session.eventLeaderId());
        assertEquals(100, session.executionAgentId());
        assertTrue(session.skipChestRooms());
    }
}
