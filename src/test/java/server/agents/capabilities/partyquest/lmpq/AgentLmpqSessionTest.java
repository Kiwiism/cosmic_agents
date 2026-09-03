package server.agents.capabilities.partyquest.lmpq;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentLmpqSessionTest {
    @Test
    void rendezvousDefaultsToNineAndMayBeSelectedAsSixteenBeforeEntry() {
        AgentLmpqSession session = new AgentLmpqSession(
                AgentLmpqSession.Mode.TEST_OBSERVATION, 1L, 1, 3, 100L);
        assertEquals(AgentLmpqDefinition.RENDEZVOUS_ROOM, session.rendezvousRoom());
        session.setRendezvousRoom(AgentLmpqDefinition.CLEAR_ROOM);
        assertEquals(AgentLmpqDefinition.CLEAR_ROOM, session.rendezvousRoom());
        session.transition(AgentLmpqSession.Phase.FARMING, 101L);
        assertThrows(IllegalStateException.class,
                () -> session.setRendezvousRoom(AgentLmpqDefinition.RENDEZVOUS_ROOM));
    }

    @Test
    void agentExecutorLeaseCanFailOverButHumanCannotClaimIt() {
        AgentLmpqSession session = new AgentLmpqSession(
                AgentLmpqSession.Mode.HUMAN_LEADER, 1, 1, 3, 100);
        session.addMember(1, AgentLmpqMemberState.MemberType.HUMAN);
        session.addMember(2, AgentLmpqMemberState.MemberType.AGENT);
        session.addMember(3, AgentLmpqMemberState.MemberType.AGENT);
        session.setLeadership(1, 2);
        assertTrue(session.claimExecutionTick(2, 100, 10));
        assertFalse(session.claimExecutionTick(3, 105, 10));
        assertTrue(session.claimExecutionTick(3, 351, 10));
        assertFalse(session.claimExecutionTick(1, 602, 10));
    }

    @Test
    void phasesAdvanceMonotonically() {
        AgentLmpqSession session = new AgentLmpqSession(
                AgentLmpqSession.Mode.AUTONOMOUS, 1, 1, 3, 100);
        session.transition(AgentLmpqSession.Phase.FARMING, 110);
        session.transition(AgentLmpqSession.Phase.PREPARING, 120);
        assertEquals(AgentLmpqSession.Phase.FARMING, session.phase());
        session.transition(AgentLmpqSession.Phase.REGROUPING, 130);
        assertEquals(AgentLmpqSession.Phase.REGROUPING, session.phase());
    }
}
