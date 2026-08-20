package server.agents.capabilities.partyquest.kpq;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentKpqSessionRegistryAtomicTest {
    @Test
    void collidingMemberRejectsWholeSecondSessionWithoutPartialIndexes() {
        AgentKpqSession first = session(10, 1, 2, 3);
        AgentKpqSession second = session(20, 3, 4, 5);
        AgentKpqSessionRegistry.registerComplete(first);
        try {
            assertThrows(IllegalStateException.class,
                    () -> AgentKpqSessionRegistry.registerComplete(second));
            assertTrue(AgentKpqSessionRegistry.forOperator(20) == null);
            assertTrue(AgentKpqSessionRegistry.forMember(4) == null);
            assertTrue(AgentKpqSessionRegistry.forMember(5) == null);
        } finally {
            AgentKpqSessionRegistry.remove(first);
            AgentKpqSessionRegistry.remove(second);
        }
    }

    @Test
    void lobbyPolicyStartsWithTwoLobbiesAndOneHumanReservation() {
        org.junit.jupiter.api.Assertions.assertEquals(2, AgentKpqLobbyPolicy.maxLobbies());
    }

    private static AgentKpqSession session(int operator, int... members) {
        AgentKpqSession session = new AgentKpqSession(
                AgentKpqSession.Mode.PRODUCTION, operator, operator, members.length, 1_000L);
        for (int member : members) session.addMember(member, AgentKpqMemberState.MemberType.AGENT);
        return session;
    }
}
