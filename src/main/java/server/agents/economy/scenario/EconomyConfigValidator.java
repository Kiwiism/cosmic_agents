package server.agents.economy.scenario;

import constants.inventory.ItemConstants;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Fail-fast validation for settings whose violation would corrupt a run. */
public final class EconomyConfigValidator {
    private static final double DISTRIBUTION_TOLERANCE = 0.000_001d;
    private static final Set<String> CLOCK_MODES = Set.of("REALTIME", "MAX_THROUGHPUT");
    private static final Set<String> NPC_ACCESS_MODES = Set.of("REMOTE_FROM_FREE_MARKET");
    private static final Set<String> NPC_ACCESS_SCOPES = Set.of("ALL_GAME");
    private static final Set<String> ACTIVITY_MODES = Set.of("RULE_EXACT");
    private static final Set<String> HOLDINGS_MODES = Set.of("IMPORT_EXISTING_COSMIC_CHARACTERS");
    private static final Set<String> SHOP_PERMIT_POLICIES = Set.of("REQUIRE_OWNED_REAL_ITEM");

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
        require(config.world.firstFreeMarketRoomMapId >= 910000001
                        && config.world.lastFreeMarketRoomMapId <= 910000022
                        && config.world.firstFreeMarketRoomMapId
                        <= config.world.lastFreeMarketRoomMapId,
                "Free Market room range must be an ordered subset of 910000001 through 910000022");
        require(config.world.activityRegions != null && !config.world.activityRegions.isEmpty(),
                "At least one activity region is required");
        require(Set.copyOf(config.world.activityRegions).equals(Set.of("MAPLE_ISLAND", "VICTORIA_ISLAND")),
                "The initial activity manifest must be exactly Maple Island and Victoria Island");
        require(!config.world.allowPhysicalActivityOutsideFreeMarket,
                "Visible economy activity must remain inside the Free Market");

        validateSession(config.session);

        validatePopulation(config.population);
        validateBootstrap(config.bootstrap);
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
        require(config.npcCommerce.dispositionNpcId > 0,
                "npcCommerce.dispositionNpcId must identify a configured real shop NPC");

        requireEnum(config.activity.executionMode, ACTIVITY_MODES, "activity.executionMode");
        requireText(config.activity.agentBuild, "activity.agentBuild");
        requireText(config.activity.mapCatalogResource, "activity.mapCatalogResource");
        require(config.activity.minimumCalibrationSamples > 0,
                "activity.minimumCalibrationSamples must be positive");
        require(config.activity.medianSessionMinutes > 0,
                "medianSessionMinutes must be positive");
        require(config.activity.maximumSessionMinutes >= config.activity.medianSessionMinutes,
                "maximumSessionMinutes must not be below the median");
        require(!config.activity.visibleWhileActive,
                "Offscreen activity agents cannot remain visible in the Free Market");
        require(config.activity.returnThroughFreeMarketEntrance,
                "Offscreen activity must return through the Free Market entrance");
        require(config.activity.levelAppropriate && config.activity.jobAppropriate
                        && config.activity.enforceInventoryCapacity,
                "Rule-exact activity requires level/job matching and real inventory capacity");
        if (config.activity.allowDeath) {
            require("LIVE_ACTIVITY_CALIBRATION".equals(config.activity.deathOccurrenceSource),
                    "death occurrence must come from matching live activity calibration");
            require("COSMIC_V83_RULES".equals(config.activity.deathPenaltySource),
                    "death penalties must use the shared Cosmic v83 rule");
            require("AGENT_RESPAWN_CONFIGURATION".equals(config.activity.deathDowntimeSource),
                    "death downtime must use the ordinary Agent respawn configuration");
        }
        require(!config.activity.congestionAware,
                "offscreen congestion must remain disabled until active-session occupancy is journaled");
        require(config.activity.consumeHpPotions && config.activity.consumeMpPotions
                        && config.activity.consumeAmmunition,
                "rule-exact activity must preserve calibrated potion and ammunition consumption");

        validateMarket(config.market, config.world);
        validateValuation(config.valuation);
        validateTax(config.tax);
        validateSeasonal(config.seasonalRules);
        validateAmbient(config.ambient);
        validateDemand(config.demand);
        require(config.quests.demandRequiresAcceptedQuest
                        && config.quests.demandRequiresRemainingObjective,
                "quest demand must remain tied to accepted, unfinished live objectives");
        if (config.quests.enabled) require(config.quests.allowRemoteQuestNpcFromFreeMarket
                        && config.quests.allowTradeAcquisition
                        && config.quests.allowNpcAcquisitionOnlyWhenGameSupportsIt,
                "enabled quest lifecycle must preserve real acquisition rules");
        require(config.quests.maximumConcurrentActive > 0,
                "quests.maximumConcurrentActive must be positive");
        require(config.quests.acceptanceProbabilityPerMarketCycle >= 0
                        && config.quests.acceptanceProbabilityPerMarketCycle <= 1,
                "quests.acceptanceProbabilityPerMarketCycle must be within zero and one");
        requireText(config.quests.catalogResource, "quests.catalogResource");
        requireText(config.quests.victoriaMapCatalogResource, "quests.victoriaMapCatalogResource");
        requireText(config.quests.selectionDisposition, "quests.selectionDisposition");
        require("WEARABLE_THEN_NPC_VALUE".equals(config.quests.rewardSelectionPolicy),
                "quests.rewardSelectionPolicy must preserve the implemented exact utility policy");
        if (config.chairs.enabled) require(config.chairs.requireOwnedChair
                        && config.chairs.allowListing,
                "enabled chair activity must preserve real ownership and market listing access");
        require(config.scrolling.preserveRealSuccessAndDestructionRates,
                "scroll projects must preserve real Cosmic outcome rates even while disabled");
        if (config.scrolling.enabled) require(config.scrolling.requireOwnedEquipment
                        && config.scrolling.requireRemainingSlots
                        && config.scrolling.preserveRealSuccessAndDestructionRates,
                "enabled scroll projects must use owned equipment, real slots, and Cosmic outcome rates");
        require("POSTGRESQL".equals(config.persistence.provider),
                "persistence.provider must be POSTGRESQL");
        requireText(config.persistence.database, "persistence.database");
        require("NONE".equals(config.persistence.eventPartition),
                "eventPartition must remain NONE until PostgreSQL partition migrations ship");
        require(!config.persistence.checkpointCompression,
                "checkpointCompression must remain false until a verified compressed codec ships");
        require("FOREVER".equals(config.persistence.retainRawEconomicEvents)
                        && "FOREVER".equals(config.persistence.retainDecisionEvents)
                        && "FOREVER".equals(config.persistence.retainChatEvents),
                "permanent economy evidence retention is required in the first release");
        require(config.persistence.retainMovementDebugDays >= 0,
                "retainMovementDebugDays must be non-negative");
        require(config.persistence.evidenceBatchSize > 0,
                "persistence.evidenceBatchSize must be positive");
        require(config.humanReadiness.enabled && config.humanReadiness.neverAssumeCounterpartyIsAgent
                        && config.humanReadiness.enforceHumanSafeValidation
                        && config.humanReadiness.separateHumanAndAgentPriceEvidence,
                "human readiness requires safe validation and separate counterparty evidence");
    }

    private static void requireSections(EconomyEngineConfig config) {
        require(config.scenario != null && config.clock != null && config.catalog != null
                        && config.world != null && config.session != null
                        && config.population != null && config.npcCommerce != null && config.demand != null
                        && config.bootstrap != null
                        && config.activity != null && config.market != null && config.valuation != null
                        && config.tax != null
                        && config.seasonalRules != null && config.quests != null
                        && config.scrolling != null && config.chairs != null
                        && config.ambient != null && config.persistence != null
                        && config.humanReadiness != null,
                "Every top-level economy configuration section is required");
    }

    private static void validateSession(EconomyEngineConfig.Session session) {
        Duration maximum = parsePositiveDuration(session.defaultMaximumDuration,
                "session.defaultMaximumDuration");
        Duration idle = parseDuration(session.maximumIdleDuration, "session.maximumIdleDuration");
        require(idle.compareTo(maximum) <= 0,
                "session.maximumIdleDuration cannot exceed the session maximum");
        require(session.maximumConsecutiveUnproductiveStalls > 0,
                "session.maximumConsecutiveUnproductiveStalls must be positive");
        require(session.exitWhenPrimaryGoalsComplete,
                "the basic session must permit early exit when primary goals complete");
        require(session.implicitEconomicIntentsEnabled,
                "structured implicit economic intents are part of the stable boundary");
    }

    private static void validateDemand(EconomyEngineConfig.Demand demand) {
        require(demand.questMaximumWalletFraction >= 0 && demand.questMaximumWalletFraction <= 1,
                "demand.questMaximumWalletFraction must be within zero and one");
        require(demand.equipmentMaximumWalletFraction >= 0 && demand.equipmentMaximumWalletFraction <= 1,
                "demand.equipmentMaximumWalletFraction must be within zero and one");
        require(demand.scrollMaximumWalletFraction >= 0 && demand.scrollMaximumWalletFraction <= 1,
                "demand.scrollMaximumWalletFraction must be within zero and one");
        require(demand.chairMaximumWalletFraction >= 0 && demand.chairMaximumWalletFraction <= 1,
                "demand.chairMaximumWalletFraction must be within zero and one");
        require(demand.utilityMesoScale > 0, "demand.utilityMesoScale must be positive");
        require(demand.minimumMarginalUtility >= 0,
                "demand.minimumMarginalUtility must be non-negative");
        require(demand.resourceTargets != null, "demand.resourceTargets is required");
        for (EconomyEngineConfig.ResourceTarget target : demand.resourceTargets) {
            require(target.itemId > 0 && target.npcId > 0 && target.targetQuantity > 0
                            && target.purchaseLot > 0 && target.purchaseLot <= target.targetQuantity,
                    "demand resource targets require real ids and positive bounded quantities");
            require(target.jobs != null, "demand resource target jobs are required");
            require(target.urgency >= 0 && target.urgency <= 1,
                    "demand resource urgency must be within zero and one");
        }
    }

    private static void validateBootstrap(EconomyEngineConfig.Bootstrap bootstrap) {
        requireEnum(bootstrap.holdingsMode, HOLDINGS_MODES, "bootstrap.holdingsMode");
        requireEnum(bootstrap.shopPermitPolicy, SHOP_PERMIT_POLICIES,
                "bootstrap.shopPermitPolicy");
        require(bootstrap.shopPermitItemId > 0, "bootstrap.shopPermitItemId must be positive");
        require(ItemConstants.isPlayerShop(bootstrap.shopPermitItemId),
                "bootstrap.shopPermitItemId must be a real Cosmic PlayerShop permit (514xxxx), not a Hired Merchant");
        require(bootstrap.journalAllEndowments, "Every bootstrap endowment must be journaled");
        require(!bootstrap.allowAdministratorEndowment,
                "Administrator endowment is forbidden in rule-exact economy runs");
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
        require(population.merchantParticipation.dedicatedMerchantFraction == 0,
                "dedicatedMerchantFraction must remain zero until the dedicated lifecycle ships");
    }

    private static void validateMarket(EconomyEngineConfig.Market market,
                                       EconomyEngineConfig.World world) {
        require("PLAYER_SHOP".equals(market.venue),
                "PLAYER_SHOP is the only supported initial venue");
        require(market.maximumStallsPerAgent == 1,
                "Exactly one stall per agent is a non-negotiable invariant");
        require(market.maximumListingsPerStall > 0 && market.maximumListingsPerStall <= 16,
                "PlayerShop listing capacity must be between 1 and 16");
        require(!market.hiredMerchantsEnabled,
                "Hired merchants require a separate permit and escrow milestone");
        require(market.sellerMustRemainAtStall,
                "PlayerShop owners must remain at their stalls");
        require(!market.globalSearchAllowed,
                "Agents must physically observe stalls rather than use global search");
        require(market.rememberObservedListings,
                "Private observed-listing memory is required");
        require(market.minimumRoomsPerTrip > 0
                        && market.maximumRoomsPerTrip >= market.minimumRoomsPerTrip
                        && market.maximumRoomsPerTrip <= world.lastFreeMarketRoomMapId
                        - world.firstFreeMarketRoomMapId + 1,
                "Room scan range must fit inside the configured FM room range");
        Duration maximumListingDuration = parsePositiveDuration(
                market.maximumListingDuration, "market.maximumListingDuration");
        Duration minimumRepriceInterval = parseDuration(
                market.minimumRepriceInterval, "market.minimumRepriceInterval");
        parsePositiveDuration(market.actionPoll, "market.actionPoll");
        parseDuration(market.postTripDelay, "market.postTripDelay");
        parsePositiveDuration(market.portalTimeout, "market.portalTimeout");
        parsePositiveDuration(market.approachTimeout, "market.approachTimeout");
        parsePositiveDuration(market.stallOpenTimeout, "market.stallOpenTimeout");
        Duration negotiationTimeout = parsePositiveDuration(
                market.negotiationTimeout, "market.negotiationTimeout");
        Duration offerReviewDelay = parseDuration(
                market.stallOfferReviewDelay, "market.stallOfferReviewDelay");
        parsePositiveDuration(market.stallOfferArrangementTimeout,
                "market.stallOfferArrangementTimeout");
        require(offerReviewDelay.compareTo(negotiationTimeout) < 0,
                "stallOfferReviewDelay must be below negotiationTimeout");
        require(market.minimumPublicOfferIncrementMesos >= 1,
                "minimumPublicOfferIncrementMesos must be positive");
        require(market.minimumPublicOfferIncrementBasisPoints >= 0
                        && market.minimumPublicOfferIncrementBasisPoints <= 10_000,
                "minimumPublicOfferIncrementBasisPoints must be between 0 and 10000");
        require(market.stallOfferFlavorTemplate != null
                        && !market.stallOfferFlavorTemplate.isBlank()
                        && market.stallOfferFlavorTemplate.contains("{offer}")
                        && market.stallOfferFlavorTemplate.contains("{item_stats}")
                        && market.stallOfferFlavorTemplate.contains("{item_name}"),
                "stallOfferFlavorTemplate must contain offer, item_stats, and item_name placeholders");
        require(market.interactionRangePixels > 0 && market.approachRangePixels > 0,
                "market physical ranges must be positive");
        require(market.maximumReprices >= 0, "maximumReprices must be non-negative");
        if (market.maximumReprices > 0) {
            require(!minimumRepriceInterval.isZero(),
                    "minimumRepriceInterval must be positive when repricing is enabled");
            require(minimumRepriceInterval.compareTo(maximumListingDuration) < 0,
                    "minimumRepriceInterval must be below maximumListingDuration");
        }
        require(Double.isFinite(market.coldStartNpcMarkupMinimum)
                        && Double.isFinite(market.coldStartNpcMarkupMaximum)
                        && market.coldStartNpcMarkupMinimum >= 0
                        && market.coldStartNpcMarkupMaximum >= market.coldStartNpcMarkupMinimum,
                "cold-start NPC markups must be finite, non-negative, and ordered");
        require(market.useCosmicTransactions,
                "Live market settlement must use Cosmic transactions");
        require(market.rejectSelfTrade, "Self trading must be rejected");
        require(!market.detectCircularTrade,
                "detectCircularTrade must remain false until durable multi-transaction cycle detection ships");
        require(!market.barterEnabled || market.publicOffersEnabled,
                "barter requires structured public offers");
    }

    private static void validateTax(EconomyEngineConfig.Tax tax) {
        require(Set.of("COSMIC_DEFAULT", "CONFIGURED").contains(tax.policy),
                "tax.policy must be COSMIC_DEFAULT or CONFIGURED");
        require(tax.maximumRateBasisPoints >= 0 && tax.maximumRateBasisPoints <= 10_000,
                "maximum tax rate must be between 0 and 10000 basis points");
        require(tax.buyerRateBasisPoints >= 0
                        && tax.buyerRateBasisPoints <= tax.maximumRateBasisPoints,
                "buyer tax exceeds configured bounds");
        require(tax.sellerRateBasisPoints >= 0
                        && tax.sellerRateBasisPoints <= tax.maximumRateBasisPoints,
                "seller tax exceeds configured bounds");
        require(tax.scheduledChanges != null, "tax.scheduledChanges is required");
        if (!tax.enabled || "COSMIC_DEFAULT".equals(tax.policy)) {
            require(tax.buyerRateBasisPoints == 0 && tax.sellerRateBasisPoints == 0
                            && tax.scheduledChanges.isEmpty(),
                    "disabled or COSMIC_DEFAULT tax cannot define simulation overrides");
        }
        Instant previous = null;
        for (EconomyEngineConfig.TaxChange change : tax.scheduledChanges) {
            require(change != null, "tax scheduled change is required");
            Instant effective = instant(change.effectiveAt, "tax.scheduledChanges.effectiveAt");
            require(previous == null || effective.isAfter(previous),
                    "tax scheduled changes must be strictly chronological");
            require(change.buyerRateBasisPoints >= 0
                            && change.buyerRateBasisPoints <= tax.maximumRateBasisPoints,
                    "scheduled buyer tax exceeds configured bounds");
            require(change.sellerRateBasisPoints >= 0
                            && change.sellerRateBasisPoints <= tax.maximumRateBasisPoints,
                    "scheduled seller tax exceeds configured bounds");
            previous = effective;
        }
    }

    private static void validateValuation(EconomyEngineConfig.Valuation valuation) {
        require(valuation != null, "valuation configuration is required");
        parsePositiveDuration(valuation.observationMemory, "valuation.observationMemory");
        require(valuation.minimumObservedListings > 0,
                "valuation.minimumObservedListings must be positive");
        require(valuation.catalogAnchorMarkupBasisPoints >= 0
                        && valuation.catalogAnchorMarkupBasisPoints <= 100_000,
                "valuation.catalogAnchorMarkupBasisPoints must be between 0 and 100000");
        require(valuation.customOverrides != null, "valuation.customOverrides is required");
        Set<Integer> itemIds = new HashSet<>();
        for (EconomyEngineConfig.ItemValueOverride override : valuation.customOverrides) {
            require(override.itemId > 0 && override.unitValueMesos > 0,
                    "valuation overrides require positive itemId and unitValueMesos");
            require(override.reason != null && !override.reason.isBlank(),
                    "valuation overrides require an audit reason");
            require(itemIds.add(override.itemId), "valuation override itemIds must be unique");
        }
    }

    private static void validateSeasonal(EconomyEngineConfig.SeasonalRules seasonal) {
        require(seasonal.overlays != null, "seasonalRules.overlays is required");
        Set<String> ids = new HashSet<>();
        for (EconomyEngineConfig.SeasonalOverlay overlay : seasonal.overlays) {
            require(overlay != null, "seasonal overlay is required");
            requireText(overlay.overlayId, "seasonal overlay id");
            require(ids.add(overlay.overlayId), "seasonal overlay ids must be unique");
            Instant start = instant(overlay.startsAt, "seasonal overlay startsAt");
            Instant end = instant(overlay.endsAt, "seasonal overlay endsAt");
            require(end.isAfter(start), "seasonal overlay end must follow start");
            require(overlay.mobId > 0 && overlay.itemId > 0,
                    "seasonal overlay requires exact mob and item ids");
            require(Double.isFinite(overlay.dropRateMultiplier) && overlay.dropRateMultiplier > 0,
                    "seasonal overlay multiplier must be positive");
        }
        require(!seasonal.enabled,
                "seasonal rules cannot be enabled until exact catalog validation and resolver support ship");
    }

    private static void validateAmbient(EconomyEngineConfig.Ambient ambient) {
        Set<String> supportedModules = Set.of("idle", "walk", "sit", "fidget");
        require(ambient.maximumConsecutiveActions >= 0,
                "maximumConsecutiveActions must be non-negative");
        require(ambient.modules != null && !ambient.modules.isEmpty(),
                "ambient modules are required");
        for (Map.Entry<String, EconomyEngineConfig.AmbientModule> entry : ambient.modules.entrySet()) {
            requireText(entry.getKey(), "ambient module name");
            require(supportedModules.contains(entry.getKey()),
                    "unsupported ambient module: " + entry.getKey());
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
        instant(value, name);
    }

    private static Instant instant(String value, String name) {
        requireText(value, name);
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException failure) {
            throw new EconomyConfigException(name + " must be an ISO-8601 instant", failure);
        }
    }

    private static Duration parseDuration(String value, String name) {
        requireText(value, name);
        try {
            Duration duration = Duration.parse(value);
            require(!duration.isNegative(), name + " cannot be negative");
            return duration;
        } catch (DateTimeParseException failure) {
            throw new EconomyConfigException(name + " must be an ISO-8601 duration", failure);
        }
    }

    private static Duration parsePositiveDuration(String value, String name) {
        Duration duration = parseDuration(value, name);
        require(!duration.isZero(), name + " must be positive");
        return duration;
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
