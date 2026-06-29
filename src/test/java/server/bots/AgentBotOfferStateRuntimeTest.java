package server.bots;

import client.Character;
import client.inventory.Item;
import org.junit.jupiter.api.Test;
import server.agents.integration.AgentBotOfferStateRuntime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentBotOfferStateRuntimeTest {
    @Test
    void storesPendingLootOfferTuple() {
        BotEntry entry = new BotEntry(null, null, null);
        Item item = new Item(1002000, (short) 1, (short) 1);

        AgentBotOfferStateRuntime.setPendingLootOffer(entry, item, 123, 10_000L, true);

        assertSame(item, AgentBotOfferStateRuntime.pendingLootOfferItem(entry));
        assertEquals(123, AgentBotOfferStateRuntime.pendingLootOfferRecipientId(entry));
        assertEquals(10_000L, AgentBotOfferStateRuntime.pendingLootOfferExpiresAt(entry));
        assertTrue(AgentBotOfferStateRuntime.pendingLootOfferBotRequesting(entry));
        assertTrue(AgentBotOfferStateRuntime.hasOfferReservation(entry));
        assertTrue(AgentBotOfferStateRuntime.hasPendingOffer(entry));
        assertTrue(AgentBotOfferStateRuntime.pendingOfferMatches(entry, item, 123));
    }

    @Test
    void detectsRecipientAndExpiry() {
        BotEntry entry = new BotEntry(null, null, null);
        Character recipient = mock(Character.class);
        when(recipient.getId()).thenReturn(123);
        AgentBotOfferStateRuntime.setPendingLootOffer(
                entry, new Item(1002000, (short) 1, (short) 1), 123, 10_000L, false);

        assertTrue(AgentBotOfferStateRuntime.pendingOfferRecipientIs(entry, recipient));
        assertFalse(AgentBotOfferStateRuntime.pendingOfferExpired(entry, 9_999L));
        assertTrue(AgentBotOfferStateRuntime.pendingOfferExpired(entry, 10_000L));
    }

    @Test
    void acceptedTransferClearsRecipientButKeepsItemUntilDelayedTradeStarts() {
        BotEntry entry = new BotEntry(null, null, null);
        Item item = new Item(1002000, (short) 1, (short) 1);
        AgentBotOfferStateRuntime.setPendingLootOffer(entry, item, 123, 10_000L, false);

        AgentBotOfferStateRuntime.clearPendingOfferForAcceptedTransfer(entry);

        assertSame(item, AgentBotOfferStateRuntime.pendingLootOfferItem(entry));
        assertEquals(0, AgentBotOfferStateRuntime.pendingLootOfferRecipientId(entry));
        assertEquals(0L, AgentBotOfferStateRuntime.pendingLootOfferExpiresAt(entry));
        assertFalse(AgentBotOfferStateRuntime.pendingLootOfferBotRequesting(entry));

        AgentBotOfferStateRuntime.clearPendingOfferItem(entry);
        assertNull(AgentBotOfferStateRuntime.pendingLootOfferItem(entry));
    }

    @Test
    void clearPendingOfferClearsWholeTuple() {
        BotEntry entry = new BotEntry(null, null, null);
        AgentBotOfferStateRuntime.setPendingLootOffer(
                entry, new Item(1002000, (short) 1, (short) 1), 123, 10_000L, true);

        AgentBotOfferStateRuntime.clearPendingOffer(entry);

        assertNull(AgentBotOfferStateRuntime.pendingLootOfferItem(entry));
        assertEquals(0, AgentBotOfferStateRuntime.pendingLootOfferRecipientId(entry));
        assertEquals(0L, AgentBotOfferStateRuntime.pendingLootOfferExpiresAt(entry));
        assertFalse(AgentBotOfferStateRuntime.pendingLootOfferBotRequesting(entry));
    }

    @Test
    void adaptsRequestedUpgradeItemsAndProactiveOfferToggle() {
        BotEntry entry = new BotEntry(null, null, null);

        assertFalse(AgentBotOfferStateRuntime.hasRequestedUpgradeItem(entry, 1002000));
        assertTrue(AgentBotOfferStateRuntime.proactiveUpgradeOffers(entry));

        AgentBotOfferStateRuntime.rememberRequestedUpgradeItem(entry, 1002000);
        entry.setProactiveUpgradeOffers(false);

        assertTrue(AgentBotOfferStateRuntime.hasRequestedUpgradeItem(entry, 1002000));
        assertFalse(AgentBotOfferStateRuntime.proactiveUpgradeOffers(entry));
        assertFalse(AgentBotOfferStateRuntime.proactiveUpgradeOffers(null));
    }
}
