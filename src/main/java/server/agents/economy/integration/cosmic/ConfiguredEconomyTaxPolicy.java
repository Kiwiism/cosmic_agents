package server.agents.economy.integration.cosmic;

import server.agents.economy.scenario.EconomyEngineConfig;
import server.economy.EconomyTaxOverride;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Null means Cosmic native tax; an override exists only for the explicit CONFIGURED policy. */
public final class ConfiguredEconomyTaxPolicy implements CosmicEconomyWorldAdapter.TaxPolicy {
    private final boolean configured;
    private final EconomyTaxOverride initial;
    private final List<Change> changes;

    public ConfiguredEconomyTaxPolicy(EconomyEngineConfig.Tax config) {
        Objects.requireNonNull(config);
        configured = config.enabled && "CONFIGURED".equals(config.policy);
        initial = configured ? new EconomyTaxOverride(config.buyerRateBasisPoints,
                config.sellerRateBasisPoints) : null;
        changes = configured ? config.scheduledChanges.stream().map(change -> new Change(
                        Instant.parse(change.effectiveAt), new EconomyTaxOverride(
                        change.buyerRateBasisPoints, change.sellerRateBasisPoints)))
                .sorted(Comparator.comparing(Change::effectiveAt)).toList() : List.of();
    }

    @Override
    public EconomyTaxOverride at(Instant logicalAt) {
        if (!configured) return null;
        EconomyTaxOverride result = initial;
        for (Change change : changes) {
            if (change.effectiveAt().isAfter(logicalAt)) break;
            result = change.rates();
        }
        return result;
    }

    private record Change(Instant effectiveAt, EconomyTaxOverride rates) { }
}
