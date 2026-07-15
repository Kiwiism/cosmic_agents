package server.maps.reservation;

import client.Character;
import server.maps.MapObjectType;
import server.maps.Portal;

import java.awt.Point;
import java.util.List;
import java.util.Optional;

public final class FreeMarketStorePlacementService {
    public static final int MAXIMUM_SNAP_DISTANCE_PX = 125;
    private static final int LEGACY_STORE_CLEARANCE_PX = 80;
    private static final int PORTAL_CLEARANCE_PX = 120;

    private FreeMarketStorePlacementService() {
    }

    public static Optional<CharacterSpaceReservation> reserveNearest(Character character) {
        if (character == null || character.getClient() == null
                || !FreeMarketCharacterSpaceCatalog.isRoom(character.getMapId())) {
            return Optional.empty();
        }
        CharacterSpaceScope scope = scope(character);
        return CharacterSpaceReservationRuntime.reserveNearestLeftOrRight(
                scope,
                CharacterSpaceOwner.character(character.getId()),
                FreeMarketCharacterSpaceCatalog.spaces(character.getMapId()),
                character.getPosition(),
                MAXIMUM_SNAP_DISTANCE_PX,
                1,
                position -> placementIsClear(character, position));
    }

    public static Optional<CharacterSpaceReservation> reservation(Character character) {
        if (character == null) {
            return Optional.empty();
        }
        return CharacterSpaceReservationRuntime.reservation(CharacterSpaceOwner.character(character.getId()))
                .filter(reservation -> reservation.scope().equals(scope(character)));
    }

    public static boolean hasAvailablePlacement(Character character) {
        if (reservation(character).isPresent()) {
            return true;
        }
        Optional<CharacterSpaceReservation> probe = reserveNearest(character);
        if (probe.isEmpty()) {
            return false;
        }
        release(character);
        return true;
    }

    public static void release(Character character) {
        if (character != null && character.getId() > 0) {
            CharacterSpaceReservationRuntime.release(CharacterSpaceOwner.character(character.getId()));
        }
    }

    public static CharacterSpaceScope scope(Character character) {
        return new CharacterSpaceScope(
                character.getWorld(), character.getClient().getChannel(), character.getMapId());
    }

    private static boolean placementIsClear(Character character, Point position) {
        Portal portal = character.getMap().findClosestTeleportPortal(position);
        if (portal != null && portal.getPosition().distance(position) < PORTAL_CLEARANCE_PX) {
            return false;
        }
        long clearanceSquared = (long) LEGACY_STORE_CLEARANCE_PX * LEGACY_STORE_CLEARANCE_PX;
        return character.getMap().getMapObjectsInRange(
                        position,
                        clearanceSquared,
                        List.of(MapObjectType.HIRED_MERCHANT, MapObjectType.SHOP))
                .isEmpty();
    }
}
