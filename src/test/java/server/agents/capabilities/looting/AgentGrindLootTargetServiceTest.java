package server.agents.capabilities.looting;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.integration.AgentBotGrindLootStateRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.maps.MapItem;
import server.maps.MapleMap;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentGrindLootTargetServiceTest {
    @Test
    void validateCachedTargetKeepsLiveMapObject() {
        MapleMap map = mock(MapleMap.class);
        Character agent = mock(Character.class);
        when(agent.getMap()).thenReturn(map);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, mock(Character.class), null);
        MapItem loot = mockLoot(7, false);
        when(map.getMapObject(7)).thenReturn(loot);
        AgentBotGrindLootStateRuntime.setGrindLootTarget(entry, loot);

        AgentGrindLootTargetService.validateCachedGrindLootTarget(entry, agent);

        assertSame(loot, AgentBotGrindLootStateRuntime.grindLootTarget(entry));
    }

    @Test
    void validateCachedTargetClearsPickedUpOrStaleMapObject() {
        MapleMap map = mock(MapleMap.class);
        Character agent = mock(Character.class);
        when(agent.getMap()).thenReturn(map);
        AgentRuntimeEntry pickedEntry = new AgentRuntimeEntry(agent, mock(Character.class), null);
        MapItem pickedLoot = mockLoot(7, true);
        AgentBotGrindLootStateRuntime.setGrindLootTarget(pickedEntry, pickedLoot);

        AgentGrindLootTargetService.validateCachedGrindLootTarget(pickedEntry, agent);

        assertNull(AgentBotGrindLootStateRuntime.grindLootTarget(pickedEntry));

        AgentRuntimeEntry staleEntry = new AgentRuntimeEntry(agent, mock(Character.class), null);
        MapItem staleLoot = mockLoot(8, false);
        AgentBotGrindLootStateRuntime.setGrindLootTarget(staleEntry, staleLoot);
        when(map.getMapObject(8)).thenReturn(null);

        AgentGrindLootTargetService.validateCachedGrindLootTarget(staleEntry, agent);

        assertNull(AgentBotGrindLootStateRuntime.grindLootTarget(staleEntry));
    }

    @Test
    void refreshDoesNothingWhenAiTickIsNotDue() {
        Character agent = mock(Character.class);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, mock(Character.class), null);
        MapItem existingLoot = mockLoot(9, false);
        AgentBotGrindLootStateRuntime.setGrindLootTarget(entry, existingLoot);

        AgentGrindLootTargetService.refreshGrindLootTarget(entry, agent, false, 100);

        assertSame(existingLoot, AgentBotGrindLootStateRuntime.grindLootTarget(entry));
    }

    private static MapItem mockLoot(int objectId, boolean pickedUp) {
        MapItem loot = mock(MapItem.class);
        when(loot.getObjectId()).thenReturn(objectId);
        when(loot.isPickedUp()).thenReturn(pickedUp);
        return loot;
    }
}
