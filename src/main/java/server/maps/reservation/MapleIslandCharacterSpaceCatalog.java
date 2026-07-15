package server.maps.reservation;

import java.util.ArrayList;
import java.util.List;

public final class MapleIslandCharacterSpaceCatalog {
    public static final int AMHERST_MAP_ID = 1000000;
    public static final int SOUTHPERRY_MAP_ID = 2000000;
    public static final int SOUTHPERRY_MIDPOINT_X = 1890;
    private static final int SPACING_PX = 25;

    private record HorizontalRange(int startX, int endX, int y) {
    }

    private static final List<CharacterSpace> AMHERST = build(
            "maple-island:amherst:character",
            AMHERST_MAP_ID,
            List.of(
                    new HorizontalRange(-150, 690, 274),
                    new HorizontalRange(335, 835, -56),
                    new HorizontalRange(880, 920, 154),
                    new HorizontalRange(1010, 1590, 154),
                    new HorizontalRange(1870, 1910, -146),
                    new HorizontalRange(1960, 2000, -146),
                    new HorizontalRange(1870, 1910, -86),
                    new HorizontalRange(1960, 2000, -86),
                    new HorizontalRange(1870, 1910, 94),
                    new HorizontalRange(1780, 1820, 214),
                    new HorizontalRange(1910, 2500, 274)));

    private static final List<CharacterSpace> SOUTHPERRY = build(
            "maple-island:southperry:character",
            SOUTHPERRY_MAP_ID,
            List.of(
                    new HorizontalRange(-70, 1420, 527),
                    new HorizontalRange(470, 970, 47),
                    new HorizontalRange(995, 1040, -8),
                    new HorizontalRange(50, 100, 465),
                    new HorizontalRange(1230, 1280, 468),
                    new HorizontalRange(1150, 1190, 107),
                    new HorizontalRange(1240, 1280, 107),
                    new HorizontalRange(1510, 1550, 107),
                    new HorizontalRange(1600, 1640, 107),
                    new HorizontalRange(1640, 1880, 407),
                    new HorizontalRange(1900, 2200, 407),
                    new HorizontalRange(2218, 2282, 347),
                    new HorizontalRange(2308, 2668, 287),
                    new HorizontalRange(2398, 2462, 167),
                    new HorizontalRange(2398, 2462, 227),
                    new HorizontalRange(2668, 2732, 227),
                    new HorizontalRange(2758, 2822, 167),
                    new HorizontalRange(2848, 3870, 107),
                    new HorizontalRange(2605, 2708, -256),
                    new HorizontalRange(2941, 3124, -196),
                    new HorizontalRange(3157, 3285, -167),
                    new HorizontalRange(3298, 3298, -100),
                    new HorizontalRange(3393, 3418, -100),
                    new HorizontalRange(3464, 3536, 5),
                    new HorizontalRange(3489, 3573, 44)));

    private static final List<CharacterSpace> SOUTHPERRY_LEFT = SOUTHPERRY.stream()
            .filter(space -> space.x() < SOUTHPERRY_MIDPOINT_X)
            .toList();
    private static final List<CharacterSpace> SOUTHPERRY_RIGHT = SOUTHPERRY.stream()
            .filter(space -> space.x() >= SOUTHPERRY_MIDPOINT_X)
            .toList();

    private MapleIslandCharacterSpaceCatalog() {
    }

    public static List<CharacterSpace> amherst() {
        return AMHERST;
    }

    public static List<CharacterSpace> southperry() {
        return SOUTHPERRY;
    }

    public static List<CharacterSpace> southperryLeft() {
        return SOUTHPERRY_LEFT;
    }

    public static List<CharacterSpace> southperryRight() {
        return SOUTHPERRY_RIGHT;
    }

    private static List<CharacterSpace> build(
            String catalogId,
            int mapId,
            List<HorizontalRange> ranges) {
        List<CharacterSpace> spaces = new ArrayList<>();
        int spotNumber = 1;
        for (int rowId = 0; rowId < ranges.size(); rowId++) {
            HorizontalRange range = ranges.get(rowId);
            int slotIndex = 0;
            for (int x = range.startX(); x <= range.endX(); x += SPACING_PX) {
                spaces.add(new CharacterSpace(
                        catalogId, spotNumber++, mapId, rowId, slotIndex++, x, range.y()));
            }
        }
        return List.copyOf(spaces);
    }
}
