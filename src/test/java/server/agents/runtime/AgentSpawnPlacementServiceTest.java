package server.agents.runtime;

import client.Character;
import org.junit.jupiter.api.Test;
import server.bots.BotEntry;
import server.maps.MapleMap;

import java.awt.Point;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentSpawnPlacementServiceTest {
    @Test
    void placesOnlineAgentWithoutRuntimeEntryLikeLegacyPath() {
        Character agent = mock(Character.class);
        MapleMap map = mock(MapleMap.class);
        Point spawnPosition = new Point(10, 20);
        List<String> calls = new ArrayList<>();

        AgentSpawnPlacementService.placeSpawnedOnlineAgent(
                null,
                agent,
                map,
                spawnPosition,
                hooks(calls));

        verify(agent).setPosition(spawnPosition);
        verify(agent).broadcastStance();
        assertEquals(List.of("partyHp"), calls);
    }

    @Test
    void resetsSpawnRuntimeInLegacyOrder() {
        Character agent = mock(Character.class);
        Character leader = mock(Character.class);
        MapleMap map = mock(MapleMap.class);
        when(map.getId()).thenReturn(1000);
        when(map.getFootholds()).thenReturn(mock(server.maps.FootholdTree.class));
        BotEntry entry = new BotEntry(agent, leader, null);
        Point spawnPosition = new Point(10, 20);
        List<String> calls = new ArrayList<>();

        AgentSpawnPlacementService.placeSpawnedOnlineAgent(
                entry,
                agent,
                map,
                spawnPosition,
                hooks(calls));

        assertEquals(List.of(
                "teleport",
                "movementReset",
                "deathClear",
                "mapTracking:1000:true",
                "navWarm",
                "cadenceReset",
                "directionClear",
                "broadcastInvalid",
                "broadcast",
                "partyHp"), calls);
    }

    private static AgentSpawnPlacementService.Hooks hooks(List<String> calls) {
        return new AgentSpawnPlacementService.Hooks(
                (map, desiredPosition) -> desiredPosition,
                (entry, agent, position) -> calls.add("teleport"),
                entry -> calls.add("movementReset"),
                entry -> calls.add("deathClear"),
                (entry, map, mapId) -> calls.add("mapTracking:" + mapId + ":" + (map != null)),
                (entry, map) -> calls.add("navWarm"),
                entry -> calls.add("cadenceReset"),
                entry -> calls.add("directionClear"),
                entry -> calls.add("broadcastInvalid"),
                entry -> calls.add("broadcast"),
                agent -> calls.add("partyHp"),
                (leader, agent) -> calls.add("partyJoin"));
    }
}
