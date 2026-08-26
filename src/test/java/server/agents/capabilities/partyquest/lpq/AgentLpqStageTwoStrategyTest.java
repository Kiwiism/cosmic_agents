package server.agents.capabilities.partyquest.lpq;

import org.junit.jupiter.api.Test;
import server.maps.Reactor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentLpqStageTwoStrategyTest {
    @Test
    void selectsTwoAgentScoutsAndKeepsTheAgentLeaderWithTheWaitingParty() {
        List<AgentLpqMemberState> members = List.of(
                member(101, AgentLpqMemberState.MemberType.AGENT),
                member(102, AgentLpqMemberState.MemberType.AGENT),
                member(103, AgentLpqMemberState.MemberType.HUMAN),
                member(104, AgentLpqMemberState.MemberType.AGENT));

        assertEquals(List.of(102, 104),
                AgentLpqCoordinator.stageTwoScoutIds(members, 101, 2));
    }

    @Test
    void selectsAgentScoutsWhenTheEventLeaderIsHuman() {
        List<AgentLpqMemberState> members = List.of(
                member(201, AgentLpqMemberState.MemberType.HUMAN),
                member(202, AgentLpqMemberState.MemberType.AGENT),
                member(203, AgentLpqMemberState.MemberType.AGENT),
                member(204, AgentLpqMemberState.MemberType.AGENT));

        assertEquals(List.of(202, 203),
                AgentLpqCoordinator.stageTwoScoutIds(members, 201, 2));
    }

    @Test
    void fallsBackToTheAgentLeaderWhenNoOtherAgentCanScout() {
        List<AgentLpqMemberState> members = List.of(
                member(301, AgentLpqMemberState.MemberType.AGENT),
                member(302, AgentLpqMemberState.MemberType.HUMAN),
                member(303, AgentLpqMemberState.MemberType.HUMAN));

        assertEquals(List.of(301),
                AgentLpqCoordinator.stageTwoScoutIds(members, 301, 2));
    }

    @Test
    void targetsOnlyTheAuthoredStageTwoTrapBox() {
        Reactor ordinary = reactor(401, 2_202_003);
        Reactor trap = reactor(402, AgentLpqDefinition.STAGE_2_TRAP_REACTOR);

        assertEquals(List.of(trap),
                AgentLpqCoordinator.stageTwoTrapReactors(List.of(ordinary, trap)));
    }

    private static AgentLpqMemberState member(int id, AgentLpqMemberState.MemberType type) {
        return new AgentLpqMemberState(id, type);
    }

    private static Reactor reactor(int objectId, int reactorId) {
        Reactor reactor = mock(Reactor.class);
        when(reactor.getObjectId()).thenReturn(objectId);
        when(reactor.getId()).thenReturn(reactorId);
        return reactor;
    }
}
