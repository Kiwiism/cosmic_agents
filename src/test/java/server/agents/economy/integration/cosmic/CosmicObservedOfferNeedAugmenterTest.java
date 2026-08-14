package server.agents.economy.integration.cosmic;

import client.Character;
import client.Job;
import client.inventory.Equip;
import client.inventory.Inventory;
import client.inventory.InventoryType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import server.agents.economy.decision.AgentNeed;
import server.agents.economy.market.EconomicReason;
import server.agents.economy.market.MarketObservation;
import server.agents.economy.scenario.EconomyAgentProfile;
import server.agents.economy.scenario.EconomyEngineConfig;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

class CosmicObservedOfferNeedAugmenterTest {
    private final Instant now = Instant.parse("2026-01-01T00:00:00Z");
    private Character agent;
    private Inventory equipInventory;
    private Inventory equippedInventory;
    private Inventory useInventory;
    private Inventory setupInventory;
    private CosmicObservedOfferNeedAugmenter.Catalog items;
    private CosmicObservedOfferNeedAugmenter augmenter;

    @BeforeEach
    void setUp() {
        agent = mock(Character.class);
        equipInventory = mock(Inventory.class); equippedInventory = mock(Inventory.class);
        useInventory = mock(Inventory.class); setupInventory = mock(Inventory.class);
        when(agent.getInventory(InventoryType.EQUIP)).thenReturn(equipInventory);
        when(agent.getInventory(InventoryType.EQUIPPED)).thenReturn(equippedInventory);
        when(agent.getInventory(InventoryType.USE)).thenReturn(useInventory);
        when(agent.getInventory(InventoryType.SETUP)).thenReturn(setupInventory);
        when(equipInventory.list()).thenReturn(List.of());
        when(equippedInventory.list()).thenReturn(List.of());
        when(equipInventory.countById(anyInt())).thenReturn(0);
        when(useInventory.countById(anyInt())).thenReturn(0);
        when(setupInventory.countById(anyInt())).thenReturn(0);
        when(agent.getJob()).thenReturn(Job.WARRIOR);
        when(agent.getMeso()).thenReturn(100_000);

        EconomyEngineConfig.Demand demand = new EconomyEngineConfig.Demand();
        demand.equipmentMaximumWalletFraction = .5;
        demand.scrollMaximumWalletFraction = .2;
        demand.chairMaximumWalletFraction = .1;
        demand.utilityMesoScale = 1_000;
        demand.minimumMarginalUtility = .01;
        EconomyEngineConfig.Scrolling scrolling = new EconomyEngineConfig.Scrolling();
        scrolling.enabled = true; scrolling.requireOwnedEquipment = true;
        scrolling.requireRemainingSlots = true;
        EconomyEngineConfig.Chairs chairs = new EconomyEngineConfig.Chairs();
        chairs.enabled = true; chairs.collectionPreferenceEnabled = true;
        items = mock(CosmicObservedOfferNeedAugmenter.Catalog.class);
        augmenter = new CosmicObservedOfferNeedAugmenter(demand, scrolling, chairs, items);
    }

    @Test
    void createsEquipmentNeedFromExactObservedStatsWithoutUsingAskAsValue() {
        int itemId = 1102053;
        when(items.meetsEquipRequirements(agent, itemId)).thenReturn(true);
        when(items.getEquipmentSlot(itemId)).thenReturn("Cp");
        MarketObservation observation = observation(itemId, 9_999_999,
                Map.of("dex", 5, "str", 1, "upgradeSlots", 5));

        AgentNeed need = augmenter.augment(agent, profile(.4), List.of(observation), List.of(), now).getFirst();

        assertEquals(EconomicReason.EQUIPMENT_UPGRADE, need.reason());
        assertEquals(4_750, need.maximumWillingnessToPay());
        assertNotEquals(observation.bundlePrice(), need.maximumWillingnessToPay());
        assertTrue(need.evidence().contains("fingerprint=fingerprint"));
    }

    @Test
    void warriorValuesDexCapeScrollWhenACompatibleOwnedCapeHasSlots() {
        int scrollId = 2041000;
        Equip cape = mock(Equip.class);
        when(cape.getItemId()).thenReturn(1102053);
        when(cape.getUpgradeSlots()).thenReturn((byte) 5);
        when(equipInventory.list()).thenReturn(List.of(cape));
        when(items.canApplyScroll(scrollId, 1102053)).thenReturn(true);
        when(items.getEquipStats(scrollId)).thenReturn(Map.of("success", 60, "cursed", 0, "DEX", 2));

        AgentNeed need = augmenter.augment(agent, profile(.4), List.of(
                observation(scrollId, 1_000, Map.of())), List.of(), now).getFirst();

        assertEquals(EconomicReason.SCROLL_UPGRADE, need.reason());
        assertEquals(1, need.deficit());
        assertTrue(need.maximumWillingnessToPay() > 0);
        assertTrue(need.complements().contains(1102053));
    }

    @Test
    void doesNotAccumulateScrollsWhileAnUnconsumedProjectScrollIsOwned() {
        int scrollId = 2041000;
        Equip cape = mock(Equip.class);
        when(cape.getItemId()).thenReturn(1102053);
        when(cape.getUpgradeSlots()).thenReturn((byte) 5);
        when(equipInventory.list()).thenReturn(List.of(cape));
        when(useInventory.countById(scrollId)).thenReturn(1);
        when(items.canApplyScroll(scrollId, 1102053)).thenReturn(true);
        when(items.getEquipStats(scrollId)).thenReturn(Map.of("success", 60, "cursed", 0, "DEX", 2));

        AgentNeed pending = augmenter.augment(agent, profile(.4),
                List.of(observation(scrollId, 1_000, Map.of())), List.of(), now).getFirst();
        assertEquals(0, pending.deficit());
    }

    @Test
    void chairRequiresPreferenceAndAbsenceFromActualInventory() {
        MarketObservation chair = observation(3010000, 500, Map.of());
        assertEquals(EconomicReason.COLLECTIBLE_OR_CHAIR,
                augmenter.augment(agent, profile(.6), List.of(chair), List.of(), now).getFirst().reason());
        assertTrue(augmenter.augment(agent, profile(0), List.of(chair), List.of(), now).isEmpty());
        when(setupInventory.countById(3010000)).thenReturn(1);
        assertTrue(augmenter.augment(agent, profile(.6), List.of(chair), List.of(), now).isEmpty());
    }

    private MarketObservation observation(int itemId, long bundlePrice, Map<String, Object> attributes) {
        return new MarketObservation("00000000-0000-0000-0000-000000000001", "agent-1", now,
                910000001, "seller", "escrow:0", itemId, 1, bundlePrice, 1, 1,
                bundlePrice, "fingerprint", attributes, MarketObservation.State.LISTED);
    }

    private static EconomyAgentProfile profile(double chairInterest) {
        return new EconomyAgentProfile("agent-1", "warrior", .5, .5, .5, .5,
                .5, .5, 24, .5, chairInterest);
    }
}
