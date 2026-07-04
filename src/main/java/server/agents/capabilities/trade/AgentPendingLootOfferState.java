package server.agents.capabilities.trade;

import client.inventory.Item;

/**
 * Mutable pending loot-offer state for a live Agent.
 */
public final class AgentPendingLootOfferState {
    private Item item = null;
    private int recipientId = 0;
    private long expiresAt = 0L;
    private boolean botRequesting = false;

    public Item item() {
        return item;
    }

    public int recipientId() {
        return recipientId;
    }

    public long expiresAt() {
        return expiresAt;
    }

    public boolean botRequesting() {
        return botRequesting;
    }

    public void set(Item item, int recipientId, long expiresAt, boolean botRequesting) {
        this.item = item;
        this.recipientId = recipientId;
        this.expiresAt = expiresAt;
        this.botRequesting = botRequesting;
    }

    public void clear() {
        set(null, 0, 0L, false);
    }

    public void clearAcceptedTransfer() {
        recipientId = 0;
        expiresAt = 0L;
        botRequesting = false;
    }

    public void clearItem() {
        item = null;
    }
}
