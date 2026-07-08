package server.agents.capabilities.trade;

import client.Character;
import client.inventory.Item;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.capabilities.dialogue.AgentDialogueCatalog;
import server.agents.capabilities.inventory.AgentInventoryRuntime;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class AgentTradeSequenceServiceTest {
    @Test
    void missingRecipientRepliesWithoutOpeningBatch() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        AtomicInteger opened = new AtomicInteger();

        try (MockedStatic<AgentInventoryRuntime> replies = mockStatic(AgentInventoryRuntime.class)) {
            AgentTradeSequenceService.startSequence(
                    "scrolls",
                    null,
                    List.of(item(2040000)),
                    0,
                    true,
                    entry,
                    (items, mesos) -> opened.incrementAndGet());

            replies.verify(() -> AgentInventoryRuntime.replyNow(
                    entry,
                    AgentDialogueCatalog.tradeRecipientNotFoundReply()));
            assertEquals(0, opened.get());
            assertFalse(AgentPendingTradeStateRuntime.hasActiveSequence(entry));
        }
    }

    @Test
    void recipientInitializesSequenceAndOpensBatch() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        Character recipient = mock(Character.class);
        Item item = item(2040000);
        AtomicReference<List<Item>> openedItems = new AtomicReference<>();
        AtomicInteger openedMesos = new AtomicInteger();
        when(recipient.getId()).thenReturn(123);

        AgentTradeSequenceService.startSequence(
                "scrolls",
                recipient,
                List.of(item),
                456,
                false,
                entry,
                (items, mesos) -> {
                    openedItems.set(items);
                    openedMesos.set(mesos);
                });

        assertEquals("scrolls", AgentPendingTradeStateRuntime.category(entry));
        assertEquals(123, AgentPendingTradeStateRuntime.recipientId(entry));
        assertEquals(false, AgentPendingTradeStateRuntime.singleBatch(entry));
        assertSame(item, openedItems.get().get(0));
        assertEquals(456, openedMesos.get());
    }

    private static Item item(int itemId) {
        return new Item(itemId, (short) 1, (short) 1);
    }
}
