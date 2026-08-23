package server.agents.capabilities.looting;

import client.Character;
import client.inventory.Inventory;
import client.inventory.InventoryType;
import org.junit.jupiter.api.Test;
import server.agents.runtime.AgentRuntimeEntry;
import server.maps.MapItem;
import server.maps.MapleMap;

import java.awt.Point;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentLootTargetServiceTest {
    @Test
    void preExitLootIncludesPassiveRangeAndHonoursPartyAssignments() {
        long nowMs = System.currentTimeMillis();
        MapleMap map = mock(MapleMap.class);
        Character agent = agentAt(map, new Point(0, 0));
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, mock(Character.class), null);
        MapItem close = mesoDrop(map, 11, new Point(40, 0), nowMs - 4_000L);
        MapItem farther = mesoDrop(map, 12, new Point(200, 0), nowMs - 4_000L);
        when(map.getDroppedItems()).thenReturn(List.of(close, farther));

        MapItem unreserved = AgentLootTargetService.findBestPreExitLootTarget(
                entry, agent, 100, null, Set.of(), -1,
                500, AgentLootEligibility.MIN_TARGET_LOOT_AGE_MS, Set.of());
        MapItem selected = AgentLootTargetService.findBestPreExitLootTarget(
                entry, agent, 100, null, Set.of(), -1,
                500, AgentLootEligibility.MIN_TARGET_LOOT_AGE_MS, Set.of(11));

        assertSame(close, unreserved);
        assertSame(farther, selected);
    }

    @Test
    void freshDropRemainsPendingUntilTargetAgeIsReached() {
        long nowMs = 10_000L;
        MapleMap map = mock(MapleMap.class);
        Character agent = agentAt(map, new Point(0, 0));
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, mock(Character.class), null);
        MapItem fresh = mesoDrop(map, 15, new Point(40, 0), nowMs - 1_000L);

        org.junit.jupiter.api.Assertions.assertTrue(AgentLootEligibility.isWaitingForTargetAge(
                entry, agent, map, fresh, nowMs,
                AgentLootEligibility.MIN_TARGET_LOOT_AGE_MS));
        org.junit.jupiter.api.Assertions.assertFalse(AgentLootEligibility.isWaitingForTargetAge(
                entry, agent, map, fresh, nowMs + 2_000L,
                AgentLootEligibility.MIN_TARGET_LOOT_AGE_MS));
    }

    @Test
    void preExitLootMayExceedTheOrdinaryGrindSeekRange() {
        long nowMs = System.currentTimeMillis();
        MapleMap map = mock(MapleMap.class);
        Character agent = agentAt(map, new Point(0, 0));
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, mock(Character.class), null);
        MapItem distant = mesoDrop(map, 21, new Point(1_200, 0), nowMs - 4_000L);
        when(map.getDroppedItems()).thenReturn(List.of(distant));

        MapItem selected = AgentLootTargetService.findBestPreExitLootTarget(
                entry, agent, 100, null, Set.of(), -1,
                1_500, AgentLootEligibility.MIN_TARGET_LOOT_AGE_MS, Set.of());

        assertSame(distant, selected);
    }

    @Test
    void unrelatedFullInventoryTabDoesNotRejectCollectibleDrop() {
        long nowMs = System.currentTimeMillis();
        MapleMap map = mock(MapleMap.class);
        Character agent = agentAt(map, new Point(0, 0));
        Inventory fullUse = mock(Inventory.class);
        Inventory availableEtc = mock(Inventory.class);
        when(fullUse.isFull()).thenReturn(true);
        when(availableEtc.isFull()).thenReturn(false);
        when(agent.getInventory(InventoryType.USE)).thenReturn(fullUse);
        when(agent.getInventory(InventoryType.ETC)).thenReturn(availableEtc);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, mock(Character.class), null);
        MapItem etcDrop = itemDrop(map, 31, 4_000_000, new Point(200, 0), nowMs - 4_000L);
        when(map.getDroppedItems()).thenReturn(List.of(etcDrop));

        MapItem selected = AgentLootTargetService.findBestPreExitLootTarget(
                entry, agent, 100, null, Set.of(), -1,
                500, AgentLootEligibility.MIN_TARGET_LOOT_AGE_MS, Set.of());

        assertSame(etcDrop, selected);
    }

    private static Character agentAt(MapleMap map, Point position) {
        Character agent = mock(Character.class);
        when(agent.getMap()).thenReturn(map);
        when(agent.getPosition()).thenReturn(position);
        when(agent.needQuestItem(anyInt(), anyInt())).thenReturn(true);
        return agent;
    }

    private static MapItem mesoDrop(
            MapleMap map, int objectId, Point position, long dropTime) {
        MapItem drop = baseDrop(map, objectId, position, dropTime);
        when(drop.getMeso()).thenReturn(10);
        when(drop.getItemId()).thenReturn(10);
        return drop;
    }

    private static MapItem itemDrop(
            MapleMap map, int objectId, int itemId, Point position, long dropTime) {
        MapItem drop = baseDrop(map, objectId, position, dropTime);
        when(drop.getMeso()).thenReturn(0);
        when(drop.getItemId()).thenReturn(itemId);
        return drop;
    }

    private static MapItem baseDrop(
            MapleMap map, int objectId, Point position, long dropTime) {
        MapItem drop = mock(MapItem.class);
        when(drop.getObjectId()).thenReturn(objectId);
        when(drop.getPosition()).thenReturn(position);
        when(drop.getDropTime()).thenReturn(dropTime);
        when(drop.getQuest()).thenReturn(-1);
        when(drop.canBePickedBy(any(Character.class))).thenReturn(true);
        when(map.getMapObject(objectId)).thenReturn(drop);
        return drop;
    }
}
