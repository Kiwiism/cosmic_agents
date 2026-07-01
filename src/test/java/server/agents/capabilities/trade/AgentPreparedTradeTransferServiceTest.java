package server.agents.capabilities.trade;

import client.inventory.Item;
import org.junit.jupiter.api.Test;
import server.agents.capabilities.inventory.AgentInventoryTradeCollectionService.PreparedTradeItems;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class AgentPreparedTradeTransferServiceTest {
    @Test
    void repliesWithPreparedError() {
        List<String> replies = new ArrayList<>();

        AgentPreparedTradeTransferService.startPreparedTradeTransfer(
                "scrolls",
                new PreparedTradeItems(List.of(), "equip bag full"),
                () -> false,
                (category, items, restoreSlots) -> {},
                replies::add);

        assertEquals(List.of("equip bag full"), replies);
    }

    @Test
    void repliesWhenPreparedItemsAreEmpty() {
        List<String> replies = new ArrayList<>();

        AgentPreparedTradeTransferService.startPreparedTradeTransfer(
                "scrolls",
                new PreparedTradeItems(List.of(), null),
                () -> false,
                (category, items, restoreSlots) -> {},
                replies::add);

        assertEquals(1, replies.size());
        assertTrue(!replies.get(0).isBlank());
    }

    @Test
    void startsPreparedTradeWithRestoreFlag() {
        Item item = mock(Item.class);
        AtomicReference<String> category = new AtomicReference<>();
        AtomicReference<List<Item>> items = new AtomicReference<>();
        AtomicBoolean restore = new AtomicBoolean(false);

        AgentPreparedTradeTransferService.startPreparedTradeTransfer(
                "name:hat",
                new PreparedTradeItems(List.of(item), null),
                () -> true,
                (startedCategory, startedItems, restoreSlots) -> {
                    category.set(startedCategory);
                    items.set(startedItems);
                    restore.set(restoreSlots);
                },
                ignored -> {});

        assertEquals("name:hat", category.get());
        assertEquals(List.of(item), items.get());
        assertTrue(restore.get());
    }
}
