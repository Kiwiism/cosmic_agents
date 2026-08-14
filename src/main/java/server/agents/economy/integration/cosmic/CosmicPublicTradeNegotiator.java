package server.agents.economy.integration.cosmic;

import client.Character;
import constants.inventory.ItemConstants;
import server.ItemInformationProvider;
import server.agents.economy.decision.AgentNeed;
import server.agents.economy.market.MarketObservation;
import server.agents.economy.persistence.EconomyEvidenceJournal;
import server.agents.economy.persistence.NegotiationEvidenceStore;
import server.agents.economy.persistence.SocialEvidence;
import server.agents.economy.scenario.EconomyAgentProfile;
import server.agents.economy.social.PublicNegotiationSession;
import server.agents.economy.social.TradeExecutionGateway;
import server.agents.economy.social.TradeOffer;
import server.agents.integration.AgentPacketGatewayRuntime;

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

    public CosmicPublicTradeNegotiator(UUID runId, ParticipantDirectory participants,
                                       CosmicMarketSellerGateway shops, TradeExecutionGateway trades,
                                       EconomyEvidenceJournal journal, NegotiationEvidenceStore sessions,
                                       Duration timeout, int interactionRangePixels) {
        this(runId, participants, shops::close, trades, journal, sessions,
                (itemId, quantity) -> Math.max(0, ItemInformationProvider.getInstance().getPrice(itemId, quantity)),
                (speaker, text) -> AgentPacketGatewayRuntime.packets().broadcastChatText(speaker, text, false, 1),
                false, (agent, profile, at) -> List.of(), timeout, interactionRangePixels);
    }

    public CosmicPublicTradeNegotiator(UUID runId, ParticipantDirectory participants,
                                       CosmicMarketSellerGateway shops, TradeExecutionGateway trades,
                                       EconomyEvidenceJournal journal, NegotiationEvidenceStore sessions,
                                       boolean barterEnabled, CounterpartyNeedReader counterpartyNeeds,
                                       Duration timeout, int interactionRangePixels) {
        this(runId, participants, shops::close, trades, journal, sessions,
                (itemId, quantity) -> Math.max(0, ItemInformationProvider.getInstance().getPrice(itemId, quantity)),
                (speaker, text) -> AgentPacketGatewayRuntime.packets().broadcastChatText(speaker, text, false, 1),
                barterEnabled, counterpartyNeeds, timeout, interactionRangePixels);
    }

    CosmicPublicTradeNegotiator(UUID runId, ParticipantDirectory participants,
                                StallCloser shops, TradeExecutionGateway trades,
                                EconomyEvidenceJournal journal, NegotiationEvidenceStore sessions,
                                NpcValueCatalog npcValues, PublicChatGateway chat,
                                Duration timeout, int interactionRangePixels) {
        this(runId, participants, shops, trades, journal, sessions, npcValues, chat, false,
                (agent, profile, at) -> List.of(), timeout, interactionRangePixels);
    }

    CosmicPublicTradeNegotiator(UUID runId, ParticipantDirectory participants,
                                StallCloser shops, TradeExecutionGateway trades,
                                EconomyEvidenceJournal journal, NegotiationEvidenceStore sessions,
                                NpcValueCatalog npcValues, PublicChatGateway chat,
                                boolean barterEnabled, CounterpartyNeedReader counterpartyNeeds,
                                Duration timeout, int interactionRangePixels) {
        this.runId = Objects.requireNonNull(runId); this.participants = Objects.requireNonNull(participants);
        this.shops = Objects.requireNonNull(shops); this.trades = Objects.requireNonNull(trades);
        this.journal = Objects.requireNonNull(journal); this.sessions = Objects.requireNonNull(sessions);
        this.npcValues = Objects.requireNonNull(npcValues); this.timeout = Objects.requireNonNull(timeout);
        this.chat = Objects.requireNonNull(chat);
        this.barterEnabled = barterEnabled;
        this.counterpartyNeeds = Objects.requireNonNull(counterpartyNeeds);
        if (timeout.isZero() || timeout.isNegative() || interactionRangePixels <= 0)
            throw new IllegalArgumentException("negotiation timing and range must be positive");
        this.interactionRangePixels = interactionRangePixels;
    }

    @Override
    public Result attempt(Character buyer, EconomyAgentProfile buyerProfile, List<AgentNeed> needs,
                          List<MarketObservation> observations, Instant logicalAt) {
        Optional<Candidate> selected = observations.stream()
                .filter(o -> o.state() == MarketObservation.State.LISTED && o.quantityPerBundle() > 0)
                .flatMap(observation -> needs.stream().filter(need -> matches(need, observation.itemId())
                                && need.deficit() > 0 && need.maximumWillingnessToPay() > 0)
                        .map(need -> candidate(buyer, observation, need)))
                .filter(Objects::nonNull).max(Comparator.comparingDouble(Candidate::surplus));
        if (selected.isEmpty()) return Result.none();
        Candidate candidate = selected.orElseThrow();
        Participant seller = participants.byCharacterId(Integer.parseInt(candidate.observation().stallOwnerAgentId()))
                .orElse(null);
        if (seller == null || !nearby(buyer, seller.character()) || seller.character().getPlayerShop() == null
                || !seller.character().getPlayerShop().isOpen()) return Result.none();

        long ask = candidate.observation().bundlePrice();
        long offer = Math.min(candidate.need().maximumWillingnessToPay(), Math.max(1,
                Math.round(ask * (1d - .15d * buyerProfile.negotiationAggressiveness()))));
        int quantity = Math.min(candidate.observation().quantityPerBundle(), candidate.need().deficit());
        long npcFloor = npcValues.sellValue(candidate.observation().itemId(), quantity);
        long reserve = Math.max(npcFloor, Math.round(ask *
                (1d - .10d * seller.profile().negotiationAggressiveness())));
        String id = UUID.nameUUIDFromBytes((runId + ":" + buyerProfile.agentId() + ":"
                + seller.profile().agentId() + ":" + logicalAt + ":" + candidate.observation().listingId())
                .getBytes(StandardCharsets.UTF_8)).toString();
        PublicNegotiationSession session = new PublicNegotiationSession(id, buyerProfile.agentId(),
                seller.profile().agentId(), logicalAt, timeout);
        speak(buyer, buyerProfile.agentId(), seller.profile().agentId(), logicalAt, "TRADE_INVITE",
                "Would you negotiate for " + quantity + " of item " + candidate.observation().itemId() + "?",
                candidate.observation().itemId(), Map.of("listingId", candidate.observation().listingId()));
        String proposalText = "I offer " + offer + " mesos for " + quantity + " of item "
                + candidate.observation().itemId() + ".";
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
            return new Result(true, false, id, "REJECTED", candidate.observation().itemId(), offer,
                    Map.of("ask", ask, "reserve", reserve, "npcFloor", npcFloor));
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

    private Candidate candidate(Character buyer, MarketObservation observation, AgentNeed need) {
        if (observation.bundlePrice() <= need.maximumWillingnessToPay()) return null;
        if (ItemConstants.getInventoryType(observation.itemId()) == client.inventory.InventoryType.EQUIP)
            return null; // equipment identity must remain fingerprint-exact; fixed-price stalls support it today
        Participant seller = parseSeller(observation);
        if (seller == null || !nearby(buyer, seller.character())) return null;
        double surplus = need.urgency() + (need.maximumWillingnessToPay()
                / (double) Math.max(1, observation.bundlePrice()));
        return new Candidate(observation, need, surplus);
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
        String raw = runId + ":" + speakerId + ":" + targetId + ":" + at + ":" + kind + ":" + text;
        journal.appendSocial(new SocialEvidence(UUID.nameUUIDFromBytes(raw.getBytes(StandardCharsets.UTF_8)),
                runId, at, speaker.getMapId(), speakerId, targetId, kind, text, intent, itemId, null));
    }

    private static boolean matches(AgentNeed need, int itemId) {
        return need.itemId() == itemId || need.substitutes().contains(itemId);
    }

    public interface ParticipantDirectory {
        Optional<Participant> byCharacterId(int characterId);
    }
    public record Participant(Character character, EconomyAgentProfile profile) {
        public Participant { Objects.requireNonNull(character); Objects.requireNonNull(profile); }
    }
    @FunctionalInterface interface NpcValueCatalog { long sellValue(int itemId, int quantity); }
    @FunctionalInterface interface StallCloser { boolean close(Character seller, String reason); }
    @FunctionalInterface interface PublicChatGateway { void broadcast(Character speaker, String text); }
    @FunctionalInterface public interface CounterpartyNeedReader {
        List<AgentNeed> read(Character character, EconomyAgentProfile profile, Instant logicalAt);
    }
    private record Candidate(MarketObservation observation, AgentNeed need, double surplus) { }
    private record Barter(int itemId, int quantity, long mesos, long needValue,
                          long buyerOpportunityCost, long sellerValue) { }
}
