package server.agents.plans.amherst;

import client.Character;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import server.maps.MapleMap;

import java.awt.Point;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MapleIslandRelaxerSpotReservationRuntimeTest {
    @AfterEach
    void clearReservations() {
        MapleIslandRelaxerSpotReservationRuntime.clear();
    }

    @Test
    void nearPioReservationSkipsLiveOccupantAndKeepsAgentFootprintsApart() {
        MapleMap map = mock(MapleMap.class);
        Character first = agent(1, map);
        Character second = agent(2, map);
        Character blocker = mock(Character.class);
        when(blocker.getId()).thenReturn(99);
        when(blocker.getPosition()).thenReturn(new Point(262, 274));
        when(map.getCharacters()).thenReturn(List.of(first, second, blocker));

        MapleIslandRelaxerSpotCatalog.Spot firstSpot =
                MapleIslandRelaxerSpotReservationRuntime.reserveNearPio(first, 0).orElseThrow();
        MapleIslandRelaxerSpotCatalog.Spot secondSpot =
                MapleIslandRelaxerSpotReservationRuntime.reserveNearPio(second, 0).orElseThrow();

        assertNotEquals(new Point(262, 274), new Point(firstSpot.x(), firstSpot.y()));
        assertTrue(Math.abs(firstSpot.x() - secondSpot.x()) >= 50);
    }

    private static Character agent(int id, MapleMap map) {
        Character agent = mock(Character.class);
        when(agent.getId()).thenReturn(id);
        when(agent.getMapId()).thenReturn(MapleIslandRelaxerSpotCatalog.AMHERST_MAP_ID);
        when(agent.getMap()).thenReturn(map);
        when(agent.getPosition()).thenReturn(new Point(547, 274));
        return agent;
    }
}
