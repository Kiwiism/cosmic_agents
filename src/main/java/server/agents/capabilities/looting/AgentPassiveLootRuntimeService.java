package server.agents.capabilities.looting;

import client.Character;
import client.inventory.Item;
import server.bots.BotEntry;
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

    public static void tickPassiveLoot(BotEntry entry,
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
                        callbacks::replyNow,
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
        boolean hasLootInhibit(BotEntry entry);

        void tickLootInhibit(BotEntry entry);

        boolean hasActiveTradeSequence(BotEntry entry);

        void tickInventoryFullWarnCooldown(BotEntry entry);

        long nowMs();

        int lootRadius();

        boolean canWarnInventoryFull(BotEntry entry);

        void replyNow(BotEntry entry, String message);

        int delayInventoryFullWarnCooldown();

        void setInventoryFullWarnCooldownMs(BotEntry entry, int cooldownMs);

        Character owner(BotEntry entry);

        Item pendingLootOfferItem(BotEntry entry);

        boolean hasItem(Character agent, Item item);

        void autoEquip(Character agent, Character owner, Item pendingLootOfferItem);

        void scheduleLootOfferPrompt(BotEntry entry, Character agent, Item item, long delayMs);

        void cleanupGhostDrop(Character agent, MapItem drop);

        void pickup(Character character, MapItem drop);

        static RuntimeCallbacks of(java.util.function.Predicate<BotEntry> hasLootInhibit,
                                   java.util.function.Consumer<BotEntry> tickLootInhibit,
                                   java.util.function.Predicate<BotEntry> hasActiveTradeSequence,
                                   java.util.function.Consumer<BotEntry> tickInventoryFullWarnCooldown,
                                   LongSupplier nowMs,
                                   IntSupplier lootRadius,
                                   java.util.function.Predicate<BotEntry> canWarnInventoryFull,
                                   AgentPassiveLootService.ReplySink replyNow,
                                   IntSupplier delayInventoryFullWarnCooldown,
                                   SetInventoryCooldown setInventoryFullWarnCooldownMs,
                                   Function<BotEntry, Character> owner,
                                   Function<BotEntry, Item> pendingLootOfferItem,
                                   AgentPassiveLootService.ItemPresence hasItem,
                                   AgentPassiveLootService.AutoEquipSink autoEquip,
                                   AgentPassiveLootService.LootOfferPromptSink scheduleLootOfferPrompt,
                                   AgentPassiveLootService.CleanupSink cleanupGhostDrop,
                                   AgentPassiveLootService.PickupSink pickup) {
            return new RuntimeCallbacks() {
                @Override
                public boolean hasLootInhibit(BotEntry entry) {
                    return hasLootInhibit.test(entry);
                }

                @Override
                public void tickLootInhibit(BotEntry entry) {
                    tickLootInhibit.accept(entry);
                }

                @Override
                public boolean hasActiveTradeSequence(BotEntry entry) {
                    return hasActiveTradeSequence.test(entry);
                }

                @Override
                public void tickInventoryFullWarnCooldown(BotEntry entry) {
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
                public boolean canWarnInventoryFull(BotEntry entry) {
                    return canWarnInventoryFull.test(entry);
                }

                @Override
                public void replyNow(BotEntry entry, String message) {
                    replyNow.reply(entry, message);
                }

                @Override
                public int delayInventoryFullWarnCooldown() {
                    return delayInventoryFullWarnCooldown.getAsInt();
                }

                @Override
                public void setInventoryFullWarnCooldownMs(BotEntry entry, int cooldownMs) {
                    setInventoryFullWarnCooldownMs.set(entry, cooldownMs);
                }

                @Override
                public Character owner(BotEntry entry) {
                    return owner.apply(entry);
                }

                @Override
                public Item pendingLootOfferItem(BotEntry entry) {
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
                public void scheduleLootOfferPrompt(BotEntry entry, Character agent, Item item, long delayMs) {
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
        void set(BotEntry entry, int cooldownMs);
    }
}
