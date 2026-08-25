package server.agents.economy.integration.cosmic;

import client.Character;
import client.inventory.InventoryType;
import server.agents.capabilities.shop.AgentFreeMarketStallService;
import server.agents.economy.decision.AgentNeed;
import server.agents.economy.decision.ObservedPurchasePolicy;
import server.agents.economy.market.*;
import server.agents.economy.persistence.DecisionEvidence;
import server.agents.economy.persistence.EconomyEvidenceJournal;
import server.agents.economy.scenario.*;
import server.agents.economy.session.CommerceParticipant;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Full FM lifecycle: browse/buy, dispose to real NPC, or remain physically present at one stall. */
public final class AutonomousFreeMarketBehavior implements CosmicEconomyWorldAdapter.MarketBehavior {
    private final UUID runId;
    private final String configHash;
    private final String catalogVersion;
    private final EconomyEngineConfig.Market config;
    private final EconomyEngineConfig.Session sessionConfig;
    private final NamedRandomStreams random;
    private final FreeMarketPhysicalGateway physical;
    private final AgentNeedReader needs;
    private final ObservedNeedAugmenter observedNeeds;
    private final EconomyEvidenceJournal journal;
    private final CosmicMarketSellerPlanReader sellerPlans;
    private final CosmicMarketSellerGateway seller;
    private final CosmicAgentEconomyFacade economy;
    private final ResourceProcurement procurement;
    private final AmbientBehavior ambient;
    private final NegotiationBehavior negotiation;
    private final OfferReviewBehavior offerReviews;
    private final ScrollBehavior scrolling;
    private final QuestBehavior quests;
    private final ArrangementBehavior arrangements;
    private final OpenChatBehavior openChat;
    private final ObservedPurchasePolicy purchasePolicy = new ObservedPurchasePolicy();
    private final RoomVisitPlanner roomPlanner;
    private final Duration actionPoll;
    private final Duration postTripDelay;
    private final Duration maximumStallDuration;
    private final Duration minimumRepriceInterval;
    private final Duration npcServiceDelay;
    private final Duration stallInspectionDurationPerListing;
    private final int maximumConsecutiveUnproductiveStalls;
    private final Map<String, State> states = new ConcurrentHashMap<>();
    private final Map<String, Long> progressRevisions = new ConcurrentHashMap<>();

    public AutonomousFreeMarketBehavior(UUID runId, String configHash, String catalogVersion,
                                        EconomyEngineConfig.Market config, NamedRandomStreams random,
                                        FreeMarketPhysicalGateway physical, AgentNeedReader needs,
                                        EconomyEvidenceJournal journal,
                                        CosmicMarketSellerPlanReader sellerPlans,
                                        CosmicMarketSellerGateway seller,
                                        CosmicAgentEconomyFacade economy,
                                        ResourceProcurement procurement,
                                        Duration actionPoll, Duration postTripDelay,
                                        Duration maximumStallDuration, Duration npcServiceDelay) {
        this(runId, configHash, catalogVersion, config, random, physical, needs,
                (agent, profile, observations, base, at) -> base, journal, sellerPlans, seller, economy,
                procurement, AmbientBehavior.disabled(), NegotiationBehavior.disabled(),
                ScrollBehavior.disabled(), OfferReviewBehavior.disabled(), QuestBehavior.disabled(),
                ArrangementBehavior.disabled(), 910000001, 910000022, defaultSession(), actionPoll, postTripDelay,
                maximumStallDuration, npcServiceDelay);
    }

    public AutonomousFreeMarketBehavior(UUID runId, String configHash, String catalogVersion,
                                        EconomyEngineConfig.Market config, NamedRandomStreams random,
                                        FreeMarketPhysicalGateway physical, AgentNeedReader needs,
                                        ObservedNeedAugmenter observedNeeds,
                                        EconomyEvidenceJournal journal,
                                        CosmicMarketSellerPlanReader sellerPlans,
                                        CosmicMarketSellerGateway seller,
                                        CosmicAgentEconomyFacade economy,
                                        ResourceProcurement procurement,
                                        AmbientBehavior ambient,
                                        NegotiationBehavior negotiation,
                                        ScrollBehavior scrolling,
                                        QuestBehavior quests,
                                        Duration actionPoll, Duration postTripDelay,
                                        Duration maximumStallDuration, Duration npcServiceDelay) {
        this(runId, configHash, catalogVersion, config, random, physical, needs, observedNeeds,
                journal, sellerPlans, seller, economy, procurement, ambient, negotiation, scrolling,
                OfferReviewBehavior.disabled(), quests, ArrangementBehavior.disabled(),
                910000001, 910000022, defaultSession(),
                actionPoll, postTripDelay, maximumStallDuration,
                npcServiceDelay);
    }

    public AutonomousFreeMarketBehavior(UUID runId, String configHash, String catalogVersion,
                                        EconomyEngineConfig.Market config, NamedRandomStreams random,
                                        FreeMarketPhysicalGateway physical, AgentNeedReader needs,
                                        ObservedNeedAugmenter observedNeeds,
                                        EconomyEvidenceJournal journal,
                                        CosmicMarketSellerPlanReader sellerPlans,
                                        CosmicMarketSellerGateway seller,
                                        CosmicAgentEconomyFacade economy,
                                        ResourceProcurement procurement,
                                        AmbientBehavior ambient,
                                        NegotiationBehavior negotiation,
                                        ScrollBehavior scrolling,
                                        OfferReviewBehavior offerReviews,
                                        QuestBehavior quests,
                                        ArrangementBehavior arrangements,
                                        int firstRoomMapId, int lastRoomMapId,
                                        EconomyEngineConfig.Session sessionConfig,
                                        Duration actionPoll, Duration postTripDelay,
                                        Duration maximumStallDuration, Duration npcServiceDelay) {
        this(runId, configHash, catalogVersion, config, random, physical, needs, observedNeeds,
                journal, sellerPlans, seller, economy, procurement, ambient, negotiation, scrolling,
                offerReviews, quests, arrangements, firstRoomMapId, lastRoomMapId, sessionConfig,
                actionPoll, postTripDelay, maximumStallDuration, npcServiceDelay,
                OpenChatBehavior.disabled());
    }

    public AutonomousFreeMarketBehavior(UUID runId, String configHash, String catalogVersion,
                                        EconomyEngineConfig.Market config, NamedRandomStreams random,
                                        FreeMarketPhysicalGateway physical, AgentNeedReader needs,
                                        ObservedNeedAugmenter observedNeeds,
                                        EconomyEvidenceJournal journal,
                                        CosmicMarketSellerPlanReader sellerPlans,
                                        CosmicMarketSellerGateway seller,
                                        CosmicAgentEconomyFacade economy,
                                        ResourceProcurement procurement,
                                        AmbientBehavior ambient,
                                        NegotiationBehavior negotiation,
                                        ScrollBehavior scrolling,
                                        OfferReviewBehavior offerReviews,
                                        QuestBehavior quests,
                                        ArrangementBehavior arrangements,
                                        int firstRoomMapId, int lastRoomMapId,
                                        EconomyEngineConfig.Session sessionConfig,
                                        Duration actionPoll, Duration postTripDelay,
                                        Duration maximumStallDuration, Duration npcServiceDelay,
                                        OpenChatBehavior openChat) {
        this.runId = Objects.requireNonNull(runId); this.configHash = Objects.requireNonNull(configHash);
        this.catalogVersion = Objects.requireNonNull(catalogVersion); this.config = Objects.requireNonNull(config);
        this.sessionConfig = Objects.requireNonNull(sessionConfig);
        this.random = Objects.requireNonNull(random); this.physical = Objects.requireNonNull(physical);
        this.needs = Objects.requireNonNull(needs); this.observedNeeds = Objects.requireNonNull(observedNeeds);
        this.journal = Objects.requireNonNull(journal);
        this.sellerPlans = Objects.requireNonNull(sellerPlans); this.seller = Objects.requireNonNull(seller);
        this.economy = Objects.requireNonNull(economy);
        this.procurement = Objects.requireNonNull(procurement);
        this.ambient = Objects.requireNonNull(ambient);
        this.negotiation = Objects.requireNonNull(negotiation);
        this.offerReviews = Objects.requireNonNull(offerReviews);
        this.scrolling = Objects.requireNonNull(scrolling);
        this.quests = Objects.requireNonNull(quests);
        this.arrangements = Objects.requireNonNull(arrangements);
        this.openChat = Objects.requireNonNull(openChat);
        this.roomPlanner = new RoomVisitPlanner(firstRoomMapId, lastRoomMapId);
        if (sessionConfig.maximumConsecutiveUnproductiveStalls <= 0)
            throw new IllegalArgumentException("maximum unproductive stalls must be positive");
        this.maximumConsecutiveUnproductiveStalls = sessionConfig.maximumConsecutiveUnproductiveStalls;
        if (actionPoll.isNegative() || actionPoll.isZero() || postTripDelay.isNegative()
                || maximumStallDuration.isNegative() || maximumStallDuration.isZero())
            throw new IllegalArgumentException("market timing must be non-negative and polling positive");
        this.actionPoll = actionPoll; this.postTripDelay = postTripDelay;
        this.maximumStallDuration = maximumStallDuration;
        this.minimumRepriceInterval = Duration.parse(config.minimumRepriceInterval);
        this.stallInspectionDurationPerListing = Duration.parse(
                config.stallInspectionDurationPerListing);
        if (npcServiceDelay.isNegative()) throw new IllegalArgumentException("NPC service delay cannot be negative");
        this.npcServiceDelay = npcServiceDelay;
    }

    @Override
    public synchronized EconomyWorldPort.MarketDirective perform(Character agent, CommerceParticipant profile,
                                                                  Instant logicalAt) {
        State state = states.computeIfAbsent(profile.agentId(), ignored -> new State(
                new PrivateMarketKnowledge(), new PhysicalMarketTrip(roomPlanner.plan(
                        config.minimumRoomsPerTrip, config.maximumRoomsPerTrip, random),
                        stallInspectionDurationPerListing)));
        if (!state.entryAppraised) {
            economy.onFreeMarketEntry(agent, profile.agentId(), logicalAt);
            if (state.sellerPlan != null)
                state.sellerPlan = economy.appraise(agent, profile.agentId(), state.sellerPlan, logicalAt);
            state.entryAppraised = true;
        }
        if (!state.entryPlanned) {
            state.entryGoals = entryGoals(agent, profile, logicalAt);
            state.entryPlanned = true;
            appendDecision(profile, logicalAt, "ECONOMY_SESSION_PLAN",
                    Map.of("goals", state.entryGoals), alternatives("EXIT_WITHOUT_ECONOMIC_WORK",
                            "Release the bounded session without inspecting or disposing holdings"),
                    Map.of("knowledge", "PRIVATE_OBSERVATIONS_AND_AUTHORITATIVE_CATALOG"),
                    Map.of("level", agent.getLevel(), "job", agent.getJob().getId()),
                    Map.of("mesos", (double) agent.getMeso()));
            if (state.entryGoals.isEmpty()) return finish(profile.agentId(), logicalAt);
        }
        ArrangementBehavior.Result arrangement = arrangements.progress(agent, profile, logicalAt);
        if (arrangement.attempted()) {
            appendDecision(profile, logicalAt, "PRIVATE_TRADE_ARRANGEMENT",
                    Map.of("arrangementId", arrangement.arrangementId(),
                            "outcome", arrangement.outcome(), "completed", arrangement.completed()),
                    alternatives("DEFER_ARRANGEMENT",
                            "Keep the exact accepted agreement pending until a later bounded tick"),
                    arrangement.evidence(), Map.of("itemId", arrangement.itemId()), Map.of());
            if (!arrangement.completed()) return revisit(logicalAt, arrangement.externalActionPending());
        }
        if (!state.questEvaluated || state.phase == Phase.PROCURING) {
            FreeMarketPhysicalGateway.ActionStatus entrance = physical.requestEntrance(agent);
            if (entrance != FreeMarketPhysicalGateway.ActionStatus.ARRIVED)
                return revisit(logicalAt, entrance == FreeMarketPhysicalGateway.ActionStatus.ASSIGNED
                        || entrance == FreeMarketPhysicalGateway.ActionStatus.IN_PROGRESS);
        }
        if (!state.questEvaluated) {
            state.questEvaluated = true;
            QuestBehavior.Result quest = quests.advance(agent, profile, logicalAt);
            if (quest.attempted()) {
                appendDecision(profile, logicalAt, "QUEST_" + quest.action(),
                        Map.of("success", quest.success(), "questId", quest.questId(),
                                "npcId", quest.npcId(), "selection", quest.selection() == null
                                        ? -1 : quest.selection()), alternatives("DEFER_QUEST",
                                "Preserve current quest capacity and reconsider next market cycle"), quest.evidence(),
                        Map.of("reason", "ELIGIBLE_REAL_COSMIC_QUEST"), Map.of());
                return revisitAfter(logicalAt, npcServiceDelay, !quest.success());
            }
        }
        if (state.phase == Phase.PROCURING) {
            Optional<ResourceProcurement.Result> purchase = procurement.buyNext(
                    agent, profile, state.attemptedResourceItems);
            if (purchase.isPresent()) {
                ResourceProcurement.Result result = purchase.orElseThrow();
                state.attemptedResourceItems.add(result.itemId());
                appendDecision(profile, logicalAt, "NPC_RESOURCE_PROCUREMENT",
                        Map.of("itemId", result.itemId(), "quantity", result.quantity(),
                                "npcId", result.npcId(), "result", result.result(),
                                "commerceAction", result.commerceAction()), alternatives("DEFER_RESOURCE_PURCHASE",
                                "Keep mesos liquid and accept a shorter activity runway"),
                        Map.of("sourceMap", result.sourceMapId()),
                        Map.of("reason", "CONFIGURED_RESOURCE_TARGET"),
                        Map.of("mesoDelta", (double) result.mesoDelta()));
                return revisitAfter(logicalAt, npcServiceDelay, false);
            }
            state.phase = Phase.BROWSING;
        }
        if (state.phase == Phase.BROWSING) {
            OpenChatBehavior.Result publicSale = openChat.attemptPurchase(agent, profile,
                    needs.read(agent, profile, logicalAt), logicalAt);
            if (publicSale.attempted()) {
                appendOpenChatDecision(profile, logicalAt, "OPEN_CHAT_PURCHASE", publicSale);
                if (publicSale.sold()) return revisit(logicalAt, false);
                if (publicSale.outcome().startsWith("APPROACH_")) return revisit(logicalAt,
                        Boolean.TRUE.equals(publicSale.evidence().get("externalActionPending")));
            }
            PhysicalMarketTrip.Step step = state.trip.tick(agent, profile.agentId(), logicalAt,
                    state.knowledge, physical);
            if (step.inspectionStarted()) {
                appendDecision(profile, logicalAt, "STALL_INSPECTION_STARTED",
                        Map.of("room", step.roomMapId(), "stallObjectId", step.stallObjectId(),
                                "listingCount", step.listingCount(), "durationMillis",
                                stallInspectionDurationPerListing.multipliedBy(step.listingCount()).toMillis()),
                        alternatives("SKIP_STALL", "Continue without learning these listed asks"),
                        Map.of("source", "PHYSICAL_PLAYER_SHOP"),
                        Map.of("reason", "CONFIGURED_PER_LISTING_DWELL"), Map.of());
            }
            if (!step.offers().isEmpty()) {
                boolean purchased = attemptObservedPurchase(agent, profile, logicalAt, state, step.offers());
                List<AgentNeed> currentNeeds = observedNeeds.augment(agent, profile,
                        step.offers().stream().map(CosmicMarketObservationService.ObservedOffer::observation).toList(),
                        needs.read(agent, profile, logicalAt), logicalAt);
                NegotiationBehavior.Result negotiated = purchased ? NegotiationBehavior.Result.none()
                        : negotiation.attempt(agent, profile, currentNeeds,
                        step.offers().stream().map(CosmicMarketObservationService.ObservedOffer::observation).toList(),
                        logicalAt);
                if (negotiated.attempted()) appendNegotiation(profile, logicalAt, negotiated);
                if (purchased || negotiated.attempted()) state.consecutiveUnproductiveStalls = 0;
                else state.consecutiveUnproductiveStalls++;
                if (state.consecutiveUnproductiveStalls >= maximumConsecutiveUnproductiveStalls)
                    state.trip.stop();
            }
            if (step.status() == PhysicalMarketTrip.Status.COMPLETE) {
                List<AgentNeed> currentNeeds = observedNeeds.augment(agent, profile,
                        state.knowledge.snapshot(), needs.read(agent, profile, logicalAt), logicalAt);
                ScrollBehavior.Result scrolled = scrolling.applyNext(agent, profile, currentNeeds, logicalAt);
                if (scrolled.attempted()) {
                    appendDecision(profile, logicalAt, "SCROLL_PROJECT",
                            Map.of("success", scrolled.success(), "outcome", scrolled.outcome(),
                                    "scrollItemId", scrolled.scrollItemId(),
                                    "equipmentItemId", scrolled.equipmentItemId()),
                            alternatives("HOLD_SCROLL_PROJECT",
                                    "Retain the owned scroll and equipment for a later cycle"), scrolled.evidence(),
                            Map.of("reason", "SCROLL_UPGRADE"), Map.of());
                    return revisit(logicalAt, !scrolled.success());
                }
                state.sellerPlan = sellerPlans.read(agent, profile, state.knowledge, currentNeeds, logicalAt);
                state.sellerPlan = economy.appraise(agent, profile.agentId(), state.sellerPlan, logicalAt);
                state.phase = Phase.DISPOSING;
                return revisit(logicalAt, false);
            }
            if (step.status() == PhysicalMarketTrip.Status.BLOCKED) {
                appendDecision(profile, logicalAt, "MARKET_TRIP_BLOCKED", Map.of("room", step.roomMapId()),
                        alternatives("RETRY_NEXT_MARKET_CYCLE",
                                "Release the blocked physical route and re-plan rooms"),
                        Map.of(), Map.of(), Map.of("result", 0d));
            }
            if (step.revisitAt().isPresent())
                return new EconomyWorldPort.MarketDirective(Optional.empty(), step.revisitAt(), false);
            return revisit(logicalAt, step.status() == PhysicalMarketTrip.Status.PHYSICAL_ACTION_PENDING);
        }
        if (state.phase == Phase.DISPOSING) {
            FreeMarketPhysicalGateway.ActionStatus entrance = physical.requestEntrance(agent);
            if (entrance != FreeMarketPhysicalGateway.ActionStatus.ARRIVED)
                return revisit(logicalAt, entrance == FreeMarketPhysicalGateway.ActionStatus.ASSIGNED
                        || entrance == FreeMarketPhysicalGateway.ActionStatus.IN_PROGRESS);
            if (state.npcSaleIndex < state.sellerPlan.npcSales().size()) {
                MarketSellerPlan.NpcSale sale = state.sellerPlan.npcSales().get(state.npcSaleIndex++);
                RemoteNpcCommerceService.Receipt receipt = seller.sellNpc(agent, sale, logicalAt);
                appendDecision(profile, logicalAt, "NPC_DISPOSITION",
                        Map.of("itemId", sale.itemId(), "quantity", sale.quantity(), "npcId", sale.npcId(),
                                "result", receipt.result()), alternatives("HOLD_OR_LIST_ITEM",
                                "Retain inventory exposure instead of taking immediate NPC proceeds"),
                        Map.of("sourceMap", receipt.sourceMapId()),
                        Map.of("reason", sale.reason(), "evidence", sale.evidence()), Map.of());
                return revisitAfter(logicalAt, npcServiceDelay, false);
            }
            if (!state.openChatPrepared) {
                OpenChatBehavior.Preparation preparation = openChat.prepare(
                        agent, profile, state.sellerPlan, logicalAt);
                state.sellerPlan = preparation.plan();
                state.openChatPrepared = true;
                if (preparation.selected()) {
                    appendDecision(profile, logicalAt, "OPEN_CHAT_SALE_OPENED",
                            Map.of("offerId", preparation.offerId(), "itemId", preparation.itemId(),
                                    "quantity", preparation.quantity(), "askMesos", preparation.askMesos(),
                                    "reserveMesos", preparation.reserveMesos()),
                            alternatives("KEEP_FOR_STALL_OR_LATER",
                                    "Do not reserve this real holding for direct public Trade"),
                            Map.of("source", "APPRAISED_REAL_INVENTORY"),
                            Map.of("reason", "CONFIGURED_OPEN_CHAT_SELLER_SAMPLE"), Map.of());
                    state.phase = Phase.OPEN_CHAT_SELLING;
                    return revisit(logicalAt, false);
                }
                finalizeStallParticipation(agent, profile, state);
            }
            if (state.sellerPlan.stallListings().isEmpty()) return finish(profile.agentId(), logicalAt);
            state.phase = Phase.OPENING_STALL;
        }
        if (state.phase == Phase.OPEN_CHAT_SELLING) {
            OpenChatBehavior.Result result = openChat.progressSeller(agent, profile, logicalAt);
            if (result.attempted() && result.done()) {
                appendOpenChatDecision(profile, logicalAt, "OPEN_CHAT_SALE_CLOSED", result);
                finalizeStallParticipation(agent, profile, state);
                if (state.sellerPlan.stallListings().isEmpty()) return finish(profile.agentId(), logicalAt);
                state.phase = Phase.OPENING_STALL;
            } else return revisit(logicalAt, false);
        }
        if (state.phase == Phase.OPENING_STALL) {
            FreeMarketPhysicalGateway.ActionStatus travel = physical.requestRoom(
                    agent, state.sellerPlan.preferredRoomMapId());
            if (travel != FreeMarketPhysicalGateway.ActionStatus.ARRIVED) return revisit(logicalAt, true);
            FreeMarketPhysicalGateway.ActionStatus opened = seller.requestOpen(agent, state.sellerPlan);
            if (opened == FreeMarketPhysicalGateway.ActionStatus.ASSIGNED) state.openAttempts++;
            if (opened == FreeMarketPhysicalGateway.ActionStatus.ARRIVED) {
                state.stallOpenedAt = logicalAt; state.phase = Phase.OWNING_STALL;
                appendDecision(profile, logicalAt, "STALL_OPENED",
                        Map.of("room", agent.getMapId(), "listings", state.sellerPlan.stallListings().size()),
                        alternatives("KEEP_ITEMS_OFF_MARKET",
                                "Preserve seller time and inventory for later use or NPC disposition"),
                        Map.of("source", "PRIVATE_OBSERVATIONS"), Map.of(), Map.of());
            } else if (opened == FreeMarketPhysicalGateway.ActionStatus.UNAVAILABLE || state.openAttempts >= 3) {
                appendDecision(profile, logicalAt, "STALL_OPEN_FAILED", Map.of("result", opened.name()),
                        alternatives("RETAIN_ESCROW_CANDIDATES",
                                "Keep owned items and retry after physical or permit constraints clear"),
                        Map.of(), Map.of("permitPolicy", "REQUIRE_OWNED_REAL_ITEM"), Map.of());
                return finish(profile.agentId(), logicalAt);
            }
            return revisit(logicalAt, opened == FreeMarketPhysicalGateway.ActionStatus.ASSIGNED
                    || opened == FreeMarketPhysicalGateway.ActionStatus.IN_PROGRESS);
        }
        if (state.phase == Phase.OWNING_STALL) {
            if (agent.getPlayerShop() == null || !agent.getPlayerShop().isOpen())
                return finish(profile.agentId(), logicalAt);
            OfferReviewBehavior.Result offer = offerReviews.reviewNext(agent, profile, logicalAt);
            if (offer.attempted()) {
                appendDecision(profile, logicalAt, "STALL_OFFER_REVIEW",
                        Map.of("offerId", offer.offerId(), "outcome", offer.outcome(),
                                "accepted", offer.accepted()),
                        alternatives("DEFER_OFFER_REVIEW",
                                "Leave the structured offer pending until a later seller check"),
                        offer.evidence(), Map.of("itemId", offer.itemId()), Map.of());
                return revisit(logicalAt, false);
            }
            Instant repriceAt = state.stallOpenedAt.plus(minimumRepriceInterval);
            if (state.repriceCount < config.maximumReprices && !logicalAt.isBefore(repriceAt)) {
                boolean closed = seller.close(agent, "REPRICE_RESEARCH");
                appendDecision(profile, logicalAt, "STALL_REPRICE_RESEARCH",
                        Map.of("closed", closed, "reprice", state.repriceCount + 1),
                        alternatives("KEEP_CURRENT_ASKS",
                                "Continue current censored listing exposure without new observations"),
                        Map.of("requiresFreshPhysicalObservations", true),
                        Map.of("reason", "UNSOLD_EXPOSURE"), Map.of());
                if (closed) {
                    state.prepareReprice(roomPlanner.plan(config.minimumRoomsPerTrip,
                            config.maximumRoomsPerTrip, random));
                    return revisit(logicalAt, false);
                }
            }
            if (logicalAt.isBefore(state.stallOpenedAt.plus(maximumStallDuration))) {
                AmbientBehavior.Result result = ambient.perform(agent, profile, logicalAt,
                        true, false, state.consecutiveAmbientActions);
                if (result.attempted()) {
                    state.consecutiveAmbientActions = result.success()
                            ? state.consecutiveAmbientActions + 1 : 0;
                    appendDecision(profile, logicalAt, "AMBIENT_MARKET_ACTION",
                            Map.of("action", result.action(), "success", result.success()),
                            alternatives("REMAIN_IDLE",
                                    "Take no ambient action while continuing to yield to economic work"),
                            result.evidence(), Map.of("reason", result.reason(),
                                    "chairItemId", result.chairItemId() == null ? 0 : result.chairItemId()), Map.of());
                } else state.consecutiveAmbientActions = 0;
                Instant next = logicalAt.plus(actionPoll);
                Instant closeAt = state.stallOpenedAt.plus(maximumStallDuration);
                return new EconomyWorldPort.MarketDirective(Optional.empty(),
                        Optional.of(next.isBefore(closeAt) ? next : closeAt), false);
            }
            boolean closed = seller.close(agent, "MAXIMUM_LISTING_DURATION");
            appendDecision(profile, logicalAt, "STALL_CLOSED", Map.of("closed", closed),
                    alternatives("KEEP_STALL_OPEN",
                            "Exceed the configured listing-duration labor budget"),
                    Map.of(), Map.of("reason", "MAXIMUM_LISTING_DURATION"), Map.of());
            if (closed) return finish(profile.agentId(), logicalAt);
        }
        return revisit(logicalAt, false);
    }

    @Override
    public synchronized EconomyWorldPort.MarketDirective drainForRelease(
            Character agent, CommerceParticipant profile, Instant logicalAt) {
        State state = states.get(profile.agentId());
        if (state != null) state.trip.cancel(agent, physical);
        if (agent.getTrade() != null || agent.getHiredMerchant() != null)
            return revisit(logicalAt, true);
        openChat.cancel(profile.agentId(), logicalAt, "ECONOMY_SESSION_DEADLINE");
        if (agent.getPlayerShop() != null && agent.getPlayerShop().isOpen()) {
            boolean closed = seller.close(agent, "ECONOMY_SESSION_DEADLINE");
            appendDecision(profile, logicalAt, "SESSION_DEADLINE_DRAIN",
                    Map.of("closed", closed), alternatives("DEFER_RELEASE",
                            "Active commerce must be drained before releasing economy ownership"),
                    Map.of(), Map.of("reason", "SESSION_DEADLINE"), Map.of());
            if (!closed) return revisit(logicalAt, true);
        }
        return finish(profile.agentId(), logicalAt);
    }

    @Override
    public long progressRevision(String agentId) {
        return progressRevisions.getOrDefault(agentId, 0L);
    }

    @Override
    public synchronized Map<String, Object> snapshotState() {
        Map<String, Object> encodedStates = new TreeMap<>();
        states.forEach((agentId, state) -> encodedStates.put(agentId, stateMap(state)));
        return Map.of("schemaVersion", 1, "agents", encodedStates, "randomStates", random.snapshot(),
                "progressRevisions", Map.copyOf(progressRevisions),
                "openChat", openChat.snapshotState());
    }

    @Override
    @SuppressWarnings("unchecked")
    public synchronized void restoreState(Map<String, Object> snapshot) {
        if (snapshot == null || snapshot.isEmpty()) return;
        if (!states.isEmpty()) throw new IllegalStateException("market behavior state is already initialized");
        if (integer(snapshot, "schemaVersion") != 1)
            throw new IllegalStateException("unsupported market behavior checkpoint schema");
        Object randomState = snapshot.get("randomStates");
        if (randomState instanceof Map<?, ?> values) {
            Map<String, Long> restored = new LinkedHashMap<>();
            values.forEach((key, value) -> restored.put(key.toString(), ((Number) value).longValue()));
            random.restore(restored);
        }
        Object openChatState = snapshot.get("openChat");
        if (openChatState instanceof Map<?, ?> values)
            openChat.restoreState((Map<String, Object>) values);
        Map<String, Object> encodedStates = (Map<String, Object>) snapshot.get("agents");
        encodedStates.forEach((agentId, value) -> states.put(agentId,
                stateFrom((Map<String, Object>) value)));
        Object encodedProgress = snapshot.get("progressRevisions");
        if (encodedProgress instanceof Map<?, ?> revisions) revisions.forEach((agentId, revision) ->
                progressRevisions.put(agentId.toString(), ((Number) revision).longValue()));
    }

    private static Map<String, Object> stateMap(State state) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("phase", state.phase.name());
        value.put("knowledge", state.knowledge.snapshot().stream()
                .map(AutonomousFreeMarketBehavior::observationMap).toList());
        PhysicalMarketTrip.Snapshot trip = state.trip.snapshot();
        Map<String, Object> tripValue = new LinkedHashMap<>();
        tripValue.put("rooms", trip.rooms()); tripValue.put("inspected", trip.inspected());
        tripValue.put("roomIndex", trip.roomIndex());
        tripValue.put("inspectionMillisPerListing", trip.inspectionMillisPerListing());
        if (trip.approachingObjectId() != null)
            tripValue.put("approachingObjectId", trip.approachingObjectId());
        if (trip.inspectingStall() != null) {
            var stall = trip.inspectingStall();
            tripValue.put("inspectingStall", Map.of("objectId", stall.objectId(),
                    "ownerCharacterId", stall.ownerCharacterId(), "roomMapId", stall.roomMapId(),
                    "x", stall.x(), "y", stall.y()));
            tripValue.put("inspectionCompletesAt", trip.inspectionCompletesAt().toString());
            tripValue.put("inspectionListingCount", trip.inspectionListingCount());
        }
        value.put("trip", tripValue);
        value.put("attemptedResourceItems", state.attemptedResourceItems.stream().sorted().toList());
        value.put("sellerPlan", state.sellerPlan == null ? Map.of() : sellerPlanMap(state.sellerPlan));
        value.put("npcSaleIndex", state.npcSaleIndex); value.put("openAttempts", state.openAttempts);
        value.put("repriceCount", state.repriceCount);
        value.put("questEvaluated", state.questEvaluated);
        value.put("entryAppraised", state.entryAppraised);
        value.put("entryPlanned", state.entryPlanned);
        value.put("entryGoals", state.entryGoals);
        value.put("consecutiveAmbientActions", state.consecutiveAmbientActions);
        value.put("consecutiveUnproductiveStalls", state.consecutiveUnproductiveStalls);
        value.put("openChatPrepared", state.openChatPrepared);
        value.put("stallParticipationFinalized", state.stallParticipationFinalized);
        if (state.stallOpenedAt != null) value.put("stallOpenedAt", state.stallOpenedAt.toString());
        return value;
    }

    @SuppressWarnings("unchecked")
    private static State stateFrom(Map<String, Object> value) {
        List<MarketObservation> observations = ((List<Map<String, Object>>) value.get("knowledge"))
                .stream().map(AutonomousFreeMarketBehavior::observationFrom).toList();
        Map<String, Object> tripValue = (Map<String, Object>) value.get("trip");
        List<Integer> rooms = ((List<Number>) tripValue.get("rooms")).stream().map(Number::intValue).toList();
        List<String> inspected = ((List<Object>) tripValue.get("inspected")).stream()
                .map(Object::toString).toList();
        Integer approaching = tripValue.containsKey("approachingObjectId")
                ? integer(tripValue, "approachingObjectId") : null;
        FreeMarketPhysicalGateway.StallTarget inspectingStall = null;
        Instant inspectionCompletesAt = null;
        int inspectionListingCount = 0;
        if (tripValue.get("inspectingStall") instanceof Map<?, ?> encodedStall) {
            Map<String, Object> stall = (Map<String, Object>) encodedStall;
            inspectingStall = new FreeMarketPhysicalGateway.StallTarget(integer(stall, "objectId"),
                    integer(stall, "ownerCharacterId"), integer(stall, "roomMapId"),
                    integer(stall, "x"), integer(stall, "y"));
            inspectionCompletesAt = Instant.parse(text(tripValue, "inspectionCompletesAt"));
            inspectionListingCount = integer(tripValue, "inspectionListingCount");
        }
        State state = new State(PrivateMarketKnowledge.restore(observations), PhysicalMarketTrip.restore(
                new PhysicalMarketTrip.Snapshot(rooms, inspected, integer(tripValue, "roomIndex"), approaching,
                        ((Number) tripValue.getOrDefault("inspectionMillisPerListing", 0)).longValue(),
                        inspectingStall, inspectionCompletesAt, inspectionListingCount)));
        state.phase = Phase.valueOf(text(value, "phase"));
        ((List<Number>) value.get("attemptedResourceItems")).stream().map(Number::intValue)
                .forEach(state.attemptedResourceItems::add);
        Map<String, Object> plan = (Map<String, Object>) value.get("sellerPlan");
        state.sellerPlan = plan == null || plan.isEmpty() ? null : sellerPlanFrom(plan);
        state.npcSaleIndex = integer(value, "npcSaleIndex");
        state.openAttempts = integer(value, "openAttempts");
        state.repriceCount = value.containsKey("repriceCount") ? integer(value, "repriceCount") : 0;
        state.questEvaluated = value.containsKey("questEvaluated")
                && Boolean.TRUE.equals(value.get("questEvaluated"));
        // Facade authority is deliberately rebuilt from current physical inventory after restart.
        state.entryAppraised = false;
        state.entryPlanned = value.containsKey("entryPlanned")
                && Boolean.TRUE.equals(value.get("entryPlanned"));
        state.entryGoals = ((List<?>) value.getOrDefault("entryGoals", List.of())).stream()
                .map(Object::toString).toList();
        state.consecutiveAmbientActions = value.containsKey("consecutiveAmbientActions")
                ? integer(value, "consecutiveAmbientActions") : 0;
        state.consecutiveUnproductiveStalls = value.containsKey("consecutiveUnproductiveStalls")
                ? integer(value, "consecutiveUnproductiveStalls") : 0;
        state.openChatPrepared = value.containsKey("openChatPrepared")
                && Boolean.TRUE.equals(value.get("openChatPrepared"));
        state.stallParticipationFinalized = value.containsKey("stallParticipationFinalized")
                && Boolean.TRUE.equals(value.get("stallParticipationFinalized"));
        if (value.containsKey("stallOpenedAt")) state.stallOpenedAt = Instant.parse(text(value, "stallOpenedAt"));
        if (state.phase.ordinal() >= Phase.DISPOSING.ordinal() && state.sellerPlan == null)
            throw new IllegalStateException("restored market phase requires a seller plan");
        return state;
    }

    private static Map<String, Object> observationMap(MarketObservation observation) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("observationId", observation.observationId());
        value.put("observerAgentId", observation.observerAgentId());
        value.put("observedAt", observation.observedAt().toString());
        value.put("roomMapId", observation.roomMapId());
        value.put("stallOwnerAgentId", observation.stallOwnerAgentId());
        value.put("listingId", observation.listingId()); value.put("itemId", observation.itemId());
        value.put("quantity", observation.quantity()); value.put("unitPrice", observation.unitPrice());
        value.put("quantityPerBundle", observation.quantityPerBundle());
        value.put("bundles", observation.bundles()); value.put("bundlePrice", observation.bundlePrice());
        value.put("fingerprint", observation.fingerprint()); value.put("attributes", observation.attributes());
        value.put("state", observation.state().name());
        return value;
    }

    private static MarketObservation observationFrom(Map<String, Object> value) {
        return new MarketObservation(text(value, "observationId"), text(value, "observerAgentId"),
                Instant.parse(text(value, "observedAt")), integer(value, "roomMapId"),
                text(value, "stallOwnerAgentId"), text(value, "listingId"), integer(value, "itemId"),
                integer(value, "quantity"), number(value, "unitPrice"), integer(value, "quantityPerBundle"),
                integer(value, "bundles"), number(value, "bundlePrice"),
                text(value, "fingerprint"), (Map<String, Object>) value.get("attributes"),
                MarketObservation.State.valueOf(text(value, "state")));
    }

    private static Map<String, Object> sellerPlanMap(MarketSellerPlan plan) {
        return Map.of("npcSales", plan.npcSales().stream().map(sale -> Map.<String, Object>of(
                        "npcId", sale.npcId(), "inventoryType", sale.inventoryType().name(),
                        "slot", (int) sale.slot(), "quantity", (int) sale.quantity(), "itemId", sale.itemId(),
                        "reason", sale.reason(), "evidence", sale.evidence())).toList(),
                "stallListings", plan.stallListings().stream().map(listing -> Map.<String, Object>of(
                        "inventoryType", listing.inventoryType().name(), "slot", (int) listing.slot(),
                        "perBundle", (int) listing.perBundle(), "bundles", (int) listing.bundles(),
                        "price", listing.price())).toList(),
                "preferredRoomMapId", plan.preferredRoomMapId(), "stallDescription", plan.stallDescription());
    }

    @SuppressWarnings("unchecked")
    private static MarketSellerPlan sellerPlanFrom(Map<String, Object> value) {
        List<MarketSellerPlan.NpcSale> npcSales = ((List<Map<String, Object>>) value.get("npcSales")).stream()
                .map(row -> new MarketSellerPlan.NpcSale(integer(row, "npcId"),
                        InventoryType.valueOf(text(row, "inventoryType")), (short) integer(row, "slot"),
                        (short) integer(row, "quantity"), integer(row, "itemId"), text(row, "reason"),
                        text(row, "evidence"))).toList();
        List<AgentFreeMarketStallService.Listing> listings =
                ((List<Map<String, Object>>) value.get("stallListings")).stream()
                        .map(row -> new AgentFreeMarketStallService.Listing(
                                InventoryType.valueOf(text(row, "inventoryType")),
                                (short) integer(row, "slot"), (short) integer(row, "perBundle"),
                                (short) integer(row, "bundles"), integer(row, "price"))).toList();
        return new MarketSellerPlan(npcSales, listings, integer(value, "preferredRoomMapId"),
                text(value, "stallDescription"));
    }

    private static String text(Map<String, Object> value, String key) { return value.get(key).toString(); }
    private static int integer(Map<String, Object> value, String key) { return ((Number) value.get(key)).intValue(); }
    private static long number(Map<String, Object> value, String key) { return ((Number) value.get(key)).longValue(); }

    private boolean attemptObservedPurchase(Character agent, CommerceParticipant profile, Instant logicalAt,
                                            State state, List<CosmicMarketObservationService.ObservedOffer> offers) {
        List<AgentNeed> currentNeeds = observedNeeds.augment(agent, profile,
                offers.stream().map(CosmicMarketObservationService.ObservedOffer::observation).toList(),
                needs.read(agent, profile, logicalAt), logicalAt);
        Optional<ObservedPurchasePolicy.Decision> decision = purchasePolicy.choose(
                offers, currentNeeds, profile, agent.getMeso());
        if (decision.isEmpty()) {
            appendDecision(profile, logicalAt, "OBSERVED_PURCHASE", Map.of("action", "PASS"),
                    offerAlternatives(offers), Map.of("observationIds", offers.stream()
                            .map(o -> o.observation().observationId()).toList()),
                    Map.of("needs", currentNeeds.stream().map(AgentNeed::evidence).toList()), Map.of());
            return false;
        }
        ObservedPurchasePolicy.Decision chosen = decision.orElseThrow();
        FreeMarketPhysicalGateway.PurchaseStatus result = physical.buyObserved(agent, profile.agentId(),
                chosen.offer(), chosen.bundles(), logicalAt, state.knowledge);
        appendDecision(profile, logicalAt, "OBSERVED_PURCHASE",
                Map.of("action", "BUY", "observationId", chosen.offer().observation().observationId(),
                        "bundles", chosen.bundles(), "result", result.result()),
                offerAlternatives(offers), Map.of("source", "PRIVATE_PHYSICAL_OBSERVATION"),
                Map.of("reason", chosen.reason().name(), "evidence", chosen.evidence()),
                Map.of("score", chosen.score(), "totalPrice", (double) chosen.totalPrice()));
        if (result.success() && sessionConfig.exitWhenPrimaryGoalsComplete
                && needs.read(agent, profile, logicalAt).stream().noneMatch(need -> need.deficit() > 0))
            state.trip.stop();
        return result.success();
    }

    private void appendNegotiation(CommerceParticipant profile, Instant logicalAt,
                                   NegotiationBehavior.Result negotiated) {
        appendDecision(profile, logicalAt, "PUBLIC_NEGOTIATION",
                Map.of("sessionId", negotiated.sessionId(), "outcome", negotiated.outcome(),
                        "success", negotiated.success()), alternatives("WALK_AWAY",
                        "Keep searching physically observed PlayerShop listings"), negotiated.evidence(),
                Map.of("itemId", negotiated.itemId()),
                Map.of("offeredMesos", (double) negotiated.offeredMesos()));
    }

    private void appendOpenChatDecision(CommerceParticipant profile, Instant logicalAt, String kind,
                                        OpenChatBehavior.Result result) {
        appendDecision(profile, logicalAt, kind,
                Map.of("offerId", result.offerId(), "outcome", result.outcome(),
                        "sold", result.sold()),
                alternatives("CONTINUE_MARKET_SEARCH",
                        "Retain the holding or liquidity and continue the bounded market visit"),
                result.evidence(), Map.of("itemId", result.itemId()),
                Map.of("mesos", (double) result.mesos()));
    }

    private void finalizeStallParticipation(Character agent, CommerceParticipant profile, State state) {
        if (state.stallParticipationFinalized) return;
        state.stallParticipationFinalized = true;
        if (!seller.hasPlayerShopPermit(agent)
                || random.stream("agent." + profile.agentId() + ".stall-participation").nextDouble()
                > profile.stallWillingness())
            state.sellerPlan = new MarketSellerPlan(state.sellerPlan.npcSales(), List.of(),
                    state.sellerPlan.preferredRoomMapId(), state.sellerPlan.stallDescription());
    }

    private List<String> entryGoals(Character agent, CommerceParticipant profile, Instant at) {
        LinkedHashSet<String> goals = new LinkedHashSet<>();
        for (AgentNeed need : needs.read(agent, profile, at)) if (need.deficit() > 0) {
            goals.add(switch (need.reason()) {
                case CONSUMABLE_RESTOCK, AMMUNITION_RESTOCK -> "BUY_RESOURCES";
                case QUEST_REQUIREMENT -> "FIND_QUEST_ITEM";
                case EQUIPMENT_UPGRADE -> "FIND_UPGRADE";
                case SCROLL_UPGRADE -> "FIND_SCROLL";
                case COLLECTIBLE_OR_CHAIR -> "FIND_COLLECTIBLE";
                default -> "BUY_NEEDED_ITEM";
            });
        }
        boolean reviewable = List.of(InventoryType.EQUIP, InventoryType.USE,
                        InventoryType.SETUP, InventoryType.ETC).stream()
                .anyMatch(type -> agent.getInventory(type).list().stream()
                        .anyMatch(item -> item.getQuantity() > 0));
        if (reviewable) goals.add("APPRAISE_AND_SELL_SURPLUS");
        if (sessionConfig.knowledgeOnlyBrowsingEnabled) goals.add("PRICE_DISCOVERY");
        return List.copyOf(goals);
    }

    private void appendDecision(CommerceParticipant profile, Instant at, String kind,
                                Map<String, Object> action, List<Map<String, Object>> alternatives,
                                Map<String, Object> beliefs, Map<String, Object> needs,
                                Map<String, Object> utility) {
        String raw = runId + ":" + profile.agentId() + ':' + at + ':' + kind + ':' + action;
        journal.appendDecision(new DecisionEvidence(UUID.nameUUIDFromBytes(raw.getBytes(StandardCharsets.UTF_8)),
                runId, profile.agentId(), at, kind, action, alternatives, beliefs, needs, utility,
                null, null, configHash, catalogVersion));
        if (!"AMBIENT_MARKET_ACTION".equals(kind))
            progressRevisions.merge(profile.agentId(), 1L, Long::sum);
    }

    private static List<Map<String, Object>> offerAlternatives(
            List<CosmicMarketObservationService.ObservedOffer> offers) {
        return offers.stream().map(value -> Map.<String, Object>of(
                "observationId", value.observation().observationId(),
                "itemId", value.observation().itemId(), "bundlePrice", value.observation().bundlePrice(),
                "bundles", value.observation().bundles())).toList();
    }

    private static List<Map<String, Object>> alternatives(String action, String rejectionReason) {
        return List.of(Map.of("action", action, "rejectionReason", rejectionReason));
    }

    @FunctionalInterface public interface AgentNeedReader {
        List<AgentNeed> read(Character agent, CommerceParticipant profile, Instant logicalAt);
    }
    @FunctionalInterface public interface ObservedNeedAugmenter {
        List<AgentNeed> augment(Character agent, CommerceParticipant profile,
                               List<MarketObservation> observations, List<AgentNeed> base,
                               Instant logicalAt);
    }
    @FunctionalInterface public interface AmbientBehavior {
        Result perform(Character agent, CommerceParticipant profile, Instant logicalAt,
                       boolean ownsOpenStall, boolean negotiating, int consecutiveActions);
        static AmbientBehavior disabled() { return (agent, profile, at, stall, negotiating, count) -> Result.none(); }
        record Result(boolean attempted, boolean success, String action, String reason,
                      Integer chairItemId, Map<String, Object> evidence) {
            public Result { evidence = evidence == null ? Map.of() : Map.copyOf(evidence); }
            public static Result none() { return new Result(false, false, "NONE", "", null, Map.of()); }
        }
    }
    @FunctionalInterface public interface NegotiationBehavior {
        Result attempt(Character agent, CommerceParticipant profile, List<AgentNeed> needs,
                       List<MarketObservation> observations, Instant logicalAt);
        static NegotiationBehavior disabled() { return (agent, profile, needs, observations, at) -> Result.none(); }
        record Result(boolean attempted, boolean success, String sessionId, String outcome,
                      int itemId, long offeredMesos, Map<String, Object> evidence) {
            public Result { evidence = evidence == null ? Map.of() : Map.copyOf(evidence); }
            public static Result none() { return new Result(false, false, "", "NONE", 0, 0, Map.of()); }
        }
    }
    @FunctionalInterface public interface OfferReviewBehavior {
        Result reviewNext(Character seller, CommerceParticipant profile, Instant logicalAt);
        static OfferReviewBehavior disabled() { return (seller, profile, at) -> Result.none(); }
        record Result(boolean attempted, boolean accepted, String offerId, String outcome,
                      int itemId, Map<String, Object> evidence) {
            public Result { evidence = evidence == null ? Map.of() : Map.copyOf(evidence); }
            public static Result none() { return new Result(false, false, "", "NONE", 0, Map.of()); }
        }
    }
    @FunctionalInterface public interface ScrollBehavior {
        Result applyNext(Character agent, CommerceParticipant profile,
                         List<AgentNeed> needs, Instant logicalAt);
        static ScrollBehavior disabled() { return (agent, profile, needs, at) -> Result.none(); }
        record Result(boolean attempted, boolean success, int scrollItemId,
                      int equipmentItemId, String outcome, Map<String, Object> evidence) {
            public Result { evidence = evidence == null ? Map.of() : Map.copyOf(evidence); }
            public static Result none() { return new Result(false, false, 0, 0, "NONE", Map.of()); }
        }
    }
    @FunctionalInterface public interface QuestBehavior {
        Result advance(Character agent, CommerceParticipant profile, Instant logicalAt);
        static QuestBehavior disabled() { return (agent, profile, at) -> Result.none(); }
        record Result(boolean attempted, boolean success, String action, int questId,
                      int npcId, Integer selection, Map<String, Object> evidence) {
            public Result { evidence = evidence == null ? Map.of() : Map.copyOf(evidence); }
            public static Result none() {
                return new Result(false, false, "NONE", 0, 0, null, Map.of());
            }
        }
    }
    @FunctionalInterface public interface ArrangementBehavior {
        Result progress(Character agent, CommerceParticipant profile, Instant logicalAt);
        static ArrangementBehavior disabled() { return (agent, profile, at) -> Result.none(); }
        record Result(boolean attempted, boolean completed, boolean externalActionPending,
                      String arrangementId, String outcome, int itemId, Map<String, Object> evidence) {
            public Result { evidence = evidence == null ? Map.of() : Map.copyOf(evidence); }
            public static Result none() {
                return new Result(false, false, false, "", "NONE", 0, Map.of());
            }
        }
    }
    public interface OpenChatBehavior {
        Preparation prepare(Character seller, CommerceParticipant profile,
                            MarketSellerPlan plan, Instant logicalAt);
        Result progressSeller(Character seller, CommerceParticipant profile, Instant logicalAt);
        Result attemptPurchase(Character buyer, CommerceParticipant profile,
                               List<AgentNeed> needs, Instant logicalAt);
        void cancel(String sellerAgentId, Instant logicalAt, String reason);
        default Map<String, Object> snapshotState() { return Map.of(); }
        default void restoreState(Map<String, Object> snapshot) {
            if (snapshot != null && !snapshot.isEmpty())
                throw new IllegalStateException("open-chat behavior does not support checkpoint restore");
        }

        static OpenChatBehavior disabled() {
            return new OpenChatBehavior() {
                @Override public Preparation prepare(Character seller, CommerceParticipant profile,
                        MarketSellerPlan plan, Instant logicalAt) { return Preparation.none(plan); }
                @Override public Result progressSeller(Character seller, CommerceParticipant profile,
                        Instant logicalAt) { return Result.none(); }
                @Override public Result attemptPurchase(Character buyer, CommerceParticipant profile,
                        List<AgentNeed> needs, Instant logicalAt) { return Result.none(); }
                @Override public void cancel(String sellerAgentId, Instant logicalAt, String reason) { }
            };
        }

        record Preparation(MarketSellerPlan plan, boolean selected, String offerId,
                           int itemId, int quantity, long askMesos, long reserveMesos) {
            public Preparation { Objects.requireNonNull(plan); offerId = offerId == null ? "" : offerId; }
            public static Preparation none(MarketSellerPlan plan) {
                return new Preparation(plan, false, "", 0, 0, 0, 0);
            }
        }
        record Result(boolean attempted, boolean done, boolean sold, String outcome,
                      String offerId, int itemId, long mesos, Map<String, Object> evidence) {
            public Result {
                outcome = outcome == null ? "" : outcome; offerId = offerId == null ? "" : offerId;
                evidence = evidence == null ? Map.of() : Map.copyOf(evidence);
            }
            public static Result none() {
                return new Result(false, false, false, "NONE", "", 0, 0, Map.of());
            }
        }
    }
    private EconomyWorldPort.MarketDirective revisit(Instant at, boolean externalPending) {
        return revisitAfter(at, actionPoll, externalPending);
    }
    private EconomyWorldPort.MarketDirective revisitAfter(Instant at, Duration delay, boolean externalPending) {
        return new EconomyWorldPort.MarketDirective(Optional.empty(), Optional.of(at.plus(delay)), externalPending);
    }
    private EconomyWorldPort.MarketDirective finish(String agentId, Instant at) {
        openChat.cancel(agentId, at, "ECONOMY_SESSION_FINISHED");
        states.remove(agentId);
        sellerPlans.releaseRoom(agentId);
        return new EconomyWorldPort.MarketDirective(Optional.of(at.plus(postTripDelay)), Optional.empty());
    }
    private static EconomyEngineConfig.Session defaultSession() {
        EconomyEngineConfig.Session value = new EconomyEngineConfig.Session();
        value.defaultMaximumDuration = "PT30M";
        value.maximumIdleDuration = "PT5M";
        value.exitWhenPrimaryGoalsComplete = true;
        value.maximumConsecutiveUnproductiveStalls = 12;
        value.knowledgeOnlyBrowsingEnabled = true;
        value.implicitEconomicIntentsEnabled = true;
        return value;
    }
    @FunctionalInterface public interface ResourceProcurement {
        Optional<Result> buyNext(Character agent, CommerceParticipant profile, Set<Integer> attemptedItemIds);
        record Result(int itemId, int quantity, int npcId, boolean success, String result,
                      int mesoDelta, int sourceMapId, String commerceAction) {
            public Result(int itemId, int quantity, int npcId, boolean success, String result,
                          int mesoDelta, int sourceMapId) {
                this(itemId, quantity, npcId, success, result, mesoDelta, sourceMapId, "BUY");
            }
        }
    }
    private enum Phase { PROCURING, BROWSING, DISPOSING, OPEN_CHAT_SELLING, OPENING_STALL, OWNING_STALL }
    private static final class State {
        private final PrivateMarketKnowledge knowledge;
        private PhysicalMarketTrip trip;
        private Phase phase = Phase.PROCURING;
        private final Set<Integer> attemptedResourceItems = new HashSet<>();
        private MarketSellerPlan sellerPlan;
        private int npcSaleIndex;
        private int openAttempts;
        private int repriceCount;
        private boolean questEvaluated;
        private boolean entryAppraised;
        private boolean entryPlanned;
        private List<String> entryGoals = List.of();
        private int consecutiveAmbientActions;
        private int consecutiveUnproductiveStalls;
        private boolean openChatPrepared;
        private boolean stallParticipationFinalized;
        private Instant stallOpenedAt;
        private State(PrivateMarketKnowledge knowledge, PhysicalMarketTrip trip) {
            this.knowledge = knowledge; this.trip = trip;
        }
        private void prepareReprice(List<Integer> rooms) {
            phase = Phase.BROWSING;
            trip = new PhysicalMarketTrip(rooms, Duration.ofMillis(
                    trip.snapshot().inspectionMillisPerListing()));
            sellerPlan = null;
            npcSaleIndex = 0;
            openAttempts = 0;
            consecutiveAmbientActions = 0;
            openChatPrepared = false;
            stallParticipationFinalized = false;
            stallOpenedAt = null;
            repriceCount++;
        }
    }
}
