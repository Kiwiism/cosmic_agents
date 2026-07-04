package server.agents.capabilities.trade;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mutable Agent state for proactive gear upgrade suggestions and requested
 * upgrade items.
 */
public final class AgentUpgradeOfferState {
    private boolean proactiveUpgradeOffers = true;
    private long nextGearSuggestionAt = 0L;
    private long pendingGearPromptAt = 0L;
    private boolean spawnUpgradeCheckDone = false;
    private final Set<Integer> requestedUpgradeItemIds = ConcurrentHashMap.newKeySet();

    public boolean proactiveUpgradeOffers() {
        return proactiveUpgradeOffers;
    }

    public void setProactiveUpgradeOffers(boolean proactiveUpgradeOffers) {
        this.proactiveUpgradeOffers = proactiveUpgradeOffers;
    }

    public long nextGearSuggestionAt() {
        return nextGearSuggestionAt;
    }

    public void setNextGearSuggestionAt(long nextGearSuggestionAt) {
        this.nextGearSuggestionAt = nextGearSuggestionAt;
    }

    public long pendingGearPromptAt() {
        return pendingGearPromptAt;
    }

    public void reserveGearPrompt(long scheduledAt) {
        this.pendingGearPromptAt = scheduledAt;
    }

    public void clearGearPrompt() {
        this.pendingGearPromptAt = 0L;
    }

    public boolean hasPendingGearPromptAfter(long nowMs) {
        return pendingGearPromptAt > nowMs;
    }

    public boolean isReservedGearPrompt(long scheduledAt) {
        return pendingGearPromptAt == scheduledAt;
    }

    public boolean spawnUpgradeCheckDone() {
        return spawnUpgradeCheckDone;
    }

    public void setSpawnUpgradeCheckDone(boolean spawnUpgradeCheckDone) {
        this.spawnUpgradeCheckDone = spawnUpgradeCheckDone;
    }

    public boolean hasRequestedUpgradeItem(int itemId) {
        return requestedUpgradeItemIds.contains(itemId);
    }

    public void rememberRequestedUpgradeItem(int itemId) {
        requestedUpgradeItemIds.add(itemId);
    }

    public void clearRequestedUpgradeItems() {
        requestedUpgradeItemIds.clear();
    }
}
