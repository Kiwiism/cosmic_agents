package server.agents.capabilities.trade;

import client.Character;
import client.inventory.Item;
import client.inventory.WeaponType;
import org.junit.jupiter.api.Test;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentTradeTransferAvailabilityRuntimeServiceTest {
    @Test
    void namedAvailabilityChecksEquippedSlotsBeforeCollectingItems() {
        Character agent = mock(Character.class);
        AtomicReference<String> equippedFragment = new AtomicReference<>();
        AtomicBoolean recommendationsCollected = new AtomicBoolean();

        boolean available = AgentTradeTransferAvailabilityRuntimeService.hasTransferableItems(
                "name:hat",
                entry(),
                agent,
                AgentTradeTransferAvailabilityRuntimeService.RuntimeCallbacks.of(
                        ignored -> null,
                        (seenAgent, fragment) -> 0,
                        (seenAgent, fragment) -> {
                            equippedFragment.set(fragment);
                            return 1;
                        }),
                inventoryCallbacks(recommendationsCollected, List.of()));

        assertTrue(available);
        assertEquals("hat", equippedFragment.get());
        assertFalse(recommendationsCollected.get());
    }

    @Test
    void namedCountCombinesNamedAndEquippedSlotCounts() {
        Character agent = mock(Character.class);

        int count = AgentTradeTransferAvailabilityRuntimeService.countTransferableItems(
                "name:cape",
                entry(),
                agent,
                AgentTradeTransferAvailabilityRuntimeService.RuntimeCallbacks.of(
                        ignored -> null,
                        (seenAgent, fragment) -> 2,
                        (seenAgent, fragment) -> 3),
                inventoryCallbacks(new AtomicBoolean(), List.of()));

        assertEquals(5, count);
    }

    @Test
    void genericCountUsesAgentInventoryTradeRuntimeCollection() {
        Character agent = mock(Character.class);
        Character owner = mock(Character.class);
        Item recommended = mock(Item.class);
        when(recommended.getQuantity()).thenReturn((short) 4);
        AtomicBoolean recommendationsCollected = new AtomicBoolean();

        int count = AgentTradeTransferAvailabilityRuntimeService.countTransferableItems(
                "recommended",
                entry(),
                agent,
                AgentTradeTransferAvailabilityRuntimeService.RuntimeCallbacks.of(
                        ignored -> owner,
                        (seenAgent, fragment) -> 0,
                        (seenAgent, fragment) -> 0),
                inventoryCallbacks(recommendationsCollected, List.of(recommended)));

        assertEquals(4, count);
        assertTrue(recommendationsCollected.get());
    }

    private static AgentRuntimeEntry entry() {
        return new AgentRuntimeEntry(mock(Character.class), null, null);
    }

    private static AgentInventoryTradeRuntimeService.RuntimeCallbacks inventoryCallbacks(AtomicBoolean recommendationsCollected,
                                                                                       List<Item> recommendedItems) {
        return AgentInventoryTradeRuntimeService.RuntimeCallbacks.of(
                (owner, agent) -> {
                    recommendationsCollected.set(true);
                    return recommendedItems;
                },
                ignored -> WeaponType.NOT_A_WEAPON,
                ignored -> 0,
                ignored -> false,
                () -> false,
                () -> false,
                ignored -> Set.of(),
                ignored -> false,
                () -> null);
    }
}
