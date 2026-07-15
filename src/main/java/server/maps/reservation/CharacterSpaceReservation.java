package server.maps.reservation;

import java.awt.Point;
import java.util.List;

public record CharacterSpaceReservation(
        CharacterSpaceScope scope,
        CharacterSpaceOwner owner,
        CharacterSpace centerSpace,
        Point position,
        List<CharacterSpace> occupiedSpaces) {

    public CharacterSpaceReservation {
        if (scope == null || owner == null || centerSpace == null || position == null
                || occupiedSpaces == null || occupiedSpaces.isEmpty()) {
            throw new IllegalArgumentException("complete character space reservation is required");
        }
        position = new Point(position);
        occupiedSpaces = List.copyOf(occupiedSpaces);
    }

    @Override
    public Point position() {
        return new Point(position);
    }
}
