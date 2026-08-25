package server.agents.capabilities.partyquest.lpq;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentLpqSessionTest {
    @Test
    void ownsLeadershipLeaseAndStageEightOrder() {
        AgentLpqSession session = new AgentLpqSession(
                AgentLpqSession.Mode.TEST_OBSERVATION, 17L, 900, 6, 1_000L);
        session.addMember(101, AgentLpqMemberState.MemberType.AGENT);
        session.addMember(102, AgentLpqMemberState.MemberType.AGENT);
        session.addMember(103, AgentLpqMemberState.MemberType.AGENT);
        session.addMember(104, AgentLpqMemberState.MemberType.AGENT);
        session.addMember(105, AgentLpqMemberState.MemberType.AGENT);
        session.addMember(106, AgentLpqMemberState.MemberType.AGENT);
        session.setLeadership(101, 102);

        assertTrue(session.claimExecutionTick(102, 1_000L, 3_000L));
        assertFalse(session.claimExecutionTick(103, 2_000L, 3_000L));
        assertEquals(5, session.stage8Combination().size());
        var first = session.stage8Combination();
        var firstAssignments = session.stage8Assignments(java.util.List.of(101, 102, 103, 104, 105));
        session.advanceStage8(2_001L);
        assertTrue(AgentLpqCombinationOrder.oneMover(first, session.stage8Combination()));
        var secondAssignments = session.stage8Assignments(java.util.List.of(101, 102, 103, 104, 105));
        long movers = firstAssignments.entrySet().stream()
                .filter(entry -> !entry.getValue().equals(secondAssignments.get(entry.getKey()))).count();
        assertEquals(1L, movers);
    }

    @Test
    void rejectsFiveMemberSessionForSixMemberRequestOverflow() {
        AgentLpqSession session = new AgentLpqSession(
                AgentLpqSession.Mode.TEST_OBSERVATION, 1L, 900, 5, 0L);
        for (int id = 1; id <= 5; id++) session.addMember(id, AgentLpqMemberState.MemberType.AGENT);
        assertThrows(IllegalStateException.class,
                () -> session.addMember(6, AgentLpqMemberState.MemberType.AGENT));
    }
}
