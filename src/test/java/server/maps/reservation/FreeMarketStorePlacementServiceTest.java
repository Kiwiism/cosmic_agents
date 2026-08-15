package server.maps.reservation;

import client.Character;
import client.Client;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import server.maps.MapleMap;
import server.maps.Portal;

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

    @Test
    void autonomousReservationCanSelectAValidSpotBeyondPlayerSnapRange() {
        Character character = mock(Character.class);
        Client client = mock(Client.class);
        MapleMap map = mock(MapleMap.class);

        when(character.getClient()).thenReturn(client);
        when(client.getChannel()).thenReturn(1);
        when(character.getWorld()).thenReturn(0);
        when(character.getMapId()).thenReturn(910000001);
        when(character.getId()).thenReturn(101);
        when(character.getPosition()).thenReturn(new Point(2_000, 34));
        when(character.getMap()).thenReturn(map);
        when(map.findClosestTeleportPortal(any(Point.class))).thenReturn(null);
        when(map.getMapObjectsInRange(any(Point.class), anyDouble(), any())).thenReturn(List.of());

        var reservation = FreeMarketStorePlacementService.reserveNearestForWalking(character);

        assertTrue(reservation.isPresent());
        assertTrue(reservation.orElseThrow().position().distance(new Point(2_000, 34))
                > FreeMarketStorePlacementService.MAXIMUM_SNAP_DISTANCE_PX);
    }

    @Test
    void autonomousReservationUsesPortalProximityInsteadOfAuthoredSpotNumber() {
        Character character = mock(Character.class);
        Client client = mock(Client.class);
        MapleMap map = mock(MapleMap.class);
        Portal entrance = mock(Portal.class);

        when(character.getClient()).thenReturn(client);
        when(client.getChannel()).thenReturn(1);
        when(character.getWorld()).thenReturn(0);
        when(character.getMapId()).thenReturn(910000001);
        when(character.getId()).thenReturn(102);
        when(character.getPosition()).thenReturn(new Point(-262, -416));
        when(character.getMap()).thenReturn(map);
        when(map.getPortals()).thenReturn(List.of(entrance));
        when(entrance.getTargetMapId()).thenReturn(910000000);
        when(entrance.getId()).thenReturn(11);
        when(entrance.getPosition()).thenReturn(new Point(790, 35));
        when(map.findClosestTeleportPortal(any(Point.class))).thenReturn(null);
        when(map.getMapObjectsInRange(any(Point.class), anyDouble(), any())).thenReturn(List.of());

        var reservation = FreeMarketStorePlacementService.reserveNearestForWalking(character);

        assertTrue(reservation.isPresent());
        assertEquals(17, reservation.orElseThrow().centerSpace().spotNumber());
    }
}
