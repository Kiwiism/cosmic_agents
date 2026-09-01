package server.agents.capabilities.partyquest.opq;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AgentOpqRoomLedgerTest {
    @Test
    void roomHasOneOwnerAndOnlyOwnerCanAdvanceIt() {
        AgentOpqRoomLedger ledger = new AgentOpqRoomLedger();
        assertTrue(ledger.claim(AgentOpqDefinition.Room.SEALED, 10, 100));
        assertFalse(ledger.claim(AgentOpqDefinition.Room.SEALED, 11, 101));
        assertFalse(ledger.advance(AgentOpqDefinition.Room.SEALED, 11,
                AgentOpqRoomLedger.State.ENTERED, 102));
        assertTrue(ledger.advance(AgentOpqDefinition.Room.SEALED, 10,
                AgentOpqRoomLedger.State.COMPLETE, 103));
        assertFalse(ledger.claim(AgentOpqDefinition.Room.SEALED, 10, 104));
    }

    @Test
    void expiryReleasesOnlyIncompleteWork() {
        AgentOpqRoomLedger ledger = new AgentOpqRoomLedger();
        ledger.claim(AgentOpqDefinition.Room.STORAGE, 1, 10);
        ledger.claim(AgentOpqDefinition.Room.LOBBY, 2, 10);
        ledger.advance(AgentOpqDefinition.Room.LOBBY, 2, AgentOpqRoomLedger.State.COMPLETE, 11);
        assertTrue(ledger.releaseExpired(30, 20));
        assertNull(ledger.lease(AgentOpqDefinition.Room.STORAGE));
        assertTrue(ledger.complete(AgentOpqDefinition.Room.LOBBY));
    }
}
