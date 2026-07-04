package server.agents.capabilities.inventory;

/**
 * Mutable inventory and looting cooldown state for a live Agent.
 */
public final class AgentInventoryCooldownState {
    private int lootInhibitMs = 0;
    private int inventoryFullWarnCooldownMs = 0;

    public int lootInhibitMs() {
        return lootInhibitMs;
    }

    public void setLootInhibitMs(int lootInhibitMs) {
        this.lootInhibitMs = lootInhibitMs;
    }

    public int inventoryFullWarnCooldownMs() {
        return inventoryFullWarnCooldownMs;
    }

    public void setInventoryFullWarnCooldownMs(int inventoryFullWarnCooldownMs) {
        this.inventoryFullWarnCooldownMs = inventoryFullWarnCooldownMs;
    }
}
