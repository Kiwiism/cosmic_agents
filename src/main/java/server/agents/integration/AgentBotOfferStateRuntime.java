package server.agents.integration;

import client.Character;
import client.inventory.Item;
import server.agents.runtime.AgentRuntimeEntry;

/**
 * Agent-owned offer state adapter.
 */
public final class AgentBotOfferStateRuntime {
    private AgentBotOfferStateRuntime() {
    }

    public static boolean hasPendingGearPromptAfter(AgentRuntimeEntry entry, long nowMs) {
        return entry.upgradeOfferState().hasPendingGearPromptAfter(nowMs);
    }

    public static long pendingGearPromptAt(AgentRuntimeEntry entry) {
        return entry.upgradeOfferState().pendingGearPromptAt();
    }

    public static void reserveGearPrompt(AgentRuntimeEntry entry, long scheduledAt) {
        entry.upgradeOfferState().reserveGearPrompt(scheduledAt);
    }

    public static boolean isReservedGearPrompt(AgentRuntimeEntry entry, long scheduledAt) {
        return entry.upgradeOfferState().isReservedGearPrompt(scheduledAt);
    }

    public static void clearGearPrompt(AgentRuntimeEntry entry) {
        entry.upgradeOfferState().clearGearPrompt();
    }

    public static Item pendingLootOfferItem(AgentRuntimeEntry entry) {
        return entry.pendingLootOfferState().item();
    }

    public static int pendingLootOfferRecipientId(AgentRuntimeEntry entry) {
        return entry.pendingLootOfferState().recipientId();
    }

    public static long pendingLootOfferExpiresAt(AgentRuntimeEntry entry) {
        return entry.pendingLootOfferState().expiresAt();
    }

    public static boolean pendingLootOfferBotRequesting(AgentRuntimeEntry entry) {
        return entry.pendingLootOfferState().botRequesting();
    }

    public static boolean hasOfferReservation(AgentRuntimeEntry entry) {
        return pendingLootOfferItem(entry) != null
                && pendingLootOfferRecipientId(entry) > 0;
    }

    public static boolean hasPendingOffer(AgentRuntimeEntry entry) {
        return hasOfferReservation(entry) && pendingLootOfferExpiresAt(entry) > 0L;
    }

    public static boolean pendingOfferRecipientIs(AgentRuntimeEntry entry, Character recipient) {
        return recipient != null && pendingLootOfferRecipientId(entry) == recipient.getId();
    }

    public static boolean pendingOfferMatches(AgentRuntimeEntry entry, Item item, int recipientId) {
        return pendingLootOfferItem(entry) == item && pendingLootOfferRecipientId(entry) == recipientId;
    }

    public static boolean pendingOfferExpired(AgentRuntimeEntry entry, long nowMs) {
        return hasPendingOffer(entry) && nowMs >= pendingLootOfferExpiresAt(entry);
    }

    public static void setPendingLootOffer(AgentRuntimeEntry entry,
                                           Item item,
                                           int recipientId,
                                           long expiresAt,
                                           boolean botRequesting) {
        entry.pendingLootOfferState().set(item, recipientId, expiresAt, botRequesting);
    }

    public static void clearPendingOfferForAcceptedTransfer(AgentRuntimeEntry entry) {
        entry.pendingLootOfferState().clearAcceptedTransfer();
    }

    public static void clearPendingOfferItem(AgentRuntimeEntry entry) {
        entry.pendingLootOfferState().clearItem();
    }

    public static void clearPendingOffer(AgentRuntimeEntry entry) {
        entry.pendingLootOfferState().clear();
    }

    public static boolean hasRequestedUpgradeItem(AgentRuntimeEntry entry, int itemId) {
        return entry.upgradeOfferState().hasRequestedUpgradeItem(itemId);
    }

    public static void rememberRequestedUpgradeItem(AgentRuntimeEntry entry, int itemId) {
        entry.upgradeOfferState().rememberRequestedUpgradeItem(itemId);
    }

    public static boolean proactiveUpgradeOffers(AgentRuntimeEntry entry) {
        return entry != null && entry.upgradeOfferState().proactiveUpgradeOffers();
    }

    public static void setProactiveUpgradeOffers(AgentRuntimeEntry entry, boolean proactive) {
        entry.upgradeOfferState().setProactiveUpgradeOffers(proactive);
    }

    public static long nextGearSuggestionAt(AgentRuntimeEntry entry) {
        return entry.upgradeOfferState().nextGearSuggestionAt();
    }

    public static void setNextGearSuggestionAt(AgentRuntimeEntry entry, long nextGearSuggestionAt) {
        entry.upgradeOfferState().setNextGearSuggestionAt(nextGearSuggestionAt);
    }

    public static boolean spawnUpgradeCheckDone(AgentRuntimeEntry entry) {
        return entry.upgradeOfferState().spawnUpgradeCheckDone();
    }

    public static void setSpawnUpgradeCheckDone(AgentRuntimeEntry entry, boolean done) {
        entry.upgradeOfferState().setSpawnUpgradeCheckDone(done);
    }
}
