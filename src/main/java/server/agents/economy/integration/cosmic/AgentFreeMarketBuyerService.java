package server.agents.economy.integration.cosmic;

import client.Character;
import server.maps.MapObject;
import server.maps.MapObjectType;
import server.maps.PlayerShop;
import server.economy.EconomyOperationContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.IntPredicate;

/** Headless adapter over real PlayerShop visit/buy primitives with physical range checks. */
public final class AgentFreeMarketBuyerService {
    private final int interactionRangePixels;
    private final IntPredicate agentCharacterIds;

    public AgentFreeMarketBuyerService(int interactionRangePixels) {
        this(interactionRangePixels, ignored -> false);
    }

    public AgentFreeMarketBuyerService(int interactionRangePixels, IntPredicate agentCharacterIds) {
        if (interactionRangePixels <= 0) throw new IllegalArgumentException("range must be positive");
        this.interactionRangePixels = interactionRangePixels;
        this.agentCharacterIds = java.util.Objects.requireNonNull(agentCharacterIds);
    }

    public List<ObservedStall> observeNearby(Character buyer) {
        return observeNearby(buyer, ignored -> true);
    }

    public List<ObservedStall> observe(Character buyer, int shopObjectId) {
        return observeNearby(buyer, objectId -> objectId == shopObjectId);
    }

    private List<ObservedStall> observeNearby(Character buyer, IntPredicate objectFilter) {
        requireFreeMarketRoom(buyer);
        long maximumDistance = (long) interactionRangePixels * interactionRangePixels;
        List<ObservedStall> result = new ArrayList<>();
        for (MapObject object : buyer.getMap().getMapObjectsInRange(buyer.getPosition(), maximumDistance,
                List.of(MapObjectType.SHOP))) {
            if (object instanceof PlayerShop shop && objectFilter.test(shop.getObjectId())
                    && shop.isOpen() && !shop.isOwner(buyer)) {
                result.add(new ObservedStall(shop.getObjectId(), shop.getOwnerId(), shop.getOwnerName(),
                        buyer.getMapId(), shop.getPosition().x,
                        shop.getEscrowId() == null ? "legacy:" + shop.getObjectId() : shop.getEscrowId(),
                        shop.listingSnapshot()));
            }
        }
        result.sort(Comparator.comparingInt(ObservedStall::objectId));
        return List.copyOf(result);
    }

    public PurchaseResult buy(Character buyer, int shopObjectId, int listingSlot, short bundles) {
        if (bundles <= 0) throw new IllegalArgumentException("bundles must be positive");
        requireFreeMarketRoom(buyer);
        MapObject object = buyer.getMap().getMapObject(shopObjectId);
        if (!(object instanceof PlayerShop shop) || !shop.isOpen() || shop.isOwner(buyer))
            return PurchaseResult.failed("stall is unavailable");
        if (buyer.getPosition().distanceSq(shop.getPosition())
                > (long) interactionRangePixels * interactionRangePixels)
            return PurchaseResult.failed("buyer has not walked within stall range");
        PlayerShop.ListingView listing = shop.listingSnapshot().stream()
                .filter(view -> view.slot() == listingSlot).findFirst().orElse(null);
        if (listing == null || listing.bundles() < bundles)
            return PurchaseResult.failed("listing changed before purchase");
        int beforeMeso = buyer.getMeso();
        if (!shop.visitShop(buyer)) return PurchaseResult.failed("stall visitor slots are full");
        try {
            boolean success = EconomyOperationContext.withParticipantFlags(true,
                    agentCharacterIds.test(shop.getOwnerId()),
                    () -> shop.buy(buyer.getClient(), listingSlot, bundles));
            return new PurchaseResult(success, success ? "SUCCESS" : "COSMIC_REJECTED",
                    shop.getOwnerId(), listing.itemId(), listing.perBundle() * bundles,
                    buyer.getMeso() - beforeMeso);
        } finally {
            if (shop.isVisitor(buyer)) shop.removeVisitor(buyer);
            buyer.setPlayerShop(null);
        }
    }

    private static void requireFreeMarketRoom(Character buyer) {
        if (buyer == null || buyer.getClient() == null || buyer.getMap() == null
                || buyer.getMapId() < 910000001 || buyer.getMapId() > 910000022)
            throw new IllegalStateException("buyer must be physically present in a Free Market room");
    }

    public record ObservedStall(int objectId, int ownerCharacterId, String ownerName,
                                int roomMapId, int x, String listingNamespace,
                                List<PlayerShop.ListingView> listings) { }
    public record PurchaseResult(boolean success, String result, int sellerCharacterId,
                                 int itemId, int quantity, int buyerMesoDelta) {
        private static PurchaseResult failed(String reason) {
            return new PurchaseResult(false, reason, 0, 0, 0, 0);
        }
    }
}
