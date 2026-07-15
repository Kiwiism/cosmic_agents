package server.maps.reservation;

import client.Character;
import client.Client;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import server.maps.MapleMap;

import java.awt.Point;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FreeMarketStorePlacementServiceTest {
    @AfterEach
    void clearReservations() {
        CharacterSpaceReservationRuntime.clear();
    }

    @Test
    void availabilityProbeFindsEmptyCatalogSpotWithoutHoldingIt() {
        Character character = mock(Character.class);
        Client client = mock(Client.class);
        MapleMap map = mock(MapleMap.class);

        when(character.getClient()).thenReturn(client);
        when(client.getChannel()).thenReturn(1);
        when(character.getWorld()).thenReturn(0);
        when(character.getMapId()).thenReturn(910000001);
        when(character.getId()).thenReturn(100);
        when(character.getPosition()).thenReturn(new Point(328, 34));
        when(character.getMap()).thenReturn(map);
        when(map.findClosestTeleportPortal(any(Point.class))).thenReturn(null);
        when(map.getMapObjectsInRange(any(Point.class), anyDouble(), any())).thenReturn(List.of());

        CharacterSpaceScope scope = new CharacterSpaceScope(0, 1, 910000001);
        List<CharacterSpace> spaces = FreeMarketCharacterSpaceCatalog.spaces(910000001);
        assertTrue(CharacterSpaceReservationRuntime.reserveExact(
                scope, CharacterSpaceOwner.testStall(1), spaces, spaces.get(20), 1).isPresent());
        assertTrue(CharacterSpaceReservationRuntime.reserveExact(
                scope, CharacterSpaceOwner.testStall(2), spaces, spaces.get(22), 1).isPresent());

        assertTrue(FreeMarketStorePlacementService.hasAvailablePlacement(character));
        assertTrue(FreeMarketStorePlacementService.reservation(character).isEmpty());
        assertEquals(2, CharacterSpaceReservationRuntime.occupiedCount());
    }
}
