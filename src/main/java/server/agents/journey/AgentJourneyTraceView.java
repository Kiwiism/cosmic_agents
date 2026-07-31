package server.agents.journey;

/** Read-only operator view of one Agent inside a journey experiment. */
public record AgentJourneyTraceView(
        int agentId,
        String agentName,
        String career,
        String status,
        int level,
        int mapId,
        String planId,
        String objectiveId,
        long lastProgressAtMs,
        int kills,
        int questsCompleted,
        int recoveries,
        int stuckEpisodes,
        String failureReason) {
}
