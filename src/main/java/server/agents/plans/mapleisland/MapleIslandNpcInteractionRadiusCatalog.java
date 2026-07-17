package server.agents.plans.mapleisland;

import server.agents.capabilities.npc.AgentNpcInteractionPolicy;

import java.util.List;

/**
 * Maple Island cohort spread radii. Keep these values explicit so placement
 * tuning does not hide behind the generic 300 px NPC click allowance.
 */
public final class MapleIslandNpcInteractionRadiusCatalog {
    private static final List<Entry> ENTRIES = List.of(
            entry(10_000, "Mushroom Town", 2_101, "Heena", -55, 135, 230),
            entry(10_000, "Mushroom Town", 2_100, "Sera", -35, 105, 220),
            entry(20_000, "Snail Garden", 2_000, "Roger", -160, 155, 180),
            entry(30_000, "Snail Field of Flowers", 2_102, "Nina", 125, 70, 225),
            entry(30_001, "Mushroom Town Townstreet", 2_001, "Sen", -50, -35, 110),
            entry(40_000, "Mushroom Town Training Ground", 2_004, "Todd", 5, -10, 180),
            entry(40_000, "Mushroom Town Training Ground", 2_002, "Peter", -180, -15, 205),
            entry(50_000, "Dangerous Forest", 2_003, "Robin", -65, -10, 155),
            entry(50_000, "Dangerous Forest", 2_005, "Sam", -280, -5, 230),
            entry(1_000_000, "Amherst", 2_103, "Maria", 30, 0, 205),
            entry(1_000_000, "Amherst", 12_000, "Lucas", 5, 5, 300),
            entry(1_000_000, "Amherst", 12_101, "Rain", 10, -5, 260),
            entry(1_000_000, "Amherst", 10_000, "Pio", 55, -50, 230),
            entry(1_010_000, "Entrance to Adventurer Training Center", 20_100, "Yoona", -80, 90, 125),
            entry(1_010_000, "Entrance to Adventurer Training Center", 12_100, "Mai", 40, -35, 150),
            entry(1_010_000, "Entrance to Adventurer Training Center", 20_001, "Bari", 55, 30, 155),
            entry(2_000_000, "Southperry", 20_002, "Biggs", -5, -10, 300));

    private MapleIslandNpcInteractionRadiusCatalog() {
    }

    public static int radiusPx(int mapId, int npcId) {
        return find(mapId, npcId).map(Entry::radiusPx)
                .orElse(AgentNpcInteractionPolicy.DEFAULT_CLICK_RANGE_PX);
    }

    public static PointOffset centerOffset(int mapId, int npcId) {
        return find(mapId, npcId)
                .map(entry -> new PointOffset(entry.centerOffsetX(), entry.centerOffsetY()))
                .orElse(new PointOffset(0, 0));
    }

    public static int requiredInteractionRangePx(int mapId, int npcId, int fallbackRangePx) {
        return find(mapId, npcId).map(Entry::requiredInteractionRangePx)
                .orElse(fallbackRangePx);
    }

    public static List<Entry> entries() {
        return ENTRIES;
    }

    private static java.util.Optional<Entry> find(int mapId, int npcId) {
        return ENTRIES.stream()
                .filter(entry -> entry.mapId() == mapId && entry.npcId() == npcId)
                .findFirst();
    }

    private static Entry entry(int mapId, String mapName, int npcId, String npcName,
                               int centerOffsetX, int centerOffsetY, int radiusPx) {
        return new Entry(mapId, mapName, npcId, npcName, centerOffsetX, centerOffsetY, radiusPx);
    }

    public record Entry(int mapId, String mapName, int npcId, String npcName,
                        int centerOffsetX, int centerOffsetY, int radiusPx) {
        public Entry {
            if (radiusPx <= 0) {
                throw new IllegalArgumentException("NPC interaction radius must be positive");
            }
        }

        public int requiredInteractionRangePx() {
            return (int) Math.ceil(Math.hypot(centerOffsetX, centerOffsetY) + radiusPx);
        }
    }

    public record PointOffset(int x, int y) {
    }
}
