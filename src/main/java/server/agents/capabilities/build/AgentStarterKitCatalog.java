package server.agents.capabilities.build;

import client.Job;

import java.util.List;
import java.util.Map;

public final class AgentStarterKitCatalog {
    // MapleStory content IDs belong to this catalog, not runtime tuning.
    private static final int BEGINNER_WARRIOR_SWORD_ID = 1_302_077;
    private static final int BEGINNER_MAGICIAN_WAND_ID = 1_372_043;
    private static final int BEGINNER_BOWMANS_BOW_ID = 1_452_051;
    private static final int BEGINNER_THIEF_WRIST_GUARD_ID = 1_472_061;
    private static final int BEGINNER_THIEF_SHORT_SWORD_ID = 1_332_063;
    private static final int NOVA_THROWING_KNIVES_ID = 2_070_015;
    private static final int GARNIER_ID = 1_492_000;
    private static final int STEEL_KNUCKLER_ID = 1_482_000;
    private static final int WOODEN_ARROWS_ID = 2_060_000;
    private static final int BULLETS_ID = 2_330_000;

    private static final Map<Job, List<AgentStarterItemGrant>> FIRST_JOB_KITS = Map.of(
            Job.WARRIOR, List.of(grant(BEGINNER_WARRIOR_SWORD_ID, 1)),
            Job.MAGICIAN, List.of(grant(BEGINNER_MAGICIAN_WAND_ID, 1)),
            Job.BOWMAN, List.of(grant(BEGINNER_BOWMANS_BOW_ID, 1), grant(WOODEN_ARROWS_ID, 1000)),
            Job.THIEF, List.of(
                    grant(BEGINNER_THIEF_WRIST_GUARD_ID, 1),
                    grant(BEGINNER_THIEF_SHORT_SWORD_ID, 1),
                    grant(NOVA_THROWING_KNIVES_ID, 500)
            ),
            Job.PIRATE, List.of(
                    grant(GARNIER_ID, 1),
                    grant(STEEL_KNUCKLER_ID, 1),
                    grant(BULLETS_ID, 1000)
            )
    );

    private AgentStarterKitCatalog() {
    }

    public static List<AgentStarterItemGrant> firstJobKitFor(Job job) {
        return FIRST_JOB_KITS.getOrDefault(job, List.of());
    }

    public static boolean isFirstJobAdvancement(Job oldJob, Job newJob) {
        return oldJob == Job.BEGINNER && FIRST_JOB_KITS.containsKey(newJob);
    }

    private static AgentStarterItemGrant grant(int itemId, int quantity) {
        return new AgentStarterItemGrant(itemId, (short) quantity);
    }
}
