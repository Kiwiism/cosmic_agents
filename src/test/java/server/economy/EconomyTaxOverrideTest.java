package server.economy;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class EconomyTaxOverrideTest {
    @Test
    void computesIndependentBuyerAndSellerTaxesInBasisPoints() {
        EconomyTaxOverride tax = new EconomyTaxOverride(250, 500);

        assertEquals(250, tax.buyerTax(10_000));
        assertEquals(500, tax.sellerTax(10_000));
        assertEquals(0, tax.buyerTax(39));
    }

    @Test
    void operationContextScopesTaxWithoutLeakingToOrdinaryGameplay() {
        assertNull(EconomyOperationContext.currentMetadata().taxOverride());
        EconomyTaxOverride tax = new EconomyTaxOverride(100, 200);
        EconomyOperationMetadata metadata = new EconomyOperationMetadata(UUID.randomUUID(),
                Instant.EPOCH, "decision", null, "config", "catalog", "purchase",
                true, true, tax);

        EconomyOperationContext.with(metadata,
                () -> assertEquals(tax, EconomyOperationContext.currentMetadata().taxOverride()));

        assertNull(EconomyOperationContext.currentMetadata().taxOverride());
    }
}
