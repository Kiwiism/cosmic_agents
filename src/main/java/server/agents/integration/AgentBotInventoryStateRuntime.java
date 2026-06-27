package server.agents.integration;

import server.bots.BotEntry;

import java.util.function.IntUnaryOperator;

/**
 * Agent-owned adapter for temporary BotEntry-backed inventory and loot cooldown state.
 */
public final class AgentBotInventoryStateRuntime {
    private AgentBotInventoryStateRuntime() {
    }

    public static int lootInhibitMs(BotEntry entry) {
        return entry.lootInhibitMs();
    }

    public static boolean hasLootInhibit(BotEntry entry) {
        return lootInhibitMs(entry) > 0;
    }

    public static void tickLootInhibit(BotEntry entry, IntUnaryOperator tickDown) {
        entry.setLootInhibitMs(tickDown.applyAsInt(entry.lootInhibitMs()));
    }

    public static void setLootInhibitMs(BotEntry entry, int delayMs) {
        entry.setLootInhibitMs(delayMs);
    }

    public static int inventoryFullWarnCooldownMs(BotEntry entry) {
        return entry.invFullWarnCooldownMs();
    }

    public static boolean canWarnInventoryFull(BotEntry entry) {
        return inventoryFullWarnCooldownMs(entry) <= 0;
    }

    public static void tickInventoryFullWarnCooldown(BotEntry entry, IntUnaryOperator tickDown) {
        entry.setInvFullWarnCooldownMs(tickDown.applyAsInt(entry.invFullWarnCooldownMs()));
    }

    public static void setInventoryFullWarnCooldownMs(BotEntry entry, int delayMs) {
        entry.setInvFullWarnCooldownMs(delayMs);
    }
}
