package server.agents.runtime.activity.world;

import java.util.EnumMap;

/** Pure milestone derivation; it never receives a mutable Cosmic object. */
public final class AgentWorldMilestoneEvaluator {
    private AgentWorldMilestoneEvaluator() {
    }

    public static AgentWorldMilestoneSnapshot evaluate(AgentWorldContext context) {
        if (context == null) {
            throw new IllegalArgumentException("world context is required");
        }
        EnumMap<AgentWorldMilestone, AgentWorldMilestoneStatus> statuses =
                new EnumMap<>(AgentWorldMilestone.class);
        EnumMap<AgentWorldMilestone, String> evidence =
                new EnumMap<>(AgentWorldMilestone.class);

        boolean victoria = context.mapId() >= 100_000_000 && context.mapId() < 200_000_000;
        put(statuses, evidence, AgentWorldMilestone.VICTORIA_REACHED,
                victoria ? AgentWorldMilestoneStatus.ACHIEVED
                        : AgentWorldMilestoneStatus.NOT_ACHIEVED,
                "map=" + context.mapId());
        boolean mapleIslandComplete = victoria || context.mapId() == 2_000_000
                && context.activeQuestIds().contains(1046);
        put(statuses, evidence, AgentWorldMilestone.MAPLE_ISLAND_COMPLETE,
                mapleIslandComplete ? AgentWorldMilestoneStatus.ACHIEVED
                        : AgentWorldMilestoneStatus.UNKNOWN,
                victoria ? "Agent is present in Victoria"
                        : mapleIslandComplete ? "Southperry handoff quest 1046 is active"
                        : "Maple Island completion cannot be inferred from this snapshot");

        boolean firstJob = context.jobId() != 0;
        put(statuses, evidence, AgentWorldMilestone.FIRST_JOB_COMPLETE,
                firstJob ? AgentWorldMilestoneStatus.ACHIEVED
                        : AgentWorldMilestoneStatus.NOT_ACHIEVED,
                "job=" + context.jobId());
        put(statuses, evidence, AgentWorldMilestone.LEVEL_15_REACHED,
                context.level() >= 15 ? AgentWorldMilestoneStatus.ACHIEVED
                        : AgentWorldMilestoneStatus.NOT_ACHIEVED,
                "level=" + context.level());

        boolean foundationComplete = "COMPLETE".equals(context.careerStage());
        AgentWorldMilestoneStatus foundationStatus = foundationComplete
                ? AgentWorldMilestoneStatus.ACHIEVED
                : context.careerStage().isBlank() ? AgentWorldMilestoneStatus.UNKNOWN
                : AgentWorldMilestoneStatus.NOT_ACHIEVED;
        put(statuses, evidence, AgentWorldMilestone.LEVEL_15_FOUNDATION_COMPLETE,
                foundationStatus, context.careerStage().isBlank()
                        ? "no career checkpoint is active" : "careerStage=" + context.careerStage());

        put(statuses, evidence, AgentWorldMilestone.LEVEL_25_REACHED,
                context.level() >= 25 ? AgentWorldMilestoneStatus.ACHIEVED
                        : AgentWorldMilestoneStatus.NOT_ACHIEVED,
                "level=" + context.level());

        boolean kpqLevel = context.level() >= 21 && context.level() <= 30;
        put(statuses, evidence, AgentWorldMilestone.KPQ_LEVEL_ELIGIBLE,
                kpqLevel ? AgentWorldMilestoneStatus.ACHIEVED
                        : AgentWorldMilestoneStatus.NOT_ACHIEVED,
                "level-only gate; combat readiness remains separate");
        put(statuses, evidence, AgentWorldMilestone.SQUISHY_SHOES_ACQUIRED,
                context.ownsSquishyShoes() ? AgentWorldMilestoneStatus.ACHIEVED
                        : AgentWorldMilestoneStatus.NOT_ACHIEVED,
                "inventory item 1072369=" + context.ownsSquishyShoes());
        put(statuses, evidence, AgentWorldMilestone.LEVEL_30_REACHED,
                context.level() >= 30 ? AgentWorldMilestoneStatus.ACHIEVED
                        : AgentWorldMilestoneStatus.NOT_ACHIEVED,
                "level=" + context.level());
        boolean secondJob = isSecondJob(context.jobId());
        put(statuses, evidence, AgentWorldMilestone.SECOND_JOB_COMPLETE,
                secondJob ? AgentWorldMilestoneStatus.ACHIEVED
                        : AgentWorldMilestoneStatus.NOT_ACHIEVED,
                "job=" + context.jobId());
        return new AgentWorldMilestoneSnapshot(context.capturedAtMs(), statuses, evidence);
    }

    private static boolean isSecondJob(int jobId) {
        if (jobId <= 0) return false;
        if (jobId < 1_000) return jobId % 100 >= 10;
        return jobId % 10 >= 1;
    }

    private static void put(
            EnumMap<AgentWorldMilestone, AgentWorldMilestoneStatus> statuses,
            EnumMap<AgentWorldMilestone, String> evidence,
            AgentWorldMilestone milestone,
            AgentWorldMilestoneStatus status,
            String reason) {
        statuses.put(milestone, status);
        evidence.put(milestone, reason);
    }
}
