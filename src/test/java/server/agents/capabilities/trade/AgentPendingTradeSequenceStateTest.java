package server.agents.capabilities.trade;

import client.inventory.Item;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class AgentPendingTradeSequenceStateTest {
    @Test
    void defaultsPreserveLegacyBotEntryValues() {
        AgentPendingTradeSequenceState state = new AgentPendingTradeSequenceState();

        assertNull(state.category());
        assertNull(state.items());
        assertEquals(0, state.recipientId());
        assertEquals(0, state.meso());
        assertEquals(0, state.itemIndex());
        assertEquals(0, state.timerMs());
        assertFalse(state.mesoAdded());
        assertFalse(state.allItemsAdded());
        assertFalse(state.agentDone());
        assertFalse(state.singleBatch());
        assertFalse(state.inviteAnnounced());
        assertNull(state.categoryMessage());
        assertEquals(0, state.shareBudget());
        assertEquals(0, state.restoreSlots().size());
    }

    @Test
    void storesPendingTradeSequenceValues() {
        AgentPendingTradeSequenceState state = new AgentPendingTradeSequenceState();
        Item item = new Item(2000000, (short) 1, (short) 1);
        List<Item> items = List.of(item);

        state.setCategory("pot_share");
        state.setItems(items);
        state.setRecipientId(123);
        state.setMeso(456);
        state.incrementItemIndex();
        state.setTimerMs(789);
        state.setMesoAdded(true);
        state.setAllItemsAdded(true);
        state.setAgentDone(true);
        state.setSingleBatch(true);
        state.setInviteAnnounced(true);
        state.setCategoryMessage("trading pots");
        state.setShareBudget(10);
        state.restoreSlots().put(item, (short) -1);

        assertEquals("pot_share", state.category());
        assertSame(items, state.items());
        assertEquals(123, state.recipientId());
        assertEquals(456, state.meso());
        assertEquals(1, state.itemIndex());
        assertEquals(789, state.timerMs());
        assertEquals("trading pots", state.categoryMessage());
        assertEquals(10, state.shareBudget());
        assertEquals((short) -1, state.restoreSlots().get(item));
    }
}
