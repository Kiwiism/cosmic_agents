package server.agents.runtime;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.capabilities.looting.AgentLootEligibility;
import server.agents.integration.AgentGrindLootStateRuntime;
import server.agents.capabilities.combat.AgentGrindWanderStateRuntime;
import server.maps.MapItem;
import server.maps.MapleMap;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentGrindTargetPositionServiceTest {
    private static final int LOOT_RADIUS = 100;
    private static final int STOP_DISTANCE = 25;
    private static final int RETRY_SUPPRESS_MS = 5_000;

    @Test
    void noGraphFallbackReusesLegacyWanderDirection() {
        AgentRuntimeEntry entry = entry(null);
        Point agentPosition = new Point(100, 100);

        Point first = AgentGrindTargetPositionService.resolveNoGrindTargetPosition(
                entry,
                agentPosition,
                null,
                LOOT_RADIUS,
                STOP_DISTANCE,
                RETRY_SUPPRESS_MS,
                (graph, e, map, position) -> -1);
        int direction = AgentGrindWanderStateRuntime.wanderDirection(entry);
        Point second = AgentGrindTargetPositionService.resolveNoGrindTargetPosition(
                entry,
                agentPosition,
                null,
                LOOT_RADIUS,
                STOP_DISTANCE,
                RETRY_SUPPRESS_MS,
                (graph, e, map, position) -> -1);

        assertTrue(direction == -1 || direction == 1);
        assertEquals(new Point(100 + direction * 200, 100), first);
        assertEquals(first, second);
    }

    @Test
    void activeLootInsidePassiveRadiusIsSuppressedAndCleared() {
        MapleMap map = mock(MapleMap.class);
        Character agent = mock(Character.class);
        when(agent.getMap()).thenReturn(map);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, mock(Character.class), null);
        MapItem loot = mockLoot(7, new Point(100 + LOOT_RADIUS, 100));
        AgentGrindLootStateRuntime.setGrindLootTarget(entry, loot);
        when(map.getMapObject(7)).thenReturn(loot);

        Point target = AgentGrindTargetPositionService.activeGrindLootPosition(
                entry,
                new Point(100, 100),
                LOOT_RADIUS,
                RETRY_SUPPRESS_MS);

        assertNull(target);
        assertNull(AgentGrindLootStateRuntime.grindLootTarget(entry));
        assertTrue(AgentGrindLootStateRuntime.isRetrySuppressed(entry, loot, System.currentTimeMillis()));
    }

    @Test
    void activeLootTravelDistanceIgnoresPassivePickupRadius() {
        assertEquals(0.0, AgentGrindTargetPositionService.activeLootTravelDistSq(
                new Point(100, 100),
                new Point(100 + LOOT_RADIUS, 100),
                LOOT_RADIUS));
        assertEquals(441.0, AgentGrindTargetPositionService.activeLootTravelDistSq(
                new Point(100, 100),
                new Point(100 + LOOT_RADIUS + 21, 100),
                LOOT_RADIUS));
    }

    @Test
    void convenientLootRequiresTravelToBeatMobDistanceRatio() {
        MapleMap map = mock(MapleMap.class);
        Character agent = mock(Character.class);
        when(agent.getMap()).thenReturn(map);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, mock(Character.class), null);
        Point lootPosition = new Point(221, 100);
        MapItem loot = mockLoot(8, lootPosition);
        AgentGrindLootStateRuntime.setGrindLootTarget(entry, loot);
        when(map.getMapObject(8)).thenReturn(loot);

        assertEquals(lootPosition, AgentGrindTargetPositionService.convenientLootTarget(
                entry,
                new Point(100, 100),
                new Point(500, 100),
                LOOT_RADIUS,
                0.09f,
                RETRY_SUPPRESS_MS));
    }

    private static AgentRuntimeEntry entry(MapleMap map) {
        Character agent = mock(Character.class);
        when(agent.getMap()).thenReturn(map);
        return new AgentRuntimeEntry(agent, mock(Character.class), null);
    }

    private static MapItem mockLoot(int objectId, Point position) {
        MapItem loot = mock(MapItem.class);
        when(loot.getObjectId()).thenReturn(objectId);
        when(loot.getPosition()).thenReturn(position);
        when(loot.isPickedUp()).thenReturn(false);
        when(loot.canBePickedBy(org.mockito.ArgumentMatchers.any())).thenReturn(true);
        when(loot.getItemId()).thenReturn(0);
        when(loot.getMeso()).thenReturn(1);
        when(loot.getDropTime()).thenReturn(System.currentTimeMillis() - AgentLootEligibility.MIN_TARGET_LOOT_AGE_MS - 1);
        return loot;
    }
}
