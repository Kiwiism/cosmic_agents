package server.agents.economy.market;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MarketRoomAllocatorTest {
    @Test
    void fillsLowerNumberedRoomBeforeOpeningTheNextRoom() {
        MarketRoomAllocator allocator = new MarketRoomAllocator(910000001, 910000003, ignored -> 2);

        assertEquals(910000001, allocator.roomFor("agent-1"));
        assertEquals(910000001, allocator.roomFor("agent-2"));
        assertEquals(910000002, allocator.roomFor("agent-3"));
        assertEquals(910000002, allocator.roomFor("agent-4"));
        assertEquals(910000003, allocator.roomFor("agent-5"));
    }

    @Test
    void releaseMakesTheEarliestRoomAvailableAgain() {
        MarketRoomAllocator allocator = new MarketRoomAllocator(910000001, 910000002, ignored -> 1);
        allocator.roomFor("agent-1");
        allocator.roomFor("agent-2");

        allocator.release("agent-1");

        assertEquals(910000001, allocator.roomFor("agent-3"));
        assertEquals(1, allocator.assignedTo(910000001));
    }
}
