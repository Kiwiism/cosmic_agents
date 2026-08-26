package server.agents.capabilities.partyquest.lpq;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentLpqObserverWarpSelectorTest {
    @Test
    void slotOneIsAlwaysLeaderAndNamedRolesRemainSelectable() {
        AgentLpqSession session = session();
        session.member(30).assign(AgentLpqMemberState.Role.TELEPORT_RUNNER, 922_010_501);
        session.member(40).assign(AgentLpqMemberState.Role.DARK_SIGHT_RUNNER, 922_010_506);

        assertEquals(List.of(30), ids(AgentLpqTestService.warpCandidates(
                session, new String[]{"1"})));
        assertEquals(List.of(30), ids(AgentLpqTestService.warpCandidates(
                session, new String[]{"leader"})));
        assertEquals(List.of(40), ids(AgentLpqTestService.warpCandidates(
                session, new String[]{"darksight"})));
        assertEquals(List.of(30), ids(AgentLpqTestService.warpCandidates(
                session, new String[]{"teleport"})));
    }

    @Test
    void stageTwoScoutsCanBeSelectedInStableOrder() {
        AgentLpqSession session = session();

        assertEquals(List.of(10, 20), ids(AgentLpqTestService.warpCandidates(
                session, new String[]{"scout"})));
    }

    private static AgentLpqSession session() {
        AgentLpqSession session = new AgentLpqSession(
                AgentLpqSession.Mode.TEST_OBSERVATION, 1L, 1, 6, 100L);
        for (int id : List.of(10, 20, 30, 40, 50, 60)) {
            session.addMember(id, AgentLpqMemberState.MemberType.AGENT);
        }
        session.setLeadership(30, 30);
        return session;
    }

    private static List<Integer> ids(List<AgentLpqMemberState> members) {
        return members.stream().map(AgentLpqMemberState::characterId).toList();
    }
}
