package server.maps.reservation;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FreeMarketCharacterSpaceCatalogTest {
    @Test
    void allTwentyTwoRoomsHaveStableNumberedSpots() {
        for (int mapId = FreeMarketCharacterSpaceCatalog.FIRST_ROOM_MAP_ID;
             mapId <= FreeMarketCharacterSpaceCatalog.LAST_ROOM_MAP_ID;
             mapId++) {
            int currentMapId = mapId;
            List<CharacterSpace> spaces = FreeMarketCharacterSpaceCatalog.spaces(mapId);
            int room = FreeMarketCharacterSpaceCatalog.roomNumber(mapId);
            int expected = room <= 6 ? 23 : room <= 12 ? 28 : room <= 17 ? 26 : 27;

            assertEquals(expected, spaces.size(), "unexpected FM catalog size for map " + mapId);
            assertEquals(expected, new HashSet<>(spaces).size());
            assertTrue(spaces.stream().allMatch(space -> space.mapId() == currentMapId));
            for (int i = 0; i < spaces.size(); i++) {
                assertEquals(i + 1, spaces.get(i).spotNumber());
            }
        }
    }

    @Test
    void nonRoomMapsHaveNoCatalog() {
        assertEquals(-1, FreeMarketCharacterSpaceCatalog.roomNumber(910000000));
        assertTrue(FreeMarketCharacterSpaceCatalog.spaces(910000000).isEmpty());
    }
}
