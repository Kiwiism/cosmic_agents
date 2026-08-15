package server.agents.economy.integration.cosmic;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CosmicFarmSettlementServiceTest {
    @Test
    void namespacesSettlementIdempotencyByRun() {
        UUID firstRun = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID secondRun = UUID.fromString("00000000-0000-0000-0000-000000000002");

        String first = CosmicFarmSettlementService.idempotencyKey(firstRun, "session-1");

        assertEquals(first, CosmicFarmSettlementService.idempotencyKey(firstRun, "session-1"));
        assertNotEquals(first, CosmicFarmSettlementService.idempotencyKey(secondRun, "session-1"));
    }

    @Test
    void rejectsUnattributedSettlement() {
        assertThrows(IllegalStateException.class,
                () -> CosmicFarmSettlementService.idempotencyKey(null, "session-1"));
    }
}
