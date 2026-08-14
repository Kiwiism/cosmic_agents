package server.agents.economy.integration.cosmic;

import client.Character;
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
    private final EconomyEvidenceJournal journal;
    private final CosmicMarketSellerPlanReader sellerPlans;
    private final CosmicMarketSellerGateway seller;
    private final ResourceProcurement procurement;
    private final ObservedPurchasePolicy purchasePolicy = new ObservedPurchasePolicy();
    private final RoomVisitPlanner roomPlanner = new RoomVisitPlanner();
    private final Duration actionPoll;
    private final Duration postTripDelay;
    private final Duration maximumStallDuration;
    private final Map<String, State> states = new ConcurrentHashMap<>();

    public AutonomousFreeMarketBehavior(UUID runId, String configHash, String catalogVersion,
                                        EconomyEngineConfig.Market config, NamedRandomStreams random,
                                        FreeMarketPhysicalGateway physical, AgentNeedReader needs,
                                        EconomyEvidenceJournal journal,
                                        CosmicMarketSellerPlanReader sellerPlans,
                                        CosmicMarketSellerGateway seller,
                                        ResourceProcurement procurement,
                                        Duration actionPoll, Duration postTripDelay,
                                        Duration maximumStallDuration) {
        this.runId = Objects.requireNonNull(runId); this.configHash = Objects.requireNonNull(configHash);
        this.catalogVersion = Objects.requireNonNull(catalogVersion); this.config = Objects.requireNonNull(config);
        this.random = Objects.requireNonNull(random); this.physical = Objects.requireNonNull(physical);
        this.needs = Objects.requireNonNull(needs); this.journal = Objects.requireNonNull(journal);
        this.sellerPlans = Objects.requireNonNull(sellerPlans); this.seller = Objects.requireNonNull(seller);
        this.procurement = Objects.requireNonNull(procurement);
        if (actionPoll.isNegative() || actionPoll.isZero() || postTripDelay.isNegative()
                || maximumStallDuration.isNegative() || maximumStallDuration.isZero())
            throw new IllegalArgumentException("market timing must be non-negative and polling positive");
        this.actionPoll = actionPoll; this.postTripDelay = postTripDelay;
        this.maximumStallDuration = maximumStallDuration;
    }

    @Override
    public EconomyWorldPort.MarketDirective perform(Character agent, EconomyAgentProfile profile,
                                                     Instant logicalAt) {
        State state = states.computeIfAbsent(profile.agentId(), ignored -> new State(
                new PrivateMarketKnowledge(), new PhysicalMarketTrip(roomPlanner.plan(
                config.minimumRoomsPerTrip, config.maximumRoomsPerTrip, random))));
        if (state.phase == Phase.PROCURING) {
            Optional<ResourceProcurement.Result> purchase = procurement.buyNext(
                    agent, profile, state.attemptedResourceItems);
            if (purchase.isPresent()) {
                ResourceProcurement.Result result = purchase.orElseThrow();
                state.attemptedResourceItems.add(result.itemId());
                appendDecision(profile, logicalAt, "NPC_RESOURCE_PROCUREMENT",
                        Map.of("itemId", result.itemId(), "quantity", result.quantity(),
                                "npcId", result.npcId(), "result", result.result()), List.of(),
                        Map.of("sourceMap", result.sourceMapId()),
                        Map.of("reason", "CONFIGURED_RESOURCE_TARGET"),
                        Map.of("mesoDelta", (double) result.mesoDelta()));
                return revisit(logicalAt, false);
            }
            state.phase = Phase.BROWSING;
        }
        if (state.phase == Phase.BROWSING) {
            PhysicalMarketTrip.Step step = state.trip.tick(agent, profile.agentId(), logicalAt,
                    state.knowledge, physical);
            if (!step.offers().isEmpty()) attemptObservedPurchase(agent, profile, logicalAt, state, step.offers());
            if (step.status() == PhysicalMarketTrip.Status.COMPLETE) {
                List<AgentNeed> currentNeeds = needs.read(agent, profile, logicalAt);
                state.sellerPlan = sellerPlans.read(agent, profile, state.knowledge, currentNeeds, logicalAt);
                if (random.stream("agent." + profile.agentId() + ".stall-participation").nextDouble()
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
                return revisit(logicalAt, false);
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
            if (logicalAt.isBefore(state.stallOpenedAt.plus(maximumStallDuration))) {
                return new EconomyWorldPort.MarketDirective(Optional.empty(),
                        Optional.of(state.stallOpenedAt.plus(maximumStallDuration)), false);
            }
            boolean closed = seller.close(agent);
            appendDecision(profile, logicalAt, "STALL_CLOSED", Map.of("closed", closed),
                    List.of(), Map.of(), Map.of("reason", "MAXIMUM_LISTING_DURATION"), Map.of());
            if (closed) return finish(profile.agentId(), logicalAt);
        }
        return revisit(logicalAt, false);
    }

    private void attemptObservedPurchase(Character agent, EconomyAgentProfile profile, Instant logicalAt,
                                         State state, List<CosmicMarketObservationService.ObservedOffer> offers) {
        List<AgentNeed> currentNeeds = needs.read(agent, profile, logicalAt);
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
    private EconomyWorldPort.MarketDirective revisit(Instant at, boolean externalPending) {
        return new EconomyWorldPort.MarketDirective(Optional.empty(), Optional.of(at.plus(actionPoll)),
                externalPending);
    }
    private EconomyWorldPort.MarketDirective finish(String agentId, Instant at) {
        states.remove(agentId);
        return new EconomyWorldPort.MarketDirective(Optional.of(at.plus(postTripDelay)), Optional.empty());
    }
    @FunctionalInterface public interface ResourceProcurement {
        Optional<Result> buyNext(Character agent, EconomyAgentProfile profile, Set<Integer> attemptedItemIds);
        record Result(int itemId, int quantity, int npcId, boolean success, String result,
                      int mesoDelta, int sourceMapId) { }
    }
    private enum Phase { PROCURING, BROWSING, DISPOSING, OPENING_STALL, OWNING_STALL }
    private static final class State {
        private final PrivateMarketKnowledge knowledge;
        private final PhysicalMarketTrip trip;
        private Phase phase = Phase.PROCURING;
        private final Set<Integer> attemptedResourceItems = new HashSet<>();
        private MarketSellerPlan sellerPlan;
        private int npcSaleIndex;
        private int openAttempts;
        private Instant stallOpenedAt;
        private State(PrivateMarketKnowledge knowledge, PhysicalMarketTrip trip) {
            this.knowledge = knowledge; this.trip = trip;
        }
    }
}
