package server.agents.economy.integration.cosmic;

import client.Character;
import constants.inventory.ItemConstants;
import server.ItemInformationProvider;
import server.agents.economy.decision.AgentNeed;
import server.agents.economy.market.MarketObservation;
import server.agents.economy.market.StallOffer;
import server.agents.economy.persistence.EconomyEvidenceJournal;
import server.agents.economy.persistence.NegotiationEvidenceStore;
import server.agents.economy.persistence.SocialEvidence;
import server.agents.economy.persistence.StallOfferStore;
import server.agents.economy.session.CommerceParticipant;
import server.agents.economy.social.PublicNegotiationSession;
import server.agents.economy.social.TradeExecutionGateway;
import server.agents.economy.social.TradeOffer;
import server.agents.integration.AgentPacketGatewayRuntime;
import server.maps.PlayerShop;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/** Public, nearby negotiation over a real observed listing followed by Cosmic PLAYER_TRADE settlement. */
public final class CosmicPublicTradeNegotiator implements AutonomousFreeMarketBehavior.NegotiationBehavior {
    private final UUID runId;
    private final ParticipantDirectory participants;
    private final StallCloser shops;
    private final TradeExecutionGateway trades;
    private final EconomyEvidenceJournal journal;
    private final NegotiationEvidenceStore sessions;
    private final NpcValueCatalog npcValues;
    private final Duration timeout;
    private final int interactionRangePixels;
    private final PublicChatGateway chat;
    private final boolean barterEnabled;
    private final CounterpartyNeedReader counterpartyNeeds;
    private final StallOfferStore stallOffers;
    private final long minimumOfferIncrementMesos;
    private final int minimumOfferIncrementBasisPoints;
    private final StallOfferTextRenderer offerText;

    public CosmicPublicTradeNegotiator(UUID runId, ParticipantDirectory participants,
                                       CosmicMarketSellerGateway shops, TradeExecutionGateway trades,
                                       EconomyEvidenceJournal journal, NegotiationEvidenceStore sessions,
                                       Duration timeout, int interactionRangePixels) {
        this(runId, participants, shops::close, trades, journal, sessions,
                (itemId, quantity) -> Math.max(0, ItemInformationProvider.getInstance().getPrice(itemId, quantity)),
                (speaker, text) -> AgentPacketGatewayRuntime.packets().broadcastChatText(speaker, text, false, 1),
                false, (agent, profile, at) -> List.of(), StallOfferStore.noop(), timeout,
                interactionRangePixels, 100, 100,
                new StallOfferFlavorRenderer(StallOfferFlavorRenderer.DEFAULT_TEMPLATE));
    }

    public CosmicPublicTradeNegotiator(UUID runId, ParticipantDirectory participants,
                                       CosmicMarketSellerGateway shops, TradeExecutionGateway trades,
                                       EconomyEvidenceJournal journal, NegotiationEvidenceStore sessions,
                                       boolean barterEnabled, CounterpartyNeedReader counterpartyNeeds,
                                       Duration timeout, int interactionRangePixels) {
        this(runId, participants, shops::close, trades, journal, sessions,
                (itemId, quantity) -> Math.max(0, ItemInformationProvider.getInstance().getPrice(itemId, quantity)),
                (speaker, text) -> AgentPacketGatewayRuntime.packets().broadcastChatText(speaker, text, false, 1),
                barterEnabled, counterpartyNeeds, StallOfferStore.noop(), timeout, interactionRangePixels);
    }

    public CosmicPublicTradeNegotiator(UUID runId, ParticipantDirectory participants,
                                       CosmicMarketSellerGateway shops, TradeExecutionGateway trades,
                                       EconomyEvidenceJournal journal, NegotiationEvidenceStore sessions,
                                       boolean barterEnabled, CounterpartyNeedReader counterpartyNeeds,
                                       StallOfferStore stallOffers, Duration timeout,
                                       int interactionRangePixels) {
        this(runId, participants, shops::close, trades, journal, sessions,
                (itemId, quantity) -> Math.max(0, ItemInformationProvider.getInstance().getPrice(itemId, quantity)),
                (speaker, text) -> AgentPacketGatewayRuntime.packets().broadcastChatText(speaker, text, false, 1),
                barterEnabled, counterpartyNeeds, stallOffers, timeout, interactionRangePixels);
    }

    public CosmicPublicTradeNegotiator(UUID runId, ParticipantDirectory participants,
                                       CosmicMarketSellerGateway shops, TradeExecutionGateway trades,
                                       EconomyEvidenceJournal journal, NegotiationEvidenceStore sessions,
                                       boolean barterEnabled, CounterpartyNeedReader counterpartyNeeds,
                                       StallOfferStore stallOffers, Duration timeout,
                                       int interactionRangePixels, long minimumOfferIncrementMesos,
                                       int minimumOfferIncrementBasisPoints,
                                       StallOfferTextRenderer offerText) {
        this(runId, participants, shops::close, trades, journal, sessions,
                (itemId, quantity) -> Math.max(0, ItemInformationProvider.getInstance().getPrice(itemId, quantity)),
                (speaker, text) -> AgentPacketGatewayRuntime.packets().broadcastChatText(speaker, text, false, 1),
                barterEnabled, counterpartyNeeds, stallOffers, timeout, interactionRangePixels,
                minimumOfferIncrementMesos, minimumOfferIncrementBasisPoints, offerText);
    }

    CosmicPublicTradeNegotiator(UUID runId, ParticipantDirectory participants,
                                StallCloser shops, TradeExecutionGateway trades,
                                EconomyEvidenceJournal journal, NegotiationEvidenceStore sessions,
                                NpcValueCatalog npcValues, PublicChatGateway chat,
                                Duration timeout, int interactionRangePixels) {
        this(runId, participants, shops, trades, journal, sessions, npcValues, chat, false,
                (agent, profile, at) -> List.of(), StallOfferStore.noop(), timeout, interactionRangePixels);
    }

    CosmicPublicTradeNegotiator(UUID runId, ParticipantDirectory participants,
                                StallCloser shops, TradeExecutionGateway trades,
                                EconomyEvidenceJournal journal, NegotiationEvidenceStore sessions,
                                NpcValueCatalog npcValues, PublicChatGateway chat,
                                boolean barterEnabled, CounterpartyNeedReader counterpartyNeeds,
                                Duration timeout, int interactionRangePixels) {
        this(runId, participants, shops, trades, journal, sessions, npcValues, chat, barterEnabled,
                counterpartyNeeds, StallOfferStore.noop(), timeout, interactionRangePixels);
    }

    CosmicPublicTradeNegotiator(UUID runId, ParticipantDirectory participants,
                                StallCloser shops, TradeExecutionGateway trades,
                                EconomyEvidenceJournal journal, NegotiationEvidenceStore sessions,
                                NpcValueCatalog npcValues, PublicChatGateway chat,
                                boolean barterEnabled, CounterpartyNeedReader counterpartyNeeds,
                                StallOfferStore stallOffers, Duration timeout, int interactionRangePixels) {
        this(runId, participants, shops, trades, journal, sessions, npcValues, chat, barterEnabled,
                counterpartyNeeds, stallOffers, timeout, interactionRangePixels, 100, 100,
                new StallOfferFlavorRenderer(StallOfferFlavorRenderer.DEFAULT_TEMPLATE));
    }

    CosmicPublicTradeNegotiator(UUID runId, ParticipantDirectory participants,
                                StallCloser shops, TradeExecutionGateway trades,
                                EconomyEvidenceJournal journal, NegotiationEvidenceStore sessions,
                                NpcValueCatalog npcValues, PublicChatGateway chat,
                                boolean barterEnabled, CounterpartyNeedReader counterpartyNeeds,
                                StallOfferStore stallOffers, Duration timeout, int interactionRangePixels,
                                long minimumOfferIncrementMesos, int minimumOfferIncrementBasisPoints,
                                StallOfferTextRenderer offerText) {
        this.runId = Objects.requireNonNull(runId); this.participants = Objects.requireNonNull(participants);
        this.shops = Objects.requireNonNull(shops); this.trades = Objects.requireNonNull(trades);
        this.journal = Objects.requireNonNull(journal); this.sessions = Objects.requireNonNull(sessions);
        this.npcValues = Objects.requireNonNull(npcValues); this.timeout = Objects.requireNonNull(timeout);
        this.chat = Objects.requireNonNull(chat);
        this.barterEnabled = barterEnabled;
        this.counterpartyNeeds = Objects.requireNonNull(counterpartyNeeds);
        this.stallOffers = Objects.requireNonNull(stallOffers);
        this.offerText = Objects.requireNonNull(offerText);
        if (timeout.isZero() || timeout.isNegative() || interactionRangePixels <= 0
                || minimumOfferIncrementMesos <= 0 || minimumOfferIncrementBasisPoints < 0
                || minimumOfferIncrementBasisPoints > 10_000)
            throw new IllegalArgumentException("negotiation timing and range must be positive");
        this.interactionRangePixels = interactionRangePixels;
        this.minimumOfferIncrementMesos = minimumOfferIncrementMesos;
        this.minimumOfferIncrementBasisPoints = minimumOfferIncrementBasisPoints;
    }

    @Override
    public Result attempt(Character buyer, CommerceParticipant buyerProfile, List<AgentNeed> needs,
                          List<MarketObservation> observations, Instant logicalAt) {
        Optional<Candidate> selected = observations.stream()
                .filter(o -> o.state() == MarketObservation.State.LISTED && o.quantityPerBundle() > 0)
                .flatMap(observation -> needs.stream().filter(need -> matches(need, observation.itemId())
                                && need.deficit() > 0 && need.maximumWillingnessToPay() > 0)
                        .map(need -> candidate(buyer, buyerProfile, observation, need, logicalAt)))
                .filter(Objects::nonNull).max(Comparator.comparingDouble(Candidate::surplus));
        if (selected.isEmpty()) return Result.none();
        Candidate candidate = selected.orElseThrow();
        Participant seller = participants.byCharacterId(Integer.parseInt(candidate.observation().stallOwnerAgentId()))
                .orElse(null);
        if (seller == null || !nearby(buyer, seller.character()) || seller.character().getPlayerShop() == null
                || !seller.character().getPlayerShop().isOpen()) return Result.none();

        long ask = candidate.observation().bundlePrice();
        long offer = candidate.offeredMesos();
        int quantity = Math.min(candidate.observation().quantityPerBundle(), candidate.need().deficit());
        long npcFloor = npcValues.sellValue(candidate.observation().itemId(), quantity);
        long reserve = Math.max(npcFloor, Math.round(ask *
                (1d - .10d * seller.profile().negotiationAggressiveness())));
        String id = UUID.nameUUIDFromBytes((runId + ":" + buyerProfile.agentId() + ":"
                + seller.profile().agentId() + ":" + logicalAt + ":" + candidate.observation().listingId())
                .getBytes(StandardCharsets.UTF_8)).toString();
        String proposalText = offerText.render(candidate.observation(), offer);
        StallOffer structuredOffer = null;
        if (stallOffers.enabled()) {
            structuredOffer = new StallOffer(UUID.fromString(id), runId, buyerProfile.agentId(),
                    seller.profile().agentId(), stallId(candidate.observation().listingId()),
                    candidate.observation().listingId(), candidate.observation().roomMapId(),
                    candidate.observation().itemId(), candidate.observation().fingerprint(),
                    candidate.observation().attributes(), quantity, ask, offer, proposalText,
                    logicalAt, logicalAt.plus(timeout), StallOffer.Status.PENDING);
            stallOffers.create(structuredOffer);
            if (!postStallChat(buyer, seller.character(), proposalText)) {
                resolveOffer(structuredOffer, StallOffer.Status.FAILED,
                        "stall chat visitor slots were unavailable", logicalAt, null);
                return new Result(true, false, id, "CHAT_DELIVERY_FAILED",
                        candidate.observation().itemId(), offer, Map.of("ask", ask));
            }
            recordSocial(buyer, buyerProfile.agentId(), seller.profile().agentId(), logicalAt,
                    "STALL_OFFER_LEFT", proposalText, candidate.observation().itemId(),
                    publicOfferIntent(id, candidate, offer, quantity, ask));
            return new Result(true, false, id, "OFFER_LEFT", candidate.observation().itemId(), offer,
                    publicOfferEvidence(candidate, ask, offer, quantity));
        }
        PublicNegotiationSession session = new PublicNegotiationSession(id, buyerProfile.agentId(),
                seller.profile().agentId(), logicalAt, timeout);
        speak(buyer, buyerProfile.agentId(), seller.profile().agentId(), logicalAt, "TRADE_INVITE",
                "Would you negotiate for " + quantity + " of item " + candidate.observation().itemId() + "?",
                candidate.observation().itemId(), Map.of("listingId", candidate.observation().listingId()));
        TradeOffer buyerOffer = new TradeOffer(offer, Map.of());
        TradeOffer sellerOffer = new TradeOffer(0, Map.of(candidate.observation().itemId(), quantity));
        session.propose(buyerProfile.agentId(), buyerOffer, sellerOffer, proposalText, logicalAt);
        speak(buyer, buyerProfile.agentId(), seller.profile().agentId(), logicalAt, "PROPOSAL", proposalText,
                candidate.observation().itemId(), Map.of("mesos", offer, "quantity", quantity,
                        "ask", ask, "buyerWtp", candidate.need().maximumWillingnessToPay()));
        long sellerValue = offer;
        Barter barter = null;
        if (offer < reserve && barterEnabled) {
            barter = findBarter(buyer, needs, seller, candidate.need(), offer, reserve, logicalAt).orElse(null);
            if (barter != null) {
                offer = barter.mesos(); sellerValue = barter.sellerValue();
                buyerOffer = new TradeOffer(offer, Map.of(barter.itemId(), barter.quantity()));
                String counter = "Include " + barter.quantity() + " of item " + barter.itemId()
                        + " with " + offer + " mesos and I can accept.";
                session.propose(seller.profile().agentId(), buyerOffer, sellerOffer, counter, logicalAt);
                speak(seller.character(), seller.profile().agentId(), buyerProfile.agentId(), logicalAt,
                        "COUNTER_PROPOSAL", counter, barter.itemId(), Map.of("mesos", offer,
                                "quantity", barter.quantity(), "sellerNeedValue", barter.needValue(),
                                "buyerNpcOpportunityCost", barter.buyerOpportunityCost()));
            }
        }
        if (sellerValue < reserve) {
            String rejection = "I cannot accept below " + reserve + " mesos.";
            session.reject(seller.profile().agentId(), rejection, logicalAt);
            speak(seller.character(), seller.profile().agentId(), buyerProfile.agentId(), logicalAt,
                    "REJECT", rejection, candidate.observation().itemId(),
                    Map.of("reserve", reserve, "npcFloor", npcFloor));
            sessions.record(runId, candidate.observation().itemId(), logicalAt, logicalAt, session, null);
            resolveOffer(structuredOffer, StallOffer.Status.REJECTED, rejection, logicalAt, null);
            return new Result(true, false, id, "REJECTED", candidate.observation().itemId(), offer,
                    Map.of("ask", ask, "reserve", reserve, "npcFloor", npcFloor));
        }

        if (ItemConstants.getInventoryType(candidate.observation().itemId())
                == client.inventory.InventoryType.EQUIP) {
            String response = "Accepted in principle; waiting for fingerprint-exact settlement.";
            session.agree(seller.profile().agentId(), response, logicalAt);
            speak(seller.character(), seller.profile().agentId(), buyerProfile.agentId(), logicalAt,
                    "ACCEPT_AWAITING_SETTLEMENT", response, candidate.observation().itemId(),
                    Map.of("acceptedMesos", offer, "fingerprint", candidate.observation().fingerprint()));
            sessions.record(runId, candidate.observation().itemId(), logicalAt, logicalAt, session, null);
            resolveOffer(structuredOffer, StallOffer.Status.ACCEPTED_AWAITING_SETTLEMENT,
                    response, logicalAt, null);
            return new Result(true, false, id, "ACCEPTED_AWAITING_SETTLEMENT",
                    candidate.observation().itemId(), offer,
                    Map.of("ask", ask, "reserve", reserve, "fingerprintExact", true));
        }

        String acceptance = "Accepted. I will close my stall and trade here.";
        Character accepter = barter == null ? seller.character() : buyer;
        String accepterId = barter == null ? seller.profile().agentId() : buyerProfile.agentId();
        String targetId = barter == null ? buyerProfile.agentId() : seller.profile().agentId();
        session.agree(accepterId, acceptance, logicalAt);
        speak(accepter, accepterId, targetId, logicalAt, "ACCEPT", acceptance,
                candidate.observation().itemId(), barter == null ? Map.of("acceptedMesos", offer)
                        : Map.of("acceptedMesos", offer, "barterItemId", barter.itemId(),
                        "barterQuantity", barter.quantity()));
        if (!shops.close(seller.character(), "NEGOTIATED_DIRECT_TRADE")) {
            session.markExecution(false, "stall close failed", logicalAt);
            sessions.record(runId, candidate.observation().itemId(), logicalAt, logicalAt, session, null);
            resolveOffer(structuredOffer, StallOffer.Status.FAILED, "stall close failed", logicalAt, null);
            return new Result(true, false, id, "STALL_CLOSE_FAILED", candidate.observation().itemId(), offer,
                    Map.of("ask", ask, "reserve", reserve));
        }
        TradeExecutionGateway.Result execution = trades.execute(id, buyerProfile.agentId(), buyerOffer,
                seller.profile().agentId(), sellerOffer);
        session.markExecution(execution.succeeded(), execution.evidence(), logicalAt);
        speak(execution.succeeded() ? buyer : seller.character(), execution.succeeded() ? buyerProfile.agentId()
                        : seller.profile().agentId(), execution.succeeded() ? seller.profile().agentId()
                        : buyerProfile.agentId(), logicalAt, execution.succeeded() ? "EXECUTED" : "FAILED",
                execution.evidence(), candidate.observation().itemId(), Map.of("transactionId", execution.transactionId()));
        sessions.record(runId, candidate.observation().itemId(), logicalAt, logicalAt, session,
                execution.transactionId().isBlank() ? null : execution.transactionId());
        resolveOffer(structuredOffer, execution.succeeded()
                        ? StallOffer.Status.EXECUTED : StallOffer.Status.FAILED,
                execution.evidence(), logicalAt,
                execution.transactionId().isBlank() ? null : execution.transactionId());
        Map<String, Object> outcome = new LinkedHashMap<>();
        outcome.put("ask", ask); outcome.put("reserve", reserve); outcome.put("npcFloor", npcFloor);
        outcome.put("transactionId", execution.transactionId());
        if (barter != null) {
            outcome.put("barterItemId", barter.itemId()); outcome.put("barterQuantity", barter.quantity());
            outcome.put("sellerNeedValue", barter.needValue());
            outcome.put("buyerNpcOpportunityCost", barter.buyerOpportunityCost());
        }
        return new Result(true, execution.succeeded(), id, session.stateAt(logicalAt).name(),
                candidate.observation().itemId(), offer, outcome);
    }

    private Optional<Barter> findBarter(Character buyer, List<AgentNeed> buyerNeeds, Participant seller,
                                        AgentNeed desired, long mesoOffer, long reserve, Instant at) {
        return counterpartyNeeds.read(seller.character(), seller.profile(), at).stream()
                .filter(need -> need.deficit() > 0 && need.maximumWillingnessToPay() > 0
                        && ItemConstants.getInventoryType(need.itemId()) != client.inventory.InventoryType.EQUIP)
                .map(need -> barterCandidate(buyer, buyerNeeds, desired, need, mesoOffer, reserve))
                .filter(Objects::nonNull).max(Comparator.comparingLong(Barter::sellerValue)
                        .thenComparing(Comparator.comparingLong(Barter::buyerOpportunityCost).reversed()));
    }

    private Barter barterCandidate(Character buyer, List<AgentNeed> buyerNeeds, AgentNeed desired,
                                   AgentNeed sellerNeed, long mesoOffer, long reserve) {
        int owned = buyer.getInventory(ItemConstants.getInventoryType(sellerNeed.itemId()))
                .countById(sellerNeed.itemId());
        int reserved = buyerNeeds.stream().filter(need -> matches(need, sellerNeed.itemId()))
                .mapToInt(AgentNeed::targetQuantity).max().orElse(0);
        int quantity = Math.min(Math.max(0, owned - reserved), sellerNeed.deficit());
        if (quantity <= 0) return null;
        long opportunityCost = npcValues.sellValue(sellerNeed.itemId(), quantity);
        long adjustedMesos = Math.min(mesoOffer,
                Math.max(0, desired.maximumWillingnessToPay() - opportunityCost));
        long needValue = Math.max(1, Math.round(sellerNeed.maximumWillingnessToPay()
                * quantity / (double) sellerNeed.deficit()));
        long sellerValue = Math.addExact(adjustedMesos, needValue);
        return sellerValue >= reserve
                ? new Barter(sellerNeed.itemId(), quantity, adjustedMesos, needValue,
                opportunityCost, sellerValue) : null;
    }

    private Candidate candidate(Character buyer, CommerceParticipant buyerProfile,
                                MarketObservation observation, AgentNeed need, Instant logicalAt) {
        if (observation.bundlePrice() <= need.maximumWillingnessToPay()) return null;
        Participant seller = parseSeller(observation);
        if (seller == null || !nearby(buyer, seller.character())) return null;
        long committed = stallOffers.enabled()
                ? stallOffers.committedMesosForBuyer(runId, buyerProfile.agentId(), logicalAt) : 0;
        long availableMesos = Math.max(0, (long) buyer.getMeso() - committed);
        long cap = Math.min(need.maximumWillingnessToPay(), availableMesos);
        long offered = Math.min(cap, Math.max(1, Math.round(observation.bundlePrice()
                * (1d - .15d * buyerProfile.negotiationAggressiveness()))));
        StallOffer previous = stallOffers.enabled()
                ? stallOffers.highestPendingForListing(runId, observation.listingId(), logicalAt).orElse(null)
                : null;
        if (previous != null) {
            if (previous.buyerAgentId().equals(buyerProfile.agentId())) return null;
            long increment = Math.max(minimumOfferIncrementMesos,
                    percentageIncrement(previous.offeredMesos(), minimumOfferIncrementBasisPoints));
            offered = Math.max(offered, safeAdd(previous.offeredMesos(), increment));
        }
        if (offered <= 0 || offered > cap || offered >= observation.bundlePrice()) return null;
        double surplus = need.urgency() + (need.maximumWillingnessToPay()
                / (double) Math.max(1, observation.bundlePrice()));
        return new Candidate(observation, need, surplus, offered, previous, committed);
    }

    private static long percentageIncrement(long value, int basisPoints) {
        if (basisPoints == 0) return 0;
        try {
            long scaled = Math.multiplyExact(value, basisPoints);
            return Math.max(1, Math.addExact(scaled, 9_999) / 10_000);
        } catch (ArithmeticException overflow) {
            return Long.MAX_VALUE;
        }
    }

    private static long safeAdd(long left, long right) {
        try { return Math.addExact(left, right); }
        catch (ArithmeticException overflow) { return Long.MAX_VALUE; }
    }

    private static Map<String, Object> publicOfferIntent(String id, Candidate candidate,
                                                         long offer, int quantity, long ask) {
        var result = new LinkedHashMap<String, Object>();
        result.put("offerId", id); result.put("listingId", candidate.observation().listingId());
        result.put("mesos", offer); result.put("quantity", quantity); result.put("ask", ask);
        result.put("itemFingerprint", candidate.observation().fingerprint());
        result.put("existingMesoCommitments", candidate.committedMesos());
        if (candidate.previous() != null) {
            result.put("outbidsOfferId", candidate.previous().offerId().toString());
            result.put("previousHighestMesos", candidate.previous().offeredMesos());
        }
        return Map.copyOf(result);
    }

    private static Map<String, Object> publicOfferEvidence(Candidate candidate, long ask,
                                                           long offer, int quantity) {
        var result = new LinkedHashMap<String, Object>();
        result.put("ask", ask); result.put("offeredMesos", offer); result.put("quantity", quantity);
        result.put("itemFingerprint", candidate.observation().fingerprint());
        result.put("existingMesoCommitments", candidate.committedMesos());
        if (candidate.previous() != null) {
            result.put("outbidsOfferId", candidate.previous().offerId().toString());
            result.put("previousHighestMesos", candidate.previous().offeredMesos());
        }
        return Map.copyOf(result);
    }

    private Participant parseSeller(MarketObservation observation) {
        try { return participants.byCharacterId(Integer.parseInt(observation.stallOwnerAgentId())).orElse(null); }
        catch (NumberFormatException ignored) { return null; }
    }

    private boolean nearby(Character first, Character second) {
        return first != null && second != null && first.getMap() != null && first.getMap() == second.getMap()
                && first.getPosition().distanceSq(second.getPosition())
                <= (long) interactionRangePixels * interactionRangePixels;
    }

    private void speak(Character speaker, String speakerId, String targetId, Instant at, String kind,
                       String text, int itemId, Map<String, Object> intent) {
        chat.broadcast(speaker, text);
        recordSocial(speaker, speakerId, targetId, at, kind, text, itemId, intent);
    }

    private void recordSocial(Character speaker, String speakerId, String targetId, Instant at, String kind,
                              String text, int itemId, Map<String, Object> intent) {
        String raw = runId + ":" + speakerId + ":" + targetId + ":" + at + ":" + kind + ":" + text;
        journal.appendSocial(new SocialEvidence(UUID.nameUUIDFromBytes(raw.getBytes(StandardCharsets.UTF_8)),
                runId, at, speaker.getMapId(), speakerId, targetId, kind, text, intent, itemId, null));
    }

    private static boolean postStallChat(Character buyer, Character seller, String text) {
        PlayerShop shop = seller == null ? null : seller.getPlayerShop();
        if (shop == null || !shop.isOpen() || shop.isOwner(buyer) || !shop.visitShop(buyer)) return false;
        try {
            shop.chat(buyer.getClient(), text);
            return true;
        } finally {
            if (shop.isVisitor(buyer)) shop.removeVisitor(buyer);
            buyer.setPlayerShop(null);
        }
    }

    private void resolveOffer(StallOffer offer, StallOffer.Status status, String response,
                              Instant respondedAt, String settlementTransactionId) {
        if (offer != null) {
            stallOffers.resolve(offer.offerId(), status, response, respondedAt, settlementTransactionId);
        }
    }

    private static String stallId(String listingId) {
        int separator = listingId.lastIndexOf(':');
        return separator <= 0 ? listingId : listingId.substring(0, separator);
    }

    private static boolean matches(AgentNeed need, int itemId) {
        return need.itemId() == itemId || need.substitutes().contains(itemId);
    }

    public interface ParticipantDirectory {
        Optional<Participant> byCharacterId(int characterId);
    }
    public record Participant(Character character, CommerceParticipant profile) {
        public Participant { Objects.requireNonNull(character); Objects.requireNonNull(profile); }
    }
    @FunctionalInterface interface NpcValueCatalog { long sellValue(int itemId, int quantity); }
    @FunctionalInterface interface StallCloser { boolean close(Character seller, String reason); }
    @FunctionalInterface interface PublicChatGateway { void broadcast(Character speaker, String text); }
    @FunctionalInterface public interface CounterpartyNeedReader {
        List<AgentNeed> read(Character character, CommerceParticipant profile, Instant logicalAt);
    }
    private record Candidate(MarketObservation observation, AgentNeed need, double surplus,
                             long offeredMesos, StallOffer previous, long committedMesos) { }
    private record Barter(int itemId, int quantity, long mesos, long needValue,
                          long buyerOpportunityCost, long sellerValue) { }
}
