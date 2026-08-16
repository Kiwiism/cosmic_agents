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

    @Test
    void counterpartClassificationCanBeNarrowedWithoutLosingRunAttribution() {
        UUID runId = UUID.randomUUID();
        EconomyOperationMetadata metadata = new EconomyOperationMetadata(runId, Instant.EPOCH,
                "decision-2", null, "config-1", "catalog-1", "MARKET_CYCLE", true, false);

        EconomyOperation operation = EconomyOperationContext.with(metadata,
                () -> EconomyOperationContext.withParticipantFlags(true, true,
                        () -> EconomyOperation.create(EconomyOperationKind.PLAYER_SHOP_SALE,
                                1, 2, "sale")));

        assertEquals(runId, operation.metadata().runId());
        assertTrue(operation.metadata().primaryIsAgent());
        assertTrue(operation.metadata().secondaryIsAgent());
        assertFalse(EconomyOperationContext.currentMetadata().primaryIsAgent());
    }
}
