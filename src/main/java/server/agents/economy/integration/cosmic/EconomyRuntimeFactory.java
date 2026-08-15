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
        return build(runId, yaml, cosmicDataSource, economyDataSource, liveAgents, false);
    }

    public static ManagedEconomyRun resume(UUID runId, Path yaml, DataSource cosmicDataSource,
                                           DataSource economyDataSource,
                                           Function<String, Character> liveAgents) {
        return build(runId, yaml, cosmicDataSource, economyDataSource, liveAgents, true);
    }

    private static ManagedEconomyRun build(UUID runId, Path yaml, DataSource cosmicDataSource,
                                           DataSource economyDataSource,
                                           Function<String, Character> liveAgents, boolean resume) {
        Objects.requireNonNull(runId); Objects.requireNonNull(cosmicDataSource);
        Objects.requireNonNull(economyDataSource); Objects.requireNonNull(liveAgents);
        LoadedEconomyConfig loaded = new EconomyConfigLoader().load(yaml);
        EconomyEngineConfig config = loaded.config();
        new EconomyDatabaseVerifier(economyDataSource).verify(config.persistence.database);
        CatalogBundleDescriptor bundle = new CatalogBundleLoader().load(config.catalog);
        NpcLocationIndex npcLocations = NpcLocationIndex.loadDefault();
        EconomyCatalog catalog = new CosmicEconomyCatalog(bundle.version(), npcLocations);
        EconomyParticipantRegistry participants = new EconomyParticipantRegistry(liveAgents);

        JdbcEconomyEvidenceJournal evidenceJournal = new JdbcEconomyEvidenceJournal(economyDataSource);
        CosmicAgentEconomyFacade economyFacade = new CosmicAgentEconomyFacade(participants,
                new server.agents.economy.ownership.DefaultAgentEconomyFacade(runId,
                        new server.agents.economy.ownership.ShadowEconomyEvaluator(catalog),
                        new JdbcEconomyOwnershipJournal(economyDataSource)));
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
                config.market.coldStartNpcMarkupMaximum, config.world.firstFreeMarketRoomMapId,
                config.world.lastFreeMarketRoomMapId);
        CosmicNegotiatedTradeExecutor tradeExecutor = new CosmicNegotiatedTradeExecutor(
                participants::admittedCharacter, config.market.interactionRangePixels);
        JdbcStallOfferStore stallOffers = new JdbcStallOfferStore(economyDataSource);
        CosmicPublicTradeNegotiator negotiation = new CosmicPublicTradeNegotiator(runId, participants,
                seller, tradeExecutor, evidenceJournal, new JdbcNegotiationEvidenceStore(economyDataSource),
                config.market.barterEnabled, needReader::read, stallOffers,
                duration(config.market.negotiationTimeout), config.market.interactionRangePixels);
        NamedRandomStreams marketRandom = new NamedRandomStreams(config.scenario.seed);
        CosmicMarketAmbientBehavior ambient = new CosmicMarketAmbientBehavior(
                new ConstrainedAmbientBehaviorPolicy(config.ambient.maximumConsecutiveActions,
                        marketRandom, config.ambient.modules));
        AutonomousFreeMarketBehavior market = new AutonomousFreeMarketBehavior(runId, loaded.sha256(),
                bundle.version(), config.market, marketRandom, physical, needReader,
                new CosmicObservedOfferNeedAugmenter(config.demand, config.scrolling, config.chairs),
                evidenceJournal, sellerPlans, seller, economyFacade,
                new CosmicNpcResourceProcurement(needReader, npc),
                config.ambient.enabled ? ambient : AutonomousFreeMarketBehavior.AmbientBehavior.disabled(),
                negotiation, new CosmicScrollProjectService(runId, marketRandom),
                new CosmicStallOfferReviewService(runId, stallOffers),
                new CosmicQuestLifecycleService(runId, config.quests, marketRandom, catalog, npcLocations),
                config.world.firstFreeMarketRoomMapId, config.world.lastFreeMarketRoomMapId,
                duration(config.market.actionPoll), duration(config.market.postTripDelay),
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
        JdbcEconomyParticipantBindingStore bindingStore =
                new JdbcEconomyParticipantBindingStore(economyDataSource);
        SimulationRunEngine.RunCheckpoint checkpoint = null;
        String initialStatus = "CREATED";
        if (resume) {
            SimulationRunRepository.RunRecord record = runRepository.find(runId)
                    .orElseThrow(() -> new IllegalStateException("economy run does not exist: " + runId));
            if (java.util.Set.of("COMPLETED", "FAILED", "INVARIANT_VIOLATION").contains(record.status()))
                throw new IllegalStateException("economy run cannot be resumed from status " + record.status());
            if (!record.configHash().equals(loaded.sha256()))
                throw new IllegalStateException("stored run configuration hash does not match the YAML");
            if (!record.catalogVersion().equals(bundle.version()))
                throw new IllegalStateException("stored run catalog version does not match the live catalog");
            checkpoint = runRepository.latestCheckpoint(runId)
                    .orElseThrow(() -> new IllegalStateException("economy run has no durable checkpoint: " + runId));
            initialStatus = "RUNNING";
        } else {
            runRepository.create(runId, loaded, bundle);
            var admissions = new PopulationAdmissionPlanner().plan(config.population,
                    java.time.Instant.parse(config.clock.logicalStart),
                    new NamedRandomStreams(config.scenario.seed));
            try {
                bindingStore.reserve(runId, admissions.stream().map(admission -> {
                    Character character = liveAgents.apply(admission.agentId());
                    if (character == null)
                        throw new IllegalStateException("reserved live character is missing: " + admission.agentId());
                    return new EconomyParticipantBindingStore.Reservation(admission.agentId(),
                            character.getId(), admission.admittedAt());
                }).toList());
            } catch (RuntimeException failure) {
                runRepository.updateStatus(runId, java.time.Instant.parse(config.clock.logicalStart),
                        "FAILED", "roster reservation failed: " + failure.getClass().getSimpleName());
                throw failure;
            }
        }
        EconomyRunApplication application = checkpoint == null
                ? EconomyRunApplication.start(runId, loaded, bundle, catalog, world,
                        new JdbcEconomyLifecycleJournal(economyDataSource))
                : EconomyRunApplication.restore(checkpoint, loaded, bundle, catalog, world,
                        new JdbcEconomyLifecycleJournal(economyDataSource));
        if (resume) runRepository.updateLogicalTime(runId, checkpoint.logicalTime(), "RUNNING");
        EconomyOutboxRelay relay = new EconomyOutboxRelay(new JdbcCosmicOutboxSource(cosmicDataSource),
                new JdbcPostgresOutboxSink(economyDataSource));
        EconomyEvidencePipeline pipeline = new EconomyEvidencePipeline(relay,
                new JdbcCosmicEconomicEventIngestor(economyDataSource),
                new JdbcEconomyProjectionService(economyDataSource),
                new JdbcEconomyInvariantAuditor(economyDataSource));
        ManagedEconomyRun managed = new ManagedEconomyRun(application, pipeline, runRepository,
                config.persistence.evidenceBatchSize, config.scenario.stopOnInvariantViolation,
                initialStatus);
        server.agents.integration.AgentEconomicActionGuardRuntime.install((agent, type, slot, itemId,
                                                                          quantity, venue, at) -> {
            var permit = economyFacade.claimNpcSale(agent, type, slot, itemId, quantity, venue, at);
            return new server.agents.integration.AgentEconomicActionGuardRuntime.Decision(
                    permit.allowed(), permit.reason());
        });
        return managed;
    }

    private static Duration duration(String value) { return Duration.parse(value); }
    private static long millis(String value) { return duration(value).toMillis(); }
}
