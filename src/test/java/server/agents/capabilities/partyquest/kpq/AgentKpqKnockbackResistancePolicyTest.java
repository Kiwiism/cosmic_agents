package server.agents.capabilities.partyquest.kpq;

import client.Character;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentKpqKnockbackResistancePolicyTest {
    @Test
    void liveRegistryLookupReturnsConfiguredStageOneResistance() {
        Character agent = mock(Character.class);
        when(agent.getId()).thenReturn(701);
        when(agent.getMapId()).thenReturn(AgentKpqDefinition.STAGE_1_MAP);
        AgentKpqSession session = new AgentKpqSession(
                AgentKpqSession.Mode.TEST_OBSERVATION, 1L, 9701, 3, 1_000L);
        session.addMember(701, AgentKpqMemberState.MemberType.AGENT);
        session.addMember(702, AgentKpqMemberState.MemberType.AGENT);
        session.addMember(703, AgentKpqMemberState.MemberType.AGENT);
        session.transition(AgentKpqSession.Phase.STAGE_1, 2_000L);
        AgentKpqSessionRegistry.registerComplete(session);
        try {
            assertEquals(50, AgentKpqKnockbackResistancePolicy.resistancePercent(agent));
        } finally {
            AgentKpqSessionRegistry.remove(session);
        }
    }

    @Test
    void appliesOnlyToAgentMembersInStageOneMapAndPhase() {
        assertEquals(50, AgentKpqKnockbackResistancePolicy.resistancePercent(
                AgentKpqDefinition.STAGE_1_MAP, AgentKpqSession.Phase.STAGE_1,
                AgentKpqMemberState.MemberType.AGENT, 50));
        assertEquals(0, AgentKpqKnockbackResistancePolicy.resistancePercent(
                AgentKpqDefinition.STAGE_1_MAP, AgentKpqSession.Phase.STAGE_1,
                AgentKpqMemberState.MemberType.HUMAN, 50));
        assertEquals(0, AgentKpqKnockbackResistancePolicy.resistancePercent(
                AgentKpqDefinition.STAGE_2_MAP, AgentKpqSession.Phase.STAGE_2,
                AgentKpqMemberState.MemberType.AGENT, 50));
    }

    @Test
    void clampsConfiguredPercentage() {
        assertEquals(0, AgentKpqKnockbackResistancePolicy.resistancePercent(
                AgentKpqDefinition.STAGE_1_MAP, AgentKpqSession.Phase.STAGE_1,
                AgentKpqMemberState.MemberType.AGENT, -1));
        assertEquals(100, AgentKpqKnockbackResistancePolicy.resistancePercent(
                AgentKpqDefinition.STAGE_1_MAP, AgentKpqSession.Phase.STAGE_1,
                AgentKpqMemberState.MemberType.AGENT, 101));
    }
}
