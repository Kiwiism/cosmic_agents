package server.agents.capabilities.partyquest.epq;

import java.util.Set;

/** Authoritative local Ellin Forest PQ contract. */
public final class AgentEpqDefinition {
    public static final int RECRUIT_MAP = 300_030_100;
    public static final int ENTRY_NPC = 2_133_000;
    public static final int STAGE_NPC = 2_133_001;
    public static final int STONE_NPC = 2_133_004;
    public static final int ENTRANCE_MAP = 930_000_000;
    public static final int STAGE_ONE_MAP = 930_000_100;
    public static final int STAGE_TWO_MAP = 930_000_200;
    public static final int STAGE_THREE_MAP = 930_000_300;
    public static final int STAGE_FOUR_MAP = 930_000_400;
    public static final int STAGE_FIVE_MAP = 930_000_500;
    public static final int BOSS_MAP = 930_000_600;
    public static final int REWARD_MAP = 930_000_800;
    public static final int MIN_LEVEL = 44;
    public static final int MAX_LEVEL = 55;
    public static final int MIN_PARTY_SIZE = 4;
    public static final int MAX_PARTY_SIZE = 6;

    public static final int POISON = 4_001_161;
    public static final int PURIFIED_POISON = 4_001_162;
    public static final int PURIFICATION_MARBLE = 2_270_004;
    public static final int MONSTER_MARBLE = 4_001_169;
    public static final int MAGIC_STONE = 4_001_163;
    public static final int ALTAIRE_FRAGMENT = 4_001_164;
    public static final Set<Integer> EXCLUSIVE_ITEMS = Set.of(
            PURIFIED_POISON, MAGIC_STONE, MONSTER_MARBLE, PURIFICATION_MARBLE);

    public static final int STAGE_ONE_MOB = 9_300_172;
    public static final int STAGE_TWO_MOB = 9_300_173;
    public static final int POISON_FLOWER = 9_300_175;
    public static final Set<Integer> BOSS_MOBS = Set.of(9_300_180, 9_300_181, 9_300_182);

    public static final int POND_REACTOR = 3_002_000;
    public static final int SPINE_REACTOR = 3_009_000;
    public static final int STONE_BOX = 3_002_001;
    public static final int EMPTY_BOX = 3_002_002;
    public static final int ALTAR_REACTOR = 3_001_000;
    public static final int REWARD_REACTOR = 3_008_000;

    private AgentEpqDefinition() { }

    public static boolean isEventMap(int mapId) {
        return mapId >= ENTRANCE_MAP && mapId <= REWARD_MAP;
    }

    public static int stageForMap(int mapId) {
        return switch (mapId) {
            case ENTRANCE_MAP -> 0;
            case STAGE_ONE_MAP -> 1;
            case STAGE_TWO_MAP -> 2;
            case STAGE_THREE_MAP -> 3;
            case STAGE_FOUR_MAP -> 4;
            case STAGE_FIVE_MAP -> 5;
            case BOSS_MAP -> 6;
            case REWARD_MAP -> 7;
            default -> -1;
        };
    }
}
