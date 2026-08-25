package server.agents.progression;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Script-verified Explorer second-job advancement contracts. */
public final class AgentSecondJobCatalog {
    public enum Family { WARRIOR, MAGICIAN, BOWMAN, THIEF, PIRATE }

    public record Branch(
            String id, Family family, int firstJobId, int targetJobId,
            int leaderMapId, int leaderNpcId, int instructorMapId, int instructorNpcId,
            int trialMapId, int examinerNpcId, int startQuestId, int collectQuestId,
            int finalQuestId, int letterItemId, int collectionItemId, int requiredCount,
            Set<Integer> trialMobIds, int requiredSkillId, int leaderSelection,
            String spProfileId) {
        public Branch {
            trialMobIds = Set.copyOf(trialMobIds);
        }

        public boolean pirate() { return family == Family.PIRATE; }
    }

    private static final Map<String, Branch> BY_ID = build();
    private static final Map<Integer, Branch> BY_JOB = indexByJob();

    private AgentSecondJobCatalog() { }

    public static Branch require(String id) {
        Branch branch = BY_ID.get(normalize(id));
        if (branch == null) throw new IllegalArgumentException("Unknown second-job branch: " + id);
        return branch;
    }

    public static Branch forTargetJob(int jobId) {
        Branch branch = BY_JOB.get(jobId);
        if (branch == null) throw new IllegalArgumentException("Unsupported second-job target: " + jobId);
        return branch;
    }

    public static Map<String, Branch> all() { return BY_ID; }

    public static String defaultBranch(String bundleId, int firstJobId) {
        String value = bundleId == null ? "" : bundleId.toLowerCase();
        if (value.contains("thief-claw")) return "assassin";
        if (value.contains("thief-dagger")) return "bandit";
        if (value.contains("pirate-gun")) return "gunslinger";
        if (value.contains("pirate-knuckle")) return "brawler";
        return switch (firstJobId) {
            case 100 -> "fighter";
            case 200 -> "cleric";
            case 300 -> "hunter";
            case 400 -> "assassin";
            case 500 -> "brawler";
            default -> throw new IllegalArgumentException("No default second-job branch for " + firstJobId);
        };
    }

    private static Map<String, Branch> build() {
        Map<String, Branch> values = new LinkedHashMap<>();
        add(values, standard("fighter", Family.WARRIOR, 100, 110, 102000003, 1022000,
                102020300, 1072000, 108000300, 1072004, 100003, 100004, 100005,
                4031008, Set.of(9000100, 9000101), 0, "mapleroyals-optimal-2026-fighter"));
        add(values, standard("page", Family.WARRIOR, 100, 120, 102000003, 1022000,
                102020300, 1072000, 108000300, 1072004, 100003, 100004, 100005,
                4031008, Set.of(9000100, 9000101), 1, "mapleroyals-optimal-2026-page"));
        add(values, standard("spearman", Family.WARRIOR, 100, 130, 102000003, 1022000,
                102020300, 1072000, 108000300, 1072004, 100003, 100004, 100005,
                4031008, Set.of(9000100, 9000101), 2, "mapleroyals-optimal-2026-spearman"));
        add(values, standard("fp-wizard", Family.MAGICIAN, 200, 210, 101000003, 1032001,
                101020000, 1072001, 108000200, 1072005, 100006, 100007, 100008,
                4031009, Set.of(9000001, 9000002), 0, "mapleroyals-optimal-2026-fp-wizard"));
        add(values, standard("il-wizard", Family.MAGICIAN, 200, 220, 101000003, 1032001,
                101020000, 1072001, 108000200, 1072005, 100006, 100007, 100008,
                4031009, Set.of(9000001, 9000002), 1, "mapleroyals-optimal-2026-il-wizard"));
        add(values, standard("cleric", Family.MAGICIAN, 200, 230, 101000003, 1032001,
                101020000, 1072001, 108000200, 1072005, 100006, 100007, 100008,
                4031009, Set.of(9000001, 9000002), 2, "mapleroyals-optimal-2026-cleric"));
        add(values, standard("hunter", Family.BOWMAN, 300, 310, 100000201, 1012100,
                106010000, 1072002, 108000100, 1072006, 100000, 100001, 100002,
                4031010, Set.of(9000200, 9000201), 0, "mapleroyals-optimal-2026-hunter"));
        add(values, standard("crossbowman", Family.BOWMAN, 300, 320, 100000201, 1012100,
                106010000, 1072002, 108000100, 1072006, 100000, 100001, 100002,
                4031010, Set.of(9000200, 9000201), 1, "mapleroyals-optimal-2026-crossbowman"));
        add(values, standard("assassin", Family.THIEF, 400, 410, 103000003, 1052001,
                102040000, 1072003, 108000400, 1072007, 100009, 100010, 100011,
                4031011, Set.of(9000300, 9000301), 0, "mapleroyals-optimal-2026-assassin"));
        add(values, standard("bandit", Family.THIEF, 400, 420, 103000003, 1052001,
                102040000, 1072003, 108000400, 1072007, 100009, 100010, 100011,
                4031011, Set.of(9000300, 9000301), 1, "mapleroyals-optimal-2026-bandit"));
        add(values, pirate("brawler", 510, 2191, 108000502, 4031856, 9001006,
                5001001, "mapleroyals-optimal-2026-brawler"));
        add(values, pirate("gunslinger", 520, 2192, 108000501, 4031857, 9001005,
                5001003, "mapleroyals-optimal-2026-gunslinger"));
        return Map.copyOf(values);
    }

    private static Branch standard(String id, Family family, int firstJob, int targetJob,
                                   int leaderMap, int leaderNpc, int instructorMap, int instructorNpc,
                                   int trialMap, int examiner, int startQuest, int collectQuest,
                                   int finalQuest, int letter, Set<Integer> mobs, int selection,
                                   String spProfile) {
        return new Branch(id, family, firstJob, targetJob, leaderMap, leaderNpc, instructorMap,
                instructorNpc, trialMap, examiner, startQuest, collectQuest, finalQuest, letter,
                4031013, 30, mobs, 0, selection, spProfile);
    }

    private static Branch pirate(String id, int targetJob, int quest, int trialMap, int item,
                                 int mob, int skill, String spProfile) {
        return new Branch(id, Family.PIRATE, 500, targetJob, 120000101, 1090000,
                120000101, 1090000, trialMap, 1072008, quest, quest, quest, 0,
                item, 15, Set.of(mob), skill, targetJob == 510 ? 0 : 1, spProfile);
    }

    private static void add(Map<String, Branch> values, Branch branch) {
        values.put(branch.id(), branch);
    }

    private static Map<Integer, Branch> indexByJob() {
        Map<Integer, Branch> values = new LinkedHashMap<>();
        BY_ID.values().forEach(branch -> values.put(branch.targetJobId(), branch));
        return Map.copyOf(values);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase().replace('_', '-');
    }
}
