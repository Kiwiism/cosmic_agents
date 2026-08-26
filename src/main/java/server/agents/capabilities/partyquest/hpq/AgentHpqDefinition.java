package server.agents.capabilities.partyquest.hpq;

import java.awt.Point;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Authoritative local Henesys PQ content contract. */
public final class AgentHpqDefinition {
    public static final int RECRUIT_MAP = 100_000_200;
    public static final int ENTRY_NPC = 101_211_2;
    public static final int TOMMY_NPC = 101_211_3;
    public static final int GROWLIE_NPC = 101_211_4;
    public static final int STAGE_MAP = 910_010_000;
    public static final int CLEAR_MAP = 910_010_100;
    public static final int BONUS_MAP = 910_010_200;
    public static final int EXIT_MAP = 910_010_300;
    public static final int REWARD_EXIT_MAP = 910_010_400;

    public static final int MOON_BUNNY = 9_300_061;
    public static final int RICE_CAKE = 4_001_101;
    public static final int REQUIRED_RICE_CAKES = 10;
    public static final int FLOWER_DROP_X_PX = 18;
    public static final int FLOWER_DROP_Y_PX = 40;

    /** Breakable primroses that produce the six seed colors used by the flower beds. */
    public static final Set<Integer> SEED_SOURCE_REACTORS = Set.of(
            9_102_002, 9_102_003, 9_102_004, 9_102_005, 9_102_006, 9_102_007);

    public static final Point MOON_BUNNY_POSITION = new Point(-183, -433);

    private static final List<SeedBed> SEED_BEDS = List.of(
            new SeedBed(4_001_095, 9_108_000, "moonflower1"),
            new SeedBed(4_001_096, 9_108_001, "moonflower2"),
            new SeedBed(4_001_097, 9_108_002, "moonflower3"),
            new SeedBed(4_001_098, 9_108_003, "moonflower4"),
            new SeedBed(4_001_099, 9_108_004, "moonflower5"),
            new SeedBed(4_001_100, 9_108_005, "moonflower6"));
    private static final Map<Integer, SeedBed> BED_BY_SEED = SEED_BEDS.stream()
            .collect(java.util.stream.Collectors.toUnmodifiableMap(SeedBed::seedItemId, bed -> bed));

    private AgentHpqDefinition() {
    }

    public static List<SeedBed> seedBeds() {
        return SEED_BEDS;
    }

    public static SeedBed seedBed(int seedItemId) {
        SeedBed bed = BED_BY_SEED.get(seedItemId);
        if (bed == null) throw new IllegalArgumentException("not an HPQ seed: " + seedItemId);
        return bed;
    }

    public static boolean isSeed(int itemId) {
        return BED_BY_SEED.containsKey(itemId);
    }

    public record SeedBed(int seedItemId, int reactorId, String reactorName) {
        public SeedBed {
            if (seedItemId <= 0 || reactorId <= 0 || reactorName == null || reactorName.isBlank()) {
                throw new IllegalArgumentException("valid HPQ seed-bed values are required");
            }
            reactorName = reactorName.trim();
        }
    }
}
