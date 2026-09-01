package server.agents.capabilities.partyquest.opq;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AgentOpqLootLedgerTest {
    @Test
    void custodyCannotBeStolenOrUsedFromAnotherRoom() {
        AgentOpqLootLedger ledger = new AgentOpqLootLedger();
        int piece = AgentOpqDefinition.statuePiece(AgentOpqDefinition.Room.SEALED);
        assertTrue(ledger.reserve(piece, 7, AgentOpqDefinition.SEALED_MAP, 1));
        assertFalse(ledger.reserve(piece, 8, AgentOpqDefinition.SEALED_MAP, 2));
        assertTrue(ledger.canLoot(piece, 7, AgentOpqDefinition.SEALED_MAP));
        assertFalse(ledger.canLoot(piece, 7, AgentOpqDefinition.CENTER_MAP));
        assertFalse(ledger.canLoot(piece, 8, AgentOpqDefinition.SEALED_MAP));
        assertTrue(ledger.pickedUp(piece, 7, 3));
        assertFalse(ledger.canLoot(piece, 7, AgentOpqDefinition.SEALED_MAP));
        assertTrue(ledger.delivered(piece, 7, 4));
    }
}
