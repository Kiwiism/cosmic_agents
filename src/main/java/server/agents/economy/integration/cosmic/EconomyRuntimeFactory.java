package server.agents.economy.integration.cosmic;

import client.Character;
import server.agents.economy.activity.VictoriaActivityMapCatalog;
import server.agents.economy.activity.RuleExactFarmResolver;
import server.agents.economy.ambient.ConstrainedAmbientBehaviorPolicy;
import server.agents.economy.catalog.*;
import server.agents.economy.persistence.*;
import server.agents.economy.scenario.*;
import server.agents.integration.AgentShopGatewayRuntime;
import server.agents.simulation.activity.CosmicExternalAgentActivityAdapter;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/** Composes the decoupled economy module around already-live Cosmic agent characters. */
public final class EconomyRuntimeFactory {
    private EconomyRuntimeFactory() { }

    public static ManagedEconomyRun start(UUID runId, Path yaml, DataSource cosmicDataSource,
                                          DataSource economyDataSource,
                                          Function<String, Character> liveAgents) {
        return start(runId, yaml, cosmicDataSource, economyDataSource, liveAgents,
                UnaryOperator.identity());
    }

    public static ManagedEconomyRun start(UUID runId, Path yaml, DataSource cosmicDataSource,
                                          DataSource economyDataSource,
                                          Function<String, Character> liveAgents,
                                          UnaryOperator<server.agents.economy.session.EconomySessionPort>
                                                  sessionDecorator) {
        return build(runId, yaml, cosmicDataSource, economyDataSource, liveAgents, false,
                sessionDecorator);
    }

    public static ManagedEconomyRun resume(UUID runId, Path yaml, DataSource cosmicDataSource,
                                           DataSource economyDataSource,
                                           Function<String, Character> liveAgents) {
        return resume(runId, yaml, cosmicDataSource, economyDataSource, liveAgents,
                UnaryOperator.identity());
    }

    public static ManagedEconomyRun resume(UUID runId, Path yaml, DataSource cosmicDataSource,
                                           DataSource economyDataSource,
                                           Function<String, Character> liveAgents,
                                           UnaryOperator<server.agents.economy.session.EconomySessionPort>
                                                   sessionDecorator) {
        return build(runId, yaml, cosmicDataSource, economyDataSource, liveAgents, true,
                sessionDecorator);
    }

    private static ManagedEconomyRun build(UUID runId, Path yaml, DataSource cosmicDataSource,
                                           DataSource economyDataSource,
                                           Function<String, Character> liveAgents, boolean resume,
                                           UnaryOperator<server.agents.economy.session.EconomySessionPort>
                                                   sessionDecorator) {
        Objects.requireNonNull(runId); Objects.requireNonNull(cosmicDataSource);
        Objects.requireNonNull(economyDataSource); Objects.requireNonNull(liveAgents);
        LoadedEconomyConfig loaded = new EconomyConfigLoader().load(yaml);
        EconomyEngineConfig config = loaded.config();
        new EconomyDatabaseVerifier(economyDataSource).verify(config.persistence.database);
        CatalogBundleDescriptor bundle = new CatalogBundleLoader().load(config.catalog);
        NpcLocationIndex npcLocations = NpcLocationIndex.loadDefault();
        EconomyCatalog catalog = new CosmicEconomyCatalog(bundle.version(), npcLocations);
        EconomyParticipantRegistry participants = new EconomyParticipantRegistry(liveAgents);
        JdbcAgentItemValuationService valuations = new JdbcAgentItemValuationService(
                runId, economyDataSource, catalog, config.valuation);

        JdbcEconomyEvidenceJournal evidenceJournal = new JdbcEconomyEvidenceJournal(economyDataSource);
        CosmicAgentEconomyFacade economyFacade = new CosmicAgentEconomyFacade(participants,
                new server.agents.economy.ownership.DefaultAgentEconomyFacade(runId,
                        new server.agents.economy.ownership.ShadowEconomyEvaluator(catalog),
                        new JdbcEconomyOwnershipJournal(economyDataSource)), valuations,
                config.session.implicitEconomicIntentsEnabled
                        ? new JdbcEconomyCommunicationPort(runId, economyDataSource)
                        : server.agents.economy.communication.EconomyCommunicationPort.disabled());
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
                config.world.lastFreeMarketRoomMapId, valuations);
        CosmicNegotiatedTradeExecutor tradeExecutor = new CosmicNegotiatedTradeExecutor(
                participants::admittedCharacter, config.market.interactionRangePixels);
        JdbcStallOfferStore stallOffers = new JdbcStallOfferStore(economyDataSource);
        CosmicPublicTradeNegotiator negotiation = new CosmicPublicTradeNegotiator(runId, participants,
                seller, tradeExecutor, evidenceJournal, new JdbcNegotiationEvidenceStore(economyDataSource),
                config.market.barterEnabled, needReader::read, stallOffers,
                duration(config.market.negotiationTimeout), config.market.interactionRangePixels,
                config.market.minimumPublicOfferIncrementMesos,
                config.market.minimumPublicOfferIncrementBasisPoints,
                new StallOfferFlavorRenderer(config.market.stallOfferFlavorTemplate));
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
                config.market.publicOffersEnabled ? negotiation
                        : AutonomousFreeMarketBehavior.NegotiationBehavior.disabled(),
                config.scrolling.enabled ? new CosmicScrollProjectService(runId, marketRandom)
                        : AutonomousFreeMarketBehavior.ScrollBehavior.disabled(),
                config.market.publicOffersEnabled ? new CosmicStallOfferReviewService(runId, stallOffers,
                        duration(config.market.stallOfferReviewDelay),
                        duration(config.market.stallOfferArrangementTimeout))
                        : AutonomousFreeMarketBehavior.OfferReviewBehavior.disabled(),
                config.quests.enabled
                        ? new CosmicQuestLifecycleService(runId, config.quests, marketRandom, catalog, npcLocations)
                        : AutonomousFreeMarketBehavior.QuestBehavior.disabled(),
                config.market.publicOffersEnabled
                        ? new CosmicPrivateTradeArrangementService(runId, stallOffers, participants,
                        physical, seller, tradeExecutor)
                        : AutonomousFreeMarketBehavior.ArrangementBehavior.disabled(),
                config.world.firstFreeMarketRoomMapId, config.world.lastFreeMarketRoomMapId,
                config.session,
                duration(config.market.actionPoll), duration(config.market.postTripDelay),
                duration(config.market.maximumListingDuration), duration(config.npcCommerce.logicalServiceDelay));
        CalibratedCosmicActivityPlanner activity = new CalibratedCosmicActivityPlanner(config.activity,
                new JdbcActivityCalibrationRepository(economyDataSource),
                new VictoriaActivityMapCatalog(config.activity.mapCatalogResource), catalog);
        ConfiguredEconomyTaxPolicy taxPolicy = new ConfiguredEconomyTaxPolicy(config.tax);
        CosmicEconomyWorldAdapter world = new CosmicEconomyWorldAdapter(runId, config.world.channelId,
                loaded.sha256(), bundle.version(), participants, market, taxPolicy,
                new JdbcEconomyParticipantBindingStore(economyDataSource),
                new JdbcEconomyBootstrapStore(economyDataSource), participants::admitted,
                participants::released);
        server.agents.economy.session.EconomySessionPort sessions =
                Objects.requireNonNull(sessionDecorator, "Commerce session decorator").apply(world);
        if (sessions == null) throw new IllegalStateException("Commerce session decorator returned null");

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
        CosmicOffscreenPresence offscreenPresence = new CosmicOffscreenPresence();
        CosmicFarmSettlementService farmSettlement = new CosmicFarmSettlementService();
        CosmicExternalAgentActivityAdapter externalActivity = new CosmicExternalAgentActivityAdapter(
                runId, loaded.sha256(), bundle.version(), participants, activity::plan,
                new RuleExactFarmResolver(catalog), new CosmicExternalAgentActivityAdapter.Presence() {
                    @Override public void leave(Character agent, java.time.Instant at) {
                        offscreenPresence.leaveVisibleFreeMarket(agent, at);
                    }
                    @Override public void enterEconomyEntrance(Character agent, java.time.Instant at) {
                        offscreenPresence.enterFreeMarketEntrance(agent, at);
                    }
                    @Override public void restoreDetached(Character agent) {
                        offscreenPresence.restoreDetached(agent);
                    }
                }, farmSettlement::settle, taxPolicy::at);
        EconomyRunApplication application = checkpoint == null
                ? EconomyRunApplication.start(runId, loaded, bundle, catalog, sessions, externalActivity,
                        new JdbcEconomyLifecycleJournal(economyDataSource))
                : EconomyRunApplication.restore(checkpoint, loaded, bundle, catalog, sessions, externalActivity,
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
        server.agents.integration.AgentEconomyRuntime.install(new server.agents.integration.AgentEconomyRuntime.Gateway() {
            @Override public boolean available() { return true; }
            @Override public server.agents.economy.market.AgentItemValuationService.Valuation valueItem(
                    String agentId, int itemId, java.time.Instant at) {
                return economyFacade.valueItem(agentId, itemId, at);
            }
            @Override public server.agents.economy.communication.EconomicIntent publishIntent(
                    String actor, String counterparty,
                    server.agents.economy.communication.EconomicIntent.Kind kind, int itemId,
                    String fingerprint, int quantity, long mesos, Integer mapId, String text,
                    java.util.Map<String, Object> attributes, java.time.Instant at,
                    java.time.Duration lifetime) {
                return economyFacade.publishIntent(actor, counterparty, kind, itemId, fingerprint,
                        quantity, mesos, mapId, text, attributes, at, lifetime);
            }
            @Override public java.util.List<server.agents.economy.communication.EconomicIntent> discoverIntents(
                    String agentId, int itemId, java.time.Instant at, int limit) {
                return economyFacade.discoverIntents(agentId, itemId, at, limit);
            }
            @Override public boolean resolveIntent(String agentId, java.util.UUID intentId,
                    server.agents.economy.communication.EconomicIntent.Status status,
                    java.time.Instant at, String reason) {
                return economyFacade.resolveIntent(agentId, intentId, status, at, reason);
            }
            @Override public server.agents.economy.ownership.InventoryReview reviewInventory(
                    client.Character agent, String agentId,
                    java.util.List<server.agents.economy.ownership.LegacyDispositionProposal> proposals,
                    java.time.Instant at) {
                return economyFacade.reviewInventory(agent, agentId, proposals, at);
            }
        });
        return managed;
    }

    private static Duration duration(String value) { return Duration.parse(value); }
    private static long millis(String value) { return duration(value).toMillis(); }
}
