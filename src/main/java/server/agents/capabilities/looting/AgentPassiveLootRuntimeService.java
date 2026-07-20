package server.agents.capabilities.looting;

import client.Character;
import client.inventory.Item;
import server.agents.runtime.AgentRuntimeEntry;
import server.maps.MapItem;

import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.function.IntUnaryOperator;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

public final class AgentPassiveLootRuntimeService {
    private AgentPassiveLootRuntimeService() {
    }

    public static void tickPassiveLoot(AgentRuntimeEntry entry,
                                       Character agent,
                                       RuntimeCallbacks callbacks) {
        AgentPassiveLootService.tickPassiveLoot(
                entry,
                agent,
                AgentPassiveLootCallbackService.passiveLootCallbacks(
                        () -> callbacks.hasLootInhibit(entry),
                        () -> callbacks.tickLootInhibit(entry),
                        () -> callbacks.hasActiveTradeSequence(entry),
                        () -> callbacks.tickInventoryFullWarnCooldown(entry),
                        callbacks::nowMs,
                        callbacks::lootRadius,
                        () -> callbacks.canWarnInventoryFull(entry),
                        callbacks::delayInventoryFullWarnCooldown,
                        cooldown -> callbacks.setInventoryFullWarnCooldownMs(entry, cooldown),
                        () -> callbacks.owner(entry),
                        () -> callbacks.pendingLootOfferItem(entry),
                        callbacks::hasItem,
                        callbacks::autoEquip,
                        callbacks::scheduleLootOfferPrompt,
                        callbacks::cleanupGhostDrop,
                        callbacks::pickup));
    }

    public interface RuntimeCallbacks {
        boolean hasLootInhibit(AgentRuntimeEntry entry);

        void tickLootInhibit(AgentRuntimeEntry entry);

        boolean hasActiveTradeSequence(AgentRuntimeEntry entry);

        void tickInventoryFullWarnCooldown(AgentRuntimeEntry entry);

        long nowMs();

        int lootRadius();

        boolean canWarnInventoryFull(AgentRuntimeEntry entry);

        int delayInventoryFullWarnCooldown();

        void setInventoryFullWarnCooldownMs(AgentRuntimeEntry entry, int cooldownMs);

        Character owner(AgentRuntimeEntry entry);

        Item pendingLootOfferItem(AgentRuntimeEntry entry);

        boolean hasItem(Character agent, Item item);

        void autoEquip(Character agent, Character owner, Item pendingLootOfferItem);

        void scheduleLootOfferPrompt(AgentRuntimeEntry entry, Character agent, Item item, long delayMs);

        void cleanupGhostDrop(Character agent, MapItem drop);

        void pickup(Character character, MapItem drop);

        static RuntimeCallbacks of(java.util.function.Predicate<AgentRuntimeEntry> hasLootInhibit,
                                   java.util.function.Consumer<AgentRuntimeEntry> tickLootInhibit,
                                   java.util.function.Predicate<AgentRuntimeEntry> hasActiveTradeSequence,
                                   java.util.function.Consumer<AgentRuntimeEntry> tickInventoryFullWarnCooldown,
                                   LongSupplier nowMs,
                                   IntSupplier lootRadius,
                                   java.util.function.Predicate<AgentRuntimeEntry> canWarnInventoryFull,
                                   IntSupplier delayInventoryFullWarnCooldown,
                                   SetInventoryCooldown setInventoryFullWarnCooldownMs,
                                   Function<AgentRuntimeEntry, Character> owner,
                                   Function<AgentRuntimeEntry, Item> pendingLootOfferItem,
                                   AgentPassiveLootService.ItemPresence hasItem,
                                   AgentPassiveLootService.AutoEquipSink autoEquip,
                                   AgentPassiveLootService.LootOfferPromptSink scheduleLootOfferPrompt,
                                   AgentPassiveLootService.CleanupSink cleanupGhostDrop,
                                   AgentPassiveLootService.PickupSink pickup) {
            return new RuntimeCallbacks() {
                @Override
                public boolean hasLootInhibit(AgentRuntimeEntry entry) {
                    return hasLootInhibit.test(entry);
                }

                @Override
                public void tickLootInhibit(AgentRuntimeEntry entry) {
                    tickLootInhibit.accept(entry);
                }

                @Override
                public boolean hasActiveTradeSequence(AgentRuntimeEntry entry) {
                    return hasActiveTradeSequence.test(entry);
                }

                @Override
                public void tickInventoryFullWarnCooldown(AgentRuntimeEntry entry) {
                    tickInventoryFullWarnCooldown.accept(entry);
                }

                @Override
                public long nowMs() {
                    return nowMs.getAsLong();
                }

                @Override
                public int lootRadius() {
                    return lootRadius.getAsInt();
                }

                @Override
                public boolean canWarnInventoryFull(AgentRuntimeEntry entry) {
                    return canWarnInventoryFull.test(entry);
                }

                @Override
                public int delayInventoryFullWarnCooldown() {
                    return delayInventoryFullWarnCooldown.getAsInt();
                }

                @Override
                public void setInventoryFullWarnCooldownMs(AgentRuntimeEntry entry, int cooldownMs) {
                    setInventoryFullWarnCooldownMs.set(entry, cooldownMs);
                }

                @Override
                public Character owner(AgentRuntimeEntry entry) {
                    return owner.apply(entry);
                }

                @Override
                public Item pendingLootOfferItem(AgentRuntimeEntry entry) {
                    return pendingLootOfferItem.apply(entry);
                }

                @Override
                public boolean hasItem(Character agent, Item item) {
                    return hasItem.hasItem(agent, item);
                }

                @Override
                public void autoEquip(Character agent, Character owner, Item pendingLootOfferItem) {
                    autoEquip.autoEquip(agent, owner, pendingLootOfferItem);
                }

                @Override
                public void scheduleLootOfferPrompt(AgentRuntimeEntry entry, Character agent, Item item, long delayMs) {
                    scheduleLootOfferPrompt.schedule(entry, agent, item, delayMs);
                }

                @Override
                public void cleanupGhostDrop(Character agent, MapItem drop) {
                    cleanupGhostDrop.cleanup(agent, drop);
                }

                @Override
                public void pickup(Character character, MapItem drop) {
                    pickup.pickup(character, drop);
                }
            };
        }
    }

    @FunctionalInterface
    public interface SetInventoryCooldown {
        void set(AgentRuntimeEntry entry, int cooldownMs);
    }
}
