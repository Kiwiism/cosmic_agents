package server.agents.capabilities.looting;

import client.Character;
import client.inventory.Item;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

public final class AgentPassiveLootCallbackService {
    private AgentPassiveLootCallbackService() {
    }

    public static AgentPassiveLootService.PassiveLootCallbacks passiveLootCallbacks(
            BooleanSupplier hasLootInhibit,
            Runnable tickLootInhibit,
            BooleanSupplier hasActiveTradeSequence,
            Runnable tickInventoryFullWarnCooldown,
            LongSupplier nowMs,
            IntSupplier lootRadius,
            BooleanSupplier canWarnInventoryFull,
            AgentPassiveLootService.ReplySink replyNow,
            IntSupplier delayInventoryFullWarnCooldown,
            Consumer<Integer> setInventoryFullWarnCooldownMs,
            Supplier<Character> owner,
            Supplier<Item> pendingLootOfferItem,
            AgentPassiveLootService.ItemPresence hasItem,
            AgentPassiveLootService.AutoEquipSink autoEquip,
            AgentPassiveLootService.LootOfferPromptSink scheduleLootOfferPrompt,
            AgentPassiveLootService.CleanupSink cleanupGhostDrop,
            AgentPassiveLootService.PickupSink pickup) {
        return AgentPassiveLootService.PassiveLootCallbacks.of(
                hasLootInhibit,
                tickLootInhibit,
                hasActiveTradeSequence,
                tickInventoryFullWarnCooldown,
                nowMs,
                lootRadius,
                canWarnInventoryFull,
                replyNow,
                delayInventoryFullWarnCooldown,
                setInventoryFullWarnCooldownMs,
                owner,
                pendingLootOfferItem,
                hasItem,
                autoEquip,
                scheduleLootOfferPrompt,
                cleanupGhostDrop,
                pickup);
    }
}
