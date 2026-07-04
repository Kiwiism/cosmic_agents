package server.agents.capabilities.trade;

import client.inventory.Item;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class AgentPendingLootOfferStateTest {
    @Test
    void storesAndClearsPendingLootOffer() {
        AgentPendingLootOfferState state = new AgentPendingLootOfferState();
        Item item = mock(Item.class);

        state.set(item, 123, 10_000L, true);

        assertSame(item, state.item());
        assertEquals(123, state.recipientId());
        assertEquals(10_000L, state.expiresAt());
        assertTrue(state.botRequesting());

        state.clear();

        assertNull(state.item());
        assertEquals(0, state.recipientId());
        assertEquals(0L, state.expiresAt());
        assertFalse(state.botRequesting());
    }

    @Test
    void acceptedTransferClearsReservationButPreservesItem() {
        AgentPendingLootOfferState state = new AgentPendingLootOfferState();
        Item item = mock(Item.class);

        state.set(item, 123, 10_000L, true);
        state.clearAcceptedTransfer();

        assertSame(item, state.item());
        assertEquals(0, state.recipientId());
        assertEquals(0L, state.expiresAt());
        assertFalse(state.botRequesting());
    }

    @Test
    void clearItemOnlyClearsItem() {
        AgentPendingLootOfferState state = new AgentPendingLootOfferState();

        state.set(mock(Item.class), 123, 10_000L, true);
        state.clearItem();

        assertNull(state.item());
        assertEquals(123, state.recipientId());
        assertEquals(10_000L, state.expiresAt());
        assertTrue(state.botRequesting());
    }
}
