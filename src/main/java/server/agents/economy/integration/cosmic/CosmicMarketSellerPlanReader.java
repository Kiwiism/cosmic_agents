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
import server.agents.economy.scenario.EconomyAgentProfile;

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

    public CosmicMarketSellerPlanReader(EconomyCatalog catalog, int dispositionNpcId,
                                        int maximumListings, int permitItemId) {
        this.catalog = Objects.requireNonNull(catalog); this.dispositionNpcId = dispositionNpcId;
        this.maximumListings = maximumListings; this.permitItemId = permitItemId;
    }

    public MarketSellerPlan read(Character agent, EconomyAgentProfile profile,
                                 PrivateMarketKnowledge knowledge, List<AgentNeed> needs, Instant now) {
        Set<Integer> reserved = new HashSet<>();
        needs.stream().filter(need -> need.deficit() > 0).forEach(need -> {
            reserved.add(need.itemId()); reserved.addAll(need.substitutes());
        });
        List<MarketSellerPlan.NpcSale> npcSales = new ArrayList<>();
        List<AgentFreeMarketStallService.Listing> listings = new ArrayList<>();
        Duration memory = Duration.ofHours(profile.priceMemoryHours());
        ItemInformationProvider information = ItemInformationProvider.getInstance();
        for (InventoryType type : List.of(InventoryType.EQUIP, InventoryType.USE,
                InventoryType.SETUP, InventoryType.ETC)) {
            for (Item item : agent.getInventory(type).list()) {
                if (item.getQuantity() <= 0 || item.getItemId() == permitItemId
                        || reserved.contains(item.getItemId())) continue;
                long market = knowledge.observedMedianAsk(item.getItemId(), now, memory);
                long npc = Math.max(0, information.getPrice(item.getItemId(), 1));
                boolean scarce = catalog.item(item.getItemId()).map(fact -> fact.categories().stream().anyMatch(
                        category -> category == ItemCategory.EQUIPMENT || category == ItemCategory.EQUIP_SCROLL
                                || category == ItemCategory.CHAIR || category == ItemCategory.QUEST_ITEM)).orElse(false);
                if (market == 0 && scarce) continue;
                double saleProbability = Math.min(.95,
                        knowledge.recentFor(item.getItemId(), now, memory).size() / 5d);
                var choice = disposition.decide(new ItemDispositionPolicy.Input(0, npc, market,
                        0, Math.max(1, npc / 20), saleProbability, false));
                if (choice.action() == ItemDispositionPolicy.Action.LIST_AT_STALL
                        && listings.size() < maximumListings) {
                    short perBundle = perBundle(item, knowledge, now, memory);
                    short bundles = (short) Math.min(Short.MAX_VALUE, item.getQuantity() / perBundle);
                    long bundlePrice = Math.multiplyExact(choice.unitPrice(), perBundle);
                    if (bundles > 0 && bundlePrice > 0 && bundlePrice <= Integer.MAX_VALUE) {
                        listings.add(new AgentFreeMarketStallService.Listing(type, item.getPosition(),
                                perBundle, bundles, (int) bundlePrice));
                    }
                } else if (choice.action() == ItemDispositionPolicy.Action.SELL_TO_NPC) {
                    npcSales.add(new MarketSellerPlan.NpcSale(dispositionNpcId, type, item.getPosition(),
                            item.getQuantity(), item.getItemId(), choice.reason().name(), choice.evidence()));
                }
            }
        }
        int room = 910000001 + Math.floorMod(agent.getId(), 22);
        return new MarketSellerPlan(npcSales, listings, room, "Selling real finds - " + agent.getName());
    }

    private static short perBundle(Item item, PrivateMarketKnowledge knowledge,
                                   Instant now, Duration memory) {
        List<Integer> observed = knowledge.recentFor(item.getItemId(), now, memory).stream()
                .map(observation -> observation.quantityPerBundle()).sorted().toList();
        int desired = observed.isEmpty() ? (item.getQuantity() >= 10 ? 10 : 1)
                : observed.get(observed.size() / 2);
        return (short) Math.max(1, Math.min(item.getQuantity(), desired));
    }
}
