package server.agents.economy.scenario;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Set;

/** Fail-fast validation for settings whose violation would corrupt a run. */
public final class EconomyConfigValidator {
    private static final double DISTRIBUTION_TOLERANCE = 0.000_001d;
    private static final Set<String> CLOCK_MODES = Set.of(
            "REALTIME", "ACCELERATED", "MAX_THROUGHPUT", "REPLAY");
    private static final Set<String> NPC_ACCESS_MODES = Set.of(
            "REMOTE_FROM_FREE_MARKET", "PHYSICAL_TRAVEL", "DISABLED");
    private static final Set<String> NPC_ACCESS_SCOPES = Set.of("ALL_GAME", "VICTORIA_ONLY");
    private static final Set<String> ACTIVITY_MODES = Set.of("RULE_EXACT");

    private EconomyConfigValidator() {
    }

    public static void validate(EconomyEngineConfig config) {
        require(config != null, "Economy configuration is required");
        require(config.schemaVersion == 1, "Unsupported economy configuration schemaVersion");
        requireSections(config);

        requireText(config.scenario.id, "scenario.id");
        require(config.scenario.targetLogicalDays >= 0, "targetLogicalDays must be non-negative");
        require(config.scenario.checkpointEveryLogicalHours > 0,
                "checkpointEveryLogicalHours must be positive");

        requireEnum(config.clock.mode, CLOCK_MODES, "clock.mode");
        parseInstant(config.clock.logicalStart, "clock.logicalStart");
        require(config.clock.maximumEventsPerBatch > 0,
                "maximumEventsPerBatch must be positive");
        requireText(config.catalog.bundleId, "catalog.bundleId");
        require(config.catalog.adaptiveResources != null && !config.catalog.adaptiveResources.isEmpty(),
                "catalog.adaptiveResources cannot be empty");
        require(config.catalog.sqlResources != null && !config.catalog.sqlResources.isEmpty(),
                "catalog.sqlResources cannot be empty");
        require(config.catalog.mechanicalResources != null && !config.catalog.mechanicalResources.isEmpty(),
                "catalog.mechanicalResources cannot be empty");

        require(config.world.channelId > 0, "channelId must be positive");
        require(config.world.freeMarketEntranceMapId == 910000000,
                "Free Market entrance must be map 910000000");
        require(config.world.firstFreeMarketRoomMapId == 910000001
                        && config.world.lastFreeMarketRoomMapId == 910000022,
                "Free Market room range must be 910000001 through 910000022");
        require(config.world.activityRegions != null && !config.world.activityRegions.isEmpty(),
                "At least one activity region is required");

        validatePopulation(config.population);
        requireEnum(config.npcCommerce.accessMode, NPC_ACCESS_MODES, "npcCommerce.accessMode");
        requireEnum(config.npcCommerce.accessScope, NPC_ACCESS_SCOPES, "npcCommerce.accessScope");
        parseDuration(config.npcCommerce.logicalServiceDelay, "npcCommerce.logicalServiceDelay");
        if ("REMOTE_FROM_FREE_MARKET".equals(config.npcCommerce.accessMode)) {
            require(config.npcCommerce.preserveRealNpcStock
                            && config.npcCommerce.preserveRealPrices
                            && config.npcCommerce.preserveRealRestrictions,
                    "Remote NPC access must preserve stock, prices, and restrictions");
            require(config.npcCommerce.recordOriginalNpcAndMap,
                    "Remote NPC access must record the original NPC and map");
        }

        requireEnum(config.activity.executionMode, ACTIVITY_MODES, "activity.executionMode");
        require(config.activity.medianSessionMinutes > 0,
                "medianSessionMinutes must be positive");
        require(config.activity.maximumSessionMinutes >= config.activity.medianSessionMinutes,
                "maximumSessionMinutes must not be below the median");
        require(!config.activity.visibleWhileActive,
                "Offscreen activity agents cannot remain visible in the Free Market");

        validateMarket(config.market);
        validateTax(config.tax);
        validateAmbient(config.ambient);
        requireText(config.persistence.provider, "persistence.provider");
        requireText(config.persistence.database, "persistence.database");
        require(config.persistence.retainMovementDebugDays >= 0,
                "retainMovementDebugDays must be non-negative");
    }

    private static void requireSections(EconomyEngineConfig config) {
        require(config.scenario != null && config.clock != null && config.catalog != null
                        && config.world != null
                        && config.population != null && config.npcCommerce != null
                        && config.activity != null && config.market != null && config.tax != null
                        && config.seasonalRules != null && config.quests != null
                        && config.scrolling != null && config.chairs != null
                        && config.ambient != null && config.persistence != null
                        && config.humanReadiness != null,
                "Every top-level economy configuration section is required");
    }

    private static void validatePopulation(EconomyEngineConfig.Population population) {
        require(population.initialAgents >= 0, "initialAgents must be non-negative");
        require(population.maximumAgents >= population.initialAgents,
                "maximumAgents must not be below initialAgents");
        require(population.growth != null, "population.growth is required");
        require("FIXED_INTERVAL".equals(population.growth.type),
                "Only FIXED_INTERVAL population growth is currently supported");
        require(population.growth.amount > 0, "growth amount must be positive");
        require(population.growth.everyLogicalDays > 0,
                "growth interval must be positive");
        validateDistribution(population.classDistribution, "classDistribution");
        validateDistribution(population.activityDistribution, "activityDistribution");
        require(population.merchantParticipation != null,
                "merchantParticipation is required");
        requireFraction(population.merchantParticipation.willingSellerFraction,
                "willingSellerFraction");
        requireFraction(population.merchantParticipation.dedicatedMerchantFraction,
                "dedicatedMerchantFraction");
        require(population.merchantParticipation.dedicatedMerchantFraction
                        <= population.merchantParticipation.willingSellerFraction,
                "dedicated merchants must be a subset of willing sellers");
    }

    private static void validateMarket(EconomyEngineConfig.Market market) {
        require("PLAYER_SHOP".equals(market.venue),
                "PLAYER_SHOP is the only supported initial venue");
        require(market.maximumStallsPerAgent == 1,
                "Exactly one stall per agent is a non-negotiable invariant");
        require(market.maximumListingsPerStall > 0 && market.maximumListingsPerStall <= 16,
                "PlayerShop listing capacity must be between 1 and 16");
        require(!market.hiredMerchantsEnabled,
                "Hired merchants require a separate permit and escrow milestone");
        require(!market.globalSearchAllowed,
                "Agents must physically observe stalls rather than use global search");
        require(market.minimumRoomsPerTrip > 0
                        && market.maximumRoomsPerTrip >= market.minimumRoomsPerTrip
                        && market.maximumRoomsPerTrip <= 22,
                "Room scan range must be ordered within the 22 FM rooms");
        parseDuration(market.maximumListingDuration, "market.maximumListingDuration");
        parseDuration(market.minimumRepriceInterval, "market.minimumRepriceInterval");
        require(market.maximumReprices >= 0, "maximumReprices must be non-negative");
        require(market.useCosmicTransactions,
                "Live market settlement must use Cosmic transactions");
        require(market.rejectSelfTrade, "Self trading must be rejected");
    }

    private static void validateTax(EconomyEngineConfig.Tax tax) {
        require(tax.maximumRateBasisPoints >= 0 && tax.maximumRateBasisPoints <= 10_000,
                "maximum tax rate must be between 0 and 10000 basis points");
        require(tax.buyerRateBasisPoints >= 0
                        && tax.buyerRateBasisPoints <= tax.maximumRateBasisPoints,
                "buyer tax exceeds configured bounds");
        require(tax.sellerRateBasisPoints >= 0
                        && tax.sellerRateBasisPoints <= tax.maximumRateBasisPoints,
                "seller tax exceeds configured bounds");
        require(tax.scheduledChanges != null, "tax.scheduledChanges is required");
    }

    private static void validateAmbient(EconomyEngineConfig.Ambient ambient) {
        require(ambient.maximumConsecutiveActions >= 0,
                "maximumConsecutiveActions must be non-negative");
        require(ambient.modules != null && !ambient.modules.isEmpty(),
                "ambient modules are required");
        for (Map.Entry<String, EconomyEngineConfig.AmbientModule> entry : ambient.modules.entrySet()) {
            requireText(entry.getKey(), "ambient module name");
            require(entry.getValue() != null && entry.getValue().weight >= 0,
                    "ambient module weights must be non-negative");
        }
        require(ambient.immediatelyYieldToEconomicWork,
                "Ambient behavior must yield to economic work");
    }

    private static void validateDistribution(Map<String, Double> distribution, String name) {
        require(distribution != null && !distribution.isEmpty(), name + " is required");
        double total = 0.0d;
        for (Map.Entry<String, Double> entry : distribution.entrySet()) {
            requireText(entry.getKey(), name + " key");
            require(entry.getValue() != null, name + " values are required");
            requireFraction(entry.getValue(), name + '.' + entry.getKey());
            total += entry.getValue();
        }
        require(Math.abs(total - 1.0d) <= DISTRIBUTION_TOLERANCE,
                name + " weights must sum to 1.0");
    }

    private static void requireFraction(double value, String name) {
        require(Double.isFinite(value) && value >= 0.0d && value <= 1.0d,
                name + " must be between 0 and 1");
    }

    private static void requireEnum(String value, Set<String> allowed, String name) {
        requireText(value, name);
        require(allowed.contains(value), name + " must be one of " + allowed);
    }

    private static void parseInstant(String value, String name) {
        requireText(value, name);
        try {
            Instant.parse(value);
        } catch (DateTimeParseException failure) {
            throw new EconomyConfigException(name + " must be an ISO-8601 instant", failure);
        }
    }

    private static void parseDuration(String value, String name) {
        requireText(value, name);
        try {
            require(!Duration.parse(value).isNegative(), name + " cannot be negative");
        } catch (DateTimeParseException failure) {
            throw new EconomyConfigException(name + " must be an ISO-8601 duration", failure);
        }
    }

    private static void requireText(String value, String name) {
        require(value != null && !value.isBlank(), name + " is required");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new EconomyConfigException(message);
        }
    }
}
