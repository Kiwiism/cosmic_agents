package server.agents.capabilities.partyquest.epq;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentEpqSessionTest {
    private AgentEpqSession registered;

    @AfterEach
    void cleanup() {
        AgentEpqSessionRegistry.remove(registered);
    }

    @Test
    void registryAcceptsFourToSixMembersAndKeepsIndexesPrivate() {
        registered = session(4);
        AgentEpqSessionRegistry.registerComplete(registered);
        assertSame(registered, AgentEpqSessionRegistry.forMember(4));
        assertSame(registered, AgentEpqSessionRegistry.forOperator(1));
        assertEquals(1, AgentEpqSessionRegistry.sessions().size());
        AgentEpqSessionRegistry.remove(registered);
        assertFalse(AgentEpqSessionRegistry.active(4));
    }

    @Test
    void rejectsAnIncompleteParty() {
        assertThrows(IllegalArgumentException.class,
                () -> AgentEpqSessionRegistry.registerComplete(session(3)));
    }

    @Test
    void transitionsForwardAndLeasesCoordination() {
        AgentEpqSession session = session(4);
        session.transition(AgentEpqSession.Phase.STAGE_ONE, 20L);
        session.transition(AgentEpqSession.Phase.ENTERING, 30L);
        assertEquals(AgentEpqSession.Phase.STAGE_ONE, session.phase());
        assertTrue(session.claimExecutionTick(1, 40L, 100L));
        assertFalse(session.claimExecutionTick(2, 50L, 100L));
        assertTrue(session.claimExecutionTick(2, 141L, 100L));
    }

    private static AgentEpqSession session(int memberCount) {
        AgentEpqSession session = new AgentEpqSession(AgentEpqSession.Mode.TEST_OBSERVATION, 7L, 1, 10L);
        for (int id = 1; id <= memberCount; id++) {
            session.addMember(id, AgentEpqMemberState.MemberType.AGENT);
        }
        if (memberCount > 0) session.setLeadership(1, 1);
        return session;
    }
}
