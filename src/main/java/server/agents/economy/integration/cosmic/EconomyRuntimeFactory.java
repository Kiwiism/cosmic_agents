package server.agents.economy.integration.cosmic;

import client.Character;
import server.agents.economy.activity.VictoriaActivityMapCatalog;
import server.agents.economy.ambient.ConstrainedAmbientBehaviorPolicy;
import server.agents.economy.catalog.*;
import server.agents.economy.persistence.*;
import server.agents.economy.scenario.*;
import server.agents.integration.AgentShopGatewayRuntime;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

/** Composes the decoupled economy module around already-live Cosmic agent characters. */
public final class EconomyRuntimeFactory {
    private EconomyRuntimeFactory() { }

    public static ManagedEconomyRun start(UUID runId, Path yaml, DataSource cosmicDataSource,
                                          DataSource economyDataSource,
                                          Function<String, Character> liveAgents) {
        Objects.requireNonNull(runId); Objects.requireNonNull(cosmicDataSource);
        Objects.requireNonNull(economyDataSource); Objects.requireNonNull(liveAgents);
        new EconomyDatabaseVerifier(economyDataSource).verify();
        LoadedEconomyConfig loaded = new EconomyConfigLoader().load(yaml);
        EconomyEngineConfig config = loaded.config();
        CatalogBundleDescriptor bundle = new CatalogBundleLoader().load(config.catalog);
        NpcLocationIndex npcLocations = NpcLocationIndex.loadDefault();
        EconomyCatalog catalog = new CosmicEconomyCatalog(bundle.version(), npcLocations);
        EconomyParticipantRegistry participants = new EconomyParticipantRegistry(liveAgents);

        JdbcEconomyEvidenceJournal evidenceJournal = new JdbcEconomyEvidenceJournal(economyDataSource);
        RemoteNpcCommerceService npc = new RemoteNpcCommerceService(catalog, AgentShopGatewayRuntime.shop());
        CosmicAgentNeedReader needReader = new CosmicAgentNeedReader(config.demand, catalog);
        AgentFreeMarketBuyerService buyer = new AgentFreeMarketBuyerService(
                config.market.interactionRangePixels, participants::isAdmittedCharacter);
        CosmicMarketObservationService observations = new CosmicMarketObservationService(
                runId, buyer, evidenceJournal);
        CosmicFreeMarketPhysicalGateway physical = new CosmicFreeMarketPhysicalGateway(observations,
                config.world.freeMarketEntranceMapId, config.world.firstFreeMarketRoomMapId,
                config.world.lastFreeMarketRoomMapId,
                millis(config.market.portalTimeout), millis(config.market.approachTimeout),
                config.market.approachRangePixels);
        CosmicMarketSellerGateway seller = new CosmicMarketSellerGateway(npc,
                config.bootstrap.shopPermitItemId, millis(config.market.stallOpenTimeout));
        CosmicMarketSellerPlanReader sellerPlans = new CosmicMarketSellerPlanReader(catalog,
                config.npcCommerce.dispositionNpcId, config.market.maximumListingsPerStall,
                config.bootstrap.shopPermitItemId, config.market.coldStartNpcMarkupMinimum,
                config.market.coldStartNpcMarkupMaximum);
        CosmicNegotiatedTradeExecutor tradeExecutor = new CosmicNegotiatedTradeExecutor(
                participants::admittedCharacter, config.market.interactionRangePixels);
        CosmicPublicTradeNegotiator negotiation = new CosmicPublicTradeNegotiator(runId, participants,
                seller, tradeExecutor, evidenceJournal, new JdbcNegotiationEvidenceStore(economyDataSource),
                config.market.barterEnabled, needReader::read,
                duration(config.market.negotiationTimeout), config.market.interactionRangePixels);
        NamedRandomStreams marketRandom = new NamedRandomStreams(config.scenario.seed);
        CosmicMarketAmbientBehavior ambient = new CosmicMarketAmbientBehavior(
                new ConstrainedAmbientBehaviorPolicy(config.ambient.maximumConsecutiveActions,
                        marketRandom, config.ambient.modules));
        AutonomousFreeMarketBehavior market = new AutonomousFreeMarketBehavior(runId, loaded.sha256(),
                bundle.version(), config.market, marketRandom, physical, needReader,
                new CosmicObservedOfferNeedAugmenter(config.demand, config.scrolling, config.chairs),
                evidenceJournal, sellerPlans, seller, new CosmicNpcResourceProcurement(needReader, npc),
                config.ambient.enabled ? ambient : AutonomousFreeMarketBehavior.AmbientBehavior.disabled(),
                negotiation, duration(config.market.actionPoll), duration(config.market.postTripDelay),
                duration(config.market.maximumListingDuration), duration(config.npcCommerce.logicalServiceDelay));
        CalibratedCosmicActivityPlanner activity = new CalibratedCosmicActivityPlanner(config.activity,
                new JdbcActivityCalibrationRepository(economyDataSource),
                new VictoriaActivityMapCatalog(config.activity.mapCatalogResource), catalog);
        CosmicEconomyWorldAdapter world = new CosmicEconomyWorldAdapter(runId, config.world.channelId,
                loaded.sha256(), bundle.version(), participants, market, activity,
                new CosmicOffscreenPresence(), new CosmicFarmSettlementService(),
                new ConfiguredEconomyTaxPolicy(config.tax),
                new JdbcEconomyParticipantBindingStore(economyDataSource),
                new JdbcEconomyBootstrapStore(economyDataSource), participants::admitted);

        JdbcSimulationRunRepository runRepository = new JdbcSimulationRunRepository(economyDataSource);
        runRepository.create(runId, loaded, bundle);
        EconomyRunApplication application = EconomyRunApplication.start(runId, loaded, bundle, catalog,
                world, new JdbcEconomyLifecycleJournal(economyDataSource));
        EconomyOutboxRelay relay = new EconomyOutboxRelay(new JdbcCosmicOutboxSource(cosmicDataSource),
                new JdbcPostgresOutboxSink(economyDataSource));
        EconomyEvidencePipeline pipeline = new EconomyEvidencePipeline(relay,
                new JdbcCosmicEconomicEventIngestor(economyDataSource),
                new JdbcEconomyProjectionService(economyDataSource),
                new JdbcEconomyInvariantAuditor(economyDataSource));
        return new ManagedEconomyRun(application, pipeline, runRepository,
                config.persistence.evidenceBatchSize, config.scenario.stopOnInvariantViolation);
    }

    private static Duration duration(String value) { return Duration.parse(value); }
    private static long millis(String value) { return duration(value).toMillis(); }
}
