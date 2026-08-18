package server.agents.capabilities.partyquest.kpq;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentKpqSessionTest {
    @Test
    void firstMemberOwnsLeadershipAndOnlyCoordinatorTicksOncePerTimestamp() {
        AgentKpqSession session = new AgentKpqSession(
                AgentKpqSession.Mode.TEST_OBSERVATION, 7L, 100, 3, 1_000L);
        session.addMember(20, AgentKpqMemberState.MemberType.AGENT);
        session.addMember(10, AgentKpqMemberState.MemberType.AGENT);

        assertEquals(20, session.eventLeaderId());
        assertEquals(1, session.member(20).partyNumber());
        assertEquals(2, session.member(10).partyNumber());
        assertTrue(session.claimCoordinatorTick(20, 2_000L));
        assertFalse(session.claimCoordinatorTick(20, 2_000L));
        assertFalse(session.claimCoordinatorTick(10, 2_001L));
    }

    @Test
    void rotationReusesVacatedStablePartyNumber() {
        AgentKpqSession session = new AgentKpqSession(
                AgentKpqSession.Mode.TEST_OBSERVATION, 7L, 100, 4, 1_000L);
        session.addMember(1, AgentKpqMemberState.MemberType.AGENT);
        session.addMember(2, AgentKpqMemberState.MemberType.AGENT);
        session.addMember(3, AgentKpqMemberState.MemberType.AGENT);
        session.addMember(4, AgentKpqMemberState.MemberType.AGENT);
        session.removeMember(2);
        session.addMember(5, AgentKpqMemberState.MemberType.AGENT);
        assertEquals(2, session.member(5).partyNumber());
    }
}
