package server.agents.economy.integration.cosmic;

import client.Character;
import client.inventory.Inventory;
import client.inventory.InventoryType;
import client.inventory.Item;
import org.junit.jupiter.api.Test;
import server.agents.economy.catalog.*;
import server.agents.economy.decision.AgentNeed;
import server.agents.economy.market.EconomicReason;
import server.agents.economy.market.MarketObservation;
import server.agents.economy.market.PrivateMarketKnowledge;
import server.agents.economy.scenario.EconomyAgentProfile;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CosmicMarketSellerPlanReaderTest {
    @Test
    void seedsFirstEquipmentAskFromExactNpcOpportunityCost() {
        int itemId = 1302000;
        EconomyCatalog catalog = new EconomyCatalog() {
            @Override public String version() { return "test"; }
            @Override public Optional<ItemFact> item(int id) {
                return id == itemId ? Optional.of(new ItemFact(id, "Sword", 100, 10, 1,
                        Set.of(ItemCategory.EQUIPMENT), Map.of())) : Optional.empty();
            }
            @Override public List<MonsterDropFact> monsterDrops(int monsterId) { return List.of(); }
            @Override public Optional<NpcShopFact> npcShop(int npcId) { return Optional.empty(); }
        };
        Character agent = mock(Character.class); Inventory equip = mock(Inventory.class);
        when(agent.getInventory(InventoryType.EQUIP)).thenReturn(equip);
        when(agent.getInventory(InventoryType.USE)).thenReturn(mock(Inventory.class));
        when(agent.getInventory(InventoryType.SETUP)).thenReturn(mock(Inventory.class));
        when(agent.getInventory(InventoryType.ETC)).thenReturn(mock(Inventory.class));
        when(agent.getInventory(InventoryType.USE).list()).thenReturn(List.of());
        when(agent.getInventory(InventoryType.SETUP).list()).thenReturn(List.of());
        when(agent.getInventory(InventoryType.ETC).list()).thenReturn(List.of());
        Item sword = mock(Item.class); when(sword.getItemId()).thenReturn(itemId);
        when(sword.getQuantity()).thenReturn((short) 1); when(sword.getPosition()).thenReturn((short) 2);
        when(equip.list()).thenReturn(List.of(sword)); when(agent.getId()).thenReturn(1);
        when(agent.getName()).thenReturn("Seller");
        CosmicMarketSellerPlanReader reader = new CosmicMarketSellerPlanReader(catalog, 1012004,
                16, 5030000, .15, .75, (id, quantity) -> 100);

        MarketSellerPlan plan = reader.read(agent, profile(), new PrivateMarketKnowledge(),
                List.of(), Instant.parse("2026-01-01T00:00:00Z"));

        assertEquals(1, plan.stallListings().size());
        assertEquals(145, plan.stallListings().getFirst().price());
        assertTrue(plan.npcSales().isEmpty());
    }

    @Test
    void offersOnlyStockAboveAnActiveReserve() {
        int itemId = 2000000;
        EconomyCatalog catalog = new EconomyCatalog() {
            @Override public String version() { return "test"; }
            @Override public Optional<ItemFact> item(int id) { return Optional.empty(); }
            @Override public List<MonsterDropFact> monsterDrops(int monsterId) { return List.of(); }
            @Override public Optional<NpcShopFact> npcShop(int npcId) { return Optional.empty(); }
        };
        Character agent = mock(Character.class);
        Inventory equip = mock(Inventory.class); Inventory use = mock(Inventory.class);
        when(agent.getInventory(InventoryType.EQUIP)).thenReturn(equip);
        when(agent.getInventory(InventoryType.USE)).thenReturn(use);
        when(agent.getInventory(InventoryType.SETUP)).thenReturn(mock(Inventory.class));
        when(agent.getInventory(InventoryType.ETC)).thenReturn(mock(Inventory.class));
        when(equip.list()).thenReturn(List.of());
        when(agent.getInventory(InventoryType.SETUP).list()).thenReturn(List.of());
        when(agent.getInventory(InventoryType.ETC).list()).thenReturn(List.of());
        Item potions = mock(Item.class); when(potions.getItemId()).thenReturn(itemId);
        when(potions.getQuantity()).thenReturn((short) 20); when(potions.getPosition()).thenReturn((short) 2);
        when(use.list()).thenReturn(List.of(potions)); when(agent.getId()).thenReturn(1);
        when(agent.getName()).thenReturn("Seller");
        PrivateMarketKnowledge knowledge = new PrivateMarketKnowledge();
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        knowledge.observe(new MarketObservation("observation", "agent", now, 910000001,
                "other", "listing", itemId, 1, 200, 1, 1, 200,
                MarketObservation.State.LISTED));
        AgentNeed reserve = new AgentNeed(itemId, 10, 10, .8,
                EconomicReason.CONSUMABLE_RESTOCK, now, 0, Set.of(), Set.of(), "runway");
        CosmicMarketSellerPlanReader reader = new CosmicMarketSellerPlanReader(catalog, 1012004,
                16, 5030000, .15, .75, (id, quantity) -> 100);

        MarketSellerPlan plan = reader.read(agent, profile(), knowledge, List.of(reserve), now);

        assertEquals(1, plan.stallListings().size());
        assertEquals(10, plan.stallListings().getFirst().bundles());
    }

    private static EconomyAgentProfile profile() {
        return new EconomyAgentProfile("agent", "warrior", .5, .5, .5, .5,
                .5, 1, 24, .5, .5);
    }
}
