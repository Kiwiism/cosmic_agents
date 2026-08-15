package server.agents.economy.integration.cosmic;

import client.Character;
import server.ItemInformationProvider;
import server.agents.economy.market.StallOffer;
import server.agents.economy.persistence.StallOfferStore;
import server.agents.economy.scenario.EconomyAgentProfile;
import server.agents.integration.AgentPacketGatewayRuntime;
import server.maps.PlayerShop;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/** Seller-side review of durable numeric offers. Public replies are renderings, never inputs. */
public final class CosmicStallOfferReviewService
        implements AutonomousFreeMarketBehavior.OfferReviewBehavior {
    private final java.util.UUID runId;
    private final StallOfferStore offers;
    private final NpcValueCatalog npcValues;
    private final PublicChatGateway chat;

    public CosmicStallOfferReviewService(java.util.UUID runId, StallOfferStore offers) {
        this(runId, offers, (itemId, quantity) -> Math.max(0,
                ItemInformationProvider.getInstance().getPrice(itemId, quantity)),
                (speaker, text) -> AgentPacketGatewayRuntime.packets()
                        .broadcastChatText(speaker, text, false, 1));
    }

    CosmicStallOfferReviewService(java.util.UUID runId, StallOfferStore offers,
                                  NpcValueCatalog npcValues) {
        this(runId, offers, npcValues, (speaker, text) -> { });
    }

    CosmicStallOfferReviewService(java.util.UUID runId, StallOfferStore offers,
                                  NpcValueCatalog npcValues, PublicChatGateway chat) {
        this.runId = Objects.requireNonNull(runId);
        this.offers = Objects.requireNonNull(offers);
        this.npcValues = Objects.requireNonNull(npcValues);
        this.chat = Objects.requireNonNull(chat);
    }

    @Override
    public Result reviewNext(Character seller, EconomyAgentProfile profile, Instant logicalAt) {
        StallOffer offer = offers.pendingForSeller(runId, profile.agentId(), logicalAt, 1)
                .stream().findFirst().orElse(null);
        if (offer == null) return Result.none();
        if (!logicalAt.isBefore(offer.expiresAt())) {
            return resolve(seller, offer, StallOffer.Status.EXPIRED,
                    "sorry, I saw your offer too late.", logicalAt, "EXPIRED");
        }
        PlayerShop shop = seller.getPlayerShop();
        PlayerShop.ListingView listing = matchingListing(shop, offer);
        if (seller.getMapId() != offer.roomMapId() || listing == null) {
            return resolve(seller, offer, StallOffer.Status.CANCELLED_LISTING_CHANGED,
                    "sorry, that exact listing is no longer available.", logicalAt,
                    "LISTING_CHANGED");
        }
        long npcFloor = npcValues.sellValue(offer.itemId(), offer.quantity());
        long reserve = Math.max(npcFloor, Math.round(offer.askMesos()
                * (1d - .10d * profile.negotiationAggressiveness())));
        if (offer.offeredMesos() < reserve) {
            String response = "thanks, but I need at least " + mesos(reserve) + " for it.";
            return resolve(seller, offer, StallOffer.Status.REJECTED, response, logicalAt,
                    "BELOW_RESERVE", Map.of("reserveMesos", reserve, "npcFloorMesos", npcFloor));
        }
        String response = "deal at " + mesos(offer.offeredMesos())
                + "; come back so we can complete the exact-item trade.";
        return resolve(seller, offer, StallOffer.Status.ACCEPTED_AWAITING_SETTLEMENT,
                response, logicalAt, "ACCEPTED_AWAITING_SETTLEMENT",
                Map.of("reserveMesos", reserve, "npcFloorMesos", npcFloor));
    }

    private Result resolve(Character seller, StallOffer offer, StallOffer.Status status,
                           String response, Instant at, String outcome) {
        return resolve(seller, offer, status, response, at, outcome, Map.of());
    }

    private Result resolve(Character seller, StallOffer offer, StallOffer.Status status,
                           String response, Instant at, String outcome, Map<String, Object> evidence) {
        offers.resolve(offer.offerId(), status, response, at, null);
        chat.broadcast(seller, "@" + offer.buyerAgentId() + " " + response);
        var details = new java.util.LinkedHashMap<String, Object>(evidence);
        details.put("offerId", offer.offerId().toString());
        details.put("listingId", offer.listingId());
        details.put("itemFingerprint", offer.itemFingerprint());
        details.put("askMesos", offer.askMesos());
        details.put("offeredMesos", offer.offeredMesos());
        return new Result(true, status == StallOffer.Status.ACCEPTED_AWAITING_SETTLEMENT,
                offer.offerId().toString(), outcome, offer.itemId(), Map.copyOf(details));
    }

    private static PlayerShop.ListingView matchingListing(PlayerShop shop, StallOffer offer) {
        if (shop == null || !shop.isOpen()) return null;
        int slot = listingSlot(offer.listingId());
        return shop.listingSnapshot().stream().filter(listing -> listing.slot() == slot
                        && listing.itemId() == offer.itemId()
                        && listing.fingerprint().equals(offer.itemFingerprint())
                        && listing.perBundle() >= offer.quantity()
                        && listing.bundlePrice() == offer.askMesos())
                .findFirst().orElse(null);
    }

    private static int listingSlot(String listingId) {
        try { return Integer.parseInt(listingId.substring(listingId.lastIndexOf(':') + 1)); }
        catch (RuntimeException ignored) { return -1; }
    }

    private static String mesos(long value) {
        if (value >= 1_000_000 && value % 1_000_000 == 0) return (value / 1_000_000) + "m";
        if (value >= 1_000_000 && value % 100_000 == 0) return (value / 1_000_000d) + "m";
        if (value >= 1_000 && value % 100 == 0) return (value / 1_000) + "k";
        return value + " mesos";
    }

    @FunctionalInterface interface NpcValueCatalog { long sellValue(int itemId, int quantity); }
    @FunctionalInterface interface PublicChatGateway { void broadcast(Character seller, String text); }
}
