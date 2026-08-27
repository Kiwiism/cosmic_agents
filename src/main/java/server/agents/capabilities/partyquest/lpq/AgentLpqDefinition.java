package server.agents.capabilities.partyquest.lpq;

import java.util.List;

/** Authoritative local Ludibrium PQ content contract. */
public final class AgentLpqDefinition {
    public static final int STAGING_MAP = 221_024_400;
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
    public static final int BOSS_TRIGGER_RATZ = 9_300_006;
    public static final int ROMBARD = 9_300_010;
    public static final int STAGE_7_TRIGGER_REACTOR = 2_201_002;
    public static final int STAGE_4_MAGIC_MOB = 9_300_008;
    public static final int STAGE_4_PHYSICAL_MOB = 9_300_014;
    public static final int STAGE_4_WEAK_MAGIC_ROOM = 922_010_403;
    public static final int STAGE_5_TELEPORT_ROOM = 922_010_501;
    public static final int STAGE_5_DARK_SIGHT_ROOM = 922_010_506;
    public static final int CLEAR_NPC = 2_040_045;
    public static final int REWARD_NPC = 2_040_035;
    public static final int STAGE_2_TRAP_MAP = 922_010_201;
    public static final int STAGE_2_TRAP_REACTOR = 2_200_002;
    public static final int STAGE_2_SCOUT_COUNT = 2;
    public static final int RED_POTION = 2_000_000;
    public static final int ROOM_MARKER_MESOS = 10;
    public static final List<Integer> ROOM_MARKER_ITEMS = List.of(
            RED_POTION, 2_000_001, 2_000_002, 2_000_003);
    public static final List<Integer> STAGE_4_MAGIC_ROOMS = List.of(
            922_010_401, 922_010_402, STAGE_4_WEAK_MAGIC_ROOM);
    public static final List<Integer> STAGE_4_PHYSICAL_ROOMS = List.of(
            922_010_404, 922_010_405);
    public static final List<Integer> STAGE_7_TRIGGER_MOBS = List.of(
            9_300_169, 9_300_170, 9_300_171);

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
        if (mapId == STAGE_2_TRAP_MAP) return 2;
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

    /** Authored pass yield for each split room. */
    public static int roomPassQuota(int roomMapId) {
        return switch (roomMapId) {
            case 922_010_401, 922_010_402, 922_010_403, 922_010_405 -> 1;
            case 922_010_404 -> 2;
            case 922_010_501, 922_010_502, 922_010_503,
                    922_010_504, 922_010_505, 922_010_506 -> 4;
            default -> 0;
        };
    }

    /** Returns the next authored map a lagging member must traverse without skipping stages. */
    public static int nextTraversalMap(int currentMapId) {
        int currentStage = stageNumber(currentMapId);
        if (currentStage < 1 || currentStage > 9) return 0;
        int mainMapId = stage(currentStage).mapId();
        if (currentMapId != mainMapId) return mainMapId;
        return currentStage == 9 ? CLEAR_MAP : stage(currentStage + 1).mapId();
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
