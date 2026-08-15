package server.agents.economy.integration.cosmic;

import client.Character;
import client.inventory.InventoryType;
import client.inventory.Item;
import server.ItemInformationProvider;
import server.agents.capabilities.shop.AgentFreeMarketStallService;
import server.agents.economy.catalog.EconomyCatalog;
import server.agents.economy.catalog.ItemCategory;
import server.agents.economy.decision.AgentNeed;
import server.agents.economy.decision.ItemDispositionPolicy;
import server.agents.economy.market.PrivateMarketKnowledge;
import server.agents.economy.market.MarketRoomAllocator;
import server.agents.economy.scenario.EconomyAgentProfile;
import server.maps.reservation.FreeMarketCharacterSpaceCatalog;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/** Reads only actual inventory plus this seller's private observations. */
public final class CosmicMarketSellerPlanReader {
    private final EconomyCatalog catalog;
    private final int dispositionNpcId;
    private final int maximumListings;
    private final int permitItemId;
    private final ItemDispositionPolicy disposition = new ItemDispositionPolicy();
    private final double coldStartMarkupMinimum;
    private final double coldStartMarkupMaximum;
    private final NpcPriceCatalog npcPrices;
    private final int firstRoomMapId;
    private final int lastRoomMapId;
    private final MarketRoomAllocator roomAllocator;

    public CosmicMarketSellerPlanReader(EconomyCatalog catalog, int dispositionNpcId,
                                        int maximumListings, int permitItemId) {
        this(catalog, dispositionNpcId, maximumListings, permitItemId, .15, .75);
    }

    public CosmicMarketSellerPlanReader(EconomyCatalog catalog, int dispositionNpcId,
                                        int maximumListings, int permitItemId,
                                        double coldStartMarkupMinimum, double coldStartMarkupMaximum) {
        this(catalog, dispositionNpcId, maximumListings, permitItemId, coldStartMarkupMinimum,
                coldStartMarkupMaximum, 910000001, 910000022, (itemId, quantity) -> Math.max(0,
                        ItemInformationProvider.getInstance().getPrice(itemId, quantity)));
    }

    public CosmicMarketSellerPlanReader(EconomyCatalog catalog, int dispositionNpcId,
                                        int maximumListings, int permitItemId,
                                        double coldStartMarkupMinimum, double coldStartMarkupMaximum,
                                        int firstRoomMapId, int lastRoomMapId) {
        this(catalog, dispositionNpcId, maximumListings, permitItemId, coldStartMarkupMinimum,
                coldStartMarkupMaximum, firstRoomMapId, lastRoomMapId, (itemId, quantity) -> Math.max(0,
                        ItemInformationProvider.getInstance().getPrice(itemId, quantity)));
    }

    CosmicMarketSellerPlanReader(EconomyCatalog catalog, int dispositionNpcId,
                                 int maximumListings, int permitItemId,
                                 double coldStartMarkupMinimum, double coldStartMarkupMaximum,
                                 NpcPriceCatalog npcPrices) {
        this(catalog, dispositionNpcId, maximumListings, permitItemId, coldStartMarkupMinimum,
                coldStartMarkupMaximum, 910000001, 910000022, npcPrices);
    }

    CosmicMarketSellerPlanReader(EconomyCatalog catalog, int dispositionNpcId,
                                 int maximumListings, int permitItemId,
                                 double coldStartMarkupMinimum, double coldStartMarkupMaximum,
                                 int firstRoomMapId, int lastRoomMapId,
                                 NpcPriceCatalog npcPrices) {
        this(catalog, dispositionNpcId, maximumListings, permitItemId, coldStartMarkupMinimum,
                coldStartMarkupMaximum, firstRoomMapId, lastRoomMapId, npcPrices,
                new MarketRoomAllocator(firstRoomMapId, lastRoomMapId,
                        room -> FreeMarketCharacterSpaceCatalog.spaces(room).size()));
    }

    CosmicMarketSellerPlanReader(EconomyCatalog catalog, int dispositionNpcId,
                                 int maximumListings, int permitItemId,
                                 double coldStartMarkupMinimum, double coldStartMarkupMaximum,
                                 int firstRoomMapId, int lastRoomMapId,
                                 NpcPriceCatalog npcPrices, MarketRoomAllocator roomAllocator) {
        this.catalog = Objects.requireNonNull(catalog); this.dispositionNpcId = dispositionNpcId;
        this.maximumListings = maximumListings; this.permitItemId = permitItemId;
        if (!Double.isFinite(coldStartMarkupMinimum) || !Double.isFinite(coldStartMarkupMaximum)
                || coldStartMarkupMinimum < 0 || coldStartMarkupMaximum < coldStartMarkupMinimum)
            throw new IllegalArgumentException("cold-start markups must be finite, non-negative, and ordered");
        this.coldStartMarkupMinimum = coldStartMarkupMinimum;
        this.coldStartMarkupMaximum = coldStartMarkupMaximum;
        if (firstRoomMapId < 910000001 || lastRoomMapId > 910000022
                || firstRoomMapId > lastRoomMapId)
            throw new IllegalArgumentException("invalid configured FM room range");
        this.firstRoomMapId = firstRoomMapId;
        this.lastRoomMapId = lastRoomMapId;
        this.npcPrices = Objects.requireNonNull(npcPrices);
        this.roomAllocator = Objects.requireNonNull(roomAllocator);
    }

    public MarketSellerPlan read(Character agent, EconomyAgentProfile profile,
                                 PrivateMarketKnowledge knowledge, List<AgentNeed> needs, Instant now) {
        Map<Integer, Integer> reserved = new HashMap<>();
        needs.forEach(need -> {
            int protectedQuantity = Math.min(need.currentQuantity(), need.targetQuantity());
            if (protectedQuantity > 0) reserved.merge(need.itemId(), protectedQuantity, Math::addExact);
            if (need.deficit() > 0) need.substitutes().forEach(itemId -> {
                int owned = count(agent, itemId);
                if (owned > 0) reserved.merge(itemId, owned, Math::addExact);
            });
        });
        List<MarketSellerPlan.NpcSale> npcSales = new ArrayList<>();
        List<AgentFreeMarketStallService.Listing> listings = new ArrayList<>();
        Duration memory = Duration.ofHours(profile.priceMemoryHours());
        for (InventoryType type : List.of(InventoryType.EQUIP, InventoryType.USE,
                InventoryType.SETUP, InventoryType.ETC)) {
            for (Item item : agent.getInventory(type).list()) {
                if (item.getQuantity() <= 0 || item.getItemId() == permitItemId) continue;
                int protectedQuantity = Math.min(item.getQuantity(),
                        reserved.getOrDefault(item.getItemId(), 0));
                if (protectedQuantity > 0) reserved.compute(item.getItemId(),
                        (ignored, quantity) -> quantity == null || quantity <= protectedQuantity
                                ? null : quantity - protectedQuantity);
                int availableQuantity = item.getQuantity() - protectedQuantity;
                if (availableQuantity <= 0) continue;
                long market = knowledge.observedMedianAsk(item.getItemId(), now, memory);
                long npc = npcPrices.price(item.getItemId(), 1);
                boolean scarce = catalog.item(item.getItemId()).map(fact -> fact.categories().stream().anyMatch(
                        category -> category == ItemCategory.EQUIPMENT || category == ItemCategory.EQUIP_SCROLL
                                || category == ItemCategory.CHAIR || category == ItemCategory.QUEST_ITEM)).orElse(false);
                long coldStartAsk = market == 0 && scarce && npc > 0
                        ? coldStartAsk(npc, profile) : 0;
                if (market == 0 && scarce && coldStartAsk == 0) continue;
                double saleProbability = Math.min(.95,
                        knowledge.recentFor(item.getItemId(), now, memory).size() / 5d);
                var choice = coldStartAsk > 0
                        ? new ItemDispositionPolicy.Decision(ItemDispositionPolicy.Action.LIST_AT_STALL,
                        coldStartAsk, server.agents.economy.market.EconomicReason.NPC_FLOOR_DISPOSITION,
                        "coldStartNpcFloor=" + npc + " sellerMarkup=" + (coldStartAsk / (double) npc - 1d))
                        : disposition.decide(new ItemDispositionPolicy.Input(0, npc, market,
                        0, Math.max(1, npc / 20), saleProbability, false));
                if (choice.action() == ItemDispositionPolicy.Action.LIST_AT_STALL
                        && listings.size() < maximumListings) {
                    short perBundle = perBundle(availableQuantity, item.getItemId(), knowledge, now, memory);
                    short bundles = (short) Math.min(Short.MAX_VALUE, availableQuantity / perBundle);
                    long bundlePrice = Math.multiplyExact(choice.unitPrice(), perBundle);
                    if (bundles > 0 && bundlePrice > 0 && bundlePrice <= Integer.MAX_VALUE) {
                        listings.add(new AgentFreeMarketStallService.Listing(type, item.getPosition(),
                                perBundle, bundles, (int) bundlePrice));
                    }
                } else if (choice.action() == ItemDispositionPolicy.Action.SELL_TO_NPC) {
                    npcSales.add(new MarketSellerPlan.NpcSale(dispositionNpcId, type, item.getPosition(),
                            (short) availableQuantity, item.getItemId(), choice.reason().name(), choice.evidence()));
                }
            }
        }
        int room;
        if (listings.isEmpty()) {
            roomAllocator.release(profile.agentId());
            room = firstRoomMapId;
        } else {
            room = roomAllocator.roomFor(profile.agentId());
        }
        return new MarketSellerPlan(npcSales, listings, room, "Selling real finds - " + agent.getName());
    }

    public void releaseRoom(String sellerAgentId) {
        roomAllocator.release(sellerAgentId);
    }

    private long coldStartAsk(long npc, EconomyAgentProfile profile) {
        double disposition = (profile.riskTolerance() + (1d - profile.liquidityPreference())) / 2d;
        double markup = coldStartMarkupMinimum
                + (coldStartMarkupMaximum - coldStartMarkupMinimum) * disposition;
        return Math.max(npc + 1, Math.round(npc * (1d + markup)));
    }

    private static short perBundle(int availableQuantity, int itemId, PrivateMarketKnowledge knowledge,
                                   Instant now, Duration memory) {
        List<Integer> observed = knowledge.recentFor(itemId, now, memory).stream()
                .map(observation -> observation.quantityPerBundle()).sorted().toList();
        int desired = observed.isEmpty() ? (availableQuantity >= 10 ? 10 : 1)
                : observed.get(observed.size() / 2);
        return (short) Math.max(1, Math.min(availableQuantity, desired));
    }

    private static int count(Character agent, int itemId) {
        return agent.getInventory(constants.inventory.ItemConstants.getInventoryType(itemId)).countById(itemId);
    }

    @FunctionalInterface interface NpcPriceCatalog { long price(int itemId, int quantity); }
}
