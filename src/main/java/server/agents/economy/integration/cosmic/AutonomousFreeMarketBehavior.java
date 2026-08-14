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
    private final NamedRandomStreams random;
    private final FreeMarketPhysicalGateway physical;
    private final AgentNeedReader needs;
    private final ObservedNeedAugmenter observedNeeds;
    private final EconomyEvidenceJournal journal;
    private final CosmicMarketSellerPlanReader sellerPlans;
    private final CosmicMarketSellerGateway seller;
    private final ResourceProcurement procurement;
    private final AmbientBehavior ambient;
    private final NegotiationBehavior negotiation;
    private final ScrollBehavior scrolling;
    private final QuestBehavior quests;
    private final ObservedPurchasePolicy purchasePolicy = new ObservedPurchasePolicy();
    private final RoomVisitPlanner roomPlanner = new RoomVisitPlanner();
    private final Duration actionPoll;
    private final Duration postTripDelay;
    private final Duration maximumStallDuration;
    private final Duration minimumRepriceInterval;
    private final Duration npcServiceDelay;
    private final Map<String, State> states = new ConcurrentHashMap<>();

    public AutonomousFreeMarketBehavior(UUID runId, String configHash, String catalogVersion,
                                        EconomyEngineConfig.Market config, NamedRandomStreams random,
                                        FreeMarketPhysicalGateway physical, AgentNeedReader needs,
                                        EconomyEvidenceJournal journal,
                                        CosmicMarketSellerPlanReader sellerPlans,
                                        CosmicMarketSellerGateway seller,
                                        ResourceProcurement procurement,
                                        Duration actionPoll, Duration postTripDelay,
                                        Duration maximumStallDuration, Duration npcServiceDelay) {
        this(runId, configHash, catalogVersion, config, random, physical, needs,
                (agent, profile, observations, base, at) -> base, journal, sellerPlans, seller,
                procurement, AmbientBehavior.disabled(), NegotiationBehavior.disabled(),
                ScrollBehavior.disabled(), QuestBehavior.disabled(),
                actionPoll, postTripDelay, maximumStallDuration, npcServiceDelay);
    }

    public AutonomousFreeMarketBehavior(UUID runId, String configHash, String catalogVersion,
                                        EconomyEngineConfig.Market config, NamedRandomStreams random,
                                        FreeMarketPhysicalGateway physical, AgentNeedReader needs,
                                        ObservedNeedAugmenter observedNeeds,
                                        EconomyEvidenceJournal journal,
                                        CosmicMarketSellerPlanReader sellerPlans,
                                        CosmicMarketSellerGateway seller,
                                        ResourceProcurement procurement,
                                        AmbientBehavior ambient,
                                        NegotiationBehavior negotiation,
                                        ScrollBehavior scrolling,
                                        QuestBehavior quests,
                                        Duration actionPoll, Duration postTripDelay,
                                        Duration maximumStallDuration, Duration npcServiceDelay) {
        this.runId = Objects.requireNonNull(runId); this.configHash = Objects.requireNonNull(configHash);
        this.catalogVersion = Objects.requireNonNull(catalogVersion); this.config = Objects.requireNonNull(config);
        this.random = Objects.requireNonNull(random); this.physical = Objects.requireNonNull(physical);
        this.needs = Objects.requireNonNull(needs); this.observedNeeds = Objects.requireNonNull(observedNeeds);
        this.journal = Objects.requireNonNull(journal);
        this.sellerPlans = Objects.requireNonNull(sellerPlans); this.seller = Objects.requireNonNull(seller);
        this.procurement = Objects.requireNonNull(procurement);
        this.ambient = Objects.requireNonNull(ambient);
        this.negotiation = Objects.requireNonNull(negotiation);
        this.scrolling = Objects.requireNonNull(scrolling);
        this.quests = Objects.requireNonNull(quests);
        if (actionPoll.isNegative() || actionPoll.isZero() || postTripDelay.isNegative()
                || maximumStallDuration.isNegative() || maximumStallDuration.isZero())
            throw new IllegalArgumentException("market timing must be non-negative and polling positive");
        this.actionPoll = actionPoll; this.postTripDelay = postTripDelay;
        this.maximumStallDuration = maximumStallDuration;
        this.minimumRepriceInterval = Duration.parse(config.minimumRepriceInterval);
        if (npcServiceDelay.isNegative()) throw new IllegalArgumentException("NPC service delay cannot be negative");
        this.npcServiceDelay = npcServiceDelay;
    }

    @Override
    public synchronized EconomyWorldPort.MarketDirective perform(Character agent, EconomyAgentProfile profile,
                                                                  Instant logicalAt) {
        State state = states.computeIfAbsent(profile.agentId(), ignored -> new State(
                new PrivateMarketKnowledge(), new PhysicalMarketTrip(roomPlanner.plan(
                config.minimumRoomsPerTrip, config.maximumRoomsPerTrip, random))));
        if (!state.questEvaluated) {
            state.questEvaluated = true;
            QuestBehavior.Result quest = quests.advance(agent, profile, logicalAt);
            if (quest.attempted()) {
                appendDecision(profile, logicalAt, "QUEST_" + quest.action(),
                        Map.of("success", quest.success(), "questId", quest.questId(),
                                "npcId", quest.npcId(), "selection", quest.selection() == null
                                        ? -1 : quest.selection()), List.of(), quest.evidence(),
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
                                "commerceAction", result.commerceAction()), List.of(),
                        Map.of("sourceMap", result.sourceMapId()),
                        Map.of("reason", "CONFIGURED_RESOURCE_TARGET"),
                        Map.of("mesoDelta", (double) result.mesoDelta()));
                return revisitAfter(logicalAt, npcServiceDelay, false);
            }
            state.phase = Phase.BROWSING;
        }
        if (state.phase == Phase.BROWSING) {
            PhysicalMarketTrip.Step step = state.trip.tick(agent, profile.agentId(), logicalAt,
                    state.knowledge, physical);
            if (!step.offers().isEmpty()) attemptObservedPurchase(agent, profile, logicalAt, state, step.offers());
            if (step.status() == PhysicalMarketTrip.Status.COMPLETE) {
                List<AgentNeed> currentNeeds = observedNeeds.augment(agent, profile,
                        state.knowledge.snapshot(), needs.read(agent, profile, logicalAt), logicalAt);
                ScrollBehavior.Result scrolled = scrolling.applyNext(agent, profile, currentNeeds, logicalAt);
                if (scrolled.attempted()) {
                    appendDecision(profile, logicalAt, "SCROLL_PROJECT",
                            Map.of("success", scrolled.success(), "outcome", scrolled.outcome(),
                                    "scrollItemId", scrolled.scrollItemId(),
                                    "equipmentItemId", scrolled.equipmentItemId()),
                            List.of(), scrolled.evidence(),
                            Map.of("reason", "SCROLL_UPGRADE"), Map.of());
                    return revisit(logicalAt, !scrolled.success());
                }
                NegotiationBehavior.Result negotiated = negotiation.attempt(agent, profile,
                        currentNeeds, state.knowledge.snapshot(), logicalAt);
                if (negotiated.attempted()) appendDecision(profile, logicalAt, "PUBLIC_NEGOTIATION",
                        Map.of("sessionId", negotiated.sessionId(), "outcome", negotiated.outcome(),
                                "success", negotiated.success()), List.of(), negotiated.evidence(),
                        Map.of("itemId", negotiated.itemId()), Map.of("offeredMesos", (double) negotiated.offeredMesos()));
                state.sellerPlan = sellerPlans.read(agent, profile, state.knowledge, currentNeeds, logicalAt);
                if (!seller.hasPlayerShopPermit(agent)
                        || random.stream("agent." + profile.agentId() + ".stall-participation").nextDouble()
                        > profile.stallWillingness()) {
                    state.sellerPlan = new MarketSellerPlan(state.sellerPlan.npcSales(), List.of(),
                            state.sellerPlan.preferredRoomMapId(), state.sellerPlan.stallDescription());
                }
                state.phase = Phase.DISPOSING;
                return revisit(logicalAt, false);
            }
            if (step.status() == PhysicalMarketTrip.Status.BLOCKED) {
                appendDecision(profile, logicalAt, "MARKET_TRIP_BLOCKED", Map.of("room", step.roomMapId()),
                        List.of(), Map.of(), Map.of(), Map.of("result", 0d));
            }
            return revisit(logicalAt, step.status() == PhysicalMarketTrip.Status.PHYSICAL_ACTION_PENDING);
        }
        if (state.phase == Phase.DISPOSING) {
            if (state.npcSaleIndex < state.sellerPlan.npcSales().size()) {
                MarketSellerPlan.NpcSale sale = state.sellerPlan.npcSales().get(state.npcSaleIndex++);
                RemoteNpcCommerceService.Receipt receipt = seller.sellNpc(agent, sale);
                appendDecision(profile, logicalAt, "NPC_DISPOSITION",
                        Map.of("itemId", sale.itemId(), "quantity", sale.quantity(), "npcId", sale.npcId(),
                                "result", receipt.result()), List.of(), Map.of("sourceMap", receipt.sourceMapId()),
                        Map.of("reason", sale.reason(), "evidence", sale.evidence()), Map.of());
                return revisitAfter(logicalAt, npcServiceDelay, false);
            }
            if (state.sellerPlan.stallListings().isEmpty()) return finish(profile.agentId(), logicalAt);
            state.phase = Phase.OPENING_STALL;
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
                        List.of(), Map.of("source", "PRIVATE_OBSERVATIONS"), Map.of(), Map.of());
            } else if (opened == FreeMarketPhysicalGateway.ActionStatus.UNAVAILABLE || state.openAttempts >= 3) {
                appendDecision(profile, logicalAt, "STALL_OPEN_FAILED", Map.of("result", opened.name()),
                        List.of(), Map.of(), Map.of("permitPolicy", "REQUIRE_OWNED_REAL_ITEM"), Map.of());
                return finish(profile.agentId(), logicalAt);
            }
            return revisit(logicalAt, opened == FreeMarketPhysicalGateway.ActionStatus.ASSIGNED
                    || opened == FreeMarketPhysicalGateway.ActionStatus.IN_PROGRESS);
        }
        if (state.phase == Phase.OWNING_STALL) {
            if (agent.getPlayerShop() == null || !agent.getPlayerShop().isOpen())
                return finish(profile.agentId(), logicalAt);
            Instant repriceAt = state.stallOpenedAt.plus(minimumRepriceInterval);
            if (state.repriceCount < config.maximumReprices && !logicalAt.isBefore(repriceAt)) {
                boolean closed = seller.close(agent, "REPRICE_RESEARCH");
                appendDecision(profile, logicalAt, "STALL_REPRICE_RESEARCH",
                        Map.of("closed", closed, "reprice", state.repriceCount + 1), List.of(),
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
                            Map.of("action", result.action(), "success", result.success()), List.of(),
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
                    List.of(), Map.of(), Map.of("reason", "MAXIMUM_LISTING_DURATION"), Map.of());
            if (closed) return finish(profile.agentId(), logicalAt);
        }
        return revisit(logicalAt, false);
    }

    @Override
    public synchronized Map<String, Object> snapshotState() {
        Map<String, Object> encodedStates = new TreeMap<>();
        states.forEach((agentId, state) -> encodedStates.put(agentId, stateMap(state)));
        return Map.of("schemaVersion", 1, "agents", encodedStates, "randomStates", random.snapshot());
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
        Map<String, Object> encodedStates = (Map<String, Object>) snapshot.get("agents");
        encodedStates.forEach((agentId, value) -> states.put(agentId,
                stateFrom((Map<String, Object>) value)));
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
        if (trip.approachingObjectId() != null)
            tripValue.put("approachingObjectId", trip.approachingObjectId());
        value.put("trip", tripValue);
        value.put("attemptedResourceItems", state.attemptedResourceItems.stream().sorted().toList());
        value.put("sellerPlan", state.sellerPlan == null ? Map.of() : sellerPlanMap(state.sellerPlan));
        value.put("npcSaleIndex", state.npcSaleIndex); value.put("openAttempts", state.openAttempts);
        value.put("repriceCount", state.repriceCount);
        value.put("questEvaluated", state.questEvaluated);
        value.put("consecutiveAmbientActions", state.consecutiveAmbientActions);
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
        State state = new State(PrivateMarketKnowledge.restore(observations), PhysicalMarketTrip.restore(
                new PhysicalMarketTrip.Snapshot(rooms, inspected, integer(tripValue, "roomIndex"), approaching)));
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
        state.consecutiveAmbientActions = value.containsKey("consecutiveAmbientActions")
                ? integer(value, "consecutiveAmbientActions") : 0;
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

    private void attemptObservedPurchase(Character agent, EconomyAgentProfile profile, Instant logicalAt,
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
            return;
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
    }

    private void appendDecision(EconomyAgentProfile profile, Instant at, String kind,
                                Map<String, Object> action, List<Map<String, Object>> alternatives,
                                Map<String, Object> beliefs, Map<String, Object> needs,
                                Map<String, Object> utility) {
        String raw = runId + ":" + profile.agentId() + ':' + at + ':' + kind + ':' + action;
        journal.appendDecision(new DecisionEvidence(UUID.nameUUIDFromBytes(raw.getBytes(StandardCharsets.UTF_8)),
                runId, profile.agentId(), at, kind, action, alternatives, beliefs, needs, utility,
                null, null, configHash, catalogVersion));
    }

    private static List<Map<String, Object>> offerAlternatives(
            List<CosmicMarketObservationService.ObservedOffer> offers) {
        return offers.stream().map(value -> Map.<String, Object>of(
                "observationId", value.observation().observationId(),
                "itemId", value.observation().itemId(), "bundlePrice", value.observation().bundlePrice(),
                "bundles", value.observation().bundles())).toList();
    }

    @FunctionalInterface public interface AgentNeedReader {
        List<AgentNeed> read(Character agent, EconomyAgentProfile profile, Instant logicalAt);
    }
    @FunctionalInterface public interface ObservedNeedAugmenter {
        List<AgentNeed> augment(Character agent, EconomyAgentProfile profile,
                               List<MarketObservation> observations, List<AgentNeed> base,
                               Instant logicalAt);
    }
    @FunctionalInterface public interface AmbientBehavior {
        Result perform(Character agent, EconomyAgentProfile profile, Instant logicalAt,
                       boolean ownsOpenStall, boolean negotiating, int consecutiveActions);
        static AmbientBehavior disabled() { return (agent, profile, at, stall, negotiating, count) -> Result.none(); }
        record Result(boolean attempted, boolean success, String action, String reason,
                      Integer chairItemId, Map<String, Object> evidence) {
            public Result { evidence = evidence == null ? Map.of() : Map.copyOf(evidence); }
            public static Result none() { return new Result(false, false, "NONE", "", null, Map.of()); }
        }
    }
    @FunctionalInterface public interface NegotiationBehavior {
        Result attempt(Character agent, EconomyAgentProfile profile, List<AgentNeed> needs,
                       List<MarketObservation> observations, Instant logicalAt);
        static NegotiationBehavior disabled() { return (agent, profile, needs, observations, at) -> Result.none(); }
        record Result(boolean attempted, boolean success, String sessionId, String outcome,
                      int itemId, long offeredMesos, Map<String, Object> evidence) {
            public Result { evidence = evidence == null ? Map.of() : Map.copyOf(evidence); }
            public static Result none() { return new Result(false, false, "", "NONE", 0, 0, Map.of()); }
        }
    }
    @FunctionalInterface public interface ScrollBehavior {
        Result applyNext(Character agent, EconomyAgentProfile profile,
                         List<AgentNeed> needs, Instant logicalAt);
        static ScrollBehavior disabled() { return (agent, profile, needs, at) -> Result.none(); }
        record Result(boolean attempted, boolean success, int scrollItemId,
                      int equipmentItemId, String outcome, Map<String, Object> evidence) {
            public Result { evidence = evidence == null ? Map.of() : Map.copyOf(evidence); }
            public static Result none() { return new Result(false, false, 0, 0, "NONE", Map.of()); }
        }
    }
    @FunctionalInterface public interface QuestBehavior {
        Result advance(Character agent, EconomyAgentProfile profile, Instant logicalAt);
        static QuestBehavior disabled() { return (agent, profile, at) -> Result.none(); }
        record Result(boolean attempted, boolean success, String action, int questId,
                      int npcId, Integer selection, Map<String, Object> evidence) {
            public Result { evidence = evidence == null ? Map.of() : Map.copyOf(evidence); }
            public static Result none() {
                return new Result(false, false, "NONE", 0, 0, null, Map.of());
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
        states.remove(agentId);
        return new EconomyWorldPort.MarketDirective(Optional.of(at.plus(postTripDelay)), Optional.empty());
    }
    @FunctionalInterface public interface ResourceProcurement {
        Optional<Result> buyNext(Character agent, EconomyAgentProfile profile, Set<Integer> attemptedItemIds);
        record Result(int itemId, int quantity, int npcId, boolean success, String result,
                      int mesoDelta, int sourceMapId, String commerceAction) {
            public Result(int itemId, int quantity, int npcId, boolean success, String result,
                          int mesoDelta, int sourceMapId) {
                this(itemId, quantity, npcId, success, result, mesoDelta, sourceMapId, "BUY");
            }
        }
    }
    private enum Phase { PROCURING, BROWSING, DISPOSING, OPENING_STALL, OWNING_STALL }
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
        private int consecutiveAmbientActions;
        private Instant stallOpenedAt;
        private State(PrivateMarketKnowledge knowledge, PhysicalMarketTrip trip) {
            this.knowledge = knowledge; this.trip = trip;
        }
        private void prepareReprice(List<Integer> rooms) {
            phase = Phase.BROWSING;
            trip = new PhysicalMarketTrip(rooms);
            sellerPlan = null;
            npcSaleIndex = 0;
            openAttempts = 0;
            consecutiveAmbientActions = 0;
            stallOpenedAt = null;
            repriceCount++;
        }
    }
}
