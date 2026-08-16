package server.agents.economy.integration.cosmic;

import org.junit.jupiter.api.Test;
import server.agents.economy.scenario.EconomyEngineConfig;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConfiguredEconomyTaxPolicyTest {
    @Test
    void cosmicDefaultNeverOverridesNativeTax() {
        EconomyEngineConfig.Tax config = config("COSMIC_DEFAULT", 0, 0);
        assertNull(new ConfiguredEconomyTaxPolicy(config).at(Instant.parse("2026-01-01T00:00:00Z")));
    }

    @Test
    void configuredPolicyAppliesLogicalTimeChanges() {
        EconomyEngineConfig.Tax config = config("CONFIGURED", 100, 200);
        EconomyEngineConfig.TaxChange change = new EconomyEngineConfig.TaxChange();
        change.effectiveAt = "2026-02-01T00:00:00Z";
        change.buyerRateBasisPoints = 300; change.sellerRateBasisPoints = 400;
        config.scheduledChanges = List.of(change);
        ConfiguredEconomyTaxPolicy policy = new ConfiguredEconomyTaxPolicy(config);

        assertEquals(100, policy.at(Instant.parse("2026-01-31T23:59:59Z")).buyerBasisPoints());
        assertEquals(400, policy.at(Instant.parse("2026-02-01T00:00:00Z")).sellerBasisPoints());
    }

    private static EconomyEngineConfig.Tax config(String policy, int buyer, int seller) {
        EconomyEngineConfig.Tax config = new EconomyEngineConfig.Tax();
        config.enabled = true; config.policy = policy;
        config.buyerRateBasisPoints = buyer; config.sellerRateBasisPoints = seller;
        config.maximumRateBasisPoints = 10_000; config.scheduledChanges = List.of();
        return config;
    }
}
