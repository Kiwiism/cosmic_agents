package server.agents.capabilities.looting;

import client.Character;
import client.inventory.Inventory;
import client.inventory.InventoryType;
import client.inventory.Item;
import constants.id.ItemId;
import constants.inventory.ItemConstants;
import server.agents.runtime.AgentRuntimeEntry;
import server.maps.MapItem;

import java.awt.Point;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

public final class AgentPassiveLootService {
    private AgentPassiveLootService() {
    }

    public static void tickPassiveLoot(AgentRuntimeEntry entry, Character agent, PassiveLootCallbacks callbacks) {
        if (callbacks.hasLootInhibit()) {
            callbacks.tickLootInhibit();
            return;
        }
        if (callbacks.hasActiveTradeSequence()) {
            return;
        }

        callbacks.tickInventoryFullWarnCooldown();
        Point agentPos = agent.getPosition();
        long now = callbacks.nowMs();
        for (MapItem drop : agent.getMap().getDroppedItems()) {
            if (!AgentLootEligibility.isPresent(agent.getMap(), drop)) {
                callbacks.cleanupGhostDrop(agent, drop);
                continue;
            }

            Point dropPos = drop.getPosition();
            if (Math.abs(dropPos.x - agentPos.x) > callbacks.lootRadius()
                    || Math.abs(dropPos.y - agentPos.y) > callbacks.lootRadius()) {
                continue;
            }

            if (!AgentLootEligibility.canBotTargetLoot(entry, agent, agent.getMap(), drop, now)) {
                if (AgentLootEligibility.canBotLoot(entry, agent, drop)) {
                    continue;
                }
                warnInventoryFullIfNeeded(entry, agent, drop, callbacks);
                continue;
            }

            if (drop.getMeso() <= 0 && drop.getItemId() > 0) {
                InventoryType type = ItemConstants.getInventoryType(drop.getItemId());
                Inventory inventory = agent.getInventory(type);
                if (inventory != null && inventory.isFull()) {
                    warnInventoryFull(entry, type, callbacks);
                    continue;
                }
            }

            Item pickedItem = drop.getItem();
            int pickedItemId = drop.getItemId();
            callbacks.pickup(agent, drop);
            callbacks.cleanupGhostDrop(agent, drop);
            if (pickedItem != null && pickedItemId > 0 && callbacks.hasItem(agent, pickedItem)) {
                InventoryType pickedType = ItemConstants.getInventoryType(pickedItemId);
                if (pickedType == InventoryType.EQUIP) {
                    callbacks.autoEquip(agent, null, callbacks.pendingLootOfferItem());
                    if (callbacks.hasItem(agent, pickedItem)) {
                        callbacks.scheduleLootOfferPrompt(entry, agent, pickedItem, 5_000L);
                    }
                } else if (ItemConstants.isThrowingStar(pickedItemId)) {
                    callbacks.scheduleLootOfferPrompt(entry, agent, pickedItem, 5_000L);
                }
            }
        }
    }

    private static void warnInventoryFullIfNeeded(AgentRuntimeEntry entry,
                                                  Character agent,
                                                  MapItem drop,
                                                  PassiveLootCallbacks callbacks) {
        if (drop.getMeso() > 0 || drop.getItemId() <= 0) {
            return;
        }
        InventoryType type = ItemConstants.getInventoryType(drop.getItemId());
        Inventory inventory = agent.getInventory(type);
        if (inventory != null && inventory.isFull()) {
            warnInventoryFull(entry, type, callbacks);
        }
    }

    private static void warnInventoryFull(AgentRuntimeEntry entry,
                                          InventoryType type,
                                          PassiveLootCallbacks callbacks) {
        if (!callbacks.canWarnInventoryFull()) {
            return;
        }
        callbacks.replyNow(entry, type.name().toLowerCase() + " inventory is full!");
        callbacks.setInventoryFullWarnCooldownMs(callbacks.delayInventoryFullWarnCooldown());
    }

    public interface PassiveLootCallbacks {
        boolean hasLootInhibit();
        void tickLootInhibit();
        boolean hasActiveTradeSequence();
        void tickInventoryFullWarnCooldown();
        long nowMs();
        int lootRadius();
        boolean canWarnInventoryFull();
        void replyNow(AgentRuntimeEntry entry, String message);
        int delayInventoryFullWarnCooldown();
        void setInventoryFullWarnCooldownMs(int cooldownMs);
        Character owner();
        Item pendingLootOfferItem();
        boolean hasItem(Character agent, Item item);
        void autoEquip(Character agent, Character owner, Item pendingLootOfferItem);
        void scheduleLootOfferPrompt(AgentRuntimeEntry entry, Character agent, Item item, long delayMs);
        void cleanupGhostDrop(Character agent, MapItem drop);
        void pickup(Character character, MapItem drop);

        static PassiveLootCallbacks of(BooleanSupplier hasLootInhibit,
                                      Runnable tickLootInhibit,
                                      BooleanSupplier hasActiveTradeSequence,
                                      Runnable tickInventoryFullWarnCooldown,
                                      LongSupplier nowMs,
                                      IntSupplier lootRadius,
                                      BooleanSupplier canWarnInventoryFull,
                                      ReplySink replyNow,
                                      IntSupplier delayInventoryFullWarnCooldown,
                                      Consumer<Integer> setInventoryFullWarnCooldownMs,
                                      Supplier<Character> owner,
                                      Supplier<Item> pendingLootOfferItem,
                                      ItemPresence hasItem,
                                      AutoEquipSink autoEquip,
                                      LootOfferPromptSink scheduleLootOfferPrompt,
                                      CleanupSink cleanupGhostDrop,
                                      PickupSink pickup) {
            return new PassiveLootCallbacks() {
                @Override public boolean hasLootInhibit() { return hasLootInhibit.getAsBoolean(); }
                @Override public void tickLootInhibit() { tickLootInhibit.run(); }
                @Override public boolean hasActiveTradeSequence() { return hasActiveTradeSequence.getAsBoolean(); }
                @Override public void tickInventoryFullWarnCooldown() { tickInventoryFullWarnCooldown.run(); }
                @Override public long nowMs() { return nowMs.getAsLong(); }
                @Override public int lootRadius() { return lootRadius.getAsInt(); }
                @Override public boolean canWarnInventoryFull() { return canWarnInventoryFull.getAsBoolean(); }
                @Override public void replyNow(AgentRuntimeEntry entry, String message) { replyNow.reply(entry, message); }
                @Override public int delayInventoryFullWarnCooldown() { return delayInventoryFullWarnCooldown.getAsInt(); }
                @Override public void setInventoryFullWarnCooldownMs(int cooldownMs) {
                    setInventoryFullWarnCooldownMs.accept(cooldownMs);
                }
                @Override public Character owner() { return owner.get(); }
                @Override public Item pendingLootOfferItem() { return pendingLootOfferItem.get(); }
                @Override public boolean hasItem(Character agent, Item item) { return hasItem.hasItem(agent, item); }
                @Override public void autoEquip(Character agent, Character owner, Item pendingLootOfferItem) {
                    autoEquip.autoEquip(agent, owner, pendingLootOfferItem);
                }
                @Override public void scheduleLootOfferPrompt(AgentRuntimeEntry entry, Character agent, Item item, long delayMs) {
                    scheduleLootOfferPrompt.schedule(entry, agent, item, delayMs);
                }
                @Override public void cleanupGhostDrop(Character agent, MapItem drop) {
                    cleanupGhostDrop.cleanup(agent, drop);
                }
                @Override public void pickup(Character character, MapItem drop) {
                    pickup.pickup(character, drop);
                }
            };
        }
    }

    @FunctionalInterface
    public interface ReplySink {
        void reply(AgentRuntimeEntry entry, String message);
    }

    @FunctionalInterface
    public interface ItemPresence {
        boolean hasItem(Character agent, Item item);
    }

    @FunctionalInterface
    public interface AutoEquipSink {
        void autoEquip(Character agent, Character owner, Item pendingLootOfferItem);
    }

    @FunctionalInterface
    public interface LootOfferPromptSink {
        void schedule(AgentRuntimeEntry entry, Character agent, Item item, long delayMs);
    }

    @FunctionalInterface
    public interface CleanupSink {
        void cleanup(Character agent, MapItem drop);
    }

    @FunctionalInterface
    public interface PickupSink {
        void pickup(Character character, MapItem drop);
    }
}
