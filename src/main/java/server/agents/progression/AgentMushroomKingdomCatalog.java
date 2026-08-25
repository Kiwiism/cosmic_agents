package server.agents.progression;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Script/WZ-verified, non-repeatable Mushroom Kingdom questline contract. */
public final class AgentMushroomKingdomCatalog {
    public static final int ENTRANCE_MAP_ID = 106_020_000;
    public static final int FINAL_QUEST_ID = 2336;

    public record QuestNode(int questId, int startMapId, int startNpcId,
                            int completeMapId, int completeNpcId,
                            int itemId, int requiredCount,
                            Set<Integer> mobIds, int huntMapId) {
        public QuestNode {
            mobIds = mobIds == null ? Set.of() : Set.copyOf(mobIds);
        }

        public boolean hunting() { return itemId > 0 || !mobIds.isEmpty(); }
    }

    private static final List<QuestNode> MAINLINE = List.of(
            talk(2312, ENTRANCE_MAP_ID, 1300005, ENTRANCE_MAP_ID, 1300005,
                    4000499, 50, Set.of(3300000), 106020100),
            talk(2313, ENTRANCE_MAP_ID, 1300005, ENTRANCE_MAP_ID, 1300003),
            talk(2314, ENTRANCE_MAP_ID, 1300003, ENTRANCE_MAP_ID, 1300003),
            talk(2315, ENTRANCE_MAP_ID, 1300003, ENTRANCE_MAP_ID, 1300004),
            talk(2316, ENTRANCE_MAP_ID, 1300004, ENTRANCE_MAP_ID, 1300007),
            talk(2317, ENTRANCE_MAP_ID, 1300007, ENTRANCE_MAP_ID, 1300007,
                    4000500, 100, Set.of(3300001), 106020300),
            talk(2318, ENTRANCE_MAP_ID, 1300007, ENTRANCE_MAP_ID, 1300007,
                    4000499, 50, Set.of(3300000), 106020100),
            talk(2319, ENTRANCE_MAP_ID, 1300007, ENTRANCE_MAP_ID, 1300004),
            talk(2320, ENTRANCE_MAP_ID, 1300007, 100000000, 1012111),
            talk(2321, ENTRANCE_MAP_ID, 1300004, ENTRANCE_MAP_ID, 1300003),
            talk(2322, ENTRANCE_MAP_ID, 1300003, ENTRANCE_MAP_ID, 1300003),
            talk(2323, ENTRANCE_MAP_ID, 1300003, ENTRANCE_MAP_ID, 1300004,
                    4000501, 100, Set.of(3300002), 106020401),
            talk(2324, ENTRANCE_MAP_ID, 1300004, ENTRANCE_MAP_ID, 1300004),
            talk(2325, ENTRANCE_MAP_ID, 1300005, 106021201, 1300008),
            talk(2326, 106021201, 1300008, 106021201, 1300008,
                    4001317, 1, Set.of(3300003), 106021100),
            talk(2327, 106021201, 1300008, 106021201, 1300008),
            talk(2328, ENTRANCE_MAP_ID, 1300003, ENTRANCE_MAP_ID, 1300005,
                    4000502, 200, Set.of(3300003), 106021100),
            talk(2329, ENTRANCE_MAP_ID, 1300005, ENTRANCE_MAP_ID, 1300005,
                    4000503, 200, Set.of(3300004), 106021300),
            talk(2330, ENTRANCE_MAP_ID, 1300000, ENTRANCE_MAP_ID, 1300000),
            talk(2332, 106021402, 1300002, 106021402, 1300002),
            talk(2333, 106021600, 1300002, 106021600, 1300002,
                    0, 1, Set.of(3300008), 106021600),
            talk(2334, 106021600, 1300002, 106021600, 1300002),
            talk(2335, 106021600, 1300002, 106021001, 1300002),
            talk(2331, ENTRANCE_MAP_ID, 1300003, ENTRANCE_MAP_ID, 1300003,
                    4001318, 1, Set.of(), 0),
            talk(FINAL_QUEST_ID, 106021600, 1300002, ENTRANCE_MAP_ID, 1300000));

    private static final Map<Integer, QuestNode> BY_ID = index();

    private AgentMushroomKingdomCatalog() { }

    public static List<QuestNode> mainline() { return MAINLINE; }

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
        return talk(questId, startMap, startNpc, endMap, endNpc, 0, 0, Set.of(), 0);
    }

    private static QuestNode talk(int questId, int startMap, int startNpc, int endMap, int endNpc,
                                  int itemId, int count, Set<Integer> mobs, int huntMap) {
        return new QuestNode(questId, startMap, startNpc, endMap, endNpc,
                itemId, count, mobs, huntMap);
    }

    private static Map<Integer, QuestNode> index() {
        Map<Integer, QuestNode> values = new LinkedHashMap<>();
        MAINLINE.forEach(node -> values.put(node.questId(), node));
        return Map.copyOf(values);
    }
}
