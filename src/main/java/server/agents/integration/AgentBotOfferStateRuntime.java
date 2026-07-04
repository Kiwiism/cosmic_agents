package server.agents.integration;

import client.Character;
import client.inventory.Item;
import server.bots.BotEntry;

/**
 * Agent-owned offer state adapter.
 */
public final class AgentBotOfferStateRuntime {
    private AgentBotOfferStateRuntime() {
    }

    public static boolean hasPendingGearPromptAfter(BotEntry entry, long nowMs) {
        return entry.pendingGearPromptAt() > nowMs;
    }

    public static long pendingGearPromptAt(BotEntry entry) {
        return entry.pendingGearPromptAt();
    }

    public static void reserveGearPrompt(BotEntry entry, long scheduledAt) {
        entry.setPendingGearPromptAt(scheduledAt);
    }

    public static boolean isReservedGearPrompt(BotEntry entry, long scheduledAt) {
        return entry.pendingGearPromptAt() == scheduledAt;
    }

    public static void clearGearPrompt(BotEntry entry) {
        entry.setPendingGearPromptAt(0L);
    }

    public static Item pendingLootOfferItem(BotEntry entry) {
        return entry.pendingLootOfferState().item();
    }

    public static int pendingLootOfferRecipientId(BotEntry entry) {
        return entry.pendingLootOfferState().recipientId();
    }

    public static long pendingLootOfferExpiresAt(BotEntry entry) {
        return entry.pendingLootOfferState().expiresAt();
    }

    public static boolean pendingLootOfferBotRequesting(BotEntry entry) {
        return entry.pendingLootOfferState().botRequesting();
    }

    public static boolean hasOfferReservation(BotEntry entry) {
        return pendingLootOfferItem(entry) != null
                && pendingLootOfferRecipientId(entry) > 0;
    }

    public static boolean hasPendingOffer(BotEntry entry) {
        return hasOfferReservation(entry) && pendingLootOfferExpiresAt(entry) > 0L;
    }

    public static boolean pendingOfferRecipientIs(BotEntry entry, Character recipient) {
        return recipient != null && pendingLootOfferRecipientId(entry) == recipient.getId();
    }

    public static boolean pendingOfferMatches(BotEntry entry, Item item, int recipientId) {
        return pendingLootOfferItem(entry) == item && pendingLootOfferRecipientId(entry) == recipientId;
    }

    public static boolean pendingOfferExpired(BotEntry entry, long nowMs) {
        return hasPendingOffer(entry) && nowMs >= pendingLootOfferExpiresAt(entry);
    }

    public static void setPendingLootOffer(BotEntry entry,
                                           Item item,
                                           int recipientId,
                                           long expiresAt,
                                           boolean botRequesting) {
        entry.pendingLootOfferState().set(item, recipientId, expiresAt, botRequesting);
    }

    public static void clearPendingOfferForAcceptedTransfer(BotEntry entry) {
        entry.pendingLootOfferState().clearAcceptedTransfer();
    }

    public static void clearPendingOfferItem(BotEntry entry) {
        entry.pendingLootOfferState().clearItem();
    }

    public static void clearPendingOffer(BotEntry entry) {
        entry.pendingLootOfferState().clear();
    }

    public static boolean hasRequestedUpgradeItem(BotEntry entry, int itemId) {
        return entry.hasRequestedUpgradeItem(itemId);
    }

    public static void rememberRequestedUpgradeItem(BotEntry entry, int itemId) {
        entry.rememberRequestedUpgradeItem(itemId);
    }

    public static boolean proactiveUpgradeOffers(BotEntry entry) {
        return entry != null && entry.proactiveUpgradeOffers();
    }
}
