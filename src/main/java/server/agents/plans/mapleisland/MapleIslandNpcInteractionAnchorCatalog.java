package server.agents.plans.mapleisland;

import java.awt.Point;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class MapleIslandNpcInteractionAnchorCatalog {
    private static final int YOONA_MAP_ID = 1010000;
    private static final int YOONA_NPC_ID = 20100;
    private static final List<Point> LEGACY_YOONA_ANCHORS = List.of(
            new Point(-210, 95), new Point(-150, 95),
            new Point(-210, 215), new Point(-150, 215));
    private static final Map<NpcPlacement, List<Point>> VARIATION_ANCHORS = Map.ofEntries(
            anchors(10000, 2101, points(116, 305, 136, 305, 156, 305, 96, 305, 174, 305)),
            anchors(10000, 2100, points(824, 125, 844, 125, 855, 125, 804, 125)),
            // Roger's own y=65 foothold is enclosed by walls with no ladder.
            // These reachable ground slots remain within the normal click range.
            anchors(20000, 2000,
                    points(120, 215, 168, 215, 216, 215, 264, 215,
                            312, 215, 360, 215, 408, 215)),
            anchors(30000, 2102, points(-51, 95, -69, 95, -89, 95, -109, 95, -129, 95)),
            // One ladder position is intentional; the rest keep Nina traffic on ground.
            anchors(30001, 2001,
                    points(24, 246, 4, 246, 44, 246, -16, 246,
                            64, 246, -36, 246, 77, 198)),
            anchors(40000, 2004, points(-50, 215, -70, 215, -30, 215, -90, 215, -10, 215)),
            anchors(40000, 2002, points(900, 155, 890, 155, 870, 155, 850, 155, 830, 155)),
            anchors(50000, 2003, points(180, 335, 150, 335, 170, 335, 130, 335, 110, 335)),
            anchors(50000, 2005, points(1120, 275, 1140, 275, 1160, 275, 1170, 275, 1100, 275)),
            anchors(1000000, 2103, points(1120, 154, 1100, 154, 1080, 154, 1140, 154, 1160, 154)),
            anchors(1000000, 12000, points(1620, 154, 1610, 154, 1590, 154, 1570, 154, 1550, 154)),
            anchors(1000000, 12101, points(90, 274, 110, 274, 130, 274, 150, 274, 170, 274)),
            anchors(1000000, 10000, points(560, 274, 540, 274, 580, 274, 600, 274, 620, 274)),
            anchors(YOONA_MAP_ID, YOONA_NPC_ID,
                    points(-179, 95, -199, 95, -159, 95, -219, 95, -141, 95)),
            anchors(1010000, 12100, points(-31, 155, 0, 155, -11, 155, -51, 155)),
            anchors(1010000, 20001, points(321, 95, 341, 95, 361, 95, 381, 95, 399, 95)),
            anchors(2000000, 20002, points(360, 527, 330, 527, 350, 527, 310, 527, 290, 527)));

    private MapleIslandNpcInteractionAnchorCatalog() {
    }

    public static Point nearestLegacy(int mapId, int npcId, Point origin) {
        List<Point> anchors = mapId == YOONA_MAP_ID && npcId == YOONA_NPC_ID
                ? LEGACY_YOONA_ANCHORS : List.of();
        if (anchors.isEmpty() || origin == null) {
            return null;
        }
        return anchors.stream().min(java.util.Comparator.comparingDouble(origin::distanceSq))
                .map(Point::new).orElse(null);
    }

    public static List<Point> anchors(int mapId, int npcId) {
        return VARIATION_ANCHORS.getOrDefault(new NpcPlacement(mapId, npcId), List.of())
                .stream().map(Point::new).toList();
    }

    public static List<Point> legacyAnchorsFor(int mapId, int npcId) {
        return mapId == YOONA_MAP_ID && npcId == YOONA_NPC_ID
                ? LEGACY_YOONA_ANCHORS.stream().map(Point::new).toList() : List.of();
    }

    private static Map.Entry<NpcPlacement, List<Point>> anchors(
            int mapId, int npcId, List<Point> points) {
        return Map.entry(new NpcPlacement(mapId, npcId), List.copyOf(points));
    }

    private static List<Point> points(int... coordinates) {
        if (coordinates.length % 2 != 0) {
            throw new IllegalArgumentException("anchor coordinates must be x/y pairs");
        }
        ArrayList<Point> points = new ArrayList<>(coordinates.length / 2);
        for (int index = 0; index < coordinates.length; index += 2) {
            points.add(new Point(coordinates[index], coordinates[index + 1]));
        }
        return List.copyOf(points);
    }

    private record NpcPlacement(int mapId, int npcId) {
    }
}
