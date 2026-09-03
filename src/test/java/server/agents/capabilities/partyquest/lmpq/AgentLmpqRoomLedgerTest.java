package server.agents.capabilities.partyquest.lmpq;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentLmpqRoomLedgerTest {
    @Test
    void reservationIsAtomicAndOnlyOwnerCanWork() {
        AgentLmpqRoomLedger ledger = new AgentLmpqRoomLedger();
        assertTrue(ledger.reserve(6, 10, 100));
        assertFalse(ledger.reserve(6, 11, 101));
        assertFalse(ledger.beginWork(6, 11, 102));
        assertTrue(ledger.beginWork(6, 10, 103));
        assertEquals(AgentLmpqRoomLedger.State.WORKING, ledger.room(6).state());
    }

    @Test
    void expiredWorkAndHumanOccupancyReleaseSafely() {
        AgentLmpqRoomLedger ledger = new AgentLmpqRoomLedger();
        ledger.reserve(7, 10, 100);
        assertEquals(1, ledger.releaseExpired(121, 20));
        assertEquals(AgentLmpqRoomLedger.State.AVAILABLE, ledger.room(7).state());
        ledger.humanOccupied(7, 122);
        assertFalse(ledger.reserve(7, 10, 123));
        ledger.humanLeft(7, 124);
        assertTrue(ledger.reserve(7, 10, 125));
    }

    @Test
    void depletedRoomNeverReturnsToAvailable() {
        AgentLmpqRoomLedger ledger = new AgentLmpqRoomLedger();
        ledger.reserve(3, 10, 1);
        ledger.depleted(3, 10, 2);
        ledger.releaseOwner(10, 3);
        assertEquals(AgentLmpqRoomLedger.State.DEPLETED, ledger.room(3).state());
        assertFalse(ledger.reserve(3, 10, 4));
    }
}
