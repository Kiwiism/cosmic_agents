package server.economy;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class EconomyOperationContextTest {
    @Test
    void attributesOnlyOperationsInsideTheLexicalSimulationScope() {
        UUID runId = UUID.randomUUID();
        EconomyOperationMetadata metadata = new EconomyOperationMetadata(runId, Instant.EPOCH,
                "decision-1", null, "config-1", "catalog-1", "QUEST_REQUIREMENT", true, false);

        EconomyOperation attributed = EconomyOperationContext.with(metadata,
                () -> EconomyOperation.create(EconomyOperationKind.SHOP_BUY, 1, null, "buy"));
        EconomyOperation ordinary = EconomyOperation.create(EconomyOperationKind.SHOP_BUY, 2, null, "buy");

        assertEquals(runId, attributed.metadata().runId());
        assertEquals("QUEST_REQUIREMENT", attributed.metadata().reasonCode());
        assertNull(ordinary.metadata().runId());
    }
}
