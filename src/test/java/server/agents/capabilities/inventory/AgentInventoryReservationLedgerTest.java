package server.agents.capabilities.inventory;

import org.junit.jupiter.api.Test;
import server.agents.capabilities.contracts.AgentDisposition;
import server.agents.capabilities.contracts.AgentInventoryReservation;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentInventoryReservationLedgerTest {
    @Test
    void aggregatesReservationsAndExpiresThem() {
        AgentInventoryReservationLedger ledger = new AgentInventoryReservationLedger();
        ledger.reserve(new AgentInventoryReservation("quest", 4000000, 3,
                AgentDisposition.QUEST_RESERVE, "quest", "objective", 100, 1_000));
        ledger.reserve(new AgentInventoryReservation("trade", 4000000, 2,
                AgentDisposition.TRADE_RESERVE, "trade", "offer", 20, 0));

        assertEquals(5, ledger.reservedQuantity(4000000, 999));
        assertEquals(2, ledger.reservedQuantity(4000000, 1_000));
        assertEquals(1, ledger.reservations(1_000).size());
    }
}
