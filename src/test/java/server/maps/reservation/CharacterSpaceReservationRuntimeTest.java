package server.maps.reservation;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.awt.Point;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CharacterSpaceReservationRuntimeTest {
    private static final CharacterSpaceScope SCOPE = new CharacterSpaceScope(0, 1, 910000001);
    private static final List<CharacterSpace> SPACES = List.of(
            new CharacterSpace("test", 1, 910000001, 0, 0, 0, 0),
            new CharacterSpace("test", 2, 910000001, 0, 1, 100, 0),
            new CharacterSpace("test", 3, 910000001, 0, 2, 200, 0));

    @AfterEach
    void clearReservations() {
        CharacterSpaceReservationRuntime.clear();
    }

    @Test
    void nearestSelectionOnlyConsidersClosestLeftAndRightCandidates() {
        assertTrue(CharacterSpaceReservationRuntime.reserveExact(
                SCOPE, CharacterSpaceOwner.character(1), SPACES, SPACES.get(0), 1).isPresent());
        assertTrue(CharacterSpaceReservationRuntime.reserveExact(
                SCOPE, CharacterSpaceOwner.character(2), SPACES, SPACES.get(1), 1).isPresent());

        assertFalse(CharacterSpaceReservationRuntime.reserveNearestLeftOrRight(
                SCOPE,
                CharacterSpaceOwner.character(3),
                SPACES,
                new Point(90, 0),
                200,
                1,
                ignored -> true).isPresent());
    }

    @Test
    void contiguousFootprintReservesAllSlotsAndUsesTheirCenter() {
        var reservation = CharacterSpaceReservationRuntime.reserveExact(
                SCOPE, CharacterSpaceOwner.character(1), SPACES, SPACES.get(0), 2).orElseThrow();

        assertEquals(2, reservation.occupiedSpaces().size());
        assertEquals(new Point(50, 0), reservation.position());
        assertFalse(CharacterSpaceReservationRuntime.reserveExact(
                SCOPE, CharacterSpaceOwner.character(2), SPACES, SPACES.get(1), 1).isPresent());
    }

    @Test
    void identicalCatalogCoordinatesAreIndependentAcrossChannels() {
        CharacterSpaceScope otherChannel = new CharacterSpaceScope(0, 2, 910000001);

        assertTrue(CharacterSpaceReservationRuntime.reserveExact(
                SCOPE, CharacterSpaceOwner.character(1), SPACES, SPACES.get(0), 1).isPresent());
        assertTrue(CharacterSpaceReservationRuntime.reserveExact(
                otherChannel, CharacterSpaceOwner.character(2), SPACES, SPACES.get(0), 1).isPresent());
    }

    @Test
    void exactReservationHonorsLivePositionAvailabilityCheck() {
        assertFalse(CharacterSpaceReservationRuntime.reserveExact(
                SCOPE, CharacterSpaceOwner.character(1), SPACES, SPACES.get(0), 1,
                ignored -> false).isPresent());
        assertTrue(CharacterSpaceReservationRuntime.reserveExact(
                SCOPE, CharacterSpaceOwner.character(1), SPACES, SPACES.get(0), 1,
                ignored -> true).isPresent());
    }
}
