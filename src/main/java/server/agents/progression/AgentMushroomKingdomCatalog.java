package server.agents.progression;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Script/WZ-verified, non-repeatable Mushroom Kingdom questline contract. */
public final class AgentMushroomKingdomCatalog {
    public static final int ENTRANCE_MAP_ID = 106_020_000;
    public static final int FINAL_QUEST_ID = 2336;

    public record HuntMap(int mapId, String mapName, int recommendedMaximum) {
        public HuntMap {
            if (mapId <= 0 || mapName == null || mapName.isBlank() || recommendedMaximum < 1) {
                throw new IllegalArgumentException("valid Mushroom Kingdom hunt-map capacity is required");
            }
        }
    }

    public record QuestNode(int questId, int startMapId, int startNpcId,
                            int completeMapId, int completeNpcId,
                            int itemId, int requiredCount,
                            Set<Integer> mobIds, List<Integer> huntMapIds) {
        public QuestNode {
            mobIds = mobIds == null ? Set.of() : Set.copyOf(mobIds);
            huntMapIds = huntMapIds == null ? List.of() : List.copyOf(huntMapIds);
            if (huntMapIds.stream().anyMatch(mapId -> mapId == null || mapId <= 0)
                    || huntMapIds.stream().distinct().count() != huntMapIds.size()) {
                throw new IllegalArgumentException("hunt maps must be positive and unique");
            }
        }

        public boolean hunting() { return itemId > 0 || !mobIds.isEmpty(); }

        /** Authored first-choice map retained for compatibility with quest route checks. */
        public int huntMapId() { return huntMapIds.isEmpty() ? 0 : huntMapIds.getFirst(); }
    }

    private static final List<HuntMap> HUNT_MAPS = List.of(
            huntMap(106020100, "Secluded Mushroom Forest", 4),
            huntMap(106020200, "Isolated Mushroom Forest", 3),
            huntMap(106020300, "Deep Inside Mushroom Forest", 3),
            huntMap(106020400, "Split Road of Destiny", 3),
            huntMap(106020401, "Steep Downhill 1", 3),
            huntMap(106020402, "Steep Downhill 2", 3),
            huntMap(106020700, "Skyscraper 1", 3),
            huntMap(106020800, "Skyscraper 2", 3),
            huntMap(106021000, "Skyscraper 3", 2),
            huntMap(106021001, "Security Room", 1),
            huntMap(106021100, "Skyscraper 4", 3),
            huntMap(106021200, "Skyscraper 5", 3),
            huntMap(106021300, "Skyscraper 6", 3));
    private static final Map<Integer, HuntMap> HUNT_MAPS_BY_ID = indexHuntMaps();

    private static final List<QuestNode> MAINLINE = List.of(
            talk(2312, ENTRANCE_MAP_ID, 1300005, ENTRANCE_MAP_ID, 1300005,
                    4000499, 50, Set.of(3300000),
                    List.of(106020100, 106020200, 106020300)),
            talk(2313, ENTRANCE_MAP_ID, 1300005, ENTRANCE_MAP_ID, 1300003),
            talk(2314, ENTRANCE_MAP_ID, 1300003, ENTRANCE_MAP_ID, 1300003),
            talk(2315, ENTRANCE_MAP_ID, 1300003, ENTRANCE_MAP_ID, 1300004),
            talk(2316, ENTRANCE_MAP_ID, 1300004, ENTRANCE_MAP_ID, 1300007),
            talk(2317, ENTRANCE_MAP_ID, 1300007, ENTRANCE_MAP_ID, 1300007,
                    4000500, 100, Set.of(3300001),
                    List.of(106020300, 106020200, 106020100)),
            talk(2318, ENTRANCE_MAP_ID, 1300007, ENTRANCE_MAP_ID, 1300007,
                    4000499, 50, Set.of(3300000),
                    List.of(106020100, 106020200, 106020300)),
            talk(2319, ENTRANCE_MAP_ID, 1300007, ENTRANCE_MAP_ID, 1300004),
            talk(2320, ENTRANCE_MAP_ID, 1300007, 100000000, 1012111),
            talk(2321, ENTRANCE_MAP_ID, 1300004, ENTRANCE_MAP_ID, 1300003),
            talk(2322, ENTRANCE_MAP_ID, 1300003, ENTRANCE_MAP_ID, 1300003),
            talk(2323, ENTRANCE_MAP_ID, 1300003, ENTRANCE_MAP_ID, 1300004,
                    4000501, 100, Set.of(3300002),
                    List.of(106020401, 106020402)),
            talk(2324, ENTRANCE_MAP_ID, 1300004, ENTRANCE_MAP_ID, 1300004),
            talk(2325, ENTRANCE_MAP_ID, 1300005, 106021201, 1300008),
            talk(2326, 106021201, 1300008, 106021201, 1300008,
                    4001317, 1, Set.of(3300003),
                    List.of(106021100, 106021000, 106020800, 106020700)),
            talk(2327, 106021201, 1300008, 106021201, 1300008),
            talk(2328, ENTRANCE_MAP_ID, 1300003, ENTRANCE_MAP_ID, 1300005,
                    4000502, 200, Set.of(3300003),
                    List.of(106021100, 106021000, 106020800, 106020700)),
            talk(2329, ENTRANCE_MAP_ID, 1300005, ENTRANCE_MAP_ID, 1300005,
                    4000503, 200, Set.of(3300004),
                    List.of(106021300, 106021200, 106021100)),
            talk(2330, ENTRANCE_MAP_ID, 1300000, ENTRANCE_MAP_ID, 1300000),
            talk(2332, 106021402, 1300002, 106021402, 1300002),
            talk(2333, 106021600, 1300002, 106021600, 1300002,
                    0, 1, Set.of(3300008), List.of(106021600)),
            talk(2334, 106021600, 1300002, 106021600, 1300002),
            talk(2335, 106021600, 1300002, 106021001, 1300002),
            talk(2331, ENTRANCE_MAP_ID, 1300003, ENTRANCE_MAP_ID, 1300003,
                    4001318, 1, Set.of(), List.of()),
            talk(FINAL_QUEST_ID, 106021600, 1300002, ENTRANCE_MAP_ID, 1300000));

    private static final Map<Integer, QuestNode> BY_ID = index();

    private AgentMushroomKingdomCatalog() { }

    public static List<QuestNode> mainline() { return MAINLINE; }

    public static List<HuntMap> huntMaps() { return HUNT_MAPS; }

    public static List<HuntMap> huntMapsFor(QuestNode node) {
        if (node == null) return List.of();
        return node.huntMapIds().stream()
                .map(HUNT_MAPS_BY_ID::get)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    public static QuestNode require(int questId) {
        QuestNode node = BY_ID.get(questId);
        if (node == null) throw new IllegalArgumentException("Unknown Mushroom Kingdom quest " + questId);
        return node;
    }

    public static int entryQuestForJob(int jobId) {
        return switch (jobId / 100) {
            case 1 -> 2300;
            case 2 -> 2301;
            case 4 -> 2302;
            case 3 -> 2303;
            case 5 -> 2304;
            default -> throw new IllegalArgumentException("Mushroom Kingdom requires an Explorer job: " + jobId);
        };
    }

    public static int entryLeaderNpc(int questId) {
        return switch (questId) {
            case 2300 -> 1022000;
            case 2301 -> 1032001;
            case 2302 -> 1052001;
            case 2303 -> 1012100;
            case 2304 -> 1090000;
            default -> throw new IllegalArgumentException("Unknown Mushroom Kingdom entry quest " + questId);
        };
    }

    public static int entryLeaderMap(int questId) {
        return switch (questId) {
            case 2300 -> 102000003;
            case 2301 -> 101000003;
            case 2302 -> 103000003;
            case 2303 -> 100000201;
            case 2304 -> 120000101;
            default -> throw new IllegalArgumentException("Unknown Mushroom Kingdom entry quest " + questId);
        };
    }

    public static boolean supportedSecondJob(int jobId) {
        return switch (jobId) {
            case 110, 120, 130, 210, 220, 230,
                    310, 320, 410, 420, 510, 520 -> true;
            default -> false;
        };
    }

    private static QuestNode talk(int questId, int startMap, int startNpc, int endMap, int endNpc) {
        return talk(questId, startMap, startNpc, endMap, endNpc, 0, 0, Set.of(), List.of());
    }

    private static QuestNode talk(int questId, int startMap, int startNpc, int endMap, int endNpc,
                                  int itemId, int count, Set<Integer> mobs, List<Integer> huntMaps) {
        return new QuestNode(questId, startMap, startNpc, endMap, endNpc,
                itemId, count, mobs, huntMaps);
    }

    private static HuntMap huntMap(int mapId, String mapName, int recommendedMaximum) {
        return new HuntMap(mapId, mapName, recommendedMaximum);
    }

    private static Map<Integer, HuntMap> indexHuntMaps() {
        Map<Integer, HuntMap> values = new LinkedHashMap<>();
        HUNT_MAPS.forEach(map -> values.put(map.mapId(), map));
        if (values.size() != HUNT_MAPS.size()) {
            throw new IllegalStateException("duplicate Mushroom Kingdom hunt map");
        }
        return Map.copyOf(values);
    }

    private static Map<Integer, QuestNode> index() {
        Map<Integer, QuestNode> values = new LinkedHashMap<>();
        MAINLINE.forEach(node -> values.put(node.questId(), node));
        return Map.copyOf(values);
    }
}
