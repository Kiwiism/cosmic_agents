package server.agents.economy.integration.cosmic;

import client.Character;
import client.inventory.InventoryType;
import client.inventory.Item;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.ItemInformationProvider;
import server.Trade;
import server.agents.capabilities.contracts.AgentDisposition;
import server.agents.capabilities.contracts.AgentInventoryReservation;
import server.agents.capabilities.inventory.AgentInventoryReservationRuntime;
import server.agents.capabilities.shop.AgentFreeMarketStallService;
import server.agents.economy.communication.EconomicIntent;
import server.agents.economy.decision.AgentNeed;
import server.agents.economy.persistence.EconomyEvidenceJournal;
import server.agents.economy.persistence.SocialEvidence;
import server.agents.economy.scenario.EconomyEngineConfig;
import server.agents.economy.scenario.NamedRandomStreams;
import server.agents.economy.session.CommerceParticipant;
import server.agents.integration.AgentPacketGatewayRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;
import server.economy.EconomyItemEvidence;
import server.economy.EconomyOperationContext;
import server.economy.EconomyOperationMetadata;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Real-inventory, structured-intent public sales that settle only through Cosmic Trade. */
public final class CosmicOpenChatSaleService implements AutonomousFreeMarketBehavior.OpenChatBehavior {
    public static final String RESERVATION_CAPABILITY = "economy-open-chat";
    private static final Logger log = LoggerFactory.getLogger(CosmicOpenChatSaleService.class);

    private final UUID runId;
    private final String configHash;
    private final String catalogVersion;
    private final EconomyEngineConfig.OpenChatSelling config;
    private final NamedRandomStreams random;
    private final EconomyParticipantRegistry participants;
    private final CosmicAgentEconomyFacade economy;
    private final EconomyEvidenceJournal journal;
    private final CosmicNegotiatedTradeExecutor trades;
    private final OpenChatSaleFlavorRenderer flavor;
    private final ReservationGateway reservations;
    private final NpcValueCatalog npcValues;
    private final PublicChat chat;
    private final ApproachGateway approach;
    private final Map<String, Offer> offers = new ConcurrentHashMap<>();
    private final Map<String, Result> terminalResults = new ConcurrentHashMap<>();
    private final Map<String, Boolean> eligibility = new ConcurrentHashMap<>();

    public CosmicOpenChatSaleService(UUID runId, String configHash, String catalogVersion,
                                     EconomyEngineConfig.OpenChatSelling config,
                                     NamedRandomStreams random, EconomyParticipantRegistry participants,
                                     CosmicAgentEconomyFacade economy, EconomyEvidenceJournal journal,
                                     CosmicNegotiatedTradeExecutor trades,
                                     int interactionRangePixels, long approachTimeoutMs) {
        this(runId, configHash, catalogVersion, config, random, participants, economy, journal, trades,
                new ReservationGateway() {
                    @Override public void reserve(Character seller, UUID offerId, int itemId,
                                                  int quantity, Duration lifetime) {
                        AgentRuntimeEntry entry = AgentRuntimeRegistry.findByCharacterInstance(seller);
                        if (entry == null) throw new IllegalStateException(
                                "open-chat seller has no live Agent runtime entry");
                        AgentInventoryReservationRuntime.ledger(entry).reserve(new AgentInventoryReservation(
                                RESERVATION_CAPABILITY + ':' + offerId, itemId, quantity,
                                AgentDisposition.TRADE_RESERVE, RESERVATION_CAPABILITY,
                                "authoritative public sale offer " + offerId, 900,
                                System.currentTimeMillis() + lifetime.toMillis()));
                    }
                    @Override public void release(int characterId) {
                        AgentRuntimeEntry entry = AgentRuntimeRegistry.findByAgentCharacterId(characterId);
                        AgentInventoryReservationRuntime.releaseCapability(entry, RESERVATION_CAPABILITY);
                    }
                }, (itemId, quantity) -> Math.max(0,
                        ItemInformationProvider.getInstance().getPrice(itemId, quantity)),
                (speaker, text) -> AgentPacketGatewayRuntime.packets()
                        .broadcastChatText(speaker, text, false, 1),
                new CosmicCounterpartyApproachService(interactionRangePixels, approachTimeoutMs)::request);
    }

    CosmicOpenChatSaleService(UUID runId, String configHash, String catalogVersion,
                              EconomyEngineConfig.OpenChatSelling config,
                              NamedRandomStreams random, EconomyParticipantRegistry participants,
                              CosmicAgentEconomyFacade economy, EconomyEvidenceJournal journal,
                              CosmicNegotiatedTradeExecutor trades, ReservationGateway reservations,
                              NpcValueCatalog npcValues, PublicChat chat) {
        this(runId, configHash, catalogVersion, config, random, participants, economy, journal,
                trades, reservations, npcValues, chat,
                (buyer, seller) -> CosmicCounterpartyApproachService.Status.ARRIVED);
    }

    CosmicOpenChatSaleService(UUID runId, String configHash, String catalogVersion,
                              EconomyEngineConfig.OpenChatSelling config,
                              NamedRandomStreams random, EconomyParticipantRegistry participants,
                              CosmicAgentEconomyFacade economy, EconomyEvidenceJournal journal,
                              CosmicNegotiatedTradeExecutor trades, ReservationGateway reservations,
                              NpcValueCatalog npcValues, PublicChat chat, ApproachGateway approach) {
        this.runId = Objects.requireNonNull(runId);
        this.configHash = Objects.requireNonNull(configHash);
        this.catalogVersion = Objects.requireNonNull(catalogVersion);
        this.config = Objects.requireNonNull(config);
        this.random = Objects.requireNonNull(random);
        this.participants = Objects.requireNonNull(participants);
        this.economy = Objects.requireNonNull(economy);
        this.journal = Objects.requireNonNull(journal);
        this.trades = Objects.requireNonNull(trades);
        this.reservations = Objects.requireNonNull(reservations);
        this.npcValues = Objects.requireNonNull(npcValues);
        this.chat = Objects.requireNonNull(chat);
        this.approach = Objects.requireNonNull(approach);
        this.flavor = new OpenChatSaleFlavorRenderer(config.flavorTemplate);
    }

    @Override
    public synchronized Preparation prepare(Character seller, CommerceParticipant profile,
                                            MarketSellerPlan plan, Instant logicalAt) {
        if (!config.enabled || offers.containsKey(profile.agentId()) || plan.stallListings().isEmpty()
                || !config.allowedMaps.contains(seller.getMapId())
                || activeInMap(seller.getMapId()) >= config.maximumActiveOffersPerRoom
                || !eligibility.computeIfAbsent(profile.agentId(), ignored -> random.stream(
                        "agent." + profile.agentId() + ".open-chat-eligibility").nextDouble()
                        < config.eligibleAgentRatio)) return Preparation.none(plan);

        List<Candidate> candidates = new ArrayList<>();
        for (int index = 0; index < plan.stallListings().size(); index++) {
            AgentFreeMarketStallService.Listing listing = plan.stallListings().get(index);
            Item item = seller.getInventory(listing.inventoryType()).getItem(listing.slot());
            if (item == null || item.getQuantity() < listing.perBundle() || listing.price() <= 0) continue;
            EconomyItemEvidence.Description description = EconomyItemEvidence.describe(item);
            if (CosmicTradeOfferPlacer.findExact(seller, listing.inventoryType(), listing.slot(),
                    item.getItemId(), description.fingerprint(), listing.perBundle()) != null)
                candidates.add(new Candidate(index, listing, item, description));
        }
        if (candidates.isEmpty()) return Preparation.none(plan);
        Candidate candidate = candidates.get(random.stream("agent." + profile.agentId()
                + ".open-chat-item").nextInt(candidates.size()));
        int quantity = candidate.listing().perBundle();
        long ask = candidate.listing().price();
        long npcValue = npcValues.sellValue(candidate.item().getItemId(), quantity);
        long npcFloor = ceilingBasisPoints(npcValue, 10_000 + config.minimumNpcPremiumBasisPoints);
        long discountFloor = ceilingBasisPoints(ask, 10_000 - config.maximumNegotiatedDiscountBasisPoints);
        long reserve = Math.max(1, Math.max(npcFloor, discountFloor));
        if (reserve > ask) return Preparation.none(plan);

        UUID offerId = deterministicId(profile.agentId(), logicalAt, candidate.description().fingerprint());
        String text = flavor.render(candidate.item().getItemId(), candidate.description().attributes(), ask);
        Duration lifetime = Duration.parse(config.offerLifetime);
        EconomicIntent intent = economy.publishIntent(profile.agentId(), "", EconomicIntent.Kind.SELL_INTEREST,
                candidate.item().getItemId(), candidate.description().fingerprint(), quantity, ask,
                seller.getMapId(), text, Map.of("offerId", offerId.toString(), "reserveMesos", reserve,
                        "inventoryType", candidate.listing().inventoryType().name(),
                        "slot", (int) candidate.listing().slot()), logicalAt, lifetime);
        Offer offer = new Offer(offerId, intent.intentId(), profile.agentId(), seller.getId(),
                candidate.listing().inventoryType(), candidate.listing().slot(), candidate.item().getItemId(),
                candidate.description().fingerprint(), candidate.description().attributes(), quantity,
                ask, reserve, seller.getMapId(), text, logicalAt, logicalAt.plus(lifetime),
                logicalAt.plus(Duration.parse(config.initialAdvertisementDelay)));
        try {
            reservations.reserve(seller, offer.offerId, offer.itemId, offer.quantity, lifetime);
        } catch (RuntimeException failure) {
            economy.resolveIntent(profile.agentId(), intent.intentId(), EconomicIntent.Status.CANCELLED,
                    logicalAt, "INVENTORY_RESERVATION_FAILED");
            throw failure;
        }
        offers.put(profile.agentId(), offer);
        return new Preparation(withoutOneBundle(plan, candidate.index()), true, offerId.toString(),
                candidate.item().getItemId(), quantity, ask, reserve);
    }

    @Override
    public synchronized Result progressSeller(Character seller, CommerceParticipant profile, Instant logicalAt) {
        Result terminal = terminalResults.remove(profile.agentId());
        if (terminal != null) return terminal;
        Offer offer = offers.get(profile.agentId());
        if (offer == null) return Result.none();
        offer.lastLogicalAt = logicalAt;
        ensureReservation(seller, offer);
        if (!logicalAt.isBefore(offer.expiresAt)) {
            Result result = close(offer, EconomicIntent.Status.EXPIRED, "OFFER_EXPIRED", logicalAt, false);
            terminalResults.remove(profile.agentId());
            return result;
        }
        if (CosmicTradeOfferPlacer.findExact(seller, offer.inventoryType, offer.slot, offer.itemId,
                offer.fingerprint, offer.quantity) == null && !offer.itemPlaced) {
            Result result = close(offer, EconomicIntent.Status.CANCELLED,
                    "RESERVED_HOLDING_CHANGED", logicalAt, false);
            terminalResults.remove(profile.agentId());
            return result;
        }
        if (seller.getMapId() != offer.mapId)
            return new Result(true, false, false, "WAITING_FOR_OFFER_MAP", offer.offerId.toString(),
                    offer.itemId, offer.ask, Map.of("mapId", offer.mapId));
        if (offer.advertisements < config.maximumAdvertisements
                && !logicalAt.isBefore(offer.nextAdvertisementAt)) advertise(seller, offer, logicalAt);
        return new Result(true, false, false, "ACTIVE", offer.offerId.toString(), offer.itemId,
                offer.ask, Map.of("reserveMesos", offer.reserve, "advertisements", offer.advertisements));
    }

    @Override
    public synchronized Result attemptPurchase(Character buyer, CommerceParticipant profile,
                                               List<AgentNeed> needs, Instant logicalAt) {
        if (!config.enabled || !config.agentTradeEnabled || buyer.getTrade() != null) return Result.none();
        long liquidityBudget = Math.max(0, Math.round(buyer.getMeso() * (1d - profile.liquidityPreference())));
        CandidatePurchase selected = offers.values().stream()
                .filter(offer -> !offer.sellerAgentId.equals(profile.agentId())
                        && offer.advertisements > 0 && offer.mapId == buyer.getMapId()
                        && logicalAt.isBefore(offer.expiresAt))
                .flatMap(offer -> needs.stream().filter(need -> need.deficit() >= offer.quantity
                                && (need.itemId() == offer.itemId || need.substitutes().contains(offer.itemId)))
                        .map(need -> purchaseCandidate(offer, need, liquidityBudget)))
                .filter(Objects::nonNull).max(Comparator.comparingDouble(CandidatePurchase::score))
                .orElse(null);
        if (selected == null) return Result.none();
        Offer offer = selected.offer();
        Character seller = participants.admittedCharacter(offer.sellerAgentId);
        if (seller == null) return Result.none();
        CosmicCounterpartyApproachService.Status proximity = approach.request(buyer, seller);
        if (proximity != CosmicCounterpartyApproachService.Status.ARRIVED)
            return new Result(true, false, false, "APPROACH_" + proximity.name(),
                    offer.offerId.toString(), offer.itemId, selected.payment(),
                    Map.of("externalActionPending", proximity == CosmicCounterpartyApproachService.Status.ASSIGNED
                            || proximity == CosmicCounterpartyApproachService.Status.IN_PROGRESS));
        long payment = selected.payment();
        String buyerText = flavor.renderOffer(offer.itemId, offer.attributes, payment);
        EconomicIntent buyerOffer = economy.publishIntent(profile.agentId(), offer.sellerAgentId,
                EconomicIntent.Kind.MESO_OFFER, offer.itemId, offer.fingerprint, offer.quantity,
                payment, offer.mapId, buyerText, Map.of("saleOfferId", offer.offerId.toString(),
                        "askMesos", offer.ask, "reserveMesos", offer.reserve), logicalAt,
                Duration.parse(config.negotiationTimeout));
        offer.state = OfferState.SELLER_REVIEW;
        speak(buyer, buyerText);
        appendSocial(offer, logicalAt, profile.agentId(), offer.sellerAgentId,
                "OPEN_CHAT_BUYER_OFFER", buyerText, payment, buyerOffer.intentId());

        String sellerText = flavor.renderAcceptance(payment);
        EconomicIntent acceptance = economy.publishIntent(offer.sellerAgentId, profile.agentId(),
                EconomicIntent.Kind.ACCEPT, offer.itemId, offer.fingerprint, offer.quantity,
                payment, offer.mapId, sellerText, Map.of("saleOfferId", offer.offerId.toString(),
                        "acceptedIntentId", buyerOffer.intentId().toString()), logicalAt,
                Duration.parse(config.negotiationTimeout));
        offer.state = OfferState.TRADE_OPEN;
        speak(seller, sellerText);
        appendSocial(offer, logicalAt, offer.sellerAgentId, profile.agentId(),
                "OPEN_CHAT_SELLER_ACCEPT", sellerText, payment, acceptance.intentId());
        TradeExecutionResult execution = executeAgentTrade(buyer, profile, seller, offer, payment, logicalAt);
        if (!execution.success()) {
            offer.state = OfferState.FAILED;
            economy.resolveIntent(profile.agentId(), buyerOffer.intentId(), EconomicIntent.Status.CANCELLED,
                    logicalAt, execution.outcome());
            economy.resolveIntent(offer.sellerAgentId, acceptance.intentId(), EconomicIntent.Status.CANCELLED,
                    logicalAt, execution.outcome());
            String failureText = flavor.renderFailure();
            speak(seller, failureText);
            appendSocial(offer, logicalAt, offer.sellerAgentId, profile.agentId(),
                    "OPEN_CHAT_TRADE_FAILED", failureText, payment, acceptance.intentId());
            refreshSlot(seller, offer);
            return new Result(true, false, false, execution.outcome(),
                    offer.offerId.toString(), offer.itemId, payment, Map.of("ask", offer.ask));
        }
        offer.state = OfferState.COMMITTED;
        economy.resolveIntent(profile.agentId(), buyerOffer.intentId(), EconomicIntent.Status.SETTLED,
                logicalAt, "AGENT_TRADE_SETTLED");
        economy.resolveIntent(offer.sellerAgentId, acceptance.intentId(), EconomicIntent.Status.SETTLED,
                logicalAt, "AGENT_TRADE_SETTLED");
        appendSocial(offer, logicalAt, offer.sellerAgentId, profile.agentId(),
                "OPEN_CHAT_TRADE_COMMITTED", "deal, thanks!", payment, acceptance.intentId());
        return close(offer, EconomicIntent.Status.SETTLED, "AGENT_TRADE_SETTLED", logicalAt, true);
    }

    /** Claims a real incoming Trade window while this character owns an active public sale. */
    public synchronized boolean handleManualTrade(Character seller) {
        Offer offer = offers.values().stream().filter(value -> value.sellerCharacterId == seller.getId())
                .findFirst().orElse(null);
        if (offer == null || !config.humanTradeEnabled) return false;
        ensureReservation(seller, offer);
        Trade trade = seller.getTrade();
        if (trade == null) {
            if (offer.itemPlaced) refreshSlot(seller, offer);
            offer.itemPlaced = false;
            offer.tradeStartedAtMs = 0;
            return false;
        }
        if (trade.getNumber() != 1 || trade.getPartner() == null) return false;
        Character buyer = trade.getPartner().getChr();
        if (buyer == null || buyer.getMap() != seller.getMap()) return false;
        if (offer.tradeStartedAtMs == 0) offer.tradeStartedAtMs = System.currentTimeMillis();
        if (!trade.isFullTrade()) {
            Trade.visitTrade(seller, buyer);
            if (seller.getTrade() == null || !seller.getTrade().isFullTrade()) return true;
            trade = seller.getTrade();
        }
        if (!offer.itemPlaced) {
            offer.itemPlaced = CosmicTradeOfferPlacer.placeExact(seller, offer.inventoryType, offer.slot,
                    offer.itemId, offer.fingerprint, offer.quantity, (byte) 1);
            if (!offer.itemPlaced) {
                Trade.cancelTrade(seller, Trade.TradeResult.NO_RESPONSE);
                close(offer, EconomicIntent.Status.CANCELLED, "HOLDING_CHANGED_DURING_HUMAN_TRADE",
                        offer.lastLogicalAt, false);
                return true;
            }
            trade.chat("Selling for " + offer.ask + " meso; lowest accepted offer is "
                    + offer.reserve + " meso.");
        }
        if (System.currentTimeMillis() - offer.tradeStartedAtMs > Duration.parse(config.negotiationTimeout).toMillis()) {
            Trade.cancelTrade(seller, Trade.TradeResult.NO_RESPONSE);
            refreshSlot(seller, offer);
            offer.itemPlaced = false;
            offer.tradeStartedAtMs = 0;
            return true;
        }
        if (!trade.isPartnerConfirmed()) return true;
        Trade partner = trade.getPartner();
        int payment = partner.getOfferedMesos();
        if (!partner.getItems().isEmpty() || payment < offer.reserve) {
            trade.chat("Sorry, I need at least " + offer.reserve + " meso and cannot accept item barter yet.");
            Trade.cancelTrade(seller, Trade.TradeResult.NO_RESPONSE);
            refreshSlot(seller, offer);
            offer.itemPlaced = false;
            offer.tradeStartedAtMs = 0;
            return true;
        }
        Instant at = offer.lastLogicalAt == null ? Instant.now() : offer.lastLogicalAt;
        EconomyOperationMetadata metadata = new EconomyOperationMetadata(runId, at,
                offer.offerId.toString(), offer.intentId.toString(), configHash, catalogVersion,
                "OPEN_CHAT_SALE", true, participants.isBoundCharacter(buyer.getId()));
        EconomyOperationContext.with(metadata, () -> Trade.completeTrade(seller));
        if (seller.getTrade() == null) close(offer, EconomicIntent.Status.SETTLED,
                "HUMAN_TRADE_SETTLED", at, true);
        return true;
    }

    @Override
    public synchronized void cancel(String sellerAgentId, Instant logicalAt, String reason) {
        Offer offer = offers.get(sellerAgentId);
        if (offer != null) {
            close(offer, EconomicIntent.Status.CANCELLED, reason, logicalAt, false);
            terminalResults.remove(sellerAgentId);
        }
    }

    public synchronized List<View> views() {
        return offers.values().stream().sorted(Comparator.comparing(value -> value.sellerAgentId))
                .map(value -> new View(value.offerId, value.sellerAgentId, value.sellerCharacterId,
                        value.itemId, value.quantity, value.ask, value.reserve, value.mapId,
                        value.createdAt, value.expiresAt, value.advertisements,
                         value.state.name())).toList();
    }

    public synchronized void shutdown() {
        RuntimeException firstFailure = null;
        for (Offer offer : List.copyOf(offers.values())) {
            try {
                Character seller = participants.boundCharacter(offer.sellerAgentId);
                if (seller != null && seller.getTrade() != null && offer.itemPlaced)
                    Trade.cancelTrade(seller, Trade.TradeResult.NO_RESPONSE);
                close(offer, EconomicIntent.Status.CANCELLED, "ECONOMY_RUNTIME_STOPPED",
                        offer.lastLogicalAt == null ? offer.createdAt : offer.lastLogicalAt, false);
            } catch (RuntimeException failure) {
                if (firstFailure == null) firstFailure = failure;
            }
        }
        terminalResults.clear();
        if (firstFailure != null) log.warn("Open-chat shutdown completed with intent cleanup failure",
                firstFailure);
    }

    @Override
    public synchronized Map<String, Object> snapshotState() {
        List<Map<String, Object>> encoded = offers.values().stream()
                .sorted(Comparator.comparing(value -> value.sellerAgentId)).map(offer -> {
                    Map<String, Object> value = new LinkedHashMap<>();
                    value.put("offerId", offer.offerId.toString());
                    value.put("intentId", offer.intentId.toString());
                    value.put("sellerAgentId", offer.sellerAgentId);
                    value.put("sellerCharacterId", offer.sellerCharacterId);
                    value.put("inventoryType", offer.inventoryType.name());
                    value.put("slot", (int) offer.slot); value.put("itemId", offer.itemId);
                    value.put("fingerprint", offer.fingerprint); value.put("attributes", offer.attributes);
                    value.put("quantity", offer.quantity); value.put("ask", offer.ask);
                    value.put("reserve", offer.reserve); value.put("mapId", offer.mapId);
                    value.put("publicText", offer.publicText); value.put("createdAt", offer.createdAt.toString());
                    value.put("expiresAt", offer.expiresAt.toString());
                    value.put("nextAdvertisementAt", offer.nextAdvertisementAt.toString());
                    value.put("lastLogicalAt", offer.lastLogicalAt.toString());
                    value.put("advertisements", offer.advertisements);
                    value.put("state", offer.state.name());
                    return value;
                }).toList();
        return Map.of("schemaVersion", 1, "offers", encoded, "eligibility", Map.copyOf(eligibility));
    }

    @Override
    @SuppressWarnings("unchecked")
    public synchronized void restoreState(Map<String, Object> snapshot) {
        if (snapshot == null || snapshot.isEmpty()) return;
        if (!offers.isEmpty()) throw new IllegalStateException("open-chat state is already initialized");
        if (((Number) snapshot.get("schemaVersion")).intValue() != 1)
            throw new IllegalStateException("unsupported open-chat checkpoint schema");
        Object encodedEligibility = snapshot.get("eligibility");
        if (encodedEligibility instanceof Map<?, ?> values) values.forEach((agentId, eligible) ->
                eligibility.put(agentId.toString(), Boolean.TRUE.equals(eligible)));
        for (Map<String, Object> value : (List<Map<String, Object>>) snapshot.get("offers")) {
            Offer offer = new Offer(UUID.fromString(value.get("offerId").toString()),
                    UUID.fromString(value.get("intentId").toString()), value.get("sellerAgentId").toString(),
                    ((Number) value.get("sellerCharacterId")).intValue(),
                    InventoryType.valueOf(value.get("inventoryType").toString()),
                    ((Number) value.get("slot")).shortValue(), ((Number) value.get("itemId")).intValue(),
                    value.get("fingerprint").toString(), (Map<String, Object>) value.get("attributes"),
                    ((Number) value.get("quantity")).intValue(), ((Number) value.get("ask")).longValue(),
                    ((Number) value.get("reserve")).longValue(), ((Number) value.get("mapId")).intValue(),
                    value.get("publicText").toString(), Instant.parse(value.get("createdAt").toString()),
                    Instant.parse(value.get("expiresAt").toString()),
                    Instant.parse(value.get("nextAdvertisementAt").toString()));
            offer.lastLogicalAt = Instant.parse(value.get("lastLogicalAt").toString());
            offer.advertisements = ((Number) value.get("advertisements")).intValue();
            offer.state = OfferState.valueOf(value.getOrDefault("state", "OPEN").toString());
            offer.reservationActive = false;
            offers.put(offer.sellerAgentId, offer);
        }
    }

    private TradeExecutionResult executeAgentTrade(Character buyer, CommerceParticipant buyerProfile,
                                                   Character seller, Offer offer, long payment,
                                                   Instant logicalAt) {
        EconomyOperationMetadata metadata = new EconomyOperationMetadata(runId, logicalAt,
                offer.offerId.toString(), offer.intentId.toString(), configHash, catalogVersion,
                "OPEN_CHAT_SALE", true, true);
        var result = EconomyOperationContext.with(metadata, () -> trades.executeExactItem(
                "open-chat:" + offer.offerId, buyerProfile.agentId(), payment,
                offer.sellerAgentId, offer.itemId, offer.fingerprint, offer.quantity));
        return new TradeExecutionResult(result.succeeded(), result.evidence());
    }

    private CandidatePurchase purchaseCandidate(Offer offer, AgentNeed need, long liquidityBudget) {
        long willingness = Math.min(need.maximumWillingnessToPay(), liquidityBudget);
        if (willingness < offer.reserve) return null;
        long payment = willingness >= offer.ask ? offer.ask : offer.reserve;
        double surplus = (willingness - payment) / (double) Math.max(1, willingness);
        return new CandidatePurchase(offer, payment, need.urgency() * 2d + surplus);
    }

    private void advertise(Character seller, Offer offer, Instant logicalAt) {
        speak(seller, offer.publicText);
        offer.advertisements++;
        long minimum = Duration.parse(config.minimumRepeatDelay).toMillis();
        long maximum = Duration.parse(config.maximumRepeatDelay).toMillis();
        long delay = minimum + (maximum == minimum ? 0 : Long.remainderUnsigned(
                random.stream("agent." + offer.sellerAgentId + ".open-chat-repeat").nextLong(),
                maximum - minimum + 1));
        offer.nextAdvertisementAt = logicalAt.plusMillis(delay);
        journal.appendSocial(new SocialEvidence(UUID.nameUUIDFromBytes((offer.offerId + ":ad:"
                + offer.advertisements).getBytes(StandardCharsets.UTF_8)), runId, logicalAt,
                seller.getMapId(), offer.sellerAgentId, "", "OPEN_CHAT_SALE_ADVERTISEMENT",
                offer.publicText, Map.of("intentId", offer.intentId.toString(), "offerId",
                        offer.offerId.toString(), "itemId", offer.itemId, "quantity", offer.quantity,
                        "askMesos", offer.ask, "reserveMesos", offer.reserve), offer.itemId, null));
    }

    private void appendSocial(Offer offer, Instant at, String speaker, String target,
                              String kind, String text, long mesos, UUID intentId) {
        journal.appendSocial(new SocialEvidence(UUID.nameUUIDFromBytes((offer.offerId + ":" + kind + ':'
                + speaker + ':' + at).getBytes(StandardCharsets.UTF_8)), runId, at, offer.mapId,
                speaker, target, kind, text, Map.of("intentId", intentId.toString(), "saleOfferId",
                offer.offerId.toString(), "itemId", offer.itemId, "quantity", offer.quantity,
                "mesos", mesos, "state", offer.state.name()), offer.itemId, null));
    }

    private void ensureReservation(Character seller, Offer offer) {
        if (offer.reservationActive) return;
        Duration remaining = Duration.between(offer.lastLogicalAt, offer.expiresAt);
        if (remaining.isNegative() || remaining.isZero()) return;
        reservations.reserve(seller, offer.offerId, offer.itemId, offer.quantity, remaining);
        offer.reservationActive = true;
    }

    private static void refreshSlot(Character seller, Offer offer) {
        Item returned = seller.getInventory(offer.inventoryType).listById(offer.itemId).stream()
                .filter(item -> item.getQuantity() >= offer.quantity)
                .filter(item -> EconomyItemEvidence.describe(item).fingerprint().equals(offer.fingerprint))
                .findFirst().orElse(null);
        if (returned != null) offer.slot = returned.getPosition();
    }

    private Result close(Offer offer, EconomicIntent.Status status, String outcome,
                         Instant logicalAt, boolean sold) {
        offers.remove(offer.sellerAgentId, offer);
        try {
            economy.resolveIntent(offer.sellerAgentId, offer.intentId, status, logicalAt, outcome);
        } finally {
            reservations.release(offer.sellerCharacterId);
        }
        Result result = new Result(true, true, sold, outcome, offer.offerId.toString(), offer.itemId,
                offer.ask, Map.of("reserveMesos", offer.reserve));
        terminalResults.put(offer.sellerAgentId, result);
        return result;
    }

    private int activeInMap(int mapId) {
        return (int) offers.values().stream().filter(offer -> offer.mapId == mapId).count();
    }

    private MarketSellerPlan withoutOneBundle(MarketSellerPlan plan, int selectedIndex) {
        List<AgentFreeMarketStallService.Listing> listings = new ArrayList<>();
        for (int index = 0; index < plan.stallListings().size(); index++) {
            AgentFreeMarketStallService.Listing listing = plan.stallListings().get(index);
            if (index != selectedIndex) listings.add(listing);
            else if (listing.bundles() > 1) listings.add(new AgentFreeMarketStallService.Listing(
                    listing.inventoryType(), listing.slot(), listing.perBundle(),
                    (short) (listing.bundles() - 1), listing.price()));
        }
        return new MarketSellerPlan(plan.npcSales(), listings, plan.preferredRoomMapId(), plan.stallDescription());
    }

    private UUID deterministicId(String sellerAgentId, Instant logicalAt, String fingerprint) {
        return UUID.nameUUIDFromBytes((runId + ":open-chat:" + sellerAgentId + ':' + logicalAt + ':'
                + fingerprint).getBytes(StandardCharsets.UTF_8));
    }

    private static long ceilingBasisPoints(long value, int basisPoints) {
        if (value <= 0 || basisPoints <= 0) return 0;
        return Math.addExact(Math.multiplyExact(value, basisPoints), 9_999) / 10_000;
    }

    private void speak(Character speaker, String text) {
        chat.speak(speaker, text);
    }

    private record Candidate(int index, AgentFreeMarketStallService.Listing listing, Item item,
                             EconomyItemEvidence.Description description) { }
    private record CandidatePurchase(Offer offer, long payment, double score) { }
    private record TradeExecutionResult(boolean success, String outcome) { }

    interface ReservationGateway {
        void reserve(Character seller, UUID offerId, int itemId, int quantity, Duration lifetime);
        void release(int characterId);
    }
    @FunctionalInterface interface NpcValueCatalog { long sellValue(int itemId, int quantity); }
    @FunctionalInterface interface PublicChat { void speak(Character speaker, String text); }
    @FunctionalInterface interface ApproachGateway {
        CosmicCounterpartyApproachService.Status request(Character buyer, Character seller);
    }

    private static final class Offer {
        private final UUID offerId;
        private final UUID intentId;
        private final String sellerAgentId;
        private final int sellerCharacterId;
        private final InventoryType inventoryType;
        private short slot;
        private final int itemId;
        private final String fingerprint;
        private final Map<String, Object> attributes;
        private final int quantity;
        private final long ask;
        private final long reserve;
        private final int mapId;
        private final String publicText;
        private final Instant createdAt;
        private final Instant expiresAt;
        private Instant nextAdvertisementAt;
        private Instant lastLogicalAt;
        private int advertisements;
        private boolean itemPlaced;
        private long tradeStartedAtMs;
        private boolean reservationActive = true;
        private OfferState state = OfferState.OPEN;

        private Offer(UUID offerId, UUID intentId, String sellerAgentId, int sellerCharacterId,
                      InventoryType inventoryType, short slot, int itemId, String fingerprint,
                      Map<String, Object> attributes, int quantity, long ask, long reserve, int mapId,
                      String publicText, Instant createdAt, Instant expiresAt, Instant nextAdvertisementAt) {
            this.offerId = offerId; this.intentId = intentId; this.sellerAgentId = sellerAgentId;
            this.sellerCharacterId = sellerCharacterId; this.inventoryType = inventoryType;
            this.slot = slot; this.itemId = itemId; this.fingerprint = fingerprint;
            this.attributes = Map.copyOf(attributes); this.quantity = quantity; this.ask = ask;
            this.reserve = reserve; this.mapId = mapId; this.publicText = publicText;
            this.createdAt = createdAt; this.expiresAt = expiresAt;
            this.nextAdvertisementAt = nextAdvertisementAt; this.lastLogicalAt = createdAt;
        }
    }

    private enum OfferState { OPEN, SELLER_REVIEW, TRADE_OPEN, COMMITTED, FAILED }

    public record View(UUID offerId, String sellerAgentId, int sellerCharacterId, int itemId,
                       int quantity, long askMesos, long reserveMesos, int mapId,
                       Instant createdAt, Instant expiresAt, int advertisements, String state) { }
}
