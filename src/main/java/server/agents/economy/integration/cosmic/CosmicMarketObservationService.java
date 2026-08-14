package server.agents.economy.integration.cosmic;

import client.Character;
import server.agents.economy.market.MarketObservation;
import server.agents.economy.market.PrivateMarketKnowledge;
import server.agents.economy.persistence.EconomyEvidenceJournal;
import server.maps.PlayerShop;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Turns only physically nearby PlayerShop listings into private, durable knowledge. */
public final class CosmicMarketObservationService {
    private final UUID runId;
    private final AgentFreeMarketBuyerService buyer;
    private final EconomyEvidenceJournal journal;

    public CosmicMarketObservationService(UUID runId, AgentFreeMarketBuyerService buyer,
                                          EconomyEvidenceJournal journal) {
        this.runId = Objects.requireNonNull(runId); this.buyer = Objects.requireNonNull(buyer);
        this.journal = Objects.requireNonNull(journal);
    }

    public List<ObservedOffer> inspectNearby(Character agent, String logicalAgentId,
                                             Instant logicalAt, PrivateMarketKnowledge knowledge) {
        List<ObservedOffer> result = new ArrayList<>();
        for (AgentFreeMarketBuyerService.ObservedStall stall : buyer.observeNearby(agent)) {
            for (PlayerShop.ListingView listing : stall.listings()) {
                String listingId = stall.objectId() + ":" + listing.slot();
                long unitPrice = ((long) listing.bundlePrice() + listing.perBundle() - 1)
                        / listing.perBundle();
                int totalQuantity = Math.multiplyExact(listing.perBundle(), listing.bundles());
                String rawId = runId + ":" + logicalAgentId + ":" + logicalAt + ":" + listingId;
                MarketObservation observation = new MarketObservation(
                        UUID.nameUUIDFromBytes(rawId.getBytes(StandardCharsets.UTF_8)).toString(),
                        logicalAgentId, logicalAt, stall.roomMapId(),
                        Integer.toString(stall.ownerCharacterId()), listingId, listing.itemId(),
                        totalQuantity, unitPrice, listing.perBundle(), listing.bundles(),
                        listing.bundlePrice(), MarketObservation.State.LISTED);
                knowledge.observe(observation);
                journal.appendObservation(runId, observation);
                result.add(new ObservedOffer(stall.objectId(), listing.slot(), observation));
            }
        }
        return List.copyOf(result);
    }

    public AgentFreeMarketBuyerService.PurchaseResult buyObserved(
            Character agent, String logicalAgentId, ObservedOffer offer, short bundles,
            Instant logicalAt, PrivateMarketKnowledge knowledge) {
        if (!offer.observation.observerAgentId().equals(logicalAgentId)
                || offer.observation.roomMapId() != agent.getMapId())
            throw new IllegalStateException("agent cannot buy an unobserved listing");
        AgentFreeMarketBuyerService.PurchaseResult purchase = buyer.buy(
                agent, offer.shopObjectId, offer.listingSlot, bundles);
        if (purchase.success()) {
            String rawId = offer.observation.observationId() + ":sale:" + logicalAt;
            MarketObservation completed = new MarketObservation(
                    UUID.nameUUIDFromBytes(rawId.getBytes(StandardCharsets.UTF_8)).toString(),
                    logicalAgentId, logicalAt, offer.observation.roomMapId(),
                    Integer.toString(purchase.sellerCharacterId()), offer.observation.listingId(),
                    purchase.itemId(), purchase.quantity(), offer.observation.unitPrice(),
                    offer.observation.quantityPerBundle(), bundles, offer.observation.bundlePrice(),
                    MarketObservation.State.SOLD_TO_OBSERVER);
            knowledge.observe(completed);
            journal.appendObservation(runId, completed);
        }
        return purchase;
    }

    public record ObservedOffer(int shopObjectId, int listingSlot, MarketObservation observation) { }
}
