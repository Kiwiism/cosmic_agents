package server.agents.journey;

import java.util.List;
import java.util.Map;

/** Final machine-readable cohort result plus pre-rendered operator views. */
public record AgentJourneyReport(
        int schemaVersion,
        String runId,
        String scenarioId,
        String status,
        long startedAtMs,
        long finishedAtMs,
        int targetLevel,
        int participantCount,
        int succeeded,
        int failed,
        long droppedSamples,
        List<AgentSummary> agents,
        List<QuestOutcome> quests,
        List<MapOutcome> maps,
        List<ResourceOutcome> resources,
        String agentsCsv,
        String questsCsv,
        String mapsCsv,
        String resourcesCsv,
        String markdown) {

    public AgentJourneyReport {
        agents = agents == null ? List.of() : List.copyOf(agents);
        quests = quests == null ? List.of() : List.copyOf(quests);
        maps = maps == null ? List.of() : List.copyOf(maps);
        resources = resources == null ? List.of() : List.copyOf(resources);
        agentsCsv = text(agentsCsv);
        questsCsv = text(questsCsv);
        mapsCsv = text(mapsCsv);
        resourcesCsv = text(resourcesCsv);
        markdown = text(markdown);
    }

    public record AgentSummary(
            int agentId,
            String agentName,
            String career,
            String status,
            int startLevel,
            int endLevel,
            int experienceGained,
            int mesosGained,
            int mesosSpent,
            int mesosNet,
            long durationMs,
            int kills,
            int questsCompleted,
            int recoveries,
            int recoveriesSucceeded,
            int stuckEpisodes,
            int mapsVisited,
            String lastPlan,
            String lastObjective,
            String failureReason) {
    }

    public record QuestOutcome(
            int agentId,
            String agentName,
            int questId,
            int jobId,
            int levelAtStart,
            int startMapId,
            int endMapId,
            long durationMs,
            int experienceGained,
            int mesosNet,
            Map<Integer, Integer> itemNet) {
        public QuestOutcome {
            itemNet = itemNet == null ? Map.of() : Map.copyOf(itemNet);
        }
    }

    public record MapOutcome(
            int agentId,
            String agentName,
            int mapId,
            long dwellMs,
            int visits,
            int kills,
            int recoveries) {
    }

    public record ResourceOutcome(
            int agentId,
            String agentName,
            int itemId,
            int startQuantity,
            int endQuantity,
            int netQuantity,
            int acquired,
            int consumedOrSold) {
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }
}
