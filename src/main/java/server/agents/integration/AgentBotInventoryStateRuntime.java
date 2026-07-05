package server.agents.integration;

import server.agents.runtime.AgentRuntimeEntry;

import java.util.function.IntUnaryOperator;

/**
 * Agent-owned adapter for temporary BotEntry-backed inventory and loot cooldown state.
 */
public final class AgentBotInventoryStateRuntime {
    private AgentBotInventoryStateRuntime() {
    }

    public static int lootInhibitMs(AgentRuntimeEntry entry) {
        return entry.inventoryCooldownState().lootInhibitMs();
    }

    public static boolean hasLootInhibit(AgentRuntimeEntry entry) {
        return lootInhibitMs(entry) > 0;
    }

    public static void tickLootInhibit(AgentRuntimeEntry entry, IntUnaryOperator tickDown) {
        entry.inventoryCooldownState().setLootInhibitMs(
                tickDown.applyAsInt(entry.inventoryCooldownState().lootInhibitMs()));
    }

    public static void setLootInhibitMs(AgentRuntimeEntry entry, int delayMs) {
        entry.inventoryCooldownState().setLootInhibitMs(delayMs);
    }

    public static int inventoryFullWarnCooldownMs(AgentRuntimeEntry entry) {
        return entry.inventoryCooldownState().inventoryFullWarnCooldownMs();
    }

    public static boolean canWarnInventoryFull(AgentRuntimeEntry entry) {
        return inventoryFullWarnCooldownMs(entry) <= 0;
    }

    public static void tickInventoryFullWarnCooldown(AgentRuntimeEntry entry, IntUnaryOperator tickDown) {
        entry.inventoryCooldownState().setInventoryFullWarnCooldownMs(
                tickDown.applyAsInt(entry.inventoryCooldownState().inventoryFullWarnCooldownMs()));
    }

    public static void setInventoryFullWarnCooldownMs(AgentRuntimeEntry entry, int delayMs) {
        entry.inventoryCooldownState().setInventoryFullWarnCooldownMs(delayMs);
    }
}
