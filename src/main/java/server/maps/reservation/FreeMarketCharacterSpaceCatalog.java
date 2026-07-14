package server.maps.reservation;

import java.awt.Point;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class FreeMarketCharacterSpaceCatalog {
    public static final int FIRST_ROOM_MAP_ID = 910000001;
    public static final int LAST_ROOM_MAP_ID = 910000022;

    private static final List<Point> HENESYS = List.of(
            new Point(-262, -416), new Point(-129, -416), new Point(19, -416), new Point(175, -416),
            new Point(328, -416), new Point(482, -416), new Point(638, -416), new Point(801, -416),
            new Point(-300, -206), new Point(-133, -206), new Point(18, -206), new Point(166, -206),
            new Point(319, -206), new Point(477, -206),
            new Point(608, -86), new Point(723, -146), new Point(854, -146),
            new Point(-271, 34), new Point(-124, 34), new Point(21, 34), new Point(169, 34),
            new Point(328, 34), new Point(481, 34));

    private static final List<Point> LUDIBRIUM = List.of(
            new Point(-1769, -318), new Point(-1595, -318), new Point(-1409, -318),
            new Point(-1236, -318), new Point(-1081, -318), new Point(-916, -318),
            new Point(-751, -318), new Point(-580, -318), new Point(-421, -318),
            new Point(-1775, -108), new Point(-1608, -108), new Point(-1455, -108),
            new Point(-1290, -108), new Point(-1146, -108), new Point(-984, -108),
            new Point(-836, -108), new Point(-682, -108), new Point(-506, -108),
            new Point(-1893, 102), new Point(-1718, 102), new Point(-1561, 102),
            new Point(-1400, 102), new Point(-1230, 102), new Point(-1052, 102),
            new Point(-881, 102), new Point(-709, 102), new Point(-529, 102), new Point(-209, 102));

    private static final List<Point> PERION = List.of(
            new Point(-418, 975), new Point(-276, 975), new Point(-119, 975), new Point(35, 975),
            new Point(170, 975), new Point(313, 975), new Point(477, 975),
            new Point(-465, 1185), new Point(-322, 1185), new Point(-169, 1185),
            new Point(-24, 1185), new Point(120, 1185), new Point(255, 1185),
            new Point(374, 1185), new Point(533, 1185),
            new Point(-110, 1425), new Point(199, 1425),
            new Point(-508, 1515), new Point(-352, 1515), new Point(-203, 1515),
            new Point(-63, 1515), new Point(68, 1515), new Point(213, 1515),
            new Point(359, 1515), new Point(494, 1515), new Point(636, 1515));

    private static final List<Point> EL_NATH = List.of(
            new Point(159, -386), new Point(317, -386), new Point(458, -386),
            new Point(587, -386), new Point(730, -386), new Point(875, -386),
            new Point(1006, -386), new Point(1148, -386),
            new Point(60, -146), new Point(208, -146), new Point(356, -146),
            new Point(498, -146), new Point(639, -146), new Point(787, -146),
            new Point(933, -146), new Point(1086, -146), new Point(1244, -146),
            new Point(-190, 94), new Point(124, 94), new Point(283, 94), new Point(433, 94),
            new Point(574, 94), new Point(727, 94), new Point(876, 94), new Point(1024, 94),
            new Point(1158, 94), new Point(1319, 94));

    private static final Map<Integer, List<CharacterSpace>> BY_MAP = buildCatalog();

    private FreeMarketCharacterSpaceCatalog() {
    }

    public static boolean isRoom(int mapId) {
        return BY_MAP.containsKey(mapId);
    }

    public static int roomNumber(int mapId) {
        return isRoom(mapId) ? mapId - 910000000 : -1;
    }

    public static List<CharacterSpace> spaces(int mapId) {
        return BY_MAP.getOrDefault(mapId, List.of());
    }

    private static Map<Integer, List<CharacterSpace>> buildCatalog() {
        Map<Integer, List<CharacterSpace>> result = new HashMap<>();
        addRooms(result, 1, 6, HENESYS);
        addRooms(result, 7, 12, LUDIBRIUM);
        addRooms(result, 13, 17, PERION);
        addRooms(result, 18, 22, EL_NATH);
        return Map.copyOf(result);
    }

    private static void addRooms(
            Map<Integer, List<CharacterSpace>> result,
            int firstRoom,
            int lastRoom,
            List<Point> template) {
        for (int room = firstRoom; room <= lastRoom; room++) {
            int mapId = 910000000 + room;
            result.put(mapId, buildRoom(mapId, template));
        }
    }

    private static List<CharacterSpace> buildRoom(int mapId, List<Point> points) {
        Map<Integer, Integer> rowByY = new LinkedHashMap<>();
        Map<Integer, Integer> nextSlotByRow = new HashMap<>();
        List<CharacterSpace> spaces = new ArrayList<>(points.size());
        for (int i = 0; i < points.size(); i++) {
            Point point = points.get(i);
            int rowId = rowByY.computeIfAbsent(point.y, ignored -> rowByY.size());
            int slotIndex = nextSlotByRow.getOrDefault(rowId, 0);
            nextSlotByRow.put(rowId, slotIndex + 1);
            spaces.add(new CharacterSpace(
                    "free-market:" + mapId,
                    i + 1,
                    mapId,
                    rowId,
                    slotIndex,
                    point.x,
                    point.y));
        }
        return List.copyOf(spaces);
    }
}
