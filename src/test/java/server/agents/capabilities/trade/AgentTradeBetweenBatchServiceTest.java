package server.agents.capabilities.trade;

import client.Character;
import client.inventory.Item;
import org.junit.jupiter.api.Test;
import server.agents.integration.AgentPendingTradeStateRuntime;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class AgentTradeBetweenBatchServiceTest {
    @Test
    void ignoresNonBetweenBatchState() {
        AgentRuntimeEntry entry = entry();
        AgentPendingTradeStateRuntime.setCategory(entry, "scrolls");
        AgentPendingTradeStateRuntime.setItems(entry, List.of(mock(Item.class)));
        TraceCallbacks callbacks = new TraceCallbacks();

        assertEquals(false, AgentTradeBetweenBatchService.tickBetweenBatches(entry, callbacks));
        assertEquals(0, callbacks.resets.get());
        assertTrue(callbacks.opened.isEmpty());
    }

    @Test
    void resetsSingleBatchBetweenBatchState() {
        AgentRuntimeEntry entry = entry();
        AgentPendingTradeStateRuntime.setCategory(entry, "scrolls");
        AgentPendingTradeStateRuntime.setSingleBatch(entry, true);
        TraceCallbacks callbacks = new TraceCallbacks();

        assertEquals(true, AgentTradeBetweenBatchService.tickBetweenBatches(entry, callbacks));
        assertEquals(1, callbacks.resets.get());
    }

    @Test
    void ticksTimerBeforeCollectingNextBatch() {
        AgentRuntimeEntry entry = entry();
        AgentPendingTradeStateRuntime.setCategory(entry, "scrolls");
        AgentPendingTradeStateRuntime.setTimerMs(entry, 500);
        TraceCallbacks callbacks = new TraceCallbacks();

        assertEquals(true, AgentTradeBetweenBatchService.tickBetweenBatches(entry, callbacks));
        assertEquals(400, AgentPendingTradeStateRuntime.timerMs(entry));
        assertTrue(callbacks.collectedCategories.isEmpty());
    }

    @Test
    void opensNextBatchForSameCategoryWhenItemsRemain() {
        AgentRuntimeEntry entry = entry();
        AgentPendingTradeStateRuntime.setCategory(entry, "scrolls");
        Item item = mock(Item.class);
        TraceCallbacks callbacks = new TraceCallbacks();
        callbacks.nextItems = List.of(item);

        assertEquals(true, AgentTradeBetweenBatchService.tickBetweenBatches(entry, callbacks));

        assertEquals(List.of("scrolls"), callbacks.collectedCategories);
        assertEquals(1, callbacks.opened.size());
        assertSame(item, callbacks.opened.get(0).get(0));
    }

    @Test
    void advancesCategoryWhenCurrentCategoryIsEmpty() {
        AgentRuntimeEntry entry = entry();
        AgentPendingTradeStateRuntime.setCategory(entry, "equips");
        Item item = mock(Item.class);
        TraceCallbacks callbacks = new TraceCallbacks();
        callbacks.advancedEquip = "equips:reserved:1";
        callbacks.advancedItems = List.of(item);
        callbacks.message = "reserved stuff";

        assertEquals(true, AgentTradeBetweenBatchService.tickBetweenBatches(entry, callbacks));

        assertEquals("equips:reserved:1", AgentPendingTradeStateRuntime.category(entry));
        assertEquals("reserved stuff", AgentPendingTradeStateRuntime.categoryMessage(entry));
        assertEquals(List.of("equips", "equips:reserved:1"), callbacks.collectedCategories);
        assertSame(item, callbacks.opened.get(0).get(0));
    }

    @Test
    void resetsWhenNoItemsOrAdvancedCategoryRemain() {
        AgentRuntimeEntry entry = entry();
        AgentPendingTradeStateRuntime.setCategory(entry, "equips");
        TraceCallbacks callbacks = new TraceCallbacks();

        assertEquals(true, AgentTradeBetweenBatchService.tickBetweenBatches(entry, callbacks));

        assertEquals(1, callbacks.resets.get());
        assertTrue(callbacks.opened.isEmpty());
    }

    private static AgentRuntimeEntry entry() {
        return new AgentRuntimeEntry(mock(Character.class), null, null);
    }

    private static final class TraceCallbacks implements AgentTradeBetweenBatchService.BetweenBatchCallbacks {
        final AtomicInteger resets = new AtomicInteger();
        final List<String> collectedCategories = new ArrayList<>();
        final List<List<Item>> opened = new ArrayList<>();
        List<Item> nextItems = List.of();
        List<Item> advancedItems = List.of();
        String advancedEquip;
        String advancedAmmo;
        String message;

        @Override
        public java.util.function.IntUnaryOperator tickDown() {
            return remaining -> remaining - 100;
        }

        @Override
        public List<Item> collectItems(String category) {
            collectedCategories.add(category);
            if (category.equals(advancedEquip) || category.equals(advancedAmmo)) {
                return advancedItems;
            }
            return nextItems;
        }

        @Override
        public String nextEquipsGroup(String category) {
            return advancedEquip;
        }

        @Override
        public String nextAmmoGroup(String category) {
            return advancedAmmo;
        }

        @Override
        public String equipsGroupMessage(String category) {
            return message;
        }

        @Override
        public void openTradeBatch(List<Item> items) {
            opened.add(items);
        }

        @Override
        public void resetTradeState() {
            resets.incrementAndGet();
        }
    }
}
