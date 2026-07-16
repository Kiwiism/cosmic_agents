package server.agents.capabilities.looting;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.capabilities.looting.AgentGrindLootStateRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.maps.MapItem;
import server.maps.MapleMap;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
        AgentGrindLootStateRuntime.setGrindLootTarget(entry, loot);

        AgentGrindLootTargetService.validateCachedGrindLootTarget(entry, agent);

        assertSame(loot, AgentGrindLootStateRuntime.grindLootTarget(entry));
    }

    @Test
    void validateCachedTargetClearsPickedUpOrStaleMapObject() {
        MapleMap map = mock(MapleMap.class);
        Character agent = mock(Character.class);
        when(agent.getMap()).thenReturn(map);
        AgentRuntimeEntry pickedEntry = new AgentRuntimeEntry(agent, mock(Character.class), null);
        MapItem pickedLoot = mockLoot(7, true);
        AgentGrindLootStateRuntime.setGrindLootTarget(pickedEntry, pickedLoot);

        AgentGrindLootTargetService.validateCachedGrindLootTarget(pickedEntry, agent);

        assertNull(AgentGrindLootStateRuntime.grindLootTarget(pickedEntry));

        AgentRuntimeEntry staleEntry = new AgentRuntimeEntry(agent, mock(Character.class), null);
        MapItem staleLoot = mockLoot(8, false);
        AgentGrindLootStateRuntime.setGrindLootTarget(staleEntry, staleLoot);
        when(map.getMapObject(8)).thenReturn(null);

        AgentGrindLootTargetService.validateCachedGrindLootTarget(staleEntry, agent);

        assertNull(AgentGrindLootStateRuntime.grindLootTarget(staleEntry));
    }

    @Test
    void refreshDoesNothingWhenAiTickIsNotDue() {
        Character agent = mock(Character.class);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, mock(Character.class), null);
        MapItem existingLoot = mockLoot(9, false);
        AgentGrindLootStateRuntime.setGrindLootTarget(entry, existingLoot);

        AgentGrindLootTargetService.refreshGrindLootTarget(entry, agent, false, 100);

        assertSame(existingLoot, AgentGrindLootStateRuntime.grindLootTarget(entry));
    }

    @Test
    void partnerManagedEntryClearsCachedLootWithoutInspectingTheMap() {
        Character agent = mock(Character.class);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, mock(Character.class), null);
        entry.markPartnerManaged();
        MapItem existingLoot = mockLoot(10, false);
        AgentGrindLootStateRuntime.setGrindLootTarget(entry, existingLoot);
        clearInvocations(agent, existingLoot);

        AgentGrindLootTargetService.validateCachedGrindLootTarget(entry, agent);

        assertNull(AgentGrindLootStateRuntime.grindLootTarget(entry));
        verify(agent, never()).getMap();
        verify(existingLoot, never()).isPickedUp();
    }

    @Test
    void partnerManagedEntryCannotAcquireOrRetainAGrindLootTarget() {
        Character agent = mock(Character.class);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, mock(Character.class), null);
        entry.markPartnerManaged();
        AgentGrindLootStateRuntime.setGrindLootTarget(entry, mockLoot(11, false));
        clearInvocations(agent);

        AgentGrindLootTargetService.refreshGrindLootTarget(entry, agent, true, 100);

        assertNull(AgentGrindLootStateRuntime.grindLootTarget(entry));
        verify(agent, never()).getMap();
    }

    private static MapItem mockLoot(int objectId, boolean pickedUp) {
        MapItem loot = mock(MapItem.class);
        when(loot.getObjectId()).thenReturn(objectId);
        when(loot.isPickedUp()).thenReturn(pickedUp);
        return loot;
    }
}
