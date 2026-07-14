package server.maps.reservation;

import java.awt.Point;

public record CharacterSpace(
        String catalogId,
        int spotNumber,
        int mapId,
        int rowId,
        int slotIndex,
        int x,
        int y) {

    public CharacterSpace {
        if (catalogId == null || catalogId.isBlank() || spotNumber <= 0 || mapId <= 0
                || rowId < 0 || slotIndex < 0) {
            throw new IllegalArgumentException("character space identity is invalid");
        }
    }

    public Point position() {
        return new Point(x, y);
    }
}
