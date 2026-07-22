package server.agents.capabilities.townlife;

import java.awt.Point;
import java.util.List;
import java.util.Map;

/** WZ-verified Lith Harbor activity anchors and enterable shop interiors. */
public final class LithHarborTownLifeCatalog {
    public static final int LITH_HARBOR_MAP_ID = 104_000_000;
    public static final int BIGGS_NPC_ID = 1_002_101;

    private static final List<Point> REST_SPOTS = List.of(
            new Point(650, 338),
            new Point(1_650, 641),
            // Native Lith Harbor bench seats from Map.wz/104000000 seat/0..8.
            new Point(2_404, 525),
            new Point(2_427, 525),
            new Point(2_448, 525),
            new Point(2_504, 525),
            new Point(2_527, 525),
            new Point(2_548, 525),
            new Point(2_606, 525),
            new Point(2_629, 525),
            new Point(2_650, 525),
            new Point(2_880, 520),
            new Point(3_240, 518),
            new Point(4_570, 75));
    private static final Map<Point, Integer> MAP_SEATS = Map.ofEntries(
            Map.entry(new Point(2_404, 525), 0),
            Map.entry(new Point(2_427, 525), 1),
            Map.entry(new Point(2_448, 525), 2),
            Map.entry(new Point(2_606, 525), 3),
            Map.entry(new Point(2_629, 525), 4),
            Map.entry(new Point(2_650, 525), 5),
            Map.entry(new Point(2_504, 525), 6),
            Map.entry(new Point(2_527, 525), 7),
            Map.entry(new Point(2_548, 525), 8));
    private static final List<Point> WANDER_SPOTS = List.of(
            new Point(360, 278),
            new Point(1_100, 279),
            new Point(1_800, 641),
            new Point(2_570, 522),
            new Point(3_390, 518),
            new Point(4_360, 433),
            new Point(4_930, 431));
    private static final List<NpcSpot> NPC_SPOTS = List.of(
            new NpcSpot(1_002_000, -70),
            new NpcSpot(1_002_001, 65),
            new NpcSpot(1_002_002, -70),
            new NpcSpot(1_002_003, 65),
            new NpcSpot(1_002_004, -70),
            new NpcSpot(1_002_005, 70),
            new NpcSpot(1_002_006, -65),
            new NpcSpot(1_002_101, 70));
    private static final List<Integer> SHOP_MAP_IDS = List.of(
            104_000_001,
            104_000_002,
            104_000_003);

    private LithHarborTownLifeCatalog() {
    }

    public static Point restSpot(int index) {
        return copy(REST_SPOTS.get(Math.floorMod(index, REST_SPOTS.size())));
    }

    public static Point wanderSpot(int index) {
        return copy(WANDER_SPOTS.get(Math.floorMod(index, WANDER_SPOTS.size())));
    }

    public static int mapSeatId(Point point) {
        return point == null ? -1 : MAP_SEATS.getOrDefault(point, -1);
    }

    public static NpcSpot npcSpot(int index) {
        return NPC_SPOTS.get(Math.floorMod(index, NPC_SPOTS.size()));
    }

    public static int shopMapId(int index) {
        return SHOP_MAP_IDS.get(Math.floorMod(index, SHOP_MAP_IDS.size()));
    }

    static List<Point> restSpots() {
        return REST_SPOTS.stream().map(LithHarborTownLifeCatalog::copy).toList();
    }

    static List<Point> wanderSpots() {
        return WANDER_SPOTS.stream().map(LithHarborTownLifeCatalog::copy).toList();
    }

    static List<NpcSpot> npcSpots() {
        return NPC_SPOTS;
    }

    private static Point copy(Point point) {
        return new Point(point);
    }

    public record NpcSpot(int npcId, int offsetX) {
    }
}
