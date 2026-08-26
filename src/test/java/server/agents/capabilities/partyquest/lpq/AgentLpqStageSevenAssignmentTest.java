package server.agents.capabilities.partyquest.lpq;

import org.junit.jupiter.api.Test;

import java.awt.Point;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AgentLpqStageSevenAssignmentTest {
    @Test
    void selectsTwoRangedNonleadersForTopAndLeavesFourAtBottom() {
        List<AgentLpqMemberState> members = List.of(
                member(101, AgentLpqMemberState.MemberType.AGENT),
                member(102, AgentLpqMemberState.MemberType.AGENT),
                member(103, AgentLpqMemberState.MemberType.AGENT),
                member(104, AgentLpqMemberState.MemberType.AGENT),
                member(105, AgentLpqMemberState.MemberType.AGENT),
                member(106, AgentLpqMemberState.MemberType.AGENT));

        List<Integer> top = AgentLpqCoordinator.stageSevenTopMemberIds(
                members, Set.of(101, 102, 103, 106)::contains, 101);

        assertEquals(List.of(102, 103), top);
        assertEquals(Set.of(101, 104, 105, 106), members.stream()
                .map(AgentLpqMemberState::characterId)
                .filter(id -> !top.contains(id)).collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    void keepsTwoQualifiedAgentsOnTopBeforeAQualifiedHuman() {
        List<AgentLpqMemberState> members = List.of(
                member(201, AgentLpqMemberState.MemberType.AGENT),
                member(202, AgentLpqMemberState.MemberType.AGENT),
                member(203, AgentLpqMemberState.MemberType.AGENT),
                member(204, AgentLpqMemberState.MemberType.AGENT),
                member(205, AgentLpqMemberState.MemberType.AGENT),
                member(206, AgentLpqMemberState.MemberType.HUMAN));

        assertEquals(List.of(202, 201), AgentLpqCoordinator.stageSevenTopMemberIds(
                members, Set.of(201, 202, 206)::contains, 201));
    }

    @Test
    void sendsTheTwoQualifiedBoxWackersTop() {
        List<AgentLpqMemberState> members = List.of(
                member(301, AgentLpqMemberState.MemberType.AGENT),
                member(302, AgentLpqMemberState.MemberType.AGENT),
                member(303, AgentLpqMemberState.MemberType.AGENT),
                member(304, AgentLpqMemberState.MemberType.AGENT),
                member(305, AgentLpqMemberState.MemberType.AGENT),
                member(306, AgentLpqMemberState.MemberType.AGENT));

        assertEquals(List.of(302, 305), AgentLpqCoordinator.stageSevenTopMemberIds(
                members, Set.of(302, 305)::contains, 301));
    }

    @Test
    void mapsEveryAuthoredTriggerToItsOppositeReachableFiringLedge() {
        assertEquals(new Point(-240, -990),
                AgentLpqCoordinator.stageSevenFiringAnchor(new Point(228, -1_037)));
        assertEquals(new Point(-240, -1_263),
                AgentLpqCoordinator.stageSevenFiringAnchor(new Point(230, -1_263)));
        assertEquals(new Point(-240, -1_469),
                AgentLpqCoordinator.stageSevenFiringAnchor(new Point(230, -1_535)));
        assertNull(AgentLpqCoordinator.stageSevenFiringAnchor(new Point(0, 0)));
    }

    private static AgentLpqMemberState member(
            int id, AgentLpqMemberState.MemberType memberType) {
        return new AgentLpqMemberState(id, memberType);
    }
}
