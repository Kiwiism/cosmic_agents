package server.agents.capabilities.partyquest.lpq;

import java.util.List;

/** Authoritative local Ludibrium PQ content contract. */
public final class AgentLpqDefinition {
    public static final int RECRUIT_MAP = 221_024_500;
    public static final int ENTRY_NPC = 2_040_034;
    public static final int EXIT_MAP = 922_010_000;
    public static final int CLEAR_MAP = 922_011_000;
    public static final int BONUS_MAP = 922_011_100;
    public static final int PASS = 4_001_022;
    public static final int BOSS_KEY = 4_001_023;
    public static final int MIN_LEVEL = 35;
    public static final int MAX_LEVEL = 50;
    public static final int MIN_PARTY_SIZE = 5;
    public static final int MAX_PARTY_SIZE = 6;
    public static final int RECOMMENDED_PARTY_SIZE = 6;
    public static final int ALISHAR = 9_300_012;
    public static final int ROMBARD = 9_300_010;

    private static final List<Integer> EVENT_MAPS = List.of(
            922_010_100, 922_010_200, 922_010_201, 922_010_300,
            922_010_400, 922_010_401, 922_010_402, 922_010_403,
            922_010_404, 922_010_405, 922_010_500, 922_010_501,
            922_010_502, 922_010_503, 922_010_504, 922_010_505,
            922_010_506, 922_010_600, 922_010_700, 922_010_800,
            922_010_900, CLEAR_MAP, BONUS_MAP);

    private static final List<Stage> STAGES = List.of(
            new Stage(1, 922_010_100, 2_040_036, 25),
            new Stage(2, 922_010_200, 2_040_037, 15),
            new Stage(3, 922_010_300, 2_040_038, 32),
            new Stage(4, 922_010_400, 2_040_039, 6),
            new Stage(5, 922_010_500, 2_040_040, 24),
            new Stage(6, 922_010_600, 2_040_041, 0),
            new Stage(7, 922_010_700, 2_040_042, 3),
            new Stage(8, 922_010_800, 2_040_043, 0),
            new Stage(9, 922_010_900, 2_040_044, 1));

    private AgentLpqDefinition() {
    }

    public static List<Stage> stages() { return STAGES; }

    public static List<Integer> eventMaps() { return EVENT_MAPS; }

    public static boolean isEventMap(int mapId) { return EVENT_MAPS.contains(mapId); }

    public static int stageNumber(int mapId) {
        if (mapId == 922_010_201) return 2;
        if (mapId >= 922_010_100 && mapId <= 922_010_900) {
            return (mapId - 922_010_100) / 100 + 1;
        }
        return mapId == CLEAR_MAP || mapId == BONUS_MAP ? 10 : 0;
    }

    public static List<Integer> roomMaps(int stage) {
        return switch (stage) {
            case 4 -> List.of(922_010_401, 922_010_402, 922_010_403,
                    922_010_404, 922_010_405);
            case 5 -> List.of(922_010_501, 922_010_502, 922_010_503,
                    922_010_504, 922_010_505, 922_010_506);
            default -> List.of();
        };
    }

    public static Stage stage(int number) {
        return STAGES.stream().filter(stage -> stage.number() == number).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("invalid LPQ stage: " + number));
    }

    public record Stage(int number, int mapId, int npcId, int submissionCount) {
        public Stage {
            if (number < 1 || number > 9 || mapId <= 0 || npcId <= 0 || submissionCount < 0) {
                throw new IllegalArgumentException("valid LPQ stage values are required");
            }
        }
    }
}
