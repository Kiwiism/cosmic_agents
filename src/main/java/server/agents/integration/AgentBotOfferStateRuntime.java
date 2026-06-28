package server.agents.integration;

import client.Character;
import client.inventory.Item;
import server.bots.BotEntry;

/**
 * Agent-owned offer state adapter. Gear-prompt reservation state is still
 * backed by BotEntry during reconstruction, but callers should depend on this
 * narrow state boundary.
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
        return entry.pendingLootOfferItem();
    }

    public static int pendingLootOfferRecipientId(BotEntry entry) {
        return entry.pendingLootOfferRecipientId();
    }

    public static long pendingLootOfferExpiresAt(BotEntry entry) {
        return entry.pendingLootOfferExpiresAt();
    }

    public static boolean pendingLootOfferBotRequesting(BotEntry entry) {
        return entry.pendingLootOfferBotRequesting();
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
        entry.setPendingLootOffer(item, recipientId, expiresAt, botRequesting);
    }

    public static void clearPendingOfferForAcceptedTransfer(BotEntry entry) {
        entry.clearPendingLootOfferForAcceptedTransfer();
    }

    public static void clearPendingOfferItem(BotEntry entry) {
        entry.clearPendingLootOfferItem();
    }

    public static void clearPendingOffer(BotEntry entry) {
        entry.clearPendingLootOffer();
    }
}
