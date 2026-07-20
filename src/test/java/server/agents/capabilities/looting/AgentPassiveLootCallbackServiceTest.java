package server.agents.capabilities.looting;

import client.Character;
import client.inventory.Item;
import org.junit.jupiter.api.Test;
import server.agents.runtime.AgentRuntimeEntry;
import server.maps.MapItem;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class AgentPassiveLootCallbackServiceTest {
    @Test
    void buildsPassiveLootCallbacksFromLegacyOperations() {
        Character agent = mock(Character.class);
        Character owner = mock(Character.class);
        Item item = mock(Item.class);
        MapItem drop = mock(MapItem.class);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, owner, null);
        AtomicBoolean lootInhibitTicked = new AtomicBoolean();
        AtomicBoolean cooldownTicked = new AtomicBoolean();
        AtomicInteger cooldown = new AtomicInteger();
        AtomicReference<Character> autoEquipAgent = new AtomicReference<>();
        AtomicReference<Character> pickupCharacter = new AtomicReference<>();
        AtomicBoolean offerScheduled = new AtomicBoolean();
        AtomicBoolean cleaned = new AtomicBoolean();

        AgentPassiveLootService.PassiveLootCallbacks callbacks =
                AgentPassiveLootCallbackService.passiveLootCallbacks(
                        () -> true,
                        () -> lootInhibitTicked.set(true),
                        () -> true,
                        () -> cooldownTicked.set(true),
                        () -> 123L,
                        () -> 456,
                        () -> true,
                        () -> 789,
                        cooldown::set,
                        () -> owner,
                        () -> item,
                        (currentAgent, currentItem) -> currentAgent == agent && currentItem == item,
                        (currentAgent, currentOwner, pendingLootOfferItem) -> {
                            autoEquipAgent.set(currentAgent);
                            assertSame(owner, currentOwner);
                            assertSame(item, pendingLootOfferItem);
                        },
                        (currentEntry, currentAgent, currentItem, delayMs) -> {
                            assertSame(entry, currentEntry);
                            assertSame(agent, currentAgent);
                            assertSame(item, currentItem);
                            assertEquals(5_000L, delayMs);
                            offerScheduled.set(true);
                        },
                        (currentAgent, currentDrop) -> {
                            assertSame(agent, currentAgent);
                            assertSame(drop, currentDrop);
                            cleaned.set(true);
                        },
                        (character, currentDrop) -> {
                            pickupCharacter.set(character);
                            assertSame(drop, currentDrop);
                        });

        assertTrue(callbacks.hasLootInhibit());
        callbacks.tickLootInhibit();
        assertTrue(callbacks.hasActiveTradeSequence());
        callbacks.tickInventoryFullWarnCooldown();
        assertEquals(123L, callbacks.nowMs());
        assertEquals(456, callbacks.lootRadius());
        assertTrue(callbacks.canWarnInventoryFull());
        assertEquals(789, callbacks.delayInventoryFullWarnCooldown());
        callbacks.setInventoryFullWarnCooldownMs(321);
        assertSame(owner, callbacks.owner());
        assertSame(item, callbacks.pendingLootOfferItem());
        assertTrue(callbacks.hasItem(agent, item));
        callbacks.autoEquip(agent, owner, item);
        callbacks.scheduleLootOfferPrompt(entry, agent, item, 5_000L);
        callbacks.cleanupGhostDrop(agent, drop);
        callbacks.pickup(owner, drop);

        assertTrue(lootInhibitTicked.get());
        assertTrue(cooldownTicked.get());
        assertEquals(321, cooldown.get());
        assertSame(agent, autoEquipAgent.get());
        assertTrue(offerScheduled.get());
        assertTrue(cleaned.get());
        assertSame(owner, pickupCharacter.get());
    }
}
