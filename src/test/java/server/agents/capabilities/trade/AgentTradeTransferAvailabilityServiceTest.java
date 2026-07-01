package server.agents.capabilities.trade;

import client.Character;
import client.inventory.Item;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentTradeTransferAvailabilityServiceTest {
    @Test
    void namedCategoryAvailabilityUsesEquippedSlotCounterBeforeBagCollection() {
        Character agent = mock(Character.class);
        AtomicReference<String> countedFragment = new AtomicReference<>();
        AtomicBoolean collected = new AtomicBoolean();

        boolean result = AgentTradeTransferAvailabilityService.hasTransferableItems(
                "name:hat",
                agent,
                fragment -> {
                    countedFragment.set(fragment);
                    return 1;
                },
                () -> {
                    collected.set(true);
                    return List.of();
                });

        assertTrue(result);
        assertEquals("hat", countedFragment.get());
        assertFalse(collected.get());
    }

    @Test
    void namedCategoryCountCombinesBagAndEquippedSlotCounts() {
        Character agent = mock(Character.class);

        int count = AgentTradeTransferAvailabilityService.countTransferableItems(
                "name:cape",
                agent,
                fragment -> 2,
                fragment -> 3,
                List::of);

        assertEquals(5, count);
    }

    @Test
    void genericCategoryAvailabilityUsesCollectedItems() {
        Character agent = mock(Character.class);
        Item item = item(2000, 4);
        AtomicBoolean collected = new AtomicBoolean();

        boolean result = AgentTradeTransferAvailabilityService.hasTransferableItems(
                "pots",
                agent,
                fragment -> 0,
                () -> {
                    collected.set(true);
                    return List.of(item);
                });

        assertTrue(result);
        assertTrue(collected.get());
    }

    @Test
    void callbackOverloadPreservesAvailabilityBehavior() {
        Character agent = mock(Character.class);
        Item item = item(2000, 4);

        boolean result = AgentTradeTransferAvailabilityService.hasTransferableItems(
                "pots",
                agent,
                AgentTradeTransferAvailabilityService.TransferAvailabilityCallbacks.of(
                        fragment -> 0,
                        fragment -> 0,
                        () -> List.of(item)));

        assertTrue(result);
    }

    @Test
    void genericCategoryCountSumsCollectedItemQuantities() {
        Character agent = mock(Character.class);
        Item first = item(2000, 4);
        Item second = item(2001, 6);
        AtomicBoolean collected = new AtomicBoolean();

        int count = AgentTradeTransferAvailabilityService.countTransferableItems(
                "pots",
                agent,
                fragment -> 0,
                fragment -> 0,
                () -> {
                    collected.set(true);
                    return List.of(first, second);
                });

        assertEquals(10, count);
        assertTrue(collected.get());
    }

    @Test
    void callbackOverloadPreservesCountBehavior() {
        Character agent = mock(Character.class);
        Item first = item(2000, 4);
        Item second = item(2001, 6);

        int count = AgentTradeTransferAvailabilityService.countTransferableItems(
                "pots",
                agent,
                AgentTradeTransferAvailabilityService.TransferAvailabilityCallbacks.of(
                        fragment -> 0,
                        fragment -> 0,
                        () -> List.of(first, second)));

        assertEquals(10, count);
    }

    @Test
    void emptyGenericCollectionIsUnavailable() {
        Character agent = mock(Character.class);

        boolean result = AgentTradeTransferAvailabilityService.hasTransferableItems(
                "etc",
                agent,
                fragment -> 0,
                List::of);

        assertFalse(result);
    }

    private static Item item(int itemId, int quantity) {
        Item item = mock(Item.class);
        when(item.getItemId()).thenReturn(itemId);
        when(item.getQuantity()).thenReturn((short) quantity);
        return item;
    }
}
