package server.agents.integration;

import server.agents.runtime.AgentRuntimeEntry;

import client.Character;
import client.inventory.Item;
import org.junit.jupiter.api.Test;
import server.agents.integration.AgentOfferStateRuntime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentOfferStateRuntimeTest {
    @Test
    void storesPendingLootOfferTuple() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        Item item = new Item(1002000, (short) 1, (short) 1);

        AgentOfferStateRuntime.setPendingLootOffer(entry, item, 123, 10_000L, true);

        assertSame(item, AgentOfferStateRuntime.pendingLootOfferItem(entry));
        assertEquals(123, AgentOfferStateRuntime.pendingLootOfferRecipientId(entry));
        assertEquals(10_000L, AgentOfferStateRuntime.pendingLootOfferExpiresAt(entry));
        assertTrue(AgentOfferStateRuntime.pendingLootOfferBotRequesting(entry));
        assertTrue(AgentOfferStateRuntime.hasOfferReservation(entry));
        assertTrue(AgentOfferStateRuntime.hasPendingOffer(entry));
        assertTrue(AgentOfferStateRuntime.pendingOfferMatches(entry, item, 123));
    }

    @Test
    void detectsRecipientAndExpiry() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        Character recipient = mock(Character.class);
        when(recipient.getId()).thenReturn(123);
        AgentOfferStateRuntime.setPendingLootOffer(
                entry, new Item(1002000, (short) 1, (short) 1), 123, 10_000L, false);

        assertTrue(AgentOfferStateRuntime.pendingOfferRecipientIs(entry, recipient));
        assertFalse(AgentOfferStateRuntime.pendingOfferExpired(entry, 9_999L));
        assertTrue(AgentOfferStateRuntime.pendingOfferExpired(entry, 10_000L));
    }

    @Test
    void acceptedTransferClearsRecipientButKeepsItemUntilDelayedTradeStarts() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        Item item = new Item(1002000, (short) 1, (short) 1);
        AgentOfferStateRuntime.setPendingLootOffer(entry, item, 123, 10_000L, false);

        AgentOfferStateRuntime.clearPendingOfferForAcceptedTransfer(entry);

        assertSame(item, AgentOfferStateRuntime.pendingLootOfferItem(entry));
        assertEquals(0, AgentOfferStateRuntime.pendingLootOfferRecipientId(entry));
        assertEquals(0L, AgentOfferStateRuntime.pendingLootOfferExpiresAt(entry));
        assertFalse(AgentOfferStateRuntime.pendingLootOfferBotRequesting(entry));

        AgentOfferStateRuntime.clearPendingOfferItem(entry);
        assertNull(AgentOfferStateRuntime.pendingLootOfferItem(entry));
    }

    @Test
    void clearPendingOfferClearsWholeTuple() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        AgentOfferStateRuntime.setPendingLootOffer(
                entry, new Item(1002000, (short) 1, (short) 1), 123, 10_000L, true);

        AgentOfferStateRuntime.clearPendingOffer(entry);

        assertNull(AgentOfferStateRuntime.pendingLootOfferItem(entry));
        assertEquals(0, AgentOfferStateRuntime.pendingLootOfferRecipientId(entry));
        assertEquals(0L, AgentOfferStateRuntime.pendingLootOfferExpiresAt(entry));
        assertFalse(AgentOfferStateRuntime.pendingLootOfferBotRequesting(entry));
    }

    @Test
    void adaptsRequestedUpgradeItemsAndProactiveOfferToggle() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        assertFalse(AgentOfferStateRuntime.hasRequestedUpgradeItem(entry, 1002000));
        assertTrue(AgentOfferStateRuntime.proactiveUpgradeOffers(entry));

        AgentOfferStateRuntime.rememberRequestedUpgradeItem(entry, 1002000);
        AgentOfferStateRuntime.setProactiveUpgradeOffers(entry, false);

        assertTrue(AgentOfferStateRuntime.hasRequestedUpgradeItem(entry, 1002000));
        assertFalse(AgentOfferStateRuntime.proactiveUpgradeOffers(entry));
        assertFalse(AgentOfferStateRuntime.proactiveUpgradeOffers(null));
    }
}
