package server.agents.capabilities.trade;

import client.inventory.Item;
import org.junit.jupiter.api.Test;
import server.agents.capabilities.dialogue.AgentDialogueCatalog;
import server.agents.capabilities.dialogue.AgentInventoryDialogueReporter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class AgentReservedEquipTradeTransferServiceTest {
    @Test
    void startsReservedEquipTradeAndStoresPageMessage() {
        Item item = mock(Item.class);
        AtomicReference<String> startedCategory = new AtomicReference<>();
        AtomicReference<List<Item>> startedItems = new AtomicReference<>();
        AtomicReference<String> categoryMessage = new AtomicReference<>();

        AgentReservedEquipTradeTransferService.startReservedEquipTradeTransfer(
                "equips:reserved:2",
                List.of(item),
                () -> "reserved equips page 2/3",
                (category, items) -> {
                    startedCategory.set(category);
                    startedItems.set(items);
                },
                categoryMessage::set,
                ignored -> {});

        assertEquals("equips:reserved:2", startedCategory.get());
        assertEquals(List.of(item), startedItems.get());
        assertEquals("reserved equips page 2/3", categoryMessage.get());
    }

    @Test
    void repliesWhenReservedEquipPageHasNoItems() {
        List<String> replies = new ArrayList<>();

        AgentReservedEquipTradeTransferService.startReservedEquipTradeTransfer(
                "equips:reserved:5",
                List.of(),
                () -> "reserved equips page 1/1",
                (category, items) -> {},
                ignored -> {},
                replies::add);

        assertEquals(1, replies.size());
        List<String> expectedReplies = AgentDialogueCatalog.noItemsReplies().stream()
                .map(template -> AgentInventoryDialogueReporter.noItemsReply(
                        "equips:reserved:2", List.of(template)))
                .toList();
        assertTrue(expectedReplies.contains(replies.get(0)));
    }
}
