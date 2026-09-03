package server.agents.capabilities.partyquest.lmpq;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentLmpqTestServiceTest {
    @Test
    void parsesPartySizeRendezvousAndSeed() {
        assertEquals(new AgentLmpqTestService.StartOptions(3, 9, 123L),
                AgentLmpqTestService.startOptions(new String[]{"start", "3", "9", "123"}, 1L));
        assertEquals(new AgentLmpqTestService.StartOptions(5, 16, 456L),
                AgentLmpqTestService.startOptions(new String[]{"start", "5", "16", "456"}, 1L));
        assertThrows(IllegalArgumentException.class,
                () -> AgentLmpqTestService.startOptions(new String[]{"start", "2"}, 1L));
        assertThrows(IllegalArgumentException.class,
                () -> AgentLmpqTestService.startOptions(new String[]{"start", "3", "10"}, 1L));
    }

    @Test
    void memberRecordsInitialRoomRouteAndDistinctAssignments() {
        AgentLmpqMemberState member = new AgentLmpqMemberState(
                10, AgentLmpqMemberState.MemberType.AGENT);
        member.observeRoom(4);
        member.observeRoom(4);
        member.observeRoom(8);
        member.assignTargetRoom(6);
        member.clearTargetRoom();
        member.assignTargetRoom(6);
        member.assignTargetRoom(7);
        assertEquals(4, member.initialRoom());
        assertEquals(java.util.List.of(4, 8), member.route());
        assertEquals(java.util.List.of(6, 7), member.assignments());
    }
}
