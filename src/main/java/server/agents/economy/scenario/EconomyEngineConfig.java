package server.agents.economy.scenario;

import java.util.List;
import java.util.Map;

/** YAML-owned configuration for an immutable economy simulation run. */
public final class EconomyEngineConfig {
    public int schemaVersion;
    public Scenario scenario;
    public Clock clock;
    public Catalog catalog;
    public World world;
    public Session session;
    public Population population;
    public Bootstrap bootstrap;
    public NpcCommerce npcCommerce;
    public Activity activity;
    public Market market;
    public Valuation valuation;
    public Tax tax;
    public SeasonalRules seasonalRules;
    public Quests quests;
    public Demand demand;
    public Scrolling scrolling;
    public Chairs chairs;
    public Ambient ambient;
    public Persistence persistence;
    public HumanReadiness humanReadiness;

    public static final class Scenario {
        public String id;
        public long seed;
        public String description;
        public long targetLogicalDays;
        public int checkpointEveryLogicalHours;
        public boolean stopOnInvariantViolation;
    }

    public static final class Clock {
        public String mode;
        public String logicalStart;
        public int maximumEventsPerBatch;
    }

    public static final class Catalog {
        public String bundleId;
        public boolean requireMatchingAdaptiveRevision;
        public List<String> adaptiveResources;
        public List<String> sqlResources;
        public List<String> mechanicalResources;
    }

    public static final class World {
        public int channelId;
        public int freeMarketEntranceMapId;
        public int firstFreeMarketRoomMapId;
        public int lastFreeMarketRoomMapId;
        public List<String> activityRegions;
        public boolean allowPhysicalActivityOutsideFreeMarket;
    }

    /** Bounded ownership policy for a single economy visit. */
    public static final class Session {
        public String defaultMaximumDuration;
        public String maximumIdleDuration;
        public boolean exitWhenPrimaryGoalsComplete;
        public int maximumConsecutiveUnproductiveStalls;
        public boolean knowledgeOnlyBrowsingEnabled;
        public boolean implicitEconomicIntentsEnabled;
    }

    public static final class Population {
        public int initialAgents;
        public int maximumAgents;
        public Growth growth;
        public Map<String, Double> classDistribution;
        public Map<String, Double> activityDistribution;
        public MerchantParticipation merchantParticipation;
    }

    public static final class Growth {
        public String type;
        public int amount;
        public int everyLogicalDays;
        public boolean spreadArrivalsAcrossInterval;
    }

    public static final class MerchantParticipation {
        public double willingSellerFraction;
        public double dedicatedMerchantFraction;
    }

    public static final class Bootstrap {
        public String holdingsMode;
        public String shopPermitPolicy;
        public int shopPermitItemId;
        public boolean allowAdministratorEndowment;
        public boolean journalAllEndowments;
    }

    public static final class NpcCommerce {
        public String accessMode;
        public String accessScope;
        public boolean preserveRealNpcStock;
        public boolean preserveRealPrices;
        public boolean preserveRealRestrictions;
        public String logicalServiceDelay;
        public boolean recordOriginalNpcAndMap;
        public int dispositionNpcId;
    }

    public static final class Activity {
        public String executionMode;
        public String agentBuild;
        public String mapCatalogResource;
        public int minimumCalibrationSamples;
        public boolean visibleWhileActive;
        public boolean returnThroughFreeMarketEntrance;
        public int medianSessionMinutes;
        public int maximumSessionMinutes;
        public boolean congestionAware;
        public boolean levelAppropriate;
        public boolean jobAppropriate;
        public boolean objectiveAware;
        public boolean consumeHpPotions;
        public boolean consumeMpPotions;
        public boolean consumeAmmunition;
        public boolean enforceInventoryCapacity;
        public boolean allowDeath;
        public String deathOccurrenceSource;
        public String deathPenaltySource;
        public String deathDowntimeSource;
    }

    public static final class Market {
        public String venue;
        public int maximumStallsPerAgent;
        public int maximumListingsPerStall;
        public boolean hiredMerchantsEnabled;
        public boolean barterEnabled;
        public boolean sellerMustRemainAtStall;
        public String maximumListingDuration;
        public String minimumRepriceInterval;
        public int maximumReprices;
        public double coldStartNpcMarkupMinimum;
        public double coldStartNpcMarkupMaximum;
        public String actionPoll;
        public String postTripDelay;
        public String portalTimeout;
        public String approachTimeout;
        public String stallOpenTimeout;
        public String negotiationTimeout;
        public String stallOfferReviewDelay;
        public String stallOfferArrangementTimeout;
        public long minimumPublicOfferIncrementMesos;
        public int minimumPublicOfferIncrementBasisPoints;
        public String stallOfferFlavorTemplate;
        public int interactionRangePixels;
        public int approachRangePixels;
        public boolean globalSearchAllowed;
        public int minimumRoomsPerTrip;
        public int maximumRoomsPerTrip;
        public boolean rememberObservedListings;
        public boolean useCosmicTransactions;
        public boolean rejectSelfTrade;
        public boolean detectCircularTrade;
        public boolean publicOffersEnabled;
    }

    public static final class Tax {
        public boolean enabled;
        public String policy;
        public int buyerRateBasisPoints;
        public int sellerRateBasisPoints;
        public int maximumRateBasisPoints;
        public List<TaxChange> scheduledChanges;
    }

    public static final class Valuation {
        public String observationMemory;
        public int minimumObservedListings;
        public int catalogAnchorMarkupBasisPoints;
        public List<ItemValueOverride> customOverrides;
    }

    public static final class ItemValueOverride {
        public int itemId;
        public long unitValueMesos;
        public String reason;
    }

    public static final class TaxChange {
        public String effectiveAt;
        public int buyerRateBasisPoints;
        public int sellerRateBasisPoints;
    }

    public static final class SeasonalRules {
        public boolean enabled;
        public List<SeasonalOverlay> overlays;
    }

    public static final class SeasonalOverlay {
        public String overlayId;
        public String startsAt;
        public String endsAt;
        public int mobId;
        public int itemId;
        public double dropRateMultiplier;
    }

    public static final class Quests {
        public boolean enabled;
        public boolean demandRequiresAcceptedQuest;
        public boolean demandRequiresRemainingObjective;
        public boolean allowTradeAcquisition;
        public boolean allowRemoteQuestNpcFromFreeMarket;
        public boolean allowNpcAcquisitionOnlyWhenGameSupportsIt;
        public int maximumConcurrentActive;
        public double acceptanceProbabilityPerMarketCycle;
        public String catalogResource;
        public String victoriaMapCatalogResource;
        public String selectionDisposition;
        public String rewardSelectionPolicy;
    }

    public static final class Demand {
        public double questMaximumWalletFraction;
        public double equipmentMaximumWalletFraction;
        public double scrollMaximumWalletFraction;
        public double chairMaximumWalletFraction;
        public long utilityMesoScale;
        public double minimumMarginalUtility;
        public List<ResourceTarget> resourceTargets;
    }

    public static final class ResourceTarget {
        public int itemId;
        public int npcId;
        public int targetQuantity;
        public int purchaseLot;
        public List<String> jobs;
        public double urgency;
    }

    public static final class Scrolling {
        public boolean enabled;
        public boolean requireOwnedEquipment;
        public boolean requireRemainingSlots;
        public boolean preserveRealSuccessAndDestructionRates;
    }

    public static final class Chairs {
        public boolean enabled;
        public boolean requireOwnedChair;
        public boolean allowListing;
        public boolean allowDirectTrade;
        public boolean collectionPreferenceEnabled;
    }

    public static final class Ambient {
        public boolean enabled;
        public int maximumConsecutiveActions;
        public boolean immediatelyYieldToEconomicWork;
        public Map<String, AmbientModule> modules;
    }

    public static final class AmbientModule {
        public boolean enabled;
        public int weight;
    }

    public static final class Persistence {
        public String provider;
        public String database;
        public String eventPartition;
        public boolean checkpointCompression;
        public String retainRawEconomicEvents;
        public String retainDecisionEvents;
        public String retainChatEvents;
        public int retainMovementDebugDays;
        public int evidenceBatchSize;
    }

    public static final class HumanReadiness {
        public boolean enabled;
        public boolean neverAssumeCounterpartyIsAgent;
        public boolean enforceHumanSafeValidation;
        public boolean separateHumanAndAgentPriceEvidence;
    }
}
