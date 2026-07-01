package server.agents.capabilities.looting;

import client.Character;
import client.inventory.Inventory;
import client.inventory.InventoryType;
import client.inventory.Item;
import constants.id.ItemId;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.bots.BotEntry;
import server.maps.MapItem;
import server.maps.MapleMap;

import java.awt.Point;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class AgentPassiveLootServiceTest {
    @Test
    void lootInhibitTicksAndSkipsMapWork() {
        Character agent = mock(Character.class);
        TraceCallbacks callbacks = new TraceCallbacks();
        callbacks.lootInhibit = true;

        AgentPassiveLootService.tickPassiveLoot(entry(agent), agent, callbacks);

        assertTrue(callbacks.lootInhibitTicked.get());
        assertFalse(callbacks.inventoryCooldownTicked.get());
    }

    @Test
    void fullInventoryWarningUsesLegacyMessageAndCooldown() {
        Character agent = agentOnMap();
        Inventory inventory = mock(Inventory.class);
        when(inventory.isFull()).thenReturn(true);
        when(agent.getInventory(InventoryType.USE)).thenReturn(inventory);
        MapItem drop = itemDrop(2000000, 1, true);
        when(agent.getMap().getDroppedItems()).thenReturn(List.of(drop));
        when(agent.getMap().getMapObject(1)).thenReturn(drop);
        TraceCallbacks callbacks = new TraceCallbacks();
        callbacks.warnAllowed = true;
        callbacks.delayCooldown = 321;

        AgentPassiveLootService.tickPassiveLoot(entry(agent), agent, callbacks);

        assertEquals("use inventory is full!", callbacks.replyMessage.get());
        assertEquals(321, callbacks.warnCooldown.get());
        assertFalse(callbacks.pickedUp.get());
    }

    @Test
    void equipPickupAutoEquipsAndSchedulesOfferWhenItemRemains() {
        Character agent = agentOnMap();
        Character owner = agentOnMap();
        Item item = mock(Item.class);
        MapItem drop = itemDrop(1002000, 1, true);
        when(drop.getItem()).thenReturn(item);
        when(agent.getMap().getDroppedItems()).thenReturn(List.of(drop));
        when(agent.getMap().getMapObject(1)).thenReturn(drop);
        when(agent.needQuestItem(eq(0), eq(1002000))).thenReturn(true);
        TraceCallbacks callbacks = new TraceCallbacks();
        callbacks.owner.set(owner);
        callbacks.hasItem = true;

        AgentPassiveLootService.tickPassiveLoot(entry(agent), agent, callbacks);

        assertSame(agent, callbacks.pickupCharacter.get());
        assertTrue(callbacks.cleaned.get());
        assertSame(agent, callbacks.autoEquipAgent.get());
        assertSame(owner, callbacks.autoEquipOwner.get());
        assertSame(item, callbacks.offerItem.get());
        assertEquals(5_000L, callbacks.offerDelayMs.get());
    }

    @Test
    void nxCardPickupRoutesToOwnerOnSameMap() {
        Character agent = agentOnMap();
        MapleMap map = agent.getMap();
        Character owner = mock(Character.class);
        when(owner.getMap()).thenReturn(map);
        MapItem drop = itemDrop(999999, 1, true);
        when(map.getDroppedItems()).thenReturn(List.of(drop));
        when(map.getMapObject(1)).thenReturn(drop);
        when(agent.needQuestItem(eq(0), eq(999999))).thenReturn(true);
        TraceCallbacks callbacks = new TraceCallbacks();
        callbacks.owner.set(owner);

        try (MockedStatic<ItemId> itemIds = mockStatic(ItemId.class, CALLS_REAL_METHODS)) {
            itemIds.when(() -> ItemId.isNxCard(999999)).thenReturn(true);

            AgentPassiveLootService.tickPassiveLoot(entry(agent), agent, callbacks);
        }

        assertSame(owner, callbacks.pickupCharacter.get());
    }

    private static BotEntry entry(Character agent) {
        return new BotEntry(agent, null, null);
    }

    private static Character agentOnMap() {
        Character agent = mock(Character.class);
        MapleMap map = mock(MapleMap.class);
        when(agent.getMap()).thenReturn(map);
        when(agent.getPosition()).thenReturn(new Point(0, 0));
        return agent;
    }

    private static MapItem itemDrop(int itemId, int objectId, boolean pickable) {
        MapItem drop = mock(MapItem.class);
        when(drop.getObjectId()).thenReturn(objectId);
        when(drop.isPickedUp()).thenReturn(false);
        when(drop.getPosition()).thenReturn(new Point(0, 0));
        when(drop.getDropTime()).thenReturn(System.currentTimeMillis() - 5_000L);
        when(drop.getItemId()).thenReturn(itemId);
        when(drop.getMeso()).thenReturn(0);
        when(drop.getQuest()).thenReturn(0);
        when(drop.canBePickedBy(any(Character.class))).thenReturn(pickable);
        return drop;
    }

    private static final class TraceCallbacks implements AgentPassiveLootService.PassiveLootCallbacks {
        boolean lootInhibit;
        boolean activeSequence;
        boolean warnAllowed;
        boolean hasItem;
        int delayCooldown;
        final AtomicBoolean lootInhibitTicked = new AtomicBoolean();
        final AtomicBoolean inventoryCooldownTicked = new AtomicBoolean();
        final AtomicReference<String> replyMessage = new AtomicReference<>();
        final AtomicInteger warnCooldown = new AtomicInteger();
        final AtomicReference<Character> owner = new AtomicReference<>();
        final AtomicReference<Character> pickupCharacter = new AtomicReference<>();
        final AtomicBoolean pickedUp = new AtomicBoolean();
        final AtomicBoolean cleaned = new AtomicBoolean();
        final AtomicReference<Character> autoEquipAgent = new AtomicReference<>();
        final AtomicReference<Character> autoEquipOwner = new AtomicReference<>();
        final AtomicReference<Item> pendingOfferItem = new AtomicReference<>();
        final AtomicReference<Item> offerItem = new AtomicReference<>();
        final AtomicReference<Long> offerDelayMs = new AtomicReference<>();

        @Override public boolean hasLootInhibit() { return lootInhibit; }
        @Override public void tickLootInhibit() { lootInhibitTicked.set(true); }
        @Override public boolean hasActiveTradeSequence() { return activeSequence; }
        @Override public void tickInventoryFullWarnCooldown() { inventoryCooldownTicked.set(true); }
        @Override public long nowMs() { return System.currentTimeMillis(); }
        @Override public int lootRadius() { return 100; }
        @Override public boolean canWarnInventoryFull() { return warnAllowed; }
        @Override public void replyNow(BotEntry entry, String message) { replyMessage.set(message); }
        @Override public int delayInventoryFullWarnCooldown() { return delayCooldown; }
        @Override public void setInventoryFullWarnCooldownMs(int cooldownMs) { warnCooldown.set(cooldownMs); }
        @Override public Character owner() { return owner.get(); }
        @Override public Item pendingLootOfferItem() { return pendingOfferItem.get(); }
        @Override public boolean hasItem(Character agent, Item item) { return hasItem; }
        @Override public void autoEquip(Character agent, Character owner, Item pendingLootOfferItem) {
            autoEquipAgent.set(agent);
            autoEquipOwner.set(owner);
            pendingOfferItem.set(pendingLootOfferItem);
        }
        @Override public void scheduleLootOfferPrompt(BotEntry entry, Character agent, Item item, long delayMs) {
            offerItem.set(item);
            offerDelayMs.set(delayMs);
        }
        @Override public void cleanupGhostDrop(Character agent, MapItem drop) { cleaned.set(true); }
        @Override public void pickup(Character character, MapItem drop) {
            pickedUp.set(true);
            pickupCharacter.set(character);
        }
    }
}
