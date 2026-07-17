package server.agents.runtime;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.integration.AgentRelationshipRuntime;
import server.maps.MapleMap;

import java.awt.Point;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentOwnerFreeReloginTest {
    @Test
    void restoresPlacementAndIndependentRelationshipsWithoutAnOwnerLookup() throws Exception {
        MapleMap map = mock(MapleMap.class);
        Character agent = character(200);
        Character followTarget = character(300);
        AtomicReference<AgentRuntimeEntry> registered = new AtomicReference<>();
        AgentReloginRequest request = new AgentReloginRequest(
                200, 700, 701L, 300, 0, 0, 1, 1010100, new Point(44, 55));

        boolean result = AgentLifecycleService.reloginAgent(
                request,
                new AgentLifecycleService.AgentReloginHooks(
                        (world, channel, mapId) -> map,
                        (world, characterId) -> characterId == 300 ? followTarget : null,
                        (targetMap, position) -> position,
                        (characterId, world, channel, targetMap, position) -> agent,
                        (cohortId, interactionTarget, loadedAgent) -> {
                            AgentRuntimeEntry entry = new AgentRuntimeEntry(loadedAgent, interactionTarget, null);
                            registered.set(entry);
                            return entry;
                        },
                        (entry, delay, action) -> { },
                        () -> 1000L,
                        (character, text) -> { }));

        assertTrue(result);
        assertSame(followTarget, AgentRelationshipRuntime.followTarget(registered.get()));
        assertSame(null, AgentRelationshipRuntime.interactionTarget(registered.get()));
        assertTrue(AgentRelationshipRuntime.cohortId(registered.get()) == 700L);
        assertTrue(AgentRelationshipRuntime.formationId(registered.get()) == 701L);
    }

    private static Character character(int id) {
        Character character = mock(Character.class);
        when(character.getId()).thenReturn(id);
        return character;
    }
}
