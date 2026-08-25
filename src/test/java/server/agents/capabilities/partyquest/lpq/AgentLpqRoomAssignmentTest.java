package server.agents.capabilities.partyquest.lpq;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentLpqRoomAssignmentTest {
    @Test
    void reservationsAreExclusiveAndExpireWithoutProgress() {
        AgentLpqRoomAssignment rooms = new AgentLpqRoomAssignment();
        assertTrue(rooms.reserve(922_010_501, 101, 1_000L));
        assertFalse(rooms.reserve(922_010_501, 102, 1_100L));
        assertEquals(101, rooms.owner(922_010_501));

        rooms.markProgress(922_010_501, 2_000L);
        rooms.releaseExpired(2_500L, 1_000L);
        assertEquals(101, rooms.owner(922_010_501));
        rooms.releaseExpired(3_001L, 1_000L);
        assertNull(rooms.owner(922_010_501));
    }

    @Test
    void completedRoomIsRememberedUntilStageReset() {
        AgentLpqRoomAssignment rooms = new AgentLpqRoomAssignment();
        rooms.reserve(922_010_506, 101, 0L);
        rooms.complete(922_010_506);
        assertTrue(rooms.completed(922_010_506));
        assertNull(rooms.owner(922_010_506));
        rooms.reset();
        assertFalse(rooms.completed(922_010_506));
    }
}
