package server.agents.capabilities.trade;

import client.inventory.Item;
import org.junit.jupiter.api.Test;
import server.agents.capabilities.trade.AgentTradeBetweenBatchService.BetweenBatchCallbacks;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class AgentTradeBetweenBatchCallbackServiceTest {
    @Test
    void buildsBetweenBatchCallbacksFromLegacyOperations() {
        Item item = mock(Item.class);
        List<Item> items = List.of(item);
        AtomicReference<String> collectedCategory = new AtomicReference<>();
        AtomicReference<String> nextEquipsCategory = new AtomicReference<>();
        AtomicReference<String> nextAmmoCategory = new AtomicReference<>();
        AtomicReference<String> messageCategory = new AtomicReference<>();
        AtomicReference<List<Item>> openedItems = new AtomicReference<>();
        AtomicBoolean reset = new AtomicBoolean();

        BetweenBatchCallbacks callbacks = AgentTradeBetweenBatchCallbackService.betweenBatchCallbacks(
                value -> value - 1,
                category -> {
                    collectedCategory.set(category);
                    return items;
                },
                category -> {
                    nextEquipsCategory.set(category);
                    return "reserved";
                },
                category -> {
                    nextAmmoCategory.set(category);
                    return "stars";
                },
                category -> {
                    messageCategory.set(category);
                    return "reserved for someone";
                },
                openedItems::set,
                () -> reset.set(true));

        assertEquals(4, callbacks.tickDown().applyAsInt(5));
        assertSame(items, callbacks.collectItems("equips"));
        assertEquals("reserved", callbacks.nextEquipsGroup("equips"));
        assertEquals("stars", callbacks.nextAmmoGroup("ammo"));
        assertEquals("reserved for someone", callbacks.equipsGroupMessage("reserved"));
        callbacks.openTradeBatch(items);
        callbacks.resetTradeState();

        assertEquals("equips", collectedCategory.get());
        assertEquals("equips", nextEquipsCategory.get());
        assertEquals("ammo", nextAmmoCategory.get());
        assertEquals("reserved", messageCategory.get());
        assertSame(items, openedItems.get());
        assertTrue(reset.get());
    }
}
